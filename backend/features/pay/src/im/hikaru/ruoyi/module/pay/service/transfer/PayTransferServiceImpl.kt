package im.hikaru.ruoyi.module.pay.service.transfer

import im.hikaru.ruoyi.framework.common.exception.util.ServiceExceptionUtil.exception
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.common.util.json.JsonUtils
import im.hikaru.ruoyi.framework.tenant.core.util.TenantUtils
import im.hikaru.ruoyi.module.pay.api.transfer.dto.PayTransferCreateReqDTO
import im.hikaru.ruoyi.module.pay.api.transfer.dto.PayTransferCreateRespDTO
import im.hikaru.ruoyi.module.pay.controller.admin.transfer.vo.PayTransferPageReqVO
import im.hikaru.ruoyi.module.pay.convert.transfer.PayTransferConvert
import im.hikaru.ruoyi.module.pay.dal.dataobject.channel.PayChannelDO
import im.hikaru.ruoyi.module.pay.dal.dataobject.transfer.PayTransferDO
import im.hikaru.ruoyi.module.pay.dal.mysql.transfer.PayTransferDao
import im.hikaru.ruoyi.module.pay.enums.ErrorCodeConstants.PAY_TRANSFER_CREATE_CHANNEL_NOT_MATCH
import im.hikaru.ruoyi.module.pay.enums.ErrorCodeConstants.PAY_TRANSFER_CREATE_FAIL_STATUS_NOT_CLOSED
import im.hikaru.ruoyi.module.pay.enums.ErrorCodeConstants.PAY_TRANSFER_CREATE_PRICE_NOT_MATCH
import im.hikaru.ruoyi.module.pay.enums.ErrorCodeConstants.PAY_TRANSFER_NOT_FOUND
import im.hikaru.ruoyi.module.pay.enums.ErrorCodeConstants.PAY_TRANSFER_NOTIFY_FAIL_STATUS_IS_NOT_WAITING
import im.hikaru.ruoyi.module.pay.enums.ErrorCodeConstants.PAY_TRANSFER_NOTIFY_FAIL_STATUS_NOT_WAITING_OR_PROCESSING
import im.hikaru.ruoyi.module.pay.enums.notify.PayNotifyTypeEnum
import im.hikaru.ruoyi.module.pay.enums.transfer.PayTransferStatusEnum
import im.hikaru.ruoyi.module.pay.framework.pay.config.PayProperties
import im.hikaru.ruoyi.module.pay.framework.pay.core.PayNoGenerator
import im.hikaru.ruoyi.module.pay.framework.pay.core.client.dto.transfer.PayTransferRespDTO
import im.hikaru.ruoyi.module.pay.framework.pay.core.client.dto.transfer.PayTransferUnifiedReqDTO
import im.hikaru.ruoyi.module.pay.service.app.PayAppService
import im.hikaru.ruoyi.module.pay.service.channel.PayChannelService
import im.hikaru.ruoyi.module.pay.service.notify.PayNotifyService
import kotlinx.datetime.toKotlinLocalDateTime
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.validation.annotation.Validated
import java.time.LocalDateTime

@Service
@Validated
class PayTransferServiceImpl(
    private val payProperties: PayProperties,
    private val appService: PayAppService,
    private val channelService: PayChannelService,
    private val notifyService: PayNotifyService,
    private val applicationContext: ApplicationContext,
) : PayTransferService {

    override fun createTransfer(reqDTO: PayTransferCreateReqDTO): PayTransferCreateRespDTO {
        val app = appService.validPayApp(requireNotNull(reqDTO.appKey))
        val appId = requireNotNull(app.id)
        val channel = channelService.validPayChannel(appId, requireNotNull(reqDTO.channelCode))
        val channelId = requireNotNull(channel.id)
        val client = channelService.getPayClient(channelId)
        var transfer = validateTransferCanCreate(reqDTO, appId)

        if (transfer == null) {
            transfer = PayTransferDO().apply {
                no = PayNoGenerator.next(payProperties.transferNoPrefix)
                this.appId = appId
                this.channelId = channelId
                channelCode = reqDTO.channelCode
                userId = reqDTO.userId
                userType = reqDTO.userType
                merchantTransferId = reqDTO.merchantTransferId
                subject = reqDTO.subject
                price = reqDTO.price
                userAccount = reqDTO.userAccount
                userName = reqDTO.userName
                channelExtras = reqDTO.channelExtras
                status = PayTransferStatusEnum.WAITING.status
                notifyUrl = app.transferNotifyUrl
                userIp = reqDTO.userIp
            }
            PayTransferDao.insert(transfer)
        } else {
            val updated = PayTransferDao.updateByIdAndStatus(
                requireNotNull(transfer.id),
                requireNotNull(transfer.status),
                PayTransferDO().apply { status = PayTransferStatusEnum.WAITING.status },
            )
            if (updated == 0) throw exception(PAY_TRANSFER_CREATE_FAIL_STATUS_NOT_CLOSED)
        }

        var response: PayTransferRespDTO? = null
        try {
            response = client.unifiedTransfer(PayTransferUnifiedReqDTO().apply {
                userIp = reqDTO.userIp
                outTransferNo = transfer.no
                price = transfer.price
                subject = transfer.subject
                userAccount = transfer.userAccount
                userName = transfer.userName
                channelExtras = transfer.channelExtras
                notifyUrl = genTransferNotifyUrl(channelId)
            })
            self().notifyTransfer(channelId, response)
        } catch (ex: Throwable) {
            // A timeout can still mean the provider accepted the idempotent transfer number.
            log.error("[createTransfer][transfer({}) channel request or notify failed]", transfer.id, ex)
        }
        return PayTransferConvert.createResponse(transfer).apply {
            channelPackageInfo = response?.channelPackageInfo
        }
    }

    private fun validateTransferCanCreate(reqDTO: PayTransferCreateReqDTO, appId: Long): PayTransferDO? {
        val transfer = PayTransferDao.selectByAppIdAndMerchantTransferId(
            appId,
            requireNotNull(reqDTO.merchantTransferId),
        ) ?: return null
        if (!PayTransferStatusEnum.isClosed(transfer.status)) {
            throw exception(PAY_TRANSFER_CREATE_FAIL_STATUS_NOT_CLOSED)
        }
        if (transfer.price != reqDTO.price) throw exception(PAY_TRANSFER_CREATE_PRICE_NOT_MATCH)
        if (transfer.channelCode != reqDTO.channelCode) throw exception(PAY_TRANSFER_CREATE_CHANNEL_NOT_MATCH)
        return transfer
    }

    override fun getTransfer(id: Long): PayTransferDO =
        PayTransferDao.selectById(id) ?: throw exception(PAY_TRANSFER_NOT_FOUND)

    override fun getTransferByNo(no: String): PayTransferDO =
        PayTransferDao.selectByNo(no) ?: throw exception(PAY_TRANSFER_NOT_FOUND)

    override fun getTransferPage(pageReqVO: PayTransferPageReqVO): PageResult<PayTransferDO> =
        PayTransferDao.selectPage(pageReqVO)

    override fun syncTransfer(): Int = PayTransferDao.selectListByStatus(
        listOf(PayTransferStatusEnum.WAITING.status, PayTransferStatusEnum.PROCESSING.status),
    ).count(::syncTransfer)

    override fun syncTransfer(id: Long) {
        syncTransfer(getTransfer(id))
    }

    private fun syncTransfer(transfer: PayTransferDO): Boolean = try {
        val response = channelService.getPayClient(requireNotNull(transfer.channelId))
            .getTransfer(requireNotNull(transfer.no))
        self().notifyTransfer(requireNotNull(transfer.channelId), response)
        true
    } catch (ex: Throwable) {
        log.error("[syncTransfer][transfer({}) failed]", transfer.id, ex)
        false
    }

    @Transactional(rollbackFor = [Exception::class])
    override fun notifyTransfer(channelId: Long, notify: PayTransferRespDTO) {
        val channel = channelService.validPayChannel(channelId)
        TenantUtils.execute(channel.tenantId, Runnable { processTransferNotify(channel, notify) })
    }

    private fun processTransferNotify(channel: PayChannelDO, notify: PayTransferRespDTO) {
        when {
            PayTransferStatusEnum.isSuccess(notify.status) -> notifyTransferSuccess(channel, notify)
            PayTransferStatusEnum.isClosed(notify.status) -> notifyTransferClosed(channel, notify)
            PayTransferStatusEnum.isProcessing(notify.status) -> notifyTransferProcessing(channel, notify)
        }
    }

    private fun notifyTransferProcessing(channel: PayChannelDO, notify: PayTransferRespDTO) {
        val transfer = findTransfer(channel, notify)
        if (PayTransferStatusEnum.isProcessing(transfer.status)) {
            updateChannelPackageInfoIfAbsent(transfer, notify)
            return
        }
        if (!PayTransferStatusEnum.isWaiting(transfer.status)) {
            throw exception(PAY_TRANSFER_NOTIFY_FAIL_STATUS_IS_NOT_WAITING)
        }
        val updated = PayTransferDao.updateByIdAndStatus(
            requireNotNull(transfer.id),
            PayTransferStatusEnum.WAITING.status,
            PayTransferDO().apply {
                status = PayTransferStatusEnum.PROCESSING.status
                channelPackageInfo = notify.channelPackageInfo
            },
        )
        if (updated > 0) return

        val latest = PayTransferDao.selectById(requireNotNull(transfer.id))
        if (latest != null && PayTransferStatusEnum.isProcessing(latest.status)) {
            updateChannelPackageInfoIfAbsent(latest, notify)
            return
        }
        throw exception(PAY_TRANSFER_NOTIFY_FAIL_STATUS_IS_NOT_WAITING)
    }

    private fun updateChannelPackageInfoIfAbsent(transfer: PayTransferDO, notify: PayTransferRespDTO) {
        val packageInfo = notify.channelPackageInfo?.takeIf(String::isNotBlank) ?: return
        if (!transfer.channelPackageInfo.isNullOrBlank()) return
        PayTransferDao.updateChannelPackageInfoIfAbsent(requireNotNull(transfer.id), packageInfo)
    }

    private fun notifyTransferSuccess(channel: PayChannelDO, notify: PayTransferRespDTO) {
        val transfer = findTransfer(channel, notify)
        if (PayTransferStatusEnum.isSuccess(transfer.status)) return
        if (!PayTransferStatusEnum.isWaitingOrProcessing(transfer.status)) {
            throw exception(PAY_TRANSFER_NOTIFY_FAIL_STATUS_NOT_WAITING_OR_PROCESSING)
        }
        val updated = PayTransferDao.updateByIdAndStatus(
            requireNotNull(transfer.id),
            listOf(PayTransferStatusEnum.WAITING.status, PayTransferStatusEnum.PROCESSING.status),
            PayTransferDO().apply {
                status = PayTransferStatusEnum.SUCCESS.status
                successTime = (notify.successTime ?: LocalDateTime.now()).toKotlinLocalDateTime()
                channelTransferNo = notify.channelTransferNo
                channelNotifyData = JsonUtils.toJsonString(notify)
            },
        )
        if (updated == 0) throw exception(PAY_TRANSFER_NOTIFY_FAIL_STATUS_NOT_WAITING_OR_PROCESSING)
        notifyService.createPayNotifyTask(PayNotifyTypeEnum.TRANSFER.type, requireNotNull(transfer.id))
    }

    private fun notifyTransferClosed(channel: PayChannelDO, notify: PayTransferRespDTO) {
        val transfer = findTransfer(channel, notify)
        if (PayTransferStatusEnum.isClosed(transfer.status)) return
        if (!PayTransferStatusEnum.isWaitingOrProcessing(transfer.status)) {
            throw exception(PAY_TRANSFER_NOTIFY_FAIL_STATUS_NOT_WAITING_OR_PROCESSING)
        }
        val updated = PayTransferDao.updateByIdAndStatus(
            requireNotNull(transfer.id),
            listOf(PayTransferStatusEnum.WAITING.status, PayTransferStatusEnum.PROCESSING.status),
            PayTransferDO().apply {
                status = PayTransferStatusEnum.CLOSED.status
                channelTransferNo = notify.channelTransferNo
                channelNotifyData = JsonUtils.toJsonString(notify)
                channelErrorCode = notify.channelErrorCode
                channelErrorMsg = notify.channelErrorMsg
            },
        )
        if (updated == 0) throw exception(PAY_TRANSFER_NOTIFY_FAIL_STATUS_NOT_WAITING_OR_PROCESSING)
        notifyService.createPayNotifyTask(PayNotifyTypeEnum.TRANSFER.type, requireNotNull(transfer.id))
    }

    private fun findTransfer(channel: PayChannelDO, notify: PayTransferRespDTO): PayTransferDO =
        PayTransferDao.selectByAppIdAndNo(
            requireNotNull(channel.appId),
            requireNotNull(notify.outTransferNo),
        ) ?: throw exception(PAY_TRANSFER_NOT_FOUND)

    private fun genTransferNotifyUrl(channelId: Long): String = "${payProperties.transferNotifyUrl}/$channelId"

    private fun self(): PayTransferServiceImpl = applicationContext.getBean(PayTransferServiceImpl::class.java)

    companion object {
        private val log = LoggerFactory.getLogger(PayTransferServiceImpl::class.java)
    }
}
