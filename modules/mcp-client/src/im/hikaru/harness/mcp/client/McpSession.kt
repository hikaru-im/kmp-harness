package im.hikaru.harness.mcp.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.request.header
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.ReconnectionOptions
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.shared.Transport
import io.modelcontextprotocol.kotlin.sdk.types.*
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.serialization.json.JsonObject

/** Sanitized boundary errors never retain SDK exceptions containing server headers/content. */
class McpOperationException(val code: String) : IllegalStateException("MCP operation failed ($code)")

interface McpSession {
    val instructions: String?
    val supportsResources: Boolean
    val disconnected: Deferred<Unit>
    val toolsChanged: Channel<Unit>
    suspend fun listTools(): List<Tool>
    suspend fun callTool(name: String, arguments: JsonObject): CallToolResult
    suspend fun listResources(): List<Resource>
    suspend fun listResourceTemplates(): List<ResourceTemplate>
    suspend fun readResource(uri: String): ReadResourceResult
    suspend fun close()
}

fun interface McpConnector {
    suspend fun connect(config: McpServerConfig): McpSession
}

/** The protocol, framing and HTTP/SSE are exclusively owned by the unmodified SDK. */
class SdkMcpConnector(
    private val environment: () -> Map<String, String> = { System.getenv() },
) : McpConnector {
    override suspend fun connect(config: McpServerConfig): McpSession {
        var process: Process? = null
        var http: HttpClient? = null
        var transport: Transport? = null
        val closed = CompletableDeferred<Unit>()
        val changed = Channel<Unit>(Channel.CONFLATED)
        val client = Client(Implementation("kmp-harness", "1"))
        try {
            transport = when (val settings = config.transport) {
                is McpTransportConfig.Stdio -> {
                    val env = mcpProcessEnvironment(environment(), settings.envFrom)
                    // start() itself is blocking. Cancellation must not lose ownership of a spawned child.
                    process = withContext(NonCancellable + Dispatchers.IO) {
                        ProcessBuilder(listOf(settings.command) + settings.args).apply {
                            settings.cwd?.let { directory(Path.of(it).toFile()) }
                            redirectErrorStream(false)
                            environment().apply { clear(); putAll(env) }
                        }.start()
                    }
                    currentCoroutineContext().ensureActive()
                    StdioClientTransport(
                        input = process!!.inputStream.asSource().buffered(),
                        output = process!!.outputStream.asSink().buffered(),
                        error = process!!.errorStream.asSource().buffered(),
                        // Never forward server stderr to logs or classify arbitrary text as fatal.
                        classifyStderr = { StdioClientTransport.StderrSeverity.IGNORE },
                    )
                }
                is McpTransportConfig.Http -> {
                    val env = environment()
                    fun resolve(name: String): String = env[name]?.takeIf { it.isNotEmpty() }
                        ?: throw McpOperationException("missing-environment-$name")
                    val headers = settings.headersFrom.mapValues { resolve(it.value) }.toMutableMap()
                    settings.bearerTokenEnvVar?.let { headers["Authorization"] = "Bearer ${resolve(it)}" }
                    require(headers.values.none { '\r' in it || '\n' in it || '\u0000' in it }) {
                        "Invalid MCP header environment value"
                    }
                    http = HttpClient(CIO) { followRedirects = false; install(SSE) }
                    StreamableHttpClientTransport(
                        http!!, settings.url,
                        // One supervisor owns whole-connection retry; SDK must not create another budget.
                        ReconnectionOptions(maxRetries = 0),
                    ) { headers.forEach { (key, value) -> header(key, value) } }
                }
            }
            transport.onClose { closed.complete(Unit) }
            client.setNotificationHandler<ToolListChangedNotification>(Method.Defined.NotificationsToolsListChanged) {
                changed.trySend(Unit)
                CompletableDeferred(Unit)
            }
            mcpDeadline(config.startupTimeoutMs) { client.connect(transport) }
            return SdkSession(client, process, http, closed, changed, config.toolCallTimeoutMs)
        } catch (cancelled: CancellationException) {
            cleanup(client, process, http, changed)
            throw cancelled
        } catch (error: McpOperationException) {
            cleanup(client, process, http, changed)
            throw error
        } catch (error: Exception) {
            cleanup(client, process, http, changed)
            throw McpOperationException("connect-${error::class.simpleName ?: "error"}")
        }
    }
}

internal suspend fun <T> mcpDeadline(timeoutMs: Long, block: suspend () -> T): T {
    // SDK 0.11 RequestOptions does not bound response.await(); keep a Host-owned outer deadline.
    val result = withTimeoutOrNull(timeoutMs) { TimedValue(block()) }
        ?: throw McpOperationException("timeout")
    return result.value
}
private data class TimedValue<T>(val value: T)

private class SdkSession(
    private val client: Client,
    private val process: Process?,
    private val http: HttpClient?,
    override val disconnected: CompletableDeferred<Unit>,
    override val toolsChanged: Channel<Unit>,
    private val timeoutMs: Long,
) : McpSession {
    private val closing = AtomicBoolean()
    override val instructions: String? get() = client.serverInstructions
    override val supportsResources: Boolean get() = client.serverCapabilities?.resources != null

    private suspend fun <T> request(block: suspend () -> T): T {
        check(!closing.get()) { "MCP session is closed" }
        try {
            return mcpDeadline(timeoutMs, block)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: McpOperationException) {
            throw error
        } catch (_: Exception) {
            throw McpOperationException("request")
        }
    }

    override suspend fun listTools(): List<Tool> = request {
        if (client.serverCapabilities?.tools == null) emptyList() else
            pages { cursor -> client.listTools(ListToolsRequest(cursor?.let { PaginatedRequestParams(it) })).let { it.tools to it.nextCursor } }
    }
    override suspend fun callTool(name: String, arguments: JsonObject): CallToolResult = request {
        client.callTool(CallToolRequest(CallToolRequestParams(name, arguments)))
    }
    override suspend fun listResources(): List<Resource> = request {
        pages { cursor -> client.listResources(ListResourcesRequest(cursor?.let { PaginatedRequestParams(it) })).let { it.resources to it.nextCursor } }
    }
    override suspend fun listResourceTemplates(): List<ResourceTemplate> = request {
        pages { cursor -> client.listResourceTemplates(ListResourceTemplatesRequest(cursor?.let { PaginatedRequestParams(it) })).let { it.resourceTemplates to it.nextCursor } }
    }
    override suspend fun readResource(uri: String): ReadResourceResult = request { client.readResource(ReadResourceRequest(ReadResourceRequestParams(uri))) }
    override suspend fun close() {
        if (closing.compareAndSet(false, true)) {
            disconnected.complete(Unit)
            cleanup(client, process, http, toolsChanged)
        }
    }
}

/** Bound both page count and item count; repeated cursors cannot loop forever. */
internal suspend fun <T> pages(fetch: suspend (String?) -> Pair<List<T>, String?>): List<T> {
    var cursor: String? = null
    val cursors = mutableSetOf<String>()
    val result = mutableListOf<T>()
    repeat(100) {
        val (items, next) = fetch(cursor)
        require(result.size + items.size <= 10_000) { "MCP inventory exceeds limit" }
        result += items
        if (next == null) return result
        require(next.isNotEmpty() && cursors.add(next)) { "MCP repeated pagination cursor" }
        cursor = next
    }
    throw McpOperationException("page-limit")
}

private suspend fun cleanup(client: Client, process: Process?, http: HttpClient?, changes: Channel<Unit>) {
    withContext(NonCancellable + Dispatchers.IO) {
        changes.close()
        // Close the SDK transport and all pipes first so a compliant child can exit naturally;
        // force-kill remains the bounded fallback for a child that ignores EOF.
        withTimeoutOrNull(2_000) { runCatching { client.close() } }
        process?.let { child ->
            runCatching { child.inputStream.close() }
            runCatching { child.outputStream.close() }
            runCatching { child.errorStream.close() }
            runCatching { child.toHandle().descendants().forEach { it.destroyForcibly() } }
            runCatching { child.toHandle().destroyForcibly() }
            runCatching { child.toHandle().onExit().get(2, java.util.concurrent.TimeUnit.SECONDS) }
            runCatching { child.waitFor(2, java.util.concurrent.TimeUnit.SECONDS) }
        }
        http?.close()
    }
}
