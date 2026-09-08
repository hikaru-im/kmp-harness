package im.hikaru.ruoyi.module.pay.api.order

import im.hikaru.ruoyi.module.pay.api.order.dto.PayOrderCreateReqDTO
import im.hikaru.ruoyi.module.pay.api.order.dto.PayOrderRespDTO
import jakarta.validation.Valid

interface PayOrderApi {
    fun createOrder(reqDTO: PayOrderCreateReqDTO): Long
    fun getOrder(id: Long): PayOrderRespDTO
    fun updatePayOrderPrice(id: Long, payPrice: Int): Unit
}
