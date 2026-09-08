package im.hikaru.contracts.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

const val SYNC_CHANGE_NOTIFICATION_TYPE = "sync-change"
const val SYNC_PUSH_TYPE_KEY = "type"
const val SYNC_PUSH_RESOURCE_KEY = "resource"
const val SYNC_PUSH_CURSOR_KEY = "cursor"

@Serializable
enum class SyncPushPlatform {
    ANDROID,
    IOS,
}

@Serializable
data class SyncPushDeviceRequest(
    val token: String,
    val platform: SyncPushPlatform,
)

@Serializable
data class SyncPushDeviceUnregisterRequest(
    val token: String,
)

@Serializable
data class SyncCommand(
    val commandId: String,
    val aggregateType: String,
    val aggregateId: String? = null,
    val operation: String,
    val payload: JsonObject,
    val baseVersion: Long? = null,
)

@Serializable
data class SyncCommandBatchRequest(
    val commands: List<SyncCommand>,
)

@Serializable
enum class SyncCommandOutcome {
    APPLIED,
    REJECTED,
    CONFLICT,
}

@Serializable
data class SyncCommandResult(
    val commandId: String,
    val outcome: SyncCommandOutcome,
    val replayed: Boolean,
    val aggregateId: String? = null,
    val serverVersion: Long? = null,
    val serverPayload: JsonObject? = null,
    val errorCode: String? = null,
)

@Serializable
data class SyncCommandBatchResponse(
    val results: List<SyncCommandResult>,
)

@Serializable
enum class SyncChangeOperation {
    UPSERT,
    DELETE,
}

@Serializable
data class SyncChange(
    val cursor: Long,
    val resource: String,
    val aggregateId: String,
    val operation: SyncChangeOperation,
    val aggregateVersion: Long,
    val payload: JsonObject,
)

@Serializable
data class SyncChangesResponse(
    val items: List<SyncChange>,
    val nextCursor: Long,
    val hasMore: Boolean,
    val resetRequired: Boolean = false,
    val resetCursor: Long? = null,
)

/** Best-effort hint only; clients still pull and apply the authoritative Changes API response. */
@Serializable
data class SyncChangeNotification(
    val resource: String,
    val cursor: Long? = null,
)
