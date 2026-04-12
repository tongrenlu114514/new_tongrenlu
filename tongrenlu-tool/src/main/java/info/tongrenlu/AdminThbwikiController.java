package info.tongrenlu;

import info.tongrenlu.model.ThbwikiAlbum;
import info.tongrenlu.service.ThbwikiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/thbwiki")
@Slf4j
public class AdminThbwikiController {

    private final ThbwikiService thbwikiService;

    /**
     * 搜索 THBWiki 专辑
     *
     * @param albumName 专辑名称（搜索关键词）
     * @return 匹配的专辑列表
     */
    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> searchAlbum(
            @RequestParam("albumName") String albumName) {

        log.info("THBWiki search request: {}", albumName);

        Map<String, Object> response = new HashMap<>();

        if (albumName == null || albumName.trim().isEmpty()) {
            response.put("success", false);
            response.put("message", "专辑名称不能为空");
            return ResponseEntity.badRequest().body(response);
        }

        try {
            List<ThbwikiAlbum> results = thbwikiService.searchAlbum(albumName.trim());

            response.put("success", true);
            response.put("data", results);
            response.put("count", results.size());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error searching THBWiki for album: {}", albumName, e);
            response.put("success", false);
            response.put("message", "搜索失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 健康检查接口
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}
