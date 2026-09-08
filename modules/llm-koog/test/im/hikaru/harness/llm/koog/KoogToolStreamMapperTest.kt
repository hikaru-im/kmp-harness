package im.hikaru.harness.llm.koog

import ai.koog.prompt.streaming.StreamFrame
import im.hikaru.harness.llm.LlmException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private fun DefaultKoogToolStreamMapper.map(
    frame: StreamFrame,
    state: KoogStreamState,
) = map(frame, state, testKoogStreamContext())

class KoogToolStreamMapperTest {

    @Test
    fun nonToolFramesShouldUseStableStateError() {
        val error =
            assertFailsWith<LlmException> {
                DefaultKoogToolStreamMapper().map(
                    frame = StreamFrame.TextDelta("text", index = 0),
                    state = KoogStreamState(),
                )
            }

        assertEquals(KoogLlmErrorCode.INVALID_STREAM_STATE, error.code)
    }
}
