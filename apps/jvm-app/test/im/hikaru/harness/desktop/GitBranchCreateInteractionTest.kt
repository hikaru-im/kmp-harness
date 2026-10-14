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
import im.hikaru.harness.ui.foundation.HarnessTheme
import java.nio.file.Files
import kotlin.test.*

/** Production page and controller against a real temporary Git repository. */
@OptIn(ExperimentalTestApi::class)
class GitBranchCreateInteractionTest {
    @Test
    fun formCreatesAndChecksOutRealBranchAndShowsVerifiedReceipt() {
        val fixture = BranchCheckoutFixture()
        try {
            lateinit var controller: GitController
            runSkikoComposeUiTest(size = Size(1000f, 1500f)) {
                setContent {
                    val scope = rememberCoroutineScope()
                    val owner = remember { GitController(scope, DesktopGitBackend(fixture.host)) }
                    controller = owner
                    DisposableEffect(owner) {
                        owner.bind(fixture.binding)
                        owner.selectRepository(fixture.workspace.toString())
                        onDispose { owner.close() }
                    }
                    HarnessTheme { SettingsGitPage(owner) }
                }
                waitUntil(timeoutMillis = 30_000) {
                    controller.state.value.branches is GitRead.Ready && controller.state.value.status is GitRead.Ready
                }
                awaitEnabled("git-branch-create-name")
                onNodeWithTag("git-branch-create-submit").performScrollTo().assertIsNotEnabled()
                onNodeWithTag("git-branch-create-name").performScrollTo().performTextInput("codex/page-new")
                waitUntil(timeoutMillis = 5_000) { controller.state.value.canCreateBranch }
                awaitEnabled("git-branch-create-submit")
                onNodeWithTag("git-branch-create-submit").performScrollTo().performMouseInput { click() }
                waitUntil(timeoutMillis = 15_000) { controller.state.value.branchCreation is GitBranchCreation.Created }
                onNodeWithTag("git-branch-create-success").assertTextEquals("已确认创建并切换：refs/heads/codex/page-new")
                onNodeWithTag("git-current-branch").assertTextEquals("refs/heads/codex/page-new")
                onNodeWithTag("git-branch-create-error").assertDoesNotExist()
                assertEquals("refs/heads/codex/page-new", fixture.git(fixture.workspace, "symbolic-ref", "HEAD").trim())
                assertEquals(fixture.baseOid, fixture.git(fixture.workspace, "rev-parse", "HEAD").trim())
                assertEquals("main content\n", Files.readString(fixture.workspace.resolve("tracked.txt")))
            }
        } finally { fixture.close() }
    }

    @Test
    fun existingNameDisablesSubmitAndCreatesNothing() {
        val fixture = BranchCheckoutFixture()
        try {
            lateinit var controller: GitController
            runSkikoComposeUiTest(size = Size(1000f, 1500f)) {
                setContent {
                    val scope = rememberCoroutineScope()
                    val owner = remember { GitController(scope, DesktopGitBackend(fixture.host)) }
                    controller = owner
                    DisposableEffect(owner) {
                        owner.bind(fixture.binding)
                        owner.selectRepository(fixture.workspace.toString())
                        onDispose { owner.close() }
                    }
                    HarnessTheme { SettingsGitPage(owner) }
                }
                waitUntil(timeoutMillis = 30_000) {
                    controller.state.value.branches is GitRead.Ready && controller.state.value.status is GitRead.Ready
                }
                awaitEnabled("git-branch-create-name")
                onNodeWithTag("git-branch-create-name").performScrollTo().performTextInput("feature")
                waitUntil(timeoutMillis = 5_000) { controller.state.value.branchCreateDisabledReason != null }
                onNodeWithTag("git-branch-create-disabled-reason").assertExists()
                onNodeWithTag("git-branch-create-submit").performScrollTo().assertIsNotEnabled()
                assertFalse(fixture.git(fixture.workspace, "branch", "--format=%(refname)").contains("codex"))
                assertEquals("refs/heads/sandbox", fixture.git(fixture.workspace, "symbolic-ref", "HEAD").trim())
            }
        } finally { fixture.close() }
    }

    private fun ComposeUiTest.awaitEnabled(tag: String) = waitUntil(timeoutMillis = 30_000) {
        onAllNodes(hasTestTag(tag) and isEnabled()).fetchSemanticsNodes().isNotEmpty()
    }
}
