package info.tongrenlu.www;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import info.tongrenlu.domain.ArticleBean;
import info.tongrenlu.model.CloudAlbumSearchResponse;
import info.tongrenlu.model.CloudAlbumSearchResult;
import info.tongrenlu.model.CloudMusicAlbum;
import info.tongrenlu.service.HomeMusicService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 未发布专辑管理控制器
 * 提供对 publishFlg=0 或 2 的专辑进行修改和发布的功能
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
@Slf4j
public class AdminUnpublishController {

    private final HomeMusicService musicService;

    /**
     * 获取未发布专辑列表（publishFlg=0 或 2）
     * 按id正向排序，分页显示
     *
     * @param pageNumber 页码，从1开始
     * @param pageSize   每页数量，默认20
     * @return 分页的未发布专辑列表
     */
    @GetMapping("/unpublished-list")
    public ResponseEntity<Map<String, Object>> getUnpublishedList(
            @RequestParam(defaultValue = "1") int pageNumber,
            @RequestParam(defaultValue = "20") int pageSize) {

        Map<String, Object> response = new HashMap<>();

        try {
            Page<ArticleBean> albums = this.musicService.getUnpublishedAlbums(pageNumber, pageSize);

            response.put("success", true);
            response.put("data", albums);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error getting unpublished albums", e);
            response.put("success", false);
            response.put("message", "获取未发布专辑列表失败");
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 搜索网易云音乐接口
     * 根据关键词搜索专辑/音乐
     *
     * @param keyword 搜索关键词
     * @return 搜索结果列表
     */
    @GetMapping("/search-cloud-music")
    public ResponseEntity<Map<String, Object>> searchCloudMusic(@RequestParam String keyword) {
        Map<String, Object> response = new HashMap<>();

        try {
            String url = UriComponentsBuilder.fromUriString("https://apis.netstart.cn/music/cloudsearch")
                    .queryParam("keywords", URLEncoder.encode(keyword, StandardCharsets.UTF_8))
                    .queryParam("limit", 30)
                    .queryParam("offset", 0)
                    .queryParam("type", 10)
                    .build()
                    .toString();

            log.info("Searching cloud music: {}", url);

            try (HttpResponse httpResponse = HttpRequest.get(url).execute()) {
                String json = httpResponse.body();

                ObjectMapper objectMapper = new ObjectMapper();
                CloudAlbumSearchResponse musicDetailResponse = objectMapper.readValue(json, CloudAlbumSearchResponse.class);
                if (musicDetailResponse == null) {
                    return null;
                }

                int code = musicDetailResponse.getCode();
                if (code != 200) {
                    return null;
                }
                CloudAlbumSearchResult result = musicDetailResponse.getResult();
                List<CloudMusicAlbum> albums = result.getAlbums();
                response.put("success", true);
                response.put("data", albums != null ? albums : List.of());
                return ResponseEntity.ok(response);
            }

        } catch (IOException e) {
            log.error("Error searching cloud music", e);
            response.put("success", false);
            response.put("message", "搜索失败:" + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        } catch (Exception e) {
            log.error("Unexpected error", e);
            response.put("success", false);
            response.put("message", "未知错误:" + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 更新专辑信息并发布
     * 更新专辑的云音乐ID、标题、图片URL，并将publishFlg设置为1
     *
     * @param albumId          专辑ID
     * @param cloudMusicId     云音乐ID
     * @param title            专辑标题
     * @param cloudMusicPicUrl 云音乐图片URL
     * @return 操作结果
     */
    @PostMapping("/update-album")
    public ResponseEntity<Map<String, Object>> updateAlbum(
            @RequestParam Long albumId,
            @RequestParam Long cloudMusicId,
            @RequestParam String title,
            @RequestParam(required = false) String cloudMusicPicUrl) {

        Map<String, Object> response = new HashMap<>();

        try {
            boolean success = this.musicService.updateAlbum(albumId, cloudMusicId, title, cloudMusicPicUrl);

            if (success) {
                response.put("success", true);
                response.put("message", "专辑更新并发布成功");
                log.info("Album {} updated and published with cloudMusicId {}", albumId, cloudMusicId);
                return ResponseEntity.ok(response);
            } else {
                response.put("success", false);
                response.put("message", "专辑更新失败，请检查专辑ID是否正确");
                return ResponseEntity.badRequest().body(response);
            }

        } catch (Exception e) {
            log.error("Error updating album {}", albumId, e);
            response.put("success", false);
            response.put("message", "更新失败:" + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 标记专辑为无匹配状态
     * 将publishFlg设置为2，表示该专辑在网易云音乐中没有对应的结果
     *
     * @param albumId 专辑ID
     * @return 操作结果
     */
    @PostMapping("/mark-no-match")
    public ResponseEntity<Map<String, Object>> markAsNoMatch(@RequestParam Long albumId) {
        Map<String, Object> response = new HashMap<>();

        try {
            boolean success = this.musicService.markAsNoMatch(albumId);

            if (success) {
                response.put("success", true);
                response.put("message", "专辑已标记为无匹配状态");
                log.info("Album {} marked as no match (publishFlg=2)", albumId);
                return ResponseEntity.ok(response);
            } else {
                response.put("success", false);
                response.put("message", "操作失败，请检查专辑ID是否正确");
                return ResponseEntity.badRequest().body(response);
            }

        } catch (Exception e) {
            log.error("Error marking album {} as no match", albumId, e);
            response.put("success", false);
            response.put("message", "操作失败:" + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
}
