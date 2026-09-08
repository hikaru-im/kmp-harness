package im.hikaru.ruoyi.module.system.api.message.user

import jakarta.validation.constraints.NotNull

class AdminUserProfileUpdateMessage {
    @field:NotNull(message = "User id must not be null")
    var userId: Long? = null
    var nickname: String? = null
    var avatar: String? = null
}
