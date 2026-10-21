package im.hikaru.harness.client.connection

import kotlinx.serialization.Serializable

/** Wire-compatible reasoning intent; provider field names remain on the Host. */
@Serializable
public data class ReasoningOptions(
    val mode: String = "provider-default",
    val effort: ReasoningEffortId? = null,
    val budgetTokens: Long? = null,
    val executionModeId: String? = null,
) {
    init {
        require(mode.isNotBlank()) { "Reasoning mode must not be blank" }
        require(budgetTokens == null || budgetTokens >= 0L) { "Reasoning budget must not be negative" }
        require(executionModeId == null || executionModeId.isNotBlank()) { "Reasoning execution mode must not be blank" }
    }
}
