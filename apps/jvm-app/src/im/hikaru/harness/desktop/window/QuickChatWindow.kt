package im.hikaru.harness.desktop.window

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import im.hikaru.harness.client.conversation.ConversationController
import im.hikaru.harness.client.quickchat.QuickChatWindowContent
import im.hikaru.harness.ui.foundation.HarnessTheme
import im.hikaru.harness.ui.foundation.HarnessThemeCustomization
import im.hikaru.harness.ui.foundation.HarnessThemeMode

/** Desktop-owned independent quick-chat window; mobile/shared hosts keep the capability unavailable. */
@Composable
public fun QuickChatWindow(
    controller: ConversationController,
    themeMode: HarnessThemeMode,
    customization: HarnessThemeCustomization,
    onCloseRequest: () -> Unit,
) {
    val state = rememberWindowState(size = DpSize(440.dp, 640.dp))
    Window(
        onCloseRequest = onCloseRequest,
        title = "快捷聊天",
        state = state,
        resizable = true,
    ) {
        HarnessTheme(themeMode = themeMode, customization = customization) {
            QuickChatWindowContent(controller = controller, onClose = onCloseRequest)
        }
    }
}
