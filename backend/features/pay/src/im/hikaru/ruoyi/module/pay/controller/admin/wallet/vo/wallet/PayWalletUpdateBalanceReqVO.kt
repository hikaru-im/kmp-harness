package im.hikaru.ruoyi.module.pay.controller.admin.wallet.vo.wallet

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull

@Schema(description = "管理后台 - 修改钱包余额 Request VO")
class PayWalletUpdateBalanceReqVO {
    @field:Schema(description = "用户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "23788")
    @field:NotNull(message = "用户编号不能为空")
    var userId: Long? = null
    @field:Schema(description = "变动余额，正数为增加，负数为减少", requiredMode = Schema.RequiredMode.REQUIRED, example = "100")
    @field:NotNull(message = "变动余额不能为空")
    var balance: Int? = null
}
