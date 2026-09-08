package im.hikaru.ruoyi.module.pay.api.wallet

import im.hikaru.ruoyi.module.pay.api.wallet.dto.PayWalletAddBalanceReqDTO
import im.hikaru.ruoyi.module.pay.api.wallet.dto.PayWalletRespDTO

interface PayWalletApi {
    fun addWalletBalance(reqDTO: PayWalletAddBalanceReqDTO): Unit
    fun getOrCreateWallet(userId: Long, userType: Int): PayWalletRespDTO
}
