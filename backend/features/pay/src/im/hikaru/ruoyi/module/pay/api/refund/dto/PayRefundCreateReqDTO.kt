package im.hikaru.ruoyi.module.pay.api.refund.dto

import im.hikaru.ruoyi.framework.common.enums.UserTypeEnum
import im.hikaru.ruoyi.framework.common.validation.InEnum
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import org.hibernate.validator.constraints.Length

class PayRefundCreateReqDTO {
    @field:NotNull(message = "应用标识不能为空")
    var appKey: String? = null
    @field:NotEmpty(message = "用户 IP 不能为空")
    var userIp: String? = null
    var userId: Long? = null
    @field:InEnum(UserTypeEnum::class)
    var userType: Int? = null
    @field:NotEmpty(message = "商户订单编号不能为空")
    var merchantOrderId: String? = null
    @field:NotEmpty(message = "商户退款编号不能为空")
    var merchantRefundId: String? = null
    @field:NotEmpty(message = "退款描述不能为空")
    @field:Length(max = 128, message = "退款描述长度不能超过 128")
    var reason: String? = null
    @field:NotNull(message = "退款金额不能为空")
    @field:Min(value = 1, message = "退款金额必须大于零")
    var price: Int? = null
}
