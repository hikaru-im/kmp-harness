package im.hikaru.ruoyi.module.pay.dal.mysql.demo

import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.module.pay.dal.dataobject.demo.PayDemoOrderDO
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PayDemoOrderDaoTest {
    @BeforeEach
    fun setUp() {
        Database.connect(
            "jdbc:h2:mem:pay_demo_order_${System.nanoTime()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
        )
        transaction { SchemaUtils.create(PayDemoOrderTable) }
        TenantContextHolder.setTenantId(1L)
    }

    @AfterEach
    fun tearDown() {
        TenantContextHolder.clear()
    }

    @Test
    fun `transfer channel package info is persisted`() {
        val id = PayDemoOrderDao.insert(PayDemoOrderDO().apply {
            userId = 1L
            spuId = 1L
            spuName = "demo"
            price = 100
            payStatus = false
            refundPrice = 0
            transferChannelPackageInfo = "{\"package\":\"test\"}"
        })

        assertEquals("{\"package\":\"test\"}", PayDemoOrderDao.selectById(id)?.transferChannelPackageInfo)
    }
}
