package im.hikaru.contracts.app.member

import im.hikaru.contracts.common.ApiResult
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class MemberLevelContractTest {
    @Test
    fun memberLevelListRoundTrips() {
        val result = ApiResult(
            code = ApiResult.SUCCESS_CODE,
            data = listOf(
                MemberLevelResponse(
                    name = "Gold",
                    level = 2,
                    experience = 100,
                    discountPercent = 95,
                ),
            ),
        )

        val encoded = Json.encodeToString(result)
        val decoded = Json.decodeFromString<ApiResult<List<MemberLevelResponse>>>(encoded)

        assertEquals(2, decoded.data?.single()?.level)
    }
}
