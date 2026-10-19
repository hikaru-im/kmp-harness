package im.hikaru.harness.subagent.tools

import kotlinx.serialization.Serializable

@Serializable
enum class SubagentOutcome {
    ACCEPTED,
    CREATED_PROMPT_FAILED,
    CREATED_DELIVERY_UNKNOWN,
    REJECTED,
    CREATE_FAILED,
    ROLLED_BACK,
    ROLLBACK_FAILED,
}

@Serializable
enum class SubagentDelivery { NOT_ATTEMPTED, ACCEPTED, UNKNOWN }

/** Encoded as JSON in the tool's TextBlock; never an agent completion receipt. */
@Serializable
data class SubagentResult(
    val outcome: SubagentOutcome,
    val parentSessionId: String? = null,
    val childSessionId: String? = null,
    val inputMessageId: String? = null,
    val receiptId: String? = null,
    val delivery: SubagentDelivery = SubagentDelivery.NOT_ATTEMPTED,
    val completionVerified: Boolean = false,
    val error: String? = null,
    val rollbackError: String? = null,
)

@Serializable
data class SendMessageResult(
    val messageId: String? = null,
    val error: String? = null,
)

@Serializable
data class InterruptAgentResult(
    val accepted: Boolean = false,
    val error: String? = null,
)

/** One list_agents row; null fields are omitted from the encoded JSON. */
@Serializable
data class ListAgentsEntry(
    val kind: String = "child",
    val id: String,
    val label: String,
    val status: String,
    val parent: String? = null,
    val depth: Long? = null,
)
