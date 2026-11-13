@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package im.hikaru.harness.client.plugins

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import im.hikaru.harness.ui.foundation.LocalHarnessToastState
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
import im.hikaru.harness.client.page.*
import im.hikaru.harness.ui.foundation.HarnessTokens

/** Parent owns routing, scope binding and controller lifetime. This page only emits typed actions. */
@Composable
public fun InstalledPluginsPage(
    state: InstalledPluginsState,
    onAction: (InstalledPluginsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val navigatorVisible = LocalWorkspaceNavigatorVisible.current
    PluginPageFrame(modifier, "installed-plugins-page") { compact ->
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            PluginListHeader(state, onAction, compact, showSearch = !navigatorVisible)
            PluginManagementReadFeedback(state)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (val list = state.list) {
                    is PluginLoadState.Success -> {
                        val visible = state.visiblePlugins.orEmpty()
                        when {
                            list.value.isEmpty() -> WorkspaceEmptyState(
                                if (state.hasManagementAdapter) "尚无已登记插件" else "尚无已安装插件",
                                action = if (state.hasManagementAdapter) ({
                                    HarnessButton("添加本地插件", { onAction(InstalledPluginsAction.BeginRegistration) },
                                        enabled = state.canManage, testTag = "plugins-empty-add")
                                }) else null,
                            )
                            visible.isEmpty() -> WorkspaceEmptyState("没有匹配的插件", "试试其他搜索词或筛选条件。")
                            navigatorVisible -> WorkspaceEmptyState("选择插件查看详情")
                            else -> LazyColumn(
                                modifier = Modifier.fillMaxSize().semantics { testTag = "plugin-inventory-list" },
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(visible, key = { it.id.value }) { plugin ->
                                    InstalledPluginRow(plugin, state.selectedId == plugin.id) {
                                        onAction(InstalledPluginsAction.Select(plugin.id))
                                    }
                                }
                            }
                        }
                    }
                    else -> PluginReadFeedback(state.list,
                        onRetry = if (state.canRefresh) ({ onAction(InstalledPluginsAction.Refresh) }) else null)
                }
            }
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
                WorkspacePageHeader(title = {
                    val plugin = (state.detail as? PluginLoadState.Success)?.value?.plugin
                    if (plugin != null) PluginSelectableValue(plugin.name, "plugin-detail-name", title = true)
                    else HarnessText("插件详情", style = MaterialTheme.typography.headlineSmall)
                }) {
                    FlowRow(Modifier.widthIn(max = 520.dp).semantics { testTag = "plugin-detail-actions" },
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
            }
            item { PluginManagementReadFeedback(state) }
            when (val detail = state.detail) {
                is PluginLoadState.Success -> {
                    item {
                        HarnessSurface(Modifier.fillMaxWidth()) {
                            Column(
                                Modifier.fillMaxWidth().padding(HarnessTokens.Spacing.Three),
                                verticalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Three),
                            ) {
                                val plugin = detail.value.plugin

                                PluginField("说明", plugin.description ?: "暂无说明", "plugin-detail-description")
                                PluginField("版本", plugin.version ?: "版本未知", "plugin-detail-version")
                                PluginField("来源", plugin.source ?: "来源未知", "plugin-detail-source")
                                PluginField("状态", enabledLabel(plugin.enabled), "plugin-detail-status")
                                state.scope?.let { PluginField("主机", it.hostId.value, "plugin-detail-host") }
                            }
                        }
                    }
                    item {
                        HarnessText("Skills", style = MaterialTheme.typography.titleMedium)
                        when (val skills = detail.value.skills) {
                            PluginSkillsState.NotLoaded -> HarnessText("Skills 尚未加载", color = HarnessTokens.Colors.TextMuted)
                            is PluginSkillsState.Unavailable -> HarnessText(skills.reason, color = HarnessTokens.Colors.TextMuted)
                            is PluginSkillsState.Available -> if (skills.skills.isEmpty()) HarnessText(
                                "没有 Skills",
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
                            PluginAppsState.NotLoaded -> HarnessText("Apps 尚未加载", color = HarnessTokens.Colors.TextMuted)
                            is PluginAppsState.Unavailable -> HarnessText(apps.reason, color = HarnessTokens.Colors.TextMuted)
                            is PluginAppsState.Available -> if (apps.names.isEmpty()) HarnessText(
                                "没有 Apps",
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
                    if (detail is PluginLoadState.Error) HarnessText("无法加载插件", style = MaterialTheme.typography.titleMedium)
                    PluginReadFeedback(
                        detail,
                        onRetry = if (state.canRetryDetail) ({ onAction(InstalledPluginsAction.RetryDetail) }) else null,
                    )
                }
            }
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
    WorkspacePageFrame(tag, modifier) {
        BoxWithConstraints(Modifier.fillMaxSize()) { content(maxWidth < 600.dp) }
    }
}
@Composable
private fun PluginListHeader(
    state: InstalledPluginsState,
    onAction: (InstalledPluginsAction) -> Unit,
    compact: Boolean,
    showSearch: Boolean,
) {
    Column(Modifier.fillMaxWidth().semantics { testTag = "plugins-header" },
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        WorkspacePageHeader("插件") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HarnessButton("刷新", { onAction(InstalledPluginsAction.Refresh) }, enabled = state.canRefresh,
                    variant = HarnessButtonVariant.Secondary, testTag = "plugins-refresh")
                if (state.hasManagementAdapter) HarnessButton("添加本地插件", { onAction(InstalledPluginsAction.BeginRegistration) },
                    enabled = state.canManage, testTag = "plugins-add-local")
            }
        }
        if (showSearch) FlowRow(Modifier.fillMaxWidth().semantics { testTag = "plugins-search-actions" },
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HarnessSearchField(state.searchQuery, { onAction(InstalledPluginsAction.Search(it)) },
                placeholder = "搜索插件", accessibilityLabel = "搜索插件",
                modifier = Modifier.widthIn(max = if (compact) 440.dp else 320.dp).fillMaxWidth(),
                enabled = state.canSearch, testTag = "plugins-search")
            HarnessButton(if (state.enabledOnly) "显示全部插件" else "仅显示已启用",
                { onAction(InstalledPluginsAction.SetEnabledOnly(!state.enabledOnly)) },
                enabled = state.canSearch, selected = state.enabledOnly, variant = HarnessButtonVariant.Ghost,
                testTag = "plugins-enabled-only")
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
                HarnessText(plugin.source ?: "来源未知", Modifier.weight(1f),
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
    "apps (unsupported)" -> "Apps 不可用"
    "MCP (unsupported)" -> "MCP 不可用"
    "hooks (unsupported)" -> "Hooks 不可用"
    "scripts (unsupported)" -> "Scripts 不可用"
    "skill attachments (unsupported)" -> "技能附件不可用"
    "other capabilities (unsupported)" -> "其他能力不可用"
    else -> descriptor
}

@Composable
private fun PluginReadFeedback(state: PluginLoadState<*>, onRetry: (() -> Unit)?) {
    when (state) {
        PluginLoadState.NotLoaded -> HarnessEmptyState("插件尚未加载")
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
public fun PluginManagementFeedback(state: InstalledPluginsState) {
    val toast = LocalHarnessToastState.current
    LaunchedEffect(state.scope, state.mutationReceipt, state.managementNotice, state.mutationError) {
        state.mutationError?.let { toast?.show(it, isError = true) }
        if (state.mutationReceipt != null) state.managementNotice?.let { toast?.show(it) }
    }
    val refreshFailed = state.mutationReceipt != null &&
        (state.list is PluginLoadState.Error || state.detail is PluginLoadState.Error || state.revision is PluginLoadState.Error)
    LaunchedEffect(state.scope, state.mutationReceipt, refreshFailed) {
        if (refreshFailed) toast?.show("保存已完成，但插件信息未能刷新。请刷新重试。", isError = true)
    }
}

@Composable
private fun PluginManagementReadFeedback(state: InstalledPluginsState) {
    if (state.hasManagementAdapter && state.list is PluginLoadState.Success && state.revision is PluginLoadState.Error) {
        PluginReadFeedback(state.revision, onRetry = null)
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

private fun enabledLabel(state: PluginEnabledState): String = when (state) {
    PluginEnabledState.Enabled -> "已启用"
    PluginEnabledState.Disabled -> "已停用"
    PluginEnabledState.Unknown -> "状态未知"
}
