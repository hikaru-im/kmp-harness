package im.hikaru.harness.llm.koog.openai.providers

import im.hikaru.harness.llm.*
import im.hikaru.harness.llm.koog.KoogStreamContext
import kotlinx.serialization.json.*

/** Compiles controls into wire fields. Missing controls stay missing, including provider defaults. */
internal fun compileReasoning(options: GenerateOptions, context: KoogStreamContext): Map<String, JsonElement> {
    val requested = options.reasoning ?: options.reasoningEffort?.let { ReasoningOptions(effort = it) } ?: return emptyMap()
    val capabilities = context.reasoningCapabilities ?: OpenAiCompatibleCapabilityRules.capabilities(context.vendor, context.api, context.model)
    try {
        validateReasoningOptions(requested, capabilities, options.maxTokens)
    } catch (error: LlmException) {
        // Preserve the established direct-mapper error for callers of the legacy effort field.
        if (options.reasoning == null) throw LlmException(error.message ?: "Unsupported reasoning effort",
            im.hikaru.harness.llm.koog.KoogLlmErrorCode.UNSUPPORTED_OPTION)
        throw error
    }
    val mode = requested.mode
    var effort = requested.effort?.value
    val response = context.api == BuiltinProviderContributions.RESPONSES
    val fields = linkedMapOf<String, JsonElement>()
    when (context.vendor) {
        "openai" -> {
            if (mode == ReasoningModeId.DISABLED) effort = "none"
        }
        "deepseek" -> {
            if (response) {
                if (mode == ReasoningModeId.DISABLED) effort = "none"
                // Responses has no separate enabled switch. Explicit enabled uses the documented default.
                if (mode == ReasoningModeId.ENABLED && effort == null) effort = "high"
            } else if (mode != ReasoningModeId.PROVIDER_DEFAULT) {
                fields["thinking"] = buildJsonObject { put("type", if (mode == ReasoningModeId.DISABLED) "disabled" else "enabled") }
            }
        }
        "alibaba" -> {
            if (response) {
                if (mode == ReasoningModeId.DISABLED) effort = "none"
            } else {
                if (mode != ReasoningModeId.PROVIDER_DEFAULT && context.model != "qwen3.8-2.4t-a95b")
                    fields["enable_thinking"] = JsonPrimitive(mode != ReasoningModeId.DISABLED)
                requested.budgetTokens?.let { fields["thinking_budget"] = JsonPrimitive(it) }
            }
        }
        "moonshot" -> if (context.model != "kimi-k3" && mode != ReasoningModeId.PROVIDER_DEFAULT) {
            fields["thinking"] = buildJsonObject { put("type", if (mode == ReasoningModeId.DISABLED) "disabled" else "enabled") }
        }
        "zhipu" -> if (context.model == "glm-5.2" && mode != ReasoningModeId.PROVIDER_DEFAULT) {
            fields["thinking"] = buildJsonObject { put("type", if (mode == ReasoningModeId.DISABLED) "disabled" else "enabled") }
        }
    }
    if (response) {
        val reasoning = buildJsonObject {
            effort?.let { put("effort", it) }
            requested.executionModeId?.let { put("mode", it) }
        }
        if (reasoning.isNotEmpty()) fields["reasoning"] = reasoning
    } else effort?.let { fields["reasoning_effort"] = JsonPrimitive(it) }
    return fields
}
