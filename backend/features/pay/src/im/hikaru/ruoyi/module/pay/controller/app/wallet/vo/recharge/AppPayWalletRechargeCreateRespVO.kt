package im.hikaru.ruoyi.module.pay.controller.app.wallet.vo.recharge

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "用户 APP - 创建钱包充值 Resp VO")
class AppPayWalletRechargeCreateRespVO {
    @field:Schema(description = "钱包充值编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    var id: Long? = null
    @field:Schema(description = "支付订单编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "100")
    var payOrderId: Long? = null
}
