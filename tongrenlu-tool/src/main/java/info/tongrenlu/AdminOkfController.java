package info.tongrenlu;

import info.tongrenlu.OkfGenerateJob;
import info.tongrenlu.mapper.OkfEntryMapper;
import info.tongrenlu.service.OkfImportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * OKF 阶段 1/2 管理端点：
 * <ul>
 *   <li>/admin/okf/import —— 6 类概念导入（阶段 1）；</li>
 *   <li>/admin/okf/generate/* —— 内容生成任务控制（阶段 2，取代 WikiGenerateJob 调度）；</li>
 *   <li>/admin/okf/count —— 按类型统计。</li>
 * </ul>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/okf")
@Slf4j
public class AdminOkfController {

    private final OkfImportService okfImportService;
    private final OkfEntryMapper okfEntryMapper;
    private final OkfGenerateJob okfGenerateJob;

    /**
     * 导入数据：不带参数导入全部 6 类；{@code ?type=} 导入单类
     * （artist / event / album / original_game / game_track / cd_track）。
     */
    @PostMapping("/import")
    public ResponseEntity<Map<String, Object>> importData(
            @RequestParam(value = "type", required = false) String type) {
        Map<String, Object> response = new HashMap<>();
        try {
            Map<String, Object> result = (type == null || type.isBlank())
                    ? okfImportService.importAll()
                    : Map.of(type, okfImportService.importByType(type));
            response.put("success", true);
            response.put("result", result);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            log.error("OKF 导入失败", e);
            response.put("success", false);
            response.put("message", String.valueOf(e.getMessage()));
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /** 按类型统计已导入条数。 */
    @GetMapping("/count")
    public ResponseEntity<Map<String, Object>> count() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("counts", okfEntryMapper.countByType());
        return ResponseEntity.ok(response);
    }

    // ==================== 内容生成任务（阶段 2） ====================

    /** OKF 内容生成任务状态。 */
    @GetMapping("/generate/status")
    public OkfGenerateJob.JobStatus generateStatus() {
        return okfGenerateJob.status();
    }

    @PostMapping("/generate/trigger")
    public Map<String, Object> generateTrigger() {
        okfGenerateJob.trigger();
        return Map.of("success", true, "message", "OKF 内容生成已触发");
    }

    @PostMapping("/generate/pause")
    public Map<String, Boolean> generatePause() {
        okfGenerateJob.pause();
        return Map.of("paused", true);
    }

    @PostMapping("/generate/resume")
    public Map<String, Boolean> generateResume() {
        okfGenerateJob.resume();
        return Map.of("resumed", true);
    }
}
