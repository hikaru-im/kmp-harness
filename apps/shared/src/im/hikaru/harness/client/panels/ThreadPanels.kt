package im.hikaru.harness.client.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import im.hikaru.harness.client.connection.BackgroundProcessLifecycle
import im.hikaru.harness.client.connection.BackgroundInputState
import im.hikaru.harness.client.page.SettingsFormSelect
import im.hikaru.harness.client.app.MessageCard
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import kotlinx.coroutines.withTimeoutOrNull
import im.hikaru.harness.ui.foundation.HarnessButton
import im.hikaru.harness.ui.foundation.HarnessButtonVariant
import im.hikaru.harness.ui.foundation.HarnessControlSize
import im.hikaru.harness.ui.foundation.HarnessIconButton
import im.hikaru.harness.ui.foundation.HarnessTab
import im.hikaru.harness.ui.foundation.HarnessTabs
import im.hikaru.harness.ui.foundation.HarnessTabsState
import im.hikaru.harness.ui.foundation.HarnessText
import im.hikaru.harness.ui.foundation.HarnessTextField
import im.hikaru.harness.ui.foundation.HarnessTokens

/**
 * Host supplies bounded space, theme, state and a real action consumer (normally state.reduce).
 * No IO is started by composition. Width policy is exposed separately by ThreadPanelLayout.
 * A child interaction slot must own real prompt/cancel consumers; it is never mounted without
 * current list AND detail canInteract. Omit it when interaction is unavailable.
 */
@Composable
public fun ThreadPanels(
    state: ThreadPanelsState,
    onAction: (ThreadPanelAction) -> Unit,
    modifier: Modifier = Modifier,
    childInteraction: (@Composable (ChildAgentDetail) -> Unit)? = null,
    onRefreshOverview: (() -> Unit)? = null,
    onRefreshDetail: (() -> Unit)? = null,
    onRefreshTerminal: (() -> Unit)? = null,
    onTerminalInput: ((BackgroundTerminalKey, String) -> Unit)? = null,
    onTerminalEof: ((BackgroundTerminalKey) -> Unit)? = null,
    onTerminalCancel: ((BackgroundTerminalKey) -> Unit)? = null,
    ptyController: PtyTerminalController? = null,
    ptyState: PtyTerminalState? = null,
) {
    var interactive by remember(state.scope, state.ownerGeneration,
        ThreadPanelTab.BackgroundOutput in state.openTabs) { mutableStateOf(false) }
    val ptyVisible = interactive && state.selectedTab == ThreadPanelTab.BackgroundOutput &&
        ThreadPanelTab.BackgroundOutput in state.openTabs && ptyState != null
    LaunchedEffect(ptyController, ptyVisible) { ptyController?.setVisible(ptyVisible) }
    DisposableEffect(ptyController) { onDispose { ptyController?.setVisible(false) } }
    val draftKey = state.selectedTerminalKey ?: (state.terminal as? PanelLoad.Ready)?.value?.key
    val draft = remember(state.scope, state.ownerGeneration, draftKey) { TerminalDraft() }
    // Reading identity: scope + owner incarnation + the exact process key. terminalGeneration and
    // revision are deliberately excluded so a same-key refresh keeps the reading position. The tab
    // flag resets the position on close/reopen while a Subagents tab switch still preserves it.
    val reading = remember(state.scope, state.ownerGeneration, draftKey,
        ThreadPanelTab.BackgroundOutput in state.openTabs) { TerminalReadingState() }
    val receipt = state.terminalControls?.takeIf { it.key == draftKey }?.inputReceipt
    LaunchedEffect(draft, receipt) {
        if (receipt != null && receipt > draft.observedReceipt) {
            draft.text = ""
            draft.observedReceipt = receipt
        }
    }
    Column(modifier.fillMaxSize().background(HarnessTokens.Colors.Surface)) {
        HarnessTabs(
            state = HarnessTabsState(state.openTabs.map {
                HarnessTab(it.name, it.title, closable = true)
            }, state.selectedTab?.name),
            onStateChange = { next ->
                val closed = state.openTabs.firstOrNull { tab -> next.tabs.none { it.key == tab.name } }
                if (closed != null) onAction(ThreadPanelAction.CloseTab(state.scope, closed))
                else ThreadPanelTab.entries.firstOrNull { it.name == next.effectiveSelectedKey }?.let {
                    onAction(ThreadPanelAction.SelectTab(state.scope, it))
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = "会话面板",
            closeLabel = { "关闭" + it.title },
        )
        Box(Modifier.fillMaxWidth().weight(1f)) {
            when (state.selectedTab) {
                ThreadPanelTab.BackgroundOutput -> Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        HarnessButton("后台输出", { interactive = false },
                            modifier = Modifier.semantics { testTag = "terminal-view-background" },
                            variant = if (!interactive) HarnessButtonVariant.Secondary else HarnessButtonVariant.Ghost,
                            size = HarnessControlSize.Compact)
                        HarnessButton("交互终端", { interactive = true },
                            modifier = Modifier.semantics { testTag = "terminal-view-interactive" },
                            variant = if (interactive) HarnessButtonVariant.Secondary else HarnessButtonVariant.Ghost,
                            size = HarnessControlSize.Compact)
                    }
                    if (interactive) {
                        if (ptyController != null && ptyState != null) PtyTerminalPanel(ptyController, ptyState, Modifier.weight(1f))
                        else PanelMessage("交互终端不可用", "当前连接没有终端服务。")
                    } else {
                    if (state.terminalInventory.isNotEmpty()) Box(Modifier.fillMaxWidth().padding(12.dp)) {
                        SettingsFormSelect<BackgroundTerminalKey?>(
                            value = state.selectedTerminalKey,
                            options = state.terminalInventory.map { it.key },
                            label = { selected ->
                                state.terminalInventory.firstOrNull { it.key == selected }?.let {
                                    (it.command?.takeIf(String::isNotBlank) ?: it.key.processId) + " · " + it.lifecycle.label
                                } ?: "所选进程不可用"
                            },
                            enabled = true, testTag = "terminal-process-select",
                            onSelect = { selected -> selected?.let { onAction(ThreadPanelAction.SelectTerminal(it)) } },
                        )
                    }
                    if (onRefreshTerminal != null) HarnessButton(
                        if (state.terminal is PanelLoad.Error) "重试输出" else "刷新输出", onRefreshTerminal,
                        enabled = state.terminal !is PanelLoad.Loading,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        variant = HarnessButtonVariant.Ghost, size = HarnessControlSize.Compact,
                    )
                    BackgroundOutputPanel(state.terminal, Modifier.fillMaxWidth().weight(1f), reading)
                    (state.terminal as? PanelLoad.Ready)?.value?.let { output ->
                        TerminalProcessControls(output, state.terminalControls?.takeIf { it.key == output.key },
                            draft.text, { draft.text = it }, onTerminalInput, onTerminalEof, onTerminalCancel)
                    }
                    }
                }
                ThreadPanelTab.Subagents -> Column(Modifier.fillMaxSize()) {
                    val refresh = if (state.selectedChild == null) onRefreshOverview else onRefreshDetail
                    if (refresh != null) HarnessButton(
                        if (state.selectedChild == null) "刷新子智能体" else "刷新历史", refresh,
                        enabled = if (state.selectedChild == null) state.subagents !is PanelLoad.Loading && !state.overviewRefreshing
                        else state.detail !is PanelLoad.Loading &&
                            state.selectedSummary?.historyAvailability == ChildHistoryAvailability.Available,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        variant = HarnessButtonVariant.Ghost, size = HarnessControlSize.Compact,
                    )
                    SubagentsPanel(state, onAction, Modifier.fillMaxWidth().weight(1f), childInteraction)
                }
                null -> PanelMessage("面板已关闭", "通过会话面板入口重新打开。")
            }
        }
    }
}

private class TerminalDraft {
    var text by mutableStateOf("")
    var observedReceipt: Long = 0
}

/**
 * Visible panel strings. Where Codex has a zh-CN key the wording matches it: `backgroundTerminalTab.title`
 * (后台终端) / `.noOutput` (暂无输出), `backgroundTasks.title.*` (后台进程 / 子智能体),
 * `subagents.panel.title|back|active|done|noActive` and `subagents.status.*`. The read-only and
 * load-state lines (只读输出, 正在加载输出, 面板已关闭, …) are Harness projections with no Codex key;
 * the batch contract records that gap.
 */
private val BackgroundProcessLifecycle.label: String
    get() = when (this) {
        BackgroundProcessLifecycle.Running -> "运行中"
        is BackgroundProcessLifecycle.Exited -> "已退出 · 代码 " + exitCode
        BackgroundProcessLifecycle.Unknown -> "状态未知"
    }

private val ThreadPanelTab.title: String
    get() = when (this) {
        ThreadPanelTab.BackgroundOutput -> "后台终端"
        ThreadPanelTab.Subagents -> "子智能体"
    }

/** Explicit pipe operations. Sending a line appends LF; EOF permanently closes stdin. */
@Composable
private fun TerminalProcessControls(
    output: BackgroundTerminalOutput, controls: TerminalControls?,
    draft: String, onDraftChange: (String) -> Unit,
    onInput: ((BackgroundTerminalKey, String) -> Unit)?,
    onEof: ((BackgroundTerminalKey) -> Unit)?,
    onCancel: ((BackgroundTerminalKey) -> Unit)?,
) {
    if (onInput == null && onEof == null && onCancel == null) return
    val pending = controls?.pending == true
    val canInput = !pending && controls?.inputBlocked != true && controls?.cancelBlocked != true &&
        output.inputState == BackgroundInputState.Open && output.lifecycle == BackgroundProcessLifecycle.Running
    val canCancel = !pending && controls?.cancelBlocked != true && output.lifecycle !is BackgroundProcessLifecycle.Exited
    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HarnessText(when (output.inputState) {
            BackgroundInputState.Unsupported -> "此进程未启用 stdin 输入。"
            BackgroundInputState.Open -> "发送一行会追加换行；EOF 永久关闭输入。"
            BackgroundInputState.Closed -> "stdin 已关闭。"
            BackgroundInputState.Unknown -> "输入状态未知，请勿重发。"
        }, style = MaterialTheme.typography.bodySmall, color = HarnessTokens.Colors.TextMuted)
        if (onInput != null && output.inputState != BackgroundInputState.Unsupported) {
            HarnessTextField(draft, onDraftChange, "进程输入", modifier = Modifier.fillMaxWidth(),
                enabled = canInput, testTag = "terminal-input")
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (onInput != null && output.inputState != BackgroundInputState.Unsupported) HarnessButton(
                "发送一行", { onInput(output.key, draft + "\n") }, enabled = canInput,
                size = HarnessControlSize.Compact,
            )
            if (onEof != null && output.inputState != BackgroundInputState.Unsupported) HarnessButton(
                "EOF", { onEof(output.key) }, enabled = canInput,
                variant = HarnessButtonVariant.Secondary, size = HarnessControlSize.Compact,
            )
        }
        if (onCancel != null) HarnessButton(
            "取消进程", { onCancel(output.key) }, enabled = canCancel,
            variant = HarnessButtonVariant.Destructive, size = HarnessControlSize.Compact,
        )
        if (pending) HarnessText("操作处理中…", style = MaterialTheme.typography.bodySmall)
        controls?.message?.let { HarnessText(it, style = MaterialTheme.typography.bodySmall) }
    }
}

/** Read-only plain text projection, not an interactive PTY or an ANSI terminal emulator. */
@Composable
public fun BackgroundOutputPanel(load: PanelLoad<BackgroundTerminalOutput>, modifier: Modifier = Modifier) {
    BackgroundOutputPanel(load, modifier, remember((load as? PanelLoad.Ready)?.value?.key) { TerminalReadingState() })
}

/** Owner-provided reading state; kept internal so the public panel surface stays unchanged. */
@Composable
internal fun BackgroundOutputPanel(
    load: PanelLoad<BackgroundTerminalOutput>,
    modifier: Modifier,
    reader: TerminalReadingState,
) {
    Box(modifier) {
        when (load) {
            is PanelLoad.Unavailable -> PanelMessage("终端不可用", load.serviceDescription ?: load.reason.description)
            is PanelLoad.Empty -> PanelMessage("没有后台进程", "当前会话没有已注册的后台进程。打开面板不会创建进程。")
            is PanelLoad.Loading -> PanelMessage("正在加载输出…")
            is PanelLoad.Error -> PanelMessage("无法加载输出", load.message, error = true)
            is PanelLoad.Ready -> {
                val output = load.value
                // The available viewport participates in the reader signature, so a same-identity
                // resize re-runs the clamp/follow decision without changing process identity.
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val signature = TerminalReaderSignature(output.key, output.buffer, output.truncated,
                        output.command, output.cwd, output.lifecycle, maxWidth, maxHeight)
                    // Captured during composition, before the new content is measured: the reader's
                    // position, range and input generation from before this change. The decision never
                    // uses the post-append maxValue, so a former bottom is not mistaken for upward
                    // reading. Each signature owns its own completed-measure signal.
                    val baseline = remember(reader, signature) { TerminalReaderBaseline(
                        reader.vertical.value, reader.vertical.maxValue, reader.upwardInputVersion, !reader.measured) }
                    val contentMeasure = remember(reader, signature) { TerminalContentMeasure() }
                    // After content/identity/resize changes: wait for the real layout, then either
                    // follow the new tail or keep the previous pixel offset clamped to the new range.
                    // A first display has no measured range yet (ScrollState.maxValue is Int.MAX_VALUE),
                    // so it is explicitly treated as "at the bottom" and shows the tail.
                    LaunchedEffect(reader, signature) {
                        val firstDisplay = baseline.firstDisplay
                        val startedAtBottom = firstDisplay || (reader.followOutput &&
                            baseline.value >= baseline.max - TerminalScrollTolerance)
                        if (!awaitContentLayout(contentMeasure)) return@LaunchedEffect
                        // The wait spans the whole pending window; nothing scrolls the axis until it
                        // ends. User input is recorded independently of layout, even if a shrinking
                        // range subsequently clamps it. A system clamp alone is the baseline offset
                        // limited to the new range; a smaller offset also proves upward movement.
                        // Later appends re-evaluate from their own
                        // pre-change baseline and stay put while the reader is above the bottom.
                        val clampedBaseline = baseline.value.coerceIn(0, reader.vertical.maxValue)
                        val userScrolledUp = reader.upwardInputVersion != baseline.upwardInputVersion ||
                            (!firstDisplay && reader.vertical.value < clampedBaseline - TerminalScrollTolerance)
                        if (userScrolledUp) reader.followOutput = false
                        if (startedAtBottom && !userScrolledUp) reader.vertical.scrollTo(reader.vertical.maxValue)
                        else reader.vertical.scrollTo(reader.vertical.value.coerceIn(0, reader.vertical.maxValue))
                        reader.horizontal.scrollTo(reader.horizontal.value.coerceIn(0, reader.horizontal.maxValue))
                    }
                    Column(Modifier.fillMaxSize()
                        .nestedScroll(reader.inputObserver)
                        // This modifier measures the scroll node itself. Its child has finished
                        // measuring (including maxValue) before placement marks THIS signature ready.
                        // Replacing the signature also replaces this layout lambda and forces measure.
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            layout(placeable.width, placeable.height) {
                                placeable.placeRelative(0, 0)
                                contentMeasure.completed = true
                                reader.measured = true
                            }
                        }
                        .verticalScroll(reader.vertical)
                        .semantics { testTag = "terminal-output" }
                        .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        HarnessText("只读输出 · 纯文本", style = MaterialTheme.typography.bodySmall,
                            color = HarnessTokens.Colors.TextMuted)
                        HarnessText(when (val lifecycle = output.lifecycle) {
                            BackgroundProcessLifecycle.Running -> "运行中"
                            is BackgroundProcessLifecycle.Exited -> "已退出 · 代码 ${lifecycle.exitCode}"
                            BackgroundProcessLifecycle.Unknown -> "状态未知"
                        }, style = MaterialTheme.typography.bodySmall, color = HarnessTokens.Colors.TextMuted)
                        output.command?.takeIf { it.isNotBlank() }?.let { HarnessText(it, maxLines = 2, style = panelTextStyle()) }
                        output.cwd?.takeIf { it.isNotBlank() }?.let { HarnessText(it, maxLines = 2,
                            style = MaterialTheme.typography.bodySmall, color = HarnessTokens.Colors.TextMuted) }
                        if (output.truncated) HarnessText("输出缓冲区已被来源截断", color = HarnessTokens.Colors.TextMuted,
                            style = MaterialTheme.typography.bodySmall)
                        if (output.buffer.isEmpty()) HarnessText("暂无输出", style = codeTextStyle())
                        else SelectionContainer {
                            HarnessText(output.buffer,
                                modifier = Modifier.horizontalScroll(reader.horizontal)
                                    .semantics { testTag = "terminal-output-text" },
                                style = codeTextStyle())
                        }
                    }
                }
            }
        }
    }
}

@Composable
public fun SubagentsPanel(
    state: ThreadPanelsState,
    onAction: (ThreadPanelAction) -> Unit,
    modifier: Modifier = Modifier,
    childInteraction: (@Composable (ChildAgentDetail) -> Unit)? = null,
) {
    Box(modifier) {
        if (state.selectedChild != null) {
            ChildDetailPanel(state, onAction, childInteraction)
        } else when (val load = state.subagents) {
            is PanelLoad.Unavailable -> PanelMessage("子智能体不可用", load.reason.description)
            is PanelLoad.Loading -> PanelMessage("正在加载子智能体…")
            is PanelLoad.Empty -> PanelMessage("没有子智能体")
            is PanelLoad.Error -> PanelMessage("无法加载子智能体", load.message, error = true)
            is PanelLoad.Ready -> Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 20.dp),
            ) {
                if (state.overviewRefreshing) {
                    HarnessText("正在刷新子智能体…", color = HarnessTokens.Colors.TextMuted, style = panelTextStyle())
                    Spacer(Modifier.height(8.dp))
                }
                if (load.value.completeness == SubagentOverviewCompleteness.LiveOnlyIncomplete) {
                    HarnessText("仅显示当前可读取的 live 子会话；历史或已卸载子会话可能未包含。",
                        color = HarnessTokens.Colors.TextMuted, style = panelTextStyle())
                    Spacer(Modifier.height(8.dp))
                }
                if (load.value.children.isEmpty()) {
                    HarnessText(if (load.value.completeness == SubagentOverviewCompleteness.Complete)
                        "没有子智能体" else "没有可读取的 live 子智能体", style = panelTextStyle())
                } else {
                    SubagentSectionView(state, SubagentSection.Active, onAction)
                    if (load.value.children(SubagentSection.Done).isNotEmpty()) {
                        Spacer(Modifier.height(24.dp))
                        SubagentSectionView(state, SubagentSection.Done, onAction)
                    }
                    if (load.value.unknown.isNotEmpty()) {
                        Spacer(Modifier.height(24.dp))
                        SectionHeading("状态未知 (" + load.value.unknown.size + ")")
                        ChildRows(load.value.unknown, onAction)
                    }
                }
            }
        }
    }
}

@Composable
private fun SubagentSectionView(state: ThreadPanelsState, section: SubagentSection, onAction: (ThreadPanelAction) -> Unit) {
    val overview = (state.subagents as? PanelLoad.Ready)?.value ?: return
    val all = overview.children(section)
    val shown = state.visibleChildren(section)
    SectionHeading(section.heading(all.size))
    if (shown.isEmpty()) HarnessText(if (overview.completeness == SubagentOverviewCompleteness.Complete)
        "没有活跃的子智能体" else "此 live 快照中没有活跃的子智能体", Modifier.padding(horizontal = 8.dp),
        color = HarnessTokens.Colors.TextMuted, style = panelTextStyle())
    else ChildRows(shown, onAction)
    if (shown.size < all.size) HarnessButton(
        "显示更多", { onAction(ThreadPanelAction.ShowMore(state.scope, section)) },
        modifier = Modifier.padding(start = 44.dp, top = 8.dp),
        variant = HarnessButtonVariant.Ghost, size = HarnessControlSize.Compact,
    )
}

@Composable
private fun SectionHeading(text: String) {
    HarnessText(text, Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp).semantics { heading() }, style = panelTextStyle())
}

/**
 * A titled block in the subagent detail view, mirroring Codex's section component: a small muted
 * heading (`subagents.panel.status` / `.delegatedTask`) above a default-coloured body. The [tag]
 * keeps each block addressable without changing its visible text.
 */
@Composable
private fun DetailSection(title: String, tag: String, body: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().semantics { testTag = tag }) {
        HarnessText(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.bodySmall,
            color = HarnessTokens.Colors.TextMuted)
        Spacer(Modifier.height(4.dp))
        body()
    }
}

@Composable
private fun ChildRows(rows: List<ChildAgentSummary>, onAction: (ThreadPanelAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        rows.forEach { row -> key(row.key) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 40.dp).clip(RoundedCornerShape(HarnessTokens.Corners.Large))
                    .clickable(role = Role.Button) { onAction(ThreadPanelAction.SelectChild(row.key)) }
                    .semantics { stateDescription = row.status.label }.padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                ChildAvatar(row)
                Column(Modifier.weight(1f)) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HarnessText(row.label, Modifier.weight(1f), style = panelTextStyle(), maxLines = 1)
                        // State text wraps independently in compact layouts instead of squeezing the title away.
                        HarnessText(row.status.label, Modifier.widthIn(max = 88.dp), style = MaterialTheme.typography.bodySmall,
                            color = HarnessTokens.Colors.TextMuted)
                    }
                    row.preview?.let { HarnessText(it, style = panelTextStyle().copy(lineHeight = 20.sp),
                        color = HarnessTokens.Colors.TextMuted, maxLines = 1) }
                }
            }
        } }
    }
}

@Composable
private fun ChildAvatar(row: ChildAgentSummary) {
    Box(Modifier.size(24.dp).clip(CircleShape).background(HarnessTokens.Colors.SurfaceSecondary), contentAlignment = Alignment.Center) {
        HarnessText(row.label.take(1), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ChildDetailPanel(
    state: ThreadPanelsState,
    onAction: (ThreadPanelAction) -> Unit,
    childInteraction: (@Composable (ChildAgentDetail) -> Unit)?,
) {
    val summary = state.selectedSummary ?: return
    Column(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val metadataMaxWidth = maxWidth * .5f
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                HarnessIconButton("返回子智能体", { onAction(ThreadPanelAction.Back(state.scope)) }) { HarnessText("‹") }
                ChildAvatar(summary)
                Column(Modifier.weight(1f)) {
                    HarnessText(summary.label, style = panelTextStyle(), maxLines = 1)
                    val metadata = listOfNotNull(summary.model, summary.reasoningEffort).filter { it.isNotBlank() }.joinToString(" · ")
                    if (metadata.isNotEmpty()) HarnessText(metadata, Modifier.widthIn(max = metadataMaxWidth),
                        color = HarnessTokens.Colors.TextMuted, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), maxLines = 1)
                }
            }
        }
        // Codex `subagents.panel.status` / `.delegatedTask`: the detail view leads with the agent's
        // current status and the task the parent delegated to it, above its history.
        val delegatedTask = summary.objective?.takeIf { it.isNotBlank() }
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DetailSection("状态", "subagent-detail-status") { HarnessText(summary.status.label, style = panelTextStyle()) }
            if (delegatedTask != null) DetailSection("委派的任务", "subagent-detail-task") {
                HarnessText(delegatedTask, style = panelTextStyle())
            }
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            when (val load = state.detail) {
                is PanelLoad.Unavailable -> PanelMessage("对话记录不可用", load.reason.description)
                is PanelLoad.Loading -> PanelMessage("正在加载子智能体对话记录…")
                is PanelLoad.Empty -> PanelMessage("没有消息")
                is PanelLoad.Error -> PanelMessage("无法加载对话记录", load.message, error = true)
                is PanelLoad.Ready -> key(state.scope, state.ownerGeneration, load.value.key) {
                    Column(Modifier.fillMaxSize().semantics { testTag = "subagent-transcript" }
                        .verticalScroll(rememberScrollState()).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        val messages = load.value.typedMessages
                        if (messages != null) {
                            if (messages.isEmpty()) HarnessText("没有消息", style = panelTextStyle())
                            else SelectionContainer {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    messages.forEach { message ->
                                        key(state.scope, state.ownerGeneration, load.value.key, message.id) {
                                            MessageCard(message)
                                        }
                                    }
                                }
                            }
                        } else if (load.value.entries.isEmpty()) HarnessText("没有消息", style = panelTextStyle())
                        else SelectionContainer {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                load.value.entries.forEach { entry -> key(entry.id) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        HarnessText(entry.role.label, color = HarnessTokens.Colors.TextMuted, style = MaterialTheme.typography.bodySmall)
                                        HarnessText(entry.text, style = panelTextStyle())
                                    }
                                } }
                            }
                        }
                    }
                }
            }
        }
        val interactive = state.interactiveDetail
        if (interactive != null && childInteraction != null) childInteraction(interactive)
        else HarnessText("只读对话记录", Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            color = HarnessTokens.Colors.TextMuted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun PanelMessage(title: String, description: String? = null, error: Boolean = false) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HarnessText(title, style = panelTextStyle(), color = if (error) HarnessTokens.Colors.Danger else HarnessTokens.Colors.Text)
        description?.let { HarnessText(it, style = panelTextStyle(), color = HarnessTokens.Colors.TextMuted) }
    }
}

@Composable
private fun panelTextStyle() = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp)

@Composable
private fun codeTextStyle() = MaterialTheme.typography.bodySmall.copy(fontFamily = HarnessTokens.Fonts.Code)

private const val TerminalScrollTolerance = 1

/**
 * Reading state scoped to one background terminal projection. It survives a same-key
 * Loading/Error and a Subagents tab switch; the owner recreates it for a new scope, owner
 * incarnation, or exact process key. terminalGeneration and revision are not part of identity.
 */
internal class TerminalReadingState {
    internal val vertical = ScrollState(0)
    internal val horizontal = ScrollState(0)
    /** False until this reader has a measured range; the first Ready display must show the tail. */
    internal var measured: Boolean = false
    /** User cancellation persists even if a later shrink clamps the reader onto the bottom. */
    internal var followOutput: Boolean = true
    internal var upwardInputVersion: Long = 0
    internal val inputObserver = object : NestedScrollConnection {
        private var beforeInputValue: Int = 0
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            beforeInputValue = vertical.value
            return Offset.Zero
        }
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            // Observe actual movement during user dispatch, independent of platform wheel sign.
            // A range clamp alone stops at min(previous value, new max); movement above that
            // position is user intent and remains recorded even if a later measure clamps again.
            if (source == NestedScrollSource.UserInput) {
                if (vertical.value < beforeInputValue.coerceAtMost(vertical.maxValue)) {
                    upwardInputVersion++
                    followOutput = false
                } else if (vertical.value > beforeInputValue &&
                    vertical.value >= vertical.maxValue - TerminalScrollTolerance) {
                    followOutput = true
                }
            }
            return Offset.Zero
        }
    }
}

private data class TerminalReaderBaseline(
    val value: Int, val max: Int, val upwardInputVersion: Long, val firstDisplay: Boolean,
)

private class TerminalContentMeasure { var completed: Boolean = false }

private data class TerminalReaderSignature(
    val key: BackgroundTerminalKey,
    val buffer: String,
    val truncated: Boolean,
    val command: String?,
    val cwd: String?,
    val lifecycle: BackgroundProcessLifecycle,
    val width: Dp,
    val height: Dp,
)

/**
 * Only this signature's measured scroll node can permit a follow. The timeout cancels waiting;
 * it never permits a decision based on an old range. One frame also gives pending input a chance
 * to cancel the follow. Disposal/identity/signature changes cancel the owning LaunchedEffect.
 */
private suspend fun awaitContentLayout(content: TerminalContentMeasure): Boolean =
    withTimeoutOrNull(10_000L) {
        do { withFrameNanos { } } while (!content.completed)
        true
    } ?: false
