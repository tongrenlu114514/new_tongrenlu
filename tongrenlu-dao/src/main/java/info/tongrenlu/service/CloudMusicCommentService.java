package info.tongrenlu.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import info.tongrenlu.support.CloudMusicHttpClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 网易云专辑推荐评论抓取：调用新版评论接口 {@code /comment/new}，
 * 以 albumId（云音乐专辑 id）取「按推荐排序」的评论，作为 OKF 专辑词条的 wiki 素材。
 *
 * <p>接口：{@code /comment/new?id=<albumId>&type=3&sortType=1&pageNo=1&pageSize=<limit>}
 * <ul>
 *   <li>type=3 表示专辑；sortType=1 按推荐排序；</li>
 *   <li>评论正文在 {@code comments[].content}，昵称在 {@code comments[].user.nickname}。</li>
 * </ul>
 * 所有网络/解析失败均降级为空列表，绝不影响专辑词条的正常生成。
 */
@Service
@Slf4j
public class CloudMusicCommentService {

    /** 网易云接口基址，与 HomeMusicService 共用同一配置。 */
    @Value("${app.cloud-music-api.base:http://127.0.0.1:3000}")
    private String cloudMusicApiBase;

    private final CloudMusicHttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public CloudMusicCommentService(ObjectMapper objectMapper) {
        this(new CloudMusicHttpClient(), objectMapper);
    }

    CloudMusicCommentService(CloudMusicHttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    /** 一条推荐评论。 */
    public record Comment(String nickname, String content) {
    }

    /**
     * 取专辑推荐评论。失败返回空列表。
     *
     * @param albumId 云音乐专辑 id（m_article.cloud_music_id）
     * @param limit   最多返回条数
     */
    public List<Comment> fetchRecommendedComments(String albumId, int limit) {
        if (!StringUtils.hasText(albumId) || limit <= 0) {
            return List.of();
        }
        String url = commentUrl(albumId.trim(), limit);
        String body;
        try {
            body = httpClient.get(url).execute().body();
        } catch (Exception e) {
            log.warn("拉取网易云专辑推荐评论失败（降级为空）: url={}, error={}", url, e.getMessage());
            return List.of();
        }
        return parseComments(body, limit);
    }

    /** 构造评论接口 URL（包内可见，便于测试）。 */
    String commentUrl(String albumId, int limit) {
        String base = StringUtils.hasText(cloudMusicApiBase)
                ? cloudMusicApiBase.trim() : "http://127.0.0.1:3000";
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String id = URLEncoder.encode(albumId, StandardCharsets.UTF_8);
        return base + "/comment/new?id=" + id
                + "&type=3&sortType=1&pageNo=1&pageSize=" + limit;
    }

    /** 解析评论响应（包内可见，便于测试）：仅取 code=200 的 comments 数组。 */
    List<Comment> parseComments(String body, int limit) {
        if (!StringUtils.hasText(body)) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            if (root.path("code").asInt(-1) != 200) {
                return List.of();
            }
            // 兼容两种响应：NeteaseCloudMusicApi 新版把评论放在 data.comments，
            // 其它实现（或旧版）可能直接放在顶层 comments。
            JsonNode comments = root.path("data").path("comments");
            if (!comments.isArray()) {
                comments = root.path("comments");
            }
            if (!comments.isArray()) {
                return List.of();
            }
            List<Comment> result = new ArrayList<>();
            for (JsonNode node : comments) {
                if (result.size() >= limit) {
                    break;
                }
                String content = node.path("content").asText("").trim();
                if (!StringUtils.hasText(content)) {
                    continue;
                }
                String nickname = node.path("user").path("nickname").asText("").trim();
                result.add(new Comment(nickname, content));
            }
            return result;
        } catch (Exception e) {
            log.warn("解析网易云评论响应失败（降级为空）: {}", e.getMessage());
            return List.of();
        }
    }
}
