package im.hikaru.harness.desktop

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import im.hikaru.harness.client.git.*
import im.hikaru.harness.client.settings.*
import im.hikaru.harness.ui.foundation.HarnessTheme
import kotlinx.coroutines.*
import kotlin.test.*

/** Production Worktrees page wired to the real settings repository; only the store is controlled. */
@OptIn(ExperimentalTestApi::class)
class WorktreeRootSettingInteractionTest {
    private class RecordingStore(var value: WorktreeSettings? = null) : WorktreeSettingsStore {
        override val durable: Boolean = true
        override suspend fun read(): WorktreeSettings? = value
        override suspend fun write(settings: WorktreeSettings) { value = settings }
    }

    @Test
    fun rootValidatesSavesAndSeedsTheCreationPath() {
        val fixture = GitWorktreesPageFixture()
        val store = RecordingStore()
        val repository = WorktreeSettingsRepository(store)
        runBlocking { repository.load() }
        val scope = CoroutineScope(Dispatchers.Unconfined)
        try {
            fixture.git.refresh()
            runSkikoComposeUiTest(size = Size(720f, 1300f)) {
                setContent {
                    HarnessTheme {
                        val state by repository.state.collectAsState()
                        WorktreesPage(
                            fixture.git,
                            worktreeSettings = state,
                            onSaveWorktreeSettings = { next -> scope.launch { repository.save(next) } },
                        )
                    }
                }
                waitUntil(timeoutMillis = 10_000) { fixture.ready() }
                // A blank root is valid ("default location") but nothing has changed yet.
                onNodeWithTag("worktree-root-save").assertIsNotEnabled()
                onNodeWithTag("worktree-root-input").performTextReplacement("relative/dir")
                onNodeWithTag("worktree-root-save").assertIsNotEnabled()
                onNodeWithText("请输入绝对路径，或留空使用默认位置。").assertExists()

                onNodeWithTag("worktree-root-input").performTextReplacement("/tmp/managed-worktrees")
                clickGitNode(onNodeWithTag("worktree-root-save").assertIsEnabled())
                waitUntil(timeoutMillis = 5_000) { repository.state.value.saveRevision > 0 }
                onNodeWithTag("worktree-root-saved").assertExists()
                onNodeWithTag("worktree-root-save").assertIsNotEnabled()
                assertEquals("/tmp/managed-worktrees", store.value?.root)

                // The saved root seeds the new worktree's parent directory once a real branch is typed.
                onNodeWithTag("git-worktree-create-branch").performScrollTo().performTextReplacement("feature/root-seed")
                waitUntil(timeoutMillis = 5_000) {
                    onAllNodesWithTag("git-worktree-create-auto-path").fetchSemanticsNodes().isNotEmpty()
                }
                onNodeWithTag("git-worktree-create-auto-path").assertTextEquals("路径随分支名称自动生成")
                onNodeWithTag("git-worktree-create-path").assertTextContains("/tmp/managed-worktrees/feature-root-seed", substring = true)
                onNodeWithTag("git-worktree-create-submit").assertIsEnabled()
            }
        } finally { scope.cancel(); fixture.close() }
    }

    @Test
    fun readOnlyStoreKeepsTheFieldAndSaveDisabled() {
        val fixture = GitWorktreesPageFixture()
        val repository = WorktreeSettingsRepository(MemoryWorktreeSettingsStore())
        runBlocking { repository.load() }
        try {
            fixture.git.refresh()
            runSkikoComposeUiTest(size = Size(720f, 1300f)) {
                setContent {
                    HarnessTheme {
                        val state by repository.state.collectAsState()
                        WorktreesPage(fixture.git, worktreeSettings = state)
                    }
                }
                waitUntil(timeoutMillis = 10_000) { fixture.ready() }
                onNodeWithTag("worktree-root-input").assertIsNotEnabled()
                onNodeWithTag("worktree-root-save").assertIsNotEnabled()
                onNodeWithTag("git-worktree-create-auto-path").assertDoesNotExist()
            }
        } finally { fixture.close() }
    }

    @Test
    fun environmentControlsPersistStoredPreferences() {
        val fixture = GitWorktreesPageFixture()
        val store = RecordingStore()
        val repository = WorktreeSettingsRepository(store)
        runBlocking { repository.load() }
        val scope = CoroutineScope(Dispatchers.Unconfined)
        try {
            fixture.git.refresh()
            runSkikoComposeUiTest(size = Size(720f, 1700f)) {
                setContent {
                    HarnessTheme {
                        val state by repository.state.collectAsState()
                        WorktreesPage(
                            fixture.git,
                            worktreeSettings = state,
                            onSaveWorktreeSettings = { next -> scope.launch { repository.save(next) } },
                        )
                    }
                }
                waitUntil(timeoutMillis = 10_000) { fixture.ready() }

                // Stored defaults are shown, and nothing is dirty yet.
                onNodeWithTag("worktree-keep-count-input").performScrollTo()
                    .assertTextContains("15", substring = true)
                onNodeWithTag("worktree-keep-count-save").assertIsNotEnabled()

                // A value below the original `min(1)` schema is rejected inline.
                onNodeWithTag("worktree-keep-count-input").performTextReplacement("0")
                onNodeWithTag("worktree-keep-count-save").assertIsNotEnabled()
                onNodeWithText("请输入不小于 1 的整数。").assertExists()

                // A valid limit is persisted.
                onNodeWithTag("worktree-keep-count-input").performTextReplacement("20")
                clickGitNode(onNodeWithTag("worktree-keep-count-save").assertIsEnabled())
                waitUntil(timeoutMillis = 5_000) { store.value?.keepCount == 20 }
                onNodeWithTag("worktree-keep-count-save").assertIsNotEnabled()

                // The upstream-refresh toggle persists the `best-effort` mode.
                onNodeWithTag("worktree-upstream-refresh-switch").performScrollTo().performClick()
                waitUntil(timeoutMillis = 5_000) {
                    store.value?.upstreamRefreshMode == WorktreeUpstreamRefreshMode.BestEffort
                }

                // Disabling auto-cleanup must be confirmed first; the checkbox alone changes nothing.
                onNodeWithTag("worktree-auto-cleanup-switch").performScrollTo().performClick()
                waitUntil(timeoutMillis = 5_000) {
                    onAllNodesWithTag("worktree-auto-cleanup-confirm:confirm").fetchSemanticsNodes().isNotEmpty()
                }
                assertTrue(store.value?.autoCleanupEnabled == true)
                onNodeWithTag("worktree-auto-cleanup-confirm:confirm").performClick()
                waitUntil(timeoutMillis = 5_000) { store.value?.autoCleanupEnabled == false }
                onNodeWithText("自动删除已停用", substring = true)
                    .assertTextContains("自动删除已停用", substring = true)
            }
        } finally { scope.cancel(); fixture.close() }
    }
}
