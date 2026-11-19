package im.hikaru.harness.llm.koog.openai

import ai.koog.http.client.KoogHttpClient
import im.hikaru.harness.credentials.CredentialRef
import im.hikaru.harness.credentials.CredentialsKey
import im.hikaru.harness.llm.*
import im.hikaru.harness.llm.koog.*
import im.hikaru.harness.llm.koog.openai.catalog.OpenAiKoogCatalog
import im.hikaru.harness.llm.koog.openai.providers.BuiltinProviderContributions
import im.hikaru.harness.llm.koog.openai.providers.OpenAiCompatibleAdapterFactory
import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.effect.EffectScope
import im.hikaru.harness.runtime.plugin.InjectSpec
import im.hikaru.harness.runtime.plugin.SimplePlugin
import im.hikaru.harness.settings.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext

/** The legacy name is retained for persisted plugin configuration; all creation uses the registry. */
public sealed interface OpenAiKoogSettingsSource {
    public data class Dynamic(val namespace: SettingsNamespace = settingsNamespace("llm-koog")) : OpenAiKoogSettingsSource
    public data class Static(val credentials: KoogCredentialResolver) : OpenAiKoogSettingsSource
}

public data class OpenAiKoogPluginConfig(
    val settingsSource: OpenAiKoogSettingsSource = OpenAiKoogSettingsSource.Dynamic(),
    val baseUrl: String? = null,
    val credentialName: String = "OPENAI_API_KEY",
    val api: String = BuiltinProviderContributions.CHAT,
    val models: List<KoogModelProfile>? = null,
    val httpClientFactory: KoogHttpClient.Factory? = null,
)

/** Installs vendor contributions, then creates every configured connection through that registry. */
public class OpenAiKoogPlugin(private val config: OpenAiKoogPluginConfig = OpenAiKoogPluginConfig()) : SimplePlugin {
    override val inject: Set<InjectSpec> = buildSet {
        add(InjectSpec.required(LlmKey))
        if (config.settingsSource is OpenAiKoogSettingsSource.Dynamic) {
            add(InjectSpec.required(SettingsKey))
            add(InjectSpec.required(CredentialsKey))
        }
    }

    override suspend fun apply(context: Context, scope: EffectScope) {
        val llm = context.llm
        val factory = OpenAiCompatibleAdapterFactory(config.httpClientFactory)
        BuiltinProviderContributions.all().forEach { contribution ->
            scope.add(llm.registerProviderContribution(if (contribution.descriptor.id in compatibleVendors)
                contribution.copy(adapterFactory = factory) else contribution))
        }
        val templates = OpenAiKoogCatalog.installedRoutes()
        suspend fun routes(settings: KoogLlmSettings): List<KoogProviderRoute> {
            val executable = linkedMapOf<String, KoogProviderSettings>()
            settings.providers.forEach { (id, value) ->
                val provider = value.normalized()
                val contribution = llm.providerContribution(provider.vendor!!) ?: error("Unknown vendor '${provider.vendor}'")
                require(provider.api in contribution.descriptor.protocols) { "Protocol '${provider.api}' is unavailable for '${provider.vendor}'" }
                // Discovery-only connections remain in the directory and do not invalidate other instances.
                if (contribution.adapterFactory != null &&
                    (provider.vendor == "openai" || !provider.models.isNullOrEmpty() ||
                        (provider.catalogMatchesConnection() && (provider.discoveredCatalog?.models.orEmpty() + provider.retainedModels).isNotEmpty()))) executable[id] = provider
            }
            return KoogLlmSettings(executable).resolveRoutes(templates).filter { it.models.isNotEmpty() }
        }
        suspend fun create(settings: KoogProviderSettings, route: KoogProviderRoute, credentials: KoogCredentialResolver): LlmProviderAdapter {
            val provider = settings.normalized()
            val reference = provider.credential ?: throw LlmException("Credential reference is missing", LlmErrorCode.INVALID_CREDENTIAL)
            require(provider.vendor == "openai" || provider.baseUrl != null) { "Vendor '${provider.vendor}' requires an explicit endpoint" }
            val connection = LlmProviderConnection(provider.provider, provider.vendor!!, provider.api!!,
                provider.baseUrl ?: "https://api.openai.com/v1", provider.serviceVariant, provider.region,
                provider.workspace, reference.name, apiVersion = provider.apiVersion)
            return llm.createProviderAdapter(connection.vendor, LlmProviderAccess(connection,
                apiKey = credentials.resolveRequired(provider.provider, reference), displayName = provider.displayName,
                requestTimeoutMillis = provider.requestTimeoutMillis, connectTimeoutMillis = provider.connectTimeoutMillis,
                socketTimeoutMillis = provider.socketTimeoutMillis,
                models = route.models.map { model -> LlmResolvedModelInfo(provider.provider, model.model.id, model.name,
                    description = model.description, inputModalities = model.inputModalities,
                    context = model.model.contextLength?.let(::LlmModelContext), defaultMaxTokens = model.defaultMaxTokens,
                    maxOutputTokens = model.model.maxOutputTokens, reasoning = model.reasoning,
                    reasoningCapabilities = model.reasoningCapabilities, metadata = model.metadata) }))
        }
        when (val source = config.settingsSource) {
            is OpenAiKoogSettingsSource.Static -> {
                val settings = OpenAiKoogCatalog.defaultSettings(config.baseUrl, config.credentialName, config.api, config.models)
                routes(settings).forEach { route ->
                    val resource = create(settings.providers.getValue(route.id), route, source.credentials)
                    scope.add(resource)
                    scope.add(llm.registerAdapter(listOf(route.id), resource.adapter))
                }
            }
            is OpenAiKoogSettingsSource.Dynamic -> {
                val service = context.require(SettingsKey)
                val credentials = context.require(CredentialsKey)
                val settings = service.register(source.namespace, KoogLlmSettings(emptyMap()).toJson(), applies = SettingsApplies.LIVE,
                    validator = SettingsValidator { KoogLlmSettings.fromJson(it) })
                val directory = listOf(LlmConfigurableProvider("openai", "OpenAI", source.namespace.value, listOf("providers", "openai")))
                val registration = llm.registerConfigurableProviders(directory)
                val coordinator = KoogLlmReloadCoordinator(templates, settings, credentials,
                    llm = llm, directoryRegistration = registration, baseDirectory = directory,
                    parentScope = CoroutineScope(currentCoroutineContext().minusKey(Job)),
                    routeResolver = ::routes,
                    connectionFactory = { value, route -> create(value, route) { reference -> credentials.resolve(CredentialRef(reference.name))?.value } })
                try {
                    coordinator.start()
                    scope.add(coordinator)
                    scope.add(llm.registerSettingsApplication(source.namespace.value, coordinator))
                } catch (error: Throwable) { coordinator.dispose(); throw error }
            }
        }
    }
}

private val compatibleVendors = setOf("openai", "deepseek", "alibaba", "moonshot", "zhipu")

private fun KoogProviderSettings.normalized(): KoogProviderSettings = copy(
    vendor = vendor ?: "openai",
    api = api ?: if (provider in setOf(BuiltinProviderContributions.CHAT, BuiltinProviderContributions.RESPONSES)) provider else BuiltinProviderContributions.CHAT,
)
