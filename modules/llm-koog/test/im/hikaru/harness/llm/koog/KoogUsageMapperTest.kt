package im.hikaru.harness.llm.koog

import ai.koog.prompt.message.ResponseMetaInfo
import im.hikaru.harness.llm.TokenUsage
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private fun DefaultKoogUsageMapper.map(metaInfo: ResponseMetaInfo) =
    map(metaInfo, testKoogStreamContext())

class KoogUsageMapperTest {

    @Test
    fun privateMetadataWithoutStandardCountsShouldRemainAbsent() {
        assertNull(
            DefaultKoogUsageMapper().map(
                ResponseMetaInfo.Empty.copy(
                    metadata =
                        buildJsonObject {
                            put("cached_tokens", 7)
                            put("reasoning_tokens", 11)
                        }
                )
            )
        )
    }

    @Test
    fun maxIntCountsWithoutTotalShouldWidenToLong() {
        assertEquals(
            TokenUsage(
                inputTokens = Int.MAX_VALUE.toLong(),
                outputTokens = Int.MAX_VALUE.toLong(),
            ),
            DefaultKoogUsageMapper().map(
                ResponseMetaInfo.Empty.copy(
                    inputTokensCount = Int.MAX_VALUE,
                    outputTokensCount = Int.MAX_VALUE,
                )
            ),
        )
    }
}
