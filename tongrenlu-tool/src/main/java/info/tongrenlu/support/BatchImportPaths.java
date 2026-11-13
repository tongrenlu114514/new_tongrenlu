package info.tongrenlu.support;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 批处理（专辑 / 歌手导入）用到的文件路径：相对路径 + 可覆盖。
 *
 * <p>旧实现把路径写死成 {@code E:\project\tongrenlu\tongrenlu-tool\src\main\resources\data\...}，
 * 仓库迁移到 new_tongrenlu 后该目录不存在，批处理一跑就找不到文件。
 *
 * <p>现在的口径：
 * <ul>
 *   <li>进度文件目录：Spring 配置项 {@code app.batch-import.progress-dir} → 系统属性
 *       {@code tongrenlu.batch.progress-dir} → 环境变量 {@code TONGRLU_BATCH_PROGRESS_DIR}
 *       → 默认相对目录 {@code data}（相对运行目录）；</li>
 *   <li>专辑输入文件：Spring 配置项 {@code app.batch-import.album-source}，
 *       默认 {@code classpath:data/ls.txt} —— 文件仍留在仓库
 *       {@code tongrenlu-tool/src/main/resources/data/ls.txt}，不移动不删除，
 *       打包后从 classpath 读取；也支持直接写文件系统路径。</li>
 * </ul>
 */
@Slf4j
@Component
public class BatchImportPaths {

    /** Spring 配置项：批处理进度目录 */
    public static final String PROGRESS_DIR_CONFIG = "app.batch-import.progress-dir";

    /** 系统属性：批处理进度目录 */
    public static final String PROGRESS_DIR_PROPERTY = "tongrenlu.batch.progress-dir";

    /** 环境变量：批处理进度目录 */
    public static final String PROGRESS_DIR_ENV = "TONGRLU_BATCH_PROGRESS_DIR";

    /** 默认相对目录（相对运行目录） */
    public static final String DEFAULT_RELATIVE_DIR = "data";

    /** Spring 配置项：专辑输入文件 */
    public static final String ALBUM_SOURCE_CONFIG = "app.batch-import.album-source";

    /** 默认专辑输入文件（classpath 资源） */
    public static final String DEFAULT_ALBUM_SOURCE = "classpath:data/ls.txt";

    private final Path progressDir;
    private final String albumSource;

    @Autowired
    public BatchImportPaths(@Value("${app.batch-import.progress-dir:}") String progressDir,
                            @Value("${app.batch-import.album-source:classpath:data/ls.txt}") String albumSource) {
        this.progressDir = resolveDir(progressDir);
        this.albumSource = (albumSource == null || albumSource.isBlank())
                ? DEFAULT_ALBUM_SOURCE : albumSource.trim();
    }

    public Path getProgressDir() {
        return progressDir;
    }

    /** 专辑输入文件（可能是 classpath: 前缀，也可能是文件系统路径） */
    public String getAlbumSource() {
        return albumSource;
    }

    /** 进度文件路径 */
    public Path progressFile(String fileName) {
        return progressDir.resolve(fileName);
    }

    /**
     * 读取进度（纯整数）；文件不存在或内容损坏时返回 0。
     */
    public int readProgress(String fileName) {
        Path file = progressFile(fileName);
        try {
            if (!Files.exists(file)) {
                return 0;
            }
            String content = Files.readString(file, StandardCharsets.UTF_8).trim();
            return content.isEmpty() ? 0 : Integer.parseInt(content);
        } catch (IOException | NumberFormatException e) {
            log.error("读取进度文件失败，按 0 处理: file={}, error={}", file, e.getMessage(), e);
            return 0;
        }
    }

    /**
     * 写入进度（纯整数），父目录自动创建。
     */
    public void writeProgress(String fileName, int value) {
        Path file = progressFile(fileName);
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(file, String.valueOf(value), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("写入进度文件失败: file={}, value={}, error={}", file, value, e.getMessage(), e);
        }
    }

    /**
     * 按「Spring 配置项 → 系统属性 → 环境变量 → 默认相对目录」的顺序解析进度目录。
     */
    public static Path resolveDir(String springConfiguredDir) {
        String configured = springConfiguredDir;
        if (configured == null || configured.isBlank()) {
            configured = System.getProperty(PROGRESS_DIR_PROPERTY);
        }
        if (configured == null || configured.isBlank()) {
            configured = System.getenv(PROGRESS_DIR_ENV);
        }
        Path base = Path.of(System.getProperty("user.dir", "."));
        if (configured != null && !configured.isBlank()) {
            Path path = Path.of(configured.trim());
            boolean absolute = path.isAbsolute() || path.getRoot() != null;
            return (absolute ? path : base.resolve(path)).toAbsolutePath().normalize();
        }
        return base.resolve(DEFAULT_RELATIVE_DIR).toAbsolutePath().normalize();
    }

    /** 读取输入文件：支持 classpath: 前缀与文件系统路径 */
    public static java.io.InputStream openSource(String source) throws IOException {
        if (source != null && source.startsWith("classpath:")) {
            String resource = source.substring("classpath:".length());
            java.io.InputStream in = BatchImportPaths.class.getClassLoader().getResourceAsStream(resource);
            if (in == null) {
                throw new java.io.FileNotFoundException("classpath 资源不存在: " + resource);
            }
            return in;
        }
        return Files.newInputStream(Path.of(source));
    }

    /** 读取输入文件用的字符集（与旧的 FileReader 一致：平台默认） */
    public static Charset defaultCharset() {
        return Charset.defaultCharset();
    }
}
