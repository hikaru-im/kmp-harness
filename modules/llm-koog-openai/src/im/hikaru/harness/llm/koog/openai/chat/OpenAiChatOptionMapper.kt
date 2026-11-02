package im.hikaru.harness.llm.koog.openai.chat

import ai.koog.prompt.executor.clients.openai.OpenAIChatParams
import ai.koog.prompt.executor.clients.openai.base.models.ReasoningEffort
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.params.LLMParams
import im.hikaru.harness.llm.GenerateOptions
import im.hikaru.harness.llm.LlmException
import im.hikaru.harness.llm.koog.KoogLlmErrorCode
import im.hikaru.harness.llm.koog.KoogOptionMapper
import im.hikaru.harness.llm.koog.KoogModelRoute
import im.hikaru.harness.llm.koog.KoogStreamContext
import im.hikaru.harness.llm.koog.openai.providers.OpenAiCompatibleCapabilityRules
import im.hikaru.harness.llm.koog.openai.providers.compileReasoning
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/**
 * OpenAI Chat Completions 参数映射。
 *
 * 该 mapper 只生成 [OpenAIChatParams]，因此 OpenAI client 会选择 Chat Completions
 * endpoint；不会把 OpenAI 专属字段塞进通用 `LLMParams`。
 */
public class OpenAiChatOptionMapper : KoogOptionMapper {

    override fun map(
        options: GenerateOptions,
        model: LLModel,
    ): LLMParams = map(options, model, KoogStreamContext(options.provider, model.id, model.provider.id, OPENAI_CHAT_COMPLETIONS_API_ID))

    override fun resolveModel(route: KoogModelRoute, context: KoogStreamContext): KoogModelRoute =
        OpenAiCompatibleCapabilityRules.resolve(route, context)

    override fun map(options: GenerateOptions, model: LLModel, context: KoogStreamContext): LLMParams {
        requireOpenAiRoute(options, model)
        requireCapability(model, LLMCapability.Completion, "completion")
        requireCapability(model, LLMCapability.OpenAIEndpoint.Completions, "Chat Completions")

        val temperature = options.temperature
        if (temperature != null && !model.supports(LLMCapability.Temperature)) {
            unsupported("Model ${model.id} does not support temperature")
        }

        val stop = options.stop?.toList()
        if (stop != null) {
            validateStop(stop)
            // OpenAI reasoning models do not accept Chat Completions stop sequences.
            if (model.supports(LLMCapability.Thinking)) {
                unsupported("OpenAI reasoning model ${model.id} does not support stop sequences")
            }
        }

        val fields = compileReasoning(options, context).toMutableMap()
        val effortValue = fields["reasoning_effort"]?.jsonPrimitive?.content
        val effort = OPENAI_REASONING_EFFORTS[effortValue]
        if (effort != null) fields.remove("reasoning_effort")
        if (context.vendor == "openai" && model.id in setOf("gpt-6.1-sol", "gpt-6-astra"))
            unsupported("Model ${model.id} requires the Responses adapter")
        if (context.vendor == "openai" && model.id in setOf("gpt-6-sol", "gpt-6-luna") &&
            (!options.tools.isNullOrEmpty() || options.temperature != null) && effortValue != "none")
            unsupported("Model ${model.id} requires reasoning disabled for Chat tools or temperature")
        // SDK chooses max_completion_tokens for every thinking model. Compatible APIs differ.
        if (context.vendor != "openai") options.maxTokens?.let {
            fields[if (context.vendor == "alibaba" && model.id in setOf("qwen3.8-max", "qwen3.8-max-0902", "qwen3.8-flash", "qwen3.8-27b", "qwen3.8-2.4t-a95b")) "max_completion_tokens" else "max_tokens"] = JsonPrimitive(it.toOpenAiInt())
        }

        return OpenAIChatParams(
            temperature = temperature,
            maxTokens = if (context.vendor == "openai") options.maxTokens?.toOpenAiInt() else null,
            reasoningEffort = effort,
            stop = stop,
            additionalProperties = fields,
        )
    }

    private fun requireOpenAiRoute(
        options: GenerateOptions,
        model: LLModel,
    ) {
        if (
            model.provider != LLMProvider.OpenAI
        ) {
            throw LlmException(
                message =
                    "OpenAI option mapper received route ${options.provider}/${model.id} " +
                        "with client provider ${model.provider.id}",
                code = KoogLlmErrorCode.UNSUPPORTED_OPTION,
            )
        }
    }

    private fun requireCapability(
        model: LLModel,
        capability: LLMCapability,
        label: String,
    ) {
        if (!model.supports(capability)) {
            unsupported("Model ${model.id} does not support $label")
        }
    }

    private fun validateStop(stop: List<String>) {
        if (stop.isEmpty() || stop.size > 4 || stop.any(String::isBlank)) {
            unsupported("OpenAI stop must contain one to four non-blank sequences")
        }
    }

    private fun Long.toOpenAiInt(): Int {
        if (this > Int.MAX_VALUE) {
            unsupported("OpenAI maxTokens cannot exceed ${Int.MAX_VALUE}: $this")
        }
        return toInt()
    }

    private fun unsupported(message: String): Nothing =
        throw LlmException(
            message = message,
            code = KoogLlmErrorCode.UNSUPPORTED_OPTION,
        )

    public companion object {
        /** Installed semantics id for OpenAI's Chat Completions protocol. */
        public const val OPENAI_CHAT_COMPLETIONS_API_ID: String =
            "openai-chat-completions"

        /** @deprecated Protocol ids are APIs, not externally visible Provider ids. */
        @Deprecated(
            message = "Use OPENAI_CHAT_COMPLETIONS_API_ID",
            replaceWith = ReplaceWith("OPENAI_CHAT_COMPLETIONS_API_ID"),
        )
        public const val OPENAI_CHAT_COMPLETIONS_PROVIDER_ID: String =
            OPENAI_CHAT_COMPLETIONS_API_ID

        /** @deprecated Use OpenAiKoogCatalog.OPENAI_PROVIDER_ID for the Provider profile. */
        @Deprecated(
            message = "Use OpenAiKoogCatalog.OPENAI_PROVIDER_ID for the Provider profile",
        )
        public const val OPENAI_PROVIDER_ID: String = OPENAI_CHAT_COMPLETIONS_API_ID

        private val OPENAI_REASONING_EFFORTS =
            mapOf(
                "none" to ReasoningEffort.NONE,
                "minimal" to ReasoningEffort.MINIMAL,
                "low" to ReasoningEffort.LOW,
                "medium" to ReasoningEffort.MEDIUM,
                "high" to ReasoningEffort.HIGH,
            )

    }
}
