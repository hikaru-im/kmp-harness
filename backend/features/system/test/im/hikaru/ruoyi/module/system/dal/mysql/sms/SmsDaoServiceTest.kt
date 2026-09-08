package im.hikaru.ruoyi.module.system.dal.mysql.sms

import im.hikaru.ruoyi.framework.common.core.KeyValue
import im.hikaru.ruoyi.framework.common.exception.ServiceException
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.module.system.api.sms.dto.code.SmsCodeSendReqDTO
import im.hikaru.ruoyi.module.system.api.sms.dto.code.SmsCodeUseReqDTO
import im.hikaru.ruoyi.module.system.api.sms.dto.code.SmsCodeValidateReqDTO
import im.hikaru.ruoyi.module.system.controller.admin.sms.vo.channel.SmsChannelSaveReqVO
import im.hikaru.ruoyi.module.system.controller.admin.sms.vo.template.SmsTemplateSaveReqVO
import im.hikaru.ruoyi.module.system.dal.dataobject.sms.SmsCodeDO
import im.hikaru.ruoyi.module.system.enums.sms.SmsSendStatusEnum
import im.hikaru.ruoyi.module.system.framework.sms.config.SmsCodeProperties
import im.hikaru.ruoyi.module.system.framework.sms.core.client.SmsClient
import im.hikaru.ruoyi.module.system.framework.sms.core.client.SmsClientFactory
import im.hikaru.ruoyi.module.system.framework.sms.core.client.dto.SmsReceiveRespDTO
import im.hikaru.ruoyi.module.system.framework.sms.core.client.dto.SmsSendRespDTO
import im.hikaru.ruoyi.module.system.framework.sms.core.client.dto.SmsTemplateRespDTO
import im.hikaru.ruoyi.module.system.framework.sms.core.enums.SmsTemplateAuditStatusEnum
import im.hikaru.ruoyi.module.system.framework.sms.core.property.SmsChannelProperties
import im.hikaru.ruoyi.module.system.framework.sms.core.client.impl.AliyunSmsClient
import im.hikaru.ruoyi.module.system.framework.sms.core.client.impl.HuaweiSmsClient
import im.hikaru.ruoyi.module.system.framework.sms.core.client.impl.QiniuSmsClient
import im.hikaru.ruoyi.module.system.framework.sms.core.client.impl.SmsClientFactoryImpl
import im.hikaru.ruoyi.module.system.framework.sms.core.client.impl.TencentSmsClient
import im.hikaru.ruoyi.module.system.mq.message.sms.SmsSendMessage
import im.hikaru.ruoyi.module.system.mq.producer.sms.SmsDispatchPublisher
import im.hikaru.ruoyi.module.system.service.sms.SmsChannelServiceImpl
import im.hikaru.ruoyi.module.system.service.sms.SmsCodeServiceImpl
import im.hikaru.ruoyi.module.system.service.sms.SmsLogServiceImpl
import im.hikaru.ruoyi.module.system.service.sms.SmsSendService
import im.hikaru.ruoyi.module.system.service.sms.SmsSendServiceImpl
import im.hikaru.ruoyi.module.system.service.sms.SmsTemplateServiceImpl
import kotlinx.datetime.toKotlinLocalDateTime
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.core.env.StandardEnvironment
import java.time.Duration
import java.time.LocalDateTime

class SmsDaoServiceTest {
    private lateinit var clientFactory: FakeSmsClientFactory
    private lateinit var channelService: SmsChannelServiceImpl
    private lateinit var templateService: SmsTemplateServiceImpl
    private lateinit var logService: SmsLogServiceImpl
    private lateinit var publisher: CapturingPublisher
    private lateinit var sendService: SmsSendServiceImpl

    @BeforeEach
    fun setUp() {
        Database.connect("jdbc:h2:mem:system_sms_${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")
        transaction { SchemaUtils.create(SmsChannelTable, SmsTemplateTable, SmsLogTable, SmsCodeTable) }
        TenantContextHolder.setTenantId(TENANT_ID)
        clientFactory = FakeSmsClientFactory()
        channelService = SmsChannelServiceImpl(clientFactory)
        templateService = SmsTemplateServiceImpl(channelService)
        logService = SmsLogServiceImpl()
        publisher = CapturingPublisher()
        sendService = SmsSendServiceImpl(
            { _, _ -> null }, channelService, templateService, logService, publisher,
        )
    }

    @AfterEach
    fun tearDown() {
        TenantContextHolder.clear()
    }

    @Test
    fun `channels and templates are global and enforce references`() {
        val channelId = channelService.createSmsChannel(channelRequest())
        val templateId = templateService.createSmsTemplate(templateRequest(channelId, "login-code"))
        assertEquals(listOf("operation", "code"), templateService.getSmsTemplate(templateId)?.params)

        TenantContextHolder.setTenantId(84L)
        assertEquals("DEBUG_DING_TALK", channelService.getSmsChannel(channelId)?.code)
        assertEquals("login-code", templateService.getSmsTemplate(templateId)?.code)
        assertThrows(ServiceException::class.java) {
            templateService.createSmsTemplate(templateRequest(channelId, "login-code"))
        }
        assertThrows(ServiceException::class.java) { channelService.deleteSmsChannel(channelId) }

        templateService.deleteSmsTemplate(templateId)
        channelService.deleteSmsChannel(channelId)
        assertNull(channelService.getSmsChannel(channelId))
    }

    @Test
    fun `send path orders parameters records ignore and persists provider result`() {
        val channelId = channelService.createSmsChannel(channelRequest())
        templateService.createSmsTemplate(templateRequest(channelId, "disabled").apply { status = 1 })
        val ignoredId = sendService.sendSingleSms(
            "15601691300", null, null, "disabled", mapOf("operation" to "login", "code" to 123456),
        )
        assertEquals(SmsSendStatusEnum.IGNORE.status, logService.getSmsLog(ignoredId)?.sendStatus)
        assertFalse(publisher.messages.isNotEmpty())

        templateService.createSmsTemplate(templateRequest(channelId, "enabled"))
        val logId = sendService.sendSingleSms(
            "15601691300", 7L, 2, "enabled", mapOf("code" to 123456, "operation" to "login"),
        )
        val message = publisher.messages.single()
        assertEquals(listOf("operation", "code"), message.templateParams.map { it.key })
        assertEquals("Operation login, code 123456", logService.getSmsLog(logId)?.templateContent)

        sendService.doSendSms(message)
        val sent = requireNotNull(logService.getSmsLog(logId))
        assertEquals(SmsSendStatusEnum.SUCCESS.status, sent.sendStatus)
        assertEquals("serial-$logId", sent.apiSerialNo)
        assertNotNull(sent.sendTime)

        assertThrows(ServiceException::class.java) {
            sendService.sendSingleSms("15601691300", null, null, "enabled", mapOf("code" to 1))
        }
    }

    @Test
    fun `verification codes enforce tenant frequency expiry daily limit and one time use`() {
        val properties = SmsCodeProperties().apply {
            beginCode = 123456
            endCode = 123456
            sendFrequency = Duration.ofMinutes(1)
            expireTimes = Duration.ofMinutes(10)
            sendMaximumQuantityPerDay = 2
        }
        val codeService = SmsCodeServiceImpl(properties, NoopSmsSendService(), StandardEnvironment())
        codeService.sendSmsCode(sendCodeRequest("15601691300"))
        assertThrows(ServiceException::class.java) { codeService.sendSmsCode(sendCodeRequest("15601691300")) }

        TenantContextHolder.setTenantId(84L)
        assertThrows(ServiceException::class.java) {
            codeService.validateSmsCode(validateCodeRequest("15601691300"))
        }

        TenantContextHolder.setTenantId(TENANT_ID)
        codeService.useSmsCode(SmsCodeUseReqDTO().apply {
            mobile = "15601691300"
            scene = 1
            code = "123456"
            usedIp = "127.0.0.2"
        })
        assertThrows(ServiceException::class.java) {
            codeService.validateSmsCode(validateCodeRequest("15601691300"))
        }

        SmsCodeDao.insert(SmsCodeDO().apply {
            mobile = "15601691301"
            code = "654321"
            scene = 1
            createIp = "127.0.0.1"
            todayIndex = 2
            used = false
            createTime = LocalDateTime.now().minusMinutes(2).toKotlinLocalDateTime()
        })
        assertThrows(ServiceException::class.java) { codeService.createSmsCode("15601691301", 1, "127.0.0.1") }

        SmsCodeDao.insert(SmsCodeDO().apply {
            mobile = "15601691302"
            code = "123456"
            scene = 1
            createIp = "127.0.0.1"
            todayIndex = 1
            used = false
            createTime = LocalDateTime.now().minusMinutes(11).toKotlinLocalDateTime()
        })
        assertThrows(ServiceException::class.java) {
            codeService.validateSmsCode(validateCodeRequest("15601691302"))
        }
    }

    @Test
    fun `local fixed verification code bypasses SMS provider and remains one time use`() {
        val properties = SmsCodeProperties().apply {
            localFixedCode = "246810"
        }
        val smsSendService = NoopSmsSendService()
        val localEnvironment = StandardEnvironment().apply { setActiveProfiles("local") }
        val codeService = SmsCodeServiceImpl(properties, smsSendService, localEnvironment)

        codeService.sendSmsCode(sendCodeRequest("15601691303"))

        assertEquals(0, smsSendService.sendCalls)
        codeService.useSmsCode(SmsCodeUseReqDTO().apply {
            mobile = "15601691303"
            scene = 1
            code = "246810"
            usedIp = "127.0.0.2"
        })
        assertThrows(ServiceException::class.java) {
            codeService.validateSmsCode(validateCodeRequest("15601691303", "246810"))
        }
    }

    @Test
    fun `fixed verification code is rejected outside local profile`() {
        val properties = SmsCodeProperties().apply {
            localFixedCode = "246810"
        }

        assertThrows(IllegalArgumentException::class.java) {
            SmsCodeServiceImpl(properties, NoopSmsSendService(), StandardEnvironment())
        }
    }

    @Test
    fun `provider factory creates all clients and parses delivery callbacks`() {
        val factory = SmsClientFactoryImpl()
        val aliyun = factory.createOrUpdateSmsClient(providerProperties(1, "ALIYUN", "key", "secret"))
        val tencent = factory.createOrUpdateSmsClient(providerProperties(2, "TENCENT", "secret-id app-id", "secret"))
        val huawei = factory.createOrUpdateSmsClient(providerProperties(3, "HUAWEI", "access-key sender", "secret"))
        val qiniu = factory.createOrUpdateSmsClient(providerProperties(4, "QINIU", "access-key", "secret"))
        assertEquals(AliyunSmsClient::class, aliyun::class)
        assertEquals(TencentSmsClient::class, tencent::class)
        assertEquals(HuaweiSmsClient::class, huawei::class)
        assertEquals(QiniuSmsClient::class, qiniu::class)

        val aliyunResult = aliyun.parseSmsReceiveStatus(
            """[{"success":true,"err_code":"DELIVRD","err_msg":"ok","phone_number":"15601691300","report_time":"2026-07-17 10:00:00","biz_id":"a-1","out_id":"91"}]""",
        ).single()
        assertEquals(91L, aliyunResult.logId)
        assertEquals("a-1", aliyunResult.serialNo)

        val tencentResult = tencent.parseSmsReceiveStatus(
            """[{"report_status":"SUCCESS","errmsg":"DELIVRD","description":"ok","mobile":"15601691300","user_receive_time":"2026-07-17 10:00:00","sid":"t-1"}]""",
        ).single()
        assertEquals(true, tencentResult.success)
        assertEquals("t-1", tencentResult.serialNo)

        val huaweiResult = huawei.parseSmsReceiveStatus(
            "status=DELIVRD&statusDesc=ok&to=15601691300&updateTime=2026-07-17T02%3A00%3A00Z&smsMsgId=h-1&extend=92",
        ).single()
        assertEquals(92L, huaweiResult.logId)
        assertNotNull(huaweiResult.receiveTime)

        val qiniuResult = qiniu.parseSmsReceiveStatus(
            """{"items":[{"status":"DELIVRD","mobile":"15601691300","delivrd_at":1784253600,"message_id":"q-1","seq":"93"}]}""",
        ).single()
        assertEquals(93L, qiniuResult.logId)
        assertEquals("q-1", qiniuResult.serialNo)
    }

    private fun channelRequest() = SmsChannelSaveReqVO().apply {
        signature = "Yudao"
        code = "DEBUG_DING_TALK"
        status = 0
        apiKey = "token"
        apiSecret = "secret"
    }

    private fun templateRequest(channelId: Long, code: String) = SmsTemplateSaveReqVO().apply {
        type = 1
        status = 0
        this.code = code
        name = "Login code"
        content = "Operation {operation}, code {code}"
        apiTemplateId = "provider-template"
        this.channelId = channelId
    }

    private fun sendCodeRequest(mobileValue: String) = SmsCodeSendReqDTO().apply {
        mobile = mobileValue
        scene = 1
        createIp = "127.0.0.1"
    }

    private fun validateCodeRequest(mobileValue: String, codeValue: String = "123456") = SmsCodeValidateReqDTO().apply {
        mobile = mobileValue
        scene = 1
        code = codeValue
    }

    private fun providerProperties(id: Long, code: String, apiKey: String, apiSecret: String) = SmsChannelProperties(
        id = id,
        signature = "Yudao",
        code = code,
        apiKey = apiKey,
        apiSecret = apiSecret,
        callbackUrl = "https://example.com/callback",
    )

    private class CapturingPublisher : SmsDispatchPublisher {
        val messages = mutableListOf<SmsSendMessage>()
        override fun publish(message: SmsSendMessage) {
            messages += message
        }
    }

    private class FakeSmsClientFactory : SmsClientFactory {
        private val byId = mutableMapOf<Long, SmsClient>()
        private val byCode = mutableMapOf<String, SmsClient>()

        override fun getSmsClient(channelId: Long): SmsClient? = byId[channelId]
        override fun getSmsClient(channelCode: String): SmsClient? = byCode[channelCode]
        override fun createOrUpdateSmsClient(properties: SmsChannelProperties): SmsClient {
            val client = FakeSmsClient(requireNotNull(properties.id))
            byId[requireNotNull(properties.id)] = client
            byCode[requireNotNull(properties.code)] = client
            return client
        }
    }

    private class FakeSmsClient(override val id: Long) : SmsClient {
        override fun sendSms(
            logId: Long,
            mobile: String,
            apiTemplateId: String,
            templateParams: List<KeyValue<String, Any?>>,
        ) = SmsSendRespDTO(true, "request-$logId", "serial-$logId", "OK", "success")

        override fun parseSmsReceiveStatus(text: String): List<SmsReceiveRespDTO> = emptyList()
        override fun getSmsTemplate(apiTemplateId: String) = SmsTemplateRespDTO(
            apiTemplateId, "", SmsTemplateAuditStatusEnum.SUCCESS.status, null,
        )
    }

    private class NoopSmsSendService : SmsSendService {
        var sendCalls = 0

        override fun sendSingleSmsToAdmin(mobile: String?, userId: Long?, templateCode: String, templateParams: Map<String, Any?>) = 1L
        override fun sendSingleSmsToMember(mobile: String?, userId: Long?, templateCode: String, templateParams: Map<String, Any?>) = 1L
        override fun sendSingleSms(mobile: String?, userId: Long?, userType: Int?, templateCode: String, templateParams: Map<String, Any?>): Long {
            sendCalls += 1
            return 1L
        }
        override fun doSendSms(message: SmsSendMessage) = Unit
        override fun receiveSmsStatus(channelCode: String, text: String) = Unit
    }

    private companion object {
        const val TENANT_ID = 42L
    }
}
