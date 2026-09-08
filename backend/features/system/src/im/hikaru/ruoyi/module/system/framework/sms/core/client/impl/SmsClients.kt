package im.hikaru.ruoyi.module.system.framework.sms.core.client.impl

import im.hikaru.ruoyi.framework.common.core.KeyValue
import im.hikaru.ruoyi.framework.common.util.json.JsonUtils
import im.hikaru.ruoyi.module.system.framework.sms.core.client.SmsClient
import im.hikaru.ruoyi.module.system.framework.sms.core.client.SmsClientFactory
import im.hikaru.ruoyi.module.system.framework.sms.core.client.dto.SmsReceiveRespDTO
import im.hikaru.ruoyi.module.system.framework.sms.core.client.dto.SmsSendRespDTO
import im.hikaru.ruoyi.module.system.framework.sms.core.client.dto.SmsTemplateRespDTO
import im.hikaru.ruoyi.module.system.framework.sms.core.enums.SmsChannelEnum
import im.hikaru.ruoyi.module.system.framework.sms.core.enums.SmsTemplateAuditStatusEnum
import im.hikaru.ruoyi.module.system.framework.sms.core.property.SmsChannelProperties
import org.springframework.stereotype.Component
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

abstract class AbstractSmsClient(
    @Volatile protected var properties: SmsChannelProperties,
) : SmsClient {
    override val id: Long?
        get() = properties.id

    val code: String?
        get() = properties.code

    fun refresh(newProperties: SmsChannelProperties) {
        properties = newProperties
    }
}

class DebugDingTalkSmsClient(properties: SmsChannelProperties) : AbstractSmsClient(properties) {
    private val httpClient = HttpClient.newHttpClient()

    override fun sendSms(
        logId: Long,
        mobile: String,
        apiTemplateId: String,
        templateParams: List<KeyValue<String, Any?>>,
    ): SmsSendRespDTO {
        val body = mapOf(
            "msgtype" to "text",
            "text" to mapOf(
                "content" to buildString {
                    appendLine("[Simulated SMS]")
                    appendLine("Mobile: $mobile")
                    appendLine("Log id: $logId")
                    append("Template params: ${templateParams.associate { it.key to it.value }}")
                },
            ),
        )
        val request = HttpRequest.newBuilder(URI.create(buildUrl()))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(JsonUtils.toJsonString(body)))
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        val result = JsonUtils.parseMap(response.body()).orEmpty()
        val code = result["errcode"]?.toString() ?: response.statusCode().toString()
        return SmsSendRespDTO(
            success = response.statusCode() in 200..299 && code == "0",
            serialNo = UUID.randomUUID().toString(),
            apiCode = code,
            apiMsg = (result["errmsg"] ?: result["errorMsg"])?.toString(),
        )
    }

    override fun parseSmsReceiveStatus(text: String): List<SmsReceiveRespDTO> = emptyList()

    override fun getSmsTemplate(apiTemplateId: String) = SmsTemplateRespDTO(
        id = apiTemplateId,
        content = "",
        auditStatus = SmsTemplateAuditStatusEnum.SUCCESS.status,
        auditReason = "",
    )

    private fun buildUrl(): String {
        val apiKey = requireNotNull(properties.apiKey) { "DingTalk access token must not be null" }
        val secret = requireNotNull(properties.apiSecret) { "DingTalk secret must not be null" }
        val timestamp = System.currentTimeMillis()
        val mac = Mac.getInstance("HmacSHA256").apply {
            init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        }
        val sign = Base64.getEncoder().encodeToString(
            mac.doFinal("$timestamp\n$secret".toByteArray(StandardCharsets.UTF_8)),
        )
        return "https://oapi.dingtalk.com/robot/send?access_token=${encode(apiKey)}&timestamp=$timestamp&sign=${encode(sign)}"
    }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)
}

@Component
class SmsClientFactoryImpl : SmsClientFactory {
    private val clientsById = ConcurrentHashMap<Long, AbstractSmsClient>()
    private val clientsByCode = ConcurrentHashMap<String, AbstractSmsClient>()

    override fun getSmsClient(channelId: Long): SmsClient? = clientsById[channelId]

    override fun getSmsClient(channelCode: String): SmsClient? = clientsByCode[channelCode]

    override fun createOrUpdateSmsClient(properties: SmsChannelProperties): SmsClient {
        val id = requireNotNull(properties.id) { "SMS channel id must not be null" }
        val code = requireNotNull(properties.code) { "SMS channel code must not be null" }
        val existing = clientsById[id]
        val client = if (existing != null && existing.code == code) {
            existing.apply { refresh(properties) }
        } else {
            createClient(properties)
        }
        clientsById[id] = client
        clientsByCode[code] = client
        return client
    }

    private fun createClient(properties: SmsChannelProperties): AbstractSmsClient = when (
        SmsChannelEnum.fromCode(properties.code) ?: error("Unknown SMS channel code: ${properties.code}")
    ) {
        SmsChannelEnum.DEBUG_DING_TALK -> DebugDingTalkSmsClient(properties)
        SmsChannelEnum.ALIYUN -> AliyunSmsClient(properties)
        SmsChannelEnum.TENCENT -> TencentSmsClient(properties)
        SmsChannelEnum.HUAWEI -> HuaweiSmsClient(properties)
        SmsChannelEnum.QINIU -> QiniuSmsClient(properties)
    }
}
