package im.hikaru.ruoyi.module.system.util.oauth2

import im.hikaru.ruoyi.framework.common.util.http.HttpUtils
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import java.nio.charset.StandardCharsets
import java.util.Base64
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

class OAuth2UtilsTest {
    @Test
    fun `basic authorization supports header and parameter credentials`() {
        val headerRequest = MockHttpServletRequest().apply {
            val credentials = Base64.getEncoder().encodeToString("client:secret:value".toByteArray(StandardCharsets.UTF_8))
            addHeader("Authorization", "Basic $credentials")
        }
        assertEquals(listOf("client", "secret:value"), HttpUtils.obtainBasicAuthorization(headerRequest)?.toList())

        val parameterRequest = MockHttpServletRequest().apply {
            addParameter("client_id", "parameter-client")
            addParameter("client_secret", "parameter-secret")
        }
        assertEquals(
            listOf("parameter-client", "parameter-secret"),
            HttpUtils.obtainBasicAuthorization(parameterRequest)?.toList(),
        )
        assertNull(HttpUtils.obtainBasicAuthorization(MockHttpServletRequest()))
    }

    @Test
    fun `redirect builders preserve query and choose protocol fragment correctly`() {
        val codeRedirect = OAuth2Utils.buildAuthorizationCodeRedirectUri(
            "https://app.example.com/callback?existing=1#anchor",
            "code value",
            "state value",
        )
        assertTrue(codeRedirect.startsWith("https://app.example.com/callback?existing=1&"))
        assertTrue(codeRedirect.contains("code=code%20value"))
        assertTrue(codeRedirect.contains("state=state%20value"))
        assertTrue(codeRedirect.endsWith("#anchor"))

        val implicitRedirect = OAuth2Utils.buildImplicitRedirectUri(
            "https://app.example.com/callback?existing=1",
            "access value",
            "state value",
            Clock.System.now().plus(60.seconds).toLocalDateTime(TimeZone.currentSystemDefault()),
            listOf("user.read", "user.write"),
            linkedMapOf("organization" to "Acme"),
        )
        assertTrue(implicitRedirect.startsWith("https://app.example.com/callback?existing=1#"))
        assertTrue(implicitRedirect.contains("access_token=access%20value"))
        assertTrue(implicitRedirect.contains("token_type=bearer"))
        assertTrue(implicitRedirect.contains("scope=user.read%20user.write"))
        assertTrue(implicitRedirect.contains("organization=Acme"))

        val denied = OAuth2Utils.buildUnsuccessfulRedirect(
            "https://app.example.com/callback",
            "token",
            "state",
            "access_denied",
            "User denied access",
        )
        assertTrue(denied.contains("#error=access_denied"))
        assertEquals(listOf("user.read", "user.write"), OAuth2Utils.buildScopes(" user.read  user.write "))
    }
}
