package info.tongrenlu.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import info.tongrenlu.cache.ThbwikiCacheService;
import info.tongrenlu.domain.TrackBean;
import info.tongrenlu.mapper.TrackMapper;
import info.tongrenlu.model.ThbwikiTrack;
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

    private Document readFixture(String name) throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(name)) {
            assertThat(in).as("test fixture " + name).isNotNull();
            return Jsoup.parse(in, "UTF-8", "");
        }
    }
}
