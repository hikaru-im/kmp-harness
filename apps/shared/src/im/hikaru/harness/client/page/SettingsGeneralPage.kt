package im.hikaru.harness.client.page

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.unit.dp
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
                description = "允许读写工作空间中的文件；额外访问需要授权。",
                control = SettingsGeneralControl.Toggle,
                initiallyChecked = true,
            ),
            SettingsGeneralRow(
                id = "full-disk-access",
                title = "完整访问权限",
                description = "允许访问工作空间以外的文件并运行联网命令，无需逐次批准。可能造成数据丢失或泄露。",
                control = SettingsGeneralControl.Toggle,
                initiallyChecked = true,
            ),
        )

    public val preferences: List<SettingsGeneralRow> =
        listOf(
            SettingsGeneralRow("task-folder", "无项目任务文件夹", "在项目外启动的任务默认存储数据的位置", SettingsGeneralControl.Button),
            SettingsGeneralRow("default-file-app", "默认文件打开位置", "", SettingsGeneralControl.Dropdown),
            SettingsGeneralRow("language", "语言", "", SettingsGeneralControl.Dropdown),
            SettingsGeneralRow("menu-bar", "在菜单栏中显示", "关闭主窗口后继续在 macOS 菜单栏中运行", SettingsGeneralControl.Toggle, true),
            SettingsGeneralRow("confirm-close", "关闭窗口前确认", "使用关闭标签页快捷键关闭最后一个标签页时提醒", SettingsGeneralControl.Dropdown),
            SettingsGeneralRow("full-view", "默认采用完整视图", "开始新任务时，在同一标签页中显示聊天和内容", SettingsGeneralControl.Toggle, true),
            SettingsGeneralRow("bottom-panel", "底部面板", "在应用标题栏中显示底部面板控件", SettingsGeneralControl.Toggle, true),
            SettingsGeneralRow("terminal-location", "默认终端位置", "", SettingsGeneralControl.Segmented),
            SettingsGeneralRow("prevent-sleep", "运行时防止系统休眠", "", SettingsGeneralControl.Toggle, true),
            SettingsGeneralRow("compress-history", "压缩本地保存的聊天历史记录", "重启后生效", SettingsGeneralControl.Toggle, false),
            SettingsGeneralRow("prompt-suggestions", "提示词建议", "通过搜索项目文件和已连接的应用，建议下一步操作", SettingsGeneralControl.Toggle, true),
            SettingsGeneralRow("licenses", "开源许可证", "", SettingsGeneralControl.Button),
            SettingsGeneralRow("plugins", "插件", "", SettingsGeneralControl.Toggle, true),
        )

}

/** The default Settings > General content surface. */
@Composable
public fun SettingsGeneralPage(
    state: GeneralSettingsState = GeneralSettingsState(),
    onClosePolicyChange: (CloseConfirmationPolicy) -> Unit = {},
    onRetry: () -> Unit = {},
    onOpenPlugins: (() -> Unit)? = null,
) {
    SettingsFormFrame("常规", "settings-general-title") {
        if (state.loading || state.saving || state.loadError != null || state.saveError != null || !state.durable) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.loading) GeneralStatus("正在读取常规设置…", "general-loading")
                if (state.saving) GeneralStatus("正在保存常规设置…", "general-saving")
                if (!state.durable) GeneralStatus("设置仅在本次运行中保留", "general-session-only")
                state.loadError?.let { GeneralStatus(it, "general-load-error") }
                state.saveError?.let { GeneralStatus(it, "general-save-error") }
                if (state.loadError != null || state.saveError != null) {
                    HarnessButton("重试", onRetry, enabled = !state.loading && !state.saving, testTag = "general-retry")
                }
            }
        }
        GeneralSection("权限", "settings-permissions", SettingsGeneralModel.permissions, state, onClosePolicyChange, onOpenPlugins)
        GeneralSection("常规", "settings-preferences", SettingsGeneralModel.preferences, state, onClosePolicyChange, onOpenPlugins)
    }
}

@Composable
private fun GeneralSection(
    title: String, tag: String, rows: List<SettingsGeneralRow>, state: GeneralSettingsState,
    onClosePolicyChange: (CloseConfirmationPolicy) -> Unit, onOpenPlugins: (() -> Unit)?,
) {
    SettingsFormSection(title, titleTag = "$tag-title") {
        SettingsFormCard(Modifier.semantics { testTag = "$tag-card" }) {
            rows.forEachIndexed { index, row ->
                SettingsRow(
                    row = if (row.id == "plugins") row.copy(control = SettingsGeneralControl.Button) else row,
                    showDivider = index > 0, state = state,
                    onClosePolicyChange = onClosePolicyChange, onOpenPlugins = onOpenPlugins,
                )
            }
        }
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
    state: GeneralSettingsState,
    onClosePolicyChange: (CloseConfirmationPolicy) -> Unit,
    onOpenPlugins: (() -> Unit)?,
) {
    if (showDivider) HorizontalDivider(
        color = HarnessTokens.Colors.BorderSubtle,
    )
    val unavailable = when (row.id) {
        "confirm-close" -> if (state.closePolicyAvailable) null else "本平台尚未接入窗口关闭策略"
        "plugins" -> if (onOpenPlugins != null) null else "本平台尚未接入插件管理"
        else -> GeneralUnavailableReasons.forRow(row.id)
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
    SettingsFormRow(
        label = row.title, description = row.description,
        modifier = Modifier.semantics { testTag = "settings-general-row-" + row.id },
        status = if (unavailable != null) "暂不可用" else null,
        statusTag = "settings-general-unavailable-" + row.id,
        control = control,
    )
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
