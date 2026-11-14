package info.tongrenlu;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin HTTP endpoints for {@link OriginalTagBackfillJob}.
 *
 * <p>把 {@code m_track.original} 里的「原曲 / 原作」解析成 {@code m_tag}
 * （{@code type=original_track} / {@code original_game}）并关联到专辑。
 *
 * <p>推荐流程：
 * <pre>
 *   1. GET  /admin/original-tag-backfill/status
 *   2. GET  /admin/original-tag-backfill/preview?size=20   → 检查拆分是否合理
 *   3. POST /admin/original-tag-backfill/dry-run            → 模拟，不写库
 *   4. POST /admin/original-tag-backfill/trigger            → 真正写入
 *   5. POST /admin/original-tag-backfill/pause | /resume    → 中途控制
 *   6. 需要重建时：POST /clear 清空关联，再 /trigger 重跑
 * </pre>
 *
 * <p>默认只处理「遗留格式」的 original（不含全角 ／），THBWiki 格式行由
 * {@code ThbwikiService.matchAndSave} 直接写 tag。若要<strong>一次性</strong>把存量的
 * THBWiki 格式行也补上，给 preview / dry-run / trigger 加 {@code ?all=true}：
 * <pre>
 *   GET  /admin/original-tag-backfill/preview?size=20&amp;all=true
 *   POST /admin/original-tag-backfill/dry-run?all=true
 *   POST /admin/original-tag-backfill/trigger?all=true
 * </pre>
 * 该开关只对本次调用生效，之后不带参数的 trigger 自动回到默认范围；中途 pause 后
 * 续跑需再次带 {@code ?all=true}（页码游标保留）。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/original-tag-backfill")
@Slf4j
public class AdminOriginalTagBackfillController {

    private final OriginalTagBackfillJob job;

    @GetMapping("/status")
    public OriginalTagBackfillJob.JobStatus status() {
        return job.status();
    }

    /** 抽样预览前 N 条的解析结果，不写库。all=true 时抽样包含 THBWiki 格式行。 */
    @GetMapping("/preview")
    public List<OriginalTagBackfillJob.SampleResult> preview(
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "false") boolean all) {
        return job.preview(size, all);
    }

    /** @param all true = 本次一次性全量触发（连 THBWiki 格式行一起补 tag） */
    @PostMapping("/trigger")
    public Map<String, Boolean> trigger(@RequestParam(defaultValue = "false") boolean all) {
        job.trigger(all);
        return Map.of("triggered", true, "all", all);
    }

    /** 模拟，不写库。@param all 同 trigger */
    @PostMapping("/dry-run")
    public Map<String, Boolean> dryRun(@RequestParam(defaultValue = "false") boolean all) {
        job.triggerDryRun(all);
        return Map.of("dryRunStarted", true, "all", all);
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

    /** 仅重置计数器与游标，不动数据。 */
    @PostMapping("/reset")
    public Map<String, Object> reset() {
        job.reset();
        Map<String, Object> resp = new HashMap<>();
        resp.put("reset", true);
        resp.put("status", job.status());
        return resp;
    }

    /** 清空全部「原曲 / 原作」标签关联（保留 m_tag 词条），供重建。 */
    @PostMapping("/clear")
    public Map<String, Object> clear() {
        int cleared = job.clearLinks();
        job.reset();
        Map<String, Object> resp = new HashMap<>();
        resp.put("cleared", cleared);
        resp.put("status", job.status());
        return resp;
    }
}
