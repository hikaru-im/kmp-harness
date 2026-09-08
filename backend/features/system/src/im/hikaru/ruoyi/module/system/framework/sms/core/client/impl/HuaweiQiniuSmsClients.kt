package im.hikaru.ruoyi.module.system.framework.sms.core.client.impl

import im.hikaru.ruoyi.framework.common.core.KeyValue
import im.hikaru.ruoyi.framework.common.util.json.JsonUtils
import im.hikaru.ruoyi.module.system.framework.sms.core.client.dto.SmsReceiveRespDTO
import im.hikaru.ruoyi.module.system.framework.sms.core.client.dto.SmsSendRespDTO
import im.hikaru.ruoyi.module.system.framework.sms.core.client.dto.SmsTemplateRespDTO
import im.hikaru.ruoyi.module.system.framework.sms.core.enums.SmsTemplateAuditStatusEnum
import im.hikaru.ruoyi.module.system.framework.sms.core.property.SmsChannelProperties
import kotlinx.datetime.toKotlinLocalDateTime
import java.net.URI
import java.net.URLDecoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class HuaweiSmsClient(properties: SmsChannelProperties) : AbstractSmsClient(properties) {
    private val accessKey: String
    private val sender: String

    init {
        val parts = requireNotNull(properties.apiKey).trim().split(Regex("\\s+"))
        require(parts.size == 2) { "Huawei SMS apiKey must use 'accessKey sender' format" }
        accessKey = parts[0]
        sender = parts[1]
    }

    override fun sendSms(
        logId: Long,
        mobile: String,
        apiTemplateId: String,
        templateParams: List<KeyValue<String, Any?>>,
    ): SmsSendRespDTO {
        val body = linkedMapOf(
            "from" to sender,
            "to" to mobile,
            "templateId" to apiTemplateId,
            "templateParas" to JsonUtils.toJsonString(templateParams.map { it.value.toString() }),
            "statusCallback" to properties.callbackUrl,
            "extend" to logId.toString(),
        ).filterValues { !it.isNullOrEmpty() }.entries.joinToString("&") { (key, value) ->
            "$key=${percentEncode(requireNotNull(value))}"
        }
        val response = request(body)
        val result = response.objectList("result").firstOrNull()
        if (result == null) {
            return SmsSendRespDTO(
                success = false,
                apiCode = response.string("code"),
                apiMsg = response.string("description"),
            )
        }
        return SmsSendRespDTO(
            success = response.string("code") == "000000",
            serialNo = result.string("smsMsgId"),
            apiCode = result.string("status"),
        )
    }

    override fun parseSmsReceiveStatus(text: String): List<SmsReceiveRespDTO> {
        val params = decodeForm(text)
        val receiveTime = params["updateTime"]?.let { value ->
            runCatching {
                java.time.LocalDateTime.ofInstant(java.time.Instant.parse(value), ZoneOffset.UTC).toKotlinLocalDateTime()
            }.getOrNull()
        }
        return listOf(SmsReceiveRespDTO(
            success = params["status"] == "DELIVRD",
            errorCode = params["status"],
            errorMsg = params["statusDesc"],
            mobile = params["to"],
            receiveTime = receiveTime,
            serialNo = params["smsMsgId"],
            logId = params["extend"]?.toLongOrNull(),
        ))
    }

    override fun getSmsTemplate(apiTemplateId: String): SmsTemplateRespDTO {
        require(apiTemplateId.trim().split(Regex("\\s+")).size == 2) {
            "Huawei SMS template id must use 'apiTemplateId sender' format"
        }
        return SmsTemplateRespDTO(
            id = apiTemplateId,
            auditStatus = SmsTemplateAuditStatusEnum.SUCCESS.status,
        )
    }

    private fun request(body: String): Map<String, Any?> {
        val sdkDate = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .withZone(ZoneOffset.UTC).format(java.time.Instant.now())
        val canonicalHeaders = "content-type:application/x-www-form-urlencoded\nhost:$HOST\nx-sdk-date:$sdkDate\n"
        val canonicalRequest = "POST\n$URI_PATH\n\n$canonicalHeaders\n$SIGNED_HEADERS\n${sha256Hex(body)}"
        val stringToSign = "SDK-HMAC-SHA256\n$sdkDate\n${sha256Hex(canonicalRequest)}"
        val signature = hmacSha256Hex(requireNotNull(properties.apiSecret), stringToSign)
        return postJson(URL, mapOf(
            "Content-Type" to "application/x-www-form-urlencoded",
            "X-Sdk-Date" to sdkDate,
            "host" to HOST,
            "Authorization" to "SDK-HMAC-SHA256 Access=$accessKey, SignedHeaders=$SIGNED_HEADERS, Signature=$signature",
        ), body)
    }

    private companion object {
        const val URL = "https://smsapi.cn-north-4.myhuaweicloud.com:443/sms/batchSendSms/v1"
        const val HOST = "smsapi.cn-north-4.myhuaweicloud.com:443"
        const val URI_PATH = "/sms/batchSendSms/v1/"
        const val SIGNED_HEADERS = "content-type;host;x-sdk-date"
    }
}

class QiniuSmsClient(properties: SmsChannelProperties) : AbstractSmsClient(properties) {
    override fun sendSms(
        logId: Long,
        mobile: String,
        apiTemplateId: String,
        templateParams: List<KeyValue<String, Any?>>,
    ): SmsSendRespDTO {
        val response = request("POST", "/v1/message/single", linkedMapOf(
            "template_id" to apiTemplateId,
            "mobile" to mobile,
            "parameters" to templateParams.associate { it.key to it.value },
            "seq" to logId.toString(),
        ))
        if (!response.string("error").isNullOrEmpty()) {
            return SmsSendRespDTO(
                success = false,
                apiCode = response.string("error"),
                apiRequestId = response.string("request_id"),
                apiMsg = response.string("message"),
            )
        }
        return SmsSendRespDTO(
            success = response.string("message_id") != null,
            serialNo = response.string("message_id"),
        )
    }

    override fun parseSmsReceiveStatus(text: String): List<SmsReceiveRespDTO> {
        val root = JsonUtils.parseMap(text).orEmpty()
        return root.objectList("items").map { status ->
            SmsReceiveRespDTO(
                success = status.string("status") == "DELIVRD",
                errorMsg = status.string("status"),
                mobile = status.string("mobile"),
                receiveTime = status.long("delivrd_at")?.let { epoch ->
                    java.time.LocalDateTime.ofInstant(
                        java.time.Instant.ofEpochSecond(epoch), java.time.ZoneId.systemDefault(),
                    ).toKotlinLocalDateTime()
                },
                serialNo = status.string("message_id"),
                logId = status.long("seq"),
            )
        }
    }

    override fun getSmsTemplate(apiTemplateId: String): SmsTemplateRespDTO {
        val response = request("GET", "/v1/template/$apiTemplateId", null)
        return SmsTemplateRespDTO(
            id = response.string("id"),
            content = response.string("template"),
            auditStatus = when (response.string("audit_status")) {
                "passed" -> SmsTemplateAuditStatusEnum.SUCCESS.status
                "reviewing" -> SmsTemplateAuditStatusEnum.CHECKING.status
                "rejected" -> SmsTemplateAuditStatusEnum.FAIL.status
                else -> error("Unknown Qiniu template audit status: ${response["audit_status"]}")
            },
            auditReason = response.string("reject_reason"),
        )
    }

    private fun request(method: String, path: String, body: Map<String, Any?>?): Map<String, Any?> {
        val bodyText = body?.let(JsonUtils::toJsonString).orEmpty()
        val signDate = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .withZone(ZoneOffset.UTC).format(java.time.Instant.now())
        val headers = mapOf(
            "Host" to HOST,
            "Authorization" to signature(method, path, bodyText, signDate),
            "Content-Type" to "application/json",
            "X-Qiniu-Date" to signDate,
        )
        val builder = HttpRequest.newBuilder(URI.create("https://$HOST$path"))
        headers.forEach(builder::header)
        if (method == "POST") builder.POST(HttpRequest.BodyPublishers.ofString(bodyText)) else builder.GET()
        val response = HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString())
        return JsonUtils.parseMap(response.body()).orEmpty()
    }

    private fun signature(method: String, path: String, body: String, signDate: String): String {
        val data = buildString {
            append(method.uppercase()).append(' ').append(path)
            append("\nHost: ").append(HOST)
            append("\nContent-Type: application/json")
            append("\nX-Qiniu-Date: ").append(signDate)
            append("\n\n")
            append(body)
        }
        val mac = Mac.getInstance("HmacSHA1").apply {
            init(SecretKeySpec(requireNotNull(properties.apiSecret).toByteArray(StandardCharsets.UTF_8), "HmacSHA1"))
        }
        val encoded = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(mac.doFinal(data.toByteArray(StandardCharsets.UTF_8)))
        return "Qiniu ${requireNotNull(properties.apiKey)}:$encoded"
    }

    private companion object {
        const val HOST = "sms.qiniuapi.com"
    }
}

private fun decodeForm(text: String): Map<String, String> = text.split('&').mapNotNull { pair ->
    val index = pair.indexOf('=')
    if (index < 0) return@mapNotNull null
    URLDecoder.decode(pair.substring(0, index), StandardCharsets.UTF_8) to
        URLDecoder.decode(pair.substring(index + 1), StandardCharsets.UTF_8)
}.toMap()
