package im.hikaru.ruoyi.module.pay.controller.app.wallet.vo.wallet

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "用户 APP - 用户钱包 Response VO")
class AppPayWalletRespVO {
    @field:Schema(description = "钱包余额，单位分", requiredMode = Schema.RequiredMode.REQUIRED, example = "100")
    var balance: Int? = null
    @field:Schema(description = "累计支出，单位分", requiredMode = Schema.RequiredMode.REQUIRED, example = "1000")
    var totalExpense: Int? = null
    @field:Schema(description = "累计充值，单位分", requiredMode = Schema.RequiredMode.REQUIRED, example = "2000")
    var totalRecharge: Int? = null
}
