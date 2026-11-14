package info.tongrenlu;

import info.tongrenlu.domain.ArtistBean;
import info.tongrenlu.domain.TagBean;
import info.tongrenlu.mapper.ArtistMapper;
import info.tongrenlu.mapper.TagMapper;
import info.tongrenlu.service.ThbwikiService;
import info.tongrenlu.service.WikiService;
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
 * 旧艺术家 Wiki 控制面，现为 /admin/wiki/* 的兼容别名（合并后的统一入口）。
 * 保留路径以免打断仓库外的运维脚本；新代码请使用 AdminWikiController。
 */
@Deprecated
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/artist-wiki")
@Slf4j
public class AdminArtistWikiController {

    private final WikiGenerateJob job;
    private final WikiService wikiService;
    private final ArtistMapper artistMapper;
    private final TagMapper tagMapper;
    private final ThbwikiService thbwikiService;

    @GetMapping("/status")
    public WikiGenerateJob.JobStatus status() {
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
     * 单艺术家同步生成：先按 m_artist.id 找到关联 tag_id，再走统一词条生成。
     */
    @PostMapping("/artist/{id}")
    public ResponseEntity<Map<String, Object>> generateOne(
            @PathVariable("id") Long id,
            @RequestParam(value = "force", required = false, defaultValue = "false") boolean force) {
        Map<String, Object> response = new HashMap<>();

        ArtistBean artist = artistMapper.selectById(id);
        if (artist == null || artist.getTagId() == null) {
            response.put("success", false);
            response.put("message", "Artist or linked wiki entry not found: id=" + id);
            return ResponseEntity.status(404).body(response);
        }
        TagBean tag = tagMapper.selectById(artist.getTagId());
        if (tag == null) {
            response.put("success", false);
            response.put("message", "Wiki entry not found: tagId=" + artist.getTagId());
            return ResponseEntity.status(404).body(response);
        }

        boolean updated = wikiService.generateForTag(tag, force);
        WikiService.WikiFields fields = wikiService.getWikiFields(tag.getId());

        response.put("success", true);
        response.put("updated", updated);
        response.put("name", artist.getName());
        response.put("wikiStatus", fields.status());
        response.put("wikiContentLength", fields.content() == null ? 0 : fields.content().length());
        response.put("wikiTags", fields.tags());
        response.put("thbWikiUrl", fields.sourceUrl());
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
