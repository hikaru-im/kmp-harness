package im.hikaru.harness.llm.koog

import ai.koog.prompt.executor.model.PromptExecutor
import im.hikaru.harness.llm.LlmErrorCode
import im.hikaru.harness.llm.LlmException
import kotlinx.serialization.Serializable

/** 只保存引用名，不保存 API key 本身。 */
@Serializable
data class KoogCredentialRef(
    val name: String,
) {
    init {
        require(name.isNotBlank()) {
            "Koog credential reference must not be blank"
        }
    }
}

/** Provider 部署设置；它覆盖已安装 route 的连接信息和模型目录。 */
@Serializable
data class KoogProviderSettings(
    val provider: String,
    val displayName: String = provider,
    /** Installed protocol template used by models that do not override it. */
    val api: String? = null,
    val baseUrl: String? = null,
    val credential: KoogCredentialRef? = null,
    /** Maximum wall-clock duration for one HTTP request, in milliseconds. */
    val requestTimeoutMillis: Long? = null,
    /** Maximum duration allowed to establish the connection, in milliseconds. */
    val connectTimeoutMillis: Long? = null,
    /** Maximum duration between socket reads/writes, in milliseconds. */
    val socketTimeoutMillis: Long? = null,
    /** Omitted or empty keeps the installed route catalog; non-empty replaces it. */
    val models: List<KoogModelProfile>? = null,
    /** Partial changes to installed models without replacing the rest of the catalog. */
    val modelOverrides: Map<String, KoogModelOverride> = emptyMap(),
    /** Capacity fallback for configured models absent from the installed catalog. */
    val defaultContextWindow: Long? = null,
    /** Output-capability fallback; unlike a model's explicit maxTokens, this is not a request default. */
    val defaultMaxTokens: Long? = null,
    /** Input fallback for configured models absent from the installed catalog. */
    val defaultInput: List<im.hikaru.harness.llm.ModelModality>? = null,
    /** Omission preserves the legacy OpenAI semantics; never infer this from a URL or key. */
    val vendor: String? = null,
    val serviceVariant: String? = null,
    val region: String? = null,
    val workspace: String? = null,
    val apiVersion: String? = null,
    val discoveredCatalog: im.hikaru.harness.llm.LlmCatalogSnapshot? = null,
    val discoveredCatalogConnection: im.hikaru.harness.llm.LlmProviderConnection? = null,
    /** Picker preferences never restrict the runtime catalog. */
    val disabledModelIds: Set<String> = emptySet(),
    val catalogManaged: Boolean = false,
    val selectedModelId: String? = null,
    val catalogUpdatedAt: String? = null,
    val retainedModels: List<im.hikaru.harness.llm.LlmDiscoveredModel> = emptyList(),
) {
    init {
        require(provider.isNotBlank()) {
            "Koog provider must not be blank"
        }
        require(displayName.isNotBlank()) {
            "Koog provider display name must not be blank"
        }
        require(api == null || api.isNotBlank()) {
            "Koog provider api must not be blank"
        }
        listOf(vendor, serviceVariant, region, workspace, apiVersion).forEach { value ->
            require(value == null || value.isNotBlank()) { "Koog connection identity fields must not be blank" }
        }
        require(baseUrl == null || baseUrl.isNotBlank()) {
            "Koog provider base URL must not be blank"
        }
        listOf(
            "requestTimeoutMillis" to requestTimeoutMillis,
            "connectTimeoutMillis" to connectTimeoutMillis,
            "socketTimeoutMillis" to socketTimeoutMillis,
        ).forEach { (name, value) ->
            require(value == null || value > 0L) {
                "Koog provider $name must be positive"
            }
        }
        require(models == null || models.map(KoogModelProfile::id).distinct().size == models.size) {
            "Koog provider models must not contain duplicate ids"
        }
        require(modelOverrides.isEmpty() || models.isNullOrEmpty()) {
            "Koog provider '$provider' cannot set modelOverrides beside a non-empty models list"
        }
        require(modelOverrides.keys.all(String::isNotBlank)) {
            "Koog provider model override ids must not be blank"
        }
        require(defaultContextWindow == null || defaultContextWindow > 0L) {
            "Koog provider default context window must be positive"
        }
        require(defaultMaxTokens == null || defaultMaxTokens > 0L) {
            "Koog provider default max tokens must be positive"
        }
        require(defaultInput == null || defaultInput.isNotEmpty()) {
            "Koog provider default input must not be empty"
        }
        require(defaultInput == null || defaultInput.distinct().size == defaultInput.size) {
            "Koog provider default input must not contain duplicates"
        }
    }
}

/** 运行时解析 Host credential 引用；实现不得把值写回配置对象。 */
fun interface KoogCredentialResolver {
    suspend fun resolve(reference: KoogCredentialRef): String?
}

/** 解析一个 Provider 明确要求的凭据；缺失或空值使用稳定错误码失败。 */
suspend fun KoogCredentialResolver.resolveRequired(
    provider: String,
    reference: KoogCredentialRef,
): String {
    require(provider.isNotBlank()) {
        "Koog credential provider must not be blank"
    }
    val value = resolve(reference)
    if (value.isNullOrBlank()) {
        throw LlmException(
            message = "Credential '${reference.name}' is unavailable for provider '$provider'",
            code = LlmErrorCode.INVALID_CREDENTIAL,
        )
    }
    return value
}

/** K10 Provider factory 的异步边界；具体 Koog client 由用户实现。 */
fun interface KoogProviderExecutorFactory {
    suspend fun create(
        routes: List<KoogProviderRoute>,
        settings: List<KoogProviderSettings>,
        credentials: KoogCredentialResolver,
    ): PromptExecutor
}
