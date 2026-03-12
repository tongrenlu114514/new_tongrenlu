package info.tongrenlu.www;

import info.tongrenlu.service.TagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 展会相关API控制器
 * 基于tag表中type='event'的数据
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/events")
@Slf4j
public class ApiEventController {

    private final TagService tagService;

    /**
     * 获取展会列表
     *
     * @param keyword 搜索关键词
     * @param page    页码
     * @param limit   每页数量
     * @param orderBy 排序方式：album_count（按专辑数）, name（按名称）
     * @return 展会列表
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getEvents(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "album_count") String orderBy) {
        try {
            Map<String, Object> result = tagService.getEventsWithAlbumCountPaged(keyword, page, limit, orderBy);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error getting events", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "获取展会列表失败");
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * 获取展会详情
     *
     * @param id 展会标签ID
     * @return 展会详情
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getEventDetail(@PathVariable Long id) {
        try {
            Map<String, Object> event = tagService.getEventDetail(id);
            if (event == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(event);
        } catch (Exception e) {
            log.error("Error getting event detail for id: {}", id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取展会的专辑列表
     *
     * @param id    展会标签ID
     * @param page  页码
     * @param limit 每页数量
     * @return 专辑列表
     */
    @GetMapping("/{id}/albums")
    public ResponseEntity<Map<String, Object>> getEventAlbums(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "12") int limit) {
        try {
            Map<String, Object> result = tagService.getEventAlbums(id, page, limit);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error getting albums for event id: {}", id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取展会统计数据
     *
     * @return 统计数据
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getEventStats() {
        try {
            Map<String, Object> stats = tagService.getEventStats();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Error getting event stats", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取展会总数
     *
     * @return 展会总数
     */
    @GetMapping("/count")
    public ResponseEntity<Map<String, Object>> getEventCount() {
        try {
            Map<String, Object> result = new HashMap<>();
            result.put("count", tagService.getBaseMapper().countEvents());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error getting event count", e);
            return ResponseEntity.internalServerError().build();
        }
    }

}