package im.hikaru.ruoyi.module.mp.dal.mysql.menu

import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.module.mp.dal.dataobject.menu.MpMenuDO
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MpMenuDaoTest {
    @BeforeEach
    fun setUp() {
        Database.connect(
            "jdbc:h2:mem:mp_menu_${System.nanoTime()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
        )
        transaction { SchemaUtils.create(MpMenuTable) }
        TenantContextHolder.setTenantId(1L)
    }

    @AfterEach
    fun tearDown() {
        TenantContextHolder.clear()
    }

    @Test
    fun `parent id round trips through the varchar source column`() {
        val id = MpMenuDao.insert(MpMenuDO().apply {
            accountId = 1L
            appId = "wx-test"
            name = "child"
            parentId = 42L
            type = "click"
        })

        assertEquals(42L, MpMenuDao.selectById(id)?.parentId)
    }
}
