package im.hikaru.harness.llm.koog.openai.responses

import ai.koog.prompt.executor.clients.openai.OpenAIResponsesParams
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.params.LLMParams
import im.hikaru.harness.llm.GenerateOptions
import im.hikaru.harness.llm.LlmException
import im.hikaru.harness.llm.koog.KoogLlmErrorCode
import im.hikaru.harness.llm.koog.KoogOptionMapper

/** OpenAI Responses API 参数映射。 */
public class OpenAiResponsesOptionMapper : KoogOptionMapper {
    override fun map(
        options: GenerateOptions,
        model: LLModel,
    ): LLMParams {
        requireResponsesRoute(options, model)
        requireCapability(model, LLMCapability.Completion, "completion")
        requireCapability(model, LLMCapability.OpenAIEndpoint.Responses, "Responses API")

        if (options.stop != null) {
            unsupported("OpenAI Responses API does not support stop sequences in this adapter")
        }
        if (options.reasoningEffort != null) {
            unsupported(
                "OpenAI Responses reasoning effort is not enabled until a route declares its reasoning contract",
            )
        }
        if (options.temperature != null && !model.supports(LLMCapability.Temperature)) {
            unsupported("Model ${model.id} does not support temperature")
        }

        return OpenAIResponsesParams(
            temperature = options.temperature,
            maxTokens = options.maxTokens?.toOpenAiInt(),
            store = false,
        )
    }

    private fun requireResponsesRoute(
        options: GenerateOptions,
        model: LLModel,
    ) {
        if (model.provider != LLMProvider.OpenAI) {
            throw LlmException(
                message =
                    "OpenAI Responses option mapper received route ${options.provider}/${model.id} " +
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

    private fun Long.toOpenAiInt(): Int {
        if (this > Int.MAX_VALUE) {
            unsupported("OpenAI max_output_tokens cannot exceed ${Int.MAX_VALUE}: $this")
        }
        return toInt()
    }

    private fun unsupported(message: String): Nothing =
        throw LlmException(
            message = message,
            code = KoogLlmErrorCode.UNSUPPORTED_OPTION,
        )

    public companion object {
        public const val OPENAI_RESPONSES_API_ID: String = "openai-responses"

        /** @deprecated Protocol ids are APIs, not externally visible Provider ids. */
        @Deprecated(
            message = "Use OPENAI_RESPONSES_API_ID",
            replaceWith = ReplaceWith("OPENAI_RESPONSES_API_ID"),
        )
        public const val OPENAI_RESPONSES_PROVIDER_ID: String = OPENAI_RESPONSES_API_ID
    }
}
