package im.hikaru.harness.client.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import im.hikaru.harness.client.conversation.ConversationAction
import im.hikaru.harness.client.conversation.ConversationState
import im.hikaru.harness.ui.foundation.*
import kotlin.math.roundToInt

@Composable
internal fun ConversationComposerToolbar(
    state: ConversationState, models: List<ConversationModelChoice>, selectedModel: ConversationModelChoice?,
    onModelSelect: (ConversationModelChoice) -> Unit, modelReason: String?,
    canSend: Boolean, onSubmit: () -> Unit, onCancel: () -> Unit,
    selectedEffort: String?, onEffortSelect: ((String?) -> Unit)?, effortKnown: Boolean,
    hostDefault: Boolean, permission: ComposerPermission, onPermissionSelect: (ComposerPermission) -> Unit,
    onConfigureModels: (() -> Unit)?, draftEmpty: Boolean,
) {
    var popup by remember(state.host, state.selectedSessionId) { mutableStateOf(ComposerPopup.Closed) }
    val choices = models.distinctBy { it.providerId to it.modelId }
    val idle = state.action == ConversationAction.Idle && !state.cancelling
    val readOnlyReason = modelReason ?: if (state.selectedSessionId != null) "此会话沿用创建时的模型和推理强度。" else null
    val canChoose = readOnlyReason == null && choices.isNotEmpty() && idle
    val efforts = selectedModel?.reasoningEfforts.orEmpty().filter(String::isNotBlank).distinct()
    val canAdjust = canChoose && efforts.isNotEmpty() && onEffortSelect != null
    val modelLabel = selectedModel?.label ?: when {
        state.selectedSessionId != null -> "会话模型未知"
        hostDefault -> "Host 默认模型"
        else -> "选择模型"
    }
    val strength = if (effortKnown) effortLabel(selectedEffort) else "强度未知"
    val close = { popup = ComposerPopup.Closed }
    val focus = LocalFocusManager.current
    val first = remember { FocusRequester() }
    val panelKeys = Modifier.focusRequester(first).focusable().onPreviewKeyEvent {
        if (it.type != KeyEventType.KeyDown) false else when (it.key) {
            Key.Escape -> { close(); true }
            Key.DirectionDown -> { focus.moveFocus(FocusDirection.Next); true }
            Key.DirectionUp -> { focus.moveFocus(FocusDirection.Previous); true }
            else -> false
        }
    }
    LaunchedEffect(popup) { if (popup != ComposerPopup.Closed) first.requestFocus() }
    // Live `_ComposerLayoutFooter`: `0 8px`, left cluster 5px apart, the model chip flush against the
    // dictation slot, then an 8px margin before the 28px primary disc.
    Row(Modifier.fillMaxWidth().padding(horizontal = HarnessTokens.Composer.FooterPaddingX)
        .semantics { testTag = "live-composer-footer" },
        verticalAlignment = Alignment.CenterVertically) {
        // Live plus glyph is `text-default` white inside a 28px transparent slot.
        HarnessTooltipTrigger("附件暂不可用") { tip ->
            Box(tip.size(HarnessTokens.Composer.ActionSize).semantics {
                testTag = "live-composer-attachment"; role = Role.Button; disabled()
                contentDescription = "添加附件"; stateDescription = "附件暂不可用"
            }, contentAlignment = Alignment.Center) {
                ConversationIcon(ConversationGlyph.Plus, ConversationAppearance.ink, 16.dp)
            }
        }
        Spacer(Modifier.width(HarnessTokens.Composer.ControlGap))
        HarnessPopover(expanded = popup == ComposerPopup.Permissions, onDismissRequest = close,
            onExpandedChange = { popup = if (it) ComposerPopup.Permissions else ComposerPopup.Closed },
            side = HarnessPopoverSide.Top, popupWidth = HarnessTokens.Composer.PermissionPopupWidth,
            // Live popup: 320 wide, radius 20, 4 inset, 0.5px 8.2% ring, `0 8px 16px -4px` shadow.
            popupShape = HarnessTokens.Composer.PopupRadius,
            popupColor = ConversationAppearance.popup,
            popupBorder = ConversationAppearance.popupRing,
            popupBorderWidth = 0.5.dp,
            popupShadow = HarnessTokens.Composer.PopupShadow,
            popupModifier = panelKeys.padding(HarnessTokens.Composer.PopupContainerInset)
                .semantics { testTag = "live-composer-permission-popup" },
            // The live chip is a 28px pill: 6px inset, a 16px leading icon, then 13/18 text. Full access
            // renders in the warning foreground rgb(255,133,73) on both the icon and the label.
            anchor = { toggle -> HarnessComposerButton(
                permission.chipLabel(), toggle,
                leadingArtwork = { ConversationIcon(permission.glyph(), permissionChipTint(permission), 16.dp) },
                tint = permissionChipTint(permission),
                testTag = "live-composer-permissions") }) {
            // Live menu header: 26 high, `5px 8px`, 13/16 description ink, an underlined link at the right.
            Row(Modifier.fillMaxWidth().heightIn(min = 24.dp).padding(horizontal = 8.dp, vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically) {
                HarnessText("应如何批准 Codex 操作？", Modifier.weight(1f),
                    color = ConversationAppearance.tertiary,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 16.sp))
                HarnessTooltipTrigger("文档暂不可用") { tip ->
                    HarnessText("了解更多", tip.semantics {
                        testTag = "live-composer-permission-learn-more"; role = Role.Button; disabled()
                        contentDescription = "了解更多"; stateDescription = "文档暂不可用"
                    }, color = ConversationAppearance.tertiary,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 16.sp,
                            textDecoration = TextDecoration.Underline))
                }
            }
            ComposerPermission.entries.forEach { option ->
                val tint = if (option == ComposerPermission.FullAccess) HarnessTokens.Composer.FullAccessColor
                    else ConversationAppearance.ink
                HarnessComposerChoice(option.label, option.description, permission == option,
                    onClick = { onPermissionSelect(option); close() },
                    modifier = Modifier.height(HarnessTokens.Composer.PermissionItemHeight),
                    tint = tint,
                    descriptionTint = if (option == ComposerPermission.FullAccess) tint else ConversationAppearance.tertiary,
                    artwork = { ConversationIcon(option.glyph(), tint, 18.dp) },
                    selectedArtwork = { ConversationIcon(ConversationGlyph.Check, tint, 16.dp) },
                    testTag = "live-composer-permission-${option.name}")
            }
        }
        Spacer(Modifier.weight(1f))
        HarnessPopover(expanded = popup == ComposerPopup.Reasoning || popup == ComposerPopup.Models,
            onDismissRequest = close,
            onExpandedChange = { open ->
                popup = when {
                    !open -> ComposerPopup.Closed
                    // Live opens straight on the model list when the current model offers fewer
                    // than two reasoning options: the effort view is filtered out entirely
                    // (`powerSelections.length >= 2`), with no explanatory placeholder. This is a
                    // property of the model, so read-only threads land on the same view.
                    selectedModel != null && efforts.size < 2 -> ComposerPopup.Models
                    else -> ComposerPopup.Reasoning
                }
            },
            // Live menu left edge (870.17) equals the chip left edge; the 254px menu grows leftward.
            side = HarnessPopoverSide.Top, align = HarnessPopoverAlign.Start,
            popupWidth = HarnessTokens.Composer.ModelPopupWidth, popupMaxHeight = HarnessTokens.Composer.PopupMaxHeight,
            popupShape = HarnessTokens.Composer.PopupRadius,
            popupColor = ConversationAppearance.popup,
            popupBorder = ConversationAppearance.popupRing,
            popupBorderWidth = 0.5.dp,
            popupShadow = HarnessTokens.Composer.PopupShadow,
            popupModifier = panelKeys.padding(HarnessTokens.Composer.PopupContainerInset)
                .semantics { testTag = "live-composer-model-popup" },
            anchor = { toggle -> ModelTriggerChip(modelLabel, strength, selectedModel != null && effortKnown && efforts.isNotEmpty(),
                tint = ConversationAppearance.tertiary, open = popup == ComposerPopup.Reasoning,
                onClick = toggle, testTag = "live-composer-model") }) {
            if (popup == ComposerPopup.Models) {
                // Live list: a 26px `选择模型` section label over 235×28.6 radio rows inset 4 from the
                // menu edge (8 inside the row), with a 17px trailing check. The live menu reserves an
                // 11px scrollbar gutter, so rows stop 15px short of the menu's right edge.
                Column(Modifier.fillMaxWidth().padding(end = 11.dp)) {
                    HarnessText("选择模型",
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 5.dp),
                        color = ConversationAppearance.tertiary,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 16.sp))
                    choices.forEach { choice ->
                        HarnessComposerChoice(choice.label, selected = choice.providerId == selectedModel?.providerId && choice.modelId == selectedModel?.modelId,
                            onClick = {
                                if (canChoose) {
                                    onModelSelect(choice)
                                    // Codex closes the picker after choosing a model whose effort
                                    // list is shorter than the slider needs; nothing replaces it.
                                    popup = if (choice.reasoningEfforts.filter(String::isNotBlank)
                                            .distinct().size >= 2) ComposerPopup.Reasoning else ComposerPopup.Closed
                                }
                            },
                            modifier = Modifier.height(HarnessTokens.Composer.ModelItemHeight),
                            selectedArtwork = { ConversationIcon(ConversationGlyph.Check, ConversationAppearance.ink, 17.dp) },
                            testTag = "menu:${choice.menuKey()}")
                    }
                }
            } else {
                // Live simple view: a 48px header — a 32px slot at each edge and a content-sized
                // chip centred between them — over a 40px slider row. The left slot holds Codex's
                // fast-mode toggle; harness has no service tiers, so the slot stays empty.
                Row(Modifier.fillMaxWidth().height(HarnessTokens.Composer.ModelChipHeight),
                    verticalAlignment = Alignment.Top) {
                    Spacer(Modifier.width(HarnessTokens.Composer.ModelChipInset))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                        ModelChip(modelLabel, strength, showStrength = selectedModel != null && effortKnown && efforts.isNotEmpty(),
                            maximum = selectedEffort == "max",
                            enabled = canChoose, onClick = { popup = ComposerPopup.Models },
                            testTag = "live-composer-model-select")
                    }
                    val canReset = canAdjust && selectedEffort != null
                    HarnessTooltipTrigger("重置为默认") { tip ->
                        Box(tip.size(HarnessTokens.Composer.ModelResetSize)
                            .clickable(enabled = canReset) { onEffortSelect?.invoke(null) }
                            .semantics {
                                testTag = "live-composer-effort-reset"; role = Role.Button
                                contentDescription = "重置为默认"
                                if (!canReset) disabled()
                            }, contentAlignment = Alignment.Center) {
                            ConversationIcon(ConversationGlyph.Reset,
                                if (canReset) ConversationAppearance.tertiary else ConversationAppearance.tertiary.copy(alpha = 0.4f),
                                16.dp)
                        }
                    }
                }
                // The live slider has exactly one stop per effort; harness has no model default
                // effort, so an unset effort parks the knob at the first stop (the reset icon
                // clears it again) instead of adding a `默认` pseudo-stop the reference lacks.
                val values = efforts
                val selectedIndex = values.indexOf(selectedEffort)
                Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(HarnessTokens.Composer.EffortRowHeight)) {
                    ComposerEffortSlider(values.map(::effortLabel),
                        if (effortKnown && selectedIndex >= 0) selectedIndex else 0,
                        enabled = canAdjust,
                        onSelect = { if (canAdjust) onEffortSelect?.invoke(values[it]) })
                }
                val reason = when {
                    !idle -> "任务执行期间不能调整模型配置。"
                    readOnlyReason != null -> readOnlyReason
                    selectedModel == null -> "请先选择模型。"
                    else -> null
                }
                if (reason != null) HarnessText(reason, Modifier.padding(horizontal = HarnessTokens.Composer.PopupItemInsetX),
                    style = MaterialTheme.typography.bodySmall, color = ConversationAppearance.tertiary)
                if (selectedModel == null && choices.isEmpty() && onConfigureModels != null)
                    Box(Modifier.padding(horizontal = HarnessTokens.Composer.PopupContainerInset,
                        vertical = HarnessTokens.Composer.PopupContainerInset)) {
                        HarnessButton("配置模型", { close(); onConfigureModels() }, variant = HarnessButtonVariant.Ghost,
                            testTag = "live-composer-popup-configure-models")
                    }
            }
        }
        // Live dictation slot: 28px, white 16px microphone over a transparent disc, flush to the chip.
        HarnessTooltipTrigger("听写暂不可用") { tip ->
            Box(tip.size(HarnessTokens.Composer.ActionSize).semantics {
                testTag = "live-composer-dictation"; role = Role.Button; disabled()
                contentDescription = "听写"; stateDescription = "听写暂不可用"
            }, contentAlignment = Alignment.Center) {
                ConversationIcon(ConversationGlyph.Microphone, ConversationAppearance.ink, 16.dp)
            }
        }
        Spacer(Modifier.width(8.dp))
        val action = state.composerPrimaryAction()
        val stop = action == ComposerPrimaryAction.Stop || action == ComposerPrimaryAction.Stopping
        val busy = action == ComposerPrimaryAction.Busy || action == ComposerPrimaryAction.Stopping
        // The empty live composer draws its voice glyph on a full primary disc instead of a send arrow.
        val voice = action == ComposerPrimaryAction.Send && draftEmpty
        val label = when (action) {
            ComposerPrimaryAction.Send -> if (voice) "语音聊天" else "发送"
            ComposerPrimaryAction.Busy -> "正在处理"
            ComposerPrimaryAction.Stop -> "停止"
            ComposerPrimaryAction.Stopping -> "正在停止"
        }
        HarnessTooltipTrigger(if (voice) "语音聊天暂不可用" else if (stop) label else "发送 · Enter；换行 · Shift+Enter") { tip ->
            Box(tip) {
                HarnessComposerAction(label,
                    enabled = if (stop) state.canCancel else if (voice) false else canSend, loading = busy,
                    onClick = { if (stop && state.canCancel) onCancel() else if (!busy && !voice && canSend) onSubmit() },
                    testTag = if (stop) "live-composer-stop" else if (busy) "live-composer-busy" else "live-composer-send",
                    // Measured primary: #2C67C5 in dark, #3A83F7 in light, white glyph.
                    containerColor = ConversationAppearance.primary, contentColor = Color.White,
                    forceActive = voice,
                    content = { if (!busy) ConversationIcon(
                        when { stop -> ConversationGlyph.Stop; voice -> ConversationGlyph.Voice; else -> ConversationGlyph.SendArrow },
                        Color.White,
                        16.dp) })
            }
        }
    }
}

/** Footer trigger: model name in ink, strength in tertiary, then the 16px disclosure chevron. */
@Composable
private fun ModelTriggerChip(
    modelLabel: String, strength: String, showStrength: Boolean,
    tint: Color, open: Boolean, onClick: () -> Unit, testTag: String,
) {
    HarnessComposerButton(modelLabel, onClick, tint = tint, testTag = testTag, active = open,
        contentPaddingX = 8.dp,
        labelContent = {
            val contentModifier = Modifier.weight(1f, fill = false)
            // Live `composer.modelPicker.selectEffort.label`: an open strength panel replaces the
            // model + strength text with a centred `选择强度` while keeping the trigger's width.
            if (open) Box(contentModifier) {
                Box(Modifier.alpha(0f)) { ModelTriggerLabel(modelLabel, strength, showStrength, tint) }
                HarnessText("选择强度", Modifier.align(Alignment.Center), maxLines = 1, color = tint,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = HarnessTokens.Composer.LabelSize,
                        lineHeight = HarnessTokens.Composer.LabelLineHeight))
            } else ModelTriggerLabel(modelLabel, strength, showStrength, tint, contentModifier)
        },
        trailingArtwork = { ConversationIcon(ConversationGlyph.ModelChevron, tint, 16.dp) })
}

@Composable
private fun ModelTriggerLabel(
    modelLabel: String, strength: String, showStrength: Boolean, tint: Color,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HarnessTokens.Composer.ControlGap)) {
        HarnessText(modelLabel, Modifier.weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = ConversationAppearance.ink,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = HarnessTokens.Composer.LabelSize,
                lineHeight = HarnessTokens.Composer.LabelLineHeight))
        if (showStrength) HarnessText(strength, maxLines = 1, color = tint,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = HarnessTokens.Composer.LabelSize,
                lineHeight = HarnessTokens.Composer.LabelLineHeight))
    }
}

/**
 * Popup header chip: strength (14/20 primary) centred over the truncated 12/16 model name and a
 * 12px chevron. Live `_ViewToggle`: content-sized (110×40 inside a 4px inset), radius 8, and the
 * surface only appears on hover — the row centres the chip between its 32px edge slots.
 */
@Composable
private fun ModelChip(
    modelLabel: String, strength: String, showStrength: Boolean, maximum: Boolean,
    enabled: Boolean, onClick: () -> Unit, testTag: String,
) {
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    Column(Modifier.height(HarnessTokens.Composer.ModelChipHeight)
        .background(if (hovered && enabled) ConversationAppearance.ink.copy(alpha = 0.08f) else Color.Transparent,
            RoundedCornerShape(HarnessTokens.Composer.ModelChipRadius))
        .clickable(interactionSource = interactions, indication = null, enabled = enabled, onClick = onClick)
        .semantics { this.testTag = testTag; role = Role.Button; if (!enabled) disabled() }
        .padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)) {
        if (showStrength) HarnessText(strength,
            color = if (maximum) ConversationAppearance.effortMaximum else ConversationAppearance.effort,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp))
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HarnessTokens.Composer.ControlGap)) {
            HarnessText(modelLabel, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = ConversationAppearance.popupModel,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp, lineHeight = 16.sp))
            ConversationIcon(ConversationGlyph.ChevronRightSmall, ConversationAppearance.tertiary, 12.dp)
        }
    }
}

/**
 * Live effort slider (`_Container_xwb5v_1`): a 242×32 slot inset 2/6 holding a 230×24 track (8 from
 * the menu edge), one 4px tick per stop inset by the knob radius, a 28px white knob, and a fill that
 * runs from the track start to the knob centre. Semantics keep the discrete 0..n-1 value so
 * `SetProgress` selects the same index.
 */
@Composable
private fun ComposerEffortSlider(labels: List<String>, index: Int, enabled: Boolean, onSelect: (Int) -> Unit) {
    val count = labels.size
    val fraction = if (count > 1) index.toFloat() / (count - 1) else 0f
    val active = if (enabled) ConversationAppearance.effort else ConversationAppearance.effort.copy(alpha = 0.4f)
    val trackColor = if (enabled) ConversationAppearance.sliderTrack else ConversationAppearance.sliderTrack.copy(alpha = 0.5f)
    val knob = if (enabled) Color.White else Color.White.copy(alpha = 0.5f)
    // Live ticks: 30% white for the stops already reached, 25% white for the rest.
    val reachedTick = Color.White.copy(alpha = if (enabled) 0.3f else 0.15f)
    val upcomingTick = Color.White.copy(alpha = if (enabled) 0.25f else 0.13f)
    Canvas(Modifier.fillMaxWidth().height(HarnessTokens.Composer.EffortRowHeight)
        .pointerInput(enabled, count) {
            if (!enabled || count < 2) return@pointerInput
            val insetPx = HarnessTokens.Composer.EffortTrackInset.toPx()
            val knobPx = HarnessTokens.Composer.EffortKnobSize.toPx()
            detectTapGestures { offset -> seek(offset.x, size.width, insetPx, knobPx, count, labels.size, onSelect) }
        }
        .pointerInput(enabled, count) {
            if (!enabled || count < 2) return@pointerInput
            val insetPx = HarnessTokens.Composer.EffortTrackInset.toPx()
            val knobPx = HarnessTokens.Composer.EffortKnobSize.toPx()
            detectHorizontalDragGestures(onDragStart = { offset ->
                seek(offset.x, size.width, insetPx, knobPx, count, labels.size, onSelect)
            }) { change, _ ->
                seek(change.position.x, size.width, insetPx, knobPx, count, labels.size, onSelect)
            }
        }
        .semantics {
            testTag = "live-composer-effort-slider"
            contentDescription = "思考强度"
            stateDescription = labels.getOrNull(index) ?: "不可用"
            progressBarRangeInfo = ProgressBarRangeInfo(index.toFloat(),
                0f..(count - 1).coerceAtLeast(1).toFloat(), (count - 2).coerceAtLeast(0))
            if (enabled && count > 1) setProgress { target ->
                onSelect(target.roundToInt().coerceIn(labels.indices)); true
            }
        }) {
        val inset = HarnessTokens.Composer.EffortTrackInset.toPx()
        val trackHeight = HarnessTokens.Composer.EffortTrackHeight.toPx()
        val knobRadius = HarnessTokens.Composer.EffortKnobSize.toPx() / 2
        val trackLeft = inset
        val trackRight = (size.width - inset).coerceAtLeast(trackLeft)
        val trackWidth = trackRight - trackLeft
        val top = (size.height - trackHeight) / 2f
        val radius = CornerRadius(trackHeight / 2)
        // Thumps stop one knob radius inside the track so the disc never overhangs the capsule.
        val stopLeft = trackLeft + knobRadius
        val stopRight = (trackRight - knobRadius).coerceAtLeast(stopLeft)
        val x = stopLeft + (stopRight - stopLeft) * fraction
        drawRoundRect(trackColor, Offset(trackLeft, top), Size(trackWidth, trackHeight), radius)
        if (fraction > 0f) drawRoundRect(active, Offset(trackLeft, top), Size(x - trackLeft, trackHeight), radius)
        val tickRadius = HarnessTokens.Composer.EffortTickSize.toPx() / 2
        for (i in 0 until count) {
            val tx = if (count > 1) stopLeft + (stopRight - stopLeft) * i / (count - 1) else (stopLeft + stopRight) / 2
            drawCircle(if (i <= index) reachedTick else upcomingTick, tickRadius, Offset(tx, size.height / 2))
        }
        drawCircle(knob, knobRadius, Offset(x, size.height / 2))
    }
}

/** Map a pointer x (px) to the nearest of [count] stops across the knob-inset track. */
private fun seek(x: Float, width: Int, inset: Float, knobSize: Float, count: Int, labelCount: Int, onSelect: (Int) -> Unit) {
    val knobRadius = knobSize / 2
    val left = inset + knobRadius
    val right = (width - inset - knobRadius).coerceAtLeast(left)
    val usable = right - left
    if (usable <= 0f || count < 2) return
    val ratio = ((x - left) / usable).coerceIn(0f, 1f)
    onSelect((ratio * (count - 1)).roundToInt().coerceIn(0, labelCount - 1))
}

@Composable
private fun permissionChipTint(permission: ComposerPermission): Color =
    if (permission == ComposerPermission.FullAccess) HarnessTokens.Composer.FullAccessColor else ConversationAppearance.tertiary

private fun ComposerPermission.glyph(): ConversationGlyph = when (this) {
    ComposerPermission.Ask -> ConversationGlyph.Hand
    ComposerPermission.Automatic -> ConversationGlyph.ShieldCheck
    ComposerPermission.FullAccess -> ConversationGlyph.ShieldAlert
    ComposerPermission.Custom -> ConversationGlyph.Permissions
}

/**
 * Trigger labels are shorter than menu labels: the live chip renders `完全访问` for `完全访问权限`.
 * Custom is inferred from the same pattern (`自定义 (config.toml)` -> `自定义`).
 */
private fun ComposerPermission.chipLabel(): String = when (this) {
    ComposerPermission.FullAccess -> "完全访问"
    ComposerPermission.Custom -> "自定义"
    else -> label
}
