package im.hikaru.ruoyi.module.pay.api.notify.dto

import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull

class PayOrderNotifyReqDTO {
    @field:NotEmpty(message = "商户订单号不能为空")
    var merchantOrderId: String? = null
    @field:NotNull(message = "支付订单编号不能为空")
    var payOrderId: Long? = null
}
