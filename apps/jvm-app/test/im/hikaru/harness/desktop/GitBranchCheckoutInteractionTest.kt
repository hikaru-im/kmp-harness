package im.hikaru.harness.desktop

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import im.hikaru.harness.client.git.*
import im.hikaru.harness.desktop.git.BranchCheckoutFixture
import im.hikaru.harness.desktop.git.DesktopGitBackend
import im.hikaru.harness.desktop.git.DesktopGitCommandRunner
import im.hikaru.harness.desktop.git.GitCommandRunner
import im.hikaru.harness.ui.foundation.HarnessTheme
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlin.test.*

/** Production page and controller, real temporary Git repositories; only invocation timing is controlled. */
@OptIn(ExperimentalTestApi::class)
class GitBranchCheckoutInteractionTest {
    @Test
    fun localPickerOnlySelectsAndExplicitSwitchVerifiesActualFilesWithRejectedDirtyRace() {
        val fixture = BranchCheckoutFixture()
        try {
            fixture.git(fixture.workspace, "update-ref", "refs/remotes/origin/feature", fixture.featureOid)
            val original = fixture.originalEvidence()
            val switches = AtomicInteger()
            val real = DesktopGitCommandRunner()
            val backend = DesktopGitBackend(fixture.host, GitCommandRunner { cwd, args ->
                if (args.firstOrNull() == "switch") switches.incrementAndGet()
                real.run(cwd, args)
            })
            lateinit var controller: GitController
            runSkikoComposeUiTest(size = Size(1000f, 1100f)) {
                setContent {
                    val scope = rememberCoroutineScope()
                    val owner = remember { GitController(scope, backend) }
                    controller = owner
                    DisposableEffect(owner) {
                        owner.bind(fixture.binding)
                        owner.selectRepository(fixture.workspace.toString())
                        onDispose { owner.close() }
                    }
                    HarnessTheme { SettingsGitPage(owner) }
                }
                waitUntil(timeoutMillis = 30_000) { controller.state.value.branches is GitRead.Ready && controller.state.value.status is GitRead.Ready }
                awaitEnabled("git-checkout-branch")
                onNodeWithTag("git-checkout-submit").performScrollTo().assertIsNotEnabled()
                onNodeWithTag("git-checkout-branch").performScrollTo().performMouseInput { click() }
                // Sorted real inventory has feature, main and sandbox; remotes never become picker entries.
                onNodeWithTag("menu:1").assertTextContains("refs/heads/feature").performMouseInput { click() }
                waitUntil(timeoutMillis = 5_000) { controller.state.value.selectedBranchRef == "refs/heads/feature" }
                assertEquals(0, switches.get())
                assertEquals(fixture.baseOid, fixture.git(fixture.workspace, "rev-parse", "HEAD").trim())
                onNodeWithTag("git-checkout-branch").performMouseInput { click() }
                onNodeWithTag("menu:4").assertDoesNotExist()
                onNodeWithTag("menu:1").performMouseInput { click() }
                waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag("menu:1").fetchSemanticsNodes().isEmpty() }
                // A real edit after the clean UI read must be rejected by backend preflight.
                Files.writeString(fixture.workspace.resolve("tracked.txt"), "user edit after snapshot\n")
                onNodeWithTag("git-checkout-submit").performScrollTo().assertIsEnabled().performMouseInput { click() }
                waitUntil(timeoutMillis = 10_000) { controller.state.value.checkout is GitBranchCheckout.Failed && controller.state.value.status is GitRead.Ready }
                onNodeWithTag("git-checkout-error").assertExists()
                onNodeWithTag("git-checkout-submit").assertIsNotEnabled()
                assertEquals(0, switches.get())
                assertEquals("user edit after snapshot\n", Files.readString(fixture.workspace.resolve("tracked.txt")))
                Files.writeString(fixture.workspace.resolve("tracked.txt"), "main content\n")
                awaitEnabled("git-refresh")
                onNodeWithTag("git-refresh").performScrollTo().performMouseInput { click() }
                waitUntil(timeoutMillis = 10_000) { controller.state.value.canSwitchBranch }
                awaitEnabled("git-checkout-submit")
                onNodeWithTag("git-checkout-submit").performScrollTo().performMouseInput { click() }
                waitUntil(timeoutMillis = 15_000) { controller.state.value.checkout is GitBranchCheckout.Switched }
                onNodeWithTag("git-checkout-success").assertTextEquals("已确认切换：refs/heads/feature")
                onNodeWithTag("git-current-branch").assertTextEquals("refs/heads/feature")
                onNodeWithTag("git-checkout-submit").assertIsNotEnabled()
                assertEquals(1, switches.get())
                assertEquals(fixture.featureOid, fixture.git(fixture.workspace, "rev-parse", "HEAD").trim())
                assertEquals("feature content\n", Files.readString(fixture.workspace.resolve("tracked.txt")))
                assertEquals(original, fixture.originalEvidence())
                runOnIdle { controller.bind(fixture.binding.copy(access = GitAccess.RemoteUnavailable, generation = 2)) }
                onNodeWithTag("git-unavailable").assertExists()
                onNodeWithTag("git-checkout-branch").assertIsNotEnabled()
                onNodeWithTag("git-checkout-submit").assertIsNotEnabled()
                assertEquals(1, switches.get())
            }
        } finally { fixture.close() }
    }

    @Test
    fun acceptedInvocationShowsBusyThenUnknownAndRealRefreshShowsRescannedWithoutReplaying() {
        val fixture = BranchCheckoutFixture()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        try {
            val original = fixture.originalEvidence()
            val switches = AtomicInteger()
            val real = DesktopGitCommandRunner()
            val backend = DesktopGitBackend(fixture.host, GitCommandRunner { cwd, args ->
                val result = real.run(cwd, args)
                if (args.firstOrNull() == "switch") {
                    switches.incrementAndGet()
                    entered.complete(Unit)
                    release.await()
                    // The command really ran; its acknowledgement is unavailable, so no receipt is invented.
                    throw GitReadException(GitFailure(GitFailureKind.CommandFailed, "controlled lost acknowledgement"))
                }
                result
            })
            lateinit var controller: GitController
            runSkikoComposeUiTest(size = Size(1000f, 1100f)) {
                setContent {
                    val scope = rememberCoroutineScope()
                    val owner = remember { GitController(scope, backend) }
                    controller = owner
                    DisposableEffect(owner) {
                        owner.bind(fixture.binding)
                        owner.selectRepository(fixture.workspace.toString())
                        onDispose { owner.close() }
                    }
                    HarnessTheme { SettingsGitPage(owner) }
                }
                waitUntil(timeoutMillis = 30_000) { controller.state.value.branches is GitRead.Ready && controller.state.value.status is GitRead.Ready }
                awaitEnabled("git-checkout-branch")
                onNodeWithTag("git-checkout-branch").performScrollTo().performMouseInput { click() }
                onNodeWithTag("menu:1").performMouseInput { click() }
                waitUntil(timeoutMillis = 5_000) { controller.state.value.canSwitchBranch }
                awaitEnabled("git-checkout-submit")
                onNodeWithTag("git-checkout-submit").performScrollTo().performMouseInput { click() }
                waitUntil(timeoutMillis = 15_000) { entered.isCompleted }
                onNodeWithTag("git-checkout-busy").assertExists()
                onNodeWithTag("git-checkout-submit").assertIsNotEnabled()
                onNodeWithTag("git-checkout-branch").assertIsNotEnabled()
                release.complete(Unit)
                waitUntil(timeoutMillis = 5_000) { controller.state.value.checkoutNeedsRescan && !controller.state.value.creationBusy }
                onNodeWithTag("git-checkout-needs-rescan").assertExists()
                onNodeWithTag("git-checkout-success").assertDoesNotExist()
                onNodeWithTag("git-checkout-submit").assertIsNotEnabled()
                awaitEnabled("git-refresh")
                onNodeWithTag("git-refresh").performScrollTo().performMouseInput { click() }
                waitUntil(timeoutMillis = 10_000) { controller.state.value.checkout is GitBranchCheckout.Rescanned }
                onNodeWithTag("git-checkout-rescanned").assertTextEquals("已重新读取当前状态，可重新选择。")
                onNodeWithTag("git-checkout-unknown").assertDoesNotExist()
                onNodeWithTag("git-checkout-needs-rescan").assertDoesNotExist()
                onNodeWithTag("git-checkout-success").assertDoesNotExist()
                onNodeWithTag("git-checkout-submit").performScrollTo().assertIsNotEnabled()
                awaitEnabled("git-checkout-branch")
                onNodeWithTag("git-checkout-branch").assertIsEnabled()
                assertEquals(1, switches.get())
                assertEquals(fixture.featureOid, fixture.git(fixture.workspace, "rev-parse", "HEAD").trim())
                assertEquals(original, fixture.originalEvidence())
            }
        } finally { release.complete(Unit); fixture.close() }
    }

    /**
     * Most controls enable only when the whole page read model is ready. Branches plus status are
     * not enough: a still-loading worktrees/diff/commit snapshot keeps pageReady false, so a control
     * stays disabled and a click would be a silent no-op. Wait for the real precondition instead.
     */
    private fun ComposeUiTest.awaitEnabled(tag: String) = waitUntil(timeoutMillis = 30_000) {
        onAllNodes(hasTestTag(tag) and isEnabled()).fetchSemanticsNodes().isNotEmpty()
    }
}
