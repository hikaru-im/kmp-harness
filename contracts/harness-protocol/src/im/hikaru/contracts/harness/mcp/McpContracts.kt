package im.hikaru.contracts.harness.mcp

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
public enum class McpAuthStatus {
    Unknown,
    Unsupported,
    NotLoggedIn,
    BearerToken,
    OAuth,
}

/** Transport-independent MCP server state exposed by the Host management API. */
@Serializable
public data class McpServerInfo(
    val entryId: String,
    val serverName: String,
    val config: JsonObject,
    val enabled: Boolean,
    val editable: Boolean,
    val connectionPhase: String,
    val authStatus: McpAuthStatus = McpAuthStatus.Unknown,
    val generation: Long = 0L,
    val toolCount: Int? = null,
    val resourcesAvailable: Boolean = false,
    val failureCode: String? = null,
)

@Serializable
public data class McpListResponse(
    val revision: String,
    val servers: List<McpServerInfo>,
)

@Serializable
public data class McpUpsertRequest(
    val expectedRevision: String,
    val serverName: String,
    val config: JsonObject,
    val enabled: Boolean = true,
)

@Serializable
public data class McpEnabledRequest(
    val expectedRevision: String,
    val entryId: String,
    val enabled: Boolean,
)

@Serializable
public data class McpRemoveRequest(
    val expectedRevision: String,
    val entryId: String,
)

@Serializable
public data class McpAuthenticateRequest(
    val serverName: String,
)

@Serializable
public data class McpAuthenticateResponse(
    val authorizationUrl: String? = null,
)

@Serializable
public data class McpMutationResponse(
    val revision: String,
    val saved: Boolean,
    val applied: Boolean,
    val applyFailureCode: String? = null,
    val server: McpServerInfo? = null,
)
