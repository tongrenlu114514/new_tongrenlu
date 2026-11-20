package info.tongrenlu;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import info.tongrenlu.domain.TagBean;
import info.tongrenlu.enums.WikiStatus;
import info.tongrenlu.mapper.TagMapper;
import info.tongrenlu.service.WikiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Wiki 词条统一批量生成任务：每轮从 m_tag 中取一批 wiki_status='PENDING' 的词条
 * （artist + event + original_game + original_track，wiki.batch-limit 默认 2），
 * 依次执行素材抓取 → LLM 生成 → 入库。全模块只保留这一个调度任务。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class WikiGenerateJob {

    private final TagMapper tagMapper;
    private final WikiService wikiService;

    @Value("${wiki.batch-limit:2}")
    private int batchLimit;

    private final AtomicBoolean paused = new AtomicBoolean(false);
    private final AtomicInteger processedCount = new AtomicInteger(0);
    private final AtomicReference<String> lastName = new AtomicReference<>(null);
    private final AtomicReference<String> lastError = new AtomicReference<>(null);
    private final AtomicReference<String> currentPhase = new AtomicReference<>("IDLE");

    /** 游标页，暂停/恢复后继续 */
    private volatile int currentPage = 1;

    private static final int PAGE_SIZE = 50;

    @Scheduled(fixedDelay = 120, initialDelay = 60, timeUnit = TimeUnit.SECONDS)
    public void runScheduledCycle() {
        runCycle();
    }

    public void runCycle() {
        if (paused.get()) {
            log.info("Job paused, skipping cycle");
            return;
        }

        currentPhase.set("RUNNING");
        log.info("Starting wiki generate cycle from page {}", currentPage);

        Page<TagBean> page = new Page<>(currentPage, PAGE_SIZE);
        tagMapper.selectPage(page, pendingWrapper());

        if (page.getRecords().isEmpty()) {
            log.info("No pending wiki entries at page {}, cycle complete", currentPage);
            currentPage = 1;
            currentPhase.set("IDLE");
            return;
        }

        int processedThisCycle = 0;
        for (TagBean tag : page.getRecords()) {
            if (paused.get()) {
                log.info("Paused at entry id={}, name={}, saving cursor page={}",
                        tag.getId(), tag.getTag(), currentPage);
                break;
            }
            if (processedThisCycle >= batchLimit) {
                log.info("Batch limit {} reached this cycle", batchLimit);
                break;
            }

            processedCount.incrementAndGet();
            processedThisCycle++;
            lastName.set(tag.getTag());
            lastError.set(null);

            log.info("Processing wiki entry: {} (id={}, type={})", tag.getTag(), tag.getId(), tag.getType());
            try {
                wikiService.generateForTag(tag, false);
            } catch (Exception e) {
                log.error("Failed to generate wiki for {} (id={})", tag.getTag(), tag.getId(), e);
                lastError.set(tag.getTag() + ": " + e.getMessage());
            }
        }

        // 游标推进：本页处理完（未因上限/暂停中断）才翻页，否则停在当前页下轮继续
        if (processedThisCycle < batchLimit && !paused.get()) {
            currentPage = (long) currentPage + 1 > page.getPages() ? 1 : currentPage + 1;
        }

        log.info("Cycle page {} complete. processed this cycle={}, total={}",
                page.getCurrent(), processedThisCycle, processedCount.get());
        currentPhase.set("IDLE");
    }

    private LambdaQueryWrapper<TagBean> pendingWrapper() {
        return new LambdaQueryWrapper<TagBean>()
                .eq(TagBean::getWikiStatus, WikiStatus.PENDING.name())
                .in(TagBean::getType, WikiService.TYPE_ARTIST, WikiService.TYPE_EVENT,
                        WikiService.TYPE_ORIGINAL_GAME, WikiService.TYPE_ORIGINAL_TRACK)
                .eq(TagBean::getDelFlg, "0")
                .orderByAsc(TagBean::getId);
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

    public void trigger() {
        CompletableFuture.runAsync(this::runCycle);
    }

    public JobStatus status() {
        long totalRemaining = tagMapper.selectCount(pendingWrapper());
        long artistRemaining = tagMapper.selectCount(new LambdaQueryWrapper<TagBean>()
                .eq(TagBean::getWikiStatus, WikiStatus.PENDING.name())
                .eq(TagBean::getType, WikiService.TYPE_ARTIST)
                .eq(TagBean::getDelFlg, "0"));
        long eventRemaining = tagMapper.selectCount(new LambdaQueryWrapper<TagBean>()
                .eq(TagBean::getWikiStatus, WikiStatus.PENDING.name())
                .eq(TagBean::getType, WikiService.TYPE_EVENT)
                .eq(TagBean::getDelFlg, "0"));
        long originalGameRemaining = tagMapper.selectCount(new LambdaQueryWrapper<TagBean>()
                .eq(TagBean::getWikiStatus, WikiStatus.PENDING.name())
                .eq(TagBean::getType, WikiService.TYPE_ORIGINAL_GAME)
                .eq(TagBean::getDelFlg, "0"));
        long originalTrackRemaining = tagMapper.selectCount(new LambdaQueryWrapper<TagBean>()
                .eq(TagBean::getWikiStatus, WikiStatus.PENDING.name())
                .eq(TagBean::getType, WikiService.TYPE_ORIGINAL_TRACK)
                .eq(TagBean::getDelFlg, "0"));
        return new JobStatus(
                currentPhase.get(),
                lastName.get(),
                lastError.get(),
                processedCount.get(),
                currentPage,
                totalRemaining,
                artistRemaining,
                eventRemaining,
                originalGameRemaining,
                originalTrackRemaining
        );
    }

    public record JobStatus(
            String phase,
            String lastName,
            String lastError,
            int processedCount,
            int currentPage,
            long totalRemaining,
            long artistRemaining,
            long eventRemaining,
            long originalGameRemaining,
            long originalTrackRemaining
    ) {}
}
