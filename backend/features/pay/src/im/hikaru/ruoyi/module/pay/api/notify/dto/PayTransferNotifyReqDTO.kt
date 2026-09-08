package im.hikaru.ruoyi.module.pay.api.notify.dto

import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull

class PayTransferNotifyReqDTO {
    @field:NotEmpty(message = "商户转账单号不能为空")
    var merchantTransferId: String? = null
    @field:NotNull(message = "转账订单编号不能为空")
    var payTransferId: Long? = null
}
