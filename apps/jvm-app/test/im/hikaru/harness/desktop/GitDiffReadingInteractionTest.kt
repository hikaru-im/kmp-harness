package im.hikaru.harness.desktop

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import im.hikaru.harness.client.git.*
import im.hikaru.harness.ui.foundation.HarnessTheme
import kotlin.math.abs
import kotlin.test.*

/** Reading a diff: restored auto-open limits, sticky headers, in-diff search and context expansion. */
@OptIn(ExperimentalTestApi::class)
class GitDiffReadingInteractionTest {
    @Test
    fun aDiffBeyondTheRestoredLimitsShowsTheBannerAndOpensOneFileAtATime() {
        val f = GitWorktreesPageFixture()
        try {
            f.backend.patch = (1..26).joinToString("\n") { index ->
                "diff --git a/f$index.kt b/f$index.kt\n--- a/f$index.kt\n+++ b/f$index.kt\n" +
                    "@@ -1 +1 @@\n-old-$index\n+new-$index"
            }
            f.git.refresh()
            runSkikoComposeUiTest(size = Size(1000f, 1200f)) {
                setContent { HarnessTheme { SettingsGitPage(f.git) } }
                onNodeWithTag("git-diff-large-banner").assertTextEquals(GitDiffLargeBannerCopy)
                onAllNodesWithTag("git-diff-file").assertCountEquals(26)
                // No file opens by itself; opening a second one closes the first.
                onAllNodesWithTag("git-diff-file-patch").assertCountEquals(0)
                scrollGitNodeIntoView(onAllNodesWithTag("git-diff-file")[0], "git-diff-vertical")
                clickGitNode(onAllNodesWithTag("git-diff-file")[0])
                waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag("git-diff-file-patch").fetchSemanticsNodes().size == 1 }
                scrollGitNodeIntoView(onAllNodesWithTag("git-diff-file")[1], "git-diff-vertical")
                clickGitNode(onAllNodesWithTag("git-diff-file")[1])
                waitUntil(timeoutMillis = 5_000) {
                    onAllNodesWithTag("git-diff-file")[0].fetchSemanticsNode()
                        .config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text.startsWith("展开") }
                }
                onAllNodesWithTag("git-diff-file-patch").assertCountEquals(1)
            }
        } finally { f.close() }
    }

    @Test
    fun aSmallDiffHasNoBannerAndKeepsEveryFileOpen() {
        val f = GitWorktreesPageFixture()
        try {
            f.backend.patch = TWO_FILE_DIFF
            f.git.refresh()
            runSkikoComposeUiTest(size = Size(1000f, 1200f)) {
                setContent { HarnessTheme { SettingsGitPage(f.git) } }
                onNodeWithTag("git-diff-large-banner").assertDoesNotExist()
                onAllNodesWithTag("git-diff-file-patch").assertCountEquals(2)
            }
        } finally { f.close() }
    }

    @Test
    fun theFileHeaderStaysAtTheTopWhileItsBodyScrollsPast() {
        val f = GitWorktreesPageFixture()
        try {
            f.backend.patch = TALL_FILE_DIFF
            f.git.refresh()
            runSkikoComposeUiTest(size = Size(1000f, 1200f)) {
                setContent { HarnessTheme { SettingsGitPage(f.git) } }
                onAllNodesWithTag("git-diff-file-patch").assertCountEquals(2)
                scrollGitNodeIntoView(onNodeWithTag("git-diff-viewport"))
                val viewport = onNodeWithTag("git-diff-viewport").fetchSemanticsNode().boundsInRoot
                onNodeWithTag("git-diff-vertical").performMouseInput {
                    moveTo(center)
                    scroll(320f)
                }
                waitForIdle()
                val scrolled = onNodeWithTag("git-diff-vertical").fetchSemanticsNode()
                    .config[SemanticsProperties.VerticalScrollAxisRange].value()
                assertTrue(scrolled > 0f, "the body should have scrolled")
                val headerAfter = onAllNodesWithTag("git-diff-file")[0].fetchSemanticsNode().boundsInRoot.top
                assertTrue(abs(headerAfter - viewport.top) < 24f,
                    "header top=$headerAfter viewport top=${viewport.top}")
            }
        } finally { f.close() }
    }

    @Test
    fun searchCountsHitsNavigatesAndExpandsTheMatchedFile() {
        val f = GitWorktreesPageFixture()
        try {
            f.backend.patch = TWO_FILE_DIFF
            f.git.refresh()
            runSkikoComposeUiTest(size = Size(1000f, 1200f)) {
                setContent { HarnessTheme { SettingsGitPage(f.git) } }
                clickGitNode(scrollGitNodeIntoView(onNodeWithTag("git-diff-search-toggle")))
                waitUntil(timeoutMillis = 5_000) {
                    onAllNodesWithTag("git-diff-search").fetchSemanticsNodes().isNotEmpty()
                }
                scrollGitNodeIntoView(onNodeWithTag("git-diff-search-field"))
                onNodeWithTag("git-diff-search-field").performTextInput("line")
                waitUntil(timeoutMillis = 5_000) {
                    onAllNodesWithTag("git-diff-search-count").fetchSemanticsNodes().isNotEmpty() &&
                        onNodeWithTag("git-diff-search-count").fetchSemanticsNode()
                            .config.getOrNull(SemanticsProperties.Text).orEmpty()
                            .any { it.text == "1/4" }
                }
                onAllNodesWithTag("git-diff-file")[0].assertTextContains("3 处命中", substring = true)
                onAllNodesWithTag("git-diff-file")[1].assertTextContains("1 处命中", substring = true)
                onNodeWithTag("git-diff-search-next").performClick()
                onNodeWithTag("git-diff-search-count").assertTextEquals("2/4")
                onNodeWithTag("git-diff-search-prev").performClick()
                onNodeWithTag("git-diff-search-count").assertTextEquals("1/4")

                // Collapse the only file that holds "extra", then search for it: the hit reopens it.
                scrollGitNodeIntoView(onAllNodesWithTag("git-diff-file")[1], "git-diff-viewport")
                clickGitNode(onAllNodesWithTag("git-diff-file")[1])
                waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag("git-diff-file-patch").fetchSemanticsNodes().size == 1 }
                onNodeWithTag("git-diff-search-field").performTextClearance()
                onNodeWithTag("git-diff-search-field").performTextInput("extra")
                waitUntil(timeoutMillis = 5_000) {
                    onAllNodesWithTag("git-diff-file-patch").fetchSemanticsNodes().size == 2
                }
                onNodeWithTag("git-diff-search-count").assertTextEquals("1/1")
            }
        } finally { f.close() }
    }

    @Test
    fun contextExpansionReadsTheWorkingTreeAndRevealsEachHunkGap() {
        val f = GitWorktreesPageFixture()
        try {
            f.backend.patch = TWO_HUNK_DIFF
            f.backend.previewFiles = mapOf("src/app.kt" to
                "one\ntwo\nthree\nfour\nfive\nsix\nseven\neight\nnine\nten\neleven\ntwelve\nthirteen")
            f.git.refresh()
            runSkikoComposeUiTest(size = Size(1000f, 1200f)) {
                setContent { HarnessTheme { SettingsGitPage(f.git) } }
                val load = onNodeWithTag("git-diff-context-load-src/app.kt")
                scrollGitNodeIntoView(load, "git-diff-viewport")
                clickGitNode(load)
                waitUntil(timeoutMillis = 5_000) {
                    onAllNodesWithTag("git-diff-context-above-0-src/app.kt").fetchSemanticsNodes().isNotEmpty()
                }
                assertTrue(f.backend.previewReads.any { it.second == "src/app.kt" })

                val above = onNodeWithTag("git-diff-context-above-0-src/app.kt")
                scrollGitNodeIntoView(above, "git-diff-viewport")
                clickGitNode(above)
                waitUntil(timeoutMillis = 5_000) {
                    onNodeWithTag("git-diff-file-patch").fetchSemanticsNode()
                        .config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text.contains(" one") }
                }
                onNodeWithTag("git-diff-file-patch").assertTextContains("@@ -1,3 +1,3 @@", substring = true)

                val below = onNodeWithTag("git-diff-context-below-0-src/app.kt")
                scrollGitNodeIntoView(below, "git-diff-viewport")
                clickGitNode(below)
                waitUntil(timeoutMillis = 5_000) {
                    onNodeWithTag("git-diff-file-patch").fetchSemanticsNode()
                        .config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text.contains(" nine") }
                }
                onNodeWithTag("git-diff-file-patch").assertTextContains("@@ -1,9 +1,9 @@", substring = true)
                onNodeWithTag("git-diff-file-patch").assertTextContains(" four", substring = true)
            }
        } finally { f.close() }
    }

    @Test
    fun contextExpansionReportsAnUnreadableWorkingTreeInsteadOfFakeContext() {
        val f = GitWorktreesPageFixture()
        try {
            f.backend.patch = TWO_HUNK_DIFF
            f.git.refresh()
            runSkikoComposeUiTest(size = Size(1000f, 1200f)) {
                setContent { HarnessTheme { SettingsGitPage(f.git) } }
                val load = onNodeWithTag("git-diff-context-load-src/app.kt")
                scrollGitNodeIntoView(load, "git-diff-viewport")
                clickGitNode(load)
                waitUntil(timeoutMillis = 5_000) {
                    onAllNodesWithTag("git-diff-context-unavailable-src/app.kt").fetchSemanticsNodes().isNotEmpty()
                }
                onNodeWithTag("git-diff-context-unavailable-src/app.kt")
                    .assertTextContains("受控夹具没有该路径的预览内容。", substring = true)
                onAllNodesWithTag("git-diff-context-above-0-src/app.kt").assertCountEquals(0)
                onAllNodesWithTag("git-diff-context-below-1-src/app.kt").assertCountEquals(0)
            }
        } finally { f.close() }
    }

    @Test
    fun aStagedDiffHidesContextExpansionBecauseTheWorkingTreeMayDiffer() {
        val f = GitWorktreesPageFixture()
        try {
            f.backend.patch = TWO_HUNK_DIFF
            f.git.refresh()
            runSkikoComposeUiTest(size = Size(1000f, 1200f)) {
                setContent { HarnessTheme { SettingsGitPage(f.git) } }
                onNodeWithTag("git-diff-source").performScrollTo().performMouseInput { click() }
                onNodeWithTag("menu:1").performMouseInput { click() }
                waitUntil(timeoutMillis = 5_000) {
                    f.git.state.value.diffRequest.source == GitDiffSource.Index && f.ready()
                }
                settleGitLayout()
                onAllNodesWithTag("git-diff-context-load-src/app.kt").assertCountEquals(0)
                onAllNodesWithTag("git-diff-file-patch").assertCountEquals(1)
            }
        } finally { f.close() }
    }

    @Test
    fun aDiffAtTheRestoredLimitsRecordsFirstFrameAndScrollMetrics() {
        val f = GitWorktreesPageFixture()
        try {
            f.backend.patch = THRESHOLD_DIFF
            f.git.refresh()
            runSkikoComposeUiTest(size = Size(1000f, 1200f)) {
                val firstFrameStart = System.nanoTime()
                setContent { HarnessTheme { SettingsGitPage(f.git) } }
                waitForIdle()
                val firstFrameMillis = (System.nanoTime() - firstFrameStart) / 1_000_000
                // Exactly 25 files and 2000 changed lines: both restored limits are inclusive.
                onNodeWithTag("git-diff-large-banner").assertDoesNotExist()
                onAllNodesWithTag("git-diff-file").assertCountEquals(25)
                onAllNodesWithTag("git-diff-file-patch").assertCountEquals(25)

                scrollGitNodeIntoView(onNodeWithTag("git-diff-viewport"))
                val scrollStart = System.nanoTime()
                repeat(10) {
                    onNodeWithTag("git-diff-vertical").performMouseInput {
                        moveTo(center)
                        scroll(320f)
                    }
                    waitForIdle()
                }
                val scrollMillis = (System.nanoTime() - scrollStart) / 1_000_000
                println(
                    "R4 metrics: files=25 changedLines=2000 firstFrame=${firstFrameMillis}ms " +
                        "tenScrolls=${scrollMillis}ms",
                )
                assertTrue(firstFrameMillis < 15_000, "first frame took ${firstFrameMillis}ms")
                assertTrue(scrollMillis < 20_000, "ten scrolls took ${scrollMillis}ms")
            }
        } finally { f.close() }
    }

    private companion object {
        /** The largest diff the restored limits still open by default: 25 files, 2000 changed lines. */
        val THRESHOLD_DIFF = (1..25).joinToString("\n") { index ->
            "diff --git a/f$index.kt b/f$index.kt\n--- a/f$index.kt\n+++ b/f$index.kt\n" +
                "@@ -0,0 +1,80 @@\n" + (1..80).joinToString("\n") { line -> "+f$index-line-$line" }
        }

        const val TWO_FILE_DIFF = "diff --git a/src/a.kt b/src/a.kt\n" +
            "--- a/src/a.kt\n+++ b/src/a.kt\n" +
            "@@ -1,2 +1,2 @@\n context line a\n-old line\n+new line\n" +
            "diff --git a/src/b.kt b/src/b.kt\n" +
            "--- a/src/b.kt\n+++ b/src/b.kt\n" +
            "@@ -1,1 +1,2 @@\n base\n+extra line\n"

        val TALL_FILE_DIFF = "diff --git a/src/a.kt b/src/a.kt\n" +
            "--- a/src/a.kt\n+++ b/src/a.kt\n" +
            "@@ -1,60 +1,60 @@\n" + (1..60).joinToString("\n") { "+line-$it" } + "\n" +
            "diff --git a/src/b.kt b/src/b.kt\n" +
            "--- a/src/b.kt\n+++ b/src/b.kt\n" +
            "@@ -1,1 +1,2 @@\n base\n+extra\n"

        const val TWO_HUNK_DIFF = "diff --git a/src/app.kt b/src/app.kt\n" +
            "--- a/src/app.kt\n+++ b/src/app.kt\n" +
            "@@ -2,2 +2,2 @@\n two\n-three\n+THREE\n" +
            "@@ -10,2 +10,2 @@\n ten\n-eleven\n+ELEVEN\n"
    }
}
