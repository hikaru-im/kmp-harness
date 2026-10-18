package im.hikaru.harness.client.shell

import androidx.compose.runtime.Composable
import im.hikaru.harness.ui.foundation.HarnessContextMenuArea
import im.hikaru.harness.ui.foundation.HarnessIconName
import im.hikaru.harness.ui.foundation.HarnessContextMenuEntry
import im.hikaru.harness.ui.foundation.HarnessContextMenuEntry.Item

/** Presentation presets only. No business operations or global shortcuts are registered. */
internal val conversationContextMenuEntries: List<HarnessContextMenuEntry> = listOf(
    Item("rename", "重命名", HarnessIconName.Rename, "Alt+Ctrl+R"),
    Item("pin", "固定", HarnessIconName.Pushpin, "Alt+Ctrl+P"),
    Item("unread", "标为未读", HarnessIconName.Eye, "Ctrl+Shift+U"),
    Item("section", "分组", HarnessIconName.Section, children = listOf(
        Item("section-new", "新建分组…", HarnessIconName.Folder),
    )),
    HarnessContextMenuEntry.Divider,
    Item("fork", "分叉", HarnessIconName.Branch, children = listOf(
        Item("fork-chat", "分叉聊天", HarnessIconName.Host),
        Item("fork-worktree", "在新工作树中分叉", HarnessIconName.ExternalLink),
    )),
    HarnessContextMenuEntry.Divider,
    Item("copy", "复制", HarnessIconName.Copy, children = listOf(
        Item("copy-id", "复制会话 ID", HarnessIconName.Copy),
        Item("copy-link", "复制会话链接", HarnessIconName.ExternalLink),
    )),
    HarnessContextMenuEntry.Divider,
    Item("open", "打开方式", HarnessIconName.ExternalLink, children = listOf(
        Item("open-tab", "在新标签页中打开", HarnessIconName.Plus),
        Item("open-window", "在新窗口中打开", HarnessIconName.ExternalLink),
    )),
    HarnessContextMenuEntry.Divider,
    Item("archive", "归档", HarnessIconName.Archive, "Ctrl+Shift+A"),
    Item("delete", "永久删除", HarnessIconName.Trash),
)

@Composable
internal fun ConversationContextMenu(content: @Composable () -> Unit) {
    HarnessContextMenuArea(conversationContextMenuEntries, content = content)
}
