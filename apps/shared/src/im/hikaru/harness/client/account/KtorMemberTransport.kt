package im.hikaru.harness.client.account

import im.hikaru.contracts.app.member.*
import im.hikaru.contracts.app.system.AppTenantResponse
import im.hikaru.contracts.common.ApiResult
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** No ambient bearer plugin: credentials come exclusively from each immutable session snapshot.
 * Redirects are disabled so tokens can never follow a server redirect to a different authority.
 */
fun createAccountHttpClient(engine: HttpClientEngine): HttpClient = HttpClient(engine) {
    followRedirects = false
    expectSuccess = false
    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; explicitNulls = false }) }
    install(HttpTimeout) { connectTimeoutMillis = 10_000; requestTimeoutMillis = 15_000; socketTimeoutMillis = 15_000 }
    install(WebSockets)
}

class KtorMemberTransport(
    private val client: HttpClient,
    private val engine: HttpClientEngine,
) : MemberTransport {
    override suspend fun login(target: BackendTenant, mobile: String, password: String): MemberAuthLoginResponse =
        response { client.post(target.baseUrl + "/app-api/member/auth/login") {
            tenant(target); contentType(ContentType.Application.Json); setBody(MemberPasswordLoginRequest(mobile, password))
        } }
    override suspend fun refresh(session: AppSession): MemberAuthLoginResponse = response {
        client.post(session.identity.target.baseUrl + "/app-api/member/auth/refresh-token") {
            tenant(session.identity.target); parameter("refreshToken", session.refreshToken)
        }
    }
    override suspend fun logout(session: AppSession) {
        response<Boolean> { client.post(session.identity.target.baseUrl + "/app-api/member/auth/logout") { authenticate(session) } }
    }
    override suspend fun profile(session: AppSession): MemberProfileResponse = response {
        client.get(session.identity.target.baseUrl + "/app-api/member/user/get") { authenticate(session) }
    }
    override suspend fun updateProfile(session: AppSession, request: MemberProfileUpdateRequest): Boolean = response {
        client.put(session.identity.target.baseUrl + "/app-api/member/user/update") {
            authenticate(session); contentType(ContentType.Application.Json); setBody(request)
        }
    }
    override suspend fun tenant(target: BackendTenant, website: String): AppTenantResponse? = response {
        client.get(target.baseUrl + "/app-api/system/tenant/get-by-website") { parameter("website", website) }
    }
    override fun close() {
        var failure: Throwable? = null
        try {
            client.close()
        } catch (error: Throwable) {
            failure = error
        }
        try {
            engine.close()
        } catch (error: Throwable) {
            if (failure == null) failure = error else if (failure !== error) failure.addSuppressed(error)
        }
        failure?.let { throw it }
    }

    private fun HttpRequestBuilder.tenant(target: BackendTenant) { header("tenant-id", target.tenantId) }
    private fun HttpRequestBuilder.authenticate(session: AppSession) {
        tenant(session.identity.target)
        header(HttpHeaders.Authorization, "Bearer ${session.accessToken}")
    }
    private suspend inline fun <reified T> response(crossinline request: suspend () -> HttpResponse): T {
        try {
            val response = request()
            if (response.status.value == 401) throw AccountException.Authentication()
            if (response.status.value !in 200..299) throw AccountException.Rejected(response.status.value)
            val envelope = response.body<ApiResult<T>>()
            if (envelope.code == 401) throw AccountException.Authentication()
            if (!envelope.isSuccess) throw AccountException.Rejected(envelope.code)
            if (envelope.data == null && null !is T) throw AccountException.InvalidResponse()
            @Suppress("UNCHECKED_CAST")
            return envelope.data as T
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: AccountException) { throw failure }
        catch (_: SerializationException) { throw AccountException.InvalidResponse() }
        catch (_: Exception) { throw AccountException.Network() }
    }
}
