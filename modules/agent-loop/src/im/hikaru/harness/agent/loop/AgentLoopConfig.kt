package im.hikaru.harness.agent.loop

/**
 * Agent-loop scheduling defaults. Model selection belongs to AgentOptions, and
 * prompt content belongs to the system-prompt service, matching DSH.
 */
data class AgentLoopConfig(
    /** Upper bound on tool calls executing at once within one step. */
    val maxParallelToolCalls: Int = 10,
) {
    init {
        require(maxParallelToolCalls > 0) { "Agent loop maxParallelToolCalls must be positive" }
    }
}
