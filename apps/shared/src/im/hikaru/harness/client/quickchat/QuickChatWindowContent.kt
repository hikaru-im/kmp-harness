package im.hikaru.harness.client.quickchat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import im.hikaru.harness.client.app.LiveThreadSurface
import im.hikaru.harness.client.conversation.ConversationController
import im.hikaru.harness.ui.foundation.HarnessButton
import im.hikaru.harness.ui.foundation.HarnessButtonVariant
import im.hikaru.harness.ui.foundation.HarnessSurface
import im.hikaru.harness.ui.foundation.HarnessText
import im.hikaru.harness.ui.foundation.HarnessTextField
import im.hikaru.harness.ui.foundation.HarnessTokens

/**
 * Compact independent chat surface. It reuses the real ConversationController, so sending creates
 * or continues a real Host session; it never keeps a second fake transcript for the window.
 */
@Composable
public fun QuickChatWindowContent(
    controller: ConversationController,
    onClose: () -> Unit,
    available: Boolean = true,
    unavailableReason: String = "当前平台不支持快捷聊天窗口。",
) {
    val state by controller.state.collectAsState()
    val live by controller.live.collectAsState()
    Column(
        Modifier.fillMaxSize().padding(16.dp).semantics { testTag = "quick-chat-window" },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            HarnessText("快捷聊天", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            HarnessButton("关闭", onClose, variant = HarnessButtonVariant.Ghost,
                testTag = "quick-chat-close")
        }
        if (!available) {
            HarnessSurface(Modifier.fillMaxWidth().semantics { testTag = "quick-chat-unavailable" }) {
                HarnessText(unavailableReason, Modifier.padding(16.dp), color = HarnessTokens.Colors.TextMuted)
            }
        } else {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                LiveThreadSurface(
                    state = state,
                    live = live,
                    onRetry = { controller.refresh() },
                )
            }
            HarnessTextField(
                value = state.draft,
                onValueChange = controller::updateDraft,
                label = "消息",
                modifier = Modifier.fillMaxWidth(),
                enabled = state.connected && state.action == im.hikaru.harness.client.conversation.ConversationAction.Idle,
                singleLine = false,
                testTag = "quick-chat-input",
            )
            HarnessButton(
                text = if (state.action == im.hikaru.harness.client.conversation.ConversationAction.Sending) "发送中…" else "发送",
                onClick = { controller.submit() },
                modifier = Modifier.fillMaxWidth(),
                enabled = available && state.canSubmit && state.draft.isNotBlank(),
                testTag = "quick-chat-send",
            )
        }
    }
}
