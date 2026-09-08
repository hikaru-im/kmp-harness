package im.hikaru.ruoyi.module.sync.controller.app.vo

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import tools.jackson.databind.JsonNode

@Schema(description = "App sync command batch request")
class AppSyncCommandBatchReqVO {
    @field:NotEmpty
    @field:Size(max = 50)
    @field:Valid
    var commands: List<AppSyncCommandReqVO> = emptyList()
}

@Schema(description = "One replayable App sync command")
class AppSyncCommandReqVO {
    @field:NotBlank
    @field:Size(max = 128)
    var commandId: String = ""

    @field:NotBlank
    @field:Size(max = 64)
    var aggregateType: String = ""

    @field:Size(max = 128)
    var aggregateId: String? = null

    @field:NotBlank
    @field:Size(max = 32)
    var operation: String = ""

    @field:NotNull
    var payload: JsonNode? = null

    @field:Min(1)
    var baseVersion: Long? = null
}

enum class AppSyncCommandOutcomeVO {
    APPLIED,
    REJECTED,
    CONFLICT,
}

data class AppSyncCommandResultVO(
    val commandId: String,
    val outcome: AppSyncCommandOutcomeVO,
    val replayed: Boolean,
    val aggregateId: String? = null,
    val serverVersion: Long? = null,
    val serverPayload: JsonNode? = null,
    val errorCode: String? = null,
)

data class AppSyncCommandBatchRespVO(
    val results: List<AppSyncCommandResultVO>,
)

data class AppSyncChangesRespVO(
    val items: List<AppSyncChangeVO>,
    val nextCursor: Long,
    val hasMore: Boolean,
    val resetRequired: Boolean = false,
    val resetCursor: Long? = null,
)

data class AppSyncChangeVO(
    val cursor: Long,
    val resource: String,
    val aggregateId: String,
    val operation: String,
    val aggregateVersion: Long,
    val payload: JsonNode,
)

data class AppSyncChangesQuery(
    @field:NotBlank
    @field:Size(max = 64)
    val resource: String,
    @field:Min(0)
    val cursor: Long = 0,
    @field:Min(1)
    @field:Max(500)
    val limit: Int = 100,
)
