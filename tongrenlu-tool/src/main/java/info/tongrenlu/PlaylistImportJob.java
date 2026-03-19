package info.tongrenlu;

import info.tongrenlu.domain.ArticleBean;
import info.tongrenlu.service.ArticleService;
import info.tongrenlu.service.HomeMusicService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 网易云歌单专辑批量导入任务
 * 通过歌单ID获取所有专辑并批量导入到数据库
 * 使用 WebFlux 真正的异步流式输出执行结果
 */
@RequiredArgsConstructor
@Slf4j
@RestController
public class PlaylistImportJob {

    private final HomeMusicService homeMusicService;
    private final ArticleService articleService;

    /**
     * 进度文件路径
     */
    private static final String PROGRESS_FILE = "E:\\project\\tongrenlu\\tongrenlu-tool\\src\\main\\resources\\data\\playlist_progress.txt";

    /**
     * 流式导入歌单专辑
     * 真正的异步流式处理，每个专辑处理完立即推送事件
     * @param playlistId 歌单ID
     * @return SSE 事件流
     */
    @GetMapping(value = "/playlist/import", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> run(@RequestParam Long playlistId) {
        // 统计计数器
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger skipCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        return Flux.defer(() -> {
            // 获取歌单中所有专辑ID (阻塞操作，在单独线程执行)
            List<Long> albumIds = homeMusicService.getAllPlaylistAlbumIds(playlistId);
            if (albumIds.isEmpty()) {
                return Flux.just(
                        event("warn", "歌单中没有找到任何专辑，请检查歌单ID是否正确")
                );
            }

            // 读取进度（支持断点续传）
            int startIndex = readProgress();
            List<Long> remainingAlbums = albumIds.subList(startIndex, albumIds.size());
            int totalSize = albumIds.size();

            // 开始事件流
            Flux<ServerSentEvent<String>> startEvents = Flux.just(
                    event("start", "========================================"),
                    event("info", "开始导入歌单专辑，歌单ID: " + playlistId),
                    event("start", "========================================"),
                    event("info", "歌单共包含 " + totalSize + " 个专辑")
            );

            if (startIndex > 0) {
                startEvents = startEvents.concatWithValues(
                        event("info", "检测到进度文件，从第 " + (startIndex + 1) + " 个专辑继续导入")
                );
            }

            // 处理每个专辑
            Flux<ServerSentEvent<String>> processEvents = Flux.fromIterable(remainingAlbums)
                    .index((index, albumId) -> {
                        int currentIndex = startIndex + index.intValue() + 1;
                        return new IndexAlbumId(currentIndex, albumId);
                    })
                    // 使用 concatMap 保持顺序处理，确保事件按顺序发送
                    .concatMap(indexed -> processAlbum(indexed, totalSize, successCount, skipCount, failCount)
                            // 添加延迟，避免触发限流（使用非阻塞延迟）
                            .delayElements(Duration.ofMillis(500))
                    )
                    .subscribeOn(Schedulers.boundedElastic()); // 将阻塞操作放到弹性线程池

            // 完成事件流
            Flux<ServerSentEvent<String>> endEvents = Flux.defer(() -> {
                clearProgress();
                return Flux.just(
                        event("complete", "========================================"),
                        event("complete", "导入完成！"),
                        event("complete", "========================================"),
                        event("stats", "歌单总专辑数: " + totalSize),
                        event("stats", "成功导入: " + successCount.get()),
                        event("stats", "跳过（已存在）: " + skipCount.get()),
                        event("stats", "失败: " + failCount.get()),
                        event("done", "进度文件已清除，导入流程结束")
                );
            });

            return startEvents
                    .concatWith(processEvents)
                    .concatWith(endEvents);

        }).subscribeOn(Schedulers.boundedElastic()) // 初始阻塞操作也在弹性线程池
          .onErrorResume(e -> {
              log.error("导入过程发生异常", e);
              return Flux.just(
                      event("fatal", "导入过程发生异常: " + e.getMessage())
              );
          });
    }

    /**
     * 处理单个专辑
     * 返回该专辑处理过程中产生的所有事件
     */
    private Flux<ServerSentEvent<String>> processAlbum(IndexAlbumId indexed, int totalSize,
                                                        AtomicInteger successCount,
                                                        AtomicInteger skipCount,
                                                        AtomicInteger failCount) {
        return Mono.fromCallable(() -> {
                    Long albumId = indexed.albumId();
                    int currentIndex = indexed.index();

                    // 检查专辑是否已存在
                    ArticleBean existing = articleService.getByCloudMusicId(albumId);
                    if (existing != null) {
                        skipCount.incrementAndGet();
                        saveProgress(currentIndex);
                        return List.of(
                                event("skip", String.format("[%d/%d] 专辑已存在，跳过: albumId=%d, title=%s",
                                        currentIndex, totalSize, albumId, existing.getTitle()))
                        );
                    }

                    // 保存专辑
                    homeMusicService.saveCloudMusicAlbum(albumId);
                    successCount.incrementAndGet();
                    saveProgress(currentIndex);

                    return List.of(
                            event("success", String.format("[%d/%d] 专辑导入成功: albumId=%d",
                                    currentIndex, totalSize, albumId))
                    );
                })
                .subscribeOn(Schedulers.boundedElastic()) // 数据库操作在单独线程
                .onErrorResume(e -> {
                    log.error("[{}/{}] 专辑导入失败: albumId={}, error={}",
                            indexed.index(), totalSize, indexed.albumId(), e.getMessage(), e);
                    failCount.incrementAndGet();
                    saveProgress(indexed.index());
                    return Mono.just(List.of(
                            event("error", String.format("[%d/%d] 专辑导入失败: albumId=%d, error=%s",
                                    indexed.index(), totalSize, indexed.albumId(), e.getMessage()))
                    ));
                })
                .flatMapMany(Flux::fromIterable);
    }

    /**
     * 创建 SSE 事件
     */
    private ServerSentEvent<String> event(String eventType, String data) {
        return ServerSentEvent.<String>builder()
                .event(eventType)
                .data(data)
                .build();
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

    /**
     * 索引和专辑ID的记录
     */
    private record IndexAlbumId(int index, Long albumId) {}
}
