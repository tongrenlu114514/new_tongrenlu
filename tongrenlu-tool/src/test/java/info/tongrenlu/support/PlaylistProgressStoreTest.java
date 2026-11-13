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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 断点续传进度存储的单元测试：路径可配置、歌单不匹配时重置、正常续传、失败不静默。
 */
class PlaylistProgressStoreTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("默认路径是运行目录下的 data/playlist_progress.json（相对路径，不依赖固定盘符）")
    void defaultPathIsRelativeToWorkingDirectory() {
        Path resolved = PlaylistProgressStore.resolvePath(null, "/opt/tongrenlu");

        assertThat(resolved.toString().replace('\\', '/'))
                .endsWith("/opt/tongrenlu/data/playlist_progress.json");
        assertThat(resolved.getFileName().toString()).isEqualTo("playlist_progress.json");
        assertThat(resolved.getParent().getFileName().toString()).isEqualTo("data");
        assertThat(PlaylistProgressStore.DEFAULT_RELATIVE_PATH).isEqualTo("data/playlist_progress.json");
    }

    @Test
    @DisplayName("相对配置按基准目录展开，绝对配置原样使用")
    void configuredPathIsResolvedAgainstBaseDirectory() {
        assertThat(PlaylistProgressStore.resolvePath("custom/progress.json", "/opt/app").toString().replace('\\', '/'))
                .endsWith("/opt/app/custom/progress.json");
        assertThat(PlaylistProgressStore.resolvePath("/var/lib/app/progress.json", "/opt/app").toString().replace('\\', '/'))
                .endsWith("/var/lib/app/progress.json");
    }

    @Test
    @DisplayName("进度文件位置可用系统属性覆盖")
    void pathIsConfigurableBySystemProperty() {
        Path custom = tempDir.resolve("custom-progress.json").toAbsolutePath().normalize();
        String previous = System.getProperty(PlaylistProgressStore.PROGRESS_FILE_PROPERTY);
        System.setProperty(PlaylistProgressStore.PROGRESS_FILE_PROPERTY, custom.toString());
        try {
            assertThat(PlaylistProgressStore.resolveConfiguredPath()).isEqualTo(custom);
        } finally {
            if (previous == null) {
                System.clearProperty(PlaylistProgressStore.PROGRESS_FILE_PROPERTY);
            } else {
                System.setProperty(PlaylistProgressStore.PROGRESS_FILE_PROPERTY, previous);
            }
        }
    }

    @Test
    @DisplayName("Spring 配置项 app.playlist-import.progress-file 优先级高于系统属性")
    void springConfiguredPathTakesPrecedence() {
        Path custom = tempDir.resolve("spring-progress.json").toAbsolutePath().normalize();
        String previous = System.getProperty(PlaylistProgressStore.PROGRESS_FILE_PROPERTY);
        System.setProperty(PlaylistProgressStore.PROGRESS_FILE_PROPERTY,
                tempDir.resolve("sys-progress.json").toString());
        try {
            assertThat(PlaylistProgressStore.resolveConfiguredPath(custom.toString())).isEqualTo(custom);
        } finally {
            if (previous == null) {
                System.clearProperty(PlaylistProgressStore.PROGRESS_FILE_PROPERTY);
            } else {
                System.setProperty(PlaylistProgressStore.PROGRESS_FILE_PROPERTY, previous);
            }
        }
    }

    @Test
    @DisplayName("保存后可按同一歌单续传，进度文件内容是含 playlistId 的 JSON")
    void resumeFromSavedIndex() throws Exception {
        PlaylistProgressStore store = new PlaylistProgressStore(tempDir.resolve("progress.json"));

        store.saveIndex(100L, 7);

        assertThat(store.readIndex(100L)).isEqualTo(7);
        String content = Files.readString(store.getProgressFile(), StandardCharsets.UTF_8);
        assertThat(content).contains("\"playlistId\":100").contains("\"index\":7");
    }

    @Test
    @DisplayName("进度文件属于其它歌单时视为无进度，从头开始，且不破坏原歌单的续传点")
    void mismatchedPlaylistIdResetsProgress() {
        PlaylistProgressStore store = new PlaylistProgressStore(tempDir.resolve("progress.json"));
        store.saveIndex(100L, 7);

        assertThat(store.readIndex(200L)).isZero();
        assertThat(store.readIndex(100L)).isEqualTo(7);
    }

    @Test
    @DisplayName("进度文件损坏时记录 error 并视为无进度")
    void corruptedProgressFileIsLoggedAndIgnored() throws Exception {
        PlaylistProgressStore store = new PlaylistProgressStore(tempDir.resolve("progress.json"));
        Files.writeString(store.getProgressFile(), "not-a-json");

        ListAppender<ILoggingEvent> appender = attachAppender();
        try {
            assertThat(store.readIndex(100L)).isZero();
            assertThat(appender.list).anyMatch(event -> event.getLevel() == Level.ERROR);
        } finally {
            detachAppender(appender);
        }
    }

    @Test
    @DisplayName("写入失败时记录 error 且不抛异常，不中断导入")
    void saveFailureIsLoggedAsError() throws Exception {
        // 父路径是一个普通文件 → 建目录必然失败
        Path blocker = tempDir.resolve("blocker");
        Files.writeString(blocker, "x");
        PlaylistProgressStore store = new PlaylistProgressStore(blocker.resolve("sub/progress.json"));

        ListAppender<ILoggingEvent> appender = attachAppender();
        try {
            store.saveIndex(100L, 1);

            assertThat(appender.list).anyMatch(event -> event.getLevel() == Level.ERROR);
        } finally {
            detachAppender(appender);
        }
    }

    @Test
    @DisplayName("清除进度只删本歌单的进度文件")
    void clearOnlyRemovesMatchingPlaylist() {
        PlaylistProgressStore store = new PlaylistProgressStore(tempDir.resolve("progress.json"));
        store.saveIndex(100L, 3);

        store.clear(200L);
        assertThat(store.getProgressFile()).exists();

        store.clear(100L);
        assertThat(store.getProgressFile()).doesNotExist();
    }

    @Test
    @DisplayName("父目录不存在时自动创建")
    void parentDirectoryIsCreatedAutomatically() {
        PlaylistProgressStore store = new PlaylistProgressStore(tempDir.resolve("nested/dir/progress.json"));

        store.saveIndex(100L, 1);

        assertThat(store.getProgressFile()).exists();
    }

    @Test
    @DisplayName("作为 Spring Bean 可正常装配，默认路径落在运行目录下的 data/ 下")
    void registersAsSpringBean() {
        new ApplicationContextRunner()
                .withUserConfiguration(PlaylistProgressStore.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    PlaylistProgressStore store = context.getBean(PlaylistProgressStore.class);
                    assertThat(store.getProgressFile().toString().replace('\\', '/'))
                            .endsWith("/data/playlist_progress.json");
                });
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
