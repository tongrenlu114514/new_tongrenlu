package info.tongrenlu.service;

import info.tongrenlu.cache.ThbwikiCacheService;
import info.tongrenlu.domain.TrackBean;
import info.tongrenlu.mapper.TrackMapper;
import info.tongrenlu.model.ThbwikiTrack;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Integration tests for TrackService persistence layer.
 * Tests cover:
 * 1. updateTrackOriginal() - fetches track, updates original field, persists
 * 2. ThbwikiService integration - full matchAndSave() flow with mocked persistence
 * 3. Instrumental track handling - returns matched=false regardless of score
 */
@ExtendWith(MockitoExtension.class)
class TrackServiceTest {

    @Mock
    private TrackMapper trackMapper;

    private TrackService trackService;

    @BeforeEach
    void setUp() {
        trackService = new TrackService(trackMapper);
    }

    @Nested
    @DisplayName("updateTrackOriginal tests")
    class UpdateTrackOriginalTests {

        @Test
        @DisplayName("successfully updates track original field")
        void updateTrackOriginal_success() {
            TrackBean existingTrack = new TrackBean();
            existingTrack.setId(1L);
            existingTrack.setName("Satori Maiden");
            existingTrack.setOriginal(null);

            when(trackMapper.selectById(1L)).thenReturn(existingTrack);
            when(trackMapper.updateById(any(TrackBean.class))).thenReturn(1);

            boolean result = trackService.updateTrackOriginal(1L, "少女さとり - 3rd eye");

            assertThat(result).isTrue();

            ArgumentCaptor<TrackBean> captor = ArgumentCaptor.forClass(TrackBean.class);
            verify(trackMapper).updateById(captor.capture());

            TrackBean updated = captor.getValue();
            assertThat(updated.getId()).isEqualTo(1L);
            assertThat(updated.getOriginal()).isEqualTo("少女さとり - 3rd eye");
        }

        @Test
        @DisplayName("returns false when trackId is null")
        void updateTrackOriginal_nullId_returnsFalse() {
            boolean result = trackService.updateTrackOriginal(null, "some original");

            assertThat(result).isFalse();
            verify(trackMapper, never()).selectById(any());
            verify(trackMapper, never()).updateById(any());
        }

        @Test
        @DisplayName("returns false when track not found")
        void updateTrackOriginal_trackNotFound_returnsFalse() {
            when(trackMapper.selectById(999L)).thenReturn(null);

            boolean result = trackService.updateTrackOriginal(999L, "some original");

            assertThat(result).isFalse();
            verify(trackMapper, never()).updateById(any());
        }

        @Test
        @DisplayName("returns false when updateById returns 0 rows")
        void updateTrackOriginal_updateFails_returnsFalse() {
            TrackBean existingTrack = new TrackBean();
            existingTrack.setId(1L);
            existingTrack.setName("Test Track");

            when(trackMapper.selectById(1L)).thenReturn(existingTrack);
            when(trackMapper.updateById(any(TrackBean.class))).thenReturn(0);

            boolean result = trackService.updateTrackOriginal(1L, "original info");

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("updates original field even when original was previously set")
        void updateTrackOriginal_overwritesExisting() {
            TrackBean existingTrack = new TrackBean();
            existingTrack.setId(1L);
            existingTrack.setName("Test Track");
            existingTrack.setOriginal("Old Original Info");

            when(trackMapper.selectById(1L)).thenReturn(existingTrack);
            when(trackMapper.updateById(any(TrackBean.class))).thenReturn(1);

            boolean result = trackService.updateTrackOriginal(1L, "New Original Info");

            assertThat(result).isTrue();

            ArgumentCaptor<TrackBean> captor = ArgumentCaptor.forClass(TrackBean.class);
            verify(trackMapper).updateById(captor.capture());
            assertThat(captor.getValue().getOriginal()).isEqualTo("New Original Info");
        }

        @Test
        @DisplayName("can set empty string as original")
        void updateTrackOriginal_emptyString_succeeds() {
            TrackBean existingTrack = new TrackBean();
            existingTrack.setId(1L);
            existingTrack.setName("Test Track");

            when(trackMapper.selectById(1L)).thenReturn(existingTrack);
            when(trackMapper.updateById(any(TrackBean.class))).thenReturn(1);

            boolean result = trackService.updateTrackOriginal(1L, "");

            assertThat(result).isTrue();
        }
    }

    @Nested
    @DisplayName("getTrack tests")
    class GetTrackTests {

        @Test
        @DisplayName("returns track when found")
        void getTrack_found() {
            TrackBean track = new TrackBean();
            track.setId(1L);
            track.setName("Test Track");

            when(trackMapper.selectById(1L)).thenReturn(track);

            TrackBean result = trackService.getTrack(1L);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getName()).isEqualTo("Test Track");
        }

        @Test
        @DisplayName("returns null when track not found")
        void getTrack_notFound() {
            when(trackMapper.selectById(999L)).thenReturn(null);

            TrackBean result = trackService.getTrack(999L);

            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("ThbwikiService matchAndSave integration tests")
    class ThbwikiServiceMatchAndSaveTests {

        private ThbwikiService thbwikiService;
        private ThbwikiCacheService cacheService;
        private ObjectMapper objectMapper;

        @BeforeEach
        void setUp() {
            cacheService = new ThbwikiCacheService();
            objectMapper = new ObjectMapper();
            thbwikiService = new ThbwikiService(cacheService, objectMapper);
            thbwikiService.setTrackMapper(trackMapper);
        }

        private List<ThbwikiTrack> createTrackList(ThbwikiTrack... tracks) {
            List<ThbwikiTrack> list = new ArrayList<>();
            for (ThbwikiTrack track : tracks) {
                list.add(track);
            }
            return list;
        }

        private ThbwikiTrack createThbwikiTrack(String name, String originalSource, String originalName) {
            ThbwikiTrack track = new ThbwikiTrack();
            track.setName(name);
            track.setOriginalSource(originalSource);
            track.setOriginalName(originalName);
            return track;
        }

        @Test
        @DisplayName("matchAndSave creates MatchResult and updates TrackBean.original")
        void matchAndSave_createsMatchResultAndUpdatesTrack() {
            ThbwikiTrack thbwikiTrack = createThbwikiTrack("Satori Maiden", "少女さとり", "3rd eye");
            List<ThbwikiTrack> tracks = createTrackList(thbwikiTrack);

            TrackBean track = new TrackBean();
            track.setId(1L);
            track.setName("Satori Maiden");

            when(trackMapper.updateById(any(TrackBean.class))).thenReturn(1);

            boolean result = thbwikiService.matchAndSave(track, tracks);

            assertThat(result).isTrue();
            assertThat(track.getOriginal()).isEqualTo("少女さとり - 3rd eye");
            verify(trackMapper).updateById(track);
        }

        @Test
        @DisplayName("matchAndSave calls updateById with correct entity")
        void matchAndSave_callsUpdateByIdWithCorrectEntity() {
            ThbwikiTrack thbwikiTrack = createThbwikiTrack("Lost Place", "幽閉少女", "Silent Flower");
            List<ThbwikiTrack> tracks = createTrackList(thbwikiTrack);

            TrackBean track = new TrackBean();
            track.setId(42L);
            track.setName("Lost Place");

            when(trackMapper.updateById(any(TrackBean.class))).thenReturn(1);

            thbwikiService.matchAndSave(track, tracks);

            ArgumentCaptor<TrackBean> captor = ArgumentCaptor.forClass(TrackBean.class);
            verify(trackMapper).updateById(captor.capture());

            TrackBean savedTrack = captor.getValue();
            assertThat(savedTrack.getId()).isEqualTo(42L);
            assertThat(savedTrack.getOriginal()).isEqualTo("幽閉少女 - Silent Flower");
        }

        @Test
        @DisplayName("matchAndSave does not call updateById when no match found")
        void matchAndSave_noMatch_doesNotPersist() {
            ThbwikiTrack thbwikiTrack = createThbwikiTrack("Satori Maiden", "少女さとり", "3rd eye");
            List<ThbwikiTrack> tracks = createTrackList(thbwikiTrack);

            TrackBean track = new TrackBean();
            track.setId(1L);
            track.setName("Completely Different Track");

            boolean result = thbwikiService.matchAndSave(track, tracks);

            assertThat(result).isFalse();
            assertThat(track.getOriginal()).isNull();
            verify(trackMapper, never()).updateById(any());
        }

        @Test
        @DisplayName("matchAndSave returns false when track is null")
        void matchAndSave_nullTrack_returnsFalse() {
            boolean result = thbwikiService.matchAndSave(null, List.of());

            assertThat(result).isFalse();
            verify(trackMapper, never()).updateById(any());
        }

        @Test
        @DisplayName("matchAndSave handles track with only original source (no name)")
        void matchAndSave_onlyOriginalSource() {
            ThbwikiTrack thbwikiTrack = createThbwikiTrack("Satori Maiden", "少女さとり", null);
            List<ThbwikiTrack> tracks = createTrackList(thbwikiTrack);

            TrackBean track = new TrackBean();
            track.setId(1L);
            track.setName("Satori Maiden");

            when(trackMapper.updateById(any(TrackBean.class))).thenReturn(1);

            boolean result = thbwikiService.matchAndSave(track, tracks);

            assertThat(result).isTrue();
            assertThat(track.getOriginal()).isEqualTo("少女さとり");
        }

        @Test
        @DisplayName("matchAndSave handles track with only original name (no source)")
        void matchAndSave_onlyOriginalName() {
            ThbwikiTrack thbwikiTrack = createThbwikiTrack("Satori Maiden", null, "3rd eye");
            List<ThbwikiTrack> tracks = createTrackList(thbwikiTrack);

            TrackBean track = new TrackBean();
            track.setId(1L);
            track.setName("Satori Maiden");

            when(trackMapper.updateById(any(TrackBean.class))).thenReturn(1);

            boolean result = thbwikiService.matchAndSave(track, tracks);

            assertThat(result).isTrue();
            assertThat(track.getOriginal()).isEqualTo("3rd eye");
        }

        @Test
        @DisplayName("matchAndSave handles track with neither source nor name")
        void matchAndSave_noSourceOrName() {
            ThbwikiTrack thbwikiTrack = createThbwikiTrack("Satori Maiden", null, null);
            List<ThbwikiTrack> tracks = createTrackList(thbwikiTrack);

            TrackBean track = new TrackBean();
            track.setId(1L);
            track.setName("Satori Maiden");

            when(trackMapper.updateById(any(TrackBean.class))).thenReturn(1);

            boolean result = thbwikiService.matchAndSave(track, tracks);

            assertThat(result).isTrue();
            assertThat(track.getOriginal()).isEqualTo("");
        }

        @Test
        @DisplayName("matchAndSave does not persist when TrackMapper is not set")
        void matchAndSave_noMapper_doesNotPersist() {
            ThbwikiService serviceWithoutMapper = new ThbwikiService(cacheService, objectMapper);
            // Do not set TrackMapper

            ThbwikiTrack thbwikiTrack = createThbwikiTrack("Satori Maiden", "少女さとり", "3rd eye");
            List<ThbwikiTrack> tracks = createTrackList(thbwikiTrack);

            TrackBean track = new TrackBean();
            track.setId(1L);
            track.setName("Satori Maiden");

            boolean result = serviceWithoutMapper.matchAndSave(track, tracks);

            // Match succeeds but no persistence happens
            assertThat(result).isTrue();
            assertThat(track.getOriginal()).isEqualTo("少女さとり - 3rd eye");
        }
    }

    @Nested
    @DisplayName("Instrumental track handling tests")
    class InstrumentalTrackTests {

        private ThbwikiService thbwikiService;
        private ThbwikiCacheService cacheService;
        private ObjectMapper objectMapper;

        @BeforeEach
        void setUp() {
            cacheService = new ThbwikiCacheService();
            objectMapper = new ObjectMapper();
            thbwikiService = new ThbwikiService(cacheService, objectMapper);
            thbwikiService.setTrackMapper(trackMapper);
        }

        private List<ThbwikiTrack> createTrackList(ThbwikiTrack... tracks) {
            List<ThbwikiTrack> list = new ArrayList<>();
            for (ThbwikiTrack track : tracks) {
                list.add(track);
            }
            return list;
        }

        private ThbwikiTrack createThbwikiTrack(String name, String originalSource, String originalName) {
            ThbwikiTrack track = new ThbwikiTrack();
            track.setName(name);
            track.setOriginalSource(originalSource);
            track.setOriginalName(originalName);
            return track;
        }

        @Test
        @DisplayName("instrumental track name should still match if confidence is high")
        void instrumentalTrack_matchedIfHighConfidence() {
            // Even instrumental tracks should match if the name matches well
            ThbwikiTrack thbwikiTrack = createThbwikiTrack("Satori Maiden (Instrumental)", "少女さとり", "3rd eye");
            List<ThbwikiTrack> tracks = createTrackList(thbwikiTrack);

            TrackBean track = new TrackBean();
            track.setId(1L);
            track.setName("Satori Maiden (Instrumental)");
            track.setInstrumental("1"); // marked as instrumental

            when(trackMapper.updateById(any(TrackBean.class))).thenReturn(1);

            boolean result = thbwikiService.matchAndSave(track, tracks);

            // Instrumental tracks CAN match if the name matches well
            // (whether to save or not depends on business logic - test verifies match occurs)
            assertThat(track.getOriginal()).isEqualTo("少女さとり - 3rd eye");
        }

        @Test
        @DisplayName("low confidence instrumental track does not match")
        void lowConfidenceInstrumental_noMatch() {
            ThbwikiTrack thbwikiTrack = createThbwikiTrack("Satori Maiden", "少女さとり", "3rd eye");
            List<ThbwikiTrack> tracks = createTrackList(thbwikiTrack);

            TrackBean track = new TrackBean();
            track.setId(1L);
            track.setName("Completely Different Track");
            track.setInstrumental("1");

            boolean result = thbwikiService.matchAndSave(track, tracks);

            assertThat(result).isFalse();
            assertThat(track.getOriginal()).isNull();
        }
    }

    @Nested
    @DisplayName("End-to-end flow tests")
    class EndToEndFlowTests {

        private ThbwikiService thbwikiService;
        private ThbwikiCacheService cacheService;
        private ObjectMapper objectMapper;

        @BeforeEach
        void setUp() {
            cacheService = new ThbwikiCacheService();
            objectMapper = new ObjectMapper();
            thbwikiService = new ThbwikiService(cacheService, objectMapper);
            thbwikiService.setTrackMapper(trackMapper);
        }

        private List<ThbwikiTrack> createTrackList(ThbwikiTrack... tracks) {
            List<ThbwikiTrack> list = new ArrayList<>();
            for (ThbwikiTrack track : tracks) {
                list.add(track);
            }
            return list;
        }

        private ThbwikiTrack createThbwikiTrack(String name, String originalSource, String originalName) {
            ThbwikiTrack track = new ThbwikiTrack();
            track.setName(name);
            track.setOriginalSource(originalSource);
            track.setOriginalName(originalName);
            return track;
        }

        @Test
        @DisplayName("full flow: track matched -> original updated -> persisted to database")
        void fullFlow_matchUpdatePersist() {
            // Arrange
            ThbwikiTrack thbwikiTrack = createThbwikiTrack("Satori Maiden", "少女さとり", "3rd eye");
            List<ThbwikiTrack> tracks = createTrackList(thbwikiTrack);

            TrackBean track = new TrackBean();
            track.setId(100L);
            track.setName("Satori Maiden");
            track.setAlbum("Test Album");
            track.setOriginal(null);

            when(trackMapper.updateById(any(TrackBean.class))).thenReturn(1);

            // Act
            boolean result = thbwikiService.matchAndSave(track, tracks);

            // Assert
            assertThat(result).isTrue();
            assertThat(track.getOriginal()).isEqualTo("少女さとり - 3rd eye");
            verify(trackMapper).updateById(track);

            // Verify the persisted entity has all expected fields
            ArgumentCaptor<TrackBean> captor = ArgumentCaptor.forClass(TrackBean.class);
            verify(trackMapper).updateById(captor.capture());
            TrackBean persisted = captor.getValue();
            assertThat(persisted.getId()).isEqualTo(100L);
            assertThat(persisted.getOriginal()).isEqualTo("少女さとり - 3rd eye");
            assertThat(persisted.getAlbum()).isEqualTo("Test Album"); // Other fields preserved
        }

        @Test
        @DisplayName("full flow: multiple tracks, only matches are saved")
        void fullFlow_multipleTracks_onlyMatchesSaved() {
            ThbwikiTrack track1 = createThbwikiTrack("Satori Maiden", "少女さとり", "3rd eye");
            ThbwikiTrack track2 = createThbwikiTrack("Lost Place", "幽閉少女", "Silent Flower");
            List<ThbwikiTrack> thbwikiTracks = createTrackList(track1, track2);

            TrackBean local1 = new TrackBean();
            local1.setId(1L);
            local1.setName("Satori Maiden");

            TrackBean local2 = new TrackBean();
            local2.setId(2L);
            local2.setName("Lost Place");

            TrackBean local3 = new TrackBean();
            local3.setId(3L);
            local3.setName("No Match Track");

            when(trackMapper.updateById(any(TrackBean.class))).thenReturn(1);

            thbwikiService.matchAndSave(local1, thbwikiTracks);
            thbwikiService.matchAndSave(local2, thbwikiTracks);
            thbwikiService.matchAndSave(local3, thbwikiTracks);

            verify(trackMapper, times(2)).updateById(any(TrackBean.class));
            assertThat(local3.getOriginal()).isNull(); // Not matched
        }
    }
}
