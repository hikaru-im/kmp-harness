package im.hikaru.harness.client.plugins

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag as testTagProperty
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import im.hikaru.contracts.harness.mcp.McpAuthStatus
import im.hikaru.contracts.harness.mcp.McpServerInfo
import im.hikaru.harness.client.page.WorkspacePageFrame
import im.hikaru.harness.client.page.WorkspacePageHeader
import im.hikaru.harness.ui.foundation.HarnessButton
import im.hikaru.harness.ui.foundation.HarnessButtonVariant
import im.hikaru.harness.ui.foundation.HarnessDialog
import im.hikaru.harness.ui.foundation.HarnessEmptyState
import im.hikaru.harness.ui.foundation.HarnessErrorState
import im.hikaru.harness.ui.foundation.HarnessLoadingState
import im.hikaru.harness.ui.foundation.HarnessSwitch
import im.hikaru.harness.ui.foundation.HarnessText
import im.hikaru.harness.ui.foundation.HarnessTextField
import im.hikaru.harness.ui.foundation.HarnessTokens

/** Codex-style Host-owned MCP settings; rows and controls are rendered only from a successful read. */
@Composable
public fun McpManagementPage(
    state: McpManagementState,
    controller: McpManagementController,
    modifier: Modifier = Modifier,
    externalUrlAvailable: Boolean = false,
) {
    WorkspacePageFrame("mcp-management-page", modifier) {
        Column(Modifier.fillMaxSize().padding(HarnessTokens.Spacing.Three),
            verticalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Three)) {
            WorkspacePageHeader("MCPs") {
                HarnessButton("刷新", { controller.dispatch(McpManagementAction.Refresh) },
                    enabled = state.canRefresh, variant = HarnessButtonVariant.Secondary,
                    testTag = "mcp-refresh")
                HarnessButton("添加服务器", { controller.dispatch(McpManagementAction.BeginAdd) },
                    enabled = state.canAdd, testTag = "mcp-add")
            }
            when (val load = state.load) {
                McpManagementLoadState.Unbound -> HarnessEmptyState("尚未连接 Host")
                McpManagementLoadState.Loading -> HarnessLoadingState("正在读取 MCP 服务器…")
                is McpManagementLoadState.Error -> HarnessErrorState(
                    load.message,
                    onRetry = { controller.dispatch(McpManagementAction.Refresh) },
                    retryLabel = "重试",
                    retryEnabled = state.canRefresh,
                )
                is McpManagementLoadState.Success -> if (load.response.servers.isEmpty()) {
                    HarnessEmptyState("尚无 MCP 服务器")
                } else {
                    LazyColumn(Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Two)) {
                        items(load.response.servers, key = { it.entryId }) { server ->
                            McpServerRow(state, controller, server, externalUrlAvailable)
                        }
                    }
                }
            }
        }
    }
    McpEditorDialog(state, controller)
    McpRemoveDialog(state, controller)
}

@Composable
private fun McpServerRow(
    state: McpManagementState,
    controller: McpManagementController,
    server: McpServerInfo,
    externalUrlAvailable: Boolean,
) {
    Row(
        Modifier.fillMaxWidth()
            .padding(HarnessTokens.Spacing.Two)
            .semantics { testTag = "mcp-server-${server.serverName}" },
        horizontalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Two),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            HarnessText(server.serverName, Modifier.semantics { testTagProperty = "mcp-server-name-${server.serverName}" })
            HarnessText(connectionLabel(server), Modifier.semantics { testTagProperty = "mcp-server-connection-${server.serverName}" })
            HarnessText(authLabel(server.authStatus), Modifier.semantics { testTagProperty = "mcp-server-auth-${server.serverName}" })
            server.toolCount?.let {
                HarnessText("工具 $it", Modifier.semantics { testTagProperty = "mcp-server-tools-${server.serverName}" })
            }
            if (server.resourcesAvailable) HarnessText("资源", Modifier.semantics { testTagProperty = "mcp-server-resources-${server.serverName}" })
        }
        HarnessSwitch(
            checked = server.enabled,
            onCheckedChange = { controller.dispatch(McpManagementAction.SetEnabled(server, it)) },
            contentDescription = "启用 ${server.serverName}",
            enabled = server.editable && !state.mutationBusy,
            testTag = "mcp-server-enabled-${server.serverName}",
        )
        HarnessButton("设置", { controller.dispatch(McpManagementAction.BeginEdit(server)) },
            enabled = server.editable && !state.mutationBusy,
            variant = HarnessButtonVariant.Secondary,
            testTag = "mcp-server-settings-${server.serverName}")
        if (server.authStatus == McpAuthStatus.NotLoggedIn) {
            HarnessButton("Authenticate", { controller.dispatch(McpManagementAction.Authenticate(server)) },
                enabled = externalUrlAvailable && !state.mutationBusy,
                variant = HarnessButtonVariant.Secondary,
                testTag = "mcp-server-authenticate-${server.serverName}")
        }
        HarnessButton("删除", { controller.dispatch(McpManagementAction.ConfirmRemove(server)) },
            enabled = server.editable && !state.mutationBusy,
            variant = HarnessButtonVariant.Ghost,
            testTag = "mcp-server-remove-${server.serverName}")
    }
}

@Composable
private fun McpEditorDialog(state: McpManagementState, controller: McpManagementController) {
    if (!state.editorVisible) return
    val serverName = state.editorServer?.serverName.orEmpty()
    HarnessDialog(
        visible = true,
        onDismissRequest = { controller.dispatch(McpManagementAction.DismissEditor) },
        title = if (state.editorServer == null) "添加 MCP 服务器" else "MCP 服务器设置",
    ) {
        McpEditorFields(state, controller, serverName)
    }
}

@Composable
private fun McpEditorFields(state: McpManagementState, controller: McpManagementController, serverName: String) {
    val draftVersion by controller.draftChanges.collectAsState()
    @Suppress("UNUSED_VARIABLE")
    val observedDraftVersion = draftVersion
    val draft = controller.draft(serverName)
    Column(Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Two)) {
        if (state.editorServer == null) {
            HarnessTextField(draft.serverName, {
                controller.dispatch(McpManagementAction.UpdateDraft(serverName) { current -> current.copy(serverName = it) })
            }, "服务器名称", Modifier.fillMaxWidth(), testTag = "mcp-editor-name")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Two)) {
            HarnessButton("Stdio", { controller.dispatch(McpManagementAction.SwitchTransport("stdio", serverName)) },
                enabled = draft.transport != "stdio", variant = HarnessButtonVariant.Secondary,
                testTag = "mcp-editor-transport-stdio")
            HarnessButton("HTTP", { controller.dispatch(McpManagementAction.SwitchTransport("http", serverName)) },
                enabled = draft.transport != "http", variant = HarnessButtonVariant.Secondary,
                testTag = "mcp-editor-transport-http")
        }
        if (draft.transport == "stdio") {
            HarnessTextField(draft.command, {
                controller.dispatch(McpManagementAction.UpdateDraft(serverName) { current -> current.copy(command = it) })
            }, "命令", Modifier.fillMaxWidth(), testTag = "mcp-editor-command")
            HarnessTextField(draft.args, {
                controller.dispatch(McpManagementAction.UpdateDraft(serverName) { current -> current.copy(args = it) })
            }, "参数（逗号分隔）", Modifier.fillMaxWidth(), testTag = "mcp-editor-args")
            HarnessTextField(draft.cwd, {
                controller.dispatch(McpManagementAction.UpdateDraft(serverName) { current -> current.copy(cwd = it) })
            }, "工作目录", Modifier.fillMaxWidth(), testTag = "mcp-editor-cwd")
            HarnessTextField(draft.envFrom, {
                controller.dispatch(McpManagementAction.UpdateDraft(serverName) { current -> current.copy(envFrom = it) })
            }, "环境变量引用（TARGET=SOURCE）", Modifier.fillMaxWidth(), testTag = "mcp-editor-env")
        } else {
            HarnessTextField(draft.url, {
                controller.dispatch(McpManagementAction.UpdateDraft(serverName) { current -> current.copy(url = it) })
            }, "HTTPS URL", Modifier.fillMaxWidth(), testTag = "mcp-editor-url")
            HarnessTextField(draft.bearerTokenEnvVar, {
                controller.dispatch(McpManagementAction.UpdateDraft(serverName) { current -> current.copy(bearerTokenEnvVar = it) })
            }, "Bearer Token 环境变量名", Modifier.fillMaxWidth(), testTag = "mcp-editor-bearer-env")
            HarnessTextField(draft.headersFrom, {
                controller.dispatch(McpManagementAction.UpdateDraft(serverName) { current -> current.copy(headersFrom = it) })
            }, "Header 环境变量引用（NAME=SOURCE）", Modifier.fillMaxWidth(), testTag = "mcp-editor-headers")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Two)) {
            HarnessButton("取消", { controller.dispatch(McpManagementAction.DismissEditor) },
                variant = HarnessButtonVariant.Secondary, testTag = "mcp-editor-cancel")
            HarnessButton("保存", { controller.dispatch(McpManagementAction.SaveEditor) },
                enabled = !state.mutationBusy, testTag = "mcp-editor-save")
        }
    }
}

@Composable
private fun McpRemoveDialog(state: McpManagementState, controller: McpManagementController) {
    val target = state.removeServer ?: return
    HarnessDialog(
        visible = true,
        onDismissRequest = { controller.dispatch(McpManagementAction.ConfirmRemove(null)) },
        title = "删除 ${target.serverName}？",
    ) {
        HarnessButton("取消", { controller.dispatch(McpManagementAction.ConfirmRemove(null)) },
            variant = HarnessButtonVariant.Secondary, testTag = "mcp-remove-cancel")
        HarnessButton("删除", { controller.dispatch(McpManagementAction.Remove) },
            enabled = !state.mutationBusy, variant = HarnessButtonVariant.Destructive,
            testTag = "mcp-remove-confirm")
    }
}

private fun connectionLabel(server: McpServerInfo): String = when (server.connectionPhase) {
    "connected" -> "已连接"
    "connecting" -> "连接中"
    "reconnecting" -> "重新连接中"
    "failed" -> "连接失败"
    "disabled" -> "已禁用"
    "configured" -> "已配置"
    else -> "状态未知"
}

private fun authLabel(status: McpAuthStatus): String = when (status) {
    McpAuthStatus.Unknown -> "认证状态未知"
    McpAuthStatus.Unsupported -> "认证不支持"
    McpAuthStatus.NotLoggedIn -> "未登录"
    McpAuthStatus.BearerToken -> "已认证（API key）"
    McpAuthStatus.OAuth -> "已认证（OAuth）"
}
