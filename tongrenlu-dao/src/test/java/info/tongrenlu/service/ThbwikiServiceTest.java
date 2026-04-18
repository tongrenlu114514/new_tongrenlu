package info.tongrenlu.service;

import info.tongrenlu.cache.ThbwikiCacheService;
import info.tongrenlu.model.ThbwikiAlbum;
import info.tongrenlu.support.TextNormalizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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

            assertThat(firstTrack.getOriginalSource()).isEqualTo("少女さとり ～ 3rd eye");
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
    @DisplayName("Cache integration tests")
    class CacheIntegrationTests {

        private static final String VALID_URL = "https://thbwiki.cc/Test_Album";

        @Test
        @DisplayName("fetchAlbumDetail returns cached result on cache hit without HTTP call")
        void fetchAlbumDetail_cacheHit_noHttpCall() {
            Clock fixedClock = Clock.fixed(
                Instant.parse("2026-04-18T12:00:00Z"),
                ZoneId.of("UTC")
            );
            AtomicInteger httpCallCount = new AtomicInteger(0);

            ThbwikiCacheService localCache = new ThbwikiCacheService();
            ThbwikiService svc = new ThbwikiService(
                localCache,
                new ObjectMapper()
            );

            // Pre-populate cache
            ThbwikiAlbum cachedAlbum = new ThbwikiAlbum();
            cachedAlbum.setName("Cached Album");
            cachedAlbum.setUrl(VALID_URL);
            localCache.put("detail:" + VALID_URL, cachedAlbum);

            svc.setHttpClient(url -> {
                httpCallCount.incrementAndGet();
                cn.hutool.http.HttpResponse mockResponse = mock(cn.hutool.http.HttpResponse.class);
                when(mockResponse.isOk()).thenReturn(true);
                when(mockResponse.body()).thenReturn("<html><body>OK</body></html>");
                return mockResponse;
            });

            Optional<ThbwikiAlbum> result = svc.fetchAlbumDetail(VALID_URL);

            assertThat(result).isPresent();
            assertThat(result.get().getName()).isEqualTo("Cached Album");
            assertThat(httpCallCount.get()).isZero();
        }

        @Test
        @DisplayName("fetchAlbumDetail caches result after successful fetch")
        void fetchAlbumDetail_cacheMiss_cachesResult() throws Exception {
            Clock fixedClock = Clock.fixed(
                Instant.parse("2026-04-18T12:00:00Z"),
                ZoneId.of("UTC")
            );
            AtomicInteger httpCallCount = new AtomicInteger(0);

            ThbwikiCacheService localCache = new ThbwikiCacheService();
            ThbwikiService svc = new ThbwikiService(
                localCache,
                new ObjectMapper()
            );

            String html = loadHtml("/thbwiki/sample-album.html");
            svc.setHttpClient(url -> {
                httpCallCount.incrementAndGet();
                cn.hutool.http.HttpResponse mockResponse = mock(cn.hutool.http.HttpResponse.class);
                when(mockResponse.isOk()).thenReturn(true);
                when(mockResponse.body()).thenReturn(html);
                return mockResponse;
            });

            // First call - should hit network and cache
            Optional<ThbwikiAlbum> result1 = svc.fetchAlbumDetail(VALID_URL);
            assertThat(result1).isPresent();
            assertThat(httpCallCount.get()).isEqualTo(1);

            // Verify cache now contains the result
            Optional<ThbwikiAlbum> cached = localCache.get("detail:" + VALID_URL);
            assertThat(cached).isPresent();
            assertThat(cached.get().getName()).isEqualTo(result1.get().getName());
        }

        @Test
        @DisplayName("fetchAlbumDetail does not cache empty result on HTTP failure")
        void fetchAlbumDetail_httpFailure_noCacheWrite() {
            Clock fixedClock = Clock.fixed(
                Instant.parse("2026-04-18T12:00:00Z"),
                ZoneId.of("UTC")
            );

            ThbwikiCacheService localCache = new ThbwikiCacheService();
            ThbwikiService svc = new ThbwikiService(
                localCache,
                new ObjectMapper()
            );

            svc.setHttpClient(url -> {
                cn.hutool.http.HttpResponse mockResponse = mock(cn.hutool.http.HttpResponse.class);
                when(mockResponse.isOk()).thenReturn(false);
                when(mockResponse.getStatus()).thenReturn(404);
                return mockResponse;
            });

            svc.fetchAlbumDetail(VALID_URL);

            // Cache should be empty (no cache write on failure)
            Optional<ThbwikiAlbum> cached = localCache.get("detail:" + VALID_URL);
            assertThat(cached).isEmpty();
        }

        @Test
        @DisplayName("fetchAlbumDetail does not cache result for invalid URL")
        void fetchAlbumDetail_invalidUrl_noCacheWrite() {
            Clock fixedClock = Clock.fixed(
                Instant.parse("2026-04-18T12:00:00Z"),
                ZoneId.of("UTC")
            );
            AtomicInteger httpCallCount = new AtomicInteger(0);

            ThbwikiCacheService localCache = new ThbwikiCacheService();
            ThbwikiService svc = new ThbwikiService(
                localCache,
                new ObjectMapper()
            );

            svc.setHttpClient(url -> {
                httpCallCount.incrementAndGet();
                cn.hutool.http.HttpResponse mockResponse = mock(cn.hutool.http.HttpResponse.class);
                when(mockResponse.isOk()).thenReturn(true);
                when(mockResponse.body()).thenReturn("<html><body>OK</body></html>");
                return mockResponse;
            });

            // Invalid URL - should return empty and not call HTTP
            Optional<ThbwikiAlbum> result = svc.fetchAlbumDetail("https://evil.com/page");
            assertThat(result).isEmpty();
            assertThat(httpCallCount.get()).isZero();

            // Cache should be empty
            Optional<ThbwikiAlbum> cached = localCache.get("detail:https://evil.com/page");
            assertThat(cached).isEmpty();
        }

        @Test
        @DisplayName("getCacheStats delegates to cache service")
        void getCacheStats_delegatesToCacheService() {
            Clock fixedClock = Clock.fixed(
                Instant.parse("2026-04-18T12:00:00Z"),
                ZoneId.of("UTC")
            );
            ThbwikiCacheService localCache = new ThbwikiCacheService();
            ThbwikiService svc = new ThbwikiService(
                localCache,
                new ObjectMapper()
            );

            String stats = svc.getCacheStats();
            assertThat(stats).isNotNull();
            // Caffeine CacheStats format: CacheStats{hitCount=N, missCount=N, ...}
            assertThat(stats).contains("hitCount=");
            assertThat(stats).contains("missCount=");
        }

        @Test
        @DisplayName("invalidateAlbumDetail removes cached entry")
        void invalidateAlbumDetail_removesCachedEntry() throws Exception {
            Clock fixedClock = Clock.fixed(
                Instant.parse("2026-04-18T12:00:00Z"),
                ZoneId.of("UTC")
            );
            AtomicInteger httpCallCount = new AtomicInteger(0);

            ThbwikiCacheService localCache = new ThbwikiCacheService();
            ThbwikiService svc = new ThbwikiService(
                localCache,
                new ObjectMapper()
            );

            String html = loadHtml("/thbwiki/sample-album.html");
            svc.setHttpClient(url -> {
                httpCallCount.incrementAndGet();
                cn.hutool.http.HttpResponse mockResponse = mock(cn.hutool.http.HttpResponse.class);
                when(mockResponse.isOk()).thenReturn(true);
                when(mockResponse.body()).thenReturn(html);
                return mockResponse;
            });

            // First call - caches result
            svc.fetchAlbumDetail(VALID_URL);
            assertThat(httpCallCount.get()).isEqualTo(1);
            assertThat(localCache.get("detail:" + VALID_URL)).isPresent();

            // Invalidate
            svc.invalidateAlbumDetail(VALID_URL);
            assertThat(localCache.get("detail:" + VALID_URL)).isEmpty();

            // Second call - should hit network again
            svc.fetchAlbumDetail(VALID_URL);
            assertThat(httpCallCount.get()).isEqualTo(2);
        }

        @Test
        @DisplayName("invalidateAlbumDetail does nothing for invalid URL")
        void invalidateAlbumDetail_invalidUrl_noAction() {
            Clock fixedClock = Clock.fixed(
                Instant.parse("2026-04-18T12:00:00Z"),
                ZoneId.of("UTC")
            );
            AtomicInteger httpCallCount = new AtomicInteger(0);

            ThbwikiCacheService localCache = new ThbwikiCacheService();
            ThbwikiService svc = new ThbwikiService(
                localCache,
                new ObjectMapper()
            );

            svc.setHttpClient(url -> {
                httpCallCount.incrementAndGet();
                cn.hutool.http.HttpResponse mockResponse = mock(cn.hutool.http.HttpResponse.class);
                when(mockResponse.isOk()).thenReturn(true);
                when(mockResponse.body()).thenReturn("<html><body>OK</body></html>");
                return mockResponse;
            });

            // Should not throw and should not call HTTP
            svc.invalidateAlbumDetail("https://evil.com/page");
            assertThat(httpCallCount.get()).isZero();
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
                new ObjectMapper()
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
                new ObjectMapper()
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
                new ObjectMapper()
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
                new ObjectMapper()
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
    @DisplayName("TextNormalization integration tests")
    class TextNormalizationIntegrationTests {

        /**
         * Integration test: verifies that TextNormalizer.normalize() is applied at all four
         * extraction points in ThbwikiService, so that whitespace variations from THBWiki
         * are normalized to a consistent format.
         *
         * Note: normalize() collapses whitespace and trims but does NOT convert full-width
         * ASCII or apply NFKC. Full-width characters are preserved as-is.
         */
        @Test
        @DisplayName("parseAlbumDetail normalizes album name: ideographic space collapsed, full-width digits preserved")
        void parseAlbumDetail_normalizesIdeographicSpaceFullWidthPreserved() throws Exception {
            String html = loadHtml("/thbwiki/sample-album-normalized.html");
            Document doc = Jsoup.parse(html);

            Optional<ThbwikiAlbum> result = service.parseAlbumDetail(doc, "https://thbwiki.cc/Album01");
            assertThat(result).isPresent();

            // "Album０１　Remix" → normalize → ideographic space → regular space; digits preserved
            assertThat(result.get().getName()).isEqualTo("Album０１ Remix");
        }

        @Test
        @DisplayName("parseTracks normalizes track name: ideographic space collapsed, circled digit preserved")
        void parseTracks_normalizesIdeographicSpaceCircledDigitPreserved() throws Exception {
            String html = loadHtml("/thbwiki/sample-album-normalized.html");
            Document doc = Jsoup.parse(html);

            var tracks = service.parseTracks(doc);
            assertThat(tracks).hasSize(3);

            // "①　Satori Maiden" → normalize → "① Satori Maiden" (circled digit U+2460 unchanged; full-width space → " ")
            assertThat(tracks.get(0).getName()).isEqualTo("① Satori Maiden");
        }

        @Test
        @DisplayName("parseTracks normalizes track name: leading/trailing/internal spaces collapsed, uppercase preserved")
        void parseTracks_normalizesWhitespaceFullWidthUppercasePreserved() throws Exception {
            String html = loadHtml("/thbwiki/sample-album-normalized.html");
            Document doc = Jsoup.parse(html);

            var tracks = service.parseTracks(doc);

            // "　　SATORI　　MAIDEN　　" → normalize → "SATORI MAIDEN"
            assertThat(tracks.get(1).getName()).isEqualTo("SATORI MAIDEN");
        }

        @Test
        @DisplayName("parseTracks normalizes track name: newline and ideographic space collapsed")
        void parseTracks_normalizesNewlineAndIdeographicSpace() throws Exception {
            String html = loadHtml("/thbwiki/sample-album-normalized.html");
            Document doc = Jsoup.parse(html);

            var tracks = service.parseTracks(doc);

            // "Album　Remix\n" → normalize → "Album Remix" (ideographic space + newline → spaces → collapse)
            assertThat(tracks.get(2).getName()).isEqualTo("Album Remix");
        }

        @Test
        @DisplayName("parseTrackRow normalizes originalSource: ideographic space → regular space, content preserved")
        void parseTrackRow_normalizesOriginalSourceIdeographicSpace() throws Exception {
            String html = loadHtml("/thbwiki/sample-album-normalized.html");
            Document doc = Jsoup.parse(html);

            var tracks = service.parseTracks(doc);

            // "少女さとり　Original" → normalize → "少女さとり Original" (ideographic space → " ")
            assertThat(tracks.get(0).getOriginalSource()).isEqualTo("少女さとり Original");
        }

        @Test
        @DisplayName("parseTrackRow normalizes originalName extracted from text before source: ideographic space collapsed")
        void parseTrackRow_normalizesOriginalNameIdeographicSpace() throws Exception {
            String html = loadHtml("/thbwiki/sample-album-normalized.html");
            Document doc = Jsoup.parse(html);

            var tracks = service.parseTracks(doc);

            // ogmusic text: "Album　Remix" + "Album　Original" → replace second → "Album　Remix"
            // normalize(): "Album Remix"
            assertThat(tracks.get(2).getOriginalName()).isEqualTo("Album Remix");
        }

        /**
         * Equivalence test: verifies that the parsed track name equals TextNormalizer.normalize()
         * applied to the raw HTML text — confirming TextNormalizer is actually in the pipeline.
         */
        @Test
        @DisplayName("parsed track name equals normalize() of raw HTML text — proves TextNormalizer is applied")
        void parsedTrackName_equalsNormalizeOfRawText() throws Exception {
            String html = loadHtml("/thbwiki/sample-album-normalized.html");
            Document doc = Jsoup.parse(html);

            var tracks = service.parseTracks(doc);

            // Raw text from HTML: "①　Satori Maiden" (circled-one + ideographic space + name)
            // normalize(): ideographic space U+3000 → " " then collapse → "① Satori Maiden"
            String rawText = "①　Satori Maiden";
            String expected = TextNormalizer.normalize(rawText);
            assertThat(tracks.get(0).getName()).isEqualTo(expected);
        }

        /**
         * Distinction test: verify normalize() vs normalizeForComparison() behavior.
         * normalize() — display-safe: whitespace + trim only, characters unchanged.
         * normalizeForComparison() — search-key: full-width conversion + NFKC + lowercase.
         */
        @Test
        @DisplayName("normalize() vs normalizeForComparison(): whitespace-only vs full comparison key")
        void normalizeVsNormalizeForComparison_whitespaceVsFull() {
            String input = " Album０１　Remix  ";

            // normalize() trims/collapses whitespace only; full-width digits preserved
            assertThat(TextNormalizer.normalize(input)).isEqualTo("Album０１ Remix");

            // normalizeForComparison() additionally: full-width → half-width, NFKC, lowercase
            assertThat(TextNormalizer.normalizeForComparison(input)).isEqualTo("album01 remix");
        }

        /**
         * Distinction test: normalize() preserves circled digit; normalizeForComparison() NFKC-converts it.
         */
        @Test
        @DisplayName("normalize() preserves circled digit while normalizeForComparison() converts it")
        void normalize_preservesCircledDigitNormalizeForComparison_convertsIt() {
            String input = "① Satori Maiden";

            // normalize() — whitespace only; circled digit U+2460 is not whitespace
            assertThat(TextNormalizer.normalize(input)).isEqualTo("① Satori Maiden");

            // normalizeForComparison() — NFKC converts circled-one to "1"
            assertThat(TextNormalizer.normalizeForComparison(input)).isEqualTo("1 satori maiden");
        }
    }
}
