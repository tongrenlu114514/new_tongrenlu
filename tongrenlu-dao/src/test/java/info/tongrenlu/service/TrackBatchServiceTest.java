package info.tongrenlu.service;

import info.tongrenlu.callback.TrackBatchCallback;
import info.tongrenlu.domain.TrackBean;
import info.tongrenlu.model.BatchStatistics;
import info.tongrenlu.model.ThbwikiTrack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TrackBatchService.
 * Tests cover batch matching orchestration, statistics collection,
 * error handling, and callback invocation.
 */
@ExtendWith(MockitoExtension.class)
class TrackBatchServiceTest {

    @Mock
    private ThbwikiService thbwikiService;

    private TrackBatchService trackBatchService;

    @BeforeEach
    void setUp() {
        trackBatchService = new TrackBatchService(thbwikiService);
    }

    // Helper methods matching existing test patterns
    private List<TrackBean> createTrackList(TrackBean... tracks) {
        List<TrackBean> list = new ArrayList<>();
        for (TrackBean track : tracks) {
            list.add(track);
        }
        return list;
    }

    private TrackBean createTrack(String name, Long id) {
        TrackBean track = new TrackBean();
        track.setName(name);
        track.setId(id);
        return track;
    }

    private ThbwikiTrack createThbwikiTrack(String name, String originalSource, String originalName) {
        ThbwikiTrack track = new ThbwikiTrack();
        track.setName(name);
        track.setOriginalSource(originalSource);
        track.setOriginalName(originalName);
        return track;
    }

    @Nested
    @DisplayName("success_allTracksMatch")
    class SuccessAllTracksMatch {

        @Test
        @DisplayName("all tracks matched returns correct statistics")
        void allTracksMatched_statisticsCorrect() {
            // Arrange: 3 tracks, all match
            TrackBean track1 = createTrack("Satori Maiden", 1L);
            TrackBean track2 = createTrack("Lost Place", 2L);
            TrackBean track3 = createTrack("U.N. Owen", 3L);
            List<TrackBean> tracks = createTrackList(track1, track2, track3);

            ThbwikiTrack thbwiki1 = createThbwikiTrack("Satori Maiden", "少女さとり", "3rd eye");
            ThbwikiTrack thbwiki2 = createThbwikiTrack("Lost Place", "幽閉少女", "Silent Flower");
            List<ThbwikiTrack> thbwikiTracks = List.of(thbwiki1, thbwiki2);

            when(thbwikiService.matchAndSave(any(TrackBean.class), any())).thenReturn(true);

            // Act
            BatchStatistics stats = trackBatchService.batchMatchAndSave(tracks, thbwikiTracks);

            // Assert
            assertThat(stats.getMatchedCount()).isEqualTo(3);
            assertThat(stats.getUnmatchedCount()).isEqualTo(0);
            assertThat(stats.getErrorCount()).isEqualTo(0);
            assertThat(stats.getTotalProcessed()).isEqualTo(3);
            assertThat(stats.getFailedTrackIds()).isEmpty();
        }
    }

    @Nested
    @DisplayName("partialMatch_someTracksMatch")
    class PartialMatchSomeTracksMatch {

        @Test
        @DisplayName("2 tracks match, 1 does not match returns correct statistics")
        void partialMatch_statisticsCorrect() {
            // Arrange
            TrackBean track1 = createTrack("Satori Maiden", 1L);
            TrackBean track2 = createTrack("Lost Place", 2L);
            TrackBean track3 = createTrack("No Match", 3L);
            List<TrackBean> tracks = createTrackList(track1, track2, track3);

            ThbwikiTrack thbwiki1 = createThbwikiTrack("Satori Maiden", "少女さとり", "3rd eye");
            ThbwikiTrack thbwiki2 = createThbwikiTrack("Lost Place", "幽閉少女", "Silent Flower");
            List<ThbwikiTrack> thbwikiTracks = List.of(thbwiki1, thbwiki2);

            when(thbwikiService.matchAndSave(eq(track1), any())).thenReturn(true);
            when(thbwikiService.matchAndSave(eq(track2), any())).thenReturn(true);
            when(thbwikiService.matchAndSave(eq(track3), any())).thenReturn(false);

            // Act
            BatchStatistics stats = trackBatchService.batchMatchAndSave(tracks, thbwikiTracks);

            // Assert
            assertThat(stats.getMatchedCount()).isEqualTo(2);
            assertThat(stats.getUnmatchedCount()).isEqualTo(1);
            assertThat(stats.getErrorCount()).isEqualTo(0);
            assertThat(stats.getTotalProcessed()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("noMatch_noTracksMatch")
    class NoMatchNoTracksMatch {

        @Test
        @DisplayName("no tracks match returns correct statistics")
        void noMatch_statisticsCorrect() {
            // Arrange: 3 tracks, none match
            TrackBean track1 = createTrack("Track One", 1L);
            TrackBean track2 = createTrack("Track Two", 2L);
            TrackBean track3 = createTrack("Track Three", 3L);
            List<TrackBean> tracks = createTrackList(track1, track2, track3);

            List<ThbwikiTrack> thbwikiTracks = List.of(
                    createThbwikiTrack("Different One", "出处1", "曲名1")
            );

            when(thbwikiService.matchAndSave(any(TrackBean.class), any())).thenReturn(false);

            // Act
            BatchStatistics stats = trackBatchService.batchMatchAndSave(tracks, thbwikiTracks);

            // Assert
            assertThat(stats.getMatchedCount()).isEqualTo(0);
            assertThat(stats.getUnmatchedCount()).isEqualTo(3);
            assertThat(stats.getErrorCount()).isEqualTo(0);
            assertThat(stats.getTotalProcessed()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("emptyTracksList")
    class EmptyTracksList {

        @Test
        @DisplayName("empty input returns all zeros")
        void emptyTracks_returnsAllZeros() {
            // Arrange
            List<TrackBean> tracks = Collections.emptyList();
            List<ThbwikiTrack> thbwikiTracks = List.of(
                    createThbwikiTrack("Some Track", "出处", "曲名")
            );

            // Act
            BatchStatistics stats = trackBatchService.batchMatchAndSave(tracks, thbwikiTracks);

            // Assert
            assertThat(stats.getMatchedCount()).isEqualTo(0);
            assertThat(stats.getUnmatchedCount()).isEqualTo(0);
            assertThat(stats.getErrorCount()).isEqualTo(0);
            assertThat(stats.getTotalProcessed()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("emptyThbwikiList")
    class EmptyThbwikiList {

        @Test
        @DisplayName("empty thbwiki list causes all unmatched")
        void emptyThbwikiList_allUnmatched() {
            // Arrange: tracks exist but thbwiki list is empty
            TrackBean track1 = createTrack("Satori Maiden", 1L);
            TrackBean track2 = createTrack("Lost Place", 2L);
            TrackBean track3 = createTrack("U.N. Owen", 3L);
            List<TrackBean> tracks = createTrackList(track1, track2, track3);

            List<ThbwikiTrack> thbwikiTracks = Collections.emptyList();

            when(thbwikiService.matchAndSave(any(TrackBean.class), any())).thenReturn(false);

            // Act
            BatchStatistics stats = trackBatchService.batchMatchAndSave(tracks, thbwikiTracks);

            // Assert
            assertThat(stats.getMatchedCount()).isEqualTo(0);
            assertThat(stats.getUnmatchedCount()).isEqualTo(3);
            assertThat(stats.getErrorCount()).isEqualTo(0);
            assertThat(stats.getTotalProcessed()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("nullTrackInList")
    class NullTrackInList {

        @Test
        @DisplayName("null track in list increments error count")
        void nullTrack_incrementsErrorCount() {
            // Arrange
            TrackBean track1 = createTrack("Valid Track", 1L);
            TrackBean nullTrack = null;
            TrackBean track2 = createTrack("Another Valid", 2L);
            List<TrackBean> tracks = new ArrayList<>();
            tracks.add(track1);
            tracks.add(nullTrack);
            tracks.add(track2);

            List<ThbwikiTrack> thbwikiTracks = List.of(
                    createThbwikiTrack("Valid Track", "出处", "曲名")
            );

            when(thbwikiService.matchAndSave(eq(track1), any())).thenReturn(true);
            when(thbwikiService.matchAndSave(eq(track2), any())).thenReturn(true);

            // Act
            BatchStatistics stats = trackBatchService.batchMatchAndSave(tracks, thbwikiTracks);

            // Assert
            assertThat(stats.getMatchedCount()).isEqualTo(2);
            assertThat(stats.getUnmatchedCount()).isEqualTo(0);
            assertThat(stats.getErrorCount()).isEqualTo(1); // null track counted as error
            assertThat(stats.getTotalProcessed()).isEqualTo(3);

            // Verify valid tracks were still processed
            verify(thbwikiService).matchAndSave(eq(track1), any());
            verify(thbwikiService).matchAndSave(eq(track2), any());
        }
    }

    @Nested
    @DisplayName("nullThbwikiList")
    class NullThbwikiList {

        @Test
        @DisplayName("null thbwiki list treated as empty")
        void nullThbwikiList_treatedAsEmpty() {
            // Arrange
            TrackBean track1 = createTrack("Track One", 1L);
            TrackBean track2 = createTrack("Track Two", 2L);
            List<TrackBean> tracks = createTrackList(track1, track2);

            // Act
            BatchStatistics stats = trackBatchService.batchMatchAndSave(tracks, null);

            // Assert
            assertThat(stats.getMatchedCount()).isEqualTo(0);
            assertThat(stats.getUnmatchedCount()).isEqualTo(2);
            assertThat(stats.getErrorCount()).isEqualTo(0);
            assertThat(stats.getTotalProcessed()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("nullTrackName")
    class NullTrackName {

        @Test
        @DisplayName("track with null name handled gracefully")
        void nullTrackName_handledGracefully() {
            // Arrange
            TrackBean trackWithNullName = new TrackBean();
            trackWithNullName.setId(1L);
            trackWithNullName.setName(null);

            TrackBean normalTrack = createTrack("Normal Track", 2L);
            List<TrackBean> tracks = createTrackList(trackWithNullName, normalTrack);

            List<ThbwikiTrack> thbwikiTracks = List.of(
                    createThbwikiTrack("Normal Track", "出处", "曲名")
            );

            when(thbwikiService.matchAndSave(eq(normalTrack), any())).thenReturn(true);

            // Act - should not throw
            BatchStatistics stats = trackBatchService.batchMatchAndSave(tracks, thbwikiTracks);

            // Assert
            assertThat(stats.getTotalProcessed()).isEqualTo(2);
            // matchAndSave is called even for null-named track (ThbwikiService decides how to handle)
            verify(thbwikiService, times(2)).matchAndSave(any(TrackBean.class), any());
        }
    }

    @Nested
    @DisplayName("statisticsAccurate")
    class StatisticsAccurate {

        @Test
        @DisplayName("5 tracks with mixed results verifies all counts exact")
        void mixedResults_allCountsExact() {
            // Arrange: 5 tracks with different outcomes
            TrackBean track1 = createTrack("Match 1", 1L);
            TrackBean track2 = createTrack("Match 2", 2L);
            TrackBean track3 = createTrack("No Match 1", 3L);
            TrackBean track4 = createTrack("No Match 2", 4L);
            TrackBean track5 = createTrack("Match 3", 5L);
            List<TrackBean> tracks = createTrackList(track1, track2, track3, track4, track5);

            List<ThbwikiTrack> thbwikiTracks = List.of(
                    createThbwikiTrack("Match 1", "出处1", "曲名1")
            );

            when(thbwikiService.matchAndSave(eq(track1), any())).thenReturn(true);
            when(thbwikiService.matchAndSave(eq(track2), any())).thenReturn(true);
            when(thbwikiService.matchAndSave(eq(track3), any())).thenReturn(false);
            when(thbwikiService.matchAndSave(eq(track4), any())).thenReturn(false);
            when(thbwikiService.matchAndSave(eq(track5), any())).thenReturn(true);

            // Act
            BatchStatistics stats = trackBatchService.batchMatchAndSave(tracks, thbwikiTracks);

            // Assert - exact counts
            assertThat(stats.getMatchedCount()).isEqualTo(3);
            assertThat(stats.getUnmatchedCount()).isEqualTo(2);
            assertThat(stats.getErrorCount()).isEqualTo(0);
            assertThat(stats.getTotalProcessed()).isEqualTo(5);
        }
    }

    @Nested
    @DisplayName("trackOriginalPreservedOnNoMatch")
    class TrackOriginalPreservedOnNoMatch {

        @Test
        @DisplayName("unmatched track.original stays null after batch")
        void unmatchedTrack_originalStaysNull() {
            // Arrange
            TrackBean track = createTrack("No Match Track", 1L);
            track.setOriginal(null);

            List<TrackBean> tracks = createTrackList(track);
            List<ThbwikiTrack> thbwikiTracks = List.of(
                    createThbwikiTrack("Different Track", "出典", "曲名")
            );

            when(thbwikiService.matchAndSave(any(TrackBean.class), any())).thenReturn(false);

            // Act
            BatchStatistics stats = trackBatchService.batchMatchAndSave(tracks, thbwikiTracks);

            // Assert
            assertThat(stats.getUnmatchedCount()).isEqualTo(1);
            assertThat(track.getOriginal()).isNull(); // Original unchanged
        }
    }

    @Nested
    @DisplayName("trackOriginalUpdatedOnMatch")
    class TrackOriginalUpdatedOnMatch {

        @Test
        @DisplayName("matched track.original updated correctly in-place")
        void matchedTrack_originalUpdatedInPlace() {
            // Arrange
            TrackBean track = createTrack("Satori Maiden", 1L);
            track.setOriginal(null);

            ThbwikiTrack thbwikiTrack = createThbwikiTrack("Satori Maiden", "少女さとり", "3rd eye");
            List<ThbwikiTrack> thbwikiTracks = List.of(thbwikiTrack);

            // ThbwikiService.matchAndSave updates track.original directly
            when(thbwikiService.matchAndSave(any(TrackBean.class), any())).thenAnswer(invocation -> {
                TrackBean t = invocation.getArgument(0);
                t.setOriginal("少女さとり - 3rd eye");
                return true;
            });

            List<TrackBean> trackList = createTrackList(track);

            // Act
            BatchStatistics stats = trackBatchService.batchMatchAndSave(trackList, thbwikiTracks);

            // Assert
            assertThat(stats.getMatchedCount()).isEqualTo(1);
            assertThat(track.getOriginal()).isEqualTo("少女さとり - 3rd eye");
        }
    }

    @Nested
    @DisplayName("batchMatchAndSaveWithProgress_callbackCalled")
    class BatchMatchAndSaveWithProgressCallbackCalled {

        @Test
        @DisplayName("callback invoked for every track with correct arguments")
        void callbackInvoked_correctArgs() {
            // Arrange
            TrackBean track1 = createTrack("Match 1", 1L);
            TrackBean track2 = createTrack("No Match", 2L);
            TrackBean track3 = createTrack("Match 2", 3L);
            List<TrackBean> tracks = createTrackList(track1, track2, track3);

            List<ThbwikiTrack> thbwikiTracks = List.of(
                    createThbwikiTrack("Match 1", "出处1", "曲名1")
            );

            TrackBatchCallback callback = mock(TrackBatchCallback.class);

            when(thbwikiService.matchAndSave(eq(track1), any())).thenReturn(true);
            when(thbwikiService.matchAndSave(eq(track2), any())).thenReturn(false);
            when(thbwikiService.matchAndSave(eq(track3), any())).thenReturn(true);

            // Act
            BatchStatistics stats = trackBatchService.batchMatchAndSaveWithProgress(
                    tracks, thbwikiTracks, callback);

            // Assert - verify in order
            var inOrder = inOrder(callback);
            inOrder.verify(callback).onProgress(1, 3, track1, true);
            inOrder.verify(callback).onProgress(2, 3, track2, false);
            inOrder.verify(callback).onProgress(3, 3, track3, true);
            inOrder.verifyNoMoreInteractions();
        }

        @Test
        @DisplayName("null callback does not cause NPE, batch still works")
        void nullCallback_worksFine() {
            // Arrange
            TrackBean track = createTrack("Test Track", 1L);
            List<TrackBean> tracks = createTrackList(track);
            List<ThbwikiTrack> thbwikiTracks = List.of(
                    createThbwikiTrack("Test Track", "出典", "曲名")
            );

            when(thbwikiService.matchAndSave(any(TrackBean.class), any())).thenReturn(true);

            // Act - null callback should not throw
            BatchStatistics stats = trackBatchService.batchMatchAndSaveWithProgress(
                    tracks, thbwikiTracks, null);

            // Assert
            assertThat(stats.getMatchedCount()).isEqualTo(1);
            verify(thbwikiService).matchAndSave(eq(track), any());
        }
    }

    @Nested
    @DisplayName("exception handling")
    class ExceptionHandling {

        @Test
        @DisplayName("exception during matchAndSave increments error count and adds track ID to failed list")
        void exception_incrementsErrorCountAndAddsToFailedList() {
            // Arrange
            TrackBean goodTrack = createTrack("Good Track", 1L);
            TrackBean failingTrack = createTrack("Failing Track", 2L);
            TrackBean anotherGood = createTrack("Another Good", 3L);
            List<TrackBean> tracks = createTrackList(goodTrack, failingTrack, anotherGood);

            List<ThbwikiTrack> thbwikiTracks = List.of(
                    createThbwikiTrack("Some Track", "出典", "曲名")
            );

            when(thbwikiService.matchAndSave(eq(goodTrack), any())).thenReturn(true);
            when(thbwikiService.matchAndSave(eq(failingTrack), any()))
                    .thenThrow(new RuntimeException("Simulated error"));
            when(thbwikiService.matchAndSave(eq(anotherGood), any())).thenReturn(true);

            // Act
            BatchStatistics stats = trackBatchService.batchMatchAndSave(tracks, thbwikiTracks);

            // Assert
            // Note: exception increments errorCount but does NOT increment unmatchedCount
            assertThat(stats.getMatchedCount()).isEqualTo(2); // goodTrack + anotherGood
            assertThat(stats.getUnmatchedCount()).isEqualTo(0);
            assertThat(stats.getErrorCount()).isEqualTo(1); // failingTrack threw
            assertThat(stats.getTotalProcessed()).isEqualTo(3);
            assertThat(stats.getFailedTrackIds()).containsExactly(2L);
        }

        @Test
        @DisplayName("exception with null track ID does not add null to failed list")
        void exception_nullTrackId_noNullInFailedList() {
            // Arrange
            TrackBean trackWithNoId = new TrackBean();
            trackWithNoId.setName("No ID Track");
            trackWithNoId.setId(null);

            List<TrackBean> tracks = createTrackList(trackWithNoId);
            List<ThbwikiTrack> thbwikiTracks = List.of(
                    createThbwikiTrack("Track", "出典", "曲名")
            );

            when(thbwikiService.matchAndSave(any(TrackBean.class), any()))
                    .thenThrow(new RuntimeException("Error"));

            // Act
            BatchStatistics stats = trackBatchService.batchMatchAndSave(tracks, thbwikiTracks);

            // Assert
            assertThat(stats.getErrorCount()).isEqualTo(1);
            assertThat(stats.getFailedTrackIds()).isEmpty(); // null ID not added
        }
    }
}
