package info.tongrenlu.support;

import cn.hutool.core.io.IORuntimeException;
import cn.hutool.http.HttpException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.net.Socket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 云音乐接口 HTTP 超时的单元测试。
 *
 * <p>模拟「上游接受连接但迟迟不返回数据」的场景，验证读取超时能快速失败，
 * 而不是像旧实现那样无限期挂起调用线程。
 */
class CloudMusicHttpClientTest {

    @Test
    @DisplayName("读取超时会快速失败，不会无限期挂起")
    void readTimeoutFailsFast() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            Thread accepter = new Thread(() -> {
                try (Socket socket = server.accept()) {
                    // 接受连接后不返回任何数据，模拟上游卡住
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception ignored) {
                    // 测试结束时忽略
                }
            });
            accepter.setDaemon(true);
            accepter.start();

            CloudMusicHttpClient client = new CloudMusicHttpClient(1000, 300);
            String url = "http://127.0.0.1:" + server.getLocalPort() + "/cloudsearch";

            long start = System.currentTimeMillis();
            assertThatThrownBy(() -> client.get(url).execute().body())
                    .isInstanceOfAny(HttpException.class, IORuntimeException.class);
            long elapsed = System.currentTimeMillis() - start;

            assertThat(elapsed).isLessThan(4000L);
        }
    }

    @Test
    @DisplayName("默认超时为连接 10s / 读取 60s")
    void defaultTimeouts() {
        CloudMusicHttpClient client = new CloudMusicHttpClient();

        assertThat(client.getConnectTimeoutMs()).isEqualTo(10_000);
        assertThat(client.getReadTimeoutMs()).isEqualTo(60_000);
        assertThat(CloudMusicHttpClient.DEFAULT_CONNECT_TIMEOUT_MS).isEqualTo(10_000);
        assertThat(CloudMusicHttpClient.DEFAULT_READ_TIMEOUT_MS).isEqualTo(60_000);
    }

    @Test
    @DisplayName("系统属性可覆盖超时，非法值回退默认值")
    void systemPropertyOverridesTimeouts() {
        String previousConnect = System.getProperty(CloudMusicHttpClient.CONNECT_TIMEOUT_PROPERTY);
        String previousRead = System.getProperty(CloudMusicHttpClient.READ_TIMEOUT_PROPERTY);
        System.setProperty(CloudMusicHttpClient.CONNECT_TIMEOUT_PROPERTY, "1500");
        System.setProperty(CloudMusicHttpClient.READ_TIMEOUT_PROPERTY, "not-a-number");
        try {
            CloudMusicHttpClient client = new CloudMusicHttpClient();

            assertThat(client.getConnectTimeoutMs()).isEqualTo(1500);
            assertThat(client.getReadTimeoutMs()).isEqualTo(60_000);
        } finally {
            restore(CloudMusicHttpClient.CONNECT_TIMEOUT_PROPERTY, previousConnect);
            restore(CloudMusicHttpClient.READ_TIMEOUT_PROPERTY, previousRead);
        }
    }

    private static void restore(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }
}
