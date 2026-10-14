@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package im.hikaru.harness.client.plugins

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import im.hikaru.harness.ui.foundation.HarnessButton
import im.hikaru.harness.ui.foundation.HarnessButtonVariant
import im.hikaru.harness.ui.foundation.HarnessControlSize
import im.hikaru.harness.ui.foundation.HarnessDialog
import im.hikaru.harness.ui.foundation.HarnessEmptyState
import im.hikaru.harness.ui.foundation.HarnessErrorState
import im.hikaru.harness.ui.foundation.HarnessLoadingState
import im.hikaru.harness.ui.foundation.HarnessSearchField
import im.hikaru.harness.ui.foundation.HarnessSurface
import im.hikaru.harness.ui.foundation.HarnessText
import im.hikaru.harness.ui.foundation.HarnessTextField
import im.hikaru.harness.ui.foundation.HarnessTokens

/** Parent owns routing, scope binding and controller lifetime. This page only emits typed actions. */
@Composable
public fun InstalledPluginsPage(
    state: InstalledPluginsState,
    onAction: (InstalledPluginsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    PluginPageFrame(modifier, "installed-plugins-page") { compact ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().semantics { testTag = "plugin-inventory-list" },
            verticalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Two),
        ) {
            item {
                PluginListHeader(state, onAction, compact)
            }
            item { PluginManagementFeedback(state) }
            when (val list = state.list) {
                is PluginLoadState.Success -> {
                    val visible = state.visiblePlugins.orEmpty()
                    if (list.value.isEmpty()) {
                        item { HarnessEmptyState(
                            if (state.hasManagementAdapter) "尚无已登记插件" else "尚无已安装插件",
                            description = if (state.hasManagementAdapter) "添加本地插件以登记源目录。" else "当前主机的插件服务返回了空列表。",
                        ) }
                    } else if (visible.isEmpty()) {
                        item { HarnessEmptyState("没有匹配的插件", description = "试试其他搜索词。") }
                    } else {
                        items(visible, key = { it.id.value }) { plugin ->
                            InstalledPluginRow(plugin, state.selectedId == plugin.id) {
                                onAction(InstalledPluginsAction.Select(plugin.id))
                            }
                        }
                    }
                }
                else -> item {
                    PluginReadFeedback(
                        state.list,
                        onRetry = if (state.canRefresh) ({ onAction(InstalledPluginsAction.Refresh) }) else null,
                    )
                }
            }
            if (!state.hasManagementAdapter) item { UnsupportedManagementNotice() }
        }
    }
    PluginManagementDialogs(state, onAction)
}

@Composable
public fun InstalledPluginDetailPage(
    state: InstalledPluginsState,
    onAction: (InstalledPluginsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    PluginPageFrame(modifier, "installed-plugin-detail-page") { _ ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().semantics { testTag = "plugin-detail-list" },
            verticalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Three),
        ) {
            item {
                FlowRow(Modifier.fillMaxWidth().semantics { testTag = "plugin-detail-actions" },
                    horizontalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Two),
                    verticalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Two)) {
                    HarnessButton(
                        if (state.hasManagementAdapter) "返回已登记插件" else "返回已安装插件",
                        { onAction(InstalledPluginsAction.Back) },
                        variant = HarnessButtonVariant.Ghost,
                        size = HarnessControlSize.Compact,
                        testTag = "plugin-detail-back",
                    )
                    HarnessButton("刷新", { onAction(InstalledPluginsAction.Refresh) }, enabled = state.canRefresh,
                        variant = HarnessButtonVariant.Secondary, size = HarnessControlSize.Compact,
                        testTag = "plugin-detail-refresh")
                    val plugin = (state.detail as? PluginLoadState.Success)?.value?.plugin
                    if (state.hasManagementAdapter && plugin != null) {
                        PluginManagementControls(state, plugin, onAction)
                    }
                }
            }
            item { PluginManagementFeedback(state) }
            when (val detail = state.detail) {
                is PluginLoadState.Success -> {
                    item {
                        val plugin = detail.value.plugin
                        PluginSelectableValue(plugin.name, "plugin-detail-name", title = true)
                        if (state.hasManagementAdapter) HarnessText("启用或停用将在下一次 Agent 请求中生效。",
                            color = HarnessTokens.Colors.TextMuted, style = MaterialTheme.typography.bodySmall)
                        if (state.hasLocalUpdateAdapter) HarnessText("从原源目录重新保存 Skills 快照，保留启用状态；下一次 Agent 请求生效。",
                            color = HarnessTokens.Colors.TextMuted, style = MaterialTheme.typography.bodySmall)
                    }
                    item {
                        HarnessSurface(Modifier.fillMaxWidth()) {
                            Column(
                                Modifier.fillMaxWidth().padding(HarnessTokens.Spacing.Three),
                                verticalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Three),
                            ) {
                                val plugin = detail.value.plugin
                                HarnessText("文字可选择并复制；长内容可在字段内滚动。",
                                    color = HarnessTokens.Colors.TextMuted, style = MaterialTheme.typography.bodySmall)
                                PluginField("说明", plugin.description ?: "服务未提供说明", "plugin-detail-description")
                                PluginField("版本", plugin.version ?: "服务未提供版本", "plugin-detail-version")
                                PluginField("来源", plugin.source ?: "服务未提供来源", "plugin-detail-source")
                                PluginField("状态", enabledLabel(plugin.enabled), "plugin-detail-status")
                            }
                        }
                    }
                    item {
                        HarnessText("Skills", style = MaterialTheme.typography.titleMedium)
                        when (val skills = detail.value.skills) {
                            PluginSkillsState.NotLoaded -> HarnessText("Skills 信息尚未读取。", color = HarnessTokens.Colors.TextMuted)
                            is PluginSkillsState.Unavailable -> HarnessText(skills.reason, color = HarnessTokens.Colors.TextMuted)
                            is PluginSkillsState.Available -> HarnessText(
                                if (skills.skills.isEmpty()) "服务确认此插件没有 Skills。" else "已读取 " + skills.skills.size + " 个 Skills",
                                color = HarnessTokens.Colors.TextMuted)
                        }
                    }
                    val skills = detail.value.skills
                    if (skills is PluginSkillsState.Available) items(skills.skills.size) { index ->
                        val skill = skills.skills[index]
                        PluginResourceRow(skill.name, skill.description, "plugin-skill-$index", state.scope)
                    }
                    item {
                        HarnessText("Apps", style = MaterialTheme.typography.titleMedium)
                        when (val apps = detail.value.apps) {
                            PluginAppsState.NotLoaded -> HarnessText("Apps 信息尚未读取。", color = HarnessTokens.Colors.TextMuted)
                            is PluginAppsState.Unavailable -> HarnessText(apps.reason, color = HarnessTokens.Colors.TextMuted)
                            is PluginAppsState.Available -> HarnessText(
                                if (apps.names.isEmpty()) "服务确认此插件没有 Apps。" else "已读取 " + apps.names.size + " 个 Apps",
                                color = HarnessTokens.Colors.TextMuted,
                            )
                        }
                    }
                    val apps = detail.value.apps
                    if (apps is PluginAppsState.Available) {
                        items(apps.names.size) { index ->
                            PluginResourceRow(apps.names[index], null, "plugin-app-$index", state.scope)
                        }
                    }
                    detail.value.capabilities.forEach { capability ->
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.One)) {
                                HarnessText(capabilityLabel(capability.kind), style = MaterialTheme.typography.titleMedium)
                                HarnessText("已读取 " + capability.names.size + " 项；配置和执行服务尚未接入。",
                                    color = HarnessTokens.Colors.TextMuted)
                            }
                        }
                        items(capability.names.size) { index ->
                            PluginResourceRow(
                                capability.names[index], null,
                                "plugin-capability-" + capability.kind.name.lowercase() + "-" + index,
                                state.scope,
                            )
                        }
                    }
                    // The parser's internal declaration tokens are not user-facing; map them and keep the
                    // list last, after the concrete Skills/Apps/capability sections it qualifies.
                    val unsupported = detail.value.unsupportedAssets.filterNot { descriptor ->
                        descriptor == "apps (unsupported)" && detail.value.apps is PluginAppsState.Unavailable
                    }
                    if (unsupported.isNotEmpty()) item {
                        Column(verticalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.One),
                            modifier = Modifier.semantics { testTag = "plugin-detail-unsupported" }) {
                            HarnessText("未支持的资源", style = MaterialTheme.typography.titleMedium)
                            unsupported.forEachIndexed { index, descriptor ->
                                HarnessText(unsupportedAssetLabel(descriptor), color = HarnessTokens.Colors.TextMuted,
                                    modifier = Modifier.semantics { testTag = "plugin-detail-unsupported-$index" })
                            }
                        }
                    }
                }
                else -> item {
                    PluginReadFeedback(
                        detail,
                        onRetry = if (state.canRetryDetail) ({ onAction(InstalledPluginsAction.RetryDetail) }) else null,
                    )
                }
            }
            if (!state.hasManagementAdapter) item { UnsupportedManagementNotice() }
        }
    }
    PluginManagementDialogs(state, onAction)
}

@Composable
private fun PluginPageFrame(
    modifier: Modifier,
    tag: String,
    content: @Composable (compact: Boolean) -> Unit,
) {
    BoxWithConstraints(
        modifier.fillMaxSize().background(HarnessTokens.Colors.SurfaceElevated).semantics { testTag = tag },
    ) {
        val compact = maxWidth < 480.dp
        val inset = if (compact) HarnessTokens.Spacing.Three else HarnessTokens.Spacing.Four
        Box(
            Modifier.align(Alignment.TopCenter).widthIn(max = 760.dp).fillMaxSize().padding(inset),
        ) { content(compact) }
    }
}

@Composable
private fun PluginListHeader(
    state: InstalledPluginsState,
    onAction: (InstalledPluginsAction) -> Unit,
    compact: Boolean,
) {
    val title = if (state.hasManagementAdapter) "已登记插件" else "已安装插件"
    Column(Modifier.fillMaxWidth().semantics { testTag = "plugins-header" },
        verticalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Two)) {
        FlowRow(Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Three),
            verticalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Two)) {
            HarnessText(title, style = MaterialTheme.typography.headlineSmall)
            HarnessButton("刷新", { onAction(InstalledPluginsAction.Refresh) },
                enabled = state.canRefresh, variant = HarnessButtonVariant.Secondary,
                size = HarnessControlSize.Compact, testTag = "plugins-refresh")
            if (state.hasManagementAdapter) {
                HarnessButton("添加本地插件", { onAction(InstalledPluginsAction.BeginRegistration) },
                    enabled = state.canManage, size = HarnessControlSize.Compact, testTag = "plugins-add-local")
            }
        }
        state.scope?.let {
            SelectionContainer { HarnessText("主机：" + it.hostId.value,
                color = HarnessTokens.Colors.TextMuted, style = MaterialTheme.typography.bodySmall) }
        }
        if (state.hasManagementAdapter) {
            HarnessText("登记本地目录并保存 Skills 文本快照；添加后默认停用。",
                color = HarnessTokens.Colors.TextMuted, style = MaterialTheme.typography.bodySmall)
        }
        FlowRow(Modifier.fillMaxWidth().semantics { testTag = "plugins-search-actions" },
            horizontalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Two),
            verticalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Two)) {
            HarnessSearchField(state.searchQuery, { onAction(InstalledPluginsAction.Search(it)) },
                placeholder = if (state.hasManagementAdapter) "搜索本地插件" else "搜索已安装插件",
                accessibilityLabel = if (state.hasManagementAdapter) "搜索本地插件" else "搜索已安装插件",
                modifier = Modifier.widthIn(max = if (compact) 320.dp else 224.dp).fillMaxWidth(),
                enabled = state.canSearch, testTag = "plugins-search")
            HarnessButton(if (state.enabledOnly) "显示全部插件" else "仅显示已启用",
                { onAction(InstalledPluginsAction.SetEnabledOnly(!state.enabledOnly)) },
                enabled = state.canSearch, selected = state.enabledOnly, variant = HarnessButtonVariant.Ghost,
                size = HarnessControlSize.Compact, testTag = "plugins-enabled-only")
        }
    }
}

@Composable
private fun InstalledPluginRow(plugin: InstalledPlugin, selected: Boolean, onClick: () -> Unit) {
    HarnessSurface(Modifier.fillMaxWidth(), selected = selected) {
        Column(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(HarnessTokens.Spacing.Three)
                .semantics { testTag = "installed-plugin-" + plugin.id.value },
            verticalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.One),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Two),
                verticalAlignment = Alignment.CenterVertically) {
                HarnessText(plugin.name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1)
                HarnessText(enabledLabel(plugin.enabled), color = HarnessTokens.Colors.TextMuted,
                    style = MaterialTheme.typography.labelSmall)
            }
            plugin.description?.let { HarnessText(it, color = HarnessTokens.Colors.TextMuted,
                style = MaterialTheme.typography.bodySmall, maxLines = 1) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Two)) {
                HarnessText(plugin.source ?: "服务未提供来源", Modifier.weight(1f),
                    color = HarnessTokens.Colors.TextMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                HarnessText("详情 ›", color = HarnessTokens.Colors.TextMuted, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun PluginField(label: String, value: String, tag: String) {
    Column(verticalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.One)) {
        HarnessText(label, color = HarnessTokens.Colors.TextMuted, style = MaterialTheme.typography.labelLarge)
        PluginSelectableValue(value, tag)
    }
}

/** Bounded readers keep arbitrarily long values selectable without growing a LazyColumn item forever. */
@Composable
private fun PluginSelectableValue(value: String, tag: String, title: Boolean = false) {
    SelectionContainer {
        Box(Modifier.fillMaxWidth().heightIn(max = if (title) 112.dp else 160.dp)
            .verticalScroll(rememberScrollState()).semantics { testTag = tag + "-reader" }) {
            HarnessText(value, Modifier.fillMaxWidth().semantics { testTag = tag },
                style = if (title) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun PluginResourceRow(name: String, description: String?, tag: String, scope: PluginScope?) {
    var expanded by remember(scope, name, description) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = HarnessTokens.Spacing.Three, vertical = HarnessTokens.Spacing.Two)
        .semantics { testTag = tag }, verticalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.One)) {
        SelectionContainer {
            HarnessText(name, Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                .semantics { testTag = tag + "-name" }, style = MaterialTheme.typography.titleSmall, maxLines = 1)
        }
        if (description != null) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Two)) {
                HarnessText(if (expanded) "完整说明（可选择并复制）" else description, Modifier.weight(1f),
                    color = HarnessTokens.Colors.TextMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                HarnessButton(if (expanded) "收起说明" else "展开完整说明", { expanded = !expanded },
                    variant = HarnessButtonVariant.Ghost, size = HarnessControlSize.Compact, testTag = tag + "-expand")
            }
            if (expanded) PluginSelectableValue(description, tag + "-description")
        }
    }
}

private fun capabilityLabel(kind: PluginCapabilityKind): String = when (kind) {
    PluginCapabilityKind.McpServer -> "MCP 服务器"
    PluginCapabilityKind.Hook -> "钩子"
    PluginCapabilityKind.Script -> "脚本"
}

/**
 * Maps the parser's internal declaration descriptors (`"apps (unsupported)"`, …) to user-facing
 * Chinese. Any value the page does not recognise passes through unchanged so nothing is hidden.
 */
private fun unsupportedAssetLabel(descriptor: String): String = when (descriptor) {
    "apps (unsupported)" -> "Apps：此声明无法在本地枚举（例如 apps 目录或未识别的 apps 文件），本地服务尚未读取。"
    "MCP (unsupported)" -> "MCP：声明的 server 配置无法在本地枚举，本地服务不会连接或启动它。"
    "hooks (unsupported)" -> "Hooks：hooks 目录或文件尚未接入本地服务，不会被读取或执行。"
    "scripts (unsupported)" -> "Scripts：scripts 目录尚未接入本地服务，不会被读取或执行。"
    "skill attachments (unsupported)" -> "技能附件：除 SKILL.md 外的文件不会被读取。"
    "other capabilities (unsupported)" -> "其他能力（commands / agents / lspServers / assets）：暂不支持。"
    else -> descriptor
}

@Composable
private fun PluginReadFeedback(state: PluginLoadState<*>, onRetry: (() -> Unit)?) {
    when (state) {
        PluginLoadState.NotLoaded -> HarnessEmptyState("尚未读取插件信息", description = "请先绑定当前主机的插件读取范围。")
        PluginLoadState.Loading -> HarnessLoadingState("正在读取插件信息…")
        is PluginLoadState.Error -> HarnessErrorState(state.error.message, onRetry = onRetry, retryLabel = "重试")
        is PluginLoadState.Unavailable -> HarnessEmptyState("插件服务不可用", description = state.reason.message)
        is PluginLoadState.AuthRequired -> HarnessEmptyState("插件服务需要身份验证", description = state.message)
        is PluginLoadState.UnsupportedHost -> HarnessEmptyState("当前主机不支持插件管理", description = state.message)
        is PluginLoadState.Success -> Unit
    }
}

@Composable
private fun PluginManagementControls(
    state: InstalledPluginsState, plugin: InstalledPlugin, onAction: (InstalledPluginsAction) -> Unit,
) {
    HarnessButton(if (plugin.enabled == PluginEnabledState.Enabled) "停用" else "启用",
        { onAction(InstalledPluginsAction.SetEnabled(plugin.id, plugin.enabled != PluginEnabledState.Enabled)) },
        enabled = state.canManageDetail && plugin.enabled != PluginEnabledState.Unknown,
        size = HarnessControlSize.Compact, testTag = "plugin-set-enabled")
    if (state.hasLocalUpdateAdapter) HarnessButton("从源目录更新",
        { onAction(InstalledPluginsAction.UpdateLocal(plugin.id)) },
        enabled = state.canUpdateLocal, variant = HarnessButtonVariant.Secondary,
        size = HarnessControlSize.Compact, testTag = "plugin-update-local")
    HarnessButton("移除登记", { onAction(InstalledPluginsAction.RequestRemove(plugin.id)) },
        enabled = state.canManageDetail, variant = HarnessButtonVariant.Destructive,
        size = HarnessControlSize.Compact, testTag = "plugin-remove-registration")
}

@Composable
private fun PluginManagementFeedback(state: InstalledPluginsState) {
    Column(verticalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.One)) {
        state.mutationReceipt?.let {
            HarnessText(state.managementNotice.orEmpty(), Modifier.semantics { testTag = "plugin-mutation-notice" })
            HarnessText("登记已保存", Modifier.semantics { testTag = "plugin-mutation-receipt" },
                color = HarnessTokens.Colors.TextMuted)
            if (state.list is PluginLoadState.Error || state.detail is PluginLoadState.Error || state.revision is PluginLoadState.Error) {
                HarnessText("保存已完成，但插件信息未能刷新。请显式刷新查看最新数据。",
                    Modifier.semantics { testTag = "plugin-saved-refresh-failure" },
                    color = HarnessTokens.Colors.TextMuted)
            }
        }
        if (state.mutationBusy) HarnessText("正在保存插件登记…",
            Modifier.semantics { testTag = "plugin-mutation-busy" }, color = HarnessTokens.Colors.TextMuted)
        state.mutationError?.let { HarnessText(it, Modifier.semantics { testTag = "plugin-mutation-error" },
            color = HarnessTokens.Colors.Danger) }
        if (state.hasManagementAdapter && !state.mutationBusy && !state.mutationRequiresRefresh &&
            state.list is PluginLoadState.Success && state.revision !is PluginLoadState.Success) {
            HarnessText("管理状态尚未就绪，请刷新。", color = HarnessTokens.Colors.TextMuted)
            PluginReadFeedback(state.revision, onRetry = null)
        }
    }
}

@Composable
private fun PluginManagementDialogs(state: InstalledPluginsState, onAction: (InstalledPluginsAction) -> Unit) {
    val cancel = { onAction(InstalledPluginsAction.CancelManagementDialog) }
    HarnessDialog(visible = state.registrationDirectory != null, onDismissRequest = cancel, title = "添加本地插件") {
        Column(Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState())
            .semantics { testTag = "plugin-register-body" },
            verticalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Three)) {
            HarnessText("请输入插件源目录的绝对路径。登记会保存 Skills 文本快照，默认停用。")
            HarnessTextField(state.registrationDirectory.orEmpty(),
                { onAction(InstalledPluginsAction.ChangeRegistrationDirectory(it)) },
                label = "源目录绝对路径", modifier = Modifier.fillMaxWidth(), enabled = !state.mutationBusy,
                isError = !state.registrationDirectory.isNullOrBlank() && !state.registrationDirectoryIsAbsolute,
                supportingText = if (!state.registrationDirectory.isNullOrBlank() && !state.registrationDirectoryIsAbsolute)
                    "请输入绝对路径，不能包含换行。" else "路径可在输入框内水平滚动、选择并复制。",
                testTag = "plugin-local-directory")
            state.mutationError?.let { HarnessText(it, color = HarnessTokens.Colors.Danger) }
            if (state.mutationRequiresRefresh) HarnessButton("刷新插件信息",
                { onAction(InstalledPluginsAction.Refresh) }, enabled = state.canRefresh,
                variant = HarnessButtonVariant.Secondary, testTag = "plugin-register-refresh")
        }
        FlowRow(Modifier.fillMaxWidth().semantics { testTag = "plugin-register-actions" },
            horizontalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Two),
            verticalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Two)) {
            HarnessButton("添加本地插件", { onAction(InstalledPluginsAction.RegisterLocal) },
                enabled = state.canRegisterLocal, testTag = "plugin-register-confirm")
            HarnessButton("取消", cancel, enabled = !state.mutationBusy, variant = HarnessButtonVariant.Ghost,
                testTag = "plugin-register-cancel")
        }
    }
    HarnessDialog(visible = state.removeConfirmation != null, onDismissRequest = cancel, title = "移除登记") {
        Column(Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState())
            .semantics { testTag = "plugin-remove-body" },
            verticalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Three)) {
            HarnessText("移除此插件的 Harness 登记？源目录和源文件都会保留。")
            PluginField("插件", state.removeConfirmation?.name.orEmpty(), "plugin-remove-name")
            state.removeConfirmation?.source?.let { PluginField("来源", it, "plugin-remove-source") }
            state.mutationError?.let { HarnessText(it, color = HarnessTokens.Colors.Danger) }
            if (state.mutationRequiresRefresh) HarnessButton("刷新插件信息",
                { onAction(InstalledPluginsAction.Refresh) }, enabled = state.canRefresh,
                variant = HarnessButtonVariant.Secondary, testTag = "plugin-remove-refresh")
        }
        FlowRow(Modifier.fillMaxWidth().semantics { testTag = "plugin-remove-actions" },
            horizontalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Two),
            verticalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Two)) {
            HarnessButton("移除登记", { onAction(InstalledPluginsAction.RemoveRegistration) },
                enabled = state.canManageDetail, variant = HarnessButtonVariant.Destructive, testTag = "plugin-remove-confirm")
            HarnessButton("取消", cancel, enabled = !state.mutationBusy, variant = HarnessButtonVariant.Ghost,
                testTag = "plugin-remove-cancel")
        }
    }
}

/** Read-only repositories and Relay bindings have no local management capability. */
@Composable
private fun UnsupportedManagementNotice() {
    HarnessSurface(Modifier.fillMaxWidth().semantics { testTag = "plugin-management-unavailable"; disabled() }) {
        Column(
            Modifier.fillMaxWidth().padding(HarnessTokens.Spacing.Three),
            verticalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.One),
        ) {
            HarnessText("管理操作不可用", style = MaterialTheme.typography.titleSmall)
            HarnessText("当前连接仅支持读取或未提供本地登记管理；添加本地插件、启用/停用和移除登记不可用。", color = HarnessTokens.Colors.TextMuted)
        }
    }
}

private fun enabledLabel(state: PluginEnabledState): String = when (state) {
    PluginEnabledState.Enabled -> "服务报告：已启用"
    PluginEnabledState.Disabled -> "服务报告：已停用"
    PluginEnabledState.Unknown -> "服务未提供启用状态"
}
