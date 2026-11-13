package info.tongrenlu.support;

import info.tongrenlu.domain.ArticleBean;
import info.tongrenlu.service.ArticleService;
import info.tongrenlu.service.HomeMusicService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 单个专辑的导入逻辑（人工导入流与补导流共用）。
 *
 * <p>只负责「查是否已存在 → 取专辑并入库」这一步，
 * <b>不写进度文件</b>：进度怎么落由调用方决定
 * （人工导入流要推进水位、补导只销账不动水位，两者语义不同）。
 *
 * <p>异常一律原样抛出，由调用方按既有规则分流：
 * 取专辑失败（{@link CloudMusicAlbumFetchException}）与入库等异常的事件口径不同，
 * 分流规则见 {@link #classify(Throwable)}。
 */
@Component
@RequiredArgsConstructor
public class AlbumImportRunner {

    private final HomeMusicService homeMusicService;
    private final ArticleService articleService;

    /**
     * 导入单个专辑。
     *
     * @return SKIPPED 表示库里已存在（跳过）；SUCCESS 表示导入成功
     * @throws CloudMusicAlbumFetchException 取专辑失败（上游超时 / IO / 解析 / code!=200）
     * @throws RuntimeException               入库等其它异常
     */
    public ImportOutcome importOne(Long albumId) {
        ArticleBean existing = articleService.getByCloudMusicId(albumId);
        if (existing != null) {
            return ImportOutcome.skipped(albumId, existing.getTitle());
        }
        homeMusicService.saveCloudMusicAlbum(albumId);
        return ImportOutcome.succeeded(albumId);
    }

    /**
     * 失败分类：取专辑失败按 retryable 分 permanent / transient，其余归 insert。
     *
     * <p>注意补导时不能沿用条目原来的类型 —— 一次「入库失败」在补导时可能变成上游 404，
     * 那时它已经是 permanent，不该再继续补下去。
     *
     * @param fetchFailure 是否是取专辑失败（决定事件口径：failed 还是 error）
     */
    public Failure classify(Throwable e) {
        if (e instanceof CloudMusicAlbumFetchException fetchException) {
            String type = fetchException.isRetryable()
                    ? PlaylistProgressStore.TYPE_TRANSIENT
                    : PlaylistProgressStore.TYPE_PERMANENT;
            return new Failure(type, fetchException.getReason(), fetchException.isRetryable(), true);
        }
        return new Failure(PlaylistProgressStore.TYPE_INSERT, e.getMessage(), false, false);
    }

    /**
     * 单个专辑的导入结果。
     */
    public record ImportOutcome(Long albumId, Status status, String title) {

        public enum Status {
            /** 导入成功 */
            SUCCESS,
            /** 库里已存在，跳过 */
            SKIPPED
        }

        public static ImportOutcome succeeded(Long albumId) {
            return new ImportOutcome(albumId, Status.SUCCESS, null);
        }

        public static ImportOutcome skipped(Long albumId, String title) {
            return new ImportOutcome(albumId, Status.SKIPPED, title);
        }
    }

    /**
     * 失败分类结果。
     *
     * @param type        permanent / transient / insert
     * @param reason      失败原因
     * @param retryable   是否可重试（取专辑超时 / IO / 解析失败为 true）
     * @param fetchFailure 是否是取专辑失败（true → 发 failed 事件；false → 发 error 事件）
     */
    public record Failure(String type, String reason, boolean retryable, boolean fetchFailure) {
    }
}
