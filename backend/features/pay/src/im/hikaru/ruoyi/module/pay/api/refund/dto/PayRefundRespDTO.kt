package im.hikaru.ruoyi.module.pay.api.refund.dto

import im.hikaru.ruoyi.module.pay.enums.refund.PayRefundStatusEnum
import java.time.LocalDateTime

class PayRefundRespDTO {
    var id: Long? = null
    var channelCode: String? = null
    var status: Int? = null
    var refundPrice: Int? = null
    var merchantOrderId: String? = null
    var merchantRefundId: String? = null
    var successTime: LocalDateTime? = null
    var channelErrorCode: String? = null
    var channelErrorMsg: String? = null
}
