package im.hikaru.harness.llm.koog

import ai.koog.prompt.streaming.StreamFrame
import im.hikaru.harness.llm.BlockEndChunk
import im.hikaru.harness.llm.BlockStartChunk
import im.hikaru.harness.llm.CallId
import im.hikaru.harness.llm.FinishChunk
import im.hikaru.harness.llm.ToolCallBlock
import im.hikaru.harness.llm.ToolCallDeltaChunk
import im.hikaru.harness.llm.ToolCallsFinishReason
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class KoogToolStreamIntegrationContractTest {

    private val deltaOnlyToolCallPolicy =
        KoogToolCallTerminalPolicy { frame, _ ->
            assertEquals("tool_calls", frame.finishReason)
            true
        }

    @Test
    fun deltaOnlyToolCallsShouldCloseAtToolCallsTerminal() = runTest {
        val chunks =
            KoogStreamMapper(
                toolCallTerminalPolicy = deltaOnlyToolCallPolicy,
                finishReasonMapper =
                    KoogFinishReasonMapper { reason, _ ->
                        assertEquals("tool_calls", reason)
                        ToolCallsFinishReason
                    }
            ).map(
                frames =
                    flowOf(
                        StreamFrame.ToolCallDelta(
                            id = "call-1",
                            name = "lookup",
                            content = "{\"q\":",
                            index = 0,
                        ),
                        StreamFrame.ToolCallDelta(
                            id = null,
                            name = null,
                            content = "\"x\"}",
                            index = 0,
                        ),
                        StreamFrame.End(finishReason = "tool_calls"),
                    ),
                context = testKoogStreamContext(),
            ).toList()

        assertEquals(
            listOf(
                BlockStartChunk(index = 0, blockType = "tool-call"),
                ToolCallDeltaChunk(
                    index = 0,
                    id = CallId("call-1"),
                    name = "lookup",
                    argumentsDelta = "{\"q\":",
                ),
                ToolCallDeltaChunk(
                    index = 0,
                    id = CallId("call-1"),
                    name = null,
                    argumentsDelta = "\"x\"}",
                ),
                BlockEndChunk(
                    index = 0,
                    block =
                        ToolCallBlock(
                            id = CallId("call-1"),
                            name = "lookup",
                            arguments = "{\"q\":\"x\"}",
                        ),
                ),
                FinishChunk(ToolCallsFinishReason),
            ),
            chunks,
        )
    }

    @Test
    fun toolFramesShouldCloseBeforeProviderSpecificToolFinish() = runTest {
        val context =
            testKoogStreamContext(
                provider = "harness-route",
                model = "provider-model",
                koogProvider = "koog-provider",
            )
        var finishContext: KoogStreamContext? = null
        val mapper =
            KoogStreamMapper(
                toolCallTerminalPolicy = deltaOnlyToolCallPolicy,
                finishReasonMapper =
                    KoogFinishReasonMapper { reason, seenContext ->
                        assertEquals("tool_calls", reason)
                        finishContext = seenContext
                        ToolCallsFinishReason
                    }
            )

        val chunks =
            mapper.map(
                frames =
                    flowOf(
                        StreamFrame.ToolCallDelta(
                            id = "call-1",
                            name = "lookup",
                            content = "{",
                            index = 2,
                        ),
                        StreamFrame.ToolCallDelta(
                            id = null,
                            name = null,
                            content = "}",
                            index = 2,
                        ),
                        StreamFrame.ToolCallComplete(
                            id = "call-1",
                            name = "lookup",
                            content = "{}",
                            index = 2,
                        ),
                        StreamFrame.End(finishReason = "tool_calls"),
                    ),
                context = context,
            ).toList()

        assertEquals(context, finishContext)
        assertEquals(
            listOf(
                BlockStartChunk(index = 0, blockType = "tool-call"),
                ToolCallDeltaChunk(
                    index = 0,
                    id = CallId("call-1"),
                    name = "lookup",
                    argumentsDelta = "{",
                ),
                ToolCallDeltaChunk(
                    index = 0,
                    id = CallId("call-1"),
                    name = null,
                    argumentsDelta = "}",
                ),
                BlockEndChunk(
                    index = 0,
                    block =
                        ToolCallBlock(
                            id = CallId("call-1"),
                            name = "lookup",
                            arguments = "{}",
                        ),
                ),
                FinishChunk(ToolCallsFinishReason),
            ),
            chunks,
        )
    }
}
