package im.hikaru.ruoyi.module.system.dal.mysql.notify

import im.hikaru.ruoyi.framework.common.exception.ServiceException
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.module.system.controller.admin.notify.vo.message.NotifyMessageMyPageReqVO
import im.hikaru.ruoyi.module.system.controller.admin.notify.vo.message.NotifyMessagePageReqVO
import im.hikaru.ruoyi.module.system.controller.admin.notify.vo.template.NotifyTemplateSaveReqVO
import im.hikaru.ruoyi.module.system.enums.notify.NotifyTemplateTypeEnum
import im.hikaru.ruoyi.module.system.service.notify.NotifyMessageServiceImpl
import im.hikaru.ruoyi.module.system.service.notify.NotifySendServiceImpl
import im.hikaru.ruoyi.module.system.service.notify.NotifyTemplateServiceImpl
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class NotifyDaoServiceTest {
    private lateinit var templateService: NotifyTemplateServiceImpl
    private lateinit var messageService: NotifyMessageServiceImpl
    private lateinit var sendService: NotifySendServiceImpl

    @BeforeEach
    fun setUp() {
        Database.connect("jdbc:h2:mem:system_notify_${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")
        transaction { SchemaUtils.create(NotifyTemplateTable, NotifyMessageTable) }
        TenantContextHolder.setTenantId(TENANT_ID)
        templateService = NotifyTemplateServiceImpl()
        messageService = NotifyMessageServiceImpl()
        sendService = NotifySendServiceImpl(templateService, messageService)
    }

    @AfterEach
    fun tearDown() {
        TenantContextHolder.clear()
    }

    @Test
    fun `notify templates are global unique and parse parameters`() {
        val id = templateService.createNotifyTemplate(templateRequest("welcome", 0))
        assertEquals(listOf("name", "action"), templateService.getNotifyTemplate(id)?.params)

        TenantContextHolder.setTenantId(84L)
        assertEquals("welcome", templateService.getNotifyTemplate(id)?.code)
        assertThrows(ServiceException::class.java) {
            templateService.createNotifyTemplate(templateRequest("welcome", 0))
        }

        templateService.updateNotifyTemplate(templateRequest("welcome-updated", 0).apply { this.id = id })
        assertEquals("welcome-updated", templateService.getNotifyTemplate(id)?.code)
        templateService.deleteNotifyTemplate(id)
        assertNull(templateService.getNotifyTemplate(id))
    }

    @Test
    fun `send and read lifecycle enforces tenant and user ownership`() {
        templateService.createNotifyTemplate(templateRequest("welcome", 0))
        val firstId = requireNotNull(sendService.sendSingleNotifyToAdmin(100L, "welcome", params("Alice", "login")))
        val secondId = requireNotNull(sendService.sendSingleNotifyToAdmin(100L, "welcome", params("Alice", "review")))
        val otherUserId = requireNotNull(sendService.sendSingleNotifyToAdmin(200L, "welcome", params("Bob", "login")))

        assertEquals(2L, messageService.getUnreadNotifyMessageCount(100L, 2))
        assertEquals(secondId, messageService.getUnreadNotifyMessageList(100L, 2, 1).single().id)
        assertEquals(0, messageService.updateNotifyMessageRead(listOf(otherUserId), 100L, 2))
        assertEquals(1, messageService.updateNotifyMessageRead(listOf(firstId), 100L, 2))
        assertTrue(messageService.getNotifyMessage(firstId)?.readStatus == true)
        assertFalse(messageService.getNotifyMessage(secondId)?.readStatus == true)

        val unreadPage = messageService.getMyNotifyMessagePage(NotifyMessageMyPageReqVO().apply {
            readStatus = false
            pageNo = 1
            pageSize = 10
        }, 100L, 2)
        assertEquals(1L, unreadPage.total)
        assertEquals(secondId, unreadPage.list.single().id)

        TenantContextHolder.setTenantId(84L)
        val tenant84Id = requireNotNull(sendService.sendSingleNotifyToAdmin(100L, "welcome", params("Other", "login")))
        assertNull(messageService.getNotifyMessage(firstId))
        assertEquals(tenant84Id, messageService.getUnreadNotifyMessageList(100L, 2, 10).single().id)

        TenantContextHolder.setTenantId(TENANT_ID)
        assertEquals(1, messageService.updateAllNotifyMessageRead(100L, 2))
        assertEquals(0L, messageService.getUnreadNotifyMessageCount(100L, 2))

        val adminPage = messageService.getNotifyMessagePage(NotifyMessagePageReqVO().apply {
            userId = 200L
            templateCode = "welcome"
            pageNo = 1
            pageSize = 10
        })
        assertEquals(1L, adminPage.total)
        assertEquals(otherUserId, adminPage.list.single().id)
    }

    @Test
    fun `disabled templates and missing parameters do not create messages`() {
        templateService.createNotifyTemplate(templateRequest("disabled", 1))
        assertNull(sendService.sendSingleNotifyToAdmin(100L, "disabled", params("Alice", "login")))
        assertEquals(0L, messageService.getUnreadNotifyMessageCount(100L, 2))

        templateService.createNotifyTemplate(templateRequest("enabled", 0))
        assertThrows(ServiceException::class.java) {
            sendService.sendSingleNotifyToAdmin(100L, "enabled", mapOf("name" to "Alice"))
        }
        assertThrows(ServiceException::class.java) {
            sendService.sendSingleNotifyToAdmin(100L, "missing", emptyMap())
        }
    }

    private fun templateRequest(code: String, status: Int) = NotifyTemplateSaveReqVO().apply {
        name = "Welcome notification"
        this.code = code
        type = NotifyTemplateTypeEnum.SYSTEM_MESSAGE.type
        nickname = "System"
        content = "Hello {name}, please {action}"
        this.status = status
        remark = "test"
    }

    private fun params(name: String, action: String) = mapOf<String, Any?>("name" to name, "action" to action)

    private companion object {
        const val TENANT_ID = 42L
    }
}
