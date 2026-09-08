package im.hikaru.ruoyi.module.pay.controller.admin.wallet.vo.rechargepackage

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "管理后台 - 充值套餐 Response VO")
class WalletRechargePackageRespVO : WalletRechargePackageBaseVO() {
    @field:Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "9032")
    var id: Long? = null
    @field:Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    var createTime: LocalDateTime? = null
}
