package info.tongrenlu.service;

import cn.hutool.http.HttpResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import info.tongrenlu.cache.ThbwikiCacheService;
import info.tongrenlu.domain.ArticleBean;
import info.tongrenlu.domain.ArtistBean;
import info.tongrenlu.domain.OkfEntryBean;
import info.tongrenlu.domain.OkfRawBean;
import info.tongrenlu.mapper.ArticleMapper;
import info.tongrenlu.mapper.ArtistMapper;
import info.tongrenlu.mapper.OkfEntryMapper;
import info.tongrenlu.mapper.OkfRawMapper;
import info.tongrenlu.mapper.TagMapper;
import info.tongrenlu.mapper.TrackMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** {@link OkfImportService} 幂等导入行为（entry + raw M:N 落库）的单测。 */
class OkfImportServiceTest {

    private static final String TRACK_LIST_HTML = """
            <html><body><div id="mw-content-text">
            <h3><span class="mw-headline" id="游戏">游戏</span></h3>
            <h4><span class="mw-headline">东方灵异传</span></h4>
            <table class="wikitable sortable"><tbody>
            <tr><th colspan="5"><a href="/%E4%B8%9C%E6%96%B9%E7%81%B5%E5%BC%82%E4%BC%A0" title="东方灵异传">东方灵异传</a>原曲列表</th></tr>
            <tr><th>序号</th><th>曲目编号</th><th>中文名</th><th>日文名</th><th>英文名</th></tr>
            <tr><td>1</td><td>1</td><td><a href="/A_Sacred_Lot">A Sacred Lot</a></td><td><span lang="ja">A Sacred Lot</span></td><td><span lang="en">A Sacred Lot</span></td></tr>
            <tr><th colspan="5">共1首原曲</th></tr>
            </tbody></table>
            </div></body></html>
            """;

    private OkfEntryMapper okfEntryMapper;
    private OkfRawMapper okfRawMapper;
    private ArtistMapper artistMapper;
    private ArticleMapper articleMapper;
    private OkfImportService service;

    @BeforeEach
    void setUp() {
        okfEntryMapper = mock(OkfEntryMapper.class);
        okfRawMapper = mock(OkfRawMapper.class);
        artistMapper = mock(ArtistMapper.class);
        articleMapper = mock(ArticleMapper.class);
        ThbwikiService thbwikiService = new ThbwikiService(
                mock(ThbwikiCacheService.class), new ObjectMapper(), mock(TrackMapper.class), null);
        thbwikiService.setHttpClient(url -> mockResponse(TRACK_LIST_HTML));
        service = new OkfImportService(
                okfEntryMapper,
                okfRawMapper,
                artistMapper,
                mock(TagMapper.class),
                articleMapper,
                thbwikiService,
                new ObjectMapper());
        // raw 幂等落库默认：查重未命中 → insert；插入后回填 id 由 Mockito 侧 stub
        when(okfRawMapper.findByUrl(any(String.class))).thenReturn(null);
        when(okfRawMapper.insert(any(OkfRawBean.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, OkfRawBean.class).setId(999L);
            return 1;
        });
        when(okfRawMapper.linkRawEntry(any(Long.class), any(Long.class), any())).thenReturn(1);
        // entry 插入同样回填 id，供桥表断言
        when(okfEntryMapper.insert(any(OkfEntryBean.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, OkfEntryBean.class).setId(100L);
            return 1;
        });
    }

    private static HttpResponse mockResponse(String body) {
        HttpResponse response = mock(HttpResponse.class);
        when(response.isOk()).thenReturn(true);
        when(response.body()).thenReturn(body);
        return response;
    }

    @Test
    @DisplayName("原作游戏导入：entry 与 raw 双落库，重跑已有内容时跳过（幂等）")
    void originalGameImportIsIdempotent() {
        OkfEntryBean existing = new OkfEntryBean();
        existing.setId(1L);
        existing.setType("original_game");
        existing.setName("东方灵异传");
        existing.setDescription("《东方灵异传》");
        existing.setSourceUrl("https://thbwiki.cc/%E4%B8%9C%E6%96%B9%E7%81%B5%E5%BC%82%E4%BC%A0");
        existing.setContent("正文");
        existing.setStatus("GENERATED");
        when(okfEntryMapper.findByTypeAndName("original_game", "东方灵异传"))
                .thenReturn(null, existing);
        // raw 幂等：首轮未命中插入，重跑命中既有 raw
        OkfRawBean existingRaw = new OkfRawBean();
        existingRaw.setId(999L);
        existingRaw.setSource("tracklist");
        existingRaw.setUrl("https://thbwiki.cc/%E4%B8%9C%E6%96%B9%E7%81%B5%E5%BC%82%E4%BC%A0");
        existingRaw.setRawJson("{}");
        when(okfRawMapper.findByUrl("https://thbwiki.cc/%E4%B8%9C%E6%96%B9%E7%81%B5%E5%BC%82%E4%BC%A0"))
                .thenReturn(null, existingRaw);

        Map<String, Object> first = service.importByType("original_game");
        assertThat(first.get("imported")).isEqualTo(1);
        assertThat(first.get("raw")).isEqualTo(1);

        Map<String, Object> second = service.importByType("original_game");
        assertThat(second.get("skipped")).isEqualTo(1);
        assertThat(second.get("raw")).isEqualTo(0); // raw 已存在，复用不重复落库

        ArgumentCaptor<OkfEntryBean> inserted = ArgumentCaptor.forClass(OkfEntryBean.class);
        verify(okfEntryMapper, times(1)).insert(inserted.capture());
        assertThat(inserted.getValue().getType()).isEqualTo("original_game");
        assertThat(inserted.getValue().getName()).isEqualTo("东方灵异传");
        verify(okfRawMapper, times(1)).insert(any(OkfRawBean.class));
        // 两条路径（首轮新 entry id=100 + 重跑已有 entry id=1）都桥接到同一 raw
        ArgumentCaptor<Long> rawIds = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Long> entryIds = ArgumentCaptor.forClass(Long.class);
        verify(okfRawMapper, times(2)).linkRawEntry(rawIds.capture(), entryIds.capture(), any());
        assertThat(rawIds.getAllValues()).containsOnly(999L);
        assertThat(entryIds.getAllValues()).containsExactly(100L, 1L);
        verify(okfEntryMapper, never()).updateById(any(OkfEntryBean.class));
    }

    @Test
    @DisplayName("artist 迁移：按 ref 查重后插入，raw 落库，sources_json 含双来源")
    void artistImportUsesRefKey() {
        ArtistBean artist = new ArtistBean();
        artist.setId(5L);
        artist.setName("IOSYS");
        artist.setCloudMusicId(6887L);
        artist.setThbWikiUrl("https://thbwiki.cc/IOSYS");
        when(artistMapper.selectList(any())).thenReturn(List.of(artist));
        when(okfEntryMapper.findByRef("artist", "m_artist", 5L)).thenReturn(null);

        Map<String, Object> result = service.importByType("artist");

        assertThat(result.get("imported")).isEqualTo(1);
        assertThat(result.get("raw")).isEqualTo(1);
        ArgumentCaptor<OkfEntryBean> inserted = ArgumentCaptor.forClass(OkfEntryBean.class);
        verify(okfEntryMapper).insert(inserted.capture());
        assertThat(inserted.getValue().getType()).isEqualTo("artist");
        assertThat(inserted.getValue().getRefType()).isEqualTo("m_artist");
        assertThat(inserted.getValue().getRefId()).isEqualTo(5L);
        assertThat(inserted.getValue().getStatus()).isEqualTo("IMPORTED");
        assertThat(inserted.getValue().getSourcesJson()).contains("https://thbwiki.cc/IOSYS");
        assertThat(inserted.getValue().getSourcesJson())
                .contains("https://music.163.com/#/artist/desc?id=6887");
        assertThat(inserted.getValue().getSourcesJson()).contains("netease-desc");

        ArgumentCaptor<OkfRawBean> rawInserted = ArgumentCaptor.forClass(OkfRawBean.class);
        verify(okfRawMapper).insert(rawInserted.capture());
        assertThat(rawInserted.getValue().getSource()).isEqualTo("local");
        assertThat(rawInserted.getValue().getUrl()).isEqualTo("local://m_artist/5");
        assertThat(rawInserted.getValue().getRawJson()).contains("cloud_music_id");
        verify(okfRawMapper).linkRawEntry(eq(999L), eq(inserted.getValue().getId()), any());
    }

    @Test
    @DisplayName("album 迁移：同名专辑按 ref 区分，不因重名合并")
    void albumImportKeepsDuplicateNames() {
        ArticleBean a1 = new ArticleBean();
        a1.setId(101L);
        a1.setTitle("同名专辑");
        ArticleBean a2 = new ArticleBean();
        a2.setId(102L);
        a2.setTitle("同名专辑");
        when(articleMapper.selectList(any())).thenReturn(List.of(a1, a2));
        when(okfEntryMapper.findByRef("album", "m_article", 101L)).thenReturn(null);
        when(okfEntryMapper.findByRef("album", "m_article", 102L)).thenReturn(null);

        Map<String, Object> result = service.importByType("album");

        assertThat(result.get("imported")).isEqualTo(2);
        verify(okfEntryMapper, times(2)).insert(any(OkfEntryBean.class));
        verify(okfRawMapper, times(2)).insert(any(OkfRawBean.class));
        verify(okfRawMapper, times(2)).linkRawEntry(any(Long.class), any(Long.class), any());
    }
}
