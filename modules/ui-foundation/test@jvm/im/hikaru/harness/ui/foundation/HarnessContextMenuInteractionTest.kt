@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package im.hikaru.harness.ui.foundation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.io.File
import org.jetbrains.skia.Image
import org.jetbrains.skia.EncodedImageFormat

class HarnessContextMenuInteractionTest {
    @Test fun lightAndDarkMenuVisuals() {
        for (theme in listOf(HarnessThemeMode.Light, HarnessThemeMode.Dark)) {
            runSkikoComposeUiTest(size = Size(800f, 600f)) {
                val entries = listOf(
                    HarnessContextMenuEntry.Item("rename", "重命名", HarnessIconName.Rename, "Alt+Ctrl+R"),
                    HarnessContextMenuEntry.Item("pin", "固定", HarnessIconName.Pushpin, "Alt+Ctrl+P"),
                    HarnessContextMenuEntry.Item("unread", "标为未读", HarnessIconName.Eye, "Ctrl+Shift+U"),
                    HarnessContextMenuEntry.Item("section", "分组", HarnessIconName.Section, children = listOf(
                        HarnessContextMenuEntry.Item("section-new", "新建分组…", HarnessIconName.Folder))),
                    HarnessContextMenuEntry.Divider,
                    HarnessContextMenuEntry.Item("fork", "分叉", HarnessIconName.Branch, children = listOf(HarnessContextMenuEntry.Item("fork-chat", "分叉聊天"))),
                    HarnessContextMenuEntry.Divider,
                    HarnessContextMenuEntry.Item("copy", "复制", HarnessIconName.Copy, children = listOf(HarnessContextMenuEntry.Item("copy-id", "复制会话 ID"))),
                    HarnessContextMenuEntry.Divider,
                    HarnessContextMenuEntry.Item("open", "打开方式", HarnessIconName.ExternalLink, children = listOf(HarnessContextMenuEntry.Item("open-tab", "在新标签页中打开"))),
                    HarnessContextMenuEntry.Divider,
                    HarnessContextMenuEntry.Item("archive", "归档", HarnessIconName.Archive, "Ctrl+Shift+A"),
                    HarnessContextMenuEntry.Item("delete", "永久删除", HarnessIconName.Trash),
                )
                setContent { HarnessTheme(theme) {
                    HarnessContextMenuArea(entries) { Box(Modifier.size(40.dp).testTag("chat")) }
                } }
                onNodeWithTag("chat").performMouseInput { click(button = MouseButton.Secondary) }
                onNodeWithTag("context-menu-section").performMouseInput { moveTo(center) }
                onNodeWithTag("context-menu-section-new").assertIsDisplayed()
                val cards = onAllNodesWithTag("context-menu-card")
                assertEquals(264f, cards[0].fetchSemanticsNode().boundsInWindow.width)
                assertTrue(cards[1].fetchSemanticsNode().boundsInWindow.width < 264f)
                assertEquals(28f, onNodeWithTag("context-menu-rename").fetchSemanticsNode().boundsInWindow.height)
                for (index in 0..1) {
                    val bitmap = cards[index].captureToImage()
                    val directory = File("build/context-menu-previews").also { it.mkdirs() }
                    Image.makeFromBitmap(bitmap.asSkiaBitmap()).use { image ->
                        image.encodeToData(EncodedImageFormat.PNG)!!.use { data ->
                            File(directory, "${theme.name.lowercase()}-$index.png").writeBytes(data.bytes)
                        }
                    }
                }
            }
        }
    }
    @Test fun rightClickSubmenuDispatchAndDismissal() = runSkikoComposeUiTest(size = Size(800f, 600f)) {
        var leftClicks = 0
        val actions = mutableListOf<String>()
        val entries = listOf(
            HarnessContextMenuEntry.Item("rename", "重命名", shortcut = "Alt+Ctrl+R"),
            HarnessContextMenuEntry.Item("fork", "分叉", children = listOf(
                HarnessContextMenuEntry.Item("fork-chat", "分叉聊天"),
                HarnessContextMenuEntry.Item("fork-worktree", "在新工作树中分叉"),
            )),
            HarnessContextMenuEntry.Divider,
            HarnessContextMenuEntry.Item("disabled", "不可用", enabled = false),
        )
        setContent {
            HarnessTheme(HarnessThemeMode.Dark) {
                HarnessContextMenuArea(entries, onAction = actions::add) {
                    Box(Modifier.size(220.dp, 40.dp).testTag("chat").clickable { leftClicks++ })
                }
            }
        }
        onNodeWithTag("chat").performMouseInput { click() }
        assertEquals(1, leftClicks)
        onNodeWithTag("chat").performMouseInput { click(button = MouseButton.Secondary) }
        onNodeWithTag("context-menu-rename").assertIsDisplayed()
        assertEquals(1, leftClicks)
        onNodeWithTag("context-menu-disabled").assertIsNotEnabled()
        onNodeWithTag("context-menu-disabled").performMouseInput { click() }
        assertTrue(actions.isEmpty())
        onNodeWithTag("context-menu-fork").performMouseInput { moveTo(center) }
        onNodeWithTag("context-menu-fork-chat").assertIsDisplayed().performMouseInput { click() }
        onNodeWithTag("context-menu-rename").assertDoesNotExist()
        onNodeWithTag("context-menu-fork-chat").assertDoesNotExist()
        assertEquals(listOf("fork-chat"), actions)
        onNodeWithTag("chat").performMouseInput { click(button = MouseButton.Secondary) }
        onNodeWithTag("context-menu-card").performKeyInput { pressKey(Key.Escape) }
        onNodeWithTag("context-menu-rename").assertDoesNotExist()
        assertEquals(listOf("fork-chat"), actions)
        onNodeWithTag("chat").performMouseInput { click(button = MouseButton.Secondary) }
        onNodeWithTag("context-menu-fork").performMouseInput { moveTo(center) }
        onNodeWithTag("context-menu-fork-chat").assertIsDisplayed()
        onNodeWithTag("chat").performMouseInput { click(Offset(5f, 5f)) }
        onNodeWithTag("context-menu-rename").assertDoesNotExist()
    }

    @Test fun keyboardEntersAndLeavesSubmenu() = runSkikoComposeUiTest(size = Size(800f, 600f)) {
        val actions = mutableListOf<String>()
        setContent {
            HarnessTheme {
                HarnessContextMenuArea(listOf(HarnessContextMenuEntry.Item("parent", "更多", children = listOf(
                    HarnessContextMenuEntry.Item("leaf", "操作"),
                ))), onAction = actions::add) {
                    Box(Modifier.size(200.dp, 40.dp).testTag("chat"))
                }
            }
        }
        onNodeWithTag("chat").performMouseInput { click(button = MouseButton.Secondary) }
        onNodeWithTag("context-menu-card").performKeyInput { pressKey(Key.DirectionDown); pressKey(Key.DirectionRight) }
        onNodeWithTag("context-menu-leaf").assertIsDisplayed()
        onAllNodesWithTag("context-menu-card")[1].performKeyInput { pressKey(Key.DirectionLeft) }
        onNodeWithTag("context-menu-leaf").assertDoesNotExist()
        onNodeWithTag("context-menu-parent").performClick()
        onAllNodesWithTag("context-menu-card")[1].performKeyInput { pressKey(Key.DirectionDown); pressKey(Key.Enter) }
        assertEquals(listOf("leaf"), actions)
        onNodeWithTag("context-menu-parent").assertDoesNotExist()
    }

    @Test fun menusStayWithinWindowAtBottomRight() = runSkikoComposeUiTest(size = Size(600f, 400f)) {
        setContent {
            HarnessTheme {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
                    HarnessContextMenuArea(listOf(HarnessContextMenuEntry.Item("fork", "分叉", children = listOf(
                        HarnessContextMenuEntry.Item("leaf", "在新工作树中分叉"),
                    )))) { Box(Modifier.size(40.dp).testTag("chat")) }
                }
            }
        }
        onNodeWithTag("chat").performMouseInput { click(button = MouseButton.Secondary) }
        onNodeWithTag("context-menu-fork").performMouseInput { moveTo(center) }
        onNodeWithTag("context-menu-leaf").assertIsDisplayed()
        onAllNodesWithTag("context-menu-card").fetchSemanticsNodes().forEach { node ->
            val bounds = node.boundsInWindow
            assertTrue(bounds.left >= 0 && bounds.top >= 0 && bounds.right <= 600 && bounds.bottom <= 400, "$bounds")
        }
    }
}
