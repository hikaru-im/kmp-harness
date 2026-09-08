package im.hikaru.ruoyi.module.system.controller.admin.socail.vo.user

import im.hikaru.ruoyi.framework.common.validation.InEnum
import im.hikaru.ruoyi.module.system.enums.social.SocialTypeEnum
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

class SocialUserBindReqVO {
    @field:NotNull(message = "Social type must not be null")
    @field:InEnum(SocialTypeEnum::class)
    var type: Int? = null

    @field:NotBlank(message = "Authorization code must not be blank")
    var code: String? = null

    @field:NotBlank(message = "State must not be blank")
    var state: String? = null
}
