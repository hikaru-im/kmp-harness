package im.hikaru.ruoyi.module.pay.controller.app.wallet.vo.recharge

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "用户 APP - 钱包充值记录 Resp VO")
class AppPayWalletRechargeRespVO {
    @field:Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    var id: Long? = null
    @field:Schema(description = "用户实际到账余额", requiredMode = Schema.RequiredMode.REQUIRED, example = "100")
    var totalPrice: Int? = null
    @field:Schema(description = "实际支付金额", requiredMode = Schema.RequiredMode.REQUIRED, example = "20")
    var payPrice: Int? = null
    @field:Schema(description = "钱包赠送金额", requiredMode = Schema.RequiredMode.REQUIRED, example = "80")
    var bonusPrice: Int? = null
    @field:Schema(description = "支付成功的支付渠道", requiredMode = Schema.RequiredMode.REQUIRED)
    var payChannelCode: String? = null
    @field:Schema(description = "支付渠道名", example = "微信小程序支付")
    var payChannelName: String? = null
    @field:Schema(description = "支付订单编号", requiredMode = Schema.RequiredMode.REQUIRED)
    var payOrderId: Long? = null
    @field:Schema(description = "支付成功的外部订单号", requiredMode = Schema.RequiredMode.REQUIRED)
    var payOrderChannelOrderNo: String? = null
    @field:Schema(description = "订单支付时间", requiredMode = Schema.RequiredMode.REQUIRED)
    var payTime: LocalDateTime? = null
    @field:Schema(description = "退款状态", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    var refundStatus: Int? = null
}
