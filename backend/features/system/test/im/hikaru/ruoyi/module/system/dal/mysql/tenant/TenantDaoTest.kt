package im.hikaru.ruoyi.module.system.dal.mysql.tenant

import im.hikaru.ruoyi.module.system.controller.admin.tenant.vo.tenant.TenantPageReqVO
import im.hikaru.ruoyi.module.system.dal.dataobject.tenant.TenantDO
import im.hikaru.ruoyi.module.system.dal.dataobject.tenant.TenantPackageDO
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TenantDaoTest {
    @BeforeEach
    fun setUp() {
        Database.connect("jdbc:h2:mem:system_tenant_${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")
        transaction { SchemaUtils.create(TenantPackageTable, TenantTable) }
    }

    @Test
    fun `tenant and package preserve list fields`() {
        val packageId = TenantPackageDao.insert(TenantPackageDO().apply { name = "Standard"; status = 0; menuIds = setOf(1, 2, 3) })
        val tenant = TenantDO().apply { name = "Acme"; contactName = "Alice"; contactMobile = "13800000000"; status = 0; websites = listOf("acme.test", "app.acme.test"); this.packageId = packageId; expireTime = LocalDateTime(2099, 1, 1, 0, 0); accountCount = 20 }
        val tenantId = TenantDao.insert(tenant)
        assertEquals(setOf(1L, 2L, 3L), TenantPackageDao.selectById(packageId)?.menuIds)
        assertEquals(tenantId, TenantDao.selectListByWebsite("app.acme.test").single().id)
        assertEquals(1L, TenantDao.selectPage(TenantPageReqVO().apply { name = "Acm" }).total)
        TenantDao.deleteById(tenantId)
        assertNull(TenantDao.selectById(tenantId))
    }
}
