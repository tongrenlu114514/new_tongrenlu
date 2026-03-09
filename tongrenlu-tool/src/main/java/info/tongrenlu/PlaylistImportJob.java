package info.tongrenlu;

import info.tongrenlu.domain.ArticleBean;
import info.tongrenlu.service.ArticleService;
import info.tongrenlu.service.HomeMusicService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 网易云歌单专辑批量导入任务
 * 通过歌单ID获取所有专辑并批量导入到数据库
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PlaylistImportJob implements CommandLineRunner {

    private final HomeMusicService homeMusicService;
    private final ArticleService articleService;

    /**
     * 进度文件路径
     */
    private static final String PROGRESS_FILE = "E:\\project\\tongrenlu\\tongrenlu-tool\\src\\main\\resources\\data\\playlist_progress.txt";

    /**
     * 默认歌单ID（可通过命令行参数覆盖）
     */
    private static final Long DEFAULT_PLAYLIST_ID = 149405221L;

    @Override
    public void run(String... args) throws Exception {
        // 解析歌单ID（优先使用命令行参数）
        Long playlistId = parsePlaylistId(args);
        log.info("========================================");
        log.info("开始导入歌单专辑，歌单ID: {}", playlistId);
        log.info("========================================");

        // 获取歌单中所有专辑ID
        List<Long> albumIds = homeMusicService.getAllPlaylistAlbumIds(playlistId);
        if (albumIds.isEmpty()) {
            log.warn("歌单中没有找到任何专辑，请检查歌单ID是否正确");
            return;
        }
        log.info("歌单共包含 {} 个专辑", albumIds.size());

        // 读取进度（支持断点续传）
        int startIndex = readProgress();
        if (startIndex > 0) {
            log.info("检测到进度文件，从第 {} 个专辑继续导入", startIndex + 1);
        }

        // 统计计数器
        int successCount = 0;
        int skipCount = 0;
        int failCount = 0;

        // 批量导入专辑
        for (int i = startIndex; i < albumIds.size(); i++) {
            Long albumId = albumIds.get(i);

            try {
                // 检查专辑是否已存在
                ArticleBean existing = articleService.getByCloudMusicId(albumId);
                if (existing != null) {
                    log.info("[{}/{}] 专辑已存在，跳过: albumId={}, title={}",
                            i + 1, albumIds.size(), albumId, existing.getTitle());
                    skipCount++;
                    saveProgress(i + 1);
                    continue;
                }

                // 保存专辑
                log.info("[{}/{}] 正在导入专辑: albumId={}", i + 1, albumIds.size(), albumId);
                homeMusicService.saveCloudMusicAlbum(albumId);
                successCount++;
                log.info("[{}/{}] 专辑导入成功: albumId={}", i + 1, albumIds.size(), albumId);

                // 保存进度
                saveProgress(i + 1);

                // 添加请求间隔，避免触发限流
                Thread.sleep(500);

            } catch (Exception e) {
                log.error("[{}/{}] 专辑导入失败: albumId={}, error={}",
                        i + 1, albumIds.size(), albumId, e.getMessage(), e);
                failCount++;
                // 失败也保存进度，继续下一个
                saveProgress(i + 1);
            }
        }

        // 输出统计报告
        log.info("========================================");
        log.info("导入完成！");
        log.info("========================================");
        log.info("歌单总专辑数: {}", albumIds.size());
        log.info("成功导入: {}", successCount);
        log.info("跳过（已存在）: {}", skipCount);
        log.info("失败: {}", failCount);

        if (failCount > 0) {
            log.warn("有 {} 个专辑导入失败，请检查日志了解详情", failCount);
        }

        // 清除进度文件
        clearProgress();
    }

    /**
     * 解析歌单ID
     * 支持命令行参数：--playlist.id=123456789
     */
    private Long parsePlaylistId(String[] args) {
        for (String arg : args) {
            if (arg.startsWith("--playlist.id=")) {
                try {
                    return Long.parseLong(arg.substring("--playlist.id=".length()));
                } catch (NumberFormatException e) {
                    log.warn("无效的歌单ID参数: {}", arg);
                }
            }
        }
        return DEFAULT_PLAYLIST_ID;
    }

    /**
     * 读取进度
     */
    private int readProgress() {
        try {
            File file = new File(PROGRESS_FILE);
            if (file.exists()) {
                String progress = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
                return Integer.parseInt(progress.trim());
            }
        } catch (Exception e) {
            log.warn("读取进度文件失败: {}", e.getMessage());
        }
        return 0;
    }

    /**
     * 保存进度
     */
    private void saveProgress(int index) {
        try {
            FileUtils.writeStringToFile(new File(PROGRESS_FILE),
                    String.valueOf(index),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("保存进度文件失败: {}", e.getMessage());
        }
    }

    /**
     * 清除进度文件
     */
    private void clearProgress() {
        try {
            File file = new File(PROGRESS_FILE);
            if (file.exists()) {
                FileUtils.forceDelete(file);
                log.info("进度文件已清除");
            }
        } catch (IOException e) {
            log.warn("清除进度文件失败: {}", e.getMessage());
        }
    }
}
