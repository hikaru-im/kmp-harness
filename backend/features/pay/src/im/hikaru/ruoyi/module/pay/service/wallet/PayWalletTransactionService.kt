package im.hikaru.ruoyi.module.pay.service.wallet

import im.hikaru.ruoyi.framework.common.pojo.*
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.pay.controller.admin.wallet.vo.transaction.PayWalletTransactionPageReqVO
import im.hikaru.ruoyi.module.pay.controller.app.wallet.vo.transaction.AppPayWalletTransactionPageReqVO
import im.hikaru.ruoyi.module.pay.controller.app.wallet.vo.transaction.AppPayWalletTransactionSummaryRespVO
import im.hikaru.ruoyi.module.pay.dal.dataobject.wallet.PayWalletTransactionDO
import im.hikaru.ruoyi.module.pay.enums.wallet.PayWalletBizTypeEnum
import im.hikaru.ruoyi.module.pay.service.wallet.bo.WalletTransactionCreateReqBO
import jakarta.validation.Valid
import java.time.LocalDateTime

interface PayWalletTransactionService {
    fun getWalletTransactionPage(userId: Long, userType: Int, pageVO: AppPayWalletTransactionPageReqVO): PageResult<PayWalletTransactionDO>
    fun getWalletTransactionPage(pageVO: PayWalletTransactionPageReqVO): PageResult<PayWalletTransactionDO>
    fun createWalletTransaction(@Valid bo: WalletTransactionCreateReqBO): PayWalletTransactionDO
    fun getWalletTransactionByNo(no: String): PayWalletTransactionDO?
    fun getWalletTransaction(bizId: String, type: PayWalletBizTypeEnum): PayWalletTransactionDO?
    fun getWalletTransactionSummary(userId: Long, userType: Int, createTime: Array<LocalDateTime>): AppPayWalletTransactionSummaryRespVO
}
