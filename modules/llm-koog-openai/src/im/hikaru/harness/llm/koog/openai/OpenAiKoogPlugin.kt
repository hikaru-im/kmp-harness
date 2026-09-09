package im.hikaru.harness.llm.koog.openai

import ai.koog.http.client.KoogHttpClient
import im.hikaru.harness.llm.LlmConfigurableProvider
import im.hikaru.harness.llm.koog.KoogCredentialResolver
import im.hikaru.harness.llm.koog.KoogLlmPlugin
import im.hikaru.harness.llm.koog.KoogModelProfile
import im.hikaru.harness.llm.koog.resolveRoutes
import im.hikaru.harness.llm.koog.openai.catalog.OpenAiKoogCatalog
import im.hikaru.harness.llm.koog.openai.client.OpenAiPromptExecutorFactory
import im.hikaru.harness.llm.koog.openai.semantics.OpenAiKoogSemantics
import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.effect.EffectScope
import im.hikaru.harness.runtime.plugin.InjectSpec
import im.hikaru.harness.runtime.plugin.SimplePlugin
import im.hikaru.harness.settings.SettingsNamespace
import im.hikaru.harness.settings.settingsNamespace

/** Selects the configuration services used by one OpenAI Koog plugin instance. */
public sealed interface OpenAiKoogSettingsSource {
    /** Read live Provider profiles from SettingsKey and credentials from CredentialsKey. */
    public data class Dynamic(
        val namespace: SettingsNamespace = settingsNamespace("llm-koog"),
    ) : OpenAiKoogSettingsSource

    /** Use an immutable profile and an explicitly supplied credential resolver. */
    public data class Static(
        val credentials: KoogCredentialResolver,
    ) : OpenAiKoogSettingsSource
}

/** Code-owned OpenAI protocol installation; connection/model fields configure static mode only. */
public data class OpenAiKoogPluginConfig(
    val settingsSource: OpenAiKoogSettingsSource = OpenAiKoogSettingsSource.Dynamic(),
    val baseUrl: String? = null,
    val credentialName: String = "OPENAI_API_KEY",
    val api: String = im.hikaru.harness.llm.koog.openai.chat.OpenAiChatOptionMapper.OPENAI_CHAT_COMPLETIONS_API_ID,
    val models: List<KoogModelProfile>? = null,
    val httpClientFactory: KoogHttpClient.Factory? = null,
)

/**
 * Owns OpenAI routes, installed models, Koog clients and Chat/Responses semantics.
 *
 * Hosts install this plugin without assembling any Provider-specific objects.
 */
public class OpenAiKoogPlugin(
    config: OpenAiKoogPluginConfig = OpenAiKoogPluginConfig(),
) : SimplePlugin {
    private val delegate = createDelegate(config)

    override val inject: Set<InjectSpec> = delegate.inject

    override suspend fun apply(
        context: Context,
        scope: EffectScope,
    ) {
        delegate.apply(context, scope)
    }
}

private fun createDelegate(config: OpenAiKoogPluginConfig): KoogLlmPlugin {
    val installedRoutes = OpenAiKoogCatalog.installedRoutes()
    val factory = OpenAiPromptExecutorFactory(httpClientFactory = config.httpClientFactory)
    val semantics = OpenAiKoogSemantics.bundles()

    return when (val source = config.settingsSource) {
        is OpenAiKoogSettingsSource.Dynamic ->
            KoogLlmPlugin(
                routes = installedRoutes,
                providerExecutorFactory = factory,
                providerSemantics = semantics,
                settingsNamespace = source.namespace,
                defaults = im.hikaru.harness.llm.koog.KoogLlmSettings(emptyMap()),
                configurableProviders =
                    listOf(
                        LlmConfigurableProvider(
                            provider = OpenAiKoogCatalog.OPENAI_PROVIDER_ID,
                            displayName = "OpenAI",
                            settingsNamespace = source.namespace.value,
                            settingsPath =
                                listOf(
                                    "providers",
                                    OpenAiKoogCatalog.OPENAI_PROVIDER_ID,
                                ),
                        )
                    ),
            )

        is OpenAiKoogSettingsSource.Static ->
            OpenAiKoogCatalog.defaultSettings(
                baseUrl = config.baseUrl,
                credentialName = config.credentialName,
                api = config.api,
                models = config.models,
            ).let { settings ->
                val resolvedRoutes = settings.resolveRoutes(installedRoutes)
                val selectedApis =
                    resolvedRoutes.flatMap { route ->
                        route.models.map { route.api }
                    }.toSet()
                KoogLlmPlugin(
                    routes = resolvedRoutes,
                    providerSettings = settings.providers.values.toList(),
                    credentials = source.credentials,
                    providerExecutorFactory = factory,
                    providerSemantics = semantics.filter { it.provider in selectedApis },
                )
            }
    }
}
