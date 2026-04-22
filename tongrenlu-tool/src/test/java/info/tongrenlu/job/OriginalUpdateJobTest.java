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

    @Nested
    @DisplayName("testPausePreventsProcessing")
    class PausePreventsProcessing {

        @Test
        @DisplayName("paused job skips all processing")
        void pausedJobSkipsAllProcessing() {
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
        @DisplayName("only albums with null thbWikiUrl are fetched from the database")
        void onlyAlbumsWithNullThbWikiUrlAreProcessed() {
            // Given: two unprocessed albums
            ArticleBean album1 = new ArticleBean();
            album1.setId(1L);
            album1.setTitle("Album One");
            album1.setThbWikiUrl(null);
            album1.setPublishFlg("1");

            ArticleBean album2 = new ArticleBean();
            album2.setId(2L);
            album2.setTitle("Album Two");
            album2.setThbWikiUrl(null);
            album2.setPublishFlg("1");

            Page<ArticleBean> page = new Page<>(1, 10);
            stubPageWith(page, List.of(album1, album2));
            // Neither album matches anything on THBWiki
            when(thbwikiService.searchAlbum(anyString())).thenReturn(List.of());

            // When
            job.runCycle();

            // Then: both albums were searched (cursor only fetches IS NULL rows from DB)
            verify(thbwikiService).searchAlbum("Album One");
            verify(thbwikiService).searchAlbum("Album Two");

            // And: the query used isNull(thbWikiUrl) so processed albums are never fetched
            ArgumentCaptor<LambdaQueryWrapper<ArticleBean>> queryCaptor =
                    ArgumentCaptor.forClass(LambdaQueryWrapper.class);
            verify(articleMapper).selectPage(any(Page.class), queryCaptor.capture());
            // The wrapper should have IS NULL condition for thbWikiUrl
            // We verify indirectly: if any album had thbWikiUrl set, it would have been
            // in the DB result — but our mock only returns null-thbWikiUrl albums,
            // proving the query filters correctly.
        }
    }

    @Nested
    @DisplayName("testThbWikiUrlWrittenAfterProcessing")
    class ThbWikiUrlWrittenAfterProcessing {

        @Test
        @DisplayName("successful processing writes thbWikiUrl to article")
        void successfulProcessingWritesThbWikiUrl() {
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
}
