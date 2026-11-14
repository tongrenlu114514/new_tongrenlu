package info.tongrenlu;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import info.tongrenlu.domain.TrackBean;
import info.tongrenlu.mapper.ArticleTagMapper;
import info.tongrenlu.mapper.TrackMapper;
import info.tongrenlu.service.OriginalTagParser;
import info.tongrenlu.service.OriginalTagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 后台批量任务：扫描 {@code m_track} 中 {@code original} 非空、且不含全角 ／ 的遗留格式曲目，
 * 解析出「原曲 / 原作」并调用 {@link OriginalTagService} 保存成 tag。
 * THBWiki 匹配写出的 {@code "原曲 ／ 原作"} 由 {@code ThbwikiService.matchAndSave} 直接写 tag，
 * 不再由本任务处理（见 scopeFilter）；仅 {@code all=true} 的一次性全量触发会连带处理。
 *
 * <p>照 {@link OriginalUrlBackfillJob} 的运维方式：分页游标 + pause/resume + dry-run +
 * preview + status。区别是任务本身没有「已处理」标记列，因此单次 trigger 会走完整轮
 * （循环翻页直到结束或暂停），重跑全量但幂等（查重后插入）。
 *
 * <p>触发方式（见 {@link AdminOriginalTagBackfillController}）：
 * <pre>
 *   GET  /admin/original-tag-backfill/status
 *   GET  /admin/original-tag-backfill/preview?size=20[&amp;all=true]
 *   POST /admin/original-tag-backfill/dry-run[?all=true]
 *   POST /admin/original-tag-backfill/trigger[?all=true]
 *   POST /admin/original-tag-backfill/pause | /resume
 *   POST /admin/original-tag-backfill/reset          （只重置计数器）
 *   POST /admin/original-tag-backfill/clear          （清空原曲/原作标签关联后重跑）
 * </pre>
 *
 * <p>{@code all=true} 是<strong>一次性</strong>开关：仅对本次调用忽略全角 ／ 过滤，
 * 把存量的 THBWiki 格式行也一起补 tag；下一次不带参数的 trigger 自动回到默认（收窄）范围。
 * 中途 pause 后要续跑，需再次带 {@code ?all=true} 触发（页码游标会保留）。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class OriginalTagBackfillJob {

    private final TrackMapper trackMapper;
    private final ArticleTagMapper articleTagMapper;
    private final OriginalTagService originalTagService;
    private final OriginalTagParser parser = new OriginalTagParser();

    private final AtomicBoolean paused = new AtomicBoolean(false);
    private final AtomicBoolean dryRun = new AtomicBoolean(false);
    private final AtomicInteger processedCount = new AtomicInteger(0);
    private final AtomicInteger taggedCount = new AtomicInteger(0);
    private final AtomicInteger skippedCount = new AtomicInteger(0);
    private final AtomicReference<String> currentPhase = new AtomicReference<>("IDLE");
    private final AtomicReference<String> lastError = new AtomicReference<>(null);

    /** 断点续跑的页码游标 */
    private volatile int currentPage = 1;

    private static final int PAGE_SIZE = 100;

    /** 默认范围的一次完整轮次（等效于 {@code trigger(false)} 的同步版本）。 */
    public void runCycle() {
        runCycle(false, dryRun.get());
    }

    /**
     * 核心轮次。{@code all} 作为参数按调用传入，天然是一次性的：本次跑完不会影响下次 trigger。
     *
     * @param all true = 连 THBWiki 格式行（含全角 ／）一起处理；false = 只处理遗留格式
     */
    void runCycle(boolean all, boolean wasDryRun) {
        if (paused.get()) {
            log.info("Job paused, skipping cycle");
            return;
        }
        currentPhase.set(wasDryRun ? "DRY_RUNNING" : "RUNNING");
        log.info("Starting original-tag backfill from page {} (dryRun={}, all={})",
                currentPage, wasDryRun, all);

        try {
            boolean complete = false;
            while (!paused.get()) {
                Page<TrackBean> page = new Page<>(currentPage, PAGE_SIZE);
                trackMapper.selectPage(page, scopeWrapper(all));
                if (page.getRecords().isEmpty()) {
                    complete = true;
                    break;
                }
                for (TrackBean track : page.getRecords()) {
                    if (paused.get()) {
                        break;
                    }
                    process(track, wasDryRun);
                }
                if (paused.get()) {
                    break;
                }
                if ((long) currentPage >= page.getPages()) {
                    complete = true;
                    break;
                }
                currentPage++;
            }

            if (paused.get()) {
                currentPhase.set("PAUSED");
                log.info("Paused at page {}", currentPage);
            } else if (complete) {
                currentPage = 1;
                currentPhase.set("DONE");
                log.info("Cycle complete. processed={} tagged={} skipped={}",
                        processedCount.get(), taggedCount.get(), skippedCount.get());
            }
        } catch (Exception e) {
            lastError.set(e.getMessage());
            currentPhase.set("IDLE");
            log.error("Original-tag backfill failed", e);
        }
    }

    /**
     * 扫描范围。默认只处理「遗留格式」的 original：THBWiki 匹配写出的
     * {@code "原曲 ／ 原作"} 含全角 ／，已由 {@code ThbwikiService.matchAndSave} 直接写 tag，
     * 两条路径不重叠，避免按「、」误切造出错误 tag。
     *
     * @param includeThbwiki true = 忽略全角 ／ 过滤（一次性全量补存量）；false = 默认收窄范围
     */
    private LambdaQueryWrapper<TrackBean> scopeFilter(boolean includeThbwiki) {
        LambdaQueryWrapper<TrackBean> wrapper = new LambdaQueryWrapper<TrackBean>()
                .isNotNull(TrackBean::getOriginal)
                .ne(TrackBean::getOriginal, "");
        if (!includeThbwiki) {
            wrapper.notLike(TrackBean::getOriginal, "／");
        }
        return wrapper;
    }

    private LambdaQueryWrapper<TrackBean> scopeWrapper(boolean includeThbwiki) {
        return scopeFilter(includeThbwiki).orderByAsc(TrackBean::getId);
    }

    private void process(TrackBean track, boolean dryRun) {
        processedCount.incrementAndGet();
        lastError.set(null);

        OriginalTagParser.ParsedOriginal parsed = parser.parse(track.getOriginal());
        int tagCount = parsed.songs().size() + parsed.games().size();
        if (tagCount == 0) {
            skippedCount.incrementAndGet();
            return;
        }
        if (!dryRun) {
            try {
                originalTagService.syncTrack(track);
            } catch (Exception e) {
                lastError.set("track id=" + track.getId() + ": " + e.getMessage());
                log.warn("Failed to sync original tags for track id={}", track.getId(), e);
                return;
            }
        }
        taggedCount.incrementAndGet();
        log.debug("{} track id={} songs={} games={}",
                dryRun ? "[DRY-RUN]" : "Tagged",
                track.getId(), parsed.songs(), parsed.games());
    }

    /** 抽样预览解析结果，不写库。all=true 时抽样范围包含 THBWiki 格式行。 */
    public List<SampleResult> preview(int sampleSize, boolean all) {
        int clamped = Math.max(1, Math.min(sampleSize, 200));
        Page<TrackBean> page = new Page<>(1, clamped);
        trackMapper.selectPage(page, scopeWrapper(all));

        List<SampleResult> out = new ArrayList<>();
        for (TrackBean track : page.getRecords()) {
            OriginalTagParser.ParsedOriginal parsed = parser.parse(track.getOriginal());
            out.add(new SampleResult(track.getId(), track.getOriginal(),
                    parsed.songs(), parsed.games()));
        }
        return out;
    }

    public void pause() {
        paused.set(true);
        currentPhase.set("PAUSED");
        log.info("Job paused");
    }

    public void resume() {
        paused.set(false);
        log.info("Job resumed");
    }

    /** @param all true = 本次连 THBWiki 格式行一起补 tag（一次性） */
    public void trigger(boolean all) {
        CompletableFuture.runAsync(() -> runCycle(all, false));
    }

    /** 触发 dry-run（不写库）。@param all 同上 */
    public void triggerDryRun(boolean all) {
        dryRun.set(true);   // 仅供 status 展示
        CompletableFuture.runAsync(() -> {
            try {
                runCycle(all, true);
            } finally {
                dryRun.set(false);
            }
        });
    }

    /** 只重置计数器与游标，不动数据。 */
    public void reset() {
        processedCount.set(0);
        taggedCount.set(0);
        skippedCount.set(0);
        currentPage = 1;
        lastError.set(null);
        currentPhase.set("IDLE");
        log.info("Job counters and cursor reset");
    }

    /** 清空全部「原曲 / 原作」标签关联（保留 m_tag 词条），用于重建。 */
    public int clearLinks() {
        int rows = articleTagMapper.deleteOriginalTagLinks();
        log.info("Cleared {} original-tag links", rows);
        return rows;
    }

    public JobStatus status() {
        // totalTracks 固定按默认（收窄）范围统计；all=true 是一次性运行参数，不改变常态口径
        long totalTracks = trackMapper.selectCount(scopeFilter(false));
        return new JobStatus(
                currentPhase.get(),
                lastError.get(),
                processedCount.get(),
                taggedCount.get(),
                skippedCount.get(),
                currentPage,
                totalTracks,
                dryRun.get());
    }

    public record SampleResult(
            Long trackId,
            String original,
            LinkedHashSet<String> songs,
            LinkedHashSet<String> games
    ) {}

    public record JobStatus(
            String phase,
            String lastError,
            int processedCount,
            int taggedCount,
            int skippedCount,
            int currentPage,
            long totalTracks,
            boolean dryRun
    ) {}
}
