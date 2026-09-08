package im.hikaru.ruoyi.module.pay.service.order

import im.hikaru.ruoyi.framework.common.pojo.*
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.pay.api.order.dto.PayOrderCreateReqDTO
import im.hikaru.ruoyi.module.pay.controller.admin.order.vo.PayOrderExportReqVO
import im.hikaru.ruoyi.module.pay.controller.admin.order.vo.PayOrderPageReqVO
import im.hikaru.ruoyi.module.pay.controller.admin.order.vo.PayOrderSubmitReqVO
import im.hikaru.ruoyi.module.pay.controller.admin.order.vo.PayOrderSubmitRespVO
import im.hikaru.ruoyi.module.pay.dal.dataobject.order.PayOrderDO
import im.hikaru.ruoyi.module.pay.dal.dataobject.order.PayOrderExtensionDO
import im.hikaru.ruoyi.module.pay.framework.pay.core.client.dto.order.PayOrderRespDTO
import jakarta.validation.Valid
import jakarta.validation.constraints.NotEmpty
import java.time.LocalDateTime

interface PayOrderService {
    fun getOrder(id: Long): PayOrderDO
    fun getOrder(no: String): PayOrderDO
    fun getOrder(appId: Long, merchantOrderId: String): PayOrderDO
    fun getOrderList(ids: Collection<Long>): List<PayOrderDO>
    fun getOrderCountByAppId(appId: Long): Long
    fun getOrderPage(pageReqVO: PayOrderPageReqVO): PageResult<PayOrderDO>
    fun getOrderList(exportReqVO: PayOrderExportReqVO): List<PayOrderDO>
    fun createOrder(@Valid reqDTO: PayOrderCreateReqDTO): Long
    fun submitOrder(@Valid reqVO: PayOrderSubmitReqVO, @NotEmpty(message = "提交 IP 不能为空") userIp: String): PayOrderSubmitRespVO
    fun notifyOrder(channelId: Long, notify: PayOrderRespDTO): Unit
    fun updateOrderRefundPrice(id: Long, incrRefundPrice: Int): Unit
    fun updatePayOrderPrice(id: Long, payPrice: Int): Unit
    fun getOrderExtension(id: Long): PayOrderExtensionDO
    fun getOrderExtensionByNo(no: String): PayOrderExtensionDO
    fun syncOrder(minCreateTime: LocalDateTime): Int
    fun syncOrderQuietly(id: Long): Unit
    fun expireOrder(): Int
}
