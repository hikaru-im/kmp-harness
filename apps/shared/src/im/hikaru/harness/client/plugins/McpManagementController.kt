package im.hikaru.harness.client.plugins

import im.hikaru.contracts.common.ApiResult
import im.hikaru.contracts.harness.mcp.McpAuthenticateRequest
import im.hikaru.contracts.harness.mcp.McpEnabledRequest
import im.hikaru.contracts.harness.mcp.McpListResponse
import im.hikaru.contracts.harness.mcp.McpMutationResponse
import im.hikaru.contracts.harness.mcp.McpRemoveRequest
import im.hikaru.contracts.harness.mcp.McpServerInfo
import im.hikaru.contracts.harness.mcp.McpUpsertRequest
import im.hikaru.harness.client.connection.McpApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

public sealed interface McpManagementLoadState {
    public data object Unbound : McpManagementLoadState
    public data object Loading : McpManagementLoadState
    public data class Success(val response: McpListResponse) : McpManagementLoadState
    public data class Error(val code: Int?, val message: String) : McpManagementLoadState
}

public data class McpManagementState(
    val scope: PluginScope? = null,
    val load: McpManagementLoadState = McpManagementLoadState.Unbound,
    val mutationBusy: Boolean = false,
    val editorServer: McpServerInfo? = null,
    val editorVisible: Boolean = false,
    val removeServer: McpServerInfo? = null,
) {
    public val revision: String?
        get() = (load as? McpManagementLoadState.Success)?.response?.revision

    public val servers: List<McpServerInfo>
        get() = (load as? McpManagementLoadState.Success)?.response?.servers.orEmpty()

    public val canRefresh: Boolean
        get() = scope != null && !mutationBusy

    public val canAdd: Boolean
        get() = revision != null && !mutationBusy
}

public sealed interface McpManagementAction {
    public data object Refresh : McpManagementAction
    public data object BeginAdd : McpManagementAction
    public data class BeginEdit(val server: McpServerInfo) : McpManagementAction
    public data class SwitchTransport(val transport: String, val serverName: String) : McpManagementAction
    public data object DismissEditor : McpManagementAction
    public data class UpdateDraft(val serverName: String, val transform: (McpEditorDraft) -> McpEditorDraft) : McpManagementAction
    public data class SetEnabled(val server: McpServerInfo, val enabled: Boolean) : McpManagementAction
    public data object SaveEditor : McpManagementAction
    public data class ConfirmRemove(val server: McpServerInfo?) : McpManagementAction
    public data object Remove : McpManagementAction
    public data class Authenticate(val server: McpServerInfo) : McpManagementAction
}

public data class McpEditorDraft(
    val serverName: String = "",
    val transport: String = "stdio",
    val command: String = "",
    val args: String = "",
    val cwd: String = "",
    val envFrom: String = "",
    val url: String = "",
    val bearerTokenEnvVar: String = "",
    val headersFrom: String = "",
    val enabled: Boolean = true,
)

/** An external URL opener supplied by the platform. Null means no browser capability is mounted. */
public fun interface McpExternalUrlOpener {
    public fun open(url: String): Boolean
}

private const val MCP_CONFLICT_CODE = 409

/**
 * UI-owned MCP page controller. Its only data source is the Host's typed management API; a failed or
 * unavailable read never becomes an empty server list.
 */
public class McpManagementController(
    private val coroutineScope: CoroutineScope,
    private val openExternalUrl: McpExternalUrlOpener? = null,
    private val onError: (String) -> Unit = {},
) {
    private val mutableState = MutableStateFlow(McpManagementState())
    public val state: StateFlow<McpManagementState> = mutableState.asStateFlow()
    private var epoch: Long = 0
    private var request: Long = 0
    private var mutationJob: Job? = null
    private var mutationBusy = false
    private var closed = false
    private val drafts = mutableMapOf<String, McpEditorDraft>()

    /** Connect the same scope and API is idempotent; replacement cancels stale requests. */
    public fun bind(scope: PluginScope?, api: McpApi?) {
        if (closed) return
        val nextApi = if (scope == null) null else api
        val current = mutableState.value
        if (current.scope == scope && boundApi === nextApi) return
        invalidate()
        boundApi = nextApi
        mutableState.value = McpManagementState(scope = scope)
        if (nextApi != null) refresh()
    }

    private var boundApi: McpApi? = null

    public fun close() {
        if (closed) return
        closed = true
        invalidate()
        boundApi = null
        mutableState.value = McpManagementState()
    }

    public fun refresh() {
        val api = boundApi ?: return
        launchList(api)
    }

    public fun openEditor(server: McpServerInfo? = null) {
        val name = server?.serverName.orEmpty()
        mutableState.value = mutableState.value.copy(editorServer = server, editorVisible = true)
        drafts[name] = server?.let(::draftFrom) ?: McpEditorDraft()
        mutableDrafts.value++
    }

    public fun closeEditor() {
        mutableState.value = mutableState.value.copy(editorVisible = false)
    }

    private val mutableDrafts = MutableStateFlow(0)
    public val draftChanges: StateFlow<Int> = mutableDrafts.asStateFlow()

    public fun updateDraft(serverName: String, transform: (McpEditorDraft) -> McpEditorDraft) {
        val draft = drafts[serverName] ?: return
        drafts[serverName] = transform(draft)
        mutableDrafts.value++
    }

    public fun draft(serverName: String): McpEditorDraft = drafts[serverName] ?: McpEditorDraft()

    public fun confirmRemove(server: McpServerInfo?) {
        mutableState.value = mutableState.value.copy(removeServer = server)
    }

    public fun dispatch(action: McpManagementAction) {
        if (closed || mutationBusy) return
        when (action) {
            McpManagementAction.Refresh -> refresh()
            McpManagementAction.BeginAdd -> openEditor()
            is McpManagementAction.BeginEdit -> openEditor(action.server)
            is McpManagementAction.SwitchTransport ->
                updateDraft(action.serverName) { it.copy(transport = action.transport) }
            McpManagementAction.DismissEditor -> closeEditor()
            is McpManagementAction.UpdateDraft -> updateDraft(action.serverName, action.transform)
            is McpManagementAction.SetEnabled -> mutate { api ->
                api.setEnabled(McpEnabledRequest(revision(), action.server.entryId, action.enabled))
            }
            McpManagementAction.SaveEditor -> {
                val editor = mutableState.value.editorServer
                val draft = drafts[editor?.serverName.orEmpty()] ?: McpEditorDraft()
                val name = editor?.serverName ?: draft.serverName.trim()
                val config = editorConfig(draft) ?: return
                mutate { api ->
                    api.upsert(McpUpsertRequest(revision(), name, config, enabled = editor?.enabled ?: draft.enabled))
                }
            }
            is McpManagementAction.ConfirmRemove -> confirmRemove(action.server)
            McpManagementAction.Remove -> {
                val target = mutableState.value.removeServer ?: return
                mutate { api -> api.remove(McpRemoveRequest(revision(), target.entryId)) }
            }
            is McpManagementAction.Authenticate -> authenticate(action.server)
        }
    }

    private fun authenticate(server: McpServerInfo) {
        val api = boundApi ?: return
        val currentEpoch = epoch
        val apiAtStart = api
        mutationJob = coroutineScope.launch {
            setBusy(true, currentEpoch)
            try {
                val result = apiAtStart.authenticate(McpAuthenticateRequest(server.serverName))
                if (!isCurrent(currentEpoch, apiAtStart)) return@launch
                if (!result.isSuccess) {
                    fail(result)
                    return@launch
                }
                val url = result.data?.authorizationUrl
                if (url.isNullOrBlank() || openExternalUrl == null || !isValidHttpsUrl(url)) {
                    onError("无法启动授权")
                    return@launch
                }
                if (!openExternalUrl.open(url)) {
                    onError("无法打开浏览器")
                    return@launch
                }
                refresh(apiAtStart, currentEpoch)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                if (isCurrent(currentEpoch, apiAtStart)) onError("授权失败")
            } finally {
                if (isCurrent(currentEpoch, apiAtStart)) setBusy(false, currentEpoch)
            }
        }
    }

    private fun mutate(block: suspend (McpApi) -> ApiResult<McpMutationResponse>) {
        val api = boundApi ?: return
        val currentEpoch = epoch
        val apiAtStart = api
        mutationJob = coroutineScope.launch {
            setBusy(true, currentEpoch)
            try {
                val result = block(apiAtStart)
                if (!isCurrent(currentEpoch, apiAtStart)) return@launch
                if (!result.isSuccess) {
                    fail(result)
                    if (result.code == MCP_CONFLICT_CODE) launchList(apiAtStart, currentEpoch)
                    return@launch
                }
                mutableState.value = mutableState.value.copy(editorVisible = false, removeServer = null)
                refresh(apiAtStart, currentEpoch)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                if (isCurrent(currentEpoch, apiAtStart)) onError("MCP 操作失败")
            } finally {
                if (isCurrent(currentEpoch, apiAtStart)) setBusy(false, currentEpoch)
            }
        }
    }

    private fun refresh(api: McpApi, currentEpoch: Long = epoch) {
        launchList(api, currentEpoch)
    }

    private var listJob: Job? = null

    private fun launchList(api: McpApi, currentEpoch: Long = epoch) {
        if (closed) return
        val token = ++request
        listJob?.cancel()
        mutableState.value = mutableState.value.copy(load = McpManagementLoadState.Loading)
        val job = coroutineScope.launch {
            val result = try {
                api.list()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                ApiResult(code = null, msg = "MCP 服务读取失败")
            }
            if (!isCurrent(currentEpoch, api, token)) return@launch
            mutableState.value = mutableState.value.copy(
                load = if (result.isSuccess && result.data != null) {
                    McpManagementLoadState.Success(result.data!!)
                } else {
                    McpManagementLoadState.Error(result.code, failMessage(result))
                },
            )
        }
        listJob = job
        job.invokeOnCompletion { if (listJob === job) listJob = null }
    }

    private fun fail(result: ApiResult<*>) = onError(failMessage(result))

    private fun failMessage(result: ApiResult<*>): String = when (result.code) {
        MCP_CONFLICT_CODE -> "配置已被更新，请重试"
        422 -> "当前连接不支持授权"
        else -> result.msg?.takeIf { it.isNotBlank() } ?: "MCP 服务不可用"
    }

    private fun revision(): String = mutableState.value.revision
        ?: throw IllegalStateException("MCP list has not loaded")

    private fun setBusy(value: Boolean, currentEpoch: Long) {
        if (!isCurrent(currentEpoch)) return
        mutationBusy = value
        mutableState.value = mutableState.value.copy(mutationBusy = value)
    }

    private fun isCurrent(currentEpoch: Long, api: McpApi? = boundApi, token: Long = request): Boolean =
        !closed && currentEpoch == epoch && api === boundApi && token == request

    private fun invalidate() {
        epoch++
        request++
        mutationJob?.cancel()
        mutationJob = null
        listJob?.cancel()
        listJob = null
        mutationBusy = false
        drafts.clear()
        mutableDrafts.value++
    }

    private fun draftFrom(server: McpServerInfo): McpEditorDraft {
        val transport = server.config["transport"] as? JsonObject ?: JsonObject(emptyMap())
        return McpEditorDraft(
            serverName = server.serverName,
            transport = transport.string("type") ?: "stdio",
            command = transport.string("command").orEmpty(),
            args = transport.strings("args"),
            cwd = transport.string("cwd").orEmpty(),
            envFrom = transport.references("envFrom"),
            url = transport.string("url").orEmpty(),
            bearerTokenEnvVar = transport.string("bearerTokenEnvVar").orEmpty(),
            headersFrom = transport.references("headersFrom"),
            enabled = server.enabled,
        )
    }

    private fun editorConfig(draft: McpEditorDraft): JsonObject? {
        val name = mutableState.value.editorServer?.serverName ?: draft.serverName.trim()
        val serverNameField = name
        if (!Regex("[A-Za-z0-9_-]{1,32}").matches(name)) {
            onError("服务器名称无效")
            return null
        }
        val transport = when (draft.transport) {
            "stdio" -> {
                val command = draft.command.trim()
                if (command.isBlank()) {
                    onError("命令不能为空")
                    return null
                }
                mapOf(
                    "type" to "stdio",
                    "command" to command,
                    "args" to draft.args.lines().flatMap { it.split(',').map(String::trim) }.filter { it.isNotEmpty() },
                    "cwd" to draft.cwd.trim(),
                    "envFrom" to parseReferences(draft.envFrom),
                )
            }
            "http" -> {
                val url = draft.url.trim()
                if (!isValidHttpsUrl(url)) {
                    onError("URL 无效")
                    return null
                }
                mapOf(
                    "type" to "http",
                    "url" to url,
                    "bearerTokenEnvVar" to draft.bearerTokenEnvVar.trim(),
                    "headersFrom" to parseReferences(draft.headersFrom),
                )
            }
            else -> {
                onError("传输类型无效")
                return null
            }
        }
        return jsonConfig(serverNameField, transport)
    }
}

private fun jsonConfig(serverName: String, transport: Map<String, Any?>): JsonObject =
    buildJsonObject {
        put("serverName", serverName)
        put("transport", buildJsonObject {
            transport.forEach { (key, value) ->
                when (value) {
                    is String -> if (value.isNotEmpty()) put(key, value)
                    is List<*> -> put(key, buildJsonArray {
                        value.filterIsInstance<String>().forEach { add(JsonPrimitive(it)) }
                    })
                    is Map<*, *> -> put(key, buildJsonObject {
                        value.forEach { (target, source) ->
                            if (target is String && source is String) put(target, source)
                        }
                    })
                }
            }
        })
    }

private fun JsonObject.string(key: String): String? =
    (get(key) as? JsonPrimitive)?.content

private fun JsonObject.strings(key: String): String =
    (get(key) as? JsonArray)?.joinToString(", ") {
        (it as? JsonPrimitive)?.content.orEmpty()
    }.orEmpty()

private fun JsonObject.references(key: String): String =
    (get(key) as? JsonObject)?.entries?.joinToString("\n") { "${it.key}=${(it.value as? JsonPrimitive)?.content.orEmpty()}" }.orEmpty()

private fun parseReferences(value: String): Map<String, String> = value.lines().mapNotNull { line ->
    val separator = line.indexOf('=')
    if (separator <= 0) return@mapNotNull null
    val target = line.substring(0, separator).trim()
    val source = line.substring(separator + 1).trim()
    if (!target.all { it.isLetterOrDigit() || it == '_' } || !source.all { it.isLetterOrDigit() || it == '_' }) {
        return@mapNotNull null
    }
    target to source
}.toMap()

private fun isValidHttpsUrl(value: String): Boolean {
    val schemeEnd = value.indexOf("://")
    if (schemeEnd <= 0 || !value.substring(0, schemeEnd).equals("https", ignoreCase = true)) return false
    val authorityStart = schemeEnd + 3
    val authorityEnd = value.indexOfAny(charArrayOf('/', '?', '#'), authorityStart).let {
        if (it < 0) value.length else it
    }
    val authority = value.substring(authorityStart, authorityEnd)
    if (authority.isBlank() || '@' in authority || '#' in value || value.any { it.isWhitespace() }) return false
    val host = authority.substringBeforeLast(':')
    val port = authority.substringAfter(':', "")
    if (host.isBlank() || (!host.startsWith('[') && ':' in host)) return false
    if (port.isNotEmpty() && (port.toIntOrNull() == null || port.toInt() !in 1..65_535)) return false
    return true
}
