package im.hikaru.harness.desktop

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import im.hikaru.harness.client.git.*
import im.hikaru.harness.desktop.git.GitPreviewFixtures
import im.hikaru.harness.ui.foundation.HarnessTheme
import kotlin.test.*

/** Rich preview end to end over one diff holding every previewable and non-previewable file kind. */
@OptIn(ExperimentalTestApi::class)
class GitDiffBinaryPreviewInteractionTest {
    @Test
    fun richPreviewRendersImageSvgPdfAndMarkdownButNotUnknownOrDeletedFiles() {
        val f = GitWorktreesPageFixture()
        try {
            f.backend.patch = MIXED_DIFF
            f.backend.previewFiles = mapOf("docs/readme.md" to "# 标题\n\n正文 **粗体**")
            f.backend.previewBinaries = mapOf(
                "docs/shot.png" to GitPreviewFile.Binary(GitPreviewFixtures.png(), "image/png"),
                "assets/icon.svg" to GitPreviewFile.Binary(GitPreviewFixtures.svg(), "image/svg+xml"),
                "docs/paper.pdf" to GitPreviewFile.Binary(GitPreviewFixtures.pdf(), "application/pdf"),
            )
            f.git.refresh()
            runSkikoComposeUiTest(size = Size(1000f, 1600f)) {
                setContent { HarnessTheme { SettingsGitPage(f.git) } }

                onAllNodesWithTag("git-diff-file").assertCountEquals(6)
                // Off by default: nothing is read and every card keeps its diff text.
                assertTrue(f.backend.previewReads.isEmpty())
                onAllNodesWithTag("git-diff-rich-preview-image").assertCountEquals(0)
                onAllNodesWithTag("git-diff-rich-preview-svg").assertCountEquals(0)
                onAllNodesWithTag("git-diff-rich-preview-pdf").assertCountEquals(0)

                clickGitNode(scrollGitNodeIntoView(onNodeWithTag("git-diff-rich-preview")))
                waitUntil(timeoutMillis = 5_000) {
                    onAllNodesWithTag("git-diff-rich-preview-image").fetchSemanticsNodes().isNotEmpty() &&
                        onAllNodesWithTag("git-diff-rich-preview-svg").fetchSemanticsNodes().isNotEmpty() &&
                        onAllNodesWithTag("git-diff-rich-preview-pdf").fetchSemanticsNodes().isNotEmpty()
                }
                onAllNodesWithTag("git-diff-rich-preview-heading-docs/readme.md").assertCountEquals(1)
                // The unknown format keeps its diff text instead of a preview frame.
                onAllNodesWithTag("git-diff-rich-preview-notes.txt").assertCountEquals(0)
                onAllNodesWithTag("git-diff-rich-preview-notice-notes.txt").assertCountEquals(0)
                // A deleted file has no working-tree bytes, so it is never read nor previewed.
                onAllNodesWithTag("git-diff-rich-preview-old.png").assertCountEquals(0)
                onAllNodesWithTag("git-diff-rich-preview-notice-old.png").assertCountEquals(0)
                onAllNodesWithTag("git-diff-file-patch").assertCountEquals(1)
                assertEquals(
                    setOf("docs/readme.md", "docs/shot.png", "assets/icon.svg", "docs/paper.pdf"),
                    f.backend.previewReads.map { it.second }.toSet(),
                )
            }
        } finally { f.close() }
    }

    @Test
    fun undecodableBinaryKeepsItsDiffTextInsteadOfAnEmptyFrame() {
        val f = GitWorktreesPageFixture()
        try {
            f.backend.patch = MIXED_DIFF
            f.backend.previewBinaries = mapOf(
                // A .png the decoder cannot read: the card must say so and fall back to the text.
                "docs/shot.png" to GitPreviewFile.Binary("not a png".toByteArray(), "image/png"),
                "assets/icon.svg" to GitPreviewFile.Binary(GitPreviewFixtures.svg(), "image/svg+xml"),
                "docs/paper.pdf" to GitPreviewFile.Binary(GitPreviewFixtures.pdf(), "application/pdf"),
            )
            f.git.refresh()
            runSkikoComposeUiTest(size = Size(1000f, 1600f)) {
                setContent { HarnessTheme { SettingsGitPage(f.git) } }
                clickGitNode(scrollGitNodeIntoView(onNodeWithTag("git-diff-rich-preview")))
                waitUntil(timeoutMillis = 5_000) {
                    onAllNodesWithTag("git-diff-rich-preview-notice-docs/shot.png").fetchSemanticsNodes().isNotEmpty()
                }
                onAllNodesWithTag("git-diff-rich-preview-image").assertCountEquals(0)
            }
        } finally { f.close() }
    }

    private companion object {
        /**
         * Six files in one diff: markdown, raster image, SVG, PDF, an unsupported text format, and a
         * deleted image. Binary files carry no text body, exactly as `git diff` reports them.
         */
        const val MIXED_DIFF = "diff --git a/docs/readme.md b/docs/readme.md\n" +
            "index 1111111..2222222 100644\n" +
            "--- a/docs/readme.md\n" +
            "+++ b/docs/readme.md\n" +
            "@@ -1,1 +1,2 @@\n" +
            " # 旧标题\n" +
            "+正文\n" +
            "diff --git a/docs/shot.png b/docs/shot.png\n" +
            "index 1111111..2222222 100644\n" +
            "Binary files a/docs/shot.png and b/docs/shot.png differ\n" +
            "diff --git a/assets/icon.svg b/assets/icon.svg\n" +
            "index 1111111..2222222 100644\n" +
            "--- a/assets/icon.svg\n" +
            "+++ b/assets/icon.svg\n" +
            "@@ -1,1 +1,1 @@\n" +
            "-<svg width=\"10\"/>\n" +
            "+<svg width=\"24\"/>\n" +
            "diff --git a/docs/paper.pdf b/docs/paper.pdf\n" +
            "index 1111111..2222222 100644\n" +
            "Binary files a/docs/paper.pdf and b/docs/paper.pdf differ\n" +
            "diff --git a/notes.txt b/notes.txt\n" +
            "index 1111111..2222222 100644\n" +
            "--- a/notes.txt\n" +
            "+++ b/notes.txt\n" +
            "@@ -1,1 +1,1 @@\n" +
            "-old\n" +
            "+new\n" +
            "diff --git a/old.png b/old.png\n" +
            "deleted file mode 100644\n" +
            "index 1111111..0000000\n" +
            "Binary files a/old.png and /dev/null differ\n"
    }
}
