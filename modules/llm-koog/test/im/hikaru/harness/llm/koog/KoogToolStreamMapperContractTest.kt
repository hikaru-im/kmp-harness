package im.hikaru.harness.llm.koog

import ai.koog.prompt.streaming.StreamFrame
import im.hikaru.harness.llm.BlockEndChunk
import im.hikaru.harness.llm.BlockStartChunk
import im.hikaru.harness.llm.CallId
import im.hikaru.harness.llm.LlmException
import im.hikaru.harness.llm.ToolCallBlock
import im.hikaru.harness.llm.ToolCallDeltaChunk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private fun DefaultKoogToolStreamMapper.map(
    frame: StreamFrame,
    state: KoogStreamState,
) = map(frame, state, testKoogStreamContext())

class KoogToolStreamMapperContractTest {

    private val mapper = DefaultKoogToolStreamMapper()

    @Test
    fun deltasShouldStartOnceAndReuseKnownCallIdentity() {
        val state = KoogStreamState()

        assertEquals(
            listOf(
                BlockStartChunk(index = 0, blockType = "tool-call"),
                ToolCallDeltaChunk(
                    index = 0,
                    id = CallId("call-1"),
                    name = "lookup",
                    argumentsDelta = "{",
                ),
            ),
            mapper.map(
                StreamFrame.ToolCallDelta(
                    id = "call-1",
                    name = "lookup",
                    content = "{",
                    index = 6,
                ),
                state,
            ),
        )
        assertEquals(
            listOf(
                ToolCallDeltaChunk(
                    index = 0,
                    id = CallId("call-1"),
                    name = null,
                    argumentsDelta = "}",
                )
            ),
            mapper.map(
                StreamFrame.ToolCallDelta(
                    id = null,
                    name = null,
                    content = "}",
                    index = 6,
                ),
                state,
            ),
        )
    }

    @Test
    fun identityOnlyDeltaShouldOpenWithoutInventingArguments() {
        val state = KoogStreamState()

        assertEquals(
            listOf(BlockStartChunk(index = 0, blockType = "tool-call")),
            mapper.map(
                StreamFrame.ToolCallDelta(
                    id = "call-1",
                    name = "lookup",
                    content = null,
                    index = 6,
                ),
                state,
            ),
        )

        assertEquals(
            listOf(
                ToolCallDeltaChunk(
                    index = 0,
                    id = CallId("call-1"),
                    name = "lookup",
                    argumentsDelta = "{}",
                )
            ),
            mapper.map(
                StreamFrame.ToolCallDelta(
                    id = null,
                    name = null,
                    content = "{}",
                    index = 6,
                ),
                state,
            ),
        )
    }

    @Test
    fun completeShouldCloseWithAuthoritativeRawArguments() {
        val state = KoogStreamState()
        mapper.map(
            StreamFrame.ToolCallDelta(
                id = "call-1",
                name = "lookup",
                content = "{\"partial\":",
                index = 2,
            ),
            state,
        )

        assertEquals(
            listOf(
                BlockEndChunk(
                    index = 0,
                    block =
                        ToolCallBlock(
                            id = CallId("call-1"),
                            name = "lookup",
                            arguments = "{\"final\":true}",
                        ),
                )
            ),
            mapper.map(
                StreamFrame.ToolCallComplete(
                    id = "call-1",
                    name = "lookup",
                    content = "{\"final\":true}",
                    index = 2,
                ),
                state,
            ),
        )
    }

    @Test
    fun completeWithoutDeltasShouldStillOpenAndCloseToolBlock() {
        val state = KoogStreamState()

        assertEquals(
            listOf(
                BlockStartChunk(index = 0, blockType = "tool-call"),
                BlockEndChunk(
                    index = 0,
                    block =
                        ToolCallBlock(
                            id = CallId("call-1"),
                            name = "lookup",
                            arguments = "{}",
                        ),
                ),
            ),
            mapper.map(
                StreamFrame.ToolCallComplete(
                    id = "call-1",
                    name = "lookup",
                    content = "{}",
                    index = 9,
                ),
                state,
            ),
        )
    }

    @Test
    fun indexlessFramesShouldReuseTheCurrentImplicitToolBlock() {
        val state = KoogStreamState()

        assertEquals(
            listOf(
                BlockStartChunk(index = 0, blockType = "tool-call"),
                ToolCallDeltaChunk(
                    index = 0,
                    id = CallId("call-1"),
                    name = "lookup",
                    argumentsDelta = "{",
                ),
            ),
            mapper.map(
                StreamFrame.ToolCallDelta(
                    id = "call-1",
                    name = "lookup",
                    content = "{",
                    index = null,
                ),
                state,
            ),
        )
        assertEquals(
            listOf(
                ToolCallDeltaChunk(
                    index = 0,
                    id = CallId("call-1"),
                    name = null,
                    argumentsDelta = "}",
                )
            ),
            mapper.map(
                StreamFrame.ToolCallDelta(
                    id = null,
                    name = null,
                    content = "}",
                    index = null,
                ),
                state,
            ),
        )
        assertEquals(
            listOf(
                BlockEndChunk(
                    index = 0,
                    block =
                        ToolCallBlock(
                            id = CallId("call-1"),
                            name = "lookup",
                            arguments = "{}",
                        ),
                )
            ),
            mapper.map(
                StreamFrame.ToolCallComplete(
                    id = "call-1",
                    name = "lookup",
                    content = "{}",
                    index = null,
                ),
                state,
            ),
        )
    }

    @Test
    fun interleavedExplicitToolBlocksShouldKeepIndependentIdentity() {
        val state = KoogStreamState()

        mapper.map(StreamFrame.ToolCallDelta("call-a", "first", "{", 3), state)
        mapper.map(StreamFrame.ToolCallDelta("call-b", "second", "[", 8), state)

        assertEquals(
            listOf(
                ToolCallDeltaChunk(
                    index = 0,
                    id = CallId("call-a"),
                    name = null,
                    argumentsDelta = "}",
                )
            ),
            mapper.map(StreamFrame.ToolCallDelta(null, null, "}", 3), state),
        )
        assertEquals(
            listOf(
                BlockEndChunk(
                    index = 1,
                    block = ToolCallBlock(CallId("call-b"), "second", "[]"),
                )
            ),
            mapper.map(StreamFrame.ToolCallComplete("call-b", "second", "[]", 8), state),
        )
        assertEquals(
            listOf(
                BlockEndChunk(
                    index = 0,
                    block = ToolCallBlock(CallId("call-a"), "first", "{}"),
                )
            ),
            mapper.map(StreamFrame.ToolCallComplete("call-a", "first", "{}", 3), state),
        )
    }

    @Test
    fun missingOrConflictingIdentityShouldUseStableStateError() {
        assertInvalidState {
            mapper.map(
                StreamFrame.ToolCallDelta(
                    id = null,
                    name = "lookup",
                    content = "{}",
                    index = 0,
                ),
                KoogStreamState(),
            )
        }

        val idConflictState = KoogStreamState()
        mapper.map(
            StreamFrame.ToolCallDelta("call-1", "lookup", "{", 0),
            idConflictState,
        )
        assertInvalidState {
            mapper.map(
                StreamFrame.ToolCallDelta("call-2", null, "}", 0),
                idConflictState,
            )
        }

        val nameConflictState = KoogStreamState()
        mapper.map(
            StreamFrame.ToolCallDelta("call-1", "lookup", "{", 0),
            nameConflictState,
        )
        assertInvalidState {
            mapper.map(
                StreamFrame.ToolCallDelta(null, "other", "}", 0),
                nameConflictState,
            )
        }
    }

    @Test
    fun negativeIndexesShouldUseStableStateError() {
        assertInvalidState {
            mapper.map(
                StreamFrame.ToolCallDelta(
                    id = "call-1",
                    name = "lookup",
                    content = "{}",
                    index = -1,
                ),
                KoogStreamState(),
            )
        }
    }

    private fun assertInvalidState(block: () -> Unit) {
        val error = assertFailsWith<LlmException>(block = block)
        assertEquals(KoogLlmErrorCode.INVALID_STREAM_STATE, error.code)
    }
}
