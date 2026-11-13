package info.tongrenlu.support;

import cn.hutool.http.HttpRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * 网易云外网调用 HTTP 客户端：统一设置连接超时与读取超时。
 *
 * <p>背景：自建 NeteaseCloudMusicApi 服务在出网到 apis.netstart.cn 时很慢
 * （实测单次约 9.4s，首包常超过 1 分钟）；直连 music.163.com 取歌曲下载地址同样可能长时间无响应。
 * 旧的 {@code HttpRequest.get(url).execute()} 没有设置任何超时，一旦上游卡住，
 * 调用线程会无限期挂住（SSE 导入流随之卡死、web 请求线程被长期占用）。
 *
 * <p>默认值：连接 10s、读取 60s；可用系统属性覆盖，便于压测与故障演练：
 * <ul>
 *   <li>{@code -Dtongrenlu.cloud-music.connect-timeout-ms=10000}</li>
 *   <li>{@code -Dtongrenlu.cloud-music.read-timeout-ms=60000}</li>
 * </ul>
 *
 * <p>异常口径（Hutool 5.8.40 源码确认）：连接超时抛 {@code IORuntimeException}，
 * 读取超时抛 {@code HttpException}（HttpResponse.forceSync 包装），两者都要单独捕获。
 */
@Slf4j
public class CloudMusicHttpClient {

    /** 系统属性：连接超时（毫秒） */
    public static final String CONNECT_TIMEOUT_PROPERTY = "tongrenlu.cloud-music.connect-timeout-ms";

    /** 系统属性：读取超时（毫秒） */
    public static final String READ_TIMEOUT_PROPERTY = "tongrenlu.cloud-music.read-timeout-ms";

    /** 默认连接超时（毫秒） */
    public static final int DEFAULT_CONNECT_TIMEOUT_MS = 10_000;

    /** 默认读取超时（毫秒）：上游首包常超过 1 分钟，给足读取时间但不允许无限挂起 */
    public static final int DEFAULT_READ_TIMEOUT_MS = 60_000;

    private final int connectTimeoutMs;
    private final int readTimeoutMs;

    public CloudMusicHttpClient() {
        this(readPositiveIntProperty(CONNECT_TIMEOUT_PROPERTY, DEFAULT_CONNECT_TIMEOUT_MS),
                readPositiveIntProperty(READ_TIMEOUT_PROPERTY, DEFAULT_READ_TIMEOUT_MS));
    }

    public CloudMusicHttpClient(int connectTimeoutMs, int readTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
    }

    /**
     * 构造带超时的 GET 请求。
     */
    public HttpRequest get(String url) {
        return HttpRequest.get(url)
                .setConnectionTimeout(connectTimeoutMs)
                .setReadTimeout(readTimeoutMs);
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    static int readPositiveIntProperty(String name, int defaultValue) {
        String raw = System.getProperty(name);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            if (value > 0) {
                return value;
            }
            log.warn("系统属性 {} 的值 {} 不是正整数，回退到默认值 {}", name, raw, defaultValue);
        } catch (NumberFormatException e) {
            log.warn("系统属性 {} 的值 {} 不是合法整数，回退到默认值 {}", name, raw, defaultValue);
        }
        return defaultValue;
    }
}
