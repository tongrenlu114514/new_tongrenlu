package info.tongrenlu.support;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 同一歌单的导入互斥锁。
 *
 * <p>为什么需要：进度文件是「读 → 改 → 写回」的 read-modify-write，
 * 人工导入流、手动补导、定时扫描如果同时操作同一歌单，后写的会覆盖先写的，
 * 可能丢掉失败清单条目或把续传位置写回旧值 —— 这正是「并发互相覆盖」那类问题。
 *
 * <p>覆盖三方（同一把锁）：
 * <ul>
 *   <li>人工导入流（{@code /playlist/import}）；</li>
 *   <li>手动补导（{@code /playlist/retry-failed}）；</li>
 *   <li>定时扫描兜底。</li>
 * </ul>
 *
 * <p><b>为什么不用 ReentrantLock</b>：Reactive 流里「加锁」发生在订阅线程，
 * 而「解锁」发生在发出终止信号的那个线程（可能是另一个弹性线程），
 * 跨线程 unlock 会抛 IllegalMonitorStateException（而且会被 reactive 链路悄悄吞掉，
 * 表现为锁永远不释放）。这里用「占用标志位」实现互斥，不绑定线程、解锁可幂等。
 *
 * <p>本服务当前是单实例部署，进程内锁够用；将来多实例部署再换成文件锁或分布式锁。
 */
@Component
public class PlaylistImportLock {

    private final ConcurrentHashMap<Long, AtomicBoolean> busy = new ConcurrentHashMap<>();

    /**
     * 尝试占用某个歌单（不等待）。
     *
     * @return true 表示占用成功；false 表示该歌单已有导入 / 补导 / 扫描在跑，调用方应放弃本次操作
     */
    public boolean tryLock(Long playlistId) {
        AtomicBoolean flag = busy.computeIfAbsent(playlistId, id -> new AtomicBoolean(false));
        return flag.compareAndSet(false, true);
    }

    /**
     * 释放某个歌单的占用（可从任意线程调用，重复调用安全）。
     */
    public void unlock(Long playlistId) {
        AtomicBoolean flag = busy.get(playlistId);
        if (flag != null) {
            flag.set(false);
        }
    }

    /**
     * 该歌单当前是否被占用（只读判断，用于日志与测试）。
     */
    public boolean isLocked(Long playlistId) {
        AtomicBoolean flag = busy.get(playlistId);
        return flag != null && flag.get();
    }
}
