package im.hikaru.harness.tools

import im.hikaru.harness.llm.CallId
import im.hikaru.harness.llm.TextBlock
import im.hikaru.harness.llm.ToolSchema
import im.hikaru.harness.runtime.Runtime
import im.hikaru.harness.runtime.effect.Disposable
import im.hikaru.harness.system.prompt.SystemPromptConfig
import im.hikaru.harness.system.prompt.SystemPromptPlugin
import im.hikaru.harness.system.prompt.systemPrompt
import im.hikaru.harness.tools.tools
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ToolsContractTest {
    @Test
    fun duplicateCallIdIsRejectedAfterFirstResult() = runTest {
        val runtime = Runtime()
        runtime.install(ToolsPlugin())
        val tools = checkNotNull(runtime.context.tools)
        tools.register(ToolSchema("echo", "Echo", JsonObject(emptyMap())), ToolHandler { ToolExecutionResult(listOf(TextBlock("ok"))) })
        val request = ToolCallRequest(CallId("one"), "echo", "{}")
        tools.execute(request)
        assertFailsWith<IllegalStateException> { tools.execute(request) }
        runtime.context.dispose()
    }

    @Test
    fun registeredHandlerReturnsProviderNeutralResult() = runTest {
        val service = object : ToolsService {
            private val delegate = TestToolsService()
            override suspend fun schemas() = delegate.schemas()
            override suspend fun execute(request: ToolCallRequest) = delegate.execute(request)
            override fun register(schema: ToolSchema, handler: ToolHandler) = delegate.register(schema, handler)
        }
        service.register(ToolSchema("echo", "Echo", JsonObject(emptyMap())), ToolHandler { ToolExecutionResult(listOf(TextBlock("ok"))) })
        assertEquals("ok", (service.execute(ToolCallRequest(CallId("one"), "echo", "{}")).content.single() as TextBlock).text)
    }

    @Test
    fun callIdUniquenessIsScopedPerSession() = runTest {
        val runtime = Runtime()
        runtime.install(ToolsPlugin())
        val tools = checkNotNull(runtime.context.tools)
        tools.register(ToolSchema("echo", "Echo", JsonObject(emptyMap())), ToolHandler { ToolExecutionResult(listOf(TextBlock("ok"))) })
        tools.execute(ToolCallRequest(CallId("same"), "echo", "{}", scope = "session-a"))
        tools.execute(ToolCallRequest(CallId("same"), "echo", "{}", scope = "session-b"))
        runtime.context.dispose()
    }

    @Test
    fun executionModeIsOptInAndDefaultsToExclusive() = runTest {
        val runtime = Runtime()
        runtime.install(ToolsPlugin())
        val tools = checkNotNull(runtime.context.tools)
        tools.register(schema("serial"), okHandler())
        tools.register(schema("parallel"), okHandler(), ToolRegistrationOptions(concurrencySafe = true))

        assertEquals(
            ToolExecutionMode.Exclusive,
            tools.executionMode(ToolCallRequest(CallId("a"), "serial", "{}")),
        )
        assertEquals(
            ToolExecutionMode.Parallel,
            tools.executionMode(ToolCallRequest(CallId("b"), "parallel", "{}")),
        )
        runtime.context.dispose()
    }

    @Test
    fun toolsPluginFeedsTheSystemPromptToolProvider() = runTest {
        val runtime = Runtime()
        runtime.install(SystemPromptPlugin(SystemPromptConfig(includeHarnessIdentity = false)))
        runtime.install(ToolsPlugin())
        val tools = checkNotNull(runtime.context.tools)
        tools.register(schema("echo"), okHandler())

        assertEquals(
            listOf("echo"),
            checkNotNull(runtime.context.systemPrompt).assemble().tools.map { it.name },
        )
        runtime.context.dispose()
    }

    private fun schema(name: String): ToolSchema = ToolSchema(name, name, JsonObject(emptyMap()))

    private fun okHandler(): ToolHandler =
        ToolHandler { ToolExecutionResult(listOf(TextBlock("ok"))) }

    private class TestToolsService : ToolsService {
        private val handlers = mutableMapOf<String, ToolHandler>()
        private val done = mutableSetOf<CallId>()
        override suspend fun schemas() = handlers.keys.map { ToolSchema(it, it, JsonObject(emptyMap())) }
        override suspend fun execute(request: ToolCallRequest): ToolExecutionResult {
            check(request.callId !in done)
            val result = checkNotNull(handlers[request.name]).execute(request)
            done += request.callId
            return result
        }
        override fun register(schema: ToolSchema, handler: ToolHandler): Disposable {
            handlers[schema.name] = handler
            return Disposable { handlers.remove(schema.name) }
        }
    }
}
