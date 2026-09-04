package info.tongrenlu;

import info.tongrenlu.domain.ArticleBean;
import info.tongrenlu.enums.ThbWikiStatus;
import info.tongrenlu.mapper.ArticleMapper;
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

    @GetMapping("/status")
    public OriginalUpdateJob.JobStatus status() {
        return job.status();
    }

    @PostMapping("/trigger")
    public Map<String, Boolean> trigger() {
        job.trigger();
        return Map.of("triggered", true);
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
