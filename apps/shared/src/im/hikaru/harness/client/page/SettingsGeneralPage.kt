package im.hikaru.harness.client.page

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import im.hikaru.harness.ui.foundation.HarnessButton
import im.hikaru.harness.ui.foundation.HarnessButtonVariant
import im.hikaru.harness.ui.foundation.HarnessControlState
import im.hikaru.harness.ui.foundation.HarnessControlSize
import im.hikaru.harness.ui.foundation.HarnessMenu
import im.hikaru.harness.ui.foundation.HarnessMenuEntry
import im.hikaru.harness.ui.foundation.HarnessSelectTrigger
import im.hikaru.harness.ui.foundation.HarnessSwitch
import im.hikaru.harness.ui.foundation.HarnessText
import im.hikaru.harness.ui.foundation.HarnessTokens
import im.hikaru.harness.client.settings.CloseConfirmationPolicy
import im.hikaru.harness.client.settings.GeneralSettingsState
import im.hikaru.harness.client.settings.GeneralUnavailableReasons

/** The stable control kind used by one General preference row. */
public enum class SettingsGeneralControl {
    Toggle,
    Dropdown,
    Button,
    Segmented,
}

/** A static General-page row model, kept separate from the Compose layout for direct testing. */
public data class SettingsGeneralRow(
    val id: String,
    val title: String,
    val description: String,
    val control: SettingsGeneralControl,
    val initiallyChecked: Boolean? = null,
)

/** Evidence-backed content model for the default Settings > General page. */
public object SettingsGeneralModel {
    public val permissions: List<SettingsGeneralRow> =
        listOf(
            SettingsGeneralRow(
                id = "default-permission",
                title = "默认权限",
                description = "默认情况下，ChatGPT 可以读取和编辑其工作空间中的文件。需要时，它可以请求额外访问权限",
                control = SettingsGeneralControl.Toggle,
                initiallyChecked = true,
            ),
            SettingsGeneralRow(
                id = "full-disk-access",
                title = "完整访问权限",
                description = "当 ChatGPT 以完整访问权限运行时，它无需你的批准即可编辑你电脑上的任何文件，并运行可访问网络的命令。这会显著增加数据丢失、泄露或意外行为的风险。了解更多关于风险升高的信息。",
                control = SettingsGeneralControl.Toggle,
                initiallyChecked = true,
            ),
        )

    public val preferences: List<SettingsGeneralRow> =
        listOf(
            SettingsGeneralRow("task-folder", "无项目任务文件夹", "在项目外启动的任务默认存储数据的位置", SettingsGeneralControl.Button),
            SettingsGeneralRow("default-file-app", "默认文件打开位置", "默认打开文件和文件夹的位置", SettingsGeneralControl.Dropdown),
            SettingsGeneralRow("language", "语言", "应用界面语言", SettingsGeneralControl.Dropdown),
            SettingsGeneralRow("menu-bar", "在菜单栏中显示", "关闭主窗口后，仍在 macOS 菜单栏中保留 ChatGPT", SettingsGeneralControl.Toggle, true),
            SettingsGeneralRow("confirm-close", "Confirm before closing a window", "Warn when a tab-close shortcut would close the window", SettingsGeneralControl.Dropdown),
            SettingsGeneralRow("full-view", "默认采用完整视图", "开始新任务时，在同一标签页中显示聊天和内容", SettingsGeneralControl.Toggle, true),
            SettingsGeneralRow("bottom-panel", "底部面板", "在应用标题栏中显示底部面板控件", SettingsGeneralControl.Toggle, true),
            SettingsGeneralRow("terminal-location", "默认终端位置", "选择终端快捷键和环境操作在何处打开终端标签页", SettingsGeneralControl.Segmented),
            SettingsGeneralRow("prevent-sleep", "运行时防止系统休眠", "在 ChatGPT 运行任务时，让电脑保持唤醒状态", SettingsGeneralControl.Toggle, true),
            SettingsGeneralRow("compress-history", "压缩本地保存的聊天历史记录", "压缩较早的本地聊天历史记录，以节省磁盘空间。重启 ChatGPT 后更改生效。", SettingsGeneralControl.Toggle, false),
            SettingsGeneralRow("prompt-suggestions", "提示词建议", "通过搜索项目文件和已连接的应用，建议下一步操作", SettingsGeneralControl.Toggle, true),
            SettingsGeneralRow("licenses", "打开源许可证", "捆绑依赖项的第三方声明", SettingsGeneralControl.Button),
            SettingsGeneralRow("plugins", "插件", "允许 ChatGPT 使用已安装的插件", SettingsGeneralControl.Toggle, true),
        )

    // The measured geometry lives in [HarnessTokens.Control] so every Settings page (this page and the
    // [SettingsFormFrame] pages) shares one source. These integer views keep the existing call sites and
    // the model test that pins the measured values.
    public val ContentWidth: Int = HarnessTokens.Control.SettingsContentMaxWidth.value.toInt()
    public val CardRadius: Int = HarnessTokens.Control.SettingsCardRadius.value.toInt()
    public val PageTopPadding: Int = HarnessTokens.Control.SettingsPageTopPadding.value.toInt()
    /**
     * Inset of a card row's content, and of the separator between two rows. The 2026-10-06 capture
     * measures both at 17px from the card edge (card x 1079..1806, row ink and separator x 1096..1789).
     */
    public val RowInsetX: Int = HarnessTokens.Control.SettingsRowInsetX.value.toInt()
    public val PreferenceRowHeights: List<Int> = listOf(61, 59, 60, 59, 60, 60, 59, 60, 59, 60, 59, 60, 61)
}

/** The default Settings > General content surface. */
@Composable
public fun SettingsGeneralPage(
    state: GeneralSettingsState = GeneralSettingsState(),
    onClosePolicyChange: (CloseConfirmationPolicy) -> Unit = {},
    onRetry: () -> Unit = {},
    onOpenPlugins: (() -> Unit)? = null,
) {
    val scrollState = rememberScrollState()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val compact = maxWidth <= 600.dp
        // The shell already caps the Settings content column at [SettingsGeneralModel.ContentWidth], so
        // the 728px column plus its -8px offset is used whenever 728 fits; otherwise the column falls
        // back to the available width with a 16px inner inset.
        val narrowContent = maxWidth < SettingsGeneralModel.ContentWidth.dp
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(top = SettingsGeneralModel.PageTopPadding.dp, bottom = HarnessTokens.Spacing.Seven),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Seven),
        ) {
            Column(
                modifier = Modifier.widthIn(max = SettingsGeneralModel.ContentWidth.dp).fillMaxWidth()
                    .then(
                        if (narrowContent) Modifier.padding(horizontal = 16.dp)
                        else Modifier.offset(x = (-8).dp),
                    ),
                verticalArrangement = Arrangement.spacedBy(46.dp),
            ) {
                HarnessText(
                    "常规",
                    // The capture's page title measures ~28px: its "常规" ink is 53x26 where the same
                    // two glyphs at the 24px [HarnessTokens.Text.HeadingLg] render 45x23 (and the
                    // plugins page title in the same capture set measures 46x23 at 24px).
                    // The line box stays at the 32px the capture's title-to-section gap implies; only the
                    // glyph size grows to the measured 28px.
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = 28.sp, lineHeight = 32.sp),
                    color = HarnessTokens.Colors.Text,
                    // The 28px glyphs fit the capture's 34px title line box; the font's own 39px box
                    // would push every section below it 5px down.
                    modifier = Modifier.height(34.dp).semantics { testTag = "settings-general-title" },
                )
                if (state.loading || state.saving || state.loadError != null || state.saveError != null || !state.durable) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (state.loading) GeneralStatus("正在读取常规设置…", "general-loading")
                        if (state.saving) GeneralStatus("正在保存常规设置…", "general-saving")
                        if (!state.durable) GeneralStatus("设置仅在本次运行中保留", "general-session-only")
                        state.loadError?.let { GeneralStatus(it, "general-load-error") }
                        state.saveError?.let { GeneralStatus(it, "general-save-error") }
                        if (state.loadError != null || state.saveError != null) {
                            HarnessButton("重试", onRetry, enabled = !state.loading && !state.saving,
                                testTag = "general-retry")
                        }
                    }
                }
                SettingsSection(title = "权限", testTag = "settings-permissions") {
                    SettingsGeneralModel.permissions.forEachIndexed { index, row ->
                        SettingsRow(
                            row = row,
                            showDivider = index > 0,
                            minHeight = if (row.id == "default-permission") 77.dp else 93.dp,
                            compact = compact, state = state,
                            onClosePolicyChange = onClosePolicyChange, onOpenPlugins = onOpenPlugins,
                        )
                    }
                }
                SettingsSection(
                    title = "常规",
                    testTag = "settings-preferences",
                    modifier = Modifier.padding(top = 5.dp),
                ) {
                    SettingsGeneralModel.preferences.forEachIndexed { index, row ->
                        SettingsRow(
                            row = if (row.id == "plugins") row.copy(control = SettingsGeneralControl.Button) else row,
                            showDivider = index > 0,
                            minHeight = SettingsGeneralModel.PreferenceRowHeights[index].dp,
                            compact = compact, state = state,
                            onClosePolicyChange = onClosePolicyChange, onOpenPlugins = onOpenPlugins,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    testTag: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(15.dp)) {
        HarnessText(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = HarnessTokens.Colors.Text,
            modifier = Modifier.semantics { this.testTag = testTag + "-title" },
        )
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(SettingsGeneralModel.CardRadius.dp))
                    .border(1.dp, HarnessTokens.Colors.BorderSubtle, RoundedCornerShape(SettingsGeneralModel.CardRadius.dp))
                    .background(HarnessTokens.Colors.SurfaceElevated)
                    .semantics { this.testTag = testTag + "-card" },
            content = { content() },
        )
    }
}

@Composable
private fun GeneralStatus(text: String, tag: String) {
    HarnessText(text, color = HarnessTokens.Colors.TextMuted,
        modifier = Modifier.semantics { testTag = tag })
}

@Composable
private fun SettingsRow(
    row: SettingsGeneralRow,
    showDivider: Boolean,
    minHeight: Dp,
    compact: Boolean,
    state: GeneralSettingsState,
    onClosePolicyChange: (CloseConfirmationPolicy) -> Unit,
    onOpenPlugins: (() -> Unit)?,
) {
    if (showDivider) HorizontalDivider(
        color = HarnessTokens.Colors.BorderSubtle,
        modifier = Modifier.padding(horizontal = SettingsGeneralModel.RowInsetX.dp),
    )
    val unavailable = when (row.id) {
        "confirm-close" -> if (state.closePolicyAvailable) null else "本平台尚未接入窗口关闭策略"
        "plugins" -> if (onOpenPlugins != null) null else "本平台尚未接入插件管理"
        else -> GeneralUnavailableReasons.forRow(row.id)
    }
    val rowModifier = Modifier.fillMaxWidth().heightIn(min = minHeight)
        .padding(horizontal = SettingsGeneralModel.RowInsetX.dp, vertical = HarnessTokens.Spacing.Two)
        .semantics { testTag = "settings-general-row-" + row.id }
    val description = if (row.id == "plugins") "查看和管理已安装的插件" else row.description
    val text: @Composable (Modifier) -> Unit = { modifier ->
        Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            HarnessText(row.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = HarnessTokens.Colors.Text)
            HarnessText(description, style = MaterialTheme.typography.bodySmall, color = HarnessTokens.Colors.TextMuted)
            unavailable?.let {
                HarnessText(it, style = MaterialTheme.typography.bodySmall, color = HarnessTokens.Colors.TextMuted,
                    modifier = Modifier.semantics { testTag = "settings-general-unavailable-" + row.id })
            }
        }
    }
    val control: @Composable () -> Unit = {
        val tag = "settings-general-control-" + row.id
        when (row.control) {
            SettingsGeneralControl.Toggle -> HarnessSwitch(
                checked = false, onCheckedChange = {}, enabled = false,
                contentDescription = row.title, testTag = tag,
            )
            SettingsGeneralControl.Dropdown -> {
                if (row.id == "confirm-close") GeneralClosePolicyMenu(state, onClosePolicyChange, tag)
                else HarnessSelectTrigger(
                    text = if (row.id == "language") "中文" else "尚未配置",
                    onClick = {}, enabled = false, testTag = tag,
                )
            }
            SettingsGeneralControl.Button -> HarnessButton(
                text = when (row.id) { "plugins" -> "管理插件"; "task-folder" -> "更改"; else -> "查看" },
                onClick = { if (row.id == "plugins") onOpenPlugins?.invoke() },
                enabled = row.id == "plugins" && onOpenPlugins != null,
                variant = HarnessButtonVariant.Secondary, size = HarnessControlSize.Compact, testTag = tag,
            )
            SettingsGeneralControl.Segmented -> Row(
                Modifier.height(28.dp).clip(RoundedCornerShape(HarnessTokens.Corners.Medium))
                    .background(HarnessTokens.Colors.SurfaceSecondary).semantics { testTag = tag },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                listOf("bottom" to "底部", "right" to "右侧").forEach { (key, label) ->
                    HarnessButton(label, {}, enabled = false, variant = HarnessButtonVariant.Ghost,
                        size = HarnessControlSize.Compact, testTag = "$tag-$key")
                }
            }
        }
    }
    if (compact) {
        Column(rowModifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            text(Modifier.fillMaxWidth())
            control()
        }
    } else {
        Row(rowModifier, verticalAlignment = Alignment.CenterVertically) {
            text(Modifier.weight(1f).offset(y = 2.dp))
            Box(Modifier.padding(start = HarnessTokens.Spacing.Three)) { control() }
        }
    }
}

@Composable
private fun GeneralClosePolicyMenu(
    state: GeneralSettingsState,
    onSelect: (CloseConfirmationPolicy) -> Unit,
    testTag: String,
) {
    var expanded by remember { mutableStateOf(false) }
    HarnessMenu(
        expanded = expanded && state.canEdit,
        entries = CloseConfirmationPolicy.entries.map { policy ->
            HarnessMenuEntry.Item(key = policy.name, text = policy.label,
                selected = policy == state.settings.closeConfirmation, enabled = state.canEdit)
        },
        onExpandedChange = { expanded = it && state.canEdit },
        onSelect = { key -> onSelect(CloseConfirmationPolicy.valueOf(key)) },
        anchor = { toggle ->
            HarnessSelectTrigger(
                text = state.settings.closeConfirmation.label,
                onClick = toggle, enabled = state.canEdit,
                state = if (state.saving) HarnessControlState.Loading else HarnessControlState.Normal,
                testTag = testTag,
            )
        },
    )
}
