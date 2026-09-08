package im.hikaru.ruoyi.module.pay.controller.admin.wallet.vo.wallet

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "管理后台 - 用户钱包 Response VO")
class PayWalletRespVO : PayWalletBaseVO() {
    @field:Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "29528")
    var id: Long? = null
    @field:Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    var createTime: LocalDateTime? = null
}
