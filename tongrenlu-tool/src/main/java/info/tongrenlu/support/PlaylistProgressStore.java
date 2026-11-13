package info.tongrenlu.support;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;

/**
 * 歌单导入的断点续传进度存储。
 *
 * <p>背景：旧实现把进度文件写死在
 * {@code E:\project\tongrenlu\tongrenlu-tool\src\main\resources\data\playlist_progress.txt}，
 * 仓库迁移到 new_tongrenlu 后该目录不存在，写入异常被 catch 吞掉 ⇒ 断点续传从未生效；
 * 且进度只存一个序号，换歌单会继承上一个歌单的序号，静默漏导前面专辑。
 *
 * <p>本类解决三件事：
 * <ol>
 *   <li>路径不依赖绝对路径：默认用运行目录下的相对路径 {@code data/playlist_progress.json}，
 *       可用 Spring 配置项 {@code app.playlist-import.progress-file}、系统属性
 *       {@code tongrenlu.playlist.progress-file} 或环境变量 {@code TONGRLU_PLAYLIST_PROGRESS_FILE}
 *       覆盖（优先级依次降低），父目录自动创建；</li>
 *   <li>进度内容改为 JSON，同时记录 playlistId 与 index，读取时 playlistId 不匹配即视为无进度；</li>
 *   <li>写入/读取/清除失败一律 log.error，不再静默吞掉。</li>
 * </ol>
 */
@Slf4j
@Component
public class PlaylistProgressStore {

    /** 系统属性：覆盖进度文件位置 */
    public static final String PROGRESS_FILE_PROPERTY = "tongrenlu.playlist.progress-file";

    /** 环境变量：覆盖进度文件位置 */
    public static final String PROGRESS_FILE_ENV = "TONGRLU_PLAYLIST_PROGRESS_FILE";

    /** Spring 配置项：覆盖进度文件位置（优先级最高） */
    public static final String PROGRESS_FILE_CONFIG = "app.playlist-import.progress-file";

    /** 默认相对路径（相对运行目录） */
    public static final String DEFAULT_RELATIVE_PATH = "data/playlist_progress.json";

    private final Path progressFile;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public PlaylistProgressStore(@Value("${app.playlist-import.progress-file:}") String configuredPath) {
        this(resolveConfiguredPath(configuredPath));
    }

    public PlaylistProgressStore(Path progressFile) {
        this.progressFile = progressFile.toAbsolutePath().normalize();
    }

    public Path getProgressFile() {
        return progressFile;
    }

    /**
     * 读取指定歌单的续传位置。
     *
     * @param playlistId 当前导入的歌单ID
     * @return 已完成的专辑数量；没有进度、进度损坏或进度属于其它歌单时返回 0（从头开始）
     */
    public int readIndex(Long playlistId) {
        if (!Files.exists(progressFile)) {
            return 0;
        }
        try {
            String content = Files.readString(progressFile, StandardCharsets.UTF_8);
            ImportProgress progress = objectMapper.readValue(content, ImportProgress.class);
            if (progress == null || progress.playlistId() == null) {
                log.error("进度文件内容不完整，视为无进度: {}", progressFile);
                return 0;
            }
            if (!progress.playlistId().equals(playlistId)) {
                log.warn("进度文件属于歌单 {}，与当前歌单 {} 不匹配，视为无进度并从头开始: {}",
                        progress.playlistId(), playlistId, progressFile);
                return 0;
            }
            int index = Math.max(0, progress.index());
            log.info("读取歌单 {} 的续传位置：第 {} 个专辑之后, file={}", playlistId, index, progressFile);
            return index;
        } catch (IOException | RuntimeException e) {
            log.error("读取进度文件失败，视为无进度: file={}, error={}", progressFile, e.getMessage(), e);
            return 0;
        }
    }

    /**
     * 保存进度。写入失败只记录 error 日志，不中断导入流程。
     *
     * @param playlistId 当前导入的歌单ID
     * @param index      已完成的专辑数量（1 起始）
     */
    public void saveIndex(Long playlistId, int index) {
        try {
            Path parent = progressFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            ImportProgress progress = new ImportProgress(playlistId, index, Instant.now().toString());
            String json = objectMapper.writeValueAsString(progress);

            // 先写临时文件再原子替换，避免进程被杀时留下半截 JSON
            Path temp = progressFile.resolveSibling(progressFile.getFileName() + ".tmp");
            Files.writeString(temp, json, StandardCharsets.UTF_8);
            try {
                Files.move(temp, progressFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, progressFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException e) {
            log.error("保存进度文件失败: file={}, playlistId={}, index={}, error={}",
                    progressFile, playlistId, index, e.getMessage(), e);
        }
    }

    /**
     * 清除进度。只清除属于该歌单的进度文件，避免误删其它歌单的续传点。
     *
     * @param playlistId 当前导入的歌单ID
     */
    public void clear(Long playlistId) {
        try {
            if (!Files.exists(progressFile)) {
                return;
            }
            ImportProgress progress = readProgressOrNull();
            if (progress != null && progress.playlistId() != null && !progress.playlistId().equals(playlistId)) {
                log.info("进度文件属于歌单 {}，与当前歌单 {} 不同，保留不动: {}",
                        progress.playlistId(), playlistId, progressFile);
                return;
            }
            Files.deleteIfExists(progressFile);
            log.info("进度文件已清除: {}", progressFile);
        } catch (IOException | RuntimeException e) {
            log.error("清除进度文件失败: file={}, error={}", progressFile, e.getMessage(), e);
        }
    }

    private ImportProgress readProgressOrNull() {
        try {
            String content = Files.readString(progressFile, StandardCharsets.UTF_8);
            return objectMapper.readValue(content, ImportProgress.class);
        } catch (IOException | RuntimeException e) {
            log.error("读取进度文件失败: file={}, error={}", progressFile, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 按「Spring 配置项 → 系统属性 → 环境变量 → 默认相对路径」的顺序解析进度文件位置。
     */
    public static Path resolveConfiguredPath() {
        return resolveConfiguredPath(null);
    }

    /**
     * 按「Spring 配置项 → 系统属性 → 环境变量 → 默认相对路径」的顺序解析进度文件位置。
     *
     * @param springConfiguredPath Spring 配置项 app.playlist-import.progress-file 的值，可为空
     */
    public static Path resolveConfiguredPath(String springConfiguredPath) {
        String configured = springConfiguredPath;
        if (isBlank(configured)) {
            configured = System.getProperty(PROGRESS_FILE_PROPERTY);
        }
        if (isBlank(configured)) {
            configured = System.getenv(PROGRESS_FILE_ENV);
        }
        return resolvePath(configured, System.getProperty("user.dir"));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * 解析进度文件位置：绝对路径直接使用，相对路径按 baseDir 展开，都为空时用默认相对路径。
     */
    public static Path resolvePath(String configured, String baseDir) {
        Path base = Path.of(baseDir == null || baseDir.isBlank() ? "." : baseDir);
        if (configured != null && !configured.isBlank()) {
            Path path = Path.of(configured.trim());
            // Windows 下 "/var/lib/x" 没有盘符但有根，仍按绝对路径处理，避免被拼到基准目录后面
            boolean absolute = path.isAbsolute() || path.getRoot() != null;
            return (absolute ? path : base.resolve(path)).toAbsolutePath().normalize();
        }
        return base.resolve(DEFAULT_RELATIVE_PATH).toAbsolutePath().normalize();
    }

    /**
     * 进度文件内容：歌单ID + 已完成数量 + 更新时间。
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ImportProgress(Long playlistId, int index, String updatedAt) {
    }
}
