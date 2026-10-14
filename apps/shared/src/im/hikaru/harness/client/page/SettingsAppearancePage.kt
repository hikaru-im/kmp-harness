package im.hikaru.harness.client.page

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import im.hikaru.harness.client.settings.*
import im.hikaru.harness.ui.foundation.*

@Composable
public fun SettingsAppearancePage(
    state: AppearanceState, onChange: (AppearanceSettings) -> Unit, onRetry: () -> Unit,
    defaultAdvancedExpanded: Boolean = false,
) {
    val settings = state.settings
    val enabled = state.canEdit
    val systemDark = isSystemInDarkTheme()
    val currentMode = if (settings.mode == AppearanceMode.System) {
        if (systemDark) AppearanceMode.Dark else AppearanceMode.Light
    } else settings.mode
    var advanced by remember { mutableStateOf(defaultAdvancedExpanded) }
    var separateModes by remember { mutableStateOf(false) }
    var importOpen by remember { mutableStateOf(false) }
    var importDraft by remember { mutableStateOf("") }
    var importError by remember { mutableStateOf<String?>(null) }
    var pendingImport by remember { mutableStateOf<AppearanceSettings?>(null) }
    var importRevision by remember { mutableStateOf(0L) }
    var feedback by remember { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboardManager.current
    LaunchedEffect(state.saveRevision, state.settings, state.saving, pendingImport) {
        if (pendingImport != null && !state.saving && state.saveRevision > importRevision && state.settings == pendingImport) {
            importOpen = false; pendingImport = null; feedback = "主题已导入并保存"
        }
    }
    fun changeVariant(mode: AppearanceMode, transform: (AppearanceVariant) -> AppearanceVariant) {
        onChange(settings.withVariant(mode, transform(settings.variant(mode))))
    }
    SettingsFormFrame("外观", "settings-appearance-title") {
        if (state.loading) HarnessText("正在读取外观设置…", color = HarnessTokens.Colors.TextMuted)
        if (!state.loaded && !state.loading && state.loadError == null) HarnessButton("重新读取外观设置", onRetry, testTag = "appearance-reload")
        val error = state.loadError ?: state.saveError
        if (error != null) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HarnessText(error, Modifier.semantics { testTag = if (state.loadError != null) "appearance-load-error" else "appearance-save-error" },
                    color = HarnessTokens.Colors.Danger)
                HarnessButton("重试", onRetry, enabled = !state.loading && !state.saving, testTag = "appearance-retry", variant = HarnessButtonVariant.Secondary)
            }
        }
        if (state.saving) HarnessText("正在保存…", color = HarnessTokens.Colors.TextMuted)
        if (!state.durable) HarnessText("当前环境的外观设置仅在本次会话保留。", color = HarnessTokens.Colors.TextMuted, style = MaterialTheme.typography.bodySmall)
        feedback?.let { HarnessText(it, Modifier.semantics { testTag = "appearance-feedback" }, color = HarnessTokens.Colors.TextMuted) }
        SettingsFormSection("视觉样式") {
            SettingsFormCard {
                SettingsFormRow("模式") { ModePreviews(settings.mode, enabled) { onChange(settings.copy(mode = it)) } }
                key(currentMode) {
                    VisualThemeFields(currentMode, settings.variant(currentMode), enabled) { transform -> changeVariant(currentMode, transform) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HarnessButton("复制主题", {
                    try {
                        clipboard.setText(AnnotatedString(AppearanceThemeCodec.encode(currentMode, settings.variant(currentMode))))
                        feedback = "主题已复制"
                    } catch (error: Exception) { feedback = error.message ?: "无法复制主题" }
                }, enabled = enabled, variant = HarnessButtonVariant.Secondary, size = HarnessControlSize.Compact, testTag = "appearance-copy")
                HarnessButton("导入主题", { importOpen = true; importDraft = ""; importError = null; pendingImport = null },
                    enabled = enabled, variant = HarnessButtonVariant.Secondary, size = HarnessControlSize.Compact, testTag = "appearance-import")
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HarnessButton(if (advanced) "▾ 高级" else "▸ 高级", { advanced = !advanced },
                    modifier = Modifier.semantics { stateDescription = if (advanced) "已展开" else "已折叠" },
                    variant = HarnessButtonVariant.Ghost, testTag = "appearance-advanced-toggle")
                Spacer(Modifier.weight(1f))
                if (advanced && (settings != settings.resetAdvanced() || separateModes)) HarnessButton("重置", { onChange(settings.resetAdvanced()) }, enabled = enabled,
                    variant = HarnessButtonVariant.Ghost, testTag = "appearance-reset")
            }
            if (advanced) {
                SettingsFormCard {
                    NumberRow("界面字号", settings.uiFontSize, 11..16, enabled, "appearance-ui-size") { onChange(settings.copy(uiFontSize = it)) }
                    NumberRow("代码字号", settings.codeFontSize, 8..24, enabled, "appearance-code-size") { onChange(settings.copy(codeFontSize = it)) }
                    SettingsFormRow("分别调整浅色和深色模式") {
                        HarnessSwitch(separateModes, { separateModes = it }, "分别调整浅色和深色模式", enabled = enabled, testTag = "appearance-separate-modes")
                    }
                }
                val modes = if (separateModes) listOf(AppearanceMode.Light, AppearanceMode.Dark) else listOf(currentMode)
                modes.forEach { mode -> key(mode) {
                    SettingsFormSection(mode.label() + "主题") {
                        SettingsFormCard {
                            AdvancedThemeFields(mode, settings.variant(mode), enabled) { transform -> changeVariant(mode, transform) }
                        }
                    }
                } }
                SettingsFormCard {
                    SettingsFormRow("差异标记") {
                        SettingsFormSelect(settings.diffStyle, AppearanceDiffStyle.entries, { if (it == AppearanceDiffStyle.Color) "颜色" else "符号" },
                            enabled, "appearance-diff-style") { onChange(settings.copy(diffStyle = it)) }
                    }
                    SettingsFormRow("减少动画", "当前应用于加载指示器；跟随系统尚未接入平台设置。") {
                        SettingsFormSelect(settings.reducedMotion, AppearanceMotion.entries, { when (it) { AppearanceMotion.System -> "跟随系统"; AppearanceMotion.On -> "开启"; AppearanceMotion.Off -> "关闭" } },
                            enabled, "appearance-motion") { onChange(settings.copy(reducedMotion = it)) }
                    }
                    SettingsFormRow("指针光标", "在共享按钮和开关上使用指针。") {
                        HarnessSwitch(settings.usePointerCursors, { onChange(settings.copy(usePointerCursors = it)) }, "指针光标", enabled = enabled, testTag = "appearance-pointer")
                    }
                    SettingsFormRow("字体平滑", "当前由系统绘制，客户端尚未接入此设置。") {
                        HarnessSwitch(settings.fontSmoothing, {}, "字体平滑不可用", enabled = false, testTag = "appearance-smoothing")
                    }
                    UnavailableRow("代码主题预设", "尚未接入预设目录")
                    UnavailableRow("半透明窗口", "尚未接入原生窗口效果")
                    UnavailableRow("Dock 图标", "尚未接入 macOS 图标设置")
                }
            }
        }
    }
    HarnessDialog(importOpen, { if (!state.saving) { importOpen = false; pendingImport = null } }, "导入主题") {
        HarnessText("支持本客户端的 harness-theme-v1 分享文本。", color = HarnessTokens.Colors.TextMuted, style = MaterialTheme.typography.bodySmall)
        BasicTextField(importDraft, { importDraft = it; importError = null; pendingImport = null }, enabled = enabled,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = HarnessTokens.Colors.Text),
            modifier = Modifier.widthIn(min = 200.dp, max = 480.dp).fillMaxWidth().heightIn(min = 100.dp, max = 180.dp)
                .verticalScroll(rememberScrollState()).border(1.dp, HarnessTokens.Colors.BorderStrong, RoundedCornerShape(8.dp))
                .padding(12.dp).semantics { testTag = "appearance-import-input" })
        (importError ?: state.saveError)?.let { HarnessText(it, color = HarnessTokens.Colors.Danger) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HarnessButton("取消", { importOpen = false; pendingImport = null }, enabled = !state.saving, variant = HarnessButtonVariant.Secondary)
            HarnessButton("导入", {
                try {
                    val share = AppearanceThemeCodec.decode(importDraft)
                    val candidate = settings.withVariant(share.mode, share.theme)
                    importRevision = state.saveRevision; pendingImport = candidate; importError = null
                    onChange(candidate)
                } catch (error: Exception) { importError = error.message ?: "主题格式无效" }
            }, enabled = enabled, testTag = "appearance-import-confirm")
            if (state.saveError != null && pendingImport != null) HarnessButton("重试保存", onRetry, enabled = enabled, testTag = "appearance-import-retry")
        }
    }
}

private fun AppearanceMode.label(): String = when (this) { AppearanceMode.System -> "系统"; AppearanceMode.Light -> "浅色"; AppearanceMode.Dark -> "深色" }
private fun AppearanceMode.slug(): String = name.lowercase()
private fun AppearanceFont.label(): String = when (this) { AppearanceFont.System -> "系统字体"; AppearanceFont.Sans -> "无衬线"; AppearanceFont.Serif -> "衬线"; AppearanceFont.Monospace -> "等宽" }
private fun AppearanceFontStyle.label(): String = when (this) { AppearanceFontStyle.Regular -> "常规"; AppearanceFontStyle.Medium -> "中等"; AppearanceFontStyle.Bold -> "粗体"; AppearanceFontStyle.Italic -> "斜体" }

@Composable
private fun ModePreviews(value: AppearanceMode, enabled: Boolean, onSelect: (AppearanceMode) -> Unit) {
    Row(Modifier.width(272.dp).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        AppearanceMode.entries.forEach { mode ->
            val interaction = remember { MutableInteractionSource() }
            val focused by interaction.collectIsFocusedAsState()
            Column(Modifier.weight(1f).harnessPointer(enabled).selectable(mode == value, interactionSource = interaction, indication = null, enabled = enabled, role = Role.RadioButton, onClick = { onSelect(mode) })
                .onPreviewKeyEvent { event ->
                    if (enabled && event.type == KeyEventType.KeyDown && event.key in listOf(Key.DirectionLeft, Key.DirectionRight, Key.MoveHome, Key.MoveEnd)) {
                        val options = AppearanceMode.entries
                        val index = when (event.key) { Key.MoveHome -> 0; Key.MoveEnd -> 2; Key.DirectionLeft -> (value.ordinal + 2) % 3; else -> (value.ordinal + 1) % 3 }
                        onSelect(options[index]); true
                    } else false
                }.semantics { testTag = "appearance-mode-" + mode.slug(); contentDescription = mode.label() },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f).border(2.dp, if (focused) HarnessTokens.Colors.BorderFocused else if (mode == value) HarnessTokens.Colors.Accent else HarnessTokens.Colors.Border, RoundedCornerShape(8.dp))
                    .padding(4.dp).clip(RoundedCornerShape(4.dp))) {
                    Row(Modifier.fillMaxSize()) {
                        if (mode == AppearanceMode.System) {
                            Box(Modifier.weight(1f)) { MiniThemePreview(false) }
                            Box(Modifier.weight(1f)) { MiniThemePreview(true) }
                        } else {
                            MiniThemePreview(mode == AppearanceMode.Dark)
                        }
                    }
                }
                HarnessText(mode.label(), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun MiniThemePreview(dark: Boolean) {
    Row(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxHeight().background(if (dark) Color(0xff242424) else Color(0xffeeeeee)))
        Column(Modifier.weight(3f).fillMaxHeight().background(if (dark) Color(0xff303030) else Color.White).padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(3) { Box(Modifier.fillMaxWidth(if (it == 2) .6f else 1f).height(3.dp)
                .background(if (dark) Color(0xff707070) else Color(0xffbbbbbb), RoundedCornerShape(2.dp))) }
        }
    }
}

@Composable
private fun VisualThemeFields(mode: AppearanceMode, theme: AppearanceVariant, enabled: Boolean, onChange: ((AppearanceVariant) -> AppearanceVariant) -> Unit) {
    val suffix = mode.slug()
    listOf("强调色" to theme.accent, "背景色" to theme.background, "文字颜色" to theme.foreground).forEachIndexed { index, (label, color) ->
        SettingsFormRow(label) {
            SettingsFormInput(color.orEmpty(), enabled, "appearance-color-" + listOf("accent", "background", "foreground")[index] + "-" + suffix, "默认",
                validate = { if (it.isBlank() || Regex("#[0-9a-fA-F]{6}").matches(it.trim())) null else "请输入 #RRGGBB" },
                onCommit = { value -> val parsed = value.trim().ifEmpty { null }; onChange { when (index) { 0 -> it.copy(accent = parsed); 1 -> it.copy(background = parsed); else -> it.copy(foreground = parsed) } } })
        }
    }
    SettingsFormRow("界面字体", "使用当前客户端支持的字体族。") {
        SettingsFormSelect(theme.uiFont, AppearanceFont.entries, { it.label() }, enabled, "appearance-ui-font-" + suffix) { font -> onChange { it.copy(uiFont = font) } }
    }
}

@Composable
private fun AdvancedThemeFields(mode: AppearanceMode, theme: AppearanceVariant, enabled: Boolean, onChange: ((AppearanceVariant) -> AppearanceVariant) -> Unit) {
    val suffix = mode.slug()
    SettingsFormRow("界面字体样式") { SettingsFormSelect(theme.uiFontStyle, AppearanceFontStyle.entries, { it.label() }, enabled, "appearance-ui-style-" + suffix) { style -> onChange { it.copy(uiFontStyle = style) } } }
    SettingsFormRow("内容字体") { SettingsFormSelect(theme.contentFont, AppearanceFont.entries, { it.label() }, enabled, "appearance-content-font-" + suffix) { font -> onChange { it.copy(contentFont = font) } } }
    SettingsFormRow("内容字体样式") { SettingsFormSelect(theme.contentFontStyle, AppearanceFontStyle.entries, { it.label() }, enabled, "appearance-content-style-" + suffix) { style -> onChange { it.copy(contentFontStyle = style) } } }
    SettingsFormRow("代码字体") { SettingsFormSelect(theme.codeFont, AppearanceFont.entries, { it.label() }, enabled, "appearance-code-font-" + suffix) { font -> onChange { it.copy(codeFont = font) } } }
    SettingsFormRow("代码字体样式") { SettingsFormSelect(theme.codeFontStyle, AppearanceFontStyle.entries, { it.label() }, enabled, "appearance-code-style-" + suffix) { style -> onChange { it.copy(codeFontStyle = style) } } }
    NumberRow("对比度", theme.contrast, 0..100, enabled, "appearance-contrast-" + suffix) { value -> onChange { it.copy(contrast = value) } }
}

@Composable
private fun NumberRow(label: String, value: Int, range: IntRange, enabled: Boolean, tag: String, onChange: (Int) -> Unit) {
    SettingsFormRow(label) {
        SettingsFormInput(value.toString(), enabled, tag, validate = { text ->
            if (text.toIntOrNull()?.let { it in range } == true) null else "请输入 " + range.first + "–" + range.last + " 的整数"
        }, onCommit = { onChange(it.toInt()) })
    }
}

@Composable
private fun UnavailableRow(label: String, reason: String) {
    SettingsFormRow(label, reason) { HarnessText("暂不可用", color = HarnessTokens.Colors.TextMuted, style = MaterialTheme.typography.bodySmall) }
}
