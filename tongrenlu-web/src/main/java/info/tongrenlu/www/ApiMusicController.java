package info.tongrenlu.www;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import info.tongrenlu.domain.ArticleBean;
import info.tongrenlu.domain.AlbumDetailBean;
import info.tongrenlu.domain.TrackBean;
import info.tongrenlu.model.CloudMusicDetailResponse;
import info.tongrenlu.model.CloudMusicSongDownloadData;
import info.tongrenlu.model.CloudMusicSongDownloadResponse;
import info.tongrenlu.service.ArtistService;
import info.tongrenlu.service.HomeMusicService;
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

    @GetMapping("/artists")
    public ResponseEntity<Map<String, Object>> getArtists(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "30") int limit) {
        Map<String, Object> result = this.artistService.getArtistsWithAlbumCountPaged(keyword, page, limit);
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
        try(HttpResponse response = HttpRequest.get(url).execute()) {
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
        }
    }
}