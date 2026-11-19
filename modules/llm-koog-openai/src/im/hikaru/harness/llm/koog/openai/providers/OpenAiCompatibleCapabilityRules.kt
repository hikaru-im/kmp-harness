package im.hikaru.harness.llm.koog.openai.providers

import ai.koog.prompt.llm.LLMCapability
import im.hikaru.harness.llm.*
import im.hikaru.harness.llm.koog.KoogModelRoute
import im.hikaru.harness.llm.koog.KoogStreamContext
import im.hikaru.harness.llm.koog.openai.catalog.OpenAiModelCatalogSnapshot
import kotlinx.serialization.json.*

/** Exact model rules; an unfamiliar model remains unknown until explicitly configured. */
public object OpenAiCompatibleCapabilityRules : LlmCapabilityRules {
    override val version: String = "2026-10-21.1"

    override fun resolve(connection: LlmProviderConnection, model: LlmResolvedModelInfo): LlmResolvedModelInfo {
        val capabilities = effective(connection.vendor, connection.api, model.id, model.reasoning, model.reasoningCapabilities, model.metadata)
        return model.copy(reasoningCapabilities = capabilities, reasoning = legacy(capabilities, model.reasoning))
    }

    internal fun resolve(route: KoogModelRoute, context: KoogStreamContext): KoogModelRoute {
        val capabilities = effective(context.vendor, context.api, route.model.id, route.reasoning, route.reasoningCapabilities, route.metadata)
        val thinking = capabilities.effortSupport == CapabilitySupport.SUPPORTED ||
            ReasoningModeId.ENABLED in capabilities.modes || capabilities.budgetSupport == CapabilitySupport.SUPPORTED
        val sdkCapabilities = route.model.capabilities.orEmpty().filterNot { it == LLMCapability.Thinking }.toMutableList()
        if (thinking) sdkCapabilities += LLMCapability.Thinking
        return route.copy(model = route.model.copy(capabilities = sdkCapabilities),
            reasoningCapabilities = capabilities, reasoning = legacy(capabilities, route.reasoning))
    }

    private fun legacy(capabilities: ReasoningCapabilities, previous: LlmModelReasoningInfo?): LlmModelReasoningInfo? {
        if (capabilities.effortSupport != CapabilitySupport.SUPPORTED) return null
        val efforts = capabilities.efforts
        return LlmModelReasoningInfo(efforts, previous?.defaultEffort?.takeIf { id -> efforts.any { it.id == id } })
    }

    internal fun capabilities(vendor: String, api: String, id: String, legacy: LlmModelReasoningInfo? = null): ReasoningCapabilities {
        val responses = api == BuiltinProviderContributions.RESPONSES
        return when (vendor) {
            "openai" -> when (id) {
                "o3-mini", "o3-mini-2025-01-31" -> spec(listOf("low", "medium", "high"), alwaysOn = true)
                "gpt-6.1-sol", "gpt-6-astra" -> spec(listOf("low", "medium", "high", "xhigh", "max"), alwaysOn = true)
                "gpt-6-sol", "gpt-6-luna" -> spec(listOf("none", "low", "medium", "high", "xhigh", "max"), off = true)
                else -> legacy?.let {
                    val efforts = it.efforts.map { effort -> effort.id.value }.filter { effort -> effort in openAiEfforts }
                    spec(efforts, off = "none" in efforts)
                }
                    ?: OpenAiModelCatalogSnapshot.models.firstOrNull { it.id == id }?.let {
                        spec(it.reasoningEfforts, off = "none" in it.reasoningEfforts)
                    } ?: ReasoningCapabilities(ruleVersion = version)
            }
            "deepseek" -> when (id) {
                "deepseek-flash", "deepseek-v4-pro" -> spec(listOf("low", "high", "max"), off = true, on = true)
                else -> ReasoningCapabilities(ruleVersion = version)
            }
            "alibaba" -> when (id) {
                "qwen3.8-max", "qwen3.8-max-0902", "qwen3.8-flash", "qwen3.8-27b" ->
                    spec(listOf("low", "medium", "xhigh"), off = true, on = !responses, budget = !responses)
                "qwen3.8-2.4t-a95b" -> spec(listOf("low", "medium", "xhigh"), alwaysOn = true, budget = !responses)
                else -> ReasoningCapabilities(ruleVersion = version)
            }
            "moonshot" -> if (responses) ReasoningCapabilities(ruleVersion = version) else when (id) {
                "kimi-k3" -> spec(listOf("low", "high", "max"), alwaysOn = true)
                "kimi-k2.7-code", "kimi-k2.7-code-highspeed" -> spec(emptyList(), alwaysOn = true)
                "kimi-k2.6" -> spec(emptyList(), off = true, on = true)
                else -> ReasoningCapabilities(ruleVersion = version)
            }
            "zhipu" -> when (id) {
                "glm-5.3", "glm-5.3-flash", "glm-5.3-flashx" -> spec(listOf("low", "high", "max"), alwaysOn = true)
                "glm-5.2" -> spec(listOf("high", "max"), off = true, on = true)
                else -> ReasoningCapabilities(ruleVersion = version)
            }
            else -> ReasoningCapabilities(ruleVersion = version)
        }
    }

    private val openAiEfforts = setOf("none", "minimal", "low", "medium", "high", "xhigh", "max")

    private fun effective(vendor: String, api: String, model: String, legacy: LlmModelReasoningInfo?,
        facts: ReasoningCapabilities?, metadata: JsonObject): ReasoningCapabilities {
        var result = capabilities(vendor, api, model, legacy)
        // Remote catalog can narrow executable controls, never manufacture new transport support.
        if (facts != null) {
            result = result.copy(constraints = (result.constraints + facts.constraints).distinct())
            if (facts.modeSupport == CapabilitySupport.UNSUPPORTED) result = result.copy(modeSupport = CapabilitySupport.UNSUPPORTED, modes = emptyList())
            else if (facts.modeSupport == CapabilitySupport.SUPPORTED && result.modeSupport == CapabilitySupport.SUPPORTED)
                result = result.copy(modes = result.modes.filter { it in facts.modes })
            if (facts.effortSupport == CapabilitySupport.UNSUPPORTED) result = result.copy(effortSupport = CapabilitySupport.UNSUPPORTED, efforts = emptyList())
            else if (facts.effortSupport == CapabilitySupport.SUPPORTED && result.effortSupport == CapabilitySupport.SUPPORTED)
                result = result.copy(efforts = result.efforts.filter { effort -> facts.efforts.any { it.id == effort.id } })
            if (facts.budgetSupport == CapabilitySupport.UNSUPPORTED) result = result.copy(budgetSupport = CapabilitySupport.UNSUPPORTED, budgetRange = null)
            else if (facts.budgetSupport == CapabilitySupport.SUPPORTED && result.budgetSupport == CapabilitySupport.SUPPORTED) {
                val a = result.budgetRange
                val b = facts.budgetRange
                if (a != null && b != null) {
                    val minimum = maxOf(a.minTokens, b.minTokens)
                    val maximum = listOfNotNull(a.maxTokens, b.maxTokens).minOrNull()
                    result = if (maximum != null && minimum > maximum) result.copy(budgetSupport = CapabilitySupport.UNSUPPORTED, budgetRange = null)
                        else result.copy(budgetRange = ReasoningBudgetRange(minimum, maximum))
                }
            }
            if (facts.executionModeSupport == CapabilitySupport.UNSUPPORTED)
                result = result.copy(executionModeSupport = CapabilitySupport.UNSUPPORTED, executionModes = emptyList())
            else if (facts.executionModeSupport == CapabilitySupport.SUPPORTED && result.executionModeSupport == CapabilitySupport.SUPPORTED)
                result = result.copy(executionModes = result.executionModes.filter { it in facts.executionModes })
        }
        val raw = metadata["raw"] as? JsonObject
        if (vendor == "deepseek") {
            val levels = ((raw?.get("effort") as? JsonObject)?.get("supported_levels") as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }?.toSet()
            if (levels != null && result.effortSupport == CapabilitySupport.SUPPORTED)
                result = result.copy(efforts = result.efforts.filter { it.id.value in levels })
            // default_level is descriptive; it must never become a request default.
        }
        if (vendor == "moonshot" && (raw?.get("supports_reasoning") as? JsonPrimitive)?.booleanOrNull == false)
            result = result.copy(modeSupport = CapabilitySupport.UNSUPPORTED, modes = emptyList(),
                effortSupport = CapabilitySupport.UNSUPPORTED, efforts = emptyList())
        if (result.effortSupport == CapabilitySupport.SUPPORTED && result.efforts.isEmpty())
            result = result.copy(effortSupport = CapabilitySupport.UNSUPPORTED)
        return result
    }

    private fun spec(efforts: List<String>, off: Boolean = false, on: Boolean = false,
        alwaysOn: Boolean = false, budget: Boolean = false, executionModes: List<String> = emptyList()): ReasoningCapabilities =
        ReasoningCapabilities(
            modeSupport = CapabilitySupport.SUPPORTED,
            modes = buildList { if (off) add(ReasoningModeId.DISABLED); if (on || alwaysOn) add(ReasoningModeId.ENABLED) },
            effortSupport = if (efforts.isEmpty()) CapabilitySupport.UNSUPPORTED else CapabilitySupport.SUPPORTED,
            efforts = efforts.map { LlmReasoningEffortInfo(ReasoningEffortId(it), it) },
            budgetSupport = if (budget) CapabilitySupport.SUPPORTED else CapabilitySupport.UNSUPPORTED,
            // Qwen3.8 zero selects its documented low budget tier; it never disables thinking.
            budgetRange = if (budget) ReasoningBudgetRange(0, 262144) else null,
            executionModeSupport = if (executionModes.isEmpty()) CapabilitySupport.UNSUPPORTED else CapabilitySupport.SUPPORTED,
            executionModes = executionModes,
            constraints = buildList {
                add(ReasoningConstraint.ForMode(ReasoningModeId.DISABLED,
                    effortSupport = CapabilitySupport.UNSUPPORTED, budgetSupport = CapabilitySupport.UNSUPPORTED))
                if (budget) add(ReasoningConstraint.EffortBudgetExclusive)
            },
            ruleVersion = version,
        )
}
