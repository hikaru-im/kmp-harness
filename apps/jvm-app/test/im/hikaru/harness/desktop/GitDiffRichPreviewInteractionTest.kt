package im.hikaru.harness.desktop

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import im.hikaru.harness.client.git.*
import im.hikaru.harness.ui.foundation.HarnessTheme
import kotlinx.coroutines.CompletableDeferred
import kotlin.test.*

/** Production page/controller with a controlled adapter: the diff rich-preview toggle, end to end. */
@OptIn(ExperimentalTestApi::class)
class GitDiffRichPreviewInteractionTest {
    @Test
    fun richPreviewToggleRendersMarkdownOnlyForMarkdownFilesAndIsCached() {
        val f = GitWorktreesPageFixture()
        try {
            f.backend.patch = MARKDOWN_DIFF
            f.backend.previewFiles = mapOf("docs/readme.md" to "# 标题\n\n正文 **粗体**")
            f.git.refresh()
            runSkikoComposeUiTest(size = Size(1000f, 1200f)) {
                setContent { HarnessTheme { SettingsGitPage(f.git) } }
                onNodeWithTag("git-diff-rich-preview").assertExists()
                // Off by default: both files show diff text and nothing has been read.
                onAllNodesWithTag("git-diff-file-patch").assertCountEquals(2)
                onAllNodesWithTag("git-diff-rich-preview-docs/readme.md").assertCountEquals(0)
                assertTrue(f.backend.previewReads.isEmpty())
                clickGitNode(scrollGitNodeIntoView(onNodeWithTag("git-diff-rich-preview")))
                waitUntil(timeoutMillis = 5_000) {
                    onAllNodesWithTag("git-diff-rich-preview-docs/readme.md").fetchSemanticsNodes().isNotEmpty()
                }
                // The markdown file renders its preview; the .kt file keeps its diff text.
                onAllNodesWithTag("git-diff-rich-preview-heading-docs/readme.md").assertCountEquals(1)
                onAllNodesWithTag("git-diff-file-patch").assertCountEquals(1)
                onAllNodesWithTag("git-diff-rich-preview-notice-docs/readme.md").assertCountEquals(0)
                assertEquals(listOf("docs/readme.md"), f.backend.previewReads.map { it.second })
                // Off again restores both diff texts.
                clickGitNode(scrollGitNodeIntoView(onNodeWithTag("git-diff-rich-preview")))
                waitUntil(timeoutMillis = 5_000) {
                    onAllNodesWithTag("git-diff-file-patch").fetchSemanticsNodes().size == 2
                }
                onAllNodesWithTag("git-diff-rich-preview-docs/readme.md").assertCountEquals(0)
                // On again reuses the cached text: the working tree is read once per incarnation.
                clickGitNode(scrollGitNodeIntoView(onNodeWithTag("git-diff-rich-preview")))
                waitUntil(timeoutMillis = 5_000) {
                    onAllNodesWithTag("git-diff-rich-preview-docs/readme.md").fetchSemanticsNodes().isNotEmpty()
                }
                assertEquals(1, f.backend.previewReads.size)
            }
        } finally { f.close() }
    }

    @Test
    fun richPreviewMenuItemSharesStateWithToolbarToggle() {
        val f = GitWorktreesPageFixture()
        try {
            f.backend.patch = MARKDOWN_DIFF
            f.backend.previewFiles = mapOf("docs/readme.md" to "# 标题\n\n正文 **粗体**")
            f.git.refresh()
            runSkikoComposeUiTest(size = Size(1000f, 1200f)) {
                setContent { HarnessTheme { SettingsGitPage(f.git) } }

                fun openOptions() {
                    clickGitNode(scrollGitNodeIntoView(onNodeWithTag("git-diff-options")))
                    waitUntil(timeoutMillis = 5_000) {
                        onAllNodesWithTag("menu:rich-preview").fetchSemanticsNodes().isNotEmpty()
                    }
                }

                openOptions()
                onNodeWithTag("menu:rich-preview")
                    .assertTextContains("渲染预览", substring = true)
                    .assertIsNotSelected()
                clickGitNode(onNodeWithTag("menu:rich-preview"))
                waitUntil(timeoutMillis = 5_000) {
                    onAllNodesWithTag("git-diff-rich-preview-docs/readme.md")
                        .fetchSemanticsNodes().isNotEmpty()
                }
                onAllNodesWithTag("git-diff-rich-preview-heading-docs/readme.md").assertCountEquals(1)
                openOptions()
                onNodeWithTag("menu:rich-preview").assertIsSelected()

                clickGitNode(scrollGitNodeIntoView(onNodeWithTag("git-diff-options")))
                clickGitNode(scrollGitNodeIntoView(onNodeWithTag("git-diff-rich-preview")))
                waitUntil(timeoutMillis = 5_000) {
                    onAllNodesWithTag("git-diff-file-patch").fetchSemanticsNodes().size == 2
                }
                onAllNodesWithTag("git-diff-rich-preview-docs/readme.md").assertCountEquals(0)
                openOptions()
                onNodeWithTag("menu:rich-preview").assertIsNotSelected()
            }
        } finally { f.close() }
    }

    @Test
    fun deletedMarkdownIsNeverReadAndAMissingPreviewFallsBackWithItsReason() {
        val f = GitWorktreesPageFixture()
        try {
            f.backend.patch = DELETED_AND_MARKDOWN_DIFF
            f.git.refresh()
            runSkikoComposeUiTest(size = Size(1000f, 1200f)) {
                setContent { HarnessTheme { SettingsGitPage(f.git) } }
                // The deleted file starts collapsed and is never previewed.
                onAllNodesWithTag("git-diff-file").assertCountEquals(2)
                clickGitNode(scrollGitNodeIntoView(onNodeWithTag("git-diff-rich-preview")))
                waitUntil(timeoutMillis = 5_000) {
                    onAllNodesWithTag("git-diff-rich-preview-notice-notes.md").fetchSemanticsNodes().isNotEmpty()
                }
                onAllNodesWithTag("git-diff-rich-preview-notes.md").assertCountEquals(0)
                onAllNodesWithTag("git-diff-rich-preview-old.md").assertCountEquals(0)
                // A failed or missing preview keeps the diff text visible.
                onAllNodesWithTag("git-diff-file-patch").assertCountEquals(1)
                assertEquals(listOf("notes.md"), f.backend.previewReads.map { it.second })
            }
        } finally { f.close() }
    }

    @Test
    fun pendingPreviewShowsItsNoticeUntilTheReadCompletes() {
        val f = GitWorktreesPageFixture()
        try {
            f.backend.patch = MARKDOWN_DIFF
            f.backend.previewFiles = mapOf("docs/readme.md" to "# 标题")
            val gate = CompletableDeferred<Unit>()
            f.backend.previewGate = gate
            f.git.refresh()
            runSkikoComposeUiTest(size = Size(1000f, 1200f)) {
                setContent { HarnessTheme { SettingsGitPage(f.git) } }
                clickGitNode(scrollGitNodeIntoView(onNodeWithTag("git-diff-rich-preview")))
                waitUntil(timeoutMillis = 5_000) {
                    onAllNodesWithTag("git-diff-rich-preview-notice-docs/readme.md").fetchSemanticsNodes().isNotEmpty()
                }
                // While loading, the diff text still fills the card.
                onAllNodesWithTag("git-diff-file-patch").assertCountEquals(2)
                gate.complete(Unit)
                waitUntil(timeoutMillis = 5_000) {
                    onAllNodesWithTag("git-diff-rich-preview-docs/readme.md").fetchSemanticsNodes().isNotEmpty()
                }
                onAllNodesWithTag("git-diff-rich-preview-notice-docs/readme.md").assertCountEquals(0)
                onAllNodesWithTag("git-diff-file-patch").assertCountEquals(1)
            }
        } finally { f.close() }
    }

    private companion object {
        const val MARKDOWN_DIFF = "diff --git a/docs/readme.md b/docs/readme.md\n" +
            "--- a/docs/readme.md\n" +
            "+++ b/docs/readme.md\n" +
            "@@ -1,1 +1,1 @@\n" +
            "-old\n" +
            "+# 标题\n" +
            "diff --git a/src/app.kt b/src/app.kt\n" +
            "--- a/src/app.kt\n" +
            "+++ b/src/app.kt\n" +
            "@@ -1,1 +1,2 @@\n" +
            " base\n" +
            "+extra\n"

        const val DELETED_AND_MARKDOWN_DIFF = "diff --git a/old.md b/old.md\n" +
            "deleted file mode 100644\n" +
            "--- a/old.md\n" +
            "+++ /dev/null\n" +
            "@@ -1,1 +0,0 @@\n" +
            "-gone\n" +
            "diff --git a/notes.md b/notes.md\n" +
            "--- a/notes.md\n" +
            "+++ b/notes.md\n" +
            "@@ -1,1 +1,1 @@\n" +
            "-a\n" +
            "+b\n"
    }
}
