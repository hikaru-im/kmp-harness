package im.hikaru.harness.client.plugins

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import im.hikaru.harness.ui.foundation.*
import im.hikaru.harness.client.page.LocalWorkspaceNavigatorVisible

/**
 * Page-owned navigator of the plugins page.
 *
 * The 2026-10-06 Codex capture (docs/desktop-ui/reference/captures/2026-10-06-user-pages/
 * plugins-marketplace.png) measures this column as: a title strip whose search action sits at the
 * right edge, two 30px leading-glyph rows on a 1px pitch, a muted group heading, then 30px item rows
 * with a 16px leading glyph and their label at +40. Every value below is taken from that capture
 * normalized to the navigator origin (x 52, y 74), the same method Batch P used for the scheduled
 * navigator.
 *
 * The group lists this app own registered plugins, read from the bound Host. It never renders an
 * entry the Host did not report, and it shows the truthful unavailable/error/empty state instead.
 */
@Composable
public fun PluginsManagementNavigator(
    state: InstalledPluginsState,
    onQueryChange: (String) -> Unit,
    onSetEnabledOnly: (Boolean) -> Unit,
    onSelect: (PluginId) -> Unit,
    onRetry: () -> Unit,
) {
    val list = state.list
    Column(
        Modifier.fillMaxSize()
            .padding(horizontal = HarnessTokens.Spacing.Two, vertical = HarnessTokens.Spacing.Three)
            .semantics { testTag = "plugins-management-navigator" },
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.fillMaxWidth().semantics { testTag = "plugins-navigator-header" },
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = HarnessTokens.Spacing.Two),
                verticalAlignment = Alignment.CenterVertically) {
                HarnessText("插件", Modifier.weight(1f))
            }
            HarnessSearchField(
                value = state.searchQuery, onValueChange = onQueryChange,
                placeholder = "搜索插件", accessibilityLabel = "搜索插件",
                modifier = Modifier.fillMaxWidth().padding(horizontal = HarnessTokens.Spacing.Two),
                enabled = state.canSearch,
                testTag = if (LocalWorkspaceNavigatorVisible.current) "plugins-search" else "plugins-navigator-search",
            )
            // The capture draws the two destination rows as one tight group on a 1px pitch, so the
            // group owns the 1px gap instead of the 12dp header gap.
            Column(Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(HarnessTokens.Control.NavigatorRowGap)) {
                PluginsFilterRow(label = "全部插件", selected = !state.enabledOnly, enabled = state.canSearch,
                    testTag = "plugins-filter-all") { onSetEnabledOnly(false) }
                PluginsFilterRow(label = "仅启用的插件", selected = state.enabledOnly, enabled = state.canSearch,
                    testTag = "plugins-filter-enabled") { onSetEnabledOnly(true) }
            }
            HarnessText(if (state.hasManagementAdapter) "已添加" else "已安装",
                Modifier.padding(start = HarnessTokens.Spacing.Two, end = HarnessTokens.Spacing.Two,
                    top = HarnessTokens.Spacing.Two),
                color = HarnessTokens.Colors.TextMuted, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
            .semantics { testTag = "plugins-navigator-list" },
            verticalArrangement = Arrangement.spacedBy(HarnessTokens.Control.NavigatorRowGap)) {
            when (list) {
                is PluginLoadState.Success -> {
                    val plugins = state.visiblePlugins.orEmpty()
                    if (plugins.isEmpty()) HarnessNavigatorHint(if (state.visibleFilterIsEmpty()) "没有匹配的已登记插件" else "暂无已登记插件")
                    plugins.forEach { plugin ->
                        PluginsInstalledRow(plugin = plugin, selected = state.selectedId == plugin.id) { onSelect(plugin.id) }
                    }
                }
                PluginLoadState.NotLoaded, PluginLoadState.Loading -> HarnessNavigatorHint("正在读取插件…")
                is PluginLoadState.Unavailable -> HarnessNavigatorHint("插件服务未接入")
                is PluginLoadState.AuthRequired -> HarnessNavigatorHint(list.message)
                is PluginLoadState.UnsupportedHost -> HarnessNavigatorHint(list.message)
                is PluginLoadState.Error -> {
                    HarnessNavigatorHint(list.error.message, danger = true)
                    HarnessButton("重试", onRetry, enabled = state.canRefresh,
                        variant = HarnessButtonVariant.Secondary, testTag = "plugins-navigator-retry")
                }
            }
        }
    }
}

/** True when the empty list is the result of a filter rather than an empty inventory. */
private fun InstalledPluginsState.visibleFilterIsEmpty(): Boolean =
    enabledOnly || searchQuery.isNotBlank()

/** Adds the navigator inset so the hint and the rows share one left edge (16px from the pane edge). */
@Composable
private fun HarnessNavigatorHint(text: String, danger: Boolean = false) {
    HarnessText(text, Modifier.padding(horizontal = HarnessTokens.Spacing.Two),
        color = if (danger) HarnessTokens.Colors.Danger else HarnessTokens.Colors.TextMuted, maxLines = 3)
}

/**
 * Filter row. Codex draws it as a 30px leading-glyph row that fills the pane width when selected, so
 * this stays a row rather than a filled button; the tags and the click behaviour are unchanged from
 * the previous navigator.
 */
@Composable
private fun PluginsFilterRow(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    testTag: String,
    onClick: () -> Unit,
) {
    val tint = if (enabled) HarnessTokens.Colors.Text else HarnessTokens.Colors.TextDisabled
    Row(
        Modifier.fillMaxWidth()
            .height(HarnessTokens.Control.NavRow)
            .clip(RoundedCornerShape(HarnessTokens.Control.NavigatorRowRadius))
            .background(if (selected) HarnessTokens.Colors.SurfaceSelected else Color.Transparent)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics {
                this.testTag = testTag
                this.selected = selected
                if (!enabled) disabled()
            }
            .padding(horizontal = HarnessTokens.Spacing.Two),
        horizontalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Two),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HarnessIcon(HarnessIconName.Plug, tint = tint, size = HarnessTokens.Control.NavigatorIcon)
        HarnessText(label, color = tint, maxLines = 1)
    }
}

/**
 * Installed item row. The capture draws one 30px line per item, so the enabled state travels in the
 * accessibility description instead of a second visual line.
 */
@Composable
private fun PluginsInstalledRow(plugin: InstalledPlugin, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .height(HarnessTokens.Control.NavRow)
            .clip(RoundedCornerShape(HarnessTokens.Control.NavigatorRowRadius))
            .background(if (selected) HarnessTokens.Colors.SurfaceSelected else Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                testTag = "plugins-installed:" + plugin.id.value
                contentDescription = plugin.name + "，" + pluginStatusLabel(plugin.enabled)
                this.selected = selected
            }
            .padding(horizontal = HarnessTokens.Spacing.Two),
        horizontalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Two),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HarnessIcon(HarnessIconName.Plug, tint = HarnessTokens.Colors.TextMuted, size = HarnessTokens.Control.NavigatorIcon)
        HarnessText(plugin.name, maxLines = 1)
    }
}

/** Short enabled-state label; the page uses the longer service-report wording on the detail field. */
private fun pluginStatusLabel(state: PluginEnabledState): String =
    when (state) {
        PluginEnabledState.Enabled -> "已启用"
        PluginEnabledState.Disabled -> "已停用"
        PluginEnabledState.Unknown -> "启用状态未知"
    }
