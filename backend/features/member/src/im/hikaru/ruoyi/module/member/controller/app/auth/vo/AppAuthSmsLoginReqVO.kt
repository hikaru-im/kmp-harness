package im.hikaru.ruoyi.module.member.controller.app.auth.vo

import com.fasterxml.jackson.annotation.JsonIgnore
import im.hikaru.ruoyi.framework.common.validation.InEnum
import im.hikaru.ruoyi.framework.common.validation.Mobile
import im.hikaru.ruoyi.module.system.enums.social.SocialTypeEnum
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Pattern
import org.hibernate.validator.constraints.Length

@Schema(description = "用户 APP - 手机 + 验证码登录 Request VO,如果登录并绑定社交用户，需要传递 social 开头的参数")
class AppAuthSmsLoginReqVO {
    @field:Schema(description = "手机号", requiredMode = Schema.RequiredMode.REQUIRED, example = "15601691300")
    @field:NotEmpty(message = "手机号不能为空")
    @field:Mobile
    var mobile: String? = null
    @field:Schema(description = "手机验证码", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @field:NotEmpty(message = "手机验证码不能为空")
    @field:Length(min = 4, max = 6, message = "手机验证码长度为 4-6 位")
    @field:Pattern(regexp = "^[0-9]+$", message = "手机验证码必须都是数字")
    var code: String? = null
    @field:Schema(description = "社交平台的类型，参见 SocialTypeEnum 枚举值", requiredMode = Schema.RequiredMode.REQUIRED, example = "10")
    @field:InEnum(SocialTypeEnum::class)
    var socialType: Int? = null
    @field:Schema(description = "授权码", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    var socialCode: String? = null
    @field:Schema(description = "state", requiredMode = Schema.RequiredMode.REQUIRED, example = "9b2ffbc1-7425-4155-9894-9d5c08541d62")
    var socialState: String? = null

    @get:AssertTrue(message = "授权码不能为空")
    @get:JsonIgnore
    val isSocialCodeValid: Boolean
        get() = socialType == null || !socialCode.isNullOrEmpty()

    @get:AssertTrue(message = "授权 state 不能为空")
    @get:JsonIgnore
    val isSocialState: Boolean
        get() = socialType == null || !socialState.isNullOrEmpty()
}
