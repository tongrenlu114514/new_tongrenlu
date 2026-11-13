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
import java.util.ArrayList;
import java.util.List;

/**
 * 歌单导入的断点续传进度存储（按歌单分文件）。
 *
 * <p>背景：旧实现把进度写死 {@code E:\project\tongrenlu\tongrenlu-tool\src\main\resources\data\playlist_progress.txt}，
 * 仓库迁移后该目录不存在、写异常被吞 ⇒ 断点续传从未生效；且进度只存一个序号，换歌单会继承上一个歌单的序号，
 * 静默漏导前面专辑。
 *
 * <p>本类的五点约定：
 * <ol>
 *   <li><b>路径不依赖绝对路径</b>：默认运行目录下的相对目录 {@code data/playlist_progress}，
 *       可用 Spring 配置项 {@code app.playlist-import.progress-dir}、系统属性
 *       {@code tongrenlu.playlist.progress-dir} 或环境变量 {@code TONGRLU_PLAYLIST_PROGRESS_DIR} 覆盖（优先级依次降低）；</li>
 *   <li><b>按歌单分文件</b>：每个歌单一个 {@code playlist_<playlistId>.json}，
 *       并发导入不同歌单不再互相覆盖；</li>
 *   <li><b>进度内容含失败清单</b>：{@code {playlistId, index, failedAlbums[], updatedAt}}，
 *       清单条目带类型与原因（{@code {albumId, type, reason, at}}），
 *       失败（永久 / 临时 / 入库）都记进清单并随进度一起落盘 —— 临时失败同样要记账，
 *       否则「这轮没导完、下轮从哪续」就无从判断；</li>
 *   <li><b>成功即出清单</b>：专辑导入成功（或命中「已存在」）后从失败清单移除，
 *       清单清空则进度文件可以安全清除，形成「失败 → 补导 → 销账」的闭环；</li>
 *   <li><b>不静默吞异常</b>：读/写/清除失败一律 log.error。</li>
 * </ol>
 */
@Slf4j
@Component
public class PlaylistProgressStore {

    /** Spring 配置项：进度目录 */
    public static final String PROGRESS_DIR_CONFIG = "app.playlist-import.progress-dir";

    /** 系统属性：进度目录 */
    public static final String PROGRESS_DIR_PROPERTY = "tongrenlu.playlist.progress-dir";

    /** 环境变量：进度目录 */
    public static final String PROGRESS_DIR_ENV = "TONGRLU_PLAYLIST_PROGRESS_DIR";

    /** 默认相对目录（相对运行目录） */
    public static final String DEFAULT_RELATIVE_DIR = "data/playlist_progress";

    /** 失败类型：取专辑永久失败（上游 code!=200） */
    public static final String TYPE_PERMANENT = "permanent";

    /** 失败类型：取专辑临时失败（超时 / IO / 解析失败） */
    public static final String TYPE_TRANSIENT = "transient";

    /** 失败类型：非取专辑异常（入库等） */
    public static final String TYPE_INSERT = "insert";

    private static final String FILE_PREFIX = "playlist_";
    private static final String FILE_SUFFIX = ".json";

    private final Path progressDir;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public PlaylistProgressStore(@Value("${app.playlist-import.progress-dir:}") String configuredDir) {
        this(resolveConfiguredDir(configuredDir));
    }

    public PlaylistProgressStore(Path progressDir) {
        this.progressDir = progressDir.toAbsolutePath().normalize();
    }

    public Path getProgressDir() {
        return progressDir;
    }

    /** 某歌单的进度文件路径 */
    public Path progressFile(Long playlistId) {
        return progressDir.resolve(FILE_PREFIX + playlistId + FILE_SUFFIX);
    }

    /**
     * 读取某歌单的进度状态。文件不存在 / 损坏 / 与歌单不匹配时返回空状态（从头开始）。
     */
    public PlaylistProgress read(Long playlistId) {
        Path file = progressFile(playlistId);
        if (!Files.exists(file)) {
            return PlaylistProgress.empty(playlistId);
        }
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            PlaylistProgress progress = objectMapper.readValue(content, PlaylistProgress.class);
            if (progress == null || progress.playlistId() == null) {
                log.error("进度文件内容不完整，视为无进度: {}", file);
                return PlaylistProgress.empty(playlistId);
            }
            if (!progress.playlistId().equals(playlistId)) {
                log.warn("进度文件属于歌单 {}，与当前歌单 {} 不匹配，视为无进度并从头开始: {}",
                        progress.playlistId(), playlistId, file);
                return PlaylistProgress.empty(playlistId);
            }
            return progress;
        } catch (IOException | RuntimeException e) {
            log.error("读取进度文件失败，视为无进度: file={}, error={}", file, e.getMessage(), e);
            return PlaylistProgress.empty(playlistId);
        }
    }

    /** 读取续传位置（已完成数量） */
    public int readIndex(Long playlistId) {
        return read(playlistId).index();
    }

    /**
     * 保存进度（保留已有的失败清单）。
     */
    public void saveIndex(Long playlistId, int index) {
        PlaylistProgress current = read(playlistId);
        write(playlistId, new PlaylistProgress(playlistId, index, current.failedAlbums(), now()));
    }

    /**
     * 记录一次成功（或命中「已存在」）：把该专辑从失败清单移除，并保存进度。
     *
     * <p>这是失败清单的销账入口 —— 清单里的专辑一旦导入成功就不再占用失败名额，
     * 清单清空后进度文件可以被安全清除，下次导入从头再来也不会漏。
     */
    public void markSuccess(Long playlistId, Long albumId, int index) {
        PlaylistProgress current = read(playlistId);
        List<FailedAlbum> failedAlbums = new ArrayList<>(current.failedAlbums());
        boolean removed = failedAlbums.removeIf(entry -> entry.albumId() != null && entry.albumId().equals(albumId));
        if (removed) {
            log.info("专辑 {} 导入成功，已从歌单 {} 的失败清单中移除", albumId, playlistId);
        }
        write(playlistId, new PlaylistProgress(playlistId, index, failedAlbums, now()));
    }

    /**
     * 记录一次失败：清单条目带类型与原因，随进度一起落盘。
     *
     * <p>同一个专辑重复失败时覆盖旧条目（保留最新的类型、原因与时间），清单不会重复膨胀。
     *
     * @param type   失败类型：{@link #TYPE_PERMANENT} / {@link #TYPE_TRANSIENT} / {@link #TYPE_INSERT}
     * @param reason 失败原因（人类可读）
     * @param index  当前进度水位（临时失败时传未推进的水位，保证不越过失败专辑）
     */
    public void markFailed(Long playlistId, Long albumId, String type, String reason, int index) {
        PlaylistProgress current = read(playlistId);
        List<FailedAlbum> failedAlbums = new ArrayList<>(current.failedAlbums());
        failedAlbums.removeIf(entry -> entry.albumId() != null && entry.albumId().equals(albumId));
        failedAlbums.add(new FailedAlbum(albumId, type, reason, now()));
        write(playlistId, new PlaylistProgress(playlistId, index, failedAlbums, now()));
    }

    /** 失败清单里的专辑 ID（按落盘顺序） */
    public List<Long> failedAlbumIds(Long playlistId) {
        return read(playlistId).failedAlbums().stream()
                .map(FailedAlbum::albumId)
                .toList();
    }

    /**
     * 清除进度文件。失败清单非空时保留文件（清单必须随进度留存，否则没导成的专辑再也补不回来）。
     *
     * <p>清单为空 = 所有失败专辑都已补导成功，此时进度文件可以安全删除。
     *
     * @return true 表示已清除，false 表示保留
     */
    public boolean clear(Long playlistId) {
        PlaylistProgress current = read(playlistId);
        if (!current.failedAlbums().isEmpty()) {
            log.info("歌单 {} 仍有 {} 个失败专辑（含临时失败），保留进度文件以便后续补导: {}",
                    playlistId, current.failedAlbums().size(), progressFile(playlistId));
            return false;
        }
        try {
            Files.deleteIfExists(progressFile(playlistId));
            log.info("进度文件已清除: {}", progressFile(playlistId));
            return true;
        } catch (IOException | RuntimeException e) {
            log.error("清除进度文件失败: file={}, error={}", progressFile(playlistId), e.getMessage(), e);
            return false;
        }
    }

    private void write(Long playlistId, PlaylistProgress progress) {
        Path file = progressFile(playlistId);
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            String json = objectMapper.writeValueAsString(progress);
            // 先写临时文件再原子替换，避免进程被杀时留下半截 JSON
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temp, json, StandardCharsets.UTF_8);
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException e) {
            log.error("保存进度文件失败: file={}, playlistId={}, error={}", file, playlistId, e.getMessage(), e);
        }
    }

    /**
     * 按「Spring 配置项 → 系统属性 → 环境变量 → 默认相对目录」的顺序解析进度目录。
     */
    public static Path resolveConfiguredDir() {
        return resolveConfiguredDir(null);
    }

    /**
     * 按「Spring 配置项 → 系统属性 → 环境变量 → 默认相对目录」的顺序解析进度目录。
     */
    public static Path resolveConfiguredDir(String springConfiguredDir) {
        String configured = springConfiguredDir;
        if (isBlank(configured)) {
            configured = System.getProperty(PROGRESS_DIR_PROPERTY);
        }
        if (isBlank(configured)) {
            configured = System.getenv(PROGRESS_DIR_ENV);
        }
        return resolveDir(configured, System.getProperty("user.dir"));
    }

    /**
     * 解析进度目录：绝对路径直接使用，相对路径按 baseDir 展开，都为空时用默认相对目录。
     */
    public static Path resolveDir(String configured, String baseDir) {
        Path base = Path.of(baseDir == null || baseDir.isBlank() ? "." : baseDir);
        if (configured != null && !configured.isBlank()) {
            Path path = Path.of(configured.trim());
            // Windows 下 "/var/lib/x" 没有盘符但有根，仍按绝对路径处理，避免被拼到基准目录后面
            boolean absolute = path.isAbsolute() || path.getRoot() != null;
            return (absolute ? path : base.resolve(path)).toAbsolutePath().normalize();
        }
        return base.resolve(DEFAULT_RELATIVE_DIR).toAbsolutePath().normalize();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String now() {
        return Instant.now().toString();
    }

    /**
     * 失败清单条目：专辑 ID + 失败类型 + 失败原因 + 记录时间。
     *
     * @param albumId 专辑 ID
     * @param type    失败类型：{@code permanent} / {@code transient} / {@code insert}
     * @param reason  失败原因（人类可读）
     * @param at      记录时间（ISO-8601）
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FailedAlbum(Long albumId, String type, String reason, String at) {
    }

    /**
     * 进度文件内容：续传位置 + 失败清单 + 更新时间。
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PlaylistProgress(Long playlistId, int index, List<FailedAlbum> failedAlbums, String updatedAt) {

        public PlaylistProgress {
            failedAlbums = failedAlbums == null ? List.of() : List.copyOf(failedAlbums);
        }

        public static PlaylistProgress empty(Long playlistId) {
            return new PlaylistProgress(playlistId, 0, List.of(), null);
        }
    }
}
