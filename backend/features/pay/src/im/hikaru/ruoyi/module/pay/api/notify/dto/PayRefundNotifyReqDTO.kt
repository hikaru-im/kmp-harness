package im.hikaru.ruoyi.module.pay.api.notify.dto

import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull

class PayRefundNotifyReqDTO {
    @field:NotEmpty(message = "商户退款单编号不能为空")
    var merchantOrderId: String? = null
    @field:NotEmpty(message = "商户退款编号不能为空")
    var merchantRefundId: String? = null
    @field:NotNull(message = "支付退款编号不能为空")
    var payRefundId: Long? = null
}
