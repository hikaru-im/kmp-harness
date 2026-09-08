package im.hikaru.ruoyi.module.system.dal.mysql.logger

import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.module.system.controller.admin.logger.vo.loginlog.LoginLogPageReqVO
import im.hikaru.ruoyi.module.system.dal.dataobject.logger.LoginLogDO
import im.hikaru.ruoyi.module.system.enums.logger.LoginResultEnum
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class LoginLogDaoTest {
    @BeforeEach
    fun setUp() {
        Database.connect("jdbc:h2:mem:system_login_log_${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")
        transaction { SchemaUtils.create(LoginLogTable) }
        TenantContextHolder.setTenantId(1L)
    }

    @AfterEach
    fun tearDown() = TenantContextHolder.clear()

    @Test
    fun `login logs are tenant aware and filter by result`() {
        val successId = LoginLogDao.insert(log("admin", LoginResultEnum.SUCCESS.result))
        LoginLogDao.insert(log("admin", LoginResultEnum.BAD_CREDENTIALS.result))
        assertEquals(successId, LoginLogDao.selectById(successId)?.id)
        assertEquals(1L, LoginLogDao.selectPage(LoginLogPageReqVO().apply { status = true }).total)
        assertEquals(1L, LoginLogDao.selectPage(LoginLogPageReqVO().apply { status = false }).total)

        TenantContextHolder.setTenantId(2L)
        assertNull(LoginLogDao.selectById(successId))
        assertEquals(0L, LoginLogDao.selectPage(LoginLogPageReqVO()).total)
    }

    private fun log(username: String, result: Int) = LoginLogDO().apply {
        logType = 100
        traceId = "trace"
        userId = 1L
        userType = 2
        this.username = username
        this.result = result
        userIp = "127.0.0.1"
        userAgent = "JUnit"
        tenantId = 1L
        createTime = LocalDateTime(2026, 7, 17, 0, 0)
        updateTime = createTime
    }
}
