package im.hikaru.harness.desktop

import androidx.compose.runtime.SideEffect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import im.hikaru.harness.client.git.*
import im.hikaru.harness.ui.foundation.HarnessTheme
import kotlinx.coroutines.CompletableDeferred
import kotlin.test.*

/** Production page/controller with a controlled adapter: file-level diff cards, not one raw blob. */
@OptIn(ExperimentalTestApi::class)
class GitDiffViewInteractionTest {
    @Test
    fun multiFileDiffShowsSummaryAndTogglesEachFileIndependently() {
        val f = GitWorktreesPageFixture()
        try {
            f.backend.patch = MULTI_FILE_DIFF
            f.git.refresh()
            runSkikoComposeUiTest(size = Size(1000f, 1200f)) {
                setContent { HarnessTheme { SettingsGitPage(f.git) } }
                onNodeWithTag("git-diff-summary").assertTextEquals("2 个文件变更 · +2 −1")
                onNodeWithTag("git-diff-leading").assertDoesNotExist()
                onAllNodesWithTag("git-diff-file").assertCountEquals(2)
                // Small diffs open by default, so both files render their hunk text.
                onAllNodesWithTag("git-diff-file-patch").assertCountEquals(2)
                // The coloured body still carries every patch character: header, markers and content.
                onAllNodesWithTag("git-diff-file-patch")[0].assertTextContains("@@ -1,2 +1,2 @@", substring = true)
                onAllNodesWithTag("git-diff-file-patch")[0].assertTextContains("+new", substring = true)
                // Each open file draws the unselectable line-number gutter beside its patch body.
                onAllNodesWithTag("git-diff-gutter").assertCountEquals(2)
                onAllNodesWithTag("git-diff-gutter")[0].assertTextContains("2", substring = true)
                // The clickable header merges its own title/detail text into one semantics node.
                onAllNodesWithTag("git-diff-file")[0].assertTextContains("src/old.kt → src/new.kt · 重命名", substring = true)
                onAllNodesWithTag("git-diff-file")[1].assertTextContains("src/app.kt · 修改", substring = true)
                onAllNodesWithTag("git-diff-file")[0].assertTextContains("+1 −1", substring = true)
                clickGitNode(scrollGitNodeIntoView(onAllNodesWithTag("git-diff-file")[0], "git-diff-vertical"))
                waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag("git-diff-file-patch").fetchSemanticsNodes().size == 1 }
                onAllNodesWithTag("git-diff-file")[0].assertTextContains("展开", substring = true)
                onAllNodesWithTag("git-diff-file")[1].assertTextContains("收起", substring = true)
                clickGitNode(scrollGitNodeIntoView(onAllNodesWithTag("git-diff-file")[0], "git-diff-vertical"))
                waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag("git-diff-file-patch").fetchSemanticsNodes().size == 2 }
                onAllNodesWithTag("git-diff-file")[0].assertTextContains("收起", substring = true)
            }
        } finally { f.close() }
    }

    @Test
    fun layoutSelectorSwitchesBetweenUnifiedSplitAndAuto() {
        val f = GitWorktreesPageFixture()
        try {
            f.backend.patch = MULTI_FILE_DIFF
            f.git.refresh()
            runSkikoComposeUiTest(size = Size(1000f, 1200f)) {
                setContent { HarnessTheme { SettingsGitPage(f.git) } }
                // Default stays unified, so the verified single-column text is unchanged.
                onNodeWithTag("git-diff-layout").assertExists()
                onAllNodesWithTag("git-diff-file-patch").assertCountEquals(2)
                onAllNodesWithTag("git-diff-split").assertCountEquals(0)
                clickGitNode(scrollGitNodeIntoView(onNodeWithTag("git-diff-layout-split")))
                waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag("git-diff-split").fetchSemanticsNodes().size == 2 }
                onAllNodesWithTag("git-diff-file-patch").assertCountEquals(0)
                // The rename file pairs old/new; the one-sided file keeps only its addition row.
                onAllNodesWithTag("git-diff-split-left")[0].assertTextEquals("old")
                onAllNodesWithTag("git-diff-split-right")[0].assertTextEquals("new")
                // Each pane keeps its own unselectable number gutter beside the text (row order:
                // rename change, rename context, one-sided context, one-sided addition).
                onAllNodesWithTag("git-diff-split-left-number").assertCountEquals(4)
                onAllNodesWithTag("git-diff-split-right-number").assertCountEquals(4)
                onAllNodesWithTag("git-diff-split-left-number")[0].assertTextEquals("1\u00A0\u00A0")
                onAllNodesWithTag("git-diff-split-right-number")[0].assertTextEquals("1\u00A0\u00A0")
                onAllNodesWithTag("git-diff-split-left-number")[1].assertTextEquals("2\u00A0\u00A0")
                // The addition has no old line, so its left cell stays a same-width blank buffer.
                onAllNodesWithTag("git-diff-split-left-number")[3].assertTextEquals("\u00A0\u00A0\u00A0")
                onAllNodesWithTag("git-diff-split-right-number")[3].assertTextEquals("2\u00A0\u00A0")
                clickGitNode(scrollGitNodeIntoView(onNodeWithTag("git-diff-layout-auto")))
                // Auto: the two-sided file splits, the one-sided file stays unified.
                waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag("git-diff-split").fetchSemanticsNodes().size == 1 }
                onAllNodesWithTag("git-diff-file-patch").assertCountEquals(1)
            }
        } finally { f.close() }
    }

    @Test
    fun largeDiffStartsCollapsedAndEmptyDiffKeepsItsOwnState() {
        val f = GitWorktreesPageFixture()
        try {
            f.backend.patch = "diff --git a/long.kt b/long.kt\n--- a/long.kt\n+++ b/long.kt\n@@ -1,500 +1,500 @@\n" +
                (1..500).joinToString("\n") { "+line-$it" }
            f.git.refresh()
            runSkikoComposeUiTest(size = Size(1000f, 1200f)) {
                setContent { HarnessTheme { SettingsGitPage(f.git) } }
                onNodeWithTag("git-diff-summary").assertTextEquals("1 个文件变更 · +500 −0")
                onAllNodesWithTag("git-diff-file-patch").assertCountEquals(0)
                onAllNodesWithTag("git-diff-file")[0].assertTextContains("展开", substring = true)
                runOnIdle { f.backend.patch = "" }
                onNodeWithTag("git-refresh").performScrollTo().performMouseInput { click() }
                waitUntil { (f.git.state.value.diff as? GitRead.Ready)?.value?.patch == "" }
                onNodeWithTag("git-diff-empty").assertExists()
                onNodeWithTag("git-diff-summary").assertDoesNotExist()
                onAllNodesWithTag("git-diff-file").assertCountEquals(0)
            }
        } finally { f.close() }
    }

    @Test
    fun diffOptionsMenuExpandsAndCollapsesEveryFileAtOnce() {
        val f = GitWorktreesPageFixture()
        try {
            f.backend.patch = MULTI_FILE_DIFF
            f.git.refresh()
            runSkikoComposeUiTest(size = Size(1000f, 1200f)) {
                setContent { HarnessTheme { SettingsGitPage(f.git) } }
                // Both small files open by default, so the menu offers the collapse action first.
                onAllNodesWithTag("git-diff-file-patch").assertCountEquals(2)
                clickGitNode(scrollGitNodeIntoView(onNodeWithTag("git-diff-options")))
                waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag("menu:expand").fetchSemanticsNodes().isNotEmpty() }
                onNodeWithTag("menu:expand").assertTextContains("折叠全部差异", substring = true)
                clickGitNode(onNodeWithTag("menu:expand"))
                waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag("git-diff-file-patch").fetchSemanticsNodes().isEmpty() }
                onAllNodesWithTag("git-diff-file")[0].assertTextContains("展开", substring = true)
                onAllNodesWithTag("git-diff-file")[1].assertTextContains("展开", substring = true)
                // The same item now expands every card, including both the file the user had closed and
                // the one that was already open.
                clickGitNode(scrollGitNodeIntoView(onNodeWithTag("git-diff-options")))
                waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag("menu:expand").fetchSemanticsNodes().isNotEmpty() }
                onNodeWithTag("menu:expand").assertTextContains("展开全部差异", substring = true)
                clickGitNode(onNodeWithTag("menu:expand"))
                waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag("git-diff-file-patch").fetchSemanticsNodes().size == 2 }
                onAllNodesWithTag("git-diff-file")[0].assertTextContains("收起", substring = true)
                onAllNodesWithTag("git-diff-file")[1].assertTextContains("收起", substring = true)
            }
        } finally { f.close() }
    }

    @Test
    fun diffOptionsMenuWrapsTheUnifiedBodyWithoutHorizontalScroll() {
        val f = GitWorktreesPageFixture()
        try {
            f.backend.patch = MULTI_FILE_DIFF
            f.git.refresh()
            runSkikoComposeUiTest(size = Size(1000f, 1200f)) {
                setContent { HarnessTheme { SettingsGitPage(f.git) } }
                // Default: one horizontally scrollable body per file, no per-line rows.
                onAllNodesWithTag("git-diff-file-patch").assertCountEquals(2)
                onAllNodesWithTag("git-diff-wrap").assertCountEquals(0)
                clickGitNode(scrollGitNodeIntoView(onNodeWithTag("git-diff-options")))
                waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag("menu:wrap").fetchSemanticsNodes().isNotEmpty() }
                clickGitNode(onNodeWithTag("menu:wrap"))
                waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag("git-diff-wrap").fetchSemanticsNodes().size == 2 }
                onAllNodesWithTag("git-diff-file-patch").assertCountEquals(0)
                // One row per parsed line (4 for the rename, 3 for the edit), each with its own
                // unselectable number cell, and every patch character still present.
                onAllNodesWithTag("git-diff-file-patch-line").assertCountEquals(7)
                onAllNodesWithTag("git-diff-file-patch-line")[0].assertTextEquals("@@ -1,2 +1,2 @@")
                onAllNodesWithTag("git-diff-file-patch-line")[2].assertTextEquals("+new")
                onAllNodesWithTag("git-diff-gutter").assertCountEquals(7)
                // The wrapped body wraps instead of scrolling, so it carries no horizontal scroll axis.
                assertNull(
                    onAllNodesWithTag("git-diff-wrap")[0].fetchSemanticsNode()
                        .config.getOrNull(SemanticsProperties.HorizontalScrollAxisRange),
                )
                // Off again restores the scrollable body; the menu item reports its pressed state.
                clickGitNode(scrollGitNodeIntoView(onNodeWithTag("git-diff-options")))
                waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag("menu:wrap").fetchSemanticsNodes().isNotEmpty() }
                onNodeWithTag("menu:wrap").assertIsSelected()
                clickGitNode(onNodeWithTag("menu:wrap"))
                waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag("git-diff-file-patch").fetchSemanticsNodes().size == 2 }
                onAllNodesWithTag("git-diff-wrap").assertCountEquals(0)
                onAllNodesWithTag("git-diff-gutter").assertCountEquals(2)
            }
        } finally { f.close() }
    }

    @Test
    fun diffOptionsMenuTogglesWordLevelEmphasisOnByDefault() {
        val f = GitWorktreesPageFixture()
        try {
            f.backend.patch = MULTI_FILE_DIFF
            f.git.refresh()
            runSkikoComposeUiTest(size = Size(1000f, 1200f)) {
                setContent { HarnessTheme { SettingsGitPage(f.git) } }
                // `wordDiffsEnabled.2` defaults to true, so the menu item starts pressed.
                clickGitNode(scrollGitNodeIntoView(onNodeWithTag("git-diff-options")))
                waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag("menu:word-diffs").fetchSemanticsNodes().isNotEmpty() }
                onNodeWithTag("menu:word-diffs").assertTextContains("词级差异", substring = true)
                onNodeWithTag("menu:word-diffs").assertIsSelected()
                // The emphasis never changes the selectable text, only the tint over the changed words.
                onAllNodesWithTag("git-diff-file-patch")[0].assertTextContains("+new", substring = true)
                clickGitNode(onNodeWithTag("menu:word-diffs"))
                clickGitNode(scrollGitNodeIntoView(onNodeWithTag("git-diff-options")))
                waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag("menu:word-diffs").fetchSemanticsNodes().isNotEmpty() }
                onNodeWithTag("menu:word-diffs").assertIsNotSelected()
                clickGitNode(onNodeWithTag("menu:word-diffs"))
                clickGitNode(scrollGitNodeIntoView(onNodeWithTag("git-diff-options")))
                waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag("menu:word-diffs").fetchSemanticsNodes().isNotEmpty() }
                onNodeWithTag("menu:word-diffs").assertIsSelected()
            }
        } finally { f.close() }
    }

    @Test
    fun diffOptionsMenuHidesTopLevelImportsAndKeepsOrdinaryChanges() {
        val f = GitWorktreesPageFixture()
        try {
            f.backend.patch = IMPORT_DIFF
            f.git.refresh()
            runSkikoComposeUiTest(size = Size(1000f, 1200f)) {
                setContent { HarnessTheme { SettingsGitPage(f.git) } }
                // `hideDiffImports` defaults to false, so the import change is visible first.
                onAllNodesWithTag("git-diff-file-patch")[0].assertTextContains("-import b", substring = true)
                onAllNodesWithTag("git-diff-file-patch")[0].assertTextContains("+import c", substring = true)
                fun openImportItem() {
                    clickGitNode(scrollGitNodeIntoView(onNodeWithTag("git-diff-options")))
                    waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag("menu:hide-imports").fetchSemanticsNodes().isNotEmpty() }
                }
                openImportItem()
                onNodeWithTag("menu:hide-imports").assertTextContains("隐藏导入内容", substring = true)
                onNodeWithTag("menu:hide-imports").assertIsNotSelected()
                clickGitNode(onNodeWithTag("menu:hide-imports"))
                waitUntil(timeoutMillis = 5_000) {
                    onAllNodesWithTag("git-diff-file-patch")[0]
                        .fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)
                        .orEmpty().none { it.text == IMPORT_DIFF_VISIBLE } &&
                        onAllNodesWithTag("git-diff-file-patch")[0]
                            .fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)
                            .orEmpty().any { it.text == IMPORT_DIFF_HIDDEN }
                }
                // Only the top-level import change is gone: the shared context and the real edit stay,
                // and the selectable body is exactly the surviving lines (no blank placeholders).
                onAllNodesWithTag("git-diff-file-patch")[0].assertTextEquals(IMPORT_DIFF_HIDDEN)
                onAllNodesWithTag("git-diff-file-patch")[0].assertTextContains(" value = 1", substring = true)
                onAllNodesWithTag("git-diff-file-patch")[0].assertTextContains("+value = 2", substring = true)
                // The gutter drops the same two lines, so it stays line-for-line with the body.
                onAllNodesWithTag("git-diff-gutter")[0].assertTextEquals(IMPORT_DIFF_HIDDEN_GUTTER)
                // Turning it off restores the untouched patch text and the pressed state clears.
                openImportItem()
                onNodeWithTag("menu:hide-imports").assertIsSelected()
                clickGitNode(onNodeWithTag("menu:hide-imports"))
                waitUntil(timeoutMillis = 5_000) {
                    onAllNodesWithTag("git-diff-file-patch")[0]
                        .fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)
                        .orEmpty().any { it.text == IMPORT_DIFF_VISIBLE }
                }
                onAllNodesWithTag("git-diff-file-patch")[0].assertTextEquals(IMPORT_DIFF_VISIBLE)
                onAllNodesWithTag("git-diff-gutter")[0].assertTextEquals(IMPORT_DIFF_VISIBLE_GUTTER)
            }
        } finally { f.close() }
    }

    @Test
    fun diffOptionsMenuLoadFullFilesRereadsThePatchWithFullContext() {
        val f = GitWorktreesPageFixture()
        try {
            f.backend.patch = PARTIAL_DIFF
            f.backend.fullPatch = FULL_FILE_DIFF
            f.git.refresh()
            runSkikoComposeUiTest(size = Size(1000f, 1200f)) {
                setContent { HarnessTheme { SettingsGitPage(f.git) } }
                fun patchText(): String = onAllNodesWithTag("git-diff-file-patch")[0]
                    .fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)
                    .orEmpty().joinToString("\n") { it.text }
                fun openItem() {
                    clickGitNode(scrollGitNodeIntoView(onNodeWithTag("git-diff-options")))
                    waitUntil(timeoutMillis = 5_000) {
                        onAllNodesWithTag("menu:load-full-files").fetchSemanticsNodes().isNotEmpty()
                    }
                }
                // The default read is partial: git's hunks stop before the untouched tail of the file.
                assertFalse(f.backend.diffReads.last().second.loadFullFiles)
                onAllNodesWithTag("git-diff-file-patch")[0].assertTextContains("+added", substring = true)
                assertFalse(patchText().contains(UNTOUCHED_TAIL_LINE))
                openItem()
                onNodeWithTag("menu:load-full-files").assertTextContains("加载完整文件", substring = true)
                onNodeWithTag("menu:load-full-files").assertIsNotSelected()
                clickGitNode(onNodeWithTag("menu:load-full-files"))
                waitUntil(timeoutMillis = 5_000) { f.backend.diffReads.last().second.loadFullFiles }
                // The toggle is a backend input: the patch is re-read, and the whole file comes back.
                waitUntil(timeoutMillis = 5_000) { patchText().contains(UNTOUCHED_TAIL_LINE) }
                openItem()
                onNodeWithTag("menu:load-full-files").assertIsSelected()
                clickGitNode(onNodeWithTag("menu:load-full-files"))
                waitUntil(timeoutMillis = 5_000) { !f.backend.diffReads.last().second.loadFullFiles }
                waitUntil(timeoutMillis = 5_000) { !patchText().contains(UNTOUCHED_TAIL_LINE) }
            }
        } finally { f.close() }
    }

    @Test
    fun diffOptionsMenuRefreshRereadsAndDisablesWhileReadIsInProgress() {
        val f = GitWorktreesPageFixture()
        val gate = CompletableDeferred<Unit>()
        try {
            f.backend.patch = MULTI_FILE_DIFF
            f.git.refresh()
            val initialDiffReads = f.backend.diffReads.size
            f.backend.diffGate = gate
            runSkikoComposeUiTest(size = Size(1000f, 1200f)) {
                setContent { HarnessTheme { SettingsGitPage(f.git) } }
                fun openRefreshItem() {
                    clickGitNode(scrollGitNodeIntoView(onNodeWithTag("git-diff-options")))
                    waitUntil(timeoutMillis = 5_000) {
                        onAllNodesWithTag("menu:refresh").fetchSemanticsNodes().isNotEmpty()
                    }
                }
                openRefreshItem()
                onNodeWithTag("menu:refresh").assertTextContains("刷新", substring = true).assertIsEnabled()
                clickGitNode(onNodeWithTag("menu:refresh"))
                waitUntil(timeoutMillis = 5_000) { f.backend.diffReads.size == initialDiffReads + 1 }

                // The previous completed patch remains visible while GitState reports Loading.
                openRefreshItem()
                onNodeWithTag("menu:refresh").assertIsNotEnabled().performMouseInput { click() }
                assertEquals(initialDiffReads + 1, f.backend.diffReads.size)

                runOnIdle {
                    f.backend.patch = REFRESHED_DIFF
                    gate.complete(Unit)
                }
                waitUntil(timeoutMillis = 5_000) {
                    onNodeWithTag("git-diff-summary").fetchSemanticsNode().config
                        .getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == "1 个文件变更 · +1 −0" }
                }
            }
        } finally {
            if (!gate.isCompleted) gate.complete(Unit)
            f.close()
        }
    }

    @Test
    fun diffOptionsMenuCopiesGitApplyCommandAndReportsUnavailablePatch() {
        val f = GitWorktreesPageFixture()
        try {
            f.backend.patch = MULTI_FILE_DIFF
            f.git.refresh()
            runSkikoComposeUiTest(size = Size(1000f, 1200f)) {
                var clipboardText: (() -> String?)? = null
                setContent {
                    val clipboard = LocalClipboardManager.current
                    SideEffect { clipboardText = { clipboard.getText()?.text } }
                    HarnessTheme { SettingsGitPage(f.git) }
                }

                fun openOptions() {
                    clickGitNode(scrollGitNodeIntoView(onNodeWithTag("git-diff-options")))
                    waitUntil(timeoutMillis = 5_000) {
                        onAllNodesWithTag("menu:copy-git-apply").fetchSemanticsNodes().isNotEmpty()
                    }
                }

                openOptions()
                onNodeWithTag("menu:copy-git-apply")
                    .assertTextContains("复制 git apply 命令", substring = true)
                    .assertIsEnabled()
                clickGitNode(onNodeWithTag("menu:copy-git-apply"))
                waitUntil(timeoutMillis = 5_000) { clipboardText?.invoke() == gitApplyCommand(MULTI_FILE_DIFF) }
                assertEquals(gitApplyCommand(MULTI_FILE_DIFF), clipboardText?.invoke())
                onNodeWithTag("git-diff-copy-git-apply-feedback")
                    .assertTextEquals("已将 git apply 命令复制到剪贴板")

                val unavailablePatch = "new file mode 100644\nindex 0000000..1111111\n"
                runOnIdle { f.backend.patch = unavailablePatch }
                onNodeWithTag("git-refresh").performScrollTo().performMouseInput { click() }
                waitUntil(timeoutMillis = 5_000) {
                    (f.git.state.value.diff as? GitRead.Ready)?.value?.patch == unavailablePatch
                }
                // A patch that cannot produce a command shows no warning on load; the item stays
                // enabled and the failure message is the result of the click.
                onAllNodesWithTag("git-diff-copy-git-apply-feedback").assertCountEquals(0)
                openOptions()
                onNodeWithTag("menu:copy-git-apply")
                    .assertTextContains("复制 git apply 命令", substring = true)
                    .assertIsEnabled()
                clickGitNode(onNodeWithTag("menu:copy-git-apply"))
                waitUntil(timeoutMillis = 5_000) {
                    onAllNodesWithTag("git-diff-copy-git-apply-feedback").fetchSemanticsNodes().isNotEmpty()
                }
                onNodeWithTag("git-diff-copy-git-apply-feedback")
                    .assertTextEquals("未能生成 git apply 命令")
            }
        } finally { f.close() }
    }

    private companion object {
        const val MULTI_FILE_DIFF = "diff --git a/src/old.kt b/src/new.kt\n" +
            "similarity index 90%\n" +
            "rename from src/old.kt\n" +
            "rename to src/new.kt\n" +
            "--- a/src/old.kt\n" +
            "+++ b/src/new.kt\n" +
            "@@ -1,2 +1,2 @@\n" +
            "-old\n" +
            "+new\n" +
            " same\n" +
            "diff --git a/src/app.kt b/src/app.kt\n" +
            "--- a/src/app.kt\n" +
            "+++ b/src/app.kt\n" +
            "@@ -1,1 +1,2 @@\n" +
            " base\n" +
            "+extra\n"

        /** One hunk: a shared import, a replaced import, a shared statement and a real edit. */
        const val IMPORT_DIFF = "diff --git a/src/app.kt b/src/app.kt\n" +
            "--- a/src/app.kt\n" +
            "+++ b/src/app.kt\n" +
            "@@ -1,3 +1,4 @@\n" +
            " import a\n" +
            "-import b\n" +
            "+import c\n" +
            " value = 1\n" +
            "+value = 2\n"
        /** The selectable body with every line visible (markers kept), as rendered by the unified view. */
        const val IMPORT_DIFF_VISIBLE = "@@ -1,3 +1,4 @@\n import a\n-import b\n+import c\n value = 1\n+value = 2"
        /** The same body after `隐藏导入内容`: the two import change lines are omitted entirely. */
        const val IMPORT_DIFF_HIDDEN = "@@ -1,3 +1,4 @@\n import a\n value = 1\n+value = 2"
        /**
         * The unselectable gutter for each state — one right-aligned number per surviving line, the
         * `@@`/leading rows buffered to the widest number, padded with the same NBSP gap as the body.
         */
        const val IMPORT_DIFF_VISIBLE_GUTTER = "\u00A0\u00A0\u00A0\n1\u00A0\u00A0\n2\u00A0\u00A0\n2\u00A0\u00A0\n3\u00A0\u00A0\n4\u00A0\u00A0"
        const val IMPORT_DIFF_HIDDEN_GUTTER = "\u00A0\u00A0\u00A0\n1\u00A0\u00A0\n3\u00A0\u00A0\n4\u00A0\u00A0"

        /** A partial diff: git's hunks stop before the untouched rest of the file. */
        const val PARTIAL_DIFF = "diff --git a/src/app.kt b/src/app.kt\n" +
            "--- a/src/app.kt\n" +
            "+++ b/src/app.kt\n" +
            "@@ -1,2 +1,3 @@\n" +
            " head\n" +
            "+added\n" +
            " tail\n"
        /** What the host returns with `loadFullFiles`: the same change with the whole file as context. */
        const val FULL_FILE_DIFF = "diff --git a/src/app.kt b/src/app.kt\n" +
            "--- a/src/app.kt\n" +
            "+++ b/src/app.kt\n" +
            "@@ -1,5 +1,6 @@\n" +
            " head\n" +
            "+added\n" +
            " tail\n" +
            " untouched-region-line-1\n" +
            " untouched-region-line-2\n" +
            " untouched-region-line-3\n"
        /** A line that only the full-context patch carries, so its presence proves the re-read. */
        const val UNTOUCHED_TAIL_LINE = "untouched-region-line-3"
        const val REFRESHED_DIFF = "diff --git a/src/refreshed.kt b/src/refreshed.kt\n" +
            "--- a/src/refreshed.kt\n" +
            "+++ b/src/refreshed.kt\n" +
            "@@ -1,0 +1,1 @@\n" +
            "+refreshed\n"
    }
}
