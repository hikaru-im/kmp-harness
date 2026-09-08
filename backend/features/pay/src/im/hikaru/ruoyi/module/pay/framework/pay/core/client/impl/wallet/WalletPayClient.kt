package im.hikaru.ruoyi.module.pay.framework.pay.core.client.impl.wallet

import im.hikaru.ruoyi.framework.common.exception.ServiceException
import im.hikaru.ruoyi.module.pay.enums.ErrorCodeConstants.PAY_ORDER_EXTENSION_NOT_FOUND
import im.hikaru.ruoyi.module.pay.enums.ErrorCodeConstants.REFUND_NOT_FOUND
import im.hikaru.ruoyi.module.pay.enums.PayChannelEnum
import im.hikaru.ruoyi.module.pay.enums.order.PayOrderStatusEnum
import im.hikaru.ruoyi.module.pay.enums.refund.PayRefundStatusEnum
import im.hikaru.ruoyi.module.pay.enums.transfer.PayTransferStatusEnum
import im.hikaru.ruoyi.module.pay.enums.wallet.PayWalletBizTypeEnum
import im.hikaru.ruoyi.module.pay.framework.pay.core.client.PayClient
import im.hikaru.ruoyi.module.pay.framework.pay.core.client.dto.order.PayOrderRespDTO
import im.hikaru.ruoyi.module.pay.framework.pay.core.client.dto.order.PayOrderUnifiedReqDTO
import im.hikaru.ruoyi.module.pay.framework.pay.core.client.dto.refund.PayRefundRespDTO
import im.hikaru.ruoyi.module.pay.framework.pay.core.client.dto.refund.PayRefundUnifiedReqDTO
import im.hikaru.ruoyi.module.pay.framework.pay.core.client.dto.transfer.PayTransferRespDTO
import im.hikaru.ruoyi.module.pay.framework.pay.core.client.dto.transfer.PayTransferUnifiedReqDTO
import im.hikaru.ruoyi.module.pay.framework.pay.core.client.impl.NonePayClientConfig
import im.hikaru.ruoyi.module.pay.service.order.PayOrderService
import im.hikaru.ruoyi.module.pay.service.refund.PayRefundService
import im.hikaru.ruoyi.module.pay.service.transfer.PayTransferService
import im.hikaru.ruoyi.module.pay.service.wallet.PayWalletService
import im.hikaru.ruoyi.module.pay.service.wallet.PayWalletTransactionService
import kotlinx.datetime.toJavaLocalDateTime
import org.slf4j.LoggerFactory

/**
 * Internal wallet channel. Unlike a mock channel it performs the actual atomic
 * wallet mutation and exposes the resulting wallet transaction as the channel
 * order number.
 */
class WalletPayClient(
    override val id: Long,
    private val walletService: () -> PayWalletService,
    private val transactionService: () -> PayWalletTransactionService,
    private val orderService: () -> PayOrderService,
    private val refundService: () -> PayRefundService,
    private val transferService: () -> PayTransferService,
    override val config: NonePayClientConfig = NonePayClientConfig(),
) : PayClient<NonePayClientConfig> {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun unifiedOrder(reqDTO: PayOrderUnifiedReqDTO): PayOrderRespDTO = try {
        val walletId = reqDTO.channelExtras?.get(WALLET_ID_KEY)?.toLongOrNull()
            ?: throw IllegalArgumentException("walletId is required")
        val transaction = walletService().orderPay(
            walletId,
            requireNotNull(reqDTO.outTradeNo),
            requireNotNull(reqDTO.price),
        )
        PayOrderRespDTO().apply {
            status = PayOrderStatusEnum.SUCCESS.status
            outTradeNo = reqDTO.outTradeNo
            channelOrderNo = transaction.no
            channelUserId = transaction.creator
            successTime = transaction.createTime?.toJavaLocalDateTime()
            rawData = transaction
            displayMode = reqDTO.displayMode ?: "url"
            displayContent = ""
        }
    } catch (ex: Throwable) {
        log.error("Wallet payment failed for {}", reqDTO.outTradeNo, ex)
        closedOrder(reqDTO, errorCode(ex), errorMessage(ex))
    }

    override fun parseOrderNotify(
        params: Map<String, String>,
        body: String,
        headers: Map<String, String>,
    ): PayOrderRespDTO = throw UnsupportedOperationException("Wallet payment has no callback")

    override fun getOrder(outTradeNo: String): PayOrderRespDTO = try {
        val extension = orderService().getOrderExtensionByNo(outTradeNo)
        when {
            PayOrderStatusEnum.isClosed(extension.status) -> closedOrder(
                outTradeNo,
                extension.channelErrorCode,
                extension.channelErrorMsg,
            )
            PayOrderStatusEnum.isSuccess(extension.status) -> {
                val transaction = transactionService().getWalletTransaction(
                    requireNotNull(extension.orderId).toString(),
                    PayWalletBizTypeEnum.PAYMENT,
                ) ?: return closedOrder(outTradeNo, PAY_ORDER_EXTENSION_NOT_FOUND.code.toString(), "wallet transaction not found")
                PayOrderRespDTO().apply {
                    status = PayOrderStatusEnum.SUCCESS.status
                    this.outTradeNo = outTradeNo
                    channelOrderNo = transaction.no
                    channelUserId = transaction.creator
                    successTime = transaction.createTime?.toJavaLocalDateTime()
                    rawData = transaction
                }
            }
            else -> PayOrderRespDTO().apply {
                status = PayOrderStatusEnum.WAITING.status
                this.outTradeNo = outTradeNo
            }
        }
    } catch (ex: Throwable) {
        log.error("Wallet payment lookup failed for {}", outTradeNo, ex)
        closedOrder(outTradeNo, errorCode(ex), errorMessage(ex))
    }

    override fun unifiedRefund(reqDTO: PayRefundUnifiedReqDTO): PayRefundRespDTO = try {
        val transaction = walletService().orderRefund(
            requireNotNull(reqDTO.outRefundNo),
            requireNotNull(reqDTO.refundPrice),
            reqDTO.reason.orEmpty(),
        )
        PayRefundRespDTO().apply {
            status = PayRefundStatusEnum.SUCCESS.status
            outRefundNo = reqDTO.outRefundNo
            channelRefundNo = transaction.no
            successTime = transaction.createTime?.toJavaLocalDateTime()
            rawData = transaction
        }
    } catch (ex: Throwable) {
        log.error("Wallet refund failed for {}", reqDTO.outRefundNo, ex)
        PayRefundRespDTO().apply {
            status = PayRefundStatusEnum.FAILURE.status
            outRefundNo = reqDTO.outRefundNo
            channelErrorCode = errorCode(ex)
            channelErrorMsg = errorMessage(ex)
        }
    }

    override fun parseRefundNotify(
        params: Map<String, String>,
        body: String,
        headers: Map<String, String>,
    ): PayRefundRespDTO = throw UnsupportedOperationException("Wallet payment has no callback")

    override fun getRefund(outTradeNo: String, outRefundNo: String): PayRefundRespDTO = try {
        val refund = refundService().getRefundByNo(outRefundNo)
        when {
            PayRefundStatusEnum.isFailure(refund.status) -> PayRefundRespDTO().apply {
                status = PayRefundStatusEnum.FAILURE.status
                this.outRefundNo = outRefundNo
                channelErrorCode = refund.channelErrorCode
                channelErrorMsg = refund.channelErrorMsg
            }
            PayRefundStatusEnum.isSuccess(refund.status) -> {
                val transaction = transactionService().getWalletTransaction(
                    requireNotNull(refund.id).toString(),
                    PayWalletBizTypeEnum.PAYMENT_REFUND,
                ) ?: return PayRefundRespDTO().apply {
                    status = PayRefundStatusEnum.FAILURE.status
                    this.outRefundNo = outRefundNo
                    channelErrorCode = REFUND_NOT_FOUND.code.toString()
                    channelErrorMsg = "wallet transaction not found"
                }
                PayRefundRespDTO().apply {
                    status = PayRefundStatusEnum.SUCCESS.status
                    this.outRefundNo = outRefundNo
                    channelRefundNo = transaction.no
                    successTime = transaction.createTime?.toJavaLocalDateTime()
                    rawData = transaction
                }
            }
            else -> PayRefundRespDTO().apply {
                status = PayRefundStatusEnum.WAITING.status
                this.outRefundNo = outRefundNo
            }
        }
    } catch (ex: Throwable) {
        log.error("Wallet refund lookup failed for {}", outRefundNo, ex)
        PayRefundRespDTO().apply {
            status = PayRefundStatusEnum.FAILURE.status
            this.outRefundNo = outRefundNo
            channelErrorCode = errorCode(ex)
            channelErrorMsg = errorMessage(ex)
        }
    }

    override fun unifiedTransfer(reqDTO: PayTransferUnifiedReqDTO): PayTransferRespDTO = try {
        val walletId = requireNotNull(reqDTO.userAccount).toLong()
        val transaction = walletService().addWalletBalance(
            walletId,
            requireNotNull(reqDTO.outTransferNo),
            PayWalletBizTypeEnum.TRANSFER,
            requireNotNull(reqDTO.price),
        )
        PayTransferRespDTO().apply {
            status = PayTransferStatusEnum.SUCCESS.status
            outTransferNo = reqDTO.outTransferNo
            channelTransferNo = transaction.no
            successTime = transaction.createTime?.toJavaLocalDateTime()
            rawData = transaction
        }
    } catch (ex: Throwable) {
        log.error("Wallet transfer failed for {}", reqDTO.outTransferNo, ex)
        PayTransferRespDTO().apply {
            status = PayTransferStatusEnum.CLOSED.status
            outTransferNo = reqDTO.outTransferNo
            channelErrorCode = errorCode(ex)
            channelErrorMsg = errorMessage(ex)
        }
    }

    override fun getTransfer(outTradeNo: String): PayTransferRespDTO = try {
        val transfer = transferService().getTransferByNo(outTradeNo)
        when {
            PayTransferStatusEnum.isClosed(transfer.status) -> PayTransferRespDTO().apply {
                status = PayTransferStatusEnum.CLOSED.status
                this.outTransferNo = outTradeNo
                channelTransferNo = transfer.channelTransferNo
                channelErrorCode = transfer.channelErrorCode
                channelErrorMsg = transfer.channelErrorMsg
            }
            PayTransferStatusEnum.isSuccess(transfer.status) -> {
                val transaction = transactionService().getWalletTransaction(
                    requireNotNull(transfer.id).toString(),
                    PayWalletBizTypeEnum.TRANSFER,
                )
                PayTransferRespDTO().apply {
                    status = PayTransferStatusEnum.SUCCESS.status
                    this.outTransferNo = outTradeNo
                    channelTransferNo = transaction?.no ?: transfer.channelTransferNo
                    successTime = transaction?.createTime?.toJavaLocalDateTime() ?: transfer.successTime?.toJavaLocalDateTime()
                    rawData = transaction ?: transfer
                }
            }
            PayTransferStatusEnum.isProcessing(transfer.status) -> PayTransferRespDTO().apply {
                status = PayTransferStatusEnum.PROCESSING.status
                this.outTransferNo = outTradeNo
                channelTransferNo = transfer.channelTransferNo
                rawData = transfer
            }
            else -> PayTransferRespDTO().apply {
                status = PayTransferStatusEnum.WAITING.status
                this.outTransferNo = outTradeNo
                channelTransferNo = transfer.channelTransferNo
                rawData = transfer
            }
        }
    } catch (ex: Throwable) {
        log.error("Wallet transfer lookup failed for {}", outTradeNo, ex)
        PayTransferRespDTO().apply {
            status = PayTransferStatusEnum.CLOSED.status
            this.outTransferNo = outTradeNo
            channelErrorCode = errorCode(ex)
            channelErrorMsg = errorMessage(ex)
        }
    }

    override fun parseTransferNotify(
        params: Map<String, String>,
        body: String,
        headers: Map<String, String>,
    ): PayTransferRespDTO = throw UnsupportedOperationException("Wallet payment has no callback")

    private fun closedOrder(reqDTO: PayOrderUnifiedReqDTO, code: String, message: String?) =
        PayOrderRespDTO().apply {
            status = PayOrderStatusEnum.CLOSED.status
            outTradeNo = reqDTO.outTradeNo
            channelErrorCode = code
            channelErrorMsg = message
        }

    private fun closedOrder(outTradeNo: String, code: String?, message: String?) =
        PayOrderRespDTO().apply {
            status = PayOrderStatusEnum.CLOSED.status
            this.outTradeNo = outTradeNo
            channelErrorCode = code
            channelErrorMsg = message
        }

    private fun errorCode(ex: Throwable): String = (ex as? ServiceException)?.code?.toString() ?: "500"

    private fun errorMessage(ex: Throwable): String = ex.message ?: "wallet channel error"

    private companion object {
        const val WALLET_ID_KEY = "walletId"
    }
}
