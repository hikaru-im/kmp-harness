package im.hikaru.ruoyi.module.system.api.social.dto

import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull

class SocialUserBindReqDTO {
    @field:NotNull(message = "User id must not be null")
    var userId: Long? = null

    @field:NotNull(message = "User type must not be null")
    var userType: Int? = null

    @field:NotNull(message = "Social type must not be null")
    var socialType: Int? = null

    @field:NotEmpty(message = "Authorization code must not be empty")
    var code: String? = null

    @field:NotNull(message = "State must not be null")
    var state: String? = null
}
