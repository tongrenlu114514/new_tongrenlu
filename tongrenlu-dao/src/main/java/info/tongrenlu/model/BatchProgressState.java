package info.tongrenlu.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Snapshot of progress for a multi-album batch matching job.
 * Tracks per-album statistics and overall job progress.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchProgressState {

    /**
     * Unique identifier for this batch job.
     */
    private String jobId;

    /**
     * Name of the album currently being processed.
     */
    private String currentAlbumName;

    /**
     * Zero-based index of the album currently being processed.
     */
    private int currentAlbumIndex;

    /**
     * Total number of albums in this batch job.
     */
    private int totalAlbums;

    /**
     * Number of albums that have finished processing (success or failure).
     */
    private int completedAlbums;

    /**
     * Number of albums that failed entirely (error during album-level processing).
     */
    private int failedAlbums;

    /**
     * Wall-clock time when this job started.
     */
    private Instant startTime;

    /**
     * Per-album statistics keyed by album ID.
     * AlbumStats.toBatchStatistics() aggregates these into the top-level job stats.
     */
    @Builder.Default
    private Map<Long, AlbumStats> albumStats = new ConcurrentHashMap<>();

    // ---- Inherited BatchStatistics delegation ----

    private int matchedCount;
    private int unmatchedCount;
    private int errorCount;
    private int totalProcessed;

    @Builder.Default
    private List<Long> failedTrackIds = new ArrayList<>();

    /**
     * Creates an empty BatchProgressState with all counts at zero and no albums.
     */
    public static BatchProgressState empty() {
        return BatchProgressState.builder()
                .currentAlbumIndex(0)
                .totalAlbums(0)
                .completedAlbums(0)
                .failedAlbums(0)
                .matchedCount(0)
                .unmatchedCount(0)
                .errorCount(0)
                .totalProcessed(0)
                .albumStats(new ConcurrentHashMap<>())
                .failedTrackIds(Collections.emptyList())
                .build();
    }

    /**
     * Creates a BatchProgressState initialized from a list of AlbumStats objects.
     * Useful when rebuilding state from persisted album statistics.
     */
    public static BatchProgressState fromAlbumStats(List<AlbumStats> albumStatsList) {
        BatchProgressState state = empty();
        if (albumStatsList != null) {
            for (AlbumStats as : albumStatsList) {
                state.albumStats.put(as.getAlbumId(), as);
            }
        }
        return state;
    }

    /**
     * Recomputes inherited BatchStatistics fields from the current albumStats map.
     * Call this after mutating albumStats to sync the aggregated counts.
     */
    public void recomputeBatchStatistics() {
        this.matchedCount = 0;
        this.unmatchedCount = 0;
        this.errorCount = 0;
        this.totalProcessed = 0;
        List<Long> allFailed = new ArrayList<>();
        for (AlbumStats as : this.albumStats.values()) {
            this.matchedCount += as.getMatchedCount();
            this.unmatchedCount += as.getUnmatchedCount();
            this.errorCount += as.getErrorCount();
            this.totalProcessed += as.getTotalProcessed();
            allFailed.addAll(as.getFailedTrackIds());
        }
        this.failedTrackIds = allFailed;
    }

    /**
     * Overall progress percentage across all albums (0–100).
     * Returns 0 when totalAlbums is 0.
     */
    public int getProgressPercent() {
        if (totalAlbums <= 0) {
            return 0;
        }
        return (int) Math.min(100, (completedAlbums * 100L) / totalAlbums);
    }

    /**
     * Total number of tracks matched across all albums.
     */
    public int getOverallMatchedCount() {
        return albumStats.values().stream()
                .mapToInt(AlbumStats::getMatchedCount)
                .sum();
    }

    /**
     * Total number of tracks unmatched across all albums.
     */
    public int getOverallUnmatchedCount() {
        return albumStats.values().stream()
                .mapToInt(AlbumStats::getUnmatchedCount)
                .sum();
    }

    /**
     * Total number of track errors across all albums.
     */
    public int getOverallErrorCount() {
        return albumStats.values().stream()
                .mapToInt(AlbumStats::getErrorCount)
                .sum();
    }

    /**
     * Total number of tracks processed across all albums.
     */
    public int getOverallTotalProcessed() {
        return albumStats.values().stream()
                .mapToInt(AlbumStats::getTotalProcessed)
                .sum();
    }

    /**
     * Aggregates all per-album AlbumStats into a single BatchStatistics object.
     * Convenience method for reporting the final job outcome.
     */
    public BatchStatistics toBatchStatistics() {
        recomputeBatchStatistics();
        return BatchStatistics.builder()
                .matchedCount(this.matchedCount)
                .unmatchedCount(this.unmatchedCount)
                .errorCount(this.errorCount)
                .totalProcessed(this.totalProcessed)
                .failedTrackIds(Collections.unmodifiableList(new ArrayList<>(this.failedTrackIds)))
                .build();
    }

    /**
     * Per-album statistics snapshot.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AlbumStats {

        private Long albumId;
        private String albumName;
        private int matchedCount;
        private int unmatchedCount;
        private int errorCount;
        private int totalProcessed;

        @Builder.Default
        private List<Long> failedTrackIds = new ArrayList<>();

        /**
         * Converts this per-album AlbumStats into a top-level BatchStatistics object.
         */
        public BatchStatistics toBatchStatistics() {
            return BatchStatistics.builder()
                    .matchedCount(this.matchedCount)
                    .unmatchedCount(this.unmatchedCount)
                    .errorCount(this.errorCount)
                    .totalProcessed(this.totalProcessed)
                    .failedTrackIds(Collections.unmodifiableList(new ArrayList<>(this.failedTrackIds)))
                    .build();
        }
    }
}
