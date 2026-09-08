package im.hikaru.ruoyi.module.sync.service

import tools.jackson.databind.JsonNode

data class SyncCommandContext(
    val tenantId: Long,
    val userId: Long,
)

data class SyncCommandEnvelope(
    val commandId: String,
    val aggregateType: String,
    val aggregateId: String?,
    val operation: String,
    val payload: JsonNode,
    val baseVersion: Long?,
)

data class SyncCommandHandlerKey(
    val aggregateType: String,
    val operation: String,
)

sealed interface SyncCommandDecision {
    data class Applied(
        val aggregateId: String? = null,
        val serverVersion: Long,
    ) : SyncCommandDecision

    data class Rejected(
        val errorCode: String,
        val serverVersion: Long? = null,
    ) : SyncCommandDecision

    data class Conflict(
        val serverVersion: Long,
        val serverPayload: JsonNode? = null,
        val errorCode: String = "VERSION_CONFLICT",
    ) : SyncCommandDecision
}

interface SyncCommandHandler {
    val keys: Set<SyncCommandHandlerKey>

    fun handle(context: SyncCommandContext, command: SyncCommandEnvelope): SyncCommandDecision
}

enum class SyncChangeOperation {
    UPSERT,
    DELETE,
}

interface SyncChangeWriter {
    fun append(
        context: SyncCommandContext,
        resource: String,
        aggregateId: String,
        operation: SyncChangeOperation,
        aggregateVersion: Long,
        payload: Any,
    ): Long
}
