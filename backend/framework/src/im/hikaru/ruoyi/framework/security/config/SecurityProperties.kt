package im.hikaru.ruoyi.framework.security.config

import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

/**
 * Security 配置项 (迁移自 Java, 去 Lombok)
 */
@ConfigurationProperties(prefix = "yudao.security")
@Validated
class SecurityProperties {

    /** HTTP 请求时，访问令牌的请求 Header */
    @field:NotEmpty(message = "Token Header 不能为空")
    var tokenHeader: String = "Authorization"

    /** HTTP 请求时，访问令牌的请求参数 */
    @field:NotEmpty(message = "Token Parameter 不能为空")
    var tokenParameter: String = "token"

    /** mock 模式的开关 */
    @field:NotNull(message = "mock 模式的开关不能为空")
    var mockEnable: Boolean = false

    /** mock 模式的密钥。一定要配置密钥，保证安全性 */
    @field:NotEmpty(message = "mock 模式的密钥不能为空")
    var mockSecret: String = "test"

    /** 免登录的 URL 列表 */
    var permitAllUrls: List<String> = emptyList()

    /** PasswordEncoder 加密复杂度，越高开销越大 */
    var passwordEncoderLength: Int = 4
}
