package im.hikaru.harness.desktop.connection

import im.hikaru.harness.client.connection.ChildHistoryResult
import im.hikaru.harness.client.connection.DescendantCoverage
import im.hikaru.harness.client.connection.DescendantStatus
import im.hikaru.harness.client.connection.SessionDescendant
import im.hikaru.harness.client.connection.SessionDescendantsResult
import im.hikaru.harness.client.connection.SessionId
import im.hikaru.harness.session.api.ChildHistoryResult as CoreHistory
import im.hikaru.harness.session.api.SessionDescendantsResult as CoreDescendants

internal fun CoreDescendants.toClient(): SessionDescendantsResult = when (this) {
    is CoreDescendants.Unavailable -> SessionDescendantsResult.Unavailable(SessionId(parent.value))
    is CoreDescendants.Available -> SessionDescendantsResult.Available(
        parent = SessionId(parent.value),
        children = children.map { child ->
            SessionDescendant(
                child = SessionId(child.child.value),
                parentSession = SessionId(child.parentSession.value),
                verifiedAncestorPath = child.verifiedAncestorPath.map { SessionId(it.value) },
                status = when (child.status) {
                    im.hikaru.harness.session.api.DescendantStatus.RUNNING -> DescendantStatus.RUNNING
                    im.hikaru.harness.session.api.DescendantStatus.UNKNOWN -> DescendantStatus.UNKNOWN
                },
                startedAtEpochMillis = child.startedAtEpochMillis,
                provider = child.provider,
                model = child.model,
                reasoningEffort = child.reasoningEffort,
                objective = child.objective,
            )
        },
        coverage = when (coverage) {
            im.hikaru.harness.session.api.DescendantCoverage.LIVE_REGISTRY -> DescendantCoverage.LIVE_REGISTRY
        },
        complete = complete,
    )
}

internal fun CoreHistory.toClient(): ChildHistoryResult = when (this) {
    is CoreHistory.Unavailable -> ChildHistoryResult.Unavailable(SessionId(parent.value), SessionId(child.value))
    is CoreHistory.Available -> ChildHistoryResult.Available(
        parent = SessionId(parent.value),
        child = SessionId(child.value),
        verifiedAncestorPath = verifiedAncestorPath.map { SessionId(it.value) },
        messages = messages.map(::toClientMessage),
    )
}
