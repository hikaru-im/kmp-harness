package im.hikaru.ruoyi.module.member.controller.app.auth.vo

import im.hikaru.ruoyi.framework.common.validation.InEnum
import im.hikaru.ruoyi.framework.common.validation.Mobile
import im.hikaru.ruoyi.module.system.enums.sms.SmsSceneEnum
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import org.hibernate.validator.constraints.Length

@Schema(description = "用户 APP - 校验验证码 Request VO")
class AppAuthCheckCodeReqVO {
    @field:Schema(description = "手机号", example = "15601691234")
    @field:NotBlank(message = "手机号不能为空")
    @field:Mobile
    var mobile: String? = null
    @field:Schema(description = "手机验证码", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @field:NotBlank(message = "手机验证码不能为空")
    @field:Length(min = 4, max = 6, message = "手机验证码长度为 4-6 位")
    @field:Pattern(regexp = "^[0-9]+$", message = "手机验证码必须都是数字")
    var code: String? = null
    @field:Schema(description = "发送场景,对应 SmsSceneEnum 枚举", example = "1")
    @field:NotNull(message = "发送场景不能为空")
    @field:InEnum(SmsSceneEnum::class)
    var scene: Int? = null
}
