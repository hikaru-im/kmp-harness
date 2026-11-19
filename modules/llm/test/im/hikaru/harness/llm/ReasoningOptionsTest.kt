package im.hikaru.harness.llm

import im.hikaru.harness.runtime.Runtime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReasoningOptionsTest {
    private val high = ReasoningEffortId("high")
    private val capabilities = ReasoningCapabilities(
        modeSupport = CapabilitySupport.SUPPORTED,
        modes = listOf(ReasoningModeId.ENABLED, ReasoningModeId.DISABLED, ReasoningModeId.ADAPTIVE),
        effortSupport = CapabilitySupport.SUPPORTED,
        efforts = listOf(LlmReasoningEffortInfo(high, "High")),
        budgetSupport = CapabilitySupport.SUPPORTED,
        budgetRange = ReasoningBudgetRange(0, 8192),
    )

    @Test
    fun oldHeadersAndNewIntentShouldRoundTripWithoutInventingDefaults() {
        val legacy = Json.decodeFromString<LlmCallConfig>("""{"provider":"p","model":"m","reasoningEffort":"high"}""")
        assertEquals(high, legacy.reasoningEffort)
        assertNull(legacy.reasoning)
        val config = LlmCallConfig("p", "m", reasoning = ReasoningOptions(budgetTokens = 0))
        val restored = Json.decodeFromString<LlmCallConfig>(Json.encodeToString(config))
        assertEquals(config, restored)
        assertEquals(ReasoningModeId.PROVIDER_DEFAULT, restored.reasoning?.mode)
    }

    @Test
    fun conflictingLegacyAndNewEffortsShouldFailWithoutDroppingEither() {
        assertEquals(LlmErrorCode.INVALID_REASONING_OPTIONS, assertFailsWith<LlmException> {
            LlmCallConfig("p", "m", reasoningEffort = high, reasoning = ReasoningOptions())
        }.code)
        LlmCallConfig("p", "m", reasoningEffort = high, reasoning = ReasoningOptions(effort = high))
    }

    @Test
    fun explicitProviderDefaultShouldBypassLegacyDefaultsButNullShouldRetainThem() = runTest {
        val runtime = Runtime()
        runtime.install(LlmPlugin())
        runtime.context.llm.registerAdapter(listOf("p"), modelAdapter())

        val explicit = runtime.context.llm.prepareCall(LlmCallConfig("p", "m", reasoning = ReasoningOptions()))
        assertNull(explicit.config.reasoningEffort)
        assertEquals(ReasoningOptions(), explicit.config.reasoning)
        assertFalse(explicit.adapterDefaults.reasoningEffort)
        explicit.dispose()

        val legacy = runtime.context.llm.prepareCall(LlmCallConfig("p", "m"))
        assertEquals(high, legacy.config.reasoningEffort)
        assertTrue(legacy.adapterDefaults.reasoningEffort)
        legacy.dispose()
        runtime.context.dispose()
    }

    @Test
    fun zeroBudgetAndCombinedEffortShouldOnlyBeConstrainedByModelRules() {
        val combined = ReasoningOptions(ReasoningModeId.DISABLED, effort = high, budgetTokens = 0)
        validateReasoningOptions(combined, capabilities)
        assertEquals(LlmErrorCode.INVALID_REASONING_OPTIONS, assertFailsWith<LlmException> {
            validateReasoningOptions(combined, capabilities.copy(constraints = listOf(ReasoningConstraint.EffortBudgetExclusive)))
        }.code)
        assertEquals(LlmErrorCode.INVALID_REASONING_OPTIONS, assertFailsWith<LlmException> {
            validateReasoningOptions(combined, capabilities.copy(budgetRange = ReasoningBudgetRange(1024)))
        }.code)
    }

    @Test
    fun unknownAndUnsupportedShouldRemainDifferentAndOmittedControlsShouldPass() {
        validateReasoningOptions(ReasoningOptions(), null)
        val requested = ReasoningOptions(effort = high)
        assertEquals(LlmErrorCode.CAPABILITY_UNKNOWN, assertFailsWith<LlmException> {
            validateReasoningOptions(requested, ReasoningCapabilities())
        }.code)
        assertEquals(LlmErrorCode.UNSUPPORTED_REASONING, assertFailsWith<LlmException> {
            validateReasoningOptions(requested, ReasoningCapabilities(effortSupport = CapabilitySupport.UNSUPPORTED))
        }.code)
    }

    @Test
    fun modeSpecificRulesShouldNotBecomeGlobalRestrictions() {
        val constrained = capabilities.copy(constraints = listOf(
            ReasoningConstraint.ForMode(ReasoningModeId.ADAPTIVE, budgetSupport = CapabilitySupport.UNSUPPORTED),
            ReasoningConstraint.ForMode(ReasoningModeId.ENABLED, requiresBudget = true),
            ReasoningConstraint.BudgetLessThanMaxTokens,
        ))
        validateReasoningOptions(ReasoningOptions(ReasoningModeId.ENABLED, budgetTokens = 1024), constrained, 2048)
        assertEquals(LlmErrorCode.UNSUPPORTED_REASONING, assertFailsWith<LlmException> {
            validateReasoningOptions(ReasoningOptions(ReasoningModeId.ADAPTIVE, budgetTokens = 1024), constrained, 2048)
        }.code)
        assertEquals(LlmErrorCode.INVALID_REASONING_OPTIONS, assertFailsWith<LlmException> {
            validateReasoningOptions(ReasoningOptions(ReasoningModeId.ENABLED), constrained, 2048)
        }.code)
        assertEquals(LlmErrorCode.INVALID_REASONING_OPTIONS, assertFailsWith<LlmException> {
            validateReasoningOptions(ReasoningOptions(budgetTokens = 2048), constrained, 2048)
        }.code)
    }

    @Test
    fun providerModesMustBeDeclaredAndCapabilitiesShouldRoundTrip() {
        val mode = ReasoningModeId("anthropic:between_tools")
        val supported = capabilities.copy(modes = capabilities.modes + mode)
        validateReasoningOptions(ReasoningOptions(mode), supported)
        assertEquals(LlmErrorCode.UNSUPPORTED_REASONING, assertFailsWith<LlmException> {
            validateReasoningOptions(ReasoningOptions(ReasoningModeId("unregistered")), supported)
        }.code)
        val encoded = Json.encodeToString(supported.copy(constraints = listOf(ReasoningConstraint.EffortBudgetExclusive)))
        assertEquals(supported.copy(constraints = listOf(ReasoningConstraint.EffortBudgetExclusive)), Json.decodeFromString<ReasoningCapabilities>(encoded))
    }

    @Test
    fun directStreamMustNotBypassExplicitCapabilityValidation() = runTest {
        val runtime = Runtime()
        runtime.install(LlmPlugin())
        var calls = 0
        runtime.context.llm.registerAdapter(listOf("p"), object : LlmAdapter {
            override fun stream(options: GenerateOptions): Flow<StreamChunk> {
                calls++
                return flowOf(FinishChunk(StopFinishReason))
            }
        })
        val result = runtime.context.llm.stream(GenerateOptions("p", "m", emptyList(), reasoning = ReasoningOptions(effort = high))).toList()
        val failure = assertIs<ErrorFinishReason>(assertIs<FinishChunk>(result.single()).reason).failure
        assertEquals(LlmErrorCode.CAPABILITY_UNKNOWN, failure.code)
        assertEquals(0, calls)
        runtime.context.dispose()
    }

    private fun modelAdapter(): LlmAdapter = object : LlmAdapter {
        override suspend fun resolveModel(provider: String, model: String) = LlmResolvedModelInfo(
            provider, model, model,
            reasoning = LlmModelReasoningInfo(listOf(LlmReasoningEffortInfo(high, "High")), high),
            reasoningCapabilities = capabilities,
        )
        override fun stream(options: GenerateOptions): Flow<StreamChunk> = flowOf(FinishChunk(StopFinishReason))
    }
}
