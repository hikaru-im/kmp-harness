package im.hikaru.ruoyi.module.system.dal.mysql.logger

import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.module.system.api.logger.dto.OperateLogPageReqDTO
import im.hikaru.ruoyi.module.system.controller.admin.logger.vo.operatelog.OperateLogPageReqVO
import im.hikaru.ruoyi.module.system.dal.dataobject.logger.OperateLogDO
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class OperateLogDaoTest {
    @BeforeEach
    fun setUp() {
        Database.connect(
            "jdbc:h2:mem:system_operate_log_${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
        )
        transaction { SchemaUtils.create(OperateLogTable) }
        TenantContextHolder.setTenantId(1L)
    }

    @AfterEach
    fun tearDown() = TenantContextHolder.clear()

    @Test
    fun `operate logs support management and api filters`() {
        val firstId = OperateLogDao.insert(log("SYSTEM user", "Create user", 10L, 1L, "created Alice"))
        val secondId = OperateLogDao.insert(log("SYSTEM role", "Update role", 20L, 2L, "updated Auditor"))

        assertEquals(firstId, OperateLogDao.selectById(firstId)?.id)

        val managementPage = OperateLogDao.selectPage(OperateLogPageReqVO().apply {
            type = "role"
            subType = "Update"
            action = "Auditor"
            bizId = 20L
            userId = 2L
            createTime = listOf(
                LocalDateTime(2026, 7, 16, 0, 0),
                LocalDateTime(2026, 7, 18, 0, 0),
            )
        })
        assertEquals(1L, managementPage.total)
        assertEquals(secondId, managementPage.list.single().id)

        val apiPage = OperateLogDao.selectPage(OperateLogPageReqDTO().apply {
            type = "SYSTEM user"
            bizId = 10L
            userId = 1L
        })
        assertEquals(1L, apiPage.total)
        assertEquals(firstId, apiPage.list.single().id)

        val ordered = OperateLogDao.selectPage(OperateLogPageReqVO())
        assertEquals(listOf(secondId, firstId), ordered.list.map { it.id })
    }

    @Test
    fun `operate logs are isolated by tenant`() {
        val tenantOneId = OperateLogDao.insert(log("SYSTEM user", "Create user", 10L, 1L, "created Alice"))

        TenantContextHolder.setTenantId(2L)
        assertNull(OperateLogDao.selectById(tenantOneId))
        assertEquals(0L, OperateLogDao.selectPage(OperateLogPageReqVO()).total)

        OperateLogDao.insert(log("SYSTEM role", "Create role", 20L, 2L, "created Auditor"))
        assertEquals(1L, OperateLogDao.selectPage(OperateLogPageReqVO()).total)

        TenantContextHolder.setIgnore(true)
        assertEquals(2L, OperateLogDao.selectPage(OperateLogPageReqVO()).total)
    }

    private fun log(type: String, subType: String, bizId: Long, userId: Long, action: String) =
        OperateLogDO().apply {
            traceId = "trace-$bizId"
            this.userId = userId
            userType = 2
            this.type = type
            this.subType = subType
            this.bizId = bizId
            this.action = action
            extra = "{}"
            requestMethod = "POST"
            requestUrl = "/system/test"
            userIp = "127.0.0.1"
            userAgent = "JUnit"
            createTime = LocalDateTime(2026, 7, 17, 12, 0)
            updateTime = createTime
        }
}
