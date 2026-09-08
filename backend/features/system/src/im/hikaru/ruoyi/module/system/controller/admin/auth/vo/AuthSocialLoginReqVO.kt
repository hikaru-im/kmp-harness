package im.hikaru.ruoyi.module.system.controller.admin.auth.vo

import im.hikaru.ruoyi.framework.common.validation.InEnum
import im.hikaru.ruoyi.module.system.enums.social.SocialTypeEnum
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull

class AuthSocialLoginReqVO {
    @field:NotNull
    @field:InEnum(SocialTypeEnum::class)
    var type: Int? = null

    @field:NotEmpty
    var code: String? = null

    @field:NotEmpty
    var state: String? = null
}
