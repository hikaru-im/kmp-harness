package im.hikaru.harness.api.gateway.session

import im.hikaru.contracts.common.ApiResult
import im.hikaru.harness.api.gateway.ApiEndpoint
import im.hikaru.harness.api.gateway.ApiGateway
import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.effect.Disposable
import im.hikaru.harness.session.SessionId
import im.hikaru.harness.session.api.HostTerminalCreateResult
import im.hikaru.harness.session.api.HostTerminalDiscoveryResult
import im.hikaru.harness.session.api.SessionApi
import im.hikaru.harness.session.api.SessionApiKey
import im.hikaru.harness.session.api.TerminalColorValue
import im.hikaru.harness.session.api.TerminalCreateRequest
import im.hikaru.harness.session.api.TerminalKey
import im.hikaru.harness.session.api.TerminalInput
import im.hikaru.harness.session.api.TerminalLineSnapshot
import im.hikaru.harness.session.api.TerminalListResult
import im.hikaru.harness.session.api.TerminalMetadata
import im.hikaru.harness.session.api.TerminalMutationResult
import im.hikaru.harness.session.api.TerminalOwner
import im.hikaru.harness.session.api.TerminalReadResult
import im.hikaru.harness.session.api.TerminalSize
import im.hikaru.harness.session.api.TerminalSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

public object SessionTerminalCapabilityEndpoint :
    ApiEndpoint<SessionId, HostTerminalDiscoveryResult>("session.terminal.capability")

public object SessionTerminalCreateEndpoint :
    ApiEndpoint<TerminalCreateRequest, HostTerminalCreateResult>("session.terminal.create")

public object SessionTerminalsEndpoint :
    ApiEndpoint<TerminalOwner, TerminalListResult>("session.terminal.list")

public object SessionTerminalReadEndpoint :
    ApiEndpoint<TerminalKey, TerminalReadResult>("session.terminal.read")

@Serializable
public data class SessionTerminalWriteRequest(val key: TerminalKey, val input: String)

public object SessionTerminalWriteEndpoint :
    ApiEndpoint<SessionTerminalWriteRequest, TerminalMutationResult>("session.terminal.write")

@Serializable
public data class SessionTerminalInputRequest(val key: TerminalKey, val input: TerminalInput)

public object SessionTerminalInputEndpoint :
    ApiEndpoint<SessionTerminalInputRequest, TerminalMutationResult>("session.terminal.input")

@Serializable
public data class SessionTerminalResizeRequest(val key: TerminalKey, val size: TerminalSize)

public object SessionTerminalResizeEndpoint :
    ApiEndpoint<SessionTerminalResizeRequest, TerminalMutationResult>("session.terminal.resize")

public object SessionTerminalCloseEndpoint :
    ApiEndpoint<TerminalKey, TerminalMutationResult>("session.terminal.close")

private const val TERMINAL_UNAVAILABLE = "Terminal Session API is unavailable"
private const val TERMINAL_UNKNOWN = "Terminal mutation was dispatched; acknowledgement is unknown"

/** Each registration is root-owned; the group also fences handlers already in flight. */
internal suspend fun ApiGateway.registerSessionTerminals(owner: Context): Disposable {
    val binding = TerminalGatewayBinding(owner)
    val registrations = mutableListOf<Disposable>()
    val cleanup = Disposable {
        withContext(NonCancellable) {
            binding.stop()
            var failure: Throwable? = null
            for (registration in registrations.asReversed()) {
                try {
                    registration.dispose()
                } catch (error: Throwable) {
                    if (failure == null) failure = error
                    else if (failure !== error) failure?.addSuppressed(error)
                }
            }
            failure?.let { throw it }
        }
    }
    try {
        registrations += register(owner, SessionTerminalCapabilityEndpoint) { session ->
            binding.read<HostTerminalDiscoveryResult>(HostTerminalDiscoveryResult.Unavailable(session, TERMINAL_UNAVAILABLE),
                { it.terminalCapability(session) }, { it.snapshot(session) })
        }
        registrations += register(owner, SessionTerminalCreateEndpoint) { request ->
            // Freeze mutable caller collections before a dispatcher hop or service suspension.
            val frozen = request.copy(owner = request.owner.copy(), argv = request.argv.toList(),
                environment = request.environment.toMap(), size = request.size.copy())
            binding.mutate<HostTerminalCreateResult>(
                HostTerminalCreateResult.Unavailable(frozen.owner, TERMINAL_UNAVAILABLE),
                { acknowledged ->
                    val key = when (acknowledged) {
                        is HostTerminalCreateResult.Created -> acknowledged.metadata.key
                        is HostTerminalCreateResult.UnknownOutcome -> acknowledged.key
                        else -> null
                    }
                    HostTerminalCreateResult.UnknownOutcome(frozen.owner, key, TERMINAL_UNKNOWN)
                },
                { it.createTerminal(frozen) }, { it.snapshot(frozen.owner) },
            )
        }
        registrations += register(owner, SessionTerminalsEndpoint) { requested ->
            binding.read<TerminalListResult>(TerminalListResult.Unavailable(requested, TERMINAL_UNAVAILABLE),
                { it.terminals(requested) }, { it.snapshot(requested) })
        }
        registrations += register(owner, SessionTerminalReadEndpoint) { key ->
            binding.read<TerminalReadResult>(TerminalReadResult.Unavailable(key, TERMINAL_UNAVAILABLE),
                { it.readTerminal(key) }, { it.snapshot(key) })
        }
        registrations += register(owner, SessionTerminalWriteEndpoint) { request ->
            binding.mutate<TerminalMutationResult>(
                TerminalMutationResult.Unavailable(request.key, TERMINAL_UNAVAILABLE),
                { TerminalMutationResult.UnknownOutcome(request.key, TERMINAL_UNKNOWN) },
                { it.writeTerminal(request.key, request.input) }, { it.snapshot(request.key) },
            )
        }
        registrations += register(owner, SessionTerminalInputEndpoint) { request ->
            val input = when (val source = request.input) {
                is TerminalInput.Text -> source.copy()
                is TerminalInput.Paste -> source.copy()
                is TerminalInput.KeyPress -> TerminalInput.KeyPress(source.key, source.modifiers.toSet(), source.character)
            }
            val frozen = SessionTerminalInputRequest(request.key.snapshot(), input)
            binding.mutate<TerminalMutationResult>(
                TerminalMutationResult.Unavailable(frozen.key, TERMINAL_UNAVAILABLE),
                { TerminalMutationResult.UnknownOutcome(frozen.key, TERMINAL_UNKNOWN) },
                { it.inputTerminal(frozen.key, frozen.input) }, { it.snapshot(frozen.key) },
            )
        }
        registrations += register(owner, SessionTerminalResizeEndpoint) { request ->
            binding.mutate<TerminalMutationResult>(
                TerminalMutationResult.Unavailable(request.key, TERMINAL_UNAVAILABLE),
                { TerminalMutationResult.UnknownOutcome(request.key, TERMINAL_UNKNOWN) },
                { it.resizeTerminal(request.key, request.size.copy()) }, { it.snapshot(request.key) },
            )
        }
        registrations += register(owner, SessionTerminalCloseEndpoint) { key ->
            binding.mutate<TerminalMutationResult>(
                TerminalMutationResult.Unavailable(key, TERMINAL_UNAVAILABLE),
                { TerminalMutationResult.UnknownOutcome(key, TERMINAL_UNKNOWN) },
                { it.closeTerminal(key) }, { it.snapshot(key) },
            )
        }
        return owner.effect(cleanup)
    } catch (error: Throwable) {
        try {
            cleanup.dispose()
        } catch (cleanupError: Throwable) {
            if (cleanupError !== error) error.addSuppressed(cleanupError)
        }
        throw error
    }
}

private class TerminalGatewayBinding(private val owner: Context) {
    private val mutex = Mutex()
    private var active = true

    suspend fun stop() {
        mutex.withLock { active = false }
    }

    private suspend fun api(): SessionApi? = mutex.withLock {
        if (!active) null else try {
            owner.get(SessionApiKey)
        } catch (_: IllegalStateException) {
            // Context.get also rejects a Context while it is disposing.
            null
        }
    }

    private suspend fun isCurrent(expected: SessionApi): Boolean = api() === expected

    suspend fun <T : Any> read(
        unavailable: T,
        operation: suspend (SessionApi) -> T,
        snapshot: (T) -> T,
    ): ApiResult<T> {
        currentCoroutineContext().ensureActive()
        val result = try {
            val service = api()
            if (service == null) unavailable else {
                val response = snapshot(operation(service))
                if (isCurrent(service)) response else unavailable
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            unavailable
        }
        currentCoroutineContext().ensureActive()
        return success(result)
    }

    suspend fun <T : Any> mutate(
        unavailable: T,
        unknown: (T?) -> T,
        operation: suspend (SessionApi) -> T,
        snapshot: (T) -> T,
    ): ApiResult<T> {
        val caller = currentCoroutineContext()[Job]
        currentCoroutineContext().ensureActive()
        // Keep the caller dispatcher on the outer boundary: returning from IO into
        // the cancelled caller must not replace a formed Unknown with cancellation.
        return withContext(NonCancellable) {
            withContext(Dispatchers.IO) dispatch@{
                caller?.ensureActive()
                val service = api()
                caller?.ensureActive()
                if (service == null || !isCurrent(service)) {
                    caller?.ensureActive()
                    return@dispatch success(unavailable)
                }
                caller?.ensureActive()
                var acknowledged: T? = null
                val result = try {
                    // Exactly one dispatch. Everything from here may have had effects.
                    val response = snapshot(operation(service))
                    acknowledged = response
                    val current = isCurrent(service)
                    if (caller?.isActive == false || !current) unknown(response) else response
                } catch (_: Throwable) {
                    unknown(acknowledged)
                }
                success(result)
            }
        }
    }
}

private fun <T : Any> success(result: T): ApiResult<T> = ApiResult(ApiResult.SUCCESS_CODE, "", result)

private fun HostTerminalDiscoveryResult.snapshot(requested: SessionId): HostTerminalDiscoveryResult {
    require(session == requested)
    return when (this) {
        is HostTerminalDiscoveryResult.Available -> {
            require(owner.session == requested)
            copy(owner = owner.copy())
        }
        is HostTerminalDiscoveryResult.Rejected -> copy()
        is HostTerminalDiscoveryResult.Unavailable -> copy()
    }
}

private fun HostTerminalCreateResult.snapshot(requested: TerminalOwner): HostTerminalCreateResult {
    require(owner == requested)
    return when (this) {
        is HostTerminalCreateResult.Created -> {
            require(metadata.key.owner == requested)
            copy(owner = owner.copy(), metadata = metadata.snapshot())
        }
        is HostTerminalCreateResult.UnknownOutcome -> {
            val terminalKey = key
            require(terminalKey == null || terminalKey.owner == requested)
            copy(owner = owner.copy(), key = terminalKey?.snapshot())
        }
        is HostTerminalCreateResult.Rejected -> copy(owner = owner.copy())
        is HostTerminalCreateResult.Unavailable -> copy(owner = owner.copy())
    }
}

private fun TerminalListResult.snapshot(requested: TerminalOwner): TerminalListResult {
    require(owner == requested)
    return when (this) {
        is TerminalListResult.Available -> {
            val rows = terminals.toList()
            require(rows.all { it.key.owner == requested } && rows.map { it.key }.distinct().size == rows.size)
            copy(owner = owner.copy(), terminals = rows.map { it.snapshot() })
        }
        is TerminalListResult.Rejected -> copy(owner = owner.copy())
        is TerminalListResult.Unavailable -> copy(owner = owner.copy())
    }
}

private fun TerminalReadResult.snapshot(requested: TerminalKey): TerminalReadResult {
    require(key == requested)
    return when (this) {
        is TerminalReadResult.Available -> {
            require(snapshot.metadata.key == requested)
            copy(key = key.snapshot(), snapshot = snapshot.snapshot())
        }
        is TerminalReadResult.NotFound -> copy(key = key.snapshot())
        is TerminalReadResult.Rejected -> copy(key = key.snapshot())
        is TerminalReadResult.Unavailable -> copy(key = key.snapshot())
    }
}

private fun TerminalMutationResult.snapshot(requested: TerminalKey): TerminalMutationResult {
    require(key == requested)
    return when (this) {
        is TerminalMutationResult.Applied -> copy(key = key.snapshot(), size = size?.copy())
        is TerminalMutationResult.NotFound -> copy(key = key.snapshot())
        is TerminalMutationResult.Rejected -> copy(key = key.snapshot())
        is TerminalMutationResult.Unavailable -> copy(key = key.snapshot())
        is TerminalMutationResult.UnknownOutcome -> copy(key = key.snapshot())
    }
}

private fun TerminalKey.snapshot(): TerminalKey = copy(owner = owner.copy())

private fun TerminalMetadata.snapshot(): TerminalMetadata = copy(key = key.snapshot(), argv = argv.toList())

private fun TerminalSnapshot.snapshot(): TerminalSnapshot = copy(
    metadata = metadata.snapshot(), size = size.copy(), cursor = cursor.copy(),
    screen = screen.map { it.snapshot() }, history = history.map { it.snapshot() },
)

private fun TerminalLineSnapshot.snapshot(): TerminalLineSnapshot = copy(runs = runs.map { run ->
    run.copy(style = run.style.copy(
        foreground = run.style.foreground.snapshot(), background = run.style.background.snapshot(),
        attributes = run.style.attributes.toSet(),
    ))
})

private fun TerminalColorValue.snapshot(): TerminalColorValue = when (this) {
    TerminalColorValue.Default -> this
    is TerminalColorValue.Indexed -> copy()
    is TerminalColorValue.Rgb -> copy()
}
