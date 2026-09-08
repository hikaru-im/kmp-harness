package im.hikaru.ruoyi.module.pay.service.refund

import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.module.pay.dal.dataobject.app.PayAppDO
import im.hikaru.ruoyi.module.pay.dal.dataobject.channel.PayChannelDO
import im.hikaru.ruoyi.module.pay.dal.dataobject.refund.PayRefundDO
import im.hikaru.ruoyi.module.pay.dal.mysql.refund.PayRefundDao
import im.hikaru.ruoyi.module.pay.dal.mysql.refund.PayRefundTable
import im.hikaru.ruoyi.module.pay.enums.notify.PayNotifyTypeEnum
import im.hikaru.ruoyi.module.pay.enums.refund.PayRefundStatusEnum
import im.hikaru.ruoyi.module.pay.framework.pay.config.PayProperties
import im.hikaru.ruoyi.module.pay.framework.pay.core.client.dto.refund.PayRefundRespDTO
import im.hikaru.ruoyi.module.pay.service.app.PayAppService
import im.hikaru.ruoyi.module.pay.service.channel.PayChannelService
import im.hikaru.ruoyi.module.pay.service.notify.PayNotifyService
import im.hikaru.ruoyi.module.pay.service.order.PayOrderService
import im.hikaru.ruoyi.module.pay.test.interfaceProxy
import im.hikaru.ruoyi.module.pay.test.selfApplicationContext
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicReference
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PayRefundServiceImplTest {
    private val channel = PayChannelDO().apply {
        id = 20L
        appId = 10L
        code = "mock"
        tenantId = 1L
    }
    private val notifyTasks = mutableListOf<Pair<Int, Long>>()
    private val orderRefundUpdates = mutableListOf<Pair<Long, Int>>()
    private lateinit var service: PayRefundServiceImpl

    @BeforeEach
    fun setUp() {
        Database.connect(
            "jdbc:h2:mem:pay_refund_${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
        )
        transaction { SchemaUtils.create(PayRefundTable) }
        TenantContextHolder.setTenantId(1L)

        val appService = interfaceProxy<PayAppService> { _, _ ->
            PayAppDO().apply { id = 10L; tenantId = 1L }
        }
        val orderService = interfaceProxy<PayOrderService> { method, args ->
            when (method) {
                "updateOrderRefundPrice" -> {
                    orderRefundUpdates += (args?.get(0) as Long) to (args[1] as Int)
                    Unit
                }
                else -> null
            }
        }
        val channelService = interfaceProxy<PayChannelService> { method, _ ->
            when (method) {
                "validPayChannel" -> channel
                else -> null
            }
        }
        val notifyService = interfaceProxy<PayNotifyService> { method, args ->
            when (method) {
                "createPayNotifyTask" -> {
                    notifyTasks += (args?.get(0) as Int) to (args[1] as Long)
                    Unit
                }
                else -> null
            }
        }
        val self = AtomicReference<Any>()
        service = PayRefundServiceImpl(
            PayProperties(),
            appService,
            orderService,
            channelService,
            notifyService,
            selfApplicationContext(self),
        )
        self.set(service)
    }

    @AfterEach
    fun tearDown() {
        TenantContextHolder.clear()
    }

    @Test
    fun `success callback updates refund order amount and merchant notification once`() {
        val refund = waitingRefund("R-success")
        val successTime = LocalDateTime.of(2026, 7, 23, 10, 0)
        val response = PayRefundRespDTO.successOf("channel-refund-1", successTime, refund.no, null)

        service.notifyRefund(requireNotNull(channel.id), response)
        service.notifyRefund(requireNotNull(channel.id), response)

        val stored = requireNotNull(PayRefundDao.selectById(requireNotNull(refund.id)))
        assertEquals(PayRefundStatusEnum.SUCCESS.status, stored.status)
        assertEquals("channel-refund-1", stored.channelRefundNo)
        assertNotNull(stored.successTime)
        assertNotNull(stored.channelNotifyData)
        assertEquals(listOf(requireNotNull(refund.orderId) to requireNotNull(refund.refundPrice)), orderRefundUpdates)
        assertEquals(listOf(PayNotifyTypeEnum.REFUND.type to requireNotNull(refund.id)), notifyTasks)
    }

    @Test
    fun `failure callback records channel error without changing order refund amount`() {
        val refund = waitingRefund("R-failure")
        val response = PayRefundRespDTO.failureOf("BALANCE", "insufficient balance", refund.no, null)

        service.notifyRefund(requireNotNull(channel.id), response)
        service.notifyRefund(requireNotNull(channel.id), response)

        val stored = requireNotNull(PayRefundDao.selectById(requireNotNull(refund.id)))
        assertEquals(PayRefundStatusEnum.FAILURE.status, stored.status)
        assertEquals("BALANCE", stored.channelErrorCode)
        assertEquals("insufficient balance", stored.channelErrorMsg)
        assertNotNull(stored.channelNotifyData)
        assertNull(stored.successTime)
        assertEquals(emptyList<Pair<Long, Int>>(), orderRefundUpdates)
        assertEquals(listOf(PayNotifyTypeEnum.REFUND.type to requireNotNull(refund.id)), notifyTasks)
    }

    private fun waitingRefund(no: String): PayRefundDO = PayRefundDO().apply {
        this.no = no
        appId = channel.appId
        channelId = channel.id
        channelCode = channel.code
        orderId = 30L
        orderNo = "P-order"
        merchantOrderId = "merchant-order"
        merchantRefundId = "merchant-$no"
        status = PayRefundStatusEnum.WAITING.status
        payPrice = 1000
        refundPrice = 300
        PayRefundDao.insert(this)
    }
}
