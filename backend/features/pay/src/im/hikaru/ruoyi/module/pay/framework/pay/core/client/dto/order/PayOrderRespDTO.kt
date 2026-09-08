package im.hikaru.ruoyi.module.pay.framework.pay.core.client.dto.order

import im.hikaru.ruoyi.module.pay.enums.order.PayOrderStatusEnum
import im.hikaru.ruoyi.module.pay.framework.pay.core.client.exception.PayClientException
import im.hikaru.ruoyi.module.pay.framework.pay.core.enums.PayOrderDisplayModeEnum
import java.time.LocalDateTime

class PayOrderRespDTO {
    var status: Int? = null
    var outTradeNo: String? = null
    var channelOrderNo: String? = null
    var channelUserId: String? = null
    var successTime: LocalDateTime? = null
    var rawData: Any? = null
    var displayMode: String? = null
    var displayContent: String? = null
    var channelErrorCode: String? = null
    var channelErrorMsg: String? = null

    fun setDisplayMode(value: String?): PayOrderRespDTO = apply { displayMode = value }
    fun setDisplayContent(value: String?): PayOrderRespDTO = apply { displayContent = value }

    companion object {
        fun of(
            status: Int?,
            channelOrderNo: String?,
            channelUserId: String?,
            successTime: LocalDateTime?,
            outTradeNo: String?,
            rawData: Any?,
        ) = PayOrderRespDTO().apply {
            this.status = status
            this.channelOrderNo = channelOrderNo
            this.channelUserId = channelUserId
            this.successTime = successTime
            this.outTradeNo = outTradeNo
            this.rawData = rawData
        }

        fun waitingOf(displayMode: String?, displayContent: String?, outTradeNo: String?, rawData: Any?) =
            PayOrderRespDTO().apply {
                status = PayOrderStatusEnum.WAITING.status
                this.displayMode = displayMode
                this.displayContent = displayContent
                this.outTradeNo = outTradeNo
                this.rawData = rawData
            }

        fun successOf(
            channelOrderNo: String?,
            channelUserId: String?,
            successTime: LocalDateTime?,
            outTradeNo: String?,
            rawData: Any?,
        ) = of(PayOrderStatusEnum.SUCCESS.status, channelOrderNo, channelUserId, successTime, outTradeNo, rawData)

        fun closedOf(errorCode: String?, errorMessage: String?, outTradeNo: String?, rawData: Any?) =
            PayOrderRespDTO().apply {
                status = PayOrderStatusEnum.CLOSED.status
                channelErrorCode = errorCode
                channelErrorMsg = errorMessage
                this.outTradeNo = outTradeNo
                this.rawData = rawData
            }
    }
}
