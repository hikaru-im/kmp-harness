package im.hikaru.harness.desktop

import androidx.compose.runtime.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import im.hikaru.harness.client.projects.*
import im.hikaru.harness.ui.foundation.HarnessTheme
import im.hikaru.harness.ui.foundation.HarnessThemeMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class ProjectFileSearchInteractionTest {
    private class Search : ProjectFileSearch {
        var searchBlock: suspend (String) -> ProjectFileSearchResult = {
            ProjectFileSearchResult(listOf(ProjectFileMatch("src/alpha.kt"), ProjectFileMatch("src/beta.kt")))
        }
        var readBlock: suspend (ProjectFileMatch) -> String = { "preview: ${it.path}" }
        override suspend fun search(target: ProjectFileSearchTarget, query: String) = searchBlock(query)
        override suspend fun read(target: ProjectFileSearchTarget, file: ProjectFileMatch) = readBlock(file)
    }

    private fun target(fixture: ProjectsRecentFixture) = ProjectFileSearchTarget(
        ProjectsRecentDisplayData.binding(fixture.connection), ProjectsRecentDisplayData.projects.first(),
    )

    @Test
    fun loadingKeyboardSelectionPreviewBackAndEscape() {
        val fixture = ProjectsRecentFixture()
        try {
            runComposeUiTest {
                val gate = CompletableDeferred<Unit>()
                val search = Search().apply { searchBlock = { gate.await(); ProjectFileSearchResult(listOf(
                    ProjectFileMatch("src/alpha.kt"), ProjectFileMatch("src/beta.kt")), truncated = true) } }
                var open by mutableStateOf(true)
                setContent { HarnessTheme(HarnessThemeMode.Light) {
                    if (open) ProjectFileSearchDialog(target(fixture), search) { open = false }
                } }
                onNodeWithTag("project-file-search-loading").assertExists()
                runOnIdle { gate.complete(Unit) }
                waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag("project-file-result-src/beta.kt").fetchSemanticsNodes().isNotEmpty() }
                onNodeWithText("仅显示部分结果，请输入更具体的路径。").assertExists()
                onNodeWithTag("project-file-search-input").performKeyInput {
                    pressKey(Key.DirectionDown); pressKey(Key.Enter)
                }
                waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag("project-file-preview").fetchSemanticsNodes().isNotEmpty() }
                onNodeWithTag("project-file-preview").assertTextEquals("preview: src/beta.kt")
                onNodeWithTag("project-file-search-back").performClick()
                onNodeWithTag("project-file-search-input").performKeyInput {
                    pressKey(Key.DirectionUp); pressKey(Key.Enter)
                }
                waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag("project-file-preview").fetchSemanticsNodes().isNotEmpty() }
                onNodeWithTag("project-file-preview").assertTextEquals("preview: src/alpha.kt")
                onNodeWithTag("project-file-search").performKeyInput { pressKey(Key.Escape) }
                onNodeWithTag("project-file-search").assertDoesNotExist()
            }
        } finally { fixture.close() }
    }

    @Test
    fun searchAndReadFailuresRetryWithoutShowingExceptionDetailsAndEmptyQueryResults() {
        val fixture = ProjectsRecentFixture()
        try {
            runComposeUiTest {
                var searchAttempts = 0
                var readAttempts = 0
                val search = Search().apply {
                    searchBlock = { query ->
                        if (++searchAttempts == 1) error("/private/sensitive-path")
                        ProjectFileSearchResult(if (query == "missing") emptyList() else listOf(ProjectFileMatch("alpha.kt")))
                    }
                    readBlock = { if (++readAttempts == 1) error("sensitive file contents") else "safe preview" }
                }
                setContent { HarnessTheme(HarnessThemeMode.Light) { ProjectFileSearchDialog(target(fixture), search) {} } }
                waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag("project-file-search-retry").fetchSemanticsNodes().isNotEmpty() }
                onNodeWithText("/private/sensitive-path").assertDoesNotExist()
                onNodeWithTag("project-file-search-retry").performClick()
                waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag("project-file-result-alpha.kt").fetchSemanticsNodes().isNotEmpty() }
                onNodeWithTag("project-file-result-alpha.kt").performClick()
                waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag("project-file-read-retry").fetchSemanticsNodes().isNotEmpty() }
                onNodeWithText("sensitive file contents").assertDoesNotExist()
                onNodeWithTag("project-file-read-retry").performClick()
                waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag("project-file-preview").fetchSemanticsNodes().isNotEmpty() }
                onNodeWithTag("project-file-preview").assertTextEquals("safe preview")
                onNodeWithTag("project-file-search-back").performClick()
                onNodeWithTag("project-file-search-input").performTextInput("missing")
                onNodeWithTag("project-file-result-alpha.kt").assertDoesNotExist()
                waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag("project-file-search-empty").fetchSemanticsNodes().isNotEmpty() }
                runOnIdle { assertEquals(2, readAttempts) }
            }
        } finally { fixture.close() }
    }

    @Test
    fun lateSearchCannotReplaceANewerQueryOrProject() {
        val fixture = ProjectsRecentFixture()
        try {
            runComposeUiTest {
                val started = CompletableDeferred<Unit>()
                val release = CompletableDeferred<Unit>()
                val oldTarget = target(fixture)
                var owner by mutableStateOf(oldTarget)
                val search = Search().apply { searchBlock = { query ->
                    if (query == "old") withContext(NonCancellable) { started.complete(Unit); release.await() }
                    ProjectFileSearchResult(listOf(ProjectFileMatch(if (query == "old") "stale.kt" else "current.kt")))
                } }
                setContent { HarnessTheme(HarnessThemeMode.Light) { ProjectFileSearchDialog(owner, search) {} } }
                onNodeWithTag("project-file-search-input").performTextInput("old")
                waitUntil(timeoutMillis = 5_000) { started.isCompleted }
                onNodeWithTag("project-file-search-input").performTextClearance()
                onNodeWithTag("project-file-search-input").performTextInput("new")
                waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag("project-file-result-current.kt").fetchSemanticsNodes().isNotEmpty() }
                runOnIdle { owner = oldTarget.copy(project = oldTarget.project.copy(name = "Other project")) }
                onNodeWithTag("project-file-search-input").assertTextEquals("")
                runOnIdle { release.complete(Unit) }
                onNodeWithTag("project-file-result-stale.kt").assertDoesNotExist()
                waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag("project-file-result-current.kt").fetchSemanticsNodes().isNotEmpty() }
            }
        } finally { fixture.close() }
    }
}
