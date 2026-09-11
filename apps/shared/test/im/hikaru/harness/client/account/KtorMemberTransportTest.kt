package im.hikaru.harness.client.account

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KtorMemberTransportTest {
    @Test
    fun `authenticated requests use only their immutable identity target`() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val engine = MockEngine { request ->
            requests += request
            respond(
                content = """{"code":0,"data":{"id":11,"nickname":"Member"}}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val transport = KtorMemberTransport(createAccountHttpClient(engine), engine)
        val session = AppSession(
            MemberIdentity(BackendTenant("https://one.example.test", 7), 11),
            accessToken = "sensitive-access-token",
            refreshToken = "sensitive-refresh-token",
            expiresAt = null,
        )

        transport.profile(session)

        assertEquals("https://one.example.test/app-api/member/user/get", requests.single().url.toString())
        assertEquals("7", requests.single().headers["tenant-id"])
        assertEquals("Bearer sensitive-access-token", requests.single().headers[HttpHeaders.Authorization])
        transport.close()
    }

    @Test
    fun `tenant discovery never sends an account token`() = runTest {
        var request: HttpRequestData? = null
        val engine = MockEngine { captured ->
            request = captured
            respond(
                content = """{"code":0,"data":{"id":7,"name":"Tenant"}}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val transport = KtorMemberTransport(createAccountHttpClient(engine), engine)

        transport.tenant(BackendTenant("https://tenant.example.test", 7), "tenant.example.test")

        assertNull(request?.headers?.get(HttpHeaders.Authorization))
        assertEquals("https://tenant.example.test/app-api/system/tenant/get-by-website?website=tenant.example.test", request?.url.toString())
        transport.close()
    }
}
