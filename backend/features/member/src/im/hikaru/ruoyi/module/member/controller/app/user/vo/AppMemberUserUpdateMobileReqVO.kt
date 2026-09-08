package im.hikaru.ruoyi.module.member.controller.app.user.vo

import im.hikaru.ruoyi.framework.common.validation.Mobile
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Pattern
import org.hibernate.validator.constraints.Length

@Schema(description = "用户 APP - 修改手机 Request VO")
class AppMemberUserUpdateMobileReqVO {
    @field:Schema(description = "手机验证码", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @field:NotEmpty(message = "手机验证码不能为空")
    @field:Length(min = 4, max = 6, message = "手机验证码长度为 4-6 位")
    @field:Pattern(regexp = "^[0-9]+$", message = "手机验证码必须都是数字")
    var code: String? = null
    @field:Schema(description = "手机号", requiredMode = Schema.RequiredMode.REQUIRED, example = "15823654487")
    @field:NotBlank(message = "手机号不能为空")
    @field:Length(min = 8, max = 11, message = "手机号码长度为 8-11 位")
    @field:Mobile
    var mobile: String? = null
    @field:Schema(description = "原手机验证码", example = "1024")
    @field:Length(min = 4, max = 6, message = "手机验证码长度为 4-6 位")
    @field:Pattern(regexp = "^[0-9]+$", message = "手机验证码必须都是数字")
    var oldCode: String? = null
}
