package im.hikaru.harness.llm.koog.openai.catalog

import im.hikaru.harness.llm.ModelModality
import im.hikaru.harness.llm.koog.KoogCredentialRef
import im.hikaru.harness.llm.koog.KoogLlmSettings
import im.hikaru.harness.llm.koog.KoogModelProfile
import im.hikaru.harness.llm.koog.KoogModelRoute
import im.hikaru.harness.llm.koog.KoogProviderRoute
import im.hikaru.harness.llm.koog.KoogProviderSettings
import im.hikaru.harness.llm.koog.openai.chat.OpenAiChatOptionMapper
import im.hikaru.harness.llm.koog.openai.responses.OpenAiResponsesOptionMapper

/** OpenAI's code-installed route catalog and deployment defaults. */
public object OpenAiKoogCatalog {
    public const val OPENAI_PROVIDER_ID: String = "openai"

    public fun installedRoutes(): List<KoogProviderRoute> {
        val snapshot = OpenAiModelCatalogSnapshot.models
        fun route(api: String, endpoint: ai.koog.prompt.llm.LLMCapability): KoogProviderRoute =
            KoogProviderRoute(
                id = api,
                name = if (api == OpenAiChatOptionMapper.OPENAI_CHAT_COMPLETIONS_API_ID) "OpenAI" else "OpenAI Responses",
                models = snapshot.map { it.toRoute(endpoint) },
            )
        return listOf(
            route(OpenAiChatOptionMapper.OPENAI_CHAT_COMPLETIONS_API_ID, ai.koog.prompt.llm.LLMCapability.OpenAIEndpoint.Completions),
            route(OpenAiResponsesOptionMapper.OPENAI_RESPONSES_API_ID, ai.koog.prompt.llm.LLMCapability.OpenAIEndpoint.Responses),
        )
    }

    public fun defaultSettings(
        baseUrl: String? = null,
        credentialName: String = "OPENAI_API_KEY",
        api: String = OpenAiChatOptionMapper.OPENAI_CHAT_COMPLETIONS_API_ID,
        models: List<KoogModelProfile>? = null,
    ): KoogLlmSettings =
        KoogLlmSettings(
            mapOf(
                OPENAI_PROVIDER_ID to
                    KoogProviderSettings(
                        provider = OPENAI_PROVIDER_ID,
                        displayName = "OpenAI",
                        api = api,
                        baseUrl = baseUrl,
                        credential = KoogCredentialRef(credentialName),
                        models = models,
                    )
            )
        )

}
