package im.hikaru.harness.client.scheduling

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import im.hikaru.harness.ui.foundation.*

/** Page-only layout, with no service/store ownership. */
@Composable
internal fun ScheduledPageLanding(state: ScheduledManagementState,
    onTemplate: (String, String, ScheduledFrequency) -> Unit, onHistory: (() -> Unit)?) {
    BoxWithConstraints(Modifier.fillMaxSize().semantics { testTag = "scheduled-landing" }) {
        val columns = if (maxWidth >= 680.dp) 2 else 1
        val viewportHeight = maxHeight
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .semantics { testTag = "scheduled-landing-scroll" }) {
            Column(Modifier.fillMaxWidth().heightIn(min = viewportHeight).padding(24.dp),
                verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Column(Modifier.widthIn(max = 738.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val clockColor = HarnessTokens.Colors.TextMuted
                    // The reference clock ink is 48x47, so the box is 48 with the 2dp ring drawn on
                    // the box edge; the old 44dp box plus its 3dp inset made the ink 4px smaller.
                    Canvas(Modifier.size(48.dp).semantics { testTag = "scheduled-landing-clock" }) {
                        drawCircle(clockColor, radius = size.minDimension / 2 - 1.dp.toPx(), style = Stroke(2.dp.toPx()))
                        drawLine(clockColor, center, Offset(center.x, size.height * .22f), 2.dp.toPx())
                        drawLine(clockColor, center, Offset(size.width * .32f, size.height * .62f), 2.dp.toPx())
                    }
                    // The reference pairs the heading with its line on a tight 4px gap: the ink gap is
                    // 11px, where the surrounding 12dp rhythm measured 19px before this change.
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.One)) {
                        HarnessText("安排任务", Modifier.semantics { testTag = "scheduled-management-title" },
                            style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp, lineHeight = 26.sp))
                        HarnessText("安排工作区中的重复工作。",
                            color = HarnessTokens.Colors.TextMuted, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (state.tasks.isEmpty()) HarnessText("还没有定时任务。", Modifier.semantics { testTag = "scheduled-empty" },
                        color = HarnessTokens.Colors.TextMuted)
                    HarnessText("示例模板 · 点击编辑，不会保存或运行", color = HarnessTokens.Colors.TextMuted,
                        style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    // The reference template grid uses a 20px gutter on both axes and a 738px outer
                    // width (card 1 = x 1080..1438, card 2 starts at 1459), independent of the 12dp
                    // rhythm the clock / title / description block keeps above it.
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        templates.chunked(columns).forEach { row ->
                            // The reference grid keeps both cards in a row the same height; without
                            // this a description that wraps to two lines would leave a ragged row.
                            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                                horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                                row.forEach { template ->
                                    val enabled = state.canMutate && state.draft == null && state.context.targets.isNotEmpty()
                                    // Reference template card: a 1px 3-on/2-off dashed outline (not a solid
                                    // hairline) with a ~22px corner, pinned by HarnessTokens.Corners.Card.
                                    HarnessSurface(
                                        Modifier.weight(1f).fillMaxHeight().clickable(enabled = enabled, role = Role.Button) {
                                            onTemplate(template.name, template.prompt, template.frequency)
                                        }.semantics { testTag = "scheduled-template:" + template.id },
                                        dashed = true,
                                        radius = HarnessTokens.Corners.Card,
                                    ) {
                                        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            HarnessText(template.name,
                                                style = MaterialTheme.typography.titleSmall.copy(fontSize = 16.sp, lineHeight = 24.sp))
                                            HarnessText(template.prompt, color = HarnessTokens.Colors.TextMuted,
                                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp, lineHeight = 18.sp), maxLines = 3)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    onHistory?.let { HarnessButton("查看已保存的运行记录", it, variant = HarnessButtonVariant.Ghost,
                        testTag = "scheduled-show-history") }
                }
            }
        }
    }
}

private data class PageTemplate(val id: String, val name: String, val prompt: String, val frequency: ScheduledFrequency)
private val templates = listOf(
    PageTemplate("workspace", "工作区摘要", "总结当前工作区的进展、风险和下一步。", ScheduledFrequency.Daily),
    PageTemplate("changes", "变更回顾", "回顾工作区近期变更，列出需要关注的问题。", ScheduledFrequency.Weekdays),
    PageTemplate("notes", "每周工作笔记", "根据工作区已有内容整理本周工作笔记。", ScheduledFrequency.Weekly),
    PageTemplate("todos", "待办梳理", "检查工作区中的待办记录，整理优先级。", ScheduledFrequency.Daily),
    PageTemplate("docs", "文档检查", "检查工作区文档，列出过时或缺失的说明。", ScheduledFrequency.Weekly),
    PageTemplate("review", "定期代码回顾", "回顾工作区代码，汇总可改进之处，不自动修改文件。", ScheduledFrequency.Hourly),
)

/** The displayed value stays complete and selectable without growing the page horizontally. */
@Composable
internal fun ScheduledPageValue(value: String, tag: String) {
    val clipboard = LocalClipboardManager.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SelectionContainer(Modifier.weight(1f)) {
            HarnessText(value, Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                .semantics { testTag = tag }, maxLines = 1, overflow = TextOverflow.Clip)
        }
        HarnessButton("复制", { clipboard.setText(AnnotatedString(value)) },
            variant = HarnessButtonVariant.Ghost, size = HarnessControlSize.Compact, testTag = tag + "-copy")
    }
}

/** Keying expansion with the full page fence closes every stale popup synchronously. */
@Composable
internal fun <T> ScheduledPagePicker(value: T, options: List<T>, label: (T) -> String,
    enabled: Boolean, tag: String, fence: Any, onSelect: (T) -> Unit) {
    // Index keys refer only to this ordered value snapshot, never to a later catalog.
    val snapshot = options.toList()
    val token = remember(fence, enabled, value, snapshot) { Any() }
    val activeToken = remember { mutableStateOf<Any?>(null) }
    activeToken.value = token
    val latestOptions by rememberUpdatedState(snapshot)
    val latestEnabled by rememberUpdatedState(enabled)
    val latestSelect by rememberUpdatedState(onSelect)
    // A retained semantic action must keep its original callback and popup lifetime.
    key(token) {
        var expanded by remember { mutableStateOf(false) }
        DisposableEffect(token) {
            onDispose {
                expanded = false
                if (activeToken.value === token) activeToken.value = null
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val popupWidth = minOf(maxWidth, 520.dp)
            Column(Modifier.fillMaxWidth()) {
                HarnessMenu(
                    expanded = expanded,
                    entries = snapshot.mapIndexed { index, option -> HarnessMenuEntry.Item(
                        key = index.toString(), text = label(option), enabled = enabled, selected = option == value,
                        testTag = tag + "-option:" + index,
                    ) },
                    onExpandedChange = { open ->
                        if (activeToken.value === token) expanded = open && latestEnabled
                    },
                    onSelect = { optionKey ->
                        val index = optionKey.toIntOrNull()
                        if (activeToken.value === token && latestEnabled && latestOptions == snapshot &&
                            index != null && index in snapshot.indices && snapshot[index] in latestOptions) {
                            latestSelect(snapshot[index])
                        }
                    },
                    anchor = { toggle -> HarnessButton(label(value), toggle, Modifier.fillMaxWidth(),
                        enabled = enabled, variant = HarnessButtonVariant.Secondary, testTag = tag) },
                    modifier = Modifier.fillMaxWidth(),
                    popupModifier = Modifier.semantics { testTag = tag + "-menu" },
                    popupWidth = popupWidth, popupMaxHeight = 280.dp, wrapLabels = true,
                    enabled = enabled, selectionScope = token,
                )
                ScheduledPageValue(label(value), tag + "-value")
            }
        }
    }
}
