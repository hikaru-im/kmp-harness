package im.hikaru.harness.agent.loop

import im.hikaru.harness.agent.AgentEvents
import im.hikaru.harness.agent.AgentPlugin
import im.hikaru.harness.agent.agents
import im.hikaru.harness.agent.AgentPreStepEvent
import im.hikaru.harness.agent.AgentTurnStoppingEvent
import im.hikaru.harness.agent.AgentRequestErrorDecision
import im.hikaru.harness.agent.AgentOptions
import im.hikaru.harness.agent.AgentStatus
import im.hikaru.harness.llm.BlockStartChunk
import im.hikaru.harness.llm.FinishChunk
import im.hikaru.harness.llm.LlmAdapter
import im.hikaru.harness.llm.LlmException
import im.hikaru.harness.llm.AbortedFinishReason
import im.hikaru.harness.llm.LlmFailure
import im.hikaru.harness.llm.LlmKey
import im.hikaru.harness.llm.LlmPlugin
import im.hikaru.harness.llm.llm
import im.hikaru.harness.llm.StreamChunk
import im.hikaru.harness.llm.TextDeltaChunk
import im.hikaru.harness.llm.StopFinishReason
import im.hikaru.harness.llm.createUserMessage
import im.hikaru.harness.llm.TextBlock
import im.hikaru.harness.llm.ToolCallBlock
import im.hikaru.harness.llm.ToolSchema
import im.hikaru.harness.llm.CallId
import im.hikaru.harness.runtime.Runtime
import im.hikaru.harness.runtime.plugin.plugin
import im.hikaru.harness.session.SessionEventNames
import im.hikaru.harness.session.SessionPlugin
import im.hikaru.harness.tools.ToolHandler
import im.hikaru.harness.tools.ToolExecutionResult
import im.hikaru.harness.tools.ToolsPlugin
import im.hikaru.harness.tools.tools
import kotlinx.serialization.json.JsonObject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.yield
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AgentLoopContractTest {
    @Test
    fun scriptedFollowupProducesOrderedTurnAndAssistantHistory() = runTest {
        val runtime = Runtime()
        runtime.install(SessionPlugin())
        runtime.install(LlmPlugin())
        runtime.install(AgentPlugin())
        runtime.context.llm.registerAdapter(
            adapter = object : LlmAdapter {
                override fun stream(options: im.hikaru.harness.llm.GenerateOptions): Flow<StreamChunk> =
                    flowOf(
                        BlockStartChunk(0, "text"),
                        TextDeltaChunk(0, "hello"),
                        FinishChunk(StopFinishReason),
                    )
            },
            providers = listOf("scripted"),
        )
        runtime.install(AgentLoopPlugin("scripted", "test"))
        val runtimeEvents = mutableListOf<String>()
        runtime.context.on(AgentEvents.PreStep) { event, next ->
            runtimeEvents += "agent/pre-step"
            next()
        }
        runtime.context.on(AgentEvents.TurnStopping) {
            runtimeEvents += "agent/turn-stopping"
        }
        val handle = runtime.context.agents.create()
        handle.agent.followup(createUserMessage(listOf(TextBlock("hi"))))
        handle.agent.awaitIdle()
        val names = handle.agent.session.events().map { it.type }.filterNot { it.startsWith("agent/inbox/") }
        assertEquals(
            listOf(
                SessionEventNames.TURN_START,
                SessionEventNames.STEP_START,
                SessionEventNames.USER_MESSAGE,
                SessionEventNames.REQUEST_HEADER,
                SessionEventNames.ASSISTANT_CHUNK,
                SessionEventNames.ASSISTANT_CHUNK,
                SessionEventNames.ASSISTANT_CHUNK,
                SessionEventNames.ASSISTANT_MESSAGE,
                SessionEventNames.STEP_END,
                SessionEventNames.TURN_END,
            ),
            names,
        )
        assertEquals(listOf("agent/pre-step", "agent/turn-stopping"), runtimeEvents)
        assertEquals(2, handle.agent.session.deriveMessages().size)
        handle.dispose()
        runtime.context.dispose()
    }

    @Test
    fun requestErrorRetriesOnlyWhenListenerReturnsRetry() = runTest {
        val runtime = Runtime()
        runtime.install(SessionPlugin())
        runtime.install(LlmPlugin())
        runtime.install(AgentPlugin())
        var calls = 0
        runtime.context.llm.registerAdapter(
            adapter = object : LlmAdapter {
                override fun stream(options: im.hikaru.harness.llm.GenerateOptions): Flow<StreamChunk> {
                    calls += 1
                    return if (calls == 1) {
                        flowOf(FinishChunk(im.hikaru.harness.llm.ErrorFinishReason(LlmException("server", "SERVER").failure)))
                    } else {
                        flowOf(BlockStartChunk(0, "text"), TextDeltaChunk(0, "ok"), FinishChunk(StopFinishReason))
                    }
                }
            },
            providers = listOf("scripted"),
        )
        runtime.install(AgentLoopPlugin("scripted", "test"))
        runtime.context.on(AgentEvents.RequestError) { _, _ -> AgentRequestErrorDecision.Retry }
        val handle = runtime.context.agents.create()
        handle.agent.followup(createUserMessage(listOf(TextBlock("hi"))))
        handle.agent.awaitIdle()
        assertEquals(2, calls)
        assertEquals(2, handle.agent.session.deriveMessages().size)
        handle.dispose()
        runtime.context.dispose()
    }

    @Test
    fun registeredToolProducesASecondStepAndPairedResult() = runTest {
        val runtime = Runtime()
        runtime.install(SessionPlugin())
        runtime.install(LlmPlugin())
        runtime.install(AgentPlugin())
        runtime.install(ToolsPlugin())
        var calls = 0
        runtime.context.llm.registerAdapter(
            providers = listOf("scripted"),
            adapter = object : LlmAdapter {
                override fun stream(options: im.hikaru.harness.llm.GenerateOptions): Flow<StreamChunk> {
                    calls += 1
                    return if (calls == 1) {
                        flowOf(
                            BlockStartChunk(0, "tool-call"),
                            im.hikaru.harness.llm.BlockEndChunk(0, ToolCallBlock(CallId("call-1"), "echo", "{}")),
                            FinishChunk(im.hikaru.harness.llm.ToolCallsFinishReason),
                        )
                    } else {
                        flowOf(BlockStartChunk(0, "text"), TextDeltaChunk(0, "done"), FinishChunk(StopFinishReason))
                    }
                }
            },
        )
        runtime.install(AgentLoopPlugin("scripted", "test"))
        runtime.context.tools!!.register(
            ToolSchema("echo", "Echo", JsonObject(emptyMap())),
            ToolHandler { ToolExecutionResult(listOf(TextBlock("result"))) },
        )
        val handle = runtime.context.agents.create()
        handle.agent.followup(createUserMessage(listOf(TextBlock("use echo"))))
        handle.agent.awaitIdle()
        assertEquals(2, calls)
        assertEquals(4, handle.agent.session.deriveMessages().size)
        val types = handle.agent.session.events().map { it.type }
        assertEquals(1, types.count { it == "tool/call" })
        assertEquals(1, types.count { it == "tool/result" })
        handle.dispose()
        runtime.context.dispose()
    }

    @Test
    fun followupsAreProcessedAsOrderedTurns() = runTest {
        val runtime = Runtime()
        runtime.install(SessionPlugin())
        runtime.install(LlmPlugin())
        runtime.install(AgentPlugin())
        var calls = 0
        runtime.context.llm.registerAdapter(
            providers = listOf("scripted"),
            adapter = object : LlmAdapter {
                override fun stream(options: im.hikaru.harness.llm.GenerateOptions): Flow<StreamChunk> {
                    calls += 1
                    return flowOf(BlockStartChunk(0, "text"), TextDeltaChunk(0, "$calls"), FinishChunk(StopFinishReason))
                }
            },
        )
        runtime.install(AgentLoopPlugin("scripted", "test"))
        val handle = runtime.context.agents.create()
        handle.agent.followup(createUserMessage(listOf(TextBlock("one"))))
        handle.agent.followup(createUserMessage(listOf(TextBlock("two"))))
        handle.agent.awaitIdle()
        assertEquals(2, calls)
        val turns = handle.agent.session.events().filter { it.type == SessionEventNames.TURN_START }
        assertEquals(2, turns.size)
        assertEquals(4, handle.agent.session.deriveMessages().size)
        handle.dispose()
        runtime.context.dispose()
    }

    @Test
    fun followupArrivingAtDriverIdleIsNotStranded() = runTest {
        val runtime = Runtime()
        runtime.install(SessionPlugin())
        runtime.install(LlmPlugin())
        runtime.install(AgentPlugin())
        var calls = 0
        runtime.context.llm.registerAdapter(
            providers = listOf("scripted"),
            adapter = object : LlmAdapter {
                override fun stream(options: im.hikaru.harness.llm.GenerateOptions): Flow<StreamChunk> =
                    flowOf(
                        BlockStartChunk(0, "text"),
                        TextDeltaChunk(0, (++calls).toString()),
                        FinishChunk(StopFinishReason),
                    )
            },
        )
        runtime.install(AgentLoopPlugin("scripted", "test"))

        // `agent/status(IDLE)` is emitted at the beginning of driver
        // finalization. Enqueue the next turn from that boundary repeatedly;
        // the driver must either claim it or schedule a replacement, never
        // leave a durable NEXT_TURN without a job.
        var idleSignal: CompletableDeferred<Unit>? = null
        runtime.context.on(AgentEvents.Status) { notice ->
            if (notice.status == AgentStatus.IDLE) {
                idleSignal?.complete(Unit)
            }
        }
        val handle = runtime.context.agents.create()
        repeat(32) { index ->
            val signal = CompletableDeferred<Unit>()
            idleSignal = signal
            handle.agent.followup(createUserMessage(listOf(TextBlock("turn-$index"))))
            signal.await()
        }
        handle.agent.awaitIdle()

        assertEquals(32, calls)
        assertEquals(64, handle.agent.session.deriveMessages().size)
        assertTrue(handle.agent.inbox.snapshot().nextTurn.isEmpty())
        handle.dispose()
        runtime.context.dispose()
    }

    @Test
    fun cancellationAfterVisiblePrefixWritesInterruptedAssistant() = runTest {
        val runtime = Runtime()
        runtime.install(SessionPlugin())
        runtime.install(LlmPlugin())
        runtime.install(AgentPlugin())
        val visible = CompletableDeferred<Unit>()
        runtime.context.llm.registerAdapter(
            providers = listOf("scripted"),
            adapter = object : LlmAdapter {
                override fun stream(options: im.hikaru.harness.llm.GenerateOptions): Flow<StreamChunk> = flow {
                    emit(BlockStartChunk(0, "text"))
                    emit(TextDeltaChunk(0, "prefix"))
                    visible.complete(Unit)
                    awaitCancellation()
                }
            },
        )
        runtime.install(AgentLoopPlugin("scripted", "test"))
        val handle = runtime.context.agents.create()
        handle.agent.followup(createUserMessage(listOf(TextBlock("hi"))))
        visible.await()
        handle.agent.cancel()
        handle.agent.awaitIdle()
        val events = handle.agent.session.events()
        assertEquals(1, events.count { it.type == SessionEventNames.ASSISTANT_MESSAGE })
        assertEquals(1, events.count { it.type == SessionEventNames.TURN_END })
        handle.dispose()
        runtime.context.dispose()
    }

    @Test
    fun cancelKeepingInboxDoesNotRestartAQueuedTurn() = runTest {
        val runtime = Runtime()
        runtime.install(SessionPlugin())
        runtime.install(LlmPlugin())
        runtime.install(AgentPlugin())
        val started = CompletableDeferred<Unit>()
        var calls = 0
        runtime.context.llm.registerAdapter(
            providers = listOf("scripted"),
            adapter = object : LlmAdapter {
                override fun stream(options: im.hikaru.harness.llm.GenerateOptions): Flow<StreamChunk> = flow {
                    calls += 1
                    emit(BlockStartChunk(0, "text"))
                    emit(TextDeltaChunk(0, "prefix"))
                    started.complete(Unit)
                    awaitCancellation()
                }
            },
        )
        runtime.install(AgentLoopPlugin("scripted", "test"))
        val handle = runtime.context.agents.create()
        handle.agent.followup(createUserMessage(listOf(TextBlock("first"))))
        started.await()
        // Let the provider flow reach its suspension point before enqueueing
        // the second turn; otherwise the test can race the first launch.
        yield()
        val queuedId = handle.agent.followup(createUserMessage(listOf(TextBlock("second"))))
        assertEquals(1, calls, "second followup must remain queued while the first stream is suspended")

        handle.agent.cancel(keepInbox = true)
        handle.agent.awaitIdle()

        assertEquals(1, calls)
        assertTrue(handle.agent.inbox.snapshot().nextTurn.any { it.id == queuedId })
        handle.dispose()
        runtime.context.dispose()
    }

    @Test
    fun unregisteredToolDoesNotCreateFakeAssistantToolHistory() = runTest {
        val runtime = Runtime()
        runtime.install(SessionPlugin())
        runtime.install(LlmPlugin())
        runtime.install(AgentPlugin())
        runtime.install(ToolsPlugin())
        runtime.context.llm.registerAdapter(
            providers = listOf("scripted"),
            adapter = object : LlmAdapter {
                override fun stream(options: im.hikaru.harness.llm.GenerateOptions): Flow<StreamChunk> =
                    flowOf(
                        BlockStartChunk(0, "tool-call"),
                        im.hikaru.harness.llm.BlockEndChunk(0, ToolCallBlock(CallId("unknown"), "missing", "{}")),
                        FinishChunk(im.hikaru.harness.llm.ToolCallsFinishReason),
                    )
            },
        )
        runtime.install(AgentLoopPlugin("scripted", "test"))
        val handle = runtime.context.agents.create()
        handle.agent.followup(createUserMessage(listOf(TextBlock("use tool"))))
        handle.agent.awaitIdle()
        assertEquals(1, handle.agent.session.deriveMessages().size)
        assertEquals(0, handle.agent.session.events().count { it.type == "tool/call" })
        handle.dispose()
        runtime.context.dispose()
    }

    @Test
    fun providerAbortedFinishRunsRequestErrorWaterfall() = runTest {
        val runtime = Runtime()
        runtime.install(SessionPlugin())
        runtime.install(LlmPlugin())
        runtime.install(AgentPlugin())
        var errors = 0
        runtime.context.llm.registerAdapter(
            providers = listOf("scripted"),
            adapter = object : LlmAdapter {
                override fun stream(options: im.hikaru.harness.llm.GenerateOptions): Flow<StreamChunk> =
                    flowOf(FinishChunk(AbortedFinishReason(LlmFailure("aborted", "ABORTED"))))
            },
        )
        runtime.install(AgentLoopPlugin("scripted", "test"))
        runtime.context.on(AgentEvents.RequestError) { _, next ->
            errors += 1
            next()
        }
        val handle = runtime.context.agents.create()
        handle.agent.followup(createUserMessage(listOf(TextBlock("hi"))))
        handle.agent.awaitIdle()
        assertEquals(1, errors)
        handle.dispose()
        runtime.context.dispose()
    }
}
