package im.hikaru.harness.client.panels

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import im.hikaru.harness.client.connection.*
import im.hikaru.harness.client.page.SettingsFormSelect
import im.hikaru.harness.ui.foundation.*

/** A real typed terminal page embedded in the existing terminal tab. */
@Composable
public fun PtyTerminalPanel(controller: PtyTerminalController, state: PtyTerminalState, modifier: Modifier = Modifier) {
    val clipboard = LocalClipboardManager.current
    val reading = remember(state.bindingEpoch, state.selectedKey) { PtyGridReading() }
    val bridge = remember(state.bindingEpoch, state.selectionEpoch, state.selectedKey) { TerminalTextInputBridge() }
    val requester = remember(bridge) { FocusRequester() }
    val menuScope = PtyTerminalMenuScope(state.bindingEpoch, state.selectionEpoch, state.selectedKey)
    // A new scope closes the popup synchronously; HarnessMenu also fences queued selections.
    var actionsExpanded by remember(menuScope) { mutableStateOf(false) }
    fun copy() {
        state.snapshot?.let { snapshot ->
            if (PtyGridModel.validate(snapshot) == null) PtyGridModel.copy(snapshot, reading.selection).takeIf { it.isNotEmpty() }
                ?.let { clipboard.setText(AnnotatedString(it)) }
        }
    }
    fun paste() {
        if (state.canInput) bridge.paste(clipboard.getText()?.text)?.let {
            controller.input((it as TerminalBridgeAction.Send).input)
        }
    }
    Column(modifier.fillMaxSize().semantics { testTag = "pty-terminal-panel" }) {
        val rows = (state.inventory as? PtyLoad.Ready)?.value.orEmpty()
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            // Measure the fixed icons first; only the selector gives up width in a narrow panel.
            Box(Modifier.weight(1f).clipToBounds()) {
                if (rows.isNotEmpty()) key(menuScope) {
                    SettingsFormSelect<TerminalKey?>(state.selectedKey, rows.map { it.key },
                        label = { key -> rows.firstOrNull { it.key == key }?.let {
                            val lifecycle = state.snapshot?.metadata?.takeIf { metadata ->
                                key == state.selectedKey && metadata.key == key
                            }?.lifecycle ?: it.lifecycle
                            "终端 " + (rows.indexOf(it) + 1) + " · " + when (lifecycle) {
                                TerminalLifecycle.Running -> "运行中"
                                is TerminalLifecycle.Exited -> "已退出 (${lifecycle.code})"
                                TerminalLifecycle.Unknown -> "未知"
                            }
                        } ?: "选择真实终端" },
                        enabled = true, testTag = "pty-terminal-select", onSelect = { it?.let(controller::select) })
                }
            }
            HarnessTooltipTrigger("新建终端", scope = menuScope, popupSuppressed = actionsExpanded) { trigger ->
                HarnessIconButton("新建终端", { controller.createTerminal() },
                    state = HarnessButtonState(enabled = state.canCreate && state.visible), modifier = trigger,
                    testTag = "pty-terminal-create") { HarnessIcon(HarnessIconName.Plus) }
            }
            HarnessMenu(
                expanded = actionsExpanded,
                entries = listOf(
                    HarnessMenuEntry.Item("refresh", "刷新终端", enabled = state.discovery !is PtyLoad.Loading, testTag = "pty-terminal-refresh"),
                    HarnessMenuEntry.Item("close", "关闭终端", enabled = state.canClose, destructive = true, testTag = "pty-terminal-close"),
                    HarnessMenuEntry.Item("copy", "复制选区", enabled = reading.selection != null, testTag = "pty-terminal-copy"),
                    HarnessMenuEntry.Item("paste", "粘贴", enabled = state.canInput, testTag = "pty-terminal-paste"),
                    HarnessMenuEntry.Item("bottom", "回到底部", enabled = !reading.follow, testTag = "pty-terminal-bottom"),
                ),
                onExpandedChange = { actionsExpanded = it },
                onSelect = { action ->
                    when (action) {
                        "refresh" -> controller.refresh()
                        "close" -> controller.closeTerminal()
                        "copy" -> copy()
                        "paste" -> paste()
                        "bottom" -> reading.bottom()
                    }
                },
                selectionScope = menuScope, align = HarnessPopoverAlign.End,
                popupModifier = Modifier.semantics { testTag = "pty-terminal-action-menu" },
                anchor = { toggle ->
                    HarnessTooltipTrigger("更多终端操作", scope = menuScope, popupSuppressed = actionsExpanded) { trigger ->
                        HarnessIconButton("更多终端操作", toggle,
                            state = HarnessButtonState(selected = actionsExpanded), modifier = trigger,
                            testTag = "pty-terminal-actions") { HarnessIcon(HarnessIconName.More) }
                    }
                },
            )
        }
        state.launchUnavailableReason?.let { PtyNotice(it, "pty-launch-unavailable") }
        if (state.createUncertain) PtyNotice("Unknown：创建回执未知，不能重试。刷新 inventory 后选择真实终端。", "pty-create-unknown")
        if (state.selectedKey in state.fencedKeys) PtyNotice("Unknown：输入和 resize 已停止。可显式申请关闭终端。", "pty-input-unknown")
        PtyLoadNotice(state.discovery, "pty-discovery")
        PtyLoadNotice(state.inventory, "pty-inventory")
        if (state.inventory is PtyLoad.Empty) PtyNotice("当前没有交互终端。点击新建终端才会启动 shell。", "pty-inventory-empty")
        if (rows.isNotEmpty() && state.selectedKey == null) PtyNotice("选择已有终端读取快照。", "pty-selection-empty")
        PtyLoadNotice(state.screen, "pty-screen")
        state.receipts.lastOrNull()?.takeUnless { it.outcome == PtyOutcome.Applied }?.let { receipt ->
            PtyNotice("${receipt.operation} · ${receipt.outcome}：${receipt.message}", "pty-terminal-receipt")
        }
        val snapshot = state.snapshot
        if (snapshot != null) {
            val incompatible = PtyGridModel.validate(snapshot)
            if (incompatible != null) PtyNotice(incompatible, "pty-snapshot-incompatible")
            else {
                (snapshot.metadata.lifecycle as? TerminalLifecycle.Exited)?.let {
                    PtyNotice("已退出 (${it.code})", "pty-terminal-exited")
                }
                TerminalTextInputHost(bridge, state.canInput, requester, onAction = { action ->
                    when (action) {
                        is TerminalBridgeAction.Send -> controller.input(action.input)
                        TerminalBridgeAction.Copy -> copy()
                        TerminalBridgeAction.Paste -> paste()
                    }
                }, modifier = Modifier.fillMaxWidth().weight(1f).padding(start = 16.dp, top = 8.dp, bottom = 12.dp)) { focused ->
                    PtyTerminalGrid(snapshot, reading, focused, state.canInput, { controller.viewport(it) },
                        { requester.requestFocus() }, Modifier.fillMaxSize())
                }
            }
        } else {
            // Measure the actual body even before creation so the create request has the viewport.
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).padding(start = 16.dp, top = 8.dp, bottom = 12.dp)) {
                PtyEmptyViewport(state.bindingEpoch, { controller.viewport(it) }, Modifier.fillMaxSize())
            }
        }
    }
}

private data class PtyTerminalMenuScope(val bindingEpoch: Long, val selectionEpoch: Long, val selectedKey: TerminalKey?)

@Composable
private fun PtyEmptyViewport(bindingEpoch: Long, onViewport: (TerminalSize) -> Unit, modifier: Modifier) {
    val metrics = rememberPtyTerminalMetrics()
    val density = LocalDensity.current
    var bodySize by remember { mutableStateOf(IntSize.Zero) }
    val viewport = metrics.viewport(bodySize)
    val currentViewport by rememberUpdatedState(onViewport)
    LaunchedEffect(bindingEpoch, viewport, metrics.style, density, metrics.cellWidth, metrics.rowHeight) {
        viewport?.let(currentViewport)
    }
    Box(modifier.padding(end = 10.dp).onSizeChanged { bodySize = it }.semantics { testTag = "pty-terminal-empty-viewport" })
}

@Composable
private fun PtyNotice(message: String, tag: String) {
    HarnessText(message, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)
        .semantics { testTag = tag }, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
}

@Composable
private fun PtyLoadNotice(load: PtyLoad<*>, tag: String) {
    when (load) {
        PtyLoad.Loading -> PtyNotice("加载中…", "$tag-loading")
        is PtyLoad.Error -> PtyNotice(load.reason, "$tag-error")
        is PtyLoad.Unavailable -> PtyNotice(load.reason, "$tag-unavailable")
        else -> Unit
    }
}
