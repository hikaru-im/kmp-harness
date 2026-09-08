package im.hikaru.harness.llm.koog

import ai.koog.prompt.message.ResponseMetaInfo
import im.hikaru.harness.llm.LlmException
import im.hikaru.harness.llm.TokenUsage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

private fun DefaultKoogUsageMapper.map(metaInfo: ResponseMetaInfo) =
    map(metaInfo, testKoogStreamContext())

class KoogUsageMapperContractTest {

    private val mapper = DefaultKoogUsageMapper()

    @Test
    fun completeInputAndOutputCountsShouldMapWithoutInventingPrivateCounts() {
        assertEquals(
            TokenUsage(
                inputTokens = 8,
                outputTokens = 4,
            ),
            mapper.map(
                ResponseMetaInfo.Empty.copy(
                    totalTokensCount = 12,
                    inputTokensCount = 8,
                    outputTokensCount = 4,
                )
            ),
        )
    }

    @Test
    fun totalMayBeAbsentWhenBothDisjointCountsExist() {
        assertEquals(
            TokenUsage(
                inputTokens = 5,
                outputTokens = 3,
            ),
            mapper.map(
                ResponseMetaInfo.Empty.copy(
                    inputTokensCount = 5,
                    outputTokensCount = 3,
                )
            ),
        )
    }

    @Test
    fun completelyAbsentUsageShouldReturnNull() {
        assertNull(mapper.map(ResponseMetaInfo.Empty))
    }

    @Test
    fun partialNegativeOrInconsistentCountsShouldFailWithStableCode() {
        val invalid =
            listOf(
                ResponseMetaInfo.Empty.copy(totalTokensCount = 9),
                ResponseMetaInfo.Empty.copy(inputTokensCount = 5),
                ResponseMetaInfo.Empty.copy(outputTokensCount = 4),
                ResponseMetaInfo.Empty.copy(
                    inputTokensCount = -1,
                    outputTokensCount = 4,
                ),
                ResponseMetaInfo.Empty.copy(
                    inputTokensCount = 5,
                    outputTokensCount = -1,
                ),
                ResponseMetaInfo.Empty.copy(
                    totalTokensCount = -1,
                    inputTokensCount = 0,
                    outputTokensCount = 0,
                ),
                ResponseMetaInfo.Empty.copy(
                    totalTokensCount = 9,
                    inputTokensCount = 5,
                ),
                ResponseMetaInfo.Empty.copy(
                    totalTokensCount = 10,
                    inputTokensCount = 5,
                    outputTokensCount = 4,
                ),
                ResponseMetaInfo.Empty.copy(
                    totalTokensCount = Int.MAX_VALUE,
                    inputTokensCount = Int.MAX_VALUE,
                    outputTokensCount = Int.MAX_VALUE,
                ),
            )

        invalid.forEach { metaInfo ->
            val error =
                assertFailsWith<LlmException> {
                    mapper.map(metaInfo)
                }
            assertEquals(KoogLlmErrorCode.INVALID_USAGE, error.code)
        }
    }
}
