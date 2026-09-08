package im.hikaru.ruoyi.module.pay.service.transfer

import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.module.pay.controller.admin.transfer.vo.PayTransferPageReqVO
import im.hikaru.ruoyi.module.pay.dal.dataobject.app.PayAppDO
import im.hikaru.ruoyi.module.pay.dal.dataobject.channel.PayChannelDO
import im.hikaru.ruoyi.module.pay.dal.dataobject.transfer.PayTransferDO
import im.hikaru.ruoyi.module.pay.dal.mysql.transfer.PayTransferDao
import im.hikaru.ruoyi.module.pay.dal.mysql.transfer.PayTransferTable
import im.hikaru.ruoyi.module.pay.enums.notify.PayNotifyTypeEnum
import im.hikaru.ruoyi.module.pay.enums.transfer.PayTransferStatusEnum
import im.hikaru.ruoyi.module.pay.framework.pay.config.PayProperties
import im.hikaru.ruoyi.module.pay.framework.pay.core.client.dto.transfer.PayTransferRespDTO
import im.hikaru.ruoyi.module.pay.service.app.PayAppService
import im.hikaru.ruoyi.module.pay.service.channel.PayChannelService
import im.hikaru.ruoyi.module.pay.service.notify.PayNotifyService
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

class PayTransferServiceImplTest {
    private val channel = PayChannelDO().apply {
        id = 20L
        appId = 10L
        code = "mock"
        tenantId = 1L
    }
    private val notifyTasks = mutableListOf<Pair<Int, Long>>()
    private lateinit var service: PayTransferServiceImpl

    @BeforeEach
    fun setUp() {
        Database.connect(
            "jdbc:h2:mem:pay_transfer_${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
        )
        transaction { SchemaUtils.create(PayTransferTable) }
        TenantContextHolder.setTenantId(1L)

        val appService = interfaceProxy<PayAppService> { _, _ ->
            PayAppDO().apply { id = 10L; tenantId = 1L }
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
        service = PayTransferServiceImpl(
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
    fun `processing and success callbacks preserve package info and notify once`() {
        val transfer = transfer("T-success", PayTransferStatusEnum.WAITING)

        service.notifyTransfer(
            requireNotNull(channel.id),
            PayTransferRespDTO.processingOf(null, transfer.no, null).setChannelPackageInfo("package-1"),
        )
        service.notifyTransfer(
            requireNotNull(channel.id),
            PayTransferRespDTO.processingOf(null, transfer.no, null).setChannelPackageInfo("package-2"),
        )

        val success = PayTransferRespDTO.successOf(
            "channel-transfer-1",
            LocalDateTime.of(2026, 7, 23, 11, 0),
            transfer.no,
            null,
        )
        service.notifyTransfer(requireNotNull(channel.id), success)
        service.notifyTransfer(requireNotNull(channel.id), success)

        val stored = requireNotNull(PayTransferDao.selectById(requireNotNull(transfer.id)))
        assertEquals(PayTransferStatusEnum.SUCCESS.status, stored.status)
        assertEquals("package-1", stored.channelPackageInfo)
        assertEquals("channel-transfer-1", stored.channelTransferNo)
        assertNotNull(stored.successTime)
        assertNotNull(stored.channelNotifyData)
        assertEquals(listOf(PayNotifyTypeEnum.TRANSFER.type to requireNotNull(transfer.id)), notifyTasks)
    }

    @Test
    fun `closed callback accepts processing state and records provider error`() {
        val transfer = transfer("T-closed", PayTransferStatusEnum.PROCESSING)
        val response = PayTransferRespDTO.closedOf("ACCOUNT", "invalid account", transfer.no, null)

        service.notifyTransfer(requireNotNull(channel.id), response)
        service.notifyTransfer(requireNotNull(channel.id), response)

        val stored = requireNotNull(PayTransferDao.selectById(requireNotNull(transfer.id)))
        assertEquals(PayTransferStatusEnum.CLOSED.status, stored.status)
        assertEquals("ACCOUNT", stored.channelErrorCode)
        assertEquals("invalid account", stored.channelErrorMsg)
        assertNull(stored.successTime)
        assertEquals(listOf(PayNotifyTypeEnum.TRANSFER.type to requireNotNull(transfer.id)), notifyTasks)
    }

    @Test
    fun `transfer page applies number recipient name and account filters`() {
        val expected = transfer("T-filter", PayTransferStatusEnum.WAITING).apply {
            userName = "Alice Zhang"
            userAccount = "alice@example.com"
            PayTransferDao.updateById(this)
        }
        transfer("T-other", PayTransferStatusEnum.WAITING).apply {
            userName = "Bob"
            userAccount = "bob@example.com"
            PayTransferDao.updateById(this)
        }

        val page = PayTransferDao.selectPage(PayTransferPageReqVO().apply {
            no = "T-filter"
            userName = "Alice"
            userAccount = "alice@"
        })
        assertEquals(1L, page.total)
        assertEquals(expected.id, page.list.single().id)
    }

    private fun transfer(no: String, status: PayTransferStatusEnum): PayTransferDO = PayTransferDO().apply {
        this.no = no
        appId = channel.appId
        channelId = channel.id
        channelCode = channel.code
        merchantTransferId = "merchant-$no"
        subject = "Transfer"
        price = 500
        userAccount = "account"
        userName = "User"
        this.status = status.status
        PayTransferDao.insert(this)
    }
}
