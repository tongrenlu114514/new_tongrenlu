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
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    @Nested
    @DisplayName("Rate limit tests")
    class RateLimitTests {

        @Test
        @DisplayName("enforceRateLimit skips delay on first call (no prior request)")
        void enforceRateLimit_firstCall_noDelay() {
            // Fixed clock — first call has no prior timestamp, so no delay should occur
            Clock fixedClock = Clock.fixed(
                Instant.parse("2026-04-18T12:00:00Z"),
                ZoneId.of("UTC")
            );
            ThbwikiService svc = new ThbwikiService(
                new ThbwikiCacheService(),
                new ObjectMapper(),
                fixedClock
            );

            long start = System.currentTimeMillis();
            svc.enforceRateLimit();
            long elapsed = System.currentTimeMillis() - start;

            // No sleep should occur on the very first call
            assertThat(elapsed).isLessThan(100);
        }

        @Test
        @DisplayName("enforceRateLimit enforces minimum 1500ms interval between rapid calls")
        void enforceRateLimit_secondCall_respectsMinInterval() {
            AtomicInteger callCount = new AtomicInteger(0);
            // Mutable clock: starts at T=0, advances by 500ms per advance() call
            Instant baseInstant = Instant.parse("2026-04-18T12:00:00Z");
            MutableClock mockClock = new MutableClock(baseInstant);

            ThbwikiService svc = new ThbwikiService(
                new ThbwikiCacheService(),
                new ObjectMapper(),
                mockClock
            );

            // Mock HTTP client that just records call count
            svc.setHttpClient(url -> {
                callCount.incrementAndGet();
                // Return a minimal mock response
                cn.hutool.http.HttpResponse mockResponse = mock(cn.hutool.http.HttpResponse.class);
                when(mockResponse.isOk()).thenReturn(true);
                when(mockResponse.body()).thenReturn("[\"test\", []]");
                return mockResponse;
            });

            // First request — should not wait
            long start1 = System.currentTimeMillis();
            svc.enforceRateLimit();
            long elapsed1 = System.currentTimeMillis() - start1;
            assertThat(elapsed1).isLessThan(100);

            // Advance clock by only 500ms (less than 1500ms interval)
            mockClock.advance(500);

            // Second request — must wait at least 1000ms (1500 - 500)
            long start2 = System.currentTimeMillis();
            svc.enforceRateLimit();
            long elapsed2 = System.currentTimeMillis() - start2;

            // Should have waited close to 1000ms
            assertThat(elapsed2).isGreaterThanOrEqualTo(900);
            assertThat(elapsed2).isLessThan(2000);
        }

        /** Minimal mutable Clock implementation for testing. */
        static class MutableClock extends Clock {
            private Instant instant;
            private final ZoneId zone;

            MutableClock(Instant base) { this.instant = base; this.zone = ZoneId.of("UTC"); }

            @Override public ZoneId getZone() { return zone; }
            @Override public Clock withZone(ZoneId z) { return this; }
            @Override public Instant instant() { return instant; }
            void advance(long ms) { instant = instant.plusMillis(ms); }
        }

        @Test
        @DisplayName("searchAlbum calls enforceRateLimit before making HTTP request")
        void searchAlbum_callsEnforceRateLimit() {
            Clock fixedClock = Clock.fixed(
                Instant.parse("2026-04-18T12:00:00Z"),
                ZoneId.of("UTC")
            );
            AtomicInteger httpCallCount = new AtomicInteger(0);

            ThbwikiService svc = new ThbwikiService(
                new ThbwikiCacheService(),
                new ObjectMapper(),
                fixedClock
            );

            svc.setHttpClient(url -> {
                httpCallCount.incrementAndGet();
                cn.hutool.http.HttpResponse mockResponse = mock(cn.hutool.http.HttpResponse.class);
                when(mockResponse.isOk()).thenReturn(true);
                when(mockResponse.body()).thenReturn("[\"album\", []]");
                return mockResponse;
            });

            // Make two rapid calls — second should wait
            svc.searchAlbum("test album");
            svc.searchAlbum("another album");

            // HTTP client was called twice (after rate-limit enforcement)
            assertThat(httpCallCount.get()).isEqualTo(2);
        }

        @Test
        @DisplayName("fetchAlbumDetail calls enforceRateLimit before making HTTP request")
        void fetchAlbumDetail_callsEnforceRateLimit() {
            Clock fixedClock = Clock.fixed(
                Instant.parse("2026-04-18T12:00:00Z"),
                ZoneId.of("UTC")
            );
            AtomicInteger httpCallCount = new AtomicInteger(0);

            ThbwikiService svc = new ThbwikiService(
                new ThbwikiCacheService(),
                new ObjectMapper(),
                fixedClock
            );

            svc.setHttpClient(url -> {
                httpCallCount.incrementAndGet();
                cn.hutool.http.HttpResponse mockResponse = mock(cn.hutool.http.HttpResponse.class);
                when(mockResponse.isOk()).thenReturn(true);
                when(mockResponse.body()).thenReturn("<html><body><h1>Test</h1></body></html>");
                return mockResponse;
            });

            svc.fetchAlbumDetail("https://thbwiki.cc/Test_Album");
            svc.fetchAlbumDetail("https://thbwiki.cc/Another_Album");

            assertThat(httpCallCount.get()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("Retry logic tests")
    class RetryTests {

        private static final String TEST_URL = "https://thbwiki.cc/Test_Album";
        private static final int MAX_RETRIES = 3;

        @Test
        @DisplayName("fetchWithRetry returns response on first success (no retries)")
        void fetchWithRetry_firstSuccess_returnsImmediately() {
            Clock fixedClock = Clock.fixed(
                Instant.parse("2026-04-18T12:00:00Z"),
                ZoneId.of("UTC")
            );
            AtomicInteger callCount = new AtomicInteger(0);

            ThbwikiService svc = new ThbwikiService(
                new ThbwikiCacheService(),
                new ObjectMapper(),
                fixedClock
            );

            svc.setHttpClient(url -> {
                callCount.incrementAndGet();
                cn.hutool.http.HttpResponse mockResponse = mock(cn.hutool.http.HttpResponse.class);
                when(mockResponse.isOk()).thenReturn(true);
                when(mockResponse.body()).thenReturn("<html><body>OK</body></html>");
                return mockResponse;
            });

            cn.hutool.http.HttpResponse result = svc.fetchWithRetry(TEST_URL, MAX_RETRIES);

            assertThat(result.isOk()).isTrue();
            assertThat(callCount.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("fetchWithRetry returns first successful response after 429 failures (exponential backoff)")
        void fetchWithRetry_429ThenSuccess_returnsSuccessfulResponse() {
            Clock fixedClock = Clock.fixed(
                Instant.parse("2026-04-18T12:00:00Z"),
                ZoneId.of("UTC")
            );
            AtomicInteger callCount = new AtomicInteger(0);

            ThbwikiService svc = new ThbwikiService(
                new ThbwikiCacheService(),
                new ObjectMapper(),
                fixedClock
            );

            svc.setHttpClient(url -> {
                int n = callCount.incrementAndGet();
                cn.hutool.http.HttpResponse mockResponse = mock(cn.hutool.http.HttpResponse.class);
                if (n == 1) {
                    // First call: 429 rate-limited
                    when(mockResponse.getStatus()).thenReturn(429);
                } else {
                    // Second call: success
                    when(mockResponse.isOk()).thenReturn(true);
                    when(mockResponse.body()).thenReturn("<html><body>OK</body></html>");
                }
                return mockResponse;
            });

            cn.hutool.http.HttpResponse result = svc.fetchWithRetry(TEST_URL, MAX_RETRIES);

            assertThat(callCount.get()).isEqualTo(2);
            assertThat(result.isOk()).isTrue();
        }

        @Test
        @DisplayName("fetchWithRetry exhausts all retries and throws RuntimeException on persistent 429")
        void fetchWithRetry_all429_throwsAfterMaxRetries() {
            Clock fixedClock = Clock.fixed(
                Instant.parse("2026-04-18T12:00:00Z"),
                ZoneId.of("UTC")
            );
            AtomicInteger callCount = new AtomicInteger(0);

            ThbwikiService svc = new ThbwikiService(
                new ThbwikiCacheService(),
                new ObjectMapper(),
                fixedClock
            );

            svc.setHttpClient(url -> {
                callCount.incrementAndGet();
                cn.hutool.http.HttpResponse mockResponse = mock(cn.hutool.http.HttpResponse.class);
                when(mockResponse.getStatus()).thenReturn(429);
                return mockResponse;
            });

            try {
                svc.fetchWithRetry(TEST_URL, MAX_RETRIES);
            } catch (RuntimeException e) {
                // Expected — all retries exhausted
            }

            // maxRetries=3 → total attempts = 4 (1 original + 3 retries)
            assertThat(callCount.get()).isEqualTo(MAX_RETRIES + 1);
        }

        @Test
        @DisplayName("fetchWithRetry does NOT retry on non-429 non-OK HTTP responses (e.g. 404)")
        void fetchWithRetry_404_noRetry() {
            Clock fixedClock = Clock.fixed(
                Instant.parse("2026-04-18T12:00:00Z"),
                ZoneId.of("UTC")
            );
            AtomicInteger callCount = new AtomicInteger(0);

            ThbwikiService svc = new ThbwikiService(
                new ThbwikiCacheService(),
                new ObjectMapper(),
                fixedClock
            );

            svc.setHttpClient(url -> {
                callCount.incrementAndGet();
                cn.hutool.http.HttpResponse mockResponse = mock(cn.hutool.http.HttpResponse.class);
                when(mockResponse.getStatus()).thenReturn(404);
                return mockResponse;
            });

            // Should return immediately with 404, no retries
            cn.hutool.http.HttpResponse result = svc.fetchWithRetry(TEST_URL, MAX_RETRIES);

            assertThat(result.getStatus()).isEqualTo(404);
            assertThat(callCount.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("fetchWithRetry retries on network exception and succeeds on second attempt")
        void fetchWithRetry_exceptionThenSuccess_returnsSuccessfulResponse() {
            Clock fixedClock = Clock.fixed(
                Instant.parse("2026-04-18T12:00:00Z"),
                ZoneId.of("UTC")
            );
            AtomicInteger callCount = new AtomicInteger(0);

            ThbwikiService svc = new ThbwikiService(
                new ThbwikiCacheService(),
                new ObjectMapper(),
                fixedClock
            );

            svc.setHttpClient(url -> {
                int n = callCount.incrementAndGet();
                if (n == 1) {
                    throw new RuntimeException("Connection timed out");
                }
                cn.hutool.http.HttpResponse mockResponse = mock(cn.hutool.http.HttpResponse.class);
                when(mockResponse.isOk()).thenReturn(true);
                when(mockResponse.body()).thenReturn("<html><body>OK</body></html>");
                return mockResponse;
            });

            cn.hutool.http.HttpResponse result = svc.fetchWithRetry(TEST_URL, MAX_RETRIES);

            assertThat(callCount.get()).isEqualTo(2);
            assertThat(result.isOk()).isTrue();
        }

        @Test
        @DisplayName("fetchWithRetry exhausts retries on persistent network exception and throws RuntimeException")
        void fetchWithRetry_allExceptions_throwsAfterMaxRetries() {
            Clock fixedClock = Clock.fixed(
                Instant.parse("2026-04-18T12:00:00Z"),
                ZoneId.of("UTC")
            );
            AtomicInteger callCount = new AtomicInteger(0);

            ThbwikiService svc = new ThbwikiService(
                new ThbwikiCacheService(),
                new ObjectMapper(),
                fixedClock
            );

            svc.setHttpClient(url -> {
                callCount.incrementAndGet();
                throw new RuntimeException("Connection timed out");
            });

            try {
                svc.fetchWithRetry(TEST_URL, MAX_RETRIES);
            } catch (RuntimeException e) {
                // Expected
                assertThat(e).hasMessageContaining("THBWiki request failed after");
            }

            assertThat(callCount.get()).isEqualTo(MAX_RETRIES + 1);
        }

        @Test
        @DisplayName("getBackoffMillis returns correct exponential values: 1s, 2s, 4s")
        void getBackoffMillis_exponentialValues() {
            Clock fixedClock = Clock.fixed(
                Instant.parse("2026-04-18T12:00:00Z"),
                ZoneId.of("UTC")
            );
            ThbwikiService svc = new ThbwikiService(
                new ThbwikiCacheService(),
                new ObjectMapper(),
                fixedClock
            );

            assertThat(svc.getBackoffMillis(0)).isEqualTo(1_000L);   // 2^0 * 1000
            assertThat(svc.getBackoffMillis(1)).isEqualTo(2_000L);   // 2^1 * 1000
            assertThat(svc.getBackoffMillis(2)).isEqualTo(4_000L);   // 2^2 * 1000
            assertThat(svc.getBackoffMillis(3)).isEqualTo(8_000L);   // 2^3 * 1000
        }
    }
}
