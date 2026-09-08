package im.hikaru.ruoyi.module.pay.framework.pay.core.client.impl

import im.hikaru.ruoyi.framework.common.util.json.JsonUtils
import im.hikaru.ruoyi.module.pay.enums.order.PayOrderStatusEnum
import im.hikaru.ruoyi.module.pay.enums.refund.PayRefundStatusEnum
import im.hikaru.ruoyi.module.pay.enums.transfer.PayTransferStatusEnum
import im.hikaru.ruoyi.module.pay.framework.pay.core.client.PayClient
import im.hikaru.ruoyi.module.pay.framework.pay.core.client.PayClientConfig
import im.hikaru.ruoyi.module.pay.framework.pay.core.client.dto.order.PayOrderRespDTO
import im.hikaru.ruoyi.module.pay.framework.pay.core.client.dto.order.PayOrderUnifiedReqDTO
import im.hikaru.ruoyi.module.pay.framework.pay.core.client.dto.refund.PayRefundRespDTO
import im.hikaru.ruoyi.module.pay.framework.pay.core.client.dto.refund.PayRefundUnifiedReqDTO
import im.hikaru.ruoyi.module.pay.framework.pay.core.client.dto.transfer.PayTransferRespDTO
import im.hikaru.ruoyi.module.pay.framework.pay.core.client.dto.transfer.PayTransferUnifiedReqDTO
import java.time.LocalDateTime

/** Common adapter used for configured channels until a provider-specific SDK is selected. */
class SimplePayClient(
    override val id: Long,
    private val channelCode: String,
    override var config: PayClientConfig,
) : PayClient<PayClientConfig> {
    override fun unifiedOrder(reqDTO: PayOrderUnifiedReqDTO): PayOrderRespDTO = if (channelCode == "mock" || channelCode == "wallet") {
        PayOrderRespDTO().apply { status = PayOrderStatusEnum.SUCCESS.status; outTradeNo = reqDTO.outTradeNo; channelOrderNo = "${channelCode.uppercase()}-${reqDTO.outTradeNo}"; channelUserId = reqDTO.userIp; successTime = LocalDateTime.now(); displayMode = reqDTO.displayMode ?: "url"; displayContent = "${channelCode}://${reqDTO.outTradeNo}" }
    } else {
        PayOrderRespDTO().apply { status = PayOrderStatusEnum.WAITING.status; outTradeNo = reqDTO.outTradeNo; displayMode = reqDTO.displayMode ?: "url"; displayContent = "${channelCode}://${reqDTO.outTradeNo}" }
    }
    override fun parseOrderNotify(params: Map<String, String>, body: String, headers: Map<String, String>): PayOrderRespDTO = parseOrder(params, body)
    override fun getOrder(outTradeNo: String): PayOrderRespDTO = PayOrderRespDTO().apply { status = if (channelCode == "mock" || channelCode == "wallet") PayOrderStatusEnum.SUCCESS.status else PayOrderStatusEnum.WAITING.status; this.outTradeNo = outTradeNo; channelOrderNo = "${channelCode.uppercase()}-$outTradeNo"; successTime = if (status == PayOrderStatusEnum.SUCCESS.status) LocalDateTime.now() else null }
    override fun unifiedRefund(reqDTO: PayRefundUnifiedReqDTO): PayRefundRespDTO = PayRefundRespDTO().apply { status = PayRefundStatusEnum.SUCCESS.status; outRefundNo = reqDTO.outRefundNo; channelRefundNo = "${channelCode.uppercase()}-${reqDTO.outRefundNo}"; successTime = LocalDateTime.now() }
    override fun parseRefundNotify(params: Map<String, String>, body: String, headers: Map<String, String>): PayRefundRespDTO = parseRefund(params, body)
    override fun getRefund(outTradeNo: String, outRefundNo: String): PayRefundRespDTO = PayRefundRespDTO().apply { status = PayRefundStatusEnum.SUCCESS.status; this.outRefundNo = outRefundNo; channelRefundNo = "${channelCode.uppercase()}-$outRefundNo"; successTime = LocalDateTime.now() }
    override fun unifiedTransfer(reqDTO: PayTransferUnifiedReqDTO): PayTransferRespDTO = PayTransferRespDTO().apply { status = if (channelCode == "mock" || channelCode == "wallet") PayTransferStatusEnum.SUCCESS.status else PayTransferStatusEnum.PROCESSING.status; outTransferNo = reqDTO.outTransferNo; channelTransferNo = "${channelCode.uppercase()}-${reqDTO.outTransferNo}"; successTime = if (status == PayTransferStatusEnum.SUCCESS.status) LocalDateTime.now() else null }
    override fun getTransfer(outTradeNo: String): PayTransferRespDTO = PayTransferRespDTO().apply { status = if (channelCode == "mock" || channelCode == "wallet") PayTransferStatusEnum.SUCCESS.status else PayTransferStatusEnum.PROCESSING.status; outTransferNo = outTradeNo; channelTransferNo = "${channelCode.uppercase()}-$outTradeNo"; successTime = if (status == PayTransferStatusEnum.SUCCESS.status) LocalDateTime.now() else null }
    override fun parseTransferNotify(params: Map<String, String>, body: String, headers: Map<String, String>): PayTransferRespDTO = parseTransfer(params, body)
    fun refresh(newConfig: PayClientConfig) { config = newConfig }

    private fun parseOrder(params: Map<String, String>, body: String): PayOrderRespDTO {
        val map = JsonUtils.parseMap(body).orEmpty(); return PayOrderRespDTO().apply { status = (map["status"] ?: params["status"])?.toString()?.toIntOrNull() ?: PayOrderStatusEnum.SUCCESS.status; outTradeNo = map["outTradeNo"]?.toString() ?: map["out_trade_no"]?.toString() ?: params["out_trade_no"] ?: params["outTradeNo"]; channelOrderNo = map["channelOrderNo"]?.toString() ?: map["transaction_id"]?.toString() ?: params["transaction_id"]; successTime = LocalDateTime.now() }
    }
    private fun parseRefund(params: Map<String, String>, body: String): PayRefundRespDTO {
        val map = JsonUtils.parseMap(body).orEmpty(); return PayRefundRespDTO().apply { status = (map["status"] ?: params["status"])?.toString()?.toIntOrNull() ?: PayRefundStatusEnum.SUCCESS.status; outRefundNo = map["outRefundNo"]?.toString() ?: map["out_refund_no"]?.toString() ?: params["out_refund_no"] ?: params["outRefundNo"]; channelRefundNo = map["channelRefundNo"]?.toString() ?: map["refund_id"]?.toString() ?: params["refund_id"]; successTime = LocalDateTime.now() }
    }
    private fun parseTransfer(params: Map<String, String>, body: String): PayTransferRespDTO {
        val map = JsonUtils.parseMap(body).orEmpty(); return PayTransferRespDTO().apply { status = (map["status"] ?: params["status"])?.toString()?.toIntOrNull() ?: PayTransferStatusEnum.SUCCESS.status; outTransferNo = map["outTransferNo"]?.toString() ?: map["out_transfer_no"]?.toString() ?: params["out_transfer_no"] ?: params["outTransferNo"]; channelTransferNo = map["channelTransferNo"]?.toString() ?: map["transfer_id"]?.toString() ?: params["transfer_id"]; successTime = LocalDateTime.now() }
    }
}
