package im.hikaru.ruoyi.module.pay.controller.admin.wallet.vo.wallet

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull

@Schema(description = "管理后台 - 用户钱包明细 Request VO")
class PayWalletUserReqVO {
    @field:Schema(description = "用户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @field:NotNull(message = "用户编号不能为空")
    var userId: Long? = null
}
