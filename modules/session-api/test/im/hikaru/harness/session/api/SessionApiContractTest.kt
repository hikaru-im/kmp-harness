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
import im.hikaru.harness.session.SessionPlugin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SessionApiContractTest {
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
}
