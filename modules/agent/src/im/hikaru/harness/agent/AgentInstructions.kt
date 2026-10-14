package im.hikaru.harness.agent

import im.hikaru.harness.runtime.service.ServiceKey

/**
 * Supplies a text snapshot for one Agent request; null means no contribution.
 * Source failures and cancellation must propagate to the caller.
 */
public fun interface AgentInstructionsService {
    public suspend fun readInstructions(agent: Agent): String?
}

public object AgentInstructionsKey : ServiceKey<AgentInstructionsService>("agent-instructions")
