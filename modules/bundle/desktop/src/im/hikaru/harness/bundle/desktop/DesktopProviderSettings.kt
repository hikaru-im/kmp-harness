package im.hikaru.harness.bundle.desktop

import im.hikaru.harness.credentials.CredentialRef
import im.hikaru.harness.credentials.CredentialsKey
import im.hikaru.harness.llm.LlmRuntime
import im.hikaru.harness.llm.LlmSettingsFailure
import im.hikaru.harness.llm.CapabilitySupport
import im.hikaru.harness.llm.LlmCatalogSnapshot
import im.hikaru.harness.llm.LlmCatalogCompleteness
import im.hikaru.harness.llm.LlmEndpointProfile
import im.hikaru.harness.llm.LlmProviderAccess
import im.hikaru.harness.llm.LlmProviderConnection
import im.hikaru.harness.llm.LlmProviderDescriptor
import im.hikaru.harness.llm.ReasoningCapabilities
import im.hikaru.harness.llm.llm
import im.hikaru.harness.llm.koog.KoogLlmSettings
import im.hikaru.harness.llm.koog.KoogProviderSettings
import im.hikaru.harness.runtime.Context
import im.hikaru.harness.settings.SettingsDescriptor
import im.hikaru.harness.settings.SettingsConflictException
import im.hikaru.harness.settings.SettingsKey
import im.hikaru.harness.settings.SettingsScope
import im.hikaru.harness.settings.settingsNamespace
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
    val reasoningCapabilities: ReasoningCapabilities? = null,
    val defaultMaxTokens: Long? = null,
    val selectable: Boolean = true,
    val absentFromCatalog: Boolean = false,
)
public data class DesktopConfigurableProvider(
    val id: String, val displayName: String, val configuration: DesktopProviderConfiguration? = null,
    val active: Boolean = false, val credentialConfigured: Boolean = false,
    val credentialWritable: Boolean = false, val credentialSource: String? = null,
    val canRemove: Boolean = false, val models: List<DesktopProviderModel> = emptyList(),
    val catalogError: String? = null,
    val applicationError: String? = null,
    val applying: Boolean = false,
    val catalogSource: String? = null,
    val catalogWarnings: List<String> = emptyList(),
    val disabledModelIds: Set<String> = emptySet(),
    val selectedModelId: String? = null,
    val catalogUpdatedAt: String? = null,
)
public data class DesktopProviderSettingsSnapshot(
    val providers: List<DesktopConfigurableProvider> = emptyList(),
    val protocols: List<DesktopProviderProtocol> = emptyList(),
    val revision: Long = 0,
    val plugins: List<DesktopProviderPlugin> = emptyList(),
)

public data class DesktopProviderPlugin(val descriptor: LlmProviderDescriptor, val inferenceSupported: Boolean)
public data class DesktopModelDiscoveryResult(
    val models: List<DesktopProviderModel>, val source: String,
    val complete: Boolean, val warnings: List<String> = emptyList(),
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
    private data class DraftCatalog(
        val connection: LlmProviderConnection, val keyFingerprint: ByteArray, val snapshot: LlmCatalogSnapshot,
        val expiresAtNanos: Long = System.nanoTime() + 300_000_000_000L,
    )
    private val draftCatalogs = ConcurrentHashMap<String, DraftCatalog>()
    private val draftRequests = ConcurrentHashMap<String, Long>()
    private val requestSequence = AtomicLong()
    private val catalogMutationMutex = Mutex()

    private suspend fun protocols(): List<DesktopProviderProtocol> = context.llm.listProviderDescriptors()
        .flatMap { it.protocols }.distinct().map { DesktopProviderProtocol(it, it) }

    private fun settings(): SettingsScope = context.require(SettingsKey).scope(namespace)

    public suspend fun read(): DesktopProviderSettingsSnapshot {
        val descriptor = settings().describe()
        val configuration = KoogLlmSettings.fromJson(descriptor.value).providers
        val directory = context.llm.listConfigurableProviders().filter { it.settingsNamespace == namespace.value }
        val active = context.llm.listProviders().map { it.id }.toSet()
        val credentials = context.require(CredentialsKey)
        val pluginDescriptors = context.llm.listProviderDescriptors()
        val plugins = pluginDescriptors.map {
            DesktopProviderPlugin(it, context.llm.providerContribution(it.id)?.adapterFactory != null)
        }
        val application = context.llm.settingsApplication(namespace.value)?.applicationState
        val providers = (directory.map { it.provider } + configuration.keys).distinct().map { id ->
            val configured = configuration[id]
            val reference = configured?.credential?.name?.let(::CredentialRef)
            val credential = reference?.let { credentials.describe(it) }
            val plugin = plugins.firstOrNull { it.descriptor.id == (configured?.vendor ?: "openai") }
            val cachedCatalog = configured?.let { matchingCatalog(it) }
            val applicationError = when {
                configured != null && plugin == null -> "厂商插件尚未安装。"
                configured != null && plugin?.inferenceSupported == false -> "此插件已提供目录能力，推理适配尚未接入。"
                configured != null && reference == null -> "未绑定凭据，请编辑 Provider 并填写凭据引用名称。"
                configured != null && credential?.configured != true -> "引用的凭据不存在，请配置凭据后重试应用。"
                application?.failure == LlmSettingsFailure.Credential -> "凭据加载失败，请检查已配置 Provider 的凭据后重试应用。"
                application?.failure == LlmSettingsFailure.Configuration -> "配置应用失败，请检查 API 协议、模型和连接配置后重试应用。"
                application?.failure == LlmSettingsFailure.Load -> "配置加载失败，请重试应用。"
                else -> null
            }
            var catalogError: String? = null
            val models = if (id !in active) cachedCatalog?.toDesktop()?.models.orEmpty() else try {
                context.llm.listModels(id).map { model ->
                    val resolved = context.llm.resolveModelInfo(id, model.id)
                    DesktopProviderModel(model.id, resolved.name, resolved.description,
                        resolved.context?.contextWindow, resolved.maxOutputTokens,
                        resolved.reasoningCapabilities?.let { capabilities ->
                            if (capabilities.effortSupport == CapabilitySupport.SUPPORTED) capabilities.efforts.map { it.id.value } else emptyList()
                        } ?: resolved.reasoning?.efforts?.map { it.id.value }.orEmpty(),
                        resolved.reasoningCapabilities, resolved.defaultMaxTokens)
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
                models = (models + cachedCatalog?.toDesktop()?.models.orEmpty() +
                    configured?.retainedModels.orEmpty().map { DesktopProviderModel(it.id, it.name) })
                    .distinctBy { it.id }.map { model ->
                        val fact = cachedCatalog?.models?.firstOrNull { it.id == model.id }
                            ?: configured?.retainedModels?.firstOrNull { it.id == model.id }
                        model.copy(selectable = fact?.let { discovered ->
                            (discovered.outputModalities == null || "text" in discovered.outputModalities.orEmpty()) &&
                                (discovered.supportedOperations == null || discovered.supportedOperations.orEmpty().any {
                                    it in setOf("chat", "chat-completions", "responses", "generateContent", "generate", "text-generation", "generateText")
                                })
                        } ?: true, absentFromCatalog = cachedCatalog != null && cachedCatalog.models.none { it.id == model.id })
                    }, catalogError = catalogError,
                disabledModelIds = configured?.disabledModelIds.orEmpty(),
                selectedModelId = configured?.selectedModelId,
                catalogUpdatedAt = configured?.catalogUpdatedAt,
                applicationError = applicationError, applying = application?.applying == true,
                catalogSource = cachedCatalog?.source?.let { "cached:$it" },
                catalogWarnings = cachedCatalog?.warnings.orEmpty(),
            )
        }
        return DesktopProviderSettingsSnapshot(providers, protocols(), descriptor.revision, plugins)
    }

    public suspend fun save(configuration: DesktopProviderConfiguration, expectedRevision: Long) {
        catalogMutationMutex.withLock { saveLocked(configuration, expectedRevision) }
    }

    private suspend fun saveLocked(configuration: DesktopProviderConfiguration, expectedRevision: Long) {
        configuration.validate()
        val plugin = context.llm.providerContribution(configuration.vendor) ?: error("厂商插件尚未安装")
        require(configuration.api in plugin.descriptor.protocols) { "此厂商插件不支持所选 API 协议" }
        val resolvedConnection = connection(configuration, null)
        val parsed = URI(resolvedConnection.baseUrl)
        require(parsed.host != null && (parsed.port == -1 || parsed.port in 1..65535)) { "服务地址的主机或端口无效" }
        val scope = settings()
        val descriptor = scope.describe()
        checkRevision(descriptor, expectedRevision)
        val previous = (descriptor.value["providers"] as? JsonObject)?.get(configuration.id) as? JsonObject
        val user = descriptor.user?.toMutableMap() ?: mutableMapOf()
        val providers = (user["providers"] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
        val fields = (providers[configuration.id] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
        val previousConfiguration = KoogLlmSettings.fromJson(descriptor.value).providers[configuration.id]?.clientConfiguration()
        if (previousConfiguration == null || runCatching { connection(previousConfiguration, null) }.getOrNull() != resolvedConnection) {
            fields.remove("discoveredCatalog")
            fields.remove("discoveredCatalogConnection")
            fields.remove("retainedModels")
            fields.remove("catalogUpdatedAt")
        }
        listOf("provider", "displayName", "api", "baseUrl", "credential", "requestTimeoutMillis", "connectTimeoutMillis", "socketTimeoutMillis",
            "models", "defaultContextWindow", "defaultMaxTokens", "vendor", "region", "workspace", "serviceVariant", "apiVersion").forEach { fields.remove(it) }
        if (previousConfiguration == null) fields["catalogManaged"] = JsonPrimitive(true)
        fields["provider"] = JsonPrimitive(configuration.id)
        fields["displayName"] = JsonPrimitive(configuration.displayName)
        fields["api"] = JsonPrimitive(configuration.api)
        fields["vendor"] = JsonPrimitive(configuration.vendor)
        resolvedConnection.region?.let { fields["region"] = JsonPrimitive(it) }
        configuration.workspace?.let { fields["workspace"] = JsonPrimitive(it) }
        resolvedConnection.serviceVariant?.let { fields["serviceVariant"] = JsonPrimitive(it) }
        configuration.apiVersion?.let { fields["apiVersion"] = JsonPrimitive(it) }
        fields["baseUrl"] = JsonPrimitive(resolvedConnection.baseUrl)
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
        draftCatalogs[configuration.id]?.takeIf { cached ->
            cached.expiresAtNanos > System.nanoTime() &&
                cached.connection.copy(credentialRef = null) == resolvedConnection.copy(credentialRef = null) && configuration.credentialName?.let { name ->
                context.require(CredentialsKey).resolve(CredentialRef(name))?.value?.let { key ->
                    MessageDigest.isEqual(cached.keyFingerprint, fingerprint(key))
                }
            } == true
        }?.let { cached ->
            fields["discoveredCatalog"] = Json.encodeToJsonElement(cached.snapshot)
            fields["discoveredCatalogConnection"] = Json.encodeToJsonElement(resolvedConnection)
            fields["catalogUpdatedAt"] = JsonPrimitive(java.time.Instant.now().toString())
        }
        providers[configuration.id] = JsonObject(fields)
        user["providers"] = JsonObject(providers)
        replace(scope, JsonObject(user), expectedRevision)
        draftCatalogs.remove(configuration.id)
    }

    public suspend fun setModelEnabled(providerId: String, modelId: String, enabled: Boolean, expectedRevision: Long) {
        updateModelPreference(providerId, modelId, enabled, false, expectedRevision)
    }

    public suspend fun selectModel(providerId: String, modelId: String, expectedRevision: Long) {
        updateModelPreference(providerId, modelId, null, true, expectedRevision)
    }

    private suspend fun updateModelPreference(providerId: String, modelId: String, enabled: Boolean?, select: Boolean, expectedRevision: Long) {
        catalogMutationMutex.withLock {
            val scope = settings()
            val descriptor = scope.describe()
            checkRevision(descriptor, expectedRevision)
            val snapshot = read()
            val target = snapshot.providers.firstOrNull { it.id == providerId } ?: error("厂商配置不存在")
            val model = target.models.firstOrNull { it.id == modelId } ?: error("模型不存在，请刷新列表")
            if (select) require(model.selectable && modelId !in target.disabledModelIds) { "请先开启可用模型" }
            val user = descriptor.user?.toMutableMap() ?: mutableMapOf()
            val providers = (user["providers"] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
            snapshot.providers.forEach { provider ->
                if (provider.id == providerId || (select && provider.selectedModelId != null)) {
                    val fields = (providers[provider.id] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
                    if (provider.id == providerId && enabled != null) {
                        val disabled = provider.disabledModelIds.toMutableSet()
                        if (enabled) disabled.remove(modelId) else disabled.add(modelId)
                        fields["disabledModelIds"] = JsonArray(disabled.map(::JsonPrimitive))
                        if (!enabled && provider.selectedModelId == modelId) fields["selectedModelId"] = JsonNull
                    }
                    if (select) fields["selectedModelId"] = if (provider.id == providerId) JsonPrimitive(modelId) else JsonNull
                    providers[provider.id] = JsonObject(fields)
                }
            }
            user["providers"] = JsonObject(providers)
            replace(scope, JsonObject(user), expectedRevision)
        }
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
        draftCatalogs.remove(providerId)
        draftRequests.remove(providerId)
    }

    public suspend fun setCredential(name: String, value: String) {
        require(validDesktopCredentialName(name) && value.isNotBlank()) { "凭据名称或值无效" }
        val reference = CredentialRef(name)
        val service = context.require(CredentialsKey)
        check(service.describe(reference).writable) { "当前凭据来源不可写入" }
        catalogMutationMutex.withLock {
            // Invalidate before publishing a new key: credential observers may immediately rebuild
            // runtime routes. Discovery's final revision/key check uses this same short commit lock.
            invalidateCredentialCatalogs(reference)
            service.set(reference, value)
        }
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
        val reference = provider.credential?.name?.let(::CredentialRef) ?: error("请先绑定凭据引用。")
        val credentials = context.require(CredentialsKey)
        val credentialChanges = AtomicLong()
        val watch = credentials.watch { changed -> if (changed == reference) credentialChanges.incrementAndGet() }
        try {
            val credentialRevision = credentialChanges.get()
            val secret = credentials.resolve(reference)?.value ?: error("请先配置该 Provider 的凭据。")
            if (credentialChanges.get() != credentialRevision) throw DesktopProviderRevisionConflict()
            val configuration = provider.clientConfiguration()
            val snapshot = discover(configuration, secret, expectedRevision.toString())
            requireUsableCatalog(snapshot)
            // Include credential watch events so an A -> B -> A key change is still stale.
            catalogMutationMutex.withLock {
                val current = scope.describe()
                checkRevision(current, expectedRevision)
                if (credentialChanges.get() != credentialRevision || credentials.resolve(reference)?.value != secret)
                    throw DesktopProviderRevisionConflict()
                val user = current.user?.toMutableMap() ?: mutableMapOf()
                val providers = (user["providers"] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
                val fields = (providers[providerId] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
                // Catalog facts and user selection/overrides are separate. Empty is a valid complete result.
                val previousModels = (provider.discoveredCatalog?.models.orEmpty() + provider.retainedModels).distinctBy { it.id }
                fields["retainedModels"] = JsonArray(previousModels.filter { old -> snapshot.models.none { it.id == old.id } }
                    .map { Json.encodeToJsonElement(it) })
                fields["catalogUpdatedAt"] = JsonPrimitive(java.time.Instant.now().toString())
                fields["discoveredCatalog"] = Json.encodeToJsonElement(snapshot)
                fields["discoveredCatalogConnection"] = Json.encodeToJsonElement(connection(configuration, null))
                providers[providerId] = JsonObject(fields)
                user["providers"] = JsonObject(providers)
                replace(scope, JsonObject(user), expectedRevision)
            }
        } finally { withContext(NonCancellable) { watch.dispose() } }
    }

    /** Discovers with a transient key before a configured/active provider exists. */
    public suspend fun discoverDraft(configuration: DesktopProviderConfiguration, apiKey: String): DesktopModelDiscoveryResult {
        val before = settings().describe().revision
        val request = requestSequence.incrementAndGet()
        draftRequests[configuration.id] = request
        draftRequests.entries.removeIf { it.value < request - 64 }
        draftCatalogs.entries.removeIf { it.value.expiresAtNanos <= System.nanoTime() }
        val snapshot = discover(configuration, apiKey, null)
        if (draftRequests[configuration.id] != request || settings().describe().revision != before)
            throw DesktopProviderRevisionConflict()
        if (isUsableCatalog(snapshot)) {
            if (draftCatalogs.size >= 32 && !draftCatalogs.containsKey(configuration.id)) {
                draftCatalogs.entries.minByOrNull { it.value.expiresAtNanos }?.key?.let(draftCatalogs::remove)
            }
            draftCatalogs[configuration.id] = DraftCatalog(connection(configuration, null), fingerprint(apiKey), snapshot)
        }
        return snapshot.toDesktop()
    }

    public suspend fun saveWithCredential(configuration: DesktopProviderConfiguration, apiKey: String, expectedRevision: Long) {
        configuration.validate()
        checkRevision(settings().describe(), expectedRevision)
        val reference = CredentialRef(requireNotNull(configuration.credentialName))
        val credentials = context.require(CredentialsKey)
        val before = credentials.describe(reference)
        require(!before.configured) { "此凭据引用已存在，请使用独立引用或在凭据设置中更新。" }
        check(before.writable) { "当前凭据来源不可写入。" }
        require(apiKey.isNotBlank()) { "API key 不能为空" }
        try {
            credentials.set(reference, apiKey)
            save(configuration, expectedRevision)
        } catch (failure: Throwable) {
            withContext(NonCancellable) {
                // A concurrent successful save may already own this key. Never remove another writer's value.
                runCatching {
                    val providers = KoogLlmSettings.fromJson(settings().describe().value).providers.values
                    val referenced = providers.any { it.credential?.name == reference.name }
                    if (!referenced && credentials.resolve(reference)?.value == apiKey) credentials.unset(reference)
                }
            }
            throw failure
        }
    }

    private suspend fun discover(configuration: DesktopProviderConfiguration, apiKey: String, revision: String?): LlmCatalogSnapshot {
        require(apiKey.isNotBlank()) { "API key 不能为空" }
        return context.llm.discoverModels(configuration.vendor, LlmProviderAccess(
            connection(configuration, revision),
            apiKey = apiKey,
        ))
    }

    private suspend fun connection(configuration: DesktopProviderConfiguration, revision: String?): LlmProviderConnection {
        val descriptor = context.llm.providerContribution(configuration.vendor)?.descriptor ?: error("厂商插件尚未安装")
        require(configuration.api in descriptor.protocols) { "此厂商插件不支持所选 API 协议" }
        val regionalProfiles = descriptor.endpointProfiles.filter {
                (configuration.region == null || configuration.region == it.region) &&
                (configuration.serviceVariant == null || configuration.serviceVariant == it.serviceVariant)
        }
        val protocolProfiles = regionalProfiles.filter { it.api == configuration.api }
        // Some compatible APIs share one declared service URL. Prefer an exact protocol profile
        // whenever the plugin provides one; never pick another protocol's distinct endpoint.
        val profiles = protocolProfiles.ifEmpty {
            regionalProfiles.takeIf { it.map(LlmEndpointProfile::baseUrl).distinct().size == 1 }.orEmpty()
        }
        fun expandEndpoint(value: String): String? {
            val endpoint = value.trimEnd('/')
            if ("{workspace}" !in endpoint && "{WorkspaceId}" !in endpoint) return endpoint
            val workspace = configuration.workspace?.takeIf { it.matches(Regex("[A-Za-z0-9][A-Za-z0-9-]{0,127}")) }
                ?: return null
            return endpoint.replace("{workspace}", workspace).replace("{WorkspaceId}", workspace)
        }
        // A custom URL does not inherit an official profile's account scope merely because
        // this vendor has one default profile. Only an exact expanded endpoint can match it.
        val matchingProfiles = if (configuration.baseUrl == null) profiles else profiles.filter {
            expandEndpoint(it.baseUrl)?.let { endpoint -> endpoint == expandEndpoint(configuration.baseUrl) } == true
        }
        val profile = matchingProfiles.singleOrNull() ?: matchingProfiles.firstOrNull().takeIf {
            matchingProfiles.map { candidate -> Triple(candidate.baseUrl, candidate.region, candidate.serviceVariant) }.distinct().size == 1
        }
        val selected = configuration.baseUrl ?: profile?.baseUrl ?: descriptor.defaultEndpoints[configuration.api]
            ?: error("请选择地区与服务类型，或明确填写服务地址。")
        require(profile?.requiresWorkspace != true || configuration.workspace?.matches(Regex("[A-Za-z0-9][A-Za-z0-9-]{0,127}")) == true) {
            "所选入口需要有效的 Workspace ID。"
        }
        val endpoint = expandEndpoint(selected) ?: error("所选入口需要有效的 Workspace ID。")
        val parsed = runCatching { URI(endpoint) }.getOrNull()
        require(parsed != null && parsed.scheme in setOf("https", "http") && parsed.host != null &&
            parsed.userInfo == null && parsed.query == null && parsed.fragment == null) { "服务地址格式无效。" }
        return LlmProviderConnection(provider = configuration.id.ifBlank { "draft" }, vendor = configuration.vendor,
            api = configuration.api, baseUrl = endpoint, serviceVariant = configuration.serviceVariant ?: profile?.serviceVariant,
            region = configuration.region ?: profile?.region, workspace = configuration.workspace,
            credentialRef = configuration.credentialName, revision = revision, apiVersion = configuration.apiVersion)
    }

    private suspend fun matchingCatalog(provider: KoogProviderSettings): LlmCatalogSnapshot? {
        val catalog = provider.discoveredCatalog ?: return null
        val binding = provider.discoveredCatalogConnection ?: return null
        val current = runCatching { connection(provider.clientConfiguration(), null) }.getOrNull() ?: return null
        return catalog.takeIf { binding.copy(revision = null, baseUrl = binding.baseUrl.trimEnd('/')) == current }
    }

    /** Called under [catalogMutationMutex], before the host updates the referenced key. */
    private suspend fun invalidateCredentialCatalogs(reference: CredentialRef) {
        draftCatalogs.entries.removeIf { it.value.connection.credentialRef == reference.name }
        val scope = settings()
        val current = scope.describe()
        val affected = KoogLlmSettings.fromJson(current.value).providers.values.filter {
            it.credential?.name == reference.name && (it.discoveredCatalog != null || it.discoveredCatalogConnection != null)
        }
        if (affected.isEmpty()) return
        val user = current.user?.toMutableMap() ?: mutableMapOf()
        val providers = (user["providers"] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
        for (provider in affected) {
            val fields = (providers[provider.provider] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
            // Explicit nulls also shadow a catalog inherited from a host's base settings.
            fields["discoveredCatalog"] = JsonNull
            fields["discoveredCatalogConnection"] = JsonNull
            fields["retainedModels"] = JsonArray(emptyList())
            providers[provider.provider] = JsonObject(fields)
        }
        user["providers"] = JsonObject(providers)
        replace(scope, JsonObject(user), current.revision)
    }

    private fun isUsableCatalog(snapshot: LlmCatalogSnapshot): Boolean =
        snapshot.completeness == LlmCatalogCompleteness.COMPLETE ||
            (snapshot.completeness == LlmCatalogCompleteness.UNKNOWN && snapshot.source.startsWith("bundled:") && snapshot.warnings.isNotEmpty())

    private fun requireUsableCatalog(snapshot: LlmCatalogSnapshot) {
        require(isUsableCatalog(snapshot)) { "模型目录不完整，保留已有配置。" }
    }

    private fun fingerprint(key: String): ByteArray = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))

    private fun LlmCatalogSnapshot.toDesktop(): DesktopModelDiscoveryResult = DesktopModelDiscoveryResult(
        models = models.map { DesktopProviderModel(it.id, it.name, it.description, it.contextWindow, it.maxOutputTokens) },
        source = source, complete = completeness == LlmCatalogCompleteness.COMPLETE, warnings = warnings,
    )

    private fun checkRevision(descriptor: SettingsDescriptor, expected: Long) {
        if (descriptor.revision != expected) throw DesktopProviderRevisionConflict()
    }

    private suspend fun replace(scope: SettingsScope, user: JsonObject, expected: Long) {
        currentCoroutineContext().ensureActive()
        // Cancellation after a file backend's atomic move must not prevent its in-memory revision
        // from committing: credential rollback relies on this authoritative ownership snapshot.
        try { withContext(NonCancellable) { scope.replace(user, expected) } }
        catch (_: SettingsConflictException) { throw DesktopProviderRevisionConflict() }
    }

    private fun KoogProviderSettings.clientConfiguration(): DesktopProviderConfiguration = DesktopProviderConfiguration(
        id = provider, displayName = displayName, api = api ?: "openai-chat-completions",
        baseUrl = baseUrl, credentialName = credential?.name, requestTimeoutMillis = requestTimeoutMillis,
        connectTimeoutMillis = connectTimeoutMillis, socketTimeoutMillis = socketTimeoutMillis,
        modelIds = models?.map { it.id }, defaultContextWindow = defaultContextWindow, defaultMaxTokens = defaultMaxTokens,
        vendor = vendor ?: "openai", serviceVariant = serviceVariant, region = region, workspace = workspace, apiVersion = apiVersion,
    )
}
