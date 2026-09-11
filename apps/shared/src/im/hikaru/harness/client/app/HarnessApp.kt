package im.hikaru.harness.client.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import im.hikaru.contracts.harness.identity.HostDescription
import im.hikaru.contracts.harness.protocol.ProtocolVersion
import im.hikaru.harness.client.account.AccountException
import im.hikaru.harness.client.account.AccountState
import im.hikaru.harness.client.account.BackendTenant
import im.hikaru.harness.client.account.MemberAccount
import im.hikaru.harness.client.connection.Connection
import im.hikaru.harness.client.connection.AgentOptions
import im.hikaru.harness.client.connection.CreateSessionOptions
import im.hikaru.harness.client.connection.Message
import im.hikaru.harness.client.connection.SessionId
import im.hikaru.harness.client.connection.SessionSummary
import im.hikaru.harness.client.connection.TextContent
import im.hikaru.harness.client.connection.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** The first application shell shared by Desktop, Android and iOS. */
@Composable
public fun HarnessApp(
    connection: Connection?,
    account: MemberAccount? = null,
    defaultTarget: BackendTenant? = null,
    availableHosts: List<HostDescription> = emptyList(),
    onSelectHost: ((HostDescription) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var destination by remember { mutableStateOf(HarnessDestination.Harness) }
    var pendingDestination by remember { mutableStateOf<HarnessDestination?>(null) }
    var host by remember { mutableStateOf<HostDescription?>(null) }
    var connectionState by remember {
        mutableStateOf(if (connection == null) HarnessConnectionState.RequiresLogin else HarnessConnectionState.Connecting)
    }
    val accountState = account?.state?.collectAsState()?.value ?: AccountState()
    LaunchedEffect(account, defaultTarget) {
        if (account != null && defaultTarget != null && account.state.value.target == null) {
            try {
                account.selectTarget(defaultTarget)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                // The tenant screen remains available for an explicit retry.
            }
        }
    }
    LaunchedEffect(connection, accountState.session == null) {
        if (connection == null) {
            connectionState = if (accountState.session == null) {
                HarnessConnectionState.RequiresLogin
            } else {
                HarnessConnectionState.NoHost
            }
            return@LaunchedEffect
        }
        connectionState = HarnessConnectionState.Connecting
        try {
            val result = connection.host.describe()
            val description = result.data
            if (result.isSuccess && description != null) {
                host = description
                connectionState =
                    if (description.protocolVersion.major == ProtocolVersion.Current.major) {
                        HarnessConnectionState.Online
                    } else {
                        HarnessConnectionState.ProtocolIncompatible
                    }
            } else {
                connectionState = HarnessConnectionState.Disconnected
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            connectionState = HarnessConnectionState.Disconnected
        }
    }

    MaterialTheme {
        Column(
            modifier = modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Harness", style = MaterialTheme.typography.headlineSmall)
            NavigationBar(
                current = destination,
                onSelect = { selected ->
                    if (selected.requiresConnection && connection == null) {
                        pendingDestination = selected
                        destination = HarnessDestination.Account
                    } else {
                        destination = selected
                    }
                },
            )
            HorizontalDivider()
            when (destination) {
                HarnessDestination.Harness -> HarnessHome(
                    host = host,
                    status = connectionState,
                    onSessions = {
                        if (connection == null) {
                            pendingDestination = HarnessDestination.Sessions
                            destination = HarnessDestination.Account
                        } else {
                            destination = HarnessDestination.Sessions
                        }
                    },
                )
                HarnessDestination.Account -> AccountScreen(
                    account = account,
                    state = accountState,
                    onLoggedIn = {
                        val target = pendingDestination
                        pendingDestination = null
                        destination = target ?: HarnessDestination.Account
                    },
                )
                HarnessDestination.Tenant -> TenantScreen(account, accountState)
                HarnessDestination.Profile -> ProfileScreen(account, accountState)
                HarnessDestination.Hosts -> HostScreen(host, availableHosts, connectionState, onSelectHost)
                HarnessDestination.Sessions -> SessionScreen(connection)
            }
        }
    }
}

public enum class HarnessDestination(
    public val label: String,
    public val requiresConnection: Boolean = false,
) {
    Harness("主页"),
    Account("账号"),
    Tenant("租户"),
    Profile("资料"),
    Hosts("Host"),
    Sessions("会话", requiresConnection = true),
}

public enum class HarnessConnectionState(public val label: String) {
    Connecting("连接中"),
    Online("在线"),
    Local("本地"),
    NoHost("暂无在线 Host"),
    RequiresLogin("需要登录"),
    Disconnected("已断开"),
    ProtocolIncompatible("协议不兼容"),
}

@Composable
private fun NavigationBar(
    current: HarnessDestination,
    onSelect: (HarnessDestination) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        HarnessDestination.entries.forEach { destination ->
            TextButton(onClick = { onSelect(destination) }) {
                Text(if (destination == current) "[${destination.label}]" else destination.label)
            }
        }
    }
}

@Composable
private fun HarnessHome(
    host: HostDescription?,
    status: HarnessConnectionState,
    onSessions: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Harness 首页", style = MaterialTheme.typography.titleLarge)
        Text("连接状态：${status.label}")
        if (host != null) {
            Text(host.displayName)
            Text("Host ID：${host.hostId.value}")
        } else {
            Text("本地 Harness 可在无账号和无网络时使用。")
        }
        Button(onClick = onSessions, enabled = status == HarnessConnectionState.Online || status == HarnessConnectionState.Local) {
            Text("打开会话")
        }
    }
}

@Composable
private fun AccountScreen(
    account: MemberAccount?,
    state: AccountState,
    onLoggedIn: () -> Unit,
) {
    if (account == null) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("本地模式无需登录", style = MaterialTheme.typography.titleLarge)
            Text("连接本地 Harness 不需要远程账号。")
        }
        return
    }
    val scope = rememberCoroutineScope()
    var mobile by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("账号", style = MaterialTheme.typography.titleLarge)
        if (state.session == null) {
            Text("选择租户后登录远程 Harness。")
            OutlinedTextField(mobile, { mobile = it }, label = { Text("手机号") }, singleLine = true)
            OutlinedTextField(password, { password = it }, label = { Text("密码") }, singleLine = true)
            Button(
                onClick = {
                    error = null
                    loading = true
                    scope.launch {
                        try {
                            account.login(mobile, password)
                            onLoggedIn()
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: AccountException) {
                            error = failure.message
                        } finally {
                            loading = false
                        }
                    }
                },
                enabled = !loading && mobile.isNotBlank() && password.isNotBlank() && state.target != null,
            ) { Text(if (loading) "登录中" else "登录") }
            if (state.target == null) Text("请先在租户页选择目标。")
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } else {
            Text("已登录用户：${state.session.identity.userId}")
            Text("持久化：${state.persistence.name}")
            Button(onClick = { scope.launch { account.logout() } }) { Text("退出登录") }
        }
    }
}

@Composable
private fun TenantScreen(account: MemberAccount?, state: AccountState) {
    val scope = rememberCoroutineScope()
    var baseUrl by remember { mutableStateOf(state.target?.baseUrl ?: "https://") }
    var tenantId by remember { mutableStateOf(state.target?.tenantId?.toString() ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("租户", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(baseUrl, { baseUrl = it }, label = { Text("后端地址") }, singleLine = true)
        OutlinedTextField(tenantId, { tenantId = it }, label = { Text("租户 ID") }, singleLine = true)
        Button(onClick = {
            val id = tenantId.toLongOrNull()
            if (account == null || id == null) {
                error = "请选择有效租户"
            } else {
                scope.launch {
                    try {
                        account.selectTarget(BackendTenant(baseUrl.trimEnd('/'), id))
                        error = null
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Throwable) {
                        error = failure.message ?: "租户不可用"
                    }
                }
            }
        }) { Text("选择租户") }
        Text("当前：${state.target?.tenantId ?: "未选择"}")
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun ProfileScreen(account: MemberAccount?, state: AccountState) {
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("个人资料", style = MaterialTheme.typography.titleLarge)
        if (state.session == null) {
            Text("登录后查看个人资料。")
        } else {
            val profile = state.profile
            Text(profile?.nickname ?: "资料尚未加载")
            Button(onClick = {
                scope.launch {
                    try {
                        account?.loadProfile()
                        error = null
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Throwable) {
                        error = failure.message ?: "资料加载失败"
                    }
                }
            }) { Text("刷新资料") }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun HostScreen(
    host: HostDescription?,
    availableHosts: List<HostDescription>,
    status: HarnessConnectionState,
    onSelectHost: ((HostDescription) -> Unit)?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Host", style = MaterialTheme.typography.titleLarge)
        Text("连接状态：${status.label}")
        Text(host?.displayName ?: "未选择 Host")
        host?.let { Text("Host ID：${it.hostId.value}") }
        availableHosts.forEach { candidate ->
            TextButton(onClick = { onSelectHost?.invoke(candidate) }) {
                Text(candidate.displayName)
            }
        }
    }
}

@Composable
private fun SessionScreen(connection: Connection?) {
    if (connection == null) {
        Text("远程会话需要登录并选择在线 Host。")
        return
    }
    val scope = rememberCoroutineScope()
    var sessions by remember { mutableStateOf<List<SessionSummary>>(emptyList()) }
    var selected by remember { mutableStateOf<SessionId?>(null) }
    var provider by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }
    var history by remember { mutableStateOf<List<String>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(connection) {
        try {
            sessions = connection.session.list()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            error = "会话列表不可用"
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("会话", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(provider, { provider = it }, label = { Text("Provider") }, singleLine = true)
        OutlinedTextField(model, { model = it }, label = { Text("Model") }, singleLine = true)
        Button(onClick = {
            scope.launch {
                try {
                    val created = connection.session.create(
                        options = CreateSessionOptions(),
                        agentOptions = AgentOptions(
                            provider = provider.trim().takeIf(String::isNotEmpty),
                            model = model.trim().takeIf(String::isNotEmpty),
                        ),
                    )
                    sessions = connection.session.list()
                    selected = created.id
                    history = connection.session.history(created.id).map(::displayMessage)
                    error = null
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Throwable) {
                    error = failure.message ?: "会话创建失败"
                }
            }
        }) { Text("创建会话") }
        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f, fill = false)) {
            items(sessions, key = { it.id.value }) { item ->
                TextButton(onClick = {
                    selected = item.id
                    scope.launch {
                        try {
                            history = connection.session.history(item.id).map(::displayMessage)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Throwable) {
                            error = "历史记录不可用"
                        }
                    }
                }) { Text(item.id.value) }
            }
        }
        selected?.let { id ->
            OutlinedTextField(prompt, { prompt = it }, label = { Text("消息") }, singleLine = false)
            Row {
                Button(onClick = {
                    scope.launch {
                        try {
                            val receipt = connection.session.prompt(
                                id,
                                userMessage(prompt),
                            )
                            receipt.awaitIdle()
                            history = connection.session.history(id).map(::displayMessage)
                            prompt = ""
                            error = null
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Throwable) {
                            error = failure.message ?: "消息发送失败"
                        }
                    }
                }, enabled = prompt.isNotBlank()) { Text("发送") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { scope.launch { connection.session.cancel(id) } }) { Text("取消") }
            }
            history.forEach { Text(it) }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

private fun displayMessage(message: Message): String =
    message.content.joinToString("") { block ->
        when (block) {
            is TextContent -> block.text
            else -> "[内容]"
        }
    }
