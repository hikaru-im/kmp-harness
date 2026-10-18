package im.hikaru.harness.desktop

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import im.hikaru.harness.client.git.*
import im.hikaru.harness.desktop.git.DesktopGitBackend
import im.hikaru.harness.desktop.git.GitCommandRunner
import im.hikaru.harness.ui.foundation.HarnessTheme
import java.nio.file.Files
import kotlinx.coroutines.*
import kotlin.test.*

/** Pointer tests on production pages/controllers, controlled adapters only. No repository mutation. */
@OptIn(ExperimentalTestApi::class)
class GitPageInteractionTest {
    @Test
    fun typedRepositorySelectionRefreshAndLoadingAreBoundToTheActualScope() {
        val f = GitWorktreesPageFixture(select = false)
        val gate = CompletableDeferred<Unit>()
        try {
            f.backend.discoveryGate = gate
            runSkikoComposeUiTest(size = Size(600f, 800f)) {
                setContent { HarnessTheme { SettingsGitPage(f.git) } }
                onNodeWithTag("git-refresh").assertIsNotEnabled()
                onNodeWithTag("git-repository-path").performMouseInput { click() }
                onNodeWithTag("git-repository-path").performTextReplacement(GitWorktreesPageFixture.longRoot)
                assertTrue(f.backend.discoveries.isEmpty(), "typing is only a draft")
                onNodeWithTag("git-repository-select").assertIsEnabled().performMouseInput { click() }
                waitUntil { f.git.state.value.repository is GitRead.Loading }
                revealRepositoryInput()
                onNodeWithTag("git-repository-select").assertIsNotEnabled().performMouseInput { click() }
                onNodeWithTag("git-repository-path").assertIsNotEnabled()
                onNodeWithTag("git-refresh").assertIsNotEnabled().performMouseInput { click() }
                onNodeWithTag("git-checkout-branch").assertIsNotEnabled()
                onNodeWithTag("git-diff-source").assertIsNotEnabled()
                assertEquals(listOf(GitWorktreesPageFixture.binding to GitWorktreesPageFixture.longRoot), f.backend.discoveries)
                runOnIdle { gate.complete(Unit) }
                waitUntil { f.ready() }
                onNodeWithTag("git-repository-root").assertTextEquals(GitWorktreesPageFixture.longRoot)
                val before = f.backend.statusReads.size
                val readGate = CompletableDeferred<Unit>()
                runOnIdle { f.backend.readGate = readGate }
                onNodeWithTag("git-refresh").performScrollTo().assertIsEnabled().performMouseInput { click() }
                onNodeWithTag("git-refresh").assertIsNotEnabled().performMouseInput { click() }
                revealRepositoryInput()
                onNodeWithTag("git-repository-select").assertIsNotEnabled()
                onNodeWithTag("git-diff-source").assertIsNotEnabled()
                onNodeWithTag("git-clean").assertDoesNotExist()
                assertEquals(before + 1, f.backend.statusReads.size)
                runOnIdle { readGate.complete(Unit) }
                waitUntil { f.ready() }
                assertEquals(GitWorktreesPageFixture.binding, f.backend.statusReads.last().binding)
                assertEquals(GitWorktreesPageFixture.longRoot, f.backend.statusReads.last().root)
            }
        } finally { gate.complete(Unit); f.close() }
    }

    @Test
    fun failedDiscoveryRemainsRetryableAndDoesNotRenderCleanOrCounts() {
        for ((kind, tag) in listOf(GitFailureKind.NotRepository to "git-not-repository",
            GitFailureKind.Unavailable to "git-read-unavailable", GitFailureKind.CommandFailed to "git-read-failed")) {
            val f = GitWorktreesPageFixture(select = false)
            try {
                f.backend.discoveryFailure = GitFailure(kind, "controlled discovery failure")
                f.git.selectRepository(GitWorktreesPageFixture.root)
                runSkikoComposeUiTest(size = Size(600f, 800f)) {
                    setContent { HarnessTheme { SettingsGitPage(f.git) } }
                    onNodeWithTag(tag).assertExists()
                    onNodeWithTag("git-error").assertTextEquals("controlled discovery failure")
                    onNodeWithTag("git-clean").assertDoesNotExist()
                    listOf("已暂存 0", "未暂存 0", "未跟踪 0").forEach { onNodeWithText(it).assertDoesNotExist() }
                    onNodeWithTag("git-diff-empty").assertDoesNotExist()
                    onNodeWithTag("git-diff-source").assertIsNotEnabled()
                    runOnIdle { f.backend.discoveryFailure = null }
                    onNodeWithTag("git-refresh").performScrollTo().assertIsEnabled().performMouseInput { click() }
                    waitUntil { f.ready() }
                    assertEquals(2, f.backend.discoveries.size)
                    onNodeWithTag("git-error").assertDoesNotExist()
                    onNodeWithTag("git-clean").assertExists()
                }
            } finally { f.close() }
        }
    }

    @Test
    fun longBranchMenuSelectionIsReadOnlyAndExplicitSwitchIsSingleFlight() {
        val f = GitWorktreesPageFixture()
        val gate = CompletableDeferred<Unit>()
        try {
            f.backend.switchGate = gate
            f.backend.extraRefs = (1..30).map { GitBranch("refs/heads/extra-$it", GitWorktreesPageFixture.oid, false, false) }
            f.git.refresh()
            runSkikoComposeUiTest(size = Size(600f, 800f)) {
                setContent { HarnessTheme { SettingsGitPage(f.git) } }
                onNodeWithTag("git-checkout-submit").performScrollTo().assertIsNotEnabled()
                onNodeWithTag("git-checkout-branch").performScrollTo().performMouseInput { click() }
                onNodeWithTag("menu:32").performScrollTo().assertTextEquals("refs/heads/extra-30")
                onNodeWithTag("menu:2").performScrollTo().assertTextEquals(GitWorktreesPageFixture.longRef).performMouseInput { click() }
                waitUntil { f.git.state.value.selectedBranchRef == GitWorktreesPageFixture.longRef }
                assertTrue(f.backend.switches.isEmpty())
                onNodeWithTag("git-checkout-branch-value").assertTextEquals(GitWorktreesPageFixture.longRef)
                onNodeWithTag("git-checkout-submit").performScrollTo().assertIsEnabled().performMouseInput { click() }
                waitUntil { f.git.state.value.checkoutBusy }
                onNodeWithTag("git-checkout-submit").assertIsNotEnabled().performMouseInput { click() }
                onNodeWithTag("git-checkout-branch").assertIsNotEnabled()
                onNodeWithTag("git-refresh").assertIsNotEnabled()
                onNodeWithTag("git-diff-source").assertIsNotEnabled()
                assertEquals(1, f.backend.switches.size)
                val (repo, request) = f.backend.switches.single()
                assertEquals(repo.binding, request.binding)
                assertEquals(repo.root, request.root)
                assertEquals(GitWorktreesPageFixture.longRef, request.targetRef)
                runOnIdle { gate.complete(Unit) }
                waitUntil { f.ready() && !f.git.state.value.checkoutBusy }
                assertIs<GitBranchCheckout.Switched>(f.git.state.value.checkout)
                onNodeWithTag("git-checkout-submit").assertIsNotEnabled()
            }
        } finally { gate.complete(Unit); f.close() }
    }

    @Test
    fun diffSourceAndFullBaseReferenceReachTheAdapterAndBusyBlocksDuplicates() {
        val f = GitWorktreesPageFixture()
        val gate = CompletableDeferred<Unit>()
        try {
            runSkikoComposeUiTest(size = Size(600f, 800f)) {
                setContent { HarnessTheme { SettingsGitPage(f.git) } }
                onNodeWithTag("git-diff-source").performScrollTo().performMouseInput { click() }
                onNodeWithTag("menu:1").performMouseInput { click() }
                waitUntil { f.git.state.value.diffRequest.source == GitDiffSource.Index && f.ready() }
                assertEquals(GitDiffRequest(GitDiffSource.Index), f.backend.diffReads.last().second)
                onNodeWithTag("git-diff-source").performScrollTo().performMouseInput { click() }
                onNodeWithTag("menu:2").performMouseInput { click() }
                waitUntil { f.git.state.value.diffRequest.source == GitDiffSource.BaseBranch && f.git.state.value.diff !is GitRead.Loading }
                assertIs<GitRead.Failed>(f.git.state.value.diff)
                val before = f.backend.diffReads.size
                runOnIdle { f.backend.diffGate = gate }
                onNodeWithTag("git-diff-base").performScrollTo().performMouseInput { click() }
                onNodeWithTag("menu:2").performScrollTo().performMouseInput { click() }
                waitUntil { f.git.state.value.diff is GitRead.Loading }
                onNodeWithTag("git-diff-source").performScrollTo().assertIsNotEnabled().performMouseInput { click() }
                onNodeWithTag("git-diff-base").performScrollTo().assertIsNotEnabled().performMouseInput { click() }
                onNodeWithTag("git-refresh").assertIsNotEnabled()
                assertEquals(before + 1, f.backend.diffReads.size)
                assertEquals(GitDiffRequest(GitDiffSource.BaseBranch, GitWorktreesPageFixture.longRef), f.backend.diffReads.last().second)
                assertEquals(GitWorktreesPageFixture.repository(), f.backend.diffReads.last().first)
                runOnIdle { gate.complete(Unit) }
                waitUntil { f.ready() }
            }
        } finally { gate.complete(Unit); f.close() }
    }

    @Test
    fun diffOptionsHideWhitespaceReReadsThePatchWithWhitespaceIgnored() {
        val f = GitWorktreesPageFixture()
        try {
            f.backend.patch = "diff --git a/a.txt b/a.txt\n--- a/a.txt\n+++ b/a.txt\n@@ -1 +1 @@\n-old indented\n+new indented\n"
            f.git.refresh()
            runSkikoComposeUiTest(size = Size(1000f, 1200f)) {
                setContent { HarnessTheme { SettingsGitPage(f.git) } }
                waitUntil { f.ready() && onAllNodesWithTag("git-diff-options").fetchSemanticsNodes().isNotEmpty() }
                // Turning it on must reach the adapter as a diff input, not filter the rendered text.
                onNodeWithTag("git-diff-options").performScrollTo().performMouseInput { click() }
                waitUntil { onAllNodesWithTag("menu:hide-whitespace").fetchSemanticsNodes().isNotEmpty() }
                onNodeWithTag("menu:hide-whitespace").performMouseInput { click() }
                waitUntil { f.git.state.value.diffRequest.hideWhitespace && f.ready() }
                assertTrue(f.backend.diffReads.last().second.hideWhitespace)
                // The menu reports the state and the toggle is reversible.
                onNodeWithTag("git-diff-options").performScrollTo().performMouseInput { click() }
                waitUntil { onAllNodesWithTag("menu:hide-whitespace").fetchSemanticsNodes().isNotEmpty() }
                onNodeWithTag("menu:hide-whitespace").assertIsSelected().performMouseInput { click() }
                waitUntil { !f.git.state.value.diffRequest.hideWhitespace && f.ready() }
                assertFalse(f.backend.diffReads.last().second.hideWhitespace)
            }
        } finally { f.close() }
    }

    @Test
    fun independentReadFailuresNeverBecomeFalseEmptyAndCountsKeepOverlap() {
        val f = GitWorktreesPageFixture()
        try {
            f.backend.changes = listOf(GitChange("overlap.kt", indexStatus = 'M', worktreeStatus = 'M'),
                GitChange("untracked.txt", indexStatus = '?', worktreeStatus = '?'))
            f.backend.branchesFailure = GitFailure(GitFailureKind.CommandFailed, "refs unavailable")
            f.backend.diffFailure = GitFailure(GitFailureKind.OutputTooLarge, "diff unavailable")
            f.git.refresh()
            runSkikoComposeUiTest(size = Size(600f, 800f)) {
                setContent { HarnessTheme { SettingsGitPage(f.git) } }
                listOf("已暂存 1", "未暂存 1", "未跟踪 1").forEach { onNodeWithText(it).assertExists() }
                onNodeWithText("没有分支引用。").assertDoesNotExist()
                onNodeWithTag("git-diff-empty").assertDoesNotExist()
                onNodeWithTag("git-clean").assertDoesNotExist()
                onNodeWithTag("git-checkout-branch").assertIsNotEnabled()
                onNodeWithTag("git-diff-source").performScrollTo().performMouseInput { click() }
                onNodeWithTag("menu:2").performMouseInput { click() }
                onNodeWithText("没有可用基准分支引用。").assertDoesNotExist()
                onAllNodesWithText("refs unavailable").assertCountEquals(2)
            }
        } finally { f.close() }
    }

    @Test
    fun missingGitFromDesktopAdapterIsUnavailableAndRemoteHasNoReadCapability() {
        val directory = Files.createTempDirectory("git-page-controlled-runner-")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val commands = mutableListOf<Pair<String, List<String>>>()
        val backend = DesktopGitBackend(GitWorktreesPageFixture.binding.hostId, GitCommandRunner { cwd, args ->
            commands += cwd to args
            throw GitReadException(GitFailure(GitFailureKind.Unavailable, "本机 Git executable 不可用。"))
        })
        val controller = GitController(scope, backend)
        try {
            controller.bind(GitWorktreesPageFixture.binding)
            controller.selectRepository(directory.toString())
            runSkikoComposeUiTest(size = Size(600f, 800f)) {
                setContent { HarnessTheme { SettingsGitPage(controller) } }
                onNodeWithTag("git-read-unavailable").assertExists()
                onNodeWithTag("git-error").assertTextEquals("本机 Git executable 不可用。")
                assertEquals(listOf(directory.toRealPath().toString() to listOf("rev-parse", "--is-inside-work-tree")), commands)
                onNodeWithTag("git-clean").assertDoesNotExist()
                runOnIdle { controller.bind(GitWorktreesPageFixture.binding.copy(access = GitAccess.RemoteUnavailable, generation = 2)) }
                onNodeWithTag("git-unavailable").assertExists()
                onNodeWithTag("git-refresh").assertIsNotEnabled()
                revealRepositoryInput()
                onNodeWithTag("git-repository-select").assertIsNotEnabled()
                onNodeWithTag("git-checkout-submit").assertIsNotEnabled()
                onNodeWithTag("git-commit-open").assertIsNotEnabled()
                onNodeWithTag("git-diff-source").assertIsNotEnabled()
            }
        } finally { controller.close(); scope.cancel(); Files.deleteIfExists(directory) }
    }
}
