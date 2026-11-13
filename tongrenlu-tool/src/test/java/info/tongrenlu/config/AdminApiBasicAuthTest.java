package info.tongrenlu.config;

import info.tongrenlu.AdminOriginalUpdateController;
import info.tongrenlu.AdminThbwikiController;
import info.tongrenlu.AdminUnpublishController;
import info.tongrenlu.OriginalUpdateJob;
import info.tongrenlu.PlaylistFailedController;
import info.tongrenlu.PlaylistRetryJob;
import info.tongrenlu.mapper.ArticleMapper;
import info.tongrenlu.mapper.TrackMapper;
import info.tongrenlu.service.ArticleService;
import info.tongrenlu.service.HomeMusicService;
import info.tongrenlu.service.ThbwikiService;
import info.tongrenlu.support.AlbumImportRunner;
import info.tongrenlu.support.FailedAlbumRetryService;
import info.tongrenlu.support.PlaylistImportLock;
import info.tongrenlu.support.PlaylistProgressStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.assertj.AssertableWebApplicationContext;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 三条管理接口的 Basic 认证回归测试。
 *
 * <p>这三条路径在生产环境曾出现「不带凭据也返回 200」，这里用真实控制器的真实映射
 * （而不是合成的 /ping）做锁定，防止过滤链调整时静默放行。
 */
class AdminApiBasicAuthTest {

    private static final String THBWIKI_HEALTH = "/admin/thbwiki/health";
    private static final String UNPUBLISHED_LIST = "/api/admin/unpublished-list";
    private static final String ORIGINAL_UPDATE_STATUS = "/admin/original-update/status";
    /** 只读失败清单端点 */
    private static final String PLAYLIST_FAILED = "/playlist/failed";
    /** 补导端点（SSE） */
    private static final String PLAYLIST_RETRY_FAILED = "/playlist/retry-failed";

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withUserConfiguration(SecurityConfig.class, AdminApiConfig.class)
            .withPropertyValues(
                    "app.basic-auth.enabled=true",
                    "app.basic-auth.username=admin",
                    "app.basic-auth.password=s3cret",
                    "app.basic-auth.realm-name=tongrenlu-tool");

    @ParameterizedTest(name = "{0} 无凭据返回 401")
    @ValueSource(strings = {THBWIKI_HEALTH, UNPUBLISHED_LIST, ORIGINAL_UPDATE_STATUS})
    @DisplayName("未携带凭据时返回 401，并带 WWW-Authenticate 头")
    void anonymousRequestIsRejected(String path) {
        runner.run(context -> mockMvc(context).perform(get(path))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE,
                        containsString("Basic realm=\"tongrenlu-tool\""))));
    }

    @ParameterizedTest(name = "{0} 错误凭据返回 401")
    @ValueSource(strings = {THBWIKI_HEALTH, UNPUBLISHED_LIST, ORIGINAL_UPDATE_STATUS})
    @DisplayName("密码错误时返回 401")
    void wrongPasswordIsRejected(String path) {
        runner.run(context -> mockMvc(context).perform(get(path).with(httpBasic("admin", "wrong-password")))
                .andExpect(status().isUnauthorized()));
    }

    @ParameterizedTest(name = "{0} 正确凭据可访问")
    @ValueSource(strings = {THBWIKI_HEALTH, UNPUBLISHED_LIST, ORIGINAL_UPDATE_STATUS})
    @DisplayName("凭据正确时仍可访问")
    void validCredentialsAreAccepted(String path) {
        runner.run(context -> mockMvc(context).perform(get(path).with(httpBasic("admin", "s3cret")))
                .andExpect(status().isOk()));
    }

    @Test
    @DisplayName("认证开启且密码仍为默认值时启动失败（防止默认弱口令裸奔）")
    void defaultPasswordFailsFast() {
        new WebApplicationContextRunner()
                .withUserConfiguration(SecurityConfig.class, AdminApiConfig.class)
                .withPropertyValues(
                        "app.basic-auth.enabled=true",
                        "app.basic-auth.username=admin",
                        "app.basic-auth.password=tongrenlu")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("默认弱口令");
                });
    }

    @Test
    @DisplayName("认证开启且密码为空时启动失败")
    void blankPasswordFailsFast() {
        new WebApplicationContextRunner()
                .withUserConfiguration(SecurityConfig.class, AdminApiConfig.class)
                .withPropertyValues(
                        "app.basic-auth.enabled=true",
                        "app.basic-auth.username=admin",
                        "app.basic-auth.password=")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("未配置密码");
                });
    }

    @Test
    @DisplayName("关闭认证时不做密码校验，本地开发可绕过")
    void disabledAuthSkipsPasswordCheck() {
        new WebApplicationContextRunner()
                .withUserConfiguration(SecurityConfig.class, AdminApiConfig.class)
                .withPropertyValues(
                        "app.basic-auth.enabled=false",
                        "app.basic-auth.username=admin",
                        "app.basic-auth.password=tongrenlu")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    @DisplayName("GET /playlist/failed 无凭据返回 401")
    void anonymousFailedListIsRejected() {
        runner.run(context -> mockMvc(context).perform(get(PLAYLIST_FAILED))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE,
                        containsString("Basic realm=\"tongrenlu-tool\""))));
    }

    @Test
    @DisplayName("POST /playlist/retry-failed 无凭据返回 401")
    void anonymousRetryFailedIsRejected() {
        runner.run(context -> mockMvc(context).perform(post(PLAYLIST_RETRY_FAILED))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE,
                        containsString("Basic realm=\"tongrenlu-tool\""))));
    }

    @Test
    @DisplayName("GET /playlist/failed 凭据正确时可访问")
    void failedListWithValidCredentialsIsAccepted() {
        runner.run(context -> mockMvc(context)
                .perform(get(PLAYLIST_FAILED).param("playlistId", "1").with(httpBasic("admin", "s3cret")))
                .andExpect(status().isOk()));
    }

    @Test
    @DisplayName("POST /playlist/retry-failed 凭据正确时不被认证挡下")
    void retryFailedWithValidCredentialsIsNotUnauthorized() {
        runner.run(context -> mockMvc(context)
                .perform(post(PLAYLIST_RETRY_FAILED).param("playlistId", "1").with(httpBasic("admin", "s3cret")))
                .andExpect(status().isOk()));
    }

    private MockMvc mockMvc(AssertableWebApplicationContext context) {
        WebApplicationContext webContext = context.getSourceApplicationContext();
        return MockMvcBuilders.webAppContextSetup(webContext)
                .apply(springSecurity())
                .build();
    }

    @Configuration
    @EnableWebMvc
    static class AdminApiConfig {

        @Bean
        ThbwikiService thbwikiService() {
            return mock(ThbwikiService.class);
        }

        @Bean
        HomeMusicService homeMusicService() {
            return mock(HomeMusicService.class);
        }

        @Bean
        OriginalUpdateJob originalUpdateJob() {
            return mock(OriginalUpdateJob.class);
        }

        @Bean
        ArticleMapper articleMapper() {
            return mock(ArticleMapper.class);
        }

        @Bean
        TrackMapper trackMapper() {
            return mock(TrackMapper.class);
        }

        @Bean
        AdminThbwikiController adminThbwikiController(ThbwikiService thbwikiService) {
            return new AdminThbwikiController(thbwikiService);
        }

        @Bean
        AdminUnpublishController adminUnpublishController(HomeMusicService homeMusicService) {
            return new AdminUnpublishController(homeMusicService);
        }

        @Bean
        AdminOriginalUpdateController adminOriginalUpdateController(OriginalUpdateJob originalUpdateJob,
                                                                 ThbwikiService thbwikiService,
                                                                 ArticleMapper articleMapper,
                                                                 TrackMapper trackMapper) {
            return new AdminOriginalUpdateController(originalUpdateJob, thbwikiService, articleMapper, trackMapper);
        }

        @Bean
        ArticleService articleService() {
            return mock(ArticleService.class);
        }

        @Bean
        PlaylistProgressStore playlistProgressStore() {
            return new PlaylistProgressStore(Path.of(System.getProperty("java.io.tmpdir"), "tongrenlu-playlist-progress-auth"));
        }

        @Bean
        PlaylistImportLock playlistImportLock() {
            return new PlaylistImportLock();
        }

        @Bean
        AlbumImportRunner albumImportRunner(HomeMusicService homeMusicService, ArticleService articleService) {
            return new AlbumImportRunner(homeMusicService, articleService);
        }

        @Bean
        FailedAlbumRetryService failedAlbumRetryService(PlaylistProgressStore playlistProgressStore,
                                                         AlbumImportRunner albumImportRunner) {
            return new FailedAlbumRetryService(playlistProgressStore, albumImportRunner);
        }

        @Bean
        PlaylistFailedController playlistFailedController(PlaylistProgressStore playlistProgressStore) {
            return new PlaylistFailedController(playlistProgressStore);
        }

        @Bean
        PlaylistRetryJob playlistRetryJob(PlaylistProgressStore playlistProgressStore,
                                          FailedAlbumRetryService failedAlbumRetryService,
                                          PlaylistImportLock playlistImportLock) {
            return new PlaylistRetryJob(playlistProgressStore, failedAlbumRetryService, playlistImportLock);
        }
    }
}
