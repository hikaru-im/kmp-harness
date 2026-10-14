package im.hikaru.harness.client.page

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import im.hikaru.harness.client.settings.*
import im.hikaru.harness.ui.foundation.*
import kotlinx.coroutines.launch

/** Only executable commands from the static registry appear here. */
@Composable
public fun SettingsShortcutsPage(repository: ShortcutsRepository) {
    val state by repository.state.collectAsState()
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    val pageFocus = remember { FocusRequester() }
    val searchFocus = remember { FocusRequester() }
    val saveFocus = remember { FocusRequester() }
    var restoreFocus by remember { mutableStateOf(searchFocus) }
    var captureWasActive by remember { mutableStateOf(false) }
    var editorWasActive by remember { mutableStateOf(false) }
    val standalone = LocalShortcutsEventRoot.current !== repository.input
    DisposableEffect(repository) {
        onDispose {
            repository.cancelCapture()
            repository.stopKeySearch()
            repository.cancelReset()
            if (standalone) repository.input.releaseAll()
        }
    }
    LaunchedEffect(state.capture, state.editor) {
        if (state.editor != null) editorWasActive = true
        if (state.capture != ShortcutCaptureMode.None) {
            captureWasActive = true
            pageFocus.requestFocus()
        } else if (captureWasActive) {
            captureWasActive = false
            if (state.editor != null) saveFocus.requestFocus() else {
                editorWasActive = false
                restoreFocus.requestFocus()
            }
        } else if (state.editor == null && editorWasActive) {
            editorWasActive = false
            restoreFocus.requestFocus()
        }
    }
    val visible = ShortcutRegistry.commands.filter { command ->
        val textMatches = query.isBlank() || (command.label + " " + command.description + " " + command.name).contains(query, ignoreCase = true)
        val keyMatches = state.keyQuery?.let { it in state.settings.bindings(command, state.platform) } ?: true
        textMatches && keyMatches
    }
    Column(Modifier.fillMaxSize().focusRequester(pageFocus)
        .onFocusChanged { if (standalone && !it.hasFocus) repository.input.releaseAll() }
        .onPreviewKeyEvent { standalone && repository.input.preview(it.toShortcutInput()) }
        .onKeyEvent { standalone && repository.input.dispatch(it.toShortcutInput()) }
        .focusable().semantics { testTag = "shortcuts-key-target" }) {
        SettingsFormFrame("键盘快捷键", "settings-shortcuts-title") {
            HarnessButton("重置所有快捷键", repository::requestReset, enabled = state.canEdit && state.editor == null,
                variant = HarnessButtonVariant.Secondary, testTag = "shortcuts-reset-all")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HarnessSearchField(query, { query = it }, "搜索命令", "搜索快捷键命令",
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.saving && state.editor == null && !state.resetConfirmation && state.capture == ShortcutCaptureMode.None,
                    testTag = "shortcuts-search")
                HarnessButton(if (state.capture == ShortcutCaptureMode.Search) "结束按键搜索" else "按键搜索", {
                    restoreFocus = searchFocus
                    if (state.capture == ShortcutCaptureMode.Search) repository.stopKeySearch() else repository.startKeySearch()
                }, modifier = Modifier.focusRequester(searchFocus),
                    enabled = state.platform != ShortcutPlatform.Unavailable && !state.saving && state.editor == null && !state.resetConfirmation,
                    variant = HarnessButtonVariant.Secondary, testTag = "shortcuts-key-search")
                if (state.capture == ShortcutCaptureMode.Search) {
                    HarnessText(state.keyQuery?.label(state.platform) ?: "按下一组组合键搜索；Escape 退出。",
                        Modifier.semantics { testTag = "shortcuts-key-query" }, color = HarnessTokens.Colors.TextMuted)
                }
                HarnessText("每个绑定只支持一组组合键，不支持多段序列。查找、剪贴板、常规格式与系统操作保留。",
                    color = HarnessTokens.Colors.TextMuted, style = MaterialTheme.typography.bodySmall)
            }
            if (state.loading) HarnessText("正在读取快捷键配置…", Modifier.semantics { testTag = "shortcuts-loading" })
            if (state.saving) HarnessText("正在保存快捷键配置…", Modifier.semantics { testTag = "shortcuts-saving" })
            state.unavailableReason?.let { HarnessText(it, Modifier.semantics { testTag = "shortcuts-unavailable" }, color = HarnessTokens.Colors.TextMuted) }
            if (!state.durable) HarnessText("快捷键仅在本次运行中保留。", color = HarnessTokens.Colors.TextMuted)
            (state.loadError ?: state.saveError ?: state.captureError)?.let { message ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ShortcutError(message, "shortcuts-error")
                    if (state.loadError != null || state.saveError != null) {
                        HarnessButton("重试", { scope.launch { repository.retry() } }, enabled = !state.loading && !state.saving,
                            variant = HarnessButtonVariant.Secondary, testTag = "shortcuts-retry")
                    }
                }
            }
            if (visible.isEmpty()) HarnessText("没有匹配的快捷键。", Modifier.semantics { testTag = "shortcuts-no-match" }, color = HarnessTokens.Colors.TextMuted)
            SettingsFormCard {
                visible.forEach { command -> key(command) {
                    val bindings = state.settings.bindings(command, state.platform)
                    val editor = state.editor?.takeIf { it.command == command }
                    val addFocus = remember(command) { FocusRequester() }
                    SettingsFormRow(command.label, command.description) {
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            HarnessText(if (command in state.settings.overrides) "自定义" else "默认",
                                color = HarnessTokens.Colors.TextMuted, style = MaterialTheme.typography.bodySmall)
                            if (bindings.isEmpty()) HarnessText("未分配快捷键", Modifier.semantics { testTag = "shortcut-empty-${command.name}" }, color = HarnessTokens.Colors.TextMuted)
                            bindings.forEachIndexed { index, chord ->
                                val bindingFocus = remember(command, index) { FocusRequester() }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    HarnessButton(chord.label(state.platform), {
                                        restoreFocus = bindingFocus
                                        repository.beginRecording(command, index)
                                    }, modifier = Modifier.weight(1f).focusRequester(bindingFocus),
                                        enabled = state.canEdit && state.editor == null, variant = HarnessButtonVariant.Secondary,
                                        size = HarnessControlSize.Compact, testTag = "shortcut-binding-${command.name}-$index")
                                    HarnessButton("移除", { scope.launch { repository.remove(command, index) } },
                                        enabled = state.canEdit && state.editor == null, variant = HarnessButtonVariant.Ghost,
                                        size = HarnessControlSize.Compact, testTag = "shortcut-remove-${command.name}-$index")
                                }
                            }
                            HarnessButton("添加快捷键", {
                                restoreFocus = addFocus
                                repository.beginRecording(command)
                            }, modifier = Modifier.focusRequester(addFocus),
                                enabled = state.canEdit && state.editor == null, variant = HarnessButtonVariant.Secondary,
                                size = HarnessControlSize.Compact, testTag = "shortcut-add-${command.name}")
                            if (command in state.settings.overrides) HarnessButton("恢复默认", { scope.launch { repository.reset(command) } },
                                enabled = state.canEdit && state.editor == null, variant = HarnessButtonVariant.Ghost,
                                size = HarnessControlSize.Compact, testTag = "shortcut-reset-${command.name}")
                            if (editor != null) {
                                HarnessText(editor.chord?.label(state.platform) ?: "按下一组组合键；Escape 取消。",
                                    Modifier.semantics { testTag = "shortcut-recording-${command.name}" })
                                editor.error?.let { ShortcutError(it, "shortcut-record-error-${command.name}") }
                                HarnessButton("重新录制", { repository.beginRecording(command, editor.replaceIndex) },
                                    enabled = state.canEdit, variant = HarnessButtonVariant.Secondary, testTag = "shortcut-rerecord")
                                HarnessButton("保存", { scope.launch { repository.saveEditor() } },
                                    modifier = Modifier.focusRequester(saveFocus),
                                    enabled = state.canEdit && editor.chord != null && editor.error == null, testTag = "shortcut-save")
                                HarnessButton("取消", repository::cancelCapture, enabled = !state.saving,
                                    variant = HarnessButtonVariant.Secondary, testTag = "shortcut-cancel")
                            }
                        }
                    }
                } }
            }
        }
    }
    HarnessConfirmDialog(
        visible = state.resetConfirmation, onDismissRequest = repository::cancelReset, title = "重置所有快捷键？",
        description = "删除所有自定义绑定和已移除状态，恢复这六个命令的默认组合。保存成功后生效。",
        cancelLabel = "取消", confirmLabel = "重置",
        submitState = when {
            state.saving -> HarnessConfirmSubmitState.Submitting
            state.saveError != null -> HarnessConfirmSubmitState.Failed(state.saveError!!)
            else -> HarnessConfirmSubmitState.Ready
        },
        onCancel = repository::cancelReset, onConfirm = { scope.launch { repository.resetAll() } },
        intent = HarnessConfirmIntent.Destructive, testTag = "shortcuts-reset-dialog",
    )
}

@Composable
private fun ShortcutError(message: String, tag: String) {
    HarnessText(message, Modifier.semantics { testTag = tag; error(message); liveRegion = LiveRegionMode.Polite },
        color = HarnessTokens.Colors.Danger, style = MaterialTheme.typography.bodySmall)
}
