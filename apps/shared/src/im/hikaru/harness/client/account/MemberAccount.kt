package im.hikaru.harness.client.account

import im.hikaru.contracts.app.member.MemberAuthLoginResponse
import im.hikaru.contracts.app.member.MemberProfileResponse
import im.hikaru.contracts.app.member.MemberProfileUpdateRequest
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.CoroutineContext

/** Application owner calls shutdown; identity changes close only account-owned resources. */
class MemberAccount(
    private val transport: MemberTransport,
    private val store: AppSessionStore,
    private val context: CoroutineContext = Dispatchers.Default,
) {
    private val mutex = Mutex()
    private val refreshMutex = Mutex()
    private var job = SupervisorJob()
    private var closed = false
    private val resources = mutableListOf<AccountResource>()
    private val mutableState = MutableStateFlow(restore())
    val state: StateFlow<AccountState> = mutableState.asStateFlow()

    private fun restore(): AccountState = try {
        val session = store.read()
        AccountState(target = session?.identity?.target, session = session,
            persistence = if (store.durable) PersistenceStatus.SECURE else PersistenceStatus.MEMORY_ONLY)
    } catch (_: Exception) { AccountState(persistence = PersistenceStatus.READ_FAILED) }

    private fun invalidate(target: BackendTenant?): Boolean {
        job.cancel()
        resources.forEach { try { it.close() } catch (_: Exception) { /* Continue closing remaining owners. */ } }
        resources.clear()
        job = SupervisorJob()
        val cleared = try { store.clear(); true } catch (_: Exception) { false }
        mutableState.value = AccountState(target = target, generation = state.value.generation + 1,
            persistence = if (!cleared) PersistenceStatus.CLEAR_FAILED else if (store.durable)
                PersistenceStatus.SECURE else PersistenceStatus.MEMORY_ONLY)
        return cleared
    }

    suspend fun selectTarget(target: BackendTenant) = mutex.withLock {
        check(!closed)
        if (target != state.value.target && !invalidate(target)) throw AccountException.Storage()
    }

    /** Starting any login invalidates the previous account, including concurrent older login attempts. */
    suspend fun login(mobile: String, password: String) {
        val operation = mutex.withLock {
            check(!closed)
            val target = requireNotNull(state.value.target) { "Select backend and tenant first" }
            if (!invalidate(target)) throw AccountException.Storage()
            val generation = state.value.generation
            CoroutineScope(context + job).async {
                val response = transport.login(target, mobile, password)
                currentCoroutineContext().ensureActive()
                mutex.withLock {
                    ensureCurrent(generation)
                    save(response.toSession(target))
                }
            }
        }
        awaitOwned(operation)
    }

    private fun ensureCurrent(generation: Long) {
        if (closed || state.value.generation != generation) throw CancellationException("Account changed")
    }

    private fun save(session: AppSession) {
        val persistence = try {
            store.write(session)
            if (store.durable) PersistenceStatus.SECURE else PersistenceStatus.MEMORY_ONLY
        } catch (_: Exception) {
            // Remove any previous refresh token when a new record cannot be committed.
            try { store.clear(); PersistenceStatus.WRITE_FAILED } catch (_: Exception) { PersistenceStatus.CLEAR_FAILED }
        }
        mutableState.value = state.value.copy(session = session, persistence = persistence)
    }

    private suspend fun refreshed(observed: AppSession, generation: Long): AppSession = refreshMutex.withLock {
        val current = mutex.withLock {
            ensureCurrent(generation)
            val latest = state.value.session ?: throw AccountException.Authentication()
            if (latest.accessToken != observed.accessToken) return@withLock latest
            latest
        }
        if (current.accessToken != observed.accessToken) return@withLock current
        try {
            if (current.refreshToken.isNullOrBlank()) throw AccountException.Authentication()
            val response = transport.refresh(current)
            currentCoroutineContext().ensureActive()
            mutex.withLock {
                ensureCurrent(generation)
                val session = response.toSession(current.identity.target)
                if (session.identity != current.identity) throw AccountException.Authentication()
                save(session)
                session
            }
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (failure: AccountException.Authentication) {
            mutex.withLock { if (state.value.generation == generation) invalidate(state.value.target) }
            throw failure
        } catch (failure: AccountException.Rejected) {
            mutex.withLock { if (state.value.generation == generation) invalidate(state.value.target) }
            throw failure
        } catch (failure: AccountException.InvalidResponse) {
            mutex.withLock { if (state.value.generation == generation) invalidate(state.value.target) }
            throw failure
        }
    }

    /** Scope and generation protect both the HTTP operation and its publication. Caller cancellation propagates. */
    private suspend fun <T> authenticated(
        request: suspend (AppSession) -> T,
        publish: (AccountState, T) -> AccountState = { state, _ -> state },
    ): T {
        val operation = mutex.withLock {
            check(!closed)
            val session = state.value.session ?: throw AccountException.Authentication()
            val generation = state.value.generation
            CoroutineScope(context + job).async {
                val result = try { request(session) } catch (_: AccountException.Authentication) {
                    request(refreshed(session, generation))
                }
                currentCoroutineContext().ensureActive()
                mutex.withLock {
                    ensureCurrent(generation)
                    mutableState.value = publish(state.value, result)
                }
                result
            }
        }
        return awaitOwned(operation)
    }

    suspend fun loadProfile(): MemberProfileResponse = authenticated(transport::profile) { state, profile ->
        if (profile.id != state.session?.identity?.userId) throw AccountException.InvalidResponse()
        state.copy(profile = profile)
    }
    suspend fun updateProfile(request: MemberProfileUpdateRequest): Boolean =
        authenticated({ transport.updateProfile(it, request) }) { state, _ -> state.copy(profile = null) }

    suspend fun findTenant(website: String): im.hikaru.contracts.app.system.AppTenantResponse? {
        val operation = mutex.withLock {
            check(!closed)
            val target = requireNotNull(state.value.target)
            val generation = state.value.generation
            CoroutineScope(context + job).async {
                val tenant = transport.tenant(target, website)
                currentCoroutineContext().ensureActive()
                mutex.withLock { ensureCurrent(generation) }
                tenant
            }
        }
        return awaitOwned(operation)
    }

    /** A remote connection must present the generation it was opened for. Stale resources are closed immediately. */
    suspend fun ownRemote(generation: Long, resource: AccountResource) = mutex.withLock {
        if (closed || generation != state.value.generation || state.value.session == null) {
            resource.close()
            throw CancellationException("Account changed")
        }
        resources.add(resource)
    }

    suspend fun logout() {
        val (session, cleared) = mutex.withLock {
            val old = state.value.session
            old to invalidate(state.value.target)
        }
        if (!cleared) throw AccountException.Storage()
        try { if (session != null) transport.logout(session) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* Local durable clear has already succeeded. */ }
    }

    /** Closing the application keeps securely saved login but releases every client owner. */
    suspend fun shutdown() = mutex.withLock {
        if (!closed) {
            closed = true
            job.cancel()
            resources.forEach { try { it.close() } catch (_: Exception) { } }
            resources.clear()
            transport.close()
            mutableState.value = AccountState(generation = state.value.generation + 1)
        }
    }

    private suspend fun <T> awaitOwned(operation: Deferred<T>): T = try { operation.await() }
    finally { operation.cancel() }
}

private fun MemberAuthLoginResponse.toSession(target: BackendTenant): AppSession {
    val id = userId?.takeIf { it > 0 } ?: throw AccountException.InvalidResponse()
    val token = accessToken?.takeIf { it.isNotBlank() } ?: throw AccountException.InvalidResponse()
    return AppSession(MemberIdentity(target, id), token, refreshToken?.takeIf(String::isNotBlank), expiresTime)
}
