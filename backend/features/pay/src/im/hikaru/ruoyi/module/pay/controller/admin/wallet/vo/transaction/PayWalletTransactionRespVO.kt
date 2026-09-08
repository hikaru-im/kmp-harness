package im.hikaru.ruoyi.module.pay.controller.admin.wallet.vo.transaction

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "用户 APP - 钱包流水分页 Response VO")
class PayWalletTransactionRespVO {
    @field:Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    var id: Long? = null
    @field:Schema(description = "钱包编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "5")
    var walletId: Long? = null
    @field:Schema(description = "业务分类", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    var bizType: Int? = null
    @field:Schema(description = "交易金额，单位分", requiredMode = Schema.RequiredMode.REQUIRED, example = "100")
    var price: Long? = null
    @field:Schema(description = "流水标题", requiredMode = Schema.RequiredMode.REQUIRED, example = "土豆土豆")
    var title: String? = null
    @field:Schema(description = "交易后的余额，单位分", requiredMode = Schema.RequiredMode.REQUIRED, example = "100")
    var balance: Long? = null
    @field:Schema(description = "交易时间", requiredMode = Schema.RequiredMode.REQUIRED)
    var createTime: LocalDateTime? = null
}
