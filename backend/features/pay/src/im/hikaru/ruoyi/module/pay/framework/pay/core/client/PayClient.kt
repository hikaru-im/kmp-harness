package im.hikaru.ruoyi.module.pay.framework.pay.core.client

import im.hikaru.ruoyi.module.pay.framework.pay.core.client.dto.order.PayOrderRespDTO
import im.hikaru.ruoyi.module.pay.framework.pay.core.client.dto.order.PayOrderUnifiedReqDTO
import im.hikaru.ruoyi.module.pay.framework.pay.core.client.dto.refund.PayRefundRespDTO
import im.hikaru.ruoyi.module.pay.framework.pay.core.client.dto.refund.PayRefundUnifiedReqDTO
import im.hikaru.ruoyi.module.pay.framework.pay.core.client.dto.transfer.PayTransferRespDTO
import im.hikaru.ruoyi.module.pay.framework.pay.core.client.dto.transfer.PayTransferUnifiedReqDTO

interface PayClient<Config> {
    val id: Long
    val config: Config

    fun unifiedOrder(reqDTO: PayOrderUnifiedReqDTO): PayOrderRespDTO
    fun parseOrderNotify(params: Map<String, String>, body: String, headers: Map<String, String>): PayOrderRespDTO
    fun getOrder(outTradeNo: String): PayOrderRespDTO
    fun unifiedRefund(reqDTO: PayRefundUnifiedReqDTO): PayRefundRespDTO
    fun parseRefundNotify(params: Map<String, String>, body: String, headers: Map<String, String>): PayRefundRespDTO
    fun getRefund(outTradeNo: String, outRefundNo: String): PayRefundRespDTO
    fun unifiedTransfer(reqDTO: PayTransferUnifiedReqDTO): PayTransferRespDTO
    fun getTransfer(outTradeNo: String): PayTransferRespDTO
    fun parseTransferNotify(params: Map<String, String>, body: String, headers: Map<String, String>): PayTransferRespDTO
}
