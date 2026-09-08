package im.hikaru.ruoyi.module.member.controller.app.auth.vo

import im.hikaru.ruoyi.framework.common.validation.InEnum
import im.hikaru.ruoyi.module.system.enums.social.SocialTypeEnum
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull

@Schema(description = "用户 APP - 社交快捷登录 Request VO，使用 code 授权码")
class AppAuthSocialLoginReqVO {
    @field:Schema(description = "社交平台的类型，参见 SocialTypeEnum 枚举值", requiredMode = Schema.RequiredMode.REQUIRED, example = "10")
    @field:InEnum(SocialTypeEnum::class)
    @field:NotNull(message = "社交平台的类型不能为空")
    var type: Int? = null
    @field:Schema(description = "授权码", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @field:NotEmpty(message = "授权码不能为空")
    var code: String? = null
    @field:Schema(description = "state", requiredMode = Schema.RequiredMode.REQUIRED, example = "9b2ffbc1-7425-4155-9894-9d5c08541d62")
    @field:NotEmpty(message = "state 不能为空")
    var state: String? = null
}
