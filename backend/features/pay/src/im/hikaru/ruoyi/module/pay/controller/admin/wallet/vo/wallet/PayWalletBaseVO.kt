package im.hikaru.ruoyi.module.pay.controller.admin.wallet.vo.wallet

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull

open class PayWalletBaseVO {
    @field:Schema(description = "用户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "20020")
    @field:NotNull(message = "用户编号不能为空")
    var userId: Long? = null
    @field:Schema(description = "用户类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @field:NotNull(message = "用户类型不能为空")
    var userType: Int? = null
    @field:Schema(description = "余额，单位分", requiredMode = Schema.RequiredMode.REQUIRED)
    @field:NotNull(message = "余额，单位分不能为空")
    var balance: Int? = null
    @field:Schema(description = "累计支出，单位分", requiredMode = Schema.RequiredMode.REQUIRED)
    @field:NotNull(message = "累计支出，单位分不能为空")
    var totalExpense: Int? = null
    @field:Schema(description = "累计充值，单位分", requiredMode = Schema.RequiredMode.REQUIRED)
    @field:NotNull(message = "累计充值，单位分不能为空")
    var totalRecharge: Int? = null
    @field:Schema(description = "冻结金额，单位分", requiredMode = Schema.RequiredMode.REQUIRED, example = "20737")
    @field:NotNull(message = "冻结金额，单位分不能为空")
    var freezePrice: Int? = null
}
