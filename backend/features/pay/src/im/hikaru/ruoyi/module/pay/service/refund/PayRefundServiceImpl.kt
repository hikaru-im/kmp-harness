package im.hikaru.ruoyi.module.pay.service.refund

import im.hikaru.ruoyi.framework.common.exception.util.ServiceExceptionUtil.exception
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.common.util.json.JsonUtils
import im.hikaru.ruoyi.framework.tenant.core.util.TenantUtils
import im.hikaru.ruoyi.module.pay.api.refund.dto.PayRefundCreateReqDTO
import im.hikaru.ruoyi.module.pay.controller.admin.refund.vo.PayRefundExportReqVO
import im.hikaru.ruoyi.module.pay.controller.admin.refund.vo.PayRefundPageReqVO
import im.hikaru.ruoyi.module.pay.convert.refund.PayRefundConvert
import im.hikaru.ruoyi.module.pay.dal.dataobject.channel.PayChannelDO
import im.hikaru.ruoyi.module.pay.dal.dataobject.order.PayOrderDO
import im.hikaru.ruoyi.module.pay.dal.dataobject.refund.PayRefundDO
import im.hikaru.ruoyi.module.pay.dal.mysql.refund.PayRefundDao
import im.hikaru.ruoyi.module.pay.enums.ErrorCodeConstants.PAY_ORDER_REFUND_FAIL_STATUS_ERROR
import im.hikaru.ruoyi.module.pay.enums.ErrorCodeConstants.REFUND_EXISTS
import im.hikaru.ruoyi.module.pay.enums.ErrorCodeConstants.REFUND_HAS_REFUNDING
import im.hikaru.ruoyi.module.pay.enums.ErrorCodeConstants.REFUND_NOT_FOUND
import im.hikaru.ruoyi.module.pay.enums.ErrorCodeConstants.REFUND_PRICE_EXCEED
import im.hikaru.ruoyi.module.pay.enums.ErrorCodeConstants.REFUND_STATUS_IS_NOT_WAITING
import im.hikaru.ruoyi.module.pay.enums.notify.PayNotifyTypeEnum
import im.hikaru.ruoyi.module.pay.enums.order.PayOrderStatusEnum
import im.hikaru.ruoyi.module.pay.enums.refund.PayRefundStatusEnum
import im.hikaru.ruoyi.module.pay.framework.pay.config.PayProperties
import im.hikaru.ruoyi.module.pay.framework.pay.core.PayNoGenerator
import im.hikaru.ruoyi.module.pay.framework.pay.core.client.dto.refund.PayRefundRespDTO
import im.hikaru.ruoyi.module.pay.framework.pay.core.client.dto.refund.PayRefundUnifiedReqDTO
import im.hikaru.ruoyi.module.pay.service.app.PayAppService
import im.hikaru.ruoyi.module.pay.service.channel.PayChannelService
import im.hikaru.ruoyi.module.pay.service.notify.PayNotifyService
import im.hikaru.ruoyi.module.pay.service.order.PayOrderService
import kotlinx.datetime.toKotlinLocalDateTime
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.validation.annotation.Validated
import java.time.LocalDateTime

@Service
@Validated
class PayRefundServiceImpl(
    private val payProperties: PayProperties,
    private val appService: PayAppService,
    private val orderService: PayOrderService,
    private val channelService: PayChannelService,
    private val notifyService: PayNotifyService,
    private val applicationContext: ApplicationContext,
) : PayRefundService {

    override fun getRefund(id: Long): PayRefundDO =
        PayRefundDao.selectById(id) ?: throw exception(REFUND_NOT_FOUND)

    override fun getRefundByNo(no: String): PayRefundDO =
        PayRefundDao.selectByNo(no) ?: throw exception(REFUND_NOT_FOUND)

    override fun getRefundCountByAppId(appId: Long): Long = PayRefundDao.selectCountByAppId(appId)

    override fun getRefundPage(pageReqVO: PayRefundPageReqVO): PageResult<PayRefundDO> =
        PayRefundDao.selectPage(pageReqVO)

    override fun getRefundList(exportReqVO: PayRefundExportReqVO): List<PayRefundDO> =
        PayRefundDao.selectList(exportReqVO)

    override fun createRefund(reqDTO: PayRefundCreateReqDTO): Long {
        val app = appService.validPayApp(requireNotNull(reqDTO.appKey))
        val appId = requireNotNull(app.id)
        val order = validateOrderCanRefund(reqDTO, appId)
        val channel = channelService.validPayChannel(requireNotNull(order.channelId))
        val client = channelService.getPayClient(requireNotNull(channel.id))
        if (PayRefundDao.selectByAppIdAndMerchantRefundId(
                appId,
                requireNotNull(reqDTO.merchantRefundId),
            ) != null
        ) {
            throw exception(REFUND_EXISTS)
        }

        val refund = PayRefundConvert.convert(reqDTO).apply {
            no = PayNoGenerator.next(payProperties.refundNoPrefix)
            this.appId = appId
            orderId = order.id
            orderNo = order.no
            channelId = order.channelId
            channelCode = order.channelCode
            notifyUrl = app.refundNotifyUrl
            channelOrderNo = order.channelOrderNo
            status = PayRefundStatusEnum.WAITING.status
            payPrice = order.price
            refundPrice = reqDTO.price
        }
        PayRefundDao.insert(refund)
        try {
            val response = client.unifiedRefund(PayRefundUnifiedReqDTO().apply {
                payPrice = order.price
                refundPrice = reqDTO.price
                outTradeNo = order.no
                outRefundNo = refund.no
                notifyUrl = genRefundNotifyUrl(requireNotNull(channel.id))
                reason = reqDTO.reason
            })
            self().notifyRefund(requireNotNull(channel.id), response)
        } catch (ex: Throwable) {
            // The provider may have accepted the refund despite a timeout. Callback/polling owns recovery.
            log.error("[createRefund][refund({}) channel request failed]", refund.id, ex)
        }
        return requireNotNull(refund.id)
    }

    private fun validateOrderCanRefund(reqDTO: PayRefundCreateReqDTO, appId: Long): PayOrderDO {
        val order = orderService.getOrder(appId, requireNotNull(reqDTO.merchantOrderId))
        if (!PayOrderStatusEnum.isSuccessOrRefund(order.status)) {
            throw exception(PAY_ORDER_REFUND_FAIL_STATUS_ERROR)
        }
        val requestedPrice = requireNotNull(reqDTO.price)
        if (requestedPrice + (order.refundPrice ?: 0) > requireNotNull(order.price)) {
            throw exception(REFUND_PRICE_EXCEED)
        }
        if (PayRefundDao.selectCountByAppIdAndOrderId(
                appId,
                requireNotNull(order.id),
                PayRefundStatusEnum.WAITING.status,
            ) > 0
        ) {
            throw exception(REFUND_HAS_REFUNDING)
        }
        return order
    }

    @Transactional(rollbackFor = [Exception::class])
    override fun notifyRefund(channelId: Long, notify: PayRefundRespDTO) {
        val channel = channelService.validPayChannel(channelId)
        TenantUtils.execute(channel.tenantId, Runnable { processRefundNotify(channel, notify) })
    }

    private fun processRefundNotify(channel: PayChannelDO, notify: PayRefundRespDTO) {
        when {
            PayRefundStatusEnum.isSuccess(notify.status) -> notifyRefundSuccess(channel, notify)
            PayRefundStatusEnum.isFailure(notify.status) -> notifyRefundFailure(channel, notify)
        }
    }

    private fun notifyRefundSuccess(channel: PayChannelDO, notify: PayRefundRespDTO) {
        val refund = PayRefundDao.selectByAppIdAndNo(
            requireNotNull(channel.appId),
            requireNotNull(notify.outRefundNo),
        ) ?: throw exception(REFUND_NOT_FOUND)
        if (PayRefundStatusEnum.isSuccess(refund.status)) return
        if (!PayRefundStatusEnum.isWaiting(refund.status)) throw exception(REFUND_STATUS_IS_NOT_WAITING)

        val updated = PayRefundDao.updateByIdAndStatus(
            requireNotNull(refund.id),
            requireNotNull(refund.status),
            PayRefundDO().apply {
                successTime = (notify.successTime ?: LocalDateTime.now()).toKotlinLocalDateTime()
                channelRefundNo = notify.channelRefundNo
                status = PayRefundStatusEnum.SUCCESS.status
                channelNotifyData = JsonUtils.toJsonString(notify)
            },
        )
        if (updated == 0) throw exception(REFUND_STATUS_IS_NOT_WAITING)

        orderService.updateOrderRefundPrice(requireNotNull(refund.orderId), requireNotNull(refund.refundPrice))
        notifyService.createPayNotifyTask(PayNotifyTypeEnum.REFUND.type, requireNotNull(refund.id))
    }

    private fun notifyRefundFailure(channel: PayChannelDO, notify: PayRefundRespDTO) {
        val refund = PayRefundDao.selectByAppIdAndNo(
            requireNotNull(channel.appId),
            requireNotNull(notify.outRefundNo),
        ) ?: throw exception(REFUND_NOT_FOUND)
        if (PayRefundStatusEnum.isFailure(refund.status)) return
        if (!PayRefundStatusEnum.isWaiting(refund.status)) throw exception(REFUND_STATUS_IS_NOT_WAITING)

        val updated = PayRefundDao.updateByIdAndStatus(
            requireNotNull(refund.id),
            requireNotNull(refund.status),
            PayRefundDO().apply {
                channelRefundNo = notify.channelRefundNo
                status = PayRefundStatusEnum.FAILURE.status
                channelNotifyData = JsonUtils.toJsonString(notify)
                channelErrorCode = notify.channelErrorCode
                channelErrorMsg = notify.channelErrorMsg
            },
        )
        if (updated == 0) throw exception(REFUND_STATUS_IS_NOT_WAITING)
        notifyService.createPayNotifyTask(PayNotifyTypeEnum.REFUND.type, requireNotNull(refund.id))
    }

    override fun syncRefund(): Int =
        PayRefundDao.selectListByStatus(PayRefundStatusEnum.WAITING.status).count(::syncRefund)

    private fun syncRefund(refund: PayRefundDO): Boolean = try {
        val response = channelService.getPayClient(requireNotNull(refund.channelId))
            .getRefund(requireNotNull(refund.orderNo), requireNotNull(refund.no))
        self().notifyRefund(requireNotNull(refund.channelId), response)
        PayRefundStatusEnum.isSuccess(response.status) || PayRefundStatusEnum.isFailure(response.status)
    } catch (ex: Throwable) {
        log.error("[syncRefund][refund({}) failed]", refund.id, ex)
        false
    }

    private fun genRefundNotifyUrl(channelId: Long): String = "${payProperties.refundNotifyUrl}/$channelId"

    private fun self(): PayRefundServiceImpl = applicationContext.getBean(PayRefundServiceImpl::class.java)

    companion object {
        private val log = LoggerFactory.getLogger(PayRefundServiceImpl::class.java)
    }
}
