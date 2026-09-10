package im.hikaru.contracts

import im.hikaru.contracts.auth.AuthLoginResponse
import im.hikaru.contracts.auth.AuthMenu
import im.hikaru.contracts.auth.AuthPermissionInfo
import im.hikaru.contracts.auth.AuthUser
import im.hikaru.contracts.common.ApiResult
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.SerializationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ContractSerializationTest {
    @Test
    fun authResponseUsesBackendFieldNames() {
        val response = ApiResult(
            code = 0,
            msg = "",
            data = AuthLoginResponse(
                userId = 42,
                accessToken = "access-token",
                refreshToken = "refresh-token",
                expiresTime = "2026-07-18T12:00:00",
            ),
        )

        val json = Json.encodeToString(response)

        assertTrue(response.isSuccess)
        assertTrue("\"userId\":42" in json)
        assertTrue("\"accessToken\":\"access-token\"" in json)
        assertEquals(response, Json.decodeFromString(json))
    }

    @Test
    fun nullableLongAndDefaultCollectionsSurviveRoundTrip() {
        val response = AuthPermissionInfo(
            user = AuthUser(id = 9007199254740991L, username = "admin"),
            roles = setOf("super_admin"),
            menus = listOf(AuthMenu(id = 1L, name = "Dashboard", children = emptyList())),
        )

        val json = Json.encodeToString(response)
        val decoded = Json.decodeFromString<AuthPermissionInfo>(json)

        assertEquals(response, decoded)
        assertTrue("9007199254740991" in json)
        assertEquals(emptyList(), AuthPermissionInfo().menus)
        assertEquals(emptySet(), AuthPermissionInfo().permissions)
    }

    @Test
    fun loginRequestOmitsOptionalDefaultFields() {
        val json = Json.encodeToString(im.hikaru.contracts.auth.AuthLoginRequest(username = "admin", password = "admin123"))

        assertTrue("username" in json)
        assertTrue("password" in json)
        assertTrue("captchaVerification" !in json)
        assertEquals("admin", Json.decodeFromString<Map<String, String?>>(json)["username"])
    }

    @Test
    fun loginRequestRequiresCredentialsWhenDecoding() {
        assertFailsWith<SerializationException> {
            Json.decodeFromString<im.hikaru.contracts.auth.AuthLoginRequest>("{}")
        }
        assertFailsWith<SerializationException> {
            Json.decodeFromString<im.hikaru.contracts.auth.AuthLoginRequest>("{\"username\":\"admin\"}")
        }
    }

    @Test
    fun loginResponsePreservesWireTimeAndNullability() {
        val json = """
            {"userId":null,"accessToken":"access-token","refreshToken":"refresh-token","expiresTime":"2026-07-18T12:00:00"}
        """.trimIndent()
        val response = Json.decodeFromString<AuthLoginResponse>(json)

        assertEquals(null, response.userId)
        assertEquals("2026-07-18T12:00:00", response.expiresTime)
        assertTrue(response.expiresTime!!.matches(Regex("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}")))
    }
}
