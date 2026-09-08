package im.hikaru.ruoyi.module.system.api.notify.dto

import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull

class NotifySendSingleToUserReqDTO {
    @field:NotNull(message = "User id must not be null")
    var userId: Long? = null

    @field:NotEmpty(message = "Notify template code must not be empty")
    var templateCode: String? = null
    var templateParams: Map<String, Any?>? = null
}
