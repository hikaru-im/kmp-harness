package im.hikaru.ruoyi.module.member.controller.app.user.vo

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Pattern
import org.hibernate.validator.constraints.Length

@Schema(description = "用户 APP - 修改密码 Request VO")
class AppMemberUserUpdatePasswordReqVO {
    @field:Schema(description = "新密码", requiredMode = Schema.RequiredMode.REQUIRED, example = "buzhidao")
    @field:NotEmpty(message = "新密码不能为空")
    @field:Length(min = 4, max = 16, message = "密码长度为 4-16 位")
    var password: String? = null
    @field:Schema(description = "手机验证码", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @field:NotEmpty(message = "手机验证码不能为空")
    @field:Length(min = 4, max = 6, message = "手机验证码长度为 4-6 位")
    @field:Pattern(regexp = "^[0-9]+$", message = "手机验证码必须都是数字")
    var code: String? = null
}
