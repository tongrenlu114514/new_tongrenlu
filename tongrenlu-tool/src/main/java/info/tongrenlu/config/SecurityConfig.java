package info.tongrenlu.config;

import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;

import java.nio.charset.StandardCharsets;

/**
 * tongrenlu-tool 模块的 HTTP Basic 认证配置。
 *
 * <p>策略：
 * <ul>
 *   <li>模块内所有请求都需要 Basic 凭据（含静态管理页与全部管理接口）；</li>
 *   <li>无状态会话，不签发 Session，也不写 HttpOnly 会话 Cookie；</li>
 *   <li>未认证/凭据错误统一返回 401 + WWW-Authenticate，响应体为 JSON，与本模块既有接口返回格式保持一致。</li>
 * </ul>
 *
 * <p>注意：本模块使用 Basic 认证，浏览器会自动缓存并附带凭据，因此 CSRF 防护被显式关闭
 * （导入/触发类接口需要用 curl 脚本直接调用，启用 CSRF token 会打断既有脚本）。
 * 若后续需要，可改为要求写操作携带自定义请求头或校验 Origin。
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(BasicAuthProperties.class)
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   BasicAuthProperties properties,
                                                   AuthenticationEntryPoint authenticationEntryPoint) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    // 错误页放行，避免请求已认证但处理异常时 ERROR 转发再次触发 401
                    auth.requestMatchers("/error").permitAll();
                    if (properties.isEnabled()) {
                        auth.anyRequest().authenticated();
                    } else {
                        auth.anyRequest().permitAll();
                    }
                })
                .httpBasic(basic -> basic
                        .realmName(properties.getRealmName())
                        .authenticationEntryPoint(authenticationEntryPoint));

        if (properties.isEnabled()) {
            log.info("Basic 认证已启用，受保护范围：tongrenlu-tool 全部接口与静态管理页");
        } else {
            log.warn("Basic 认证已关闭（app.basic-auth.enabled=false），本模块当前对所有请求开放！");
        }

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(BasicAuthProperties properties, PasswordEncoder passwordEncoder) {
        String username = (properties.getUsername() == null || properties.getUsername().isBlank())
                ? "admin"
                : properties.getUsername().trim();
        String rawPassword = properties.getPassword() == null ? "" : properties.getPassword();

        if (properties.isEnabled() && rawPassword.isBlank()) {
            throw new IllegalStateException(
                    "Basic 认证已启用但未配置密码：请设置环境变量 TOOL_AUTH_PASSWORD，"
                            + "或配置 app.basic-auth.password（也可临时设置 TOOL_AUTH_ENABLED=false 关闭认证）");
        }

        UserDetails user = User.withUsername(username)
                .password(passwordEncoder.encode(rawPassword))
                .roles("ADMIN")
                .build();
        return new InMemoryUserDetailsManager(user);
    }

    @Bean
    public AuthenticationEntryPoint basicAuthenticationEntryPoint(BasicAuthProperties properties) {
        return (request, response, authException) -> {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE,
                    "Basic realm=\"" + properties.getRealmName() + "\"");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("{\"success\":false,\"message\":\"需要 Basic 认证，"
                    + "请在请求头中携带 Authorization: Basic base64(账号:密码)\"}");
        };
    }
}
