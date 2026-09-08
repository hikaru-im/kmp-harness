package im.hikaru.ruoyi.module.pay.service.refund

import im.hikaru.ruoyi.framework.common.pojo.*
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.pay.api.refund.dto.PayRefundCreateReqDTO
import im.hikaru.ruoyi.module.pay.controller.admin.refund.vo.PayRefundExportReqVO
import im.hikaru.ruoyi.module.pay.controller.admin.refund.vo.PayRefundPageReqVO
import im.hikaru.ruoyi.module.pay.dal.dataobject.refund.PayRefundDO
import im.hikaru.ruoyi.module.pay.framework.pay.core.client.dto.refund.PayRefundRespDTO

interface PayRefundService {
    fun getRefund(id: Long): PayRefundDO
    fun getRefundByNo(no: String): PayRefundDO
    fun getRefundCountByAppId(appId: Long): Long
    fun getRefundPage(pageReqVO: PayRefundPageReqVO): PageResult<PayRefundDO>
    fun getRefundList(exportReqVO: PayRefundExportReqVO): List<PayRefundDO>
    fun createRefund(reqDTO: PayRefundCreateReqDTO): Long
    fun notifyRefund(channelId: Long, notify: PayRefundRespDTO): Unit
    fun syncRefund(): Int
}
