package im.hikaru.ruoyi.framework.swagger.config

import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties(prefix = "yudao.swagger")
class SwaggerProperties {
    @field:NotBlank(message = "标题不能为空")
    var title: String = ""

    @field:NotBlank(message = "描述不能为空")
    var description: String = ""

    @field:NotBlank(message = "作者不能为空")
    var author: String = ""

    @field:NotBlank(message = "版本不能为空")
    var version: String = ""

    @field:NotBlank(message = "项目地址不能为空")
    var url: String = ""

    @field:NotBlank(message = "联系邮箱不能为空")
    var email: String = ""

    @field:NotBlank(message = "许可证不能为空")
    var license: String = ""

    @field:NotBlank(message = "许可证地址不能为空")
    var licenseUrl: String = ""
}
