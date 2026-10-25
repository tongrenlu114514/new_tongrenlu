package info.tongrenlu.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.assertj.AssertableWebApplicationContext;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * tongrenlu-tool Basic 认证开启时的行为验证。
 */
class BasicAuthSecurityTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withUserConfiguration(SecurityConfig.class, TestWebConfig.class)
            .withPropertyValues(
                    "app.basic-auth.enabled=true",
                    "app.basic-auth.username=admin",
                    "app.basic-auth.password=s3cret",
                    "app.basic-auth.realm-name=tongrenlu-tool");

    @Test
    @DisplayName("未携带凭据时返回 401，并带 WWW-Authenticate 头与 JSON 提示")
    void anonymousRequestIsRejected() {
        runner.run(context -> mockMvc(context).perform(get("/ping"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE,
                        containsString("Basic realm=\"tongrenlu-tool\"")))
                .andExpect(jsonPath("$.success").value(false)));
    }

    @Test
    @DisplayName("密码错误时返回 401")
    void wrongPasswordIsRejected() {
        runner.run(context -> mockMvc(context).perform(get("/ping").with(httpBasic("admin", "wrong-password")))
                .andExpect(status().isUnauthorized()));
    }

    @Test
    @DisplayName("账号错误时返回 401")
    void wrongUsernameIsRejected() {
        runner.run(context -> mockMvc(context).perform(get("/ping").with(httpBasic("nobody", "s3cret")))
                .andExpect(status().isUnauthorized()));
    }

    @Test
    @DisplayName("凭据正确时放行")
    void validCredentialsAreAccepted() {
        runner.run(context -> mockMvc(context).perform(get("/ping").with(httpBasic("admin", "s3cret")))
                .andExpect(status().isOk())
                .andExpect(content().string("pong")));
    }

    private MockMvc mockMvc(AssertableWebApplicationContext context) {
        WebApplicationContext webContext = context.getSourceApplicationContext();
        return MockMvcBuilders.webAppContextSetup(webContext)
                .apply(springSecurity())
                .build();
    }

    @Configuration
    @EnableWebMvc
    static class TestWebConfig {

        @Bean
        PingController pingController() {
            return new PingController();
        }
    }

    @RestController
    static class PingController {

        @GetMapping("/ping")
        public String ping() {
            return "pong";
        }
    }
}
