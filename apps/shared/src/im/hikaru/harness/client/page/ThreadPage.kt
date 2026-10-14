package im.hikaru.harness.client.page

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import im.hikaru.harness.client.fixture.ReferenceBlock
import im.hikaru.harness.client.fixture.ReferenceBlockKind
import im.hikaru.harness.client.fixture.ReferenceChangedFile
import im.hikaru.harness.client.fixture.ReferenceThread
import im.hikaru.harness.client.fixture.ReferenceTurn
import im.hikaru.harness.ui.foundation.HarnessIcon
import im.hikaru.harness.ui.foundation.HarnessIconButton
import im.hikaru.harness.ui.foundation.HarnessIconName
import im.hikaru.harness.ui.foundation.HarnessSurface
import im.hikaru.harness.ui.foundation.HarnessText
import im.hikaru.harness.ui.foundation.HarnessTokens
import im.hikaru.harness.ui.foundation.LocalHarnessContentTextStyle
import im.hikaru.harness.ui.foundation.LocalHarnessCodeTextStyle
import im.hikaru.harness.ui.foundation.LocalHarnessThemeCustomization

/** Thread surface: the main acceptance page. Timeline, change cards and scroll area. */
@Composable
public fun ThreadPage(
    thread: ReferenceThread,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Five),
        contentPadding = PaddingValues(vertical = HarnessTokens.Spacing.Five),
    ) {
        items(thread.turns) { turn -> TurnView(turn) }
    }
}

@Composable
private fun TurnView(turn: ReferenceTurn) {
    if (turn.isUser) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Column(
                modifier =
                    Modifier
                        .widthIn(max = 420.dp)
                        .clip(RoundedCornerShape(HarnessTokens.Corners.Panel))
                        .background(HarnessTokens.Colors.SurfaceSecondary)
                        .padding(horizontal = HarnessTokens.Spacing.Four, vertical = HarnessTokens.Spacing.Three),
                verticalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Two),
            ) {
                turn.blocks.forEach { block -> BlockView(block) }
            }
        }
        return
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Three),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Two),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(18.dp).clip(CircleShape).background(HarnessTokens.Colors.Text),
                contentAlignment = Alignment.Center,
            ) {
                HarnessText(
                    "H",
                    color = HarnessTokens.Colors.Canvas,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            HarnessText(turn.author, color = HarnessTokens.Colors.TextMuted, style = MaterialTheme.typography.labelLarge)
        }
        turn.blocks.forEach { block -> BlockView(block) }
    }
}

@Composable
private fun BlockView(block: ReferenceBlock) {
    when (block.kind) {
        ReferenceBlockKind.Paragraph ->
            HarnessText(block.text, color = HarnessTokens.Colors.Text, style = LocalHarnessContentTextStyle.current)

        ReferenceBlockKind.Code -> CodeBlock(block)
        ReferenceBlockKind.Checklist -> ChecklistBlock(block)
        ReferenceBlockKind.ChangeCard -> ChangeCard(block)
    }
}

@Composable
private fun CodeBlock(block: ReferenceBlock) {
    Column(verticalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.One)) {
        block.title?.let { HarnessText(it, color = HarnessTokens.Colors.TextMuted, style = MaterialTheme.typography.labelMedium) }
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(HarnessTokens.Corners.Medium))
                    .background(HarnessTokens.Colors.CodeBackground)
                    .padding(HarnessTokens.Spacing.Three),
        ) {
            HarnessText(
                block.text,
                color = HarnessTokens.Colors.Text,
                style = LocalHarnessCodeTextStyle.current,
            )
        }
    }
}

@Composable
private fun ChecklistBlock(block: ReferenceBlock) {
    HarnessSurface(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(HarnessTokens.Spacing.Three),
            verticalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Two),
        ) {
            block.title?.let { HarnessText(it, color = HarnessTokens.Colors.TextMuted, style = MaterialTheme.typography.labelMedium) }
            block.items.forEach { item ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Two),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HarnessIcon(
                        name = if (item.done) HarnessIconName.Check else HarnessIconName.ChevronRight,
                        tint = if (item.done) HarnessTokens.Colors.Success else HarnessTokens.Colors.TextMuted,
                        size = 16.dp,
                    )
                    HarnessText(item.label, color = HarnessTokens.Colors.TextMuted)
                }
            }
        }
    }
}

@Composable
private fun ChangeCard(block: ReferenceBlock) {
    HarnessSurface(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(HarnessTokens.Spacing.Three),
                horizontalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Two),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HarnessIcon(HarnessIconName.Copy, tint = HarnessTokens.Colors.TextMuted, size = 16.dp)
                HarnessText(
                    block.title ?: "变更",
                    modifier = Modifier.weight(1f),
                    color = HarnessTokens.Colors.Text,
                    style = MaterialTheme.typography.labelLarge,
                )
                HarnessText(
                    block.files.size.toString() + " 个文件",
                    color = HarnessTokens.Colors.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            HorizontalDivider(color = HarnessTokens.Colors.BorderSubtle)
            block.files.forEach { file -> ChangeFileRow(file) }
        }
    }
}

@Composable
private fun ChangeFileRow(file: ReferenceChangedFile) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = HarnessTokens.Spacing.Three, vertical = HarnessTokens.Spacing.Two),
        horizontalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Two),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HarnessText(
            file.path,
            modifier = Modifier.weight(1f),
            color = HarnessTokens.Colors.Text,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = HarnessTokens.Fonts.Code),
            maxLines = 1,
        )
        HarnessText(
            "+" + file.added.toString(),
            color = if (LocalHarnessThemeCustomization.current.diffSymbols) HarnessTokens.Colors.Text else HarnessTokens.Colors.DiffAdded,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
        )
        HarnessText(
            "-" + file.removed.toString(),
            color = if (LocalHarnessThemeCustomization.current.diffSymbols) HarnessTokens.Colors.Text else HarnessTokens.Colors.DiffRemoved,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
        )
    }
}

/** Right-hand Project Context Panel content. */
@Composable
public fun ProjectContextPanel(
    thread: ReferenceThread,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Three),
    ) {
        HarnessText("项目上下文", color = HarnessTokens.Colors.Text, style = MaterialTheme.typography.titleMedium)
        ContextRow("项目", thread.projectName)
        ContextRow("分支", thread.context.branch)
        ContextRow("Host", thread.context.host)
        ContextRow("路径", thread.context.workspacePath)
        HorizontalDivider(color = HarnessTokens.Colors.BorderSubtle)
        HarnessText("变更", color = HarnessTokens.Colors.TextMuted, style = MaterialTheme.typography.labelMedium)
        thread.context.changedFiles.forEach { file ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Two),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HarnessText(
                    file.path,
                    modifier = Modifier.weight(1f),
                    color = HarnessTokens.Colors.Text,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = HarnessTokens.Fonts.Code),
                    maxLines = 1,
                )
                HarnessText(
                    "+" + file.added.toString() + " -" + file.removed.toString(),
                    color = HarnessTokens.Colors.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                )
            }
        }
        HorizontalDivider(color = HarnessTokens.Colors.BorderSubtle)
        HarnessText("任务", color = HarnessTokens.Colors.TextMuted, style = MaterialTheme.typography.labelMedium)
        thread.context.tasks.forEach { task ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Two),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.size(6.dp).background(HarnessTokens.Colors.Accent, CircleShape))
                HarnessText(task, modifier = Modifier.weight(1f), color = HarnessTokens.Colors.TextMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ContextRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.One)) {
        HarnessText(label, color = HarnessTokens.Colors.TextMuted, style = MaterialTheme.typography.labelMedium)
        HarnessText(value, color = HarnessTokens.Colors.Text, style = MaterialTheme.typography.bodySmall, maxLines = 1)
    }
}

/** Thread composer dock: rounded input with attachment and send controls. */
@Composable
public fun ThreadComposer(
    draft: String,
    onDraftChange: (String) -> Unit,
    placeholder: String = "要求后续变更",
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(HarnessTokens.Control.ComposerRadius))
                .background(HarnessTokens.Colors.SurfaceElevated)
                .border(1.dp, HarnessTokens.Colors.SurfaceRing, RoundedCornerShape(HarnessTokens.Control.ComposerRadius))
                .padding(HarnessTokens.Control.ComposerGutter),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Three)) {
            Box(modifier = Modifier.fillMaxWidth().height(24.dp), contentAlignment = Alignment.CenterStart) {
                if (draft.isEmpty()) {
                    HarnessText(placeholder, color = HarnessTokens.Colors.TextMuted, maxLines = 1)
                }
                BasicTextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = HarnessTokens.Colors.Text),
                    cursorBrush = SolidColor(HarnessTokens.Colors.Accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Two),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HarnessIconButton(contentDescription = "Attach", onClick = {}) {
                    HarnessIcon(HarnessIconName.Attach, tint = HarnessTokens.Colors.TextMuted, size = 16.dp)
                }
                HarnessText("本地", color = HarnessTokens.Colors.TextMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                Spacer(Modifier.weight(1f))
                Box(
                    modifier =
                        Modifier
                            .size(HarnessTokens.Control.IconButton)
                            .clip(CircleShape)
                            .background(HarnessTokens.Colors.Accent),
                    contentAlignment = Alignment.Center,
                ) {
                    HarnessIcon(HarnessIconName.Send, tint = HarnessTokens.Colors.OnAccent, size = 16.dp)
                }
            }
        }
    }
}
