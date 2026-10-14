package im.hikaru.harness.desktop

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.harness.client.git.*
import im.hikaru.harness.ui.foundation.HarnessTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.*

/** Production page/controllers; only read observations are controlled. No destructive seam exists. */
@OptIn(ExperimentalTestApi::class)
class ManagedWorktreeInteractionTest {
    @Test
    fun optionalServiceAbsentShowsUnavailableAndExistingReadOnlyPageStillWorks() {
        val fixture = ManagedPageFixture()
        try {
            runSkikoComposeUiTest(size = Size(640f, 1000f)) {
                setContent { HarnessTheme { WorktreesPage(fixture.git) } }
                onNodeWithTag("managed-unavailable").performScrollTo().assertTextEquals("Managed 登记服务不可用。")
                onNodeWithTag("managed-refresh").assertDoesNotExist()
                onNodeWithTag("git-worktree-create-submit").performScrollTo().assertIsNotEnabled()
                assertEquals(0, fixture.reads.calls)
            }
        } finally { fixture.close() }
    }

    @Test
    fun actualControllerRefreshShowsUnknownProtectionStaleFailureAndSuccessfulRetry() {
        val fixture = ManagedPageFixture()
        try {
            runSkikoComposeUiTest(size = Size(640f, 1000f)) {
                setContent { HarnessTheme { WorktreesPage(fixture.git, managedController = fixture.managed) } }
                waitUntil(timeoutMillis = 5_000) { fixture.managed.state.value.phase == ManagedWorktreePhase.Ready }
                onNodeWithTag("managed-ownership").performScrollTo().assertTextEquals("归属未知")
                onNodeWithTag("managed-sessions").performScrollTo().assertTextContains("关联会话未知", substring = true)
                onNodeWithTag("managed-pins").performScrollTo().assertTextEquals("Pin 保护服务不可用。")
                onNodeWithTag("managed-archive").performScrollTo().assertTextEquals("归档服务不可用。")
                onNodeWithTag("managed-destructive-unavailable").performScrollTo().assertTextContains("不可用", substring = true)
                val before = fixture.reads.calls
                runOnIdle { fixture.reads.answer = { ManagedWorktreeRead.Failed("controlled disk read failure") } }
                onNodeWithTag("managed-refresh").performScrollTo().performMouseInput { click() }
                waitUntil(timeoutMillis = 5_000) { fixture.managed.state.value.phase == ManagedWorktreePhase.Error }
                onNodeWithTag("managed-error").performScrollTo().assertTextEquals("controlled disk read failure")
                onNodeWithTag("managed-stale").performScrollTo().assertExists()
                onNodeWithTag("managed-worktree-path").performScrollTo().assertTextEquals("/current")
                assertEquals(before + 1, fixture.reads.calls)
                runOnIdle { fixture.reads.answer = { managedPageReady(it) } }
                onNodeWithTag("managed-refresh").performScrollTo().performMouseInput { click() }
                waitUntil(timeoutMillis = 5_000) { fixture.managed.state.value.phase == ManagedWorktreePhase.Ready }
                onNodeWithTag("managed-error").assertDoesNotExist()
                onNodeWithTag("managed-stale").assertDoesNotExist()
                assertEquals(before + 2, fixture.reads.calls)
                // Ordinary worktree selection remains a read; it exposes no archive/delete callback.
                onNodeWithTag("git-worktree-select").performScrollTo().performMouseInput { click() }
                assertEquals("/current", fixture.git.state.value.selectedCwd)
            }
        } finally { fixture.close() }
    }

    @Test
    fun previousOwnerInventoryIsHiddenWhileCurrentRepositoryReadIsPending() {
        val fixture = ManagedPageFixture()
        val gate = CompletableDeferred<Unit>()
        try {
            val previous = managedPageRepository("/old-owner-secret", HostId("previous-owner"))
            fixture.managed.bind(previous)
            assertEquals(previous, fixture.managed.state.value.inventory?.repository)
            fixture.reads.answer = { repo -> gate.await(); managedPageReady(repo) }
            runSkikoComposeUiTest(size = Size(320f, 600f)) {
                setContent { HarnessTheme { WorktreesPage(fixture.git, managedController = fixture.managed) } }
                waitUntil(timeoutMillis = 5_000) { fixture.managed.state.value.phase == ManagedWorktreePhase.Loading }
                onNodeWithText("/old-owner-secret").assertDoesNotExist()
                onNodeWithTag("managed-worktree-path").assertDoesNotExist()
                onNodeWithTag("managed-loading").performScrollTo().assertExists()
                onNodeWithTag("managed-refresh").performScrollTo().assertIsNotEnabled()
                runOnIdle { gate.complete(Unit) }
                waitUntil(timeoutMillis = 5_000) { fixture.managed.state.value.phase == ManagedWorktreePhase.Ready }
                onNodeWithTag("managed-worktree-path").performScrollTo().assertTextEquals("/current")
                onNodeWithText("/old-owner-secret").assertDoesNotExist()
            }
        } finally { gate.complete(Unit); fixture.close() }
    }
}

private class ManagedPageReads : ManagedWorktreeRepository {
    override val revision = MutableStateFlow(0L)
    var calls = 0
    var answer: suspend (GitRepository) -> ManagedWorktreeRead = { managedPageReady(it) }
    override suspend fun read(repository: GitRepository): ManagedWorktreeRead { calls++; return answer(repository) }
}

private class ManagedPageFixture : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    val reads = ManagedPageReads()
    val managed = ManagedWorktreeController(scope, reads)
    private val backend = object : GitBackend {
        override suspend fun discover(binding: GitBinding, cwd: String) = managedPageRepository(cwd, binding.hostId).copy(binding = binding)
        override suspend fun status(repository: GitRepository) = GitStatusSnapshot(emptyList(), "refs/heads/main", "a".repeat(40))
        override suspend fun branches(repository: GitRepository) = listOf(GitBranch("refs/heads/main", "a".repeat(40), true, false))
        override suspend fun worktrees(repository: GitRepository) = listOf(GitWorktree(repository.root, "a".repeat(40), "refs/heads/main"))
        override suspend fun diff(repository: GitRepository, request: GitDiffRequest) = GitDiffSnapshot(request, "", "a".repeat(40))
    }
    val git = GitController(scope, backend)
    init {
        git.bind(managedPageRepository().binding)
        git.selectRepository("/current")
    }
    override fun close() { managed.close(); git.close(); scope.cancel() }
}

private fun managedPageRepository(root: String = "/current", host: HostId = HostId("current-local")) =
    GitRepository(GitBinding(null, host, 1, GitAccess.LocalReadOnly), root, root + "/.git", root + "/.git")

private fun managedPageReady(repo: GitRepository) = ManagedWorktreeRead.Ready(ManagedWorktreeInventory(
    ManagedWorktreeOwner(repo.binding.identity, repo.binding.hostId, "/test-home"), repo,
    listOf(ManagedWorktreeEntry(repo.root, GitWorktree(repo.root, "a".repeat(40), "refs/heads/main"),
        ManagedOwnership.UnknownOwnership)), 0))
