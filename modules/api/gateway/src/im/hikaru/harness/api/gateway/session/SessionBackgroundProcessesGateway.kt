package im.hikaru.harness.api.gateway.session

import im.hikaru.harness.api.gateway.ApiEndpoint
import im.hikaru.harness.session.SessionId
import im.hikaru.harness.session.api.BackgroundProcessId
import im.hikaru.harness.session.api.BackgroundProcessesResult
import im.hikaru.harness.session.api.BackgroundOutputResult
import im.hikaru.harness.session.api.BackgroundInputResult
import im.hikaru.harness.session.api.BackgroundControlResult
import kotlinx.serialization.Serializable

/** Read existing Host-owned processes; these endpoints cannot start or attach a process. */
public object SessionBackgroundProcessesEndpoint :
    ApiEndpoint<SessionId, BackgroundProcessesResult>("session.background-processes.list")

@Serializable
public data class BackgroundOutputRequest(val parent: SessionId, val process: BackgroundProcessId)

public object SessionBackgroundOutputEndpoint :
    ApiEndpoint<BackgroundOutputRequest, BackgroundOutputResult>("session.background-processes.read")

@Serializable
public data class BackgroundInputRequest(val parent: SessionId, val process: BackgroundProcessId, val input: String)

@Serializable
public data class BackgroundControlRequest(val parent: SessionId, val process: BackgroundProcessId)

/** Mutate only the exact Host-owned process. None of these operations may be retried automatically. */
public object SessionBackgroundInputEndpoint :
    ApiEndpoint<BackgroundInputRequest, BackgroundInputResult>("session.background-processes.write")

public object SessionBackgroundCloseInputEndpoint :
    ApiEndpoint<BackgroundControlRequest, BackgroundControlResult>("session.background-processes.close-input")

public object SessionBackgroundCancelEndpoint :
    ApiEndpoint<BackgroundControlRequest, BackgroundControlResult>("session.background-processes.cancel")
