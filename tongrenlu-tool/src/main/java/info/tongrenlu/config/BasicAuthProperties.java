package info.tongrenlu.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * HTTP Basic 认证配置。
 *
 * <p>tongrenlu-tool 是纯管理工具模块，本配置对其<b>全部接口</b>（/api/**、/admin/**、
 * /album/import、/artist/import、/playlist/import）与<b>静态管理页</b>（artist.html、unpublish.html）
 * 一律生效，未携带凭据的请求统一返回 401。
 *
 * <p>凭据来源：application.properties 中的默认值 + 环境变量覆盖
 * （TOOL_AUTH_ENABLED / TOOL_AUTH_USERNAME / TOOL_AUTH_PASSWORD）。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.basic-auth")
public class BasicAuthProperties {

    /** 是否启用 Basic 认证；本地调试可临时设为 false 关闭保护 */
    private boolean enabled = true;

    /** 认证账号 */
    private String username = "admin";

    /** 认证密码；生产环境务必通过环境变量注入，不要写进版本库 */
    private String password = "";

    /** 认证域，浏览器登录框与 WWW-Authenticate 头中显示 */
    private String realmName = "tongrenlu-tool";
}
