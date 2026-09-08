package im.hikaru.ruoyi.module.system.framework.sms.core.client.impl

import im.hikaru.ruoyi.framework.common.core.KeyValue
import im.hikaru.ruoyi.framework.common.util.json.JsonUtils
import im.hikaru.ruoyi.module.system.framework.sms.core.client.dto.SmsReceiveRespDTO
import im.hikaru.ruoyi.module.system.framework.sms.core.client.dto.SmsSendRespDTO
import im.hikaru.ruoyi.module.system.framework.sms.core.client.dto.SmsTemplateRespDTO
import im.hikaru.ruoyi.module.system.framework.sms.core.enums.SmsTemplateAuditStatusEnum
import im.hikaru.ruoyi.module.system.framework.sms.core.property.SmsChannelProperties
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toKotlinLocalDateTime
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class AliyunSmsClient(properties: SmsChannelProperties) : AbstractSmsClient(properties) {
    override fun sendSms(
        logId: Long,
        mobile: String,
        apiTemplateId: String,
        templateParams: List<KeyValue<String, Any?>>,
    ): SmsSendRespDTO {
        val response = request("SendSms", sortedMapOf(
            "PhoneNumbers" to mobile,
            "SignName" to requireNotNull(properties.signature),
            "TemplateCode" to apiTemplateId,
            "TemplateParam" to JsonUtils.toJsonString(templateParams.associate { it.key to it.value }),
            "OutId" to logId,
        ))
        return SmsSendRespDTO(
            success = response.string("Code") == "OK",
            serialNo = response.string("BizId"),
            apiRequestId = response.string("RequestId"),
            apiCode = response.string("Code"),
            apiMsg = response.string("Message"),
        )
    }

    override fun parseSmsReceiveStatus(text: String): List<SmsReceiveRespDTO> = parseObjectList(text).map { status ->
        SmsReceiveRespDTO(
            success = status.boolean("success"),
            errorCode = status.string("err_code"),
            errorMsg = status.string("err_msg"),
            mobile = status.string("phone_number"),
            receiveTime = parseDateTime(status.string("report_time")),
            serialNo = status.string("biz_id"),
            logId = status.long("out_id"),
        )
    }

    override fun getSmsTemplate(apiTemplateId: String): SmsTemplateRespDTO? {
        val response = request("GetSmsTemplate", sortedMapOf("TemplateCode" to apiTemplateId))
        if (response.string("Code") != "OK") return null
        return SmsTemplateRespDTO(
            id = response.string("TemplateCode"),
            content = response.string("TemplateContent"),
            auditStatus = when (response.int("TemplateStatus")) {
                0 -> SmsTemplateAuditStatusEnum.CHECKING.status
                1 -> SmsTemplateAuditStatusEnum.SUCCESS.status
                2 -> SmsTemplateAuditStatusEnum.FAIL.status
                else -> error("Unknown Aliyun template audit status: ${response["TemplateStatus"]}")
            },
            auditReason = response.string("Reason"),
        )
    }

    private fun request(action: String, queryParams: Map<String, Any?>): Map<String, Any?> {
        val query = queryParams.entries.joinToString("&") { (key, value) ->
            "${percentEncode(key)}=${percentEncode(value.toString())}"
        }
        val payloadHash = sha256Hex("")
        val headers = sortedMapOf(
            "host" to HOST,
            "x-acs-version" to VERSION,
            "x-acs-action" to action,
            "x-acs-date" to DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
                .withZone(ZoneOffset.UTC).format(java.time.Instant.now()),
            "x-acs-signature-nonce" to UUID.randomUUID().toString(),
            "x-acs-content-sha256" to payloadHash,
        )
        val canonicalHeaders = headers.entries.joinToString("") { (key, value) -> "${key.lowercase()}:${value.trim()}\n" }
        val signedHeaders = headers.keys.joinToString(";") { it.lowercase() }
        val canonicalRequest = "POST\n/\n$query\n$canonicalHeaders\n$signedHeaders\n$payloadHash"
        val signature = hmacSha256Hex(requireNotNull(properties.apiSecret), "ACS3-HMAC-SHA256\n${sha256Hex(canonicalRequest)}")
        val authorization = "ACS3-HMAC-SHA256 Credential=${requireNotNull(properties.apiKey)}, " +
            "SignedHeaders=$signedHeaders, Signature=$signature"
        val requestHeaders = LinkedHashMap(headers).apply { put("Authorization", authorization) }
        return postJson("$URL?$query", requestHeaders, "")
    }

    private companion object {
        const val URL = "https://dysmsapi.aliyuncs.com"
        const val HOST = "dysmsapi.aliyuncs.com"
        const val VERSION = "2017-05-25"
    }
}

class TencentSmsClient(properties: SmsChannelProperties) : AbstractSmsClient(properties) {
    private val secretId: String
    private val sdkAppId: String

    init {
        val parts = requireNotNull(properties.apiKey).trim().split(Regex("\\s+"))
        require(parts.size == 2) { "Tencent SMS apiKey must use 'secretId sdkAppId' format" }
        secretId = parts[0]
        sdkAppId = parts[1]
    }

    override fun sendSms(
        logId: Long,
        mobile: String,
        apiTemplateId: String,
        templateParams: List<KeyValue<String, Any?>>,
    ): SmsSendRespDTO {
        val response = request("SendSms", sortedMapOf(
            "PhoneNumberSet" to listOf(mobile),
            "SmsSdkAppId" to sdkAppId,
            "SignName" to properties.signature,
            "TemplateId" to apiTemplateId,
            "TemplateParamSet" to templateParams.map { it.value.toString() },
        )).objectMap("Response")
        response.objectMapOrNull("Error")?.let { error ->
            return SmsSendRespDTO(
                success = false,
                apiRequestId = response.string("RequestId"),
                apiCode = error.string("Code"),
                apiMsg = error.string("Message"),
            )
        }
        val sendResult = response.objectList("SendStatusSet").first()
        return SmsSendRespDTO(
            success = sendResult.string("Code") == "Ok",
            apiRequestId = response.string("RequestId"),
            serialNo = sendResult.string("SerialNo"),
            apiCode = sendResult.string("Code"),
            apiMsg = sendResult.string("Message"),
        )
    }

    override fun parseSmsReceiveStatus(text: String): List<SmsReceiveRespDTO> = parseObjectList(text).map { status ->
        SmsReceiveRespDTO(
            success = status.string("report_status") == "SUCCESS",
            errorCode = status.string("errmsg"),
            errorMsg = status.string("description"),
            mobile = status.string("mobile"),
            receiveTime = parseDateTime(status.string("user_receive_time")),
            serialNo = status.string("sid"),
        )
    }

    override fun getSmsTemplate(apiTemplateId: String): SmsTemplateRespDTO {
        val response = request("DescribeSmsTemplateList", sortedMapOf(
            "International" to 0,
            "TemplateIdSet" to listOf(apiTemplateId.toInt()),
        )).objectMap("Response")
        val result = response.objectList("DescribeTemplateStatusSet").first()
        return SmsTemplateRespDTO(
            id = apiTemplateId,
            content = result.string("TemplateContent"),
            auditStatus = when (result.int("StatusCode")) {
                1 -> SmsTemplateAuditStatusEnum.CHECKING.status
                0 -> SmsTemplateAuditStatusEnum.SUCCESS.status
                -1 -> SmsTemplateAuditStatusEnum.FAIL.status
                else -> error("Unknown Tencent template audit status: ${result["StatusCode"]}")
            },
            auditReason = result.string("ReviewReply"),
        )
    }

    private fun request(action: String, body: Map<String, Any?>): Map<String, Any?> {
        val bodyText = JsonUtils.toJsonString(body)
        val instant = java.time.Instant.now()
        val timestamp = instant.epochSecond
        val date = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC).format(instant)
        val lowerAction = action.lowercase(Locale.ROOT)
        val canonicalHeaders = "content-type:application/json; charset=utf-8\nhost:$HOST\nx-tc-action:$lowerAction\n"
        val signedHeaders = "content-type;host;x-tc-action"
        val canonicalRequest = "POST\n/\n\n$canonicalHeaders\n$signedHeaders\n${sha256Hex(bodyText)}"
        val credentialScope = "$date/sms/tc3_request"
        val stringToSign = "TC3-HMAC-SHA256\n$timestamp\n$credentialScope\n${sha256Hex(canonicalRequest)}"
        val secretDate = hmacSha256(("TC3" + requireNotNull(properties.apiSecret)).toByteArray(), date)
        val secretService = hmacSha256(secretDate, "sms")
        val secretSigning = hmacSha256(secretService, "tc3_request")
        val signature = hmacSha256(secretSigning, stringToSign).toHex()
        val headers = mapOf(
            "Content-Type" to "application/json; charset=utf-8",
            "Host" to HOST,
            "X-TC-Action" to action,
            "X-TC-Timestamp" to timestamp.toString(),
            "X-TC-Version" to VERSION,
            "X-TC-Region" to REGION,
            "Authorization" to "TC3-HMAC-SHA256 Credential=$secretId/$credentialScope, " +
                "SignedHeaders=$signedHeaders, Signature=$signature",
        )
        return postJson("https://$HOST", headers, bodyText)
    }

    private companion object {
        const val HOST = "sms.tencentcloudapi.com"
        const val VERSION = "2021-01-11"
        const val REGION = "ap-guangzhou"
    }
}

internal fun postJson(url: String, headers: Map<String, String>, body: String): Map<String, Any?> {
    val builder = HttpRequest.newBuilder(URI.create(url)).POST(HttpRequest.BodyPublishers.ofString(body))
    headers.forEach(builder::header)
    val response = HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString())
    return JsonUtils.parseMap(response.body()).orEmpty()
}

internal fun parseObjectList(text: String): List<Map<String, Any?>> =
    JsonUtils.parseArray(text, Map::class.java).map { raw -> raw.entries.associate { it.key.toString() to it.value } }

internal fun parseDateTime(value: String?): LocalDateTime? {
    if (value.isNullOrBlank()) return null
    val normalized = value.trim().replace(' ', 'T').removeSuffix("Z")
    return runCatching { java.time.LocalDateTime.parse(normalized).toKotlinLocalDateTime() }
        .recoverCatching { java.time.OffsetDateTime.parse(value).toLocalDateTime().toKotlinLocalDateTime() }
        .getOrNull()
}

internal fun Map<String, Any?>.string(key: String): String? = this[key]?.toString()
internal fun Map<String, Any?>.int(key: String): Int? = (this[key] as? Number)?.toInt() ?: string(key)?.toIntOrNull()
internal fun Map<String, Any?>.long(key: String): Long? = (this[key] as? Number)?.toLong() ?: string(key)?.toLongOrNull()
internal fun Map<String, Any?>.boolean(key: String): Boolean = this[key] as? Boolean ?: string(key).toBoolean()
internal fun Map<String, Any?>.objectMap(key: String): Map<String, Any?> = requireNotNull(objectMapOrNull(key))
internal fun Map<String, Any?>.objectMapOrNull(key: String): Map<String, Any?>? =
    (this[key] as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value }
internal fun Map<String, Any?>.objectList(key: String): List<Map<String, Any?>> =
    (this[key] as? Collection<*>)?.mapNotNull { item ->
        (item as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value }
    }.orEmpty()

internal fun percentEncode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)
    .replace("+", "%20").replace("*", "%2A").replace("%7E", "~")

internal fun sha256Hex(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(StandardCharsets.UTF_8)).toHex()

internal fun hmacSha256(key: ByteArray, value: String): ByteArray = Mac.getInstance("HmacSHA256").run {
    init(SecretKeySpec(key, "HmacSHA256"))
    doFinal(value.toByteArray(StandardCharsets.UTF_8))
}

internal fun hmacSha256Hex(key: String, value: String): String =
    hmacSha256(key.toByteArray(StandardCharsets.UTF_8), value).toHex()

internal fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
