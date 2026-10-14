package im.hikaru.harness.subagent.tools

import kotlinx.serialization.Serializable

@Serializable
enum class SpawnAgentOutcome {
    ACCEPTED,
    CREATED_PROMPT_FAILED,
    CREATED_DELIVERY_UNKNOWN,
    REJECTED,
    CREATE_FAILED,
    ROLLED_BACK,
    ROLLBACK_FAILED,
}

@Serializable
enum class SpawnAgentDelivery { NOT_ATTEMPTED, ACCEPTED, UNKNOWN }

/** Encoded as JSON in the tool's TextBlock; never an agent completion receipt. */
@Serializable
data class SpawnAgentResult(
    val outcome: SpawnAgentOutcome,
    val parentSessionId: String? = null,
    val childSessionId: String? = null,
    val inputMessageId: String? = null,
    val receiptId: String? = null,
    val delivery: SpawnAgentDelivery = SpawnAgentDelivery.NOT_ATTEMPTED,
    val completionVerified: Boolean = false,
    val error: String? = null,
    val rollbackError: String? = null,
)
