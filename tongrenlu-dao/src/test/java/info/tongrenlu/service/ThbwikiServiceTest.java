package info.tongrenlu.service;

import info.tongrenlu.cache.ThbwikiCacheService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import info.tongrenlu.model.ThbwikiAlbum;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ThbwikiServiceTest {

    private ThbwikiService service;

    @BeforeEach
    void setUp() {
        service = new ThbwikiService(
            new ThbwikiCacheService(),
            new ObjectMapper()
        );
    }

    private String loadHtml(String resourcePath) throws IOException, URISyntaxException {
        return Files.readString(
            Paths.get(getClass().getResource(resourcePath).toURI())
        );
    }

    @Nested
    @DisplayName("fetchAlbumDetail tests")
    class FetchAlbumDetailTests {

        @Test
        @DisplayName("returns empty for null URL")
        void fetchAlbumDetail_nullUrl_returnsEmpty() {
            Optional<ThbwikiAlbum> result = service.fetchAlbumDetail(null);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty for empty URL")
        void fetchAlbumDetail_emptyUrl_returnsEmpty() {
            Optional<ThbwikiAlbum> result = service.fetchAlbumDetail("");
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty for invalid URL (not THBWiki)")
        void fetchAlbumDetail_invalidUrl_returnsEmpty() {
            Optional<ThbwikiAlbum> result = service.fetchAlbumDetail("https://evil.com/page");
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty for URL with control characters")
        void fetchAlbumDetail_controlCharsUrl_returnsEmpty() {
            Optional<ThbwikiAlbum> result = service.fetchAlbumDetail("https://thbwiki.cc/test\n");
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("parseAlbumDetail tests")
    class ParseAlbumDetailTests {

        @Test
        @DisplayName("extracts album name from title")
        void parseAlbumDetail_validHtml_extractsName() throws Exception {
            String html = loadHtml("/thbwiki/sample-album.html");
            Document doc = Jsoup.parse(html);

            Optional<ThbwikiAlbum> result = service.parseAlbumDetail(doc, "https://thbwiki.cc/Satori_Maiden");
            assertThat(result).isPresent();
        }

        @Test
        @DisplayName("returns album with correct name")
        void parseAlbumDetail_extractsCorrectName() throws Exception {
            String html = loadHtml("/thbwiki/sample-album.html");
            Document doc = Jsoup.parse(html);

            Optional<ThbwikiAlbum> result = service.parseAlbumDetail(doc, "https://thbwiki.cc/Satori_Maiden");
            assertThat(result).isPresent();
            ThbwikiAlbum album = result.get();
            assertThat(album.getName()).isEqualTo("Satori Maiden");
        }

        @Test
        @DisplayName("returns album with correct URL")
        void parseAlbumDetail_extractsCorrectUrl() throws Exception {
            String html = loadHtml("/thbwiki/sample-album.html");
            Document doc = Jsoup.parse(html);
            String expectedUrl = "https://thbwiki.cc/Test_Album";

            Optional<ThbwikiAlbum> result = service.parseAlbumDetail(doc, expectedUrl);
            assertThat(result).isPresent();
            assertThat(result.get().getUrl()).isEqualTo(expectedUrl);
        }
    }

    @Nested
    @DisplayName("parseTracks tests")
    class ParseTracksTests {

        @Test
        @DisplayName("parses all tracks from valid HTML")
        void parseTracks_validHtml_parsesAllTracks() throws Exception {
            String html = loadHtml("/thbwiki/sample-album.html");
            Document doc = Jsoup.parse(html);

            var tracks = service.parseTracks(doc);
            assertThat(tracks).hasSize(3);
        }

        @Test
        @DisplayName("parses first track correctly")
        void parseTracks_firstTrack_parsedCorrectly() throws Exception {
            String html = loadHtml("/thbwiki/sample-album.html");
            Document doc = Jsoup.parse(html);

            var tracks = service.parseTracks(doc);
            assertThat(tracks.get(0).getName()).isEqualTo("Satori Maiden");
        }

        @Test
        @DisplayName("parses second track correctly")
        void parseTracks_secondTrack_parsedCorrectly() throws Exception {
            String html = loadHtml("/thbwiki/sample-album.html");
            Document doc = Jsoup.parse(html);

            var tracks = service.parseTracks(doc);
            assertThat(tracks.get(1).getName()).isEqualTo("Lost Place");
        }

        @Test
        @DisplayName("handles empty HTML gracefully")
        void parseTracks_emptyHtml_returnsEmptyList() {
            Document doc = Jsoup.parse("<html><body></body></html>");

            var tracks = service.parseTracks(doc);
            assertThat(tracks).isEmpty();
        }
    }

    @Nested
    @DisplayName("parseTrackRow tests")
    class ParseTrackRowTests {

        @Test
        @DisplayName("extracts original source from track")
        void parseTrackRow_withSource_extractsOriginalSource() throws Exception {
            String html = loadHtml("/thbwiki/sample-album.html");
            Document doc = Jsoup.parse(html);

            var tracks = service.parseTracks(doc);
            var firstTrack = tracks.get(0);

            assertThat(firstTrack.getOriginalSource()).isEqualTo("少女さとり　～ 3rd eye");
        }

        @Test
        @DisplayName("extracts original URL from track")
        void parseTrackRow_withSource_extractsOriginalUrl() throws Exception {
            String html = loadHtml("/thbwiki/sample-album.html");
            Document doc = Jsoup.parse(html);

            var tracks = service.parseTracks(doc);
            var firstTrack = tracks.get(0);

            assertThat(firstTrack.getOriginalUrl()).isEqualTo("https://thbwiki.cc/少女さとり　～_3rd_eye");
        }
    }

    @Nested
    @DisplayName("Edge case: tracks without source")
    class NoSourceTests {

        @Test
        @DisplayName("handles track with empty source cell")
        void parseTracks_emptyCell_includedInResults() throws Exception {
            String html = loadHtml("/thbwiki/sample-track-no-source.html");
            Document doc = Jsoup.parse(html);

            var tracks = service.parseTracks(doc);
            // Should include track without source (graceful degradation)
            assertThat(tracks).isNotEmpty();
        }

        @Test
        @DisplayName("handles track without ogmusic class")
        void parseTracks_noOgmusic_includedInResults() throws Exception {
            String html = loadHtml("/thbwiki/sample-track-no-source.html");
            Document doc = Jsoup.parse(html);

            var tracks = service.parseTracks(doc);
            // Should include track without ogmusic (graceful degradation)
            assertThat(tracks).hasSizeGreaterThanOrEqualTo(2);
        }

        @Test
        @DisplayName("returns track with name even without source info")
        void parseTrackRow_noSource_returnsTrackWithName() throws Exception {
            String html = loadHtml("/thbwiki/sample-track-no-source.html");
            Document doc = Jsoup.parse(html);

            var tracks = service.parseTracks(doc);
            // Find track without source
            var trackWithoutSource = tracks.stream()
                .filter(t -> "Track Without Source".equals(t.getName()))
                .findFirst();

            assertThat(trackWithoutSource).isPresent();
            assertThat(trackWithoutSource.get().getOriginalSource()).isNull();
        }
    }
}
