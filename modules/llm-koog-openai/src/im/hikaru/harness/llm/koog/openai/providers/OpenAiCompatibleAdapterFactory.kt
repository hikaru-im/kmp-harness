package im.hikaru.harness.llm.koog.openai.providers

import ai.koog.http.client.KoogHttpClient
import im.hikaru.harness.llm.*
import im.hikaru.harness.llm.koog.*
import im.hikaru.harness.llm.koog.openai.catalog.OpenAiKoogCatalog
import im.hikaru.harness.llm.koog.openai.client.OpenAiPromptExecutorFactory
import im.hikaru.harness.llm.koog.openai.semantics.OpenAiKoogSemantics

/** A registered contribution owns one connection's client; no shared SDK provider routing pool. */
internal class OpenAiCompatibleAdapterFactory(private val httpClientFactory: KoogHttpClient.Factory?) : LlmProviderAdapterFactory {
    override suspend fun create(access: LlmProviderAccess): LlmProviderAdapter {
        val connection = access.connection
        require(access.models.isNotEmpty()) { "Provider '${connection.provider}' has no selected models" }
        val settings = KoogProviderSettings(
            provider = connection.provider, displayName = access.displayName ?: connection.provider,
            vendor = connection.vendor, api = connection.api, baseUrl = connection.baseUrl,
            serviceVariant = connection.serviceVariant, region = connection.region,
            workspace = connection.workspace, apiVersion = connection.apiVersion,
            credential = KoogCredentialRef(connection.credentialRef ?: "provider-access"),
            requestTimeoutMillis = access.requestTimeoutMillis, connectTimeoutMillis = access.connectTimeoutMillis,
            socketTimeoutMillis = access.socketTimeoutMillis,
            models = access.models.map { model -> KoogModelProfile(
                id = model.id, name = model.name, description = model.description,
                contextWindow = model.context?.contextWindow, maxTokens = model.defaultMaxTokens,
                maxOutputTokens = model.maxOutputTokens, input = model.inputModalities,
                reasoningEfforts = model.reasoning?.let { KoogReasoningEfforts.Supported(it.efforts.map { effort -> effort.id }, it.defaultEffort) },
            ) },
        )
        val route = KoogLlmSettings(mapOf(settings.provider to settings)).resolveRoutes(OpenAiKoogCatalog.installedRoutes()).single().let { route ->
            route.copy(models = route.models.map { model ->
                val configured = access.models.single { it.id == model.model.id }
                model.copy(reasoning = configured.reasoning, reasoningCapabilities = configured.reasoningCapabilities, metadata = configured.metadata)
            })
        }
        val executor = OpenAiPromptExecutorFactory(httpClientFactory).create(listOf(route), listOf(settings)) { access.apiKey }
        return try {
            val semantics = OpenAiKoogSemantics.bundles().single { it.provider == connection.api }
            val adapter = KoogLlmAdapter(executor, listOf(route),
                optionMapper = semantics.optionMapper, usageMapper = semantics.usageMapper,
                finishReasonMapper = semantics.finishReasonMapper, replayRestorer = semantics.replayRestorer,
                replayWriter = semantics.replayWriter, failureClassifier = semantics.failureClassifier,
                messageMapper = semantics.messageMapper, toolMapper = semantics.toolMapper,
                reasoningMapper = semantics.reasoningMapper, toolStreamMapper = semantics.toolStreamMapper,
                toolCallTerminalPolicy = semantics.toolCallTerminalPolicy, textTerminalPolicy = semantics.textTerminalPolicy)
            object : LlmProviderAdapter {
                override val adapter: LlmAdapter = adapter
                override suspend fun dispose() { executor.close() }
            }
        } catch (error: Throwable) { executor.close(); throw error }
    }
}
