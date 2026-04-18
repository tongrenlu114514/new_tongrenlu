package info.tongrenlu.service;

import info.tongrenlu.domain.TrackBean;
import info.tongrenlu.mapper.TrackMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service for managing track data, including updating original track information.
 */
@RequiredArgsConstructor
@Service
@Slf4j
public class TrackService {

    private final TrackMapper trackMapper;
    /**
     * Update the original field for a track.
     *
     * @param trackId the ID of the track to update
     * @param original the original track info to set
     * @return true if the update was successful
     */
    public boolean updateTrackOriginal(Long trackId, String original) {
        if (trackId == null) {
            log.warn("Cannot update original: trackId is null");
            return false;
        }

        TrackBean track = trackMapper.selectById(trackId);
        if (track == null) {
            log.warn("Cannot update original: track not found for id={}", trackId);
            return false;
        }

        track.setOriginal(original);
        int rows = trackMapper.updateById(track);

        if (rows > 0) {
            log.info("Updated track id={} original to '{}'", trackId, original);
            return true;
        } else {
            log.warn("Failed to update track id={}", trackId);
            return false;
        }
    }

    /**
     * Get a track by ID.
     *
     * @param trackId the ID of the track
     * @return the track, or null if not found
     */
    public TrackBean getTrack(Long trackId) {
        return trackMapper.selectById(trackId);
    }
}
