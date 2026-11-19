package im.hikaru.harness.llm.koog.openai.providers

import im.hikaru.harness.llm.*
import kotlinx.serialization.json.*
import kotlin.test.*

class OpenAiCompatibleCapabilityRulesTest {
    @Test
    fun remoteDeepSeekEffortsCanNarrowRulesButCannotIntroduceLevelsOrRequestDefaults() {
        val resolved = OpenAiCompatibleCapabilityRules.resolve(connection("deepseek"),
            LlmResolvedModelInfo("test", "deepseek-v4-pro", "DeepSeek", metadata = buildJsonObject {
                putJsonObject("raw") { putJsonObject("effort") {
                    put("supported_levels", JsonArray(listOf(JsonPrimitive("high"), JsonPrimitive("experimental"))))
                    put("default_level", "high")
                } }
            }))
        assertEquals(listOf("high"), resolved.reasoningCapabilities!!.efforts.map { it.id.value })
        assertNull(resolved.reasoning!!.defaultEffort)
        assertFailsWith<LlmException> {
            validateReasoningOptions(ReasoningOptions(effort = ReasoningEffortId("max")), resolved.reasoningCapabilities)
        }
    }

    @Test
    fun unknownModelSuffixDoesNotInheritKnownFamilyControls() {
        val model = OpenAiCompatibleCapabilityRules.resolve(connection("deepseek"), LlmResolvedModelInfo("test", "deepseek-v4-pro-future", "Future"))
        assertEquals(CapabilitySupport.UNKNOWN, model.reasoningCapabilities!!.effortSupport)
        assertFailsWith<LlmException> {
            validateReasoningOptions(ReasoningOptions(effort = ReasoningEffortId("high")), model.reasoningCapabilities)
        }
    }

    @Test
    fun alwaysThinkingModelsRejectDisabledAndQwenRejectsEffortWithBudget() {
        val kimi = OpenAiCompatibleCapabilityRules.resolve(connection("moonshot"), LlmResolvedModelInfo("test", "kimi-k3", "Kimi"))
        assertFailsWith<LlmException> { validateReasoningOptions(ReasoningOptions(ReasoningModeId.DISABLED), kimi.reasoningCapabilities) }
        val qwen = OpenAiCompatibleCapabilityRules.resolve(connection("alibaba"), LlmResolvedModelInfo("test", "qwen3.8-max", "Qwen"))
        validateReasoningOptions(ReasoningOptions(budgetTokens = 0), qwen.reasoningCapabilities)
        assertFailsWith<LlmException> { validateReasoningOptions(ReasoningOptions(effort = ReasoningEffortId("low"), budgetTokens = 0), qwen.reasoningCapabilities) }
    }

    private fun connection(vendor: String) = LlmProviderConnection("test", vendor, BuiltinProviderContributions.CHAT, "https://fixture.invalid/v1")
}
