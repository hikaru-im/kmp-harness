package im.hikaru.ruoyi.module.pay.api.refund

import im.hikaru.ruoyi.module.pay.api.refund.dto.PayRefundCreateReqDTO
import im.hikaru.ruoyi.module.pay.api.refund.dto.PayRefundRespDTO
import jakarta.validation.Valid

interface PayRefundApi {
    fun createRefund(reqDTO: PayRefundCreateReqDTO): Long
    fun getRefund(id: Long): PayRefundRespDTO
}
