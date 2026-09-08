package im.hikaru.ruoyi.module.pay.dal.mysql.app

import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.module.pay.controller.admin.app.vo.PayAppPageReqVO
import im.hikaru.ruoyi.module.pay.convert.app.PayAppConvert
import im.hikaru.ruoyi.module.pay.dal.dataobject.app.PayAppDO
import java.time.LocalDateTime
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PayAppDaoTest {
    @BeforeEach
    fun setUp() {
        Database.connect(
            "jdbc:h2:mem:pay_app_${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
        )
        transaction { SchemaUtils.create(PayAppTable) }
        TenantContextHolder.setTenantId(1L)
    }

    @AfterEach
    fun tearDown() {
        TenantContextHolder.clear()
    }

    @Test
    fun `payment app CRUD keeps tenant filtering and response create time`() {
        val app = PayAppDO().apply {
            appKey = "migration-app"
            name = "Migration app"
            status = 0
            orderNotifyUrl = "https://example.com/order"
            refundNotifyUrl = "https://example.com/refund"
            transferNotifyUrl = "https://example.com/transfer"
        }
        val id = PayAppDao.insert(app)

        val page = PayAppDao.selectPage(PayAppPageReqVO().apply {
            appKey = "migration"
            status = 0
            createTime = arrayOf(LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusMinutes(1))
        })
        assertEquals(1L, page.total)
        assertEquals(id, page.list.single().id)
        assertNotNull(PayAppConvert.convert(page.list.single()).createTime)
        assertEquals(id, PayAppDao.selectByAppKey("migration-app")?.id)

        TenantContextHolder.setTenantId(2L)
        assertNull(PayAppDao.selectById(id))

        TenantContextHolder.setTenantId(1L)
        assertEquals(1, PayAppDao.updateById(PayAppDO().apply {
            this.id = id
            name = "Updated app"
            status = 1
        }))
        assertEquals("Updated app", PayAppDao.selectById(id)?.name)
        assertEquals(1, PayAppDao.deleteById(id))
        assertNull(PayAppDao.selectById(id))
    }
}
