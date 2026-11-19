package im.hikaru.harness.mcp.client

import java.net.URI
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

/** Credentials are environment variable references, never persisted literal values. */
@Serializable
sealed interface McpTransportConfig {
    @Serializable
    @SerialName("stdio")
    data class Stdio(
        val command: String,
        val args: List<String> = emptyList(),
        val cwd: String? = null,
        val envFrom: Map<String, String> = emptyMap(),
    ) : McpTransportConfig {
        init {
            require(command.isNotBlank() && '\u0000' !in command) { "Invalid MCP command" }
            require(args.none { '\u0000' in it }) { "Invalid MCP arguments" }
            require(cwd == null || (cwd.isNotBlank() && '\u0000' !in cwd)) { "Invalid MCP working directory" }
            validateMcpEnvironmentReferences(envFrom)
        }
    }

    @Serializable
    @SerialName("http")
    data class Http(
        val url: String,
        val headersFrom: Map<String, String> = emptyMap(),
        val bearerTokenEnvVar: String? = null,
    ) : McpTransportConfig {
        init {
            validateMcpUrl(url)
            require(headersFrom.keys.all { MCP_HEADER_NAME.matches(it) && it.lowercase() !in MCP_RESERVED_HEADERS }) {
                "Invalid or reserved MCP header name"
            }
            require(headersFrom.keys.map { it.lowercase() }.distinct().size == headersFrom.size) {
                "Duplicate MCP header name"
            }
            require(headersFrom.values.all(MCP_ENV_NAME::matches)) { "Invalid MCP header environment reference" }
            require(bearerTokenEnvVar == null || MCP_ENV_NAME.matches(bearerTokenEnvVar)) {
                "Invalid MCP bearer token environment reference"
            }
            require(bearerTokenEnvVar == null || headersFrom.keys.none { it.equals("Authorization", ignoreCase = true) }) {
                "Conflicting MCP authorization configuration"
            }
        }
    }
}

@Serializable
data class McpReconnectConfig(
    val enabled: Boolean = true,
    val maxAttempts: Int = 10,
    val initialDelayMs: Long = 500,
    val maxDelayMs: Long = 30_000,
    val stableAfterMs: Long = 30_000,
) {
    init {
        require(maxAttempts in 1..1_000) { "MCP reconnect attempts must be between 1 and 1000" }
        validateMcpDuration(initialDelayMs)
        validateMcpDuration(maxDelayMs)
        validateMcpDuration(stableAfterMs)
        require(maxDelayMs >= initialDelayMs) { "MCP reconnect maximum delay must not be less than initial delay" }
    }
}

@Serializable
data class McpServerConfig(
    val serverName: String,
    val transport: McpTransportConfig,
    val startupTimeoutMs: Long = 10_000,
    val toolCallTimeoutMs: Long = 60_000,
    val failOnStartupError: Boolean = false,
    val maxInstructionBytes: Int = 32_768,
    val reconnect: McpReconnectConfig = McpReconnectConfig(),
    val enabledTools: Set<String>? = null,
    val disabledTools: Set<String> = emptySet(),
) {
    init {
        validateMcpServerName(serverName)
        validateMcpDuration(startupTimeoutMs)
        validateMcpDuration(toolCallTimeoutMs)
        require(maxInstructionBytes in 1..1_048_576) { "MCP instruction byte limit must be between 1 and 1048576" }
        require(enabledTools.orEmpty().all(::validMcpRawName) && disabledTools.all(::validMcpRawName)) {
            "Invalid MCP tool filter name"
        }
    }
}

private val mcpConfigJson = Json { ignoreUnknownKeys = false }

/** Do not expose decoder diagnostics: they can include untrusted credential values. */
fun decodeMcpServerConfig(config: JsonObject): McpServerConfig = try {
    mcpConfigJson.decodeFromJsonElement<McpServerConfig>(config)
} catch (_: IllegalArgumentException) {
    throw IllegalArgumentException("Invalid MCP server configuration")
}

/** Inherit only basic process settings; credentials require explicit target -> source mappings. */
fun mcpProcessEnvironment(parent: Map<String, String>, envFrom: Map<String, String>): Map<String, String> {
    validateMcpEnvironmentReferences(envFrom)
    return buildMap {
        parent.forEach { (name, value) ->
            if (name in MCP_SAFE_ENVIRONMENT) {
                require('\u0000' !in value) { "Invalid environment variable $name" }
                put(name, value)
            }
        }
        envFrom.forEach { (target, source) ->
            val value = parent[source] ?: throw IllegalArgumentException("Missing environment variable $source")
            require('\u0000' !in value) { "Invalid environment variable $source" }
            put(target, value)
        }
    }
}

internal fun validateMcpServerName(serverName: String) {
    require(MCP_SERVER_NAME.matches(serverName)) { "Invalid MCP server name" }
}

internal fun validMcpRawName(name: String): Boolean = name.isNotBlank() && '\u0000' !in name

private fun validateMcpDuration(value: Long) {
    require(value in 1..3_600_000) { "MCP duration must be between 1 and 3600000 milliseconds" }
}

private fun validateMcpEnvironmentReferences(envFrom: Map<String, String>) {
    require(envFrom.all { (target, source) -> MCP_ENV_NAME.matches(target) && MCP_ENV_NAME.matches(source) }) {
        "Invalid MCP environment reference"
    }
}

private fun validateMcpUrl(url: String) {
    // URI parser exceptions contain the input. Never retain them as a cause.
    val uri = try {
        URI(url)
    } catch (_: Exception) {
        throw IllegalArgumentException("Invalid MCP HTTP URL")
    }
    val scheme = uri.scheme?.lowercase()
    val host = uri.host?.lowercase()
    require(
        !uri.isOpaque && host != null && uri.rawUserInfo == null && uri.rawFragment == null &&
            (uri.port == -1 || uri.port in 1..65_535) &&
            (scheme == "https" || (scheme == "http" && host in setOf("localhost", "127.0.0.1", "[::1]"))),
    ) { "Invalid MCP HTTP URL" }
}

private val MCP_SERVER_NAME = Regex("[A-Za-z0-9_-]{1,32}")
private val MCP_ENV_NAME = Regex("[A-Za-z_][A-Za-z0-9_]*")
private val MCP_HEADER_NAME = Regex("[!#$%&'*+.^_`|~A-Za-z0-9-]+")
private val MCP_RESERVED_HEADERS = setOf(
    "host", "content-length", "connection", "accept", "content-type", "mcp-session-id", "mcp-protocol-version",
)
private val MCP_SAFE_ENVIRONMENT = setOf(
    "PATH", "HOME", "USER", "TMPDIR", "TEMP", "TMP", "SystemRoot", "WINDIR", "PATHEXT", "LANG", "LC_ALL",
)
