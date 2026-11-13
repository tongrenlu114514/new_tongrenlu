package info.tongrenlu.support;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 失败清单里单个专辑的补导（手动补导与定时扫描共用）。
 *
 * <p>补导的语义边界：
 * <ul>
 *   <li><b>只销账、不动水位</b>：被补导的专辑水位早已越过，补导绝不改写 index；</li>
 *   <li><b>失败重新分类</b>：不沿用条目原来的类型 —— 一次「入库失败」在补导时可能变成上游 404，
 *       那时它已经是 permanent，不该再继续补下去；</li>
 *   <li><b>失败计数</b>：人工手动补导先归零（人明确要求了，再给一份完整预算），
 *       自动扫描则继续累加（用尽预算后由人接管）。</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FailedAlbumRetryService {

    private final PlaylistProgressStore progressStore;
    private final AlbumImportRunner albumImportRunner;

    /**
     * 补导一条失败条目。
     *
     * @param resetAttemptsBefore true 表示人工手动补导（先把失败计数归零，重新给一份预算）；
     *                             false 表示自动扫描（失败计数继续累加）
     */
    public RetryOutcome retryOne(Long playlistId, PlaylistProgressStore.FailedAlbum entry, boolean resetAttemptsBefore) {
        Long albumId = entry.albumId();
        if (resetAttemptsBefore) {
            progressStore.resetAttempts(playlistId, albumId);
        }
        try {
            AlbumImportRunner.ImportOutcome outcome = albumImportRunner.importOne(albumId);
            // 成功（或已存在）→ 从清单销账；只销账，不动 index
            progressStore.removeFailedAlbum(playlistId, albumId);
            return RetryOutcome.succeeded(albumId, outcome.status() == AlbumImportRunner.ImportOutcome.Status.SKIPPED,
                    outcome.title());
        } catch (RuntimeException e) {
            AlbumImportRunner.Failure failure = albumImportRunner.classify(e);
            // 记账：attempts 累加；index 原样带回（补导绝不改动续传位置）
            progressStore.markFailedKeepingIndex(playlistId, albumId, failure.type(), failure.reason());
            log.warn("歌单 {} 的专辑 {} 补导失败（type={}）：{}", playlistId, albumId, failure.type(), failure.reason());
            return RetryOutcome.failed(albumId, failure);
        }
    }

    /**
     * 补导结果。
     *
     * @param success      是否补导成功
     * @param skipped      是否因为「已存在」而跳过
     * @param type         失败时的类型：permanent / transient / insert
     * @param reason       失败原因
     * @param retryable    是否可重试
     * @param fetchFailure 是否取专辑失败（true → 发 failed 事件；false → 发 error 事件）
     */
    public record RetryOutcome(Long albumId, boolean success, boolean skipped, String title,
                               String type, String reason, boolean retryable, boolean fetchFailure) {

        static RetryOutcome succeeded(Long albumId, boolean skipped, String title) {
            return new RetryOutcome(albumId, true, skipped, title, null, null, false, false);
        }

        static RetryOutcome failed(Long albumId, AlbumImportRunner.Failure failure) {
            return new RetryOutcome(albumId, false, false, null,
                    failure.type(), failure.reason(), failure.retryable(), failure.fetchFailure());
        }
    }
}
