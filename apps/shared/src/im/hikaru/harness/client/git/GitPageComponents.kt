package im.hikaru.harness.client.git

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import im.hikaru.harness.client.page.SettingsFormCard
import im.hikaru.harness.ui.foundation.*

/** Optional read-only event observation; absent in normal app composition and adds no UI. */
public data class GitSelectEvent(
    val tag: String, val instance: Any, val kind: String, val enabled: Boolean?, val expanded: Boolean?,
    val detail: String = "",
)
public val LocalGitSelectEventObserver = staticCompositionLocalOf<((GitSelectEvent) -> Unit)?> { null }

// UI readiness is deliberately stricter than GitState.canRefresh. Failed reads remain retryable.
internal val GitState.pageMutationBusy: Boolean get() = creationBusy || checkoutBusy || commitBusy
internal val GitState.pageReading: Boolean get() = repository is GitRead.Loading || status is GitRead.Loading ||
    branches is GitRead.Loading || worktrees is GitRead.Loading || diff is GitRead.Loading || commitSnapshot is GitRead.Loading
internal val GitState.pageCanRead: Boolean get() = unavailableReason == null && !pageMutationBusy && !pageReading
internal val GitState.pageReady: Boolean get() = pageCanRead &&
    (repository as? GitRead.Ready<GitRepository>)?.value?.binding == binding && binding != null

@Composable
internal fun GitPageCard(content: @Composable ColumnScope.() -> Unit) {
    SettingsFormCard {
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

/** Full paths/refs can be selected or horizontally scrolled without widening the page. */
@Composable
internal fun GitPageValue(value: String, tag: String, muted: Boolean = false) {
    SelectionContainer {
        Box(Modifier.fillMaxWidth()) {
            HarnessText(value, Modifier.testTag(tag).horizontalScroll(rememberScrollState()),
                color = if (muted) HarnessTokens.Colors.TextMuted else HarnessTokens.Colors.Text,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace))
        }
    }
}

/** Session picker stays above the page scroll area; retained popup callbacks cannot redirect a new selection. */
@Composable
internal fun GitSessionDirectoryPicker(
    controller: GitController, state: GitState, bindingReady: Boolean, choices: List<String>, compact: Boolean = false,
) {
    val options = (listOf<String?>(null) + choices + listOfNotNull(state.selectedCwd)).distinct()
    val selection = remember(controller, state.binding, state.selectedCwd) { booleanArrayOf(true) }
    val latestSelection by rememberUpdatedState(selection)
    val latestReady by rememberUpdatedState(bindingReady)
    val latestOptions by rememberUpdatedState(options)
    val observer = LocalGitSelectEventObserver.current
    DisposableEffect(selection) {
        observer?.invoke(GitSelectEvent("git-session-cwd", selection, "selection-attach", null, null,
            "binding=${state.binding}, cwd=${state.selectedCwd}, bindingReady=$bindingReady"))
        onDispose {
            selection[0] = false
            observer?.invoke(GitSelectEvent("git-session-cwd", selection, "selection-dispose", null, null,
                "binding=${state.binding}, cwd=${state.selectedCwd}"))
        }
    }
    val current = controller.state.value
    val enabled = bindingReady && state.binding?.access == GitAccess.LocalReadOnly && state.pageCanRead &&
        current.binding == state.binding && current.selectedCwd == state.selectedCwd && current.pageCanRead
    SideEffect {
        observer?.invoke(GitSelectEvent("git-session-cwd", selection, "picker-applied", enabled, null,
            "binding=${state.binding}, cwd=${state.selectedCwd}, bindingReady=$bindingReady, " +
                "stateCanRead=${state.pageCanRead}, liveBinding=${current.binding}, liveCwd=${current.selectedCwd}, " +
                "liveCanRead=${current.pageCanRead}, selectionAlive=${selection[0]}"))
    }
    key(selection) {
        GitPageSelect(state.selectedCwd, options, { it ?: "选择目录" }, enabled, "git-session-cwd",
            triggerLabel = if (compact) "切换目录" else null) { cwd ->
            val live = controller.state.value
            if (selection[0] && latestSelection === selection && latestReady && cwd in latestOptions &&
                live.binding == state.binding && live.selectedCwd == state.selectedCwd && live.pageCanRead) {
                controller.selectRepository(cwd)
            }
        }
    }
}

/** Bounded anchor and scrollable menu, with complete wrapping option labels. */
@Composable
internal fun <T> GitPageSelect(
    value: T, options: List<T>, label: (T) -> String, enabled: Boolean, testTag: String,
    triggerLabel: String? = null, onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val observer = LocalGitSelectEventObserver.current
    val instance = remember { Any() }
    val active = remember { booleanArrayOf(true) }
    val latestEnabled by rememberUpdatedState(enabled)
    val latestSelect by rememberUpdatedState(onSelect)
    // Value equality and ordered option equality preserve a lifetime. Removal/reordering or A -> B -> A
    // creates a fresh lifetime, so an old index is never interpreted using a new options list.
    val snapshot = options.toList()
    val optionsLifetime = remember(snapshot, value) { Any() }
    val latestOptionsLifetime by rememberUpdatedState(optionsLifetime)
    fun record(kind: String, detail: String = "") {
        observer?.invoke(GitSelectEvent(testTag, instance, kind, enabled, expanded, detail))
    }
    val latestRecord by rememberUpdatedState(::record)
    DisposableEffect(instance) {
        record("select-attach")
        onDispose { active[0] = false; latestRecord("select-dispose", "") }
    }
    SideEffect { record("applied", "options=${options.size}") }
    LaunchedEffect(enabled) {
        record("enabled-effect")
        if (!enabled) { expanded = false; record("disabled-close") }
    }
    val observation = if (observer == null) Modifier else Modifier.pointerInput(observer, instance) {
        latestRecord("pointer-probe-start", "")
        try {
            awaitPointerEventScope {
                while (true) {
                    val initial = awaitPointerEvent(PointerEventPass.Initial)
                    if (initial.type == PointerEventType.Press || initial.type == PointerEventType.Release ||
                        initial.type == PointerEventType.Scroll) {
                        latestRecord("pointer-initial", "type=${initial.type}, changes=${initial.changes.map {
                            "${it.id}:position=${it.position}, pressed=${it.pressed}, previous=${it.previousPressed}, consumed=${it.isConsumed}"
                        }}")
                        val final = awaitPointerEvent(PointerEventPass.Final)
                        latestRecord("pointer-final", "type=${final.type}, changes=${final.changes.map {
                            "${it.id}:position=${it.position}, pressed=${it.pressed}, previous=${it.previousPressed}, consumed=${it.isConsumed}"
                        }}")
                    }
                }
            }
        } finally { latestRecord("pointer-probe-stop", "") }
    }
    BoxWithConstraints(Modifier.fillMaxWidth().then(observation)) {
        val menuWidth = if (triggerLabel != null) 480.dp else maxWidth.coerceAtMost(560.dp)
        // A retained semantics action belongs to its option lifetime, even when index keys and labels match.
        key(optionsLifetime) {
            HarnessMenu(
                expanded = expanded && enabled, enabled = enabled,
                entries = snapshot.mapIndexed { index, option ->
                    HarnessMenuEntry.Item(index.toString(), label(option), enabled = enabled, selected = option == value)
                },
                selectionScope = optionsLifetime,
                onExpandedChange = {
                    record("dismiss-before")
                    if (!it || active[0] && latestEnabled) expanded = it
                    record("dismiss-after")
                },
                onSelect = { key ->
                    val index = key.toIntOrNull()
                    if (active[0] && latestEnabled && latestOptionsLifetime === optionsLifetime && index != null && index in snapshot.indices) {
                        record("option", "index=$index")
                        latestSelect(snapshot[index])
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                popupModifier = Modifier.testTag("$testTag-menu"), popupWidth = menuWidth,
                popupMaxHeight = 320.dp, wrapLabels = true,
                onContentMountChange = if (observer == null) null else { mounted ->
                    latestRecord(if (mounted) "menu-content-attach" else "menu-content-dispose", "")
                },
                onContentApplied = if (observer == null) null else { -> record("menu-content-applied") },
                anchor = { toggle ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 36.dp)
                        .border(1.dp, HarnessTokens.Colors.Border, RoundedCornerShape(8.dp))
                        .background(HarnessTokens.Colors.SurfaceElevated, RoundedCornerShape(8.dp))
                        .testTag(testTag).semantics { stateDescription = label(value) }
                        .clickable(enabled = enabled, role = Role.Button) {
                            if (active[0] && latestEnabled) { record("click-before"); toggle(); record("click-after") }
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HarnessText(triggerLabel ?: label(value), Modifier.weight(1f), maxLines = 2,
                        color = if (enabled) HarnessTokens.Colors.Text else HarnessTokens.Colors.TextDisabled,
                            style = MaterialTheme.typography.bodyMedium)
                        HarnessIcon(HarnessIconName.ChevronDown, size = 14.dp,
                            tint = if (enabled) HarnessTokens.Colors.TextMuted else HarnessTokens.Colors.TextDisabled)
                    }
                },
            )
        }
    }
    if (triggerLabel == null && label(value).length > 64) GitPageValue(label(value), "$testTag-value", muted = true)
}

@Composable
internal fun GitPageList(tag: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().heightIn(max = 320.dp).testTag(tag).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

@Composable
internal fun GitPatch(
    patch: String,
    previewFiles: Map<String, GitPreviewFile> = emptyMap(),
    onRequestPreviews: (List<String>) -> Unit = {},
    hideWhitespace: Boolean = false,
    onToggleHideWhitespace: (Boolean) -> Unit = {},
    loadFullFiles: Boolean = false,
    onToggleLoadFullFiles: (Boolean) -> Unit = {},
    onRefresh: () -> Unit = {},
    refreshDisabled: Boolean = false,
    copyGitApplyCommandDisabled: Boolean = false,
) {
    val document = remember(patch) { parseGitDiff(patch) }
    val clipboard = LocalClipboardManager.current
    val command = remember(patch) { gitApplyCommand(patch) }
    // Feedback only ever follows a click: an unparseable patch keeps the menu item enabled so the
    // 「未能生成 git apply 命令」 message is the result of the action, never a page-load warning.
    var copyFeedback by remember(patch) { mutableStateOf<String?>(null) }
    // The feedback line is emitted from inside GitDiffView so this composable keeps contributing the
    // exact same children to the caller's layout scope as before the copy action existed. Wrapping the
    // diff in an extra Column would re-parent its hint/toolbar/viewport and change their spacing.
    GitDiffView(document, previewFiles, onRequestPreviews, hideWhitespace, onToggleHideWhitespace,
        loadFullFiles, onToggleLoadFullFiles, onRefresh, refreshDisabled,
        onCopyGitApplyCommand = {
            if (command == null) copyFeedback = "未能生成 git apply 命令"
            else {
                clipboard.setText(AnnotatedString(command))
                copyFeedback = "已将 git apply 命令复制到剪贴板"
            }
        },
        copyGitApplyCommandDisabled = copyGitApplyCommandDisabled,
        copyFeedback = copyFeedback)
}

/**
 * ASAR D01/D03: the original renders a diff as one block per file with a status and +/- statistics,
 * collapsible with a sticky header, and opens small diffs by default. The file-count/line thresholds
 * that decide `initialOpen` are not observed, so [openByDefault] is a Harness choice.
 */
@Composable
internal fun GitDiffView(
    document: GitDiffDocument,
    previewFiles: Map<String, GitPreviewFile> = emptyMap(),
    onRequestPreviews: (List<String>) -> Unit = {},
    hideWhitespace: Boolean = false,
    onToggleHideWhitespace: (Boolean) -> Unit = {},
    loadFullFiles: Boolean = false,
    onToggleLoadFullFiles: (Boolean) -> Unit = {},
    onRefresh: () -> Unit = {},
    refreshDisabled: Boolean = false,
    onCopyGitApplyCommand: () -> Unit = {},
    copyGitApplyCommandDisabled: Boolean = false,
    copyFeedback: String? = null,
) {
    var layout by remember { mutableStateOf(GitDiffLayout.Unified) }
    var richPreview by remember { mutableStateOf(false) }
    var wrap by remember { mutableStateOf(false) }
    // `wordDiffsEnabled.2` defaults to true in the original, so the emphasis is on until toggled off.
    var wordDiffs by remember { mutableStateOf(true) }
    // `hideDiffImports` defaults to false in the original (`Fu(hideDiffImports, !1)`), so imports stay
    // visible until the menu item is checked.
    var hideImports by remember { mutableStateOf(false) }
    val openByDefault = document.files.size <= 10 && document.addedLines + document.deletedLines <= 400
    // Open/closed state lives here rather than inside each card, so the expand/collapse-all action
    // (`codex.review.expandOrCollapseDiffMenu.*`) and its menu label always agree with what is on screen.
    var expandedHeaders by remember(document) {
        mutableStateOf(
            document.files.filter { openByDefault && it.status != GitDiffFileStatus.Deleted }
                .map { it.header }.toSet(),
        )
    }
    val allExpanded = document.files.isNotEmpty() && document.files.all { it.header in expandedHeaders }
    // `fOr`: only markdown files, never a deletion, and only while the toggle is on.
    val previewPaths = remember(document, richPreview) {
        if (!richPreview) emptyList()
        else document.files
            .filter { gitDiffPreviewKind(it.path, it.status == GitDiffFileStatus.Deleted, true) == GitDiffPreviewKind.Markdown }
            .map { it.path }
    }
    // Re-request when the diff itself changes: the controller drops its cache then, so the card
    // must ask again instead of showing text that belonged to the previous patch.
    LaunchedEffect(document, previewPaths) { if (previewPaths.isNotEmpty()) onRequestPreviews(previewPaths) }
    HarnessText("可在 Diff 内分别上下与左右滚动；文本可选中复制。",
        style = MaterialTheme.typography.bodySmall, color = HarnessTokens.Colors.TextMuted)
    Row(
        Modifier.fillMaxWidth().testTag("git-diff-toolbar"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HarnessText(diffSummary(document), Modifier.testTag("git-diff-summary"), style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.One), verticalAlignment = Alignment.CenterVertically) {
            GitDiffLayoutSelector(layout) { layout = it }
            GitDiffRichPreviewToggle(richPreview) { richPreview = it }
            GitDiffOptionsMenu(
                wrap = wrap,
                onWrapChange = { wrap = it },
                expanded = allExpanded,
                onToggleExpanded = { expandedHeaders = if (allExpanded) emptySet() else document.files.map { it.header }.toSet() },
                hideWhitespace = hideWhitespace,
                onToggleHideWhitespace = onToggleHideWhitespace,
                loadFullFiles = loadFullFiles,
                onToggleLoadFullFiles = onToggleLoadFullFiles,
                richPreview = richPreview,
                onToggleRichPreview = { richPreview = it },
                wordDiffs = wordDiffs,
                onToggleWordDiffs = { wordDiffs = it },
                hideImports = hideImports,
                onToggleHideImports = { hideImports = it },
                onRefresh = onRefresh,
                refreshDisabled = refreshDisabled,
                onCopyGitApplyCommand = onCopyGitApplyCommand,
                copyGitApplyCommandDisabled = copyGitApplyCommandDisabled,
            )
        }
    }
    if (document.leadingContent) {
        HarnessText("补丁开头存在不属于任何文件的文本，未合并进文件卡片。",
            Modifier.testTag("git-diff-leading"), color = HarnessTokens.Colors.TextMuted)
    }
    // No viewport-wide SelectionContainer: the unified gutter must stay out of the selectable text.
    // Selectable regions (headers, previews, patch bodies) wrap themselves instead.
    BoxWithConstraints(Modifier.fillMaxWidth().height(288.dp).testTag("git-diff-viewport")
        .border(1.dp, HarnessTokens.Colors.Border, RoundedCornerShape(8.dp))
        .background(HarnessTokens.Colors.SurfaceElevated, RoundedCornerShape(8.dp))) {
        val narrowPane = maxWidth < 520.dp
        Column(Modifier.fillMaxSize().testTag("git-diff-vertical").verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            document.files.forEach { file ->
                key(file.path, file.originalPath, file.status, file.header) {
                    GitDiffFileCard(
                        file = file,
                        expanded = file.header in expandedHeaders,
                        onExpandedChange = { open ->
                            expandedHeaders = if (open) expandedHeaders + file.header else expandedHeaders - file.header
                        },
                        layout = effectiveGitDiffLayout(layout, file, narrowPane),
                        preview = gitDiffPreviewKind(file.path, file.status == GitDiffFileStatus.Deleted, richPreview),
                        previewFile = previewFiles[file.path],
                        wrap = wrap,
                        wordDiffs = wordDiffs,
                        hideImports = hideImports,
                    )
                }
            }
        }
    }
    // Rendered last so it is additive: with no feedback the diff emits exactly its original children.
    copyFeedback?.let { feedback ->
        HarnessText(feedback, Modifier.testTag("git-diff-copy-git-apply-feedback"),
            color = if (feedback == "未能生成 git apply 命令") HarnessTokens.Colors.Danger
            else HarnessTokens.Colors.TextMuted, style = MaterialTheme.typography.bodySmall)
    }
}

/** D01 toolbar control: unified / split / auto. Labels stand in for the original's icon glyphs. */
@Composable
private fun GitDiffLayoutSelector(selected: GitDiffLayout, onSelect: (GitDiffLayout) -> Unit) {
    Row(
        Modifier.testTag("git-diff-layout"),
        horizontalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.One),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GitDiffLayoutOption("统一", "git-diff-layout-unified", GitDiffLayout.Unified, selected, onSelect)
        GitDiffLayoutOption("分栏", "git-diff-layout-split", GitDiffLayout.Split, selected, onSelect)
        GitDiffLayoutOption("自动", "git-diff-layout-auto", GitDiffLayout.Auto, selected, onSelect)
    }
}

@Composable
private fun GitDiffLayoutOption(
    text: String,
    tag: String,
    value: GitDiffLayout,
    selected: GitDiffLayout,
    onSelect: (GitDiffLayout) -> Unit,
) {
    HarnessButton(
        text = text, onClick = { onSelect(value) }, selected = selected == value,
        variant = HarnessButtonVariant.Ghost, size = HarnessControlSize.Compact, testTag = tag,
    )
}

/**
 * D01 toolbar control: the original's rich-preview toggle (`codex.diffView.richPreviewToggle`),
 * a pressed icon button. This control and the options-menu item share the page-local state; the
 * label stands in for the icon glyphs.
 */
@Composable
private fun GitDiffRichPreviewToggle(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    HarnessButton(
        text = "富文本预览",
        onClick = { onToggle(!enabled) },
        selected = enabled,
        variant = HarnessButtonVariant.Ghost,
        size = HarnessControlSize.Compact,
        testTag = "git-diff-rich-preview",
    )
}

/**
 * D01 menu: the original keeps word wrap and expand/collapse-all in the “变更”选项 menu
 * (`codex.review.header.moreOptions`), with labels from `codex.review.wrap.label` (自动换行) and
 * `codex.review.expandOrCollapseDiffMenu.*` (展开全部差异 / 折叠全部差异) and, for
 * `codex.review.whitespace.hide` (隐藏空白字符), a third item that re-reads the diff with whitespace
 * ignored, `codex.review.loadFullFiles.enable` (加载完整文件) which re-reads the patch with full file
 * context so a partial diff shows the whole file, `codex.review.wordDiffs.label` (词级差异) toggling
 * the intra-line emphasis, `codex.review.richPreview.label` (渲染预览) toggling rich preview, and
 * `codex.review.imports.hide` (隐藏导入内容) dropping top-level import-only changes. The menu item
 * and toolbar toggle share the same page-local state. This page does not implement the menu item's
 * `leftIcon`, `showRichPreview` gating, image/SVG/PDF previews, or persistence.
 */
@Composable
private fun GitDiffOptionsMenu(
    wrap: Boolean,
    onWrapChange: (Boolean) -> Unit,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    hideWhitespace: Boolean,
    onToggleHideWhitespace: (Boolean) -> Unit,
    loadFullFiles: Boolean,
    onToggleLoadFullFiles: (Boolean) -> Unit,
    richPreview: Boolean,
    onToggleRichPreview: (Boolean) -> Unit,
    wordDiffs: Boolean,
    onToggleWordDiffs: (Boolean) -> Unit,
    hideImports: Boolean,
    onToggleHideImports: (Boolean) -> Unit,
    onRefresh: () -> Unit = {},
    refreshDisabled: Boolean = false,
    onCopyGitApplyCommand: () -> Unit = {},
    copyGitApplyCommandDisabled: Boolean = false,
) {
    var open by remember { mutableStateOf(false) }
    val entries = listOf(
        HarnessMenuEntry.Item(key = "wrap", text = "自动换行", selected = wrap),
        HarnessMenuEntry.Item(key = "expand", text = if (expanded) "折叠全部差异" else "展开全部差异"),
        HarnessMenuEntry.Item(key = "load-full-files", text = "加载完整文件", selected = loadFullFiles),
        HarnessMenuEntry.Item(key = "rich-preview", text = "渲染预览", selected = richPreview),
        HarnessMenuEntry.Item(key = "hide-whitespace", text = "隐藏空白字符", selected = hideWhitespace),
        HarnessMenuEntry.Item(key = "word-diffs", text = "词级差异", selected = wordDiffs),
        HarnessMenuEntry.Item(key = "hide-imports", text = "隐藏导入内容", selected = hideImports),
        HarnessMenuEntry.Item(key = "refresh", text = "刷新", enabled = !refreshDisabled),
        HarnessMenuEntry.Item(key = "copy-git-apply", text = "复制 git apply 命令",
            enabled = !copyGitApplyCommandDisabled),
    )
    HarnessMenu(
        expanded = open,
        entries = entries,
        onExpandedChange = { open = it },
        onSelect = { key ->
            when (key) {
                "wrap" -> onWrapChange(!wrap)
                "expand" -> onToggleExpanded()
                "load-full-files" -> onToggleLoadFullFiles(!loadFullFiles)
                "rich-preview" -> onToggleRichPreview(!richPreview)
                "hide-whitespace" -> onToggleHideWhitespace(!hideWhitespace)
                "word-diffs" -> onToggleWordDiffs(!wordDiffs)
                "hide-imports" -> onToggleHideImports(!hideImports)
                "refresh" -> onRefresh()
                "copy-git-apply" -> onCopyGitApplyCommand()
            }
        },
        anchor = { toggle -> HarnessButton(
            text = "变更选项", onClick = toggle,
            variant = HarnessButtonVariant.Ghost, size = HarnessControlSize.Compact,
            testTag = "git-diff-options",
        ) },
        popupModifier = Modifier.testTag("git-diff-options-menu"),
        popupWidth = 200.dp,
    )
}

/** D01 header: the file count plus the added/removed line badge. */
internal fun diffSummary(document: GitDiffDocument): String {
    if (document.files.isEmpty()) return "未解析出文件块。"
    val stats = if (document.addedLines > 0 || document.deletedLines > 0) " · +${document.addedLines} −${document.deletedLines}" else ""
    return "${document.files.size} 个文件变更$stats"
}

internal fun diffStatusLabel(status: GitDiffFileStatus): String = when (status) {
    GitDiffFileStatus.Added -> "新增"
    GitDiffFileStatus.Deleted -> "删除"
    GitDiffFileStatus.Renamed -> "重命名"
    GitDiffFileStatus.Copied -> "复制"
    GitDiffFileStatus.Modified -> "修改"
}

@Composable
private fun GitDiffFileCard(
    file: GitDiffFile,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    layout: GitDiffLayout,
    preview: GitDiffPreviewKind = GitDiffPreviewKind.Diff,
    previewFile: GitPreviewFile? = null,
    wrap: Boolean = false,
    wordDiffs: Boolean = false,
    hideImports: Boolean = false,
) {
    Column(Modifier.fillMaxWidth().border(1.dp, HarnessTokens.Colors.Border, RoundedCornerShape(8.dp))) {
        SelectionContainer {
            Column(Modifier.fillMaxWidth().testTag("git-diff-file")
                .clickable(role = Role.Button) { onExpandedChange(!expanded) }
                .padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                HarnessText(
                    (if (file.originalPath != null) "${file.originalPath} → ${file.path}" else file.path) + " · " + diffStatusLabel(file.status),
                    Modifier.testTag("git-diff-file-title"), style = MaterialTheme.typography.bodyMedium)
                val detail = buildString {
                    append(if (expanded) "收起" else "展开")
                    append(" · +").append(file.addedLines).append(" −").append(file.deletedLines)
                    if (file.binary) append(" · 二进制，无文本差异")
                }
                HarnessText(detail, Modifier.testTag("git-diff-file-detail"),
                    style = MaterialTheme.typography.bodySmall, color = HarnessTokens.Colors.TextMuted)
            }
        }
        if (expanded && file.body.isNotEmpty()) {
            val markdown = if (preview == GitDiffPreviewKind.Markdown) {
                (previewFile as? GitPreviewFile.Ready)?.text
            } else {
                null
            }
            if (markdown != null) {
                SelectionContainer { GitMarkdownPreview(file.path, markdown) }
            } else {
                if (preview == GitDiffPreviewKind.Markdown) {
                    // The original silently falls back to the diff while a preview loads or fails;
                    // this page names the reason instead of hiding it.
                    val notice = when (previewFile) {
                        null -> "尚未读取文件，先显示差异文本。"
                        is GitPreviewFile.Loading -> "正在加载预览…"
                        is GitPreviewFile.Unavailable -> previewFile.reason
                        is GitPreviewFile.Ready -> null
                    }
                    if (notice != null) {
                        SelectionContainer {
                            HarnessText(notice, Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                .testTag("git-diff-rich-preview-notice-${file.path}"),
                                style = MaterialTheme.typography.bodySmall, color = HarnessTokens.Colors.TextMuted)
                        }
                    }
                }
                if (layout == GitDiffLayout.Split) {
                    GitDiffSplitView(file, wordDiffs, hideImports)
                } else {
                    GitUnifiedDiff(file.body, wrap, wordDiffs, hideImports)
                }
            }
        }
    }
}

/**
 * Unified diff body. The default scroll variant keeps the original's single horizontally scrollable
 * body; the word-wrap variant lays the same lines out one row per line so the gutter keeps its number
 * on the line it belongs to while the text wraps instead of scrolling.
 */
@Composable
private fun GitUnifiedDiff(body: String, wrap: Boolean, wordDiffs: Boolean, hideImports: Boolean) {
    if (wrap) GitWrappedUnifiedDiff(body, wordDiffs, hideImports) else GitUnifiedDiffScroll(body, wordDiffs, hideImports)
}

/**
 * Unified diff body. Added/removed lines carry a tinted background and `@@` headers act as
 * `line-info` separators, matching the observed renderer's `[data-line-type='change-…']` and
 * `[data-separator='line-info']` rules. The original keeps a separate, unselectable line-number
 * gutter (`[data-column-number]` / `[data-gutter-buffer]`); the gutter text is drawn beside the body
 * and stays outside the SelectionContainer, so copying a range never picks up the numbers while the
 * patch text keeps every `+`/`-`/space marker.
 */
@Composable
private fun GitUnifiedDiffScroll(body: String, wordDiffs: Boolean, hideImports: Boolean) {
    val added = HarnessTokens.Colors.DiffAddedBackground
    val removed = HarnessTokens.Colors.DiffRemovedBackground
    val separator = HarnessTokens.Colors.BorderSubtle
    val muted = HarnessTokens.Colors.TextMuted
    val wordAdded = HarnessTokens.Colors.DiffAdded.copy(alpha = WORD_DIFF_EMPHASIS_ALPHA)
    val wordRemoved = HarnessTokens.Colors.DiffRemoved.copy(alpha = WORD_DIFF_EMPHASIS_ALPHA)
    val lines = remember(body, hideImports) {
        parseGitDiffLines(body).let { if (hideImports) gitDiffHiddenImportLines(it) else it }
    }
    val wordSpans = remember(lines, wordDiffs) { if (wordDiffs) gitUnifiedWordDiff(lines) else emptyMap() }
    val annotated = remember(body, added, removed, separator, muted, wordSpans, wordAdded, wordRemoved, hideImports) {
        gitUnifiedDiffAnnotatedString(body, added, removed, separator, muted, wordSpans, wordAdded, wordRemoved, hideImports)
    }
    val gutter = remember(lines, added, removed, separator) {
        gitDiffGutterAnnotatedString(lines, added, removed, separator)
    }
    val bodyStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        // Non-selectable gutter: rendered outside the SelectionContainer so it is never copied. Its
        // per-line background tints added/removed cells and `@@` separators like the body, and the
        // trailing gap of spaces keeps the two bands adjacent without a layout seam.
        Text(
            gutter,
            Modifier.testTag("git-diff-gutter").padding(start = 10.dp),
            style = bodyStyle,
            color = muted,
        )
        SelectionContainer(Modifier.weight(1f)) {
            Text(
                annotated,
                Modifier.fillMaxWidth().testTag("git-diff-file-patch").horizontalScroll(rememberScrollState())
                    .padding(end = 10.dp),
                style = bodyStyle,
                color = HarnessTokens.Colors.Text,
            )
        }
    }
}

/**
 * Word-wrap variant of the unified body. The original's `[data-unified]` grid keeps one gutter cell
 * per line, so a wrapped line stays beside its own number; one wrapping text block cannot reproduce
 * that, so this variant renders a row per line exactly like [GitDiffSplitView]. The number cell sits
 * outside each line's [SelectionContainer], so copying the body still never picks up the numbers, and
 * the line text keeps every `+`/`-`/space marker and its per-line tint.
 */
@Composable
private fun GitWrappedUnifiedDiff(body: String, wordDiffs: Boolean, hideImports: Boolean) {
    val added = HarnessTokens.Colors.DiffAddedBackground
    val removed = HarnessTokens.Colors.DiffRemovedBackground
    val separator = HarnessTokens.Colors.BorderSubtle
    val muted = HarnessTokens.Colors.TextMuted
    val wordAdded = HarnessTokens.Colors.DiffAdded.copy(alpha = WORD_DIFF_EMPHASIS_ALPHA)
    val wordRemoved = HarnessTokens.Colors.DiffRemoved.copy(alpha = WORD_DIFF_EMPHASIS_ALPHA)
    val lines = remember(body, hideImports) {
        parseGitDiffLines(body).let { if (hideImports) gitDiffHiddenImportLines(it) else it }
    }
    val width = remember(lines) { gitDiffGutterWidth(lines) }
    val wordSpans = remember(lines, wordDiffs) { if (wordDiffs) gitUnifiedWordDiff(lines) else emptyMap() }
    val bodyStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
    Column(Modifier.fillMaxWidth().testTag("git-diff-wrap").padding(vertical = 8.dp)) {
        lines.forEachIndexed { index, line ->
            Row(Modifier.fillMaxWidth()) {
                Text(
                    gitDiffGutterLineAnnotatedString(line, width, added, removed, separator),
                    Modifier.testTag("git-diff-gutter").padding(start = 10.dp),
                    style = bodyStyle,
                    color = muted,
                )
                SelectionContainer(Modifier.weight(1f)) {
                    Text(
                        gitDiffUnifiedLineAnnotatedString(line, added, removed, separator, muted,
                            wordSpans[index], wordAdded, wordRemoved),
                        Modifier.testTag("git-diff-file-patch-line").padding(end = 10.dp),
                        style = bodyStyle,
                        color = HarnessTokens.Colors.Text,
                    )
                }
            }
        }
    }
}

/**
 * Paints each body line. Kept apart from the composable so the mapping is testable without Compose.
 * Every character of the input body appears exactly once, in order, including the `+`/`-`/space
 * markers, so the selectable text is unchanged from the raw patch.
 */
internal fun gitUnifiedDiffAnnotatedString(
    body: String,
    addedBackground: androidx.compose.ui.graphics.Color,
    removedBackground: androidx.compose.ui.graphics.Color,
    separatorBackground: androidx.compose.ui.graphics.Color,
    muted: androidx.compose.ui.graphics.Color,
    wordDiffs: Map<Int, GitWordDiff> = emptyMap(),
    wordAddedBackground: Color = Color.Unspecified,
    wordRemovedBackground: Color = Color.Unspecified,
    hideImports: Boolean = false,
): AnnotatedString = buildAnnotatedString {
    val lines = parseGitDiffLines(body).let { if (hideImports) gitDiffHiddenImportLines(it) else it }
    lines.forEachIndexed { index, line ->
        if (index > 0) append('\n')
        val span = gitDiffLineSpan(line, addedBackground, removedBackground, separatorBackground, muted)
        val wordBackground = if (line.kind == GitDiffLineKind.Removed) wordRemovedBackground else wordAddedBackground
        appendWordDiffLine(line, span, wordDiffs[index], wordBackground)
    }
}

/** One body line's full patch text, marker included, so the selectable body keeps every character. */
internal fun gitDiffLineText(line: GitDiffLine): String = when (line.kind) {
    GitDiffLineKind.Added -> "+" + line.text
    GitDiffLineKind.Removed -> "-" + line.text
    GitDiffLineKind.Context -> " " + line.text
    GitDiffLineKind.HunkHeader, GitDiffLineKind.NoNewline, GitDiffLineKind.Meta -> line.text
}

/** The leading marker one body line prints; `@@`/`\`/meta lines carry none. */
private fun gitDiffLineMarker(line: GitDiffLine): String = when (line.kind) {
    GitDiffLineKind.Added -> "+"
    GitDiffLineKind.Removed -> "-"
    GitDiffLineKind.Context -> " "
    GitDiffLineKind.HunkHeader, GitDiffLineKind.NoNewline, GitDiffLineKind.Meta -> ""
}

/**
 * Appends one body line: its marker, then its text with the changed words emphasised. Every character
 * appears exactly once and in order, so the selectable text is unchanged from the raw patch; the
 * optional [wordBackground] only adds a stronger tint over the ranges the word diff marked.
 */
private fun AnnotatedString.Builder.appendWordDiffLine(
    line: GitDiffLine,
    span: SpanStyle?,
    wordDiff: GitWordDiff?,
    wordBackground: Color,
) {
    val spans = when (line.kind) {
        GitDiffLineKind.Removed -> wordDiff?.removed
        GitDiffLineKind.Added -> wordDiff?.added
        else -> null
    }
    val content: AnnotatedString.Builder.() -> Unit = {
        append(gitDiffLineMarker(line))
        appendWordSpans(line.text, spans, wordBackground)
    }
    if (span == null) content() else withStyle(span) { content() }
}

/** The tint/colour one body line carries; context lines stay unstyled. */
private fun gitDiffLineSpan(
    line: GitDiffLine,
    addedBackground: Color,
    removedBackground: Color,
    separatorBackground: Color,
    muted: Color,
): SpanStyle? = when (line.kind) {
    GitDiffLineKind.Added -> SpanStyle(background = addedBackground)
    GitDiffLineKind.Removed -> SpanStyle(background = removedBackground)
    GitDiffLineKind.HunkHeader -> SpanStyle(background = separatorBackground, color = muted)
    GitDiffLineKind.NoNewline, GitDiffLineKind.Meta -> SpanStyle(color = muted)
    GitDiffLineKind.Context -> null
}

/**
 * One painted body line, for a body rendered line-by-line (the word-wrap variant). Same text and span
 * as the matching line inside [gitUnifiedDiffAnnotatedString], so the two renderings cannot drift.
 */
internal fun gitDiffUnifiedLineAnnotatedString(
    line: GitDiffLine,
    addedBackground: Color,
    removedBackground: Color,
    separatorBackground: Color,
    muted: Color,
    wordDiff: GitWordDiff? = null,
    wordAddedBackground: Color = Color.Unspecified,
    wordRemovedBackground: Color = Color.Unspecified,
): AnnotatedString = buildAnnotatedString {
    val span = gitDiffLineSpan(line, addedBackground, removedBackground, separatorBackground, muted)
    val wordBackground = if (line.kind == GitDiffLineKind.Removed) wordRemovedBackground else wordAddedBackground
    appendWordDiffLine(line, span, wordDiff, wordBackground)
}

/** The subset renderer behind the rich-preview toggle; see [parseMarkdownBlocks]. */
@Composable
private fun GitMarkdownPreview(path: String, text: String) {
    val blocks = remember(text) { parseMarkdownBlocks(text) }
    val accent = HarnessTokens.Colors.Accent
    Column(
        Modifier.fillMaxWidth().testTag("git-diff-rich-preview-$path").padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.One),
    ) {
        if (blocks.isEmpty()) {
            HarnessText("文件没有可预览的文本。", Modifier.testTag("git-diff-rich-preview-empty-$path"),
                style = MaterialTheme.typography.bodySmall, color = HarnessTokens.Colors.TextMuted)
        }
        blocks.forEach { block ->
            when (block) {
                is GitMarkdownBlock.Heading -> MarkdownInline(
                    block.text, accent,
                    Modifier.testTag("git-diff-rich-preview-heading-$path"),
                    style = when (block.level) {
                        1 -> MaterialTheme.typography.headlineSmall
                        2 -> MaterialTheme.typography.titleLarge
                        3 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.titleSmall
                    },
                )
                is GitMarkdownBlock.Paragraph -> MarkdownInline(block.text, accent, Modifier, MaterialTheme.typography.bodyMedium)
                is GitMarkdownBlock.Quote -> Row(Modifier.fillMaxWidth()) {
                    Box(Modifier.width(2.dp).height(IntrinsicSize.Max).background(HarnessTokens.Colors.BorderSubtle))
                    MarkdownInline(block.text, accent, Modifier.padding(start = 8.dp), MaterialTheme.typography.bodyMedium,
                        HarnessTokens.Colors.TextMuted)
                }
                is GitMarkdownBlock.Bullet -> MarkdownInline("• ${block.text}", accent, Modifier.padding(start = (block.depth * 12).dp),
                    MaterialTheme.typography.bodyMedium)
                is GitMarkdownBlock.Ordered -> MarkdownInline("${block.number}. ${block.text}", accent,
                    Modifier.padding(start = (block.depth * 12).dp), MaterialTheme.typography.bodyMedium)
                is GitMarkdownBlock.Code -> HarnessText(
                    block.text.ifEmpty { " " },
                    Modifier.fillMaxWidth().testTag("git-diff-rich-preview-code-$path")
                        .background(HarnessTokens.Colors.CodeBackground, RoundedCornerShape(6.dp))
                        .horizontalScroll(rememberScrollState()).padding(8.dp),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                )
                GitMarkdownBlock.Rule -> Box(Modifier.fillMaxWidth().height(1.dp).background(HarnessTokens.Colors.BorderSubtle))
            }
        }
    }
}

@Composable
private fun MarkdownInline(
    text: String,
    linkColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = LocalTextStyle.current,
    color: androidx.compose.ui.graphics.Color = HarnessTokens.Colors.Text,
) {
    val rendered = remember(text, linkColor, color) { markdownAnnotatedString(text, linkColor) }
    Text(rendered, modifier, color = color, style = style)
}

/** Inline runs keep every character: an unmatched marker stays literal text. */
internal fun markdownAnnotatedString(text: String, linkColor: androidx.compose.ui.graphics.Color): AnnotatedString =
    buildAnnotatedString {
        parseMarkdownInline(text).forEach { token ->
            val span = when (token.style) {
                GitMarkdownInline.Bold -> SpanStyle(fontWeight = FontWeight.SemiBold)
                GitMarkdownInline.Italic -> SpanStyle(fontStyle = FontStyle.Italic)
                GitMarkdownInline.Code -> SpanStyle(fontFamily = FontFamily.Monospace)
                GitMarkdownInline.Link -> SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
                GitMarkdownInline.Plain -> null
            }
            if (span == null) append(token.text) else withStyle(span) { append(token.text) }
        }
    }

/**
 * Split layout: one aligned row per change, old side on the left and new side on the right. Each pane
 * is preceded by its own line-number gutter (`[data-gutter]`); the gutters render outside the
 * SelectionContainer so copying a pane never picks up the numbers, and a wrapped line keeps its
 * number on the row it belongs to because the gutter and content share the row.
 */
@Composable
private fun GitDiffSplitView(file: GitDiffFile, wordDiffs: Boolean, hideImports: Boolean) {
    val hunks = remember(file.body, hideImports) {
        splitDiffBody(file.body).let { if (hideImports) gitDiffHiddenImportHunks(it) else it }
    }
    val added = HarnessTokens.Colors.DiffAddedBackground
    val removed = HarnessTokens.Colors.DiffRemovedBackground
    val wordAdded = HarnessTokens.Colors.DiffAdded.copy(alpha = WORD_DIFF_EMPHASIS_ALPHA)
    val wordRemoved = HarnessTokens.Colors.DiffRemoved.copy(alpha = WORD_DIFF_EMPHASIS_ALPHA)
    val gutterWidth = remember(hunks) { gitDiffSplitGutterWidth(hunks.flatMap { it.rows }) }
    val wordSpans = remember(hunks, wordDiffs) { hunks.map { if (wordDiffs) gitSplitWordDiff(it.rows) else emptyMap() } }
    val rowStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
    Column(
        Modifier.fillMaxWidth().testTag("git-diff-split").padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        hunks.forEachIndexed { hunkIndex, hunk ->
            SelectionContainer {
                HarnessText(hunk.header, Modifier.testTag("git-diff-split-hunk"),
                    style = rowStyle, color = HarnessTokens.Colors.TextMuted)
            }
            hunk.rows.forEachIndexed { rowIndex, row ->
                val diff = wordSpans[hunkIndex][rowIndex]
                Row(Modifier.fillMaxWidth().testTag("git-diff-split-row")) {
                    GitDiffSplitGutter(row, gutterWidth, left = true, added, removed, rowStyle,
                        Modifier.testTag("git-diff-split-left-number"))
                    SelectionContainer(Modifier.weight(1f)) {
                        Text(
                            gitDiffSplitPaneText(row, left = true, addedBackground = added, removedBackground = removed,
                                wordDiff = diff, wordAddedBackground = wordAdded, wordRemovedBackground = wordRemoved),
                            Modifier.testTag("git-diff-split-left"),
                            style = rowStyle,
                            color = HarnessTokens.Colors.Text,
                        )
                    }
                    GitDiffSplitGutter(row, gutterWidth, left = false, added, removed, rowStyle,
                        Modifier.testTag("git-diff-split-right-number"))
                    SelectionContainer(Modifier.weight(1f)) {
                        Text(
                            gitDiffSplitPaneText(row, left = false, addedBackground = added, removedBackground = removed,
                                wordDiff = diff, wordAddedBackground = wordAdded, wordRemovedBackground = wordRemoved),
                            Modifier.testTag("git-diff-split-right"),
                            style = rowStyle,
                            color = HarnessTokens.Colors.Text,
                        )
                    }
                }
            }
        }
    }
}

/**
 * One split-pane gutter cell: the unselectable line number, tinted on the changed side of the row
 * like the pane body. Drawn outside the [SelectionContainer] so a copied pane never includes it.
 */
@Composable
private fun GitDiffSplitGutter(
    row: GitDiffSplitRow,
    width: Int,
    left: Boolean,
    addedBackground: Color,
    removedBackground: Color,
    style: TextStyle,
    modifier: Modifier,
) {
    Text(
        gitDiffSplitGutterAnnotatedString(row, width, left, addedBackground, removedBackground),
        modifier,
        style = style,
        color = HarnessTokens.Colors.TextMuted,
    )
}
