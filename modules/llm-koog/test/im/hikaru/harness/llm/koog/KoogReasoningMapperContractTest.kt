package im.hikaru.harness.llm.koog

import ai.koog.prompt.streaming.StreamFrame
import im.hikaru.harness.llm.BlockEndChunk
import im.hikaru.harness.llm.BlockStartChunk
import im.hikaru.harness.llm.LlmException
import im.hikaru.harness.llm.ReasoningBlock
import im.hikaru.harness.llm.ReasoningDeltaChunk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private fun DefaultKoogReasoningMapper.map(
    frame: StreamFrame,
    state: KoogStreamState,
) = map(frame, state, testKoogStreamContext())

class KoogReasoningMapperContractTest {

    private val mapper = DefaultKoogReasoningMapper()

    @Test
    fun plainTextDeltasShouldStartOnceAndPreserveOrder() {
        val state = KoogStreamState()

        assertEquals(
            listOf(
                BlockStartChunk(index = 0, blockType = "reasoning"),
                ReasoningDeltaChunk(index = 0, text = "think"),
            ),
            mapper.map(
                StreamFrame.ReasoningDelta(text = "think", index = 5),
                state,
            ),
        )
        assertEquals(
            listOf(ReasoningDeltaChunk(index = 0, text = "ing")),
            mapper.map(
                StreamFrame.ReasoningDelta(text = "ing", index = 5),
                state,
            ),
        )
    }

    @Test
    fun completeShouldCloseWithAuthoritativeConcatenatedContent() {
        val state = KoogStreamState()
        mapper.map(
            StreamFrame.ReasoningDelta(text = "thinking", index = 2),
            state,
        )

        assertEquals(
            listOf(
                BlockEndChunk(
                    index = 0,
                    block = ReasoningBlock("thinking"),
                )
            ),
            mapper.map(
                StreamFrame.ReasoningComplete(
                    id = null,
                    content = listOf("think", "ing"),
                    index = 2,
                ),
                state,
            ),
        )
    }

    @Test
    fun completeWithoutDeltaShouldStillOpenAndCloseReasoningBlock() {
        val state = KoogStreamState()

        assertEquals(
            listOf(
                BlockStartChunk(index = 0, blockType = "reasoning"),
                BlockEndChunk(
                    index = 0,
                    block = ReasoningBlock("complete-only"),
                ),
            ),
            mapper.map(
                StreamFrame.ReasoningComplete(
                    id = null,
                    content = listOf("complete", "-only"),
                    index = 9,
                ),
                state,
            ),
        )
    }

    @Test
    fun unrepresentableReasoningFieldsShouldFailInsteadOfBeingDropped() {
        val unsupported =
            listOf(
                StreamFrame.ReasoningDelta(
                    id = "reasoning-id",
                    text = "thinking",
                    index = 0,
                ),
                StreamFrame.ReasoningDelta(
                    text = null,
                    summary = "summary",
                    index = 0,
                ),
                StreamFrame.ReasoningComplete(
                    id = null,
                    content = listOf("thinking"),
                    summary = listOf("summary"),
                    index = 0,
                ),
                StreamFrame.ReasoningComplete(
                    id = null,
                    content = listOf("thinking"),
                    encrypted = "encrypted",
                    index = 0,
                ),
                StreamFrame.ReasoningDelta(
                    id = "",
                    text = "thinking",
                    index = 0,
                ),
                StreamFrame.ReasoningDelta(
                    text = "thinking",
                    summary = "",
                    index = 0,
                ),
                StreamFrame.ReasoningComplete(
                    id = null,
                    content = listOf("thinking"),
                    summary = emptyList(),
                    index = 0,
                ),
                StreamFrame.ReasoningComplete(
                    id = null,
                    content = listOf("thinking"),
                    encrypted = "",
                    index = 0,
                ),
            )

        unsupported.forEach { frame ->
            val error =
                assertFailsWith<LlmException> {
                    mapper.map(frame, KoogStreamState())
                }
            assertEquals(KoogLlmErrorCode.UNSUPPORTED_REASONING_CONTENT, error.code)
        }
    }

    @Test
    fun negativeIndexesShouldUseStreamStateError() {
        val error =
            assertFailsWith<LlmException> {
                mapper.map(
                    StreamFrame.ReasoningDelta(
                        text = "thinking",
                        index = -1,
                    ),
                    KoogStreamState(),
                )
            }
        assertEquals(KoogLlmErrorCode.INVALID_STREAM_STATE, error.code)
    }
}
