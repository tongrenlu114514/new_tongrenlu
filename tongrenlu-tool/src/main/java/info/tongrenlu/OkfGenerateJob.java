package info.tongrenlu;

import info.tongrenlu.domain.OkfEntryBean;
import info.tongrenlu.mapper.OkfEntryMapper;
import info.tongrenlu.service.OkfGenerateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * OKF 阶段 2 内容生成任务：扫描 {@code m_okf_entry} 中 content 为空的条目，
 * 按 6 类概念生成 markdown 正文（取代 m_tag 侧的 WikiGenerateJob 调度）。
 * 每轮取前 okf.generate.batch-limit 条（默认 2），按 id 升序；生成完成的条目
 * 自然退出扫描范围，无需游标。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class OkfGenerateJob {

    private final OkfEntryMapper okfEntryMapper;
    private final OkfGenerateService okfGenerateService;

    @Value("${okf.generate.batch-limit:2}")
    private int batchLimit;

    private final AtomicBoolean paused = new AtomicBoolean(false);
    private final AtomicInteger processedCount = new AtomicInteger(0);
    private final AtomicInteger generatedCount = new AtomicInteger(0);
    private final AtomicReference<String> lastEntry = new AtomicReference<>(null);
    private final AtomicReference<String> lastError = new AtomicReference<>(null);
    private final AtomicReference<String> currentPhase = new AtomicReference<>("IDLE");

    @Scheduled(fixedDelay = 120, initialDelay = 60, timeUnit = TimeUnit.SECONDS)
    public void runScheduledCycle() {
        runCycle();
    }

    public void runCycle() {
        if (paused.get()) {
            log.info("Job paused, skipping cycle");
            return;
        }
        currentPhase.set("RUNNING");
        log.info("Starting OKF generate cycle");

        List<OkfEntryBean> batch = okfEntryMapper.findGeneratable(batchLimit);
        if (batch.isEmpty()) {
            currentPhase.set("IDLE");
            log.info("No generatable OKF entries, cycle complete");
            return;
        }

        for (OkfEntryBean entry : batch) {
            if (paused.get()) {
                currentPhase.set("PAUSED");
                log.info("Paused at entry id={}, name={}", entry.getId(), entry.getName());
                return;
            }
            processedCount.incrementAndGet();
            lastEntry.set(entry.getName());
            lastError.set(null);
            log.info("Generating OKF entry: {} (id={}, type={})", entry.getName(), entry.getId(), entry.getType());
            try {
                if (okfGenerateService.generateForEntry(entry, false)) {
                    generatedCount.incrementAndGet();
                }
            } catch (Exception e) {
                lastError.set(entry.getName() + ": " + e.getMessage());
                log.error("Failed to generate OKF entry {} (id={})", entry.getName(), entry.getId(), e);
            }
        }
        currentPhase.set("IDLE");
        log.info("OKF generate cycle complete. processed={}, generated={}", processedCount.get(), generatedCount.get());
    }

    public void pause() {
        paused.set(true);
        currentPhase.set("PAUSED");
        log.info("Job paused");
    }

    public void resume() {
        paused.set(false);
        log.info("Job resumed");
    }

    public void trigger() {
        CompletableFuture.runAsync(this::runCycle);
    }

    public JobStatus status() {
        long totalRemaining = okfEntryMapper.countGeneratable();
        return new JobStatus(
                currentPhase.get(),
                lastEntry.get(),
                lastError.get(),
                processedCount.get(),
                generatedCount.get(),
                totalRemaining
        );
    }

    public record JobStatus(
            String phase,
            String lastEntry,
            String lastError,
            int processedCount,
            int generatedCount,
            long totalRemaining
    ) {}
}
