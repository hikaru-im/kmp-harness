package im.hikaru.ruoyi.module.pay.api.refund

import im.hikaru.ruoyi.module.pay.api.refund.dto.PayRefundCreateReqDTO
import im.hikaru.ruoyi.module.pay.api.refund.dto.PayRefundRespDTO
import im.hikaru.ruoyi.module.pay.convert.refund.PayRefundConvert
import im.hikaru.ruoyi.module.pay.service.refund.PayRefundService
import org.springframework.stereotype.Service
import org.springframework.validation.annotation.Validated

@Service
@Validated
class PayRefundApiImpl(
    private val payRefundService: PayRefundService,
) : PayRefundApi {
    override fun createRefund(reqDTO: PayRefundCreateReqDTO): Long = payRefundService.createRefund(reqDTO)

    override fun getRefund(id: Long): PayRefundRespDTO = PayRefundConvert.convertApi(payRefundService.getRefund(id))
}
