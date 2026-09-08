package im.hikaru.ruoyi.module.pay.service.wallet

import im.hikaru.ruoyi.framework.common.pojo.*
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.pay.controller.admin.wallet.vo.wallet.PayWalletPageReqVO
import im.hikaru.ruoyi.module.pay.dal.dataobject.wallet.PayWalletDO
import im.hikaru.ruoyi.module.pay.dal.dataobject.wallet.PayWalletTransactionDO
import im.hikaru.ruoyi.module.pay.enums.wallet.PayWalletBizTypeEnum

interface PayWalletService {
    fun getOrCreateWallet(userId: Long, userType: Int): PayWalletDO
    fun getWallet(walletId: Long): PayWalletDO
    fun getWalletPage(pageReqVO: PayWalletPageReqVO): PageResult<PayWalletDO>
    fun orderPay(walletId: Long, outTradeNo: String, price: Int): PayWalletTransactionDO
    fun orderRefund(outRefundNo: String, refundPrice: Int, reason: String): PayWalletTransactionDO
    fun reduceWalletBalance(walletId: Long, bizId: Long, bizType: PayWalletBizTypeEnum, price: Int): PayWalletTransactionDO
    fun addWalletBalance(walletId: Long, bizId: String, bizType: PayWalletBizTypeEnum, price: Int): PayWalletTransactionDO
    fun freezePrice(id: Long, price: Int): Unit
    fun unfreezePrice(id: Long, price: Int): Unit
}
