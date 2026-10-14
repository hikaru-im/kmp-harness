package im.hikaru.harness.desktop.connection

import im.hikaru.harness.client.connection.BackgroundProcess
import im.hikaru.harness.client.connection.BackgroundProcessId
import im.hikaru.harness.client.connection.BackgroundProcessLifecycle
import im.hikaru.harness.client.connection.BackgroundProcessesResult
import im.hikaru.harness.client.connection.BackgroundOutputResult
import im.hikaru.harness.client.connection.BackgroundInputState
import im.hikaru.harness.client.connection.BackgroundInputResult
import im.hikaru.harness.client.connection.BackgroundControlResult
import im.hikaru.harness.client.connection.SessionId
import im.hikaru.harness.session.api.BackgroundProcessLifecycle as CoreLifecycle
import im.hikaru.harness.session.api.BackgroundProcessesResult as CoreProcesses
import im.hikaru.harness.session.api.BackgroundOutputResult as CoreOutput
import im.hikaru.harness.session.api.BackgroundInputState as CoreInputState
import im.hikaru.harness.session.api.BackgroundInputResult as CoreInput
import im.hikaru.harness.session.api.BackgroundControlResult as CoreControl

internal fun CoreInputState.toClient(): BackgroundInputState = when (this) {
    CoreInputState.Unsupported -> BackgroundInputState.Unsupported
    CoreInputState.Open -> BackgroundInputState.Open
    CoreInputState.Closed -> BackgroundInputState.Closed
    CoreInputState.Unknown -> BackgroundInputState.Unknown
}

internal fun CoreLifecycle.toClient(): BackgroundProcessLifecycle = when (this) {
    CoreLifecycle.Running -> BackgroundProcessLifecycle.Running
    CoreLifecycle.Unknown -> BackgroundProcessLifecycle.Unknown
    is CoreLifecycle.Exited -> BackgroundProcessLifecycle.Exited(exitCode)
}

internal fun CoreProcesses.toClient(): BackgroundProcessesResult = when (this) {
    is CoreProcesses.Unavailable -> BackgroundProcessesResult.Unavailable(SessionId(parent.value), reason)
    is CoreProcesses.Available -> BackgroundProcessesResult.Available(SessionId(parent.value), processes.map {
        BackgroundProcess(BackgroundProcessId(it.process.value), SessionId(it.ownerSession.value), it.callId.value,
            it.command, it.cwd, it.startedAtEpochMillis, it.producerIncarnation, it.lifecycle.toClient(), it.inputState.toClient())
    })
}

internal fun CoreOutput.toClient(): BackgroundOutputResult = when (this) {
    is CoreOutput.Unavailable -> BackgroundOutputResult.Unavailable(SessionId(parent.value), BackgroundProcessId(process.value), reason)
    is CoreOutput.NotFound -> BackgroundOutputResult.NotFound(SessionId(parent.value), BackgroundProcessId(process.value))
    is CoreOutput.Available -> BackgroundOutputResult.Available(SessionId(parent.value), BackgroundProcessId(process.value),
        SessionId(ownerSession.value), buffer, revision, truncated, lifecycle.toClient(), inputState.toClient())
}

internal fun CoreInput.toClient(): BackgroundInputResult = when (this) {
    is CoreInput.Unavailable -> BackgroundInputResult.Unavailable(SessionId(parent.value), BackgroundProcessId(process.value), reason)
    is CoreInput.NotFound -> BackgroundInputResult.NotFound(SessionId(parent.value), BackgroundProcessId(process.value))
    is CoreInput.Accepted -> BackgroundInputResult.Accepted(SessionId(parent.value), BackgroundProcessId(process.value), bytes)
    is CoreInput.Rejected -> BackgroundInputResult.Rejected(SessionId(parent.value), BackgroundProcessId(process.value), reason)
    is CoreInput.UnknownOutcome -> BackgroundInputResult.UnknownOutcome(SessionId(parent.value), BackgroundProcessId(process.value), reason)
}

internal fun CoreControl.toClient(): BackgroundControlResult = when (this) {
    is CoreControl.Unavailable -> BackgroundControlResult.Unavailable(SessionId(parent.value), BackgroundProcessId(process.value), reason)
    is CoreControl.NotFound -> BackgroundControlResult.NotFound(SessionId(parent.value), BackgroundProcessId(process.value))
    is CoreControl.Applied -> BackgroundControlResult.Applied(SessionId(parent.value), BackgroundProcessId(process.value))
    is CoreControl.Rejected -> BackgroundControlResult.Rejected(SessionId(parent.value), BackgroundProcessId(process.value), reason)
    is CoreControl.UnknownOutcome -> BackgroundControlResult.UnknownOutcome(SessionId(parent.value), BackgroundProcessId(process.value), reason)
}
