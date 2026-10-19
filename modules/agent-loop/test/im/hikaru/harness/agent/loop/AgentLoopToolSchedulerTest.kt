package im.hikaru.harness.agent.loop

import im.hikaru.harness.agent.Agent
import im.hikaru.harness.agent.AgentOptions
import im.hikaru.harness.agent.AgentPlugin
import im.hikaru.harness.agent.agents
import im.hikaru.harness.llm.BlockEndChunk
import im.hikaru.harness.llm.BlockStartChunk
import im.hikaru.harness.llm.CallId
import im.hikaru.harness.llm.FinishChunk
import im.hikaru.harness.llm.GenerateOptions
import im.hikaru.harness.llm.LlmAdapter
import im.hikaru.harness.llm.LlmPlugin
import im.hikaru.harness.llm.StopFinishReason
import im.hikaru.harness.llm.StreamChunk
import im.hikaru.harness.llm.TextBlock
import im.hikaru.harness.llm.TextDeltaChunk
import im.hikaru.harness.llm.ToolCallBlock
import im.hikaru.harness.llm.ToolCallsFinishReason
import im.hikaru.harness.llm.ToolSchema
import im.hikaru.harness.llm.createUserMessage
import im.hikaru.harness.llm.llm
import im.hikaru.harness.runtime.Runtime
import im.hikaru.harness.session.AbortedTurnEndReason
import im.hikaru.harness.session.SessionEventNames
import im.hikaru.harness.session.SessionPlugin
import im.hikaru.harness.session.TurnEndEvent
import im.hikaru.harness.session.UserTurnAbortCause
import im.hikaru.harness.tools.ToolCallEvent
import im.hikaru.harness.tools.ToolExecutionResult
import im.hikaru.harness.tools.ToolHandler
import im.hikaru.harness.tools.ToolRegistrationOptions
import im.hikaru.harness.tools.ToolResultEvent
import im.hikaru.harness.tools.ToolsPlugin
import im.hikaru.harness.tools.tools
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AgentLoopToolSchedulerTest {
    @Test
    fun parallelSafeCallsOverlapAndCommitInModelOrder() = runTest {
        val slowStarted = CompletableDeferred<Unit>()
        val fastStarted = CompletableDeferred<Unit>()
        withFixture(maxParallelToolCalls = 4, calls = listOf("slow" to "a", "fast" to "b")) { runtime, fixture ->
            runtime.context.tools!!.register(
                schema("slow"),
                ToolHandler {
                    slowStarted.complete(Unit)
                    // Completing only after the sibling starts proves overlap.
                    fastStarted.await()
                    ToolExecutionResult(listOf(TextBlock("slow-result")))
                },
                ToolRegistrationOptions(concurrencySafe = true),
            )
            runtime.context.tools!!.register(
                schema("fast"),
                ToolHandler {
                    fastStarted.complete(Unit)
                    ToolExecutionResult(listOf(TextBlock("fast-result")))
                },
                ToolRegistrationOptions(concurrencySafe = true),
            )
            fixture.run()
            assertTrue(slowStarted.isCompleted)
            assertTrue(fastStarted.isCompleted)
            // Results commit in model order even though fast finished first.
            assertEquals(listOf("a", "b"), fixture.resultCallIds())
        }
    }

    @Test
    fun exclusiveCallIsABarrierBetweenParallelGroups() = runTest {
        val timeline = mutableListOf<String>()
        withFixture(
            maxParallelToolCalls = 4,
            calls = listOf("parallel" to "a", "exclusive" to "b", "parallel" to "c"),
        ) { runtime, fixture ->
            runtime.context.tools!!.register(
                schema("parallel"),
                ToolHandler { request ->
                    timeline += "start-${request.callId.value}"
                    delay(10)
                    timeline += "end-${request.callId.value}"
                    ToolExecutionResult(listOf(TextBlock("ok")))
                },
                ToolRegistrationOptions(concurrencySafe = true),
            )
            runtime.context.tools!!.register(
                schema("exclusive"),
                ToolHandler { request ->
                    timeline += "start-${request.callId.value}"
                    delay(10)
                    timeline += "end-${request.callId.value}"
                    ToolExecutionResult(listOf(TextBlock("ok")))
                },
            )
            fixture.run()
            assertEquals(
                listOf("start-a", "end-a", "start-b", "end-b", "start-c", "end-c"),
                timeline,
            )
            assertEquals(listOf("a", "b", "c"), fixture.resultCallIds())
        }
    }

    @Test
    fun maxParallelToolCallsBoundsTheRollingPool() = runTest {
        var active = 0
        var peak = 0
        val guard = Mutex()
        withFixture(
            maxParallelToolCalls = 2,
            calls = listOf("work" to "a", "work" to "b", "work" to "c"),
        ) { runtime, fixture ->
            runtime.context.tools!!.register(
                schema("work"),
                ToolHandler {
                    guard.withLock {
                        active += 1
                        peak = maxOf(peak, active)
                    }
                    delay(20)
                    guard.withLock { active -= 1 }
                    ToolExecutionResult(listOf(TextBlock("ok")))
                },
                ToolRegistrationOptions(concurrencySafe = true),
            )
            fixture.run()
            assertEquals(2, peak)
            assertEquals(listOf("a", "b", "c"), fixture.resultCallIds())
        }
    }

    @Test
    fun abortRecordsSyntheticResultsForStartedAndSkippedCalls() = runTest {
        val started = CompletableDeferred<Unit>()
        withFixture(maxParallelToolCalls = 1, calls = listOf("hang" to "a", "hang" to "b")) { runtime, fixture ->
            runtime.context.tools!!.register(
                schema("hang"),
                ToolHandler {
                    started.complete(Unit)
                    awaitCancellation()
                },
                ToolRegistrationOptions(concurrencySafe = true),
            )
            fixture.agent.followup(createUserMessage(listOf(TextBlock("run"))))
            started.await()
            fixture.agent.cancel()
            fixture.agent.awaitIdle()

            assertEquals(2, fixture.callEvents().size)
            val results = fixture.resultEvents()
            assertEquals(listOf("a", "b"), results.map { it.callId.value })
            assertEquals(
                listOf("Error: tool call aborted", "Error: tool call aborted before dispatch"),
                results.map { event -> (event.result.content.single() as TextBlock).text },
            )
            assertTrue(results.all { it.result.isError })
            val end = fixture.turnEnd()
            assertIs<AbortedTurnEndReason>(end)
            assertEquals(UserTurnAbortCause, end.cause)
        }
    }

    @Test
    fun maxParallelToolCallsMustBePositive() {
        val error = runCatching { AgentLoopConfig(maxParallelToolCalls = 0) }.exceptionOrNull()
        assertIs<IllegalArgumentException>(error)
    }

    private fun schema(name: String): ToolSchema =
        ToolSchema(name, name, JsonObject(emptyMap()))

    private suspend fun withFixture(
        maxParallelToolCalls: Int,
        calls: List<Pair<String, String>>,
        block: suspend (Runtime, Fixture) -> Unit,
    ) {
        val runtime = Runtime()
        var handle: im.hikaru.harness.agent.AgentHandle? = null
        try {
            runtime.install(SessionPlugin())
            runtime.install(LlmPlugin())
            runtime.install(AgentPlugin())
            runtime.install(ToolsPlugin())
            var requests = 0
            runtime.context.llm.registerAdapter(
                providers = listOf("scripted"),
                adapter =
                    object : LlmAdapter {
                        override fun stream(options: GenerateOptions): Flow<StreamChunk> {
                            requests += 1
                            return if (requests == 1) {
                                val chunks =
                                    calls.flatMapIndexed { index, call ->
                                        listOf<StreamChunk>(
                                            BlockStartChunk(index, "tool-call"),
                                            BlockEndChunk(
                                                index,
                                                ToolCallBlock(CallId(call.second), call.first, "{}"),
                                            ),
                                        )
                                    } + FinishChunk(ToolCallsFinishReason)
                                flowOf(*chunks.toTypedArray())
                            } else {
                                flowOf(BlockStartChunk(0, "text"), TextDeltaChunk(0, "done"), FinishChunk(StopFinishReason))
                            }
                        }
                    },
            )
            runtime.install(AgentLoopPlugin(AgentLoopConfig(maxParallelToolCalls = maxParallelToolCalls)))
            handle = runtime.context.agents.create(options = AgentOptions(provider = "scripted", model = "test"))
            block(runtime, Fixture(handle.agent))
        } finally {
            handle?.dispose()
            runtime.context.dispose()
        }
    }

    private class Fixture(val agent: Agent) {
        suspend fun run() {
            agent.followup(createUserMessage(listOf(TextBlock("run"))))
            agent.awaitIdle()
        }

        suspend fun callEvents(): List<ToolCallEvent> =
            agent.session.events()
                .filter { it.type == "tool/call" }
                .map { Json.decodeFromJsonElement(ToolCallEvent.serializer(), it.data) }

        suspend fun resultEvents(): List<ToolResultEvent> =
            agent.session.events()
                .filter { it.type == "tool/result" }
                .map { Json.decodeFromJsonElement(ToolResultEvent.serializer(), it.data) }

        suspend fun resultCallIds(): List<String> = resultEvents().map { it.callId.value }

        suspend fun turnEnd() =
            Json.decodeFromJsonElement(
                TurnEndEvent.serializer(),
                agent.session.events().last { it.type == SessionEventNames.TURN_END }.data,
            ).reason
    }
}
