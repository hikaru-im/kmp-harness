package im.hikaru.contracts.app.member

import im.hikaru.contracts.common.ApiResult
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MemberAuthContractTest {
    @Test
    fun loginRequestUsesMemberBackendFieldNames() {
        val request = MemberPasswordLoginRequest(
            mobile = "15600000000",
            password = "secret",
        )

        val json = Json.encodeToString(request)

        assertTrue("\"mobile\":\"15600000000\"" in json)
        assertTrue("\"password\":\"secret\"" in json)
        assertTrue("socialType" !in json)
    }

    @Test
    fun loginResponseRoundTripsInsideApiResult() {
        val result = ApiResult(
            code = ApiResult.SUCCESS_CODE,
            data = MemberAuthLoginResponse(
                userId = 7L,
                accessToken = "access",
                refreshToken = "refresh",
                expiresTime = 1_785_126_900_863,
            ),
        )

        val decoded = Json.decodeFromString<ApiResult<MemberAuthLoginResponse>>(Json.encodeToString(result))

        assertEquals(7L, decoded.data?.userId)
        assertEquals("refresh", decoded.data?.refreshToken)
        assertEquals(1_785_126_900_863, decoded.data?.expiresTime)
    }

    @Test
    fun loginResponseDecodesServerEpochMillis() {
        val response =
            """{"code":0,"msg":"","data":{"accessToken":"access","expiresTime":1785126900863,"openid":null,"refreshToken":"refresh","userId":2}}"""

        val decoded = Json.decodeFromString<ApiResult<MemberAuthLoginResponse>>(response)

        assertEquals(2L, decoded.data?.userId)
        assertEquals(1_785_126_900_863, decoded.data?.expiresTime)
    }

    @Test
    fun smsRequestsUseAppApiFieldNames() {
        val sendJson = Json.encodeToString(
            MemberSmsSendRequest(
                mobile = "15600000000",
                scene = 1,
            ),
        )
        val validateJson = Json.encodeToString(
            MemberSmsValidateRequest(
                mobile = "15600000000",
                scene = 1,
                code = "123456",
            ),
        )

        assertTrue("\"mobile\":\"15600000000\"" in sendJson)
        assertTrue("\"scene\":1" in sendJson)
        assertTrue("\"code\":\"123456\"" in validateJson)
        assertTrue("\"scene\":1" in validateJson)
    }

    @Test
    fun socialCapabilityContractsKeepWireNames() {
        val request = MemberSocialWxaQrcodeRequest(
            scene = "member=7",
            path = "pages/index",
            width = 430,
            autoColor = true,
            checkPath = true,
            hyaline = false,
        )
        val json = Json.encodeToString(request)

        assertTrue("\"scene\":\"member=7\"" in json)
        assertTrue("\"path\":\"pages/index\"" in json)
        assertTrue("\"autoColor\":true" in json)
        assertTrue("\"checkPath\":true" in json)
        assertTrue("\"hyaline\":false" in json)
    }
}
