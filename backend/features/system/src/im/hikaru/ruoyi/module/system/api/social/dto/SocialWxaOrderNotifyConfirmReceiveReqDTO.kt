package im.hikaru.ruoyi.module.system.api.social.dto

import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import java.time.LocalDateTime

class SocialWxaOrderNotifyConfirmReceiveReqDTO {
    @field:NotEmpty(message = "Transaction id must not be empty")
    var transactionId: String? = null

    @field:NotNull(message = "Received time must not be null")
    var receivedTime: LocalDateTime? = null
}
