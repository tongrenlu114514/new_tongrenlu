package info.tongrenlu;

import info.tongrenlu.service.ArticleService;
import info.tongrenlu.service.HomeMusicService;
import info.tongrenlu.support.AlbumImportRunner;
import info.tongrenlu.support.PlaylistImportLock;
import info.tongrenlu.support.PlaylistProgressStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 网易云歌单专辑批量导入任务
 * 通过歌单ID获取所有专辑并批量导入到数据库
 * 使用 WebFlux 真正的异步流式输出执行结果
 *
 * <p>失败处理口径（2026-11-14 起）：
 * <ul>
 *   <li><b>成功 / 已存在</b>：推进进度水位 + 从失败清单中移除该专辑（销账）；</li>
 *   <li><b>永久失败</b>（上游 code!=200）：发 failed 事件 + 记入该歌单失败清单（type=permanent）+ 推进进度，
 *       不阻塞整条歌单；</li>
 *   <li><b>临时失败</b>（超时 / IO / 解析失败）：发 failed 事件 + 记入失败清单（type=transient）+
 *       <b>不推进进度</b> + 连续第 2 次即中止整流（fatal），进度文件保留，下次续传从断点重来；</li>
 *   <li><b>非取专辑异常</b>（入库等）：只发 error（老事件文案不变）+ 记入失败清单（type=insert）+ 推进水位。</li>
 * </ul>
 *
 * <p>失败清单条目带类型与原因，随进度一起落盘：清单非空 ⇒ 进度文件保留；
 * 专辑补导成功后从清单移除 ⇒ 清单清空 ⇒ 进度文件清除。
 *
 * <p>硬约束：进度序号不得越过任何一个未成功的专辑。水位只推进到「最后一个连续成功/跳过的序号」，
 * 失败点之后的成功不会把进度推过去，避免该专辑被永久静默跳过。
 *
 * <p>并发：同一歌单的导入与补导共用一把互斥锁（见 {@link PlaylistImportLock}），
 * 避免同时写同一个进度文件把清单条目或续传位置互相覆盖。
 */
@RequiredArgsConstructor
@Slf4j
@RestController
public class PlaylistImportJob {

    /**
     * 同一歌单本轮内「连续」临时失败达到该次数即中止整流。
     *
     * <p>口径：第 1 次临时失败留一次机会（上游抖动），连续第 2 次即判定上游整体故障、立即中止并保留进度。
     * 计数是<b>本轮内连续</b>计数：成功或跳过一个专辑即清零，本轮开始时从 0 起算（上一轮的失败已写进失败清单与进度，不再叠加）。
     */
    private static final int TRANSIENT_FAILURE_ABORT_THRESHOLD = 2;

    private final HomeMusicService homeMusicService;
    private final AlbumImportRunner albumImportRunner;
    private final PlaylistProgressStore progressStore;
    private final PlaylistImportLock importLock;

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

            // 同一歌单的导入 / 补导 / 扫描共用一把锁，避免并发写同一个进度文件
            if (!importLock.tryLock(playlistId)) {
                log.warn("歌单 {} 正在导入或补导中，本次导入请求已跳过", playlistId);
                return Flux.just(
                        event("warn", "歌单 " + playlistId + " 正在导入或补导中，本次导入请求已跳过")
                );
            }

            try {
                // 读取进度（支持断点续传；进度文件里的歌单ID与当前歌单不一致时视为无进度，从头开始）
                PlaylistProgressStore.PlaylistProgress progress = progressStore.read(playlistId);
                int startIndex = resolveStartIndex(progress.index(), albumIds.size(), playlistId);
                List<Long> remainingAlbums = albumIds.subList(startIndex, albumIds.size());
                int totalSize = albumIds.size();

                // 进度水位：只推进到「最后一个连续成功/跳过的序号」，失败点之后的成功不推进
                AtomicInteger watermark = new AtomicInteger(startIndex);

                // 本轮内「连续」临时失败次数：成功/跳过即清零，本轮从 0 起算（不跨轮累计）
                AtomicInteger consecutiveTransientFailures = new AtomicInteger(0);

                // 本轮失败分类计数：永久 / 临时 / 入库，用于结束时在 stats 里说清失败构成
                RoundFailures roundFailures = new RoundFailures();

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
                        // （节流延迟放在 processAlbum 内部的成功路径上，避免失败路径的事件与中止信号赛跑）
                        .concatMap(indexed -> processAlbum(indexed, playlistId, totalSize, watermark,
                                        consecutiveTransientFailures, roundFailures, successCount, skipCount, failCount))
                        .subscribeOn(Schedulers.boundedElastic()); // 将阻塞操作放到弹性线程池

                // 完成事件流
                Flux<ServerSentEvent<String>> endEvents = Flux.defer(() -> {
                    List<ServerSentEvent<String>> events = new ArrayList<>();
                    events.add(event("complete", "========================================"));
                    events.add(event("complete", "导入完成！"));
                    events.add(event("complete", "========================================"));
                    events.add(event("stats", "歌单总专辑数: " + totalSize));
                    events.add(event("stats", "成功导入: " + successCount.get()));
                    events.add(event("stats", "跳过（已存在）: " + skipCount.get()));
                    events.add(event("stats", "失败: " + failCount.get()));

                    // 失败构成说清楚：永久（上游 code!=200）/ 临时（超时、IO、解析）/ 入库（其它异常）
                    int permanentFailures = roundFailures.permanentCount();
                    int transientFailures = roundFailures.transientCount();
                    int insertFailures = roundFailures.insertCount();
                    int failedThisRound = permanentFailures + transientFailures + insertFailures;
                    if (failedThisRound > 0) {
                        events.add(event("stats", "本次失败 " + failedThisRound + " 个专辑（永久 " + permanentFailures
                                + " / 临时 " + transientFailures + " / 入库 " + insertFailures + "）"));
                    }

                    // 失败清单非空时保留进度文件（没导成的专辑还要能补导回来）
                    int failedCount = progressStore.read(playlistId).failedAlbums().size();
                    if (failedCount > 0) {
                        events.add(event("stats", "失败清单共 " + failedCount + " 个专辑，已保留在进度文件中: "
                                + progressStore.progressFile(playlistId)));
                    }
                    progressStore.clear(playlistId);

                    events.add(event("done", "进度文件已清除，导入流程结束"));
                    return Flux.fromIterable(events);
                });

                return startEvents
                        .concatWith(processEvents)
                        .concatWith(endEvents)
                        // 客户端断开 / 正常结束 / 出错都要释放锁。
                        // 用 doOnTerminate + doOnCancel（而不是 doFinally）：doFinally 在终止信号
                        // 传给下游之后才回调，调用方可能在锁释放前就拿到结果并立刻再跑一轮；
                        // doOnTerminate 在信号传播前回调，锁一定先释放再让下游看到完成。
                        .doOnTerminate(() -> importLock.unlock(playlistId))
                        .doOnCancel(() -> importLock.unlock(playlistId));

            } catch (RuntimeException e) {
                importLock.unlock(playlistId);
                throw e;
            }

        }).subscribeOn(Schedulers.boundedElastic()) // 初始阻塞操作也在弹性线程池
          .onErrorResume(e -> {
              log.error("导入过程发生异常", e);
              return Flux.just(
                      event("fatal", "导入过程发生异常: " + e.getMessage())
              );
          });
    }

    /**
     * 越界保护：序号为负或 >= 当前歌单专辑数都视为无效进度，归零从头开始，绝不让 subList 抛异常。
     */
    private int resolveStartIndex(int progressIndex, int totalAlbums, Long playlistId) {
        if (progressIndex < 0 || progressIndex >= totalAlbums) {
            if (progressIndex != 0) {
                log.warn("进度序号 {} 越界（当前歌单共 {} 个专辑），归零从头导入: playlistId={}",
                        progressIndex, totalAlbums, playlistId);
            }
            return 0;
        }
        return progressIndex;
    }

    /**
     * 推进进度水位：只有当该专辑正好接在水位之后时才推进，
     * 保证进度序号不会越过任何一个失败的专辑。
     */
    private void advanceWatermark(AtomicInteger watermark, int currentIndex) {
        if (currentIndex == watermark.get() + 1) {
            watermark.set(currentIndex);
        }
    }

    /**
     * 处理单个专辑
     * 返回该专辑处理过程中产生的所有事件
     */
    private Flux<ServerSentEvent<String>> processAlbum(IndexAlbumId indexed, Long playlistId, int totalSize,
                                                        AtomicInteger watermark,
                                                        AtomicInteger consecutiveTransientFailures,
                                                        RoundFailures roundFailures,
                                                        AtomicInteger successCount,
                                                        AtomicInteger skipCount,
                                                        AtomicInteger failCount) {
        return Flux.defer(() -> {
                    Long albumId = indexed.albumId();
                    int currentIndex = indexed.index();

                    return Mono.fromCallable(() -> {
                                // 单个专辑的导入逻辑与补导共用（AlbumImportRunner）：
                                // 已存在 → 跳过；取专辑 / 入库失败按既有规则抛出去，由下面的 onErrorResume 分流
                                AlbumImportRunner.ImportOutcome outcome = albumImportRunner.importOne(albumId);
                                if (outcome.status() == AlbumImportRunner.ImportOutcome.Status.SKIPPED) {
                                    skipCount.incrementAndGet();
                                    consecutiveTransientFailures.set(0); // 成功/跳过 → 连续失败计数清零
                                    advanceWatermark(watermark, currentIndex);
                                    // 命中「已存在」也算这个专辑已经导进去了 → 从失败清单销账
                                    progressStore.markSuccess(playlistId, albumId, watermark.get());
                                    return List.of(
                                            event("skip", String.format("[%d/%d] 专辑已存在，跳过: albumId=%d, title=%s",
                                                    currentIndex, totalSize, albumId, outcome.title()))
                                    );
                                }

                                successCount.incrementAndGet();
                                consecutiveTransientFailures.set(0); // 成功 → 连续失败计数清零
                                advanceWatermark(watermark, currentIndex);
                                // 导入成功 → 从失败清单销账（清单空了就允许清进度文件）
                                progressStore.markSuccess(playlistId, albumId, watermark.get());

                                return List.of(
                                        event("success", String.format("[%d/%d] 专辑导入成功: albumId=%d",
                                                currentIndex, totalSize, albumId))
                                );
                            })
                            .subscribeOn(Schedulers.boundedElastic()) // 数据库操作在单独线程
                            .flatMapMany(Flux::fromIterable)
                            // 添加延迟，避免触发限流（只作用于成功/跳过路径）
                            .delayElements(Duration.ofMillis(500));
                })
                .onErrorResume(e -> {
                    log.error("[{}/{}] 专辑导入失败: albumId={}, error={}",
                            indexed.index(), totalSize, indexed.albumId(), e.getMessage(), e);
                    failCount.incrementAndGet();

                    // 失败分类（与补导共用）：取专辑失败按 retryable 分 permanent / transient，其余归 insert
                    AlbumImportRunner.Failure failure = albumImportRunner.classify(e);

                    // 事件分流：error 是旧事件（语义固定为「导入过程抛异常」，文案一字不改），
                    // failed 是纯新增事件（只用于「取专辑失败」），两者互斥，老脚本不受影响。
                    if (!failure.fetchFailure()) {
                        // 非取专辑异常（入库等）：只发 error，老路径保持原样；
                        // 但该专辑同样没入库，必须记入失败清单并推进水位，否则整流结束时进度文件被清掉就静默丢了
                        advanceWatermark(watermark, indexed.index());
                        progressStore.markFailed(playlistId, indexed.albumId(),
                                PlaylistProgressStore.TYPE_INSERT, failure.reason(), watermark.get());
                        roundFailures.countInsert();
                        log.warn("专辑 {} 发生非取专辑异常（入库等），已记入歌单 {} 的失败清单",
                                indexed.albumId(), playlistId);
                        return Flux.just(event("error", String.format("[%d/%d] 专辑导入失败: albumId=%d, error=%s",
                                indexed.index(), totalSize, indexed.albumId(), e.getMessage())));
                    }

                    boolean retryable = failure.retryable();
                    String reason = failure.reason();

                    // 取专辑失败：只发 failed 事件，不发 error
                    ServerSentEvent<String> failedEvent = event("failed",
                            String.format("[%d/%d] 专辑导入失败: albumId=%d, 可重试=%s, 原因=%s",
                                    indexed.index(), totalSize, indexed.albumId(), retryable, reason));

                    if (!retryable) {
                        // 永久失败：记入该歌单失败清单并推进进度，不阻塞整条歌单
                        advanceWatermark(watermark, indexed.index());
                        progressStore.markFailed(playlistId, indexed.albumId(),
                                PlaylistProgressStore.TYPE_PERMANENT, reason, watermark.get());
                        roundFailures.countPermanent();
                        log.warn("专辑 {} 为永久失败，已记入歌单 {} 的失败清单并推进进度", indexed.albumId(), playlistId);
                        return Flux.just(failedEvent);
                    }

                    // 临时失败：不推进进度（水位保持不变），但同样要落盘记账（清单里标 transient），
                    // 这样整流走完后清单非空 → 进度文件保留 → 下一轮从断点重试这个专辑
                    progressStore.markFailed(playlistId, indexed.albumId(),
                            PlaylistProgressStore.TYPE_TRANSIENT, reason, watermark.get());
                    roundFailures.countTransient();
                    int consecutiveFailures = consecutiveTransientFailures.incrementAndGet();
                    if (consecutiveFailures >= TRANSIENT_FAILURE_ABORT_THRESHOLD) {
                        log.error("歌单 {} 本轮连续临时失败 {} 次，判定上游整体故障，中止导入并保留进度",
                                playlistId, consecutiveFailures);
                        // 先把 failed 事件发出去，再抛出去由外层 onErrorResume 发 fatal 并中止整流；
                        // endEvents 不执行 → 进度文件保留
                        return Flux.concat(Flux.just(failedEvent), Flux.error(e));
                    }
                    log.warn("歌单 {} 临时失败 {}/{} 次，继续处理后续专辑: albumId={}",
                            playlistId, consecutiveFailures, TRANSIENT_FAILURE_ABORT_THRESHOLD, indexed.albumId());
                    return Flux.just(failedEvent);
                });
    }

    /**
     * 本轮失败分类计数：永久（上游 code!=200）/ 临时（超时、IO、解析）/ 入库（其它异常）。
     * 结束时写进 stats，让「失败」这件事在结果里看得见、说得清。
     */
    private static final class RoundFailures {

        private final AtomicInteger permanent = new AtomicInteger(0);
        private final AtomicInteger transientFailures = new AtomicInteger(0);
        private final AtomicInteger insert = new AtomicInteger(0);

        void countPermanent() {
            permanent.incrementAndGet();
        }

        void countTransient() {
            transientFailures.incrementAndGet();
        }

        void countInsert() {
            insert.incrementAndGet();
        }

        int permanentCount() {
            return permanent.get();
        }

        int transientCount() {
            return transientFailures.get();
        }

        int insertCount() {
            return insert.get();
        }
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
     * 索引和专辑ID的记录
     */
    private record IndexAlbumId(int index, Long albumId) {}
}
