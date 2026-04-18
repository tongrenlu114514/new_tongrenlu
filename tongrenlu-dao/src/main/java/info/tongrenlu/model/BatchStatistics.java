package info.tongrenlu.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Statistics collected during a batch track matching operation.
 * Immutable after construction — use the builder or static factory methods.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchStatistics {

    /**
     * Number of tracks successfully matched against THBWiki.
     */
    private int matchedCount;

    /**
     * Number of tracks that had no match (confidence below threshold).
     */
    private int unmatchedCount;

    /**
     * Number of tracks that threw an exception during processing.
     */
    private int errorCount;

    /**
     * Total number of tracks processed.
     */
    private int totalProcessed;

    /**
     * IDs of tracks that failed due to exceptions.
     */
    @Builder.Default
    private List<Long> failedTrackIds = new ArrayList<>();

    /**
     * Creates an empty BatchStatistics with all counts at zero.
     */
    public static BatchStatistics empty() {
        return BatchStatistics.builder()
                .matchedCount(0)
                .unmatchedCount(0)
                .errorCount(0)
                .totalProcessed(0)
                .failedTrackIds(Collections.emptyList())
                .build();
    }

    /**
     * Creates a BatchStatistics from individual counts.
     * Convenience factory when caller already computed the values.
     */
    public static BatchStatistics of(int matched, int unmatched, int errors, int total, List<Long> failedIds) {
        return BatchStatistics.builder()
                .matchedCount(matched)
                .unmatchedCount(unmatched)
                .errorCount(errors)
                .totalProcessed(total)
                .failedTrackIds(failedIds != null ? failedIds : Collections.emptyList())
                .build();
    }
}
