package info.tongrenlu.www;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import info.tongrenlu.domain.ArticleBean;
import info.tongrenlu.domain.ArtistBean;
import info.tongrenlu.model.ArtistAlbumRequest;
import info.tongrenlu.model.CloudMusicAlbum;
import info.tongrenlu.model.CloudMusicArtistAlbumResponse;
import info.tongrenlu.model.CloudMusicSearchArtistResponse;
import info.tongrenlu.service.ArticleService;
import info.tongrenlu.service.ArtistService;
import info.tongrenlu.service.HomeMusicService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/artist")
@Slf4j
public class AdminArtistController {

    private static final String CLOUD_MUSIC_API = "https://apis.netstart.cn/music";
    private static final int SEARCH_TYPE_ARTIST = 100;
    private final HomeMusicService homeMusicService;
    private final ArticleService articleService;
    private final ArtistService artistService;

    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> searchArtists(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "30") int limit) {

        Map<String, Object> response = new HashMap<>();

        try {
            int offset = (page - 1) * limit;

            String url = UriComponentsBuilder.fromUriString(CLOUD_MUSIC_API + "/cloudsearch")
                    .queryParam("keywords", URLEncoder.encode(keyword, StandardCharsets.UTF_8))
                    .queryParam("limit", limit)
                    .queryParam("offset", offset)
                    .queryParam("type", SEARCH_TYPE_ARTIST)
                    .build()
                    .toString();

            log.info("Searching artists: {}", url);

            try (HttpResponse httpResponse = HttpRequest.get(url).execute()) {
                String json = httpResponse.body();
                CloudMusicSearchArtistResponse searchResponse = new ObjectMapper()
                        .readValue(json, CloudMusicSearchArtistResponse.class);

                if (searchResponse == null || searchResponse.getCode() != 200) {
                    response.put("success", false);
                    response.put("message", "搜索失败");
                    return ResponseEntity.badRequest().body(response);
                }

                Map<String, Object> pageData = new HashMap<>();
                if (searchResponse.getResult() != null) {
                    pageData.put("records", searchResponse.getResult().getArtists());
                    pageData.put("total", searchResponse.getResult().getArtistCount());
                    pageData.put("pages", (int) Math.ceil(
                            (double) searchResponse.getResult().getArtistCount() / limit));
                    pageData.put("current", page);
                } else {
                    pageData.put("records", List.of());
                    pageData.put("total", 0);
                    pageData.put("pages", 1);
                    pageData.put("current", page);
                }

                response.put("success", true);
                response.put("data", pageData);
                return ResponseEntity.ok(response);
            }

        } catch (Exception e) {
            log.error("Error searching artists", e);
            response.put("success", false);
            response.put("message", "搜索失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 分页查询本地歌手列表
     *
     * @param keyword 搜索关键词（可选）
     * @param page    页码，默认1
     * @param limit   每页数量，默认30
     * @return 分页歌手列表数据
     */
    @GetMapping("/list")
    public ResponseEntity<Map<String, Object>> getArtistList(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int limit) {

        Map<String, Object> response = new HashMap<>();

        try {
            Page<ArtistBean> resultPage = artistService.getArtistList(keyword, page, limit);

            Map<String, Object> pageData = new HashMap<>();
            pageData.put("records", resultPage.getRecords());
            pageData.put("total", resultPage.getTotal());
            pageData.put("pages", resultPage.getPages());
            pageData.put("current", resultPage.getCurrent());
            pageData.put("size", resultPage.getSize());

            response.put("success", true);
            response.put("data", pageData);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error getting artist list", e);
            response.put("success", false);
            response.put("message", "获取歌手列表失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @PostMapping("/albums")
    public ResponseEntity<Map<String, Object>> getArtistAlbums(@RequestBody ArtistAlbumRequest request) {
        Map<String, Object> response = new HashMap<>();

        Long id = request.getId();
        if (id == null) {
            response.put("success", false);
            response.put("message", "歌手ID不能为空");
            return ResponseEntity.badRequest().body(response);
        }

        ArtistBean artistBean = this.artistService.getById(id);


        try {
            long limit = request.getLimit() != null ? request.getLimit() : 30;
            long offset = request.getOffset() != null ? request.getOffset() : 0;

            String url = UriComponentsBuilder.fromUriString(CLOUD_MUSIC_API + "/artist/album")
                    .queryParam("id", artistBean.getCloudMusicId())
                    .queryParam("limit", limit)
                    .queryParam("offset", offset)
                    .build()
                    .toString();

            log.info("Getting artist albums: {}", url);

            try (HttpResponse httpResponse = HttpRequest.get(url).execute()) {
                String json = httpResponse.body();
                CloudMusicArtistAlbumResponse albumResponse = new ObjectMapper()
                        .readValue(json, CloudMusicArtistAlbumResponse.class);

                if (albumResponse == null || albumResponse.getCode() != 200) {
                    response.put("success", false);
                    response.put("message", "获取歌手专辑失败");
                    return ResponseEntity.badRequest().body(response);
                }


                Map<String, Object> result = new HashMap<>();
                result.put("artist", albumResponse.getArtist());
                List<CloudMusicAlbum> hotAlbums = albumResponse.getHotAlbums();
                hotAlbums.forEach(cloudMusicAlbum -> {
                            long cloudMusicAlbumId = cloudMusicAlbum.getId();
                            ArticleBean existingAlbum = articleService.getByCloudMusicId(cloudMusicAlbumId);
                            if (existingAlbum != null) {
                                cloudMusicAlbum.setExistsInDb(true);
                            }
                        });

                result.put("albums", hotAlbums);
                result.put("more", albumResponse.isMore());

                response.put("success", true);
                response.put("data", result);
                return ResponseEntity.ok(response);
            }

        } catch (Exception e) {
            log.error("Error getting artist albums", e);
            response.put("success", false);
            response.put("message", "获取歌手专辑失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @PostMapping("/save-album")
    public ResponseEntity<Map<String, Object>> saveAlbum(@RequestBody CloudMusicAlbum cloudMusicAlbum) {
        Map<String, Object> response = new HashMap<>();

        try {
            Long cloudMusicId = cloudMusicAlbum.getId();

            ArticleBean existingAlbum = articleService.getByCloudMusicId(cloudMusicId);
            if (existingAlbum != null) {
                response.put("success", false);
                response.put("message", "专辑已存在于数据库中");
                return ResponseEntity.badRequest().body(response);
            }

            homeMusicService.saveCloudMusicAlbum(cloudMusicId);

            response.put("success", true);
            response.put("message", "专辑保存成功");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error saving album", e);
            response.put("success", false);
            response.put("message", "保存专辑失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

}
