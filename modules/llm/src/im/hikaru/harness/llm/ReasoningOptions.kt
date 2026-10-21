package im.hikaru.harness.llm

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/** Stable intent IDs. Additional IDs must be declared by the exact model's capabilities. */
@JvmInline
@Serializable
value class ReasoningModeId(val value: String) {
    init { require(value.isNotBlank()) { "Reasoning mode must not be blank" } }

    companion object {
        val PROVIDER_DEFAULT = ReasoningModeId("provider-default")
        val DISABLED = ReasoningModeId("disabled")
        val ENABLED = ReasoningModeId("enabled")
        val ADAPTIVE = ReasoningModeId("adaptive")
    }
}

/** Already merged intent: default/null means omit that dimension, never fill it again. */
@Serializable
data class ReasoningOptions(
    val mode: ReasoningModeId = ReasoningModeId.PROVIDER_DEFAULT,
    val effort: ReasoningEffortId? = null,
    val budgetTokens: Long? = null,
    val executionModeId: String? = null,
) {
    init {
        require(budgetTokens == null || budgetTokens >= 0L) { "Reasoning budget must not be negative" }
        require(executionModeId == null || executionModeId.isNotBlank()) { "Execution mode must not be blank" }
    }
}

@Serializable
enum class CapabilitySupport {
    @SerialName("supported") SUPPORTED,
    @SerialName("unsupported") UNSUPPORTED,
    @SerialName("unknown") UNKNOWN,
}

@Serializable
data class ReasoningBudgetRange(
    val minTokens: Long = 0L,
    val maxTokens: Long? = null,
) {
    init {
        require(minTokens >= 0L) { "Minimum reasoning budget must not be negative" }
        require(maxTokens == null || maxTokens >= minTokens) { "Invalid reasoning budget range" }
    }
}

/** Finite, host-defined rules; model catalogs cannot supply executable validation logic. */
@Serializable
sealed interface ReasoningConstraint {
    @Serializable
    @SerialName("effort-budget-exclusive")
    data object EffortBudgetExclusive : ReasoningConstraint

    @Serializable
    @SerialName("budget-less-than-max-tokens")
    data object BudgetLessThanMaxTokens : ReasoningConstraint

    @Serializable
    @SerialName("for-mode")
    data class ForMode(
        val mode: ReasoningModeId,
        val effortSupport: CapabilitySupport? = null,
        val allowedEfforts: List<ReasoningEffortId>? = null,
        val budgetSupport: CapabilitySupport? = null,
        val requiresBudget: Boolean = false,
    ) : ReasoningConstraint
}

/** Effective, executable capabilities for one provider/model/protocol and rules version. */
@Serializable
data class ReasoningCapabilities(
    val modeSupport: CapabilitySupport = CapabilitySupport.UNKNOWN,
    val modes: List<ReasoningModeId> = emptyList(),
    val effortSupport: CapabilitySupport = CapabilitySupport.UNKNOWN,
    val efforts: List<LlmReasoningEffortInfo> = emptyList(),
    val budgetSupport: CapabilitySupport = CapabilitySupport.UNKNOWN,
    val budgetRange: ReasoningBudgetRange? = null,
    val executionModeSupport: CapabilitySupport = CapabilitySupport.UNKNOWN,
    val executionModes: List<String> = emptyList(),
    val constraints: List<ReasoningConstraint> = emptyList(),
    val ruleVersion: String? = null,
) {
    init {
        require(modes.distinct().size == modes.size) { "Duplicate reasoning modes" }
        require(efforts.map { it.id }.distinct().size == efforts.size && efforts.all { it.name.isNotBlank() }) {
            "Invalid reasoning efforts"
        }
        require(executionModes.distinct().size == executionModes.size && executionModes.all(String::isNotBlank)) {
            "Invalid reasoning execution modes"
        }
        require(ruleVersion == null || ruleVersion.isNotBlank()) { "Reasoning rules version must not be blank" }
    }
}

/** Validates only explicit controls. Unknown is distinguishable from known unsupported. */
fun validateReasoningOptions(
    options: ReasoningOptions,
    capabilities: ReasoningCapabilities?,
    maxTokens: Long? = null,
) {
    val facts = capabilities ?: ReasoningCapabilities()
    if (options.mode != ReasoningModeId.PROVIDER_DEFAULT) {
        requireReasoningSupport(facts.modeSupport, "mode")
        if (options.mode !in facts.modes) unsupportedControl("mode '${options.mode.value}'")
    }
    options.effort?.let { effort ->
        requireReasoningSupport(facts.effortSupport, "effort")
        if (facts.efforts.none { it.id == effort }) unsupportedControl("effort '${effort.value}'")
    }
    options.budgetTokens?.let { budget ->
        requireReasoningSupport(facts.budgetSupport, "budget")
        facts.budgetRange?.let { range ->
            if (budget < range.minTokens || (range.maxTokens != null && budget > range.maxTokens)) {
                invalidCombination("Reasoning budget is outside the supported range")
            }
        }
    }
    options.executionModeId?.let { mode ->
        requireReasoningSupport(facts.executionModeSupport, "execution mode")
        if (mode !in facts.executionModes) unsupportedControl("execution mode '$mode'")
    }
    facts.constraints.forEach { constraint ->
        when (constraint) {
            ReasoningConstraint.EffortBudgetExclusive ->
                if (options.effort != null && options.budgetTokens != null) {
                    invalidCombination("This model does not allow effort and budget together")
                }
            ReasoningConstraint.BudgetLessThanMaxTokens ->
                if (options.budgetTokens != null && (maxTokens == null || options.budgetTokens >= maxTokens)) {
                    invalidCombination("This model requires maxTokens greater than the reasoning budget")
                }
            is ReasoningConstraint.ForMode -> if (options.mode == constraint.mode) {
                options.effort?.let { effort ->
                    constraint.effortSupport?.let { requireReasoningSupport(it, "effort in this mode") }
                    if (constraint.allowedEfforts != null && effort !in constraint.allowedEfforts) {
                        invalidCombination("This reasoning mode does not allow effort '${effort.value}'")
                    }
                }
                if (options.budgetTokens != null) {
                    constraint.budgetSupport?.let { requireReasoningSupport(it, "budget in this mode") }
                } else if (constraint.requiresBudget) {
                    invalidCombination("This reasoning mode requires a budget")
                }
            }
        }
    }
}

private fun requireReasoningSupport(support: CapabilitySupport, dimension: String) {
    when (support) {
        CapabilitySupport.SUPPORTED -> Unit
        CapabilitySupport.UNSUPPORTED -> unsupportedControl(dimension)
        CapabilitySupport.UNKNOWN -> throw LlmException(
            "Reasoning $dimension capability has not been verified",
            LlmErrorCode.CAPABILITY_UNKNOWN,
        )
    }
}

private fun unsupportedControl(control: String): Nothing = throw LlmException(
    "The model does not support reasoning $control",
    LlmErrorCode.UNSUPPORTED_REASONING,
)

private fun invalidCombination(message: String): Nothing = throw LlmException(
    message,
    LlmErrorCode.INVALID_REASONING_OPTIONS,
)

/** Preserve the legacy field when absent; two explicit representations must agree. */
internal fun validateReasoningCompatibility(reasoning: ReasoningOptions?, effort: ReasoningEffortId?) {
    if (reasoning != null && effort != null && reasoning.effort != effort) {
        throw LlmException(
            "reasoning and legacy reasoningEffort disagree",
            LlmErrorCode.INVALID_REASONING_OPTIONS,
        )
    }
}

internal fun ReasoningCapabilities.detachedCopy(): ReasoningCapabilities = copy(
    modes = modes.toList(),
    efforts = efforts.toList(),
    executionModes = executionModes.toList(),
    constraints = constraints.map { constraint ->
        when (constraint) {
            is ReasoningConstraint.ForMode -> constraint.copy(allowedEfforts = constraint.allowedEfforts?.toList())
            else -> constraint
        }
    },
)
