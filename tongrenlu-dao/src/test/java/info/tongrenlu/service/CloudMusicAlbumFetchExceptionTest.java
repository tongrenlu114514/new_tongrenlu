package info.tongrenlu.service;

import info.tongrenlu.mapper.ArticleMapper;
import info.tongrenlu.mapper.ArticleTagMapper;
import info.tongrenlu.mapper.TagMapper;
import info.tongrenlu.mapper.TrackMapper;
import info.tongrenlu.support.CloudMusicAlbumFetchException;
import info.tongrenlu.support.CloudMusicHttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * 取专辑失败的分类是否正确（验收标准 6）。
 *
 * <p>用本地 socket 模拟上游：正常 JSON / code!=200 / 响应损坏 / 一直不响应（超时），
 * 验证 {@link CloudMusicAlbumFetchException#isRetryable()} 的分类与
 * 「saveCloudMusicAlbum 不再静默 return」的行为。
 */
class CloudMusicAlbumFetchExceptionTest {

    private ServerSocket server;

    @BeforeEach
    void setUp() {
        // 缩短读取超时，避免超时用例把测试拖成 60s
        System.setProperty(CloudMusicHttpClient.READ_TIMEOUT_PROPERTY, "300");
        System.setProperty(CloudMusicHttpClient.CONNECT_TIMEOUT_PROPERTY, "1000");
    }

    @AfterEach
    void tearDown() {
        System.clearProperty(CloudMusicHttpClient.READ_TIMEOUT_PROPERTY);
        System.clearProperty(CloudMusicHttpClient.CONNECT_TIMEOUT_PROPERTY);
        if (server != null) {
            try {
                server.close();
            } catch (IOException ignored) {
                // 忽略
            }
        }
    }

    @Test
    @DisplayName("上游 code!=200 → 永久失败（retryable=false）")
    void non200CodeIsPermanentFailure() throws Exception {
        HomeMusicService service = serviceWithBase(startServer(
                "{\"code\":404,\"album\":null,\"songs\":null}").baseUrl());

        assertThatThrownBy(() -> service.getCloudMusicAlbumByIdOrThrow(11L))
                .isInstanceOf(CloudMusicAlbumFetchException.class)
                .satisfies(e -> {
                    CloudMusicAlbumFetchException ex = (CloudMusicAlbumFetchException) e;
                    assertThat(ex.isRetryable()).isFalse();
                    assertThat(ex.getAlbumId()).isEqualTo(11L);
                    assertThat(ex.getReason()).contains("404");
                });
    }

    @Test
    @DisplayName("saveCloudMusicAlbum 取不到专辑时显式抛错，不再静默 return")
    void saveCloudMusicAlbumThrowsInsteadOfSilentlyReturning() throws Exception {
        HomeMusicService service = serviceWithBase(startServer(
                "{\"code\":404,\"album\":null,\"songs\":null}").baseUrl());

        assertThatThrownBy(() -> service.saveCloudMusicAlbum(11L))
                .isInstanceOf(CloudMusicAlbumFetchException.class)
                .satisfies(e -> assertThat(((CloudMusicAlbumFetchException) e).isRetryable()).isFalse());
    }

    @Test
    @DisplayName("上游超时 → 临时故障（retryable=true）")
    void timeoutIsTransientFailure() throws Exception {
        HomeMusicService service = serviceWithBase(startServer(null).baseUrl());

        assertThatThrownBy(() -> service.getCloudMusicAlbumByIdOrThrow(11L))
                .isInstanceOf(CloudMusicAlbumFetchException.class)
                .satisfies(e -> assertThat(((CloudMusicAlbumFetchException) e).isRetryable()).isTrue());
    }

    @Test
    @DisplayName("响应解析失败 → 临时故障（retryable=true）")
    void brokenJsonIsTransientFailure() throws Exception {
        HomeMusicService service = serviceWithBase(startServer("not-a-json").baseUrl());

        assertThatThrownBy(() -> service.getCloudMusicAlbumByIdOrThrow(11L))
                .isInstanceOf(CloudMusicAlbumFetchException.class)
                .satisfies(e -> assertThat(((CloudMusicAlbumFetchException) e).isRetryable()).isTrue());
    }

    @Test
    @DisplayName("旧签名 getCloudMusicAlbumById 保持旧行为：失败返回 null 而不抛异常")
    void legacyMethodStillReturnsNullOnFailure() throws Exception {
        HomeMusicService service = serviceWithBase(startServer(
                "{\"code\":404,\"album\":null,\"songs\":null}").baseUrl());

        assertThat(service.getCloudMusicAlbumById(11L)).isNull();
    }

    private HomeMusicService serviceWithBase(String baseUrl) throws Exception {
        HomeMusicService service = new HomeMusicService(
                mock(ArticleMapper.class),
                mock(TrackMapper.class),
                mock(TagMapper.class),
                mock(ArticleTagMapper.class),
                mock(ArtistService.class));
        Field field = HomeMusicService.class.getDeclaredField("cloudMusicApiBase");
        field.setAccessible(true);
        field.set(service, baseUrl);
        return service;
    }

    /**
     * 启动一个本地 HTTP 服务：body 为 null 时接受连接但不返回任何数据（模拟上游卡住）。
     */
    private static TestServer startServer(String body) throws IOException {
        ServerSocket socket = new ServerSocket(0);
        Thread worker = new Thread(() -> {
            while (!socket.isClosed()) {
                try (Socket client = socket.accept()) {
                    if (body == null) {
                        Thread.sleep(3000);
                        continue;
                    }
                    readRequest(client.getInputStream());
                    byte[] payload = body.getBytes(StandardCharsets.UTF_8);
                    OutputStream out = client.getOutputStream();
                    out.write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: "
                            + payload.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                    out.write(payload);
                    out.flush();
                } catch (Exception ignored) {
                    // 连接结束即退出
                }
            }
        });
        worker.setDaemon(true);
        worker.start();
        return new TestServer(socket);
    }

    private static void readRequest(InputStream in) throws IOException {
        int b;
        int newline = 0;
        while ((b = in.read()) != -1) {
            if (b == '\n') {
                newline++;
                if (newline == 2) {
                    return;
                }
            } else if (b != '\r') {
                newline = 0;
            }
        }
    }

    private record TestServer(ServerSocket socket) {
        String baseUrl() {
            return "http://127.0.0.1:" + socket.getLocalPort();
        }
    }
}
