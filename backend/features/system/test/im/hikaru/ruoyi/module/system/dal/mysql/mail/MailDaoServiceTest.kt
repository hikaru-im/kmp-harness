package im.hikaru.ruoyi.module.system.dal.mysql.mail

import im.hikaru.ruoyi.framework.common.exception.ServiceException
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.module.system.controller.admin.mail.vo.account.MailAccountSaveReqVO
import im.hikaru.ruoyi.module.system.controller.admin.mail.vo.log.MailLogPageReqVO
import im.hikaru.ruoyi.module.system.controller.admin.mail.vo.template.MailTemplateSaveReqVO
import im.hikaru.ruoyi.module.system.enums.mail.MailSendStatusEnum
import im.hikaru.ruoyi.module.system.mq.message.mail.MailSendMessage
import im.hikaru.ruoyi.module.system.mq.producer.mail.MailDispatchPublisher
import im.hikaru.ruoyi.module.system.service.mail.MailAccountServiceImpl
import im.hikaru.ruoyi.module.system.service.mail.MailLogServiceImpl
import im.hikaru.ruoyi.module.system.service.mail.MailSendServiceImpl
import im.hikaru.ruoyi.module.system.service.mail.MailTemplateServiceImpl
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MailDaoServiceTest {
    private lateinit var templateService: MailTemplateServiceImpl
    private lateinit var accountService: MailAccountServiceImpl
    private lateinit var logService: MailLogServiceImpl
    private lateinit var publisher: CapturingPublisher
    private lateinit var sendService: MailSendServiceImpl

    @BeforeEach
    fun setUp() {
        Database.connect("jdbc:h2:mem:system_mail_${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")
        transaction { SchemaUtils.create(MailAccountTable, MailTemplateTable, MailLogTable) }
        templateService = MailTemplateServiceImpl()
        accountService = MailAccountServiceImpl(templateService)
        logService = MailLogServiceImpl()
        publisher = CapturingPublisher()
        sendService = MailSendServiceImpl(
            { _, _ -> null },
            accountService,
            templateService,
            logService,
            publisher,
        )
    }

    @AfterEach
    fun tearDown() {
        TenantContextHolder.clear()
    }

    @Test
    fun `mail accounts and templates are global and preserve CRUD rules`() {
        TenantContextHolder.setTenantId(42L)
        val accountId = accountService.createMailAccount(accountRequest())
        val templateId = templateService.createMailTemplate(templateRequest(accountId, "welcome-mail"))

        assertEquals(listOf("name", "code"), templateService.getMailTemplate(templateId)?.params)
        assertThrows(ServiceException::class.java) {
            templateService.createMailTemplate(templateRequest(accountId, "welcome-mail"))
        }
        assertThrows(ServiceException::class.java) { accountService.deleteMailAccount(accountId) }

        TenantContextHolder.setTenantId(84L)
        assertEquals("sender@example.com", accountService.getMailAccount(accountId)?.mail)
        assertEquals("welcome-mail", templateService.getMailTemplate(templateId)?.code)

        templateService.deleteMailTemplate(templateId)
        accountService.deleteMailAccount(accountId)
        assertEquals(null, accountService.getMailAccount(accountId))
    }

    @Test
    fun `send path renders templates deduplicates recipients and records results`() {
        val accountId = accountService.createMailAccount(accountRequest())
        templateService.createMailTemplate(templateRequest(accountId, "welcome-mail"))

        val logId = sendService.sendSingleMail(
            toMails = listOf("person@example.com", "invalid", "person@example.com"),
            ccMails = listOf("copy@example.com"),
            bccMails = emptyList(),
            userId = null,
            userType = null,
            templateCode = "welcome-mail",
            templateParams = mapOf("name" to "Alice", "code" to 1234),
        )

        val message = requireNotNull(publisher.messages.singleOrNull())
        assertEquals(logId, message.logId)
        assertEquals(listOf("person@example.com"), message.toMails.toList())
        assertEquals("Welcome Alice", message.title)
        assertEquals("<div><code>1234</code></div>", message.content)

        val log = requireNotNull(logService.getMailLog(logId))
        assertEquals(MailSendStatusEnum.INIT.status, log.sendStatus)
        assertEquals(mapOf("name" to "Alice", "code" to 1234), log.templateParams)

        logService.updateMailSendResult(logId, "message-id", null)
        val successful = requireNotNull(logService.getMailLog(logId))
        assertEquals(MailSendStatusEnum.SUCCESS.status, successful.sendStatus)
        assertEquals("message-id", successful.sendMessageId)
        assertNotNull(successful.sendTime)

        val page = logService.getMailLogPage(MailLogPageReqVO().apply {
            toMail = "person@example.com"
            sendStatus = MailSendStatusEnum.SUCCESS.status
            pageNo = 1
            pageSize = 10
        })
        assertEquals(1L, page.total)
    }

    @Test
    fun `disabled templates only create ignored logs and missing data is rejected`() {
        val accountId = accountService.createMailAccount(accountRequest())
        templateService.createMailTemplate(templateRequest(accountId, "disabled-mail").apply { status = 1 })

        val ignoredId = sendService.sendSingleMail(
            listOf("person@example.com"), null, null, null, null,
            "disabled-mail", mapOf("name" to "Alice", "code" to "x"),
        )
        assertEquals(MailSendStatusEnum.IGNORE.status, logService.getMailLog(ignoredId)?.sendStatus)
        assertFalse(publisher.messages.isNotEmpty())

        assertThrows(ServiceException::class.java) {
            sendService.sendSingleMail(
                listOf("person@example.com"), null, null, null, null,
                "disabled-mail", mapOf("name" to "Alice"),
            )
        }
        assertThrows(ServiceException::class.java) {
            sendService.sendSingleMail(
                listOf("not-an-email"), null, null, null, null,
                "disabled-mail", mapOf("name" to "Alice", "code" to "x"),
            )
        }
    }

    private fun accountRequest() = MailAccountSaveReqVO().apply {
        mail = "sender@example.com"
        username = "sender"
        password = "secret"
        host = "smtp.example.com"
        port = 465
        sslEnable = true
        starttlsEnable = false
    }

    private fun templateRequest(accountId: Long, code: String) = MailTemplateSaveReqVO().apply {
        name = "Welcome template"
        this.code = code
        this.accountId = accountId
        nickname = "Support"
        title = "Welcome {name}"
        content = "&lt;pre&gt;&lt;code&gt;{code}&lt;/code&gt;&lt;/pre&gt;"
        status = 0
        remark = "test"
    }

    private class CapturingPublisher : MailDispatchPublisher {
        val messages = mutableListOf<MailSendMessage>()
        override fun publish(message: MailSendMessage) {
            messages += message
        }
    }
}
