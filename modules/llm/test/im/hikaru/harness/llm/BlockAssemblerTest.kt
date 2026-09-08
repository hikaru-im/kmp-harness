package im.hikaru.harness.llm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class BlockAssemblerTest {

    @Test
    fun shouldAssembleInterleavedDeltasInBlockOrder() {
        val assembler = BlockAssembler()

        listOf(
            BlockStartChunk(index = 0, blockType = "reasoning"),
            ReasoningDeltaChunk(index = 0, text = "thinking"),
            BlockEndChunk(index = 0, block = ReasoningBlock("thinking")),
            BlockStartChunk(index = 1, blockType = "text"),
            TextDeltaChunk(index = 1, text = "hello"),
            TextDeltaChunk(index = 1, text = " world"),
            BlockStartChunk(index = 2, blockType = "tool-call"),
            ToolCallDeltaChunk(
                index = 2,
                id = CallId("call-1"),
                name = "echo",
                argumentsDelta = "{\"text\":",
            ),
            ToolCallDeltaChunk(
                index = 2,
                id = CallId("call-1"),
                argumentsDelta = "\"hi\"}",
            ),
            UsageChunk(TokenUsage(inputTokens = 10, outputTokens = 5)),
            FinishChunk(ToolCallsFinishReason),
        ).forEach(assembler::push)

        assertEquals(
            listOf(
                ReasoningBlock("thinking"),
                TextBlock("hello world"),
                ToolCallBlock(
                    id = CallId("call-1"),
                    name = "echo",
                    arguments = "{\"text\":\"hi\"}",
                ),
            ),
            assembler.blocks(),
        )
        assertEquals(TokenUsage(10, 5), assembler.usage)
        assertEquals(ToolCallsFinishReason, assembler.finish)
    }

    @Test
    fun shouldSupportDeltaOnlyProtocolsAndFirstCloseWins() {
        val assembler = BlockAssembler()

        assembler.push(TextDeltaChunk(index = 0, text = "delta"))
        assembler.push(BlockEndChunk(index = 0, block = TextBlock("complete")))
        assembler.push(BlockEndChunk(index = 0, block = TextBlock("ignored")))
        assembler.push(TextDeltaChunk(index = 0, text = "ignored"))

        assertEquals(listOf(TextBlock("complete")), assembler.blocks())
        assertEquals(StopFinishReason, assembler.finish)
    }

    @Test
    fun maxTokensShouldDropIncompleteToolCalls() {
        val assembler = BlockAssembler()

        assembler.push(
            ToolCallDeltaChunk(
                index = 0,
                id = CallId("call"),
                name = "write",
                argumentsDelta = "{",
            )
        )
        assembler.push(FinishChunk(MaxTokensFinishReason))

        assertEquals(emptyList(), assembler.blocks())
    }

    @Test
    fun unknownOpenBlockShouldRequireAuthoritativeBlockEnd() {
        val assembler = BlockAssembler()
        assembler.push(BlockStartChunk(index = 0, blockType = "future-block"))

        val error =
            assertFailsWith<IllegalStateException> {
                assembler.blocks()
            }

        assertEquals(
            "Cannot assemble incomplete block of type 'future-block'",
            error.message,
        )
    }

    @Test
    fun messageShouldCarryProviderModelAndReplayState() {
        val assembler = BlockAssembler()
        assembler.push(TextDeltaChunk(index = 0, text = "answer"))
        assembler.push(FinishChunk(StopFinishReason))

        val message = assembler.message(provider = "provider", model = "model")
        val source = assertIs<ModelMessageSource>(message.source)

        assertEquals(MessageRole.ASSISTANT, message.role)
        assertEquals("provider", source.provider)
        assertEquals("model", source.model)
    }
}
