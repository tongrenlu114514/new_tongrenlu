package info.tongrenlu;

import info.tongrenlu.service.ArticleService;
import info.tongrenlu.service.HomeMusicService;
import info.tongrenlu.support.AlbumImportRunner;
import info.tongrenlu.support.CloudMusicAlbumFetchException;
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
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 歌单导入任务的行为验证：
 * 断点续传、换歌单从 0、序号越界不崩，以及临时/永久失败的分流与事件口径。
 */
class PlaylistImportJobResumeTest {

    private static final Long PLAYLIST_ID = 100L;
    private static final Long OTHER_PLAYLIST_ID = 200L;

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("进度文件歌单匹配时从断点继续导入，不再重复处理前面的专辑")
    void resumesFromSavedIndex() {
        HomeMusicService musicService = mock(HomeMusicService.class);
        ArticleService articleService = mock(ArticleService.class);
        when(musicService.getAllPlaylistAlbumIds(PLAYLIST_ID)).thenReturn(List.of(11L, 22L, 33L));
        when(articleService.getByCloudMusicId(anyLong())).thenReturn(null);

        PlaylistProgressStore store = new PlaylistProgressStore(tempDir.resolve("progress"));
        store.saveIndex(PLAYLIST_ID, 1);

        PlaylistImportJob job = new PlaylistImportJob(musicService, new AlbumImportRunner(musicService, articleService), store, new PlaylistImportLock());
        List<ServerSentEvent<String>> events = job.run(PLAYLIST_ID).collectList().block(Duration.ofSeconds(60));

        assertThat(events).isNotNull();
        assertThat(events).extracting(ServerSentEvent::data)
                .anyMatch(data -> data.contains("从第 2 个专辑继续导入"));
        verify(musicService, never()).saveCloudMusicAlbum(11L);
        verify(musicService).saveCloudMusicAlbum(22L);
        verify(musicService).saveCloudMusicAlbum(33L);
    }

    @Test
    @DisplayName("进度文件属于其它歌单时从头开始，不静默漏导前面的专辑")
    void mismatchedPlaylistIdStartsFromBeginning() {
        HomeMusicService musicService = mock(HomeMusicService.class);
        ArticleService articleService = mock(ArticleService.class);
        when(musicService.getAllPlaylistAlbumIds(PLAYLIST_ID)).thenReturn(List.of(11L, 22L, 33L));
        when(articleService.getByCloudMusicId(anyLong())).thenReturn(null);

        PlaylistProgressStore store = new PlaylistProgressStore(tempDir.resolve("progress"));
        store.saveIndex(999L, 2);

        PlaylistImportJob job = new PlaylistImportJob(musicService, new AlbumImportRunner(musicService, articleService), store, new PlaylistImportLock());
        List<ServerSentEvent<String>> events = job.run(PLAYLIST_ID).collectList().block(Duration.ofSeconds(60));

        assertThat(events).isNotNull();
        assertThat(events).extracting(ServerSentEvent::data)
                .noneMatch(data -> data.contains("继续导入"));
        verify(musicService).saveCloudMusicAlbum(11L);
        verify(musicService).saveCloudMusicAlbum(22L);
        verify(musicService).saveCloudMusicAlbum(33L);
    }

    @Test
    @DisplayName("进度序号越界时归零从头开始，不抛 IndexOutOfBoundsException")
    void outOfRangeIndexStartsFromBeginning() {
        HomeMusicService musicService = mock(HomeMusicService.class);
        ArticleService articleService = mock(ArticleService.class);
        when(musicService.getAllPlaylistAlbumIds(PLAYLIST_ID)).thenReturn(List.of(11L, 22L, 33L));
        when(articleService.getByCloudMusicId(anyLong())).thenReturn(null);

        PlaylistProgressStore store = new PlaylistProgressStore(tempDir.resolve("progress"));
        // 当前歌单只有 3 个专辑，进度序号 99 已越界
        store.saveIndex(PLAYLIST_ID, 99);

        PlaylistImportJob job = new PlaylistImportJob(musicService, new AlbumImportRunner(musicService, articleService), store, new PlaylistImportLock());
        List<ServerSentEvent<String>> events = job.run(PLAYLIST_ID).collectList().block(Duration.ofSeconds(60));

        assertThat(events).isNotNull();
        verify(musicService).saveCloudMusicAlbum(11L);
        verify(musicService).saveCloudMusicAlbum(22L);
        verify(musicService).saveCloudMusicAlbum(33L);
    }

    @Test
    @DisplayName("永久失败（取专辑）：只发 failed 不发 error、不计成功、记入失败清单、推进进度、整流走完")
    void permanentFailureEmitsFailedOnlyAndIsRecorded() {
        HomeMusicService musicService = mock(HomeMusicService.class);
        ArticleService articleService = mock(ArticleService.class);
        when(musicService.getAllPlaylistAlbumIds(PLAYLIST_ID)).thenReturn(List.of(11L, 22L));
        when(articleService.getByCloudMusicId(anyLong())).thenReturn(null);
        doThrow(new CloudMusicAlbumFetchException(11L, "上游返回 code=404", false))
                .when(musicService).saveCloudMusicAlbum(11L);

        PlaylistProgressStore store = new PlaylistProgressStore(tempDir.resolve("progress"));
        PlaylistImportJob job = new PlaylistImportJob(musicService, new AlbumImportRunner(musicService, articleService), store, new PlaylistImportLock());
        List<ServerSentEvent<String>> events = job.run(PLAYLIST_ID).collectList().block(Duration.ofSeconds(60));

        assertThat(events).isNotNull();
        // 有 failed 事件，且标明不可重试
        assertThat(events).extracting(ServerSentEvent::data)
                .anyMatch(data -> data.contains("可重试=false") && data.contains("albumId=11"));
        // 取专辑失败不发 error（error 是旧事件，语义固定为「导入过程抛异常」）
        assertThat(events).extracting(ServerSentEvent::event)
                .doesNotContain("error");
        // 失败的专辑不计成功，后续专辑照常成功
        assertThat(events).extracting(ServerSentEvent::data)
                .noneMatch(data -> data.contains("专辑导入成功: albumId=11"));
        assertThat(events).extracting(ServerSentEvent::data)
                .anyMatch(data -> data.contains("专辑导入成功: albumId=22"));
        // 失败清单落盘 + 进度推进
        assertThat(store.failedAlbumIds(PLAYLIST_ID)).containsExactly(11L);
        assertThat(store.read(PLAYLIST_ID).failedAlbums())
                .singleElement()
                .satisfies(entry -> {
                    assertThat(entry.type()).isEqualTo(PlaylistProgressStore.TYPE_PERMANENT);
                    assertThat(entry.reason()).isEqualTo("上游返回 code=404");
                    assertThat(entry.at()).isNotBlank();
                });
        assertThat(store.read(PLAYLIST_ID).index()).isEqualTo(2);
        // 清单非空 → 进度文件保留，并在 stats 里说明失败构成
        assertThat(store.progressFile(PLAYLIST_ID)).exists();
        assertThat(events).extracting(ServerSentEvent::data)
                .anyMatch(data -> data.contains("本次失败 1 个专辑（永久 1 / 临时 0 / 入库 0）"));
        assertThat(events).extracting(ServerSentEvent::data)
                .anyMatch(data -> data.contains("失败清单共 1 个专辑，已保留在进度文件中"));
        // 整流正常走完
        assertThat(events).extracting(ServerSentEvent::data)
                .anyMatch(data -> data.contains("导入完成！"));
    }

    @Test
    @DisplayName("非取专辑异常（入库等）：只发 error 不发 failed")
    void nonFetchExceptionEmitsErrorOnly() {
        HomeMusicService musicService = mock(HomeMusicService.class);
        ArticleService articleService = mock(ArticleService.class);
        when(musicService.getAllPlaylistAlbumIds(PLAYLIST_ID)).thenReturn(List.of(11L, 22L));
        when(articleService.getByCloudMusicId(anyLong())).thenReturn(null);
        doThrow(new RuntimeException("数据库写入失败")).when(musicService).saveCloudMusicAlbum(11L);

        PlaylistProgressStore store = new PlaylistProgressStore(tempDir.resolve("progress"));
        PlaylistImportJob job = new PlaylistImportJob(musicService, new AlbumImportRunner(musicService, articleService), store, new PlaylistImportLock());
        List<ServerSentEvent<String>> events = job.run(PLAYLIST_ID).collectList().block(Duration.ofSeconds(60));

        assertThat(events).isNotNull();
        // 有 error 事件（老事件，格式未改）
        assertThat(events).extracting(ServerSentEvent::event).contains("error");
        assertThat(events).extracting(ServerSentEvent::data)
                .anyMatch(data -> data.contains("专辑导入失败: albumId=11, error=数据库写入失败"));
        // 不发 failed 事件
        assertThat(events).extracting(ServerSentEvent::event).doesNotContain("failed");
        // 但入库失败必须记入清单（type=insert），否则整流结束时进度被清掉就静默丢了
        assertThat(store.failedAlbumIds(PLAYLIST_ID)).containsExactly(11L);
        assertThat(store.read(PLAYLIST_ID).failedAlbums())
                .singleElement()
                .satisfies(entry -> {
                    assertThat(entry.type()).isEqualTo(PlaylistProgressStore.TYPE_INSERT);
                    assertThat(entry.reason()).isEqualTo("数据库写入失败");
                });
        assertThat(store.progressFile(PLAYLIST_ID)).exists();
        assertThat(events).extracting(ServerSentEvent::data)
                .anyMatch(data -> data.contains("本次失败 1 个专辑（永久 0 / 临时 0 / 入库 1）"));
    }

    @Test
    @DisplayName("第 1 次临时失败：计数 1，流继续处理后续专辑，不中止")
    void firstTransientFailureContinuesFlow() {
        HomeMusicService musicService = mock(HomeMusicService.class);
        ArticleService articleService = mock(ArticleService.class);
        when(musicService.getAllPlaylistAlbumIds(PLAYLIST_ID)).thenReturn(List.of(11L, 22L));
        when(articleService.getByCloudMusicId(anyLong())).thenReturn(null);
        doThrow(new CloudMusicAlbumFetchException(11L, "上游请求失败或超时", true))
                .when(musicService).saveCloudMusicAlbum(11L);

        PlaylistProgressStore store = new PlaylistProgressStore(tempDir.resolve("progress"));
        PlaylistImportJob job = new PlaylistImportJob(musicService, new AlbumImportRunner(musicService, articleService), store, new PlaylistImportLock());
        List<ServerSentEvent<String>> events = job.run(PLAYLIST_ID).collectList().block(Duration.ofSeconds(60));

        assertThat(events).isNotNull();
        // 只发 failed，不发 error
        assertThat(events).extracting(ServerSentEvent::event).contains("failed");
        assertThat(events).extracting(ServerSentEvent::event).doesNotContain("error");
        // 未达阈值 → 不中止，后续专辑继续处理
        assertThat(events).extracting(ServerSentEvent::data)
                .noneMatch(data -> data.contains("导入过程发生异常"));
        assertThat(events).extracting(ServerSentEvent::data)
                .anyMatch(data -> data.contains("专辑导入成功: albumId=22"));
        verify(musicService).saveCloudMusicAlbum(22L);
        // 临时失败也要落盘记账（清单里标 transient）→ 清单非空 → 进度文件保留 → 下轮从断点重试
        assertThat(store.failedAlbumIds(PLAYLIST_ID)).containsExactly(11L);
        assertThat(store.read(PLAYLIST_ID).failedAlbums())
                .singleElement()
                .satisfies(entry -> {
                    assertThat(entry.type()).isEqualTo(PlaylistProgressStore.TYPE_TRANSIENT);
                    assertThat(entry.reason()).isEqualTo("上游请求失败或超时");
                });
        assertThat(store.progressFile(PLAYLIST_ID)).exists();
        // 水位不越过失败专辑 → 下轮从第一个专辑重来
        assertThat(store.readIndex(PLAYLIST_ID)).isZero();
        assertThat(events).extracting(ServerSentEvent::data)
                .anyMatch(data -> data.contains("本次失败 1 个专辑（永久 0 / 临时 1 / 入库 0）"));
    }

    @Test
    @DisplayName("连续第 2 次临时失败：中止整流 + 保留进度文件 + 不计成功")
    void secondConsecutiveTransientFailureAbortsAndKeepsProgress() {
        HomeMusicService musicService = mock(HomeMusicService.class);
        ArticleService articleService = mock(ArticleService.class);
        when(musicService.getAllPlaylistAlbumIds(PLAYLIST_ID)).thenReturn(List.of(11L, 22L, 33L));
        when(articleService.getByCloudMusicId(anyLong())).thenReturn(null);
        // 第一个成功（写入进度），随后连续两个临时失败 → 第 2 次连续失败即中止
        doThrow(new CloudMusicAlbumFetchException(22L, "上游请求失败或超时", true))
                .when(musicService).saveCloudMusicAlbum(22L);
        doThrow(new CloudMusicAlbumFetchException(33L, "上游请求失败或超时", true))
                .when(musicService).saveCloudMusicAlbum(33L);

        PlaylistProgressStore store = new PlaylistProgressStore(tempDir.resolve("progress"));
        PlaylistImportJob job = new PlaylistImportJob(musicService, new AlbumImportRunner(musicService, articleService), store, new PlaylistImportLock());
        List<ServerSentEvent<String>> events = job.run(PLAYLIST_ID).collectList().block(Duration.ofSeconds(60));

        assertThat(events).isNotNull();
        // 中止整流（fatal）
        assertThat(events).extracting(ServerSentEvent::data)
                .anyMatch(data -> data.contains("导入过程发生异常"));
        assertThat(events).extracting(ServerSentEvent::data)
                .noneMatch(data -> data.contains("导入完成！"));
        // 两个取专辑失败都只发 failed，没有 error 事件
        assertThat(events).extracting(ServerSentEvent::event)
                .containsOnly("start", "info", "success", "failed", "fatal");
        // 不计成功（只有第 1 个成功）
        assertThat(events).extracting(ServerSentEvent::data)
                .noneMatch(data -> data.contains("专辑导入成功: albumId=22"));
        // 进度文件保留，序号停在第 1 个成功处（不越过失败专辑）
        assertThat(store.progressFile(PLAYLIST_ID)).exists();
        assertThat(store.read(PLAYLIST_ID).index()).isEqualTo(1);
        // 两个临时失败都落盘记账了
        assertThat(store.failedAlbumIds(PLAYLIST_ID)).containsExactly(22L, 33L);
        assertThat(store.read(PLAYLIST_ID).failedAlbums())
                .extracting(PlaylistProgressStore.FailedAlbum::type)
                .containsOnly(PlaylistProgressStore.TYPE_TRANSIENT);
    }

    @Test
    @DisplayName("失败一次 → 下一个专辑成功 → 计数清零 → 再失败一次不中止")
    void transientFailureCounterResetsAfterSuccess() {
        HomeMusicService musicService = mock(HomeMusicService.class);
        ArticleService articleService = mock(ArticleService.class);
        when(musicService.getAllPlaylistAlbumIds(PLAYLIST_ID)).thenReturn(List.of(11L, 22L, 33L, 44L));
        when(articleService.getByCloudMusicId(anyLong())).thenReturn(null);
        doThrow(new CloudMusicAlbumFetchException(11L, "上游请求失败或超时", true))
                .when(musicService).saveCloudMusicAlbum(11L);
        doThrow(new CloudMusicAlbumFetchException(33L, "上游请求失败或超时", true))
                .when(musicService).saveCloudMusicAlbum(33L);

        PlaylistProgressStore store = new PlaylistProgressStore(tempDir.resolve("progress"));
        PlaylistImportJob job = new PlaylistImportJob(musicService, new AlbumImportRunner(musicService, articleService), store, new PlaylistImportLock());
        List<ServerSentEvent<String>> events = job.run(PLAYLIST_ID).collectList().block(Duration.ofSeconds(60));

        assertThat(events).isNotNull();
        // 两次失败但中间有成功 → 连续计数被清零 → 不中止，整流走完
        assertThat(events).extracting(ServerSentEvent::data)
                .noneMatch(data -> data.contains("导入过程发生异常"));
        assertThat(events).extracting(ServerSentEvent::data)
                .anyMatch(data -> data.contains("导入完成！"));
        assertThat(events).extracting(ServerSentEvent::data)
                .anyMatch(data -> data.contains("专辑导入成功: albumId=22"));
        assertThat(events).extracting(ServerSentEvent::data)
                .anyMatch(data -> data.contains("专辑导入成功: albumId=44"));
    }

    @Test
    @DisplayName("核心回归：临时失败后进度不越过失败专辑")
    void progressNeverPassesFailedAlbum() {
        HomeMusicService musicService = mock(HomeMusicService.class);
        ArticleService articleService = mock(ArticleService.class);
        when(musicService.getAllPlaylistAlbumIds(PLAYLIST_ID)).thenReturn(List.of(11L, 22L, 33L, 44L, 55L));
        when(articleService.getByCloudMusicId(anyLong())).thenReturn(null);
        // 前 3 个成功，第 4、5 个连续临时失败 → 中止整流，进度文件保留
        doThrow(new CloudMusicAlbumFetchException(44L, "上游请求失败或超时", true))
                .when(musicService).saveCloudMusicAlbum(44L);
        doThrow(new CloudMusicAlbumFetchException(55L, "上游请求失败或超时", true))
                .when(musicService).saveCloudMusicAlbum(55L);

        PlaylistProgressStore store = new PlaylistProgressStore(tempDir.resolve("progress"));
        PlaylistImportJob job = new PlaylistImportJob(musicService, new AlbumImportRunner(musicService, articleService), store, new PlaylistImportLock());
        List<ServerSentEvent<String>> events = job.run(PLAYLIST_ID).collectList().block(Duration.ofSeconds(60));

        assertThat(events).isNotNull();
        // 前 3 个成功 → 水位推进到 3；第 4 个失败 → 进度不越过它
        assertThat(store.progressFile(PLAYLIST_ID)).exists();
        assertThat(store.read(PLAYLIST_ID).index()).isEqualTo(3);
        // 临时失败同样进清单（带 transient 类型），但水位依旧不越过它
        assertThat(store.failedAlbumIds(PLAYLIST_ID)).containsExactly(44L, 55L);
    }

    @Test
    @DisplayName("全部成功时整流走完并清除进度文件")
    void successRunClearsProgressFile() {
        HomeMusicService musicService = mock(HomeMusicService.class);
        ArticleService articleService = mock(ArticleService.class);
        when(musicService.getAllPlaylistAlbumIds(PLAYLIST_ID)).thenReturn(List.of(11L, 22L));
        when(articleService.getByCloudMusicId(anyLong())).thenReturn(null);

        PlaylistProgressStore store = new PlaylistProgressStore(tempDir.resolve("progress"));
        PlaylistImportJob job = new PlaylistImportJob(musicService, new AlbumImportRunner(musicService, articleService), store, new PlaylistImportLock());
        List<ServerSentEvent<String>> events = job.run(PLAYLIST_ID).collectList().block(Duration.ofSeconds(60));

        assertThat(events).isNotNull();
        assertThat(events).extracting(ServerSentEvent::data)
                .anyMatch(data -> data.contains("导入完成！"));
        // 失败清单为空 → 进度文件被清除
        assertThat(store.progressFile(PLAYLIST_ID)).doesNotExist();
    }

    @Test
    @DisplayName("临时失败落盘 → 下轮从断点重试 → 成功后销账 → 清单空 → 进度文件清除")
    void transientFailureIsRetriedNextRoundAndClearedAfterSuccess() {
        HomeMusicService musicService = mock(HomeMusicService.class);
        ArticleService articleService = mock(ArticleService.class);
        when(musicService.getAllPlaylistAlbumIds(PLAYLIST_ID)).thenReturn(List.of(11L, 22L));
        when(articleService.getByCloudMusicId(anyLong())).thenReturn(null);
        doThrow(new CloudMusicAlbumFetchException(11L, "上游请求失败或超时", true))
                .when(musicService).saveCloudMusicAlbum(11L);

        PlaylistProgressStore store = new PlaylistProgressStore(tempDir.resolve("progress"));
        PlaylistImportJob job = new PlaylistImportJob(musicService, new AlbumImportRunner(musicService, articleService), store, new PlaylistImportLock());

        // 第一轮：11 临时失败（未达中止阈值），22 成功
        List<ServerSentEvent<String>> firstRound = job.run(PLAYLIST_ID).collectList().block(Duration.ofSeconds(60));
        assertThat(firstRound).isNotNull();
        // 失败落盘 + 进度文件保留 + 水位停在失败专辑之前
        assertThat(store.failedAlbumIds(PLAYLIST_ID)).containsExactly(11L);
        assertThat(store.progressFile(PLAYLIST_ID)).exists();
        assertThat(store.readIndex(PLAYLIST_ID)).isZero();

        // 第二轮：上游恢复
        doNothing().when(musicService).saveCloudMusicAlbum(11L);
        List<ServerSentEvent<String>> secondRound = job.run(PLAYLIST_ID).collectList().block(Duration.ofSeconds(60));
        assertThat(secondRound).isNotNull();

        // 第二轮确实从断点重试了 11，不是跳过
        verify(musicService, times(2)).saveCloudMusicAlbum(11L);
        // 成功后从清单销账 → 清单空 → 进度文件清除
        assertThat(store.read(PLAYLIST_ID).failedAlbums()).isEmpty();
        assertThat(store.progressFile(PLAYLIST_ID)).doesNotExist();
    }

    @Test
    @DisplayName("两个不同歌单各自写自己的进度文件，互不覆盖")
    void differentPlaylistsUseSeparateFiles() {
        HomeMusicService musicService = mock(HomeMusicService.class);
        ArticleService articleService = mock(ArticleService.class);
        when(musicService.getAllPlaylistAlbumIds(PLAYLIST_ID)).thenReturn(List.of(11L));
        when(musicService.getAllPlaylistAlbumIds(OTHER_PLAYLIST_ID)).thenReturn(List.of(22L));
        when(articleService.getByCloudMusicId(anyLong())).thenReturn(null);
        // 歌单 100 的第一个专辑永久失败 → 其进度文件保留，便于验证两歌单互不影响
        doThrow(new CloudMusicAlbumFetchException(11L, "上游返回 code=404", false))
                .when(musicService).saveCloudMusicAlbum(11L);

        PlaylistProgressStore store = new PlaylistProgressStore(tempDir.resolve("progress"));
        PlaylistImportJob job = new PlaylistImportJob(musicService, new AlbumImportRunner(musicService, articleService), store, new PlaylistImportLock());
        job.run(PLAYLIST_ID).collectList().block(Duration.ofSeconds(60));
        job.run(OTHER_PLAYLIST_ID).collectList().block(Duration.ofSeconds(60));

        assertThat(store.progressFile(PLAYLIST_ID)).exists();
        assertThat(store.failedAlbumIds(PLAYLIST_ID)).containsExactly(11L);
        // 歌单 200 全部成功 → 自己的文件已清除，且没有影响歌单 100
        assertThat(store.progressFile(OTHER_PLAYLIST_ID)).doesNotExist();
        assertThat(store.read(PLAYLIST_ID).index()).isEqualTo(1);
    }

    @Test
    @DisplayName("同一歌单正在补导时，导入请求被锁挡下（warn 事件，不碰进度文件）")
    void concurrentRetryBlocksImport() {
        HomeMusicService musicService = mock(HomeMusicService.class);
        ArticleService articleService = mock(ArticleService.class);
        when(musicService.getAllPlaylistAlbumIds(PLAYLIST_ID)).thenReturn(List.of(11L));
        when(articleService.getByCloudMusicId(anyLong())).thenReturn(null);

        PlaylistProgressStore store = new PlaylistProgressStore(tempDir.resolve("progress"));
        PlaylistImportLock lock = new PlaylistImportLock();
        // 模拟补导正在跑：同一歌单的锁已被占用
        assertThat(lock.tryLock(PLAYLIST_ID)).isTrue();

        PlaylistImportJob job = new PlaylistImportJob(musicService, new AlbumImportRunner(musicService, articleService), store, lock);
        List<ServerSentEvent<String>> events = job.run(PLAYLIST_ID).collectList().block(Duration.ofSeconds(60));

        assertThat(events).isNotNull();
        assertThat(events).extracting(ServerSentEvent::event).contains("warn");
        assertThat(events).extracting(ServerSentEvent::data)
                .anyMatch(data -> data.contains("正在导入或补导中"));
        // 被挡下时一个专辑都不处理，也不写进度文件
        verify(musicService, never()).saveCloudMusicAlbum(anyLong());
        assertThat(store.progressFile(PLAYLIST_ID)).doesNotExist();
    }
}
