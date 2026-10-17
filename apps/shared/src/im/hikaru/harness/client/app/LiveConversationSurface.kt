package im.hikaru.harness.client.app

import androidx.compose.foundation.BorderStroke
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
    Column(Modifier.fillMaxSize().padding(HarnessTokens.Spacing.Four).semantics { testTag = "live-thread" },
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
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ConversationNotice(issue.message, "live-thread-error-${issue.kind.name}", isError = true)
                if (issue.retry != null) {
                    // Execution retries belong to Send/Stop, never a generic history refresh.
                    when (issue.retry) {
                        ConversationRetry.History, ConversationRetry.Refresh -> HarnessButton(
                            "重试", onRetry, enabled = state.connected && state.historyPhase != ConversationDataPhase.Loading,
                            testTag = "live-thread-retry-${issue.kind.name}", size = HarnessControlSize.Compact,
                        )
                        ConversationRetry.SubmitExplicitly -> ConversationNotice(
                            "确认消息尚未发送后，可在输入框中重新发送。", "live-thread-submit-recovery",
                        )
                        ConversationRetry.Cancel -> ConversationNotice("可使用停止按钮重试。", "live-thread-stop-recovery")
                    }
                }
            }
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
    val configuredCap = LocalConversationEditorMaxHeight.current.coerceAtLeast(40.dp)
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
        val editorCap = if (viewport == null) configuredCap else minOf(configuredCap, (viewport * 0.25f).coerceAtLeast(40.dp))
        val compact = maxWidth < 640.dp
        Surface(
            Modifier.fillMaxWidth().semantics { testTag = "live-composer" },
            color = HarnessTokens.Colors.SurfaceElevated,
            contentColor = HarnessTokens.Colors.Text,
            shape = RoundedCornerShape(HarnessTokens.Control.ComposerRadius),
            // The reference rings the composer with a 1px 4% ink hairline, not a full border.
            border = BorderStroke(1.dp, HarnessTokens.Colors.SurfaceRing),
        ) {
            Column(Modifier.padding(HarnessTokens.Control.ComposerGutter), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BasicTextField(
                    value = editor,
                    onValueChange = { value ->
                        val previousText = editor.text
                        editor = value
                        if (value.text != previousText) onDraftChange(value.text)
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp, max = editorCap)
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
                        }
                        .semantics { testTag = "live-composer-editor"; contentDescription = "消息" },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = HarnessTokens.Colors.Text, lineHeight = 18.9.sp),
                    cursorBrush = SolidColor(HarnessTokens.Colors.Text),
                    singleLine = false,
                    decorationBox = { inner ->
                        Box {
                            if (editor.text.isEmpty()) HarnessText("描述你想构建的内容…", color = HarnessTokens.Colors.TextMuted)
                            inner()
                        }
                    },
                )
                ComposerFooter(state, models, selectedModel, onModelSelect, modelUnavailableReason, compact, canSend, onSubmit, onCancel)
                ConversationNotice("附件与权限调整暂不可用。", "live-composer-unsupported")
                val modelReason = modelUnavailableReason ?: when {
                    state.selectedSessionId != null -> "会话开始后不能更改模型。"
                    models.isEmpty() -> "模型列表不可用，使用 Host 默认模型。"
                    else -> null
                }
                modelReason?.let { ConversationNotice(it, "live-composer-model-unavailable") }
                when {
                    submitUnavailableReason != null -> ConversationNotice(submitUnavailableReason, "live-composer-submit-unavailable")
                    !state.connected -> ConversationNotice("连接 Host 后可发送消息。", "live-composer-disconnected")
                    state.cancelling -> ConversationNotice("正在停止…", "live-composer-action")
                    state.action != ConversationAction.Idle -> ConversationNotice(when (state.action) {
                        ConversationAction.Creating -> "正在创建会话…"
                        ConversationAction.Associating -> "正在确认项目关联…"
                        ConversationAction.Sending -> "正在发送…"
                        ConversationAction.WaitingForLocalIdle -> "正在运行…"
                        ConversationAction.RunningUnknown -> "消息已接收，运行状态尚未确认。"
                        ConversationAction.Idle -> ""
                    }, "live-composer-action")
                }
                // Home has no timeline slot: preserve create/prompt/cancel errors beside its draft.
                state.errors.values.filter { it.kind in listOf(ConversationErrorKind.Create, ConversationErrorKind.Association, ConversationErrorKind.Prompt,
                    ConversationErrorKind.Cancel, ConversationErrorKind.Completion) }.forEach {
                    ConversationNotice(it.message, "live-composer-error-${it.kind.name}", isError = true)
                }
            }
        }
    }
}

@Composable
private fun ComposerFooter(
    state: ConversationState, models: List<ConversationModelChoice>, selectedModel: ConversationModelChoice?,
    onModelSelect: (ConversationModelChoice) -> Unit, modelReason: String?, compact: Boolean,
    canSend: Boolean, onSubmit: () -> Unit, onCancel: () -> Unit,
) {
    var modelMenuOpen by remember { mutableStateOf(false) }
    val choices = models.distinctBy { it.providerId to it.modelId }
    val effectiveReason = modelReason ?: if (state.selectedSessionId != null) "会话开始后不能更改模型。" else null
    val canChoose = effectiveReason == null && choices.isNotEmpty() && state.action == ConversationAction.Idle && !state.cancelling
    val controls: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            UnsupportedComposerControl("+", "添加附件", "附件暂不可用", "live-composer-attachment")
            UnsupportedComposerControl("权限", "权限", "权限调整暂不可用", "live-composer-permissions")
            Box(Modifier.weight(1f)) {
                HarnessMenu(
                    expanded = modelMenuOpen && canChoose,
                    entries = choices.map { choice ->
                        HarnessMenuEntry.Item(key = choice.menuKey(), text = choice.label,
                            selected = choice.providerId == selectedModel?.providerId && choice.modelId == selectedModel?.modelId)
                    },
                    onExpandedChange = { modelMenuOpen = it && canChoose },
                    onSelect = { key ->
                        if (canChoose) choices.firstOrNull { it.menuKey() == key }?.let(onModelSelect)
                    },
                    anchor = { toggle ->
                        androidx.compose.material3.TextButton(onClick = toggle, enabled = canChoose,
                            modifier = Modifier.fillMaxWidth().semantics {
                                testTag = "live-composer-model"
                                contentDescription = "选择模型"
                                stateDescription = effectiveReason ?: if (choices.isEmpty()) "模型列表不可用" else "选择本次新会话的模型"
                            }) {
                            HarnessText(selectedModel?.label ?: "Host 默认模型", maxLines = 1,
                                color = if (canChoose) HarnessTokens.Colors.Text else HarnessTokens.Colors.TextDisabled)
                        }
                    },
                )
            }
            if (!compact) ComposerSubmitControls(state, canSend, onSubmit, onCancel)
        }
    }
    if (compact) {
        Column(Modifier.fillMaxWidth().semantics { testTag = "live-composer-footer-compact" }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            controls()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                ComposerSubmitControls(state, canSend, onSubmit, onCancel)
            }
        }
    } else Row(Modifier.fillMaxWidth().semantics { testTag = "live-composer-footer-wide" }) { controls() }
}

private fun ConversationModelChoice.menuKey(): String =
    "conversation-model:${providerId.length}:$providerId:${modelId.length}:$modelId"

/** Disabled semantics without a pretend click action or empty callback. */
@Composable
private fun UnsupportedComposerControl(label: String, description: String, reason: String, tag: String) {
    Box(Modifier.heightIn(min = 36.dp).padding(horizontal = 6.dp).semantics {
        testTag = tag; role = Role.Button; disabled(); contentDescription = description; stateDescription = reason
    }, contentAlignment = Alignment.Center) { HarnessText(label, color = HarnessTokens.Colors.TextDisabled) }
}

@Composable
private fun ComposerSubmitControls(state: ConversationState, canSend: Boolean, onSubmit: () -> Unit, onCancel: () -> Unit) {
    if (state.canCancel || state.cancelling) HarnessButton(
        if (state.cancelling) "停止中" else "停止", { if (state.canCancel) onCancel() },
        enabled = state.canCancel, testTag = "live-composer-stop", size = HarnessControlSize.Compact,
        variant = HarnessButtonVariant.Secondary,
    )
    HarnessButton("发送", { if (canSend) onSubmit() }, enabled = canSend, testTag = "live-composer-send",
        size = HarnessControlSize.Compact)
}

@Composable
private fun ConversationNotice(text: String, tag: String, isError: Boolean = false) {
    HarnessText(text, Modifier.semantics { testTag = tag; if (isError) error(text) },
        color = if (isError) HarnessTokens.Colors.Danger else HarnessTokens.Colors.TextMuted,
        style = MaterialTheme.typography.bodySmall)
}
