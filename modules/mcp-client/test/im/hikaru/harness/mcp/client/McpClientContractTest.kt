package im.hikaru.harness.mcp.client

import im.hikaru.contracts.harness.mcp.McpAuthStatus
import im.hikaru.harness.llm.CallId
import im.hikaru.harness.llm.TextBlock
import im.hikaru.harness.runtime.Runtime
import im.hikaru.harness.session.SessionPlugin
import im.hikaru.harness.session.createSession
import im.hikaru.harness.system.prompt.SystemPromptKey
import im.hikaru.harness.system.prompt.SystemPromptPlugin
import im.hikaru.harness.tools.*
import io.modelcontextprotocol.kotlin.sdk.types.*
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

class McpClientContractTest {
    @Test
    fun stdioDiscoveryDoesNotClaimOAuthAuthorization() = runTest(timeout = 10.seconds) {
        val runtime = Runtime()
        try {
            runtime.install(SessionPlugin())
            runtime.install(ToolsPlugin())
            runtime.install(McpRegistryPlugin())
            runtime.install(McpClientPlugin(McpConnector { FakeMcpSession() }), config())
            val registry = runtime.context.require(McpRegistryKey)
            runCurrent()
            assertEquals(McpAuthStatus.Unsupported, registry.states.value.getValue("fixture").authStatus)
        } finally {
            runtime.context.dispose()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun initialDiscoveryNamesLiteralPromptAndGenerationReplacement() = runTest(timeout = 10.seconds) {
        val runtime = Runtime()
        val session = FakeMcpSession()
        try {
            runtime.install(SessionPlugin())
            runtime.install(SystemPromptPlugin())
            runtime.install(ToolsPlugin())
            runtime.install(McpRegistryPlugin())
            val fiber = runtime.install(McpClientPlugin(McpConnector { session }), config())
            val tools = runtime.context.require(ToolsKey)
            assertEquals(listOf("mcp__fixture__echo"), tools.schemas().map { it.name })
            val assembled = runtime.context.require(SystemPromptKey).assemble()
            assertTrue(assembled.sections.any { it.text.contains("${'$'}{unbound}") && !it.interpolate })
            val execution = ToolExecutionContext(runtime.context, runtime.context.createSession())
            val result = tools.execute(ToolCallRequest(CallId("1"), "mcp__fixture__echo", "{\"text\":\"hello\"}", execution.session.id.value), execution)
            assertEquals("echo", session.rawCalled)
            assertEquals("hello", (session.arguments?.get("text") as JsonPrimitive).content)
            assertEquals("ok", assertIs<TextBlock>(result.content.single()).text)
            assertEquals(McpConnectionPhase.Connected, runtime.context.require(McpRegistryKey).states.value["fixture"]?.phase)
            session.tools = listOf(tool("new"), tool("other"))
            session.toolsChanged.send(Unit)
            withTimeout(3_000) {
                while (tools.schemas().map { it.name }.toSet() != setOf("mcp__fixture__new", "mcp__fixture__other")) yield()
            }
            runtime.uninstall(fiber)
            assertTrue(tools.schemas().isEmpty())
            assertTrue(runtime.context.require(McpRegistryKey).states.value.isEmpty())
            assertEquals(1, session.closes)
            assertFalse(runtime.context.require(SystemPromptKey).assemble().sections.any { it.name.startsWith("mcp-server:") })
        } finally { runtime.context.dispose() }
    }

    @Test
    fun duplicateServerDoesNotReplaceTheOriginalLease() = runTest(timeout = 10.seconds) {
        val runtime = Runtime()
        val session = FakeMcpSession()
        try {
            runtime.install(ToolsPlugin()); runtime.install(McpRegistryPlugin())
            runtime.install(McpClientPlugin(McpConnector { session }), config())
            assertFailsWith<IllegalStateException> {
                runtime.install(McpClientPlugin(McpConnector { error("must not connect") }), config())
            }
            assertEquals(McpConnectionPhase.Connected, runtime.context.require(McpRegistryKey).states.value.getValue("fixture").phase)
            assertEquals(listOf("mcp__fixture__echo"), runtime.context.require(ToolsKey).schemas().map { it.name })
        } finally { runtime.context.dispose() }
    }

    @Test
    fun defaultStartupFailureRemainsInspectableAndStrictFailureRollsBack() = runTest(timeout = 10.seconds) {
        val runtime = Runtime()
        try {
            runtime.install(ToolsPlugin()); runtime.install(McpRegistryPlugin())
            val connector = McpConnector { throw IllegalArgumentException("SECRET must not appear") }
            val fiber = runtime.install(McpClientPlugin(connector), config().copy(reconnect = McpReconnectConfig(enabled = false)))
            withTimeout(3_000) {
                while (runtime.context.require(McpRegistryKey).states.value.getValue("fixture").phase != McpConnectionPhase.Failed) yield()
            }
            val failure = runtime.context.require(McpRegistryKey).states.value.getValue("fixture")
            assertFalse(failure.toString().contains("SECRET"))
            assertTrue(runtime.context.require(ToolsKey).schemas().isEmpty())
            runtime.uninstall(fiber)
            assertFailsWith<IllegalStateException> {
                runtime.install(McpClientPlugin(connector), config().copy(failOnStartupError = true))
            }
            assertTrue(runtime.context.require(McpRegistryKey).states.value.isEmpty())
        } finally { runtime.context.dispose() }
    }

    @Test
    fun disconnectWithdrawsToolsThenReconnectsWithoutReplayingCalls() = runTest(timeout = 10.seconds) {
        val runtime = Runtime()
        val first = FakeMcpSession()
        val second = FakeMcpSession()
        val count = AtomicInteger()
        try {
            runtime.install(ToolsPlugin()); runtime.install(McpRegistryPlugin())
            runtime.install(McpClientPlugin(McpConnector { if (count.incrementAndGet() == 1) first else second }),
                config().copy(reconnect = McpReconnectConfig(initialDelayMs = 10, maxDelayMs = 20)))
            first.disconnected.complete(Unit)
            withTimeout(3_000) {
                while (runtime.context.require(McpRegistryKey).states.value.getValue("fixture").generation < 2) yield()
            }
            assertEquals(2, count.get())
            assertNull(first.rawCalled); assertNull(second.rawCalled)
            assertEquals(1, first.closes)
        } finally { runtime.context.dispose() }
    }

    @Test
    fun wholeCallDeadlineBoundsAConnectorThatIgnoresRequestOptions() = runTest(timeout = 10.seconds) {
        assertFailsWith<McpOperationException> { mcpDeadline(50) { awaitCancellation() } }
        val policy = McpReconnectConfig()
        assertEquals(listOf(500L, 1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 30_000L), (0..6).map { reconnectDelay(policy, it) })
        assertEquals(30_000, reconnectDelay(policy, Int.MAX_VALUE))
    }

    @Test
    fun repeatedCursorIsRejectedInsteadOfLooping() = runTest {
        var calls = 0
        assertFailsWith<IllegalArgumentException> { pages<Int> { calls++; listOf(1) to "same" } }
        assertEquals(2, calls)
    }

    private fun config() = McpServerConfig("fixture", McpTransportConfig.Stdio("never-executed"))
}

internal fun tool(name: String) = Tool(name, io.modelcontextprotocol.kotlin.sdk.types.ToolSchema(properties = JsonObject(emptyMap())))

internal class FakeMcpSession : McpSession {
    override val instructions = "literal ${'$'}{unbound}"
    override val supportsResources = true
    override val disconnected = CompletableDeferred<Unit>()
    override val toolsChanged = Channel<Unit>(Channel.CONFLATED)
    var tools = listOf(tool("echo"))
    var rawCalled: String? = null
    var arguments: JsonObject? = null
    var closes = 0
    override suspend fun listTools() = tools
    override suspend fun callTool(name: String, arguments: JsonObject): CallToolResult {
        rawCalled = name; this.arguments = arguments
        return CallToolResult(listOf(TextContent("ok")))
    }
    override suspend fun listResources() = listOf(Resource("memory://test", "test"))
    override suspend fun listResourceTemplates() = emptyList<ResourceTemplate>()
    override suspend fun readResource(uri: String) = ReadResourceResult(listOf(TextResourceContents("resource", uri)))
    override suspend fun close() { closes++; disconnected.complete(Unit); toolsChanged.close() }
}
