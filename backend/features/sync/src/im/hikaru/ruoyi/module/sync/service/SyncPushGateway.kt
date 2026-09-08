package im.hikaru.ruoyi.module.sync.service

import im.hikaru.contracts.sync.SYNC_CHANGE_NOTIFICATION_TYPE
import im.hikaru.contracts.sync.SYNC_PUSH_CURSOR_KEY
import im.hikaru.contracts.sync.SYNC_PUSH_RESOURCE_KEY
import im.hikaru.contracts.sync.SYNC_PUSH_TYPE_KEY
import im.hikaru.contracts.sync.SyncChangeNotification
import com.google.auth.oauth2.GoogleCredentials
import im.hikaru.ruoyi.framework.common.util.json.JsonUtils
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

data class SyncPushDeliveryResult(
    val invalidTokens: Set<String> = emptySet(),
)

fun interface SyncPushGateway {
    fun send(tokens: List<String>, notification: SyncChangeNotification): SyncPushDeliveryResult
}

class FirebaseHttpV1SyncPushGateway(
    private val credentials: GoogleCredentials,
    private val projectId: String,
    private val httpClient: HttpClient,
) : SyncPushGateway {
    override fun send(
        tokens: List<String>,
        notification: SyncChangeNotification,
    ): SyncPushDeliveryResult {
        val invalidTokens = tokens.distinct().mapNotNull { token ->
            when (sendOne(token, notification)) {
                SendResult.Delivered -> null
                SendResult.Unregistered -> token
            }
        }.toSet()
        return SyncPushDeliveryResult(invalidTokens)
    }

    private fun sendOne(token: String, notification: SyncChangeNotification): SendResult {
        val data = linkedMapOf(
            SYNC_PUSH_TYPE_KEY to SYNC_CHANGE_NOTIFICATION_TYPE,
            SYNC_PUSH_RESOURCE_KEY to notification.resource,
        ).apply {
            notification.cursor?.let { put(SYNC_PUSH_CURSOR_KEY, it.toString()) }
        }
        val body = JsonUtils.toJsonString(
            mapOf(
                "message" to mapOf(
                    "token" to token,
                    "data" to data,
                    "android" to mapOf("priority" to "high"),
                    "apns" to mapOf(
                        "headers" to mapOf(
                            "apns-push-type" to "background",
                            "apns-priority" to "5",
                        ),
                        "payload" to mapOf(
                            "aps" to mapOf("content-available" to 1),
                        ),
                    ),
                ),
            ),
        )
        val request = HttpRequest.newBuilder()
            .uri(URI.create("https://fcm.googleapis.com/v1/projects/$projectId/messages:send"))
            .timeout(Duration.ofSeconds(15))
            .header("Authorization", "Bearer ${accessToken()}")
            .header("Content-Type", "application/json; charset=UTF-8")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        return when {
            response.statusCode() in 200..299 -> SendResult.Delivered
            response.body().contains("UNREGISTERED") -> SendResult.Unregistered
            else -> error("FCM HTTP v1 request failed with status ${response.statusCode()}")
        }
    }

    private fun accessToken(): String = synchronized(credentials) {
        credentials.refreshIfExpired()
        requireNotNull(credentials.accessToken?.tokenValue) { "Unable to obtain an FCM access token" }
    }

    private enum class SendResult {
        Delivered,
        Unregistered,
    }
}
