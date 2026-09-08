package im.hikaru.harness.llm.koog.openai.client

import ai.koog.http.client.HttpClientFactoryResolver
import ai.koog.http.client.KoogHttpClient
import ai.koog.prompt.executor.clients.ConnectionTimeoutConfig
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.model.PromptExecutor
import im.hikaru.harness.llm.LlmException
import im.hikaru.harness.llm.koog.KoogLlmErrorCode
import im.hikaru.harness.llm.koog.KoogCredentialResolver
import im.hikaru.harness.llm.koog.KoogProviderExecutorFactory
import im.hikaru.harness.llm.koog.KoogProviderRoute
import im.hikaru.harness.llm.koog.KoogProviderSettings
import im.hikaru.harness.llm.koog.resolveRequired
import im.hikaru.harness.llm.koog.openai.chat.OpenAiChatOptionMapper
import im.hikaru.harness.llm.koog.openai.responses.OpenAiResponsesOptionMapper
import java.net.URI

/** JVM/Desktop 的 OpenAI executor factory for Chat Completions and Responses routes. */
public class OpenAiPromptExecutorFactory(
    private val httpClientFactory: KoogHttpClient.Factory? = null,
    private val defaultBaseUrl: String = DEFAULT_BASE_URL,
    /** Optional compatibility filter for a single-route fixture or legacy caller. */
    private val providerId: String? = null,
) : KoogProviderExecutorFactory {

    override suspend fun create(
        routes: List<KoogProviderRoute>,
        settings: List<KoogProviderSettings>,
        credentials: KoogCredentialResolver,
    ): PromptExecutor {
        require(routes.isNotEmpty()) {
            "OpenAI executor requires at least one resolved Provider route"
        }
        val routeIds = routes.map(KoogProviderRoute::id)
        val settingIds = settings.map(KoogProviderSettings::provider)
        require(routeIds.distinct().size == routeIds.size && settingIds.distinct().size == settingIds.size) {
            "OpenAI executor Provider routes and settings must have unique ids"
        }
        if (routeIds.toSet() != settingIds.toSet()) {
            throw LlmException(
                message =
                    "OpenAI executor settings must match resolved Provider routes exactly",
                code = KoogLlmErrorCode.UNSUPPORTED_OPTION,
            )
        }
        val selectedApis =
            routes.flatMap { route ->
                route.models.map { model -> model.api ?: route.api }
            }.toSet()
        if (selectedApis.isEmpty() || !selectedApis.all(SUPPORTED_API_IDS::contains)) {
            throw LlmException(
                message =
                    "OpenAI executor only supports APIs ${SUPPORTED_API_IDS.joinToString()}; " +
                        "received ${selectedApis.joinToString()}",
                code = KoogLlmErrorCode.UNSUPPORTED_OPTION,
            )
        }
        if (providerId != null && providerId !in selectedApis) {
            throw LlmException(
                message = "OpenAI executor does not contain requested API '$providerId'",
                code = KoogLlmErrorCode.UNSUPPORTED_OPTION,
            )
        }
        val settingsByProvider = settings.associateBy(KoogProviderSettings::provider)
        val selectedSettings = routeIds.map(settingsByProvider::getValue)
        val references =
            selectedSettings.map { setting ->
                setting.credential
                    ?: throw LlmException(
                        message =
                            "OpenAI credential reference is not configured for ${setting.provider}",
                        code = im.hikaru.harness.llm.LlmErrorCode.INVALID_CREDENTIAL,
                    )
            }
        val referenceNames = references.map { reference -> reference.name }.distinct()
        if (referenceNames.size != 1) {
            throw LlmException(
                message =
                    "OpenAI Provider profiles must share one credential reference per executor",
                code = KoogLlmErrorCode.UNSUPPORTED_OPTION,
            )
        }
        val baseUrls =
            selectedSettings
                .map { setting -> validateBaseUrl(setting.baseUrl ?: defaultBaseUrl) }
                .distinct()
        if (baseUrls.size != 1) {
            throw LlmException(
                message = "OpenAI Provider profiles must share one base URL per executor",
                code = KoogLlmErrorCode.UNSUPPORTED_OPTION,
            )
        }
        val requestTimeoutMillis =
            selectedSettings
                .map { it.requestTimeoutMillis ?: KoogHttpClient.Factory.DEFAULT_REQUEST_TIMEOUT_MS }
                .distinct()
        val connectTimeoutMillis =
            selectedSettings
                .map { it.connectTimeoutMillis ?: KoogHttpClient.Factory.DEFAULT_CONNECT_TIMEOUT_MS }
                .distinct()
        val socketTimeoutMillis =
            selectedSettings
                .map { it.socketTimeoutMillis ?: KoogHttpClient.Factory.DEFAULT_SOCKET_TIMEOUT_MS }
                .distinct()
        if (requestTimeoutMillis.size != 1 || connectTimeoutMillis.size != 1 || socketTimeoutMillis.size != 1) {
            throw LlmException(
                message = "OpenAI Provider profiles must share one HTTP timeout configuration per executor",
                code = KoogLlmErrorCode.UNSUPPORTED_OPTION,
            )
        }
        val reference = references.first()
        val apiKey =
            credentials.resolveRequired(
                provider = selectedSettings.first().provider,
                reference = reference,
            )
        val baseUrl = baseUrls.single()
        val client =
            OpenAILLMClient(
                apiKey = apiKey,
                settings = OpenAIClientSettings(
                    baseUrl = baseUrl,
                    timeoutConfig =
                        ConnectionTimeoutConfig(
                            requestTimeoutMillis = requestTimeoutMillis.single(),
                            connectTimeoutMillis = connectTimeoutMillis.single(),
                            socketTimeoutMillis = socketTimeoutMillis.single(),
                        ),
                ),
                httpClientFactory = httpClientFactory ?: HttpClientFactoryResolver.resolve(),
            )

        return try {
            // Do not configure a Koog fallback model or an SDK retry wrapper: Harness owns retries.
            PromptExecutor.builder()
                .addClient(client)
                .build()
        } catch (error: Throwable) {
            client.close()
            throw error
        }
    }

    private fun validateBaseUrl(value: String): String {
        val uri =
            runCatching { URI(value) }
                .getOrElse {
                    throw invalidBaseUrl()
                }
        if (uri.scheme != "https" && uri.scheme != "http") {
            throw invalidBaseUrl()
        }
        if (uri.host.isNullOrBlank()) {
            throw invalidBaseUrl()
        }
        if (uri.userInfo != null || uri.query != null || uri.fragment != null) {
            throw invalidBaseUrl()
        }
        val normalized = value.trimEnd('/')
        // Koog appends v1/chat/completions or v1/responses itself.
        return normalized.removeSuffix("/v1")
    }

    private fun invalidBaseUrl(): LlmException =
        LlmException(
            message = "OpenAI base URL must be an http(s) URL without credentials or query parameters",
            code = KoogLlmErrorCode.UNSUPPORTED_OPTION,
        )

    public companion object {
        public const val DEFAULT_BASE_URL: String = "https://api.openai.com"

        private val SUPPORTED_API_IDS =
            setOf(
                OpenAiChatOptionMapper.OPENAI_CHAT_COMPLETIONS_API_ID,
                OpenAiResponsesOptionMapper.OPENAI_RESPONSES_API_ID,
            )
    }
}
