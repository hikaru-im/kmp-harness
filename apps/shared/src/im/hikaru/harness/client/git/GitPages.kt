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
public fun SettingsGitPage(
    controller: GitController, modifier: Modifier = Modifier, directoryPicker: (@Composable () -> Unit)? = null,
    /** One-shot path a transcript card asked to reveal; the page scrolls its file open once. */
    revealPath: String? = null,
    onRevealConsumed: () -> Unit = {},
) {
    val state by controller.state.collectAsState()
    GitPageToasts(modifier) {
        SettingsFormFrame("Git", "git-page") {
            RepositoryControls(controller, state, directoryPicker)
            SettingsFormSection("工作区变更") {
                GitPageCard {
                    ReadResult(state.status, "选择仓库后读取状态。") { status ->
                        GitPageValue(when {
                            status.unborn -> "${status.branch?.let(::gitRefLabel) ?: "HEAD"} · 尚无提交"
                            status.detached -> "Detached HEAD · ${status.headOid ?: "提交不可用"}"
                            else -> status.branch?.let(::gitRefLabel) ?: "HEAD 不可用"
                        }, "git-current-branch")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            GitStatusLabel("已暂存 ${status.stagedCount}")
                            GitStatusLabel("未暂存 ${status.unstagedCount}")
                            GitStatusLabel("未跟踪 ${status.untrackedCount}")
                        }
                        if (status.changes.isEmpty()) HarnessText("工作树干净。", Modifier.testTag("git-clean"))
                        GitPageList("git-changes-list") {
                            status.changes.forEach { change ->
                                GitChangeRow(change)
                            }
                        }
                    }
                }
            }
            SettingsFormSection("分支") {
                GitPageCard {
                    ReadResult(state.branches, "尚未读取分支。") { branches ->
                        if (branches.isEmpty()) HarnessText("没有分支引用。")
                        GitPageList("git-branches-list") {
                            branches.forEach { branch -> GitBranchRow(branch) }
                        }
                    }
                }
            }
            BranchCheckoutForm(controller, state)
            BranchCreationForm(controller, state)
            CommitForm(controller, state)
            SettingsFormSection("差异") {
                GitPageCard {
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
                    // Codex's diff-source picker is the control and nothing else; it carries no
                    // paragraph describing what the chosen base does or does not include.
                }
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
                        copyGitApplyCommandDisabled = !state.pageCanRead,
                        contextExpansionAllowed = state.diffRequest.source != GitDiffSource.Index,
                        revealPath = revealPath,
                        onRevealConsumed = onRevealConsumed)
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
                            copyGitApplyCommandDisabled = !state.pageCanRead,
                            contextExpansionAllowed = state.diffRequest.source != GitDiffSource.Index,
                            revealPath = revealPath,
                            onRevealConsumed = onRevealConsumed)
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
    directoryPicker: (@Composable () -> Unit)? = null,
) {
    val state by controller.state.collectAsState()
    val currentRepository = (state.repository as? GitRead.Ready<GitRepository>)?.value
    // Render fence precedes the asynchronous bind: never paint a previous owner's inventory.
    val managedState = managedController?.let { managed ->
        val observed by managed.state.collectAsState()
        if (observed.repository == currentRepository) observed else ManagedWorktreeState(repository = currentRepository)
    }
    LaunchedEffect(managedController, currentRepository) { managedController?.bind(currentRepository) }
    GitPageToasts(modifier) {
        // `settings.nav.worktrees` / `settings.section.worktrees` = 工作树.
        SettingsFormFrame("工作树", "worktrees-page") {
            RepositoryControls(controller, state, directoryPicker)
            SettingsFormSection("本地 Git 工作树") {
                ReadResult(state.worktrees, "选择仓库后读取工作树。") { worktrees ->
                    // `settings.worktrees.empty.title`.
                    if (worktrees.isEmpty()) HarnessText("尚无工作树", Modifier.testTag("git-worktrees-empty"))
                    GitPageList("git-worktrees-list") {
                        worktrees.forEach { worktree ->
                            GitPageCard {
                                GitPageValue(worktree.path, "git-worktree-path")
                                GitPageValue(when {
                                    worktree.bare -> "Bare repository"
                                    worktree.detached -> "Detached HEAD · ${worktree.headOid ?: "提交不可用"}"
                                    else -> worktree.branch?.let(::gitRefLabel) ?: "分支不可用"
                                }, "git-worktree-branch")
                                if (worktree.locked) HarnessText("已锁定" + (worktree.lockReason?.let { "：$it" } ?: ""))
                                if (worktree.prunable) HarnessText("prunable" + (worktree.pruneReason?.let { "：$it" } ?: ""))
                                WorktreeConversations(worktree.path, sessions, onOpenSession)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    HarnessButton("新建聊天", { onNewChatInWorktree(worktree.path) },
                                        enabled = state.pageReady && !worktree.bare && !worktree.prunable,
                                        testTag = "git-worktree-new-chat")
                                    HarnessButton("读取此工作树", { controller.selectWorktree(worktree.path) }, variant = HarnessButtonVariant.Secondary,
                                        enabled = state.pageReady && !worktree.bare && !worktree.prunable,
                                        testTag = "git-worktree-select")
                                }
                            }
                        }
                    }
                }
            }
            ManagedWorktreeSection(managedController, managedState, state.pageReady, sessions, onOpenSession,
                onNewChatInWorktree)
            WorktreeCreationForm(controller, state, worktreeSettings?.settings?.configuredRoot.orEmpty())
            WorktreeRootSection(worktreeSettings, onSaveWorktreeSettings)
            WorktreeEnvironmentSection(worktreeSettings, onSaveWorktreeSettings)
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
        if (matching.isEmpty()) HarnessText("无关联到此工作树的对话。",
            Modifier.testTag("worktree-conversations-empty-$dir"), color = HarnessTokens.Colors.TextMuted)
        else matching.forEach { session -> key(session.id) {
            HarnessRow(session.id.value,
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
    SettingsFormSection("托管工作树") {
        if (controller == null || state == null) {
            // `settings.worktrees.error.title` / `.error.body`: Codex's own copy for worktrees that
            // could not be read. No invented second sentence — an unavailable service is stated once,
            // and never enumerates which downstream controls that implies.
            GitPageCard {
                HarnessText("无法加载工作树", Modifier.testTag("managed-unavailable"))
            }
        } else {
            // `settings.worktrees.refresh` is just 刷新; the button lives at the section header.
            HarnessButton("刷新", controller::refresh,
                variant = HarnessButtonVariant.Secondary, size = HarnessControlSize.Compact,
                enabled = pageReady && state.canRefresh && state.phase != ManagedWorktreePhase.Loading,
                testTag = "managed-refresh")
            when (state.phase) {
                ManagedWorktreePhase.Unbound -> HarnessText("未选择仓库", Modifier.testTag("managed-unbound"))
                ManagedWorktreePhase.Loading -> HarnessText("正在加载工作树", Modifier.testTag("managed-loading"))
                // `settings.worktrees.error.title` / `.error.body`: the failure line is Codex's own
                // title, and the body only appears when the read itself carried a message.
                ManagedWorktreePhase.Error -> HarnessText(state.message ?: "无法加载工作树",
                    Modifier.testTag("managed-error"), color = HarnessTokens.Colors.Danger)
                ManagedWorktreePhase.Unavailable -> HarnessText(state.message ?: "无法加载工作树",
                    Modifier.testTag("managed-unavailable"))
                ManagedWorktreePhase.Ready -> Unit
            }
            if (state.stale) HarnessText("上次观察已过期。", Modifier.testTag("managed-stale"))
            state.inventory?.let { inventory ->
                val verified = state.phase == ManagedWorktreePhase.Ready && !state.stale
                HarnessText(if (verified) "已核验 Managed ${inventory.registeredCount} · 其他归属仍需核验"
                    else "上次观察 ${inventory.entries.size} 条 · 当前核验数量未知", Modifier.testTag("managed-count"))
                // `settings.worktrees.empty.title` / `.empty.body`.
                if (verified && inventory.entries.isEmpty()) HarnessText("尚无工作树",
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
                            else SessionObservationLabel(sessionObservation, verified, entry.root, state, controller, pageReady)
                            when (val pins = inventory.pinRead) {
                                // A read pin store is a fact, not a licence: the label below still reports
                                // whatever protection the inventory actually verified. No sentence here
                                // explains what the read does or does not permit.
                                is ManagedWorktreePinRead.Available -> Unit
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
                                    // The action is simply offered or not; Codex never prints a paragraph
                                    // beside a worktree's delete control describing what the checks prove.
                                    val eligible = verified && state.canRemove(entry.root)
                                    HarnessButton("删除工作树", { pendingRemoval = removal.plan },
                                        enabled = eligible && pageReady,
                                        variant = HarnessButtonVariant.Secondary,
                                        testTag = "managed-remove")
                                }
                                is ManagedWorktreeRemovalRead.Unknown, is ManagedWorktreeRemovalRead.Unavailable -> Unit
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
                        is ManagedWorktreePinReceipt.Applied -> it.durabilityWarning ?: "聊天固定已提交。"
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
                            null -> "聊天归档已提交。"
                            ManagedWorktreeChatRestore.Restored -> "已取消归档"
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
                        is ManagedWorktreeRemovalReceipt.Removed -> "工作树已删除。"
                        is ManagedWorktreeRemovalReceipt.Blocked -> receipt.reason
                        is ManagedWorktreeRemovalReceipt.Unsupported -> receipt.reason
                        is ManagedWorktreeRemovalReceipt.Failed -> receipt.reason
                        is ManagedWorktreeRemovalReceipt.UnknownOutcome -> receipt.reason
                    }, Modifier.testTag("managed-removal-receipt"))
                }
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
                    else -> "当前聊天固定状态未知"
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
                    else -> "当前聊天归档状态未知"
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
    }
}

@Composable
private fun BranchCheckoutForm(controller: GitController, state: GitState) {
    val refs = (state.branches as? GitRead.Ready<List<GitBranch>>)?.value.orEmpty()
        .filter { !it.remote && it.ref.startsWith("refs/heads/") }.map { it.ref }
    GitFormSection("切换已有本地分支") {
        GitPageSelect(state.selectedBranchRef, listOf<String?>(null) + refs,
            { it ?: "选择已有分支" }, state.pageReady && state.checkoutAvailable &&
                !state.checkoutNeedsRescan && !state.creationNeedsRescan && !state.commitNeedsRescan &&
                !state.branchCreateNeedsRescan && refs.isNotEmpty(),
            "git-checkout-branch") { controller.selectBranch(it) }
        // `git-action-blocked-reason-tooltips.tsx`: Codex states why a git action is blocked in the
        // button's own hover tip, never as a standing line under it.
        HarnessTooltipTrigger(state.checkoutDisabledReason, popupTestTag = "git-checkout-disabled-reason") { tip ->
            HarnessButton("切换分支", controller::switchBranch, modifier = tip,
                enabled = state.pageReady && state.canSwitchBranch, testTag = "git-checkout-submit")
        }
        when (val checkout = state.checkout) {
            GitBranchCheckout.Idle -> Unit
            // A completed rescan leaves no note: Codex reports a git action's outcome, never a caption
            // telling the user they may now choose again.
            GitBranchCheckout.Rescanned -> Unit
            is GitBranchCheckout.Switching -> HarnessText("正在切换本地分支…", Modifier.testTag("git-checkout-busy"))
            is GitBranchCheckout.Switched -> GitActionOutcomeToast(checkout, "已切换到 ${checkout.receipt.status.branch?.let(::gitRefLabel) ?: "HEAD"}")
            is GitBranchCheckout.Failed -> GitActionOutcomeToast(checkout, checkout.failure.message, isError = true)
            is GitBranchCheckout.UnknownOutcome -> GitActionOutcomeToast(checkout, checkout.failure.message, isError = true)
        }
        // The outcome facts, nothing more: no sentence instructing the user what to do next.
        if (state.checkoutNeedsRescan) HarnessText("切换结果未知。",
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
    // `composer.footer.branchSwitch.createDialog.title` = 创建并检出分支. Codex's dialog is the
    // name field and the action; it carries no paragraph describing what the form would do.
    GitFormSection("创建并检出分支") {
        HarnessTextField(state.newBranchName, { controller.setNewBranchName(it) }, "分支名称",
            Modifier.fillMaxWidth(), enabled = editable, testTag = "git-branch-create-name")
        GitPageSelect(start, startOptions, { it }, editable && startOptions.size > 1, "git-branch-create-start") {
            controller.setNewBranchStart(it)
        }
        HarnessTooltipTrigger(state.branchCreateDisabledReason, popupTestTag = "git-branch-create-disabled-reason") { tip ->
            // `composer.footer.branchSwitch.createDialog.createAndCheckout` = 创建并检出.
            HarnessButton("创建并检出", controller::createBranch, modifier = tip,
                enabled = state.pageReady && state.canCreateBranch, testTag = "git-branch-create-submit")
        }
        when (val creation = state.branchCreation) {
            GitBranchCreation.Idle -> Unit
            GitBranchCreation.Rescanned -> Unit
            is GitBranchCreation.Creating -> HarnessText("正在创建并切换分支…", Modifier.testTag("git-branch-create-busy"))
            is GitBranchCreation.Created -> GitActionOutcomeToast(creation, "已创建并检出 ${creation.receipt.status.branch?.let(::gitRefLabel) ?: "HEAD"}")
            is GitBranchCreation.Failed -> GitActionOutcomeToast(creation, creation.failure.message, isError = true)
            is GitBranchCreation.UnknownOutcome -> GitActionOutcomeToast(creation, creation.failure.message, isError = true)
        }
        if (state.branchCreateNeedsRescan) HarnessText("新建分支结果未知。",
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
    when (operation) {
        is GitCommit.Verified -> GitActionOutcomeToast(operation,
            "已提交到 ${operation.receipt.snapshot.status.branch?.let(::gitRefLabel) ?: "HEAD"}")
        is GitCommit.Rejected -> GitActionOutcomeToast(operation, operation.failure.message, isError = true)
        is GitCommit.UnknownOutcome -> GitActionOutcomeToast(operation, operation.failure.message, isError = true)
        else -> Unit
    }
    // `review.commit.form.commitTitle` = 提交. Codex's commit surface is the title, the message field
    // and the action; it carries no standing paragraph describing what the form will do.
    GitFormSection("提交") {
        HarnessTooltipTrigger(observed.commitDisabledReason, popupTestTag = "git-commit-disabled-reason") { tip ->
            HarnessButton("提交变更…", { visible = true }, modifier = tip,
                enabled = repo != null && observed.commitAvailable && observed.unavailableReason == null, testTag = "git-commit-open")
        }
        if (!visible) CommitOutcome(operation)
    }
    HarnessDialog(visible, { visible = false }, "提交") {
        Column(Modifier.widthIn(max = 420.dp).fillMaxWidth().heightIn(max = 520.dp)
            .verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GitPageValue(repo?.root ?: "仓库不可用", "git-commit-repository")
            GitPageValue(snapshot?.status?.branch ?: "当前分支不可用", "git-commit-branch")
            ReadResult(observed.commitSnapshot, "尚未读取暂存区。") { value ->
                if (value.repository == repo) HarnessText("已暂存 ${value.stagedCount}", Modifier.testTag("git-commit-staged-count"))
            }
            // `review.commit.selection.includeUnstagedChanges` = 包含未暂存的更改. Codex pairs the
            // checkbox with that label alone — no paragraph explaining what checking it would stage.
            HarnessCheckbox(
                checked = observed.includeUnstaged,
                onCheckedChange = { controller.setIncludeUnstaged(it) },
                contentDescription = "包含未暂存的更改",
                enabled = !observed.pageMutationBusy && !observed.commitNeedsRescan && observed.commitAvailable,
                testTag = "git-commit-include-unstaged",
            )
            HarnessTextField(message, { message = it }, "提交消息", Modifier.fillMaxWidth().heightIn(min = 100.dp),
                enabled = !observed.pageMutationBusy && !observed.commitNeedsRescan && observed.commitAvailable,
                singleLine = false, testTag = "git-commit-message")
            HarnessButton("查看 Index diff", {
                controller.selectDiff(GitDiffRequest(GitDiffSource.Index)); visible = false
            }, enabled = observed.pageReady, testTag = "git-commit-index-diff",
                variant = HarnessButtonVariant.Secondary)
            HarnessTooltipTrigger(observed.commitDisabledReason, popupTestTag = "git-commit-dialog-reason") { tip ->
                // `review.commit.nextSteps.commit` = 提交: one action label, regardless of the checkbox.
                HarnessButton("提交", { controller.commit(message) },
                    modifier = tip, enabled = enabled && normalizeGitCommitMessage(message) != null, testTag = "git-commit-submit")
            }
            CommitOutcome(operation)
            HarnessButton("关闭", { visible = false }, testTag = "git-commit-close", variant = HarnessButtonVariant.Secondary)
        }
    }
}

@Composable
private fun CommitOutcome(commit: GitCommit) {
    when (commit) {
        GitCommit.Idle -> Unit
        // `review.commit.disabled.committing` = 正在提交… and `review.commit.successToast` reports the
        // branch; both are Codex's own, and neither explains what a success receipt already proves.
        is GitCommit.Submitting -> HarnessText("正在提交…", Modifier.testTag("git-commit-busy"))
        is GitCommit.Verified, is GitCommit.Rejected, is GitCommit.UnknownOutcome -> Unit
        GitCommit.Rescanned -> Unit
    }
}

@Composable
private fun WorktreeRootSection(state: WorktreeSettingsState?, onSave: (WorktreeSettings) -> Unit) {
    GitFormSection("工作树根目录") {

        if (state == null) {
            HarnessText("工作树设置服务不可用。", Modifier.testTag("worktree-root-unavailable"))
            return@GitFormSection
        }
        val saved = state.settings.root
        val toasts = LocalHarnessToastState.current
        var pendingSave by remember { mutableStateOf<Pair<Long, WorktreeSettings>?>(null) }
        LaunchedEffect(state.saveRevision, state.saving, state.saveError, pendingSave, toasts) {
            val pending = pendingSave ?: return@LaunchedEffect
            if (!state.saving) {
                if (state.saveError != null) {
                    toasts?.show(state.saveError, isError = true)
                    pendingSave = null
                } else if (state.saveRevision > pending.first && state.settings == pending.second) {
                    toasts?.show("已保存工作树根目录。")
                    pendingSave = null
                }
            }
        }
        // Re-seed the draft whenever the persisted value changes (load or a completed save).
        var draft by remember(state.loaded, saved) { mutableStateOf(saved) }
        val error = worktreeRootError(draft)
        val changed = worktreeRootValue(draft) != worktreeRootValue(saved)
        HarnessTextField(draft, { draft = it }, "工作树根目录（留空使用默认位置）", Modifier.fillMaxWidth(),
            enabled = state.canEdit, isError = error != null, supportingText = error,
            testTag = "worktree-root-input")
        HarnessButton("保存根目录", {
            val next = state.settings.copy(root = worktreeRootValue(draft))
            pendingSave = state.saveRevision to next
            onSave(next)
        }, enabled = state.canEdit && changed && error == null, testTag = "worktree-root-save")
        state.loadError?.let { HarnessText(it, Modifier.testTag("worktree-root-load-error"), color = HarnessTokens.Colors.Danger) }
    }
}

/**
 * The original page's "Environment" rows. Each control persists one stored preference through the
 * same repository as the root field; the destructive pruning and the pre-create fetch are backend
 * behavior that this page does not run.
 */
@Composable
private fun WorktreeEnvironmentSection(state: WorktreeSettingsState?, onSave: (WorktreeSettings) -> Unit) {
    GitFormSection("环境") {
        if (state == null) {
            HarnessText("工作树设置服务不可用。", Modifier.testTag("worktree-env-unavailable"))
            return@GitFormSection
        }
        val settings = state.settings
        val toasts = LocalHarnessToastState.current
        var pendingSave by remember { mutableStateOf<Triple<Long, WorktreeSettings, String>?>(null) }
        val saveSettings: (WorktreeSettings, String) -> Unit = { next, message ->
            pendingSave = Triple(state.saveRevision, next, message)
            onSave(next)
        }
        LaunchedEffect(state.saveRevision, state.saving, state.saveError, pendingSave, toasts) {
            val pending = pendingSave ?: return@LaunchedEffect
            if (!state.saving) {
                if (state.saveError != null) {
                    toasts?.show(state.saveError, isError = true)
                    pendingSave = null
                } else if (state.saveRevision > pending.first && settings == pending.second) {
                    toasts?.show(pending.third)
                    pendingSave = null
                }
            }
        }

        // `settings.worktrees.autoCleanup.label` / `.description`; the original confirms before
        // disabling it.
        var confirmDisable by remember(settings.autoCleanupEnabled) { mutableStateOf(false) }
        SettingsFormRow("自动删除旧工作树", "推荐大多数用户启用。仅当你需要手动管理旧工作树和磁盘使用空间时，再关闭此功能。",
            modifier = Modifier.testTag("worktree-auto-cleanup-label")) {
            HarnessSwitch(
                checked = settings.autoCleanupEnabled,
                onCheckedChange = { next -> if (next) saveSettings(settings.copy(autoCleanupEnabled = true), "自动删除功能已启用") else confirmDisable = true },
                contentDescription = "自动删除旧工作树", enabled = state.canEdit,
                testTag = "worktree-auto-cleanup-switch")
        }
        // "Auto-delete limit"; the value must stay an integer >= 1 (the original schema).
        val savedKeep = settings.keepCount.toString()
        var draft by remember(state.loaded, savedKeep) { mutableStateOf(savedKeep) }
        val keepError = keepCountError(draft)
        val keepChanged = keepCountValue(draft) != settings.keepCount
        SettingsFormRow("自动删除限制", "自动清理较旧工作树前保留的工作树数量。",
            modifier = Modifier.testTag("worktree-keep-count-label")) {
            Column(Modifier.widthIn(max = 240.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HarnessTextField(draft, { draft = it }, "保留数量", Modifier.fillMaxWidth(),
                    enabled = state.canEdit, isError = keepError != null, supportingText = keepError,
                    testTag = "worktree-keep-count-input")
                HarnessButton("保存", { keepCountValue(draft)?.let { saveSettings(settings.copy(keepCount = it), "已保存自动删除限制") } },
                    variant = HarnessButtonVariant.Secondary, size = HarnessControlSize.Compact,
                    enabled = state.canEdit && keepChanged && keepError == null, testTag = "worktree-keep-count-save")
            }
        }
        // "Always fetch upstream before creating worktrees".
        SettingsFormRow("创建前获取上游更新", "创建前尝试获取远程分支的最新提交。",
            modifier = Modifier.testTag("worktree-upstream-refresh-label")) {
            HarnessSwitch(
                checked = settings.upstreamRefreshMode == WorktreeUpstreamRefreshMode.BestEffort,
                onCheckedChange = { next -> saveSettings(settings.copy(
                    upstreamRefreshMode = if (next) WorktreeUpstreamRefreshMode.BestEffort else WorktreeUpstreamRefreshMode.Never),
                    "已保存工作树设置。") },
                contentDescription = "创建工作树前始终获取上游更新", enabled = state.canEdit,
                testTag = "worktree-upstream-refresh-switch")
        }
        state.loadError?.let { HarnessText(it, Modifier.testTag("worktree-env-load-error"), color = HarnessTokens.Colors.Danger) }

        HarnessConfirmDialog(
            visible = confirmDisable,
            onDismissRequest = { confirmDisable = false },
            title = "禁用工作树自动删除功能？",
            description = "关闭后需要手动清理旧工作树，已保存的保留数量不会丢失。",
            cancelLabel = "保持开启",
            confirmLabel = "禁用自动删除功能",
            submitState = HarnessConfirmSubmitState.Ready,
            onCancel = { confirmDisable = false },
            onConfirm = { confirmDisable = false; saveSettings(settings.copy(autoCleanupEnabled = false), "自动删除功能已禁用") },
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
    GitFormSection("创建本地工作树") {
        // A host without the creation capability leaves the fields and action disabled; Codex states
        // no cause beside an unavailable control, so no sentence explains the missing capability.
        HarnessTextField(path, { path = it; pathTouched = true }, "新工作树绝对路径（父目录须已存在）", Modifier.fillMaxWidth(),
            enabled = state.pageReady && state.canCreateWorktree, testTag = "git-worktree-create-path")
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
            is GitWorktreeCreation.Created -> GitActionOutcomeToast(creation, "已创建工作树：${creation.receipt.worktree.path}")
            is GitWorktreeCreation.Failed -> GitActionOutcomeToast(creation, creation.failure.failure.message, isError = true)
        }
        if (state.creationNeedsRescan) HarnessText("创建结果未知。",
            Modifier.testTag("git-worktree-create-needs-rescan"), color = HarnessTokens.Colors.Danger)
    }
}

@Composable
private fun RepositoryControls(controller: GitController, state: GitState, directoryPicker: (@Composable () -> Unit)?) {
    var cwd by remember(state.binding?.identity, state.binding?.hostId, state.selectedCwd) {
        mutableStateOf(state.selectedCwd.orEmpty())
    }
    var editing by remember(state.binding) { mutableStateOf(false) }
    val repository = (state.repository as? GitRead.Ready<GitRepository>)?.value
    GitFormSection("仓库") {
        state.unavailableReason?.let { HarnessText(it, Modifier.testTag("git-unavailable")) }
        ReadResult(state.repository, "选择一个本地仓库开始。") { repo ->
            GitPageValue(repo.root, "git-repository-root")
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (directoryPicker != null) Box(Modifier.width(144.dp)) { directoryPicker() }
            HarnessButton(if (editing) "收起输入" else "输入目录", { editing = !editing },
                variant = HarnessButtonVariant.Secondary, testTag = "git-repository-edit")
            HarnessButton("刷新", controller::refresh, enabled = state.canRefresh && state.pageCanRead,
                variant = HarnessButtonVariant.Secondary, testTag = "git-refresh")
        }
        if (repository == null || editing) {
            HarnessTextField(cwd, { cwd = it }, "仓库目录", Modifier.fillMaxWidth(),
                enabled = state.pageCanRead, testTag = "git-repository-path")
            HarnessButton("读取仓库", { editing = true; controller.selectRepository(cwd) },
                enabled = state.pageCanRead && cwd.isNotBlank() && cwd != state.selectedCwd,
                variant = HarnessButtonVariant.Secondary, size = HarnessControlSize.Compact,
                testTag = "git-repository-select")
        }
        if (repository != null) SettingsFormDetails("仓库详情", "git-repository-details") {
            GitPageValue("Git 目录：${repository.gitDir}", "git-repository-git-dir", muted = true)
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

/** Standalone Git pages share the application's toast host when available. */
@Composable
private fun GitPageToasts(modifier: Modifier, content: @Composable () -> Unit) {
    val applicationToasts = LocalHarnessToastState.current
    val localToasts = rememberHarnessToastState()
    CompositionLocalProvider(LocalHarnessToastState provides (applicationToasts ?: localToasts)) {
        Box(modifier) {
            content()
            if (applicationToasts == null) HarnessToastHost(localToasts, Modifier.align(Alignment.TopCenter))
        }
    }
}

@Composable
private fun GitActionOutcomeToast(outcome: Any, message: String, isError: Boolean = false) {
    val toasts = LocalHarnessToastState.current
    LaunchedEffect(outcome, toasts) { toasts?.show(message, isError = isError) }
}
