package im.hikaru.harness.client.settings

import im.hikaru.harness.client.connection.ReasoningEffortId
import im.hikaru.harness.client.connection.ReasoningOptions
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReasoningValidationTest {
    private val high = ReasoningEffortId("high")
    private val supported = ModelReasoningCapabilities(
        modes = listOf("enabled", "disabled", "adaptive", "vendor:between-tools"),
        efforts = listOf("low", "high"), budgetSupported = true, minBudget = 0, maxBudget = 8192,
        executionModes = listOf("standard", "pro"),
    )

    @Test
    fun legalCombinedControlsAndZeroBudgetShouldRemainSelectable() {
        assertNull(reasoningOptionsError(ReasoningOptions("disabled", high, 0, "pro"), supported))
        assertNull(reasoningOptionsError(ReasoningOptions("vendor:between-tools", high, 1024), supported))
        assertNotNull(reasoningOptionsError(ReasoningOptions("enabled", high, 1024),
            supported.copy(constraints = listOf(ModelReasoningConstraint.EffortBudgetExclusive))))
    }

    @Test
    fun conditionalModeRulesShouldRejectKnownInvalidCombinationsBeforeSubmission() {
        val capabilities = supported.copy(constraints = listOf(
            ModelReasoningConstraint.ForMode("adaptive", budgetSupport = ModelCapabilitySupport.UNSUPPORTED),
            ModelReasoningConstraint.ForMode("enabled", requiresBudget = true),
            ModelReasoningConstraint.ForMode("vendor:between-tools", allowedEfforts = listOf("low")),
            ModelReasoningConstraint.BudgetLessThanMaxTokens,
        ), defaultMaxTokens = 4096)
        assertNotNull(reasoningOptionsError(ReasoningOptions("adaptive", budgetTokens = 0), capabilities))
        assertNotNull(reasoningOptionsError(ReasoningOptions("enabled"), capabilities))
        assertNotNull(reasoningOptionsError(ReasoningOptions("vendor:between-tools", high), capabilities))
        assertNotNull(reasoningOptionsError(ReasoningOptions("enabled", budgetTokens = 4096), capabilities))
        assertNull(reasoningOptionsError(ReasoningOptions("enabled", budgetTokens = 1024), capabilities))
        assertNull(reasoningOptionsError(ReasoningOptions("vendor:between-tools", ReasoningEffortId("low")), capabilities))
    }

    @Test
    fun executionAndUnknownCapabilitiesShouldNotBeSilentlyAccepted() {
        assertNotNull(reasoningOptionsError(ReasoningOptions(executionModeId = "unknown"), supported))
        assertTrue(reasoningOptionsError(ReasoningOptions(effort = high), ModelReasoningCapabilities()).orEmpty().contains("尚未确认"))
        assertTrue(reasoningOptionsError(ReasoningOptions(effort = high), ModelReasoningCapabilities(effortSupport = ModelCapabilitySupport.UNSUPPORTED)).orEmpty().contains("不支持"))
        assertNull(reasoningOptionsError(ReasoningOptions(), ModelReasoningCapabilities()))
    }
}
