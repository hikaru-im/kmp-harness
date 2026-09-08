package im.hikaru.harness.llm.koog

import ai.koog.prompt.streaming.StreamFrame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private fun DefaultKoogReasoningMapper.map(
    frame: StreamFrame,
    state: KoogStreamState,
) = map(frame, state, testKoogStreamContext())

class KoogReasoningMapperTest {

    @Test
    fun nonReasoningFrameShouldUseStableStreamStateError() {
        val error =
            assertFailsWith<im.hikaru.harness.llm.LlmException> {
                DefaultKoogReasoningMapper().map(
                    frame = StreamFrame.TextDelta("text", index = 0),
                    state = KoogStreamState(),
                )
            }

        assertEquals(KoogLlmErrorCode.INVALID_STREAM_STATE, error.code)
    }
}
