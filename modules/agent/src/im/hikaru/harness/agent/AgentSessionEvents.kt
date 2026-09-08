package im.hikaru.harness.agent

import im.hikaru.harness.session.SessionEventKey

object AgentSessionEventNames {
    const val INBOX_SPLICED = "agent/inbox/spliced"
}

object AgentSessionEvents {
    val InboxSpliced =
        SessionEventKey(
            name = AgentSessionEventNames.INBOX_SPLICED,
            serializer = InboxSpliceEvent.serializer(),
            ignorable = false,
        )
}
