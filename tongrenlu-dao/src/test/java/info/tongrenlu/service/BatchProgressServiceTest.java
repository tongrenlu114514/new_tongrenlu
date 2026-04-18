package info.tongrenlu.service;

import info.tongrenlu.callback.TrackBatchCallback;
import info.tongrenlu.domain.AlbumDetailBean;
import info.tongrenlu.domain.TrackBean;
import info.tongrenlu.model.BatchProgressState;
import info.tongrenlu.model.BatchProgressState.AlbumStats;
import info.tongrenlu.model.BatchStatistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for BatchProgressService.
 */
class BatchProgressServiceTest {

    private BatchProgressService service;

    @BeforeEach
    void setUp() {
        service = new BatchProgressService();
    }

    @AfterEach
    void tearDown() {
        // Service is stateless between tests due to fresh instance per test
    }

    // ---- startBatchJob tests ----

    @Nested
    class StartBatchJobTests {

        @Test
        void startBatchJob_returnsValidUuid() {
            String jobId = service.startBatchJob(Collections.emptyList());

            assertNotNull(jobId);
            assertFalse(jobId.isEmpty());
            // UUID format: 8-4-4-4-12 = 36 chars
            assertEquals(36, jobId.length());
            assertTrue(jobId.contains("-"));
        }

        @Test
        void startBatchJob_initializesStateCorrectly() {
            List<AlbumDetailBean> albums = createAlbums(3);

            String jobId = service.startBatchJob(albums);
            BatchProgressState state = service.getProgress(jobId);

            assertNotNull(state);
            assertEquals(jobId, state.getJobId());
            assertEquals(3, state.getTotalAlbums());
            assertEquals(0, state.getCompletedAlbums());
            assertEquals(0, state.getFailedAlbums());
            assertEquals(0, state.getCurrentAlbumIndex());
            assertNull(state.getCurrentAlbumName());
            assertNotNull(state.getStartTime());
            assertNotNull(state.getAlbumStats());
            assertTrue(state.getAlbumStats().isEmpty());
        }

        @Test
        void startBatchJob_withNullList_handlesGracefully() {
            String jobId = service.startBatchJob(null);
            BatchProgressState state = service.getProgress(jobId);

            assertNotNull(state);
            assertEquals(0, state.getTotalAlbums());
        }

        @Test
        void startBatchJob_withEmptyList_handlesGracefully() {
            String jobId = service.startBatchJob(Collections.emptyList());
            BatchProgressState state = service.getProgress(jobId);

            assertNotNull(state);
            assertEquals(0, state.getTotalAlbums());
        }

        @Test
        void startBatchJob_createsUniqueJobIds() {
            String jobId1 = service.startBatchJob(Collections.emptyList());
            String jobId2 = service.startBatchJob(Collections.emptyList());

            assertNotEquals(jobId1, jobId2);
        }
    }

    // ---- onAlbumStart tests ----

    @Nested
    class OnAlbumStartTests {

        @Test
        void onAlbumStart_updatesCurrentAlbumNameAndIndex() {
            String jobId = service.startBatchJob(createAlbums(3));

            service.onAlbumStart(jobId, 1L, "Album One");

            BatchProgressState state = service.getProgress(jobId);
            assertEquals("Album One", state.getCurrentAlbumName());
            // Index should be 0 initially (before increment on album start)
            assertEquals(0, state.getCurrentAlbumIndex());
        }

        @Test
        void onAlbumStart_unknownJobId_handledGracefully() {
            // Should not throw
            assertDoesNotThrow(() ->
                service.onAlbumStart("unknown-job-id", 1L, "Test Album")
            );
        }

        @Test
        void onAlbumStart_logsAtInfoLevel() {
            String jobId = service.startBatchJob(Collections.emptyList());

            // Just verify it completes without error
            service.onAlbumStart(jobId, 1L, "Test Album");

            BatchProgressState state = service.getProgress(jobId);
            assertEquals("Test Album", state.getCurrentAlbumName());
        }
    }

    // ---- onAlbumComplete tests ----

    @Nested
    class OnAlbumCompleteTests {

        @Test
        void onAlbumComplete_accumulatesAlbumStatsCorrectly() {
            String jobId = service.startBatchJob(createAlbums(2));

            service.onAlbumStart(jobId, 1L, "Album One");
            BatchStatistics stats1 = BatchStatistics.builder()
                    .matchedCount(5)
                    .unmatchedCount(2)
                    .errorCount(1)
                    .totalProcessed(8)
                    .failedTrackIds(Arrays.asList(101L, 102L))
                    .build();
            service.onAlbumComplete(jobId, 1L, stats1);

            BatchProgressState state = service.getProgress(jobId);
            assertEquals(1, state.getCompletedAlbums());
            assertEquals(1, state.getFailedAlbums()); // errorCount > 0
            assertEquals(1, state.getAlbumStats().size());

            AlbumStats albumStats = state.getAlbumStats().get(1L);
            assertNotNull(albumStats);
            assertEquals(5, albumStats.getMatchedCount());
            assertEquals(2, albumStats.getUnmatchedCount());
            assertEquals(1, albumStats.getErrorCount());
            assertEquals(8, albumStats.getTotalProcessed());
        }

        @Test
        void onAlbumComplete_incrementsFailedAlbumsWhenErrors() {
            String jobId = service.startBatchJob(createAlbums(1));

            service.onAlbumStart(jobId, 1L, "Album One");
            BatchStatistics statsWithErrors = BatchStatistics.builder()
                    .matchedCount(5)
                    .unmatchedCount(0)
                    .errorCount(2)
                    .totalProcessed(7)
                    .failedTrackIds(Arrays.asList(101L))
                    .build();
            service.onAlbumComplete(jobId, 1L, statsWithErrors);

            BatchProgressState state = service.getProgress(jobId);
            assertEquals(1, state.getFailedAlbums());
        }

        @Test
        void onAlbumComplete_doesNotIncrementFailedAlbumsWhenNoErrors() {
            String jobId = service.startBatchJob(createAlbums(1));

            service.onAlbumStart(jobId, 1L, "Album One");
            BatchStatistics statsNoErrors = BatchStatistics.builder()
                    .matchedCount(10)
                    .unmatchedCount(0)
                    .errorCount(0)
                    .totalProcessed(10)
                    .failedTrackIds(Collections.emptyList())
                    .build();
            service.onAlbumComplete(jobId, 1L, statsNoErrors);

            BatchProgressState state = service.getProgress(jobId);
            assertEquals(0, state.getFailedAlbums());
            assertEquals(1, state.getCompletedAlbums());
        }

        @Test
        void onAlbumComplete_overallStatsAggregationMatchesSumOfPerAlbum() {
            String jobId = service.startBatchJob(createAlbums(3));

            // Album 1
            service.onAlbumStart(jobId, 1L, "Album One");
            service.onAlbumComplete(jobId, 1L, BatchStatistics.builder()
                    .matchedCount(5)
                    .unmatchedCount(2)
                    .errorCount(1)
                    .totalProcessed(8)
                    .failedTrackIds(Arrays.asList(101L))
                    .build());

            // Album 2
            service.onAlbumStart(jobId, 2L, "Album Two");
            service.onAlbumComplete(jobId, 2L, BatchStatistics.builder()
                    .matchedCount(10)
                    .unmatchedCount(3)
                    .errorCount(0)
                    .totalProcessed(13)
                    .failedTrackIds(Collections.emptyList())
                    .build());

            // Album 3
            service.onAlbumStart(jobId, 3L, "Album Three");
            service.onAlbumComplete(jobId, 3L, BatchStatistics.builder()
                    .matchedCount(7)
                    .unmatchedCount(1)
                    .errorCount(2)
                    .totalProcessed(10)
                    .failedTrackIds(Arrays.asList(301L, 302L))
                    .build());

            BatchProgressState state = service.getProgress(jobId);

            // Verify per-album stats
            assertEquals(3, state.getAlbumStats().size());

            // Verify aggregated counts
            assertEquals(5 + 10 + 7, state.getMatchedCount());
            assertEquals(2 + 3 + 1, state.getUnmatchedCount());
            assertEquals(1 + 0 + 2, state.getErrorCount());
            assertEquals(8 + 13 + 10, state.getTotalProcessed());
            assertEquals(3, state.getCompletedAlbums());
            assertEquals(2, state.getFailedAlbums()); // Album 1 and 3 have errors

            // Verify failed track IDs
            assertEquals(3, state.getFailedTrackIds().size());
            assertTrue(state.getFailedTrackIds().contains(101L));
            assertTrue(state.getFailedTrackIds().contains(301L));
            assertTrue(state.getFailedTrackIds().contains(302L));
        }

        @Test
        void onAlbumComplete_unknownJobId_handledGracefully() {
            // Should not throw
            assertDoesNotThrow(() -> {
                service.onAlbumComplete("unknown-job-id", 1L, BatchStatistics.empty());
            });
        }

        @Test
        void onAlbumComplete_incrementsCurrentAlbumIndex() {
            String jobId = service.startBatchJob(createAlbums(3));

            assertEquals(0, service.getProgress(jobId).getCurrentAlbumIndex());

            service.onAlbumStart(jobId, 1L, "Album 1");
            service.onAlbumComplete(jobId, 1L, BatchStatistics.empty());
            assertEquals(1, service.getProgress(jobId).getCurrentAlbumIndex());

            service.onAlbumStart(jobId, 2L, "Album 2");
            service.onAlbumComplete(jobId, 2L, BatchStatistics.empty());
            assertEquals(2, service.getProgress(jobId).getCurrentAlbumIndex());
        }
    }

    // ---- pause/resume tests ----

    @Nested
    class PauseResumeTests {

        @Test
        void pause_resume_togglesPausedStateCorrectly() {
            String jobId = service.startBatchJob(Collections.emptyList());

            // Initially not paused
            assertFalse(service.isPaused(jobId));

            // Pause
            service.pause(jobId);
            assertTrue(service.isPaused(jobId));

            // Resume
            service.resume(jobId);
            assertFalse(service.isPaused(jobId));
        }

        @Test
        void isPaused_unknownJobId_returnsFalse() {
            assertFalse(service.isPaused("non-existent-job"));
        }

        @Test
        void pause_unknownJobId_handledGracefully() {
            assertDoesNotThrow(() -> service.pause("unknown-job-id"));
        }

        @Test
        void resume_unknownJobId_handledGracefully() {
            assertDoesNotThrow(() -> service.resume("unknown-job-id"));
        }

        @Test
        void pause_canBeCalledMultipleTimes() {
            String jobId = service.startBatchJob(Collections.emptyList());

            service.pause(jobId);
            service.pause(jobId);
            assertTrue(service.isPaused(jobId));

            service.resume(jobId);
            assertFalse(service.isPaused(jobId));
        }
    }

    // ---- getProgress tests ----

    @Nested
    class GetProgressTests {

        @Test
        void getProgress_returnsCurrentStateSnapshot() {
            List<AlbumDetailBean> albums = createAlbums(2);
            String jobId = service.startBatchJob(albums);

            service.onAlbumStart(jobId, 1L, "Album 1");
            BatchProgressState state = service.getProgress(jobId);

            assertNotNull(state);
            assertEquals(jobId, state.getJobId());
            assertEquals(2, state.getTotalAlbums());
            assertEquals("Album 1", state.getCurrentAlbumName());
        }

        @Test
        void getProgress_unknownJobId_returnsNull() {
            BatchProgressState state = service.getProgress("non-existent-job");
            assertNull(state);
        }

        @Test
        void getProgress_returnsStateWithUpdatedStats() {
            String jobId = service.startBatchJob(createAlbums(1));

            service.onAlbumStart(jobId, 1L, "Album 1");
            service.onAlbumComplete(jobId, 1L, BatchStatistics.builder()
                    .matchedCount(5)
                    .unmatchedCount(3)
                    .errorCount(1)
                    .totalProcessed(9)
                    .failedTrackIds(Arrays.asList(1L, 2L))
                    .build());

            BatchProgressState state = service.getProgress(jobId);

            assertEquals(5, state.getMatchedCount());
            assertEquals(3, state.getUnmatchedCount());
            assertEquals(1, state.getErrorCount());
            assertEquals(9, state.getTotalProcessed());
            assertEquals(1, state.getCompletedAlbums());
        }
    }

    // ---- registerCallback tests ----

    @Nested
    class RegisterCallbackTests {

        @Test
        void registerCallback_forwardsTrackProgressToCallback() {
            String jobId = service.startBatchJob(Collections.emptyList());

            TrackBatchCallback callback = mock(TrackBatchCallback.class);
            service.registerCallback(jobId, callback);

            TrackBean track = new TrackBean();
            track.setId(1L);
            track.setName("Test Track");

            service.onTrackProgress(jobId, 1, 10, track, true);

            verify(callback, times(1)).onProgress(1, 10, track, true);
        }

        @Test
        void registerCallback_withNullCallback_doesNotThrow() {
            String jobId = service.startBatchJob(Collections.emptyList());

            assertDoesNotThrow(() -> service.registerCallback(jobId, null));

            // Should not throw when calling onTrackProgress
            TrackBean track = new TrackBean();
            assertDoesNotThrow(() -> service.onTrackProgress(jobId, 1, 10, track, true));
        }

        @Test
        void onTrackProgress_withoutCallback_doesNotThrow() {
            String jobId = service.startBatchJob(Collections.emptyList());

            TrackBean track = new TrackBean();
            assertDoesNotThrow(() ->
                service.onTrackProgress(jobId, 1, 10, track, true)
            );
        }

        @Test
        void onTrackProgress_unknownJobId_doesNotThrow() {
            TrackBean track = new TrackBean();
            assertDoesNotThrow(() ->
                service.onTrackProgress("unknown-job", 1, 10, track, true)
            );
        }

        @Test
        void registerCallback_multipleCallbacks_overwritesPrevious() {
            String jobId = service.startBatchJob(Collections.emptyList());

            TrackBatchCallback callback1 = mock(TrackBatchCallback.class);
            TrackBatchCallback callback2 = mock(TrackBatchCallback.class);

            service.registerCallback(jobId, callback1);
            service.registerCallback(jobId, callback2);

            TrackBean track = new TrackBean();
            service.onTrackProgress(jobId, 1, 10, track, true);

            verify(callback1, never()).onProgress(anyInt(), anyInt(), any(), anyBoolean());
            verify(callback2, times(1)).onProgress(1, 10, track, true);
        }
    }

    // ---- Integration tests ----

    @Nested
    class IntegrationTests {

        @Test
        void fullBatchWorkflow_withMultipleAlbums_tracksProgressCorrectly() {
            List<AlbumDetailBean> albums = createAlbums(3);
            String jobId = service.startBatchJob(albums);

            TrackBatchCallback callback = mock(TrackBatchCallback.class);
            service.registerCallback(jobId, callback);

            // Album 1
            service.onAlbumStart(jobId, 1L, "Album One");
            for (int i = 1; i <= 5; i++) {
                service.onTrackProgress(jobId, i, 5, createTrack((long) i), true);
            }
            service.onAlbumComplete(jobId, 1L, BatchStatistics.builder()
                    .matchedCount(5)
                    .unmatchedCount(0)
                    .errorCount(0)
                    .totalProcessed(5)
                    .build());

            // Album 2 (with errors)
            service.onAlbumStart(jobId, 2L, "Album Two");
            for (int i = 6; i <= 10; i++) {
                service.onTrackProgress(jobId, i - 5, 5, createTrack((long) i), i < 9);
            }
            service.onAlbumComplete(jobId, 2L, BatchStatistics.builder()
                    .matchedCount(3)
                    .unmatchedCount(1)
                    .errorCount(1)
                    .totalProcessed(5)
                    .failedTrackIds(Arrays.asList(9L))
                    .build());

            // Album 3
            service.onAlbumStart(jobId, 3L, "Album Three");
            for (int i = 11; i <= 15; i++) {
                service.onTrackProgress(jobId, i - 10, 5, createTrack((long) i), true);
            }
            service.onAlbumComplete(jobId, 3L, BatchStatistics.builder()
                    .matchedCount(5)
                    .unmatchedCount(0)
                    .errorCount(0)
                    .totalProcessed(5)
                    .build());

            // Verify final state
            BatchProgressState state = service.getProgress(jobId);
            assertNotNull(state);
            assertEquals(3, state.getCompletedAlbums());
            assertEquals(1, state.getFailedAlbums());
            assertEquals(13, state.getMatchedCount());
            assertEquals(1, state.getUnmatchedCount());
            assertEquals(1, state.getErrorCount());
            assertEquals(15, state.getTotalProcessed());
            assertEquals(3, state.getAlbumStats().size());
            assertTrue(state.getFailedTrackIds().contains(9L));

            // Verify callback was called for all tracks
            verify(callback, times(15)).onProgress(anyInt(), anyInt(), any(), anyBoolean());
        }
    }

    // ---- Helper methods ----

    private List<AlbumDetailBean> createAlbums(int count) {
        List<AlbumDetailBean> albums = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            AlbumDetailBean album = new AlbumDetailBean();
            album.setId((long) i);
            album.setTitle("Album " + i);
            albums.add(album);
        }
        return albums;
    }

    private TrackBean createTrack(Long id) {
        TrackBean track = new TrackBean();
        track.setId(id);
        track.setName("Track " + id);
        return track;
    }
}
