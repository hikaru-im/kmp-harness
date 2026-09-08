package im.hikaru.harness.llm.koog

import im.hikaru.harness.llm.LlmException
import im.hikaru.harness.llm.StopFinishReason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private fun StopOnlyKoogFinishReasonMapper.map(reason: String?) =
    map(reason, testKoogStreamContext())

class KoogFinishReasonMapperTest {

    @Test
    fun stopShouldMapWithoutGuessingUnknownReasons() {
        val mapper = StopOnlyKoogFinishReasonMapper()

        assertEquals(StopFinishReason, mapper.map("stop"))

        listOf(null, "length", "tool_calls").forEach { reason ->
            val error =
                assertFailsWith<LlmException> {
                    mapper.map(reason)
                }
            assertEquals(KoogLlmErrorCode.UNSUPPORTED_FINISH_REASON, error.code)
        }
    }
}
