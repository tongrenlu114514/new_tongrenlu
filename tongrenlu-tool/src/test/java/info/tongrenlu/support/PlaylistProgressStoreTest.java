package info.tongrenlu.support;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 断点续传进度存储的单元测试：按歌单分文件、失败清单随进度落盘、清单非空不删文件。
 */
class PlaylistProgressStoreTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("默认进度目录是运行目录下的 data/playlist_progress（相对路径）")
    void defaultDirIsRelativeToWorkingDirectory() {
        Path resolved = PlaylistProgressStore.resolveConfiguredDir(null);

        assertThat(resolved.toString().replace('\\', '/')).endsWith("/data/playlist_progress");
        assertThat(PlaylistProgressStore.DEFAULT_RELATIVE_DIR).isEqualTo("data/playlist_progress");
    }

    @Test
    @DisplayName("Spring 配置项 app.playlist-import.progress-dir 优先级高于系统属性")
    void springConfiguredDirTakesPrecedence() {
        Path custom = tempDir.resolve("spring-progress").toAbsolutePath().normalize();
        String previous = System.getProperty(PlaylistProgressStore.PROGRESS_DIR_PROPERTY);
        System.setProperty(PlaylistProgressStore.PROGRESS_DIR_PROPERTY,
                tempDir.resolve("sys-progress").toString());
        try {
            assertThat(PlaylistProgressStore.resolveConfiguredDir(custom.toString())).isEqualTo(custom);
        } finally {
            if (previous == null) {
                System.clearProperty(PlaylistProgressStore.PROGRESS_DIR_PROPERTY);
            } else {
                System.setProperty(PlaylistProgressStore.PROGRESS_DIR_PROPERTY, previous);
            }
        }
    }

    @Test
    @DisplayName("每个歌单一个文件，互不覆盖（并发导入不同歌单）")
    void separateFilePerPlaylist() {
        PlaylistProgressStore store = new PlaylistProgressStore(tempDir.resolve("progress"));

        store.saveIndex(100L, 3);
        store.saveIndex(200L, 7);

        assertThat(store.progressFile(100L)).isNotEqualTo(store.progressFile(200L));
        assertThat(store.progressFile(100L)).exists();
        assertThat(store.progressFile(200L)).exists();
        assertThat(store.readIndex(100L)).isEqualTo(3);
        assertThat(store.readIndex(200L)).isEqualTo(7);
    }

    @Test
    @DisplayName("失败清单随进度一起落盘，清单非空时进度文件不被清除")
    void failedListPersistsAndBlocksClear() {
        PlaylistProgressStore store = new PlaylistProgressStore(tempDir.resolve("progress"));

        store.markFailed(100L, 55L, PlaylistProgressStore.TYPE_PERMANENT, "上游返回 code=404", 1);

        assertThat(store.failedAlbumIds(100L)).containsExactly(55L);
        assertThat(store.read(100L).failedAlbums())
                .singleElement()
                .satisfies(entry -> {
                    assertThat(entry.albumId()).isEqualTo(55L);
                    assertThat(entry.type()).isEqualTo(PlaylistProgressStore.TYPE_PERMANENT);
                    assertThat(entry.reason()).isEqualTo("上游返回 code=404");
                    assertThat(entry.at()).isNotBlank();
                });
        assertThat(store.clear(100L)).isFalse();
        assertThat(store.progressFile(100L)).exists();
    }

    @Test
    @DisplayName("失败清单条目带类型与原因：永久 / 临时 / 入库三类分别落盘")
    void failedEntriesCarryTypeAndReason() {
        PlaylistProgressStore store = new PlaylistProgressStore(tempDir.resolve("progress"));

        store.markFailed(100L, 11L, PlaylistProgressStore.TYPE_PERMANENT, "上游返回 code=404", 1);
        store.markFailed(100L, 22L, PlaylistProgressStore.TYPE_TRANSIENT, "上游请求超时", 1);
        store.markFailed(100L, 33L, PlaylistProgressStore.TYPE_INSERT, "数据库写入失败", 1);

        assertThat(store.read(100L).failedAlbums())
                .extracting(PlaylistProgressStore.FailedAlbum::albumId)
                .containsExactly(11L, 22L, 33L);
        assertThat(store.read(100L).failedAlbums())
                .extracting(PlaylistProgressStore.FailedAlbum::type)
                .containsExactly(PlaylistProgressStore.TYPE_PERMANENT,
                        PlaylistProgressStore.TYPE_TRANSIENT,
                        PlaylistProgressStore.TYPE_INSERT);
        assertThat(store.read(100L).failedAlbums())
                .extracting(PlaylistProgressStore.FailedAlbum::reason)
                .containsExactly("上游返回 code=404", "上游请求超时", "数据库写入失败");
    }

    @Test
    @DisplayName("临时失败也记账：清单里只剩 transient 条目时进度文件同样保留")
    void transientOnlyFailureStillBlocksClear() {
        PlaylistProgressStore store = new PlaylistProgressStore(tempDir.resolve("progress"));

        store.markFailed(100L, 77L, PlaylistProgressStore.TYPE_TRANSIENT, "上游请求超时", 2);

        assertThat(store.failedAlbumIds(100L)).containsExactly(77L);
        assertThat(store.clear(100L)).isFalse();
        assertThat(store.progressFile(100L)).exists();
        assertThat(store.readIndex(100L)).isEqualTo(2);
    }

    @Test
    @DisplayName("专辑成功后从失败清单移除，清单空了下次 clear 才会清文件")
    void markSuccessRemovesEntryFromFailedList() {
        PlaylistProgressStore store = new PlaylistProgressStore(tempDir.resolve("progress"));
        store.markFailed(100L, 77L, PlaylistProgressStore.TYPE_TRANSIENT, "上游请求超时", 1);

        store.markSuccess(100L, 77L, 2);

        assertThat(store.read(100L).failedAlbums()).isEmpty();
        assertThat(store.readIndex(100L)).isEqualTo(2);
        assertThat(store.clear(100L)).isTrue();
        assertThat(store.progressFile(100L)).doesNotExist();
    }

    @Test
    @DisplayName("命中「已存在」同样算成功 → 从失败清单销账")
    void markSuccessOnExistingAlbumAlsoClearsEntry() {
        PlaylistProgressStore store = new PlaylistProgressStore(tempDir.resolve("progress"));
        store.markFailed(100L, 88L, PlaylistProgressStore.TYPE_INSERT, "数据库写入失败", 1);

        store.markSuccess(100L, 88L, 2);

        assertThat(store.failedAlbumIds(100L)).isEmpty();
    }

    @Test
    @DisplayName("同一专辑重复失败只保留最新条目，清单不膨胀")
    void repeatedFailureDoesNotDuplicateEntries() {
        PlaylistProgressStore store = new PlaylistProgressStore(tempDir.resolve("progress"));

        store.markFailed(100L, 55L, PlaylistProgressStore.TYPE_TRANSIENT, "上游请求超时", 1);
        store.markFailed(100L, 55L, PlaylistProgressStore.TYPE_PERMANENT, "上游返回 code=404", 2);

        assertThat(store.failedAlbumIds(100L)).containsExactly(55L);
        assertThat(store.read(100L).failedAlbums())
                .singleElement()
                .satisfies(entry -> {
                    assertThat(entry.type()).isEqualTo(PlaylistProgressStore.TYPE_PERMANENT);
                    assertThat(entry.reason()).isEqualTo("上游返回 code=404");
                });
    }

    @Test
    @DisplayName("失败清单为空时才清除进度文件")
    void clearRemovesWhenNoFailures() {
        PlaylistProgressStore store = new PlaylistProgressStore(tempDir.resolve("progress"));
        store.saveIndex(100L, 3);

        assertThat(store.clear(100L)).isTrue();
        assertThat(store.progressFile(100L)).doesNotExist();
    }

    @Test
    @DisplayName("进度文件内容是含 playlistId / index / failedAlbums 的 JSON")
    void progressFileIsJson() throws Exception {
        PlaylistProgressStore store = new PlaylistProgressStore(tempDir.resolve("progress"));
        store.markFailed(100L, 55L, PlaylistProgressStore.TYPE_PERMANENT, "上游返回 code=404", 2);

        String content = Files.readString(store.progressFile(100L), StandardCharsets.UTF_8);
        assertThat(content).contains("\"playlistId\":100").contains("\"index\":2").contains("\"albumId\":55")
                .contains("\"type\":\"permanent\"").contains("上游返回 code=404");
        assertThat(content).doesNotContain("transientFailures");
    }

    @Test
    @DisplayName("进度文件损坏时记录 error 并视为无进度")
    void corruptedProgressFileIsLoggedAndIgnored() throws Exception {
        PlaylistProgressStore store = new PlaylistProgressStore(tempDir.resolve("progress"));
        Files.createDirectories(store.getProgressDir());
        Files.writeString(store.progressFile(100L), "not-a-json");

        ListAppender<ILoggingEvent> appender = attachAppender();
        try {
            assertThat(store.readIndex(100L)).isZero();
            assertThat(appender.list).anyMatch(event -> event.getLevel() == Level.ERROR);
        } finally {
            detachAppender(appender);
        }
    }

    @Test
    @DisplayName("进度文件里的歌单与当前歌单不一致时视为无进度")
    void mismatchedPlaylistIdResetsProgress() throws Exception {
        PlaylistProgressStore store = new PlaylistProgressStore(tempDir.resolve("progress"));
        Files.createDirectories(store.getProgressDir());
        Files.writeString(store.progressFile(200L),
                "{\"playlistId\":999,\"index\":7,\"failedAlbums\":[]}");

        assertThat(store.readIndex(200L)).isZero();
    }

    @Test
    @DisplayName("父目录不存在时自动创建")
    void parentDirectoryIsCreatedAutomatically() {
        PlaylistProgressStore store = new PlaylistProgressStore(tempDir.resolve("nested/dir/progress"));

        store.saveIndex(100L, 1);

        assertThat(store.progressFile(100L)).exists();
    }

    @Test
    @DisplayName("作为 Spring Bean 可正常装配，默认落在运行目录的 data/playlist_progress 下")
    void registersAsSpringBean() {
        new ApplicationContextRunner()
                .withUserConfiguration(PlaylistProgressStore.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    PlaylistProgressStore store = context.getBean(PlaylistProgressStore.class);
                    assertThat(store.getProgressDir().toString().replace('\\', '/'))
                            .endsWith("/data/playlist_progress");
                });
    }

    @Test
    @DisplayName("同一专辑重复失败时 attempts 累加而不是重置")
    void repeatedFailureAccumulatesAttempts() {
        PlaylistProgressStore store = new PlaylistProgressStore(tempDir.resolve("progress"));

        store.markFailed(100L, 55L, PlaylistProgressStore.TYPE_INSERT, "数据库写入失败", 1);
        store.markFailed(100L, 55L, PlaylistProgressStore.TYPE_INSERT, "数据库写入失败", 1);
        store.markFailed(100L, 55L, PlaylistProgressStore.TYPE_INSERT, "数据库写入失败", 1);

        assertThat(store.read(100L).failedAlbums())
                .singleElement()
                .satisfies(entry -> assertThat(entry.attempts()).isEqualTo(3));
    }

    @Test
    @DisplayName("人工补导把失败计数归零，重新给一份预算")
    void resetAttemptsGivesFreshBudget() {
        PlaylistProgressStore store = new PlaylistProgressStore(tempDir.resolve("progress"));
        store.markFailed(100L, 55L, PlaylistProgressStore.TYPE_INSERT, "数据库写入失败", 1);
        store.markFailed(100L, 55L, PlaylistProgressStore.TYPE_INSERT, "数据库写入失败", 1);

        store.resetAttempts(100L, 55L);

        assertThat(store.read(100L).failedAlbums())
                .singleElement()
                .satisfies(entry -> assertThat(entry.attempts()).isZero());

        // 归零后再失败一次 → attempts 回到 1（不是接着原来累加）
        store.markFailed(100L, 55L, PlaylistProgressStore.TYPE_INSERT, "数据库写入失败", 1);
        assertThat(store.read(100L).failedAlbums())
                .singleElement()
                .satisfies(entry -> assertThat(entry.attempts()).isEqualTo(1));
    }

    @Test
    @DisplayName("removeFailedAlbum 只销账，续传位置 index 原样不动")
    void removeFailedAlbumKeepsIndex() {
        PlaylistProgressStore store = new PlaylistProgressStore(tempDir.resolve("progress"));
        store.markFailed(100L, 55L, PlaylistProgressStore.TYPE_INSERT, "数据库写入失败", 7);

        store.removeFailedAlbum(100L, 55L);

        assertThat(store.read(100L).failedAlbums()).isEmpty();
        assertThat(store.readIndex(100L)).isEqualTo(7);
    }

    @Test
    @DisplayName("markFailedKeepingIndex 记账但不改动续传位置")
    void markFailedKeepingIndexDoesNotMoveIndex() {
        PlaylistProgressStore store = new PlaylistProgressStore(tempDir.resolve("progress"));
        store.saveIndex(100L, 5);

        store.markFailedKeepingIndex(100L, 55L, PlaylistProgressStore.TYPE_INSERT, "数据库写入失败");

        assertThat(store.readIndex(100L)).isEqualTo(5);
        assertThat(store.failedAlbumIds(100L)).containsExactly(55L);
    }

    @Test
    @DisplayName("按失败类型筛选清单条目")
    void failedAlbumsByTypeFiltersEntries() {
        PlaylistProgressStore store = new PlaylistProgressStore(tempDir.resolve("progress"));
        store.markFailed(100L, 11L, PlaylistProgressStore.TYPE_PERMANENT, "上游返回 code=404", 1);
        store.markFailed(100L, 22L, PlaylistProgressStore.TYPE_INSERT, "数据库写入失败", 1);
        store.markFailed(100L, 33L, PlaylistProgressStore.TYPE_TRANSIENT, "上游请求超时", 1);

        assertThat(store.failedAlbumsByType(100L, PlaylistProgressStore.TYPE_INSERT))
                .extracting(PlaylistProgressStore.FailedAlbum::albumId)
                .containsExactly(22L);
        assertThat(store.failedAlbumsByType(100L, null)).hasSize(3);
    }

    @Test
    @DisplayName("TTL 清理只删 permanent，insert / transient 是待办不受影响")
    void ttlOnlyExpiresPermanentEntries() {
        Instant fortyDaysAgo = Instant.now().minus(40, ChronoUnit.DAYS);
        PlaylistProgressStore oldClockStore = new PlaylistProgressStore(tempDir.resolve("progress"),
                Duration.ofDays(30), () -> fortyDaysAgo);
        oldClockStore.markFailed(100L, 11L, PlaylistProgressStore.TYPE_PERMANENT, "上游返回 code=404", 1);
        oldClockStore.markFailed(100L, 22L, PlaylistProgressStore.TYPE_INSERT, "数据库写入失败", 1);
        oldClockStore.markFailed(100L, 33L, PlaylistProgressStore.TYPE_TRANSIENT, "上游请求超时", 1);

        PlaylistProgressStore store = new PlaylistProgressStore(tempDir.resolve("progress"));

        assertThat(store.read(100L).failedAlbums())
                .extracting(PlaylistProgressStore.FailedAlbum::albumId)
                .containsExactly(22L, 33L);
    }

    @Test
    @DisplayName("老进度文件没有 attempts 字段时兜底为 0")
    void legacyProgressFileWithoutAttemptsDefaultsToZero() throws Exception {
        PlaylistProgressStore store = new PlaylistProgressStore(tempDir.resolve("progress"));
        Files.createDirectories(store.getProgressDir());
        Files.writeString(store.progressFile(100L),
                "{\"playlistId\":100,\"index\":2,\"failedAlbums\":[{\"albumId\":55,\"type\":\"insert\","
                        + "\"reason\":\"数据库写入失败\",\"at\":\"2026-11-14T00:00:00Z\"}]}");

        assertThat(store.read(100L).failedAlbums())
                .singleElement()
                .satisfies(entry -> assertThat(entry.attempts()).isZero());
    }

    @Test
    @DisplayName("listPlaylistIds 列出进度目录里的歌单")
    void listPlaylistIdsReturnsPlaylists() {
        PlaylistProgressStore store = new PlaylistProgressStore(tempDir.resolve("progress"));
        store.saveIndex(100L, 1);
        store.saveIndex(200L, 2);

        assertThat(store.listPlaylistIds()).containsExactlyInAnyOrder(100L, 200L);
    }

    private static ListAppender<ILoggingEvent> attachAppender() {
        Logger logger = (Logger) LoggerFactory.getLogger(PlaylistProgressStore.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private static void detachAppender(ListAppender<ILoggingEvent> appender) {
        Logger logger = (Logger) LoggerFactory.getLogger(PlaylistProgressStore.class);
        logger.detachAppender(appender);
        appender.stop();
    }
}
