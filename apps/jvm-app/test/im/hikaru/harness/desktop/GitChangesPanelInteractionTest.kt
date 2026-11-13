package im.hikaru.harness.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.harness.client.connection.SessionId
import im.hikaru.harness.client.git.*
import im.hikaru.harness.client.panels.*
import im.hikaru.harness.ui.foundation.HarnessTheme
import im.hikaru.harness.ui.foundation.HarnessThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlin.test.*

/**
 * The thread side panel's Changes (审查) tab on the production panel and per-change page components.
 * The review pane renders an app-owned GitController snapshot; the backend here only answers reads
 * and records every request, so a write would show up as an unexpected call.
 */
@OptIn(ExperimentalTestApi::class)
class GitChangesPanelInteractionTest {
    @Test
    fun reviewDiffExposesValidatedHunkActionAndPassesSingleHunkPatch() {
        var captured: String? = null
        val binding = GitBinding(null, HostId("hunk-host"), 1, GitAccess.LocalReadOnly)
        val repository = GitRepository(binding, "/fixture/project", "/fixture/project/.git", "/fixture/project/.git")
        val state = GitState(
            binding = binding,
            selectedCwd = repository.root,
            repository = GitRead.Ready(repository),
            status = GitRead.Ready(GitStatusSnapshot(
                listOf(GitChange("edited.kt", indexStatus = ' ', worktreeStatus = 'M')),
                "refs/heads/main", "a".repeat(40))),
            reviewDiff = GitRead.Ready(GitDiffSnapshot(
                GitDiffRequest(source = GitDiffSource.WorkingTree), WORKTREE_PATCH, "a".repeat(40))),
            stageAvailable = true,
        )
        runSkikoComposeUiTest(size = Size(640f, 800f)) {
            setContent { HarnessTheme(themeMode = HarnessThemeMode.Light) {
                GitChangesPanel(state, onHunkStageChange = { action, _, patch ->
                    assertEquals(GitStageAction.Stage, action)
                    captured = patch
                })
            } }
            onNodeWithTag("git-diff-hunk-stage-1-edited.kt").performClick()
        }
        assertNotNull(captured)
        assertTrue(captured!!.contains("diff --git a/edited.kt b/edited.kt"))
        assertEquals(1, Regex("^@@ ", RegexOption.MULTILINE).findAll(captured!!).count())
    }

    @Test
    fun changesTabAppearsOnDemandClosesAndNeverWritesToTheRepository() {
        val fixture = GitChangesFixture()
        try {
            var state by mutableStateOf(ThreadPanelsState(fixture.scope))
            runSkikoComposeUiTest(size = Size(420f, 800f)) {
                setContent { HarnessTheme(themeMode = HarnessThemeMode.Light) {
                    val gitState by fixture.git.state.collectAsState()
                    ThreadPanels(state, { next -> state = state.reduce(next) },
                        changesContent = { ChangesFor(gitState, fixture.git) })
                } }
                // The review tab is not open by default; the panel still shows the two service tabs.
                onNodeWithText("审查").assertDoesNotExist()
                // Codex's `ThreadPanelOpenTabDropdown`: the strip's + is the user-reachable opener.
                onNodeWithTag("thread-panel-open-tab").performMouseInput { click() }
                onNodeWithTag("thread-panel-open-Changes").performMouseInput { click() }
                onNodeWithText("审查").assertIsDisplayed()
                waitUntil { fixture.backend.reviewReads.isNotEmpty() }
                onNodeWithTag("git-changes-panel").assertIsDisplayed()
                // Default filter is Codex's "unstaged" half, so only the worktree edit is listed.
                onNodeWithTag("git-changes-file-edited.kt").assertExists()
                onNodeWithTag("git-changes-stage-edited.kt").assertIsNotEnabled()
                onNodeWithTag("git-changes-source").performClick()
                onNodeWithTag("git-changes-source-workingtree").assertExists()
                onNodeWithTag("git-changes-source-index").assertExists()
                onNodeWithTag("git-changes-source-index").performMouseInput { click() }
                waitUntil(timeoutMillis = 10_000) { fixture.backend.reviewReads.last() == GitDiffSource.Index }
                onNodeWithTag("git-changes-file-added.kt").assertExists()
                onNodeWithTag("git-changes-file-jump").performClick()
                onNodeWithTag("git-changes-jump-added.kt").assertExists().performMouseInput { click() }
                onNodeWithTag("git-changes-file-edited.kt").assertDoesNotExist()
                onNodeWithTag("git-changes-empty").assertDoesNotExist()

                // Switch to the staged half: only the staged file remains, and the diff read follows.
                // `DiffFilterDropdown` keeps every source inside one menu, so the option is reached
                // through the anchor rather than sitting in the header as its own button.
                onNodeWithTag("git-changes-filter").performMouseInput { click() }
                onNodeWithTag("git-changes-filter-staged").performMouseInput { click() }
                waitUntil { fixture.backend.reviewReads.last() == GitDiffSource.Index }
                onNodeWithTag("git-changes-file-added.kt").assertExists()
                onNodeWithTag("git-changes-unstage-added.kt").assertIsNotEnabled()
                onNodeWithTag("git-changes-file-edited.kt").assertDoesNotExist()

                // `ReviewOptionsMenuCompact` owns 刷新, so the header exposes it behind 查看选项
                // rather than as a standalone button. Reading again is enough to prove the menu item
                // reaches the controller: no write may result from it either.
                onNodeWithTag("git-changes-refresh").assertDoesNotExist()
                onNodeWithTag("git-changes-options").performMouseInput { click() }
                onNodeWithTag("git-changes-options-menu").assertExists()
                val beforeRefresh = fixture.backend.statusReads
                onNodeWithTag("git-changes-refresh").performMouseInput { click() }
                waitUntil { fixture.backend.statusReads > beforeRefresh }

                // Closing through the tab strip's own close button leaves no review pane and starts
                // no further read (the tab owns no service read to fence).
                val reads = fixture.backend.reviewReads.size
                onNodeWithTag("关闭审查").performMouseInput { click() }
                onNodeWithTag("git-changes-panel").assertDoesNotExist()
                assertEquals(reads, fixture.backend.reviewReads.size)
                runOnIdle { assertFalse(ThreadPanelTab.Changes in state.openTabs) }
            }
            // Read-only boundary: no stage/unstage/commit/checkout/worktree call ever happened.
            assertTrue(fixture.backend.mutations.isEmpty(), "review tab performed ${fixture.backend.mutations}")
        } finally { fixture.close() }
    }

    @Test
    fun emptyReviewSaysSoPerFilterAndAfterAnUnreadableRepository() {
        val fixture = GitChangesFixture()
        try {
            runSkikoComposeUiTest(size = Size(420f, 800f)) {
                setContent { HarnessTheme(themeMode = HarnessThemeMode.Light) {
                    val gitState by fixture.git.state.collectAsState()
                    ThreadPanels(ThreadPanelsState(fixture.scope,
                        openTabs = listOf(ThreadPanelTab.Changes), selectedTab = ThreadPanelTab.Changes),
                        { }, changesContent = { ChangesFor(gitState, fixture.git) })
                } }
                waitUntil { fixture.backend.statusReads > 0 }
                // Only worktree work exists: the staged half states the filter's own empty message.
                runOnIdle { fixture.backend.changes = listOf(GitChange("edited.kt", indexStatus = ' ', worktreeStatus = 'M')) }
                fixture.git.refresh()
                waitUntil { (fixture.git.state.value.status as? GitRead.Ready)?.value?.changes?.size == 1 }
                runOnIdle { fixture.git.selectChangeFilter(GitChangeFilter.Staged) }
                onNodeWithText("无暂存更改").assertIsDisplayed()
                onNodeWithText("接受编辑内容并暂存").assertIsDisplayed()

                // A clean repository gets the generic message, not a per-filter one.
                runOnIdle { fixture.backend.changes = emptyList() }
                fixture.git.refresh()
                waitUntil { (fixture.git.state.value.status as? GitRead.Ready)?.value?.changes?.isEmpty() == true }
                runOnIdle { fixture.git.selectChangeFilter(GitChangeFilter.Unstaged) }
                onNodeWithText("尚无文件更改").assertIsDisplayed()
                onNodeWithText("此项目中的更改将显示在此处。").assertIsDisplayed()

                // A failed status read keeps its real failure and never pretends the tree is clean.
                runOnIdle { fixture.backend.statusFailure = GitFailure(GitFailureKind.CommandFailed, "git status 退出码 128") }
                fixture.git.refresh()
                waitUntil { (fixture.git.state.value.status as? GitRead.Failed) != null }
                onNodeWithTag("git-changes-error").assertExists()
                onNodeWithText("无法加载更改").assertIsDisplayed()
                onNodeWithText("git status 退出码 128").assertIsDisplayed()
                onNodeWithTag("git-changes-empty").assertDoesNotExist()
            }
            assertTrue(fixture.backend.mutations.isEmpty())
        } finally { fixture.close() }
    }

    @Test
    fun emptyChangesContentWithoutAnInjectedReviewSlotIsHonest() {
        runSkikoComposeUiTest(size = Size(420f, 800f)) {
            val scope = ThreadPanelScope(HostId("no-review-host"), SessionId("parent"))
            setContent { HarnessTheme(themeMode = HarnessThemeMode.Light) {
                ThreadPanels(ThreadPanelsState(scope, openTabs = listOf(ThreadPanelTab.Changes),
                    selectedTab = ThreadPanelTab.Changes), { })
            } }
            // No slot means no Git snapshot: say so instead of rendering an empty review.
            onNodeWithText("审查不可用").assertIsDisplayed()
            onNodeWithTag("git-changes-panel").assertDoesNotExist()
        }
    }
}

/** The same body the app injects: align the review read with the filter, then render the snapshot. */
@androidx.compose.runtime.Composable
private fun ChangesFor(state: GitState, controller: GitController) {
    LaunchedEffect(controller, state.selectedCwd, state.changeFilter) {
        if (state.repository !is GitRead.Ready) return@LaunchedEffect
        if ((state.reviewDiff as? GitRead.Ready<GitDiffSnapshot>)?.value?.request?.source == state.changeFilter.diffSource) return@LaunchedEffect
        controller.refreshReviewDiff()
    }
    GitChangesPanel(state, onSelectFilter = { filter ->
        controller.selectChangeFilter(filter)
        controller.refreshReviewDiff()
    }, onSelectSource = { source ->
        controller.selectChangeFilter(if (source == GitDiffSource.Index) GitChangeFilter.Staged else GitChangeFilter.Unstaged)
        controller.refreshReviewDiff()
    }, onRefresh = { controller.refresh() })
}

private class GitChangesFixture : AutoCloseable {
    val scope = ThreadPanelScope(HostId("changes-panel-host"), SessionId("parent"))
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    val backend = ReadOnlyGitBackend()
    val git = GitController(coroutineScope, backend)

    init { runBlocking {
        git.bind(backend.binding)
        git.selectRepository("/fixture/project")
    } }

    override fun close() { git.close(); coroutineScope.cancel() }
}

/**
 * Answers reads only. Mutating capabilities are deliberately absent from the type, so the review
 * pane cannot stage, commit, check out or create a worktree even by accident; [mutations] stays
 * empty unless something bypasses the interfaces.
 */
private class ReadOnlyGitBackend : GitBackend {
    val binding = GitBinding(null, HostId("changes-panel-host"), 1, GitAccess.LocalReadOnly)
    var changes = listOf(
        GitChange("edited.kt", indexStatus = ' ', worktreeStatus = 'M'),
        GitChange("added.kt", indexStatus = 'A', worktreeStatus = ' '),
    )
    var statusFailure: GitFailure? = null
    var statusReads = 0
    val reviewReads = mutableListOf<GitDiffSource>()
    val mutations = mutableListOf<String>()
    private val repository = GitRepository(binding, "/fixture/project", "/fixture/project/.git", "/fixture/project/.git")

    override suspend fun discover(binding: GitBinding, cwd: String) = repository.copy(root = cwd, commonDir = "$cwd/.git", gitDir = "$cwd/.git")
    override suspend fun status(repository: GitRepository): GitStatusSnapshot {
        statusReads++
        statusFailure?.let { throw GitReadException(it) }
        return GitStatusSnapshot(changes, "refs/heads/main", "a".repeat(40))
    }
    override suspend fun branches(repository: GitRepository) = listOf(GitBranch("refs/heads/main", "a".repeat(40), true, false))
    override suspend fun worktrees(repository: GitRepository) = listOf(GitWorktree(repository.root, "a".repeat(40), "refs/heads/main"))
    override suspend fun diff(repository: GitRepository, request: GitDiffRequest): GitDiffSnapshot {
        reviewReads += request.source
        return GitDiffSnapshot(request, when (request.source) {
            GitDiffSource.Index -> STAGED_PATCH
            else -> WORKTREE_PATCH
        }, "a".repeat(40))
    }
}

private const val WORKTREE_PATCH =
    "diff --git a/edited.kt b/edited.kt\n--- a/edited.kt\n+++ b/edited.kt\n@@ -1,2 +1,3 @@\n one\n+two\n three\n"

private const val STAGED_PATCH =
    "diff --git a/added.kt b/added.kt\nnew file mode 100644\n--- /dev/null\n+++ b/added.kt\n@@ -0,0 +1 @@\n+new\n"
