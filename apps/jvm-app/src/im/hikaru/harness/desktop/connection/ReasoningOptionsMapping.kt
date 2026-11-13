package im.hikaru.harness.desktop.connection

import im.hikaru.harness.client.connection.ReasoningOptions as ClientReasoningOptions
import im.hikaru.harness.client.connection.ReasoningEffortId as ClientReasoningEffortId
import im.hikaru.harness.llm.ReasoningEffortId
import im.hikaru.harness.llm.ReasoningModeId
import im.hikaru.harness.llm.ReasoningOptions

internal fun ClientReasoningOptions.toCoreReasoning(): ReasoningOptions = ReasoningOptions(
    mode = ReasoningModeId(mode),
    effort = effort?.let { ReasoningEffortId(it.value) },
    budgetTokens = budgetTokens,
    executionModeId = executionModeId,
)

internal fun ReasoningOptions.toClientReasoning(): ClientReasoningOptions = ClientReasoningOptions(
    mode = mode.value,
    effort = effort?.let { ClientReasoningEffortId(it.value) },
    budgetTokens = budgetTokens,
    executionModeId = executionModeId,
)
