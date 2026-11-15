package info.tongrenlu;

import info.tongrenlu.domain.TagBean;
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
 * Wiki 词条生成任务的触发/监控接口，以及单词条的同步生成（测试用）。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/wiki")
@Slf4j
public class AdminWikiController {

    private final WikiGenerateJob job;
    private final WikiService wikiService;
    private final TagMapper tagMapper;
    private final ThbwikiService thbwikiService;

    @GetMapping("/status")
    public WikiGenerateJob.JobStatus status() {
        return job.status();
    }

    /**
     * 按类型批量重置词条为 PENDING，让生成任务用当前素材源重新生成（「更新」入口）。
     * 例如原作词条改从官方游戏列表页获取后：
     * {@code POST /admin/wiki/regenerate?type=original_game}
     */
    @PostMapping("/regenerate")
    public ResponseEntity<Map<String, Object>> regenerate(@RequestParam("type") String type) {
        Map<String, Object> response = new HashMap<>();
        try {
            int reset = wikiService.resetTypeToPending(type);
            response.put("success", true);
            response.put("type", type);
            response.put("reset", reset);
            response.put("message", "已重置 " + reset + " 条词条为 PENDING，等待生成任务处理");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
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
     * 单词条同步生成。force=true 时忽略已有内容重新生成。
     *
     * @param tagId m_tag 词条 ID（artist / event 通用）
     */
    @PostMapping("/tag/{tagId}")
    public ResponseEntity<Map<String, Object>> generateOne(
            @PathVariable("tagId") Long tagId,
            @RequestParam(value = "force", required = false, defaultValue = "false") boolean force) {
        Map<String, Object> response = new HashMap<>();

        TagBean tag = tagMapper.selectById(tagId);
        if (tag == null) {
            response.put("success", false);
            response.put("message", "Wiki entry not found: tagId=" + tagId);
            return ResponseEntity.status(404).body(response);
        }

        boolean updated = wikiService.generateForTag(tag, force);
        WikiService.WikiFields fields = wikiService.getWikiFields(tagId);

        response.put("success", true);
        response.put("updated", updated);
        response.put("tagId", tagId);
        response.put("name", tag.getTag());
        response.put("type", tag.getType());
        response.put("wikiStatus", fields.status());
        response.put("wikiContentLength", fields.content() == null ? 0 : fields.content().length());
        response.put("wikiTags", fields.tags());
        response.put("wikiSourceUrl", fields.sourceUrl());
        return ResponseEntity.ok(response);
    }

    /**
     * 手动搜索 THBWiki 页面（辅助排查匹配问题）。
     */
    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> search(@RequestParam("keyword") String keyword) {
        Map<String, Object> response = new HashMap<>();
        if (keyword == null || keyword.trim().isEmpty()) {
            response.put("success", false);
            response.put("message", "关键词不能为空");
            return ResponseEntity.badRequest().body(response);
        }
        var results = thbwikiService.searchAlbum(keyword.trim());
        response.put("success", true);
        response.put("data", results);
        response.put("count", results.size());
        return ResponseEntity.ok(response);
    }
}
