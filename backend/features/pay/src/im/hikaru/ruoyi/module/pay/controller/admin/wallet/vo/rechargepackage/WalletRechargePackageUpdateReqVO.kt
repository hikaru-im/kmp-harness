package im.hikaru.ruoyi.module.pay.controller.admin.wallet.vo.rechargepackage

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull

@Schema(description = "管理后台 - 充值套餐更新 Request VO")
class WalletRechargePackageUpdateReqVO : WalletRechargePackageBaseVO() {
    @field:Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "9032")
    @field:NotNull(message = "编号不能为空")
    var id: Long? = null
}
