package im.hikaru.ruoyi.module.pay.service.wallet

import im.hikaru.ruoyi.framework.common.exception.util.ServiceExceptionUtil.exception
import im.hikaru.ruoyi.framework.common.pojo.PageParam
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.pay.api.order.dto.PayOrderCreateReqDTO
import im.hikaru.ruoyi.module.pay.api.refund.dto.PayRefundCreateReqDTO
import im.hikaru.ruoyi.module.pay.controller.app.wallet.vo.recharge.AppPayWalletRechargeCreateReqVO
import im.hikaru.ruoyi.module.pay.convert.wallet.PayWalletRechargeConvert
import im.hikaru.ruoyi.module.pay.dal.dataobject.order.PayOrderDO
import im.hikaru.ruoyi.module.pay.dal.dataobject.refund.PayRefundDO
import im.hikaru.ruoyi.module.pay.dal.dataobject.wallet.PayWalletDO
import im.hikaru.ruoyi.module.pay.dal.dataobject.wallet.PayWalletRechargeDO
import im.hikaru.ruoyi.module.pay.dal.mysql.wallet.PayWalletRechargeDao
import im.hikaru.ruoyi.module.pay.enums.ErrorCodeConstants.WALLET_RECHARGE_NOT_FOUND
import im.hikaru.ruoyi.module.pay.enums.ErrorCodeConstants.WALLET_RECHARGE_REFUND_BALANCE_NOT_ENOUGH
import im.hikaru.ruoyi.module.pay.enums.ErrorCodeConstants.WALLET_RECHARGE_REFUND_FAIL_NOT_PAID
import im.hikaru.ruoyi.module.pay.enums.ErrorCodeConstants.WALLET_RECHARGE_REFUND_FAIL_REFUNDED
import im.hikaru.ruoyi.module.pay.enums.ErrorCodeConstants.WALLET_RECHARGE_REFUND_FAIL_REFUND_NOT_FOUND
import im.hikaru.ruoyi.module.pay.enums.ErrorCodeConstants.WALLET_RECHARGE_REFUND_FAIL_REFUND_ORDER_ID_ERROR
import im.hikaru.ruoyi.module.pay.enums.ErrorCodeConstants.WALLET_RECHARGE_REFUND_FAIL_REFUND_PRICE_NOT_MATCH
import im.hikaru.ruoyi.module.pay.enums.ErrorCodeConstants.WALLET_RECHARGE_UPDATE_PAID_PAY_ORDER_ID_ERROR
import im.hikaru.ruoyi.module.pay.enums.ErrorCodeConstants.WALLET_RECHARGE_UPDATE_PAID_PAY_ORDER_STATUS_NOT_SUCCESS
import im.hikaru.ruoyi.module.pay.enums.ErrorCodeConstants.WALLET_RECHARGE_UPDATE_PAID_PAY_PRICE_NOT_MATCH
import im.hikaru.ruoyi.module.pay.enums.ErrorCodeConstants.WALLET_RECHARGE_UPDATE_PAID_STATUS_NOT_UNPAID
import im.hikaru.ruoyi.module.pay.enums.order.PayOrderStatusEnum
import im.hikaru.ruoyi.module.pay.enums.refund.PayRefundStatusEnum
import im.hikaru.ruoyi.module.pay.enums.wallet.PayWalletBizTypeEnum
import im.hikaru.ruoyi.module.pay.framework.pay.config.PayProperties
import im.hikaru.ruoyi.module.pay.service.order.PayOrderService
import im.hikaru.ruoyi.module.pay.service.refund.PayRefundService
import java.time.LocalDateTime
import kotlinx.datetime.toKotlinLocalDateTime
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.validation.annotation.Validated

@Service
@Validated
class PayWalletRechargeServiceImpl(
    private val payWalletService: PayWalletService,
    private val payOrderService: PayOrderService,
    private val payRefundService: PayRefundService,
    private val rechargePackageService: PayWalletRechargePackageService,
    private val payProperties: PayProperties,
) : PayWalletRechargeService {

    @Transactional(rollbackFor = [Exception::class])
    override fun createWalletRecharge(
        userId: Long,
        userType: Int,
        userIp: String,
        createReqVO: AppPayWalletRechargeCreateReqVO,
    ): PayWalletRechargeDO {
        val rechargePackage = createReqVO.packageId?.let(rechargePackageService::validWalletRechargePackage)
        val payPrice = rechargePackage?.payPrice ?: requireNotNull(createReqVO.payPrice)
        val bonusPrice = rechargePackage?.bonusPrice ?: 0
        val wallet = payWalletService.getOrCreateWallet(userId, userType)
        val recharge = PayWalletRechargeConvert.create(
            requireNotNull(wallet.id),
            payPrice,
            bonusPrice,
            createReqVO.packageId,
        )
        PayWalletRechargeDao.insert(recharge)
        val payOrderId = payOrderService.createOrder(PayOrderCreateReqDTO().apply {
            appKey = payProperties.walletPayAppKey
            this.userIp = userIp
            this.userId = userId
            this.userType = userType
            merchantOrderId = requireNotNull(recharge.id).toString()
            subject = "Wallet recharge"
            body = ""
            price = payPrice
            expireTime = LocalDateTime.now().plusHours(2)
        })
        PayWalletRechargeDao.updateById(PayWalletRechargeDO().apply {
            id = recharge.id
            this.payOrderId = payOrderId
        })
        recharge.payOrderId = payOrderId
        return recharge
    }

    override fun getWalletRechargePackagePage(
        userId: Long,
        userType: Int,
        pageReqVO: PageParam,
        payStatus: Boolean,
    ): PageResult<PayWalletRechargeDO> {
        val wallet = payWalletService.getOrCreateWallet(userId, userType)
        return PayWalletRechargeDao.selectPage(requireNotNull(wallet.id), payStatus, pageReqVO)
    }

    @Transactional(rollbackFor = [Exception::class])
    override fun updateWalletRechargerPaid(id: Long, payOrderId: Long) {
        val recharge = getRecharge(id)
        if (recharge.payStatus == true) {
            if (recharge.payOrderId == payOrderId) return
            throw exception(WALLET_RECHARGE_UPDATE_PAID_PAY_ORDER_ID_ERROR)
        }
        val payOrder = validatePayOrderPaid(recharge, payOrderId)
        val updated = PayWalletRechargeDao.updateByIdAndPayStatus(id, false, PayWalletRechargeDO().apply {
            payStatus = true
            payTime = LocalDateTime.now().toKotlinLocalDateTime()
            payChannelCode = payOrder.channelCode
        })
        if (updated == 0) throw exception(WALLET_RECHARGE_UPDATE_PAID_STATUS_NOT_UNPAID)
        payWalletService.addWalletBalance(
            requireNotNull(recharge.walletId),
            id.toString(),
            PayWalletBizTypeEnum.RECHARGE,
            requireNotNull(recharge.totalPrice),
        )
    }

    @Transactional(rollbackFor = [Exception::class])
    override fun refundWalletRecharge(id: Long, userIp: String) {
        val recharge = getRecharge(id)
        val wallet = validateCanRefund(recharge)
        val totalPrice = requireNotNull(recharge.totalPrice)
        payWalletService.freezePrice(requireNotNull(wallet.id), totalPrice)
        val merchantRefundId = "$id-refund"
        val payRefundId = payRefundService.createRefund(PayRefundCreateReqDTO().apply {
            appKey = payProperties.walletPayAppKey
            this.userIp = userIp
            userId = wallet.userId
            userType = wallet.userType
            merchantOrderId = id.toString()
            this.merchantRefundId = merchantRefundId
            reason = "Wallet recharge refund"
            price = recharge.payPrice
        })
        PayWalletRechargeDao.updateById(PayWalletRechargeDO().apply {
            this.id = id
            this.payRefundId = payRefundId
            refundStatus = PayRefundStatusEnum.WAITING.status
        })
    }

    @Transactional(rollbackFor = [Exception::class])
    override fun updateWalletRechargeRefunded(id: Long, refundId: String, payRefundId: Long) {
        val recharge = getRecharge(id)
        if (recharge.payRefundId != payRefundId) {
            throw exception(WALLET_RECHARGE_REFUND_FAIL_REFUND_ORDER_ID_ERROR)
        }
        val payRefund = validateRefund(recharge, refundId, payRefundId)
        val update = PayWalletRechargeDO()
        when {
            PayRefundStatusEnum.isSuccess(payRefund.status) -> {
                payWalletService.reduceWalletBalance(
                    requireNotNull(recharge.walletId),
                    id,
                    PayWalletBizTypeEnum.RECHARGE_REFUND,
                    requireNotNull(recharge.totalPrice),
                )
                update.refundStatus = PayRefundStatusEnum.SUCCESS.status
                update.refundTime = payRefund.successTime
                update.refundTotalPrice = recharge.totalPrice
                update.refundPayPrice = recharge.payPrice
                update.refundBonusPrice = recharge.bonusPrice
            }
            PayRefundStatusEnum.isFailure(payRefund.status) -> {
                payWalletService.unfreezePrice(requireNotNull(recharge.walletId), requireNotNull(recharge.totalPrice))
                update.refundStatus = PayRefundStatusEnum.FAILURE.status
            }
            else -> return
        }
        PayWalletRechargeDao.updateByIdAndRefundStatus(id, PayRefundStatusEnum.WAITING.status, update)
    }

    private fun getRecharge(id: Long): PayWalletRechargeDO =
        PayWalletRechargeDao.selectById(id) ?: throw exception(WALLET_RECHARGE_NOT_FOUND)

    private fun validatePayOrderPaid(recharge: PayWalletRechargeDO, payOrderId: Long): PayOrderDO {
        if (recharge.payOrderId != payOrderId) throw exception(WALLET_RECHARGE_UPDATE_PAID_PAY_ORDER_ID_ERROR)
        val payOrder = payOrderService.getOrder(payOrderId)
        if (!PayOrderStatusEnum.isSuccess(payOrder.status)) {
            throw exception(WALLET_RECHARGE_UPDATE_PAID_PAY_ORDER_STATUS_NOT_SUCCESS)
        }
        if (payOrder.price != recharge.payPrice) {
            throw exception(WALLET_RECHARGE_UPDATE_PAID_PAY_PRICE_NOT_MATCH)
        }
        if (payOrder.merchantOrderId != recharge.id.toString()) {
            throw exception(WALLET_RECHARGE_UPDATE_PAID_PAY_ORDER_ID_ERROR)
        }
        return payOrder
    }

    private fun validateCanRefund(recharge: PayWalletRechargeDO): PayWalletDO {
        if (recharge.payStatus != true) throw exception(WALLET_RECHARGE_REFUND_FAIL_NOT_PAID)
        if (recharge.payRefundId != null) throw exception(WALLET_RECHARGE_REFUND_FAIL_REFUNDED)
        val wallet = payWalletService.getWallet(requireNotNull(recharge.walletId))
        if ((wallet.balance ?: 0) < (recharge.totalPrice ?: 0)) {
            throw exception(WALLET_RECHARGE_REFUND_BALANCE_NOT_ENOUGH)
        }
        return wallet
    }

    private fun validateRefund(
        recharge: PayWalletRechargeDO,
        refundId: String,
        payRefundId: Long,
    ): PayRefundDO {
        val payRefund = runCatching { payRefundService.getRefund(payRefundId) }.getOrNull()
            ?: throw exception(WALLET_RECHARGE_REFUND_FAIL_REFUND_NOT_FOUND)
        if (payRefund.refundPrice != recharge.payPrice) {
            throw exception(WALLET_RECHARGE_REFUND_FAIL_REFUND_PRICE_NOT_MATCH)
        }
        if (payRefund.merchantRefundId != refundId || refundId != "${recharge.id}-refund") {
            throw exception(WALLET_RECHARGE_REFUND_FAIL_REFUND_ORDER_ID_ERROR)
        }
        return payRefund
    }
}
