package im.hikaru.ruoyi.module.pay.framework.pay.config

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotEmpty
import org.hibernate.validator.constraints.URL
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import org.springframework.validation.annotation.Validated

/** Runtime settings used by the payment services and callback URLs. */
@Component
@ConfigurationProperties(prefix = "yudao.pay")
@Validated
class PayProperties {
    @field:NotEmpty
    var orderNoPrefix: String = "P"
    @field:NotEmpty
    var refundNoPrefix: String = "R"
    @field:NotEmpty
    var transferNoPrefix: String = "T"
    @field:NotEmpty
    var transactionNoPrefix: String = "W"
    @field:NotEmpty
    var walletPayAppKey: String = "wallet"
    @field:NotEmpty
    @field:URL
    var orderNotifyUrl: String = "http://127.0.0.1:48080/admin-api/pay/notify/order"
    @field:NotEmpty
    @field:URL
    var refundNotifyUrl: String = "http://127.0.0.1:48080/admin-api/pay/notify/refund"
    @field:NotEmpty
    @field:URL
    var transferNotifyUrl: String = "http://127.0.0.1:48080/admin-api/pay/notify/transfer"
    @field:Min(1)
    var maxNotifyTimes: Int = 9
}
