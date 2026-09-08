package im.hikaru.ruoyi.module.pay.controller.admin.wallet.vo.rechargepackage

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull

open class WalletRechargePackageBaseVO {
    @field:Schema(description = "套餐名", requiredMode = Schema.RequiredMode.REQUIRED, example = "李四")
    @field:NotNull(message = "套餐名不能为空")
    var name: String? = null
    @field:Schema(description = "支付金额", requiredMode = Schema.RequiredMode.REQUIRED, example = "16454")
    @field:NotNull(message = "支付金额不能为空")
    var payPrice: Int? = null
    @field:Schema(description = "赠送金额", requiredMode = Schema.RequiredMode.REQUIRED, example = "20887")
    @field:NotNull(message = "赠送金额不能为空")
    var bonusPrice: Int? = null
    @field:Schema(description = "状态", requiredMode = Schema.RequiredMode.REQUIRED, example = "2")
    @field:NotNull(message = "状态不能为空")
    var status: Byte? = null
}
