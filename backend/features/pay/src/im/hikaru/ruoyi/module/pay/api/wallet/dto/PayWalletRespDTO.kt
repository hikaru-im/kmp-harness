package im.hikaru.ruoyi.module.pay.api.wallet.dto

import im.hikaru.ruoyi.framework.common.enums.UserTypeEnum

class PayWalletRespDTO {
    var id: Long? = null
    var userId: Long? = null
    var userType: Int? = null
    var balance: Int? = null
    var freezePrice: Int? = null
    var totalExpense: Int? = null
    var totalRecharge: Int? = null
}
