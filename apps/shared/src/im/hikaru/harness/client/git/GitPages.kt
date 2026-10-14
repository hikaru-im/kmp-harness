package im.hikaru.harness.client.git

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import im.hikaru.harness.client.connection.SessionSummary
import im.hikaru.harness.client.page.*
import im.hikaru.harness.client.settings.*
import im.hikaru.harness.ui.foundation.*

/** Repository reads and explicit optional local checkout. Parent owns the controller and Host binding. */
@Composable
public fun SettingsGitPage(controller: GitController, modifier: Modifier = Modifier) {
    val state by controller.state.collectAsState()
    Box(modifier) {
        SettingsFormFrame("Git", "git-page") {
            RepositoryControls(controller, state)
            SettingsFormSection("分支与变更") {
                GitPageCard {
                    ReadResult(state.status, "选择仓库后读取状态。") { status ->
                        GitPageValue(when {
                            status.unborn -> "${status.branch ?: "HEAD"} · 尚无提交"
                            status.detached -> "Detached HEAD · ${status.headOid ?: "提交不可用"}"
                            else -> status.branch ?: "HEAD 不可用"
                        }, "git-current-branch")
                        HarnessText("已暂存 ${status.stagedCount} · 未暂存 ${status.unstagedCount} · 未跟踪 ${status.untrackedCount}")
                        if (status.changes.isEmpty()) HarnessText("工作树干净。", Modifier.testTag("git-clean"))
                        GitPageList("git-changes-list") {
                            status.changes.forEach { change ->
                                GitPageValue("${change.indexStatus}${change.worktreeStatus}  " +
                                    (change.originalPath?.let { "$it → " } ?: "") + change.path,
                                    "git-change")
                            }
                        }
                    }
                    ReadResult(state.branches, "尚未读取分支。") { branches ->
                        if (branches.isEmpty()) HarnessText("没有分支引用。")
                        GitPageList("git-branches-list") {
                            branches.forEach { branch ->
                                GitPageValue((if (branch.current) "* " else "  ") + branch.ref, "git-branch")
                            }
                        }
                    }
                }
            }
            BranchCheckoutForm(controller, state)
            BranchCreationForm(controller, state)
            CommitForm(controller, state)
            SettingsFormSection("Diff") {
                GitPageSelect(state.diffRequest.source, GitDiffSource.entries.toList(),
                    { when (it) {
                        GitDiffSource.WorkingTree -> "未暂存（工作树与暂存区）"
                        GitDiffSource.Index -> "已暂存（暂存区与 HEAD）"
                        GitDiffSource.BaseBranch -> "基准分支与工作树"
                    } }, state.pageReady, "git-diff-source") { source ->
                    controller.selectDiff(GitDiffRequest(source, state.diffRequest.baseRef))
                }
                if (state.diffRequest.source == GitDiffSource.BaseBranch) {
                    val branches = (state.branches as? GitRead.Ready<List<GitBranch>>)?.value.orEmpty()
                    val refs = branches.map { it.ref }
                    if (state.branches !is GitRead.Ready) ReadResult(state.branches, "尚未读取基准分支。") {}
                    else if (refs.isEmpty()) HarnessText("没有可用基准分支引用。")
                    else GitPageSelect(state.diffRequest.baseRef, listOf<String?>(null) + refs,
                        { it ?: "选择基准分支" }, state.pageReady, "git-diff-base") { ref ->
                        if (ref != null) controller.selectDiff(GitDiffRequest(GitDiffSource.BaseBranch, ref))
                    }
                }
                HarnessText("Diff 不包含未跟踪文件；HEAD 变化会使读取失败。文件与暂存区并非原子快照。",
                    color = HarnessTokens.Colors.TextMuted)
                val readyDiff = (state.diff as? GitRead.Ready<GitDiffSnapshot>)?.value
                // Keep the last completed patch visible while the controller's existing Loading state
                // disables actions during a refresh. A failed read still goes through ReadResult.
                var lastReadyDiff by remember(state.binding, state.selectedCwd, state.diffRequest) {
                    mutableStateOf(readyDiff)
                }
                SideEffect { if (readyDiff != null && lastReadyDiff != readyDiff) lastReadyDiff = readyDiff }
                val diffForDisplay = readyDiff ?: lastReadyDiff.takeIf { state.diff is GitRead.Loading }
                if (diffForDisplay != null) {
                    val diff = diffForDisplay
                    if (diff.patch.isEmpty()) HarnessText("所选比较没有差异。", Modifier.testTag("git-diff-empty"))
                    else GitPatch(diff.patch, state.previewFiles, { controller.refreshPreviewFiles(it) },
                        hideWhitespace = state.diffRequest.hideWhitespace,
                        onToggleHideWhitespace = { controller.selectDiff(state.diffRequest.copy(hideWhitespace = it)) },
                        loadFullFiles = state.diffRequest.loadFullFiles,
                        onToggleLoadFullFiles = { controller.selectDiff(state.diffRequest.copy(loadFullFiles = it)) },
                        onRefresh = controller::refresh,
                        refreshDisabled = !state.pageCanRead,
                        copyGitApplyCommandDisabled = !state.pageCanRead)
                } else {
                    ReadResult(state.diff, "尚未读取 Diff。") { diff ->
                        if (diff.patch.isEmpty()) HarnessText("所选比较没有差异。", Modifier.testTag("git-diff-empty"))
                        else GitPatch(diff.patch, state.previewFiles, { controller.refreshPreviewFiles(it) },
                            hideWhitespace = state.diffRequest.hideWhitespace,
                            onToggleHideWhitespace = { controller.selectDiff(state.diffRequest.copy(hideWhitespace = it)) },
                            loadFullFiles = state.diffRequest.loadFullFiles,
                            onToggleLoadFullFiles = { controller.selectDiff(state.diffRequest.copy(loadFullFiles = it)) },
                            onRefresh = controller::refresh,
                            refreshDisabled = !state.pageCanRead,
                            copyGitApplyCommandDisabled = !state.pageCanRead)
                    }
                }
            }
        }
    }
}

@Composable
public fun WorktreesPage(
    controller: GitController,
    modifier: Modifier = Modifier,
    managedController: ManagedWorktreeController? = null,
    worktreeSettings: WorktreeSettingsState? = null,
    onSaveWorktreeSettings: (WorktreeSettings) -> Unit = {},
    /**
     * The current Host's loaded sessions. Used only to list the "known conversations" of a worktree
     * (`settings.worktrees.row.conversations`) by matching each session's cwd to the worktree dir; no
     * new read and no repository write.
     */
    sessions: List<SessionSummary> = emptyList(),
    onOpenSession: (SessionSummary) -> Unit = {},
    /**
     * `settings.worktrees.row.newChat` (在此工作树中新建聊天): start a new conversation whose working
     * directory is this worktree. This only binds a pending directory and navigates; no session is
     * created and no repository is written until the user sends the first message from Home.
     */
    onNewChatInWorktree: (String) -> Unit = {},
) {
    val state by controller.state.collectAsState()
    val currentRepository = (state.repository as? GitRead.Ready<GitRepository>)?.value
    // Render fence precedes the asynchronous bind: never paint a previous owner's inventory.
    val managedState = managedController?.let { managed ->
        val observed by managed.state.collectAsState()
        if (observed.repository == currentRepository) observed else ManagedWorktreeState(repository = currentRepository)
    }
    LaunchedEffect(managedController, currentRepository) { managedController?.bind(currentRepository) }
    Box(modifier) {
        SettingsFormFrame("Worktrees", "worktrees-page") {
            RepositoryControls(controller, state)
            WorktreeRootSection(worktreeSettings, onSaveWorktreeSettings)
            WorktreeEnvironmentSection(worktreeSettings, onSaveWorktreeSettings)
            WorktreeCreationForm(controller, state, worktreeSettings?.settings?.configuredRoot.orEmpty())
            ManagedWorktreeSection(managedController, managedState, state.pageReady, sessions, onOpenSession,
                onNewChatInWorktree)
            SettingsFormSection("本地 Git 工作树") {
                HarnessText("从 Git 读取实际工作树列表，选择后读取对应目录。此列表不证明 Managed 登记、归属或保护。",
                    color = HarnessTokens.Colors.TextMuted)
                ReadResult(state.worktrees, "选择仓库后读取工作树。") { worktrees ->
                    if (worktrees.isEmpty()) HarnessText("没有工作树。", Modifier.testTag("git-worktrees-empty"))
                    GitPageList("git-worktrees-list") {
                        worktrees.forEach { worktree ->
                            GitPageCard {
                                GitPageValue(worktree.path, "git-worktree-path")
                                GitPageValue(when {
                                    worktree.bare -> "Bare repository"
                                    worktree.detached -> "Detached HEAD · ${worktree.headOid ?: "提交不可用"}"
                                    else -> worktree.branch ?: "分支不可用"
                                }, "git-worktree-branch")
                                if (worktree.locked) HarnessText("已锁定" + (worktree.lockReason?.let { "：$it" } ?: ""))
                                if (worktree.prunable) HarnessText("Git 标记为 prunable（清理不可用）" + (worktree.pruneReason?.let { "：$it" } ?: ""))
                                WorktreeConversations(worktree.path, sessions, onOpenSession)
                                HarnessButton("在此工作树中新建聊天", { onNewChatInWorktree(worktree.path) },
                                    enabled = state.pageReady && !worktree.bare && !worktree.prunable,
                                    testTag = "git-worktree-new-chat")
                                HarnessButton("读取此工作树", { controller.selectWorktree(worktree.path) },
                                    enabled = state.pageReady && !worktree.bare && !worktree.prunable,
                                    testTag = "git-worktree-select")
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Codex `settings.worktrees.row.conversations` (已知对话): the current Host's already-loaded sessions
 * whose cwd is exactly this worktree directory. Read-only — it matches the loaded session list and
 * opens an existing history; it creates no session and writes nothing. No session title exists in
 * `SessionSummary`, so the row leads with the session id, matching the Recent page.
 */
@Composable
private fun WorktreeConversations(root: String, sessions: List<SessionSummary>, onOpenSession: (SessionSummary) -> Unit) {
    val dir = root.trimEnd('/')
    val matching = remember(root, sessions) { worktreeConversations(root, sessions) }
    Column(Modifier.fillMaxWidth().testTag("worktree-conversations-$dir"),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        HarnessText("已知对话", style = MaterialTheme.typography.bodySmall, color = HarnessTokens.Colors.TextMuted)
        if (matching.isEmpty()) HarnessText("未找到关联到此工作树的对话",
            Modifier.testTag("worktree-conversations-empty-$dir"), color = HarnessTokens.Colors.TextMuted)
        else matching.forEach { session -> key(session.id) {
            HarnessRow(session.id.value, description = session.cwd,
                onClick = { onOpenSession(session) }, testTag = "worktree-conversation-${session.id.value}")
        } }
    }
}

@Composable
private fun ManagedWorktreeSection(
    controller: ManagedWorktreeController?,
    state: ManagedWorktreeState?,
    pageReady: Boolean,
    sessions: List<SessionSummary>,
    onOpenSession: (SessionSummary) -> Unit,
    onNewChatInWorktree: (String) -> Unit,
) {
    var pendingRemoval by remember(controller) { mutableStateOf<ManagedWorktreeRemovalPlan?>(null) }
    SettingsFormSection("Managed 工作树归属与保护") {
        HarnessText("Git 列表包含未登记工作树；只读刷新不会自动认领。", color = HarnessTokens.Colors.TextMuted)
        if (controller == null || state == null) {
            HarnessText("Managed 登记服务不可用。", Modifier.testTag("managed-unavailable"))
            HarnessText("关联会话未知；Pin 与归档服务不可用。删除与清理不可用。")
        } else {
            HarnessButton("刷新归属与保护", controller::refresh,
                enabled = pageReady && state.canRefresh && state.phase != ManagedWorktreePhase.Loading,
                testTag = "managed-refresh")
            when (state.phase) {
                ManagedWorktreePhase.Unbound -> HarnessText("选择仓库后读取 managed 登记。", Modifier.testTag("managed-unbound"))
                ManagedWorktreePhase.Loading -> HarnessText("正在读取 managed 登记…", Modifier.testTag("managed-loading"))
                ManagedWorktreePhase.Error -> HarnessText(state.message ?: "Managed 读取失败。",
                    Modifier.testTag("managed-error"), color = HarnessTokens.Colors.Danger)
                ManagedWorktreePhase.Unavailable -> HarnessText(state.message ?: "Managed 登记服务不可用。",
                    Modifier.testTag("managed-unavailable"))
                ManagedWorktreePhase.Ready -> Unit
            }
            if (state.stale) HarnessText("上次观察已过期；当前归属与保护尚未确认。", Modifier.testTag("managed-stale"))
            state.inventory?.let { inventory ->
                val verified = state.phase == ManagedWorktreePhase.Ready && !state.stale
                HarnessText(if (verified) "已核验 Managed ${inventory.registeredCount} · 其他归属仍需核验"
                    else "上次观察 ${inventory.entries.size} 条 · 当前核验数量未知", Modifier.testTag("managed-count"))
                if (verified && inventory.entries.isEmpty()) HarnessText("完整读取成功，没有工作树或登记意图。",
                    Modifier.testTag("managed-empty"))
                GitPageList("managed-inventory-list") {
                    inventory.entries.forEach { entry ->
                        GitPageCard {
                            GitPageValue(entry.root, "managed-worktree-path")
                            HarnessText(when (entry.ownership) {
                                ManagedOwnership.Managed -> if (verified) "Managed 归属已核验" else "上次归属为 Managed · 当前未核验"
                                ManagedOwnership.UnknownOwnership -> if (verified) "归属未知" else "上次归属未知 · 当前未核验"
                                ManagedOwnership.FingerprintMismatch -> if (verified) "登记与当前 Git 指纹不匹配" else "上次登记与 Git 指纹不匹配 · 当前未核验"
                                ManagedOwnership.Missing -> if (verified) "当前 Git 列表未找到此登记路径" else "上次 Git 列表未找到此登记路径 · 当前未核验"
                            }, Modifier.testTag("managed-ownership"),
                                color = if (verified) HarnessTokens.Colors.Text else HarnessTokens.Colors.TextMuted)
                            entry.worktree?.let { actual ->
                                GitPageValue(actual.branch ?: if (actual.detached) "Detached HEAD" else "当前分支不可用", "managed-branch", muted = !verified)
                                GitPageValue("${if (verified) "当前" else "上次"} HEAD：${actual.headOid ?: "不可用"}", "managed-head", muted = !verified)
                            } ?: HarnessText("当前分支与 HEAD 不可用。")
                            WorktreeConversations(entry.root, sessions, onOpenSession)
                            // `settings.worktrees.row.newChat` for a Managed row: only a fully re-verified
                            // worktree directory whose Git entry is usable can seed a new chat. Verified
                            // ownership is required so a stale/mismatched registration never seeds a chat.
                            HarnessButton("在此工作树中新建聊天", { onNewChatInWorktree(entry.root) },
                                enabled = verified && pageReady && entry.worktree?.let { !it.bare && !it.prunable } == true,
                                testTag = "managed-worktree-new-chat")
                            entry.registration?.let { HarnessText("${if (verified) "登记状态" else "上次登记状态"}：$it",
                                Modifier.testTag("managed-registration")) }
                            entry.registrationWarning?.let { HarnessText(if (verified) it else "上次警告：$it", Modifier.testTag("managed-registration-warning"),
                                color = HarnessTokens.Colors.Danger) }
                            val sessionObservation = entry.sessionObservation
                            if (sessionObservation == null) ProtectionLabel(entry.protection.sessions, "managed-sessions")
                            else {
                                SessionObservationLabel(sessionObservation, verified, entry.root, state, controller, pageReady)
                                HarnessText("会话保护仍未完整核验；本次观察不提供删除许可。", Modifier.testTag("managed-sessions"),
                                    color = HarnessTokens.Colors.TextMuted)
                            }
                            when (val pins = inventory.pinRead) {
                                is ManagedWorktreePinRead.Available -> HarnessText(
                                    "自有聊天固定存储已读取；完整 Pin 保护仍未知，不提供删除许可。",
                                    Modifier.testTag("managed-pins"), color = HarnessTokens.Colors.TextMuted)
                                is ManagedWorktreePinRead.Unknown -> HarnessText(pins.reason, Modifier.testTag("managed-pins"))
                                else -> ProtectionLabel(entry.protection.pins, "managed-pins")
                            }
                            ProtectionLabel(entry.protection.archive, "managed-archive")
                            ProtectionLabel(entry.protection.other, "managed-other-protection")
                            // The destructive control is the card's last element, after every protection
                            // label, so a rejected removal never hides which re-verified check stopped it.
                            when (val removal = entry.removal) {
                                null -> Unit
                                is ManagedWorktreeRemovalRead.Available -> {
                                    val eligible = verified && state.canRemove(entry.root)
                                    HarnessText(
                                        if (eligible) "移除保护已完整核验：可以删除此工作树。"
                                        else "删除被拒绝：" + (removal.plan.blocks.firstOrNull()?.detail ?: "仍有保护项未通过。"),
                                        Modifier.testTag("managed-removal-status"),
                                        color = if (eligible) HarnessTokens.Colors.Text else HarnessTokens.Colors.TextMuted,
                                    )
                                    HarnessButton("删除工作树", { pendingRemoval = removal.plan },
                                        enabled = eligible && pageReady,
                                        variant = HarnessButtonVariant.Secondary,
                                        testTag = "managed-remove")
                                }
                                is ManagedWorktreeRemovalRead.Unknown -> HarnessText("删除与清理不可用：" + removal.reason,
                                    Modifier.testTag("managed-removal-status"), color = HarnessTokens.Colors.TextMuted)
                                is ManagedWorktreeRemovalRead.Unavailable -> HarnessText("删除与清理不可用：" + removal.reason,
                                    Modifier.testTag("managed-removal-status"), color = HarnessTokens.Colors.TextMuted)
                            }
                        }
                    }
                }
                val receipt = state.pinReceipt?.takeIf { receipt -> receipt.request.owner == inventory.owner &&
                    receipt.request.repository == inventory.repository && inventory.entries.any { entry ->
                        val observation = entry.sessionObservation
                        entry.root == receipt.request.root && observation != null && observation.owner == inventory.owner &&
                            observation.status == ManagedSessionObservationStatus.Observed && observation.issues.isEmpty() &&
                            observation.sessions.any { session -> session.pinKey == receipt.request.key }
                    } }
                receipt?.let {
                    HarnessText(when (it) {
                        is ManagedWorktreePinReceipt.Applied -> it.durabilityWarning ?: "聊天固定已提交；显示状态来自磁盘刷新。"
                        is ManagedWorktreePinReceipt.Rejected -> it.reason
                        is ManagedWorktreePinReceipt.Unknown -> it.reason
                    }, Modifier.testTag("managed-pin-receipt"))
                }
                val archiveReceipt = state.archiveReceipt?.takeIf { receipt -> receipt.request.owner == inventory.owner &&
                    receipt.request.repository == inventory.repository && inventory.entries.any { entry ->
                        val observation = entry.sessionObservation
                        entry.root == receipt.request.root && observation != null && observation.owner == inventory.owner &&
                            observation.status == ManagedSessionObservationStatus.Observed && observation.issues.isEmpty() &&
                            observation.sessions.any { session ->
                                session.pinKey?.let { ChatArchiveKey(it.id, it.headerFingerprint) } == receipt.request.key
                            }
                    } }
                archiveReceipt?.let {
                    HarnessText(when (it) {
                        is ManagedWorktreeChatArchiveReceipt.Applied -> it.durabilityWarning ?: when (val restore = it.restore) {
                            null -> "聊天归档已提交；显示状态来自磁盘刷新。"
                            ManagedWorktreeChatRestore.Restored -> "已取消归档；聊天已恢复为可打开会话，未发送新的模型请求。"
                            ManagedWorktreeChatRestore.AlreadyLive -> "已取消归档；该聊天已是活动会话。"
                            ManagedWorktreeChatRestore.NoHistory -> "已取消归档，但没有可恢复的持久历史。"
                            ManagedWorktreeChatRestore.Unavailable -> "已取消归档；本机恢复服务不可用。"
                            is ManagedWorktreeChatRestore.Failed -> "已取消归档；恢复失败：${restore.reason}"
                        }
                        is ManagedWorktreeChatArchiveReceipt.Rejected -> it.reason
                        is ManagedWorktreeChatArchiveReceipt.Unknown -> it.reason
                    }, Modifier.testTag("managed-archive-receipt"))
                }
                state.removalReceipt?.let { receipt ->
                    HarnessText(when (receipt) {
                        is ManagedWorktreeRemovalReceipt.Removed -> "工作树已删除；显示列表来自磁盘刷新。"
                        is ManagedWorktreeRemovalReceipt.Blocked -> receipt.reason
                        is ManagedWorktreeRemovalReceipt.Unsupported -> receipt.reason
                        is ManagedWorktreeRemovalReceipt.Failed -> receipt.reason
                        is ManagedWorktreeRemovalReceipt.UnknownOutcome -> receipt.reason
                    }, Modifier.testTag("managed-removal-receipt"))
                }
            }
            // The guarded delete block is per worktree; this line only covers inventories whose
            // removal evidence is not available at all.
            if (state.inventory?.entries?.none { it.removal is ManagedWorktreeRemovalRead.Available } != false) {
                HarnessText("删除与清理不可用：保护数据尚不完整。", Modifier.testTag("managed-destructive-unavailable"))
            }
        }
    }
    pendingRemoval?.let { plan ->
        HarnessConfirmDialog(
            visible = true,
            onDismissRequest = { pendingRemoval = null },
            title = "删除工作树？",
            description = "将删除 " + plan.root + " 的 Git 工作树登记与工作目录内容。只有在没有任何关联聊天、固定、归档、" +
                "未提交变更、子模块或嵌套内容，且不是主工作树时才会执行。",
            cancelLabel = "取消",
            confirmLabel = "删除",
            submitState = if (state?.removalBusy == true) HarnessConfirmSubmitState.Submitting else HarnessConfirmSubmitState.Ready,
            onCancel = { pendingRemoval = null },
            onConfirm = { pendingRemoval = null; controller?.remove(plan, pageReady) },
            intent = HarnessConfirmIntent.Destructive,
            testTag = "managed-remove-dialog",
        )
    }
}

@Composable
private fun SessionObservationLabel(observation: ManagedSessionObservation, current: Boolean, root: String,
    state: ManagedWorktreeState, controller: ManagedWorktreeController, pageReady: Boolean) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        HarnessText(if (!current) "上次只读会话观察已过期；当前关联未知。" else when (observation.status) {
            ManagedSessionObservationStatus.Observed -> if (observation.sessions.isEmpty())
                "本次未观察到关联会话" else "本次只读观察：关联会话 ${observation.sessions.size}"
            ManagedSessionObservationStatus.Unknown -> "本次只读观察：关联尚未完整判定"
            ManagedSessionObservationStatus.Unavailable -> "本次只读观察：会话目录能力不可用"
        }, Modifier.testTag("managed-session-observation-status"), color = HarnessTokens.Colors.TextMuted)
        HarnessText("${if (current) "本次" else "上次"}观察（UTC）：开始 ${kotlin.time.Instant.fromEpochMilliseconds(observation.startedAtMillis)}；完成 ${kotlin.time.Instant.fromEpochMilliseconds(observation.completedAtMillis)}",
            Modifier.testTag("managed-session-observation-time"), style = MaterialTheme.typography.bodySmall)
        GitPageValue("Host：${observation.owner.hostId.value} · home：${observation.owner.homeNamespace}",
            "managed-session-observation-owner", muted = !current)
        observation.sessions.forEach { session ->
            GitPageValue("${if (current) "观察到" else "上次观察到"}会话：${session.id}", "managed-session-id", muted = !current)
            GitPageValue("来源：" + session.sources.joinToString(" + ") { source -> when (source) {
                ManagedSessionSource.Live -> "本次内存会话"
                ManagedSessionSource.Durable -> "持久历史"
            } }, "managed-session-sources", muted = !current)
            GitPageValue("cwd：${session.cwd}", "managed-session-cwd", muted = !current)
            session.parentSessionId?.let { GitPageValue("父会话：$it", "managed-session-parent", muted = !current) }
            val key = session.pinKey
            val inventory = state.inventory
            val available = (inventory?.pinRead as? ManagedWorktreePinRead.Available)?.takeIf {
                current && inventory != null && inventory.owner == observation.owner && it.owner == observation.owner &&
                    key != null && key.id == session.id && observation.status == ManagedSessionObservationStatus.Observed
            }
            val pinned = available != null && key != null && key in available.snapshot.pins
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                HarnessText(when {
                    available != null -> if (pinned) "聊天已固定" else "此聊天未设置固定"
                    state.inventory?.pinRead is ManagedWorktreePinRead.Unavailable || state.inventory?.pinRead == null -> "聊天固定服务不可用"
                    else -> "当前聊天固定状态未知；请刷新核验"
                }, Modifier.testTag("managed-session-pin-status"), color = HarnessTokens.Colors.TextMuted)
                val archiveAvailable = (inventory?.archiveRead as? ManagedWorktreeChatArchiveRead.Available)?.takeIf {
                    current && inventory != null && inventory.owner == observation.owner && it.owner == observation.owner &&
                        key != null && key.id == session.id && observation.status == ManagedSessionObservationStatus.Observed
                }
                val archived = archiveAvailable != null && key != null &&
                    ChatArchiveKey(key.id, key.headerFingerprint) in archiveAvailable.snapshot.archived
                HarnessText(when {
                    archiveAvailable != null -> if (archived) "聊天已归档" else "此聊天未设置归档"
                    inventory?.archiveRead is ManagedWorktreeChatArchiveRead.Unavailable || inventory?.archiveRead == null ->
                        "聊天归档服务不可用"
                    else -> "当前聊天归档状态未知；请刷新核验"
                }, Modifier.testTag("managed-session-archive-status"), color = HarnessTokens.Colors.TextMuted)
                // Both controls share one row so the observed-session card keeps its previous height.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HarnessButton(if (pinned) "取消固定聊天" else "固定聊天",
                        { if (key != null) controller.setPinned(root, key, !pinned, pageReady) },
                        enabled = pageReady && available != null && key != null && state.canPin(root, key),
                        testTag = "managed-session-pin-toggle")
                    HarnessButton(if (archived) "取消归档聊天" else "归档聊天",
                        { if (key != null) controller.setArchived(root, ChatArchiveKey(key.id, key.headerFingerprint), !archived, pageReady) },
                        enabled = pageReady && archiveAvailable != null && key != null &&
                            state.canArchive(root, ChatArchiveKey(key.id, key.headerFingerprint)),
                        testTag = "managed-session-archive-toggle")
                }
            }
        }
        observation.issues.forEach { issue ->
            HarnessText(listOfNotNull(issue.sessionId, issue.reason).joinToString("："),
                Modifier.testTag("managed-session-observation-issue"), style = MaterialTheme.typography.bodySmall)
        }
        HarnessText("两个来源独立读取；本次观察不证明无会话或安全删除。",
            Modifier.testTag("managed-session-observation-boundary"), style = MaterialTheme.typography.bodySmall,
            color = HarnessTokens.Colors.TextMuted)
    }
}

@Composable
private fun BranchCheckoutForm(controller: GitController, state: GitState) {
    val refs = (state.branches as? GitRead.Ready<List<GitBranch>>)?.value.orEmpty()
        .filter { !it.remote && it.ref.startsWith("refs/heads/") }.map { it.ref }
    SettingsFormSection("切换已有本地分支") {
        GitPageSelect(state.selectedBranchRef, listOf<String?>(null) + refs,
            { it ?: "选择已有分支" }, state.pageReady && state.checkoutAvailable &&
                !state.checkoutNeedsRescan && !state.creationNeedsRescan && !state.commitNeedsRescan &&
                !state.branchCreateNeedsRescan && refs.isNotEmpty(),
            "git-checkout-branch") { controller.selectBranch(it) }
        HarnessButton("切换分支", controller::switchBranch, enabled = state.pageReady && state.canSwitchBranch, testTag = "git-checkout-submit")
        state.checkoutDisabledReason?.let {
            HarnessText(it, Modifier.testTag("git-checkout-disabled-reason"), color = HarnessTokens.Colors.TextMuted)
        }
        when (val checkout = state.checkout) {
            GitBranchCheckout.Idle -> Unit
            GitBranchCheckout.Rescanned -> HarnessText("已重新读取当前状态，可重新选择。", Modifier.testTag("git-checkout-rescanned"))
            is GitBranchCheckout.Switching -> HarnessText("正在切换本地分支…", Modifier.testTag("git-checkout-busy"))
            is GitBranchCheckout.Switched -> HarnessText("已确认切换：${checkout.receipt.status.branch}",
                Modifier.testTag("git-checkout-success"))
            is GitBranchCheckout.Failed -> HarnessText(checkout.failure.message,
                Modifier.testTag("git-checkout-error"), color = HarnessTokens.Colors.Danger)
            is GitBranchCheckout.UnknownOutcome -> HarnessText(checkout.failure.message,
                Modifier.testTag("git-checkout-unknown"), color = HarnessTokens.Colors.Danger)
        }
        if (state.checkoutNeedsRescan) HarnessText("切换结果未知。请先刷新状态、分支和工作树，再决定下一步。",
            Modifier.testTag("git-checkout-needs-rescan"), color = HarnessTokens.Colors.Danger)
    }
}

/** Create-and-checkout of one new branch; the same admission slot and rescan gate as switching. */
@Composable
private fun BranchCreationForm(controller: GitController, state: GitState) {
    val localRefs = (state.branches as? GitRead.Ready<List<GitBranch>>)?.value.orEmpty()
        .filter { !it.remote && it.ref.startsWith("refs/heads/") }.map { it.ref }
    val startOptions = listOf("HEAD") + localRefs
    val start = state.newBranchStartRef ?: "HEAD"
    val editable = state.pageReady && state.branchCreateAvailable && !state.pageMutationBusy && !state.branchCreateNeedsRescan
    SettingsFormSection("新建分支并切换") {
        HarnessText("在当前 HEAD 或已有本地分支上创建并切换到一个新分支；同名分支存在时不会创建，也不使用 force/reset。",
            Modifier.testTag("git-branch-create-policy"), color = HarnessTokens.Colors.TextMuted)
        HarnessTextField(state.newBranchName, { controller.setNewBranchName(it) }, "新分支名称",
            Modifier.fillMaxWidth(), enabled = editable, testTag = "git-branch-create-name")
        GitPageSelect(start, startOptions, { it }, editable && startOptions.size > 1, "git-branch-create-start") {
            controller.setNewBranchStart(it)
        }
        HarnessButton("新建并切换分支", controller::createBranch, enabled = state.pageReady && state.canCreateBranch,
            testTag = "git-branch-create-submit")
        state.branchCreateDisabledReason?.let {
            HarnessText(it, Modifier.testTag("git-branch-create-disabled-reason"), color = HarnessTokens.Colors.TextMuted)
        }
        when (val creation = state.branchCreation) {
            GitBranchCreation.Idle -> Unit
            GitBranchCreation.Rescanned -> HarnessText("已重新读取当前状态，可重新选择。",
                Modifier.testTag("git-branch-create-rescanned"))
            is GitBranchCreation.Creating -> HarnessText("正在创建并切换分支…", Modifier.testTag("git-branch-create-busy"))
            is GitBranchCreation.Created -> HarnessText("已确认创建并切换：${creation.receipt.status.branch}",
                Modifier.testTag("git-branch-create-success"))
            is GitBranchCreation.Failed -> HarnessText(creation.failure.message,
                Modifier.testTag("git-branch-create-error"), color = HarnessTokens.Colors.Danger)
            is GitBranchCreation.UnknownOutcome -> HarnessText(creation.failure.message,
                Modifier.testTag("git-branch-create-unknown"), color = HarnessTokens.Colors.Danger)
        }
        if (state.branchCreateNeedsRescan) HarnessText("新建分支结果未知。请先刷新状态、分支和工作树，再决定下一步。",
            Modifier.testTag("git-branch-create-needs-rescan"), color = HarnessTokens.Colors.Danger)
    }
}

@Composable
private fun CommitForm(controller: GitController, observed: GitState) {
    val repo = (observed.repository as? GitRead.Ready<GitRepository>)?.value?.takeIf { it.binding == observed.binding }
    // Synchronous render filter: a previous scope's message/receipt must never paint in a new scope.
    val snapshot = (observed.commitSnapshot as? GitRead.Ready<GitCommitSnapshot>)?.value?.takeIf { it.repository == repo }
    val operation = when (val commit = observed.commit) {
        is GitCommit.Submitting -> if (commit.request.snapshot.repository == repo) commit else GitCommit.Idle
        is GitCommit.Verified -> if (commit.receipt.repository == repo) commit else GitCommit.Idle
        else -> if (repo != null) commit else GitCommit.Idle
    }
    var visible by remember(observed.binding, observed.selectedCwd) { mutableStateOf(false) }
    var message by remember(observed.binding, observed.selectedCwd) { mutableStateOf("") }
    val enabled = repo != null && snapshot != null && observed.pageReady && observed.canCommit
    SettingsFormSection("提交已有暂存变更") {
        HarnessText("默认仅提交已有暂存内容；可在弹窗中勾选先暂存未暂存变更。不运行 Git hooks，不签名。",
            Modifier.testTag("git-commit-policy"), color = HarnessTokens.Colors.TextMuted)
        HarnessButton("Commit…", { visible = true },
            enabled = repo != null && observed.commitAvailable && observed.unavailableReason == null, testTag = "git-commit-open")
        observed.commitDisabledReason?.let { HarnessText(it, Modifier.testTag("git-commit-disabled-reason")) }
        if (!visible) CommitOutcome(operation)
    }
    HarnessDialog(visible, { visible = false }, "提交暂存变更") {
        Column(Modifier.widthIn(max = 420.dp).fillMaxWidth().heightIn(max = 520.dp)
            .verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GitPageValue(repo?.root ?: "仓库不可用", "git-commit-repository")
            GitPageValue(snapshot?.status?.branch ?: "当前分支不可用", "git-commit-branch")
            ReadResult(observed.commitSnapshot, "尚未读取暂存区。") { value ->
                if (value.repository == repo) HarnessText("已暂存 ${value.stagedCount}", Modifier.testTag("git-commit-staged-count"))
            }
            HarnessCheckbox(
                checked = observed.includeUnstaged,
                onCheckedChange = { controller.setIncludeUnstaged(it) },
                contentDescription = "包含未暂存变更（提交前先暂存全部变更）",
                enabled = !observed.pageMutationBusy && !observed.commitNeedsRescan && observed.commitAvailable,
                testTag = "git-commit-include-unstaged",
            )
            HarnessText(
                if (observed.includeUnstaged) "提交前会先暂存工作树全部变更（含未跟踪，忽略 .gitignore 之外的忽略项），再提交全部已暂存内容。"
                else "仅提交已有暂存内容；勾选上方选项可先暂存未暂存与未跟踪变更。不运行 Git hooks，不签名。",
                Modifier.testTag("git-commit-include-unstaged-hint"), color = HarnessTokens.Colors.TextMuted)
            HarnessTextField(message, { message = it }, "提交消息", Modifier.fillMaxWidth().heightIn(min = 100.dp),
                enabled = !observed.pageMutationBusy && !observed.commitNeedsRescan && observed.commitAvailable,
                singleLine = false, testTag = "git-commit-message")
            HarnessButton("查看 Index diff", {
                controller.selectDiff(GitDiffRequest(GitDiffSource.Index)); visible = false
            }, enabled = observed.pageReady, testTag = "git-commit-index-diff",
                variant = HarnessButtonVariant.Secondary)
            HarnessButton(if (observed.includeUnstaged) "暂存全部变更并提交" else "提交暂存变更", { controller.commit(message) },
                enabled = enabled && normalizeGitCommitMessage(message) != null, testTag = "git-commit-submit")
            observed.commitDisabledReason?.let { HarnessText(it, Modifier.testTag("git-commit-dialog-reason")) }
            CommitOutcome(operation)
            HarnessButton("关闭", { visible = false }, testTag = "git-commit-close", variant = HarnessButtonVariant.Secondary)
        }
    }
}

@Composable
private fun CommitOutcome(commit: GitCommit) {
    when (commit) {
        GitCommit.Idle -> Unit
        is GitCommit.Submitting -> HarnessText("正在提交暂存变更…", Modifier.testTag("git-commit-busy"))
        is GitCommit.Verified -> HarnessText("已验证提交：${commit.receipt.commitOid.take(12)}", Modifier.testTag("git-commit-verified"))
        is GitCommit.Rejected -> HarnessText(commit.failure.message, Modifier.testTag("git-commit-error"), color = HarnessTokens.Colors.Danger)
        is GitCommit.UnknownOutcome -> HarnessText(commit.failure.message, Modifier.testTag("git-commit-unknown"), color = HarnessTokens.Colors.Danger)
        GitCommit.Rescanned -> HarnessText("已重新读取当前状态，可重新选择。", Modifier.testTag("git-commit-rescanned"))
    }
}

@Composable
private fun WorktreeRootSection(state: WorktreeSettingsState?, onSave: (WorktreeSettings) -> Unit) {
    SettingsFormSection("工作树根目录") {
        HarnessText("ChatGPT 创建托管工作树的目录。留空则使用默认位置。", color = HarnessTokens.Colors.TextMuted)
        if (state == null) {
            HarnessText("工作树设置服务不可用。", Modifier.testTag("worktree-root-unavailable"))
            return@SettingsFormSection
        }
        val saved = state.settings.root
        // Re-seed the draft whenever the persisted value changes (load or a completed save).
        var draft by remember(state.loaded, saved) { mutableStateOf(saved) }
        val error = worktreeRootError(draft)
        val changed = worktreeRootValue(draft) != worktreeRootValue(saved)
        HarnessTextField(draft, { draft = it }, "工作树根目录（留空使用默认位置）", Modifier.fillMaxWidth(),
            enabled = state.canEdit, isError = error != null, supportingText = error,
            testTag = "worktree-root-input")
        HarnessButton("保存根目录", { onSave(state.settings.copy(root = worktreeRootValue(draft))) },
            enabled = state.canEdit && changed && error == null, testTag = "worktree-root-save")
        state.loadError?.let { HarnessText(it, Modifier.testTag("worktree-root-load-error"), color = HarnessTokens.Colors.Danger) }
        state.saveError?.let { HarnessText(it, Modifier.testTag("worktree-root-save-error"), color = HarnessTokens.Colors.Danger) }
        if (state.saveRevision > 0 && state.saveError == null && !changed) {
            HarnessText("已保存工作树根目录。", Modifier.testTag("worktree-root-saved"),
                color = HarnessTokens.Colors.TextMuted)
        }
    }
}

/**
 * The original page's "Environment" rows. Each control persists one stored preference through the
 * same repository as the root field; the destructive pruning and the pre-create fetch are backend
 * behavior that this page does not run.
 */
@Composable
private fun WorktreeEnvironmentSection(state: WorktreeSettingsState?, onSave: (WorktreeSettings) -> Unit) {
    SettingsFormSection("环境") {
        if (state == null) {
            HarnessText("工作树设置服务不可用。", Modifier.testTag("worktree-env-unavailable"))
            return@SettingsFormSection
        }
        val settings = state.settings

        // "Automatically delete old worktrees"; the original confirms before disabling it.
        var confirmDisable by remember(settings.autoCleanupEnabled) { mutableStateOf(false) }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Two)) {
            Column(Modifier.weight(1f)) {
                HarnessText("自动删除旧工作树", Modifier.testTag("worktree-auto-cleanup-label"))
                HarnessText("推荐大多数用户启用。仅当你需要手动管理旧工作树和磁盘使用空间时，再关闭此功能。",
                    color = HarnessTokens.Colors.TextMuted, style = MaterialTheme.typography.bodySmall)
            }
            HarnessSwitch(
                checked = settings.autoCleanupEnabled,
                onCheckedChange = { next -> if (next) onSave(settings.copy(autoCleanupEnabled = true)) else confirmDisable = true },
                contentDescription = "自动删除旧工作树", enabled = state.canEdit,
                testTag = "worktree-auto-cleanup-switch")
        }

        // "Auto-delete limit"; the value must stay an integer >= 1 (the original schema).
        val savedKeep = settings.keepCount.toString()
        var draft by remember(state.loaded, savedKeep) { mutableStateOf(savedKeep) }
        val keepError = keepCountError(draft)
        val keepChanged = keepCountValue(draft) != settings.keepCount
        HarnessText("自动删除限制", Modifier.testTag("worktree-keep-count-label"))
        HarnessText(
            if (settings.autoCleanupEnabled) "要保留的托管工作树数量；超过后，较旧的工作树会自动被清理。ChatGPT 会在删除工作树前创建快照，因此被清理的工作树应始终可以恢复。"
            else "自动删除已停用。ChatGPT 不会自动清理旧工作树。重新启用即可再次使用此已保存的限制。",
            Modifier.testTag("worktree-keep-count-description"),
            color = HarnessTokens.Colors.TextMuted, style = MaterialTheme.typography.bodySmall)
        HarnessTextField(draft, { draft = it }, "自动删除限制（不小于 1）", Modifier.fillMaxWidth(),
            enabled = state.canEdit, isError = keepError != null, supportingText = keepError,
            testTag = "worktree-keep-count-input")
        HarnessButton("保存自动删除限制", { keepCountValue(draft)?.let { onSave(settings.copy(keepCount = it)) } },
            enabled = state.canEdit && keepChanged && keepError == null, testTag = "worktree-keep-count-save")

        // "Always fetch upstream before creating worktrees".
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Two)) {
            Column(Modifier.weight(1f)) {
                HarnessText("创建工作树前始终获取上游更新",
                    Modifier.testTag("worktree-upstream-refresh-label"))
                HarnessText("Codex 通常会在常规 Git 操作中获取分支更新。此设置还会在创建每个新工作树前获取上游更新。",
                    color = HarnessTokens.Colors.TextMuted, style = MaterialTheme.typography.bodySmall)
            }
            HarnessSwitch(
                checked = settings.upstreamRefreshMode == WorktreeUpstreamRefreshMode.BestEffort,
                onCheckedChange = { next -> onSave(settings.copy(
                    upstreamRefreshMode = if (next) WorktreeUpstreamRefreshMode.BestEffort else WorktreeUpstreamRefreshMode.Never)) },
                contentDescription = "创建工作树前始终获取上游更新", enabled = state.canEdit,
                testTag = "worktree-upstream-refresh-switch")
        }

        state.loadError?.let { HarnessText(it, Modifier.testTag("worktree-env-load-error"), color = HarnessTokens.Colors.Danger) }
        state.saveError?.let { HarnessText(it, Modifier.testTag("worktree-env-save-error"), color = HarnessTokens.Colors.Danger) }
        if (state.saveRevision > 0 && state.saveError == null) {
            HarnessText("已保存工作树设置。", Modifier.testTag("worktree-env-saved"), color = HarnessTokens.Colors.TextMuted)
        }

        HarnessConfirmDialog(
            visible = confirmDisable,
            onDismissRequest = { confirmDisable = false },
            title = "禁用工作树自动删除功能？",
            description = "我们强烈建议保持自动删除功能开启，以免旧工作树不断累积并占用不必要的磁盘空间。如果你希望自行管理旧工作树，可以关闭此功能，ChatGPT 将停止自动删除它们。",
            cancelLabel = "启用自动删除功能",
            confirmLabel = "禁用自动删除功能",
            submitState = HarnessConfirmSubmitState.Ready,
            onCancel = { confirmDisable = false },
            onConfirm = { confirmDisable = false; onSave(settings.copy(autoCleanupEnabled = false)) },
            testTag = "worktree-auto-cleanup-confirm")
    }
}

@Composable
private fun WorktreeCreationForm(controller: GitController, state: GitState, worktreeRoot: String = "") {
    var path by remember(state.binding, state.selectedCwd) { mutableStateOf("") }
    var pathTouched by remember(state.binding, state.selectedCwd) { mutableStateOf(false) }
    var branch by remember(state.binding, state.selectedCwd) { mutableStateOf("codex/") }
    var startRef by remember(state.binding, state.selectedCwd) { mutableStateOf("HEAD") }
    // A configured worktree root seeds the parent directory until the user edits the path field.
    val autoPath = worktreeDefaultPath(worktreeRoot, branch)
    LaunchedEffect(autoPath) { if (!pathTouched && autoPath != null) path = autoPath }
    val refs = (state.branches as? GitRead.Ready<List<GitBranch>>)?.value.orEmpty().map { it.ref }
    val options = listOf("HEAD") + refs
    SettingsFormSection("创建本地工作树") {
        if (!state.creationAvailable) HarnessText("此 Host 未提供本地工作树创建能力。",
            Modifier.testTag("git-worktree-create-unavailable"), color = HarnessTokens.Colors.TextMuted)
        HarnessTextField(path, { path = it; pathTouched = true }, "新工作树绝对路径（父目录须已存在）", Modifier.fillMaxWidth(),
            enabled = state.pageReady && state.canCreateWorktree, testTag = "git-worktree-create-path")
        if (!pathTouched && autoPath != null) HarnessText("默认创建于工作树根目录下：$autoPath",
            Modifier.testTag("git-worktree-create-auto-path"), color = HarnessTokens.Colors.TextMuted)
        HarnessTextField(branch, { branch = it }, "新分支名称", Modifier.fillMaxWidth(),
            enabled = state.pageReady && state.canCreateWorktree, testTag = "git-worktree-create-branch")
        GitPageSelect(startRef, options, { it }, state.pageReady && state.canCreateWorktree, "git-worktree-create-start") { startRef = it }
        HarnessButton("创建工作树", { controller.createWorktree(path, branch, startRef) },
            enabled = state.pageReady && state.canCreateWorktree && path.isNotBlank() && branch.isNotBlank() && branch != "codex/" && startRef in options,
            testTag = "git-worktree-create-submit")
        if (state.creation is GitWorktreeCreation.Creating)
            HarnessText("正在创建本地工作树…", Modifier.testTag("git-worktree-create-busy"))
        when (val creation = state.creation) {
            GitWorktreeCreation.Idle, is GitWorktreeCreation.Creating -> Unit
            is GitWorktreeCreation.Created -> GitPageCard {
                GitPageValue("已创建：${creation.receipt.worktree.path}", "git-worktree-create-success")
                GitPageValue(creation.receipt.worktree.branch ?: "分支不可用", "git-worktree-created-branch")
                HarnessText("此回执只确认 Git 工作树已创建；Managed 登记请查看归属清单。环境设置与会话关联尚未确认。",
                    Modifier.testTag("git-worktree-created-boundary"), color = HarnessTokens.Colors.TextMuted)
            }
            is GitWorktreeCreation.Failed -> HarnessText(creation.failure.failure.message,
                Modifier.testTag("git-worktree-create-error"), color = HarnessTokens.Colors.Danger)
        }
        if (state.creationNeedsRescan) HarnessText("创建结果未知。请先刷新并检查实际工作树列表，再决定下一步。",
            Modifier.testTag("git-worktree-create-needs-rescan"), color = HarnessTokens.Colors.Danger)
    }
}

@Composable
private fun RepositoryControls(controller: GitController, state: GitState) {
    var cwd by remember(state.binding?.identity, state.binding?.hostId, state.selectedCwd) {
        mutableStateOf(state.selectedCwd.orEmpty())
    }
    SettingsFormSection("仓库") {
        state.unavailableReason?.let { HarnessText(it, Modifier.testTag("git-unavailable")) }
        HarnessTextField(cwd, { cwd = it }, "本机仓库目录", Modifier.fillMaxWidth(),
            enabled = state.pageCanRead, testTag = "git-repository-path")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HarnessButton("读取仓库", { controller.selectRepository(cwd) },
                enabled = state.pageCanRead && cwd.isNotBlank() && cwd != state.selectedCwd,
                testTag = "git-repository-select")
            HarnessButton("刷新", controller::refresh, enabled = state.canRefresh && state.pageCanRead, testTag = "git-refresh")
        }
        ReadResult(state.repository, "尚未选择仓库。") { repo ->
            GitPageCard {
                GitPageValue(repo.root, "git-repository-root")
                GitPageValue("Git 目录：${repo.gitDir}", "git-repository-git-dir", muted = true)
            }
        }
    }
}

@Composable
private fun <T> ReadResult(read: GitRead<T>, unselected: String, content: @Composable (T) -> Unit) {
    when (read) {
        GitRead.Unselected -> HarnessText(unselected, color = HarnessTokens.Colors.TextMuted)
        GitRead.Loading -> HarnessText("正在读取…", Modifier.testTag("git-loading"))
        is GitRead.Failed -> GitPageCard {
            val tag = when (read.failure.kind) {
                GitFailureKind.Unavailable -> "git-read-unavailable"
                GitFailureKind.NotRepository -> "git-not-repository"
                else -> "git-read-failed"
            }
            HarnessText(when (read.failure.kind) {
                GitFailureKind.Unavailable -> "Git 读取服务不可用"
                GitFailureKind.NotRepository -> "目录不是 Git 仓库"
                else -> "Git 读取失败"
            }, Modifier.testTag(tag), style = MaterialTheme.typography.titleSmall)
            HarnessText(read.failure.message, Modifier.testTag("git-error"),
                color = if (read.failure.kind == GitFailureKind.Unavailable) HarnessTokens.Colors.TextMuted else HarnessTokens.Colors.Danger)
        }
        is GitRead.Ready -> content(read.value)
    }
}

@Composable
private fun ProtectionLabel(protection: ManagedProtection, tag: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        HarnessText(when (protection.availability) {
            ManagedProtectionAvailability.Unknown -> "保护状态未知"
            ManagedProtectionAvailability.Unavailable -> "保护服务不可用"
        }, Modifier.testTag("$tag-availability"), style = MaterialTheme.typography.labelMedium,
            color = HarnessTokens.Colors.TextMuted)
        HarnessText(protection.reason, Modifier.testTag(tag), style = MaterialTheme.typography.bodyMedium)
    }
}
