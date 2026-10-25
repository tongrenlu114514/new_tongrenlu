package info.tongrenlu.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.assertj.AssertableWebApplicationContext;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * tongrenlu-tool Basic 认证关闭时（本地调试用）应完全放行。
 */
class BasicAuthDisabledTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withUserConfiguration(SecurityConfig.class, TestWebConfig.class)
            .withPropertyValues(
                    "app.basic-auth.enabled=false",
                    "app.basic-auth.username=admin",
                    "app.basic-auth.password=s3cret");

    @Test
    @DisplayName("关闭认证后匿名请求直接放行")
    void anonymousRequestIsPermitted() {
        runner.run(context -> mockMvc(context).perform(get("/ping"))
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
