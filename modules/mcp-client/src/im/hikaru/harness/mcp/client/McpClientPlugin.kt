package im.hikaru.harness.mcp.client

import im.hikaru.contracts.harness.mcp.McpAuthStatus
import im.hikaru.harness.llm.TextBlock
import im.hikaru.harness.llm.ToolSchema
import im.hikaru.harness.loader.jsonObjectConfig
import im.hikaru.harness.loader.pluginDefinition
import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.effect.EffectScope
import im.hikaru.harness.runtime.plugin.InjectSpec
import im.hikaru.harness.runtime.plugin.Plugin
import im.hikaru.harness.system.prompt.*
import im.hikaru.harness.tools.*
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import io.modelcontextprotocol.kotlin.sdk.types.TaskSupport
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.*
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement

const val MCP_CLIENT_PLUGIN_NAME = "mcp-client"

class McpClientPlugin(private val connector: McpConnector = SdkMcpConnector()) : Plugin<McpServerConfig> {
    override val inject = setOf(
        InjectSpec.required(ToolsKey), InjectSpec.required(McpRegistryKey), InjectSpec.optional(SystemPromptKey),
    )

    override suspend fun apply(context: Context, config: McpServerConfig, scope: EffectScope) {
        val initialAuthStatus = when (config.transport) {
            is McpTransportConfig.Stdio -> McpAuthStatus.Unsupported
            is McpTransportConfig.Http -> McpAuthStatus.Unknown
        }
        val lease = context.require(McpRegistryKey).reserve(config.serverName)
        scope.add(lease)
        val batch = context.require(ToolsKey).registrationBatch()
        scope.add(batch)
        val active = AtomicReference<McpSession?>(null)
        val instructions = AtomicReference("")
        context.get(SystemPromptKey)?.let { prompt ->
            scope.add(prompt.section(PromptSection(
                name = "mcp-server:${config.serverName}",
                order = PromptSectionOrders.McpServers,
                interpolate = false,
                text = { instructions.get() },
            )))
        }
        val first = CompletableDeferred<Boolean>()
        val worker = context.managedScope().launch {
            var attempts = 0
            var generation = 0L
            while (isActive) {
                var session: McpSession? = null
                var connectedAt: Long? = null
                var failureCode = "connect"
                try {
                    lease.publish(McpServerSnapshot(config.serverName,
                        if (generation == 0L && attempts == 0) McpConnectionPhase.Connecting else McpConnectionPhase.Reconnecting,
                        generation, authStatus = initialAuthStatus))
                    session = mcpDeadline(config.startupTimeoutMs) { connector.connect(config) }
                    val current = session
                    active.set(current)
                    generation++
                    suspend fun syncTools() {
                        val tools = current.listTools().filter { tool ->
                            (config.enabledTools == null || tool.name in config.enabledTools) && tool.name !in config.disabledTools &&
                                tool.execution?.taskSupport != TaskSupport.Required
                        }
                        require(tools.map { it.name }.distinct().size == tools.size) { "Duplicate MCP raw tool names" }
                        val registrations = tools.map { tool ->
                            val schema = ToolSchema(
                                mcpPublicToolName(config.serverName, tool.name), tool.description.orEmpty(),
                                // Accepted SDK compatibility limit: do not pretend this is raw schema.
                                McpJson.encodeToJsonElement(tool.inputSchema) as JsonObject,
                            )
                            ToolRegistration(schema, object : ContextualToolHandler {
                                override suspend fun execute(request: ToolCallRequest, execution: ToolExecutionContext): ToolExecutionResult {
                                    require(execution.context.root === context.root) { "MCP execution belongs to another Host" }
                                    require(request.scope == null || request.scope == execution.session.id.value) { "MCP scope mismatch" }
                                    if (active.get() !== current) return unavailable()
                                    val arguments = try { Json.parseToJsonElement(request.arguments) as? JsonObject }
                                    catch (_: Exception) { null }
                                    if (arguments == null) return ToolExecutionResult(listOf(TextBlock("MCP arguments must be a JSON object")), true)
                                    return try {
                                        val result = mcpDeadline(config.toolCallTimeoutMs) { current.callTool(tool.name, arguments) }
                                        if (active.get() !== current) unavailable() else projectMcpResult(result)
                                    } catch (cancelled: CancellationException) { throw cancelled }
                                    catch (_: Exception) { ToolExecutionResult(listOf(TextBlock("MCP tool call failed")), true) }
                                }
                            })
                        }
                        check(active.get() === current && !current.disconnected.isCompleted) { "MCP generation is stale" }
                        batch.replace(registrations)
                        instructions.set(mcpInstructionText(config.serverName, current.instructions.orEmpty(), config.maxInstructionBytes))
                        lease.publish(McpServerSnapshot(config.serverName, McpConnectionPhase.Connected,
                            generation, registrations.size, current.supportsResources,
                            authStatus = initialAuthStatus), current)
                    }
                    // Both initial discovery and later notifications share this single coroutine.
                    syncTools()
                    connectedAt = System.nanoTime()
                    first.complete(true)
                    while (isActive && !current.disconnected.isCompleted) {
                        val refresh = select<Boolean> {
                            current.disconnected.onAwait { false }
                            current.toolsChanged.onReceiveCatching { it.isSuccess }
                        }
                        if (!refresh) break
                        syncTools()
                    }
                    failureCode = "disconnected"
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    failureCode = (error as? McpOperationException)?.code ?: "discovery"
                    first.complete(false)
                } finally {
                    // Fence handlers BEFORE removing registrations or awaiting any close operation.
                    active.set(null)
                    instructions.set("")
                    runCatching { batch.replace(emptyList()) }
                    runCatching { lease.publish(McpServerSnapshot(config.serverName, McpConnectionPhase.Reconnecting, generation, authStatus = initialAuthStatus)) }
                    withContext(NonCancellable) { session?.close() }
                }
                if (!isActive) break
                if (connectedAt != null && (System.nanoTime() - connectedAt) / 1_000_000 >= config.reconnect.stableAfterMs) attempts = 0
                if (!config.reconnect.enabled || attempts >= config.reconnect.maxAttempts) {
                    lease.publish(McpServerSnapshot(config.serverName, McpConnectionPhase.Failed, generation,
                        failureCode = failureCode, authStatus = initialAuthStatus))
                    break
                }
                lease.publish(McpServerSnapshot(config.serverName, McpConnectionPhase.Reconnecting, generation,
                    failureCode = failureCode, authStatus = initialAuthStatus))
                val backoff = reconnectDelay(config.reconnect, attempts++)
                delay(backoff)
            }
        }
        // EffectScope disposes in reverse: stop/join worker before batch, section and lease teardown.
        scope.add {
            active.set(null)
            instructions.set("")
            worker.cancelAndJoin()
        }
        val success = first.await()
        if (!success && config.failOnStartupError) throw McpOperationException("startup")
    }
}

private fun unavailable() = ToolExecutionResult(listOf(TextBlock("MCP server is unavailable")), true)
internal fun reconnectDelay(policy: McpReconnectConfig, attempt: Int): Long {
    var value = policy.initialDelayMs
    repeat(attempt.coerceIn(0, 63)) { value = if (value >= policy.maxDelayMs / 2) policy.maxDelayMs else value * 2 }
    return value.coerceAtMost(policy.maxDelayMs)
}

val McpClientPluginDefinition = pluginDefinition(
    MCP_CLIENT_PLUGIN_NAME, McpClientPlugin(), jsonObjectConfig(decode = ::decodeMcpServerConfig),
)
