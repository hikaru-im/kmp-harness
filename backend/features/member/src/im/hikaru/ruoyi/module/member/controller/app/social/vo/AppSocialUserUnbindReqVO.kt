package im.hikaru.ruoyi.module.member.controller.app.social.vo

import im.hikaru.ruoyi.framework.common.validation.InEnum
import im.hikaru.ruoyi.module.system.enums.social.SocialTypeEnum
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull

@Schema(description = "用户 APP - 取消社交绑定 Request VO")
class AppSocialUserUnbindReqVO {
    @field:Schema(description = "社交平台的类型，参见 SocialTypeEnum 枚举值", requiredMode = Schema.RequiredMode.REQUIRED, example = "10")
    @field:InEnum(SocialTypeEnum::class)
    @field:NotNull(message = "社交平台的类型不能为空")
    var type: Int? = null
    @field:Schema(description = "社交用户的 openid", requiredMode = Schema.RequiredMode.REQUIRED, example = "IPRmJ0wvBptiPIlGEZiPewGwiEiE")
    @field:NotEmpty(message = "社交用户的 openid 不能为空")
    var openid: String? = null
}
