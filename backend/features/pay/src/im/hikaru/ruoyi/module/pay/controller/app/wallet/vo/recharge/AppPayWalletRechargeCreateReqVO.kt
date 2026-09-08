package im.hikaru.ruoyi.module.pay.controller.app.wallet.vo.recharge

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.Min

@Schema(description = "用户 APP - 创建钱包充值 Request VO")
class AppPayWalletRechargeCreateReqVO {
    @field:Schema(description = "支付金额", example = "1000")
    @field:Min(value = 1, message = "支付金额必须大于零")
    var payPrice: Int? = null
    @field:Schema(description = "充值套餐编号", example = "1024")
    var packageId: Long? = null
}
