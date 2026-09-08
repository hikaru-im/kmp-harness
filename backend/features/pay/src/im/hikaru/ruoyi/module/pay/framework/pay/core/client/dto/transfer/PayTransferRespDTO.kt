package im.hikaru.ruoyi.module.pay.framework.pay.core.client.dto.transfer

import im.hikaru.ruoyi.module.pay.enums.transfer.PayTransferStatusEnum
import java.time.LocalDateTime

class PayTransferRespDTO {
    var status: Int? = null
    var outTransferNo: String? = null
    var channelTransferNo: String? = null
    var successTime: LocalDateTime? = null
    var rawData: Any? = null
    var channelErrorCode: String? = null
    var channelErrorMsg: String? = null
    var channelPackageInfo: String? = null

    fun setChannelPackageInfo(value: String?): PayTransferRespDTO = apply { channelPackageInfo = value }

    companion object {
        fun successOf(channelTransferNo: String?, successTime: LocalDateTime?, outTransferNo: String?, rawData: Any?) =
            PayTransferRespDTO().apply {
                status = PayTransferStatusEnum.SUCCESS.status
                this.channelTransferNo = channelTransferNo
                this.successTime = successTime
                this.outTransferNo = outTransferNo
                this.rawData = rawData
            }

        fun processingOf(channelTransferNo: String?, outTransferNo: String?, rawData: Any?) =
            PayTransferRespDTO().apply {
                status = PayTransferStatusEnum.PROCESSING.status
                this.channelTransferNo = channelTransferNo
                this.outTransferNo = outTransferNo
                this.rawData = rawData
            }

        fun waitingOf(channelTransferNo: String?, outTransferNo: String?, rawData: Any?) =
            PayTransferRespDTO().apply {
                status = PayTransferStatusEnum.WAITING.status
                this.channelTransferNo = channelTransferNo
                this.outTransferNo = outTransferNo
                this.rawData = rawData
            }

        fun closedOf(errorCode: String?, errorMessage: String?, outTransferNo: String?, rawData: Any?) =
            PayTransferRespDTO().apply {
                status = PayTransferStatusEnum.CLOSED.status
                channelErrorCode = errorCode
                channelErrorMsg = errorMessage
                this.outTransferNo = outTransferNo
                this.rawData = rawData
            }
    }
}
