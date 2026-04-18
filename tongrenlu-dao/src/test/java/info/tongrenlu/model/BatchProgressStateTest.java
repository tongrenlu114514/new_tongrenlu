package info.tongrenlu.model;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BatchProgressState DTO and its AlbumStats inner class.
 */
class BatchProgressStateTest {

    // ---- BatchProgressState tests ----

    @Nested
    class EmptyStateTests {

        @Test
        void empty_shouldReturnZeroCounts() {
            BatchProgressState state = BatchProgressState.empty();

            assertEquals(0, state.getMatchedCount());
            assertEquals(0, state.getUnmatchedCount());
            assertEquals(0, state.getErrorCount());
            assertEquals(0, state.getTotalProcessed());
            assertEquals(0, state.getCurrentAlbumIndex());
            assertEquals(0, state.getTotalAlbums());
            assertEquals(0, state.getCompletedAlbums());
            assertEquals(0, state.getFailedAlbums());
            assertNull(state.getJobId());
            assertNull(state.getCurrentAlbumName());
            assertNull(state.getStartTime());
            assertTrue(state.getAlbumStats().isEmpty());
            assertTrue(state.getFailedTrackIds().isEmpty());
        }

        @Test
        void empty_progressPercent_shouldReturnZero() {
            BatchProgressState state = BatchProgressState.empty();
            assertEquals(0, state.getProgressPercent());
        }

        @Test
        void empty_overallAggregations_shouldReturnZero() {
            BatchProgressState state = BatchProgressState.empty();
            assertEquals(0, state.getOverallMatchedCount());
            assertEquals(0, state.getOverallUnmatchedCount());
            assertEquals(0, state.getOverallErrorCount());
            assertEquals(0, state.getOverallTotalProcessed());
        }

        @Test
        void empty_toBatchStatistics_shouldReturnZeros() {
            BatchProgressState state = BatchProgressState.empty();
            BatchStatistics bs = state.toBatchStatistics();

            assertEquals(0, bs.getMatchedCount());
            assertEquals(0, bs.getUnmatchedCount());
            assertEquals(0, bs.getErrorCount());
            assertEquals(0, bs.getTotalProcessed());
            assertTrue(bs.getFailedTrackIds().isEmpty());
        }
    }

    @Nested
    class JobIdTests {

        @Test
        void builder_shouldAcceptJobId() {
            BatchProgressState state = BatchProgressState.builder()
                    .jobId("job-123")
                    .build();

            assertEquals("job-123", state.getJobId());
        }
    }

    @Nested
    class AlbumIndexingTests {

        @Test
        void currentAlbumIndex_shouldReflectZeroBasedPosition() {
            BatchProgressState state = BatchProgressState.builder()
                    .currentAlbumIndex(3)
                    .totalAlbums(10)
                    .build();

            assertEquals(3, state.getCurrentAlbumIndex());
            assertEquals(10, state.getTotalAlbums());
        }

        @Test
        void progressPercent_shouldCalculateCorrectly() {
            BatchProgressState state = BatchProgressState.builder()
                    .totalAlbums(10)
                    .completedAlbums(5)
                    .build();

            assertEquals(50, state.getProgressPercent());
        }

        @Test
        void progressPercent_shouldCapAt100() {
            BatchProgressState state = BatchProgressState.builder()
                    .totalAlbums(5)
                    .completedAlbums(6)
                    .build();

            assertEquals(100, state.getProgressPercent());
        }

        @Test
        void progressPercent_shouldReturnZeroWhenNoAlbums() {
            BatchProgressState state = BatchProgressState.builder()
                    .totalAlbums(0)
                    .completedAlbums(0)
                    .build();

            assertEquals(0, state.getProgressPercent());
        }
    }

    @Nested
    class PerAlbumStatsTests {

        @Test
        void albumStats_shouldStoreByAlbumId() {
            BatchProgressState.AlbumStats albumStats = BatchProgressState.AlbumStats.builder()
                    .albumId(42L)
                    .albumName("Test Album")
                    .matchedCount(5)
                    .unmatchedCount(2)
                    .errorCount(1)
                    .totalProcessed(8)
                    .failedTrackIds(Arrays.asList(101L, 102L))
                    .build();

            assertEquals(42L, albumStats.getAlbumId());
            assertEquals("Test Album", albumStats.getAlbumName());
            assertEquals(5, albumStats.getMatchedCount());
            assertEquals(2, albumStats.getUnmatchedCount());
            assertEquals(1, albumStats.getErrorCount());
            assertEquals(8, albumStats.getTotalProcessed());
            assertEquals(Arrays.asList(101L, 102L), albumStats.getFailedTrackIds());
        }

        @Test
        void albumStats_shouldUseDefaultEmptyList() {
            BatchProgressState.AlbumStats albumStats = new BatchProgressState.AlbumStats();
            assertNotNull(albumStats.getFailedTrackIds());
            assertTrue(albumStats.getFailedTrackIds().isEmpty());
        }

        @Test
        void albumStats_toBatchStatistics_shouldConvertCorrectly() {
            List<Long> failedIds = Arrays.asList(201L, 202L);
            BatchProgressState.AlbumStats albumStats = BatchProgressState.AlbumStats.builder()
                    .albumId(99L)
                    .albumName("Converted Album")
                    .matchedCount(10)
                    .unmatchedCount(3)
                    .errorCount(2)
                    .totalProcessed(15)
                    .failedTrackIds(new ArrayList<>(failedIds))
                    .build();

            BatchStatistics bs = albumStats.toBatchStatistics();

            assertEquals(10, bs.getMatchedCount());
            assertEquals(3, bs.getUnmatchedCount());
            assertEquals(2, bs.getErrorCount());
            assertEquals(15, bs.getTotalProcessed());
            assertEquals(2, bs.getFailedTrackIds().size());
            assertTrue(bs.getFailedTrackIds().contains(201L));
            assertTrue(bs.getFailedTrackIds().contains(202L));
        }

        @Test
        void albumStats_toBatchStatistics_shouldReturnImmutableList() {
            List<Long> mutableList = new ArrayList<>(Arrays.asList(300L));
            BatchProgressState.AlbumStats albumStats = BatchProgressState.AlbumStats.builder()
                    .albumId(1L)
                    .failedTrackIds(mutableList)
                    .build();

            BatchStatistics bs = albumStats.toBatchStatistics();
            assertThrows(UnsupportedOperationException.class, () ->
                    bs.getFailedTrackIds().add(999L));
        }
    }

    @Nested
    class OverallStatsAggregationTests {

        private BatchProgressState.AlbumStats makeAlbumStats(Long id, String name,
                                                              int matched, int unmatched,
                                                              int error, int total,
                                                              List<Long> failedIds) {
            return BatchProgressState.AlbumStats.builder()
                    .albumId(id)
                    .albumName(name)
                    .matchedCount(matched)
                    .unmatchedCount(unmatched)
                    .errorCount(error)
                    .totalProcessed(total)
                    .failedTrackIds(new ArrayList<>(failedIds))
                    .build();
        }

        @Test
        void overallMatchedCount_shouldSumAcrossAlbums() {
            Map<Long, BatchProgressState.AlbumStats> stats = new ConcurrentHashMap<>();
            stats.put(1L, makeAlbumStats(1L, "Album A", 5, 0, 0, 5, Collections.emptyList()));
            stats.put(2L, makeAlbumStats(2L, "Album B", 10, 0, 0, 10, Collections.emptyList()));
            stats.put(3L, makeAlbumStats(3L, "Album C", 3, 0, 0, 3, Collections.emptyList()));

            BatchProgressState state = BatchProgressState.builder()
                    .albumStats(stats)
                    .build();

            assertEquals(18, state.getOverallMatchedCount());
            assertEquals(0, state.getOverallUnmatchedCount());
            assertEquals(0, state.getOverallErrorCount());
            assertEquals(18, state.getOverallTotalProcessed());
        }

        @Test
        void overallUnmatchedCount_shouldSumAcrossAlbums() {
            Map<Long, BatchProgressState.AlbumStats> stats = new ConcurrentHashMap<>();
            stats.put(1L, makeAlbumStats(1L, "Album A", 0, 2, 0, 2, Collections.emptyList()));
            stats.put(2L, makeAlbumStats(2L, "Album B", 0, 7, 0, 7, Collections.emptyList()));

            BatchProgressState state = BatchProgressState.builder()
                    .albumStats(stats)
                    .build();

            assertEquals(9, state.getOverallUnmatchedCount());
        }

        @Test
        void overallErrorCount_shouldSumAcrossAlbums() {
            Map<Long, BatchProgressState.AlbumStats> stats = new ConcurrentHashMap<>();
            stats.put(1L, makeAlbumStats(1L, "Album A", 0, 0, 1, 1, Arrays.asList(1L)));
            stats.put(2L, makeAlbumStats(2L, "Album B", 0, 0, 3, 3, Arrays.asList(2L, 3L, 4L)));

            BatchProgressState state = BatchProgressState.builder()
                    .albumStats(stats)
                    .build();

            assertEquals(4, state.getOverallErrorCount());
            assertEquals(4, state.getOverallTotalProcessed());
        }

        @Test
        void overallAggregations_shouldReturnZeroWhenAlbumStatsIsEmpty() {
            BatchProgressState state = BatchProgressState.builder()
                    .albumStats(new ConcurrentHashMap<>())
                    .build();

            assertEquals(0, state.getOverallMatchedCount());
            assertEquals(0, state.getOverallUnmatchedCount());
            assertEquals(0, state.getOverallErrorCount());
            assertEquals(0, state.getOverallTotalProcessed());
        }

        @Test
        void recomputeBatchStatistics_shouldSyncInheritedFields() {
            Map<Long, BatchProgressState.AlbumStats> stats = new ConcurrentHashMap<>();
            stats.put(10L, makeAlbumStats(10L, "Album X", 4, 1, 2, 7, Arrays.asList(50L, 51L)));
            stats.put(20L, makeAlbumStats(20L, "Album Y", 6, 2, 0, 8, Collections.emptyList()));

            BatchProgressState state = BatchProgressState.builder()
                    .albumStats(stats)
                    .build();

            state.recomputeBatchStatistics();

            assertEquals(10, state.getMatchedCount());
            assertEquals(3, state.getUnmatchedCount());
            assertEquals(2, state.getErrorCount());
            assertEquals(15, state.getTotalProcessed());
            assertEquals(2, state.getFailedTrackIds().size());
            assertTrue(state.getFailedTrackIds().contains(50L));
            assertTrue(state.getFailedTrackIds().contains(51L));
        }

        @Test
        void toBatchStatistics_shouldReturnAggregatedStats() {
            Map<Long, BatchProgressState.AlbumStats> stats = new ConcurrentHashMap<>();
            stats.put(10L, makeAlbumStats(10L, "Album X", 4, 1, 2, 7, Arrays.asList(50L, 51L)));
            stats.put(20L, makeAlbumStats(20L, "Album Y", 6, 2, 0, 8, Arrays.asList(60L)));

            BatchProgressState state = BatchProgressState.builder()
                    .albumStats(stats)
                    .build();

            BatchStatistics bs = state.toBatchStatistics();

            assertEquals(10, bs.getMatchedCount());
            assertEquals(3, bs.getUnmatchedCount());
            assertEquals(2, bs.getErrorCount());
            assertEquals(15, bs.getTotalProcessed());
            assertEquals(3, bs.getFailedTrackIds().size());
        }

        @Test
        void failedTrackIds_shouldBeImmutableOnToBatchStatistics() {
            Map<Long, BatchProgressState.AlbumStats> stats = new ConcurrentHashMap<>();
            stats.put(1L, makeAlbumStats(1L, "A1", 0, 0, 1, 1, Arrays.asList(100L)));

            BatchProgressState state = BatchProgressState.builder()
                    .albumStats(stats)
                    .build();

            BatchStatistics bs = state.toBatchStatistics();
            assertThrows(UnsupportedOperationException.class, () ->
                    bs.getFailedTrackIds().add(999L));
        }
    }

    @Nested
    class FromAlbumStatsTests {

        @Test
        void fromAlbumStats_shouldPopulateAlbumStatsMap() {
            List<BatchProgressState.AlbumStats> list = Arrays.asList(
                    BatchProgressState.AlbumStats.builder()
                            .albumId(1L).albumName("First").matchedCount(3).build(),
                    BatchProgressState.AlbumStats.builder()
                            .albumId(2L).albumName("Second").matchedCount(5).build()
            );

            BatchProgressState state = BatchProgressState.fromAlbumStats(list);

            assertEquals(2, state.getAlbumStats().size());
            assertEquals("First", state.getAlbumStats().get(1L).getAlbumName());
            assertEquals("Second", state.getAlbumStats().get(2L).getAlbumName());
            assertEquals(3, state.getAlbumStats().get(1L).getMatchedCount());
            assertEquals(5, state.getAlbumStats().get(2L).getMatchedCount());
        }

        @Test
        void fromAlbumStats_shouldHandleNullList() {
            BatchProgressState state = BatchProgressState.fromAlbumStats(null);
            assertNotNull(state.getAlbumStats());
            assertTrue(state.getAlbumStats().isEmpty());
        }

        @Test
        void fromAlbumStats_shouldHandleEmptyList() {
            BatchProgressState state = BatchProgressState.fromAlbumStats(Collections.emptyList());
            assertTrue(state.getAlbumStats().isEmpty());
        }

        @Test
        void fromAlbumStats_shouldOverwriteDuplicateAlbumIds() {
            List<BatchProgressState.AlbumStats> list = Arrays.asList(
                    BatchProgressState.AlbumStats.builder()
                            .albumId(5L).albumName("First").matchedCount(3).build(),
                    BatchProgressState.AlbumStats.builder()
                            .albumId(5L).albumName("Second").matchedCount(7).build()
            );

            BatchProgressState state = BatchProgressState.fromAlbumStats(list);

            // Later entry wins
            assertEquals(1, state.getAlbumStats().size());
            assertEquals("Second", state.getAlbumStats().get(5L).getAlbumName());
            assertEquals(7, state.getAlbumStats().get(5L).getMatchedCount());
        }
    }

    @Nested
    class StartTimeAndJobMetadataTests {

        @Test
        void builder_shouldAcceptStartTime() {
            Instant now = Instant.now();
            BatchProgressState state = BatchProgressState.builder()
                    .jobId("batch-job-001")
                    .startTime(now)
                    .totalAlbums(20)
                    .currentAlbumIndex(5)
                    .completedAlbums(5)
                    .failedAlbums(1)
                    .build();

            assertEquals("batch-job-001", state.getJobId());
            assertEquals(now, state.getStartTime());
            assertEquals(20, state.getTotalAlbums());
            assertEquals(5, state.getCurrentAlbumIndex());
            assertEquals(5, state.getCompletedAlbums());
            assertEquals(1, state.getFailedAlbums());
        }
    }
}
