package im.hikaru.ruoyi.module.pay.controller.app.wallet.vo.transaction

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "用户 APP - 钱包流水统计 Request VO")
class AppPayWalletTransactionSummaryRespVO {
    @field:Schema(description = "累计支出，单位分", requiredMode = Schema.RequiredMode.REQUIRED, example = "1000")
    var totalExpense: Int? = null
    @field:Schema(description = "累计收入，单位分", requiredMode = Schema.RequiredMode.REQUIRED, example = "2000")
    var totalIncome: Int? = null
}
