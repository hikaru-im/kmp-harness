package im.hikaru.ruoyi.module.pay.service.demo

import im.hikaru.ruoyi.framework.common.enums.UserTypeEnum
import im.hikaru.ruoyi.framework.common.exception.util.ServiceExceptionUtil.exception
import im.hikaru.ruoyi.framework.common.pojo.PageParam
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.common.util.servlet.ServletUtils
import im.hikaru.ruoyi.module.pay.api.transfer.PayTransferApi
import im.hikaru.ruoyi.module.pay.api.transfer.dto.PayTransferCreateReqDTO
import im.hikaru.ruoyi.module.pay.api.transfer.dto.PayTransferRespDTO
import im.hikaru.ruoyi.module.pay.controller.admin.demo.vo.withdraw.PayDemoWithdrawCreateReqVO
import im.hikaru.ruoyi.module.pay.dal.dataobject.demo.PayDemoWithdrawDO
import im.hikaru.ruoyi.module.pay.dal.mysql.demo.PayDemoWithdrawDao
import im.hikaru.ruoyi.module.pay.enums.ErrorCodeConstants.DEMO_WITHDRAW_NOT_FOUND
import im.hikaru.ruoyi.module.pay.enums.ErrorCodeConstants.DEMO_WITHDRAW_TRANSFER_FAIL_STATUS_NOT_WAITING_OR_CLOSED
import im.hikaru.ruoyi.module.pay.enums.ErrorCodeConstants.DEMO_WITHDRAW_UPDATE_STATUS_FAIL_PAY_CHANNEL_NOT_MATCH
import im.hikaru.ruoyi.module.pay.enums.ErrorCodeConstants.DEMO_WITHDRAW_UPDATE_STATUS_FAIL_PAY_MERCHANT_EXISTS
import im.hikaru.ruoyi.module.pay.enums.ErrorCodeConstants.DEMO_WITHDRAW_UPDATE_STATUS_FAIL_PAY_PRICE_NOT_MATCH
import im.hikaru.ruoyi.module.pay.enums.ErrorCodeConstants.DEMO_WITHDRAW_UPDATE_STATUS_FAIL_PAY_TRANSFER_ID_ERROR
import im.hikaru.ruoyi.module.pay.enums.ErrorCodeConstants.DEMO_WITHDRAW_UPDATE_STATUS_FAIL_PAY_TRANSFER_STATUS_NOT_SUCCESS_OR_CLOSED
import im.hikaru.ruoyi.module.pay.enums.PayChannelEnum
import im.hikaru.ruoyi.module.pay.enums.demo.PayDemoWithdrawStatusEnum
import im.hikaru.ruoyi.module.pay.enums.demo.PayDemoWithdrawTypeEnum
import im.hikaru.ruoyi.module.pay.enums.transfer.PayTransferStatusEnum
import kotlinx.datetime.toKotlinLocalDateTime
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.validation.annotation.Validated

@Service
@Validated
class PayDemoWithdrawServiceImpl(
    private val payTransferApi: PayTransferApi,
) : PayDemoWithdrawService {

    override fun createDemoWithdraw(createReqVO: PayDemoWithdrawCreateReqVO): Long {
        val withdraw = PayDemoWithdrawDO().apply {
            subject = createReqVO.subject
            price = createReqVO.price
            userAccount = createReqVO.userAccount
            userName = createReqVO.userName
            type = createReqVO.type
            transferChannelCode = channelCode(createReqVO.type)
            status = PayDemoWithdrawStatusEnum.WAITING.status
        }
        return PayDemoWithdrawDao.insert(withdraw)
    }

    @Transactional(rollbackFor = [Exception::class])
    override fun transferDemoWithdraw(id: Long, userId: Long): Long {
        val withdraw = validateCanTransfer(id)
        if (PayDemoWithdrawStatusEnum.isClosed(withdraw.status)) {
            val updated = PayDemoWithdrawDao.updateByIdAndStatus(
                id,
                requireNotNull(withdraw.status),
                PayDemoWithdrawDO().apply {
                    status = PayDemoWithdrawStatusEnum.WAITING.status
                    transferErrorMsg = ""
                },
            )
            if (updated == 0) throw exception(DEMO_WITHDRAW_TRANSFER_FAIL_STATUS_NOT_WAITING_OR_CLOSED)
            withdraw.status = PayDemoWithdrawStatusEnum.WAITING.status
        }
        val response = payTransferApi.createTransfer(PayTransferCreateReqDTO().apply {
            appKey = PAY_APP_KEY
            channelCode = withdraw.transferChannelCode
            userIp = ServletUtils.getClientIP() ?: "127.0.0.1"
            this.userId = userId
            userType = UserTypeEnum.ADMIN.value
            merchantTransferId = id.toString()
            subject = withdraw.subject
            price = withdraw.price
            userAccount = withdraw.userAccount
            userName = withdraw.userName
            if (withdraw.type == PayDemoWithdrawTypeEnum.WECHAT.type) {
                channelExtras = PayTransferCreateReqDTO.buildWeiXinChannelExtra1000("Demo activity", "Demo reward")
            }
        })
        val payTransferId = requireNotNull(response.id)
        val updated = PayDemoWithdrawDao.updateByIdAndStatus(
            id,
            requireNotNull(withdraw.status),
            PayDemoWithdrawDO().apply { this.payTransferId = payTransferId },
        )
        if (updated == 0) throw exception(DEMO_WITHDRAW_TRANSFER_FAIL_STATUS_NOT_WAITING_OR_CLOSED)
        return payTransferId
    }

    override fun getDemoWithdrawPage(pageVO: PageParam): PageResult<PayDemoWithdrawDO> =
        PayDemoWithdrawDao.selectPage(pageVO)

    override fun updateDemoWithdrawTransferred(id: Long, payTransferId: Long) {
        val withdraw = PayDemoWithdrawDao.selectById(id) ?: throw exception(DEMO_WITHDRAW_NOT_FOUND)
        if (PayDemoWithdrawStatusEnum.isSuccess(withdraw.status) || PayDemoWithdrawStatusEnum.isClosed(withdraw.status)) {
            if (withdraw.payTransferId == payTransferId) return
            throw exception(DEMO_WITHDRAW_UPDATE_STATUS_FAIL_PAY_TRANSFER_ID_ERROR)
        }
        val payTransfer = validateTransfer(withdraw, payTransferId)
        val newStatus = when {
            PayTransferStatusEnum.isSuccess(payTransfer.status) -> PayDemoWithdrawStatusEnum.SUCCESS.status
            PayTransferStatusEnum.isClosed(payTransfer.status) -> PayDemoWithdrawStatusEnum.CLOSED.status
            else -> throw exception(DEMO_WITHDRAW_UPDATE_STATUS_FAIL_PAY_TRANSFER_STATUS_NOT_SUCCESS_OR_CLOSED)
        }
        PayDemoWithdrawDao.updateByIdAndStatus(
            id,
            requireNotNull(withdraw.status),
            PayDemoWithdrawDO().apply {
                status = newStatus
                transferTime = payTransfer.successTime?.toKotlinLocalDateTime()
                transferErrorMsg = payTransfer.channelErrorMsg
            },
        )
    }

    private fun validateCanTransfer(id: Long): PayDemoWithdrawDO {
        val withdraw = PayDemoWithdrawDao.selectById(id) ?: throw exception(DEMO_WITHDRAW_NOT_FOUND)
        if (!PayDemoWithdrawStatusEnum.isWaiting(withdraw.status) && !PayDemoWithdrawStatusEnum.isClosed(withdraw.status)) {
            throw exception(DEMO_WITHDRAW_TRANSFER_FAIL_STATUS_NOT_WAITING_OR_CLOSED)
        }
        return withdraw
    }

    private fun validateTransfer(withdraw: PayDemoWithdrawDO, payTransferId: Long): PayTransferRespDTO {
        if (withdraw.payTransferId != payTransferId) {
            throw exception(DEMO_WITHDRAW_UPDATE_STATUS_FAIL_PAY_TRANSFER_ID_ERROR)
        }
        val payTransfer = payTransferApi.getTransfer(payTransferId)
        if (!PayTransferStatusEnum.isSuccessOrClosed(payTransfer.status)) {
            throw exception(DEMO_WITHDRAW_UPDATE_STATUS_FAIL_PAY_TRANSFER_STATUS_NOT_SUCCESS_OR_CLOSED)
        }
        if (payTransfer.price != withdraw.price) {
            throw exception(DEMO_WITHDRAW_UPDATE_STATUS_FAIL_PAY_PRICE_NOT_MATCH)
        }
        if (payTransfer.merchantTransferId != withdraw.id.toString()) {
            throw exception(DEMO_WITHDRAW_UPDATE_STATUS_FAIL_PAY_MERCHANT_EXISTS)
        }
        if (payTransfer.channelCode != withdraw.transferChannelCode) {
            throw exception(DEMO_WITHDRAW_UPDATE_STATUS_FAIL_PAY_CHANNEL_NOT_MATCH)
        }
        return payTransfer
    }

    private fun channelCode(type: Int?): String = when (type) {
        PayDemoWithdrawTypeEnum.ALIPAY.type -> PayChannelEnum.ALIPAY_PC.code
        PayDemoWithdrawTypeEnum.WECHAT.type -> PayChannelEnum.WX_LITE.code
        PayDemoWithdrawTypeEnum.WALLET.type -> PayChannelEnum.WALLET.code
        else -> throw IllegalArgumentException("Unknown withdraw type: $type")
    }

    private companion object {
        const val PAY_APP_KEY = "demo"
    }
}
