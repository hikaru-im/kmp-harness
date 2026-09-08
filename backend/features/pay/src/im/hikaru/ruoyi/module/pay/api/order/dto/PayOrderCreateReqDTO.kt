package im.hikaru.ruoyi.module.pay.api.order.dto

import im.hikaru.ruoyi.framework.common.enums.UserTypeEnum
import im.hikaru.ruoyi.framework.common.validation.InEnum
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import java.io.Serializable
import java.time.LocalDateTime
import org.hibernate.validator.constraints.Length

private const val SUBJECT_MAX_LENGTH = 32

class PayOrderCreateReqDTO {
    @field:NotNull(message = "应用标识不能为空")
    var appKey: String? = null
    @field:NotEmpty(message = "用户 IP 不能为空")
    var userIp: String? = null
    var userId: Long? = null
    @field:InEnum(UserTypeEnum::class)
    var userType: Int? = null
    @field:NotEmpty(message = "商户订单编号不能为空")
    var merchantOrderId: String? = null
    @field:NotEmpty(message = "商品标题不能为空")
    @field:Length(max = SUBJECT_MAX_LENGTH, message = "商品标题不能超过 32")
    var subject: String? = null
    @field:Length(max = 128, message = "商品描述信息长度不能超过128")
    var body: String? = null
    @field:NotNull(message = "支付金额不能为空")
    @DecimalMin(value = "0", inclusive = false, message = "支付金额必须大于零")
    var price: Int? = null
    @field:NotNull(message = "支付过期时间不能为空")
    var expireTime: LocalDateTime? = null
}
