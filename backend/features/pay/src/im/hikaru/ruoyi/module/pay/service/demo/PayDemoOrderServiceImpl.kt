package im.hikaru.ruoyi.module.pay.service.demo

import im.hikaru.ruoyi.framework.common.enums.UserTypeEnum
import im.hikaru.ruoyi.framework.common.exception.util.ServiceExceptionUtil.exception
import im.hikaru.ruoyi.framework.common.pojo.PageParam
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.common.util.servlet.ServletUtils
import im.hikaru.ruoyi.module.pay.api.order.PayOrderApi
import im.hikaru.ruoyi.module.pay.api.order.dto.PayOrderCreateReqDTO
import im.hikaru.ruoyi.module.pay.api.order.dto.PayOrderRespDTO
import im.hikaru.ruoyi.module.pay.api.refund.PayRefundApi
import im.hikaru.ruoyi.module.pay.api.refund.dto.PayRefundCreateReqDTO
import im.hikaru.ruoyi.module.pay.api.refund.dto.PayRefundRespDTO
import im.hikaru.ruoyi.module.pay.controller.admin.demo.vo.order.PayDemoOrderCreateReqVO
import im.hikaru.ruoyi.module.pay.dal.dataobject.demo.PayDemoOrderDO
import im.hikaru.ruoyi.module.pay.dal.mysql.demo.PayDemoOrderDao
import im.hikaru.ruoyi.module.pay.enums.ErrorCodeConstants.DEMO_ORDER_NOT_FOUND
import im.hikaru.ruoyi.module.pay.enums.ErrorCodeConstants.DEMO_ORDER_REFUND_FAIL_NOT_PAID
import im.hikaru.ruoyi.module.pay.enums.ErrorCodeConstants.DEMO_ORDER_REFUND_FAIL_REFUNDED
import im.hikaru.ruoyi.module.pay.enums.ErrorCodeConstants.DEMO_ORDER_REFUND_FAIL_REFUND_NOT_FOUND
import im.hikaru.ruoyi.module.pay.enums.ErrorCodeConstants.DEMO_ORDER_REFUND_FAIL_REFUND_NOT_SUCCESS
import im.hikaru.ruoyi.module.pay.enums.ErrorCodeConstants.DEMO_ORDER_REFUND_FAIL_REFUND_ORDER_ID_ERROR
import im.hikaru.ruoyi.module.pay.enums.ErrorCodeConstants.DEMO_ORDER_REFUND_FAIL_REFUND_PRICE_NOT_MATCH
import im.hikaru.ruoyi.module.pay.enums.ErrorCodeConstants.DEMO_ORDER_UPDATE_PAID_FAIL_PAY_ORDER_ID_ERROR
import im.hikaru.ruoyi.module.pay.enums.ErrorCodeConstants.DEMO_ORDER_UPDATE_PAID_FAIL_PAY_ORDER_STATUS_NOT_SUCCESS
import im.hikaru.ruoyi.module.pay.enums.ErrorCodeConstants.DEMO_ORDER_UPDATE_PAID_FAIL_PAY_PRICE_NOT_MATCH
import im.hikaru.ruoyi.module.pay.enums.ErrorCodeConstants.DEMO_ORDER_UPDATE_PAID_STATUS_NOT_UNPAID
import im.hikaru.ruoyi.module.pay.enums.order.PayOrderStatusEnum
import im.hikaru.ruoyi.module.pay.enums.refund.PayRefundStatusEnum
import java.time.LocalDateTime
import kotlinx.datetime.toKotlinLocalDateTime
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.validation.annotation.Validated

@Service
@Validated
class PayDemoOrderServiceImpl(
    private val payOrderApi: PayOrderApi,
    private val payRefundApi: PayRefundApi,
) : PayDemoOrderService {

    private val products = mapOf(
        1L to Product("Huawei Phone", 1),
        2L to Product("Xiaomi TV", 10),
        3L to Product("Apple Watch", 100),
        4L to Product("Asus Laptop", 1_000),
        5L to Product("Nio Car", 200_000),
    )

    @Transactional(rollbackFor = [Exception::class])
    override fun createDemoOrder(userId: Long, createReqVO: PayDemoOrderCreateReqVO): Long {
        val product = requireNotNull(products[createReqVO.spuId]) { "Product ${createReqVO.spuId} does not exist" }
        val order = PayDemoOrderDO().apply {
            this.userId = userId
            spuId = createReqVO.spuId
            spuName = product.name
            price = product.price
            payStatus = false
            refundPrice = 0
        }
        PayDemoOrderDao.insert(order)
        val payOrderId = payOrderApi.createOrder(PayOrderCreateReqDTO().apply {
            appKey = PAY_APP_KEY
            userIp = ServletUtils.getClientIP() ?: "127.0.0.1"
            this.userId = userId
            userType = UserTypeEnum.ADMIN.value
            merchantOrderId = requireNotNull(order.id).toString()
            subject = product.name
            body = ""
            price = product.price
            expireTime = LocalDateTime.now().plusHours(2)
        })
        PayDemoOrderDao.updateById(PayDemoOrderDO().apply {
            id = order.id
            this.payOrderId = payOrderId
        })
        return requireNotNull(order.id)
    }

    override fun getDemoOrder(id: Long): PayDemoOrderDO =
        PayDemoOrderDao.selectById(id) ?: throw exception(DEMO_ORDER_NOT_FOUND)

    override fun getDemoOrderPage(pageReqVO: PageParam): PageResult<PayDemoOrderDO> =
        PayDemoOrderDao.selectPage(pageReqVO)

    @Transactional(rollbackFor = [Exception::class])
    override fun updateDemoOrderPaid(id: Long, payOrderId: Long) {
        val order = getDemoOrder(id)
        if (order.payStatus == true) {
            if (order.payOrderId == payOrderId) return
            throw exception(DEMO_ORDER_UPDATE_PAID_FAIL_PAY_ORDER_ID_ERROR)
        }
        val payOrder = validatePayOrderPaid(order, payOrderId)
        val updated = PayDemoOrderDao.updateByIdAndPayStatus(id, false, PayDemoOrderDO().apply {
            payStatus = true
            payTime = LocalDateTime.now().toKotlinLocalDateTime()
            payChannelCode = payOrder.channelCode
        })
        if (updated == 0) throw exception(DEMO_ORDER_UPDATE_PAID_STATUS_NOT_UNPAID)
    }

    @Transactional(rollbackFor = [Exception::class])
    override fun refundDemoOrder(id: Long, userIp: String) {
        val order = validateCanRefund(id)
        val payRefundId = payRefundApi.createRefund(PayRefundCreateReqDTO().apply {
            appKey = PAY_APP_KEY
            this.userIp = userIp
            userId = order.userId
            userType = UserTypeEnum.ADMIN.value
            merchantOrderId = id.toString()
            merchantRefundId = "$id-refund"
            reason = "Demo order refund"
            price = order.price
        })
        PayDemoOrderDao.updateById(PayDemoOrderDO().apply {
            this.id = id
            this.payRefundId = payRefundId
            refundPrice = order.price
        })
    }

    override fun updateDemoOrderRefunded(id: Long, refundId: String, payRefundId: Long) {
        val payRefund = validateRefund(id, refundId, payRefundId)
        PayDemoOrderDao.updateById(PayDemoOrderDO().apply {
            this.id = id
            refundTime = payRefund.successTime?.toKotlinLocalDateTime()
        })
    }

    private fun validatePayOrderPaid(order: PayDemoOrderDO, payOrderId: Long): PayOrderRespDTO {
        if (order.payOrderId != payOrderId) throw exception(DEMO_ORDER_UPDATE_PAID_FAIL_PAY_ORDER_ID_ERROR)
        val payOrder = payOrderApi.getOrder(payOrderId)
        if (!PayOrderStatusEnum.isSuccess(payOrder.status)) {
            throw exception(DEMO_ORDER_UPDATE_PAID_FAIL_PAY_ORDER_STATUS_NOT_SUCCESS)
        }
        if (payOrder.price != order.price) throw exception(DEMO_ORDER_UPDATE_PAID_FAIL_PAY_PRICE_NOT_MATCH)
        if (payOrder.merchantOrderId != order.id.toString()) {
            throw exception(DEMO_ORDER_UPDATE_PAID_FAIL_PAY_ORDER_ID_ERROR)
        }
        return payOrder
    }

    private fun validateCanRefund(id: Long): PayDemoOrderDO {
        val order = getDemoOrder(id)
        if (order.payStatus != true) throw exception(DEMO_ORDER_REFUND_FAIL_NOT_PAID)
        if (order.payRefundId != null) throw exception(DEMO_ORDER_REFUND_FAIL_REFUNDED)
        return order
    }

    private fun validateRefund(id: Long, refundId: String, payRefundId: Long): PayRefundRespDTO {
        val order = getDemoOrder(id)
        if (order.payRefundId != payRefundId) throw exception(DEMO_ORDER_REFUND_FAIL_REFUND_ORDER_ID_ERROR)
        val payRefund = runCatching { payRefundApi.getRefund(payRefundId) }.getOrNull()
            ?: throw exception(DEMO_ORDER_REFUND_FAIL_REFUND_NOT_FOUND)
        if (!PayRefundStatusEnum.isSuccess(payRefund.status)) {
            throw exception(DEMO_ORDER_REFUND_FAIL_REFUND_NOT_SUCCESS)
        }
        if (payRefund.refundPrice != order.price) {
            throw exception(DEMO_ORDER_REFUND_FAIL_REFUND_PRICE_NOT_MATCH)
        }
        if (payRefund.merchantRefundId != refundId || refundId != "$id-refund") {
            throw exception(DEMO_ORDER_REFUND_FAIL_REFUND_ORDER_ID_ERROR)
        }
        return payRefund
    }

    private data class Product(val name: String, val price: Int)

    private companion object {
        const val PAY_APP_KEY = "demo"
    }
}
