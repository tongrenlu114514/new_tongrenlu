package info.tongrenlu;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import info.tongrenlu.domain.ArticleBean;
import info.tongrenlu.domain.TrackBean;
import info.tongrenlu.enums.ThbWikiStatus;
import info.tongrenlu.mapper.ArticleMapper;
import info.tongrenlu.mapper.TrackMapper;
import info.tongrenlu.model.ThbwikiAlbum;
import info.tongrenlu.service.ThbwikiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin override endpoint for THBWiki matching.
 *
 * Per CLAUDE.md the matching strategy is "automatic best match, no manual selection",
 * but in practice auto-matching can be wrong. This endpoint lets an admin reset an
 * album's status back to PENDING (so the job retries it) or directly assign a URL
 * picked from a manual search via {@link #search(String)}.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/original-update")
@Slf4j
public class AdminOriginalUpdateController {

    private final OriginalUpdateJob job;
    private final ThbwikiService thbwikiService;
    private final ArticleMapper articleMapper;
    private final TrackMapper trackMapper;

    @GetMapping("/status")
    public OriginalUpdateJob.JobStatus status() {
        return job.status();
    }

    @PostMapping("/trigger")
    public ResponseEntity<Map<String, Object>> trigger(@RequestParam(value = "reset", required = false) Boolean reset) {
        Map<String, Object> response = new HashMap<>();

        if (Boolean.TRUE.equals(reset)) {
            // Reset all tracks' original info and all albums' status to PENDING
            List<TrackBean> tracks = trackMapper.selectList(null);
            for (TrackBean track : tracks) {
                track.setOriginal(null);
                track.setOriginalUrl(null);
                trackMapper.updateById(track);
            }

            articleMapper.update(null,
                    new LambdaUpdateWrapper<ArticleBean>()
                            .set(ArticleBean::getThbWikiStatus, ThbWikiStatus.PENDING.name())
                            .set(ArticleBean::getThbWikiUrl, null)
                            .set(ArticleBean::getUpdDate, new Date()));

            log.info("Reset all tracks' original info and all albums to PENDING. Tracks affected: {}", tracks.size());
            response.put("success", true);
            response.put("message", "已重置所有曲目原曲信息和专辑状态");
            response.put("tracksReset", tracks.size());
        } else {
            job.trigger();
            response.put("success", true);
            response.put("message", "任务已触发");
        }

        return ResponseEntity.ok(response);
    }

    @PostMapping("/pause")
    public Map<String, Boolean> pause() {
        job.pause();
        return Map.of("paused", true);
    }

    @PostMapping("/resume")
    public Map<String, Boolean> resume() {
        job.resume();
        return Map.of("resumed", true);
    }

    /**
     * Manual search for THBWiki albums (mirrors {@link AdminThbwikiController#searchAlbum}
     * but kept here so admins have a single namespace for original-update operations).
     */
    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> search(@RequestParam("albumName") String albumName) {
        Map<String, Object> response = new HashMap<>();
        if (albumName == null || albumName.trim().isEmpty()) {
            response.put("success", false);
            response.put("message", "专辑名称不能为空");
            return ResponseEntity.badRequest().body(response);
        }
        List<ThbwikiAlbum> results = thbwikiService.searchAlbum(albumName.trim());
        response.put("success", true);
        response.put("data", results);
        response.put("count", results.size());
        return ResponseEntity.ok(response);
    }

    /**
     * Manually override the THBWiki URL of an album.
     * Sets status to MATCHED. Pass {@code thbWikiUrl=null} (or empty) to reset
     * the album back to PENDING so the job re-processes it.
     */
    @PutMapping("/album/{id}")
    public ResponseEntity<Map<String, Object>> overrideAlbum(
            @PathVariable("id") Long id,
            @RequestBody(required = false) OverrideRequest body) {
        Map<String, Object> response = new HashMap<>();

        ArticleBean album = articleMapper.selectById(id);
        if (album == null) {
            response.put("success", false);
            response.put("message", "Album not found: id=" + id);
            return ResponseEntity.status(404).body(response);
        }

        if (body == null) {
            body = new OverrideRequest(null);
        }

        String url = body.thbWikiUrl();
        if (url != null) {
            url = url.trim();
            if (url.isEmpty()) {
                url = null;
            }
        }

        if (url == null) {
            // Reset to PENDING so the job retries.
            album.setThbWikiUrl(null);
            album.setThbWikiStatus(ThbWikiStatus.PENDING.name());
        } else {
            album.setThbWikiUrl(url);
            album.setThbWikiStatus(ThbWikiStatus.MATCHED.name());
        }
        album.setUpdDate(new Date());
        articleMapper.updateById(album);

        log.info("Admin override album id={} title='{}' -> url={} status={}",
                album.getId(), album.getTitle(), album.getThbWikiUrl(), album.getThbWikiStatus());

        response.put("success", true);
        response.put("id", album.getId());
        response.put("thbWikiUrl", album.getThbWikiUrl());
        response.put("thbWikiStatus", album.getThbWikiStatus());
        return ResponseEntity.ok(response);
    }

    public record OverrideRequest(String thbWikiUrl) {}
}
