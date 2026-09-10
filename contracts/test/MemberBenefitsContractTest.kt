package im.hikaru.contracts.app.member

import im.hikaru.contracts.common.ApiResult
import im.hikaru.contracts.common.PageResponse
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MemberBenefitsContractTest {
    @Test
    fun pagedMemberRecordsRoundTripWithBackendFieldNames() {
        val result = ApiResult(
            code = ApiResult.SUCCESS_CODE,
            data = PageResponse(
                total = 1L,
                list = listOf(
                    MemberPointRecordResponse(
                        id = 11L,
                        title = "签到奖励",
                        point = 10,
                        createTime = 1_785_126_900_863,
                    ),
                ),
            ),
        )

        val json = Json.encodeToString(result)
        val decoded = Json.decodeFromString<ApiResult<PageResponse<MemberPointRecordResponse>>>(json)

        assertTrue(decoded.isSuccess)
        assertTrue("\"createTime\"" in json)
        assertEquals(11L, decoded.data?.list?.single()?.id)
        assertEquals(1_785_126_900_863, decoded.data?.list?.single()?.createTime)
    }

    @Test
    fun memberRecordPagesDecodeServerEpochMillis() {
        val point = Json.decodeFromString<ApiResult<PageResponse<MemberPointRecordResponse>>>(
            """{"code":0,"data":{"list":[{"id":11,"createTime":1785126900863}],"total":1}}""",
        )
        val experience = Json.decodeFromString<ApiResult<PageResponse<MemberExperienceRecordResponse>>>(
            """{"code":0,"data":{"list":[{"experience":5,"createTime":1785126900863}],"total":1}}""",
        )
        val signIn = Json.decodeFromString<ApiResult<PageResponse<MemberSignInRecordResponse>>>(
            """{"code":0,"data":{"list":[{"id":12,"day":2,"point":10,"experience":5,"createTime":1785126900863}],"total":1}}""",
        )

        assertEquals(1_785_126_900_863, point.data?.list?.single()?.createTime)
        assertEquals(1_785_126_900_863, experience.data?.list?.single()?.createTime)
        assertEquals(1_785_126_900_863, signIn.data?.list?.single()?.createTime)
    }

    @Test
    fun signInSummaryKeepsNullableWireFields() {
        val json = """
            {"totalDay":12,"continuousDay":3,"todaySignIn":true}
        """.trimIndent()

        val summary = Json.decodeFromString<MemberSignInSummaryResponse>(json)

        assertEquals(12, summary.totalDay)
        assertEquals(3, summary.continuousDay)
        assertEquals(true, summary.todaySignIn)
    }
}
