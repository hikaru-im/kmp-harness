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
) {
    public fun validate() {
        require(Regex("""[A-Za-z0-9][A-Za-z0-9_.:/-]*""").matches(id)) { "Provider ID 格式无效" }
        require(displayName.isNotBlank()) { "显示名称不能为空" }
        require(api.isNotBlank()) { "请选择已安装的 API 协议" }
        baseUrl?.let { value ->
            require(Regex("""https?://[^/?#\s]+(?:/[^?#\s]*)?""").matches(value) && '@' !in value.substringAfter("://").substringBefore('/')) {
                "地址须为 HTTP(S) URL，且不能包含账号、查询参数或片段"
            }
        }
        credentialName?.let { require(validCredentialName(it)) { "凭据名称格式无效" } }
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
public data class ProviderModel(
    val id: String, val name: String, val description: String? = null,
    val contextWindow: Long? = null, val maxTokens: Long? = null,
    val reasoningEfforts: List<String> = emptyList(),
)
public data class ConfigurableProvider(
    val id: String, val displayName: String, val configuration: ProviderConfiguration? = null,
    val active: Boolean = false, val credentialConfigured: Boolean = false,
    val credentialWritable: Boolean = false, val credentialSource: String? = null,
    val canRemove: Boolean = false, val models: List<ProviderModel> = emptyList(),
    val catalogError: String? = null,
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
)

public interface ModelSettingsBackend {
    public suspend fun read(): ModelSettingsSnapshot
    public suspend fun save(configuration: ProviderConfiguration, expectedRevision: Long)
    public suspend fun remove(providerId: String, expectedRevision: Long)
    public suspend fun setCredential(name: String, value: String)
}

public class UnavailableModelSettingsBackend : ModelSettingsBackend {
    override suspend fun read(): ModelSettingsSnapshot = ModelSettingsSnapshot()
    override suspend fun save(configuration: ProviderConfiguration, expectedRevision: Long): Unit = error("模型配置服务不可用")
    override suspend fun remove(providerId: String, expectedRevision: Long): Unit = error("模型配置服务不可用")
    override suspend fun setCredential(name: String, value: String): Unit = error("凭据服务不可用")
}

public class ProviderRevisionConflict : IllegalStateException("配置已在其他位置修改，请刷新并检查后重新保存。")
