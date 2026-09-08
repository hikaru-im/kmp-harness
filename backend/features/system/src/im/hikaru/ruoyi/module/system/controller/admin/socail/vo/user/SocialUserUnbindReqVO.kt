package im.hikaru.ruoyi.module.system.controller.admin.socail.vo.user

import im.hikaru.ruoyi.framework.common.validation.InEnum
import im.hikaru.ruoyi.module.system.enums.social.SocialTypeEnum
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

class SocialUserUnbindReqVO {
    @field:NotNull(message = "Social type must not be null")
    @field:InEnum(SocialTypeEnum::class)
    var type: Int? = null

    @field:NotBlank(message = "Openid must not be blank")
    var openid: String? = null
}
