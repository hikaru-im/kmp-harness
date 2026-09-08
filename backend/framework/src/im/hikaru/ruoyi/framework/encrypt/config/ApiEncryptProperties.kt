package im.hikaru.ruoyi.framework.encrypt.config

import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties(prefix = "yudao.api-encrypt")
class ApiEncryptProperties {
    var enable: Boolean = false

    @field:NotBlank(message = "请求头（响应头）名称不能为空")
    var header: String = "X-Api-Encrypt"

    @field:NotBlank(message = "加密算法不能为空")
    var algorithm: String = ""

    @field:NotBlank(message = "请求的解密密钥不能为空")
    var requestKey: String = ""

    @field:NotBlank(message = "响应的加密密钥不能为空")
    var responseKey: String = ""
}
