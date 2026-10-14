package im.hikaru.harness.client.page

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import im.hikaru.contracts.harness.identity.HostDescription
import im.hikaru.contracts.harness.protocol.ProtocolVersion
import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.harness.client.connection.ConnectionTransport
import im.hikaru.harness.client.connection.ConnectionsPhase
import im.hikaru.harness.client.connection.ConnectionsState
import im.hikaru.harness.ui.foundation.HarnessButton
import im.hikaru.harness.ui.foundation.HarnessButtonVariant
import im.hikaru.harness.ui.foundation.HarnessControlSize
import im.hikaru.harness.ui.foundation.HarnessControlState
import im.hikaru.harness.ui.foundation.HarnessDialog
import im.hikaru.harness.ui.foundation.HarnessEmptyState
import im.hikaru.harness.ui.foundation.HarnessErrorState
import im.hikaru.harness.ui.foundation.HarnessLoadingState
import im.hikaru.harness.ui.foundation.HarnessText
import im.hikaru.harness.ui.foundation.HarnessTokens

/** Controlled page: describe/discovery, lifecycle and actions belong to the connection owner. */
@Composable
public fun SettingsConnectionsPage(
    state: ConnectionsState,
    onRefresh: () -> Unit,
    onSelectHost: (HostDescription) -> Unit,
) {
    // Keep an identity, never a DTO snapshot. The exposed connection scope resets the dialog.
    var detailsTarget by remember(state.scope, state.transport, state.currentHost?.hostId) {
        mutableStateOf<ConnectionsDetailsTarget?>(null)
    }
    val detailsHost = detailsTarget?.let { target ->
        if (target.fromDiscovery) state.hosts.firstOrNull { it.hostId == target.hostId }
        else state.currentHost?.takeIf { it.hostId == target.hostId }
    }
    LaunchedEffect(detailsTarget, detailsHost?.hostId) {
        if (detailsHost == null) detailsTarget = null
    }
    val refresh: () -> Unit = { if (state.canRefresh) onRefresh() }
    val selectionReason = when {
        state.transport == ConnectionTransport.Local ->
            state.selectionUnavailableReason ?: "本机模式不支持切换远程 Host。"
        state.selectionUnavailableReason != null -> state.selectionUnavailableReason
        state.busy -> "正在刷新或连接，请等待操作完成后再选择 Host。"
        else -> null
    }

    Box(Modifier.fillMaxSize().semantics { testTag = "settings-connections-page" }) {
        SettingsFormFrame(title = "连接", testTag = "settings-connections-title") {
            SettingsFormSection("当前连接") {
                SettingsFormCard {
                    val currentHost = state.currentHost
                    SettingsFormRow(
                        label = state.transport.label,
                        description = currentHost?.let { "${it.displayName}\n${connectionsHostMetadata(it)}" }
                            ?: "尚无当前 Host 信息。",
                    ) {
                        // The discovery row owns this action when present, keeping its tag unique.
                        if (currentHost != null && state.hosts.none { it.hostId == currentHost.hostId }) {
                            ConnectionsDetailsButton(currentHost) {
                                detailsTarget = ConnectionsDetailsTarget(currentHost.hostId, fromDiscovery = false)
                            }
                        }
                    }
                    SettingsFormRow(
                        label = "连接状态",
                        description = state.phase.label,
                    ) {
                        HarnessButton(
                            text = if (state.refreshing) "刷新中" else "刷新",
                            onClick = refresh,
                            enabled = state.canRefresh,
                            state = if (state.refreshing) HarnessControlState.Loading else HarnessControlState.Normal,
                            testTag = "connections-refresh",
                            variant = HarnessButtonVariant.Secondary,
                            size = HarnessControlSize.Compact,
                        )
                    }
                    state.refreshUnavailableReason?.let { ConnectionsNote(it) }
                }
            }

            SettingsFormSection("已发现的编程 Host") {
                if (state.transport == ConnectionTransport.Relay) {
                    ConnectionsNote("发现结果不代表已建立连接。切换 Host 会丢弃旧 Host 的事件订阅，后续会话操作将使用所选 Host。")
                }
                selectionReason?.let { ConnectionsNote(it) }
                state.error?.let { message ->
                    SettingsFormCard {
                        HarnessErrorState(message = message)
                        HarnessButton(
                            text = "重试",
                            onClick = refresh,
                            enabled = state.canRefresh,
                            state = if (state.refreshing) HarnessControlState.Loading else HarnessControlState.Normal,
                            testTag = "connections-retry",
                            variant = HarnessButtonVariant.Secondary,
                            size = HarnessControlSize.Compact,
                        )
                        state.refreshUnavailableReason?.let { ConnectionsNote(it) }
                    }
                }
                if (state.busy) {
                    HarnessLoadingState(
                        message = when {
                            state.refreshing -> if (state.transport == ConnectionTransport.Local)
                                "正在读取本机 Host 信息…" else "正在刷新 Host 列表…"
                            else -> state.phase.label + "…"
                        },
                    )
                }
                if (state.hosts.isEmpty()) {
                    if (!state.busy) {
                        SettingsFormCard {
                            HarnessEmptyState(
                                title = "暂无已发现的 Host",
                                description = if (state.transport == ConnectionTransport.Local)
                                    "本机 Host 信息由当前连接提供；可用时通过刷新重新读取。"
                                else "当前账号作用域没有可展示的发现结果；可用时通过刷新重新发现。",
                            )
                        }
                    }
                } else {
                    state.hosts.forEach { host ->
                        key(host.hostId) {
                            val isCurrent = host.hostId == state.currentHost?.hostId
                            val isOnline = isCurrent && state.phase == ConnectionsPhase.Online
                            val compatible = host.protocolVersion.major == ProtocolVersion.Current.major
                            val hostStatus = if (!compatible) "协议不兼容，无法连接"
                                else if (isCurrent) "当前 Host · ${state.phase.label}"
                                else "已发现 · 不代表在线"
                            Column(Modifier.fillMaxWidth().semantics {
                                testTag = "connections-host-${host.hostId.value}"
                                stateDescription = hostStatus
                            }) {
                                SettingsFormCard {
                                    SettingsFormRow(
                                        label = host.displayName,
                                        description = "${connectionsHostMetadata(host)}\n$hostStatus",
                                    ) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            if (state.transport == ConnectionTransport.Relay) {
                                                val canSelect = state.canSelect && !isOnline && compatible
                                                HarnessButton(
                                                    text = if (isOnline) "已连接" else if (isCurrent) "重新连接" else "连接",
                                                    onClick = { if (canSelect) onSelectHost(host) },
                                                    enabled = canSelect,
                                                    testTag = "connections-select-${host.hostId.value}",
                                                    variant = HarnessButtonVariant.Secondary,
                                                    size = HarnessControlSize.Compact,
                                                )
                                            }
                                            ConnectionsDetailsButton(host) {
                                                detailsTarget = ConnectionsDetailsTarget(host.hostId, fromDiscovery = true)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            SettingsFormSection("SSH / WSL") {
                SettingsFormCard {
                    SettingsFormRow(
                        label = "SSH",
                        description = "尚未接入 SSH 连接适配器，暂不可发现、添加或连接 SSH Host。",
                    ) {
                        HarnessButton(
                            text = "添加 SSH",
                            onClick = {},
                            enabled = false,
                            testTag = "connections-ssh-add",
                            variant = HarnessButtonVariant.Secondary,
                            size = HarnessControlSize.Compact,
                        )
                    }
                    SettingsFormRow(
                        label = "WSL",
                        description = "尚未接入 WSL 连接适配器，暂不可发现或连接 WSL Host。",
                    ) {
                        HarnessButton(
                            text = "连接 WSL",
                            onClick = {},
                            enabled = false,
                            testTag = "connections-wsl-connect",
                            variant = HarnessButtonVariant.Secondary,
                            size = HarnessControlSize.Compact,
                        )
                    }
                }
            }
        }
    }

    detailsHost?.let { host ->
        ConnectionsDetailsDialog(host = host, onDismiss = { detailsTarget = null })
    }
}

private data class ConnectionsDetailsTarget(val hostId: HostId, val fromDiscovery: Boolean)

private fun connectionsHostMetadata(host: HostDescription): String =
    "Host ID：${host.hostId.value}\n协议：${host.protocolVersion.major}.${host.protocolVersion.minor}"

@Composable
private fun ConnectionsNote(text: String) {
    HarnessText(text, color = HarnessTokens.Colors.TextMuted, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun ConnectionsDetailsButton(host: HostDescription, onClick: () -> Unit) {
    HarnessButton(
        text = "详情",
        onClick = onClick,
        testTag = "connections-details-${host.hostId.value}",
        variant = HarnessButtonVariant.Secondary,
        size = HarnessControlSize.Compact,
    )
}

@Composable
private fun ConnectionsDetailsDialog(host: HostDescription, onDismiss: () -> Unit) {
    HarnessDialog(visible = true, onDismissRequest = onDismiss, title = "Host 详情") {
        Column(
            Modifier.fillMaxWidth().semantics { testTag = "connections-details-dialog" },
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ConnectionsDetailField("名称", host.displayName)
                ConnectionsDetailField("Host ID", host.hostId.value)
                ConnectionsDetailField("协议", "${host.protocolVersion.major}.${host.protocolVersion.minor}")
                ConnectionsDetailField(
                    "协议能力",
                    host.capabilities.joinToString("\n") { "${it.id.value} · v${it.version}" }
                        .ifEmpty { "未声明协议能力" },
                )
                ConnectionsNote("协议能力声明不代表当前用户的使用权限，也不保证服务当前可用。")
            }
            HarnessButton(
                text = "关闭",
                onClick = onDismiss,
                testTag = "connections-details-close",
                variant = HarnessButtonVariant.Secondary,
                size = HarnessControlSize.Compact,
            )
        }
    }
}

@Composable
private fun ConnectionsDetailField(label: String, value: String) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ConnectionsNote(label)
        HarnessText(value, style = MaterialTheme.typography.bodyMedium)
    }
}
