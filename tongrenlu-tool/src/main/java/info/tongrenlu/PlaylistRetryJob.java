package info.tongrenlu;

import info.tongrenlu.support.FailedAlbumRetryService;
import info.tongrenlu.support.PlaylistImportLock;
import info.tongrenlu.support.PlaylistProgressStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 按失败清单补导专辑（type=insert 为主）。
 *
 * <p>为什么需要：入库失败的专辑（type=insert）水位已经越过它，
 * 正常导入流不会回头重试 —— 不补导它就会被无限期遗忘。
 * 另两类不归这里管：
 * <ul>
 *   <li>{@code transient}：水位不越过它，下一轮导入流自动重试；</li>
 *   <li>{@code permanent}：上游明确没有这个专辑，补导只是反复打一个必然失败的接口。</li>
 * </ul>
 *
 * <p>与人工导入流的关系：
 * <ul>
 *   <li><b>只销账、不动水位</b>：被补导的专辑水位早已越过，改 index 会破坏续传位置；</li>
 *   <li><b>同歌单互斥</b>：与人工导入流、定时扫描共用同一把锁，避免同时写同一进度文件；</li>
 *   <li><b>不清进度文件</b>：清单里可能还有别的条目，删掉进度文件就丢了续传位置。</li>
 * </ul>
 *
 * <p>事件口径：沿用既有事件名（start / info / complete / success / skip / failed / error / warn / stats / done），
 * 不新增事件名；既有 {@code /playlist/import} 的事件名与文案一字未动。
 */
@RequiredArgsConstructor
@Slf4j
@RestController
public class PlaylistRetryJob {

    private final PlaylistProgressStore progressStore;
    private final FailedAlbumRetryService retryService;
    private final PlaylistImportLock importLock;

    /**
     * 补导指定歌单的失败条目。
     *
     * @param playlistId 歌单 ID（必填）
     * @param type      要补导的失败类型，默认 {@code insert}；自动模式下生效
     * @param albumIds  逗号分隔的专辑 ID；给了就按这个列表来（人工明确指定时听人的，不再受 type 限制）
     */
    @PostMapping(value = "/playlist/retry-failed", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> retryFailed(@RequestParam Long playlistId,
                                                      @RequestParam(required = false) String type,
                                                      @RequestParam(required = false) String albumIds) {
        return Flux.defer(() -> {
            String targetType = (type == null || type.isBlank())
                    ? PlaylistProgressStore.TYPE_INSERT
                    : type.trim();
            List<Long> requestedIds = parseAlbumIds(albumIds);
            List<PlaylistProgressStore.FailedAlbum> candidates = selectCandidates(playlistId, targetType, requestedIds);

            if (candidates.isEmpty()) {
                int remaining = progressStore.read(playlistId).failedAlbums().size();
                log.info("歌单 {} 没有需要补导的条目（类型 {}），本次不执行", playlistId, targetType);
                return Flux.just(
                        event("info", "歌单 " + playlistId + " 的失败清单里没有需要补导的条目（类型：" + targetType + "）"),
                        event("stats", "失败清单仍有 " + remaining + " 条"),
                        event("done", "补导结束：没有需要补导的条目，失败清单仍有 " + remaining + " 条，进度文件未清除")
                );
            }

            if (!importLock.tryLock(playlistId)) {
                int remaining = progressStore.read(playlistId).failedAlbums().size();
                log.warn("歌单 {} 正在导入或补导中，本次补导请求已跳过", playlistId);
                return Flux.just(
                        event("warn", "歌单 " + playlistId + " 正在导入或补导中，本次补导请求已跳过"),
                        event("done", "补导结束：未执行（该歌单正在导入或补导中），失败清单仍有 "
                                + remaining + " 条，进度文件未清除")
                );
            }

            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger skipCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);
            int total = candidates.size();

            Flux<ServerSentEvent<String>> startEvents = Flux.just(
                    event("start", "========================================"),
                    event("info", "开始补导歌单 " + playlistId + " 的失败条目，共 " + total + " 条（类型：" + targetType + "）")
            );

            Flux<ServerSentEvent<String>> processEvents = Flux.fromIterable(candidates)
                    .index((index, entry) -> new IndexEntry(index.intValue() + 1, entry))
                    .concatMap(indexed -> processEntry(indexed, playlistId, total, successCount, skipCount, failCount))
                    .subscribeOn(Schedulers.boundedElastic());

            Flux<ServerSentEvent<String>> endEvents = Flux.defer(() -> {
                int remaining = progressStore.read(playlistId).failedAlbums().size();
                List<ServerSentEvent<String>> events = new ArrayList<>();
                events.add(event("complete", "========================================"));
                events.add(event("complete", "补导完成！"));
                events.add(event("complete", "========================================"));
                events.add(event("stats", "补导目标: " + total));
                events.add(event("stats", "补导成功: " + successCount.get()));
                events.add(event("stats", "跳过（已存在）: " + skipCount.get()));
                events.add(event("stats", "补导失败: " + failCount.get()));
                events.add(event("stats", "失败清单仍有 " + remaining + " 条"));
                // done 文案必须与真实行为一致：补导不清进度文件，不能写「进度文件已清除」
                events.add(event("done", "补导结束：成功 " + successCount.get() + " / 跳过 " + skipCount.get()
                        + " / 失败 " + failCount.get() + "，失败清单仍有 " + remaining + " 条，进度文件未清除"));
                return Flux.fromIterable(events);
            });

            return startEvents
                    .concatWith(processEvents)
                    .concatWith(endEvents)
                    // 客户端断开 / 正常结束 / 出错都要释放锁。
                    // 用 doOnTerminate + doOnCancel（而不是 doFinally）：doFinally 在终止信号
                    // 传给下游之后才回调，调用方可能在锁释放前就拿到结果并立刻再跑一轮。
                    .doOnTerminate(() -> importLock.unlock(playlistId))
                    .doOnCancel(() -> importLock.unlock(playlistId));
        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorResume(e -> {
              log.error("补导过程发生异常", e);
              return Flux.just(
                      event("fatal", "补导过程发生异常: " + e.getMessage())
              );
          });
    }

    /**
     * 选出本次要补导的条目。
     *
     * <p>自动模式（没指定 albumIds）：按失败类型筛。
     * 人工指定模式（给了 albumIds）：按指定的专辑来，不再受类型限制
     * —— 人明确点了某个专辑，就该听人的（transient / permanent 也可以人工强补一次）。
     */
    private List<PlaylistProgressStore.FailedAlbum> selectCandidates(Long playlistId, String type, List<Long> requestedIds) {
        List<PlaylistProgressStore.FailedAlbum> all = progressStore.read(playlistId).failedAlbums();
        if (requestedIds.isEmpty()) {
            return all.stream()
                    .filter(entry -> type.equals(entry.type()))
                    .toList();
        }
        return all.stream()
                .filter(entry -> entry.albumId() != null && requestedIds.contains(entry.albumId()))
                .toList();
    }

    /**
     * 解析逗号分隔的专辑 ID 列表。
     */
    private List<Long> parseAlbumIds(String albumIds) {
        if (albumIds == null || albumIds.isBlank()) {
            return List.of();
        }
        return Arrays.stream(albumIds.split(","))
                .map(String::trim)
                .filter(id -> !id.isEmpty())
                .map(id -> {
                    try {
                        return Long.parseLong(id);
                    } catch (NumberFormatException e) {
                        log.warn("专辑 ID 无法解析，已忽略: {}", id);
                        return null;
                    }
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    /**
     * 补导单个条目并产出对应事件。
     */
    private Flux<ServerSentEvent<String>> processEntry(IndexEntry indexed, Long playlistId, int total,
                                                        AtomicInteger successCount, AtomicInteger skipCount,
                                                        AtomicInteger failCount) {
        return Flux.defer(() -> {
                    PlaylistProgressStore.FailedAlbum entry = indexed.entry();
                    Long albumId = entry.albumId();
                    return Mono.fromCallable(() -> {
                                // 人工手动补导：失败计数归零，重新给一份完整预算
                                FailedAlbumRetryService.RetryOutcome outcome =
                                        retryService.retryOne(playlistId, entry, true);
                                if (outcome.success()) {
                                    if (outcome.skipped()) {
                                        skipCount.incrementAndGet();
                                    } else {
                                        successCount.incrementAndGet();
                                    }
                                } else {
                                    failCount.incrementAndGet();
                                }
                                return outcome;
                            })
                            .subscribeOn(Schedulers.boundedElastic())
                            .flatMapMany(outcome -> {
                                Flux<ServerSentEvent<String>> events = Flux.fromIterable(eventsFor(outcome, indexed, total));
                                // 节流只作用于成功路径（与人工导入流一致）
                                return outcome.success()
                                        ? events.delayElements(Duration.ofMillis(500))
                                        : events;
                            })
                            .onErrorResume(e -> {
                                log.error("补导条目失败: albumId={}, error={}", albumId, e.getMessage(), e);
                                failCount.incrementAndGet();
                                return Flux.just(event("error", String.format("[%d/%d] 补导失败: albumId=%d, error=%s",
                                        indexed.position(), total, albumId, e.getMessage())));
                            });
                });
    }

    /**
     * 按补导结果产出事件：取专辑失败只发 failed，入库等异常只发 error（与人工导入流同一套口径）。
     */
    private List<ServerSentEvent<String>> eventsFor(FailedAlbumRetryService.RetryOutcome outcome,
                                                     IndexEntry indexed, int total) {
        int position = indexed.position();
        Long albumId = outcome.albumId();
        if (outcome.success()) {
            if (outcome.skipped()) {
                return List.of(event("skip", String.format("[%d/%d] 补导跳过：专辑已存在: albumId=%d, title=%s",
                        position, total, albumId, outcome.title())));
            }
            return List.of(event("success", String.format("[%d/%d] 补导成功: albumId=%d",
                    position, total, albumId)));
        }
        if (outcome.fetchFailure()) {
            return List.of(event("failed", String.format("[%d/%d] 补导失败: albumId=%d, 可重试=%s, 原因=%s",
                    position, total, albumId, outcome.retryable(), outcome.reason())));
        }
        return List.of(event("error", String.format("[%d/%d] 补导失败: albumId=%d, error=%s",
                position, total, albumId, outcome.reason())));
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
     * 序号与失败条目的记录
     */
    private record IndexEntry(int position, PlaylistProgressStore.FailedAlbum entry) {}
}
