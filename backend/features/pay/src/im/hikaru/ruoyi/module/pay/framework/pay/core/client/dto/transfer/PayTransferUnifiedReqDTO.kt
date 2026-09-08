package im.hikaru.ruoyi.module.pay.framework.pay.core.client.dto.transfer

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import org.hibernate.validator.constraints.Length
import org.hibernate.validator.constraints.URL

class PayTransferUnifiedReqDTO {
    @field:NotEmpty(message = "用户 IP 不能为空")
    var userIp: String? = null
    @field:NotEmpty(message = "外部转账单编号不能为空")
    var outTransferNo: String? = null
    @field:NotNull(message = "转账金额不能为空")
    @field:Min(value = 1, message = "转账金额必须大于零")
    var price: Int? = null
    @field:NotEmpty(message = "转账标题不能为空")
    @field:Length(max = 128, message = "转账标题不能超过 128")
    var subject: String? = null
    @field:NotEmpty(message = "收款人账号不能为空")
    var userAccount: String? = null
    var userName: String? = null
    var channelExtras: Map<String, String>? = null
    @field:NotEmpty(message = "转账结果的回调地址不能为空")
    @URL(message = "转账结果的 notify 回调地址必须是 URL 格式")
    var notifyUrl: String? = null
}
