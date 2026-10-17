package im.hikaru.harness.client.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.focusable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.*
import im.hikaru.harness.client.conversation.ConversationAction
import im.hikaru.harness.client.conversation.ConversationState
import im.hikaru.harness.ui.foundation.*

@Composable
internal fun ConversationComposerToolbar(
    state: ConversationState, models: List<ConversationModelChoice>, selectedModel: ConversationModelChoice?,
    onModelSelect: (ConversationModelChoice) -> Unit, modelReason: String?,
    canSend: Boolean, onSubmit: () -> Unit, onCancel: () -> Unit,
    selectedEffort: String?, onEffortSelect: ((String?) -> Unit)?, effortKnown: Boolean,
    hostDefault: Boolean, permission: ComposerPermission, onPermissionSelect: (ComposerPermission) -> Unit,
    onConfigureModels: (() -> Unit)?,
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
    LaunchedEffect(canChoose) { if (!canChoose && popup == ComposerPopup.Models) popup = ComposerPopup.Reasoning }
    Row(Modifier.fillMaxWidth().semantics { testTag = "live-composer-footer" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HarnessTokens.Composer.ControlGap)) {
        HarnessTooltipTrigger("附件暂不可用") { tip ->
            Box(tip.size(HarnessTokens.Composer.ActionSize).semantics {
                testTag = "live-composer-attachment"; role = Role.Button; disabled()
                contentDescription = "添加附件"; stateDescription = "附件暂不可用"
            }, contentAlignment = Alignment.Center) { HarnessIcon(HarnessIconName.Plus, tint = HarnessTokens.Colors.TextDisabled) }
        }
        Box(Modifier.weight(1f)) {
            HarnessPopover(expanded = popup == ComposerPopup.Permissions, onDismissRequest = close,
                onExpandedChange = { popup = if (it) ComposerPopup.Permissions else ComposerPopup.Closed },
                side = HarnessPopoverSide.Top, popupWidth = HarnessTokens.Composer.PermissionPopupWidth,
                popupModifier = panelKeys.semantics { testTag = "live-composer-permission-popup" },
                anchor = { toggle -> HarnessComposerButton(permission.label, toggle,
                    tint = if (permission == ComposerPermission.FullAccess) HarnessTokens.Composer.FullAccessColor else HarnessTokens.Colors.TextMuted,
                    testTag = "live-composer-permissions") }) {
                ComposerPermission.entries.forEach { option ->
                    HarnessComposerChoice(option.label, option.description, permission == option,
                        onClick = { onPermissionSelect(option); close() },
                        icon = when (option) { ComposerPermission.Ask -> HarnessIconName.User; ComposerPermission.Automatic -> HarnessIconName.Check; ComposerPermission.FullAccess -> HarnessIconName.Warning },
                        tint = if (option == ComposerPermission.FullAccess) HarnessTokens.Composer.FullAccessColor else HarnessTokens.Colors.Text,
                        testTag = "live-composer-permission-${option.name}")
                }
                HarnessText("仅保存界面选择，执行权限仍由 Host 管理。", Modifier.padding(HarnessTokens.Composer.PopupInset),
                    style = MaterialTheme.typography.bodySmall, color = HarnessTokens.Colors.TextMuted)
            }
        }
        Box(Modifier.weight(2f), contentAlignment = Alignment.CenterEnd) {
            HarnessPopover(expanded = popup == ComposerPopup.Reasoning || popup == ComposerPopup.Models,
                onDismissRequest = close,
                onExpandedChange = { popup = if (!it) ComposerPopup.Closed else if (selectedModel == null && canChoose) ComposerPopup.Models else ComposerPopup.Reasoning },
                side = HarnessPopoverSide.Top, align = HarnessPopoverAlign.End,
                popupWidth = HarnessTokens.Composer.PopupWidth, popupMaxHeight = HarnessTokens.Composer.PopupMaxHeight,
                popupModifier = panelKeys.semantics { testTag = "live-composer-model-popup" },
                anchor = { toggle -> HarnessComposerButton(
                    if (selectedModel != null && effortKnown && efforts.isNotEmpty()) "$modelLabel · $strength" else modelLabel,
                    toggle, trailing = HarnessIconName.ChevronDown, testTag = "live-composer-model") }) {
                if (popup == ComposerPopup.Models) {
                    HarnessComposerButton("返回 · 选择模型", { popup = ComposerPopup.Reasoning },
                        trailing = HarnessIconName.ChevronLeft,
                        testTag = "live-composer-model-back")
                    choices.forEach { choice ->
                        HarnessComposerChoice(choice.label, selected = choice.providerId == selectedModel?.providerId && choice.modelId == selectedModel?.modelId,
                            onClick = { if (canChoose) { onModelSelect(choice); popup = ComposerPopup.Reasoning } },
                            testTag = "menu:${choice.menuKey()}")
                    }
                } else {
                    Column(Modifier.padding(HarnessTokens.Composer.PopupInset), verticalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Two)) {
                        HarnessComposerButton("$modelLabel · $strength", { if (canChoose) popup = ComposerPopup.Models },
                            enabled = canChoose, trailing = if (canChoose) HarnessIconName.ChevronRight else null,
                            testTag = "live-composer-model-select")
                        val values = listOf<String?>(null) + efforts
                        HarnessDiscreteSlider(if (effortKnown) values.map(::effortLabel) else listOf("未知"),
                            if (effortKnown) values.indexOf(selectedEffort) else 0,
                            onSelect = { if (canAdjust) onEffortSelect?.invoke(values[it]) }, description = "思考强度",
                            enabled = canAdjust, testTag = "live-composer-effort-slider")
                        val reason = when {
                            !idle -> "任务执行期间不能调整模型配置。"
                            readOnlyReason != null -> readOnlyReason
                            selectedModel == null -> "请先选择模型。"
                            efforts.isEmpty() -> "此模型未提供可调整的推理档位。"
                            else -> null
                        }
                        if (reason != null) HarnessText(reason, style = MaterialTheme.typography.bodySmall, color = HarnessTokens.Colors.TextMuted)
                        if (selectedModel == null && choices.isEmpty() && onConfigureModels != null)
                            HarnessButton("配置模型", { close(); onConfigureModels() }, variant = HarnessButtonVariant.Ghost,
                                testTag = "live-composer-popup-configure-models")
                    }
                }
            }
        }
        val action = state.composerPrimaryAction()
        val stop = action == ComposerPrimaryAction.Stop || action == ComposerPrimaryAction.Stopping
        val busy = action == ComposerPrimaryAction.Busy || action == ComposerPrimaryAction.Stopping
        val label = when (action) { ComposerPrimaryAction.Send -> "发送"; ComposerPrimaryAction.Busy -> "正在处理"; ComposerPrimaryAction.Stop -> "停止"; ComposerPrimaryAction.Stopping -> "正在停止" }
        HarnessTooltipTrigger(if (action == ComposerPrimaryAction.Send) "发送 · Enter；换行 · Shift+Enter" else label) { tip ->
            Box(tip) { HarnessComposerAction(label, if (stop) HarnessIconName.Stop else HarnessIconName.Send,
                enabled = if (stop) state.canCancel else canSend, loading = busy,
                onClick = { if (stop && state.canCancel) onCancel() else if (!busy && canSend) onSubmit() },
                testTag = if (stop) "live-composer-stop" else if (busy) "live-composer-busy" else "live-composer-send") }
        }
    }
}
