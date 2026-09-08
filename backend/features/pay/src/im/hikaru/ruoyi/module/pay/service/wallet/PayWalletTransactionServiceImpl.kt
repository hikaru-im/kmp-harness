package im.hikaru.ruoyi.module.pay.service.wallet

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.pay.controller.admin.wallet.vo.transaction.PayWalletTransactionPageReqVO
import im.hikaru.ruoyi.module.pay.controller.app.wallet.vo.transaction.*
import im.hikaru.ruoyi.module.pay.dal.dataobject.wallet.PayWalletTransactionDO
import im.hikaru.ruoyi.module.pay.dal.mysql.wallet.PayWalletDao
import im.hikaru.ruoyi.module.pay.dal.mysql.wallet.PayWalletTransactionDao
import im.hikaru.ruoyi.module.pay.enums.wallet.PayWalletBizTypeEnum
import im.hikaru.ruoyi.module.pay.service.wallet.bo.WalletTransactionCreateReqBO
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import org.springframework.validation.annotation.Validated
import java.time.LocalDateTime

@Service
@Validated
class PayWalletTransactionServiceImpl(
    @param:Lazy private val walletService: PayWalletService,
) : PayWalletTransactionService {
    override fun getWalletTransactionPage(userId: Long, userType: Int, pageVO: AppPayWalletTransactionPageReqVO): PageResult<PayWalletTransactionDO> {
        val wallet = walletService.getOrCreateWallet(userId, userType); return PayWalletTransactionDao.selectPage(null, null, requireNotNull(wallet.id), pageVO.type, pageVO.createTime, pageVO.pageNo, pageVO.pageSize)
    }
    override fun getWalletTransactionPage(pageVO: PayWalletTransactionPageReqVO): PageResult<PayWalletTransactionDO> {
        val walletId = pageVO.walletId ?: if (pageVO.userId != null && pageVO.userType != null) requireNotNull(walletService.getOrCreateWallet(pageVO.userId!!, pageVO.userType!!).id) else null
        return PayWalletTransactionDao.selectPage(pageVO.userId, pageVO.userType, walletId, null, null, pageVO.pageNo, pageVO.pageSize)
    }
    override fun createWalletTransaction(bo: WalletTransactionCreateReqBO): PayWalletTransactionDO {
        val existing = PayWalletTransactionDao.selectByBizIdAndType(requireNotNull(bo.bizId), requireNotNull(bo.bizType)); if (existing != null) return existing
        return PayWalletTransactionDO().apply { no = "W" + java.util.UUID.randomUUID().toString().replace("-", "").take(24); walletId = bo.walletId; price = bo.price; balance = bo.balance; bizType = bo.bizType; bizId = bo.bizId; title = bo.title }.also { PayWalletTransactionDao.insert(it) }
    }
    override fun getWalletTransactionByNo(no: String): PayWalletTransactionDO? = PayWalletTransactionDao.selectByNo(no)
    override fun getWalletTransaction(bizId: String, type: PayWalletBizTypeEnum): PayWalletTransactionDO? = PayWalletTransactionDao.selectByBizIdAndType(bizId, type.type)
    override fun getWalletTransactionSummary(userId: Long, userType: Int, createTime: Array<LocalDateTime>): AppPayWalletTransactionSummaryRespVO {
        val walletId = requireNotNull(walletService.getOrCreateWallet(userId, userType).id)
        val list = PayWalletTransactionDao.selectList(walletId, createTime)
        return AppPayWalletTransactionSummaryRespVO().apply {
            totalExpense = list.filter { (it.price ?: 0) < 0 }.sumOf { -(it.price ?: 0) }
            totalIncome = list.filter { (it.price ?: 0) > 0 }.sumOf { it.price ?: 0 }
        }
    }
}
