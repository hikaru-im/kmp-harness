package im.hikaru.ruoyi.module.pay.framework.pay.core.client.dto.refund

import im.hikaru.ruoyi.module.pay.enums.refund.PayRefundStatusEnum
import im.hikaru.ruoyi.module.pay.framework.pay.core.client.exception.PayClientException
import java.time.LocalDateTime

class PayRefundRespDTO {
    var status: Int? = null
    var outRefundNo: String? = null
    var channelRefundNo: String? = null
    var successTime: LocalDateTime? = null
    var rawData: Any? = null
    var channelErrorCode: String? = null
    var channelErrorMsg: String? = null

    companion object {
        fun successOf(channelRefundNo: String?, successTime: LocalDateTime?, outRefundNo: String?, rawData: Any?) =
            PayRefundRespDTO().apply {
                status = PayRefundStatusEnum.SUCCESS.status
                this.channelRefundNo = channelRefundNo
                this.successTime = successTime
                this.outRefundNo = outRefundNo
                this.rawData = rawData
            }

        fun waitingOf(channelRefundNo: String?, outRefundNo: String?, rawData: Any?) =
            PayRefundRespDTO().apply {
                status = PayRefundStatusEnum.WAITING.status
                this.channelRefundNo = channelRefundNo
                this.outRefundNo = outRefundNo
                this.rawData = rawData
            }

        fun failureOf(errorCode: String?, errorMessage: String?, outRefundNo: String?, rawData: Any?) =
            PayRefundRespDTO().apply {
                status = PayRefundStatusEnum.FAILURE.status
                channelErrorCode = errorCode
                channelErrorMsg = errorMessage
                this.outRefundNo = outRefundNo
                this.rawData = rawData
            }

        fun failureOf(outRefundNo: String?, rawData: Any?) = failureOf(null, null, outRefundNo, rawData)
    }
}
