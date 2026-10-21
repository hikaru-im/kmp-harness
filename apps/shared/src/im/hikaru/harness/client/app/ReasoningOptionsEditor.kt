package im.hikaru.harness.client.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import im.hikaru.harness.client.connection.ReasoningEffortId
import im.hikaru.harness.client.connection.ReasoningOptions
import im.hikaru.harness.client.page.SettingsFormRow
import im.hikaru.harness.client.page.SettingsFormSelect
import im.hikaru.harness.client.settings.ModelReasoningCapabilities
import im.hikaru.harness.client.settings.reasoningOptionsError
import im.hikaru.harness.ui.foundation.*

/** Generic controls driven entirely by the Host's effective capabilities. */
@Composable
internal fun ReasoningOptionsEditor(
    initial: ReasoningOptions, capabilities: ModelReasoningCapabilities,
    onDismiss: () -> Unit, onSave: (ReasoningOptions) -> Unit,
) {
    var mode by remember(initial, capabilities) { mutableStateOf(initial.mode) }
    var effort by remember(initial, capabilities) { mutableStateOf(initial.effort?.value.orEmpty()) }
    var budget by remember(initial, capabilities) { mutableStateOf(initial.budgetTokens?.toString().orEmpty()) }
    var execution by remember(initial, capabilities) { mutableStateOf(initial.executionModeId.orEmpty()) }
    var error by remember(initial, capabilities) { mutableStateOf<String?>(null) }
    fun modeLabel(value: String): String = when (value) {
        "provider-default" -> "跟随厂商"
        "disabled" -> "关闭"
        "enabled" -> "开启"
        "adaptive" -> "自动"
        else -> value
    }
    HarnessDialog(true, onDismiss, "思考设置") {
        Column(Modifier.widthIn(min = 260.dp, max = 420.dp).heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            SettingsFormRow("思考模式") {
                SettingsFormSelect(mode, (listOf("provider-default") + capabilities.modes).distinct(), ::modeLabel,
                    true, "reasoning-options-mode") { mode = it }
            }
            if (capabilities.efforts.isNotEmpty()) SettingsFormRow("强度") {
                SettingsFormSelect(effort, listOf("") + capabilities.efforts, { if (it.isEmpty()) "跟随厂商" else it },
                    true, "reasoning-options-effort") { effort = it }
            }
            if (capabilities.budgetSupported) SettingsFormRow("思考预算", "留空使用厂商默认；范围由当前模型决定。") {
                BasicTextField(budget, { budget = it }, singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = HarnessTokens.Colors.Text),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 36.dp).semantics { testTag = "reasoning-options-budget" })
            }
            if (capabilities.executionModes.isNotEmpty()) SettingsFormRow("执行模式") {
                SettingsFormSelect(execution, listOf("") + capabilities.executionModes, { if (it.isEmpty()) "跟随厂商" else it },
                    true, "reasoning-options-execution") { execution = it }
            }
            error?.let { HarnessText(it, color = HarnessTokens.Colors.Danger) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HarnessButton("全部跟随厂商", { onSave(ReasoningOptions()) }, variant = HarnessButtonVariant.Secondary,
                testTag = "reasoning-options-default")
            HarnessButton("取消", onDismiss, variant = HarnessButtonVariant.Secondary)
            HarnessButton("应用", {
                try {
                    val tokens = budget.trim().takeIf(String::isNotEmpty)?.let {
                        it.toLongOrNull()?.takeIf { value -> value >= 0L } ?: error("预算须为非负整数或留空")
                    }
                    val options = ReasoningOptions(mode, effort.takeIf(String::isNotEmpty)?.let(::ReasoningEffortId), tokens,
                        execution.takeIf(String::isNotEmpty))
                    val problem = reasoningOptionsError(options, capabilities)
                    require(problem == null) { problem.orEmpty() }
                    onSave(options)
                } catch (failure: Exception) { error = failure.message ?: "思考设置无效" }
            }, testTag = "reasoning-options-save")
        }
    }
}
