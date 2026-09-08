package im.hikaru.ruoyi.module.pay.service.transfer

import im.hikaru.ruoyi.framework.common.pojo.*
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.pay.api.transfer.dto.PayTransferCreateReqDTO
import im.hikaru.ruoyi.module.pay.api.transfer.dto.PayTransferCreateRespDTO
import im.hikaru.ruoyi.module.pay.controller.admin.transfer.vo.PayTransferPageReqVO
import im.hikaru.ruoyi.module.pay.dal.dataobject.transfer.PayTransferDO
import im.hikaru.ruoyi.module.pay.framework.pay.core.client.dto.transfer.PayTransferRespDTO
import jakarta.validation.Valid

interface PayTransferService {
    fun createTransfer(@Valid reqDTO: PayTransferCreateReqDTO): PayTransferCreateRespDTO
    fun getTransfer(id: Long): PayTransferDO
    fun getTransferByNo(no: String): PayTransferDO
    fun getTransferPage(pageReqVO: PayTransferPageReqVO): PageResult<PayTransferDO>
    fun syncTransfer(): Int
    fun syncTransfer(id: Long): Unit
    fun notifyTransfer(channelId: Long, notify: PayTransferRespDTO): Unit
}
