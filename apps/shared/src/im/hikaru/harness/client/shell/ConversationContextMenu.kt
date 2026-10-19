package im.hikaru.harness.client.shell

import androidx.compose.runtime.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import im.hikaru.harness.client.connection.SessionSummary
import im.hikaru.harness.ui.foundation.*
import im.hikaru.harness.ui.foundation.HarnessContextMenuEntry.Item
import im.hikaru.harness.client.conversation.ConversationCommand
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.launch

internal data class ConversationNavigationActions(
    val sessions: Map<String, SessionSummary> = emptyMap(),
    val busy: Boolean = false,
    val dispatch: (String, ConversationCommand) -> Deferred<Boolean>? = { _, _ -> null },
    /** Bulk activity-feed actions receive the ids currently shown; null disables the menu entry. */
    val markAllRead: ((List<String>) -> Unit)? = null,
    val archiveAll: ((List<String>) -> Unit)? = null,
    val owner: Any? = null,
)
internal val LocalConversationNavigationActions = staticCompositionLocalOf { ConversationNavigationActions() }

/** Unsupported actions remain visible and disabled; labels are not global shortcuts. */
internal val conversationContextMenuEntries: List<HarnessContextMenuEntry> = listOf(
    Item("rename", "重命名", HarnessIconName.Rename, "Alt+Ctrl+R", enabled = false),
    Item("pin", "固定", HarnessIconName.Pushpin, "Alt+Ctrl+P", enabled = false),
    Item("unread", "标为未读", HarnessIconName.Eye, "Ctrl+Shift+U", enabled = false),
    Item("section", "分组", HarnessIconName.Section, enabled = false, children = listOf(
        Item("section-new", "新建分组…", HarnessIconName.Folder, enabled = false))),
    HarnessContextMenuEntry.Divider,
    Item("fork", "分叉", HarnessIconName.Branch, enabled = false, children = listOf(
        Item("fork-chat", "分叉聊天", HarnessIconName.Host, enabled = false),
        Item("fork-worktree", "在新工作树中分叉", HarnessIconName.ExternalLink, enabled = false))),
    HarnessContextMenuEntry.Divider,
    Item("copy", "复制", HarnessIconName.Copy, children = listOf(
        Item("copy-id", "复制会话 ID", HarnessIconName.Copy),
        Item("copy-link", "复制会话链接", HarnessIconName.ExternalLink, enabled = false))),
    HarnessContextMenuEntry.Divider,
    Item("open", "打开方式", HarnessIconName.ExternalLink, enabled = false, children = listOf(
        Item("open-tab", "在新标签页中打开", HarnessIconName.Plus, enabled = false),
        Item("open-window", "在新窗口中打开", HarnessIconName.ExternalLink, enabled = false))),
    HarnessContextMenuEntry.Divider,
    Item("archive", "归档", HarnessIconName.Archive, "Ctrl+Shift+A", enabled = false),
    Item("delete", "删除会话", HarnessIconName.Trash, enabled = false, destructive = true),
)

@Composable
internal fun ConversationContextMenu(id: String, content: @Composable () -> Unit) {
    val actions = LocalConversationNavigationActions.current
    val session = actions.sessions[id]
    val clipboard = LocalClipboardManager.current
    var renaming by remember(id, actions.owner) { mutableStateOf(false) }
    var title by remember(id, actions.owner) { mutableStateOf("") }
    var saving by remember(id, actions.owner) { mutableStateOf(false) }
    var renameFailed by remember(id, actions.owner) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val entries = conversationContextMenuEntries.map { entry ->
        if (entry !is Item) entry else when (entry.id) {
            "rename", "unread" -> entry.copy(enabled = session?.managementAvailable == true && !actions.busy)
            "pin" -> entry.copy(label = if (session?.pinned == true) "取消固定" else "固定",
                enabled = session?.managementAvailable == true && !actions.busy)
            "delete" -> entry.copy(enabled = session?.managementAvailable == true && !actions.busy)
            "archive" -> entry.copy(label = if (session?.archived == true) "取消归档" else "归档",
                enabled = session?.archiveAvailable == true && !actions.busy)
            else -> entry
        }
    }
    key(actions.owner) { HarnessContextMenuArea(entries, onAction = { action ->
        when (action) {
            "copy-id" -> clipboard.setText(AnnotatedString(id))
            "rename" -> { title = session?.title.orEmpty(); renameFailed = false; renaming = true }
            "pin" -> actions.dispatch(id, ConversationCommand.TogglePin)
            "unread" -> actions.dispatch(id, ConversationCommand.MarkUnread)
            "archive" -> actions.dispatch(id, ConversationCommand.ToggleArchive)
            "delete" -> actions.dispatch(id, ConversationCommand.Delete)
        }
    }, content = content) }
    if (renaming) AlertDialog(onDismissRequest = { if (!saving) renaming = false }, title = { Text("重命名会话") },
        text = { OutlinedTextField(value = title, onValueChange = { title = it.take(200); renameFailed = false },
            enabled = !saving, singleLine = true, isError = renameFailed,
            supportingText = if (renameFailed) ({ Text("保存未成功，内容已保留，可重试。") }) else null) },
        confirmButton = { TextButton(onClick = {
            saving = true
            val command = actions.dispatch(id, ConversationCommand.Rename(title))
            scope.launch {
                try { if (command?.await() == true) renaming = false else renameFailed = true }
                finally { saving = false }
            }
        }, enabled = title.isNotBlank() && !actions.busy && !saving) { Text(if (saving) "保存中…" else "保存") } },
        dismissButton = { TextButton(onClick = { renaming = false }, enabled = !saving) { Text("取消") } })
}
