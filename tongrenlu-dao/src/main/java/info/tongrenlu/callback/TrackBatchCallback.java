package info.tongrenlu.callback;

import info.tongrenlu.domain.TrackBean;

/**
 * Callback interface for batch track matching progress notifications.
 * Implementations can use these callbacks to update UIs, log progress,
 * or trigger side-effects during batch processing.
 */
@FunctionalInterface
public interface TrackBatchCallback {

    /**
     * Called after each track is processed.
     *
     * @param current the 1-based index of the current track
     * @param total the total number of tracks in the batch
     * @param track the track that was processed (may be null if track was null)
     * @param matched true if the track was matched and saved, false otherwise
     */
    void onProgress(int current, int total, TrackBean track, boolean matched);
}
