package im.hikaru.harness.desktop

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import im.hikaru.harness.client.connection.SessionId
import im.hikaru.harness.client.connection.SessionSummary
import im.hikaru.harness.client.git.*
import im.hikaru.harness.ui.foundation.HarnessTheme
import kotlinx.coroutines.CompletableDeferred
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class WorktreePageInteractionTest {
    @Test
    fun ordinarySelectionReadsTheFullPathAndBlocksWhilePending() {
        val f = GitWorktreesPageFixture()
        val gate = CompletableDeferred<Unit>()
        try {
            f.backend.extraWorktrees = listOf(GitWorktree(GitWorktreesPageFixture.longRoot,
                GitWorktreesPageFixture.oid, GitWorktreesPageFixture.longRef),
                GitWorktree("/test-only/bare", null, null, bare = true),
                GitWorktree("/test-only/prunable", null, null, prunable = true))
            f.git.refresh()
            runSkikoComposeUiTest(size = Size(600f, 800f)) {
                setContent { HarnessTheme { WorktreesPage(f.git, managedController = f.managed) } }
                waitUntil { f.managed.state.value.phase == ManagedWorktreePhase.Ready }
                scrollGitNodeIntoView(onAllNodesWithTag("git-worktree-select")[2], "git-worktrees-list").assertIsNotEnabled()
                scrollGitNodeIntoView(onAllNodesWithTag("git-worktree-select")[3], "git-worktrees-list").assertIsNotEnabled()
                runOnIdle { f.backend.discoveryGate = gate }
                val before = f.backend.discoveries.size
                clickGitNode(scrollGitNodeIntoView(onAllNodesWithTag("git-worktree-select")[1], "git-worktrees-list").assertIsEnabled())
                waitUntil { f.git.state.value.repository is GitRead.Loading }
                assertEquals(before + 1, f.backend.discoveries.size)
                assertEquals(GitWorktreesPageFixture.longRoot, f.git.state.value.selectedCwd)
                assertEquals(GitWorktreesPageFixture.binding to GitWorktreesPageFixture.longRoot, f.backend.discoveries.last())
                onNodeWithTag("git-refresh").assertIsNotEnabled()
                onNodeWithTag("git-worktree-create-submit").assertIsNotEnabled()
                onNodeWithTag("managed-refresh").assertIsNotEnabled()
                assertTrue(f.backend.creations.isEmpty())
                assertTrue(f.backend.switches.isEmpty())
                runOnIdle { gate.complete(Unit) }
                waitUntil { f.ready() && f.managed.state.value.phase == ManagedWorktreePhase.Ready }
                assertEquals(GitWorktreesPageFixture.longRoot, f.managedReads.calls.last().root)
                onNodeWithTag("git-repository-root").assertTextEquals(GitWorktreesPageFixture.longRoot)
                onNodeWithText("此列表不证明", substring = true).assertExists()
            }
        } finally { gate.complete(Unit); f.close() }
    }

    @Test
    fun managedRefreshIsSingleFlightAndStaleOwnershipLosesVerifiedClaim() {
        val f = GitWorktreesPageFixture()
        val gate = CompletableDeferred<Unit>()
        try {
            f.managedReads.ownerships = listOf(ManagedOwnership.Managed)
            runSkikoComposeUiTest(size = Size(600f, 800f)) {
                setContent { HarnessTheme { WorktreesPage(f.git, managedController = f.managed) } }
                waitUntil { f.managed.state.value.phase == ManagedWorktreePhase.Ready }
                onNodeWithTag("managed-count").assertTextContains("已核验 Managed 1", substring = true)
                onNodeWithTag("managed-ownership").assertTextEquals("Managed 归属已核验")
                val before = f.managedReads.calls.size
                runOnIdle { f.managedReads.gate = gate; f.managedReads.failure = ManagedWorktreeRead.Failed("inventory service unavailable", unavailable = true) }
                onNodeWithTag("managed-refresh").performScrollTo().performMouseInput { click() }
                onNodeWithTag("managed-refresh").assertIsNotEnabled().performMouseInput { click() }
                assertEquals(before + 1, f.managedReads.calls.size)
                onNodeWithTag("managed-stale").assertExists()
                onNodeWithTag("managed-count").assertTextContains("当前核验数量未知", substring = true)
                onNodeWithText("Managed 归属已核验").assertDoesNotExist()
                runOnIdle { gate.complete(Unit) }
                waitUntil { f.managed.state.value.phase == ManagedWorktreePhase.Unavailable }
                onNodeWithTag("managed-unavailable").assertTextEquals("inventory service unavailable")
                onNodeWithTag("managed-ownership").assertTextEquals("上次归属为 Managed · 当前未核验")
                onNodeWithTag("managed-refresh").assertIsEnabled()
                runOnIdle { f.managedReads.failure = null; f.managedReads.gate = null }
                onNodeWithTag("managed-refresh").performMouseInput { click() }
                waitUntil { f.managed.state.value.phase == ManagedWorktreePhase.Ready }
                onNodeWithTag("managed-stale").assertDoesNotExist()
                onNodeWithTag("managed-count").assertTextContains("已核验 Managed 1", substring = true)
                assertEquals(before + 2, f.managedReads.calls.size)
            }
        } finally { gate.complete(Unit); f.close() }
    }

    @Test
    fun ownershipVariantsAndProtectionAvailabilityAreDistinctAndNeverDestructive() {
        val f = GitWorktreesPageFixture()
        val gate = CompletableDeferred<Unit>()
        try {
            f.managedReads.ownerships = ManagedOwnership.entries.toList()
            runSkikoComposeUiTest(size = Size(600f, 800f)) {
                setContent { HarnessTheme { WorktreesPage(f.git, managedController = f.managed) } }
                waitUntil { f.managed.state.value.phase == ManagedWorktreePhase.Ready }
                for (label in listOf("Managed 归属已核验", "归属未知", "登记与当前 Git 指纹不匹配", "当前 Git 列表未找到此登记路径")) {
                    scrollGitNodeIntoView(onNodeWithText(label), "managed-inventory-list").assertIsDisplayed()
                }
                onAllNodesWithTag("managed-sessions-availability")[0].assertTextEquals("保护状态未知")
                onAllNodesWithTag("managed-pins-availability")[0].assertTextEquals("保护服务不可用")
                scrollGitNodeIntoView(onNodeWithTag("managed-destructive-unavailable")).assertIsDisplayed()
                for (tag in listOf("managed-delete", "managed-archive-submit", "managed-cleanup", "managed-pin", "managed-snapshot")) {
                    onNodeWithTag(tag).assertDoesNotExist()
                }
                assertTrue(f.backend.creations.isEmpty())
                runOnIdle { f.managedReads.gate = gate }
                clickGitNode(scrollGitNodeIntoView(onNodeWithTag("managed-refresh")))
                onNodeWithTag("managed-refresh").assertIsNotEnabled()
                onNodeWithTag("managed-count").assertTextContains("当前核验数量未知", substring = true)
                for (label in listOf("上次归属为 Managed · 当前未核验", "上次归属未知 · 当前未核验",
                    "上次登记与 Git 指纹不匹配 · 当前未核验", "上次 Git 列表未找到此登记路径 · 当前未核验")) {
                    scrollGitNodeIntoView(onNodeWithText(label), "managed-inventory-list").assertIsDisplayed()
                }
                onNodeWithText("登记与当前 Git 指纹不匹配").assertDoesNotExist()
                onNodeWithText("当前 Git 列表未找到此登记路径").assertDoesNotExist()
            }
        } finally { gate.complete(Unit); f.close() }
    }

    @Test
    fun createdReceiptShowsActualRootAndBranchWithoutClaimingSetupOrAttachment() {
        val f = GitWorktreesPageFixture()
        val gate = CompletableDeferred<Unit>()
        try {
            f.backend.creationGate = gate
            runSkikoComposeUiTest(size = Size(600f, 800f)) {
                setContent { HarnessTheme { WorktreesPage(f.git, managedController = f.managed) } }
                waitUntil { f.managed.state.value.phase == ManagedWorktreePhase.Ready }
                onNodeWithTag("git-worktree-create-path").performScrollTo().performTextReplacement(GitWorktreesPageFixture.longRoot)
                onNodeWithTag("git-worktree-create-branch").performScrollTo().performTextReplacement("codex/typed-receipt")
                assertTrue(f.backend.creations.isEmpty())
                onNodeWithTag("git-worktree-create-submit").performScrollTo().performMouseInput { click() }
                waitUntil { f.git.state.value.creationBusy }
                onNodeWithTag("git-worktree-create-submit").assertIsNotEnabled().performMouseInput { click() }
                onNodeWithTag("git-worktree-create-path").assertIsNotEnabled()
                onNodeWithTag("git-worktree-create-branch").assertIsNotEnabled()
                onNodeWithTag("git-worktree-create-start").assertIsNotEnabled()
                onNodeWithTag("git-repository-select").assertIsNotEnabled()
                onNodeWithTag("managed-refresh").assertIsNotEnabled()
                onNodeWithTag("git-worktree-create-success").assertDoesNotExist()
                assertEquals(1, f.backend.creations.size)
                assertEquals(GitWorktreesPageFixture.repository(), f.backend.creations.single().first)
                assertEquals(GitCreateWorktreeRequest(GitWorktreesPageFixture.binding, GitWorktreesPageFixture.longRoot,
                    "codex/typed-receipt", "HEAD"), f.backend.creations.single().second)
                runOnIdle { gate.complete(Unit) }
                waitUntil { f.git.state.value.creation is GitWorktreeCreation.Created && f.ready() }
                onNodeWithTag("git-worktree-create-success").performScrollTo().assertTextEquals("已创建：${GitWorktreesPageFixture.longRoot}")
                onNodeWithTag("git-worktree-created-branch").assertTextEquals("refs/heads/codex/typed-receipt")
                onNodeWithTag("git-worktree-created-boundary").assertTextContains("环境设置与会话关联尚未确认", substring = true)
                onNodeWithText("Managed 归属已核验").assertDoesNotExist()
                assertEquals(GitWorktreesPageFixture.longRoot, f.git.state.value.selectedCwd)
            }
        } finally { gate.complete(Unit); f.close() }
    }

    @Test
    fun pathExistsFailurePreservesDraftAndPermitsExplicitRetry() {
        creationFailure(needsRescan = false)
    }

    @Test
    fun uncertainCreationPreservesDraftAndRequiresRescanWithoutSuccessClaim() {
        creationFailure(needsRescan = true)
    }

    private fun creationFailure(needsRescan: Boolean) {
        val f = GitWorktreesPageFixture()
        try {
            f.backend.creationFailure = GitCreateWorktreeFailure(GitFailure(GitFailureKind.InvalidRequest,
                if (needsRescan) "创建结果未知" else "目标路径已存在"), needsRescan)
            runSkikoComposeUiTest(size = Size(600f, 800f)) {
                setContent { HarnessTheme { WorktreesPage(f.git, managedController = f.managed) } }
                waitUntil { f.managed.state.value.phase == ManagedWorktreePhase.Ready }
                onNodeWithTag("git-worktree-create-path").performScrollTo().performTextReplacement(GitWorktreesPageFixture.longRoot)
                onNodeWithTag("git-worktree-create-branch").performScrollTo().performTextReplacement("codex/keep-draft")
                onNodeWithTag("git-worktree-create-submit").performScrollTo().performMouseInput { click() }
                waitUntil { f.git.state.value.creation is GitWorktreeCreation.Failed && !f.git.state.value.creationBusy }
                onNodeWithTag("git-worktree-create-error").assertTextEquals(if (needsRescan) "创建结果未知" else "目标路径已存在")
                onNodeWithTag("git-worktree-create-path").assertTextEquals(GitWorktreesPageFixture.longRoot)
                onNodeWithTag("git-worktree-create-branch").assertTextEquals("codex/keep-draft")
                onNodeWithTag("git-worktree-create-success").assertDoesNotExist()
                onNodeWithTag("git-worktree-created-boundary").assertDoesNotExist()
                if (needsRescan) {
                    onNodeWithTag("git-worktree-create-needs-rescan").assertExists()
                    onNodeWithTag("git-worktree-create-submit").assertIsNotEnabled().performMouseInput { click() }
                    onNodeWithTag("git-refresh").performScrollTo().assertIsEnabled().performMouseInput { click() }
                    waitUntil { !f.git.state.value.creationNeedsRescan && f.ready() }
                    onNodeWithTag("git-worktree-create-path").assertTextEquals(GitWorktreesPageFixture.longRoot)
                    onNodeWithTag("git-worktree-create-success").assertDoesNotExist()
                }
                onNodeWithTag("git-worktree-create-submit").performScrollTo().assertIsEnabled()
                assertEquals(1, f.backend.creations.size)
                assertEquals(GitWorktreesPageFixture.root, f.git.state.value.selectedCwd)
            }
        } finally { f.close() }
    }

    @Test
    fun readFailureIsDistinctFromEmptyAndMissingManagedServiceHasNoRefresh() {
        val f = GitWorktreesPageFixture()
        try {
            f.backend.worktreesFailure = GitFailure(GitFailureKind.CommandFailed, "worktree inventory failed")
            f.git.refresh()
            runSkikoComposeUiTest(size = Size(600f, 800f)) {
                setContent { HarnessTheme { WorktreesPage(f.git) } }
                onNodeWithTag("managed-unavailable").assertTextEquals("Managed 登记服务不可用。")
                onNodeWithTag("managed-refresh").assertDoesNotExist()
                onNodeWithTag("git-worktrees-empty").assertDoesNotExist()
                onNodeWithText("worktree inventory failed").assertExists()
                assertTrue(f.managedReads.calls.isEmpty())
                runOnIdle { f.backend.worktreesFailure = null; f.backend.emptyWorktrees = true }
                onNodeWithTag("git-refresh").performScrollTo().performMouseInput { click() }
                waitUntil { f.ready() }
                onNodeWithTag("git-worktrees-empty").assertExists()
                onNodeWithText("worktree inventory failed").assertDoesNotExist()
            }
        } finally { f.close() }
    }

    @Test
    fun knownConversationsListOnlyThatWorktreeAndOpenItsHistory() {
        val f = GitWorktreesPageFixture()
        val opened = mutableListOf<SessionSummary>()
        try {
            f.backend.extraWorktrees = listOf(GitWorktree("/test-only/secondary", GitWorktreesPageFixture.oid,
                "refs/heads/secondary"))
            f.git.refresh()
            val primary = SessionSummary(SessionId("chat-primary"), 5_000L, cwd = GitWorktreesPageFixture.root)
            val elsewhere = SessionSummary(SessionId("chat-elsewhere"), 9_000L, cwd = "/somewhere/else")
            runSkikoComposeUiTest(size = Size(600f, 800f)) {
                setContent { HarnessTheme { WorktreesPage(f.git, sessions = listOf(elsewhere, primary),
                    onOpenSession = { opened += it }) } }
                waitUntil { f.ready() }
                val tag = "worktree-conversations-${GitWorktreesPageFixture.root}"
                scrollGitNodeIntoView(onNodeWithTag(tag), "git-worktrees-list").assertIsDisplayed()
                // Only the session whose cwd is this worktree dir is listed; the other one is not.
                onNodeWithTag("worktree-conversation-chat-primary", true).assertExists()
                onNodeWithTag("worktree-conversation-chat-elsewhere", true).assertDoesNotExist()
                // A worktree with no matching session keeps the explicit empty text.
                onNodeWithTag("worktree-conversations-empty-/test-only/secondary", true).assertExists()
                clickGitNode(scrollGitNodeIntoView(onNodeWithTag("worktree-conversation-chat-primary"),
                    "git-worktrees-list"))
                assertEquals(listOf(primary), opened)
            }
        } finally { f.close() }
    }

    @Test
    fun newChatBindsThisWorktreeDirectoryAndIsUnavailableForBareOrPrunable() {
        val f = GitWorktreesPageFixture()
        val requested = mutableListOf<String>()
        try {
            f.backend.extraWorktrees = listOf(GitWorktree(GitWorktreesPageFixture.longRoot,
                GitWorktreesPageFixture.oid, GitWorktreesPageFixture.longRef),
                GitWorktree("/test-only/bare", null, null, bare = true),
                GitWorktree("/test-only/prunable", null, null, prunable = true))
            f.git.refresh()
            runSkikoComposeUiTest(size = Size(600f, 800f)) {
                setContent { HarnessTheme { WorktreesPage(f.git, managedController = f.managed,
                    onNewChatInWorktree = { requested += it }) } }
                waitUntil { f.ready() && f.managed.state.value.phase == ManagedWorktreePhase.Ready }
                // A bare or prunable checkout is not a usable working directory: the row action stays off.
                scrollGitNodeIntoView(onAllNodesWithTag("git-worktree-new-chat")[2], "git-worktrees-list").assertIsNotEnabled()
                scrollGitNodeIntoView(onAllNodesWithTag("git-worktree-new-chat")[3], "git-worktrees-list").assertIsNotEnabled()
                assertTrue(requested.isEmpty())
                clickGitNode(scrollGitNodeIntoView(onAllNodesWithTag("git-worktree-new-chat")[1],
                    "git-worktrees-list").assertIsEnabled())
                assertEquals(listOf(GitWorktreesPageFixture.longRoot), requested)
                // Binding a directory is not a session, checkout or creation side effect.
                assertTrue(f.backend.creations.isEmpty())
                assertTrue(f.backend.switches.isEmpty())
            }
        } finally { f.close() }
    }

    @Test
    fun managedNewChatBindsVerifiedRootAndIsUnavailableForMissingOwnership() {
        val f = GitWorktreesPageFixture()
        val requested = mutableListOf<String>()
        try {
            // A Managed row with a real Git entry can seed a chat; a Missing registration has no entry.
            f.managedReads.ownerships = listOf(ManagedOwnership.Managed, ManagedOwnership.Missing)
            runSkikoComposeUiTest(size = Size(600f, 800f)) {
                setContent { HarnessTheme { WorktreesPage(f.git, managedController = f.managed,
                    onNewChatInWorktree = { requested += it }) } }
                waitUntil { f.managed.state.value.phase == ManagedWorktreePhase.Ready }
                scrollGitNodeIntoView(onAllNodesWithTag("managed-worktree-new-chat")[1],
                    "managed-inventory-list").assertIsNotEnabled()
                assertTrue(requested.isEmpty())
                clickGitNode(scrollGitNodeIntoView(onAllNodesWithTag("managed-worktree-new-chat")[0],
                    "managed-inventory-list").assertIsEnabled())
                assertEquals(listOf(GitWorktreesPageFixture.root), requested)
                assertTrue(f.backend.creations.isEmpty())
                assertTrue(f.backend.switches.isEmpty())
            }
        } finally { f.close() }
    }
}
