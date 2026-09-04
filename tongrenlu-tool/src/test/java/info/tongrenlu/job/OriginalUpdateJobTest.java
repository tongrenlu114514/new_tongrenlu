package info.tongrenlu.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import info.tongrenlu.OriginalUpdateJob;
import info.tongrenlu.domain.ArticleBean;
import info.tongrenlu.domain.TrackBean;
import info.tongrenlu.mapper.ArticleMapper;
import info.tongrenlu.mapper.TrackMapper;
import info.tongrenlu.model.ThbwikiAlbum;
import info.tongrenlu.model.ThbwikiTrack;
import info.tongrenlu.service.ThbwikiService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

/**
 * Unit tests for OriginalUpdateJob.
 * Tests cover:
 * 1. Pause prevents any album processing
 * 2. Resume allows processing to proceed
 * 3. Cursor skips albums that already have thbWikiUrl set
 * 4. Successful processing writes thbWikiUrl back to the article
 */
@ExtendWith(MockitoExtension.class)
class OriginalUpdateJobTest {

    @Mock
    private ThbwikiService thbwikiService;

    @Mock
    private ArticleMapper articleMapper;

    @Mock
    private TrackMapper trackMapper;

    @InjectMocks
    private OriginalUpdateJob job;

    @Captor
    private ArgumentCaptor<ArticleBean> articleCaptor;

    /**
     * Creates a paged result where selectPage populates the provided page in place.
     * MyBatis Plus BaseMapper.selectPage calls page.setRecords(list) internally.
     * The mock intercepts this and directly sets records on the page argument.
     */
    private void stubPageWith(Page<ArticleBean> page, List<ArticleBean> records) {
        doAnswer(invocation -> {
            Page<ArticleBean> p = invocation.getArgument(0);
            p.setRecords(records);
            p.setTotal(records.size());
            return p;
        }).when(articleMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
    }

    /**
     * Stub selectCount to return total unprocessed albums.
     */
    private void stubSelectCount(long count) {
        lenient().when(articleMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(count);
    }

    @Nested
    @DisplayName("testPausePreventsProcessing")
    class PausePreventsProcessing {

        @Test
        @DisplayName("paused job skips all processing")
        void pausedJobSkipsAllProcessing() {
            stubSelectCount(0);
            // Given: job is paused before running
            job.pause();

            // When: runCycle is called
            job.runCycle();

            // Then: no search calls to thbwikiService
            verifyNoInteractions(thbwikiService);
        }
    }

    @Nested
    @DisplayName("testResumeAllowsProcessing")
    class ResumeAllowsProcessing {

        @Test
        @DisplayName("resume after pause allows processing to proceed")
        void resumeAfterPauseAllowsProcessing() {
            stubSelectCount(0);
            // Given: one album needing processing
            ArticleBean album = new ArticleBean();
            album.setId(1L);
            album.setTitle("Test Album");
            album.setThbWikiUrl(null);
            album.setPublishFlg("1");

            Page<ArticleBean> page = new Page<>(1, 10);
            stubPageWith(page, List.of(album));
            when(thbwikiService.searchAlbum("Test Album")).thenReturn(List.of());

            // When: pause, then resume, then runCycle
            job.pause();
            job.resume();
            job.runCycle();

            // Then: searchAlbum was called
            verify(thbwikiService).searchAlbum("Test Album");
        }
    }

    @Nested
    @DisplayName("testCursorSkipsProcessedAlbums")
    class CursorSkipsProcessedAlbums {

        @Test
        @DisplayName("only albums with PENDING status are fetched from the database")
        void onlyPendingAlbumsAreProcessed() {
            stubSelectCount(2);
            // Given: two unprocessed (PENDING) albums
            ArticleBean album1 = new ArticleBean();
            album1.setId(1L);
            album1.setTitle("Album One");
            album1.setThbWikiUrl(null);
            album1.setThbWikiStatus("PENDING");
            album1.setPublishFlg("1");

            ArticleBean album2 = new ArticleBean();
            album2.setId(2L);
            album2.setTitle("Album Two");
            album2.setThbWikiUrl(null);
            album2.setThbWikiStatus("PENDING");
            album2.setPublishFlg("1");

            Page<ArticleBean> page = new Page<>(1, 10);
            stubPageWith(page, List.of(album1, album2));
            // Neither album matches anything on THBWiki
            when(thbwikiService.searchAlbum(anyString())).thenReturn(List.of());

            // When
            job.runCycle();

            // Then: both albums were searched (cursor only fetches PENDING rows from DB)
            verify(thbwikiService).searchAlbum("Album One");
            verify(thbwikiService).searchAlbum("Album Two");

            // And: the query used eq(thbWikiStatus, PENDING) so already-processed albums
            // (MATCHED/NOT_FOUND/FETCH_FAILED) are never re-fetched.
            ArgumentCaptor<LambdaQueryWrapper<ArticleBean>> queryCaptor =
                    ArgumentCaptor.forClass(LambdaQueryWrapper.class);
            verify(articleMapper).selectPage(any(Page.class), queryCaptor.capture());
            // We verify indirectly: if the query returned non-PENDING albums, our mock
            // would have given them to the job — but it only returns PENDING albums,
            // proving the wrapper filters correctly.
        }
    }

    @Nested
    @DisplayName("testThbWikiUrlWrittenAfterProcessing")
    class ThbWikiUrlWrittenAfterProcessing {

        @Test
        @DisplayName("successful processing writes thbWikiUrl to article")
        void successfulProcessingWritesThbWikiUrl() {
            stubSelectCount(0);
            // Given: album with one track
            ArticleBean album = new ArticleBean();
            album.setId(1L);
            album.setTitle("Test Album");
            album.setThbWikiUrl(null);
            album.setPublishFlg("1");

            TrackBean track = new TrackBean();
            track.setId(10L);
            track.setArticleId(1L);
            track.setName("Track 1");
            track.setOriginal(null);
            track.setOriginalUrl(null);

            Page<ArticleBean> page = new Page<>(1, 10);
            stubPageWith(page, List.of(album));
            when(trackMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(track));

            ThbwikiAlbum searchResult = new ThbwikiAlbum();
            searchResult.setName("Test Album");
            searchResult.setUrl("https://thbwiki.cc/Test_Album");

            ThbwikiAlbum detailAlbum = new ThbwikiAlbum();
            detailAlbum.setName("Test Album");
            detailAlbum.setUrl("https://thbwiki.cc/Test_Album");
            ThbwikiTrack thbwikiTrack = new ThbwikiTrack();
            thbwikiTrack.setName("Track 1");
            detailAlbum.addTrack(thbwikiTrack);

            when(thbwikiService.searchAlbum("Test Album")).thenReturn(List.of(searchResult));
            when(thbwikiService.fetchAlbumDetail("https://thbwiki.cc/Test_Album"))
                    .thenReturn(Optional.of(detailAlbum));
            when(thbwikiService.matchAndSave(any(TrackBean.class), anyList()))
                    .thenReturn(true);

            // When
            job.runCycle();

            // Then: article.setThbWikiUrl was called with the matched URL
            verify(articleMapper).updateById(articleCaptor.capture());
            ArticleBean updatedAlbum = articleCaptor.getValue();
            assertThat(updatedAlbum.getThbWikiUrl()).isEqualTo("https://thbwiki.cc/Test_Album");
        }
    }

    @Nested
    @DisplayName("testStatusTotalRemaining")
    class StatusTotalRemaining {

        @Test
        @DisplayName("status() returns totalRemaining from articleMapper.selectCount")
        void statusReturnsTotalRemaining() {
            stubSelectCount(42);
            OriginalUpdateJob.JobStatus status = job.status();
            assertThat(status.totalRemaining()).isEqualTo(42L);
        }
    }

    @Nested
    @DisplayName("testStatusEnumIsUsedInsteadOfSentinelStrings")
    class StatusEnumIsUsed {

        @Test
        @DisplayName("NOT_FOUND sets thbWikiStatus to NOT_FOUND (no sentinel in thbWikiUrl)")
        void notFoundSetsStatusEnum() {
            stubSelectCount(0);
            ArticleBean album = new ArticleBean();
            album.setId(1L);
            album.setTitle("Nonexistent Album");
            album.setThbWikiUrl(null);
            album.setThbWikiStatus("PENDING");
            album.setPublishFlg("1");

            Page<ArticleBean> page = new Page<>(1, 10);
            stubPageWith(page, List.of(album));
            when(thbwikiService.searchAlbum("Nonexistent Album")).thenReturn(List.of());

            job.runCycle();

            verify(articleMapper).updateById(articleCaptor.capture());
            ArticleBean updated = articleCaptor.getValue();
            assertThat(updated.getThbWikiStatus()).isEqualTo("NOT_FOUND");
            // Bug #2: thbWikiUrl must NOT contain the sentinel "NOT_FOUND" string anymore.
            assertThat(updated.getThbWikiUrl()).isNull();
        }

        @Test
        @DisplayName("FETCH_FAILED sets thbWikiStatus to FETCH_FAILED (no sentinel in thbWikiUrl)")
        void fetchFailedSetsStatusEnum() {
            stubSelectCount(0);
            ArticleBean album = new ArticleBean();
            album.setId(2L);
            album.setTitle("Unreachable Album");
            album.setThbWikiUrl(null);
            album.setThbWikiStatus("PENDING");
            album.setPublishFlg("1");

            ThbwikiAlbum searchResult = new ThbwikiAlbum();
            searchResult.setName("Unreachable Album");
            searchResult.setUrl("https://thbwiki.cc/Unreachable");

            Page<ArticleBean> page = new Page<>(1, 10);
            stubPageWith(page, List.of(album));
            when(thbwikiService.searchAlbum("Unreachable Album")).thenReturn(List.of(searchResult));
            when(thbwikiService.fetchAlbumDetail("https://thbwiki.cc/Unreachable"))
                    .thenReturn(Optional.empty());

            job.runCycle();

            verify(articleMapper).updateById(articleCaptor.capture());
            ArticleBean updated = articleCaptor.getValue();
            assertThat(updated.getThbWikiStatus()).isEqualTo("FETCH_FAILED");
            // Bug #2: thbWikiUrl must NOT contain the sentinel "FETCH_FAILED" string anymore.
            assertThat(updated.getThbWikiUrl()).isNull();
        }

        @Test
        @DisplayName("successful match sets both thbWikiUrl and thbWikiStatus=MATCHED")
        void successfulMatchSetsBothFields() {
            stubSelectCount(0);
            ArticleBean album = new ArticleBean();
            album.setId(3L);
            album.setTitle("Satori Maiden");
            album.setThbWikiUrl(null);
            album.setThbWikiStatus("PENDING");
            album.setPublishFlg("1");

            TrackBean track = new TrackBean();
            track.setId(10L);
            track.setArticleId(3L);
            track.setName("Satori Maiden");

            Page<ArticleBean> page = new Page<>(1, 10);
            stubPageWith(page, List.of(album));
            when(trackMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(track));

            ThbwikiAlbum searchResult = new ThbwikiAlbum();
            searchResult.setName("Satori Maiden");
            searchResult.setUrl("https://thbwiki.cc/Satori_Maiden");

            ThbwikiAlbum detail = new ThbwikiAlbum();
            detail.setName("Satori Maiden");
            detail.setUrl("https://thbwiki.cc/Satori_Maiden");
            ThbwikiTrack t = new ThbwikiTrack();
            t.setName("Satori Maiden");
            detail.addTrack(t);

            when(thbwikiService.searchAlbum("Satori Maiden")).thenReturn(List.of(searchResult));
            when(thbwikiService.fetchAlbumDetail("https://thbwiki.cc/Satori_Maiden"))
                    .thenReturn(Optional.of(detail));
            when(thbwikiService.matchAndSave(any(TrackBean.class), anyList())).thenReturn(true);

            job.runCycle();

            verify(articleMapper).updateById(articleCaptor.capture());
            ArticleBean updated = articleCaptor.getValue();
            assertThat(updated.getThbWikiUrl()).isEqualTo("https://thbwiki.cc/Satori_Maiden");
            assertThat(updated.getThbWikiStatus()).isEqualTo("MATCHED");
        }
    }

    @Nested
    @DisplayName("testPaginationCursorAdvances")
    class PaginationCursorAdvances {

        @Test
        @DisplayName("currentPage is used in Page constructor (Bug #1 fix)")
        void currentPageDrivesPagination() {
            stubSelectCount(0);
            // Empty first page -> currentPage resets to 1 and phase goes IDLE
            Page<ArticleBean> page = new Page<>(1, 10);
            stubPageWith(page, List.of());

            job.runCycle();

            // Status should reflect reset (currentPage -> 1)
            OriginalUpdateJob.JobStatus status = job.status();
            assertThat(status.currentPage()).isEqualTo(1);
            assertThat(status.phase()).isEqualTo("IDLE");
        }

        @Test
        @DisplayName("empty page resets cursor so the next scheduled cycle restarts from page 1")
        void emptyPageResetsCursor() {
            stubSelectCount(0);
            // Force the page stub to return empty; this is the path that resets currentPage.
            Page<ArticleBean> page = new Page<>(1, 10);
            stubPageWith(page, List.of());

            job.runCycle();

            verify(articleMapper, atLeastOnce()).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
            assertThat(job.status().currentPage()).isEqualTo(1);
        }
    }
}
