package im.hikaru.harness.llm.koog.openai.catalog

import ai.koog.prompt.executor.clients.openai.OpenAIModels
import im.hikaru.harness.llm.LlmModelReasoningInfo
import im.hikaru.harness.llm.LlmReasoningEffortInfo
import im.hikaru.harness.llm.ModelModality
import im.hikaru.harness.llm.ReasoningEffortId
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

    public fun installedRoutes(): List<KoogProviderRoute> =
        listOf(
            KoogProviderRoute(
                id = OpenAiChatOptionMapper.OPENAI_CHAT_COMPLETIONS_API_ID,
                name = "OpenAI",
                models =
                    listOf(
                        KoogModelRoute(
                            model = OpenAIModels.Chat.GPT4oMini,
                            name = "GPT-4o mini",
                            description = "Fast OpenAI Chat Completions model",
                            inputModalities = listOf(ModelModality.TEXT),
                        ),
                        KoogModelRoute(
                            model = OpenAIModels.Chat.O3Mini,
                            name = "o3-mini",
                            description = "OpenAI reasoning model via Chat Completions",
                            inputModalities = listOf(ModelModality.TEXT),
                            reasoning =
                                LlmModelReasoningInfo(
                                    efforts =
                                        listOf(
                                            LlmReasoningEffortInfo(ReasoningEffortId("low"), "Low"),
                                            LlmReasoningEffortInfo(ReasoningEffortId("medium"), "Medium"),
                                            LlmReasoningEffortInfo(ReasoningEffortId("high"), "High"),
                                        )
                                ),
                        ),
                    ),
            ),
            KoogProviderRoute(
                id = OpenAiResponsesOptionMapper.OPENAI_RESPONSES_API_ID,
                name = "OpenAI Responses",
                models =
                    listOf(
                        KoogModelRoute(
                            model = OpenAIModels.Chat.GPT4oMini,
                            name = "GPT-4o mini",
                            description = "OpenAI Responses API text/tool model",
                            inputModalities = listOf(ModelModality.TEXT),
                        )
                    ),
            ),
        )

    public fun defaultSettings(
        baseUrl: String? = null,
        credentialName: String = "OPENAI_API_KEY",
        chatModels: List<KoogModelProfile>? = null,
        responsesModels: List<KoogModelProfile>? = null,
    ): KoogLlmSettings =
        KoogLlmSettings(
            mapOf(
                OPENAI_PROVIDER_ID to
                    KoogProviderSettings(
                        provider = OPENAI_PROVIDER_ID,
                        displayName = "OpenAI",
                        api = OpenAiChatOptionMapper.OPENAI_CHAT_COMPLETIONS_API_ID,
                        baseUrl = baseUrl,
                        credential = KoogCredentialRef(credentialName),
                        models = configuredModels(chatModels, responsesModels),
                    )
            )
        )

    private fun configuredModels(
        chatModels: List<KoogModelProfile>?,
        responsesModels: List<KoogModelProfile>?,
    ): List<KoogModelProfile>? {
        if (chatModels == null && responsesModels == null) return null

        val configured =
            chatModels.orEmpty().map { model ->
                model.copy(api = OpenAiChatOptionMapper.OPENAI_CHAT_COMPLETIONS_API_ID)
            } +
                responsesModels.orEmpty().map { model ->
                    model.copy(api = OpenAiResponsesOptionMapper.OPENAI_RESPONSES_API_ID)
                }
        require(configured.isNotEmpty()) {
            "OpenAI explicit model configuration must not be empty"
        }
        require(configured.map(KoogModelProfile::id).distinct().size == configured.size) {
            "One OpenAI Provider profile cannot declare the same model id for multiple APIs"
        }
        return configured
    }
}
