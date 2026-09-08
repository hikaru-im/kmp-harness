package im.hikaru.ruoyi.module.pay.api.order

import im.hikaru.ruoyi.module.pay.api.order.dto.PayOrderCreateReqDTO
import im.hikaru.ruoyi.module.pay.api.order.dto.PayOrderRespDTO
import im.hikaru.ruoyi.module.pay.convert.order.PayOrderConvert
import im.hikaru.ruoyi.module.pay.service.order.PayOrderService
import org.springframework.stereotype.Service
import org.springframework.validation.annotation.Validated

@Service
@Validated
class PayOrderApiImpl(
    private val payOrderService: PayOrderService,
) : PayOrderApi {
    override fun createOrder(reqDTO: PayOrderCreateReqDTO): Long = payOrderService.createOrder(reqDTO)

    override fun getOrder(id: Long): PayOrderRespDTO = PayOrderConvert.convertApi(payOrderService.getOrder(id))

    override fun updatePayOrderPrice(id: Long, payPrice: Int) {
        payOrderService.updatePayOrderPrice(id, payPrice)
    }
}
