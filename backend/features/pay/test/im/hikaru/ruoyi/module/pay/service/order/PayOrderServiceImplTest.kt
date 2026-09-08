package im.hikaru.ruoyi.module.pay.service.order

import im.hikaru.ruoyi.framework.common.exception.ServiceException
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.module.pay.api.order.dto.PayOrderCreateReqDTO
import im.hikaru.ruoyi.module.pay.dal.dataobject.app.PayAppDO
import im.hikaru.ruoyi.module.pay.dal.dataobject.channel.PayChannelDO
import im.hikaru.ruoyi.module.pay.dal.dataobject.order.PayOrderDO
import im.hikaru.ruoyi.module.pay.dal.dataobject.order.PayOrderExtensionDO
import im.hikaru.ruoyi.module.pay.dal.mysql.order.PayOrderDao
import im.hikaru.ruoyi.module.pay.dal.mysql.order.PayOrderExtensionDao
import im.hikaru.ruoyi.module.pay.dal.mysql.order.PayOrderExtensionTable
import im.hikaru.ruoyi.module.pay.dal.mysql.order.PayOrderTable
import im.hikaru.ruoyi.module.pay.enums.notify.PayNotifyTypeEnum
import im.hikaru.ruoyi.module.pay.enums.order.PayOrderStatusEnum
import im.hikaru.ruoyi.module.pay.framework.pay.config.PayProperties
import im.hikaru.ruoyi.module.pay.framework.pay.core.client.PayClient
import im.hikaru.ruoyi.module.pay.framework.pay.core.client.dto.order.PayOrderRespDTO
import im.hikaru.ruoyi.module.pay.service.app.PayAppService
import im.hikaru.ruoyi.module.pay.service.channel.PayChannelService
import im.hikaru.ruoyi.module.pay.service.notify.PayNotifyService
import im.hikaru.ruoyi.module.pay.test.interfaceProxy
import im.hikaru.ruoyi.module.pay.test.selfApplicationContext
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicReference
import kotlinx.datetime.toJavaLocalDateTime
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PayOrderServiceImplTest {
    private val app = PayAppDO().apply {
        id = 10L
        appKey = "test-app"
        orderNotifyUrl = "https://merchant.example/order"
        tenantId = 1L
    }
    private val channel = PayChannelDO().apply {
        id = 20L
        appId = 10L
        code = "mock"
        feeRate = 2.5
        tenantId = 1L
    }
    private val notifyTasks = mutableListOf<Pair<Int, Long>>()
    private var providerOrder: (String) -> PayOrderRespDTO = { outTradeNo ->
        PayOrderRespDTO.waitingOf(null, null, outTradeNo, null)
    }
    private lateinit var service: PayOrderServiceImpl

    @BeforeEach
    fun setUp() {
        Database.connect(
            "jdbc:h2:mem:pay_order_${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
        )
        transaction { SchemaUtils.create(PayOrderTable, PayOrderExtensionTable) }
        TenantContextHolder.setTenantId(1L)

        val client = interfaceProxy<PayClient<Any>> { method, args ->
            when (method) {
                "getId" -> channel.id
                "getConfig" -> Any()
                "getOrder" -> providerOrder(args?.get(0) as String)
                else -> null
            }
        }
        val appService = interfaceProxy<PayAppService> { method, _ ->
            when (method) {
                "validPayApp" -> app
                else -> null
            }
        }
        val channelService = interfaceProxy<PayChannelService> { method, _ ->
            when (method) {
                "validPayChannel" -> channel
                "getPayClient" -> client
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
        service = PayOrderServiceImpl(
            PayProperties(),
            appService,
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
    fun `creating a main order does not allocate the channel order number`() {
        val id = service.createOrder(PayOrderCreateReqDTO().apply {
            appKey = "test-app"
            userIp = "127.0.0.1"
            userId = 7L
            userType = 1
            merchantOrderId = "merchant-order-1"
            subject = "Migration test"
            price = 1000
            expireTime = LocalDateTime.now().plusMinutes(10)
        })

        val order = requireNotNull(PayOrderDao.selectById(id))
        assertNull(order.no)
        assertNull(order.extensionId)
        assertEquals(PayOrderStatusEnum.WAITING.status, order.status)
        assertEquals("https://merchant.example/order", order.notifyUrl)
    }

    @Test
    fun `success callback updates the extension and order exactly once`() {
        val order = waitingOrder()
        val extension = waitingExtension(requireNotNull(order.id), "P-success")
        val successTime = LocalDateTime.of(2026, 7, 23, 9, 30)
        val response = PayOrderRespDTO.successOf(
            "channel-order-1",
            "channel-user-1",
            successTime,
            extension.no,
            mapOf("result" to "ok"),
        )

        service.notifyOrder(requireNotNull(channel.id), response)
        service.notifyOrder(requireNotNull(channel.id), response)

        val storedExtension = requireNotNull(PayOrderExtensionDao.selectById(requireNotNull(extension.id)))
        assertEquals(PayOrderStatusEnum.SUCCESS.status, storedExtension.status)
        assertNotNull(storedExtension.channelNotifyData)

        val storedOrder = requireNotNull(PayOrderDao.selectById(requireNotNull(order.id)))
        assertEquals(PayOrderStatusEnum.SUCCESS.status, storedOrder.status)
        assertEquals(extension.id, storedOrder.extensionId)
        assertEquals(extension.no, storedOrder.no)
        assertEquals(channel.id, storedOrder.channelId)
        assertEquals("mock", storedOrder.channelCode)
        assertEquals("channel-order-1", storedOrder.channelOrderNo)
        assertEquals("channel-user-1", storedOrder.channelUserId)
        assertEquals(2.5, storedOrder.channelFeeRate)
        assertEquals(25, storedOrder.channelFeePrice)
        assertEquals(successTime, storedOrder.successTime?.toJavaLocalDateTime())
        assertEquals(listOf(PayNotifyTypeEnum.ORDER.type to requireNotNull(order.id)), notifyTasks)
    }

    @Test
    fun `closed callback only closes the attempted extension`() {
        val order = waitingOrder()
        val extension = waitingExtension(requireNotNull(order.id), "P-closed")

        service.notifyOrder(
            requireNotNull(channel.id),
            PayOrderRespDTO.closedOf("USER_CANCEL", "cancelled", extension.no, null),
        )

        assertEquals(
            PayOrderStatusEnum.CLOSED.status,
            PayOrderExtensionDao.selectById(requireNotNull(extension.id))?.status,
        )
        assertEquals(PayOrderStatusEnum.WAITING.status, PayOrderDao.selectById(requireNotNull(order.id))?.status)
        assertEquals(emptyList<Pair<Int, Long>>(), notifyTasks)
    }

    @Test
    fun `provider paid extension blocks another submission`() {
        val order = waitingOrder()
        val extension = waitingExtension(requireNotNull(order.id), "P-paid")
        providerOrder = { outTradeNo ->
            PayOrderRespDTO.successOf("channel-paid", "user", LocalDateTime.now(), outTradeNo, null)
        }

        assertThrows(ServiceException::class.java) {
            service.validateOrderActuallyPaid(requireNotNull(order.id))
        }
        assertEquals(PayOrderStatusEnum.WAITING.status, PayOrderExtensionDao.selectById(requireNotNull(extension.id))?.status)
    }

    private fun waitingOrder(): PayOrderDO = PayOrderDO().apply {
        appId = app.id
        userId = 7L
        userType = 1
        merchantOrderId = "merchant-${System.nanoTime()}"
        subject = "Order"
        price = 1000
        refundPrice = 0
        status = PayOrderStatusEnum.WAITING.status
        expireTime = kotlinx.datetime.LocalDateTime(2026, 12, 31, 0, 0)
        PayOrderDao.insert(this)
    }

    private fun waitingExtension(orderId: Long, no: String): PayOrderExtensionDO = PayOrderExtensionDO().apply {
        this.no = no
        this.orderId = orderId
        channelId = channel.id
        channelCode = channel.code
        userIp = "127.0.0.1"
        status = PayOrderStatusEnum.WAITING.status
        PayOrderExtensionDao.insert(this)
    }
}
