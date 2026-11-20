package info.tongrenlu.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import info.tongrenlu.cache.ThbwikiCacheService;
import info.tongrenlu.domain.TrackBean;
import info.tongrenlu.mapper.TrackMapper;
import info.tongrenlu.model.ThbwikiTrack;
import cn.hutool.http.HttpResponse;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** THBWiki 匹配直接写 tag 的单测。 */
class ThbwikiServiceTest {

    private TrackMapper trackMapper;
    private OriginalTagService originalTagService;
    private ThbwikiService service;

    @BeforeEach
    void setUp() {
        trackMapper = mock(TrackMapper.class);
        originalTagService = mock(OriginalTagService.class);
        service = new ThbwikiService(
                mock(ThbwikiCacheService.class), new ObjectMapper(), trackMapper, originalTagService);
    }

    @Test
    @DisplayName("parseTracks 同时保留结构化原曲名列表和展示文本")
    void parseTracksKeepsStructuredSongNames() throws Exception {
        Document doc = readFixture("thbwiki/sample-album.html");

        List<ThbwikiTrack> tracks = service.parseTracks(doc);

        assertThat(tracks).isNotEmpty();
        ThbwikiTrack first = tracks.get(0);
        // 第一个曲目有 2 个 .ogmusic>a，应作为 2 个独立原曲名，而不是拼好的文本
        assertThat(first.getOriginalSongNames()).hasSize(2);
        assertThat(first.getOriginalSource()).isNotBlank();
        // 展示文本仍按原样拼出，供 m_track.original / 前台副标题使用
        assertThat(first.getOriginalName()).contains("／");
    }

    @Test
    @DisplayName("只有 .source>a（游戏）没有 .ogmusic 的曲目不再被丢弃")
    void gameOnlyOriginalRowIsRetained() {
        String html = """
                <table class="wikitable musicTable"><tbody>
                  <tr><td>1</td><td id="Only_Game" class="title">Only Game</td><td class="time">03:00</td></tr>
                  <tr><td class="left"></td><td class="label">原曲</td><td class="text" colspan="2">
                     <div class="source"><a href="/Game" title="Game">東方紅魔郷</a></div>
                  </td></tr>
                </tbody></table>
                """;

        List<ThbwikiTrack> tracks = service.parseTracks(Jsoup.parse(html));

        assertThat(tracks).hasSize(1);
        ThbwikiTrack track = tracks.get(0);
        assertThat(track.getOriginalSongNames()).isNotNull().isEmpty();
        assertThat(track.getOriginalSource()).isNotBlank();
    }

    @Test
    @DisplayName("matchAndSave 用结构化原曲/原作直接写 tag，并保留 m_track.original")
    void matchAndSaveWritesTagsFromStructuredData() {
        TrackBean track = new TrackBean();
        track.setId(10L);
        track.setArticleId(1L);
        track.setName("Track 1");

        ThbwikiTrack thbwikiTrack = new ThbwikiTrack();
        thbwikiTrack.setName("Track 1");           // 完全匹配 → confidence 1.0
        thbwikiTrack.setOriginalSongNames(List.of("Song A", "Song B"));
        thbwikiTrack.setOriginalSource("东方红魔乡");
        thbwikiTrack.setOriginalUrl("https://thbwiki.cc/Game");

        when(trackMapper.updateById(any(TrackBean.class))).thenReturn(1);

        boolean matched = service.matchAndSave(track, List.of(thbwikiTrack));

        assertThat(matched).isTrue();
        verify(trackMapper).updateById(track);
        assertThat(track.getOriginal()).isEqualTo("东方红魔乡");   // buildOriginalInfo 回落
        assertThat(track.getOriginalUrl()).isEqualTo("https://thbwiki.cc/Game");
        // 关键：直接用结构化数据写 tag，不是把 m_track.original 文本拆回来
        verify(originalTagService).saveOriginalTags(
                eq(track), eq(List.of("Song A", "Song B")), eq(List.of("东方红魔乡")));
    }

    @Test
    @DisplayName("未注入 OriginalTagService 时匹配流程不抛异常")
    void nullTagServiceDoesNotBreakMatch() {
        ThbwikiService withoutTagService = new ThbwikiService(
                mock(ThbwikiCacheService.class), new ObjectMapper(), trackMapper, null);

        TrackBean track = new TrackBean();
        track.setId(11L);
        track.setArticleId(2L);
        track.setName("Track 1");
        ThbwikiTrack thbwikiTrack = new ThbwikiTrack();
        thbwikiTrack.setName("Track 1");
        thbwikiTrack.setOriginalSongNames(List.of());
        when(trackMapper.updateById(any(TrackBean.class))).thenReturn(1);

        assertThat(withoutTagService.matchAndSave(track, List.of(thbwikiTrack))).isTrue();
    }

    @Test
    @DisplayName("findEntryLinkInHtml 按曲名全名匹配条目链接")
    void findsEntryLinkByFullName() {
        String html = "<html><body><div id=\"mw-content-text\"><ul><li><a href=\"/%E5%B0%91%E5%A5%B3"
                + "%E3%81%95%E3%81%A8%E3%82%8A_%EF%BD%9E_3rd_eye\">少女さとり　～ 3rd eye</a></li></ul></div></body></html>";
        assertThat(service.findEntryLinkInHtml(html, "少女さとり　～ 3rd eye"))
                .isEqualTo("https://thbwiki.cc/%E5%B0%91%E5%A5%B3%E3%81%95%E3%81%A8%E3%82%8A_%EF%BD%9E_3rd_eye");
    }

    @Test
    @DisplayName("findEntryLinkInHtml 词条名带副标题时按主名回落匹配")
    void fallsBackToPrimaryName() {
        String html = "<html><body><div id=\"mw-content-text\">"
                + "<a href=\"/U.N.%E3%82%AA%E3%83%BC%E3%82%A8%E3%83%B3\">U.N.オーエンは彼女なのか？</a>"
                + "</div></body></html>";
        assertThat(service.findEntryLinkInHtml(html, "U.N.オーエンは彼女なのか？　～ 真実の扉"))
                .isEqualTo("https://thbwiki.cc/U.N.%E3%82%AA%E3%83%BC%E3%82%A8%E3%83%B3");
    }

    @Test
    @DisplayName("findEntryLinkInHtml 命中命名空间链接（File: 等）时跳过")
    void skipsNamespaceLinks() {
        String html = "<html><body><div id=\"mw-content-text\">"
                + "<a href=\"/File:%E5%B0%91%E5%A5%B3%E3%81%95%E3%81%A8%E3%82%8A\">少女さとり ～ 3rd eye</a>"
                + "</div></body></html>";
        assertThat(service.findEntryLinkInHtml(html, "少女さとり　～ 3rd eye")).isNull();
    }

    @Test
    @DisplayName("findEntryLinkInHtml 无匹配条目返回 null")
    void returnsNullWhenNoEntryMatch() {
        String html = "<html><body><div id=\"mw-content-text\"><a href=\"/other\">其它曲目</a></div></body></html>";
        assertThat(service.findEntryLinkInHtml(html, "少女さとり　～ 3rd eye")).isNull();
        assertThat(service.findEntryLinkInHtml("<html><body></body></html>", "任意")).isNull();
    }

    @Test
    @DisplayName("buildLinkIndex 同时收录全名与去副标题主名两个 key")
    void buildLinkIndexAddsPrimaryVariant() {
        String html = "<html><body><div id=\"mw-content-text\">"
                + "<a href=\"/Song\">Song ～ Subtitle</a>"
                + "</div></body></html>";
        var index = service.buildLinkIndex(html);
        assertThat(index).containsKeys("song ~ subtitle", "song");
        assertThat(index.get("song")).isEqualTo("https://thbwiki.cc/Song");
    }

    @Test
    @DisplayName("索引页解析结果缓存复用：同 URL 第二次查询不再抓取")
    void cachesLinkIndexAcrossLookups() throws Exception {
        ThbwikiHttpClient httpClient = mock(ThbwikiHttpClient.class);
        HttpResponse response = mock(HttpResponse.class);
        when(response.isOk()).thenReturn(true);
        when(response.body()).thenReturn(
                "<html><body><div id=\"mw-content-text\"><a href=\"/Song\">Song</a></div></body></html>");
        when(httpClient.execute(any(String.class))).thenReturn(response);
        service.setHttpClient(httpClient);

        assertThat(service.findEntryLink("https://thbwiki.cc/Index", "Song")).isPresent();
        assertThat(service.findEntryLink("https://thbwiki.cc/Index", "Song")).isPresent();
        verify(httpClient, times(1)).execute(any(String.class));
    }

    private Document readFixture(String name) throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(name)) {
            assertThat(in).as("test fixture " + name).isNotNull();
            return Jsoup.parse(in, "UTF-8", "");
        }
    }
}
