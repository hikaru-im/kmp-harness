package im.hikaru.harness.bundle.desktop

import im.hikaru.harness.credentials.CredentialRef
import im.hikaru.harness.credentials.CredentialsKey
import im.hikaru.harness.llm.LlmRuntime
import im.hikaru.harness.llm.LlmSettingsFailure
import im.hikaru.harness.llm.llm
import im.hikaru.harness.llm.koog.KoogLlmSettings
import im.hikaru.harness.llm.koog.KoogProviderSettings
import im.hikaru.harness.llm.koog.openai.catalog.OpenAiKoogCatalog
import im.hikaru.harness.llm.koog.openai.client.OpenAiModelDiscovery
import im.hikaru.harness.runtime.Context
import im.hikaru.harness.settings.SettingsDescriptor
import im.hikaru.harness.settings.SettingsConflictException
import im.hikaru.harness.settings.SettingsKey
import im.hikaru.harness.settings.SettingsScope
import im.hikaru.harness.settings.settingsNamespace
import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*

/** Editable provider fields. Credential values are never part of a settings snapshot. */
public data class DesktopProviderConfiguration(
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
        require(!credentialName.isNullOrBlank()) { "请填写凭据引用名称" }
        require(validDesktopCredentialName(credentialName)) { "凭据名称格式无效" }
        listOf(requestTimeoutMillis, connectTimeoutMillis, socketTimeoutMillis, defaultContextWindow, defaultMaxTokens).forEach {
            require(it == null || it > 0) { "超时和模型容量须为正整数" }
        }
        modelIds?.let { ids ->
            require(ids.all { it.isNotBlank() && it.none(Char::isWhitespace) } && ids.distinct().size == ids.size) { "模型 ID 不能为空、包含空白或重复" }
        }
    }
}

public fun validDesktopCredentialName(name: String): Boolean = Regex("""[A-Za-z_][A-Za-z0-9_]*""").matches(name)

public data class DesktopProviderProtocol(val id: String, val label: String)
public data class DesktopProviderModel(
    val id: String, val name: String, val description: String? = null,
    val contextWindow: Long? = null, val maxTokens: Long? = null,
    val reasoningEfforts: List<String> = emptyList(),
)
public data class DesktopConfigurableProvider(
    val id: String, val displayName: String, val configuration: DesktopProviderConfiguration? = null,
    val active: Boolean = false, val credentialConfigured: Boolean = false,
    val credentialWritable: Boolean = false, val credentialSource: String? = null,
    val canRemove: Boolean = false, val models: List<DesktopProviderModel> = emptyList(),
    val catalogError: String? = null,
    val applicationError: String? = null,
    val applying: Boolean = false,
)
public data class DesktopProviderSettingsSnapshot(
    val providers: List<DesktopConfigurableProvider> = emptyList(),
    val protocols: List<DesktopProviderProtocol> = emptyList(),
    val revision: Long = 0,
)

public class DesktopProviderRevisionConflict : IllegalStateException("配置已在其他位置修改，请刷新并检查后重新保存。")

/**
 * Provider-neutral handle to the runtime model catalog for Desktop host sources.
 *
 * App sources must reach provider capabilities through the bundle layer rather than importing
 * provider internals ([im.hikaru.harness.desktop.DesktopProductionBoundaryTest] enforces this), so
 * the single-model lookup used by scheduled-execution admission lives here.
 */
public class DesktopModelCatalog internal constructor(private val llm: LlmRuntime) {
    /** Throws when the runtime catalog cannot resolve this provider/model pair. */
    public suspend fun requireResolvable(providerId: String, modelId: String) {
        llm.resolveModelInfo(providerId, modelId)
    }
}

/** Requires the local LLM runtime, so an unavailable catalog is reported eagerly, then hands back the handle. */
public fun requireDesktopModelCatalog(context: Context): DesktopModelCatalog =
    DesktopModelCatalog(context.llm)

/** Desktop bundle service for provider catalogs, settings overrides, and credential writes. */
public class DesktopProviderSettings(private val context: Context) {
    private val namespace = settingsNamespace("llm-koog")
    private val protocols = OpenAiKoogCatalog.installedRoutes().map { DesktopProviderProtocol(it.id, it.name) }

    private fun settings(): SettingsScope = context.require(SettingsKey).scope(namespace)

    public suspend fun read(): DesktopProviderSettingsSnapshot {
        val descriptor = settings().describe()
        val configuration = KoogLlmSettings.fromJson(descriptor.value).providers
        val directory = context.llm.listConfigurableProviders().filter { it.settingsNamespace == namespace.value }
        val active = context.llm.listProviders().map { it.id }.toSet()
        val credentials = context.require(CredentialsKey)
        val application = context.llm.settingsApplication(namespace.value)?.applicationState
        val providers = (directory.map { it.provider } + configuration.keys).distinct().map { id ->
            val configured = configuration[id]
            val reference = configured?.credential?.name?.let(::CredentialRef)
            val credential = reference?.let { credentials.describe(it) }
            val applicationError = when {
                configured != null && reference == null -> "未绑定凭据，请编辑 Provider 并填写凭据引用名称。"
                configured != null && credential?.configured != true -> "引用的凭据不存在，请配置凭据后重试应用。"
                application?.failure == LlmSettingsFailure.Credential -> "凭据加载失败，请检查已配置 Provider 的凭据后重试应用。"
                application?.failure == LlmSettingsFailure.Configuration -> "配置应用失败，请检查 API 协议、模型和连接配置后重试应用。"
                application?.failure == LlmSettingsFailure.Load -> "配置加载失败，请重试应用。"
                else -> null
            }
            var catalogError: String? = null
            val models = if (id !in active) emptyList() else try {
                context.llm.listModels(id).map { model ->
                    val resolved = context.llm.resolveModelInfo(id, model.id)
                    DesktopProviderModel(model.id, resolved.name, resolved.description,
                        resolved.context?.contextWindow, resolved.defaultMaxTokens,
                        resolved.reasoning?.efforts?.map { it.id.value }.orEmpty())
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                catalogError = "当前模型目录无法读取，请刷新重试。"
                emptyList()
            }
            DesktopConfigurableProvider(
                id = id, displayName = configured?.displayName ?: directory.firstOrNull { it.provider == id }?.displayName ?: id,
                configuration = configured?.clientConfiguration(), active = id in active,
                credentialConfigured = credential?.configured == true, credentialWritable = credential?.writable == true, credentialSource = credential?.source,
                canRemove = configured != null && (descriptor.base["providers"] as? JsonObject)?.containsKey(id) != true,
                models = models, catalogError = catalogError,
                applicationError = applicationError, applying = application?.applying == true,
            )
        }
        return DesktopProviderSettingsSnapshot(providers, protocols, descriptor.revision)
    }

    public suspend fun save(configuration: DesktopProviderConfiguration, expectedRevision: Long) {
        configuration.validate()
        require(configuration.api in protocols.map { it.id }) { "此 API 协议没有安装" }
        configuration.baseUrl?.let { url ->
            val parsed = URI(url)
            require(parsed.host != null && (parsed.port == -1 || parsed.port in 1..65535)) { "服务地址的主机或端口无效" }
        }
        val scope = settings()
        val descriptor = scope.describe()
        checkRevision(descriptor, expectedRevision)
        val previous = (descriptor.value["providers"] as? JsonObject)?.get(configuration.id) as? JsonObject
        val user = descriptor.user?.toMutableMap() ?: mutableMapOf()
        val providers = (user["providers"] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
        val fields = (providers[configuration.id] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
        listOf("provider", "displayName", "api", "baseUrl", "credential", "requestTimeoutMillis", "connectTimeoutMillis", "socketTimeoutMillis",
            "models", "defaultContextWindow", "defaultMaxTokens").forEach { fields.remove(it) }
        fields["provider"] = JsonPrimitive(configuration.id)
        fields["displayName"] = JsonPrimitive(configuration.displayName)
        fields["api"] = JsonPrimitive(configuration.api)
        configuration.baseUrl?.let { fields["baseUrl"] = JsonPrimitive(it) }
        configuration.credentialName?.let { fields["credential"] = buildJsonObject { put("name", it) } }
        configuration.requestTimeoutMillis?.let { fields["requestTimeoutMillis"] = JsonPrimitive(it) }
        configuration.connectTimeoutMillis?.let { fields["connectTimeoutMillis"] = JsonPrimitive(it) }
        configuration.socketTimeoutMillis?.let { fields["socketTimeoutMillis"] = JsonPrimitive(it) }
        configuration.defaultContextWindow?.let { fields["defaultContextWindow"] = JsonPrimitive(it) }
        configuration.defaultMaxTokens?.let { fields["defaultMaxTokens"] = JsonPrimitive(it) }
        configuration.modelIds?.let { ids ->
            val previousModels = (previous?.get("models") as? JsonArray)?.associateBy { (it as JsonObject)["id"]?.jsonPrimitive?.content }.orEmpty()
            fields["models"] = JsonArray(ids.map { id -> previousModels[id] ?: buildJsonObject { put("id", id) } })
        }
        providers[configuration.id] = JsonObject(fields)
        user["providers"] = JsonObject(providers)
        replace(scope, JsonObject(user), expectedRevision)
    }

    public suspend fun remove(providerId: String, expectedRevision: Long) {
        val scope = settings()
        val descriptor = scope.describe()
        checkRevision(descriptor, expectedRevision)
        require((descriptor.base["providers"] as? JsonObject)?.containsKey(providerId) != true) { "此 Provider 由 Host 预设提供，不能删除" }
        val user = descriptor.user?.toMutableMap() ?: mutableMapOf()
        val providers = (user["providers"] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
        require(providers.remove(providerId) != null) { "此 Provider 没有用户配置可删除" }
        user["providers"] = JsonObject(providers)
        replace(scope, JsonObject(user), expectedRevision)
    }

    public suspend fun setCredential(name: String, value: String) {
        require(validDesktopCredentialName(name) && value.isNotBlank()) { "凭据名称或值无效" }
        val reference = CredentialRef(name)
        val service = context.require(CredentialsKey)
        check(service.describe(reference).writable) { "当前凭据来源不可写入" }
        service.set(reference, value)
        // Await application before publishing readback; credential watchers are asynchronous.
        retryApplication()
    }

    public suspend fun retryApplication() {
        checkNotNull(context.llm.settingsApplication(namespace.value)) { "模型配置应用服务不可用" }.retry()
    }

    public suspend fun discoverModels(providerId: String, expectedRevision: Long) {
        val scope = settings()
        val before = scope.describe()
        checkRevision(before, expectedRevision)
        val provider = KoogLlmSettings.fromJson(before.value).providers[providerId]
            ?: error("请先保存 Provider 配置。")
        require(provider.api in protocols.map { it.id }) { "此协议暂不支持自动获取模型。" }
        val reference = provider.credential?.name?.let(::CredentialRef) ?: error("请先绑定凭据引用。")
        val secret = context.require(CredentialsKey).resolve(reference)?.value ?: error("请先配置该 Provider 的凭据。")
        val discovered = OpenAiModelDiscovery().fetch(provider.baseUrl, secret, provider.requestTimeoutMillis ?: 30_000)
        // The network request may outlive an external edit: never overwrite a newer revision.
        val current = scope.describe()
        checkRevision(current, expectedRevision)
        val user = current.user?.toMutableMap() ?: mutableMapOf()
        val providers = (user["providers"] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
        val fields = (providers[providerId] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
        val oldModels = (current.value["providers"] as? JsonObject)?.get(providerId)?.jsonObject?.get("models")
            ?.jsonArray?.associateBy { it.jsonObject["id"]?.jsonPrimitive?.content }.orEmpty()
        fields["models"] = JsonArray(discovered.map { model ->
            val old = oldModels[model.id]?.jsonObject?.toMutableMap() ?: mutableMapOf()
            old["id"] = JsonPrimitive(model.id)
            model.name?.let { old["name"] = JsonPrimitive(it) }
            model.contextWindow?.let { old["contextWindow"] = JsonPrimitive(it) }
            JsonObject(old)
        })
        // modelOverrides and an explicit replacement catalog are mutually exclusive. Preserve
        // user overrides for surviving IDs by merging them into the explicit model entries.
        val overrides = current.value["providers"]?.jsonObject?.get(providerId)?.jsonObject?.get("modelOverrides") as? JsonObject
        if (overrides != null) {
            fields["models"] = JsonArray(fields.getValue("models").jsonArray.map { model ->
                JsonObject(model.jsonObject + (overrides[model.jsonObject.getValue("id").jsonPrimitive.content] as? JsonObject).orEmpty())
            })
            fields["modelOverrides"] = buildJsonObject {}
        }
        providers[providerId] = JsonObject(fields)
        user["providers"] = JsonObject(providers)
        replace(scope, JsonObject(user), expectedRevision)
    }

    private fun checkRevision(descriptor: SettingsDescriptor, expected: Long) {
        if (descriptor.revision != expected) throw DesktopProviderRevisionConflict()
    }

    private suspend fun replace(scope: SettingsScope, user: JsonObject, expected: Long) {
        try { scope.replace(user, expected) } catch (_: SettingsConflictException) { throw DesktopProviderRevisionConflict() }
    }

    private fun KoogProviderSettings.clientConfiguration(): DesktopProviderConfiguration = DesktopProviderConfiguration(
        id = provider, displayName = displayName, api = api ?: "openai-chat-completions",
        baseUrl = baseUrl, credentialName = credential?.name, requestTimeoutMillis = requestTimeoutMillis,
        connectTimeoutMillis = connectTimeoutMillis, socketTimeoutMillis = socketTimeoutMillis,
        modelIds = models?.map { it.id }, defaultContextWindow = defaultContextWindow, defaultMaxTokens = defaultMaxTokens,
    )
}
