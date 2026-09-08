package im.hikaru.ruoyi.module.pay.service.demo

import im.hikaru.ruoyi.framework.common.pojo.*
import im.hikaru.ruoyi.framework.common.pojo.PageParam
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.pay.controller.admin.demo.vo.order.PayDemoOrderCreateReqVO
import im.hikaru.ruoyi.module.pay.dal.dataobject.demo.PayDemoOrderDO
import jakarta.validation.Valid

interface PayDemoOrderService {
    fun createDemoOrder(userId: Long, @Valid createReqVO: PayDemoOrderCreateReqVO): Long
    fun getDemoOrder(id: Long): PayDemoOrderDO
    fun getDemoOrderPage(pageReqVO: PageParam): PageResult<PayDemoOrderDO>
    fun updateDemoOrderPaid(id: Long, payOrderId: Long): Unit
    fun refundDemoOrder(id: Long, userIp: String): Unit
    fun updateDemoOrderRefunded(id: Long, refundId: String, payRefundId: Long): Unit
}
