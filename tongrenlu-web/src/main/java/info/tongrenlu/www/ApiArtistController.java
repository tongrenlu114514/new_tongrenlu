package info.tongrenlu.www;

import info.tongrenlu.domain.ArtistBean;
import info.tongrenlu.service.ArtistService;
import info.tongrenlu.service.WikiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 公开艺术家接口：列表来自 {@code m_artist}（按 tag_id 去重，只含有已发布专辑的艺术家），
 * 详情正文来自 {@code m_artist.description}。
 * <p>
 * 旧的 {@code /api/music/artists} 仍保留原契约（首页在用），本控制器不动它。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/artists")
@Slf4j
public class ApiArtistController {

    private final ArtistService artistService;
    private final WikiService wikiService;

    /**
     * 艺术家分页列表：keyword 匹配 name / cloud_music_name。
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "30") int limit) {
        try {
            return ResponseEntity.ok(artistService.getArtistShowcasePaged(keyword, page, limit));
        } catch (Exception e) {
            log.error("获取艺术家列表失败: keyword={}", keyword, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 艺术家详情：核心字段 + 简介正文。
     * 正文取 {@code m_artist.description}，为空时 content 为 null。
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> detail(@PathVariable Long id) {
        try {
            ArtistBean artist = artistService.getArtistDetail(id, null);
            if (artist == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(buildDetail(artist));
        } catch (Exception e) {
            log.error("获取艺术家详情失败: id={}", id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 艺术家相关专辑：按 tagId 服务端分页（原 /api/wiki/{tagId}/albums 迁入）。
     * 供 artist-showcase.html 详情页的「相关专辑」区块使用。
     */
    @GetMapping("/{tagId}/albums")
    public ResponseEntity<Map<String, Object>> albums(
            @PathVariable Long tagId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        try {
            return ResponseEntity.ok(wikiService.getAlbums(tagId, page, limit));
        } catch (Exception e) {
            log.error("获取艺术家相关专辑失败: tagId={}", tagId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    private Map<String, Object> buildDetail(ArtistBean artist) {
        Map<String, Object> result = new HashMap<>();
        result.put("id", artist.getId());
        result.put("name", artist.getName());
        result.put("description", artist.getDescription());
        result.put("cloudMusicId", artist.getCloudMusicId());
        result.put("cloudMusicPicUrl", artist.getCloudMusicPicUrl());
        result.put("tagId", artist.getTagId());
        result.put("thbWikiUrl", artist.getThbWikiUrl());
        result.put("albumCount", artist.getAlbumCount() == null ? 0L : artist.getAlbumCount());

        if (StringUtils.hasText(artist.getDescription())) {
            result.put("content", artist.getDescription());
            result.put("contentSource", "artist");
        } else {
            result.put("content", null);
            result.put("contentSource", "none");
        }
        return result;
    }
}
