package im.hikaru.harness.client.panels

import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.harness.client.connection.SessionId
import im.hikaru.harness.client.connection.Message
import im.hikaru.harness.client.connection.BackgroundProcessLifecycle
import im.hikaru.harness.client.connection.BackgroundInputState

/** All external data and local actions belong to a host and parent conversation. */
public data class ThreadPanelScope(val hostId: HostId, val conversationId: SessionId)

/** Local views, not synthetic terminal sessions. Closing a view never kills a process. */
public enum class ThreadPanelTab { BackgroundOutput, Subagents }

/**
 * Panel sections. [name] stays the stable identity used by state, persistence and tests; [label] is
 * the Chinese heading. Codex: `subagents.panel.active` (进行中), `subagents.panel.done` (已完成).
 */
public enum class SubagentSection {
    Active,
    Done,
    ;

    public val label: String
        get() = when (this) {
            Active -> "进行中"
            Done -> "已完成"
        }

    /**
     * Section heading as Codex renders it: `subagents.panel.active` (进行中) carries no count, while
     * `subagents.panel.done` (已完成 · {count}) appends the number of finished agents. [label] stays
     * the plain name for callers that do not need the tally.
     */
    public fun heading(count: Int): String {
        require(count >= 0) { "Section count cannot be negative" }
        return when (this) {
            Active -> "进行中"
            Done -> "已完成 · $count"
        }
    }
}

public enum class PanelUnavailableReason(public val description: String) {
    TerminalServiceMissing("当前 Host 未提供会话终端或后台进程输出服务，无法读取真实输出。"),
    ChildrenServiceMissing("当前 Host 未提供子 Agent 枚举、谱系及生命周期服务，无法确认子 Agent 列表。"),
    ChildHistoryUnavailable("当前 Host 未提供该子会话的可读取历史。"),
}

/** Ready is emitted only after a successful external read; overview completeness is explicit. */
public sealed interface PanelLoad<out T> {
    public val scope: ThreadPanelScope

    public data class Unavailable(
        override val scope: ThreadPanelScope,
        val reason: PanelUnavailableReason,
        val serviceDescription: String? = null,
    ) : PanelLoad<Nothing>

    public data class Loading(override val scope: ThreadPanelScope) : PanelLoad<Nothing>

    /** Successful inventory read with no processes; a real empty output buffer is still Ready. */
    public data class Empty(override val scope: ThreadPanelScope) : PanelLoad<Nothing>

    public data class Error(override val scope: ThreadPanelScope, val message: String) : PanelLoad<Nothing> {
        init { require(message.isNotBlank()) }
    }

    public data class Ready<T>(override val scope: ThreadPanelScope, val value: T) : PanelLoad<T>
}

public data class BackgroundTerminalKey(
    val scope: ThreadPanelScope, val processId: String, val producerIncarnation: String? = null,
) {
    init { require(processId.isNotBlank()); require(producerIncarnation == null || producerIncarnation.isNotBlank()) }
}

/** Full external buffer, never a generated command log. Revision is monotonic within one key. */
public data class BackgroundTerminalOutput(
    val key: BackgroundTerminalKey,
    val buffer: String,
    val revision: Long,
    val command: String? = null,
    val truncated: Boolean = false,
    val lifecycle: BackgroundProcessLifecycle = BackgroundProcessLifecycle.Unknown,
    val cwd: String? = null,
    val inputState: BackgroundInputState = BackgroundInputState.Unsupported,
) {
    init { require(revision >= 0) }
}

/** Metadata from an existing process, or a single real output supplied by a legacy adapter. */
public data class BackgroundTerminalSummary(
    val key: BackgroundTerminalKey,
    val command: String?,
    val lifecycle: BackgroundProcessLifecycle,
    val cwd: String? = null,
    val startedAtEpochMillis: Long? = null,
    val inputState: BackgroundInputState = BackgroundInputState.Unsupported,
) {
    init { require(startedAtEpochMillis == null || startedAtEpochMillis >= 0) }
}

/** Inventory is retained even when the exact selected process has disappeared or output fails. */
public data class BackgroundTerminalSelection(
    val scope: ThreadPanelScope,
    val inventory: List<BackgroundTerminalSummary>,
    val selectedKey: BackgroundTerminalKey?,
    val output: PanelLoad<BackgroundTerminalOutput>,
) {
    init {
        require(inventory.all { it.key.scope == scope })
        require(inventory.map { it.key.processId }.distinct().size == inventory.size)
        require(selectedKey == null || selectedKey.scope == scope)
        require(output.scope == scope && output !is PanelLoad.Loading)
        if (output is PanelLoad.Ready) {
            require(output.value.key == selectedKey)
            require(inventory.any { it.key == selectedKey })
        }
        require(output !is PanelLoad.Empty || (inventory.isEmpty() && selectedKey == null))
    }
}

public data class ChildAgentKey(val scope: ThreadPanelScope, val sessionId: SessionId) {
    init { require(sessionId != scope.conversationId) { "The parent cannot be its own child" } }
}

/** Supplied by a real lifecycle projection. Session IDLE does not imply Done or Waiting. */
public enum class ChildAgentStatus {
    Active,
    Waiting,
    Done,
    Unknown,
    ;

    /**
     * Chinese display label. Codex: `subagentsPanel.working` (处理中), `subagents.status.waiting`
     * (等待中), `subagents.status.done` (已完成). Unknown has no Codex key and states the gap.
     */
    public val label: String
        get() = when (this) {
            Active -> "处理中"
            Waiting -> "等待中"
            Done -> "已完成"
            Unknown -> "状态未知"
        }
}

public enum class ChildHistoryAvailability { Available, Unavailable }

/** Ancestor path is service verified, inclusive of parent and child, and cycle free. */
public data class ChildAgentSummary(
    val key: ChildAgentKey,
    val verifiedAncestorPath: List<SessionId>,
    val status: ChildAgentStatus,
    val canInteract: Boolean,
    val historyAvailability: ChildHistoryAvailability = ChildHistoryAvailability.Unavailable,
    val displayName: String? = null,
    val objective: String? = null,
    val statusSummary: String? = null,
    val startedAtEpochMillis: Long? = null,
    val lastAssistantAtEpochMillis: Long? = null,
    val recencyAtEpochMillis: Long? = null,
    val model: String? = null,
    val reasoningEffort: String? = null,
) {
    init {
        require(verifiedAncestorPath.size >= 2)
        require(verifiedAncestorPath.first() == key.scope.conversationId)
        require(verifiedAncestorPath.last() == key.sessionId)
        require(verifiedAncestorPath.distinct().size == verifiedAncestorPath.size)
        require(listOfNotNull(startedAtEpochMillis, lastAssistantAtEpochMillis, recencyAtEpochMillis).all { it >= 0 })
    }

    public val label: String get() = displayName?.takeIf { it.isNotBlank() } ?: key.sessionId.value
    public val preview: String?
        get() = objective?.trim()?.replace(Regex("\\s+"), " ")?.takeIf { it.isNotBlank() }?.take(60)
            ?: statusSummary?.takeIf { it.isNotBlank() }
}

public enum class SubagentOverviewCompleteness { Complete, LiveOnlyIncomplete }

/** A successful children response. A live-only empty snapshot does not prove no descendants exist. */
public data class SubagentOverview(
    val scope: ThreadPanelScope,
    val children: List<ChildAgentSummary>,
    val completeness: SubagentOverviewCompleteness = SubagentOverviewCompleteness.Complete,
) {
    init {
        require(children.all { it.key.scope == scope })
        require(children.map { it.key }.distinct().size == children.size)
    }

    public fun children(section: SubagentSection): List<ChildAgentSummary> = children.filter {
        when (section) {
            SubagentSection.Active -> it.status == ChildAgentStatus.Active || it.status == ChildAgentStatus.Waiting
            SubagentSection.Done -> it.status == ChildAgentStatus.Done
        }
    }.sortedByDescending { it.recencyAtEpochMillis ?: 0L }

    public val unknown: List<ChildAgentSummary>
        get() = children.filter { it.status == ChildAgentStatus.Unknown }.sortedByDescending { it.recencyAtEpochMillis ?: 0L }
}

/** Role labels for the compatibility transcript projection, when no typed [Message] is available. */
public enum class ChildTranscriptRole {
    User,
    Assistant,
    Tool,
    System,
    ;

    public val label: String
        get() = when (this) {
            User -> "用户"
            Assistant -> "助手"
            Tool -> "工具"
            System -> "系统"
        }
}

public data class ChildTranscriptEntry(val id: String, val role: ChildTranscriptRole, val text: String) {
    init { require(id.isNotBlank()) }
}

/** Only real history entries; canInteract must come from current service authorization. */
public data class ChildAgentDetail(
    val key: ChildAgentKey,
    val entries: List<ChildTranscriptEntry>,
    val canInteract: Boolean,
    val typedMessages: List<Message>? = null,
) {
    init {
        require(entries.map { it.id }.distinct().size == entries.size)
        typedMessages?.let { messages ->
            require(messages.all { it.id.isNotBlank() }) { "Typed history message IDs must not be blank" }
            require(messages.map { it.id }.distinct().size == messages.size) { "Typed history message IDs must be unique" }
            require(messages.map { it.id } == entries.map { it.id }) { "Typed and compatibility history IDs must match in order" }
        }
    }
}

public enum class TerminalOperationKind { Input, Eof, Cancel }
public enum class TerminalOperationOutcome { Applied, Rejected, Unknown }
public data class TerminalOperationResult(val outcome: TerminalOperationOutcome, val message: String)
public data class TerminalControls(
    val key: BackgroundTerminalKey,
    val pending: Boolean = false,
    val inputBlocked: Boolean = false,
    val cancelBlocked: Boolean = false,
    val lastKind: TerminalOperationKind? = null,
    val lastOutcome: TerminalOperationOutcome? = null,
    val message: String? = null,
    /** Increment only after confirmed input delivery so the editor can clear the exact draft. */
    val inputReceipt: Long = 0,
)
