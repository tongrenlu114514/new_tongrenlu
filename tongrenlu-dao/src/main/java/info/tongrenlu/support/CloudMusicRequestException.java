package info.tongrenlu.support;

/**
 * 云音乐接口请求失败（超时 / IO 异常）的内部异常（unchecked）。
 *
 * <p>用于把「请求层失败」与「业务层失败（code!=200）」区分开：
 * 前者可重试，后者是永久失败。
 */
public class CloudMusicRequestException extends RuntimeException {

    public CloudMusicRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
