package im.hikaru.harness.llm.retry

import im.hikaru.harness.agent.AgentPlugin
import im.hikaru.harness.agent.agents
import im.hikaru.harness.agent.loop.AgentLoopPlugin
import im.hikaru.harness.llm.BlockStartChunk
import im.hikaru.harness.llm.ErrorFinishReason
import im.hikaru.harness.llm.FinishChunk
import im.hikaru.harness.llm.GenerateOptions
import im.hikaru.harness.llm.LlmAdapter
import im.hikaru.harness.llm.LlmException
import im.hikaru.harness.llm.LlmPlugin
import im.hikaru.harness.llm.NormalRetryPolicy
import im.hikaru.harness.llm.StopFinishReason
import im.hikaru.harness.llm.StreamChunk
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

class LlmRetryContractTest {
    @Test
    fun retryIsPersistedBeforeSecondAttempt() = runTest {
        val runtime = Runtime()
        runtime.install(SessionPlugin())
        runtime.install(LlmPlugin())
        runtime.install(AgentPlugin())
        var calls = 0
        runtime.context.llm.registerAdapter(
            providers = listOf("scripted"),
            adapter = object : LlmAdapter {
                override fun providerRetryPolicy(provider: String) =
                    NormalRetryPolicy(maxRetries = 1, initialDelayMs = 1, maxDelayMs = 1, jitterRatio = 0.0)

                override fun stream(options: GenerateOptions): Flow<StreamChunk> {
                    calls += 1
                    return if (calls == 1) {
                        flowOf(FinishChunk(ErrorFinishReason(LlmException("server", "SERVER").failure)))
                    } else {
                        flowOf(BlockStartChunk(0, "text"), TextDeltaChunk(0, "ok"), FinishChunk(StopFinishReason))
                    }
                }
            },
        )
        runtime.install(AgentLoopPlugin("scripted", "test"))
        runtime.install(LlmRetryPlugin())
        val handle = runtime.context.agents.create()
        handle.agent.followup(createUserMessage(listOf(TextBlock("hi"))))
        handle.agent.awaitIdle()
        assertEquals(2, calls)
        val types = handle.agent.session.events().map { it.type }
        assertEquals(1, types.count { it == "llm/retry" })
        assertEquals(1, types.count { it == "llm/retry-started" })
        assertEquals(1, types.count { it == "llm/retry-terminal" })
        handle.dispose()
        runtime.context.dispose()
    }

}
