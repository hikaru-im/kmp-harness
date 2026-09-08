package im.hikaru.ruoyi.module.pay.api.wallet.dto

import im.hikaru.ruoyi.framework.common.enums.UserTypeEnum
import jakarta.validation.constraints.NotNull

class PayWalletAddBalanceReqDTO {
    @field:NotNull(message = "用户编号不能为空")
    var userId: Long? = null
    @field:NotNull(message = "用户类型不能为空")
    var userType: Int? = null
    @field:NotNull(message = "关联业务分类不能为空")
    var bizType: Int? = null
    @field:NotNull(message = "关联业务编号不能为空")
    var bizId: String? = null
    @field:NotNull(message = "交易金额不能为空")
    var price: Int? = null
}
