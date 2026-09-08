package im.hikaru.ruoyi.module.pay.framework.pay.core.client.dto.refund

import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import org.hibernate.validator.constraints.URL

class PayRefundUnifiedReqDTO {
    @field:NotEmpty(message = "外部订单编号不能为空")
    var outTradeNo: String? = null
    @field:NotEmpty(message = "退款请求单号不能为空")
    var outRefundNo: String? = null
    @field:NotEmpty(message = "退款原因不能为空")
    var reason: String? = null
    @field:NotNull(message = "支付金额不能为空")
    @DecimalMin(value = "0", inclusive = false, message = "支付金额必须大于零")
    var payPrice: Int? = null
    @field:NotNull(message = "退款金额不能为空")
    @DecimalMin(value = "0", inclusive = false, message = "支付金额必须大于零")
    var refundPrice: Int? = null
    @field:NotEmpty(message = "支付结果的回调地址不能为空")
    @URL(message = "支付结果的 notify 回调地址必须是 URL 格式")
    var notifyUrl: String? = null
}
