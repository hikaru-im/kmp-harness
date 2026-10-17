package im.hikaru.harness.client.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import im.hikaru.harness.client.conversation.ConversationAction
import im.hikaru.harness.client.conversation.ConversationDataPhase
import im.hikaru.harness.client.conversation.ConversationErrorKind
import im.hikaru.harness.client.conversation.ConversationRetry
import im.hikaru.harness.client.conversation.ConversationState
import im.hikaru.harness.ui.foundation.*

public data class ConversationModelChoice(val providerId: String, val modelId: String, val label: String)

/**
 * Browser rem values use a 16px base, mapped to logical dp: 2.5rem = 40dp,
 * 40rem = 640dp; 28 four-pixel spacing units = 112dp. These are layout conversions,
 * not a claim of pixel parity. Font and palette still follow HarnessTheme.
 * Owners can configure the editor cap without changing the shared composer interface.
 */
public val LocalConversationEditorMaxHeight = staticCompositionLocalOf { 160.dp }
/** Supply the main viewport height when Thread and its composer occupy separate layout slots. */
public val LocalConversationViewportHeight = staticCompositionLocalOf<Dp?> { null }
internal val LocalConversationBottomInset = staticCompositionLocalOf { 0.dp }

/**
 * The 新聊天 page: the surface a new chat opens on, measured against the live Codex page.
 *
 * The page is its hero above its composer. The hero is the reference's `home-icon` block — the Codex
 * mark at 56dp and 30% ink — over the `heading-xl` question, with the project name carried *inside* the
 * heading as a dotted-underlined segment rather than as a line of its own; the composer then sits at the
 * bottom of the page, which is where the reference docks it (its own region ends 16px above the page's
 * bottom edge). Centring the hero in the space the composer leaves puts it at the height the reference
 * draws it at: the live hero's ink centre is 610 of a page running 90..1316, i.e. 42% down.
 *
 * The reference names the selected *project* in that heading and opens a project picker when it is
 * clicked. This client has no project picker and no project selection to name, so the heading names the
 * Host the page is bound to — the same label the workspace navigator's header shows — and draws the
 * name as plain dotted-underlined text rather than a control that would do nothing.
 */
@Composable
public fun LiveHomeSurface(hostLabel: String?, content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().semantics { testTag = "live-home" }) {
        val viewportHeight = if (constraints.hasBoundedHeight) maxHeight else null
        CompositionLocalProvider(LocalConversationViewportHeight provides viewportHeight) {
            Column(Modifier.fillMaxSize().padding(HarnessTokens.Spacing.Four)) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    HomeHero(hostLabel)
                }
                Box(Modifier.fillMaxWidth().semantics { testTag = "live-home-content" }) { content() }
            }
        }
    }
}

/** The heading the reference sets when no project is selected (`home.hero.noProjectWhatShouldWeBuild`). */
private const val HomeHeroNoProject = "我们要构建什么？"

/** The heading around the project name (`home.hero.whatShouldWeBuildInSelectedProject`). */
private const val HomeHeroPrefix = "你想让我们在 "

private const val HomeHeroSuffix = " 中构建什么？"

/**
 * The hero block: the Codex mark over the heading, 24dp apart (the reference's `gap-6`). The mark keeps
 * its ink and changes its own opacity with the pointer, exactly as the live element does — it is
 * `aria-hidden` there, so it is drawn without an action here too.
 */
@Composable
private fun HomeHero(hostLabel: String?) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Six),
    ) {
        CodexMark(
            modifier = Modifier.hoverable(source),
            tint = HarnessTokens.Colors.ChromeText.copy(
                alpha =
                    if (hovered) HarnessTokens.Control.HomeHeroMarkHoverAlpha
                    else HarnessTokens.Control.HomeHeroMarkAlpha,
            ),
            size = HarnessTokens.Control.HomeHeroMark,
        )
        HeroHeading(hostLabel?.takeIf { it.isNotBlank() })
    }
}

/**
 * The hero's question, centred on the reference's `heading-xl` (28sp on a 33.6sp line at weight 400).
 * With a name to place, the question wraps around it and the name carries the reference's dotted rule
 * (`decoration-dotted decoration-[1px] underline-offset-4`); without one it is the reference's own
 * shorter question. Both variants wrap, so a narrow pane wraps the question instead of clipping it.
 */
@Composable
private fun HeroHeading(project: String?) {
    val style =
        MaterialTheme.typography.headlineSmall.copy(
            fontSize = HarnessTokens.Text.HeadingXl,
            lineHeight = 33.6.sp,
            fontWeight = FontWeight.Normal,
        )
    val modifier = Modifier.semantics { testTag = "live-home-hero"; heading() }
    if (project == null) {
        Text(
            text = HomeHeroNoProject,
            modifier = modifier,
            color = HarnessTokens.Colors.ChromeText,
            style = style,
            textAlign = TextAlign.Center,
        )
        return
    }
    val text = buildAnnotatedString {
        append(HomeHeroPrefix)
        append(project)
        append(HomeHeroSuffix)
    }
    val range = HomeHeroPrefix.length until HomeHeroPrefix.length + project.length
    // Read the ink in the composition: the draw lambda below is not a composable scope.
    val underlineInk = HarnessTokens.Colors.ChromeIcon
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text = text,
        modifier = modifier.drawBehind { drawHeroUnderline(layout, range, underlineInk) },
        color = HarnessTokens.Colors.ChromeText,
        style = style,
        textAlign = TextAlign.Center,
        onTextLayout = { layout = it },
    )
}

/**
 * The dotted rule under the project name, drawn from the laid-out text rather than as a text
 * decoration: Compose has no dotted underline, and the reference's is a measured pattern — 1px dots on
 * a 2px pitch, sitting 4px under the baseline, running the name's full width (scanned row by row on the
 * live page: dots on y=664 against a baseline at 660). A name that wraps draws a run per line, each
 * stopping at the line's end.
 */
private fun DrawScope.drawHeroUnderline(layout: TextLayoutResult?, range: IntRange, ink: Color) {
    if (layout == null || range.isEmpty()) return
    val pitch = HarnessTokens.Control.HomeHeroUnderlinePitch.toPx()
    val dot = pitch / 2f
    val baseline = HarnessTokens.Control.HomeHeroUnderlineOffset.toPx()
    for (line in layout.getLineForOffset(range.first)..layout.getLineForOffset(range.last)) {
        val from = maxOf(range.first, layout.getLineStart(line))
        val to = minOf(range.last + 1, layout.getLineEnd(line))
        if (to <= from) continue
        val left = layout.getHorizontalPosition(from, usePrimaryDirection = true)
        val right = layout.getHorizontalPosition(to, usePrimaryDirection = false)
        val top = layout.getLineBaseline(line) + baseline
        var x = left
        while (x < right) {
            drawRect(ink, topLeft = Offset(x, top), size = Size(minOf(dot, right - x), dot))
            x += pitch
        }
    }
}

/** Timeline only. The app owns the selected route, controller and separate shared composer. */
@Composable
public fun LiveThreadSurface(state: ConversationState, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize().background(ConversationAppearance.canvas)) {
        Column(Modifier.fillMaxSize().semantics { testTag = "live-thread" },
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                !state.connected || state.historyPhase == ConversationDataPhase.Disconnected ->
                    ConversationNotice("连接已断开。重新连接后可继续此会话。", "live-thread-disconnected")
                state.historyPhase == ConversationDataPhase.Loading ->
                    ConversationNotice("正在读取会话…", "live-thread-loading")
                state.historyPhase == ConversationDataPhase.Failed && ConversationErrorKind.History !in state.errors -> {
                    ConversationNotice("会话历史不可用。", "live-thread-unavailable")
                    HarnessButton("重试", onRetry, testTag = "live-thread-retry", size = HarnessControlSize.Compact)
                }
            }
            state.errors.values.forEach { issue ->
                ConversationErrorCard(issue.message,
                    onRetry = if (issue.retry == ConversationRetry.History || issue.retry == ConversationRetry.Refresh) onRetry else null,
                    tag = "live-thread-error-${issue.kind.name}")
            }
            if (state.history.isNotEmpty()) {
                key(state.host?.hostId, state.host?.identity, state.selectedSessionId) {
                    ConversationViewport(state.history, Modifier.weight(1f).semantics { testTag = "live-thread-timeline" })
                }
            } else {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (state.connected && state.historyPhase !in listOf(ConversationDataPhase.Loading, ConversationDataPhase.Failed,
                            ConversationDataPhase.Disconnected)) {
                        HarnessText("此会话暂无消息。", Modifier.semantics { testTag = "live-thread-empty" },
                            color = HarnessTokens.Colors.TextMuted)
                    }
                }
            }
        }
        Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(40.dp).background(
            androidx.compose.ui.graphics.Brush.verticalGradient(
                0f to ConversationAppearance.canvas,
                0.1f to ConversationAppearance.canvas.copy(alpha = 0.82f),
                0.3f to ConversationAppearance.canvas.copy(alpha = 0.48f),
                0.6f to ConversationAppearance.canvas.copy(alpha = 0.12f),
                1f to Color.Transparent,
            ),
        ))
    }

}

@Composable
public fun LiveConversationComposer(
    state: ConversationState,
    onDraftChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
    models: List<ConversationModelChoice>,
    selectedModel: ConversationModelChoice?,
    onModelSelect: (ConversationModelChoice) -> Unit,
    modelUnavailableReason: String?,
    submitUnavailableReason: String?,
) {
    val configuredCap = LocalConversationEditorMaxHeight.current.coerceAtLeast(44.dp)
    val suppliedViewport = LocalConversationViewportHeight.current
    val canSend = state.canSubmit && submitUnavailableReason == null
    val latestCanSend by rememberUpdatedState(canSend)
    val latestSubmit by rememberUpdatedState(onSubmit)
    val hostId = state.host?.hostId
    val identity = state.host?.identity
    val sessionId = state.selectedSessionId
    var editor by remember(hostId, identity, sessionId) {
        mutableStateOf(TextFieldValue(state.draft, TextRange(state.draft.length)))
    }
    var enterHeld by remember(hostId, identity, sessionId) { mutableStateOf(false) }
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(windowFocused) { if (!windowFocused) enterHeld = false }
    LaunchedEffect(hostId, identity, sessionId, state.draft) {
        if (editor.text != state.draft) editor = TextFieldValue(state.draft,
            TextRange(editor.selection.start.coerceIn(0, state.draft.length), editor.selection.end.coerceIn(0, state.draft.length)))
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val viewport = suppliedViewport ?: if (constraints.hasBoundedHeight) maxHeight else null
        val editorCap = if (viewport == null) configuredCap else minOf(configuredCap, (viewport * 0.25f).coerceAtLeast(44.dp))
        val ink = ConversationAppearance.ink
        val ring = if (ConversationAppearance.dark) Color.White.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.04f)
        val status = submitUnavailableReason ?: when {
            !state.connected -> "连接 Host 后可发送消息。"
            state.cancelling -> "正在停止…"
            state.action != ConversationAction.Idle -> "正在运行…"
            else -> null
        }
        Column(Modifier.fillMaxWidth()) {
            // Real failures remain visible; capability explanations live on their controls.
            if (state.selectedSessionId == null) state.errors.values.forEach {
                ConversationErrorCard(it.message, tag = "live-composer-error-${it.kind.name}")
            }
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                    .background(ConversationAppearance.composer)
                    .drawWithContent {
                        drawContent()
                        drawRoundRect(ring, cornerRadius = androidx.compose.ui.geometry.CornerRadius(22.dp.toPx()),
                            style = Stroke(0.5.dp.toPx()))
                    }
                    .semantics { testTag = "live-composer"; status?.let { stateDescription = it } }
                    .padding(top = 14.dp, bottom = 8.dp),
            ) {
                Box(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                    BasicTextField(
                        value = editor,
                        onValueChange = { value ->
                            val previousText = editor.text
                            editor = value
                            if (value.text != previousText) onDraftChange(value.text)
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).heightIn(min = 44.dp, max = editorCap)
                            .onFocusChanged { if (!it.isFocused) enterHeld = false }
                            .onPreviewKeyEvent { event ->
                                when {
                                    event.key != Key.Enter && event.key != Key.NumPadEnter -> false
                                    event.type == KeyEventType.KeyUp -> {
                                        val consumed = enterHeld
                                        enterHeld = false
                                        consumed
                                    }
                                    event.type != KeyEventType.KeyDown -> false
                                    enterHeld -> true
                                    event.isShiftPressed || event.isCtrlPressed || event.isAltPressed || event.isMetaPressed -> false
                                    editor.composition != null -> false
                                    else -> {
                                        enterHeld = true
                                        if (latestCanSend) latestSubmit()
                                        true
                                    }
                                }
                            }.semantics { testTag = "live-composer-editor"; contentDescription = "消息" },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = ink, lineHeight = 20.sp),
                        cursorBrush = SolidColor(ink), singleLine = false,
                        decorationBox = { inner ->
                            Box {
                                if (editor.text.isEmpty()) HarnessText(
                                    if (state.selectedSessionId == null) "随心输入" else "与 Codex 协作",
                                    color = ink.copy(alpha = 0.5f),
                                )
                                inner()
                            }
                        },
                    )
                }
                ComposerFooter(state, models, selectedModel, onModelSelect, modelUnavailableReason, canSend, onSubmit, onCancel,
                    submitUnavailableReason)
            }
        }
    }
}

@Composable
private fun ComposerFooter(
    state: ConversationState, models: List<ConversationModelChoice>, selectedModel: ConversationModelChoice?,
    onModelSelect: (ConversationModelChoice) -> Unit, modelReason: String?,
    canSend: Boolean, onSubmit: () -> Unit, onCancel: () -> Unit, submitReason: String?,
) {
    var modelMenuOpen by remember { mutableStateOf(false) }
    val choices = models.distinctBy { it.providerId to it.modelId }
    val effectiveReason = modelReason ?: if (state.selectedSessionId != null) "会话开始后不能更改模型。" else null
    val canChoose = effectiveReason == null && choices.isNotEmpty() && state.action == ConversationAction.Idle && !state.cancelling
    val ink = ConversationAppearance.ink
    Row(Modifier.fillMaxWidth().height(28.dp).padding(horizontal = 8.dp).semantics { testTag = "live-composer-footer-wide" },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        ConversationActionButton(ConversationGlyph.Plus, "添加文件等内容（附件暂不可用）", size = 28.dp,
            tint = ink, modifier = Modifier.semantics { testTag = "live-composer-attachment" })
        val interactions = remember { MutableInteractionSource() }
        val hovered by interactions.collectIsHoveredAsState()
        HarnessTooltip("更改权限（权限调整暂不可用）", hovered) {
            Row(Modifier.height(28.dp).hoverable(interactions).padding(horizontal = 7.dp).semantics {
                testTag = "live-composer-permissions"; role = Role.Button; disabled(); contentDescription = "更改权限"; stateDescription = "权限调整暂不可用"
            }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ConversationIcon(ConversationGlyph.Permissions, ConversationAppearance.tertiary)
                HarnessText("自定义", color = ConversationAppearance.tertiary, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            HarnessMenu(
                expanded = modelMenuOpen && canChoose,
                entries = choices.map { choice -> HarnessMenuEntry.Item(key = choice.menuKey(), text = choice.label,
                    selected = choice.providerId == selectedModel?.providerId && choice.modelId == selectedModel?.modelId) },
                onExpandedChange = { modelMenuOpen = it && canChoose },
                onSelect = { key -> if (canChoose) choices.firstOrNull { it.menuKey() == key }?.let(onModelSelect) },
                anchor = { toggle ->
                    val interactions = remember { MutableInteractionSource() }
                    val hovered by interactions.collectIsHoveredAsState()
                    HarnessTooltip(effectiveReason ?: "选择模型", hovered) {
                        Row(Modifier.height(28.dp).hoverable(interactions)
                            .then(if (canChoose) Modifier.clickable(onClick = toggle) else Modifier)
                            .padding(horizontal = 7.dp).semantics {
                                testTag = "live-composer-model"; contentDescription = "选择模型"
                                stateDescription = effectiveReason ?: if (choices.isEmpty()) "模型列表不可用" else "选择本次新会话的模型"
                                if (!canChoose) disabled()
                            }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            HarnessText(selectedModel?.label ?: "Host 默认模型", Modifier.weight(1f, fill = false), maxLines = 1,
                                color = ink, style = MaterialTheme.typography.bodyMedium)
                            ConversationIcon(ConversationGlyph.Chevron, ConversationAppearance.tertiary, 12.dp)
                        }
                    }
                },
            )
        }
        ConversationActionButton(ConversationGlyph.Microphone, "听写（暂不可用）", size = 28.dp, tint = ink,
            modifier = Modifier.semantics { testTag = "live-composer-dictation" })
        Spacer(Modifier.width(3.dp))
        ComposerSubmitControls(state, canSend, onSubmit, onCancel, submitReason)
    }
}

private fun ConversationModelChoice.menuKey(): String =
    "conversation-model:${providerId.length}:$providerId:${modelId.length}:$modelId"

@Composable
private fun ComposerSubmitControls(state: ConversationState, canSend: Boolean, onSubmit: () -> Unit, onCancel: () -> Unit,
    submitReason: String?) {
    val stopping = state.canCancel || state.cancelling
    val voice = !stopping && state.draft.isBlank()
    val label = when { stopping -> if (state.cancelling) "正在停止…" else "停止"; voice -> "开启语音聊天（暂不可用）"; else -> submitReason ?: "发送" }
    val enabled = if (stopping) state.canCancel else !voice && canSend
    val action: (() -> Unit)? = if (!enabled) null else if (stopping) onCancel else onSubmit
    Box(Modifier.size(28.dp).background(ConversationAppearance.primary, CircleShape)) {
        ConversationActionButton(when { stopping -> ConversationGlyph.Stop; voice -> ConversationGlyph.Voice; else -> ConversationGlyph.Up },
            label, action, size = 28.dp, tint = Color.White,
            modifier = Modifier.semantics { testTag = if (stopping) "live-composer-stop" else "live-composer-send" })
    }
}

@Composable
private fun ConversationNotice(text: String, tag: String, isError: Boolean = false) {
    HarnessText(text, Modifier.semantics { testTag = tag; if (isError) error(text) },
        color = if (isError) HarnessTokens.Colors.Danger else HarnessTokens.Colors.TextMuted,
        style = MaterialTheme.typography.bodySmall)
}
