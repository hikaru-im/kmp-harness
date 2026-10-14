package im.hikaru.harness.client.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import im.hikaru.harness.client.connection.Message
import im.hikaru.harness.client.connection.MessageContent
import im.hikaru.harness.client.connection.MessageRole
import im.hikaru.harness.client.connection.ModelMessageSource
import im.hikaru.harness.client.connection.PluginMessageSource
import im.hikaru.harness.client.connection.ReasoningContent
import im.hikaru.harness.client.connection.TextContent
import im.hikaru.harness.client.connection.ToolCallContent
import im.hikaru.harness.client.connection.ToolResultContent
import im.hikaru.harness.client.connection.ToolMessageSource
import im.hikaru.harness.client.connection.UserMessageSource
import im.hikaru.harness.ui.foundation.HarnessIcon
import im.hikaru.harness.ui.foundation.HarnessIconName
import im.hikaru.harness.ui.foundation.HarnessSurface
import im.hikaru.harness.ui.foundation.HarnessText
import im.hikaru.harness.ui.foundation.HarnessTokens

@Composable
internal fun ConversationViewport(
    messages: List<Message>,
    modifier: Modifier = Modifier,
    error: String? = null,
    onRetry: () -> Unit = {},
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Three),
    ) {
        if (messages.isEmpty()) {
            item {
                HarnessSurface(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(HarnessTokens.Spacing.Seven),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Two),
                    ) {
                        HarnessIcon(HarnessIconName.Send, tint = HarnessTokens.Colors.Accent, size = HarnessTokens.Control.LargeHeight)
                        HarnessText("开始新的会话", color = HarnessTokens.Colors.Text)
                        HarnessText("输入问题，Harness 会在当前 Host 上运行 Agent。", color = HarnessTokens.Colors.TextMuted)
                    }
                }
            }
        }
        items(messages, key = { it.id }) { message ->
            MessageCard(message)
        }
        error?.let { message ->
            item {
                ErrorMessage(message = message, onRetry = onRetry)
            }
        }
    }
}

@Composable
internal fun MessageCard(message: Message) {
    val isHuman = message.role == MessageRole.USER && message.source == UserMessageSource
    val description = when (val source = message.source) {
        UserMessageSource -> if (isHuman) "You · USER" else "User source · ${message.role}"
        is ModelMessageSource -> "Model message · ${message.role}"
        is ToolMessageSource -> "Tool message · ${message.role}"
        is PluginMessageSource -> "Plugin ${source.plugin} · ${message.role}"
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        // The 672dp content column and 85%/560dp bubble cap are Harness adaptations.
        // Rounded 16dp corners and 10dp vertical padding map rounded-2xl/py-2.5;
        // original runtime widths, spacing and colors have not been measured.
        BoxWithConstraints(
            Modifier.widthIn(max = HarnessTokens.Control.ContentMaxWidth).fillMaxWidth(),
            contentAlignment = if (isHuman) Alignment.TopEnd else Alignment.TopStart,
        ) {
            val contentModifier = if (isHuman) {
                Modifier.widthIn(max = minOf(maxWidth * 0.85f, 560.dp))
                    .background(HarnessTokens.Colors.SurfaceSecondary, RoundedCornerShape(16.dp))
            } else Modifier.fillMaxWidth()
            Column(
                modifier = contentModifier.semantics {
                    testTag = "message:${message.id}"
                    contentDescription = description
                }.then(if (isHuman) Modifier.padding(horizontal = 16.dp, vertical = 10.dp) else Modifier),
                verticalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Two),
            ) {
                message.content.forEachIndexed { index, content ->
                    MessageContentView(content, "message:${message.id}:content:$index")
                }
            }
        }
    }
}

@Composable
private fun MessageContentView(content: MessageContent, tag: String) {
    when (content) {
        is TextContent -> MessageText(content.text, tag)
        is ReasoningContent -> ReasoningBlock(content, tag)
        is ToolCallContent -> ToolCallBlock(content, tag)
        is ToolResultContent -> ToolResultBlock(content, tag)
    }
}

@Composable
private fun MessageText(text: String, tag: String, muted: Boolean = false) {
    HarnessText(text, Modifier.semantics { testTag = tag },
        color = if (muted) HarnessTokens.Colors.TextMuted else HarnessTokens.Colors.Text,
        maxLines = Int.MAX_VALUE, overflow = TextOverflow.Visible)
}

@Composable
private fun ReasoningBlock(content: ReasoningContent, tag: String) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        MessageDisclosureHeader(if (expanded) "Reasoning" else "Reasoning · 展开",
            HarnessIconName.Search, expanded, tag) { expanded = !expanded }
        if (expanded) MessageText(content.text, "$tag:body", muted = true)
    }
}

@Composable
private fun ToolCallBlock(content: ToolCallContent, tag: String) {
    var expanded by remember(content.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        MessageDisclosureHeader(content.name, HarnessIconName.Host, expanded, tag) { expanded = !expanded }
        if (expanded) MessageText(content.arguments, "$tag:body", muted = true)
    }
}

@Composable
private fun MessageDisclosureHeader(
    label: String, icon: HarnessIconName, expanded: Boolean, tag: String, onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = HarnessTokens.Control.CompactHeight)
            .clickable(role = Role.Button, onClickLabel = if (expanded) "Collapse" else "Expand", onClick = onToggle)
            .semantics { testTag = "$tag:header"; stateDescription = if (expanded) "Expanded" else "Collapsed" }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Two),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HarnessIcon(icon, tint = HarnessTokens.Colors.TextMuted, size = 16.dp)
        HarnessText(label, Modifier.weight(1f), color = HarnessTokens.Colors.TextMuted,
            maxLines = Int.MAX_VALUE, overflow = TextOverflow.Visible)
        HarnessIcon(if (expanded) HarnessIconName.ChevronDown else HarnessIconName.ChevronRight,
            tint = HarnessTokens.Colors.TextMuted, size = 16.dp)
    }
}

@Composable
private fun ToolResultBlock(content: ToolResultContent, tag: String) {
    Column(
        modifier = Modifier.fillMaxWidth().semantics { testTag = tag },
        verticalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Two),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Two), verticalAlignment = Alignment.CenterVertically) {
            HarnessIcon(
                if (content.isError) HarnessIconName.Warning else HarnessIconName.Host,
                tint = if (content.isError) HarnessTokens.Colors.Danger else HarnessTokens.Colors.TextMuted,
                size = 16.dp,
            )
            HarnessText(if (content.isError) "Tool failed" else "Tool result",
                color = if (content.isError) HarnessTokens.Colors.Danger else HarnessTokens.Colors.TextMuted)
        }
        content.content.forEachIndexed { index, child ->
            MessageContentView(child, "$tag:content:$index")
        }
    }
}

@Composable
private fun ErrorMessage(message: String, onRetry: () -> Unit) {
    HarnessSurface(modifier = Modifier.fillMaxWidth(), selected = true) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(HarnessTokens.Spacing.Three),
            horizontalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Three),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HarnessIcon(HarnessIconName.Warning, tint = HarnessTokens.Colors.Danger)
            Column(modifier = Modifier.weight(1f)) {
                HarnessText("请求失败", color = HarnessTokens.Colors.Danger)
                HarnessText(message, color = HarnessTokens.Colors.TextMuted)
            }
            androidx.compose.material3.TextButton(onClick = onRetry) { HarnessText("重试") }
        }
    }
}
