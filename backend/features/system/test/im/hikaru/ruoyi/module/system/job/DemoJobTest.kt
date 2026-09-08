package im.hikaru.ruoyi.module.system.job

import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.framework.tenant.core.job.TenantJob
import im.hikaru.ruoyi.module.system.dal.dataobject.user.AdminUserDO
import im.hikaru.ruoyi.module.system.dal.mysql.user.AdminUserDao
import im.hikaru.ruoyi.module.system.dal.mysql.user.AdminUserTable
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DemoJobTest {
    @BeforeEach
    fun setUp() {
        Database.connect("jdbc:h2:mem:demo_job_${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")
        transaction { SchemaUtils.create(AdminUserTable) }
        TenantContextHolder.setTenantId(1L)
    }

    @AfterEach
    fun tearDown() {
        TenantContextHolder.clear()
    }

    @Test
    fun `job counts users inside the current tenant`() {
        AdminUserDao.insert(user("tenant-one", 1L))
        AdminUserDao.insert(user("tenant-two", 2L))

        assertEquals("User count: 1", DemoJob().execute("sample"))
        assertTrue(
            DemoJob::class.java.getDeclaredMethod("execute", String::class.java)
                .isAnnotationPresent(TenantJob::class.java),
        )
    }

    private fun user(username: String, tenantId: Long) = AdminUserDO().apply {
        this.username = username
        nickname = username
        password = "password"
        status = 0
        this.tenantId = tenantId
    }
}
