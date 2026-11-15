package info.tongrenlu.www;

import info.tongrenlu.service.WikiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 公开 Wiki 词条接口：词条主体为 m_tag（artist / event / original_game / original_track）。
 * 另提供 OKF（Open Knowledge Format）bundle 导出。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/wiki")
@Slf4j
public class ApiWikiController {

    private final WikiService wikiService;

    /**
     * 词条列表：type=all / artist / event，支持关键词搜索与分页。
     */
    @GetMapping("/entries")
    public ResponseEntity<Map<String, Object>> entries(
            @RequestParam(required = false, defaultValue = "all") String type,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "30") int limit) {
        try {
            return ResponseEntity.ok(wikiService.getEntries(type, keyword, page, limit));
        } catch (Exception e) {
            log.error("获取 wiki 词条列表失败", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "获取词条列表失败");
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * 词条关联专辑（服务端分页，只返回卡片所需字段）。
     */
    @GetMapping("/{tagId}/albums")
    public ResponseEntity<Map<String, Object>> albums(
            @PathVariable Long tagId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        try {
            return ResponseEntity.ok(wikiService.getAlbums(tagId, page, limit));
        } catch (Exception e) {
            log.error("获取词条专辑失败: tagId={}", tagId, e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "获取词条专辑失败");
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * OKF（Open Knowledge Format）bundle 索引：按类型分组列出全部词条的概念文档链接
     * （渐进披露入口）。文本响应，人和 agent 都可直接消费。
     */
    @GetMapping("/okf/index.md")
    public ResponseEntity<String> okfIndex() {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/markdown;charset=UTF-8"))
                .body(wikiService.okfIndex());
    }

    /** OKF 保留文件 log.md：按更新时间倒序的词条变更记录。 */
    @GetMapping("/okf/log.md")
    public ResponseEntity<String> okfLog() {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/markdown;charset=UTF-8"))
                .body(wikiService.okfLog());
    }

    /**
     * OKF 概念文档：单个词条的 YAML frontmatter（type/title/description/resource/tags/
     * timestamp）+ markdown 正文。路径即概念 ID（如 artist/123）。
     */
    @GetMapping("/okf/{type}/{tagId}.md")
    public ResponseEntity<String> okfEntry(
            @PathVariable("type") String type,
            @PathVariable("tagId") Long tagId) {
        String document = wikiService.okfDocument(type, tagId);
        if (document == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/markdown;charset=UTF-8"))
                .body(document);
    }

    /**
     * 词条详情：按 tagId 或词条名查询。
     */
    @GetMapping("/detail")
    public ResponseEntity<Map<String, Object>> detail(
            @RequestParam(required = false) Long id,
            @RequestParam(required = false) String tag) {
        try {
            Map<String, Object> detail = wikiService.getDetail(id, tag);
            if (detail == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(detail);
        } catch (Exception e) {
            log.error("获取 wiki 词条详情失败: id={}, tag={}", id, tag, e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "获取词条详情失败");
            return ResponseEntity.internalServerError().body(error);
        }
    }
}
