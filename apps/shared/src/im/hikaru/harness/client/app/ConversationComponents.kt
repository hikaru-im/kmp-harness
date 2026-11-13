package im.hikaru.harness.client.app

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import im.hikaru.harness.client.connection.*
import im.hikaru.harness.client.find.ConversationFindBar
import im.hikaru.harness.client.find.findConversationMatches
import im.hikaru.harness.ui.foundation.*
import kotlinx.coroutines.launch
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToInt

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
    /** Opens a changed file on the Git page; a deleted file never offers it. */
    onOpenInGit: ((path: String) -> Unit)? = null,
    findOpen: Boolean = false,
    onCloseFind: () -> Unit = {},
) {
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val bottom = LocalConversationBottomInset.current
    val atBottom by remember { derivedStateOf { !list.canScrollForward } }
    var following by remember { mutableStateOf(true) }
    // CONV-02: history is grouped into human-delimited turns before anything is rendered. Activity
    // headers and actions belong to a turn, and each turn is one lazy item in the transcript.
    val settledTurns = remember(messages) { conversationTurns(messages) }
    // Live Codex prints one `用时 {duration}` header at the top of a turn's activity; the duration is the
    // turn's own wall clock, which our messages already carry as event timestamps.
    val workedFor = remember(settledTurns) { turnWorkedFor(settledTurns) }
    val toolCalls = remember(messages) {
        messages.flatMap { it.content }.filterIsInstance<ToolCallContent>().associateBy { it.id }
    }
    // The live app draws one activity row per call and keeps the output inside it; our history stores the
    // result as its own message, so those messages fold into the call row instead of printing a second row.
    val toolResults = remember(messages) { toolResultIndex(messages) }
    // Settled-only maps stay cached while the tail grows; only this appended reference changes per frame.
    val shown = remember(messages, toolResults, live) { messages.filterNot { it.isFoldedToolResult(toolResults) } + live }
    // Each turn owns its time marker, so the marker's height still participates in scrolling.
    // Streaming messages without a durable timestamp never create one. One zone for both grouping
    // and the printed label, so a marker's day and its clock agree.
    val zone = remember { TimeZone.currentSystemDefault() }
    val turns = remember(shown) { conversationTurns(shown) }
    val liveIds = remember(live) { live.mapTo(mutableSetOf()) { it.id } }
    var findQuery by remember(findOpen) { mutableStateOf("") }
    val findResult = remember(shown, findQuery) { findConversationMatches(shown, findQuery) }
    var activeFindIndex by remember(shown, findQuery) { mutableStateOf(0) }
    val activeFindMatch = findResult.matches.getOrNull(activeFindIndex)
    LaunchedEffect(findOpen, activeFindMatch, turns) {
        if (!findOpen) return@LaunchedEffect
        val match = activeFindMatch ?: return@LaunchedEffect
        val turnIndex = turns.indexOfFirst { turn -> turn.messages.any { it.id == match.messageId } }
        if (turnIndex >= 0) list.animateScrollToItem(turnIndex)
    }
    LaunchedEffect(findOpen) {
        if (!findOpen) findQuery = ""
    }
    val lastTextMessage = remember(settledTurns) { turnLastTextMessageIds(settledTurns) }
    // CONV-02 step two: while the reader is parked above the bottom, remember the topmost turn and
    // its intra-item offset so later content and layout changes cannot move what they are reading.
    val latestTurns by rememberUpdatedState(turns)
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var anchor by remember { mutableStateOf<TurnScrollAnchor?>(null) }
    // The end anchor is a zero-height spacer and is deliberately excluded from the scrollbar's
    // document estimate, which now counts turns instead of individual messages.
    val contentItemCount = turns.size + (if (error != null) 1 else 0)
    val itemCount = contentItemCount + 1
    LaunchedEffect(list) {
        var wasScrolling = false
        snapshotFlow { list.isScrollInProgress to !list.canScrollForward }.collect { (scrolling, reachedBottom) ->
            // Content and composer remeasurement must not cancel an existing follow intent.
            if (scrolling || wasScrolling) following = reachedBottom
            wasScrolling = scrolling
            if (!scrolling) {
                // Take the anchor only at rest: a moving list is already showing the user's intent.
                // Bottom-follow re-applies itself, so it never keeps an anchor of its own.
                val captured = if (following) null else list.turnScrollAnchor(latestTurns)
                if (following || captured != null) anchor = captured
            }
        }
    }
    LaunchedEffect(itemCount, shown.lastOrNull()?.content, error, bottom) {
        if (shown.isNotEmpty() && following) list.scrollToItem(itemCount - 1)
    }
    // Observe the measured viewport from a coroutine instead of writing state in onSizeChanged: that
    // callback runs inside the layout pass, and a scroll started there remeasures during measure.
    LaunchedEffect(list) {
        snapshotFlow { list.layoutInfo.viewportSize }.collect { viewportSize = it }
    }
    // Re-apply the turn anchor when the transcript list, viewport or composer inset changes. Bottom
    // follow owns the other direction; an active gesture is never interrupted. If the anchored turn
    // is still where it was, this is a no-op, so history refresh and streaming growth stay put.
    LaunchedEffect(turns, error, bottom, viewportSize, anchor) {
        val target = anchor ?: return@LaunchedEffect
        if (following || list.isScrollInProgress) return@LaunchedEffect
        val index = turns.indexOfFirst { it.id == target.turnId }
        if (index < 0) return@LaunchedEffect
        val first = list.layoutInfo.visibleItemsInfo.firstOrNull()
        if (first?.index == index && list.firstVisibleItemScrollOffset == target.offset) return@LaunchedEffect
        list.scrollToItem(index, target.offset)
    }
    val scrollbarMetrics by remember {
        derivedStateOf { conversationScrollbarMetrics(list, contentItemCount) }
    }
    Box(modifier.fillMaxWidth()) {
        LazyColumn(
            state = list,
            // Codex reserves `scrollbar-gutter: stable both-edges` on the transcript, so the
            // measured 11px gutter sits on both sides of the centred content column.
            modifier = Modifier.fillMaxSize()
                .padding(horizontal = 11.dp)
                .semantics { contentDescription = "会话" },
            // The 12dp gap before the end anchor completes the measured 32dp transcript inset.
            contentPadding = PaddingValues(top = 32.dp, bottom = bottom + 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // CONV-02: one lazy item per turn, not one per message. Each turn owns its marker and
            // messages, so the anchor above and any future visible-turn range work on this unit.
            // Virtualization below a turn (message-level) is still a recorded gap.
            items(turns.size, key = { "conversation-turn:${turns[it].id}" }) { position ->
                ConversationTurnSection(
                    turn = turns[position],
                    zone = zone,
                    workedFor = workedFor,
                    toolCalls = toolCalls,
                    toolResults = toolResults,
                    liveIds = liveIds,
                    lastTextMessage = lastTextMessage,
                    onEditMessage = onEditMessage,
                    onOpenInGit = onOpenInGit,
                )
            }
            error?.let { message -> item { ConversationErrorCard(message, onRetry) } }
            item(key = "conversation-end") { Spacer(Modifier.height(0.dp)) }
        }
        if (findOpen) {
            ConversationFindBar(
                query = findQuery,
                result = findResult,
                activeIndex = activeFindIndex.coerceIn(0, (findResult.matches.size - 1).coerceAtLeast(0)),
                onQueryChange = { next -> findQuery = next; activeFindIndex = 0 },
                onPrevious = {
                    if (findResult.matches.isNotEmpty()) {
                        activeFindIndex = (activeFindIndex - 1 + findResult.matches.size) % findResult.matches.size
                    }
                },
                onNext = {
                    if (findResult.matches.isNotEmpty()) activeFindIndex = (activeFindIndex + 1) % findResult.matches.size
                },
                onClose = onCloseFind,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp, start = 12.dp, end = 12.dp),
            )
        }
        if (scrollbarMetrics.visible) {
            HarnessOverlayScrollbar(
                metrics = scrollbarMetrics,
                modifier = Modifier.align(Alignment.CenterEnd).width(11.dp).fillMaxHeight(),
                variant = HarnessScrollbarVariant.Arrowless,
                onScrollToFraction = { fraction ->
                    val target = (fraction * (list.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)).roundToInt()
                    scope.launch { list.animateScrollToItem(target) }
                },
                onScrollBy = { delta -> scope.launch { list.scrollBy(delta) } },
                testTag = "conversation-scrollbar",
                contentDescription = "会话滚动条",
            )
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

/**
 * One turn as a single lazy item: its time separator (when it has a durable timestamp) followed by
 * every message it owns. The inner spacing matches the former per-row `LazyColumn` arrangement, so
 * grouping does not change the transcript's visual rhythm.
 */
@Composable
private fun ConversationTurnSection(
    turn: ConversationTurn,
    zone: TimeZone,
    workedFor: Map<String, Long>,
    toolCalls: Map<String, ToolCallContent>,
    toolResults: Map<String, ToolResultEntry>,
    liveIds: Set<String>,
    lastTextMessage: Set<String>,
    onEditMessage: ((Message) -> Unit)?,
    onOpenInGit: ((path: String) -> Unit)?,
) {
    Column(
        Modifier.fillMaxWidth().semantics { testTag = "conversation-turn:${turn.id}" },
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        turn.anchorTimestampMillis?.let { timestamp ->
            ConversationTimeMarker(timestamp, turn.id, zone)
        }
        turn.messages.forEach { message ->
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
                    onOpenInGit = onOpenInGit,
                )
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
    onOpenInGit: ((path: String) -> Unit)? = null,
) {
    val isHuman = message.role == MessageRole.USER && message.source == UserMessageSource
    val clipboard = LocalClipboardManager.current
    val text = message.content.filterIsInstance<TextContent>().joinToString("\n") { it.text }
    // Consecutive file changes form one card group; the first index owns the group's tag.
    val fileChanges = remember(message.content) { fileChangeRuns(message.content) }
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
                        val changes = fileChanges[index]
                        when {
                            changes != null -> FileChangeCard(
                                changes, "message:${message.id}:content:$index", onOpenInGit)
                            // Every member after the run's first is drawn by that group card.
                            content is FileChangeContent -> Unit
                            else -> MessageContentView(content, "message:${message.id}:content:$index",
                                isHuman, toolCalls, toolResults, live)
                        }
                    }
                }
            }
            if (isHuman) {
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    // The turn's clock lives in the timeline separator, not under the bubble: Codex
                    // prints one `role="separator"` row per turn and no per-message time.
                    ConversationActionButton(ConversationGlyph.Copy, "复制消息", { clipboard.setText(AnnotatedString(text)) })
                    if (onEdit != null) ConversationActionButton(ConversationGlyph.Edit, "编辑消息", onEdit)
                }
            } else if (showActions && text.isNotBlank()) {
                Row(Modifier.padding(top = 6.dp).offset(x = (-4).dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    ConversationActionButton(ConversationGlyph.Copy, "复制", { clipboard.setText(AnnotatedString(text)) })
                    ConversationActionButton(ConversationGlyph.Hand, "喜欢")
                    ConversationActionButton(ConversationGlyph.Feedback, "不喜欢")
                    ConversationActionButton(ConversationGlyph.Branch, "分叉")
                }
            }
        }
    }
}

/**
 * One row of the rendered timeline: either a message slot or a Codex time separator.
 *
 * Codex prints one separator per turn, carrying the turn's first durable event time
 * (`<div role="separator" aria-label="10月7日周三 1:02"><time datetime=…>`, 52px tall,
 * centred on the transcript column). Measured live through CDP on 2026-10-23.
 */
internal sealed interface ConversationTimelineRow {
    val key: String

    data class MessageRow(val message: Message, val turnId: String) : ConversationTimelineRow {
        override val key: String get() = message.id
    }

    data class TimeMarkerRow(val anchorMessageId: String, val timestampMillis: Long) : ConversationTimelineRow {
        override val key: String get() = "conversation-time:$anchorMessageId:$timestampMillis"
    }
}

/**
 * Flattens the turn model back into rows: one separator before each turn, then the messages it
 * owns. Kept as the row-level projection used by timeline tests and by any future visible-range
 * calculation; the viewport itself renders one lazy item per [ConversationTurn].
 */
internal fun conversationTimeline(
    messages: List<Message>,
    zone: TimeZone = TimeZone.currentSystemDefault(),
): List<ConversationTimelineRow> = conversationTimelineRows(conversationTurns(messages))

internal fun conversationTimelineRows(turns: List<ConversationTurn>): List<ConversationTimelineRow> {
    val rows = mutableListOf<ConversationTimelineRow>()
    turns.forEach { turn ->
        turn.anchorTimestampMillis?.let { anchor ->
            rows += ConversationTimelineRow.TimeMarkerRow(turn.id, anchor)
        }
        turn.messages.forEach { message ->
            rows += ConversationTimelineRow.MessageRow(message, turn.id)
        }
    }
    return rows
}

/** `10月7日周三 1:02`, the label Codex puts in the separator's `aria-label`. */
internal fun timeMarkerLabel(epochMillis: Long, zone: TimeZone = TimeZone.currentSystemDefault()): String =
    timeMarkerDateLabel(epochMillis, zone) + " " + timeMarkerClockLabel(epochMillis, zone)

/** `10月7日周三`: the marker's leading, medium-weight span. */
internal fun timeMarkerDateLabel(epochMillis: Long, zone: TimeZone = TimeZone.currentSystemDefault()): String {
    val local = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(zone)
    // Codex abbreviates the weekday: `10月7日周三`, not the full `星期三`.
    val weekday = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")[local.dayOfWeek.ordinal]
    return "${local.monthNumber}月${local.day}日$weekday"
}

/** `1:02`: the marker's trailing, normal-weight span. */
internal fun timeMarkerClockLabel(epochMillis: Long, zone: TimeZone = TimeZone.currentSystemDefault()): String {
    val local = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(zone)
    return "${local.hour}:${local.minute.toString().padStart(2, '0')}"
}

/**
 * Codex's conversation separator: `flex justify-center py-4` around a `text-sm leading-5`
 * timestamp, i.e. a 52dp row whose 20dp line is centred between 16dp paddings. The date keeps
 * `font-medium`; the clock stays normal weight. It is not selectable, and it does not join
 * markdown copy.
 */
@Composable
private fun ConversationTimeMarker(epochMillis: Long, anchorMessageId: String, zone: TimeZone) {
    val label = timeMarkerLabel(epochMillis, zone)
    val style = LocalHarnessContentTextStyle.current
    Box(
        // The semantics sit outside the padding so the tagged node is the separator row itself —
        // the 52dp box Codex lays out — rather than the 20dp line inside it.
        Modifier.fillMaxWidth().height(52.dp).clearAndSetSemantics {
            contentDescription = label
            testTag = "conversation-time-marker:$anchorMessageId"
        }.padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Medium)) { append(timeMarkerDateLabel(epochMillis, zone)) }
                append(" ")
                append(timeMarkerClockLabel(epochMillis, zone))
            },
            color = ConversationAppearance.tertiary,
            maxLines = 1,
            style = style.copy(fontSize = 13.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal),
        )
    }
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

/**
 * CONV-02 step two: the reader's resting position, expressed in the same units the lazy list uses
 * for `scrollToItem` — turn id plus how far that turn's top is scrolled past the viewport top.
 * Turn ids survive refresh and streaming, so the anchor survives both even when indices shift.
 */
private data class TurnScrollAnchor(val turnId: String, val offset: Int)

/**
 * Anchors only on a turn: the error card and the zero-height end spacer are not reading positions.
 * The first visible item is where the reader is looking, so no message-level anchor is attempted.
 */
private fun LazyListState.turnScrollAnchor(turns: List<ConversationTurn>): TurnScrollAnchor? {
    val first = layoutInfo.visibleItemsInfo.firstOrNull() ?: return null
    if (first.index !in turns.indices) return null
    return TurnScrollAnchor(turns[first.index].id, firstVisibleItemScrollOffset)
}

private fun conversationScrollbarMetrics(state: LazyListState, itemCount: Int): HarnessScrollbarMetrics {
    val layout = state.layoutInfo
    val viewport = (layout.viewportEndOffset - layout.viewportStartOffset).coerceAtLeast(0)
    val visible = layout.visibleItemsInfo
    if (viewport <= 0 || itemCount <= 0 || visible.isEmpty()) {
        return HarnessScrollbarMetrics(0f, 1f, visible = false)
    }
    // The end anchor has zero height; averaging it in would halve the estimated document extent
    // once turns (not messages) became the lazy items.
    val measured = visible.filter { it.size > 0 }.ifEmpty { visible }
    val measuredExtent = measured.sumOf { it.size }.toFloat()
    val averageItemExtent = (measuredExtent / measured.size).coerceAtLeast(1f)
    val contentExtent = (itemCount * averageItemExtent).coerceAtLeast(viewport.toFloat())
    val maxScroll = (contentExtent - viewport).coerceAtLeast(0f)
    val first = visible.first()
    val scrollOffset = (first.index * averageItemExtent - first.offset).coerceIn(0f, maxScroll)
    return HarnessScrollbarMetrics(
        positionFraction = if (maxScroll == 0f) 0f else scrollOffset / maxScroll,
        thumbFraction = (viewport / contentExtent).coerceIn(0.05f, 1f),
        visible = maxScroll > 0f,
    )
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
        // Only reached when a file change is nested inside a disclosure; transcript rows are grouped
        // by MessageCard before this composable sees them, so this card has no Git navigation.
        is FileChangeContent -> FileChangeCard(listOf(content), "$tag:file-change")
    }
}

/** Maps each run of consecutive file changes to the run's files, keyed by the run's first index. */
internal fun fileChangeRuns(contents: List<MessageContent>): Map<Int, List<FileChangeContent>> {
    val runs = linkedMapOf<Int, List<FileChangeContent>>()
    var index = 0
    while (index < contents.size) {
        if (contents[index] is FileChangeContent) {
            val start = index
            val files = mutableListOf<FileChangeContent>()
            while (index < contents.size) {
                val file = contents[index] as? FileChangeContent ?: break
                files += file
                index++
            }
            runs[start] = files
        } else index++
    }
    return runs
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
