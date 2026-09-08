package im.hikaru.ruoyi.module.pay.framework.pay.core.client.dto.order

import im.hikaru.ruoyi.module.pay.framework.pay.core.enums.PayOrderDisplayModeEnum
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import java.time.LocalDateTime
import org.hibernate.validator.constraints.Length
import org.hibernate.validator.constraints.URL

class PayOrderUnifiedReqDTO {
    @field:NotEmpty(message = "用户 IP 不能为空")
    var userIp: String? = null
    @field:NotEmpty(message = "外部订单编号不能为空")
    var outTradeNo: String? = null
    @field:NotEmpty(message = "商品标题不能为空")
    @field:Length(max = 32, message = "商品标题不能超过 32")
    var subject: String? = null
    @field:Length(max = 128, message = "商品描述信息长度不能超过128")
    var body: String? = null
    @field:NotEmpty(message = "支付结果的回调地址不能为空")
    @URL(message = "支付结果的 notify 回调地址必须是 URL 格式")
    var notifyUrl: String? = null
    @URL(message = "支付结果的 return 回调地址必须是 URL 格式")
    var returnUrl: String? = null
    @field:NotNull(message = "支付金额不能为空")
    @DecimalMin(value = "0", inclusive = false, message = "支付金额必须大于零")
    var price: Int? = null
    @field:NotNull(message = "支付过期时间不能为空")
    var expireTime: LocalDateTime? = null
    var channelExtras: Map<String, String>? = null
    var displayMode: String? = null
}
