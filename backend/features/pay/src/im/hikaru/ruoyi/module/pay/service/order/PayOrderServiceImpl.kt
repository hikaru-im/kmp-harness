package im.hikaru.ruoyi.module.pay.service.order

import im.hikaru.ruoyi.framework.common.exception.util.ServiceExceptionUtil.exception
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.common.util.json.JsonUtils
import im.hikaru.ruoyi.framework.common.util.number.MoneyUtils
import im.hikaru.ruoyi.framework.tenant.core.util.TenantUtils
import im.hikaru.ruoyi.module.pay.api.order.dto.PayOrderCreateReqDTO
import im.hikaru.ruoyi.module.pay.controller.admin.order.vo.PayOrderExportReqVO
import im.hikaru.ruoyi.module.pay.controller.admin.order.vo.PayOrderPageReqVO
import im.hikaru.ruoyi.module.pay.controller.admin.order.vo.PayOrderSubmitReqVO
import im.hikaru.ruoyi.module.pay.controller.admin.order.vo.PayOrderSubmitRespVO
import im.hikaru.ruoyi.module.pay.convert.order.PayOrderConvert
import im.hikaru.ruoyi.module.pay.dal.dataobject.channel.PayChannelDO
import im.hikaru.ruoyi.module.pay.dal.dataobject.order.PayOrderDO
import im.hikaru.ruoyi.module.pay.dal.dataobject.order.PayOrderExtensionDO
import im.hikaru.ruoyi.module.pay.dal.mysql.order.PayOrderDao
import im.hikaru.ruoyi.module.pay.dal.mysql.order.PayOrderExtensionDao
import im.hikaru.ruoyi.module.pay.enums.ErrorCodeConstants.CHANNEL_NOT_FOUND
import im.hikaru.ruoyi.module.pay.enums.ErrorCodeConstants.PAY_ORDER_EXTENSION_IS_PAID
import im.hikaru.ruoyi.module.pay.enums.ErrorCodeConstants.PAY_ORDER_EXTENSION_NOT_FOUND
import im.hikaru.ruoyi.module.pay.enums.ErrorCodeConstants.PAY_ORDER_EXTENSION_STATUS_IS_NOT_WAITING
import im.hikaru.ruoyi.module.pay.enums.ErrorCodeConstants.PAY_ORDER_IS_EXPIRED
import im.hikaru.ruoyi.module.pay.enums.ErrorCodeConstants.PAY_ORDER_NOT_FOUND
import im.hikaru.ruoyi.module.pay.enums.ErrorCodeConstants.PAY_ORDER_REFUND_FAIL_STATUS_ERROR
import im.hikaru.ruoyi.module.pay.enums.ErrorCodeConstants.PAY_ORDER_STATUS_IS_NOT_WAITING
import im.hikaru.ruoyi.module.pay.enums.ErrorCodeConstants.PAY_ORDER_STATUS_IS_SUCCESS
import im.hikaru.ruoyi.module.pay.enums.ErrorCodeConstants.PAY_ORDER_SUBMIT_CHANNEL_ERROR
import im.hikaru.ruoyi.module.pay.enums.ErrorCodeConstants.REFUND_PRICE_EXCEED
import im.hikaru.ruoyi.module.pay.enums.notify.PayNotifyTypeEnum
import im.hikaru.ruoyi.module.pay.enums.order.PayOrderStatusEnum
import im.hikaru.ruoyi.module.pay.framework.pay.config.PayProperties
import im.hikaru.ruoyi.module.pay.framework.pay.core.PayNoGenerator
import im.hikaru.ruoyi.module.pay.framework.pay.core.client.dto.order.PayOrderRespDTO
import im.hikaru.ruoyi.module.pay.service.app.PayAppService
import im.hikaru.ruoyi.module.pay.service.channel.PayChannelService
import im.hikaru.ruoyi.module.pay.service.notify.PayNotifyService
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toKotlinLocalDateTime
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.validation.annotation.Validated
import java.time.LocalDateTime

@Service
@Validated
class PayOrderServiceImpl(
    private val payProperties: PayProperties,
    private val appService: PayAppService,
    private val channelService: PayChannelService,
    private val notifyService: PayNotifyService,
    private val applicationContext: ApplicationContext,
) : PayOrderService {

    override fun getOrder(id: Long): PayOrderDO =
        PayOrderDao.selectById(id) ?: throw exception(PAY_ORDER_NOT_FOUND)

    override fun getOrder(no: String): PayOrderDO =
        PayOrderDao.selectByNo(no) ?: throw exception(PAY_ORDER_NOT_FOUND)

    override fun getOrder(appId: Long, merchantOrderId: String): PayOrderDO =
        PayOrderDao.selectByAppIdAndMerchantOrderId(appId, merchantOrderId)
            ?: throw exception(PAY_ORDER_NOT_FOUND)

    override fun getOrderList(ids: Collection<Long>): List<PayOrderDO> = PayOrderDao.selectByIds(ids)

    override fun getOrderCountByAppId(appId: Long): Long = PayOrderDao.selectCountByAppId(appId)

    override fun getOrderPage(pageReqVO: PayOrderPageReqVO): PageResult<PayOrderDO> =
        PayOrderDao.selectPage(pageReqVO)

    override fun getOrderList(exportReqVO: PayOrderExportReqVO): List<PayOrderDO> =
        PayOrderDao.selectList(exportReqVO)

    override fun createOrder(reqDTO: PayOrderCreateReqDTO): Long {
        val app = appService.validPayApp(requireNotNull(reqDTO.appKey))
        val appId = requireNotNull(app.id)
        val merchantOrderId = requireNotNull(reqDTO.merchantOrderId)
        PayOrderDao.selectByAppIdAndMerchantOrderId(appId, merchantOrderId)?.let { existing ->
            log.warn(
                "[createOrder][appId({}) merchantOrderId({}) already has order({})]",
                appId,
                merchantOrderId,
                existing.id,
            )
            return requireNotNull(existing.id)
        }

        val order = PayOrderConvert.convert(reqDTO).apply {
            this.appId = appId
            notifyUrl = app.orderNotifyUrl
            status = PayOrderStatusEnum.WAITING.status
            refundPrice = 0
        }
        return PayOrderDao.insert(order)
    }

    // Do not make submission transactional: a channel failure must not roll back the extension record.
    override fun submitOrder(reqVO: PayOrderSubmitReqVO, userIp: String): PayOrderSubmitRespVO {
        var order = validateOrderCanSubmit(requireNotNull(reqVO.id))
        val channel = validateChannelCanSubmit(requireNotNull(order.appId), requireNotNull(reqVO.channelCode))
        val channelId = requireNotNull(channel.id)
        val client = channelService.getPayClient(channelId)

        val extension = PayOrderConvert.convert(reqVO, userIp).apply {
            orderId = order.id
            no = PayNoGenerator.next(payProperties.orderNoPrefix)
            this.channelId = channelId
            channelCode = channel.code
            status = PayOrderStatusEnum.WAITING.status
        }
        PayOrderExtensionDao.insert(extension)

        val request = PayOrderConvert.unified(reqVO, userIp).apply {
            outTradeNo = extension.no
            subject = order.subject
            body = order.body
            notifyUrl = genOrderNotifyUrl(channelId)
            returnUrl = reqVO.returnUrl
            price = order.price
            expireTime = order.expireTime?.toJavaLocalDateTime()
        }
        val response = client.unifiedOrder(request)
        try {
            self().notifyOrder(channelId, response)
        } catch (ex: Exception) {
            log.warn(
                "[submitOrder][order({}) channel({}) notifying synchronous result failed; callback may be concurrent]",
                order.id,
                channelId,
                ex,
            )
        }
        if (!response.channelErrorCode.isNullOrBlank()) {
            throw exception(PAY_ORDER_SUBMIT_CHANNEL_ERROR, response.channelErrorCode, response.channelErrorMsg)
        }
        order = PayOrderDao.selectById(requireNotNull(order.id)) ?: order
        return PayOrderConvert.convert(order, response)
    }

    private fun validateOrderCanSubmit(id: Long): PayOrderDO {
        val order = PayOrderDao.selectById(id) ?: throw exception(PAY_ORDER_NOT_FOUND)
        if (PayOrderStatusEnum.isSuccess(order.status)) throw exception(PAY_ORDER_STATUS_IS_SUCCESS)
        if (!PayOrderStatusEnum.isWaiting(order.status)) throw exception(PAY_ORDER_STATUS_IS_NOT_WAITING)
        if (order.expireTime?.toJavaLocalDateTime()?.isBefore(LocalDateTime.now()) == true) {
            throw exception(PAY_ORDER_IS_EXPIRED)
        }
        validateOrderActuallyPaid(id)
        return order
    }

    internal fun validateOrderActuallyPaid(id: Long) {
        PayOrderExtensionDao.selectListByOrderId(id).forEach { extension ->
            if (PayOrderStatusEnum.isSuccess(extension.status)) {
                log.warn(
                    "[validateOrderActuallyPaid][order({}) extension({}) is already paid]",
                    id,
                    extension.id,
                )
                throw exception(PAY_ORDER_EXTENSION_IS_PAID)
            }
            val client = channelService.getPayClient(requireNotNull(extension.channelId))
            val response = client.getOrder(requireNotNull(extension.no))
            if (PayOrderStatusEnum.isSuccess(response.status)) {
                log.warn(
                    "[validateOrderActuallyPaid][order({}) provider reports extension({}) paid]",
                    id,
                    extension.id,
                )
                throw exception(PAY_ORDER_EXTENSION_IS_PAID)
            }
        }
    }

    private fun validateChannelCanSubmit(appId: Long, channelCode: String): PayChannelDO {
        appService.validPayApp(appId)
        val channel = channelService.validPayChannel(appId, channelCode)
        runCatching { channelService.getPayClient(requireNotNull(channel.id)) }.getOrElse {
            log.error("[validateChannelCanSubmit][channel({}) has no client]", channel.id, it)
            throw exception(CHANNEL_NOT_FOUND)
        }
        return channel
    }

    @Transactional(rollbackFor = [Exception::class])
    override fun notifyOrder(channelId: Long, notify: PayOrderRespDTO) {
        val channel = channelService.validPayChannel(channelId)
        TenantUtils.execute(channel.tenantId, Runnable { processOrderNotify(channel, notify) })
    }

    private fun processOrderNotify(channel: PayChannelDO, notify: PayOrderRespDTO) {
        when {
            PayOrderStatusEnum.isSuccess(notify.status) -> notifyOrderSuccess(channel, notify)
            PayOrderStatusEnum.isClosed(notify.status) -> notifyOrderClosed(notify)
        }
    }

    private fun notifyOrderSuccess(channel: PayChannelDO, notify: PayOrderRespDTO) {
        val extension = updateOrderExtensionSuccess(notify)
        if (updateOrderSuccess(channel, extension, notify)) return
        notifyService.createPayNotifyTask(PayNotifyTypeEnum.ORDER.type, requireNotNull(extension.orderId))
    }

    private fun updateOrderExtensionSuccess(notify: PayOrderRespDTO): PayOrderExtensionDO {
        val extension = PayOrderExtensionDao.selectByNo(requireNotNull(notify.outTradeNo))
            ?: throw exception(PAY_ORDER_EXTENSION_NOT_FOUND)
        if (PayOrderStatusEnum.isSuccess(extension.status)) return extension
        if (!PayOrderStatusEnum.isWaiting(extension.status)) {
            throw exception(PAY_ORDER_EXTENSION_STATUS_IS_NOT_WAITING)
        }
        val updated = PayOrderExtensionDao.updateByIdAndStatus(
            requireNotNull(extension.id),
            requireNotNull(extension.status),
            PayOrderExtensionDO().apply {
                status = PayOrderStatusEnum.SUCCESS.status
                channelNotifyData = JsonUtils.toJsonString(notify)
            },
        )
        if (updated == 0) throw exception(PAY_ORDER_EXTENSION_STATUS_IS_NOT_WAITING)
        return extension
    }

    /** Returns true when the same extension had already completed the main order. */
    private fun updateOrderSuccess(
        channel: PayChannelDO,
        extension: PayOrderExtensionDO,
        notify: PayOrderRespDTO,
    ): Boolean {
        val order = PayOrderDao.selectById(requireNotNull(extension.orderId))
            ?: throw exception(PAY_ORDER_NOT_FOUND)
        if (PayOrderStatusEnum.isSuccess(order.status) && order.extensionId == extension.id) return true
        if (!PayOrderStatusEnum.isWaiting(order.status)) throw exception(PAY_ORDER_STATUS_IS_NOT_WAITING)

        val feeRate = channel.feeRate ?: 0.0
        val updated = PayOrderDao.updateByIdAndStatus(
            requireNotNull(order.id),
            PayOrderStatusEnum.WAITING.status,
            PayOrderDO().apply {
                status = PayOrderStatusEnum.SUCCESS.status
                channelId = channel.id
                channelCode = channel.code
                successTime = (notify.successTime ?: LocalDateTime.now()).toKotlinLocalDateTime()
                extensionId = extension.id
                no = extension.no
                channelOrderNo = notify.channelOrderNo
                channelUserId = notify.channelUserId
                channelFeeRate = feeRate
                channelFeePrice = MoneyUtils.calculateRatePrice(requireNotNull(order.price), feeRate)
            },
        )
        if (updated == 0) throw exception(PAY_ORDER_STATUS_IS_NOT_WAITING)
        return false
    }

    private fun notifyOrderClosed(notify: PayOrderRespDTO) {
        val extension = PayOrderExtensionDao.selectByNo(requireNotNull(notify.outTradeNo))
            ?: throw exception(PAY_ORDER_EXTENSION_NOT_FOUND)
        if (PayOrderStatusEnum.isClosed(extension.status) || PayOrderStatusEnum.isSuccess(extension.status)) return
        if (!PayOrderStatusEnum.isWaiting(extension.status)) {
            throw exception(PAY_ORDER_EXTENSION_STATUS_IS_NOT_WAITING)
        }
        val updated = PayOrderExtensionDao.updateByIdAndStatus(
            requireNotNull(extension.id),
            requireNotNull(extension.status),
            PayOrderExtensionDO().apply {
                status = PayOrderStatusEnum.CLOSED.status
                channelNotifyData = JsonUtils.toJsonString(notify)
                channelErrorCode = notify.channelErrorCode
                channelErrorMsg = notify.channelErrorMsg
            },
        )
        if (updated == 0) throw exception(PAY_ORDER_EXTENSION_STATUS_IS_NOT_WAITING)
    }

    override fun updateOrderRefundPrice(id: Long, incrRefundPrice: Int) {
        val order = PayOrderDao.selectById(id) ?: throw exception(PAY_ORDER_NOT_FOUND)
        if (!PayOrderStatusEnum.isSuccessOrRefund(order.status)) {
            throw exception(PAY_ORDER_REFUND_FAIL_STATUS_ERROR)
        }
        val nextRefundPrice = (order.refundPrice ?: 0) + incrRefundPrice
        if (nextRefundPrice > requireNotNull(order.price)) throw exception(REFUND_PRICE_EXCEED)
        val updated = PayOrderDao.updateByIdAndStatus(
            id,
            requireNotNull(order.status),
            PayOrderDO().apply {
                refundPrice = nextRefundPrice
                status = PayOrderStatusEnum.REFUND.status
            },
        )
        if (updated == 0) throw exception(PAY_ORDER_REFUND_FAIL_STATUS_ERROR)
    }

    override fun updatePayOrderPrice(id: Long, payPrice: Int) {
        val order = PayOrderDao.selectById(id) ?: throw exception(PAY_ORDER_NOT_FOUND)
        if (!PayOrderStatusEnum.isWaiting(order.status)) throw exception(PAY_ORDER_STATUS_IS_NOT_WAITING)
        if (order.price == payPrice) return
        PayOrderDao.updateById(PayOrderDO().apply {
            this.id = id
            price = payPrice
        })
    }

    override fun getOrderExtension(id: Long): PayOrderExtensionDO =
        PayOrderExtensionDao.selectById(id) ?: throw exception(PAY_ORDER_EXTENSION_NOT_FOUND)

    override fun getOrderExtensionByNo(no: String): PayOrderExtensionDO =
        PayOrderExtensionDao.selectByNo(no) ?: throw exception(PAY_ORDER_EXTENSION_NOT_FOUND)

    override fun syncOrder(minCreateTime: LocalDateTime): Int =
        PayOrderExtensionDao.selectListByStatusAndCreateTimeGe(
            PayOrderStatusEnum.WAITING.status,
            minCreateTime,
        ).count(::syncOrder)

    override fun syncOrderQuietly(id: Long) {
        PayOrderExtensionDao.selectListByOrderIdAndStatus(id, PayOrderStatusEnum.WAITING.status)
            .forEach(::syncOrder)
    }

    private fun syncOrder(extension: PayOrderExtensionDO): Boolean = try {
        val response = channelService.getPayClient(requireNotNull(extension.channelId))
            .getOrder(requireNotNull(extension.no))
        if (PayOrderStatusEnum.isClosed(response.status)) {
            false
        } else {
            self().notifyOrder(requireNotNull(extension.channelId), response)
            PayOrderStatusEnum.isSuccess(response.status)
        }
    } catch (ex: Throwable) {
        log.error("[syncOrder][extension({}) failed]", extension.id, ex)
        false
    }

    override fun expireOrder(): Int =
        PayOrderDao.selectListByStatusAndExpireTimeLt(PayOrderStatusEnum.WAITING.status, LocalDateTime.now())
            .count(::expireOrder)

    private fun expireOrder(order: PayOrderDO): Boolean = try {
        for (extension in PayOrderExtensionDao.selectListByOrderId(requireNotNull(order.id))) {
            if (PayOrderStatusEnum.isClosed(extension.status)) continue
            if (PayOrderStatusEnum.isSuccess(extension.status)) {
                log.error("[expireOrder][order({}) extension({}) is already paid]", order.id, extension.id)
                return false
            }
            val response = channelService.getPayClient(requireNotNull(extension.channelId))
                .getOrder(requireNotNull(extension.no))
            if (PayOrderStatusEnum.isRefund(response.status)) {
                log.error("[expireOrder][extension({}) provider reports refunded]", extension.id)
                return false
            }
            if (PayOrderStatusEnum.isSuccess(response.status)) {
                self().notifyOrder(requireNotNull(extension.channelId), response)
                return false
            }
            val extensionUpdated = PayOrderExtensionDao.updateByIdAndStatus(
                requireNotNull(extension.id),
                PayOrderStatusEnum.WAITING.status,
                PayOrderExtensionDO().apply {
                    status = PayOrderStatusEnum.CLOSED.status
                    channelNotifyData = JsonUtils.toJsonString(response)
                },
            )
            if (extensionUpdated == 0) return false
        }
        PayOrderDao.updateByIdAndStatus(
            requireNotNull(order.id),
            requireNotNull(order.status),
            PayOrderDO().apply { status = PayOrderStatusEnum.CLOSED.status },
        ) > 0
    } catch (ex: Throwable) {
        log.error("[expireOrder][order({}) failed]", order.id, ex)
        false
    }

    private fun genOrderNotifyUrl(channelId: Long): String = "${payProperties.orderNotifyUrl}/$channelId"

    private fun self(): PayOrderServiceImpl = applicationContext.getBean(PayOrderServiceImpl::class.java)

    companion object {
        private val log = LoggerFactory.getLogger(PayOrderServiceImpl::class.java)
    }
}
