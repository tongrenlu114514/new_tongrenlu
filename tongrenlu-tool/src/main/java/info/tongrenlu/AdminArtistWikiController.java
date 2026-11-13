package info.tongrenlu;

import info.tongrenlu.domain.ArtistBean;
import info.tongrenlu.mapper.ArtistMapper;
import info.tongrenlu.service.ArtistWikiService;
import info.tongrenlu.service.ThbwikiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 艺术家 Wiki 生成任务的触发/监控接口，以及单艺术家的同步生成（测试用）。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/artist-wiki")
@Slf4j
public class AdminArtistWikiController {

    private final ArtistWikiGenerateJob job;
    private final ArtistWikiService artistWikiService;
    private final ArtistMapper artistMapper;
    private final ThbwikiService thbwikiService;

    @GetMapping("/status")
    public ArtistWikiGenerateJob.JobStatus status() {
        return job.status();
    }

    @PostMapping("/trigger")
    public Map<String, Object> trigger() {
        job.trigger();
        return Map.of("success", true, "message", "任务已触发");
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
     * 单艺术家同步生成。force=true 时忽略已有内容重新生成。
     */
    @PostMapping("/artist/{id}")
    public ResponseEntity<Map<String, Object>> generateOne(
            @PathVariable("id") Long id,
            @RequestParam(value = "force", required = false, defaultValue = "false") boolean force) {
        Map<String, Object> response = new HashMap<>();

        ArtistBean artist = artistMapper.selectById(id);
        if (artist == null) {
            response.put("success", false);
            response.put("message", "Artist not found: id=" + id);
            return ResponseEntity.status(404).body(response);
        }

        boolean updated = artistWikiService.generateForArtist(artist, force);

        ArtistBean refreshed = artistMapper.selectById(id);
        response.put("success", true);
        response.put("updated", updated);
        response.put("name", refreshed.getName());
        response.put("thbWikiUrl", refreshed.getThbWikiUrl());
        response.put("thbWikiStatus", refreshed.getThbWikiStatus());
        response.put("wikiContentLength", refreshed.getWikiContent() == null ? 0 : refreshed.getWikiContent().length());
        response.put("wikiTags", refreshed.getWikiTags());
        return ResponseEntity.ok(response);
    }

    /**
     * 手动搜索 THBWiki 社团页面（辅助排查匹配问题）。
     */
    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> search(@RequestParam("artistName") String artistName) {
        Map<String, Object> response = new HashMap<>();
        if (artistName == null || artistName.trim().isEmpty()) {
            response.put("success", false);
            response.put("message", "社团名称不能为空");
            return ResponseEntity.badRequest().body(response);
        }
        var results = thbwikiService.searchAlbum(artistName.trim());
        response.put("success", true);
        response.put("data", results);
        response.put("count", results.size());
        return ResponseEntity.ok(response);
    }
}
