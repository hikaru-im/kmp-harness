package im.hikaru.harness.client.projects

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import im.hikaru.harness.ui.foundation.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

@Composable
public fun ProjectFileSearchDialog(
    target: ProjectFileSearchTarget?,
    search: ProjectFileSearch,
    onDismiss: () -> Unit,
) {
    // Replacing the owner also disposes outstanding search/read effects and their visible results.
    key(target, search) {
        var query by remember { mutableStateOf("") }
        var result by remember { mutableStateOf<ProjectFileSearchResult?>(null) }
        var loading by remember { mutableStateOf(target != null) }
        var error by remember { mutableStateOf<String?>(null) }
        var retry by remember { mutableStateOf(0) }
        var selected by remember(query, result) { mutableStateOf(0) }
        var file by remember { mutableStateOf<ProjectFileMatch?>(null) }
        var content by remember { mutableStateOf<String?>(null) }
        var reading by remember { mutableStateOf(false) }
        var readError by remember { mutableStateOf<String?>(null) }
        var readRetry by remember { mutableStateOf(0) }
        val focus = remember { FocusRequester() }
        val previewFocus = remember { FocusRequester() }
        val list = rememberLazyListState()
        // Do not expose results from the previous query during the effect's next-frame handoff.
        var resultQuery by remember { mutableStateOf<String?>(null) }
        val files = result?.files.orEmpty().takeIf { resultQuery == query }.orEmpty()
        LaunchedEffect(query, retry) {
            if (target == null) return@LaunchedEffect
            loading = true; result = null; error = null
            try {
                delay(150)
                val found = search.search(target, query)
                currentCoroutineContext().ensureActive()
                result = found; resultQuery = query
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) {
                error = "无法搜索项目文件，请检查目录访问权限后重试。"
            } finally { loading = false }
        }
        LaunchedEffect(file, readRetry) {
            val selectedFile = file ?: return@LaunchedEffect
            val owner = target ?: return@LaunchedEffect
            reading = true; content = null; readError = null
            try {
                val text = search.read(owner, selectedFile)
                currentCoroutineContext().ensureActive()
                content = text
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) {
                readError = "无法预览此文件。仅支持项目内不超过 512 KiB 的 UTF-8 文本文件。"
            } finally { reading = false }
        }
        LaunchedEffect(file) {
            if (file == null) focus.requestFocus() else previewFocus.requestFocus()
        }
        LaunchedEffect(selected, files) {
            if (selected in files.indices) list.animateScrollToItem(selected)
        }
        fun open(match: ProjectFileMatch) {
            content = null; readError = null; reading = true; file = match
        }
        HarnessDialog(true, onDismiss, if (file == null) "搜索文件" else "文件预览 · 只读") {
            Column(Modifier.fillMaxWidth().heightIn(max = 560.dp)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) false else when (event.key) {
                        Key.Escape -> { onDismiss(); true }
                        Key.DirectionDown -> if (file == null) {
                            if (files.isNotEmpty()) selected = (selected + 1) % files.size
                            true
                        } else false
                        Key.DirectionUp -> if (file == null) {
                            if (files.isNotEmpty()) selected = (selected - 1 + files.size) % files.size
                            true
                        } else false
                        Key.Enter, Key.NumPadEnter -> if (file == null && files.isNotEmpty()) {
                            open(files[selected.coerceIn(files.indices)]); true
                        } else false
                        else -> false
                    }
                }.semantics { testTag = "project-file-search" },
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HarnessText(target?.project?.name ?: "请先选择项目", color = HarnessTokens.Colors.TextMuted)
                if (file == null) {
                    BasicTextField(query, { query = it }, singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = HarnessTokens.Colors.Text),
                        cursorBrush = SolidColor(HarnessTokens.Colors.Accent),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus)
                            .background(HarnessTokens.Colors.Surface).padding(10.dp)
                            .semantics { testTag = "project-file-search-input"; contentDescription = "搜索项目文件路径" })
                    when {
                        target == null -> HarnessText("请先在项目页或新聊天中选择项目，再搜索文件。")
                        loading || (error == null && resultQuery != query) -> HarnessText("正在搜索…", Modifier.semantics { testTag = "project-file-search-loading" })
                        error != null -> {
                            HarnessText(error!!, Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                            HarnessButton("重试", { retry++ }, testTag = "project-file-search-retry")
                        }
                        files.isEmpty() -> HarnessText("没有找到匹配的文件", Modifier.semantics { testTag = "project-file-search-empty" })
                        else -> {
                            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp), state = list) {
                                itemsIndexed(files, key = { _, item -> item.path }) { index, match ->
                                    HarnessText(match.path, Modifier.fillMaxWidth()
                                        .background(if (index == selected) HarnessTokens.Colors.SurfaceSelected else HarnessTokens.Colors.Surface)
                                        .clickable { open(match) }.padding(10.dp)
                                        .semantics { testTag = "project-file-result-${match.path}"; this.selected = index == selected })
                                }
                            }
                            if (result?.truncated == true) HarnessText("仅显示部分结果，请输入更具体的路径。", color = HarnessTokens.Colors.TextMuted)
                        }
                    }
                } else {
                    HarnessText(file!!.path,
                        Modifier.focusRequester(previewFocus).focusable().semantics { testTag = "project-file-preview-focus" })
                    when {
                        reading -> HarnessText("正在读取…")
                        readError != null -> {
                            HarnessText(readError!!, Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                            HarnessButton("重试", { readRetry++ }, testTag = "project-file-read-retry")
                        }
                        else -> SelectionContainer {
                            HarnessText(content.orEmpty(), Modifier.fillMaxWidth().heightIn(max = 400.dp)
                                .verticalScroll(rememberScrollState()).semantics { testTag = "project-file-preview" },
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace))
                        }
                    }
                    HarnessButton("返回搜索", { file = null; content = null; readError = null }, testTag = "project-file-search-back")
                }
                HarnessButton("关闭", onDismiss, testTag = "project-file-search-close")
            }
        }
    }
}
