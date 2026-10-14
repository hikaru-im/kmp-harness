package im.hikaru.harness.desktop.connection

import im.hikaru.harness.api.gateway.ApiEndpoint
import im.hikaru.harness.api.gateway.ApiGateway
import im.hikaru.harness.api.gateway.GatewayResultCodes
import im.hikaru.harness.api.gateway.session.SessionTerminalCapabilityEndpoint
import im.hikaru.harness.api.gateway.session.SessionTerminalCloseEndpoint
import im.hikaru.harness.api.gateway.session.SessionTerminalCreateEndpoint
import im.hikaru.harness.api.gateway.session.SessionTerminalReadEndpoint
import im.hikaru.harness.api.gateway.session.SessionTerminalResizeEndpoint
import im.hikaru.harness.api.gateway.session.SessionTerminalResizeRequest
import im.hikaru.harness.api.gateway.session.SessionTerminalsEndpoint
import im.hikaru.harness.api.gateway.session.SessionTerminalWriteEndpoint
import im.hikaru.harness.api.gateway.session.SessionTerminalWriteRequest
import im.hikaru.harness.api.gateway.session.SessionTerminalInputEndpoint
import im.hikaru.harness.api.gateway.session.SessionTerminalInputRequest
import im.hikaru.harness.client.connection.TerminalInput
import im.hikaru.harness.client.connection.TerminalKeyName
import im.hikaru.harness.client.connection.TerminalModifier
import im.hikaru.harness.session.api.TerminalInput as CoreTerminalInput
import im.hikaru.harness.session.api.TerminalKeyName as CoreTerminalKeyName
import im.hikaru.harness.session.api.TerminalModifier as CoreTerminalModifier
import im.hikaru.harness.client.connection.SessionId
import im.hikaru.harness.client.connection.TerminalScope
import im.hikaru.harness.client.connection.TerminalIncarnation
import im.hikaru.harness.client.connection.TerminalSessionId
import im.hikaru.harness.client.connection.TerminalOwner
import im.hikaru.harness.client.connection.TerminalKey
import im.hikaru.harness.client.connection.TerminalSize
import im.hikaru.harness.client.connection.TerminalCreateRequest
import im.hikaru.harness.client.connection.TerminalLifecycle
import im.hikaru.harness.client.connection.TerminalInputState
import im.hikaru.harness.client.connection.TerminalMetadata
import im.hikaru.harness.client.connection.TerminalColorValue
import im.hikaru.harness.client.connection.TerminalTextAttribute
import im.hikaru.harness.client.connection.TerminalTextStyle
import im.hikaru.harness.client.connection.TerminalTextRun
import im.hikaru.harness.client.connection.TerminalLineSnapshot
import im.hikaru.harness.client.connection.TerminalCursor
import im.hikaru.harness.client.connection.TerminalSnapshot
import im.hikaru.harness.client.connection.TerminalCreateResult
import im.hikaru.harness.client.connection.TerminalDiscoveryResult
import im.hikaru.harness.client.connection.TerminalListResult
import im.hikaru.harness.client.connection.TerminalReadResult
import im.hikaru.harness.client.connection.TerminalMutationResult
import im.hikaru.harness.session.SessionId as CoreSessionId
import im.hikaru.harness.session.api.TerminalScope as CoreTerminalScope
import im.hikaru.harness.session.api.TerminalIncarnation as CoreTerminalIncarnation
import im.hikaru.harness.session.api.TerminalSessionId as CoreTerminalSessionId
import im.hikaru.harness.session.api.TerminalOwner as CoreTerminalOwner
import im.hikaru.harness.session.api.TerminalKey as CoreTerminalKey
import im.hikaru.harness.session.api.TerminalSize as CoreTerminalSize
import im.hikaru.harness.session.api.TerminalCreateRequest as CoreTerminalCreateRequest
import im.hikaru.harness.session.api.TerminalLifecycle as CoreTerminalLifecycle
import im.hikaru.harness.session.api.TerminalInputState as CoreTerminalInputState
import im.hikaru.harness.session.api.TerminalMetadata as CoreTerminalMetadata
import im.hikaru.harness.session.api.TerminalColorValue as CoreTerminalColorValue
import im.hikaru.harness.session.api.TerminalTextAttribute as CoreTerminalTextAttribute
import im.hikaru.harness.session.api.TerminalTextStyle as CoreTerminalTextStyle
import im.hikaru.harness.session.api.TerminalTextRun as CoreTerminalTextRun
import im.hikaru.harness.session.api.TerminalLineSnapshot as CoreTerminalLineSnapshot
import im.hikaru.harness.session.api.TerminalCursor as CoreTerminalCursor
import im.hikaru.harness.session.api.TerminalSnapshot as CoreTerminalSnapshot
import im.hikaru.harness.session.api.TerminalListResult as CoreTerminalListResult
import im.hikaru.harness.session.api.TerminalReadResult as CoreTerminalReadResult
import im.hikaru.harness.session.api.TerminalMutationResult as CoreTerminalMutationResult
import im.hikaru.harness.session.api.HostTerminalCreateResult as CoreHostTerminalCreateResult
import im.hikaru.harness.session.api.HostTerminalDiscoveryResult as CoreHostTerminalDiscoveryResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

private const val TERMINAL_UNAVAILABLE = "Terminal Host service is unavailable"
private const val TERMINAL_UNKNOWN = "Terminal mutation was dispatched; acknowledgement is unknown"

/** Local PTY calls use one dispatch and publish only results attributed to the complete requested identity. */
internal class LocalSessionTerminals(private val gateway: ApiGateway) {
    suspend fun terminalCapability(session: SessionId): TerminalDiscoveryResult =
        read(SessionTerminalCapabilityEndpoint, CoreSessionId(session.value),
            TerminalDiscoveryResult.Unavailable(session, TERMINAL_UNAVAILABLE)) { response ->
            response.toClient().also { require(it.session == session) }
        }

    suspend fun createTerminal(request: TerminalCreateRequest): TerminalCreateResult {
        // Copy caller collections synchronously, before entering either coroutine boundary.
        val frozen = request.toCore()
        val owner = frozen.owner.toClient()
        return mutate(SessionTerminalCreateEndpoint, frozen,
            TerminalCreateResult.Unavailable(owner, TERMINAL_UNAVAILABLE),
            { acknowledged: TerminalCreateResult? ->
                val key = when (acknowledged) {
                    is TerminalCreateResult.Created -> acknowledged.metadata.key
                    is TerminalCreateResult.UnknownOutcome -> acknowledged.key
                    else -> null
                }
                TerminalCreateResult.UnknownOutcome(owner, key, TERMINAL_UNKNOWN)
            }) { response ->
            response.toClient().also { require(it.owner == owner) }
        }
    }

    suspend fun terminals(owner: TerminalOwner): TerminalListResult {
        val frozen = owner.toCore()
        val requested = frozen.toClient()
        return read(SessionTerminalsEndpoint, frozen,
            TerminalListResult.Unavailable(requested, TERMINAL_UNAVAILABLE)) { response ->
            response.toClient().also { require(it.owner == requested) }
        }
    }

    suspend fun readTerminal(key: TerminalKey): TerminalReadResult {
        val frozen = key.toCore()
        val requested = frozen.toClient()
        return read(SessionTerminalReadEndpoint, frozen,
            TerminalReadResult.Unavailable(requested, TERMINAL_UNAVAILABLE)) { response ->
            response.toClient().also { require(it.key == requested) }
        }
    }

    suspend fun writeTerminal(key: TerminalKey, input: String): TerminalMutationResult {
        val frozen = key.toCore()
        return mutateKey(SessionTerminalWriteEndpoint, SessionTerminalWriteRequest(frozen, input), frozen.toClient())
    }

    suspend fun inputTerminal(key: TerminalKey, input: TerminalInput): TerminalMutationResult {
        val frozen = SessionTerminalInputRequest(key.toCore(), input.toCore())
        return mutateKey(SessionTerminalInputEndpoint, frozen, frozen.key.toClient())
    }

    suspend fun resizeTerminal(key: TerminalKey, size: TerminalSize): TerminalMutationResult {
        val frozen = key.toCore()
        return mutateKey(SessionTerminalResizeEndpoint, SessionTerminalResizeRequest(frozen, size.toCore()), frozen.toClient())
    }

    suspend fun closeTerminal(key: TerminalKey): TerminalMutationResult {
        val frozen = key.toCore()
        return mutateKey(SessionTerminalCloseEndpoint, frozen, frozen.toClient())
    }

    private suspend fun <Request : Any> mutateKey(
        endpoint: ApiEndpoint<Request, CoreTerminalMutationResult>,
        request: Request,
        key: TerminalKey,
    ): TerminalMutationResult =
        mutate(endpoint, request, TerminalMutationResult.Unavailable(key, TERMINAL_UNAVAILABLE),
            { _: TerminalMutationResult? -> TerminalMutationResult.UnknownOutcome(key, TERMINAL_UNKNOWN) }) { response ->
            response.toClient().also { require(it.key == key) }
        }

    private suspend fun <Request : Any, Response : Any, Result : Any> read(
        endpoint: ApiEndpoint<Request, Response>,
        request: Request,
        unavailable: Result,
        map: (Response) -> Result,
    ): Result {
        currentCoroutineContext().ensureActive()
        val response = try {
            val result = gateway.invoke(endpoint, request)
            val data = result.data
            if (!result.isSuccess || data == null) unavailable else map(data)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            unavailable
        }
        currentCoroutineContext().ensureActive()
        return response
    }

    private suspend fun <Request : Any, Response : Any, Result : Any> mutate(
        endpoint: ApiEndpoint<Request, Response>,
        request: Request,
        unavailable: Result,
        unknown: (Result?) -> Result,
        map: (Response) -> Result,
    ): Result {
        val caller = currentCoroutineContext()[Job]
        currentCoroutineContext().ensureActive()
        // Keep the original dispatcher here so its cancellation cannot discard a formed Unknown on the IO return.
        return withContext(NonCancellable) {
            withContext(Dispatchers.IO) {
                caller?.ensureActive()
                var acknowledged: Result? = null
                try {
                    // Dispatch exactly once. Any failure from this point may follow an applied operation.
                    val result = gateway.invoke(endpoint, request)
                    val data = result.data
                    val response = when {
                        result.code == GatewayResultCodes.NotFound &&
                            result.msg == "Harness API endpoint '${endpoint.method}' 未注册" -> unavailable
                        !result.isSuccess || data == null -> unknown(null)
                        else -> map(data).also { acknowledged = it }
                    }
                    if (caller?.isActive == false) unknown(acknowledged) else response
                } catch (_: Throwable) {
                    // No caller ensureActive here: cancellation after dispatch is an unknown outcome.
                    unknown(acknowledged)
                }
            }
        }
    }
}

internal fun TerminalScope.toCore(): CoreTerminalScope = CoreTerminalScope(value)

internal fun TerminalInput.toCore(): CoreTerminalInput = when (this) {
    is TerminalInput.Text -> CoreTerminalInput.Text(text)
    is TerminalInput.Paste -> CoreTerminalInput.Paste(text)
    is TerminalInput.KeyPress -> CoreTerminalInput.KeyPress(key.toCore(), modifiers.map { it.toCore() }.toSet(), character)
}

internal fun CoreTerminalInput.toClient(): TerminalInput = when (this) {
    is CoreTerminalInput.Text -> TerminalInput.Text(text)
    is CoreTerminalInput.Paste -> TerminalInput.Paste(text)
    is CoreTerminalInput.KeyPress -> TerminalInput.KeyPress(key.toClient(), modifiers.map { it.toClient() }.toSet(), character)
}

internal fun TerminalKeyName.toCore(): CoreTerminalKeyName = when (this) {
    TerminalKeyName.Enter -> CoreTerminalKeyName.Enter
    TerminalKeyName.Escape -> CoreTerminalKeyName.Escape
    TerminalKeyName.Tab -> CoreTerminalKeyName.Tab
    TerminalKeyName.Backspace -> CoreTerminalKeyName.Backspace
    TerminalKeyName.Up -> CoreTerminalKeyName.Up
    TerminalKeyName.Down -> CoreTerminalKeyName.Down
    TerminalKeyName.Left -> CoreTerminalKeyName.Left
    TerminalKeyName.Right -> CoreTerminalKeyName.Right
    TerminalKeyName.Home -> CoreTerminalKeyName.Home
    TerminalKeyName.End -> CoreTerminalKeyName.End
    TerminalKeyName.Insert -> CoreTerminalKeyName.Insert
    TerminalKeyName.Delete -> CoreTerminalKeyName.Delete
    TerminalKeyName.PageUp -> CoreTerminalKeyName.PageUp
    TerminalKeyName.PageDown -> CoreTerminalKeyName.PageDown
    TerminalKeyName.F1 -> CoreTerminalKeyName.F1
    TerminalKeyName.F2 -> CoreTerminalKeyName.F2
    TerminalKeyName.F3 -> CoreTerminalKeyName.F3
    TerminalKeyName.F4 -> CoreTerminalKeyName.F4
    TerminalKeyName.F5 -> CoreTerminalKeyName.F5
    TerminalKeyName.F6 -> CoreTerminalKeyName.F6
    TerminalKeyName.F7 -> CoreTerminalKeyName.F7
    TerminalKeyName.F8 -> CoreTerminalKeyName.F8
    TerminalKeyName.F9 -> CoreTerminalKeyName.F9
    TerminalKeyName.F10 -> CoreTerminalKeyName.F10
    TerminalKeyName.F11 -> CoreTerminalKeyName.F11
    TerminalKeyName.F12 -> CoreTerminalKeyName.F12
    TerminalKeyName.KeypadUp -> CoreTerminalKeyName.KeypadUp
    TerminalKeyName.KeypadDown -> CoreTerminalKeyName.KeypadDown
    TerminalKeyName.KeypadLeft -> CoreTerminalKeyName.KeypadLeft
    TerminalKeyName.KeypadRight -> CoreTerminalKeyName.KeypadRight
    TerminalKeyName.Character -> CoreTerminalKeyName.Character
}

internal fun CoreTerminalKeyName.toClient(): TerminalKeyName = when (this) {
    CoreTerminalKeyName.Enter -> TerminalKeyName.Enter
    CoreTerminalKeyName.Escape -> TerminalKeyName.Escape
    CoreTerminalKeyName.Tab -> TerminalKeyName.Tab
    CoreTerminalKeyName.Backspace -> TerminalKeyName.Backspace
    CoreTerminalKeyName.Up -> TerminalKeyName.Up
    CoreTerminalKeyName.Down -> TerminalKeyName.Down
    CoreTerminalKeyName.Left -> TerminalKeyName.Left
    CoreTerminalKeyName.Right -> TerminalKeyName.Right
    CoreTerminalKeyName.Home -> TerminalKeyName.Home
    CoreTerminalKeyName.End -> TerminalKeyName.End
    CoreTerminalKeyName.Insert -> TerminalKeyName.Insert
    CoreTerminalKeyName.Delete -> TerminalKeyName.Delete
    CoreTerminalKeyName.PageUp -> TerminalKeyName.PageUp
    CoreTerminalKeyName.PageDown -> TerminalKeyName.PageDown
    CoreTerminalKeyName.F1 -> TerminalKeyName.F1
    CoreTerminalKeyName.F2 -> TerminalKeyName.F2
    CoreTerminalKeyName.F3 -> TerminalKeyName.F3
    CoreTerminalKeyName.F4 -> TerminalKeyName.F4
    CoreTerminalKeyName.F5 -> TerminalKeyName.F5
    CoreTerminalKeyName.F6 -> TerminalKeyName.F6
    CoreTerminalKeyName.F7 -> TerminalKeyName.F7
    CoreTerminalKeyName.F8 -> TerminalKeyName.F8
    CoreTerminalKeyName.F9 -> TerminalKeyName.F9
    CoreTerminalKeyName.F10 -> TerminalKeyName.F10
    CoreTerminalKeyName.F11 -> TerminalKeyName.F11
    CoreTerminalKeyName.F12 -> TerminalKeyName.F12
    CoreTerminalKeyName.KeypadUp -> TerminalKeyName.KeypadUp
    CoreTerminalKeyName.KeypadDown -> TerminalKeyName.KeypadDown
    CoreTerminalKeyName.KeypadLeft -> TerminalKeyName.KeypadLeft
    CoreTerminalKeyName.KeypadRight -> TerminalKeyName.KeypadRight
    CoreTerminalKeyName.Character -> TerminalKeyName.Character
}

internal fun TerminalModifier.toCore(): CoreTerminalModifier = when (this) {
    TerminalModifier.Shift -> CoreTerminalModifier.Shift
    TerminalModifier.Control -> CoreTerminalModifier.Control
    TerminalModifier.Alt -> CoreTerminalModifier.Alt
    TerminalModifier.Meta -> CoreTerminalModifier.Meta
}

internal fun CoreTerminalModifier.toClient(): TerminalModifier = when (this) {
    CoreTerminalModifier.Shift -> TerminalModifier.Shift
    CoreTerminalModifier.Control -> TerminalModifier.Control
    CoreTerminalModifier.Alt -> TerminalModifier.Alt
    CoreTerminalModifier.Meta -> TerminalModifier.Meta
}
internal fun CoreTerminalScope.toClient(): TerminalScope = TerminalScope(value)

internal fun TerminalIncarnation.toCore(): CoreTerminalIncarnation = CoreTerminalIncarnation(value)
internal fun CoreTerminalIncarnation.toClient(): TerminalIncarnation = TerminalIncarnation(value)

internal fun TerminalSessionId.toCore(): CoreTerminalSessionId = CoreTerminalSessionId(value)
internal fun CoreTerminalSessionId.toClient(): TerminalSessionId = TerminalSessionId(value)

internal fun TerminalOwner.toCore(): CoreTerminalOwner =
    CoreTerminalOwner(scope.toCore(), CoreSessionId(session.value), incarnation.toCore())

internal fun CoreTerminalOwner.toClient(): TerminalOwner =
    TerminalOwner(scope.toClient(), SessionId(session.value), incarnation.toClient())

internal fun TerminalKey.toCore(): CoreTerminalKey = CoreTerminalKey(owner.toCore(), terminal.toCore())
internal fun CoreTerminalKey.toClient(): TerminalKey = TerminalKey(owner.toClient(), terminal.toClient())

internal fun TerminalSize.toCore(): CoreTerminalSize = CoreTerminalSize(rows, columns)
internal fun CoreTerminalSize.toClient(): TerminalSize = TerminalSize(rows, columns)

internal fun TerminalCreateRequest.toCore(): CoreTerminalCreateRequest = CoreTerminalCreateRequest(
    owner = owner.toCore(),
    argv = argv.map { it },
    environment = environment.entries.associate { (key, value) -> key to value },
    size = size.toCore(),
    cwd = cwd,
)

internal fun CoreTerminalCreateRequest.toClient(): TerminalCreateRequest = TerminalCreateRequest(
    owner = owner.toClient(),
    argv = argv.map { it },
    environment = environment.entries.associate { (key, value) -> key to value },
    size = size.toClient(),
    cwd = cwd,
)

internal fun TerminalLifecycle.toCore(): CoreTerminalLifecycle = when (this) {
    TerminalLifecycle.Running -> CoreTerminalLifecycle.Running
    is TerminalLifecycle.Exited -> CoreTerminalLifecycle.Exited(code)
    TerminalLifecycle.Unknown -> CoreTerminalLifecycle.Unknown
}

internal fun CoreTerminalLifecycle.toClient(): TerminalLifecycle = when (this) {
    CoreTerminalLifecycle.Running -> TerminalLifecycle.Running
    is CoreTerminalLifecycle.Exited -> TerminalLifecycle.Exited(code)
    CoreTerminalLifecycle.Unknown -> TerminalLifecycle.Unknown
}

internal fun TerminalInputState.toCore(): CoreTerminalInputState = when (this) {
    TerminalInputState.Open -> CoreTerminalInputState.Open
    TerminalInputState.Closed -> CoreTerminalInputState.Closed
    TerminalInputState.Unknown -> CoreTerminalInputState.Unknown
}

internal fun CoreTerminalInputState.toClient(): TerminalInputState = when (this) {
    CoreTerminalInputState.Open -> TerminalInputState.Open
    CoreTerminalInputState.Closed -> TerminalInputState.Closed
    CoreTerminalInputState.Unknown -> TerminalInputState.Unknown
}

internal fun TerminalMetadata.toCore(): CoreTerminalMetadata = CoreTerminalMetadata(
    key = key.toCore(),
    canonicalCwd = canonicalCwd,
    argv = argv.map { it },
    lifecycle = lifecycle.toCore(),
    input = input.toCore(),
)

internal fun CoreTerminalMetadata.toClient(): TerminalMetadata = TerminalMetadata(
    key = key.toClient(),
    canonicalCwd = canonicalCwd,
    argv = argv.map { it },
    lifecycle = lifecycle.toClient(),
    input = input.toClient(),
)

internal fun TerminalColorValue.toCore(): CoreTerminalColorValue = when (this) {
    TerminalColorValue.Default -> CoreTerminalColorValue.Default
    is TerminalColorValue.Indexed -> CoreTerminalColorValue.Indexed(index)
    is TerminalColorValue.Rgb -> CoreTerminalColorValue.Rgb(red, green, blue)
}

internal fun CoreTerminalColorValue.toClient(): TerminalColorValue = when (this) {
    CoreTerminalColorValue.Default -> TerminalColorValue.Default
    is CoreTerminalColorValue.Indexed -> TerminalColorValue.Indexed(index)
    is CoreTerminalColorValue.Rgb -> TerminalColorValue.Rgb(red, green, blue)
}

internal fun TerminalTextAttribute.toCore(): CoreTerminalTextAttribute = when (this) {
    TerminalTextAttribute.Bold -> CoreTerminalTextAttribute.Bold
    TerminalTextAttribute.Italic -> CoreTerminalTextAttribute.Italic
    TerminalTextAttribute.Dim -> CoreTerminalTextAttribute.Dim
    TerminalTextAttribute.Inverse -> CoreTerminalTextAttribute.Inverse
    TerminalTextAttribute.Underlined -> CoreTerminalTextAttribute.Underlined
    TerminalTextAttribute.Hidden -> CoreTerminalTextAttribute.Hidden
    TerminalTextAttribute.SlowBlink -> CoreTerminalTextAttribute.SlowBlink
    TerminalTextAttribute.RapidBlink -> CoreTerminalTextAttribute.RapidBlink
}

internal fun CoreTerminalTextAttribute.toClient(): TerminalTextAttribute = when (this) {
    CoreTerminalTextAttribute.Bold -> TerminalTextAttribute.Bold
    CoreTerminalTextAttribute.Italic -> TerminalTextAttribute.Italic
    CoreTerminalTextAttribute.Dim -> TerminalTextAttribute.Dim
    CoreTerminalTextAttribute.Inverse -> TerminalTextAttribute.Inverse
    CoreTerminalTextAttribute.Underlined -> TerminalTextAttribute.Underlined
    CoreTerminalTextAttribute.Hidden -> TerminalTextAttribute.Hidden
    CoreTerminalTextAttribute.SlowBlink -> TerminalTextAttribute.SlowBlink
    CoreTerminalTextAttribute.RapidBlink -> TerminalTextAttribute.RapidBlink
}

internal fun TerminalTextStyle.toCore(): CoreTerminalTextStyle = CoreTerminalTextStyle(
    foreground = foreground.toCore(),
    background = background.toCore(),
    attributes = attributes.map { it.toCore() }.toSet(),
)

internal fun CoreTerminalTextStyle.toClient(): TerminalTextStyle = TerminalTextStyle(
    foreground = foreground.toClient(),
    background = background.toClient(),
    attributes = attributes.map { it.toClient() }.toSet(),
)

internal fun TerminalTextRun.toCore(): CoreTerminalTextRun =
    CoreTerminalTextRun(column, span, text, style.toCore(), columnOffsets?.toList())
internal fun CoreTerminalTextRun.toClient(): TerminalTextRun =
    TerminalTextRun(column, span, text, style.toClient(), columnOffsets?.toList())

internal fun TerminalLineSnapshot.toCore(): CoreTerminalLineSnapshot =
    CoreTerminalLineSnapshot(runs.map { it.toCore() }, wrapped)

internal fun CoreTerminalLineSnapshot.toClient(): TerminalLineSnapshot =
    TerminalLineSnapshot(runs.map { it.toClient() }, wrapped)

internal fun TerminalCursor.toCore(): CoreTerminalCursor = CoreTerminalCursor(column, row, visible)
internal fun CoreTerminalCursor.toClient(): TerminalCursor = TerminalCursor(column, row, visible)

internal fun TerminalSnapshot.toCore(): CoreTerminalSnapshot = CoreTerminalSnapshot(
    metadata = metadata.toCore(),
    size = size.toCore(),
    screen = screen.map { it.toCore() },
    history = history.map { it.toCore() },
    cursor = cursor.toCore(),
    alternate = alternate,
    revision = revision,
    rawTail = rawTail,
    truncated = truncated,
)

internal fun CoreTerminalSnapshot.toClient(): TerminalSnapshot = TerminalSnapshot(
    metadata = metadata.toClient(),
    size = size.toClient(),
    screen = screen.map { it.toClient() },
    history = history.map { it.toClient() },
    cursor = cursor.toClient(),
    alternate = alternate,
    revision = revision,
    rawTail = rawTail,
    truncated = truncated,
)

internal fun TerminalDiscoveryResult.toCore(): CoreHostTerminalDiscoveryResult = when (this) {
    is TerminalDiscoveryResult.Available -> CoreHostTerminalDiscoveryResult.Available(
        CoreSessionId(session.value), owner.toCore(), canonicalCreationCwd, capacity)
    is TerminalDiscoveryResult.Rejected -> CoreHostTerminalDiscoveryResult.Rejected(CoreSessionId(session.value), reason)
    is TerminalDiscoveryResult.Unavailable -> CoreHostTerminalDiscoveryResult.Unavailable(CoreSessionId(session.value), reason)
}

internal fun CoreHostTerminalDiscoveryResult.toClient(): TerminalDiscoveryResult = when (this) {
    is CoreHostTerminalDiscoveryResult.Available -> TerminalDiscoveryResult.Available(
        SessionId(session.value), owner.toClient(), canonicalCwd, capacity)
    is CoreHostTerminalDiscoveryResult.Rejected -> TerminalDiscoveryResult.Rejected(SessionId(session.value), reason)
    is CoreHostTerminalDiscoveryResult.Unavailable -> TerminalDiscoveryResult.Unavailable(SessionId(session.value), reason)
}

internal fun TerminalCreateResult.toCore(): CoreHostTerminalCreateResult = when (this) {
    is TerminalCreateResult.Created -> CoreHostTerminalCreateResult.Created(owner.toCore(), metadata.toCore())
    is TerminalCreateResult.Rejected -> CoreHostTerminalCreateResult.Rejected(owner.toCore(), reason)
    is TerminalCreateResult.Unavailable -> CoreHostTerminalCreateResult.Unavailable(owner.toCore(), reason)
    is TerminalCreateResult.UnknownOutcome -> CoreHostTerminalCreateResult.UnknownOutcome(owner.toCore(), key?.toCore(), reason)
}

internal fun CoreHostTerminalCreateResult.toClient(): TerminalCreateResult = when (this) {
    is CoreHostTerminalCreateResult.Created -> TerminalCreateResult.Created(owner.toClient(), metadata.toClient())
    is CoreHostTerminalCreateResult.Rejected -> TerminalCreateResult.Rejected(owner.toClient(), reason)
    is CoreHostTerminalCreateResult.Unavailable -> TerminalCreateResult.Unavailable(owner.toClient(), reason)
    is CoreHostTerminalCreateResult.UnknownOutcome -> TerminalCreateResult.UnknownOutcome(owner.toClient(), key?.toClient(), reason)
}

internal fun TerminalListResult.toCore(): CoreTerminalListResult = when (this) {
    is TerminalListResult.Available -> CoreTerminalListResult.Available(owner.toCore(), terminals.map { it.toCore() })
    is TerminalListResult.Rejected -> CoreTerminalListResult.Rejected(owner.toCore(), reason)
    is TerminalListResult.Unavailable -> CoreTerminalListResult.Unavailable(owner.toCore(), reason)
}

internal fun CoreTerminalListResult.toClient(): TerminalListResult = when (this) {
    is CoreTerminalListResult.Available -> TerminalListResult.Available(owner.toClient(), terminals.map { it.toClient() })
    is CoreTerminalListResult.Rejected -> TerminalListResult.Rejected(owner.toClient(), reason)
    is CoreTerminalListResult.Unavailable -> TerminalListResult.Unavailable(owner.toClient(), reason)
}

internal fun TerminalReadResult.toCore(): CoreTerminalReadResult = when (this) {
    is TerminalReadResult.Available -> CoreTerminalReadResult.Available(key.toCore(), snapshot.toCore())
    is TerminalReadResult.NotFound -> CoreTerminalReadResult.NotFound(key.toCore())
    is TerminalReadResult.Rejected -> CoreTerminalReadResult.Rejected(key.toCore(), reason)
    is TerminalReadResult.Unavailable -> CoreTerminalReadResult.Unavailable(key.toCore(), reason)
}

internal fun CoreTerminalReadResult.toClient(): TerminalReadResult = when (this) {
    is CoreTerminalReadResult.Available -> TerminalReadResult.Available(key.toClient(), snapshot.toClient())
    is CoreTerminalReadResult.NotFound -> TerminalReadResult.NotFound(key.toClient())
    is CoreTerminalReadResult.Rejected -> TerminalReadResult.Rejected(key.toClient(), reason)
    is CoreTerminalReadResult.Unavailable -> TerminalReadResult.Unavailable(key.toClient(), reason)
}

internal fun TerminalMutationResult.toCore(): CoreTerminalMutationResult = when (this) {
    is TerminalMutationResult.Applied -> CoreTerminalMutationResult.Applied(key.toCore(), bytes, size?.toCore())
    is TerminalMutationResult.NotFound -> CoreTerminalMutationResult.NotFound(key.toCore())
    is TerminalMutationResult.Rejected -> CoreTerminalMutationResult.Rejected(key.toCore(), reason)
    is TerminalMutationResult.Unavailable -> CoreTerminalMutationResult.Unavailable(key.toCore(), reason)
    is TerminalMutationResult.UnknownOutcome -> CoreTerminalMutationResult.UnknownOutcome(key.toCore(), reason)
}

internal fun CoreTerminalMutationResult.toClient(): TerminalMutationResult = when (this) {
    is CoreTerminalMutationResult.Applied -> TerminalMutationResult.Applied(key.toClient(), bytes, size?.toClient())
    is CoreTerminalMutationResult.NotFound -> TerminalMutationResult.NotFound(key.toClient())
    is CoreTerminalMutationResult.Rejected -> TerminalMutationResult.Rejected(key.toClient(), reason)
    is CoreTerminalMutationResult.Unavailable -> TerminalMutationResult.Unavailable(key.toClient(), reason)
    is CoreTerminalMutationResult.UnknownOutcome -> TerminalMutationResult.UnknownOutcome(key.toClient(), reason)
}
