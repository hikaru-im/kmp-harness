package im.hikaru.harness.session.api

import im.hikaru.harness.agent.AgentPlugin
import im.hikaru.harness.agent.AgentOptions
import im.hikaru.harness.agent.agents
import im.hikaru.harness.agent.loop.AgentLoopPlugin
import im.hikaru.harness.llm.BlockStartChunk
import im.hikaru.harness.llm.FinishChunk
import im.hikaru.harness.llm.LlmAdapter
import im.hikaru.harness.llm.LlmPlugin
import im.hikaru.harness.llm.StreamChunk
import im.hikaru.harness.llm.StopFinishReason
import im.hikaru.harness.llm.TextBlock
import im.hikaru.harness.llm.TextDeltaChunk
import im.hikaru.harness.llm.createUserMessage
import im.hikaru.harness.llm.llm
import im.hikaru.harness.runtime.Runtime
import im.hikaru.harness.session.SessionOrigin
import im.hikaru.harness.session.SessionPlugin
import im.hikaru.harness.session.sessions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import im.hikaru.harness.llm.ErrorFinishReason
import im.hikaru.harness.llm.LlmFailure

class SessionApiContractTest {
    @Test
    fun failureBelongsToThePromptTurnAndDoesNotExposeProviderMessage() = runTest {
        val runtime = Runtime()
        try {
            runtime.install(SessionPlugin())
            runtime.install(LlmPlugin())
            runtime.install(AgentPlugin())
            var calls = 0
            runtime.context.llm.registerAdapter(listOf("scripted"), object : LlmAdapter {
                override fun stream(options: im.hikaru.harness.llm.GenerateOptions): Flow<StreamChunk> =
                    if (++calls == 1) flowOf(FinishChunk(ErrorFinishReason(LlmFailure(
                        "private provider response", "KOOG_UNSUPPORTED_TOOL_SCHEMA", status = 400,
                    )))) else flowOf(BlockStartChunk(0, "text"), TextDeltaChunk(0, "ok"), FinishChunk(StopFinishReason))
            })
            runtime.install(AgentLoopPlugin())
            runtime.install(SessionApiPlugin())
            val api = runtime.context.sessionApi
            val session = api.create(agentOptions = AgentOptions(provider = "scripted", model = "test"))
            val first = api.prompt(session.id, createUserMessage(listOf(TextBlock("first"))))
            first.awaitIdle()
            assertEquals(SessionPromptFailure("KOOG_UNSUPPORTED_TOOL_SCHEMA", 400), first.failure())
            val second = api.prompt(session.id, createUserMessage(listOf(TextBlock("second"))))
            second.awaitIdle()
            assertNull(second.failure())
            assertEquals(SessionPromptFailure("KOOG_UNSUPPORTED_TOOL_SCHEMA", 400), first.failure())
        } finally {
            runtime.context.dispose()
        }
    }

    @Test
    fun createPromptHistoryAndCancelUseSessionLog() = runTest {
        val runtime = Runtime()
        runtime.install(SessionPlugin())
        runtime.install(LlmPlugin())
        runtime.install(AgentPlugin())
        runtime.context.llm.registerAdapter(
            providers = listOf("scripted"),
            adapter = object : LlmAdapter {
                override fun stream(options: im.hikaru.harness.llm.GenerateOptions): Flow<StreamChunk> =
                    flowOf(BlockStartChunk(0, "text"), TextDeltaChunk(0, "ok"), FinishChunk(StopFinishReason))
            },
        )
        runtime.install(AgentLoopPlugin())
        runtime.install(SessionApiPlugin())
        val api = runtime.context.sessionApi
        val created = api.create(agentOptions = AgentOptions(cwd = "/tmp", provider = "scripted", model = "test"))
        val prompt = api.prompt(created.id, createUserMessage(listOf(TextBlock("hi"))))
        prompt.awaitIdle()
        assertEquals(2, api.history(created.id).size)
        assertEquals(created.id, api.list().single().id)
        api.cancel(created.id)
        runtime.context.dispose()
    }

    @Test
    fun forkCopiesThePrefixBeforeTheEditedMessageAndLeavesTheParentUntouched() = runTest {
        val runtime = Runtime()
        try {
            runtime.install(SessionPlugin())
            runtime.install(LlmPlugin())
            runtime.install(AgentPlugin())
            runtime.context.llm.registerAdapter(
                providers = listOf("scripted"),
                adapter = object : LlmAdapter {
                    override fun stream(options: im.hikaru.harness.llm.GenerateOptions): Flow<StreamChunk> =
                        flowOf(BlockStartChunk(0, "text"), TextDeltaChunk(0, "ok"), FinishChunk(StopFinishReason))
                },
            )
            runtime.install(AgentLoopPlugin())
            runtime.install(SessionApiPlugin())
            val api = runtime.context.sessionApi
            val parent = api.create(agentOptions = AgentOptions(cwd = "/tmp", provider = "scripted", model = "test"))
            api.prompt(parent.id, createUserMessage(listOf(TextBlock("first")))).awaitIdle()
            val second = createUserMessage(listOf(TextBlock("second")))
            api.prompt(parent.id, second).awaitIdle()
            val parentHistory = api.history(parent.id)
            assertEquals(4, parentHistory.size)

            val forked = api.fork(parent.id, second.id.value)

            val forkedHistory = api.history(forked.id)
            assertEquals(parentHistory.take(2).map { it.id }, forkedHistory.map { it.id })
            assertEquals("first", (forkedHistory.first().content.single() as TextBlock).text)
            assertEquals("/tmp", forked.cwd)
            assertEquals("first", forked.title)
            assertEquals(4, api.history(parent.id).size)
            val header = runtime.context.sessions.get(forked.id)?.header
            assertEquals(SessionOrigin.FORK, header?.origin)
            assertEquals(parent.id, header?.parentSession)
            val seedLength = requireNotNull(header?.seedLength)
            assertEquals(runtime.context.sessions.get(forked.id)?.events()?.size?.toLong(), seedLength)
            assertTrue(seedLength > parentHistory.take(2).size, "the seed is an event prefix, not a message count")
        } finally {
            runtime.context.dispose()
        }
    }
}
