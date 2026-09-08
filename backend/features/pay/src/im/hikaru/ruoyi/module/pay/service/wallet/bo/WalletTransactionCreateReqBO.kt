package im.hikaru.ruoyi.module.pay.service.wallet.bo

import im.hikaru.ruoyi.framework.common.validation.InEnum
import im.hikaru.ruoyi.module.pay.enums.wallet.PayWalletBizTypeEnum
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull

class WalletTransactionCreateReqBO {
    @field:NotNull(message = "钱包编号不能为空")
    var walletId: Long? = null
    @field:NotNull(message = "交易金额不能为空")
    var price: Int? = null
    @field:NotNull(message = "交易后余额不能为空")
    var balance: Int? = null
    @field:NotNull(message = "关联业务分类不能为空")
    @field:InEnum(PayWalletBizTypeEnum::class)
    var bizType: Int? = null
    @field:NotEmpty(message = "关联业务编号不能为空")
    var bizId: String? = null
    @field:NotEmpty(message = "流水说明不能为空")
    var title: String? = null
}
