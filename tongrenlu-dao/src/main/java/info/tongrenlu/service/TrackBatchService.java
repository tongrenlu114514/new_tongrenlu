package info.tongrenlu.service;

import info.tongrenlu.callback.TrackBatchCallback;
import info.tongrenlu.domain.TrackBean;
import info.tongrenlu.model.BatchStatistics;
import info.tongrenlu.model.ThbwikiTrack;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Orchestrates batch matching of local tracks against THBWiki tracks.
 * Collects statistics and supports progress callbacks.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TrackBatchService {

    private final ThbwikiService thbwikiService;

    /**
     * Batch match tracks against THBWiki tracks and save matches.
     * Returns a BatchStatistics object summarizing the results.
     *
     * @param tracks        the list of local tracks to match (nullable)
     * @param thbwikiTracks the list of THBWiki tracks to match against (nullable, treated as empty)
     * @return batch statistics with counts and failed track IDs
     */
    public BatchStatistics batchMatchAndSave(List<TrackBean> tracks, List<ThbwikiTrack> thbwikiTracks) {
        return batchMatchAndSaveWithProgress(tracks, thbwikiTracks, null);
    }

    /**
     * Batch match tracks against THBWiki tracks with progress callbacks.
     * When callback is null, behaves identically to batchMatchAndSave.
     *
     * @param tracks        the list of local tracks to match (nullable)
     * @param thbwikiTracks the list of THBWiki tracks to match against (nullable, treated as empty)
     * @param callback       optional progress callback (may be null)
     * @return batch statistics with counts and failed track IDs
     */
    public BatchStatistics batchMatchAndSaveWithProgress(
            List<TrackBean> tracks,
            List<ThbwikiTrack> thbwikiTracks,
            TrackBatchCallback callback) {

        // Normalize inputs
        List<TrackBean> safeTracks = tracks != null ? tracks : Collections.emptyList();
        List<ThbwikiTrack> safeThbwikiTracks = thbwikiTracks != null ? thbwikiTracks : Collections.emptyList();

        int total = safeTracks.size();
        log.info("Starting batch match for {} tracks against {} THBWiki tracks", total, safeThbwikiTracks.size());

        int matchedCount = 0;
        int unmatchedCount = 0;
        int errorCount = 0;
        List<Long> failedTrackIds = new ArrayList<>();

        for (int i = 0; i < safeTracks.size(); i++) {
            TrackBean track = safeTracks.get(i);
            int current = i + 1;

            if (track == null) {
                log.debug("Track at index {} is null, skipping", i);
                errorCount++;
                log.debug("Track 'null' was not matched (null track)");
                if (callback != null) {
                    callback.onProgress(current, total, null, false);
                }
                continue;
            }

            try {
                boolean matched = thbwikiService.matchAndSave(track, safeThbwikiTracks);

                if (matched) {
                    matchedCount++;
                    log.debug("Track '{}' was matched", track.getName());
                } else {
                    unmatchedCount++;
                    log.debug("Track '{}' was not matched", track.getName());
                }

                if (callback != null) {
                    callback.onProgress(current, total, track, matched);
                }

            } catch (Exception e) {
                errorCount++;
                Long trackId = track.getId();
                if (trackId != null) {
                    failedTrackIds.add(trackId);
                }
                log.error("Error processing track '{}': {}", track.getName(), e.getMessage(), e);

                if (callback != null) {
                    callback.onProgress(current, total, track, false);
                }
            }
        }

        log.info("Batch complete: {} matched, {} unmatched, {} errors, {} total",
                matchedCount, unmatchedCount, errorCount, total);

        return BatchStatistics.builder()
                .matchedCount(matchedCount)
                .unmatchedCount(unmatchedCount)
                .errorCount(errorCount)
                .totalProcessed(total)
                .failedTrackIds(failedTrackIds)
                .build();
    }
}
