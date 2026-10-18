package im.hikaru.harness.client.account

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import im.hikaru.contracts.harness.identity.HostDescription
import im.hikaru.harness.client.app.HarnessConnectionState
import im.hikaru.harness.client.page.*
import im.hikaru.harness.ui.foundation.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Account UI uses the existing account owner for tenant, login and logout operations. */
@Composable
public fun AccountWorkspacePage(
    account: MemberAccount?, state: AccountState, status: HarnessConnectionState, host: HostDescription?,
    onOpenSettings: () -> Unit, onLoggedIn: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var mobile by remember(account, state.target, state.session?.identity) { mutableStateOf("") }
    var password by remember(account, state.target, state.session?.identity) { mutableStateOf("") }
    var error by remember(account, state.target) { mutableStateOf<String?>(null) }
    var busy by remember(account) { mutableStateOf(false) }
    var selectTenant by remember(account) { mutableStateOf(false) }
    var details by remember(account, state.session?.identity) { mutableStateOf(false) }
    WorkspacePageFrame("account-workspace-page", maxContentWidth = 960.dp) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            WorkspacePageHeader("账号") {
                HarnessButton("设置", onOpenSettings, variant = HarnessButtonVariant.Secondary, testTag = "account-settings")
            }
            Row(Modifier.fillMaxWidth().semantics { testTag = "account-connection" },
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                HarnessIcon(HarnessIconName.Host, tint = HarnessTokens.Colors.TextMuted)
                HarnessText(host?.displayName ?: "未选择主机", Modifier.weight(1f), maxLines = 2)
                HarnessBadge(status.label, color = when (status) {
                    HarnessConnectionState.Local, HarnessConnectionState.Online -> HarnessTokens.Colors.Success
                    HarnessConnectionState.Disconnected, HarnessConnectionState.ProtocolIncompatible -> HarnessTokens.Colors.Danger
                    else -> HarnessTokens.Colors.Accent
                })
            }
            HarnessSurface(Modifier.widthIn(max = 440.dp).fillMaxWidth().semantics { testTag = "account-card" }) {
                Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    HarnessText(if (state.session == null) "登录远程账号" else "当前账号", style = MaterialTheme.typography.titleLarge)
                    if (account == null) {
                        HarnessText("本地模式无需登录。", color = HarnessTokens.Colors.TextMuted)
                        HarnessButton("登录", {}, modifier = Modifier.fillMaxWidth(), enabled = false, testTag = "account-login")
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                HarnessText("租户", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                                HarnessButton(if (state.target == null) "选择租户" else "更换", { selectTenant = true },
                                    enabled = !busy, variant = HarnessButtonVariant.Secondary,
                                    size = HarnessControlSize.Compact, testTag = "account-select-tenant")
                            }
                            HarnessText(state.target?.let { "租户 ${it.tenantId}" } ?: "请选择登录目标",
                                Modifier.semantics { testTag = "account-current-tenant" }, color = HarnessTokens.Colors.TextMuted)
                            state.target?.let { HarnessText(it.baseUrl, color = HarnessTokens.Colors.TextMuted, style = MaterialTheme.typography.bodySmall) }
                        }
                        if (state.session == null) {
                            HarnessTextField(mobile, { mobile = it }, "手机号", Modifier.fillMaxWidth(), enabled = !busy,
                                testTag = "account-mobile")
                            HarnessTextField(password, { password = it }, "密码", Modifier.fillMaxWidth(), enabled = !busy,
                                visualTransformation = PasswordVisualTransformation(), testTag = "account-password")
                            HarnessButton(if (busy) "登录中…" else "登录", {
                                val owner = account
                                val target = owner.state.value.target
                                if (!busy && target != null && mobile.isNotBlank() && password.isNotBlank()) {
                                    busy = true; error = null
                                    val loginMobile = mobile.trim()
                                    val loginPassword = password
                                    scope.launch {
                                        try {
                                            owner.login(loginMobile, loginPassword)
                                            if (owner.state.value.session?.identity?.target == target) onLoggedIn()
                                        } catch (cancelled: CancellationException) { throw cancelled
                                        } catch (failure: AccountException) { error = accountErrorLabel(failure)
                                        } finally { busy = false; password = "" }
                                    }
                                }
                            }, modifier = Modifier.fillMaxWidth(),
                                enabled = !busy && state.target != null && mobile.isNotBlank() && password.isNotBlank(), testTag = "account-login")
                        } else {
                            HarnessText(state.profile?.nickname ?: "用户 ${state.session.identity.userId}",
                                Modifier.semantics { testTag = "account-user" })
                            HarnessButton("退出登录", {
                                if (!busy) {
                                    busy = true; error = null
                                    scope.launch {
                                        try { account.logout() }
                                        catch (cancelled: CancellationException) { throw cancelled }
                                        catch (failure: AccountException) { error = accountErrorLabel(failure) }
                                        finally { busy = false }
                                    }
                                }
                            }, enabled = !busy, variant = HarnessButtonVariant.Secondary, testTag = "account-logout")
                            HarnessButton(if (details) "收起账号详情" else "账号详情", { details = !details },
                                variant = HarnessButtonVariant.Ghost, size = HarnessControlSize.Compact, testTag = "account-details")
                            if (details) HarnessText("登录存储：" + state.persistence.name, color = HarnessTokens.Colors.TextMuted,
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    error?.let { HarnessText(it, Modifier.semantics { testTag = "account-error" }, color = HarnessTokens.Colors.Danger) }
                    if (account != null && (state.session != null || state.persistence == PersistenceStatus.READ_FAILED ||
                            state.persistence == PersistenceStatus.CLEAR_FAILED)) state.persistence.message?.let {
                        HarnessText(it, Modifier.semantics { testTag = "account-storage-status" },
                            color = HarnessTokens.Colors.TextMuted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
    if (selectTenant && account != null) AccountTenantDialog(account, state, { selectTenant = false })
}

@Composable
private fun AccountTenantDialog(account: MemberAccount, state: AccountState, onDismiss: () -> Unit) {
    var baseUrl by remember(state.target) { mutableStateOf(state.target?.baseUrl ?: "https://") }
    var tenantId by remember(state.target) { mutableStateOf(state.target?.tenantId?.toString().orEmpty()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    HarnessDialog(true, { if (!busy) onDismiss() }, "选择租户") {
        Column(Modifier.widthIn(max = 400.dp).fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (state.session != null) HarnessText("切换租户将退出当前账号。", color = HarnessTokens.Colors.TextMuted)
            HarnessTextField(baseUrl, { baseUrl = it }, "服务地址", Modifier.fillMaxWidth(), enabled = !busy, testTag = "account-tenant-url")
            HarnessTextField(tenantId, { tenantId = it }, "租户 ID", Modifier.fillMaxWidth(), enabled = !busy, testTag = "account-tenant-id")
            error?.let { HarnessText(it, Modifier.semantics { testTag = "account-tenant-error" }, color = HarnessTokens.Colors.Danger) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HarnessButton(if (busy) "正在选择…" else "确认选择", {
                    if (!busy) {
                        val candidate = try { BackendTenant(baseUrl.trim().trimEnd('/'), tenantId.trim().toLong()) }
                            catch (_: Exception) { null }
                        if (candidate == null) error = "请输入有效的服务地址和租户 ID"
                        else {
                            busy = true; error = null
                            scope.launch {
                                try { account.selectTarget(candidate); onDismiss() }
                                catch (cancelled: CancellationException) { throw cancelled }
                                catch (failure: AccountException) { error = accountErrorLabel(failure) }
                                finally { busy = false }
                            }
                        }
                    }
                }, enabled = !busy, testTag = "account-tenant-confirm")
                HarnessButton("取消", onDismiss, enabled = !busy, variant = HarnessButtonVariant.Secondary, testTag = "account-tenant-cancel")
            }
        }
    }
}

private fun accountErrorLabel(failure: AccountException): String = when (failure) {
    is AccountException.Authentication, is AccountException.Rejected -> "登录信息无效或请求被拒绝，请检查后重试。"
    is AccountException.Network -> "无法连接服务，请稍后重试。"
    is AccountException.Storage -> "无法更新安全登录记录，请解锁系统安全存储后重试。"
    is AccountException.InvalidResponse -> "服务响应无效，请稍后重试。"
}
