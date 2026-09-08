package im.hikaru.ruoyi.module.pay.controller.app.wallet.vo.recharge

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "用户 APP - 用户充值套餐 Response VO")
class AppPayWalletPackageRespVO {
    @field:Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    var id: Long? = null
    @field:Schema(description = "套餐名", requiredMode = Schema.RequiredMode.REQUIRED, example = "小套餐")
    var name: String? = null
    @field:Schema(description = "支付金额", requiredMode = Schema.RequiredMode.REQUIRED, example = "10")
    var payPrice: Int? = null
    @field:Schema(description = "赠送金额", requiredMode = Schema.RequiredMode.REQUIRED, example = "20")
    var bonusPrice: Int? = null
}
