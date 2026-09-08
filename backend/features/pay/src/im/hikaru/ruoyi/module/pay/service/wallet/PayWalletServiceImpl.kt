package im.hikaru.ruoyi.module.pay.service.wallet

import im.hikaru.ruoyi.framework.common.exception.util.ServiceExceptionUtil.exception
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.pay.controller.admin.wallet.vo.wallet.PayWalletPageReqVO
import im.hikaru.ruoyi.module.pay.dal.dataobject.wallet.PayWalletDO
import im.hikaru.ruoyi.module.pay.dal.dataobject.wallet.PayWalletTransactionDO
import im.hikaru.ruoyi.module.pay.dal.mysql.order.PayOrderExtensionDao
import im.hikaru.ruoyi.module.pay.dal.mysql.refund.PayRefundDao
import im.hikaru.ruoyi.module.pay.dal.mysql.wallet.PayWalletDao
import im.hikaru.ruoyi.module.pay.dal.redis.wallet.PayWalletLockRedisDAO
import im.hikaru.ruoyi.module.pay.enums.ErrorCodeConstants.PAY_ORDER_EXTENSION_NOT_FOUND
import im.hikaru.ruoyi.module.pay.enums.ErrorCodeConstants.REFUND_NOT_FOUND
import im.hikaru.ruoyi.module.pay.enums.ErrorCodeConstants.WALLET_BALANCE_NOT_ENOUGH
import im.hikaru.ruoyi.module.pay.enums.ErrorCodeConstants.WALLET_FREEZE_PRICE_NOT_ENOUGH
import im.hikaru.ruoyi.module.pay.enums.ErrorCodeConstants.WALLET_NOT_FOUND
import im.hikaru.ruoyi.module.pay.enums.ErrorCodeConstants.WALLET_REFUND_EXIST
import im.hikaru.ruoyi.module.pay.enums.ErrorCodeConstants.WALLET_TRANSACTION_NOT_FOUND
import im.hikaru.ruoyi.module.pay.enums.wallet.PayWalletBizTypeEnum
import im.hikaru.ruoyi.module.pay.service.wallet.bo.WalletTransactionCreateReqBO
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.validation.annotation.Validated
import java.time.Duration

@Service
@Validated
class PayWalletServiceImpl(
    private val lockRedisDAO: PayWalletLockRedisDAO,
    @param:Lazy private val transactionService: PayWalletTransactionService,
) : PayWalletService {

    override fun getOrCreateWallet(userId: Long, userType: Int): PayWalletDO {
        PayWalletDao.selectByUserIdAndType(userId, userType)?.let { return it }
        return lockRedisDAO.withLock(userId, UPDATE_TIMEOUT) {
            PayWalletDao.selectByUserIdAndType(userId, userType) ?: PayWalletDO().apply {
                this.userId = userId
                this.userType = userType
                balance = 0
                freezePrice = 0
                totalExpense = 0
                totalRecharge = 0
                PayWalletDao.insert(this)
            }
        }
    }

    override fun getWallet(walletId: Long): PayWalletDO =
        PayWalletDao.selectById(walletId) ?: throw exception(WALLET_NOT_FOUND)

    override fun getWalletPage(pageReqVO: PayWalletPageReqVO): PageResult<PayWalletDO> =
        PayWalletDao.selectPage(pageReqVO)

    @Transactional(rollbackFor = [Exception::class])
    override fun orderPay(walletId: Long, outTradeNo: String, price: Int): PayWalletTransactionDO {
        val extension = PayOrderExtensionDao.selectByNo(outTradeNo)
            ?: throw exception(PAY_ORDER_EXTENSION_NOT_FOUND)
        return reduceWalletBalance(walletId, requireNotNull(extension.orderId), PayWalletBizTypeEnum.PAYMENT, price)
    }

    @Transactional(rollbackFor = [Exception::class])
    override fun orderRefund(outRefundNo: String, refundPrice: Int, reason: String): PayWalletTransactionDO {
        val refund = PayRefundDao.selectByNo(outRefundNo) ?: throw exception(REFUND_NOT_FOUND)
        val payment = transactionService.getWalletTransactionByNo(
            refund.channelOrderNo ?: throw exception(WALLET_TRANSACTION_NOT_FOUND),
        ) ?: throw exception(WALLET_TRANSACTION_NOT_FOUND)
        val refundBizId = requireNotNull(refund.id).toString()
        if (transactionService.getWalletTransaction(refundBizId, PayWalletBizTypeEnum.PAYMENT_REFUND) != null) {
            throw exception(WALLET_REFUND_EXIST)
        }
        return addWalletBalance(
            requireNotNull(payment.walletId),
            refundBizId,
            PayWalletBizTypeEnum.PAYMENT_REFUND,
            refundPrice,
        )
    }

    @Transactional(rollbackFor = [Exception::class])
    override fun reduceWalletBalance(
        walletId: Long,
        bizId: Long,
        bizType: PayWalletBizTypeEnum,
        price: Int,
    ): PayWalletTransactionDO {
        require(price > 0)
        val bizIdText = bizId.toString()
        transactionService.getWalletTransaction(bizIdText, bizType)?.let { return it }
        return lockRedisDAO.withLock(walletId, UPDATE_TIMEOUT) {
            transactionService.getWalletTransaction(bizIdText, bizType)?.let { return@withLock it }
            getWallet(walletId)
            val updated = when (bizType) {
                PayWalletBizTypeEnum.PAYMENT -> PayWalletDao.updateWhenConsumption(walletId, price)
                PayWalletBizTypeEnum.RECHARGE_REFUND -> PayWalletDao.updateWhenRechargeRefund(walletId, price)
                PayWalletBizTypeEnum.UPDATE_BALANCE -> PayWalletDao.updateWhenSubtract(walletId, price)
                else -> throw IllegalArgumentException("Unsupported wallet debit type: $bizType")
            }
            if (updated == 0) throw exception(WALLET_BALANCE_NOT_ENOUGH)
            val currentBalance = requireNotNull(PayWalletDao.selectById(walletId)?.balance)
            createTransaction(walletId, bizIdText, bizType, -price, currentBalance)
        }
    }

    @Transactional(rollbackFor = [Exception::class])
    override fun addWalletBalance(
        walletId: Long,
        bizId: String,
        bizType: PayWalletBizTypeEnum,
        price: Int,
    ): PayWalletTransactionDO {
        require(price > 0)
        transactionService.getWalletTransaction(bizId, bizType)?.let { return it }
        return lockRedisDAO.withLock(walletId, UPDATE_TIMEOUT) {
            transactionService.getWalletTransaction(bizId, bizType)?.let { return@withLock it }
            getWallet(walletId)
            val updated = when (bizType) {
                PayWalletBizTypeEnum.PAYMENT_REFUND -> PayWalletDao.updateWhenConsumptionRefund(walletId, price)
                PayWalletBizTypeEnum.RECHARGE -> PayWalletDao.updateWhenRecharge(walletId, price)
                PayWalletBizTypeEnum.UPDATE_BALANCE,
                PayWalletBizTypeEnum.TRANSFER,
                -> PayWalletDao.updateWhenAdd(walletId, price)
                else -> throw IllegalArgumentException("Unsupported wallet credit type: $bizType")
            }
            if (updated == 0) {
                log.error("[addWalletBalance][wallet({}) disappeared during update]", walletId)
                throw exception(WALLET_NOT_FOUND)
            }
            val currentBalance = requireNotNull(PayWalletDao.selectById(walletId)?.balance)
            createTransaction(walletId, bizId, bizType, price, currentBalance)
        }
    }

    override fun freezePrice(id: Long, price: Int) {
        require(price > 0)
        if (PayWalletDao.freezePrice(id, price) == 0) throw exception(WALLET_BALANCE_NOT_ENOUGH)
    }

    override fun unfreezePrice(id: Long, price: Int) {
        require(price > 0)
        if (PayWalletDao.unfreezePrice(id, price) == 0) throw exception(WALLET_FREEZE_PRICE_NOT_ENOUGH)
    }

    private fun createTransaction(
        walletId: Long,
        bizId: String,
        bizType: PayWalletBizTypeEnum,
        price: Int,
        balance: Int,
    ): PayWalletTransactionDO = transactionService.createWalletTransaction(WalletTransactionCreateReqBO().apply {
        this.walletId = walletId
        this.price = price
        this.balance = balance
        this.bizType = bizType.type
        this.bizId = bizId
        title = bizType.description
    })

    companion object {
        private val UPDATE_TIMEOUT: Duration = Duration.ofSeconds(120)
        private val log = LoggerFactory.getLogger(PayWalletServiceImpl::class.java)
    }
}
