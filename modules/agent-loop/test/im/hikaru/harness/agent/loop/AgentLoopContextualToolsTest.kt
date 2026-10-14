package im.hikaru.harness.agent.loop

import im.hikaru.harness.agent.AgentOptions
import im.hikaru.harness.agent.AgentPlugin
import im.hikaru.harness.agent.agents
import im.hikaru.harness.llm.*
import im.hikaru.harness.runtime.Runtime
import im.hikaru.harness.session.SessionPlugin
import im.hikaru.harness.tools.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlin.test.*

class AgentLoopContextualToolsTest {
    @Test
    fun realAgentLoopSuppliesItsExactContextSessionAndScope() = runTest {
        val runtime = Runtime()
        try {
            runtime.install(SessionPlugin())
            runtime.install(LlmPlugin())
            runtime.install(AgentPlugin())
            runtime.install(ToolsPlugin())
            var requests = 0
            runtime.context.llm.registerAdapter(
                providers = listOf("scripted"),
                adapter = object : LlmAdapter {
                    override fun stream(options: GenerateOptions): Flow<StreamChunk> =
                        if (++requests == 1) {
                            flowOf(
                                BlockStartChunk(0, "tool-call"),
                                BlockEndChunk(0, ToolCallBlock(CallId("trusted-call"), "trusted", "{}")),
                                FinishChunk(ToolCallsFinishReason),
                            )
                        } else {
                            flowOf(BlockStartChunk(0, "text"), TextDeltaChunk(0, "done"), FinishChunk(StopFinishReason))
                        }
                },
            )
            runtime.install(AgentLoopPlugin())
            var execution: ToolExecutionContext? = null
            var request: ToolCallRequest? = null
            runtime.context.tools!!.register(
                ToolSchema("trusted", "Trusted contextual command", JsonObject(emptyMap())),
                ContextualToolHandler { actualRequest, actualExecution ->
                    request = actualRequest
                    execution = actualExecution
                    ToolExecutionResult(listOf(TextBlock("actual contextual result")))
                },
            )
            val handle = runtime.context.agents.create(options = AgentOptions(provider = "scripted", model = "test"))
            handle.agent.followup(createUserMessage(listOf(TextBlock("run trusted"))))
            handle.agent.awaitIdle()
            val received = assertNotNull(execution)
            assertSame(handle.agent.context, received.context)
            assertSame(handle.agent.session, received.session)
            assertEquals(handle.agent.session.id.value, assertNotNull(request).scope)
            assertEquals(CallId("trusted-call"), request?.callId)
            assertEquals(2, requests)
            val events = handle.agent.session.events().map { it.type }
            assertEquals(1, events.count { it == "tool/call" })
            assertEquals(1, events.count { it == "tool/result" })
            assertEquals(4, handle.agent.session.deriveMessages().size)
        } finally { runtime.context.dispose() }
    }
}
