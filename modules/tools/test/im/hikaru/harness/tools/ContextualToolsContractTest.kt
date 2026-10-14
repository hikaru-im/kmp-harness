package im.hikaru.harness.tools

import im.hikaru.harness.llm.CallId
import im.hikaru.harness.llm.TextBlock
import im.hikaru.harness.llm.ToolSchema
import im.hikaru.harness.runtime.Runtime
import im.hikaru.harness.runtime.effect.Disposable
import im.hikaru.harness.session.SessionPlugin
import im.hikaru.harness.session.createSession
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlin.test.*

class ContextualToolsContractTest {
    @Test
    fun contextualDispatchPreservesExactAuthorityAndLegacyHandlers() = runTest {
        val runtime = Runtime()
        try {
            runtime.install(SessionPlugin())
            runtime.install(ToolsPlugin())
            val execution = ToolExecutionContext(runtime.context, runtime.context.createSession())
            val tools = runtime.context.require(ToolsKey)
            var received: ToolExecutionContext? = null
            tools.register(schema("trusted"), ContextualToolHandler { _, actual ->
                received = actual
                ToolExecutionResult(listOf(TextBlock("contextual")))
            })
            // A matching scope alone cannot enter the contextual overload. Rejection
            // releases inFlight so the real caller can still execute this call.
            val request = request("one", "trusted", execution.session.id.value)
            assertFailsWith<IllegalStateException> { tools.execute(request) }
            assertNull(received)
            assertEquals("contextual", text(tools.execute(request, execution)))
            assertSame(execution, received)
            assertSame(execution.context, received?.context)
            assertSame(execution.session, received?.session)
            assertFailsWith<IllegalStateException> { tools.execute(request, execution) }

            var legacyCalls = 0
            tools.register(schema("legacy"), ToolHandler {
                legacyCalls++
                ToolExecutionResult(listOf(TextBlock("legacy")))
            })
            assertEquals("legacy", text(tools.execute(request("two", "legacy"))))
            assertEquals("legacy", text(tools.execute(request("three", "legacy"), execution)))
            assertEquals(2, legacyCalls)
        } finally { runtime.context.dispose() }
    }

    @Test
    fun oldServiceImplementationSupportsNewOverloadByDefault() = runTest {
        val runtime = Runtime()
        try {
            runtime.install(SessionPlugin())
            val execution = ToolExecutionContext(runtime.context, runtime.context.createSession())
            var received: ToolCallRequest? = null
            val legacy = object : ToolsService {
                override suspend fun schemas(): List<ToolSchema> = emptyList()
                override suspend fun execute(request: ToolCallRequest): ToolExecutionResult {
                    received = request
                    return ToolExecutionResult(listOf(TextBlock("compatible")))
                }
                override fun register(schema: ToolSchema, handler: ToolHandler): Disposable =
                    error("This read fixture does not register tools")
            }
            val request = request("legacy-api", "legacy")
            assertEquals("compatible", text(legacy.execute(request, execution)))
            assertSame(request, received)
        } finally { runtime.context.dispose() }
    }

    private fun schema(name: String) = ToolSchema(name, name, JsonObject(emptyMap()))
    private fun request(call: String, name: String, scope: String? = null) =
        ToolCallRequest(CallId(call), name, "{}", scope)
    private fun text(result: ToolExecutionResult) = (result.content.single() as TextBlock).text
}
