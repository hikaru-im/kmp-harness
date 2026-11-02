@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package im.hikaru.harness.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.harness.client.app.LiveThreadSurface
import im.hikaru.harness.client.connection.FileChangeContent
import im.hikaru.harness.client.connection.FileChangeKind
import im.hikaru.harness.client.connection.Message
import im.hikaru.harness.client.connection.MessageRole
import im.hikaru.harness.client.connection.ModelMessageSource
import im.hikaru.harness.client.connection.SessionId
import im.hikaru.harness.client.connection.TextContent
import im.hikaru.harness.client.conversation.ConversationDataPhase
import im.hikaru.harness.client.conversation.ConversationHostScope
import im.hikaru.harness.client.conversation.ConversationRunState
import im.hikaru.harness.client.conversation.ConversationState
import im.hikaru.harness.ui.foundation.HarnessTheme
import im.hikaru.harness.ui.foundation.HarnessThemeMode
import im.hikaru.harness.ui.foundation.HarnessTokens
import kotlin.test.*

/** A turn's changed files collapse into one card; expanding one file never hides its patch. */
class FileChangeCardInteractionTest {
    @Test
    fun fourKindsRenderTheirLabelsPathsCountsAndTotals() = runSkikoComposeUiTest(size = Size(600f, 800f)) {
        mount(
            history = listOf(message(
                change("src/added.kt", FileChangeKind.ADDED, added = 12, removed = 0, patch = "+brand new"),
                change("src/modified.kt", FileChangeKind.MODIFIED, added = 2, removed = 3, patch = "-old\n+new"),
                change("src/deleted.kt", FileChangeKind.DELETED, added = 0, removed = 9, patch = "-gone"),
                change("src/renamed.kt", FileChangeKind.RENAMED, previousPath = "src/old.kt", truncated = true),
            )),
        )
        onNodeWithTag("$CARD:header").assertTextContains("4 个文件已更改")
        onNodeWithText("+12", useUnmergedTree = true).assertExists()
        onNodeWithText(" −9", useUnmergedTree = true).assertExists()
        onNodeWithTag("$CARD:file:src/added.kt").assertTextContains("已创建")
            .assertTextContains("src/added.kt").assertTextContains("+12").assertTextContains(" −0")
        onNodeWithTag("$CARD:file:src/modified.kt").assertTextContains("已编辑")
            .assertTextContains("+2").assertTextContains(" −3")
        onNodeWithTag("$CARD:file:src/deleted.kt").assertTextContains("已删除")
            .assertTextContains("+0").assertTextContains(" −9")
        onNodeWithTag("$CARD:file:src/renamed.kt").assertTextContains("已重命名")
            .assertTextContains("src/old.kt → src/renamed.kt")
        // Collapsed by default: no body is printed until the row is opened.
        onNodeWithTag("$CARD:body:src/added.kt", true).assertDoesNotExist()
        onNodeWithTag("$CARD:body:src/deleted.kt", true).assertDoesNotExist()
    }

    @Test
    fun clickingARowExpandsItsPatchAndClickingAgainFolds() = runSkikoComposeUiTest(size = Size(600f, 800f)) {
        mount(
            history = listOf(message(
                change("src/modified.kt", FileChangeKind.MODIFIED, added = 2, removed = 3, patch = "-old line\n+new line"),
            )),
        )
        val row = "$CARD:file:src/modified.kt"
        onNodeWithTag(row, true).performClick()
        onNodeWithTag("$CARD:body:src/modified.kt", true).assertExists()
        onNodeWithText("-old line", useUnmergedTree = true).assertExists()
        onNodeWithText("+new line", useUnmergedTree = true).assertExists()
        onNodeWithTag(row, true).performScrollTo().performClick()
        onNodeWithTag("$CARD:body:src/modified.kt", true).assertDoesNotExist()
        onNodeWithText("-old line", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun expandingOneFileKeepsTheOtherFilesFolded() = runSkikoComposeUiTest(size = Size(600f, 800f)) {
        mount(
            history = listOf(message(
                change("a.kt", FileChangeKind.MODIFIED, added = 1, removed = 0, patch = "+a"),
                change("b.kt", FileChangeKind.MODIFIED, added = 0, removed = 1, patch = "-b"),
            )),
        )
        onNodeWithTag("$CARD:file:a.kt", true).performClick()
        onNodeWithTag("$CARD:body:a.kt", true).assertExists()
        onNodeWithTag("$CARD:body:b.kt", true).assertDoesNotExist()
        onNodeWithTag("$CARD:file:b.kt", true).performClick()
        onNodeWithTag("$CARD:body:a.kt", true).assertExists()
        onNodeWithTag("$CARD:body:b.kt", true).assertExists()
    }

    @Test
    fun aProducerTruncatedFileShowsTheNoticeAndNoPatch() = runSkikoComposeUiTest(size = Size(600f, 800f)) {
        mount(
            history = listOf(message(
                change("assets/large.bin", FileChangeKind.MODIFIED, added = 0, removed = 0, truncated = true),
            )),
        )
        onNodeWithTag("$CARD:file:assets/large.bin", true).performClick()
        onNodeWithTag("$CARD:truncated:assets/large.bin", true).assertExists()
        onNodeWithText("内容过大，无法内联渲染", useUnmergedTree = true).assertExists()
        onNodeWithTag("$CARD:patch:assets/large.bin", true).assertDoesNotExist()
        onNodeWithTag("$CARD:open:assets/large.bin", true).assertDoesNotExist()
    }

    @Test
    fun aDeletedFileNeverOffersTheGitJumpWhileTheOthersDo() = runSkikoComposeUiTest(size = Size(600f, 800f)) {
        val opened = mutableListOf<String>()
        mount(
            history = listOf(message(
                change("src/modified.kt", FileChangeKind.MODIFIED, added = 1, removed = 1, patch = "-a\n+b"),
                change("src/deleted.kt", FileChangeKind.DELETED, added = 0, removed = 4, patch = "-gone"),
            )),
            onOpenInGit = { path -> opened += path },
        )
        onNodeWithTag("$CARD:file:src/modified.kt", true).performClick()
        onNodeWithTag("$CARD:open:src/modified.kt", true).assertExists().performClick()
        onNodeWithTag("$CARD:file:src/deleted.kt", true).performScrollTo().performClick()
        onNodeWithTag("$CARD:body:src/deleted.kt", true).assertExists()
        onNodeWithTag("$CARD:open:src/deleted.kt", true).assertDoesNotExist()
        assertEquals(listOf("src/modified.kt"), opened)
    }

    @Test
    fun aFileWithoutPatchOrGitActionCannotBeExpanded() = runSkikoComposeUiTest(size = Size(600f, 800f)) {
        mount(
            history = listOf(message(
                change("src/quiet.kt", FileChangeKind.MODIFIED, added = 1, removed = 1),
            )),
        )
        onNodeWithTag("$CARD:file:src/quiet.kt", true).performClick()
        onNodeWithTag("$CARD:body:src/quiet.kt", true).assertDoesNotExist()
    }

    @Test
    fun aVeryLongPatchIsCutAtTheDisplayLimit() = runSkikoComposeUiTest(size = Size(600f, 800f)) {
        val patch = (1..405).joinToString("\n") { "+line-$it" }
        mount(
            history = listOf(message(
                change("src/huge.kt", FileChangeKind.MODIFIED, added = 405, removed = 0, patch = patch),
            )),
        )
        onNodeWithTag("$CARD:file:src/huge.kt", true).performClick()
        onNodeWithText("+line-400", useUnmergedTree = true).assertExists()
        onNodeWithText("+line-401", useUnmergedTree = true).assertDoesNotExist()
        onNodeWithTag("$CARD:patch-truncated:src/huge.kt", true).assertExists()
        onNodeWithText("补丁过长，仅显示前 400 行", useUnmergedTree = true).assertExists()
    }

    private fun ComposeUiTest.mount(history: List<Message>, onOpenInGit: ((String) -> Unit)? = null) {
        val state = ConversationState(
            host = HOST, connected = true,
            listPhase = ConversationDataPhase.Ready, historyPhase = ConversationDataPhase.Ready,
            selectedSessionId = SESSION, history = history, runState = ConversationRunState.Idle,
        )
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                HarnessTheme(HarnessThemeMode.Light) {
                    Box(Modifier.fillMaxSize().background(HarnessTokens.Colors.Canvas)
                        .semantics { testTag = "file-change-fixture" }) {
                        LiveThreadSurface(state, onRetry = { fail("Unexpected history retry") }, onOpenInGit = onOpenInGit)
                    }
                }
            }
        }
        mainClock.advanceTimeByFrame()
        waitForIdle()
    }

    /** The leading text keeps the card's run keyed at content index 1, the way a turn's prose precedes it. */
    private fun message(vararg files: FileChangeContent) = Message(
        "turn-1", MessageRole.ASSISTANT, listOf(TextContent("完成")) + files, MODEL,
    )

    private fun change(
        path: String,
        kind: FileChangeKind,
        added: Int = 0,
        removed: Int = 0,
        patch: String? = null,
        truncated: Boolean = false,
        previousPath: String? = null,
    ) = FileChangeContent(path, kind, added, removed, patch, truncated, previousPath)

    private companion object {
        const val CARD = "message:turn-1:content:1"
        val SESSION = SessionId("file-change-session")
        val HOST = ConversationHostScope(HostId("file-change-host"), 1, null)
        val MODEL = ModelMessageSource("fixture-provider", "fixture-model")
    }
}
