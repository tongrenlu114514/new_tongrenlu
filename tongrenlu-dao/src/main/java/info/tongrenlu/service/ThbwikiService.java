package info.tongrenlu.service;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import info.tongrenlu.cache.ThbwikiCacheService;
import info.tongrenlu.domain.TrackBean;
import info.tongrenlu.mapper.TrackMapper;
import info.tongrenlu.model.MatchResult;
import info.tongrenlu.model.ThbwikiAlbum;
import info.tongrenlu.model.ThbwikiTrack;
import info.tongrenlu.support.TextNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@Service
@Slf4j
@RequiredArgsConstructor
public class ThbwikiService {

    private static final String THBWIKI_BASE_URL = "https://thbwiki.cc";
    private static final String OPENSEARCH_API = THBWIKI_BASE_URL + "/api.php?action=opensearch&search=%s&format=json&limit=10";
    private static final long MIN_REQUEST_INTERVAL_MS = 1500;

    private final ThbwikiCacheService cacheService;
    private final ObjectMapper objectMapper;
    private final Clock clock = Clock.systemUTC();

    /** Functional interface for HTTP execution — enables test mocking. */
    private ThbwikiHttpClient httpClient = url -> HttpRequest.get(url)
            .header("User-Agent", "tongrenlu/1.0 (同人音乐库管理)")
            .timeout(10000)
            .execute();

    /** Tracks the last HTTP request timestamp for rate limiting. */
    private final AtomicReference<Instant> lastRequestTime = new AtomicReference<>();

    /**
     * Enforce minimum interval between HTTP requests to THBWiki.
     * If the last request was less than {@value #MIN_REQUEST_INTERVAL_MS}ms ago, sleep the difference.
     * Logs a warning when a delay is triggered.
     */
    void enforceRateLimit() {
        Instant now = Instant.now(clock);
        Instant last = lastRequestTime.get();
        if (last != null) {
            long elapsed = now.toEpochMilli() - last.toEpochMilli();
            if (elapsed < MIN_REQUEST_INTERVAL_MS) {
                long delay = MIN_REQUEST_INTERVAL_MS - elapsed;
                log.warn("Rate limit triggered: waiting {}ms before next request", delay);
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Rate limit sleep interrupted");
                }
            }
        }
        lastRequestTime.set(Instant.now(clock));
    }

    void setHttpClient(ThbwikiHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * 根据专辑名搜索 THBWiki
     *
     * @param albumName 专辑名称
     * @return 匹配的专辑列表（包含名称和 URL）
     */
    public List<ThbwikiAlbum> searchAlbum(String albumName) {
        if (!StringUtils.hasText(albumName)) {
            log.warn("Search attempted with empty album name");
            return List.of();
        }

        String encodedName = URLEncoder.encode(albumName.trim(), StandardCharsets.UTF_8);
        String apiUrl = String.format(OPENSEARCH_API, encodedName);

        log.info("Searching THBWiki: {}", apiUrl);

        enforceRateLimit();

        try (HttpResponse response = httpClient.execute(apiUrl)) {

            if (!response.isOk()) {
                log.error("THBWiki API returned status: {}", response.getStatus());
                return List.of();
            }

            String body = response.body();
            return parseOpenSearchResponse(body);

        } catch (Exception e) {
            log.error("Error searching THBWiki for album: {}", albumName, e);
            return List.of();
        }
    }

    /**
     * 解析 MediaWiki OpenSearch API 返回的 JSON 数组
     *
     * 响应格式: ["AlbumName", "url1", "url2", ...]
     * 第一项是搜索词，后续是匹配的 URL 列表
     */
    private List<ThbwikiAlbum> parseOpenSearchResponse(String json) {
        List<ThbwikiAlbum> results = new ArrayList<>();

        try {
            List<?> items = objectMapper.readValue(json, List.class);
            if (items.size() < 2) {
                return results;
            }

            // items[0] = 搜索词, items[1] = URL 数组
            Object urls = items.get(1);
            if (urls instanceof List<?> urlList) {
                for (Object url : urlList) {
                    if (url instanceof String urlStr) {
                        ThbwikiAlbum album = new ThbwikiAlbum();
                        // 从 URL 提取专辑名作为显示名称
                        album.setName(extractTitleFromUrl(urlStr));
                        album.setUrl(urlStr);
                        results.add(album);
                    }
                }
            }

            log.info("Found {} results from THBWiki", results.size());

        } catch (Exception e) {
            log.error("Error parsing OpenSearch response: {}", json, e);
        }

        return results;
    }

    /**
     * 从 THBWiki URL 提取专辑标题
     * 例如: https://thbwiki.cc/Satori_Maiden -> Satori Maiden
     */
    private String extractTitleFromUrl(String url) {
        if (url == null) {
            return "";
        }
        // 移除基础 URL 和下划线
        String title = url.substring(url.lastIndexOf('/') + 1);
        return title.replace('_', ' ');
    }

    /**
     * Execute an HTTP GET with automatic retry and exponential backoff.
     * Calls {@link #enforceRateLimit()} before every attempt.
     * Retries on 429 (Too Many Requests) or any non-IOException network exception.
     * Does NOT retry on non-OK non-429 responses — those propagate immediately.
     *
     * @param url the URL to fetch
     * @param maxRetries the maximum number of retries (total attempts = maxRetries + 1)
     * @return the successful HTTP response; never null
     * @throws RuntimeException wrapping the underlying cause after all retries are exhausted
     */
    HttpResponse fetchWithRetry(String url, int maxRetries) {
        Exception lastCause = null;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            enforceRateLimit();
            try {
                HttpResponse response = httpClient.execute(url);
                if (response.getStatus() == 429) {
                    if (attempt < maxRetries) {
                        long backoffMs = getBackoffMillis(attempt);
                        log.warn("THBWiki rate-limited (429) on attempt {}/{}, backing off {}ms before retry",
                                attempt + 1, maxRetries + 1, backoffMs);
                        sleepQuietly(backoffMs);
                    }
                    // Continue to next iteration to retry
                } else {
                    // Return immediately regardless of status — caller decides what to do with it
                    return response;
                }
            } catch (Exception e) {
                lastCause = e;
                if (attempt < maxRetries) {
                    long backoffMs = getBackoffMillis(attempt);
                    log.warn("THBWiki request failed on attempt {}/{} ({}), backing off {}ms before retry",
                            attempt + 1, maxRetries + 1, e.getClass().getSimpleName(), backoffMs);
                    sleepQuietly(backoffMs);
                }
            }
        }
        // All retries exhausted
        log.error("THBWiki request failed after {} retries for URL: {}", maxRetries + 1, url);
        if (lastCause != null) {
            throw new RuntimeException("THBWiki request failed after " + (maxRetries + 1)
                    + " attempts: " + url, lastCause);
        }
        throw new RuntimeException("THBWiki request failed after " + (maxRetries + 1) + " attempts: " + url);
    }

    /**
     * Compute exponential backoff in milliseconds: 1s, 2s, 4s, …
     * Exposed for unit testing.
     */
    long getBackoffMillis(int attempt) {
        return (long) Math.pow(2, attempt) * 1000L;
    }

    /** Sleep without propagating InterruptedException. */
    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Backoff sleep interrupted");
        }
    }

    private static final String DETAIL_CACHE_KEY_PREFIX = "detail:";
    private static final double MIN_CONFIDENCE_THRESHOLD = 0.85;
    private static final LevenshteinDistance LEVENSHTEIN = new LevenshteinDistance(Integer.MAX_VALUE);

    private TrackMapper trackMapper;

    /**
     * 获取缓存的专辑
     */
    public Optional<ThbwikiAlbum> getCachedAlbum(String key) {
        return cacheService.get(key);
    }

    /**
     * 缓存专辑
     */
    public void cacheAlbum(String key, ThbwikiAlbum album) {
        cacheService.put(key, album);
    }

    /**
     * 根据专辑页面 URL 获取专辑详情（包含曲目列表）
     * 先检查缓存，命中则直接返回；未命中则抓取并缓存结果。
     *
     * @param url THBWiki 专辑页面 URL
     * @return 专辑详情（包含曲目列表），失败时返回空 Optional
     */
    public Optional<ThbwikiAlbum> fetchAlbumDetail(String url) {
        // URL validation rejects invalid URLs before cache lookup
        if (!isValidThbwikiUrl(url)) {
            log.warn("Invalid THBWiki URL: {}", url);
            return Optional.empty();
        }

        // Check cache first
        String cacheKey = DETAIL_CACHE_KEY_PREFIX + url;
        Optional<ThbwikiAlbum> cached = cacheService.get(cacheKey);
        if (cached.isPresent()) {
            log.debug("Cache hit for album detail: {}", url);
            return cached;
        }

        enforceRateLimit();

        try (HttpResponse response = httpClient.execute(url)) {

            if (!response.isOk()) {
                log.warn("THBWiki page returned status: {} for URL: {}", response.getStatus(), url);
                return Optional.empty();
            }

            String html = response.body();
            Document doc = org.jsoup.Jsoup.parse(html);
            Optional<ThbwikiAlbum> result = parseAlbumDetail(doc, url);

            // Cache successful parse result
            result.ifPresent(album -> {
                cacheService.put(cacheKey, album);
                log.debug("Cached album detail: {} (key: {})", album.getName(), cacheKey);
            });

            return result;

        } catch (Exception e) {
            log.warn("Error fetching THBWiki page: {}", url, e);
            return Optional.empty();
        }
    }

    /**
     * 获取缓存统计信息
     *
     * @return Caffeine CacheStats 字符串表示
     */
    public String getCacheStats() {
        return cacheService.getStats();
    }

    /**
     * 手动清除指定专辑的缓存
     *
     * @param url THBWiki 专辑页面 URL
     */
    public void invalidateAlbumDetail(String url) {
        if (!isValidThbwikiUrl(url)) {
            log.warn("Invalid THBWiki URL for cache invalidation: {}", url);
            return;
        }
        String cacheKey = DETAIL_CACHE_KEY_PREFIX + url;
        cacheService.invalidate(cacheKey);
        log.debug("Invalidated cache for album detail: {}", url);
    }

    /**
     * 验证 URL 是否为有效的 THBWiki URL
     */
    private boolean isValidThbwikiUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return false;
        }
        // Must start with THBWiki base URL
        if (!url.startsWith(THBWIKI_BASE_URL)) {
            return false;
        }
        // Length limit
        if (url.length() > 500) {
            return false;
        }
        // No control characters
        if (url.contains("\n") || url.contains("\r") || url.contains("\t")) {
            return false;
        }
        return true;
    }

    /**
     * 解析专辑页面 HTML，提取专辑信息和曲目列表
     */
    Optional<ThbwikiAlbum> parseAlbumDetail(Document doc, String url) {
        // Extract album name from page title
        String albumName = "";
        Element titleElement = doc.selectFirst(".mw-page-title-main");
        if (titleElement != null) {
            albumName = TextNormalizer.normalize(titleElement.text());
        }

        // Parse tracks
        List<ThbwikiTrack> tracks = parseTracks(doc);

        // Create album
        ThbwikiAlbum album = new ThbwikiAlbum();
        album.setName(albumName);
        album.setUrl(url);
        album.setTracks(tracks);

        if (tracks.isEmpty()) {
            log.warn("No tracks parsed from album page: {}", url);
        }

        return Optional.of(album);
    }

    /**
     * 解析曲目列表
     */
    List<ThbwikiTrack> parseTracks(Document doc) {
        List<ThbwikiTrack> tracks = new ArrayList<>();

        // Try multiple CSS selector strategies (fallback pattern)
        Elements rows = doc.select("#musicTable tr");
        if (rows.isEmpty()) {
            rows = doc.select(".wikitable.musicTable tr");
        }
        if (rows.isEmpty()) {
            rows = doc.select(".wikitable tr");
        }

        for (Element row : rows) {
            ThbwikiTrack track = parseTrackRow(row);
            if (track != null) {
                tracks.add(track);
            }
        }

        return tracks;
    }

    /**
     * 解析单行曲目数据
     */
    ThbwikiTrack parseTrackRow(Element row) {
        Elements cells = row.select("td");
        if (cells.isEmpty()) {
            return null;
        }

        ThbwikiTrack track = new ThbwikiTrack();
        // Track name from first cell
        track.setName(TextNormalizer.normalize(cells.get(0).text()));

        // Original source from second cell (index 1)
        if (cells.size() > 1) {
            Element ogmusic = cells.get(1).selectFirst(".ogmusic");
            if (ogmusic != null) {
                Element source = ogmusic.selectFirst(".source");
                if (source != null) {
                    track.setOriginalSource(TextNormalizer.normalize(source.text()));
                    String href = source.attr("href");
                    if (!href.startsWith("http")) {
                        href = THBWIKI_BASE_URL + href;
                    }
                    track.setOriginalUrl(href);
                    // Original name: ogmusic text minus source text
                    String originalName = ogmusic.text().replace(source.text(), "").trim();
                    track.setOriginalName(TextNormalizer.normalize(originalName));
                }
            }
        }

        return track;
    }

    public void setTrackMapper(TrackMapper trackMapper) {
        this.trackMapper = trackMapper;
    }

    /**
     * Match a track name against a list of THBWiki tracks using Levenshtein distance.
     * Uses TextNormalizer.normalizeForComparison() to normalize both strings before comparison.
     *
     * @param trackName the local track name to match
     * @param thbwikiTracks the list of THBWiki tracks to match against
     * @return the best match result with confidence score
     */
    public MatchResult matchTrack(String trackName, java.util.List<ThbwikiTrack> thbwikiTracks) {
        if (trackName == null || trackName.isBlank()) {
            log.debug("Match attempted with empty track name");
            return MatchResult.noMatch(null);
        }
        if (thbwikiTracks == null || thbwikiTracks.isEmpty()) {
            log.debug("Match attempted with empty THBWiki track list");
            return MatchResult.noMatch(TextNormalizer.normalizeForComparison(trackName));
        }

        String normalizedInput = TextNormalizer.normalizeForComparison(trackName);
        MatchResult bestMatch = MatchResult.noMatch(normalizedInput);

        for (ThbwikiTrack thbwikiTrack : thbwikiTracks) {
            String candidateName = thbwikiTrack.getName();
            if (candidateName == null || candidateName.isBlank()) {
                continue;
            }

            String normalizedCandidate = TextNormalizer.normalizeForComparison(candidateName);
            double confidence = calculateConfidence(normalizedInput, normalizedCandidate);

            if (confidence >= MIN_CONFIDENCE_THRESHOLD && confidence > bestMatch.getConfidence()) {
                bestMatch = MatchResult.matched(
                        normalizedInput,
                        normalizedCandidate,
                        thbwikiTrack,
                        confidence
                );
                log.debug("New best match found: '{}' matched '{}' with confidence {}",
                        normalizedInput, normalizedCandidate, String.format("%.2f", confidence));
            }
        }

        if (!bestMatch.isMatched()) {
            log.debug("No match found for track: {} (best candidate was '{}' with confidence {})",
                    trackName, bestMatch.getNormalizedMatch(), String.format("%.2f", bestMatch.getConfidence()));
        }

        return bestMatch;
    }

    /**
     * Calculate confidence score based on Levenshtein distance.
     * Returns 1.0 for exact match, decreasing as edit distance increases.
     * Uses normalized strings for comparison.
     *
     * @param normalizedInput the normalized input string
     * @param normalizedCandidate the normalized candidate string
     * @return confidence score between 0.0 and 1.0
     */
    double calculateConfidence(String normalizedInput, String normalizedCandidate) {
        if (normalizedInput == null || normalizedCandidate == null) {
            return 0.0;
        }
        if (normalizedInput.equals(normalizedCandidate)) {
            return 1.0;
        }

        int maxLength = Math.max(normalizedInput.length(), normalizedCandidate.length());
        if (maxLength == 0) {
            return 1.0;
        }

        int distance = LEVENSHTEIN.apply(normalizedInput, normalizedCandidate);
        return 1.0 - ((double) distance / maxLength);
    }

    /**
     * Match a track against THBWiki album tracks and save the original info to database.
     * Logs the input track name, matched THBWiki track, confidence score, and save outcome.
     *
     * @param track the local track to match and update
     * @param thbwikiTracks the list of THBWiki tracks to match against
     * @return true if a match was found and saved (confidence >= 0.85)
     */
    public boolean matchAndSave(TrackBean track, java.util.List<ThbwikiTrack> thbwikiTracks) {
        if (track == null) {
            log.info("Track is null, skipping match");
            return false;
        }

        String trackName = track.getName();
        log.info("Matching track: {}", trackName);

        MatchResult result = matchTrack(trackName, thbwikiTracks);

        if (result.isMatched()) {
            ThbwikiTrack matchedThbwikiTrack = result.getThbwikiTrack();
            String originalInfo = buildOriginalInfo(matchedThbwikiTrack);
            String originalUrl = matchedThbwikiTrack.getOriginalUrl();

            log.info("Track '{}' matched to THBWiki '{}' with confidence {}, saving original: '{}', originalUrl: '{}'",
                    trackName,
                    matchedThbwikiTrack.getName(),
                    String.format("%.2f", result.getConfidence()),
                    originalInfo,
                    originalUrl);

            track.setOriginal(originalInfo);
            track.setOriginalUrl(originalUrl);
            if (this.trackMapper != null) {
                this.trackMapper.updateById(track);
                log.info("Successfully saved original '{}' originalUrl '{}' to track id={}", originalInfo, originalUrl, track.getId());
            } else {
                log.warn("TrackMapper not set, cannot persist match for track id={}", track.getId());
            }
            return true;
        } else {
            log.info("Track '{}' had no match (best confidence: {}), skipping save",
                    trackName, String.format("%.2f", result.getConfidence()));
            return false;
        }
    }

    /**
     * Build a formatted original info string from a THBWiki track.
     * Format: "原曲出处 - 原曲名称" or just "原曲出处" if name is missing.
     */
    private String buildOriginalInfo(ThbwikiTrack thbwikiTrack) {
        String source = thbwikiTrack.getOriginalSource();
        String name = thbwikiTrack.getOriginalName();

        if (source != null && name != null && !name.isBlank()) {
            return source + " - " + name;
        } else if (source != null && !source.isBlank()) {
            return source;
        } else if (name != null && !name.isBlank()) {
            return name;
        }
        return "";
    }
}
