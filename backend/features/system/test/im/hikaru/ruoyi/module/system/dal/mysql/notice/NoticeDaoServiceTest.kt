package im.hikaru.ruoyi.module.system.dal.mysql.notice

import im.hikaru.ruoyi.framework.common.exception.ServiceException
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.module.system.controller.admin.notice.vo.NoticePageReqVO
import im.hikaru.ruoyi.module.system.controller.admin.notice.vo.NoticeSaveReqVO
import im.hikaru.ruoyi.module.system.service.notice.NoticeServiceImpl
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class NoticeDaoServiceTest {
    private lateinit var service: NoticeServiceImpl

    @BeforeEach
    fun setUp() {
        Database.connect("jdbc:h2:mem:system_notice_${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")
        transaction { SchemaUtils.create(NoticeTable) }
        TenantContextHolder.setTenantId(TENANT_ID)
        service = NoticeServiceImpl()
    }

    @AfterEach
    fun tearDown() {
        TenantContextHolder.clear()
    }

    @Test
    fun `notice CRUD paging and tenant isolation preserve reference behavior`() {
        val firstId = service.createNotice(request("Maintenance notice", 0))
        val secondId = service.createNotice(request("Release announcement", 1).apply { type = 2 })

        val page = service.getNoticePage(NoticePageReqVO().apply {
            title = "notice"
            status = 0
            pageNo = 1
            pageSize = 10
        })
        assertEquals(1L, page.total)
        assertEquals(firstId, page.list.single().id)

        service.updateNotice(request("Updated notice", 1).apply { id = firstId })
        assertEquals("Updated notice", service.getNotice(firstId)?.title)

        TenantContextHolder.setTenantId(84L)
        assertNull(service.getNotice(firstId))
        val otherTenantId = service.createNotice(request("Tenant notice", 0))
        assertEquals("Tenant notice", service.getNotice(otherTenantId)?.title)

        TenantContextHolder.setTenantId(TENANT_ID)
        service.deleteNoticeList(listOf(firstId, secondId))
        assertNull(service.getNotice(firstId))
        assertNull(service.getNotice(secondId))
        assertThrows(ServiceException::class.java) { service.deleteNotice(firstId) }
    }

    private fun request(title: String, status: Int) = NoticeSaveReqVO().apply {
        this.title = title
        type = 1
        content = "<p>$title</p>"
        this.status = status
    }

    private companion object {
        const val TENANT_ID = 42L
    }
}
