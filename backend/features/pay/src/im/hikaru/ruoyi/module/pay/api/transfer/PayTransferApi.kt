package im.hikaru.ruoyi.module.pay.api.transfer

import im.hikaru.ruoyi.module.pay.api.transfer.dto.PayTransferCreateReqDTO
import im.hikaru.ruoyi.module.pay.api.transfer.dto.PayTransferCreateRespDTO
import im.hikaru.ruoyi.module.pay.api.transfer.dto.PayTransferRespDTO
import jakarta.validation.Valid

interface PayTransferApi {
    fun createTransfer(reqDTO: PayTransferCreateReqDTO): PayTransferCreateRespDTO
    fun getTransfer(id: Long): PayTransferRespDTO
}
