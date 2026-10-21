package im.hikaru.harness.client.app

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import im.hikaru.harness.client.connection.*
import im.hikaru.harness.ui.foundation.*
import kotlinx.coroutines.launch
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
internal fun ConversationViewport(
    messages: List<Message>,
    modifier: Modifier = Modifier,
    error: String? = null,
    onRetry: () -> Unit = {},
    /** Offered only while the session is settled; editing forks before the message and resends. */
    onEditMessage: ((Message) -> Unit)? = null,
    /** Streaming assistant tail; it is drawn after the settled history and never gets row actions. */
    live: List<Message> = emptyList(),
) {
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val bottom = LocalConversationBottomInset.current
    val atBottom by remember { derivedStateOf { !list.canScrollForward } }
    var following by remember { mutableStateOf(true) }
    // Live Codex prints one `用时 {duration}` header at the top of a turn's activity; the duration is the
    // turn's own wall clock, which our messages already carry as event timestamps.
    val workedFor = remember(messages) { turnWorkedFor(messages) }
    val toolCalls = remember(messages) {
        messages.flatMap { it.content }.filterIsInstance<ToolCallContent>().associateBy { it.id }
    }
    // The live app draws one activity row per call and keeps the output inside it; our history stores the
    // result as its own message, so those messages fold into the call row instead of printing a second row.
    val toolResults = remember(messages) { toolResultIndex(messages) }
    // Settled-only maps stay cached while the tail grows; only this appended reference changes per frame.
    val shown = remember(messages, toolResults, live) { messages.filterNot { it.isFoldedToolResult(toolResults) } + live }
    val liveIds = remember(live) { live.mapTo(mutableSetOf()) { it.id } }
    val lastTextMessage = remember(messages) { turnLastTextMessageIds(messages) }
    LaunchedEffect(list) {
        var wasScrolling = false
        snapshotFlow { list.isScrollInProgress to !list.canScrollForward }.collect { (scrolling, reachedBottom) ->
            // Content and composer remeasurement must not cancel an existing follow intent.
            if (scrolling || wasScrolling) following = reachedBottom
            wasScrolling = scrolling
        }
    }
    LaunchedEffect(shown.size, shown.lastOrNull()?.content, error, bottom) {
        if (shown.isNotEmpty() && following) list.scrollToItem(shown.size + if (error != null) 1 else 0)
    }
    Box(modifier.fillMaxWidth()) {
        LazyColumn(
            state = list,
            modifier = Modifier.fillMaxSize().padding(horizontal = 11.dp).semantics { contentDescription = "会话" },
            // The 12dp gap before the end anchor completes the measured 32dp transcript inset.
            contentPadding = PaddingValues(top = 32.dp, bottom = bottom + 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items(shown, key = { it.id }) { message ->
                val streaming = message.id in liveIds
                Box(Modifier.widthIn(max = 768.dp).fillMaxWidth().padding(horizontal = 16.dp)) {
                    MessageCard(
                        message = message,
                        workedForMillis = workedFor[message.id],
                        toolCalls = toolCalls,
                        toolResults = toolResults,
                        showActions = !streaming && message.id in lastTextMessage,
                        onEdit = onEditMessage?.takeIf { message.role == MessageRole.USER && message.source == UserMessageSource }
                            ?.let { edit -> { edit(message) } },
                        live = streaming,
                    )
                }
            }
            error?.let { message -> item { ConversationErrorCard(message, onRetry) } }
            item(key = "conversation-end") { Spacer(Modifier.height(0.dp)) }
        }
        if (!atBottom) {
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = bottom + 24.dp)
                .size(32.dp).background(ConversationAppearance.canvas, CircleShape)
                .border(1.dp, ConversationAppearance.ink.copy(alpha = 0.08f), CircleShape)) {
                ConversationActionButton(ConversationGlyph.Down, "滚动到底部", {
                    scope.launch { list.animateScrollToItem((list.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)) }
                }, size = 32.dp, tint = ConversationAppearance.ink)
            }
        }
    }
}

@Composable
internal fun MessageCard(
    message: Message,
    workedForMillis: Long? = null,
    toolCalls: Map<String, ToolCallContent> = emptyMap(),
    toolResults: Map<String, ToolResultEntry> = emptyMap(),
    showActions: Boolean = true,
    onEdit: (() -> Unit)? = null,
    /** True for the tail that is still arriving; its reasoning and labels stay in progress. */
    live: Boolean = false,
) {
    val isHuman = message.role == MessageRole.USER && message.source == UserMessageSource
    val clipboard = LocalClipboardManager.current
    val text = message.content.filterIsInstance<TextContent>().joinToString("\n") { it.text }
    BoxWithConstraints(Modifier.fillMaxWidth().semantics { testTag = "message:${message.id}" }) {
        val bubbleMaxWidth = maxWidth * 0.7f
        Column(Modifier.fillMaxWidth(), horizontalAlignment = if (isHuman) Alignment.End else Alignment.Start) {
            val contents = if (isHuman) Modifier.widthIn(max = bubbleMaxWidth)
                .background(ConversationAppearance.userBackground, RoundedCornerShape(22.dp)).padding(horizontal = 16.dp, vertical = 10.dp)
                else Modifier.fillMaxWidth()
            SelectionContainer {
                Column(contents, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (!isHuman && workedForMillis != null) WorkedForHeader(workedForMillis, "message:${message.id}")
                    message.content.forEachIndexed { index, content ->
                        MessageContentView(content, "message:${message.id}:content:$index", isHuman, toolCalls, toolResults, live)
                    }
                }
            }
            if (isHuman) {
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    message.timestampMillis?.let {
                        HarnessText(messageTimeLabel(it), color = ConversationAppearance.tertiary,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp))
                    }
                    ConversationActionButton(ConversationGlyph.Copy, "复制消息", { clipboard.setText(AnnotatedString(text)) })
                    if (onEdit != null) ConversationActionButton(ConversationGlyph.Edit, "编辑消息", onEdit)
                }
            } else if (showActions && text.isNotBlank()) {
                Row(Modifier.padding(top = 6.dp).offset(x = (-4).dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    ConversationActionButton(ConversationGlyph.Copy, "复制", { clipboard.setText(AnnotatedString(text)) })
                    ConversationActionButton(ConversationGlyph.Speaker, "朗读（暂不可用）")
                    ConversationActionButton(ConversationGlyph.Feedback, "评价回复（暂不可用）")
                    ConversationActionButton(ConversationGlyph.Branch, "从这里创建聊天分支（暂不可用）")
                }
            }
        }
    }
}

private fun isHumanMessage(message: Message): Boolean =
    message.role == MessageRole.USER && message.source == UserMessageSource

/**
 * Live Codex prints one `用时 {duration}` line above the activity of a turn (`localConversation.workedFor`
 * = `用时 {time}`, zh-CN catalogue). The duration is the turn's own wall clock, measured here from the human
 * prompt to the turn's last event, both of which our messages already carry as event timestamps. The header is
 * attached to the first message of the turn that holds activity so a turn prints it once.
 */
private fun turnWorkedFor(messages: List<Message>): Map<String, Long> {
    val headers = mutableMapOf<String, Long>()
    var index = 0
    while (index < messages.size) {
        if (isHumanMessage(messages[index])) { index++; continue }
        var firstActivity: String? = null
        var last: Long? = null
        var cursor = index
        while (cursor < messages.size && !isHumanMessage(messages[cursor])) {
            val current = messages[cursor]
            if (firstActivity == null && current.content.any {
                    it is ReasoningContent || it is ToolCallContent || it is ToolResultContent
                }) firstActivity = current.id
            val time = current.timestampMillis
            if (time != null && (last == null || time > last!!)) last = time
            cursor++
        }
        val start = messages.getOrNull(index - 1)?.takeIf(::isHumanMessage)?.timestampMillis
            ?: messages.subList(index, cursor).firstNotNullOfOrNull { it.timestampMillis }
        val end = last
        if (firstActivity != null && start != null && end != null && end > start) {
            headers[firstActivity] = end - start
        }
        index = cursor
    }
    return headers
}

internal class ToolResultEntry(val messageId: String, val index: Int, val content: ToolResultContent)

/** Results whose call is present in the same history: they belong to that call's activity row. */
private fun toolResultIndex(messages: List<Message>): Map<String, ToolResultEntry> {
    val calls = messages.flatMap { it.content }.filterIsInstance<ToolCallContent>().mapTo(mutableSetOf()) { it.id }
    val index = mutableMapOf<String, ToolResultEntry>()
    messages.forEach { message ->
        message.content.forEachIndexed { position, content ->
            if (content is ToolResultContent && content.toolCallId in calls) {
                index[content.toolCallId] = ToolResultEntry(message.id, position, content)
            }
        }
    }
    return index
}

/** A message that holds nothing but results folded into a call row is not drawn on its own. */
private fun Message.isFoldedToolResult(results: Map<String, ToolResultEntry>): Boolean =
    content.isNotEmpty() && content.all { it is ToolResultContent && results[it.toolCallId]?.messageId == id }

/** The action row sits under the last text of a turn, not under every message inside it. */
private fun turnLastTextMessageIds(messages: List<Message>): Set<String> {
    val ids = mutableSetOf<String>()
    var index = 0
    while (index < messages.size) {
        if (isHumanMessage(messages[index])) { index++; continue }
        var lastText: String? = null
        var cursor = index
        while (cursor < messages.size && !isHumanMessage(messages[cursor])) {
            val current = messages[cursor]
            if (current.content.any { it is TextContent }) lastText = current.id
            cursor++
        }
        lastText?.let(ids::add)
        index = cursor
    }
    return ids
}

private fun workedForLabel(millis: Long): String {
    val total = (millis / 1000).coerceAtLeast(1)
    val minutes = total / 60
    val seconds = total % 60
    return when {
        minutes == 0L -> "${seconds}秒"
        seconds == 0L -> "${minutes}分"
        else -> "${minutes}分${seconds}秒"
    }
}

@Composable
private fun WorkedForHeader(millis: Long, tag: String) {
    // Live `activity-divider`: `text-size-chat text-secondary` = rgba(255,255,255,.498), 14 / 21, no icon.
    Row(Modifier.fillMaxWidth().height(21.dp).semantics { testTag = "$tag:worked-for" },
        verticalAlignment = Alignment.CenterVertically) {
        HarnessText("用时 ${workedForLabel(millis)}", color = ConversationAppearance.tertiary, maxLines = 1,
            style = LocalHarnessContentTextStyle.current)
    }
}

private fun messageTimeLabel(epochMillis: Long): String {
    val local = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(TimeZone.currentSystemDefault())
    val day = listOf("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日")[local.dayOfWeek.ordinal]
    return "$day${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
}

@Composable
private fun MessageContentView(
    content: MessageContent,
    tag: String,
    human: Boolean = false,
    toolCalls: Map<String, ToolCallContent> = emptyMap(),
    toolResults: Map<String, ToolResultEntry> = emptyMap(),
    live: Boolean = false,
) {
    when (content) {
        is TextContent -> if (human) HarnessText(content.text, Modifier.semantics { testTag = tag },
            color = ConversationAppearance.userInk, style = LocalHarnessContentTextStyle.current.copy(
                lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                    androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center, androidx.compose.ui.text.style.LineHeightStyle.Trim.None)),
            overflow = TextOverflow.Visible)
            else ConversationMarkdown(content.text, Modifier.semantics { testTag = tag })
        // The reasoning row is already open while tokens arrive, so its label names the activity
        // instead of claiming the thought is complete.
        is ReasoningContent -> ActivityDisclosure(if (live) "正在思考…" else "已完成思考", HarnessIconName.Search, tag) {
            ConversationMarkdown(content.text, color = ConversationAppearance.muted)
        }
        is ToolCallContent -> ActivityDisclosure(toolActivityLabel(content), HarnessIconName.Host, tag) {
            val result = toolResults[content.id]
            if (result != null) {
                result.content.content.forEachIndexed { i, child ->
                    MessageContentView(child, "message:${result.messageId}:content:${result.index}:content:$i",
                        toolCalls = toolCalls, toolResults = toolResults)
                }
            } else ConversationMarkdown("```json\n${content.arguments}\n```", color = ConversationAppearance.muted)
        }
        // The live app summarises a tool result by the action it belongs to (`已读取 <file>`), not by its
        // outcome, and a failed call gets no separate surface: the error text only shows up if the assistant
        // repeats it in its reply. Only conversation-level errors (capacity, submit failures) use the aside.
        is ToolResultContent -> ActivityDisclosure(toolResultLabel(toolCalls[content.toolCallId]), HarnessIconName.Host, tag) {
            content.content.forEachIndexed { i, child -> MessageContentView(child, "$tag:content:$i", toolCalls = toolCalls) }
        }
    }
}

private fun toolActivityLabel(call: ToolCallContent): String {
    val name = call.name
    val file = toolArgumentFile(call.arguments)
    return when {
        name.contains("read", ignoreCase = true) -> file?.let { "已读取 $it" } ?: "已读取文件"
        name.contains("edit", ignoreCase = true) || name.contains("write", ignoreCase = true) ->
            file?.let { "编辑了 $it" } ?: "编辑了文件"
        name.contains("search", ignoreCase = true) -> "已搜索网页"
        name.contains("bash", ignoreCase = true) || name.contains("pwsh", ignoreCase = true) ||
            name.contains("shell", ignoreCase = true) || name.contains("exec", ignoreCase = true) -> "运行了命令"
        else -> "加载了工具 · $name"
    }
}

private fun toolResultLabel(call: ToolCallContent?): String = call?.let(::toolActivityLabel) ?: "工具输出"

/** Basename of the `path`-style argument a tool call carries, if the arguments are a JSON object we can read. */
private fun toolArgumentFile(arguments: String): String? {
    val match = Regex("\"(?:path|file_path|filePath)\"\\s*:\\s*\"([^\"]+)\"").find(arguments) ?: return null
    return match.groupValues[1].substringAfterLast('/').takeIf { it.isNotBlank() }
}

@Composable
private fun ActivityDisclosure(label: String, icon: HarnessIconName, tag: String, content: @Composable () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    // Live Codex `activity-header`: `self-start` content-width row, icon 16 / gap 6 / label / gap 4 / chevron
    // `icon-2xs` (14). Label and chevron are `text-text/60`; hovering lifts the label to full ink and fades the
    // chevron in (`opacity-0` by default), and expanding adds `rotate-90 opacity-100`.
    val chevron by animateFloatAsState(if (expanded || hovered) 1f else 0f, label = "activity-chevron")
    val angle by animateFloatAsState(if (expanded) 90f else 0f, label = "activity-chevron-angle")
    Column(Modifier.fillMaxWidth().semantics { testTag = tag }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.align(Alignment.Start).height(21.dp).hoverable(interactions)
            .clickable(interactionSource = interactions, indication = null, role = Role.Button,
                onClickLabel = if (expanded) "收起" else "展开") { expanded = !expanded }
            .semantics { testTag = "$tag:header"; stateDescription = if (expanded) "已展开" else "已折叠" },
            verticalAlignment = Alignment.CenterVertically) {
            HarnessIcon(icon, tint = ConversationAppearance.muted, size = 16.dp)
            HarnessText(label, Modifier.padding(start = 6.dp), maxLines = 1,
                color = if (hovered) ConversationAppearance.ink else ConversationAppearance.muted,
                style = LocalHarnessContentTextStyle.current)
            Box(Modifier.padding(start = 4.dp).rotate(angle)) {
                ConversationIcon(ConversationGlyph.ActivityChevron, ConversationAppearance.muted.copy(alpha = chevron), size = 14.dp)
            }
        }
        if (expanded) content()
    }
}

@Composable
internal fun ConversationErrorCard(message: String, onRetry: (() -> Unit)? = null, tag: String = "conversation-error") {
    // Live Codex aside: text-sm (13 / 21.125), 0.5px border-strong ring, 20/16/12/16 padding, 12 gap,
    // 18px icon on `self-center`, and `0 0 2px rgb(0 0 0/5%), 0 4px 6px rgb(0 0 0/2%)`.
    val shape = RoundedCornerShape(25.dp)
    Row(Modifier.fillMaxWidth()
        .shadow(6.dp, shape, clip = false, ambientColor = Color.Black.copy(alpha = 0.05f), spotColor = Color.Black.copy(alpha = 0.02f))
        .background(ConversationAppearance.canvas, shape)
        .border(0.5.dp, ConversationAppearance.ringStrong, shape)
        .semantics { testTag = tag; error(message) }.padding(start = 20.dp, top = 16.dp, end = 12.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        HarnessIcon(HarnessIconName.Warning, tint = ConversationAppearance.ink, size = 18.dp)
        HarnessText(message, Modifier.weight(1f), color = ConversationAppearance.ink,
            style = LocalHarnessContentTextStyle.current.copy(fontSize = HarnessTokens.Text.Label, lineHeight = HarnessTokens.Text.BodyLineHeight))
        if (onRetry != null) HarnessText("重试", Modifier.clickable(onClick = onRetry).padding(4.dp), color = ConversationAppearance.ink,
            style = LocalHarnessContentTextStyle.current.copy(fontSize = HarnessTokens.Text.Label, lineHeight = HarnessTokens.Text.BodyLineHeight))
    }
}
