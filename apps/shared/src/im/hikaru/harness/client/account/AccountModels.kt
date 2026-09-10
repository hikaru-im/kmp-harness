package im.hikaru.harness.client.account

import im.hikaru.contracts.app.member.MemberAuthLoginResponse
import im.hikaru.contracts.app.member.MemberProfileResponse
import im.hikaru.contracts.app.member.MemberProfileUpdateRequest
import im.hikaru.contracts.app.system.AppTenantResponse
import io.ktor.http.Url
import kotlinx.serialization.Serializable

/** Immutable target: never change headers underneath an in-flight request. */
@Serializable
data class BackendTenant(val baseUrl: String, val tenantId: Long) {
    init {
        val url = Url(baseUrl)
        require(url.protocol.name in setOf("https", "http") && url.host.isNotBlank())
        require(url.user == null && url.password == null && url.parameters.isEmpty() && url.fragment.isEmpty())
        require(baseUrl == baseUrl.trimEnd('/') && tenantId > 0)
    }
}

@Serializable
data class MemberIdentity(val target: BackendTenant, val userId: Long, val userType: Int = 1) {
    init { require(userId > 0 && userType == 1) }
}

@Serializable
data class AppSession(val identity: MemberIdentity, val accessToken: String, val refreshToken: String?, val expiresAt: Long?) {
    init { require(accessToken.isNotBlank()) }
    override fun toString(): String = "AppSession(identity=$identity, tokens=<redacted>)"
}

enum class PersistenceStatus(val message: String?) {
    SECURE(null),
    MEMORY_ONLY("安全存储不可用，仅本次会话登录；退出应用后需要重新登录。"),
    READ_FAILED("无法读取安全登录记录，请重新登录。"),
    WRITE_FAILED("无法保存登录，仅本次会话有效。"),
    CLEAR_FAILED("无法删除安全登录记录，请解锁系统安全存储后重试退出。"),
}

data class AccountState(
    val target: BackendTenant? = null,
    val session: AppSession? = null,
    val profile: MemberProfileResponse? = null,
    val generation: Long = 0,
    val persistence: PersistenceStatus = PersistenceStatus.MEMORY_ONLY,
)

/** Only platform secure stores implement durable=true. No Room or ordinary JSON fallback. */
interface AppSessionStore {
    val durable: Boolean
    fun read(): AppSession?
    fun write(session: AppSession)
    fun clear()
}

/** Tenant is non-secret preference data; platform implementations must still use Harness-specific keys. */
interface AppTenantStore {
    fun read(): Long?
    fun write(tenantId: Long?)
}
class MemoryAppSessionStore : AppSessionStore {
    override val durable = false
    override fun read(): AppSession? = null
    override fun write(session: AppSession) = Unit
    override fun clear() = Unit
}

/** Stable errors contain no URL, server message, password, token or response body. */
sealed class AccountException(message: String) : Exception(message) {
    class Authentication : AccountException("Authentication required")
    class Network : AccountException("Network unavailable")
    class InvalidResponse : AccountException("Invalid server response")
    class Rejected(val code: Int?) : AccountException("Request rejected")
    class Storage : AccountException("Secure storage operation failed")
}

interface MemberTransport : AutoCloseable {
    suspend fun login(target: BackendTenant, mobile: String, password: String): MemberAuthLoginResponse
    suspend fun refresh(session: AppSession): MemberAuthLoginResponse
    suspend fun logout(session: AppSession)
    suspend fun profile(session: AppSession): MemberProfileResponse
    suspend fun updateProfile(session: AppSession, request: MemberProfileUpdateRequest): Boolean
    suspend fun tenant(target: BackendTenant, website: String): AppTenantResponse?
}

/** Registered resources belong to the remote account, never to the local Host. */
fun interface AccountResource : AutoCloseable { override fun close() }
