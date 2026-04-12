package info.tongrenlu.service;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import info.tongrenlu.cache.ThbwikiCacheService;
import info.tongrenlu.model.ThbwikiAlbum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ThbwikiService {

    private static final String THBWIKI_BASE_URL = "https://thbwiki.cc";
    private static final String OPENSEARCH_API = THBWIKI_BASE_URL + "/api.php?action=opensearch&search=%s&format=json&limit=10";

    private final ThbwikiCacheService cacheService;
    private final ObjectMapper objectMapper;

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

        try (HttpResponse response = HttpRequest.get(apiUrl)
                .header("User-Agent", "tongrenlu/1.0 (同人音乐库管理)")
                .timeout(10000)
                .execute()) {

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
}
