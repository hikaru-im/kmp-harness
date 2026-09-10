package im.hikaru.contracts.app.member

import im.hikaru.contracts.common.ApiResult
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MemberProfileContractTest {
    @Test
    fun memberProfileRoundTripsNestedLevel() {
        val result = ApiResult(
            code = ApiResult.SUCCESS_CODE,
            data = MemberProfileResponse(
                id = 7L,
                nickname = "会员",
                profileVersion = 3,
                point = 120,
                level = MemberProfileLevelResponse(id = 2L, name = "黄金", level = 2),
            ),
        )

        val encoded = Json.encodeToString(result)
        val decoded = Json.decodeFromString<ApiResult<MemberProfileResponse>>(encoded)

        assertTrue(decoded.isSuccess)
        assertEquals(7L, decoded.data?.id)
        assertEquals(3, decoded.data?.profileVersion)
        assertEquals(2, decoded.data?.level?.level)
    }

    @Test
    fun accountSecurityRequestsUseBackendFieldNames() {
        val mobileJson = Json.encodeToString(
            MemberUpdateMobileRequest(
                code = "123456",
                mobile = "13900000000",
                oldCode = "654321",
            ),
        )
        val resetJson = Json.encodeToString(
            MemberResetPasswordRequest(
                password = "new-password",
                code = "123456",
                mobile = "13900000000",
            ),
        )

        assertTrue("\"oldCode\":\"654321\"" in mobileJson)
        assertTrue("\"password\":\"new-password\"" in resetJson)
        assertTrue("\"mobile\":\"13900000000\"" in resetJson)
    }
}
