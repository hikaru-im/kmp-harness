package im.hikaru.harness.client.page

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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

/** How a new provider starts: an installed vendor preset, or a hand-declared connection. */
private enum class ProviderAddMode { Preset, Custom }

private data class ProviderEditorSession(
    val configuration: ProviderConfiguration,
    val revision: Long,
    val creating: Boolean,
    val mode: ProviderAddMode = ProviderAddMode.Preset,
)

private data class ProviderEditorState(
    val session: ProviderEditorSession,
    val draft: MutableState<ProviderDraft>,
    val apiKey: MutableState<String>,
    val discovering: MutableState<Boolean> = mutableStateOf(false),
    val discovered: MutableState<ModelDiscoveryResult?> = mutableStateOf(null),
)
private data class ProviderRemovalSession(val provider: ConfigurableProvider, val revision: Long)

private fun providerStatus(provider: ConfigurableProvider): String = when {
    provider.applying -> "正在应用"
    provider.applicationError != null && provider.active -> "新配置未生效，沿用上次配置"
    provider.applicationError != null -> "配置未生效"
    provider.active -> "已生效"
    provider.configuration != null -> "待生效"
    else -> "未配置"
}

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
    var editing by remember { mutableStateOf<ProviderEditorSession?>(null) }
    var addEditorStates by remember { mutableStateOf<Map<ProviderAddMode, ProviderEditorState>>(emptyMap()) }
    var removing by remember { mutableStateOf<ProviderRemovalSession?>(null) }
    var feedback by remember { mutableStateOf<String?>(null) }
    val snapshot = state.snapshot
    LaunchedEffect(state.completedOperation, state.saving) {
        if (!state.saving && state.completedOperation?.startsWith("models:") == true && !state.refreshRequired) {
            feedback = "模型目录已更新；来源与生效结果见下方状态。"
        }
    }
    fun closeEditor() {
        editing = null
        addEditorStates = emptyMap()
        query = ""
    }
    fun edit(provider: ConfigurableProvider) {
        addEditorStates = emptyMap()
        editing = ProviderEditorSession(
            provider.configuration ?: ProviderConfiguration(
                provider.id,
                provider.displayName,
                api = snapshot.protocols.firstOrNull()?.id ?: "openai-chat-completions",
            ),
            snapshot.revision,
            false,
        )
        query = ""
    }
    fun create(mode: ProviderAddMode, reload: Boolean = false) {
        if (editing?.creating == true && !reload) {
            addEditorStates[mode]?.let {
                editing = it.session
                query = ""
                return
            }
        } else if (editing?.creating != true) {
            addEditorStates = emptyMap()
        }
        val plugin = if (mode == ProviderAddMode.Preset) {
            snapshot.plugins.firstOrNull()
        } else {
            snapshot.plugins.firstOrNull { it.id == "openai" } ?: snapshot.plugins.firstOrNull()
        }
        val endpoint = plugin?.endpoints?.firstOrNull()
        val id = if (mode == ProviderAddMode.Preset) {
            plugin?.let { it.id + "-" + kotlin.random.Random.nextInt(100000, 999999) }.orEmpty()
        } else {
            "custom-" + kotlin.random.Random.nextInt(100000, 999999)
        }
        val configuration = if (mode == ProviderAddMode.Preset) {
            ProviderConfiguration(
                id, plugin?.name.orEmpty(),
                api = endpoint?.api ?: snapshot.protocols.firstOrNull()?.id ?: "openai-chat-completions",
                baseUrl = endpoint?.baseUrl,
                credentialName = if (id.isBlank()) null else "PROVIDER_" + id.replace('-', '_'),
                vendor = plugin?.id ?: "openai",
                region = endpoint?.region,
                serviceVariant = endpoint?.serviceVariant,
            )
        } else {
            ProviderConfiguration(
                id, "",
                api = endpoint?.api ?: snapshot.protocols.firstOrNull()?.id ?: "openai-chat-completions",
                credentialName = "PROVIDER_" + id.replace('-', '_'),
                vendor = plugin?.id ?: "openai",
            )
        }
        val session = ProviderEditorSession(configuration, snapshot.revision, true, mode)
        addEditorStates = addEditorStates + (mode to ProviderEditorState(
            session, mutableStateOf(ProviderDraft.from(configuration)), mutableStateOf(""),
        ))
        editing = session
        query = ""
    }

    SettingsFormFrame("模型管理", "settings-models-title") {
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            HarnessText(snapshot.scopeLabel, color = HarnessTokens.Colors.TextMuted, style = MaterialTheme.typography.bodySmall)
            if (state.loading) HarnessText("正在读取模型配置…", color = HarnessTokens.Colors.TextMuted)
            (state.loadError ?: state.saveError)?.let {
                HarnessText(it, Modifier.semantics { testTag = "models-error" }, color = HarnessTokens.Colors.Danger)
            }
            feedback?.let { HarnessText(it, Modifier.semantics { testTag = "models-feedback" }, color = HarnessTokens.Colors.TextMuted) }
            if (state.loaded && !snapshot.available) {
                HarnessText(snapshot.unavailableReason ?: "模型配置服务不可用。", Modifier.semantics { testTag = "models-unavailable" }, color = HarnessTokens.Colors.TextMuted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HarnessButton("添加厂商", { create(ProviderAddMode.Preset) },
                        enabled = false, variant = HarnessButtonVariant.Secondary, testTag = "models-new-provider")
                    HarnessButton("刷新", onRefresh, enabled = !state.loading && !state.saving,
                        variant = HarnessButtonVariant.Ghost, testTag = "models-refresh")
                }
            } else if (state.loaded && !state.loading) {
                if (snapshot.providers.isEmpty() && editing?.creating != true) {
                    HarnessText("此 Host 没有模型配置入口。", Modifier.semantics { testTag = "models-empty" }, color = HarnessTokens.Colors.TextMuted)
                }
                if (snapshot.providers.isNotEmpty()) {
                    SettingsFormSection("厂商") {
                        SettingsFormCard {
                            snapshot.providers.forEachIndexed { index, provider ->
                                key(provider.id) {
                                    if (index > 0) {
                                        Box(Modifier.fillMaxWidth().height(1.dp).background(HarnessTokens.Colors.BorderSubtle))
                                    }
                                    val expanded = editing?.creating == false && editing?.configuration?.id == provider.id
                                    ProviderRow(provider, expanded, state.canEdit,
                                        onToggle = {
                                            if (expanded) closeEditor() else edit(provider)
                                        },
                                        onRemove = { removing = ProviderRemovalSession(provider, snapshot.revision) })
                                    if (expanded) {
                                        key(editing) {
                                            ProviderEditor(
                                                session = editing!!, state = state, provider = provider,
                                                query = query, onQueryChange = { query = it },
                                                onRefresh = onRefresh, onSave = onSave, onReload = {
                                                    editing = ProviderEditorSession(
                                                        snapshot.providers.firstOrNull { it.id == provider.id }?.configuration
                                                            ?: editing!!.configuration,
                                                        snapshot.revision,
                                                        false,
                                                    )
                                                },
                                                onDismiss = ::closeEditor,
                                                onSaved = {
                                                    closeEditor()
                                                    feedback = "Provider 配置已保存；生效结果见厂商状态。"
                                                },
                                                onDiscoverDraft = onDiscoverDraft,
                                                onConnect = onConnect,
                                                onDiscoverModels = onDiscoverModels,
                                                onModelEnabled = onModelEnabled,
                                                onModelSelected = onModelSelected,
                                                onRetryApplication = onRetryApplication,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (editing?.creating == true) {
                    SettingsFormSection("添加厂商") {
                        SettingsFormCard {
                            AddModeSwitch(editing!!.mode,
                                enabled = state.canEdit && addEditorStates.values.none { it.discovering.value },
                            ) { mode -> create(mode) }
                            key(editing) {
                                ProviderEditor(
                                    session = editing!!, state = state, provider = null,
                                    editorState = addEditorStates[editing!!.mode],
                                    query = query, onQueryChange = { query = it },
                                    onRefresh = onRefresh, onSave = onSave, onReload = { create(editing!!.mode, reload = true) },
                                    onDismiss = ::closeEditor,
                                    onSaved = {
                                        closeEditor()
                                        feedback = "Provider 配置已保存；生效结果见厂商状态。"
                                    },
                                    onDiscoverDraft = onDiscoverDraft,
                                    onConnect = onConnect,
                                    onDiscoverModels = onDiscoverModels,
                                    onModelEnabled = onModelEnabled,
                                    onModelSelected = onModelSelected,
                                    onRetryApplication = onRetryApplication,
                                )
                            }
                        }
                    }
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        HarnessButton("添加厂商", { create(ProviderAddMode.Preset) },
                            enabled = state.canEdit && snapshot.canAddProvider,
                            variant = HarnessButtonVariant.Secondary, testTag = "models-new-provider")
                        HarnessButton("刷新", onRefresh, enabled = !state.loading && !state.saving,
                            variant = HarnessButtonVariant.Ghost, testTag = "models-refresh")
                    }
                }
            }
            if (snapshot.providers.any { it.active }) {
                HarnessText("配置已生效不代表远端连接已验证。", color = HarnessTokens.Colors.TextMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    removing?.let { session ->
        ProviderRemovalDialog(session, state, onRefresh, onRemove, { removing = null }, { removing = null; feedback = "Provider 配置已删除" })
    }
}

/** One provider row: identity, live status and the two actions the page offers. */
@Composable
private fun ProviderRow(
    provider: ConfigurableProvider,
    expanded: Boolean,
    canEdit: Boolean,
    onToggle: () -> Unit,
    onRemove: () -> Unit,
) {
    val dotColor = when {
        provider.applying -> HarnessTokens.Colors.Warning
        provider.applicationError != null -> HarnessTokens.Colors.Warning
        provider.active -> HarnessTokens.Colors.Success
        provider.configuration != null && provider.credentialConfigured -> HarnessTokens.Colors.Warning
        else -> HarnessTokens.Colors.TextDisabled
    }
    val summary = buildString {
        append(if (provider.credentialConfigured) "凭据已配置" else "凭据未配置")
        append(" · ")
        append(if (provider.models.isEmpty()) "无模型" else provider.models.size.toString() + " 个模型")
        provider.selectedModelId?.let { append(" · 默认 " + it) }
    }
    Row(
        Modifier.fillMaxWidth().semantics { testTag = "models-provider-" + provider.id }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).background(dotColor, CircleShape))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            HarnessText(provider.displayName, style = MaterialTheme.typography.bodyLarge)
            HarnessText(providerStatus(provider),
                Modifier.semantics { testTag = "models-status-" + provider.id },
                color = if (provider.applicationError != null) HarnessTokens.Colors.Danger else HarnessTokens.Colors.TextMuted,
                style = MaterialTheme.typography.bodySmall)
            HarnessText(summary, color = HarnessTokens.Colors.TextMuted, style = MaterialTheme.typography.bodySmall)
            provider.applicationError?.let {
                HarnessText(it, color = HarnessTokens.Colors.Danger, style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.width(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            HarnessButton(if (expanded) "收起" else "编辑", onToggle, enabled = canEdit,
                variant = HarnessButtonVariant.Ghost, size = HarnessControlSize.Compact,
                testTag = "models-edit-" + provider.id)
            if (provider.canRemove) HarnessButton("删除", onRemove, enabled = canEdit,
                variant = HarnessButtonVariant.Ghost, size = HarnessControlSize.Compact,
                testTag = "models-remove-" + provider.id)
        }
    }
}

/** DSH's add card switches between adopting a provider preset and declaring a custom route. */
@Composable
private fun AddModeSwitch(mode: ProviderAddMode, enabled: Boolean, onMode: (ProviderAddMode) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        HarnessButton("厂商预设", { if (mode != ProviderAddMode.Preset) onMode(ProviderAddMode.Preset) }, enabled = enabled,
            variant = if (mode == ProviderAddMode.Preset) HarnessButtonVariant.Secondary else HarnessButtonVariant.Ghost,
            size = HarnessControlSize.Compact, testTag = "models-add-mode-preset")
        HarnessButton("自定义连接", { if (mode != ProviderAddMode.Custom) onMode(ProviderAddMode.Custom) }, enabled = enabled,
            variant = if (mode == ProviderAddMode.Custom) HarnessButtonVariant.Secondary else HarnessButtonVariant.Ghost,
            size = HarnessControlSize.Compact, testTag = "models-add-mode-custom")
    }
}

/** The provider's model catalog, shown inside its editor card. */
@Composable
private fun ProviderModelList(
    provider: ConfigurableProvider,
    state: ModelSettingsState,
    query: String,
    onQueryChange: (String) -> Unit,
    onDiscoverModels: ((String, Long) -> Unit)?,
    onModelEnabled: (String, String, Boolean, Long) -> Unit,
    onModelSelected: (String, String, Long) -> Unit,
) {
    val snapshot = state.snapshot
    val plugin = snapshot.plugins.firstOrNull { it.id == provider.configuration?.vendor }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HarnessText("模型（" + provider.models.size + "）", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.weight(1f))
            if (onDiscoverModels != null) HarnessButton(
                when {
                    state.discoveringProviderId == provider.id -> "获取中…"
                    plugin?.supportsDiscovery == false -> "载入预置目录"
                    provider.catalogUpdatedAt == null -> "获取模型列表"
                    else -> "更新模型列表"
                },
                { onDiscoverModels(provider.id, snapshot.revision) },
                enabled = state.canEdit && provider.configuration != null && provider.credentialConfigured,
                variant = HarnessButtonVariant.Secondary, size = HarnessControlSize.Compact,
                testTag = "models-discover-" + provider.id,
            )
        }
        HarnessText("开关控制输入框中的可选模型；选中项为新会话默认模型。",
            style = MaterialTheme.typography.bodySmall, color = HarnessTokens.Colors.TextMuted)
        HarnessSearchField(query, onQueryChange, placeholder = "搜索模型", accessibilityLabel = "搜索模型",
            testTag = "models-search", enabled = !state.loading)
        provider.catalogSource?.let { HarnessText("目录来源：$it", color = HarnessTokens.Colors.TextMuted, style = MaterialTheme.typography.bodySmall) }
        provider.catalogUpdatedAt?.let { HarnessText("最近成功更新：$it", style = MaterialTheme.typography.bodySmall, color = HarnessTokens.Colors.TextMuted) }
        provider.catalogWarnings.forEach { HarnessText(it, color = HarnessTokens.Colors.TextMuted, style = MaterialTheme.typography.bodySmall) }
        provider.catalogError?.let { HarnessText(it, color = HarnessTokens.Colors.Danger, style = MaterialTheme.typography.bodySmall) }
        val listed = provider.models.filter { query.isBlank() || it.id.contains(query, true) || it.name.contains(query, true) }
        if (listed.isEmpty()) {
            HarnessText(if (query.isBlank()) "尚无模型，请获取模型列表。" else "没有匹配的模型。",
                Modifier.semantics { testTag = "models-empty" }, color = HarnessTokens.Colors.TextMuted)
        }
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
                    val details = buildList<String> {
                        model.description?.let { add(it) }
                        model.contextWindow?.let { add("上下文容量：$it") }
                        model.maxTokens?.let { add("最大输出：$it") }
                        if (model.reasoningEfforts.isNotEmpty()) add("推理档位：" + model.reasoningEfforts.joinToString(" / "))
                    }
                    if (details.isNotEmpty()) HarnessText(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = HarnessTokens.Colors.TextMuted)
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

private data class ProviderDraft(
    val id: String, val name: String, val api: String, val baseUrl: String, val credential: String,
    val request: String, val connect: String, val socket: String, val context: String, val maxTokens: String,
    val models: String, val vendor: String, val region: String, val workspace: String,
    val serviceVariant: String, val apiVersion: String,
) {
    fun configuration(): ProviderConfiguration = ProviderConfiguration(
        id = id.trim(), displayName = name.trim(), api = api, baseUrl = baseUrl.trim().ifBlank { null },
        credentialName = credential.trim().ifBlank { null },
        requestTimeoutMillis = request.trim().toLongOrNull(), connectTimeoutMillis = connect.trim().toLongOrNull(),
        socketTimeoutMillis = socket.trim().toLongOrNull(), defaultContextWindow = context.trim().toLongOrNull(),
        defaultMaxTokens = maxTokens.trim().toLongOrNull(),
        modelIds = models.lines().map(String::trim).filter(String::isNotEmpty).takeIf { it.isNotEmpty() },
        vendor = vendor, region = region.trim().ifBlank { null }, workspace = workspace.trim().ifBlank { null },
        serviceVariant = serviceVariant.trim().ifBlank { null }, apiVersion = apiVersion.trim().ifBlank { null },
    )

    companion object {
        fun from(value: ProviderConfiguration): ProviderDraft = ProviderDraft(
            value.id, value.displayName, value.api, value.baseUrl.orEmpty(), value.credentialName.orEmpty(),
            value.requestTimeoutMillis?.toString().orEmpty(), value.connectTimeoutMillis?.toString().orEmpty(), value.socketTimeoutMillis?.toString().orEmpty(),
            value.defaultContextWindow?.toString().orEmpty(), value.defaultMaxTokens?.toString().orEmpty(), value.modelIds?.joinToString("\n").orEmpty(),
            value.vendor, value.region.orEmpty(), value.workspace.orEmpty(), value.serviceVariant.orEmpty(), value.apiVersion.orEmpty())
    }
}

@Composable
private fun ProviderEditor(
    session: ProviderEditorSession, state: ModelSettingsState, provider: ConfigurableProvider?,
    query: String, onQueryChange: (String) -> Unit,
    onRefresh: () -> Unit, onSave: (ProviderConfiguration, Long) -> Unit, onReload: () -> Unit,
    onDismiss: () -> Unit, onSaved: () -> Unit,
    onDiscoverDraft: (suspend (ProviderConfiguration, String) -> ModelDiscoveryResult)? = null,
    onConnect: ((ProviderConfiguration, String, Long) -> Unit)? = null,
    onDiscoverModels: ((String, Long) -> Unit)? = null,
    onModelEnabled: (String, String, Boolean, Long) -> Unit = { _, _, _, _ -> },
    onModelSelected: (String, String, Long) -> Unit = { _, _, _ -> },
    onRetryApplication: () -> Unit = {},
    editorState: ProviderEditorState? = null,
) {
    val localDraft = remember { mutableStateOf(ProviderDraft.from(session.configuration)) }
    var draft by (editorState?.draft ?: localDraft)
    var error by remember { mutableStateOf<String?>(null) }
    var candidate by remember { mutableStateOf<ProviderConfiguration?>(null) }
    var baseline by remember { mutableStateOf(state.saveRevision) }
    val localApiKey = remember { mutableStateOf("") }
    var apiKey by (editorState?.apiKey ?: localApiKey)
    DisposableEffect(Unit) { onDispose { localApiKey.value = "" } }
    val localDiscovering = remember { mutableStateOf(false) }
    var discovering by (editorState?.discovering ?: localDiscovering)
    val localDiscovered = remember { mutableStateOf<ModelDiscoveryResult?>(null) }
    var discovered by (editorState?.discovered ?: localDiscovered)
    var advanced by remember { mutableStateOf(!session.creating || session.mode == ProviderAddMode.Custom || state.snapshot.plugins.isEmpty()) }
    val scope = rememberCoroutineScope()
    val plugin = state.snapshot.plugins.firstOrNull { it.id == draft.vendor }
    val endpoints = plugin?.endpoints.orEmpty()
    val selectedEndpoint = endpoints.firstOrNull {
        it.baseUrl == draft.baseUrl && it.region == draft.region && it.serviceVariant == draft.serviceVariant
    }
    fun invalidateDiscovery(value: ProviderDraft = draft) {
        if (draft != value) discovered = null
        draft = value
    }
    LaunchedEffect(state.saveRevision, state.saving, state.refreshRequired, candidate) {
        if (candidate != null && !state.saving && !state.refreshRequired && state.saveRevision > baseline &&
            state.completedOperation == "provider:" + candidate?.id && state.confirmedProviderConfiguration == candidate) onSaved()
    }
    val enabled = state.canEdit && !discovering
    Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        (error ?: state.saveError ?: state.loadError)?.let {
            HarnessText(it, Modifier.semantics { testTag = "provider-editor-error" }, color = HarnessTokens.Colors.Danger)
        }
        if (state.refreshRequired) HarnessButton("刷新", onRefresh, enabled = !state.loading && !state.saving,
            variant = HarnessButtonVariant.Secondary, testTag = "provider-editor-refresh")
        if (session.creating && state.snapshot.plugins.isNotEmpty()) SettingsFormRow("厂商插件") {
            SettingsFormSelect(draft.vendor, state.snapshot.plugins.map { it.id }, { id -> state.snapshot.plugins.firstOrNull { it.id == id }?.name ?: id },
                enabled, "provider-editor-vendor") { vendor ->
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
        val credentialWritable = session.creating || provider?.credentialWritable == true
        if (credentialWritable) {
            DraftField(
                "API key" + when {
                    session.creating -> ""
                    provider?.credentialConfigured == true -> "（已配置，留空保持不变）"
                    else -> "（未配置）"
                },
                apiKey, { apiKey = it; if (session.creating) invalidateDiscovery() }, enabled,
                "provider-editor-api-key", password = true,
            )
        }
        if (session.creating && plugin != null && onDiscoverDraft != null && onConnect != null) {
            HarnessButton(if (discovering) "获取中…" else if (plugin.supportsDiscovery == false) "载入预置模型" else "获取模型列表", {
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
            if (plugin.inferenceSupported == false) HarnessText("已支持模型目录；此插件的推理适配尚未接入。", color = HarnessTokens.Colors.TextMuted)
        }
        HarnessButton(if (advanced) "收起自定义设置" else "自定义设置", { advanced = !advanced }, enabled = enabled,
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
            DraftField("模型 ID（每行一个；留空使用目录）", draft.models, { draft = draft.copy(models = it) }, enabled, "provider-editor-models")
            if (plugin?.connectionFields?.any { it.id == "region" } == true) {
                DraftField("Region", draft.region, { draft = draft.copy(region = it) }, enabled, "provider-editor-region")
            }
            if (plugin?.connectionFields?.any { it.id == "serviceVariant" } == true) {
                DraftField("服务类型", draft.serviceVariant, { draft = draft.copy(serviceVariant = it) }, enabled, "provider-editor-service-variant")
            }
        }
        if (provider != null) {
            ProviderModelList(provider, state, query, onQueryChange, onDiscoverModels, onModelEnabled, onModelSelected)
            if (provider.applicationError != null) {
                HarnessButton("重试应用", onRetryApplication, enabled = state.canEdit && !provider.applying,
                    variant = HarnessButtonVariant.Secondary, size = HarnessControlSize.Compact,
                    testTag = "models-retry-" + provider.id)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HarnessButton(if (session.creating) "保存" else "应用", {
                try {
                    val next = draft.configuration()
                    next.validate()
                    baseline = state.saveRevision
                    candidate = next
                    error = null
                    if (onConnect != null && apiKey.isNotBlank()) onConnect(next, apiKey, session.revision)
                    else onSave(next, session.revision)
                } catch (failure: Exception) { error = failure.message ?: "配置无效" }
            }, enabled = enabled, testTag = "provider-editor-save")
            HarnessButton("取消", onDismiss, enabled = !state.saving, variant = HarnessButtonVariant.Secondary,
                testTag = "provider-editor-cancel")
            HarnessButton("重新加载", onReload, enabled = enabled, variant = HarnessButtonVariant.Ghost,
                testTag = "provider-editor-reload")
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
            HarnessButton(if (state.saving) "删除中…" else "删除配置", {
                submitted = true
                baseline = state.saveRevision
                onRemove(session.provider.id, session.revision)
            }, enabled = !state.saving && !stale && !state.refreshRequired, variant = HarnessButtonVariant.Destructive,
                testTag = "provider-delete-confirm")
        }
    }
}

@Composable
private fun DraftField(
    label: String, value: String, onValueChange: (String) -> Unit, enabled: Boolean, tag: String,
    password: Boolean = false,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        HarnessText(label, style = MaterialTheme.typography.bodySmall, color = HarnessTokens.Colors.TextMuted)
        BasicTextField(
            value = value, onValueChange = onValueChange, enabled = enabled, singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = HarnessTokens.Colors.Text),
            cursorBrush = SolidColor(HarnessTokens.Colors.Accent),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            modifier = Modifier.fillMaxWidth().semantics { testTag = tag }
                .border(1.dp, HarnessTokens.Colors.Border, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
}
