package im.hikaru.harness.client.panels

import im.hikaru.harness.client.connection.BackgroundInputResult
import im.hikaru.harness.client.connection.BackgroundControlResult
import im.hikaru.harness.client.connection.BackgroundProcessId
import im.hikaru.harness.client.connection.Connection
import im.hikaru.harness.client.connection.BackgroundOutputResult
import im.hikaru.harness.client.connection.BackgroundProcessLifecycle
import im.hikaru.harness.client.connection.BackgroundProcessesResult
import im.hikaru.harness.client.connection.ChildHistoryResult
import im.hikaru.harness.client.connection.DescendantStatus
import im.hikaru.harness.client.connection.Message
import im.hikaru.harness.client.connection.MessageContent
import im.hikaru.harness.client.connection.MessageRole
import im.hikaru.harness.client.connection.SessionDescendantsResult
import im.hikaru.harness.client.connection.SessionId
import im.hikaru.harness.client.conversation.ConversationHostScope
import kotlinx.coroutines.CancellationException

/** Only bind after the parent verifies this connection's Host and authenticated identity. */
public data class ThreadPanelsOwner(
    val connection: Connection,
    val host: ConversationHostScope,
    val parentSessionId: SessionId,
) {
    public val scope: ThreadPanelScope get() = ThreadPanelScope(host.hostId, parentSessionId)

    internal fun sameOwner(other: ThreadPanelsOwner): Boolean =
        connection === other.connection && host == other.host && parentSessionId == other.parentSessionId
}

/** Read results are successful snapshots, unavailable capabilities, or errors; never Loading. */
public interface ThreadPanelsRepository {
    public suspend fun readTerminal(owner: ThreadPanelsOwner): PanelLoad<BackgroundTerminalOutput> =
        PanelLoad.Unavailable(owner.scope, PanelUnavailableReason.TerminalServiceMissing)

    /** Compatibility adapter: a legacy real output can expose only that one process. */
    public suspend fun readTerminalSelection(
        owner: ThreadPanelsOwner, selectedKey: BackgroundTerminalKey? = null,
    ): BackgroundTerminalSelection {
        require(selectedKey == null || selectedKey.scope == owner.scope)
        val result = readTerminal(owner).terminalSnapshot(owner.scope)
        val actual = (result as? PanelLoad.Ready)?.value
        val rows = actual?.let { listOf(BackgroundTerminalSummary(
            it.key, it.command, it.lifecycle, it.cwd, inputState = it.inputState,
        )) } ?: emptyList()
        val selected = selectedKey ?: actual?.key
        val output = if ((actual != null && selected != actual.key) ||
            (selected != null && result is PanelLoad.Empty)) staleTerminal(owner.scope) else result
        return BackgroundTerminalSelection(owner.scope, rows, selected, output)
    }

    public suspend fun operateTerminal(
        owner: ThreadPanelsOwner, key: BackgroundTerminalKey, kind: TerminalOperationKind, input: String = "",
    ): TerminalOperationResult = TerminalOperationResult(TerminalOperationOutcome.Rejected, "终端控制服务不可用。")

    public suspend fun readOverview(owner: ThreadPanelsOwner): PanelLoad<SubagentOverview>
    public suspend fun readDetail(owner: ThreadPanelsOwner, child: ChildAgentKey): PanelLoad<ChildAgentDetail>
}

public class UnavailableThreadPanelsRepository : ThreadPanelsRepository {
    override suspend fun readOverview(owner: ThreadPanelsOwner): PanelLoad<SubagentOverview> =
        PanelLoad.Unavailable(owner.scope, PanelUnavailableReason.ChildrenServiceMissing)

    override suspend fun readDetail(owner: ThreadPanelsOwner, child: ChildAgentKey): PanelLoad<ChildAgentDetail> =
        PanelLoad.Unavailable(owner.scope, PanelUnavailableReason.ChildHistoryUnavailable)
}

/**
 * Injection boundary for typed SessionApi adapters. readChildHistory MUST call the service that
 * revalidates parent/child ancestry at read time, never the unrestricted history(child) method.
 * Only finite read consumers are available through this adapter.
 */
public class ReadOnlyThreadPanelsRepository(
    private val readOverview: suspend (ThreadPanelsOwner) -> PanelLoad<SubagentOverview>,
    private val readChildHistory: suspend (ThreadPanelsOwner, ChildAgentKey) -> PanelLoad<ChildAgentDetail>,
    private val readBackgroundOutput: suspend (ThreadPanelsOwner) -> PanelLoad<BackgroundTerminalOutput> = {
        PanelLoad.Unavailable(it.scope, PanelUnavailableReason.TerminalServiceMissing)
    },
) : ThreadPanelsRepository {
    override suspend fun readTerminal(owner: ThreadPanelsOwner): PanelLoad<BackgroundTerminalOutput> =
        readBackgroundOutput(owner).terminalSnapshot(owner.scope)

    override suspend fun readOverview(owner: ThreadPanelsOwner): PanelLoad<SubagentOverview> =
        readOverview.invoke(owner).readOnlySnapshot(owner.scope)

    override suspend fun readDetail(owner: ThreadPanelsOwner, child: ChildAgentKey): PanelLoad<ChildAgentDetail> {
        require(child.scope == owner.scope) { "Child belongs to another owner" }
        return readChildHistory(owner, child).readOnlySnapshot(owner.scope, child)
    }
}

internal fun PanelLoad<SubagentOverview>.readOnlySnapshot(expected: ThreadPanelScope): PanelLoad<SubagentOverview> {
    require(scope == expected) { "Overview scope mismatch" }
    return when (this) {
        is PanelLoad.Loading -> error("A read cannot complete with Loading")
        is PanelLoad.Ready -> {
            require(value.scope == expected) { "Overview payload scope mismatch" }
            PanelLoad.Ready(expected, value.copy(children = value.children.map {
                it.copy(verifiedAncestorPath = it.verifiedAncestorPath.toList(), canInteract = false)
            }))
        }
        else -> this
    }
}

/**
 * Production read adapter for the typed SessionApi descendants surface. The server owns lineage
 * verification; this adapter projects existing process data and descendants. Explicit terminal
 * actions only control the selected registered process; they never create a shell or Agent.
 */
public class ConnectionThreadPanelsRepository : ThreadPanelsRepository {
    override suspend fun readTerminal(owner: ThreadPanelsOwner): PanelLoad<BackgroundTerminalOutput> =
        readTerminalSelection(owner).output

    override suspend fun readTerminalSelection(
        owner: ThreadPanelsOwner, selectedKey: BackgroundTerminalKey?,
    ): BackgroundTerminalSelection {
        require(selectedKey == null || selectedKey.scope == owner.scope) { "Terminal selection scope mismatch" }
        val inventory = owner.connection.session.backgroundProcesses(owner.parentSessionId)
        require(inventory.parent == owner.parentSessionId) { "Background inventory parent mismatch" }
        if (inventory is BackgroundProcessesResult.Unavailable) return BackgroundTerminalSelection(
            owner.scope, emptyList(), selectedKey, PanelLoad.Unavailable(
                owner.scope, PanelUnavailableReason.TerminalServiceMissing, inventory.reason),
        )
        val rows = (inventory as BackgroundProcessesResult.Available).terminalInventory(owner.scope)
        // Unknown remains readable when it is the only evidence; it is never projected as exited.
        val key = selectedKey ?: rows.firstOrNull { it.lifecycle is BackgroundProcessLifecycle.Running }?.key
            ?: rows.firstOrNull { it.lifecycle is BackgroundProcessLifecycle.Exited }?.key ?: rows.firstOrNull()?.key
        if (key == null) return BackgroundTerminalSelection(owner.scope, rows, null, PanelLoad.Empty(owner.scope))
        val selected = rows.firstOrNull { it.key == key }
            ?: return BackgroundTerminalSelection(owner.scope, rows, key, staleTerminal(owner.scope))
        val process = BackgroundProcessId(key.processId)
        val output = try {
            owner.connection.session.readBackgroundOutput(owner.parentSessionId, process)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return BackgroundTerminalSelection(owner.scope, rows, key,
                PanelLoad.Error(owner.scope, "无法读取后台进程输出，请重试。"))
        }
        require(output.parent == owner.parentSessionId && output.process == process) {
            "Background output identity mismatch"
        }
        var verifiedRows = rows
        val load = when (output) {
            is BackgroundOutputResult.Unavailable -> PanelLoad.Unavailable(
                owner.scope, PanelUnavailableReason.TerminalServiceMissing, output.reason,
            )
            is BackgroundOutputResult.NotFound -> PanelLoad.Error(
                owner.scope, "所选后台进程已失效，请选择当前列表中的进程。",
            )
            is BackgroundOutputResult.Available -> {
                require(output.ownerSession == owner.parentSessionId) { "Background output owner mismatch" }
                // Output has no incarnation field. Recheck its inventory identity before publishing.
                val after = owner.connection.session.backgroundProcesses(owner.parentSessionId)
                require(after.parent == owner.parentSessionId) { "Background inventory parent mismatch" }
                if (after is BackgroundProcessesResult.Unavailable) return BackgroundTerminalSelection(
                    owner.scope, rows, key, PanelLoad.Error(owner.scope, "无法确认所选进程身份，请刷新重试。"),
                )
                verifiedRows = (after as BackgroundProcessesResult.Available).terminalInventory(owner.scope)
                if (verifiedRows.none { it.key == key }) return BackgroundTerminalSelection(
                    owner.scope, verifiedRows, key, staleTerminal(owner.scope),
                )
                PanelLoad.Ready(owner.scope, BackgroundTerminalOutput(
                    key = key,
                    buffer = output.buffer, revision = output.revision, command = selected.command,
                    truncated = output.truncated, lifecycle = output.lifecycle, cwd = selected.cwd, inputState = output.inputState,
                ))
            }
        }
        return BackgroundTerminalSelection(owner.scope, verifiedRows, key, load)
    }

    override suspend fun operateTerminal(
        owner: ThreadPanelsOwner, key: BackgroundTerminalKey, kind: TerminalOperationKind, input: String,
    ): TerminalOperationResult {
        require(key.scope == owner.scope) { "Terminal belongs to another owner" }
        val inventory = owner.connection.session.backgroundProcesses(owner.parentSessionId)
        require(inventory.parent == owner.parentSessionId) { "Background inventory parent mismatch" }
        if (inventory !is BackgroundProcessesResult.Available ||
            inventory.terminalInventory(owner.scope).none { it.key == key }) return TerminalOperationResult(
            TerminalOperationOutcome.Rejected, "所选后台进程已失效或无法确认身份，请刷新列表。",
        )
        val process = BackgroundProcessId(key.processId)
        return if (kind == TerminalOperationKind.Input) {
            val result = owner.connection.session.writeBackgroundInput(owner.parentSessionId, process, input)
            require(result.parent == owner.parentSessionId && result.process == process) { "Input result identity mismatch" }
            when (result) {
                is BackgroundInputResult.Accepted -> TerminalOperationResult(TerminalOperationOutcome.Applied, "已发送到进程 stdin。")
                is BackgroundInputResult.UnknownOutcome -> TerminalOperationResult(TerminalOperationOutcome.Unknown, "输入结果未知，已禁止重发。")
                is BackgroundInputResult.Rejected -> TerminalOperationResult(TerminalOperationOutcome.Rejected, result.reason)
                is BackgroundInputResult.Unavailable -> TerminalOperationResult(TerminalOperationOutcome.Rejected, result.reason)
                is BackgroundInputResult.NotFound -> TerminalOperationResult(TerminalOperationOutcome.Rejected, "进程已不在当前会话中。")
            }
        } else {
            val result = if (kind == TerminalOperationKind.Eof) owner.connection.session.closeBackgroundInput(owner.parentSessionId, process)
            else owner.connection.session.cancelBackgroundProcess(owner.parentSessionId, process)
            require(result.parent == owner.parentSessionId && result.process == process) { "Control result identity mismatch" }
            when (result) {
                is BackgroundControlResult.Applied -> TerminalOperationResult(TerminalOperationOutcome.Applied,
                    if (kind == TerminalOperationKind.Eof) "已关闭 stdin（EOF）。" else "已请求取消；刷新可查看退出状态。")
                is BackgroundControlResult.UnknownOutcome -> TerminalOperationResult(TerminalOperationOutcome.Unknown, "操作结果未知，已禁止重复操作。")
                is BackgroundControlResult.Rejected -> TerminalOperationResult(TerminalOperationOutcome.Rejected, result.reason)
                is BackgroundControlResult.Unavailable -> TerminalOperationResult(TerminalOperationOutcome.Rejected, result.reason)
                is BackgroundControlResult.NotFound -> TerminalOperationResult(TerminalOperationOutcome.Rejected, "进程已不在当前会话中。")
            }
        }
    }

    override suspend fun readOverview(owner: ThreadPanelsOwner): PanelLoad<SubagentOverview> =
        when (val result = owner.connection.session.descendants(owner.parentSessionId)) {
            is SessionDescendantsResult.Unavailable -> {
                require(result.parent == owner.parentSessionId) { "Descendants parent mismatch" }
                PanelLoad.Unavailable(owner.scope, PanelUnavailableReason.ChildrenServiceMissing)
            }
            is SessionDescendantsResult.Available -> {
                require(result.parent == owner.parentSessionId) { "Descendants parent mismatch" }
                val children = result.children.map { descendant ->
                    ChildAgentSummary(
                        key = ChildAgentKey(owner.scope, descendant.child),
                        verifiedAncestorPath = descendant.verifiedAncestorPath.toList(),
                        status = when (descendant.status) {
                            DescendantStatus.RUNNING -> ChildAgentStatus.Active
                            DescendantStatus.UNKNOWN -> ChildAgentStatus.Unknown
                        },
                        canInteract = false,
                        historyAvailability = ChildHistoryAvailability.Available,
                        objective = descendant.objective,
                        startedAtEpochMillis = descendant.startedAtEpochMillis,
                        recencyAtEpochMillis = descendant.startedAtEpochMillis,
                        model = descendant.model,
                        reasoningEffort = descendant.reasoningEffort,
                    )
                }
                PanelLoad.Ready(
                    owner.scope,
                    SubagentOverview(
                        scope = owner.scope,
                        children = children,
                        completeness = if (result.complete) SubagentOverviewCompleteness.Complete
                        else SubagentOverviewCompleteness.LiveOnlyIncomplete,
                    ),
                )
            }
        }

    override suspend fun readDetail(owner: ThreadPanelsOwner, child: ChildAgentKey): PanelLoad<ChildAgentDetail> {
        require(child.scope == owner.scope) { "Child belongs to another owner" }
        return when (val result = owner.connection.session.readChildHistory(owner.parentSessionId, child.sessionId)) {
            is ChildHistoryResult.Unavailable -> {
                require(result.parent == owner.parentSessionId && result.child == child.sessionId) {
                    "Child history identity mismatch"
                }
                PanelLoad.Unavailable(owner.scope, PanelUnavailableReason.ChildHistoryUnavailable)
            }
            is ChildHistoryResult.Available -> {
                require(result.parent == owner.parentSessionId && result.child == child.sessionId) {
                    "Child history identity mismatch"
                }
                require(result.verifiedAncestorPath.size >= 2 &&
                    result.verifiedAncestorPath.first() == owner.parentSessionId &&
                    result.verifiedAncestorPath.last() == child.sessionId &&
                    result.verifiedAncestorPath.distinct().size == result.verifiedAncestorPath.size) {
                    "Child history lineage mismatch"
                }
                val messages = result.messages.map(::childMessageSnapshot)
                PanelLoad.Ready(
                    owner.scope,
                    ChildAgentDetail(
                        key = child,
                        entries = messages.map(::transcriptEntry),
                        canInteract = false,
                        typedMessages = messages,
                    ),
                )
            }
        }
    }

    private fun transcriptEntry(message: Message): ChildTranscriptEntry =
        ChildTranscriptEntry(
            id = message.id,
            role = if (message.source is im.hikaru.harness.client.connection.ToolMessageSource) ChildTranscriptRole.Tool
            else when (message.role) {
                MessageRole.USER -> ChildTranscriptRole.User
                MessageRole.ASSISTANT -> ChildTranscriptRole.Assistant
            },
            text = message.content.joinToString(separator = "\n", transform = ::contentText),
        )

    private fun contentText(content: MessageContent): String = when (content) {
        is im.hikaru.harness.client.connection.TextContent -> content.text
        is im.hikaru.harness.client.connection.ReasoningContent -> content.text
        is im.hikaru.harness.client.connection.ToolCallContent ->
            "${content.name}: ${content.arguments}"
        is im.hikaru.harness.client.connection.ToolResultContent ->
            content.content.joinToString(separator = "\n", transform = ::contentText)
    }
}

private fun staleTerminal(scope: ThreadPanelScope): PanelLoad.Error =
    PanelLoad.Error(scope, "所选后台进程已消失或身份已变化，请选择当前列表中的进程。")

private fun BackgroundProcessesResult.Available.terminalInventory(scope: ThreadPanelScope): List<BackgroundTerminalSummary> {
    require(parent == scope.conversationId) { "Background inventory parent mismatch" }
    val rows = processes.toList()
    require(rows.all { it.ownerSession == parent }) { "Background process owner mismatch" }
    require(rows.map { it.process }.distinct().size == rows.size) { "Duplicate background process" }
    return rows.sortedWith(
        compareByDescending<im.hikaru.harness.client.connection.BackgroundProcess> { it.startedAtEpochMillis }
            .thenBy { it.process.value },
    ).map { BackgroundTerminalSummary(
        BackgroundTerminalKey(scope, it.process.value, it.producerIncarnation),
        it.command, it.lifecycle, it.cwd, it.startedAtEpochMillis, it.inputState,
    ) }
}

internal fun BackgroundTerminalSelection.selectionSnapshot(
    expected: ThreadPanelScope, requested: BackgroundTerminalKey?,
): BackgroundTerminalSelection {
    require(scope == expected) { "Terminal selection scope mismatch" }
    require(requested == null || selectedKey == requested) { "Terminal selection identity mismatch" }
    output.terminalSnapshot(expected)
    return copy(inventory = inventory.toList())
}

internal fun PanelLoad<BackgroundTerminalOutput>.terminalSnapshot(
    expected: ThreadPanelScope,
): PanelLoad<BackgroundTerminalOutput> {
    require(scope == expected) { "Terminal scope mismatch" }
    require(this !is PanelLoad.Loading) { "A read cannot complete with Loading" }
    if (this is PanelLoad.Ready) require(value.key.scope == expected) { "Terminal payload scope mismatch" }
    return this
}

internal fun PanelLoad<ChildAgentDetail>.readOnlySnapshot(
    expected: ThreadPanelScope, child: ChildAgentKey,
): PanelLoad<ChildAgentDetail> {
    require(scope == expected && child.scope == expected) { "Detail scope mismatch" }
    return when (this) {
        is PanelLoad.Loading -> error("A read cannot complete with Loading")
        is PanelLoad.Ready -> {
            require(value.key == child) { "Detail child mismatch" }
            PanelLoad.Ready(expected, value.copy(entries = value.entries.toList(), canInteract = false,
                typedMessages = value.typedMessages?.map(::childMessageSnapshot)))
        }
        else -> this
    }
}

/** Snapshot every externally owned collection; source metadata and raw content remain typed. */
private fun childMessageSnapshot(message: Message): Message = message.copy(
    content = message.content.map(::childContentSnapshot),
    source = when (val source = message.source) {
        is im.hikaru.harness.client.connection.ModelMessageSource ->
            source.copy(replayState = source.replayState?.let(::childReplaySnapshot))
        else -> source
    },
)

private fun childContentSnapshot(content: MessageContent): MessageContent = when (content) {
    is im.hikaru.harness.client.connection.ToolResultContent ->
        content.copy(content = content.content.map(::childContentSnapshot))
    else -> content
}

private fun childReplaySnapshot(value: kotlinx.serialization.json.JsonElement): kotlinx.serialization.json.JsonElement =
    when (value) {
        is kotlinx.serialization.json.JsonObject ->
            kotlinx.serialization.json.JsonObject(value.mapValues { childReplaySnapshot(it.value) })
        is kotlinx.serialization.json.JsonArray ->
            kotlinx.serialization.json.JsonArray(value.map(::childReplaySnapshot))
        else -> value
    }
