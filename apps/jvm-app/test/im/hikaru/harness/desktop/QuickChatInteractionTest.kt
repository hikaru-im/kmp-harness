package im.hikaru.harness.desktop

import androidx.compose.ui.test.*
import im.hikaru.harness.client.conversation.ConversationController
import im.hikaru.harness.client.page.NewTabPage
import im.hikaru.harness.client.page.NewTabTool
import im.hikaru.harness.client.page.SettingsShortcutsPage
import im.hikaru.harness.client.settings.GlobalHotkeyController
import im.hikaru.harness.client.settings.GlobalHotkeyStatus
import im.hikaru.harness.client.settings.ShortcutPlatform
import im.hikaru.harness.client.settings.ShortcutsRepository
import im.hikaru.harness.desktop.window.DesktopGlobalHotkeyRegistrar
import im.hikaru.harness.client.quickchat.QuickChatWindowContent
import im.hikaru.harness.ui.foundation.HarnessTheme
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class QuickChatInteractionTest {
    @Test
    fun desktopNewTabEnablesSideChatAndRoutesToIndependentWindowRequest() = runComposeUiTest {
        var opened = false
        setContent {
            HarnessTheme {
                NewTabPage(
                    onOpenTool = { if (it == NewTabTool.SideChat) opened = true },
                    sideChatAvailable = true,
                )
            }
        }
        onNodeWithTag("new-tab-tool-sidechat").assertIsEnabled()
        onNodeWithText("侧边聊天").performClick()
        runOnIdle { assertTrue(opened) }
    }

    @Test
    fun globalHotkeyUnsupportedStateIsVisibleInShortcutSettings() = runComposeUiTest {
        val repository = ShortcutsRepository(platform = ShortcutPlatform.Mac)
        val controller = GlobalHotkeyController(DesktopGlobalHotkeyRegistrar())
        controller.sync(repository.state.value.settings, ShortcutPlatform.Mac)
        assertTrue(controller.state.value.status == GlobalHotkeyStatus.Unsupported)
        setContent {
            HarnessTheme {
                SettingsShortcutsPage(
                    repository = repository,
                    globalHotkeyState = controller.state.value,
                )
            }
        }
        onNodeWithTag("shortcuts-global-hotkey-status").assertExists()
        onNodeWithText("系统级快捷键：不可用").assertExists()
        onNodeWithText("当前平台未接入系统级热键。").assertExists()
        controller.close()
    }

    @Test
    fun unsupportedQuickChatKeepsExplicitUnavailableState() = runComposeUiTest {
        val controller = ConversationController(CoroutineScope(SupervisorJob() + EmptyCoroutineContext))
        var closed = false
        setContent {
            HarnessTheme {
                QuickChatWindowContent(
                    controller = controller,
                    onClose = { closed = true },
                    available = false,
                    unavailableReason = "当前平台不支持快捷聊天窗口。",
                )
            }
        }
        onNodeWithTag("quick-chat-unavailable").assertExists()
        onNodeWithTag("quick-chat-close").assertIsEnabled().performClick()
        runOnIdle { assertTrue(closed) }
        controller.close()
    }
}
