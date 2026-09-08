package im.hikaru.ruoyi.module.pay.api.transfer.dto

import im.hikaru.ruoyi.module.pay.enums.PayChannelEnum
import im.hikaru.ruoyi.module.pay.enums.transfer.PayTransferStatusEnum
import java.time.LocalDateTime

class PayTransferRespDTO {
    var id: Long? = null
    var no: String? = null
    var channelCode: String? = null
    var merchantTransferId: String? = null
    var price: Int? = null
    var status: Int? = null
    var successTime: LocalDateTime? = null
    var channelErrorCode: String? = null
    var channelErrorMsg: String? = null
    var channelPackageInfo: String? = null
    var channelMchId: String? = null
}
