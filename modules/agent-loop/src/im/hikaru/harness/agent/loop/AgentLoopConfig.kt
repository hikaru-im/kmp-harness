package im.hikaru.harness.agent.loop

import im.hikaru.harness.llm.LlmCallConfig

/** Defaults used by newly-created agents. Callers may still provide per-agent options later. */
data class AgentLoopConfig(
    val provider: String,
    val model: String,
    val system: String? = null,
) {
    init {
        require(provider.isNotBlank()) { "Agent loop provider must not be blank" }
        require(model.isNotBlank()) { "Agent loop model must not be blank" }
    }

    fun callConfig(): LlmCallConfig =
        LlmCallConfig(provider = provider, model = model)
}
