package info.tongrenlu.support;

/**
 * 获取云音乐专辑失败的异常（unchecked）。
 *
 * <p>{@code saveCloudMusicAlbum} 带 {@code @Transactional(rollbackFor = Exception.class)}，
 * 抛 unchecked 异常才能正确触发事务回滚并向上传播，避免「取不到专辑却当成成功入库」。
 *
 * <p>失败原因分两类：
 * <ul>
 *   <li>{@code retryable = true}：上游超时 / IO 异常 / 响应解析失败 —— 属于临时故障，重试即可；</li>
 *   <li>{@code retryable = false}：上游返回 code != 200 —— 属于永久失败，重试也没用，应跳过并记入清单。</li>
 * </ul>
 */
public class CloudMusicAlbumFetchException extends RuntimeException {

    private final Long albumId;
    private final String reason;
    private final boolean retryable;

    public CloudMusicAlbumFetchException(Long albumId, String reason, boolean retryable) {
        this(albumId, reason, retryable, null);
    }

    public CloudMusicAlbumFetchException(Long albumId, String reason, boolean retryable, Throwable cause) {
        super("专辑 " + albumId + " 获取失败（" + (retryable ? "临时故障，可重试" : "永久失败") + "）：" + reason, cause);
        this.albumId = albumId;
        this.reason = reason;
        this.retryable = retryable;
    }

    public Long getAlbumId() {
        return albumId;
    }

    /** 失败原因（人类可读） */
    public String getReason() {
        return reason;
    }

    /** 是否为可重试的临时故障 */
    public boolean isRetryable() {
        return retryable;
    }
}
