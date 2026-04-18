package info.tongrenlu.service;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import info.tongrenlu.cache.ThbwikiCacheService;
import info.tongrenlu.model.ThbwikiAlbum;
import info.tongrenlu.model.ThbwikiTrack;
import lombok.extern.slf4j.Slf4j;
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
public class ThbwikiService {

    private static final String THBWIKI_BASE_URL = "https://thbwiki.cc";
    private static final String OPENSEARCH_API = THBWIKI_BASE_URL + "/api.php?action=opensearch&search=%s&format=json&limit=10";
    private static final long MIN_REQUEST_INTERVAL_MS = 1500;

    private final ThbwikiCacheService cacheService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /** Functional interface for HTTP execution — enables test mocking. */
    private ThbwikiHttpClient httpClient = url -> HttpRequest.get(url)
            .header("User-Agent", "tongrenlu/1.0 (同人音乐库管理)")
            .timeout(10000)
            .execute();

    /** Tracks the last HTTP request timestamp for rate limiting. */
    private final AtomicReference<Instant> lastRequestTime = new AtomicReference<>();

    public ThbwikiService(ThbwikiCacheService cacheService, ObjectMapper objectMapper) {
        this(cacheService, objectMapper, Clock.systemUTC());
    }

    ThbwikiService(ThbwikiCacheService cacheService, ObjectMapper objectMapper, Clock clock) {
        this.cacheService = cacheService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

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
     *
     * @param url THBWiki 专辑页面 URL
     * @return 专辑详情（包含曲目列表），失败时返回空 Optional
     */
    public Optional<ThbwikiAlbum> fetchAlbumDetail(String url) {
        // URL validation
        if (!isValidThbwikiUrl(url)) {
            log.warn("Invalid THBWiki URL: {}", url);
            return Optional.empty();
        }

        enforceRateLimit();

        try (HttpResponse response = httpClient.execute(url)) {

            if (!response.isOk()) {
                log.warn("THBWiki page returned status: {} for URL: {}", response.getStatus(), url);
                return Optional.empty();
            }

            String html = response.body();
            Document doc = org.jsoup.Jsoup.parse(html);
            return parseAlbumDetail(doc, url);

        } catch (Exception e) {
            log.warn("Error fetching THBWiki page: {}", url, e);
            return Optional.empty();
        }
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
            albumName = titleElement.text().trim();
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
        track.setName(cells.get(0).text().trim());

        // Original source from second cell (index 1)
        if (cells.size() > 1) {
            Element ogmusic = cells.get(1).selectFirst(".ogmusic");
            if (ogmusic != null) {
                Element source = ogmusic.selectFirst(".source");
                if (source != null) {
                    track.setOriginalSource(source.text().trim());
                    String href = source.attr("href");
                    if (!href.startsWith("http")) {
                        href = THBWIKI_BASE_URL + href;
                    }
                    track.setOriginalUrl(href);
                    // Original name: ogmusic text minus source text
                    String originalName = ogmusic.text().replace(source.text(), "").trim();
                    track.setOriginalName(originalName);
                }
            }
        }

        return track;
    }
}
