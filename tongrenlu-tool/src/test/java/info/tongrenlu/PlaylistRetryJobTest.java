package info.tongrenlu;

import info.tongrenlu.service.ArticleService;
import info.tongrenlu.service.HomeMusicService;
import info.tongrenlu.support.AlbumImportRunner;
import info.tongrenlu.support.CloudMusicAlbumFetchException;
import info.tongrenlu.support.FailedAlbumRetryScheduler;
import info.tongrenlu.support.FailedAlbumRetryService;
import info.tongrenlu.support.PlaylistImportLock;
import info.tongrenlu.support.PlaylistProgressStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.codec.ServerSentEvent;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 按失败清单补导的行为验证：
 * 只销账不动水位、失败计数累加与人工重置、类型筛选、并发互斥，以及定时扫描的边界
 * （不碰 permanent、不碰预算用尽的条目）。
 */
class PlaylistRetryJobTest {

    private static final Long PLAYLIST_ID = 100L;

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("补导成功 → 从清单移除，index 原样不动，done 文案不说「进度文件已清除」")
    void retrySuccessRemovesEntryAndKeepsIndex() {
        HomeMusicService musicService = mock(HomeMusicService.class);
        ArticleService articleService = mock(ArticleService.class);
        when(articleService.getByCloudMusicId(anyLong())).thenReturn(null);

        PlaylistProgressStore store = newPlaylistProgressStore();
        // 补导场景的典型状态：水位早已越过这个专辑
        store.saveIndex(PLAYLIST_ID, 3);
        store.markFailed(PLAYLIST_ID, 11L, PlaylistProgressStore.TYPE_INSERT, "数据库写入失败", 3);

        PlaylistRetryJob job = retryJob(musicService, articleService, store, new PlaylistImportLock());
        List<ServerSentEvent<String>> events =
                job.retryFailed(PLAYLIST_ID, null, null).collectList().block(Duration.ofSeconds(60));

        assertThat(events).isNotNull();
        verify(musicService).saveCloudMusicAlbum(11L);
        // 补导成功 → 清单销账
        assertThat(store.failedAlbumIds(PLAYLIST_ID)).isEmpty();
        // 续传位置原样不动
        assertThat(store.readIndex(PLAYLIST_ID)).isEqualTo(3);
        assertThat(events).extracting(ServerSentEvent::data)
                .anyMatch(data -> data.contains("补导成功: albumId=11"));
        // done 文案必须与真实行为一致：补导不清进度文件
        assertThat(events).extracting(ServerSentEvent::data)
                .anyMatch(data -> data.contains("补导结束") && data.contains("进度文件未清除"));
        assertThat(events).extracting(ServerSentEvent::data)
                .noneMatch(data -> data.contains("进度文件已清除"));
    }

    @Test
    @DisplayName("补导失败 → 重新记账（人工补导先归零再失败一次 → attempts 为 1）")
    void retryFailureKeepsFailureRecorded() {
        HomeMusicService musicService = mock(HomeMusicService.class);
        ArticleService articleService = mock(ArticleService.class);
        when(articleService.getByCloudMusicId(anyLong())).thenReturn(null);
        doThrow(new RuntimeException("数据库写入失败")).when(musicService).saveCloudMusicAlbum(11L);

        PlaylistProgressStore store = newPlaylistProgressStore();
        store.saveIndex(PLAYLIST_ID, 3);
        store.markFailed(PLAYLIST_ID, 11L, PlaylistProgressStore.TYPE_INSERT, "数据库写入失败", 3);

        PlaylistRetryJob job = retryJob(musicService, articleService, store, new PlaylistImportLock());
        List<ServerSentEvent<String>> events =
                job.retryFailed(PLAYLIST_ID, null, null).collectList().block(Duration.ofSeconds(60));

        assertThat(events).isNotNull();
        // 人工补导先把计数归零（再给一份预算），本次失败后 attempts = 1，而不是接着原来的 1 累加成 2
        assertThat(store.read(PLAYLIST_ID).failedAlbums())
                .singleElement()
                .satisfies(entry -> {
                    assertThat(entry.attempts()).isEqualTo(1);
                    assertThat(entry.type()).isEqualTo(PlaylistProgressStore.TYPE_INSERT);
                });
        // 入库异常 → error 事件（取专辑失败才发 failed）
        assertThat(events).extracting(ServerSentEvent::event).contains("error");
        assertThat(events).extracting(ServerSentEvent::event).doesNotContain("failed");
        // index 不动
        assertThat(store.readIndex(PLAYLIST_ID)).isEqualTo(3);
    }

    @Test
    @DisplayName("补导时遇到上游超时 → 重新分类为 transient 而不是沿用 insert")
    void retryReclassifiesFailureByActualCause() {
        HomeMusicService musicService = mock(HomeMusicService.class);
        ArticleService articleService = mock(ArticleService.class);
        when(articleService.getByCloudMusicId(anyLong())).thenReturn(null);
        doThrow(new CloudMusicAlbumFetchException(11L, "上游请求失败或超时", true))
                .when(musicService).saveCloudMusicAlbum(11L);

        PlaylistProgressStore store = newPlaylistProgressStore();
        store.markFailed(PLAYLIST_ID, 11L, PlaylistProgressStore.TYPE_INSERT, "数据库写入失败", 1);

        PlaylistRetryJob job = retryJob(musicService, articleService, store, new PlaylistImportLock());
        List<ServerSentEvent<String>> events =
                job.retryFailed(PLAYLIST_ID, null, null).collectList().block(Duration.ofSeconds(60));

        assertThat(events).isNotNull();
        assertThat(store.read(PLAYLIST_ID).failedAlbums())
                .singleElement()
                .satisfies(entry -> assertThat(entry.type()).isEqualTo(PlaylistProgressStore.TYPE_TRANSIENT));
        // 取专辑失败 → failed 事件
        assertThat(events).extracting(ServerSentEvent::event).contains("failed");
        assertThat(events).extracting(ServerSentEvent::event).doesNotContain("error");
    }

    @Test
    @DisplayName("人工手动补导 → attempts 归零后重新给一份预算")
    void manualRetryResetsAttempts() {
        HomeMusicService musicService = mock(HomeMusicService.class);
        ArticleService articleService = mock(ArticleService.class);
        when(articleService.getByCloudMusicId(anyLong())).thenReturn(null);
        doThrow(new RuntimeException("数据库写入失败")).when(musicService).saveCloudMusicAlbum(11L);

        PlaylistProgressStore store = newPlaylistProgressStore();
        // 先攒到 attempts=3（预算上限）
        store.markFailed(PLAYLIST_ID, 11L, PlaylistProgressStore.TYPE_INSERT, "数据库写入失败", 1);
        store.markFailed(PLAYLIST_ID, 11L, PlaylistProgressStore.TYPE_INSERT, "数据库写入失败", 1);
        store.markFailed(PLAYLIST_ID, 11L, PlaylistProgressStore.TYPE_INSERT, "数据库写入失败", 1);
        assertThat(store.read(PLAYLIST_ID).failedAlbums())
                .singleElement()
                .satisfies(entry -> assertThat(entry.attempts()).isEqualTo(3));

        PlaylistRetryJob job = retryJob(musicService, articleService, store, new PlaylistImportLock());
        job.retryFailed(PLAYLIST_ID, null, null).collectList().block(Duration.ofSeconds(60));

        // 人工补导归零 + 本次失败一次 → attempts = 1（不是接着 3 累加成 4）
        assertThat(store.read(PLAYLIST_ID).failedAlbums())
                .singleElement()
                .satisfies(entry -> assertThat(entry.attempts()).isEqualTo(1));
    }

    @Test
    @DisplayName("默认只补 type=insert，permanent 与 transient 不自动补")
    void defaultOnlyRetriesInsertEntries() {
        HomeMusicService musicService = mock(HomeMusicService.class);
        ArticleService articleService = mock(ArticleService.class);
        when(articleService.getByCloudMusicId(anyLong())).thenReturn(null);

        PlaylistProgressStore store = newPlaylistProgressStore();
        store.markFailed(PLAYLIST_ID, 11L, PlaylistProgressStore.TYPE_PERMANENT, "上游返回 code=404", 1);
        store.markFailed(PLAYLIST_ID, 22L, PlaylistProgressStore.TYPE_TRANSIENT, "上游请求超时", 1);
        store.markFailed(PLAYLIST_ID, 33L, PlaylistProgressStore.TYPE_INSERT, "数据库写入失败", 1);

        PlaylistRetryJob job = retryJob(musicService, articleService, store, new PlaylistImportLock());
        job.retryFailed(PLAYLIST_ID, null, null).collectList().block(Duration.ofSeconds(60));

        verify(musicService, never()).saveCloudMusicAlbum(11L);
        verify(musicService, never()).saveCloudMusicAlbum(22L);
        verify(musicService).saveCloudMusicAlbum(33L);
        assertThat(store.failedAlbumIds(PLAYLIST_ID)).containsExactly(11L, 22L);
    }

    @Test
    @DisplayName("人工显式指定 albumIds 时可以强补 transient（听人的）")
    void explicitAlbumIdsCanForceRetryTransient() {
        HomeMusicService musicService = mock(HomeMusicService.class);
        ArticleService articleService = mock(ArticleService.class);
        when(articleService.getByCloudMusicId(anyLong())).thenReturn(null);

        PlaylistProgressStore store = newPlaylistProgressStore();
        store.markFailed(PLAYLIST_ID, 22L, PlaylistProgressStore.TYPE_TRANSIENT, "上游请求超时", 1);

        PlaylistRetryJob job = retryJob(musicService, articleService, store, new PlaylistImportLock());
        job.retryFailed(PLAYLIST_ID, null, "22").collectList().block(Duration.ofSeconds(60));

        verify(musicService).saveCloudMusicAlbum(22L);
        assertThat(store.failedAlbumIds(PLAYLIST_ID)).isEmpty();
    }

    @Test
    @DisplayName("同一歌单正在导入/补导时，补导请求被锁挡下（warn 事件，一个专辑都不处理）")
    void concurrentRetryIsRejected() {
        HomeMusicService musicService = mock(HomeMusicService.class);
        ArticleService articleService = mock(ArticleService.class);
        when(articleService.getByCloudMusicId(anyLong())).thenReturn(null);

        PlaylistProgressStore store = newPlaylistProgressStore();
        store.markFailed(PLAYLIST_ID, 11L, PlaylistProgressStore.TYPE_INSERT, "数据库写入失败", 1);

        PlaylistImportLock lock = new PlaylistImportLock();
        assertThat(lock.tryLock(PLAYLIST_ID)).isTrue();

        PlaylistRetryJob job = retryJob(musicService, articleService, store, lock);
        List<ServerSentEvent<String>> events =
                job.retryFailed(PLAYLIST_ID, null, null).collectList().block(Duration.ofSeconds(60));

        assertThat(events).isNotNull();
        assertThat(events).extracting(ServerSentEvent::event).contains("warn");
        assertThat(events).extracting(ServerSentEvent::data)
                .anyMatch(data -> data.contains("正在导入或补导中"));
        verify(musicService, never()).saveCloudMusicAlbum(anyLong());
        // 清单保持原样
        assertThat(store.failedAlbumIds(PLAYLIST_ID)).containsExactly(11L);
    }

    @Test
    @DisplayName("扫描器跳过 attempts 达预算上限的条目，不发请求")
    void schedulerSkipsExhaustedEntries() {
        HomeMusicService musicService = mock(HomeMusicService.class);
        ArticleService articleService = mock(ArticleService.class);
        when(articleService.getByCloudMusicId(anyLong())).thenReturn(null);

        PlaylistProgressStore store = newPlaylistProgressStore();
        // 攒到预算上限（3 次）
        store.markFailed(PLAYLIST_ID, 11L, PlaylistProgressStore.TYPE_INSERT, "数据库写入失败", 1);
        store.markFailed(PLAYLIST_ID, 11L, PlaylistProgressStore.TYPE_INSERT, "数据库写入失败", 1);
        store.markFailed(PLAYLIST_ID, 11L, PlaylistProgressStore.TYPE_INSERT, "数据库写入失败", 1);

        FailedAlbumRetryScheduler scheduler = scheduler(musicService, articleService, store, 3);
        int retried = scheduler.scanOnce();

        assertThat(retried).isZero();
        verify(musicService, never()).saveCloudMusicAlbum(anyLong());
        assertThat(store.failedAlbumIds(PLAYLIST_ID)).containsExactly(11L);
    }

    @Test
    @DisplayName("扫描器绝不对 permanent 发请求")
    void schedulerNeverRequestsPermanentEntries() {
        HomeMusicService musicService = mock(HomeMusicService.class);
        ArticleService articleService = mock(ArticleService.class);
        when(articleService.getByCloudMusicId(anyLong())).thenReturn(null);

        PlaylistProgressStore store = newPlaylistProgressStore();
        store.markFailed(PLAYLIST_ID, 11L, PlaylistProgressStore.TYPE_PERMANENT, "上游返回 code=404", 1);

        FailedAlbumRetryScheduler scheduler = scheduler(musicService, articleService, store, 3);
        int retried = scheduler.scanOnce();

        assertThat(retried).isZero();
        verify(musicService, never()).saveCloudMusicAlbum(anyLong());
        assertThat(store.failedAlbumIds(PLAYLIST_ID)).containsExactly(11L);
    }

    @Test
    @DisplayName("扫描器补导 insert 且未达预算上限的条目，成功后销账")
    void schedulerRetriesInsertEntryWithinBudget() {
        HomeMusicService musicService = mock(HomeMusicService.class);
        ArticleService articleService = mock(ArticleService.class);
        when(articleService.getByCloudMusicId(anyLong())).thenReturn(null);

        PlaylistProgressStore store = newPlaylistProgressStore();
        store.markFailed(PLAYLIST_ID, 11L, PlaylistProgressStore.TYPE_INSERT, "数据库写入失败", 1);

        FailedAlbumRetryScheduler scheduler = scheduler(musicService, articleService, store, 3);
        int retried = scheduler.scanOnce();

        assertThat(retried).isEqualTo(1);
        verify(musicService).saveCloudMusicAlbum(11L);
        assertThat(store.failedAlbumIds(PLAYLIST_ID)).isEmpty();
    }

    @Test
    @DisplayName("扫描器重试不重置 attempts（只有人工补导才重置）")
    void schedulerDoesNotResetAttempts() {
        HomeMusicService musicService = mock(HomeMusicService.class);
        ArticleService articleService = mock(ArticleService.class);
        when(articleService.getByCloudMusicId(anyLong())).thenReturn(null);
        doThrow(new RuntimeException("数据库写入失败")).when(musicService).saveCloudMusicAlbum(11L);

        PlaylistProgressStore store = newPlaylistProgressStore();
        store.markFailed(PLAYLIST_ID, 11L, PlaylistProgressStore.TYPE_INSERT, "数据库写入失败", 1);
        store.markFailed(PLAYLIST_ID, 11L, PlaylistProgressStore.TYPE_INSERT, "数据库写入失败", 1);

        FailedAlbumRetryScheduler scheduler = scheduler(musicService, articleService, store, 3);
        scheduler.scanOnce();

        // 扫描不归零：2 次 + 本次失败 = 3
        assertThat(store.read(PLAYLIST_ID).failedAlbums())
                .singleElement()
                .satisfies(entry -> assertThat(entry.attempts()).isEqualTo(3));
    }

    @Test
    @DisplayName("扫描器不碰 transient（由导入流的水位机制自动重试）")
    void schedulerIgnoresTransientEntries() {
        HomeMusicService musicService = mock(HomeMusicService.class);
        ArticleService articleService = mock(ArticleService.class);
        when(articleService.getByCloudMusicId(anyLong())).thenReturn(null);

        PlaylistProgressStore store = newPlaylistProgressStore();
        store.markFailed(PLAYLIST_ID, 22L, PlaylistProgressStore.TYPE_TRANSIENT, "上游请求超时", 1);

        FailedAlbumRetryScheduler scheduler = scheduler(musicService, articleService, store, 3);
        int retried = scheduler.scanOnce();

        assertThat(retried).isZero();
        verify(musicService, never()).saveCloudMusicAlbum(anyLong());
    }

    private PlaylistProgressStore newPlaylistProgressStore() {
        return new PlaylistProgressStore(tempDir.resolve("progress"));
    }

    private PlaylistRetryJob retryJob(HomeMusicService musicService, ArticleService articleService,
                                      PlaylistProgressStore store, PlaylistImportLock lock) {
        AlbumImportRunner runner = new AlbumImportRunner(musicService, articleService);
        FailedAlbumRetryService retryService = new FailedAlbumRetryService(store, runner);
        return new PlaylistRetryJob(store, retryService, lock);
    }

    private FailedAlbumRetryScheduler scheduler(HomeMusicService musicService, ArticleService articleService,
                                                 PlaylistProgressStore store, int retryBudget) {
        AlbumImportRunner runner = new AlbumImportRunner(musicService, articleService);
        FailedAlbumRetryService retryService = new FailedAlbumRetryService(store, runner);
        return new FailedAlbumRetryScheduler(store, retryService, new PlaylistImportLock(), retryBudget);
    }
}
