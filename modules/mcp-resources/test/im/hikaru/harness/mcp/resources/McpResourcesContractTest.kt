package im.hikaru.harness.mcp.resources

import im.hikaru.harness.llm.TextBlock
import im.hikaru.harness.mcp.client.*
import im.hikaru.harness.runtime.Runtime
import im.hikaru.harness.session.SessionPlugin
import im.hikaru.harness.session.createSession
import im.hikaru.harness.tools.*
import io.modelcontextprotocol.kotlin.sdk.types.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlinx.serialization.json.JsonObject
import kotlin.test.*

class McpResourcesContractTest {
    @Test
    fun resourcesToolsAreVisibleOnlyWithProviderAndBindReadsToServer() = runTest {
        val runtime = Runtime()
        try {
            runtime.install(SessionPlugin())
            runtime.install(ToolsPlugin())
            runtime.install(McpRegistryPlugin())
            runtime.install(McpResourcesPlugin())
            assertTrue(runtime.context.require(ToolsKey).schemas().isEmpty())
            val session = ResourceSession("one")
            val lease = runtime.context.require(McpRegistryKey).reserve("one")
            lease.publish(McpServerSnapshot("one", McpConnectionPhase.Connected, toolCount = 0, resourcesAvailable = true), session)
            val tools = runtime.context.require(ToolsKey)
            withTimeout(3_000) {
                while (tools.schemas().isEmpty()) yield()
            }
            assertEquals(
                setOf("list_mcp_resources", "list_mcp_resource_templates", "read_mcp_resource"),
                tools.schemas().map { it.name }.toSet(),
            )
            val execution = ToolExecutionContext(runtime.context, runtime.context.createSession())
            val result = tools.execute(ToolCallRequest(im.hikaru.harness.llm.CallId("read"), "read_mcp_resource", "{\"server\":\"one\",\"uri\":\"memory://one\"}"), execution)
            assertFalse(result.isError)
            assertEquals("one:memory://one", assertIs<TextBlock>(result.content.single()).text)
            assertEquals("memory://one", session.lastUri)
            val unavailable = tools.execute(ToolCallRequest(im.hikaru.harness.llm.CallId("bad"), "read_mcp_resource", "{\"server\":\"other\",\"uri\":\"memory://one\"}"), execution)
            assertTrue(unavailable.isError)
            lease.dispose()
            withTimeout(3_000) {
                while (tools.schemas().isNotEmpty()) yield()
            }
            assertTrue(tools.schemas().isEmpty())
        } finally { runtime.context.dispose() }
    }
}

private class ResourceSession(private val id: String) : McpSession {
    override val instructions: String? = null
    override val supportsResources = true
    override val disconnected = CompletableDeferred<Unit>()
    override val toolsChanged = Channel<Unit>(Channel.CONFLATED)
    var lastUri: String? = null
    override suspend fun listTools() = emptyList<Tool>()
    override suspend fun callTool(name: String, arguments: JsonObject) = error("not used")
    override suspend fun listResources() = listOf(Resource("memory://$id", id))
    override suspend fun listResourceTemplates() = emptyList<ResourceTemplate>()
    override suspend fun readResource(uri: String): ReadResourceResult {
        lastUri = uri
        return ReadResourceResult(listOf(TextResourceContents("$id:$uri", uri)))
    }
    override suspend fun close() { disconnected.complete(Unit); toolsChanged.close() }
}
