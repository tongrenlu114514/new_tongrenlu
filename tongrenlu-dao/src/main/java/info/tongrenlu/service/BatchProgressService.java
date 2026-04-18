package info.tongrenlu.service;

import info.tongrenlu.callback.TrackBatchCallback;
import info.tongrenlu.domain.AlbumDetailBean;
import info.tongrenlu.domain.TrackBean;
import info.tongrenlu.model.BatchProgressState;
import info.tongrenlu.model.BatchProgressState.AlbumStats;
import info.tongrenlu.model.BatchStatistics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages persistent state tracking for multi-album batch matching jobs.
 * Supports pause/resume and progress callbacks.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BatchProgressService {

    private final ConcurrentHashMap<String, BatchProgressState> jobs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicBoolean> pausedFlags = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, TrackBatchCallback> callbacks = new ConcurrentHashMap<>();

    /**
     * Starts a new batch job for processing a list of albums.
     *
     * @param albums the list of albums to process
     * @return a unique job ID for tracking this batch
     */
    public String startBatchJob(List<AlbumDetailBean> albums) {
        String jobId = UUID.randomUUID().toString();
        int totalAlbums = albums != null ? albums.size() : 0;

        BatchProgressState state = BatchProgressState.builder()
                .jobId(jobId)
                .currentAlbumIndex(0)
                .totalAlbums(totalAlbums)
                .completedAlbums(0)
                .failedAlbums(0)
                .startTime(Instant.now())
                .albumStats(new ConcurrentHashMap<>())
                .matchedCount(0)
                .unmatchedCount(0)
                .errorCount(0)
                .totalProcessed(0)
                .failedTrackIds(new ArrayList<>())
                .build();

        jobs.put(jobId, state);
        pausedFlags.put(jobId, new AtomicBoolean(false));

        log.info("Batch job started: jobId={}, totalAlbums={}", jobId, totalAlbums);
        return jobId;
    }

    /**
     * Called when processing of an album begins.
     *
     * @param jobId     the job ID
     * @param albumId   the album ID
     * @param albumName the album name
     */
    public void onAlbumStart(String jobId, Long albumId, String albumName) {
        BatchProgressState state = jobs.get(jobId);
        if (state == null) {
            log.warn("onAlbumStart: job not found, jobId={}", jobId);
            return;
        }

        state.setCurrentAlbumName(albumName);
        // Index is 0-based, so currentAlbumIndex is incremented before processing
        log.info("Album started: jobId={}, albumId={}, albumName={}, index={}",
                jobId, albumId, albumName, state.getCurrentAlbumIndex());
    }

    /**
     * Called when processing of an album completes.
     *
     * @param jobId  the job ID
     * @param albumId the album ID
     * @param stats  the batch statistics for this album
     */
    public void onAlbumComplete(String jobId, Long albumId, BatchStatistics stats) {
        BatchProgressState state = jobs.get(jobId);
        if (state == null) {
            log.warn("onAlbumComplete: job not found, jobId={}", jobId);
            return;
        }

        // Create AlbumStats from BatchStatistics
        AlbumStats albumStats = AlbumStats.builder()
                .albumId(albumId)
                .albumName(state.getCurrentAlbumName())
                .matchedCount(stats.getMatchedCount())
                .unmatchedCount(stats.getUnmatchedCount())
                .errorCount(stats.getErrorCount())
                .totalProcessed(stats.getTotalProcessed())
                .failedTrackIds(stats.getFailedTrackIds() != null
                        ? new ArrayList<>(stats.getFailedTrackIds())
                        : new ArrayList<>())
                .build();

        state.getAlbumStats().put(albumId, albumStats);

        // Increment completed albums
        state.setCompletedAlbums(state.getCompletedAlbums() + 1);

        // Increment failed albums if there were errors
        if (stats.getErrorCount() > 0) {
            state.setFailedAlbums(state.getFailedAlbums() + 1);
        }

        // Update overall statistics
        state.setMatchedCount(state.getMatchedCount() + stats.getMatchedCount());
        state.setUnmatchedCount(state.getUnmatchedCount() + stats.getUnmatchedCount());
        state.setErrorCount(state.getErrorCount() + stats.getErrorCount());
        state.setTotalProcessed(state.getTotalProcessed() + stats.getTotalProcessed());

        // Collect failed track IDs
        if (stats.getFailedTrackIds() != null) {
            List<Long> currentFailed = state.getFailedTrackIds();
            List<Long> newFailed = new ArrayList<>(currentFailed);
            newFailed.addAll(stats.getFailedTrackIds());
            state.setFailedTrackIds(newFailed);
        }

        // Increment current album index for next album
        state.setCurrentAlbumIndex(state.getCurrentAlbumIndex() + 1);

        log.info("Album completed: jobId={}, albumId={}, stats={}", jobId, albumId, stats);
    }

    /**
     * Called after each track is processed. Forwards to registered callback if present.
     *
     * @param jobId    the job ID
     * @param current  the 1-based index of the current track
     * @param total    the total number of tracks
     * @param track    the track that was processed
     * @param matched  true if the track was matched, false otherwise
     */
    public void onTrackProgress(String jobId, int current, int total, TrackBean track, boolean matched) {
        TrackBatchCallback callback = callbacks.get(jobId);
        if (callback != null) {
            callback.onProgress(current, total, track, matched);
        }
    }

    /**
     * Gets the current progress state for a job.
     *
     * @param jobId the job ID
     * @return the current BatchProgressState snapshot, or null if not found
     */
    public BatchProgressState getProgress(String jobId) {
        return jobs.get(jobId);
    }

    /**
     * Registers a callback for track progress notifications.
     *
     * @param jobId    the job ID
     * @param callback the callback to register
     */
    public void registerCallback(String jobId, TrackBatchCallback callback) {
        if (callback != null) {
            callbacks.put(jobId, callback);
        }
    }

    /**
     * Pauses the specified job.
     *
     * @param jobId the job ID
     */
    public void pause(String jobId) {
        AtomicBoolean paused = pausedFlags.get(jobId);
        if (paused != null) {
            paused.set(true);
            log.info("Job paused: jobId={}", jobId);
        } else {
            log.warn("Pause requested for unknown job: jobId={}", jobId);
        }
    }

    /**
     * Resumes the specified job.
     *
     * @param jobId the job ID
     */
    public void resume(String jobId) {
        AtomicBoolean paused = pausedFlags.get(jobId);
        if (paused != null) {
            paused.set(false);
            log.info("Job resumed: jobId={}", jobId);
        } else {
            log.warn("Resume requested for unknown job: jobId={}", jobId);
        }
    }

    /**
     * Checks if a job is currently paused.
     *
     * @param jobId the job ID
     * @return true if paused, false otherwise (including if job doesn't exist)
     */
    public boolean isPaused(String jobId) {
        AtomicBoolean paused = pausedFlags.get(jobId);
        return paused != null && paused.get();
    }
}
