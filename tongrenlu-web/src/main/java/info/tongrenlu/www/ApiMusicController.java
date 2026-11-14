package info.tongrenlu.www;

import cn.hutool.core.io.IORuntimeException;
import cn.hutool.http.HttpException;
import cn.hutool.http.HttpResponse;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import info.tongrenlu.config.FoliaConfig;
import info.tongrenlu.domain.ArticleBean;
import info.tongrenlu.domain.AlbumDetailBean;
import info.tongrenlu.domain.ArtistBean;
import info.tongrenlu.domain.TrackBean;
import info.tongrenlu.model.CloudMusicDetailResponse;
import info.tongrenlu.model.CloudMusicSongDownloadData;
import info.tongrenlu.model.CloudMusicSongDownloadResponse;
import info.tongrenlu.service.ArtistService;
import info.tongrenlu.service.HomeMusicService;
import info.tongrenlu.service.WikiService;
import info.tongrenlu.support.CloudMusicHttpClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/music")
@Slf4j
public class ApiMusicController {

    private final HomeMusicService musicService;
    private final ArtistService artistService;
    private final WikiService wikiService;
    private final FoliaConfig foliaConfig;

    /**
     * 网易云外网调用 HTTP 客户端：统一设置连接/读取超时，避免上游卡住时请求线程无限期挂起。
     */
    private final CloudMusicHttpClient cloudMusicHttpClient = new CloudMusicHttpClient();

    @GetMapping("search")
    public Page<ArticleBean> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "1") int pageNumber,
            @RequestParam(required = false, defaultValue = "30") int pageSize,
            @RequestParam(required = false, defaultValue = "publishDate") String orderBy,
            @RequestParam(required = false) String tag) {
        return this.musicService.searchMusic(keyword, pageNumber, pageSize, orderBy, tag);
    }

    @GetMapping("detail")
    public AlbumDetailBean detail(@RequestParam Long albumId) {
        return this.musicService.getAlbumDetail(albumId);
    }

    @GetMapping("track")
    public ResponseEntity<CloudMusicDetailResponse> getTrackById(@RequestParam Long id) {
        TrackBean track = musicService.getTrackById(id);
        if (track == null) {
            return ResponseEntity.notFound().build();
        }

        CloudMusicSongDownloadResponse cloudMusicSongDownloadResponse = getMusicDetailResponse2(track);
        if (cloudMusicSongDownloadResponse == null || cloudMusicSongDownloadResponse.getCode() != 200) {
            return ResponseEntity.notFound().build();
        }

        CloudMusicSongDownloadData data = cloudMusicSongDownloadResponse.getData();

        CloudMusicDetailResponse  musicDetailResponse = new CloudMusicDetailResponse();
        musicDetailResponse.setUrl(data.getUrl());
        musicDetailResponse.setName(track.getName());
        musicDetailResponse.setAlName(track.getAlbum());
        musicDetailResponse.setArName(track.getArtist());
        musicDetailResponse.setLevel(data.getLevel());

        return ResponseEntity.ok(musicDetailResponse);
    }

    @GetMapping("/album-stats")
    public ResponseEntity<Map<String, Object>> getAlbumStats() {
        Map<String, Object> response = new HashMap<>();
        try {
            Map<String, Long> stats = this.musicService.getAlbumStats();
            response.put("success", true);
            response.put("data", stats);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting album stats", e);
            response.put("success", false);
            response.put("message", "获取数据失败");
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @PostMapping("/report-error")
    public ResponseEntity<Map<String, Object>> reportError(@RequestParam Long albumId) {
        Map<String, Object> response = new HashMap<>();

        try {
            boolean success = this.musicService.reportAlbumError(albumId);
            if (success) {
                response.put("success", true);
                response.put("message", "Album error reported successfully");
                log.info("Album {} publish flag updated to 0", albumId);
                return ResponseEntity.ok(response);
            } else {
                response.put("success", false);
                response.put("message", "Album not found or update failed");
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            log.error("Error reporting album {} error", albumId, e);
            response.put("success", false);
            response.put("message", "Internal server error");
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @GetMapping("/random")
    public ResponseEntity<AlbumDetailBean> getRandomAlbum() {
        AlbumDetailBean randomAlbum = this.musicService.getRandomAlbum();
        if (randomAlbum == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(randomAlbum);
    }

    @GetMapping("/random-albums")
    public ResponseEntity<List<ArticleBean>> getRandomAlbums(
            @RequestParam(defaultValue = "15") int count) {
        List<ArticleBean> randomAlbums = this.musicService.getRandomAlbums(count);
        return ResponseEntity.ok(randomAlbums);
    }

    @GetMapping("/tags")
    public ResponseEntity<List<Map<String, Object>>> getPopularTags(
            @RequestParam(defaultValue = "10") int limit) {
        List<Map<String, Object>> tags = this.musicService.getPopularTags(limit);
        return ResponseEntity.ok(tags);
    }

    @GetMapping("track-search")
    public ResponseEntity<Map<String, Object>> searchTracks(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "30") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        List<Map<String, Object>> tracks = this.musicService.searchTracks(keyword, limit, offset);
        long total = this.musicService.countSearchTracks(keyword);
        Map<String, Object> result = new HashMap<>();
        result.put("items", tracks);
        result.put("total", total);
        result.put("hasMore", offset + tracks.size() < total);
        result.put("nextOffset", offset + tracks.size());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/artists")
    public ResponseEntity<Map<String, Object>> getArtists(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "30") int limit) {
        Map<String, Object> result = this.artistService.getArtistsWithAlbumCountPaged(keyword, page, limit);
        return ResponseEntity.ok(result);
    }

    /**
     * 艺术家 Wiki 详情：按 id 或 name 查询，含简介、生成的 wiki 内容与互联标签。
     * wiki 正文统一由 WikiService 从 m_tag 词条提供（合并后的 Wiki 模块），
     * 迁移前回落 m_artist 旧列，最终回落网易云简介。
     */
    @GetMapping("/artist-detail")
    public ResponseEntity<Map<String, Object>> getArtistDetail(
            @RequestParam(required = false) Long id,
            @RequestParam(required = false) String name) {
        ArtistBean artist = this.artistService.getArtistDetail(id, name);
        if (artist == null) {
            return ResponseEntity.notFound().build();
        }
        Map<String, Object> result = new HashMap<>();
        result.put("id", artist.getId());
        result.put("name", artist.getName());
        result.put("description", artist.getDescription());
        result.put("cloudMusicPicUrl", artist.getCloudMusicPicUrl());
        result.put("cloudMusicId", artist.getCloudMusicId());
        result.put("albumCount", artist.getAlbumCount());

        WikiService.WikiFields wiki = wikiService.getWikiFields(artist.getTagId());
        boolean generated = StringUtils.isNotBlank(wiki.content())
                || StringUtils.isNotBlank(artist.getWikiContent());
        String content = StringUtils.isNotBlank(wiki.content())
                ? wiki.content()
                : (StringUtils.isNotBlank(artist.getWikiContent())
                        ? artist.getWikiContent() : artist.getDescription());
        result.put("wikiContent", content);
        result.put("wikiIsGenerated", generated);
        result.put("wikiTags", wiki.tags().isEmpty()
                ? wikiService.parseTags(artist.getWikiTags()) : wiki.tags());
        result.put("thbWikiUrl", StringUtils.isNotBlank(wiki.sourceUrl())
                ? wiki.sourceUrl() : artist.getThbWikiUrl());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/folia-album-url")
    public ResponseEntity<Map<String, Object>> getFoliaAlbumUrl(@RequestParam Long albumId) {
        Map<String, Object> result = new HashMap<>();
        result.put("url", foliaConfig.albumUrl(albumId));
        return ResponseEntity.ok(result);
    }

    private CloudMusicDetailResponse getMusicDetailResponseKXZ(TrackBean track) {
        return null;
    }

    private CloudMusicSongDownloadResponse getMusicDetailResponse2(TrackBean track) {
        //https://apis.netstart.cn/music/song/download/url?id=765822
        String url = UriComponentsBuilder.fromUriString("https://music.163.com/song/media/outer/url")
                .queryParam("id", track.getCloudMusicId() + ".mp3")
                .queryParam("br", 128000)
                .queryParam("gain", 0)
                .queryParam("peak", 1)
                .build()
                .toString();
        try(HttpResponse response = cloudMusicHttpClient.get(url).execute()) {
            String location = response.header("Location");
            if (StringUtils.isBlank(location)) {
                return null;
            }

            CloudMusicSongDownloadResponse cloudMusicSongDownloadResponse = new CloudMusicSongDownloadResponse();
            CloudMusicSongDownloadData data = new CloudMusicSongDownloadData();
            data.setUrl(location);
            cloudMusicSongDownloadResponse.setData(data);
            cloudMusicSongDownloadResponse.setCode(200);
            return cloudMusicSongDownloadResponse;
        } catch (IORuntimeException | HttpException e) {
            // 超时或网络异常：降级为“无下载地址”，由调用方走既有的 404 分支，不让请求线程挂死
            log.error("网易云歌曲下载地址获取失败或超时: url={}, connectTimeout={}ms, readTimeout={}ms, error={}",
                    url, cloudMusicHttpClient.getConnectTimeoutMs(), cloudMusicHttpClient.getReadTimeoutMs(),
                    e.getMessage(), e);
            return null;
        }
    }
}