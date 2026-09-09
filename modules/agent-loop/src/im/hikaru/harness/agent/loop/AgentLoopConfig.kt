package im.hikaru.harness.agent.loop

/** Scheduling defaults used by newly-created agents. Model selection belongs to AgentOptions. */
data class AgentLoopConfig(
    val system: String? = null,
) {
    init {
        require(system == null || system.isNotBlank()) { "Agent loop system must not be blank" }
    }
}
