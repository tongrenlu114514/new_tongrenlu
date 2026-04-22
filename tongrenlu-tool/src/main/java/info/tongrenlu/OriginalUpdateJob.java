package info.tongrenlu;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import info.tongrenlu.domain.ArticleBean;
import info.tongrenlu.domain.TrackBean;
import info.tongrenlu.mapper.ArticleMapper;
import info.tongrenlu.mapper.TrackMapper;
import info.tongrenlu.model.ThbwikiAlbum;
import info.tongrenlu.service.ThbwikiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Component
@Slf4j
@RequiredArgsConstructor
public class OriginalUpdateJob {

    private final ThbwikiService thbwikiService;
    private final ArticleMapper articleMapper;
    private final TrackMapper trackMapper;

    private final AtomicBoolean paused = new AtomicBoolean(false);
    private final AtomicInteger processedCount = new AtomicInteger(0);
    private final AtomicReference<String> lastAlbumTitle = new AtomicReference<>(null);
    private final AtomicReference<String> lastError = new AtomicReference<>(null);
    private final AtomicReference<String> currentPhase = new AtomicReference<>("IDLE");

    /** Cursor page for resume after pause. */
    private volatile int currentPage = 1;

    private static final int PAGE_SIZE = 10;

    @Scheduled(cron = "${app.original-update.cron:0 0 3 * * ?}")
    public void runScheduledCycle() {
        runCycle();
    }

    public void runCycle() {
        if (paused.get()) {
            log.info("Job paused, skipping cycle");
            return;
        }

        currentPhase.set("RUNNING");
        log.info("Starting original update cycle");

        Page<ArticleBean> page = new Page<>(1, PAGE_SIZE);
        articleMapper.selectPage(page, new LambdaQueryWrapper<ArticleBean>()
                .isNull(ArticleBean::getThbWikiUrl)
                .eq(ArticleBean::getPublishFlg, "1"));

        if (page.getRecords().isEmpty()) {
            log.info("No unprocessed albums, cycle complete");
            currentPhase.set("IDLE");
            return;
        }

        int matchCount = 0;

        for (ArticleBean album : page.getRecords()) {
            if (paused.get()) {
                log.info("Paused at album id={}, title={}, saving cursor page={}",
                        album.getId(), album.getTitle(), currentPage);
                break;
            }

            processedCount.incrementAndGet();
            lastAlbumTitle.set(album.getTitle());
            lastError.set(null);

            log.info("Processing album: {} (id={})", album.getTitle(), album.getId());

            List<ThbwikiAlbum> searchResults = thbwikiService.searchAlbum(album.getTitle());

            if (searchResults.isEmpty()) {
                log.warn("No THBWiki results for album: {}", album.getTitle());
                album.setThbWikiUrl("NOT_FOUND");
                album.setUpdDate(new Date());
                articleMapper.updateById(album);
                lastError.set("No results found for: " + album.getTitle());
                currentPage++;
                continue;
            }

            ThbwikiAlbum firstResult = searchResults.get(0);
            Optional<ThbwikiAlbum> detailOpt = thbwikiService.fetchAlbumDetail(firstResult.getUrl());

            if (detailOpt.isEmpty()) {
                log.warn("Could not fetch detail for album: {}", album.getTitle());
                album.setThbWikiUrl("FETCH_FAILED");
                album.setUpdDate(new Date());
                articleMapper.updateById(album);
                lastError.set("Fetch failed for: " + album.getTitle());
                currentPage++;
                continue;
            }

            ThbwikiAlbum thbwikiAlbum = detailOpt.get();
            List<TrackBean> localTracks = trackMapper.selectList(
                    new LambdaQueryWrapper<TrackBean>().eq(TrackBean::getArticleId, album.getId()));

            int albumMatchCount = 0;
            for (TrackBean track : localTracks) {
                boolean matched = thbwikiService.matchAndSave(track, thbwikiAlbum.getTracks());
                if (matched) {
                    albumMatchCount++;
                }
            }

            album.setThbWikiUrl(firstResult.getUrl());
            album.setUpdDate(new Date());
            articleMapper.updateById(album);

            matchCount += albumMatchCount;
            log.info("Completed album: {}, {} tracks matched", album.getTitle(), albumMatchCount);
            currentPage++;
        }

        log.info("Cycle complete. Processed {} albums", processedCount.get());
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
        return new JobStatus(
                currentPhase.get(),
                lastAlbumTitle.get(),
                lastError.get(),
                processedCount.get(),
                currentPage
        );
    }

    public record JobStatus(
            String phase,
            String lastAlbumTitle,
            String lastError,
            int processedCount,
            int currentPage
    ) {}
}
