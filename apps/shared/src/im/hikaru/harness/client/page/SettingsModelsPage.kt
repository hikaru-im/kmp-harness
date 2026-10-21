package im.hikaru.harness.client.page

import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.Alignment
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import im.hikaru.harness.client.settings.*
import im.hikaru.harness.ui.foundation.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private data class ProviderEditorSession(val configuration: ProviderConfiguration, val revision: Long, val creating: Boolean)
private data class ProviderRemovalSession(val provider: ConfigurableProvider, val revision: Long)

@Composable
public fun SettingsModelsPage(
    state: ModelSettingsState,
    onRefresh: () -> Unit,
    onSave: (ProviderConfiguration, Long) -> Unit,
    onRemove: (String, Long) -> Unit,
    onSetCredential: (String, String) -> Unit,
    onRetryApplication: () -> Unit = {},
    onDiscoverModels: ((String, Long) -> Unit)? = null,
    onDiscoverDraft: (suspend (ProviderConfiguration, String) -> ModelDiscoveryResult)? = null,
    onConnect: ((ProviderConfiguration, String, Long) -> Unit)? = null,
    onModelEnabled: (String, String, Boolean, Long) -> Unit = { _, _, _, _ -> },
    onModelSelected: (String, String, Long) -> Unit = { _, _, _ -> },
) {
    var query by remember { mutableStateOf("") }
    var selectedProviderId by remember { mutableStateOf<String?>(null) }
    var editor by remember { mutableStateOf<ProviderEditorSession?>(null) }
    var viewing by remember { mutableStateOf<String?>(null) }
    var removing by remember { mutableStateOf<ProviderRemovalSession?>(null) }
    var credentialProvider by remember { mutableStateOf<ConfigurableProvider?>(null) }
    var feedback by remember { mutableStateOf<String?>(null) }
    val snapshot = state.snapshot
    LaunchedEffect(state.completedOperation, state.saving) {
        if (!state.saving && state.completedOperation?.startsWith("models:") == true && !state.refreshRequired) {
            feedback = "模型目录已更新；来源与生效结果见下方状态。"
        }
    }
    val currentProvider = snapshot.providers.firstOrNull { it.id == selectedProviderId } ?: snapshot.providers.firstOrNull()
    val visible = listOfNotNull(currentProvider)
    fun edit(provider: ConfigurableProvider) {
        editor = ProviderEditorSession(provider.configuration ?: ProviderConfiguration(provider.id, provider.displayName,
            api = snapshot.protocols.firstOrNull()?.id ?: "openai-chat-completions"), snapshot.revision, false)
    }
    SettingsFormFrame("模型管理", "settings-models-title") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            HarnessText(snapshot.scopeLabel, color = HarnessTokens.Colors.TextMuted, style = MaterialTheme.typography.bodySmall)
            if (snapshot.providers.isNotEmpty()) SettingsFormRow("厂商配置") {
                SettingsFormSelect(currentProvider?.id.orEmpty(), snapshot.providers.map { it.id },
                    { id -> snapshot.providers.first { it.id == id }.displayName }, !state.saving, "models-provider-select") {
                    selectedProviderId = it; query = ""
                }
            }
            HarnessSearchField(query, { query = it }, placeholder = "搜索模型", accessibilityLabel = "搜索模型",
                testTag = "models-search", enabled = !state.loading)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HarnessButton("刷新", onRefresh, enabled = !state.loading && !state.saving, variant = HarnessButtonVariant.Secondary, testTag = "models-refresh")
                HarnessButton("添加厂商配置", {
                    val plugin = snapshot.plugins.firstOrNull()
                    val endpoint = plugin?.endpoints?.firstOrNull()
                    val id = plugin?.let { it.id + "-" + kotlin.random.Random.nextInt(100000, 999999) }.orEmpty()
                    editor = ProviderEditorSession(ProviderConfiguration(id, plugin?.name.orEmpty(),
                        api = endpoint?.api ?: snapshot.protocols.firstOrNull()?.id ?: "openai-chat-completions",
                        baseUrl = endpoint?.baseUrl, credentialName = if (id.isBlank()) null else "PROVIDER_" + id.replace('-', '_'),
                        vendor = plugin?.id ?: "openai", region = endpoint?.region, serviceVariant = endpoint?.serviceVariant), snapshot.revision, true)
                }, enabled = state.canEdit && snapshot.canAddProvider, testTag = "models-new-provider")
            }
        }
        if (state.loading) HarnessText("正在读取模型配置…", color = HarnessTokens.Colors.TextMuted)
        (state.loadError ?: state.saveError)?.let {
            HarnessText(it, Modifier.semantics { testTag = "models-error" }, color = HarnessTokens.Colors.Danger)
        }
        feedback?.let { HarnessText(it, Modifier.semantics { testTag = "models-feedback" }, color = HarnessTokens.Colors.TextMuted) }
        if (state.loaded && !snapshot.available) {
            HarnessText(snapshot.unavailableReason ?: "模型配置服务不可用。", Modifier.semantics { testTag = "models-unavailable" }, color = HarnessTokens.Colors.TextMuted)
        } else if (state.loaded && !state.loading && visible.isEmpty()) {
            HarnessText(if (query.isBlank()) "此 Host 没有模型配置入口。" else "没有匹配的 Provider 或模型。",
                Modifier.semantics { testTag = "models-empty" }, color = HarnessTokens.Colors.TextMuted)
        }
        visible.forEach { provider ->
            Column(Modifier.fillMaxWidth().semantics { testTag = "models-provider-" + provider.id }) {
                SettingsFormSection(provider.displayName) {
                    SettingsFormCard {
                        SettingsFormRow("状态") {
                            HarnessText(when {
                                provider.applying -> "正在应用"
                                provider.applicationError != null && provider.active -> "新配置未生效，沿用上次配置"
                                provider.applicationError != null -> "配置未生效"
                                provider.active -> "已生效"
                                provider.configuration != null -> "待生效"
                                else -> "未配置"
                            }, color = HarnessTokens.Colors.TextMuted, style = MaterialTheme.typography.bodySmall)
                        }
                        SettingsFormRow("凭据", provider.configuration?.credentialName) {
                            HarnessText(if (provider.credentialConfigured) "已配置" else "未配置", color = HarnessTokens.Colors.TextMuted, style = MaterialTheme.typography.bodySmall)
                        }
                        provider.configuration?.baseUrl?.let { HarnessText(it, color = HarnessTokens.Colors.TextMuted, style = MaterialTheme.typography.bodySmall) }
                        provider.catalogError?.let { HarnessText(it, color = HarnessTokens.Colors.Danger, style = MaterialTheme.typography.bodySmall) }
                        provider.catalogSource?.let { HarnessText("目录来源：$it", color = HarnessTokens.Colors.TextMuted, style = MaterialTheme.typography.bodySmall) }
                        provider.catalogUpdatedAt?.let { HarnessText("最近成功更新：" + it, style = MaterialTheme.typography.bodySmall, color = HarnessTokens.Colors.TextMuted) }
                        provider.catalogWarnings.forEach { HarnessText(it, color = HarnessTokens.Colors.TextMuted, style = MaterialTheme.typography.bodySmall) }
                        provider.applicationError?.let { HarnessText(it, color = HarnessTokens.Colors.Danger, style = MaterialTheme.typography.bodySmall) }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                            HarnessButton("编辑", { edit(provider) }, enabled = state.canEdit, variant = HarnessButtonVariant.Secondary, size = HarnessControlSize.Compact, testTag = "models-edit-" + provider.id)
                            HarnessButton("查看模型 (" + provider.models.size + ")", { viewing = provider.id }, enabled = !state.loading && !state.saving,
                                variant = HarnessButtonVariant.Secondary, size = HarnessControlSize.Compact, testTag = "models-view-" + provider.id)
                            if (onDiscoverModels != null) HarnessButton(
                                if (state.discoveringProviderId == provider.id) "获取中…" else if (snapshot.plugins.firstOrNull { it.id == provider.configuration?.vendor }?.supportsDiscovery == false) "载入预置目录" else if (provider.catalogUpdatedAt == null) "获取模型列表" else "更新模型列表",
                                { onDiscoverModels(provider.id, snapshot.revision) },
                                enabled = state.canEdit && provider.configuration != null && provider.credentialConfigured,
                                variant = HarnessButtonVariant.Secondary, size = HarnessControlSize.Compact,
                                testTag = "models-discover-" + provider.id)
                            HarnessButton("配置凭据", { credentialProvider = provider }, enabled = state.canEdit && provider.credentialWritable,
                                variant = HarnessButtonVariant.Secondary, size = HarnessControlSize.Compact, testTag = "models-credential-" + provider.id)
                            if (provider.applicationError != null) HarnessButton("重试应用", onRetryApplication,
                                enabled = state.canEdit && !provider.applying, variant = HarnessButtonVariant.Secondary,
                                size = HarnessControlSize.Compact, testTag = "models-retry-" + provider.id)
                            if (provider.canRemove) HarnessButton("删除配置", { removing = ProviderRemovalSession(provider, snapshot.revision) }, enabled = state.canEdit,
                                variant = HarnessButtonVariant.Ghost, size = HarnessControlSize.Compact, testTag = "models-remove-" + provider.id)
                        }
                    }
                }
            }
        }
        currentProvider?.let { provider ->
            SettingsFormSection("模型列表（" + provider.models.size + "）") {
                HarnessText("开关控制输入框中的可选模型；深色背景表示新会话默认模型。",
                    style = MaterialTheme.typography.bodySmall, color = HarnessTokens.Colors.TextMuted)
                val listed = provider.models.filter { query.isBlank() || it.id.contains(query, true) || it.name.contains(query, true) }
                if (listed.isEmpty()) HarnessText(if (query.isBlank()) "尚无模型，请获取模型列表。" else "没有匹配的模型。",
                    Modifier.semantics { testTag = "models-empty" }, color = HarnessTokens.Colors.TextMuted)
                listed.forEach { model ->
                    val enabled = model.id !in provider.disabledModelIds
                    val selected = enabled && model.id == provider.selectedModelId
                    Row(Modifier.fillMaxWidth()
                        .background(HarnessTokens.Colors.Text.copy(alpha = if (selected) 0.14f else 0.035f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)
                            .selectable(selected, enabled = state.canEdit && enabled && model.selectable,
                                role = Role.RadioButton, onClick = { onModelSelected(provider.id, model.id, snapshot.revision) })
                            .semantics { testTag = "models-select-" + provider.id + "-" + model.id }
                            .padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            HarnessText(model.name)
                            if (model.name != model.id) HarnessText(model.id, style = MaterialTheme.typography.bodySmall, color = HarnessTokens.Colors.TextMuted)
                            if (!model.selectable) HarnessText("不适用于当前聊天", style = MaterialTheme.typography.bodySmall, color = HarnessTokens.Colors.TextMuted)
                            if (model.absentFromCatalog) HarnessText("最新目录未列出", style = MaterialTheme.typography.bodySmall, color = HarnessTokens.Colors.TextMuted)
                        }
                        HarnessSwitch(enabled, { onModelEnabled(provider.id, model.id, it, snapshot.revision) },
                            "启用 " + model.name, enabled = state.canEdit,
                            testTag = "models-enabled-" + provider.id + "-" + model.id)
                    }
                }
            }
        }
        if (snapshot.providers.any { it.active }) HarnessText("配置已生效不代表远端连接已验证。", color = HarnessTokens.Colors.TextMuted, style = MaterialTheme.typography.bodySmall)
    }
    editor?.let { session -> key(session) {
        ProviderEditor(session, state, onRefresh, onSave,
            onDiscoverDraft = onDiscoverDraft, onConnect = onConnect,
            onReload = { editor = ProviderEditorSession(snapshot.providers.firstOrNull { it.id == session.configuration.id }?.configuration ?: session.configuration, snapshot.revision, session.creating) },
            onDismiss = { editor = null }, onSaved = { selectedProviderId = state.confirmedProviderConfiguration?.id ?: session.configuration.id; editor = null; feedback = "Provider 配置已保存；生效结果见下方状态。" })
    } }
    removing?.let { session ->
        ProviderRemovalDialog(session, state, onRefresh, onRemove, { removing = null }, { removing = null; feedback = "Provider 配置已删除" })
    }
    credentialProvider?.let { provider -> key(provider.id) {
        CredentialEditor(provider, state, onRefresh, onSetCredential, { credentialProvider = null }, { credentialProvider = null; feedback = "凭据已保存" })
    } }
    viewing?.let { id ->
        val provider = snapshot.providers.firstOrNull { it.id == id }
        HarnessDialog(true, { viewing = null }, (provider?.displayName ?: id) + " · 模型") {
            Column(Modifier.widthIn(min = 240.dp, max = 520.dp).heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (provider?.catalogError != null) HarnessText(provider.catalogError, color = HarnessTokens.Colors.Danger)
                provider?.applicationError?.let { HarnessText(it, color = HarnessTokens.Colors.Danger) }
                if (provider?.models.isNullOrEmpty()) HarnessText(if (provider?.active == true) "当前没有模型目录。" else "此 Provider 尚未注册路由，暂时无法读取模型目录。", color = HarnessTokens.Colors.TextMuted)
                provider?.models?.forEach { model ->
                    SettingsFormCard {
                        HarnessText(model.name)
                        HarnessText(model.id, color = HarnessTokens.Colors.TextMuted, style = MaterialTheme.typography.bodySmall)
                        model.description?.let { HarnessText(it, color = HarnessTokens.Colors.TextMuted, style = MaterialTheme.typography.bodySmall) }
                        model.contextWindow?.let { HarnessText("上下文容量：" + it, style = MaterialTheme.typography.bodySmall) }
                        model.maxTokens?.let { HarnessText("最大输出：" + it, style = MaterialTheme.typography.bodySmall) }
                        if (model.reasoningEfforts.isNotEmpty()) HarnessText("推理档位：" + model.reasoningEfforts.joinToString(" / "), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            HarnessButton("关闭", { viewing = null }, variant = HarnessButtonVariant.Secondary)
        }
    }
}

private data class ProviderDraft(
    val id: String, val name: String, val api: String, val baseUrl: String, val credential: String,
    val request: String, val connect: String, val socket: String, val context: String, val maxTokens: String, val models: String,
    val vendor: String = "openai", val region: String = "", val workspace: String = "", val serviceVariant: String = "", val apiVersion: String = "",
) {
    fun configuration(): ProviderConfiguration {
        fun number(value: String): Long? = value.trim().takeIf { it.isNotEmpty() }?.let {
            it.toLongOrNull()?.takeIf { parsed -> parsed > 0 } ?: error("超时和模型容量须为正整数或留空")
        }
        fun optional(value: String): String? = value.trim().ifEmpty { null }
        val endpoint = baseUrl.replace("{workspace}", workspace.trim())
        require("{workspace}" !in baseUrl || workspace.isNotBlank()) { "此入口需要 Workspace ID" }
        return ProviderConfiguration(id.trim(), name.trim(), api, optional(endpoint), optional(credential),
            number(request), number(connect), number(socket), models.lineSequence().map(String::trim).filter(String::isNotEmpty).toList().ifEmpty { null },
            number(context), number(maxTokens), vendor, optional(serviceVariant), optional(region), optional(workspace), optional(apiVersion)).also { it.validate() }
    }
    companion object {
        fun from(value: ProviderConfiguration): ProviderDraft = ProviderDraft(value.id, value.displayName, value.api, value.baseUrl.orEmpty(), value.credentialName.orEmpty(),
            value.requestTimeoutMillis?.toString().orEmpty(), value.connectTimeoutMillis?.toString().orEmpty(), value.socketTimeoutMillis?.toString().orEmpty(),
            value.defaultContextWindow?.toString().orEmpty(), value.defaultMaxTokens?.toString().orEmpty(), value.modelIds?.joinToString("\n").orEmpty(),
            value.vendor, value.region.orEmpty(), value.workspace.orEmpty(), value.serviceVariant.orEmpty(), value.apiVersion.orEmpty())
    }
}

@Composable
private fun ProviderEditor(
    session: ProviderEditorSession, state: ModelSettingsState, onRefresh: () -> Unit,
    onSave: (ProviderConfiguration, Long) -> Unit, onReload: () -> Unit, onDismiss: () -> Unit, onSaved: () -> Unit,
    onDiscoverDraft: (suspend (ProviderConfiguration, String) -> ModelDiscoveryResult)? = null,
    onConnect: ((ProviderConfiguration, String, Long) -> Unit)? = null,
    onModelEnabled: (String, String, Boolean, Long) -> Unit = { _, _, _, _ -> },
    onModelSelected: (String, String, Long) -> Unit = { _, _, _ -> },
) {
    var draft by remember { mutableStateOf(ProviderDraft.from(session.configuration)) }
    var error by remember { mutableStateOf<String?>(null) }
    var candidate by remember { mutableStateOf<ProviderConfiguration?>(null) }
    var baseline by remember { mutableStateOf(state.saveRevision) }
    var apiKey by remember { mutableStateOf("") }
    DisposableEffect(Unit) { onDispose { apiKey = "" } }
    var discovering by remember { mutableStateOf(false) }
    var discovered by remember { mutableStateOf<ModelDiscoveryResult?>(null) }
    var advanced by remember { mutableStateOf(!session.creating || state.snapshot.plugins.isEmpty()) }
    val scope = rememberCoroutineScope()
    val plugin = state.snapshot.plugins.firstOrNull { it.id == draft.vendor }
    val endpoints = plugin?.endpoints.orEmpty()
    val selectedEndpoint = endpoints.firstOrNull { it.region.orEmpty() == draft.region && it.serviceVariant.orEmpty() == draft.serviceVariant && it.api == draft.api }
    fun invalidateDiscovery(next: ProviderDraft = draft) {
        draft = next
        discovered = null
    }
    LaunchedEffect(state.saveRevision, state.saving, state.refreshRequired, candidate) {
        if (candidate != null && !state.saving && !state.refreshRequired && state.saveRevision > baseline &&
            state.completedOperation == "provider:" + candidate?.id && state.confirmedProviderConfiguration == candidate) onSaved()
    }
    val enabled = state.canEdit && !discovering
    HarnessDialog(true, { if (!state.saving) onDismiss() }, if (session.creating) "添加厂商配置" else "编辑厂商配置") {
        Column(Modifier.widthIn(min = 240.dp, max = 520.dp).fillMaxWidth().heightIn(max = 430.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.snapshot.plugins.isNotEmpty()) SettingsFormRow("厂商插件") {
                SettingsFormSelect(draft.vendor, state.snapshot.plugins.map { it.id }, { id -> state.snapshot.plugins.firstOrNull { it.id == id }?.name ?: id },
                    enabled && session.creating, "provider-editor-vendor") { vendor ->
                    val selected = state.snapshot.plugins.first { it.id == vendor }
                    val endpoint = selected.endpoints.firstOrNull()
                    draft = draft.copy(vendor = vendor, name = selected.name, api = endpoint?.api ?: selected.protocols.first(),
                        baseUrl = endpoint?.baseUrl.orEmpty(), region = endpoint?.region.orEmpty(),
                        serviceVariant = endpoint?.serviceVariant.orEmpty(), workspace = "", models = "")
                    apiKey = ""; discovered = null
                }
            }
            if (endpoints.size > 1) SettingsFormRow("服务入口") {
                SettingsFormSelect(selectedEndpoint?.id ?: endpoints.first().id, endpoints.map { it.id }, { id -> endpoints.first { it.id == id }.label },
                    enabled, "provider-editor-endpoint") { id ->
                    val endpoint = endpoints.first { it.id == id }
                    draft = draft.copy(baseUrl = endpoint.baseUrl, api = endpoint.api, region = endpoint.region.orEmpty(),
                        serviceVariant = endpoint.serviceVariant.orEmpty(), workspace = "", models = "")
                    apiKey = ""; discovered = null
                }
            }
            if (plugin?.connectionFields?.any { it.id == "workspace" } == true || selectedEndpoint?.requiresWorkspace == true) {
                DraftField("Workspace ID" + if (selectedEndpoint?.requiresWorkspace == true) "（必填）" else "（按凭据需要填写）",
                    draft.workspace, { invalidateDiscovery(draft.copy(workspace = it)) }, enabled, "provider-editor-workspace")
            }
            DraftField("显示名称", draft.name, { draft = draft.copy(name = it) }, enabled, "provider-editor-name")
            if (session.creating && plugin != null && onDiscoverDraft != null && onConnect != null) {
                DraftField("API key", apiKey, { apiKey = it; invalidateDiscovery() }, enabled, "provider-editor-api-key", password = true)
                HarnessButton(if (discovering) "获取中…" else if (plugin?.supportsDiscovery == false) "载入预置模型" else "获取模型列表", {
                    try {
                        val requested = draft.configuration()
                        val requestedDraft = draft
                        val key = apiKey
                        require(key.isNotBlank()) { "请填写 API key" }
                        discovering = true; error = null
                        scope.launch {
                            try {
                                val result = onDiscoverDraft(requested, key)
                                if (draft == requestedDraft && apiKey == key) discovered = result
                            } catch (cancelled: CancellationException) { throw cancelled }
                            catch (failure: Exception) { error = failure.message ?: "无法获取模型目录" }
                            finally { discovering = false }
                        }
                    } catch (failure: Exception) { error = failure.message ?: "配置无效" }
                }, enabled = enabled && apiKey.isNotBlank(), testTag = "provider-editor-discover")
                discovered?.let { result ->
                    HarnessText("目录来源：" + result.source, color = HarnessTokens.Colors.TextMuted, style = MaterialTheme.typography.bodySmall)
                    result.warnings.forEach { HarnessText(it, color = HarnessTokens.Colors.TextMuted, style = MaterialTheme.typography.bodySmall) }
                    if (result.models.isEmpty()) HarnessText("此目录没有返回模型。", color = HarnessTokens.Colors.TextMuted)
                    else {
                        HarnessText(if (result.complete || result.source.startsWith("bundled:")) "已获取 " + result.models.size + " 个模型，保存后默认开启。" else "获取未完成，请重试；本次结果不会替换已保存目录。", style = MaterialTheme.typography.bodySmall)
                        Column(Modifier.heightIn(max = 140.dp).verticalScroll(rememberScrollState())) {
                            result.models.forEach { HarnessText(it.name, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
                if (plugin?.inferenceSupported == false) HarnessText("已支持模型目录；此插件的推理适配尚未接入。", color = HarnessTokens.Colors.TextMuted)
            }
            HarnessButton(if (advanced) "收起高级配置" else "高级配置", { advanced = !advanced }, enabled = enabled,
                variant = HarnessButtonVariant.Ghost, testTag = "provider-editor-advanced")
            if (advanced) {
            DraftField("Provider ID", draft.id, { draft = draft.copy(id = it) }, enabled && session.creating, "provider-editor-id")
            SettingsFormRow("API 协议") {
                SettingsFormSelect(draft.api, plugin?.protocols ?: state.snapshot.protocols.map { it.id }, { id -> state.snapshot.protocols.firstOrNull { it.id == id }?.label ?: id },
                    enabled, "provider-editor-api") { draft = draft.copy(api = it, models = ""); discovered = null }
            }
            DraftField("服务地址（留空使用默认值）", draft.baseUrl, { invalidateDiscovery(draft.copy(baseUrl = it)) }, enabled, "provider-editor-base-url")
            DraftField("API 版本（按服务需要填写）", draft.apiVersion, { invalidateDiscovery(draft.copy(apiVersion = it)) }, enabled, "provider-editor-api-version")
            DraftField("凭据引用名称（必填）", draft.credential, { invalidateDiscovery(draft.copy(credential = it)) }, enabled, "provider-editor-credential")
            DraftField("请求超时 / 毫秒", draft.request, { draft = draft.copy(request = it) }, enabled, "provider-editor-request-timeout")
            DraftField("连接超时 / 毫秒", draft.connect, { draft = draft.copy(connect = it) }, enabled, "provider-editor-connect-timeout")
            DraftField("Socket 超时 / 毫秒", draft.socket, { draft = draft.copy(socket = it) }, enabled, "provider-editor-socket-timeout")
            DraftField("默认上下文容量", draft.context, { draft = draft.copy(context = it) }, enabled, "provider-editor-context")
            DraftField("默认最大输出", draft.maxTokens, { draft = draft.copy(maxTokens = it) }, enabled, "provider-editor-max-tokens")
            DraftField("手工模型 ID（每行一个，与发现目录合并）", draft.models, { draft = draft.copy(models = it) }, enabled, "provider-editor-models", multiline = true)
            if (draft.baseUrl.isNotBlank() && draft.models.isBlank()) HarnessText("保存后可获取模型列表；手工模型会在更新时保留。", color = HarnessTokens.Colors.TextMuted)
            }
        }
        (error ?: state.saveError ?: state.loadError)?.let { HarnessText(it, Modifier.semantics { testTag = "provider-editor-error" }, color = HarnessTokens.Colors.Danger) }
        if (session.revision != state.snapshot.revision || state.refreshRequired) {
            HarnessText("配置可能已变化。刷新并检查后可重新载入表单。", color = HarnessTokens.Colors.TextMuted, style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HarnessButton("刷新", onRefresh, enabled = !state.loading && !state.saving, testTag = "provider-editor-refresh", variant = HarnessButtonVariant.Secondary)
                HarnessButton("重新载入表单", onReload, enabled = state.canEdit, testTag = "provider-editor-reload", variant = HarnessButtonVariant.Secondary)
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HarnessButton("取消", onDismiss, enabled = !state.saving, variant = HarnessButtonVariant.Secondary)
            HarnessButton("保存", {
                try {
                    val next = draft.configuration()
                    require(!session.creating || state.snapshot.providers.none { it.id == next.id && it.configuration != null }) { "此 Provider ID 已有配置" }
                    baseline = state.saveRevision; candidate = next; error = null
                    if (session.creating && onConnect != null && apiKey.isNotBlank()) {
                        onConnect(next, apiKey, session.revision)
                    } else onSave(next, session.revision)
                } catch (failure: Exception) { error = failure.message ?: "配置无效" }
            }, enabled = enabled, testTag = "provider-editor-save")
        }
    }
}

@Composable
private fun ProviderRemovalDialog(
    session: ProviderRemovalSession, state: ModelSettingsState, onRefresh: () -> Unit, onRemove: (String, Long) -> Unit,
    onDismiss: () -> Unit, onRemoved: () -> Unit,
) {
    var submitted by remember(session) { mutableStateOf(false) }
    var baseline by remember(session) { mutableStateOf(state.saveRevision) }
    LaunchedEffect(state.saveRevision, state.saving, state.refreshRequired, submitted) {
        if (submitted && !state.saving && !state.refreshRequired && state.saveRevision > baseline && state.completedOperation == "remove:" + session.provider.id) onRemoved()
    }
    HarnessDialog(true, { if (!state.saving) onDismiss() }, "删除 Provider 配置") {
        HarnessText("删除 " + session.provider.displayName + " 的用户配置。已保存的凭据会保留。")
        (state.saveError ?: state.loadError)?.let { HarnessText(it, color = HarnessTokens.Colors.Danger) }
        val stale = session.revision != state.snapshot.revision
        if (stale) HarnessText("配置已变化，请取消并重新打开确认窗口，检查最新配置后再删除。", color = HarnessTokens.Colors.TextMuted)
        if (state.refreshRequired) HarnessButton("刷新", onRefresh, enabled = !state.loading && !state.saving, variant = HarnessButtonVariant.Secondary, testTag = "provider-delete-refresh")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HarnessButton("取消", onDismiss, enabled = !state.saving, variant = HarnessButtonVariant.Secondary)
            HarnessButton("删除配置", { baseline = state.saveRevision; submitted = true; onRemove(session.provider.id, session.revision) },
                enabled = state.canEdit && !stale, variant = HarnessButtonVariant.Destructive, testTag = "provider-delete-confirm")
        }
    }
}

@Composable
private fun CredentialEditor(
    provider: ConfigurableProvider, state: ModelSettingsState, onRefresh: () -> Unit, onSetCredential: (String, String) -> Unit,
    onDismiss: () -> Unit, onSaved: () -> Unit,
) {
    val name = provider.configuration?.credentialName.orEmpty()
    var value by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var submittedName by remember { mutableStateOf<String?>(null) }
    var baseline by remember { mutableStateOf(state.saveRevision) }
    DisposableEffect(Unit) { onDispose { value = "" } }
    LaunchedEffect(state.saveRevision, state.saving, state.refreshRequired, submittedName) {
        if (submittedName != null && !state.saving && !state.refreshRequired && state.saveRevision > baseline && state.completedOperation == "credential:" + submittedName) {
            value = ""; onSaved()
        }
    }
    HarnessDialog(true, { if (!state.saving) { value = ""; onDismiss() } }, "配置凭据") {
        HarnessText(provider.displayName + " · 保存后通过凭据引用使用。", color = HarnessTokens.Colors.TextMuted, style = MaterialTheme.typography.bodySmall)
        HarnessText("凭据引用：$name（如需更换，请编辑 Provider）")
        DraftField("API key", value, { value = it }, state.canEdit && provider.credentialWritable, "credential-editor-value", password = true)
        (error ?: state.saveError)?.let { HarnessText(it, Modifier.semantics { testTag = "credential-editor-error" }, color = HarnessTokens.Colors.Danger) }
        if (state.refreshRequired) HarnessButton("刷新", onRefresh, enabled = !state.loading && !state.saving, variant = HarnessButtonVariant.Secondary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HarnessButton("取消", { value = ""; onDismiss() }, enabled = !state.saving, variant = HarnessButtonVariant.Secondary)
            HarnessButton("保存凭据", {
                if (!validCredentialName(name.trim()) || value.isBlank()) { error = "请输入有效凭据名称和非空 API key" }
                else { baseline = state.saveRevision; submittedName = name.trim(); error = null; onSetCredential(name.trim(), value) }
            }, enabled = state.canEdit && provider.credentialWritable, testTag = "credential-editor-save")
        }
    }
}

@Composable
private fun DraftField(
    label: String, value: String, onChange: (String) -> Unit, enabled: Boolean, tag: String,
    multiline: Boolean = false, password: Boolean = false,
) {
    Column(Modifier.widthIn(min = 240.dp, max = 520.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        HarnessText(label, style = MaterialTheme.typography.bodySmall)
        BasicTextField(value, onChange, enabled = enabled, singleLine = !multiline,
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = HarnessTokens.Colors.Text),
            cursorBrush = SolidColor(HarnessTokens.Colors.Text),
            modifier = Modifier.fillMaxWidth().heightIn(min = if (multiline) 84.dp else 36.dp, max = if (multiline) 160.dp else 80.dp)
                .border(1.dp, HarnessTokens.Colors.BorderStrong, RoundedCornerShape(8.dp)).padding(10.dp).semantics { testTag = tag; contentDescription = label })
    }
}
