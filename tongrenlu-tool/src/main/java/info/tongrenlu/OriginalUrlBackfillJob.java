package info.tongrenlu;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import info.tongrenlu.domain.TrackBean;
import info.tongrenlu.mapper.TrackMapper;
import info.tongrenlu.service.OriginalUrlBackfillService;
import info.tongrenlu.service.OriginalUrlBackfillService.BackfillResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Admin batch job that backfills {@code m_track.original_url} from {@code m_track.original}
 * by parsing the free-text descriptions in Java (instead of relying on MySQL string
 * functions which can't handle the full variety of prefix variants).
 *
 * Replaces the previous SQL-only migration (sql/20260423/m_track_original_url_backfill.sql).
 *
 * Trigger:
 *   - HTTP: POST /admin/original-url-backfill/trigger
 *   - HTTP: POST /admin/original-url-backfill/pause|resume
 *   - HTTP: GET  /admin/original-url-backfill/status
 *
 * Status codes (from {@link JobStatus}):
 *   IDLE     - no run in progress
 *   RUNNING  - actively processing
 *   PAUSED   - paused mid-cycle
 *   DONE     - cycle complete (remaining == 0)
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class OriginalUrlBackfillJob {

    private final TrackMapper trackMapper;
    private final OriginalUrlBackfillService backfillService;

    private final AtomicBoolean paused = new AtomicBoolean(false);
    private final AtomicBoolean dryRun = new AtomicBoolean(false);
    private final AtomicInteger processedCount = new AtomicInteger(0);
    private final AtomicInteger updatedCount = new AtomicInteger(0);
    private final AtomicInteger skippedCount = new AtomicInteger(0);
    private final AtomicReference<String> currentPhase = new AtomicReference<>("IDLE");
    private final AtomicReference<String> lastError = new AtomicReference<>(null);
    private final Map<String, AtomicInteger> stepCounts = new HashMap<>();

    /** Cursor page for resume after pause. */
    private volatile int currentPage = 1;

    private static final int PAGE_SIZE = 100;

    public void runCycle() {
        if (paused.get()) {
            log.info("Job paused, skipping cycle");
            return;
        }
        boolean wasDryRun = dryRun.get();
        currentPhase.set(wasDryRun ? "DRY_RUNNING" : "RUNNING");
        log.info("Starting original_url backfill cycle from page {} (dryRun={})", currentPage, wasDryRun);

        // Order by id so pagination is deterministic and idempotent across restarts.
        Page<TrackBean> page = new Page<>(currentPage, PAGE_SIZE);
        trackMapper.selectPage(page, new LambdaQueryWrapper<TrackBean>()
                .isNotNull(TrackBean::getOriginal)
                .ne(TrackBean::getOriginal, "")
                .isNull(TrackBean::getOriginalUrl)
                .orderByAsc(TrackBean::getId));

        if (page.getRecords().isEmpty()) {
            log.info("No tracks to backfill at page {}, cycle complete", currentPage);
            currentPage = 1;
            currentPhase.set("DONE");
            return;
        }

        for (TrackBean track : page.getRecords()) {
            if (paused.get()) {
                log.info("Paused at track id={}, saving cursor page={}", track.getId(), currentPage);
                break;
            }

            processedCount.incrementAndGet();
            lastError.set(null);

            String original = track.getOriginal();
            BackfillResult result = backfillService.deriveUrl(original);

            if (result == null) {
                skippedCount.incrementAndGet();
                log.debug("No URL derived for track id={} original='{}'", track.getId(), original);
                continue;
            }

            int rows;
            if (wasDryRun) {
                rows = 1; // simulate success without writing
                log.info("[DRY-RUN] WOULD update track id={} original_url={} step={}",
                        track.getId(), result.url(), result.step());
            } else {
                track.setOriginalUrl(result.url());
                rows = trackMapper.updateById(track);
                if (rows > 0) {
                    log.info("Updated track id={} original_url={} step={}",
                            track.getId(), result.url(), result.step());
                }
            }

            if (rows > 0) {
                updatedCount.incrementAndGet();
                stepCounts.computeIfAbsent(result.step(), k -> new AtomicInteger(0))
                        .incrementAndGet();
            } else {
                lastError.set("Failed to update track id=" + track.getId());
                log.warn("Failed to update track id={}", track.getId());
            }
        }

        // Advance cursor: next page, or wrap to 1 if end reached.
        currentPage = (long) currentPage + 1 > page.getPages() ? 1 : currentPage + 1;

        log.info("Cycle page {} complete (dryRun={}). processed={} updated={} skipped={}",
                page.getCurrent(), wasDryRun, processedCount.get(), updatedCount.get(), skippedCount.get());
        currentPhase.set("IDLE");
    }

    /**
     * Preview what the job would do for a small sample of rows. Used by the
     * admin UI to give operators confidence before triggering a real run.
     */
    public List<SampleResult> preview(int sampleSize) {
        Page<TrackBean> page = new Page<>(1, sampleSize);
        trackMapper.selectPage(page, new LambdaQueryWrapper<TrackBean>()
                .isNotNull(TrackBean::getOriginal)
                .ne(TrackBean::getOriginal, "")
                .isNull(TrackBean::getOriginalUrl)
                .orderByAsc(TrackBean::getId));

        List<SampleResult> out = new java.util.ArrayList<>();
        for (TrackBean track : page.getRecords()) {
            BackfillResult result = backfillService.deriveUrl(track.getOriginal());
            out.add(new SampleResult(
                    track.getId(),
                    track.getOriginal(),
                    result != null ? result.url() : null,
                    result != null ? result.step() : null));
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

    public void trigger() {
        CompletableFuture.runAsync(this::runCycle);
    }

    /** Trigger a dry-run cycle (does NOT write to DB). */
    public void triggerDryRun() {
        dryRun.set(true);
        CompletableFuture.runAsync(() -> {
            try {
                runCycle();
            } finally {
                dryRun.set(false);
            }
        });
    }

    /** Reset all counters and the cursor. Useful between runs. */
    public void reset() {
        processedCount.set(0);
        updatedCount.set(0);
        skippedCount.set(0);
        stepCounts.clear();
        currentPage = 1;
        lastError.set(null);
        currentPhase.set("IDLE");
        log.info("Job counters and cursor reset");
    }

    public JobStatus status() {
        long totalRemaining = trackMapper.selectCount(
                new LambdaQueryWrapper<TrackBean>()
                        .isNotNull(TrackBean::getOriginal)
                        .ne(TrackBean::getOriginal, "")
                        .isNull(TrackBean::getOriginalUrl));
        Map<String, Integer> snapshot = new HashMap<>();
        stepCounts.forEach((k, v) -> snapshot.put(k, v.get()));
        return new JobStatus(
                currentPhase.get(),
                lastError.get(),
                processedCount.get(),
                updatedCount.get(),
                skippedCount.get(),
                currentPage,
                totalRemaining,
                snapshot,
                dryRun.get()
        );
    }

    public record SampleResult(
            Long trackId,
            String original,
            String derivedUrl,
            String step
    ) {}

    public record JobStatus(
            String phase,
            String lastError,
            int processedCount,
            int updatedCount,
            int skippedCount,
            int currentPage,
            long totalRemaining,
            Map<String, Integer> stepCounts,
            boolean dryRun
    ) {}
}
