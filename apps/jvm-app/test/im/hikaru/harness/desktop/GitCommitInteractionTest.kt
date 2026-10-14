package im.hikaru.harness.desktop

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import im.hikaru.harness.client.git.*
import im.hikaru.harness.desktop.git.CommitFixture
import im.hikaru.harness.desktop.git.DesktopGitBackend
import im.hikaru.harness.desktop.git.GitCommandRunner
import im.hikaru.harness.ui.foundation.HarnessTheme
import java.nio.file.Files
import kotlinx.coroutines.CompletableDeferred
import kotlin.test.*

/** Production Git page interaction at the real controller/backend seam. */
@OptIn(ExperimentalTestApi::class)
class GitCommitInteractionTest {
    @Test
    fun commitDialogRequiresMessageAndCommitsOnlyExistingIndex() {
        val f = CommitFixture()
        try {
            f.stage("ui staged\n")
            val originalUntracked = f.workspace.resolve("ui-untracked.txt")
            Files.writeString(originalUntracked, "keep\n")
            val original = f.originalEvidence()
            lateinit var controller: GitController
            runSkikoComposeUiTest(size = Size(1000f, 1100f)) {
                setContent {
                    val scope = rememberCoroutineScope()
                    val owner = remember { GitController(scope, f.backend) }
                    controller = owner
                    DisposableEffect(owner) {
                        owner.bind(f.binding)
                        owner.selectRepository(f.workspace.toString())
                        onDispose { owner.close() }
                    }
                    HarnessTheme { SettingsGitPage(owner) }
                }
                waitUntil(timeoutMillis = 15_000) { controller.state.value.canCommit }
                onNodeWithTag("git-commit-open").performScrollTo().performMouseInput { click() }
                onNodeWithTag("git-commit-submit").performScrollTo().assertIsNotEnabled()
                assertEquals(0, f.commitInvocations)
                onNodeWithTag("git-commit-message").performScrollTo().assertExists().performTextInput("UI commit message\nsecond line")
                assertEquals(0, f.commitInvocations, "typing is not a mutation")
                onNodeWithTag("git-commit-submit").performScrollTo().assertIsEnabled().performMouseInput { click() }
                waitUntil(timeoutMillis = 15_000) {
                    controller.state.value.commit is GitCommit.Verified && !controller.state.value.commitBusy
                }
                onNodeWithTag("git-commit-verified").assertExists()
                onNodeWithTag("git-commit-submit").assertIsNotEnabled()
                assertEquals(0, assertIs<GitRead.Ready<GitCommitSnapshot>>(controller.state.value.commitSnapshot).value.stagedCount)
            }
            assertEquals("ui staged\n", f.git(f.workspace, "show", "HEAD:tracked.txt"))
            assertEquals("keep\n", Files.readString(originalUntracked))
            assertEquals(original, f.originalEvidence())
            assertEquals(1, f.commitInvocations)
        } finally { f.close() }
    }

    @Test
    fun includeUnstagedCheckboxStagesWorktreeAndUntrackedThenCommitsThem() {
        val f = CommitFixture()
        try {
            Files.writeString(f.workspace.resolve("tracked.txt"), "ui include unstaged\n")
            Files.writeString(f.workspace.resolve("ui-untracked.txt"), "ui untracked\n")
            Files.writeString(f.workspace.resolve("ignored.log"), "ignored keep\n")
            lateinit var controller: GitController
            runSkikoComposeUiTest(size = Size(1000f, 1100f)) {
                setContent {
                    val scope = rememberCoroutineScope()
                    val owner = remember { GitController(scope, f.backend) }
                    controller = owner
                    DisposableEffect(owner) {
                        owner.bind(f.binding)
                        owner.selectRepository(f.workspace.toString())
                        onDispose { owner.close() }
                    }
                    HarnessTheme { SettingsGitPage(owner) }
                }
                waitUntil(timeoutMillis = 15_000) { controller.state.value.commitSnapshot is GitRead.Ready }
                assertFalse(controller.state.value.canCommit)
                onNodeWithTag("git-commit-disabled-reason").performScrollTo()
                    .assertTextContains("勾选", substring = true)
                onNodeWithTag("git-commit-open").performScrollTo().performMouseInput { click() }
                onNodeWithTag("git-commit-submit").performScrollTo().assertIsNotEnabled()
                onNodeWithTag("git-commit-include-unstaged").performScrollTo().performMouseInput { click() }
                waitUntil(timeoutMillis = 15_000) { controller.state.value.includeUnstaged && controller.state.value.canCommit }
                onNodeWithTag("git-commit-include-unstaged-hint").assertTextContains("先暂存", substring = true)
                onNodeWithTag("git-commit-message").performScrollTo().performTextInput("ui include unstaged commit")
                onNodeWithTag("git-commit-submit").performScrollTo().assertIsEnabled().performMouseInput { click() }
                waitUntil(timeoutMillis = 15_000) {
                    controller.state.value.commit is GitCommit.Verified && !controller.state.value.commitBusy
                }
                onNodeWithTag("git-commit-verified").assertExists()
            }
            assertEquals("ui include unstaged\n", f.git(f.workspace, "show", "HEAD:tracked.txt"))
            assertEquals("ui untracked\n", f.git(f.workspace, "show", "HEAD:ui-untracked.txt"))
            assertEquals("ignored keep\n", Files.readString(f.workspace.resolve("ignored.log")))
            assertEquals(1, f.commitInvocations)
        } finally { f.close() }
    }

    @Test
    fun unknownOutcomeDisablesReplayUntilRefreshAndDoesNotExposePrivateRunnerError() {
        val f = CommitFixture()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        try {
            f.stage("ui unknown\n")
            val backend = DesktopGitBackend(f.host, GitCommandRunner { cwd, args ->
                if (args.firstOrNull() == "cat-file") throw GitReadException(
                    GitFailure(GitFailureKind.Timeout, "private confirmation path"))
                val result = f.runner.run(cwd, args)
                if ("commit" in args && args.firstOrNull() != "cat-file") {
                    entered.complete(Unit)
                    release.await()
                }
                result
            })
            lateinit var controller: GitController
            runSkikoComposeUiTest(size = Size(320f, 600f)) {
                setContent {
                    val scope = rememberCoroutineScope()
                    val owner = remember { GitController(scope, backend) }
                    controller = owner
                    DisposableEffect(owner) {
                        owner.bind(f.binding)
                        owner.selectRepository(f.workspace.toString())
                        onDispose { owner.close() }
                    }
                    HarnessTheme { SettingsGitPage(owner) }
                }
                waitUntil(timeoutMillis = 15_000) { controller.state.value.canCommit }
                onNodeWithTag("git-commit-open").performScrollTo().performMouseInput { click() }
                onNodeWithTag("git-commit-message").performScrollTo().performTextInput("unknown UI commit")
                onNodeWithTag("git-commit-submit").performScrollTo().performMouseInput { click() }
                waitUntil(timeoutMillis = 15_000) { entered.isCompleted }
                onNodeWithTag("git-commit-busy").assertExists()
                onNodeWithTag("git-commit-message").assertIsNotEnabled()
                onNodeWithTag("git-commit-submit").assertIsNotEnabled()
                // Closing only changes the view; the accepted mutation still owns its slot.
                onNodeWithTag("git-commit-close").performScrollTo().performMouseInput { click() }
                runOnIdle { assertTrue(controller.state.value.creationBusy) }
                onNodeWithTag("git-commit-open").performScrollTo().performMouseInput { click() }
                runOnIdle { release.complete(Unit) }
                waitUntil(timeoutMillis = 15_000) { controller.state.value.commitNeedsRescan }
                onNodeWithTag("git-commit-unknown").assertExists()
                onNodeWithTag("git-commit-unknown").assertTextContains("结果未知", substring = true)
                onNodeWithTag("git-commit-submit").performScrollTo().assertIsNotEnabled()
                assertFalse(controller.state.value.commit is GitCommit.Verified)
                runOnIdle {
                    controller.commit("replay")
                    assertEquals(1, f.commitInvocations)
                    assertFalse(assertIs<GitCommit.UnknownOutcome>(controller.state.value.commit).failure.message.contains("private"))
                }
                onNodeWithTag("git-commit-close").performScrollTo().performMouseInput { click() }
                onNodeWithTag("git-refresh").performScrollTo().performMouseInput { click() }
                waitUntil(timeoutMillis = 15_000) { controller.state.value.commit is GitCommit.Rescanned }
                onNodeWithTag("git-commit-rescanned").performScrollTo().assertExists()
                onNodeWithTag("git-commit-unknown").assertDoesNotExist()
                onNodeWithTag("git-commit-verified").assertDoesNotExist()
                assertEquals(1, f.commitInvocations)
            }
            assertNotEquals(f.baseOid, f.git(f.workspace, "rev-parse", "HEAD").trim())
            assertEquals("ui unknown\n", f.git(f.workspace, "show", "HEAD:tracked.txt"))
        } finally { release.complete(Unit); f.close() }
    }
}
