package im.hikaru.ruoyi.module.system.api.social.dto

import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull

class SocialUserUnbindReqDTO {
    @field:NotNull(message = "User id must not be null")
    var userId: Long? = null

    @field:NotNull(message = "User type must not be null")
    var userType: Int? = null

    @field:NotNull(message = "Social type must not be null")
    var socialType: Int? = null

    @field:NotEmpty(message = "Social openid must not be empty")
    var openid: String? = null
}
