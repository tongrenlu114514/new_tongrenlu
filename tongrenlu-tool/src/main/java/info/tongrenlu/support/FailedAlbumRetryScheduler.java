package info.tongrenlu.support;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 失败清单的定时兜底扫描：自动重试入库类失败（type=insert）。
 *
 * <p>边界（与人工补导共用同一套语义）：
 * <ul>
 *   <li><b>只重试 type=insert</b>：permanent 是上游明确没有该专辑，绝不对它发请求；
 *       transient 由导入流的水位机制自动重试（水位不越过它），不归这里管；</li>
 *   <li><b>attempts 达预算上限的条目跳过</b>：不再浪费上游请求，也不改人工补导重置后的计数；</li>
 *   <li><b>不重置 attempts</b>：人工手动补导才重置；自动扫描失败则继续累加。</li>
 * </ul>
 *
 * <p>可观测性：日志说明扫了哪些歌单、发现多少条待重试、跳过了什么（permanent / transient / attempts 达上限）；
 * 没有可重试条目时空转且不打错误日志。
 */
@Component
@Slf4j
public class FailedAlbumRetryScheduler {

    private final PlaylistProgressStore progressStore;
    private final FailedAlbumRetryService retryService;
    private final PlaylistImportLock importLock;
    private final int retryBudget;

    public FailedAlbumRetryScheduler(PlaylistProgressStore progressStore,
                                     FailedAlbumRetryService retryService,
                                     PlaylistImportLock importLock,
                                     @Value("${app.playlist-import.retry-budget:3}") int retryBudget) {
        this.progressStore = progressStore;
        this.retryService = retryService;
        this.importLock = importLock;
        this.retryBudget = retryBudget;
    }

    /**
     * 定时扫描并重试。周期由 {@code app.playlist-import.retry-scan-interval-ms} 控制（默认 30 分钟）。
     */
    @Scheduled(fixedDelayString = "${app.playlist-import.retry-scan-interval-ms:1800000}",
            initialDelayString = "${app.playlist-import.retry-scan-initial-delay-ms:600000}")
    public void scan() {
        scanOnce();
    }

    /**
     * 扫描一轮（抽成方法便于测试直接调用）。
     *
     * @return 本轮实际重试的条目数
     */
    public int scanOnce() {
        List<Long> playlistIds = progressStore.listPlaylistIds();
        if (playlistIds.isEmpty()) {
            log.debug("失败清单扫描：进度目录里没有歌单，本轮空转");
            return 0;
        }
        int totalRetried = 0;
        for (Long playlistId : playlistIds) {
            totalRetried += scanPlaylist(playlistId);
        }
        log.info("失败清单扫描完成：共扫描 {} 个歌单，重试 {} 个条目", playlistIds.size(), totalRetried);
        return totalRetried;
    }

    /**
     * 扫描单个歌单。
     */
    public int scanPlaylist(Long playlistId) {
        List<PlaylistProgressStore.FailedAlbum> entries = progressStore.read(playlistId).failedAlbums();
        List<PlaylistProgressStore.FailedAlbum> pending = new ArrayList<>();
        int skippedPermanent = 0;
        int skippedTransient = 0;
        int skippedExhausted = 0;
        for (PlaylistProgressStore.FailedAlbum entry : entries) {
            if (PlaylistProgressStore.TYPE_PERMANENT.equals(entry.type())) {
                // 上游明确没有这个专辑，绝不对它发请求
                skippedPermanent++;
            } else if (PlaylistProgressStore.TYPE_TRANSIENT.equals(entry.type())) {
                // 临时失败由导入流的水位机制自动重试，不归这里管
                skippedTransient++;
            } else if (entry.attempts() >= retryBudget) {
                // 自动重试预算已用尽，等人工补导（人工补导会重置 attempts）
                skippedExhausted++;
            } else {
                pending.add(entry);
            }
        }

        if (pending.isEmpty()) {
            log.info("歌单 {} 无待重试条目：permanent 跳过 {} 条、transient 跳过 {} 条、失败次数达上限（{} 次）跳过 {} 条",
                    playlistId, skippedPermanent, skippedTransient, retryBudget, skippedExhausted);
            return 0;
        }

        if (!importLock.tryLock(playlistId)) {
            log.info("歌单 {} 正在导入或补导中，本轮扫描跳过（待重试 {} 条）", playlistId, pending.size());
            return 0;
        }

        AtomicInteger retried = new AtomicInteger(0);
        AtomicInteger succeeded = new AtomicInteger(0);
        try {
            for (PlaylistProgressStore.FailedAlbum entry : pending) {
                // 自动扫描不重置 attempts：失败继续累加，用尽预算后交给人工
                FailedAlbumRetryService.RetryOutcome outcome =
                        retryService.retryOne(playlistId, entry, false);
                retried.incrementAndGet();
                if (outcome.success()) {
                    succeeded.incrementAndGet();
                    log.info("歌单 {} 的专辑 {} 自动补导成功，已从失败清单移除", playlistId, entry.albumId());
                } else {
                    log.warn("歌单 {} 的专辑 {} 自动补导失败（type={}）：{}",
                            playlistId, entry.albumId(), outcome.type(), outcome.reason());
                }
            }
        } finally {
            importLock.unlock(playlistId);
        }
        log.info("歌单 {} 自动补导完成：重试 {} 条，成功 {} 条，permanent 跳过 {} 条、transient 跳过 {} 条、失败次数达上限跳过 {} 条",
                playlistId, retried.get(), succeeded.get(), skippedPermanent, skippedTransient, skippedExhausted);
        return retried.get();
    }

    /** 自动重试预算 N */
    public int getRetryBudget() {
        return retryBudget;
    }
}
