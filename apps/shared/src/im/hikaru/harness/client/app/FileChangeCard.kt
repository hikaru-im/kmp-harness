package im.hikaru.harness.client.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import im.hikaru.harness.client.connection.FileChangeContent
import im.hikaru.harness.client.connection.FileChangeKind
import im.hikaru.harness.ui.foundation.*

/** A turn's changed files, folded into one card group sorted by path. */
internal const val FileChangePatchDisplayLines: Int = 400

@Composable
internal fun FileChangeCard(
    files: List<FileChangeContent>,
    tag: String,
    onOpenInGit: ((path: String) -> Unit)? = null,
) {
    if (files.isEmpty()) return
    val ordered = remember(files) { files.sortedBy { it.path } }
    val added = ordered.sumOf { it.added }
    val removed = ordered.sumOf { it.removed }
    var expanded by remember(ordered) { mutableStateOf(emptySet<String>()) }
    Column(Modifier.fillMaxWidth()
        .background(ConversationAppearance.codeBackground, RoundedCornerShape(12.dp))
        .border(1.dp, ConversationAppearance.popupRing, RoundedCornerShape(12.dp))
        .semantics { testTag = tag }
        .padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            HarnessText("${ordered.size} 个文件已更改", Modifier.semantics { testTag = "$tag:header" },
                color = ConversationAppearance.ink,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
                maxLines = 1)
            Spacer(Modifier.weight(1f))
            HarnessText("+$added", color = HarnessTokens.Colors.DiffAdded,
                style = MaterialTheme.typography.bodySmall, maxLines = 1)
            HarnessText(" −$removed", color = HarnessTokens.Colors.DiffRemoved,
                style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
        ordered.forEach { file ->
            key(file.path) {
                FileChangeRow(file, tag, file.path in expanded, onOpenInGit) { open ->
                    expanded = if (open) expanded + file.path else expanded - file.path
                }
            }
        }
    }
}

@Composable
private fun FileChangeRow(
    file: FileChangeContent,
    tag: String,
    expanded: Boolean,
    onOpenInGit: ((path: String) -> Unit)?,
    onExpandedChange: (Boolean) -> Unit,
) {
    val canOpenInGit = file.kind != FileChangeKind.DELETED && onOpenInGit != null
    val expandable = file.patch != null || file.truncated || canOpenInGit
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()
            .then(if (expandable) Modifier.clickable { onExpandedChange(!expanded) } else Modifier)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .semantics { testTag = "$tag:file:${file.path}" },
            verticalAlignment = Alignment.CenterVertically) {
            HarnessText(fileChangeKindLabel(file.kind), color = fileChangeKindInk(file.kind),
                style = MaterialTheme.typography.bodySmall, maxLines = 1)
            HarnessText(fileChangePathLabel(file), Modifier.weight(1f).padding(start = 8.dp),
                color = ConversationAppearance.ink,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            HarnessText("+${file.added}", color = HarnessTokens.Colors.DiffAdded,
                style = MaterialTheme.typography.bodySmall, maxLines = 1)
            HarnessText(" −${file.removed}", color = HarnessTokens.Colors.DiffRemoved,
                style = MaterialTheme.typography.bodySmall, maxLines = 1)
            if (expandable) {
                // Same chevron the transcript's activity rows use, rotated open like Codex's diff card.
                Box(Modifier.padding(start = 6.dp).rotate(if (expanded) 90f else 0f)) {
                    HarnessIcon(HarnessIconName.ChevronRight, tint = ConversationAppearance.tertiary, size = 12.dp)
                }
            }
        }
        if (expanded) {
            Column(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 8.dp)
                .semantics { testTag = "$tag:body:${file.path}" },
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                when {
                    // A producer that withheld the body offers the summary and the notice only.
                    file.truncated -> HarnessText("内容过大，无法内联渲染",
                        Modifier.semantics { testTag = "$tag:truncated:${file.path}" },
                        color = ConversationAppearance.muted, style = MaterialTheme.typography.bodySmall)
                    file.patch != null -> FileChangePatch(file.patch, tag, file.path)
                }
                if (canOpenInGit) {
                    HarnessButton("在 Git 中查看", { onOpenInGit?.invoke(file.path) },
                        variant = HarnessButtonVariant.Ghost, size = HarnessControlSize.Compact,
                        testTag = "$tag:open:${file.path}")
                }
            }
        }
    }
}

@Composable
private fun FileChangePatch(patch: String, tag: String, path: String) {
    val lines = remember(patch) { patch.lines() }
    Column(Modifier.fillMaxWidth().semantics { testTag = "$tag:patch:$path" }) {
        lines.take(FileChangePatchDisplayLines).forEach { line ->
            HarnessText(line.ifEmpty { " " }, Modifier.fillMaxWidth()
                .background(fileChangeLineBackground(line))
                .padding(horizontal = 6.dp),
                color = ConversationAppearance.ink,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                overflow = TextOverflow.Visible)
        }
        if (lines.size > FileChangePatchDisplayLines) {
            HarnessText("补丁过长，仅显示前 $FileChangePatchDisplayLines 行",
                Modifier.padding(top = 4.dp).semantics { testTag = "$tag:patch-truncated:$path" },
                color = ConversationAppearance.muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun fileChangeLineBackground(line: String): androidx.compose.ui.graphics.Color = when {
    line.startsWith("+++") || line.startsWith("---") -> androidx.compose.ui.graphics.Color.Transparent
    line.startsWith("+") -> HarnessTokens.Colors.DiffAddedBackground
    line.startsWith("-") -> HarnessTokens.Colors.DiffRemovedBackground
    else -> androidx.compose.ui.graphics.Color.Transparent
}

private fun fileChangeKindLabel(kind: FileChangeKind): String = when (kind) {
    FileChangeKind.ADDED -> "已创建"
    FileChangeKind.MODIFIED -> "已编辑"
    FileChangeKind.DELETED -> "已删除"
    FileChangeKind.RENAMED -> "已重命名"
}

@Composable
private fun fileChangeKindInk(kind: FileChangeKind): androidx.compose.ui.graphics.Color = when (kind) {
    FileChangeKind.ADDED -> HarnessTokens.Colors.DiffAdded
    FileChangeKind.DELETED -> HarnessTokens.Colors.DiffRemoved
    FileChangeKind.MODIFIED, FileChangeKind.RENAMED -> ConversationAppearance.muted
}

private fun fileChangePathLabel(file: FileChangeContent): String =
    if (file.kind == FileChangeKind.RENAMED && file.previousPath != null) "${file.previousPath} → ${file.path}"
    else file.path
