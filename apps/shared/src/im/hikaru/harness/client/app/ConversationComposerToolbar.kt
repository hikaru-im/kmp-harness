package im.hikaru.harness.client.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.drawscope.Stroke
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
    permission: ComposerPermission, onPermissionSelect: (ComposerPermission) -> Unit,
    draftEmpty: Boolean,
    selectedReasoning: im.hikaru.harness.client.connection.ReasoningOptions? = null,
    onManageModels: (() -> Unit)? = null,
) {
    var popupStack by remember(state.host, state.selectedSessionId) { mutableStateOf(emptyList<ComposerPopup>()) }
    val popup = popupStack.lastOrNull() ?: ComposerPopup.Closed
    val choices = models.distinctBy { it.providerId to it.modelId }
    val idle = state.action == ConversationAction.Idle && !state.cancelling
    val canChoose = choices.isNotEmpty() && idle
    val triggerEnabled = idle
    val efforts = selectedModel?.allowedReasoningEfforts(selectedReasoning).orEmpty()
    val selectedAvailable = choices.any { it.providerId == selectedModel?.providerId && it.modelId == selectedModel?.modelId }
    val canAdjust = canChoose && selectedAvailable && efforts.isNotEmpty() && onEffortSelect != null
    val modelLabel = selectedModel?.label ?: "选择模型"
    val strength = effortLabel(selectedEffort)
    val close = { popupStack = emptyList() }
    val back = { popupStack = popupStack.dropLast(1) }
    val focus = LocalFocusManager.current
    val first = remember { FocusRequester() }
    val panelKeys = Modifier.focusRequester(first).focusable().onPreviewKeyEvent {
        if (it.type != KeyEventType.KeyDown) false else when (it.key) {
            Key.Escape -> { back(); true }
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
            onExpandedChange = { popupStack = if (it) listOf(ComposerPopup.Permissions) else emptyList() },
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
                popupStack = if (open) listOf(ComposerPopup.Reasoning) else emptyList()
            },
            // Live Radix popper: `data-side="top"`, `data-align="end"` against the trigger, but the
            // rendered menu is centred on it — trigger 141.7 wide, menu 254 wide, centres 1.1 apart.
            // Both views keep their bottom edge 9px above the trigger; the list grows upward.
            side = HarnessPopoverSide.Top, align = HarnessPopoverAlign.Center,
            sideOffset = HarnessTokens.Composer.ModelPopupOffset,
            popupWidth = HarnessTokens.Composer.ModelPopupWidth, popupMaxHeight = HarnessTokens.Composer.PopupMaxHeight,
            popupScrollable = false, constrainToAnchorSpace = true,
            popupShape = HarnessTokens.Composer.PopupRadius,
            popupColor = ConversationAppearance.popup,
            popupBorder = ConversationAppearance.popupRing,
            popupBorderWidth = 0.5.dp,
            popupShadow = HarnessTokens.Composer.PopupShadow,
            popupModifier = panelKeys.padding(
                start = if (popup == ComposerPopup.Models) 0.dp else HarnessTokens.Composer.PopupContainerInset,
                top = HarnessTokens.Composer.PopupContainerInset,
                end = if (popup == ComposerPopup.Models) 0.dp else HarnessTokens.Composer.PopupContainerInset,
                bottom = if (popup == ComposerPopup.Models) 0.dp else HarnessTokens.Composer.PopupContainerInset,
            ).semantics { testTag = "live-composer-model-popup" },
            anchor = { toggle -> ModelTriggerChip(modelLabel, strength, selectedModel != null && effortKnown && efforts.isNotEmpty(),
                tint = ConversationAppearance.tertiary,
                openLabel = when (popup) {
                    ComposerPopup.Models -> "选择模型"
                    ComposerPopup.Reasoning -> "选择强度"
                    else -> null
                },
                enabled = triggerEnabled, onClick = toggle, testTag = "live-composer-model") }) {
            if (popup == ComposerPopup.Models) {
                // Model rows scroll together with an 11px scrollbar gutter and a 17px trailing check.
                val modelScroll = rememberScrollState()
                Box(Modifier.fillMaxWidth().heightIn(max = HarnessTokens.Composer.ModelListMaxHeight)) {
                    Column(Modifier.fillMaxWidth()
                        .verticalScroll(modelScroll)
                        .padding(start = 4.dp, end = 15.dp, bottom = HarnessTokens.Composer.PopupContainerInset)) {
                        if (choices.isEmpty()) {
                            HarnessText(modelReason ?: "暂无可选模型，请在模型管理中开启模型。",
                                Modifier.padding(8.dp), color = ConversationAppearance.tertiary, style = MaterialTheme.typography.bodySmall)
                        }
                        if (choices.isEmpty() && onManageModels != null) HarnessButton("前往模型管理",
                            { close(); onManageModels() }, variant = HarnessButtonVariant.Ghost, testTag = "live-composer-manage-models")
                        choices.forEach { choice ->

                            HarnessComposerChoice(choice.label, selected = choice.providerId == selectedModel?.providerId && choice.modelId == selectedModel?.modelId,
                                onClick = {
                                    if (canChoose) {
                                        onModelSelect(choice)
                                        back()
                                    }
                                },
                                modifier = Modifier.height(HarnessTokens.Composer.ModelItemHeight)
                                    .background(ConversationAppearance.ink.copy(alpha =
                                        if (choice.providerId == selectedModel?.providerId && choice.modelId == selectedModel?.modelId) 0.14f else 0.035f),
                                        RoundedCornerShape(6.dp)),
                                selectedArtwork = { ConversationIcon(ConversationGlyph.Check, ConversationAppearance.ink, 17.dp) },
                                testTag = "menu:${choice.menuKey()}")
                        }
                    }
                    ModelListScrollbar(modelScroll, Modifier.matchParentSize())
                }
            } else {
                // Explicit model selections stack effort over model. The controls row grows with
                // that content; 36px is only its minimum. Reserve the reference's 32px edge slots.
                Row(Modifier.fillMaxWidth().heightIn(min = HarnessTokens.Composer.ModelChipRowHeight),
                    verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(HarnessTokens.Composer.ModelChipInset))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        ModelChip(modelLabel, strength, showStrength = selectedModel != null && effortKnown && efforts.isNotEmpty(),
                            maximum = selectedEffort == "max" || selectedEffort == "ultra",
                            enabled = idle, onClick = { popupStack = popupStack + ComposerPopup.Models },
                            testTag = "live-composer-model-select")
                    }
                    // Codex's reset restores its recommended model selection. Harness has no
                    // equivalent default option, so there is no reset action in this slot.
                    Spacer(Modifier.width(HarnessTokens.Composer.ModelResetSize))
                }
                val values = listOf<String?>(null) + efforts
                val selectedIndex = values.indexOf(selectedEffort).coerceAtLeast(0)
                if (efforts.isNotEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(HarnessTokens.Composer.EffortRowHeight)) {
                        ComposerEffortSlider(values.map { if (it == null) "跟随厂商" else effortLabel(it) },
                            selectedIndex, enabled = canAdjust,
                            onSelect = { if (canAdjust) onEffortSelect?.invoke(values[it]) })
                    }
                } else HarnessText(if (selectedModel == null) "请先选择模型" else "此模型未提供可调整的思考强度，跟随厂商。",
                    Modifier.padding(8.dp), color = ConversationAppearance.tertiary, style = MaterialTheme.typography.bodySmall)

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
                    containerColor = ConversationAppearance.primary, contentColor = ConversationAppearance.onPrimary,
                    forceActive = voice,
                    content = { if (!busy) ConversationIcon(
                        when { stop -> ConversationGlyph.Stop; voice -> ConversationGlyph.Voice; else -> ConversationGlyph.SendArrow },
                        ConversationAppearance.onPrimary,
                        16.dp) })
            }
        }
    }
}

@Composable
private fun ModelListScrollbar(scroll: ScrollState, modifier: Modifier = Modifier) {
    val thumbColor = ConversationAppearance.scrollbar
    Box(modifier) {
        if (scroll.maxValue > 0 && scroll.viewportSize > 0) {
            Canvas(Modifier.align(Alignment.CenterEnd).width(11.dp).fillMaxHeight().padding(vertical = 4.dp)
                .semantics { testTag = "live-composer-model-scrollbar" }
                .pointerInput(scroll) {
                    detectTapGestures { position ->
                        val thumb = (size.height.toFloat() * scroll.viewportSize /
                            (scroll.viewportSize + scroll.maxValue)).coerceAtLeast(24.dp.toPx()).coerceAtMost(size.height.toFloat())
                        val travel = size.height - thumb
                        if (travel > 0f) scroll.dispatchRawDelta(
                            ((position.y - thumb / 2) / travel).coerceIn(0f, 1f) * scroll.maxValue - scroll.value)
                    }
                }
                .pointerInput(scroll) {
                    detectVerticalDragGestures { change, delta ->
                        val thumb = (size.height.toFloat() * scroll.viewportSize /
                            (scroll.viewportSize + scroll.maxValue)).coerceAtLeast(24.dp.toPx()).coerceAtMost(size.height.toFloat())
                        val travel = size.height - thumb
                        if (travel > 0f) scroll.dispatchRawDelta(delta / travel * scroll.maxValue)
                        change.consume()
                    }
                }) {
                val thumb = (size.height * scroll.viewportSize / (scroll.viewportSize + scroll.maxValue))
                    .coerceAtLeast(24.dp.toPx()).coerceAtMost(size.height)
                val top = (size.height - thumb) * scroll.value / scroll.maxValue
                val width = 6.dp.toPx()
                drawRoundRect(thumbColor, Offset((size.width - width) / 2, top), Size(width, thumb), CornerRadius(width / 2))
            }
        }
    }
}

/** Footer trigger: model name in ink, strength in tertiary, then the 16px disclosure chevron. */
@Composable
private fun ModelTriggerChip(
    modelLabel: String, strength: String, showStrength: Boolean,
    tint: Color, openLabel: String?, enabled: Boolean, onClick: () -> Unit, testTag: String,
) {
    HarnessComposerButton(modelLabel, onClick, enabled = enabled, tint = tint, testTag = testTag, active = openLabel != null,
        contentPaddingX = 8.dp,
        labelContent = {
            val contentModifier = Modifier.weight(1f, fill = false)
            // Keep the trigger width while its text follows the active model/effort view.
            if (openLabel != null) Box(contentModifier) {
                Box(Modifier.alpha(0f)) { ModelTriggerLabel(modelLabel, strength, showStrength, tint) }
                HarnessText(openLabel, Modifier.align(Alignment.Center), maxLines = 1, color = tint,
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

/** Explicit model chip: 20px effort over a 16px model row, with 4px outer and 2px inner vertical insets. */
@Composable
private fun ModelChip(
    modelLabel: String, strength: String, showStrength: Boolean, maximum: Boolean,
    enabled: Boolean, onClick: () -> Unit, testTag: String,
) {
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    Column(Modifier.heightIn(min = HarnessTokens.Composer.ModelChipHeight)
        .clickable(interactionSource = interactions, indication = null, enabled = enabled, onClick = onClick)
        .semantics { this.testTag = testTag; role = Role.Button; if (!enabled) disabled() }
        .padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.background(
            if (hovered && enabled) ConversationAppearance.ink.copy(alpha = 0.08f) else Color.Transparent,
            RoundedCornerShape(6.dp),
        ).padding(horizontal = 4.dp, vertical = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (showStrength) HarnessText(strength, Modifier.height(20.dp), maxLines = 1,
                color = if (maximum) ConversationAppearance.effortMaximum else ConversationAppearance.effort,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium))
            Row(Modifier.height(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(16.dp))
                HarnessText(modelLabel, Modifier.weight(1f, fill = false), maxLines = 1,
                    overflow = TextOverflow.Ellipsis, color = ConversationAppearance.popupModel,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp, lineHeight = 16.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Normal))
                Box(Modifier.width(16.dp), contentAlignment = Alignment.CenterStart) {
                    ConversationIcon(ConversationGlyph.ChevronRightSmall, ConversationAppearance.tertiary, 12.dp)
                }
            }
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
    // Reached stops sit on the blue fill; upcoming stops follow the theme's foreground.
    val reachedTick = Color.White.copy(alpha = if (enabled) 0.3f else 0.15f)
    val upcomingTick = if (enabled) ConversationAppearance.sliderTick else ConversationAppearance.sliderTick.copy(alpha = 0.13f)
    val thumbBorder = ConversationAppearance.ringStrong
    val trackBorder = ConversationAppearance.popupRing
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
        val borderWidth = 0.5.dp.toPx()
        drawRoundRect(trackBorder, Offset(trackLeft + borderWidth / 2, top + borderWidth / 2),
            Size((trackWidth - borderWidth).coerceAtLeast(0f), trackHeight - borderWidth),
            CornerRadius((trackHeight - borderWidth) / 2), style = Stroke(borderWidth))
        val centre = Offset(x, size.height / 2)
        // Approximate the reference's 0 0 2px black shadow with a soft outer ring.
        for (spread in 4 downTo 1) {
            drawCircle(Color.Black.copy(alpha = if (enabled) 0.02f else 0.01f),
                knobRadius + spread * 0.5.dp.toPx(), centre)
        }
        drawCircle(knob, knobRadius, centre)
        drawCircle(thumbBorder, knobRadius - borderWidth / 2, centre, style = Stroke(borderWidth))
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
