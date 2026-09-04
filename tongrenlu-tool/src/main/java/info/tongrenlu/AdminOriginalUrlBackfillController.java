package info.tongrenlu;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin HTTP endpoints for the {@link OriginalUrlBackfillJob}.
 *
 * The job scans {@code m_track} for rows where {@code original} is non-empty but
 * {@code original_url} is null, and fills the URL by parsing the free-text original
 * with Java regex (handles the full variety of prefix/separator variants found in
 * production data — see {@link info.tongrenlu.service.OriginalUrlBackfillService}).
 *
 * Workflow (recommended):
 *   1. GET  /admin/original-url-backfill/status         → check totalRemaining > 0
 *   2. GET  /admin/original-url-backfill/preview?size  → sample what would change
 *   3. POST /admin/original-url-backfill/dry-run       → non-destructive simulation
 *   4. POST /admin/original-url-backfill/trigger        → actually write
 *   5. POST /admin/original-url-backfill/pause|resume   → control mid-run
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/original-url-backfill")
@Slf4j
public class AdminOriginalUrlBackfillController {

    private final OriginalUrlBackfillJob job;

    @GetMapping("/status")
    public OriginalUrlBackfillJob.JobStatus status() {
        return job.status();
    }

    /**
     * Sample preview: derive URLs for the first N rows that need backfill, without
     * touching the database. Use this to sanity-check the regex output before triggering.
     */
    @GetMapping("/preview")
    public List<OriginalUrlBackfillJob.SampleResult> preview(
            @RequestParam(defaultValue = "20") int size) {
        int clamped = Math.max(1, Math.min(size, 200));
        return job.preview(clamped);
    }

    @PostMapping("/trigger")
    public Map<String, Boolean> trigger() {
        job.trigger();
        return Map.of("triggered", true);
    }

    /**
     * Dry-run: process all pages exactly like a real run, but don't write to DB.
     * Counts/breakdown are still updated so you can see what the run would have done.
     */
    @PostMapping("/dry-run")
    public Map<String, Boolean> dryRun() {
        job.triggerDryRun();
        return Map.of("dryRunStarted", true);
    }

    @PostMapping("/pause")
    public Map<String, Boolean> pause() {
        job.pause();
        return Map.of("paused", true);
    }

    @PostMapping("/resume")
    public Map<String, Boolean> resume() {
        job.resume();
        return Map.of("resumed", true);
    }

    /** Reset counters and cursor (does not touch data). */
    @PostMapping("/reset")
    public Map<String, Object> reset() {
        job.reset();
        Map<String, Object> resp = new HashMap<>();
        resp.put("reset", true);
        resp.put("status", job.status());
        return resp;
    }
}
