package im.hikaru.ruoyi.module.pay.api.order.dto

import im.hikaru.ruoyi.module.pay.enums.order.PayOrderStatusEnum
import java.time.LocalDateTime

class PayOrderRespDTO {
    var id: Long? = null
    var channelCode: String? = null
    var merchantOrderId: String? = null
    var subject: String? = null
    var price: Int? = null
    var status: Int? = null
    var successTime: LocalDateTime? = null
    var channelUserId: String? = null
    var channelOrderNo: String? = null
}
