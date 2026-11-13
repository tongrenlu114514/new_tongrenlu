package info.tongrenlu;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import info.tongrenlu.domain.ArtistBean;
import info.tongrenlu.enums.ThbWikiStatus;
import info.tongrenlu.mapper.ArtistMapper;
import info.tongrenlu.service.ArtistWikiService;
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
 * 艺术家 Wiki 内容批量生成任务：
 * 每轮从 thb_wiki_status='PENDING' 的艺术家中取一批（artist.wiki.batch-limit，默认 2），
 * 依次执行 THBWiki 抓取 → LLM 生成 → 入库。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ArtistWikiGenerateJob {

    private final ArtistMapper artistMapper;
    private final ArtistWikiService artistWikiService;

    @Value("${artist.wiki.batch-limit:2}")
    private int batchLimit;

    private final AtomicBoolean paused = new AtomicBoolean(false);
    private final AtomicInteger processedCount = new AtomicInteger(0);
    private final AtomicReference<String> lastArtistName = new AtomicReference<>(null);
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
        log.info("Starting artist wiki generate cycle from page {}", currentPage);

        Page<ArtistBean> page = new Page<>(currentPage, PAGE_SIZE);
        artistMapper.selectPage(page, new LambdaQueryWrapper<ArtistBean>()
                .eq(ArtistBean::getThbWikiStatus, ThbWikiStatus.PENDING.name())
                .isNotNull(ArtistBean::getTagId)
                .orderByAsc(ArtistBean::getId));

        if (page.getRecords().isEmpty()) {
            log.info("No pending artists at page {}, cycle complete", currentPage);
            currentPage = 1;
            currentPhase.set("IDLE");
            return;
        }

        int processedThisCycle = 0;
        for (ArtistBean artist : page.getRecords()) {
            if (paused.get()) {
                log.info("Paused at artist id={}, name={}, saving cursor page={}",
                        artist.getId(), artist.getName(), currentPage);
                break;
            }
            if (processedThisCycle >= batchLimit) {
                log.info("Batch limit {} reached this cycle", batchLimit);
                break;
            }

            processedCount.incrementAndGet();
            processedThisCycle++;
            lastArtistName.set(artist.getName());
            lastError.set(null);

            log.info("Processing artist: {} (id={})", artist.getName(), artist.getId());
            try {
                artistWikiService.generateForArtist(artist, false);
            } catch (Exception e) {
                log.error("Failed to generate wiki for artist {} (id={})", artist.getName(), artist.getId(), e);
                lastError.set(artist.getName() + ": " + e.getMessage());
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
        long totalRemaining = artistMapper.selectCount(
                new LambdaQueryWrapper<ArtistBean>()
                        .eq(ArtistBean::getThbWikiStatus, ThbWikiStatus.PENDING.name())
                        .isNotNull(ArtistBean::getTagId));
        return new JobStatus(
                currentPhase.get(),
                lastArtistName.get(),
                lastError.get(),
                processedCount.get(),
                currentPage,
                totalRemaining
        );
    }

    public record JobStatus(
            String phase,
            String lastArtistName,
            String lastError,
            int processedCount,
            int currentPage,
            long totalRemaining
    ) {}
}
