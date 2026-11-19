package im.hikaru.harness.client.settings

/** Editable provider fields. Credential values are never part of a settings snapshot. */
public data class ProviderConfiguration(
    val id: String,
    val displayName: String = id,
    val api: String = "openai-chat-completions",
    val baseUrl: String? = null,
    val credentialName: String? = null,
    val requestTimeoutMillis: Long? = null,
    val connectTimeoutMillis: Long? = null,
    val socketTimeoutMillis: Long? = null,
    val modelIds: List<String>? = null,
    val defaultContextWindow: Long? = null,
    val defaultMaxTokens: Long? = null,
    val vendor: String = "openai",
    val serviceVariant: String? = null,
    val region: String? = null,
    val workspace: String? = null,
    val apiVersion: String? = null,
) {
    public fun validate() {
        require(Regex("""[A-Za-z0-9][A-Za-z0-9_.:/-]*""").matches(id)) { "Provider ID 格式无效" }
        require(displayName.isNotBlank()) { "显示名称不能为空" }
        require(api.isNotBlank()) { "请选择已安装的 API 协议" }
        require(vendor.isNotBlank()) { "请选择厂商插件" }
        baseUrl?.let { value ->
            require(Regex("""https?://[^/?#\s]+(?:/[^?#\s]*)?""").matches(value) && '@' !in value.substringAfter("://").substringBefore('/')) {
                "地址须为 HTTP(S) URL，且不能包含账号、查询参数或片段"
            }
        }
        require(!credentialName.isNullOrBlank()) { "请填写凭据引用名称" }
        require(validCredentialName(credentialName)) { "凭据名称格式无效" }
        listOf(requestTimeoutMillis, connectTimeoutMillis, socketTimeoutMillis, defaultContextWindow, defaultMaxTokens).forEach {
            require(it == null || it > 0) { "超时和模型容量须为正整数" }
        }
        modelIds?.let { ids ->
            require(ids.all { it.isNotBlank() && it.none(Char::isWhitespace) } && ids.distinct().size == ids.size) { "模型 ID 不能为空、包含空白或重复" }
        }
    }
}

public fun validCredentialName(name: String): Boolean = Regex("""[A-Za-z_][A-Za-z0-9_]*""").matches(name)

public data class ModelProtocol(val id: String, val label: String)
public data class ProviderEndpointProfile(
    val id: String, val label: String, val baseUrl: String, val api: String,
    val region: String? = null, val serviceVariant: String? = null, val requiresWorkspace: Boolean = false,
)
public data class ProviderConnectionField(val id: String, val label: String, val required: Boolean = false)
public data class ProviderPlugin(
    val id: String, val name: String, val protocols: List<String>,
    val endpoints: List<ProviderEndpointProfile> = emptyList(),
    val connectionFields: List<ProviderConnectionField> = emptyList(),
    val supportsDiscovery: Boolean = false, val inferenceSupported: Boolean = false,
    val discoveryStatus: String? = null,
)
public data class ModelReasoningCapabilities(
    val modes: List<String> = emptyList(), val efforts: List<String> = emptyList(),
    val budgetSupported: Boolean = false, val minBudget: Long? = null, val maxBudget: Long? = null,
    val executionModes: List<String> = emptyList(), val ruleVersion: String? = null,
    val modeSupport: ModelCapabilitySupport = if (modes.isEmpty()) ModelCapabilitySupport.UNKNOWN else ModelCapabilitySupport.SUPPORTED,
    val effortSupport: ModelCapabilitySupport = if (efforts.isEmpty()) ModelCapabilitySupport.UNKNOWN else ModelCapabilitySupport.SUPPORTED,
    val budgetSupport: ModelCapabilitySupport = if (budgetSupported) ModelCapabilitySupport.SUPPORTED else ModelCapabilitySupport.UNKNOWN,
    val executionModeSupport: ModelCapabilitySupport = if (executionModes.isEmpty()) ModelCapabilitySupport.UNKNOWN else ModelCapabilitySupport.SUPPORTED,
    val constraints: List<ModelReasoningConstraint> = emptyList(),
    val defaultMaxTokens: Long? = null,
)

public enum class ModelCapabilitySupport { SUPPORTED, UNSUPPORTED, UNKNOWN }

/** Finite capability facts shared by generic controls; never vendor-specific UI logic. */
public sealed interface ModelReasoningConstraint {
    public data object EffortBudgetExclusive : ModelReasoningConstraint
    public data object BudgetLessThanMaxTokens : ModelReasoningConstraint
    public data class ForMode(
        val mode: String,
        val effortSupport: ModelCapabilitySupport? = null,
        val allowedEfforts: List<String>? = null,
        val budgetSupport: ModelCapabilitySupport? = null,
        val requiresBudget: Boolean = false,
    ) : ModelReasoningConstraint
}
public data class ProviderModel(
    val id: String, val name: String, val description: String? = null,
    val contextWindow: Long? = null, val maxTokens: Long? = null,
    val reasoningEfforts: List<String> = emptyList(),
    val reasoningCapabilities: ModelReasoningCapabilities? = null,
    val selectable: Boolean = true,
    val absentFromCatalog: Boolean = false,
)
public data class ConfigurableProvider(
    val id: String, val displayName: String, val configuration: ProviderConfiguration? = null,
    val active: Boolean = false, val credentialConfigured: Boolean = false,
    val credentialWritable: Boolean = false, val credentialSource: String? = null,
    val canRemove: Boolean = false, val models: List<ProviderModel> = emptyList(),
    val catalogError: String? = null,
    val applicationError: String? = null,
    val applying: Boolean = false,
    val catalogSource: String? = null,
    val catalogWarnings: List<String> = emptyList(),
    val disabledModelIds: Set<String> = emptySet(),
    val selectedModelId: String? = null,
    val catalogUpdatedAt: String? = null,
)
public data class ModelSettingsSnapshot(
    val providers: List<ConfigurableProvider> = emptyList(),
    val protocols: List<ModelProtocol> = emptyList(),
    val revision: Long = 0,
    val available: Boolean = false,
    val writable: Boolean = false,
    val canAddProvider: Boolean = false,
    val unavailableReason: String? = "当前 Host 尚未提供模型配置服务。",
    val scopeLabel: String = "Host 模型配置",
    val plugins: List<ProviderPlugin> = emptyList(),
)

public data class ModelDiscoveryResult(
    val models: List<ProviderModel>, val source: String,
    val complete: Boolean, val warnings: List<String> = emptyList(),
)

public interface ModelSettingsBackend {
    public suspend fun read(): ModelSettingsSnapshot
    public suspend fun save(configuration: ProviderConfiguration, expectedRevision: Long)
    public suspend fun remove(providerId: String, expectedRevision: Long)
    public suspend fun setCredential(name: String, value: String)
    public suspend fun setModelEnabled(providerId: String, modelId: String, enabled: Boolean, expectedRevision: Long): Unit = error("此 Host 暂不支持模型开关")
    public suspend fun selectModel(providerId: String, modelId: String, expectedRevision: Long): Unit = error("此 Host 暂不支持默认模型")
    public suspend fun retryApplication(): Unit = error("模型配置应用服务不可用")
    public suspend fun discoverModels(providerId: String, expectedRevision: Long): Unit = error("此 Host 暂不支持获取模型列表")
    public suspend fun discoverDraft(configuration: ProviderConfiguration, apiKey: String): ModelDiscoveryResult = error("此 Host 暂不支持草稿模型发现")
    public suspend fun saveWithCredential(configuration: ProviderConfiguration, apiKey: String, expectedRevision: Long) {
        configuration.validate()
        setCredential(requireNotNull(configuration.credentialName), apiKey)
        save(configuration, expectedRevision)
    }
}

public class UnavailableModelSettingsBackend : ModelSettingsBackend {
    override suspend fun read(): ModelSettingsSnapshot = ModelSettingsSnapshot()
    override suspend fun save(configuration: ProviderConfiguration, expectedRevision: Long): Unit = error("模型配置服务不可用")
    override suspend fun remove(providerId: String, expectedRevision: Long): Unit = error("模型配置服务不可用")
    override suspend fun setCredential(name: String, value: String): Unit = error("凭据服务不可用")
}

public class ProviderRevisionConflict : IllegalStateException("配置已在其他位置修改，请刷新并检查后重新保存。")
