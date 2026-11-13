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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * 歌单导入的断点续传进度存储（按歌单分文件）。
 *
 * <p>背景：旧实现把进度写死 {@code E:\project\tongrenlu\tongrenlu-tool\src\main\resources\data\playlist_progress.txt}，
 * 仓库迁移后该目录不存在、写异常被吞 ⇒ 断点续传从未生效；且进度只存一个序号，换歌单会继承上一个歌单的序号，
 * 静默漏导前面专辑。
 *
 * <p>本类的约定：
 * <ol>
 *   <li><b>路径不依赖绝对路径</b>：默认运行目录下的相对目录 {@code data/playlist_progress}，
 *       可用 Spring 配置项 {@code app.playlist-import.progress-dir}、系统属性
 *       {@code tongrenlu.playlist.progress-dir} 或环境变量 {@code TONGRLU_PLAYLIST_PROGRESS_DIR} 覆盖（优先级依次降低）；</li>
 *   <li><b>按歌单分文件</b>：每个歌单一个 {@code playlist_<playlistId>.json}，
 *       并发导入不同歌单不再互相覆盖；</li>
 *   <li><b>进度内容含失败清单</b>：{@code {playlistId, index, failedAlbums[], updatedAt}}，
 *       清单条目带类型与原因（{@code {albumId, type, reason, at, attempts}}），
 *       失败（永久 / 临时 / 入库）都记进清单并随进度一起落盘；</li>
 *   <li><b>成功即出清单</b>：专辑导入成功（或命中「已存在」）后从失败清单移除，
 *       清单清空则进度文件可以安全清除，形成「失败 → 补导 → 销账」的闭环；</li>
 *   <li><b>补导只销账、不动水位</b>：{@link #removeFailedAlbum} 与 {@link #markFailedKeepingIndex}
 *       都不改写 {@code index} —— 被补导的专辑水位早已越过，改 index 会破坏续传位置；</li>
 *   <li><b>permanent 条目惰性过期</b>：{@link #TYPE_PERMANENT} 条目超过 TTL 天数自动移除
 *       （上游明确没有这个专辑，留着也没用）；{@code insert} / {@code transient} 是待办，
 *       <b>不受 TTL 影响</b>，过期删掉等于忘了；</li>
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

    /** 默认：入库类失败的自动重试预算 N */
    public static final int DEFAULT_RETRY_BUDGET = 3;

    /** 默认：permanent 失败条目的保留天数 */
    public static final int DEFAULT_PERMANENT_TTL_DAYS = 30;

    private static final String FILE_PREFIX = "playlist_";
    private static final String FILE_SUFFIX = ".json";

    private final Path progressDir;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Duration permanentTtl;
    private final Supplier<Instant> clock;

    @Autowired
    public PlaylistProgressStore(@Value("${app.playlist-import.progress-dir:}") String configuredDir,
                                 @Value("${app.playlist-import.permanent-ttl-days:30}") long permanentTtlDays) {
        this(resolveConfiguredDir(configuredDir), Duration.ofDays(permanentTtlDays), Instant::now);
    }

    public PlaylistProgressStore(Path progressDir) {
        this(progressDir, Duration.ofDays(DEFAULT_PERMANENT_TTL_DAYS), Instant::now);
    }

    /**
     * 测试与特殊场景用构造器：可指定 permanent 条目的 TTL 与时钟。
     */
    public PlaylistProgressStore(Path progressDir, Duration permanentTtl, Supplier<Instant> clock) {
        this.progressDir = progressDir.toAbsolutePath().normalize();
        this.permanentTtl = permanentTtl == null ? Duration.ofDays(DEFAULT_PERMANENT_TTL_DAYS) : permanentTtl;
        this.clock = clock == null ? Instant::now : clock;
    }

    public Path getProgressDir() {
        return progressDir;
    }

    /** permanent 条目的保留时长 */
    public Duration getPermanentTtl() {
        return permanentTtl;
    }

    /** 某歌单的进度文件路径 */
    public Path progressFile(Long playlistId) {
        return progressDir.resolve(FILE_PREFIX + playlistId + FILE_SUFFIX);
    }

    /**
     * 读取某歌单的进度状态。文件不存在 / 损坏 / 与歌单不匹配时返回空状态（从头开始）。
     *
     * <p>读取时会惰性剔除已过期的 permanent 条目（超期即视为上游长期没有该专辑，留着也没用）；
     * 下一次写入会把剔除结果落盘。insert / transient 条目是待办，不受 TTL 影响。
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
            return dropExpiredPermanent(progress);
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
     * 只从失败清单移除该专辑，<b>不改动 index</b>。
     *
     * <p>补导专用：被补导的专辑水位早已越过，补导绝不能改写续传位置。
     */
    public void removeFailedAlbum(Long playlistId, Long albumId) {
        PlaylistProgress current = read(playlistId);
        List<FailedAlbum> failedAlbums = new ArrayList<>(current.failedAlbums());
        boolean removed = failedAlbums.removeIf(entry -> entry.albumId() != null && entry.albumId().equals(albumId));
        if (!removed) {
            return;
        }
        log.info("专辑 {} 已补导成功，已从歌单 {} 的失败清单中移除（续传位置保持不变）", albumId, playlistId);
        write(playlistId, new PlaylistProgress(playlistId, current.index(), failedAlbums, now()));
    }

    /**
     * 记录一次失败：清单条目带类型与原因，随进度一起落盘。
     *
     * <p>同一个专辑重复失败时覆盖旧条目，但 {@code attempts} <b>累加</b>而不是重置
     * （人工手动补导会先调 {@link #resetAttempts} 归零，再给一份完整预算）。
     *
     * @param type   失败类型：{@link #TYPE_PERMANENT} / {@link #TYPE_TRANSIENT} / {@link #TYPE_INSERT}
     * @param reason 失败原因（人类可读）
     * @param index  当前进度水位（临时失败时传未推进的水位，保证不越过失败专辑）
     */
    public void markFailed(Long playlistId, Long albumId, String type, String reason, int index) {
        PlaylistProgress current = read(playlistId);
        write(playlistId, new PlaylistProgress(playlistId, index,
                upsertFailed(current.failedAlbums(), albumId, type, reason), now()));
    }

    /**
     * 记录一次失败，<b>不改动 index</b>（补导专用）。
     */
    public void markFailedKeepingIndex(Long playlistId, Long albumId, String type, String reason) {
        PlaylistProgress current = read(playlistId);
        write(playlistId, new PlaylistProgress(playlistId, current.index(),
                upsertFailed(current.failedAlbums(), albumId, type, reason), now()));
    }

    /**
     * 把某条失败记录的 attempts 归零（人工手动补导时调用：人明确要求了，再给一份预算）。
     */
    public void resetAttempts(Long playlistId, Long albumId) {
        PlaylistProgress current = read(playlistId);
        List<FailedAlbum> failedAlbums = new ArrayList<>(current.failedAlbums());
        boolean changed = false;
        for (int i = 0; i < failedAlbums.size(); i++) {
            FailedAlbum entry = failedAlbums.get(i);
            if (entry.albumId() != null && entry.albumId().equals(albumId) && entry.attempts() != 0) {
                failedAlbums.set(i, new FailedAlbum(entry.albumId(), entry.type(), entry.reason(), entry.at(), 0));
                changed = true;
            }
        }
        if (changed) {
            log.info("人工补导：歌单 {} 的专辑 {} 失败计数已归零，重新给一份预算", playlistId, albumId);
            write(playlistId, new PlaylistProgress(playlistId, current.index(), failedAlbums, now()));
        }
    }

    /** 失败清单里的专辑 ID（按落盘顺序） */
    public List<Long> failedAlbumIds(Long playlistId) {
        return read(playlistId).failedAlbums().stream()
                .map(FailedAlbum::albumId)
                .toList();
    }

    /**
     * 按失败类型筛选清单条目。type 为 null 时返回全部。
     */
    public List<FailedAlbum> failedAlbumsByType(Long playlistId, String type) {
        return read(playlistId).failedAlbums().stream()
                .filter(entry -> type == null || type.equals(entry.type()))
                .toList();
    }

    /**
     * 列出进度目录里已有的歌单 ID（供定时扫描遍历用）。
     */
    public List<Long> listPlaylistIds() {
        if (!Files.isDirectory(progressDir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(progressDir)) {
            return files.map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith(FILE_PREFIX) && name.endsWith(FILE_SUFFIX))
                    .map(name -> name.substring(FILE_PREFIX.length(), name.length() - FILE_SUFFIX.length()))
                    .map(this::parsePlaylistId)
                    .filter(Objects::nonNull)
                    .toList();
        } catch (IOException | RuntimeException e) {
            log.error("列出进度目录失败: dir={}, error={}", progressDir, e.getMessage(), e);
            return List.of();
        }
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

    /**
     * 写入前把同一条目的 attempts 累加（重复失败只保留最新条目，清单不膨胀）。
     */
    private List<FailedAlbum> upsertFailed(List<FailedAlbum> current, Long albumId, String type, String reason) {
        List<FailedAlbum> failedAlbums = new ArrayList<>(current);
        int attempts = 1;
        for (FailedAlbum entry : current) {
            if (entry.albumId() != null && entry.albumId().equals(albumId)) {
                attempts = entry.attempts() + 1;
                break;
            }
        }
        failedAlbums.removeIf(entry -> entry.albumId() != null && entry.albumId().equals(albumId));
        failedAlbums.add(new FailedAlbum(albumId, type, reason, now(), attempts));
        return failedAlbums;
    }

    /**
     * 惰性剔除已过期的 permanent 条目（insert / transient 是待办，不受影响）。
     */
    private PlaylistProgress dropExpiredPermanent(PlaylistProgress progress) {
        List<FailedAlbum> alive = new ArrayList<>();
        int expired = 0;
        for (FailedAlbum entry : progress.failedAlbums()) {
            if (isExpiredPermanent(entry)) {
                expired++;
            } else {
                alive.add(entry);
            }
        }
        if (expired == 0) {
            return progress;
        }
        log.info("歌单 {} 有 {} 条 permanent 失败条目已超过保留期，自动移除", progress.playlistId(), expired);
        return new PlaylistProgress(progress.playlistId(), progress.index(), alive, progress.updatedAt());
    }

    /**
     * 是否是已过期的 permanent 条目：类型是 permanent、且记录时间超过 TTL。
     */
    private boolean isExpiredPermanent(FailedAlbum entry) {
        if (!TYPE_PERMANENT.equals(entry.type())) {
            return false;
        }
        if (permanentTtl.isZero() || permanentTtl.isNegative()) {
            return false;
        }
        if (entry.at() == null || entry.at().isBlank()) {
            return false;
        }
        try {
            Instant recordedAt = Instant.parse(entry.at());
            return clock.get().isAfter(recordedAt.plus(permanentTtl));
        } catch (RuntimeException e) {
            // 含 DateTimeParseException：时间戳解析失败不当过期处理
            log.warn("失败条目 {} 的时间戳无法解析，不做过期处理: {}", entry.albumId(), entry.at());
            return false;
        }
    }

    private Long parsePlaylistId(String raw) {
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            log.warn("进度文件名中的歌单 ID 无法解析，忽略: {}", raw);
            return null;
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

    private String now() {
        return clock.get().toString();
    }

    /**
     * 失败清单条目：专辑 ID + 失败类型 + 失败原因 + 记录时间 + 已失败次数。
     *
     * @param albumId  专辑 ID
     * @param type     失败类型：{@code permanent} / {@code transient} / {@code insert}
     * @param reason   失败原因（人类可读）
     * @param at       记录时间（ISO-8601）
     * @param attempts 已失败次数（人工补导可归零重新给预算；老进度文件缺该字段时兜底 0）
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FailedAlbum(Long albumId, String type, String reason, String at, Integer attempts) {

        public FailedAlbum {
            attempts = attempts == null ? 0 : attempts;
        }
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
