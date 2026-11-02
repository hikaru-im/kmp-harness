package im.hikaru.harness.client.app

import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.harness.client.connection.SessionId
import im.hikaru.harness.client.conversation.*
import im.hikaru.harness.client.connection.ReasoningEffortId
import im.hikaru.harness.client.connection.ReasoningOptions
import im.hikaru.harness.client.settings.ModelReasoningCapabilities
import im.hikaru.harness.client.settings.ModelReasoningConstraint
import kotlin.test.*

class ComposerPresentationTest {
    @Test
    fun singleActionTracksActualCancellationCapability() {
        val idle = ConversationState(host = ConversationHostScope(HostId("host"), 1, null), connected = true,
            selectedSessionId = SessionId("session"), runState = ConversationRunState.Idle)
        assertEquals(ComposerPrimaryAction.Send, idle.composerPrimaryAction())
        assertEquals(ComposerPrimaryAction.Busy, idle.copy(action = ConversationAction.Sending).composerPrimaryAction())
        val running = idle.copy(action = ConversationAction.WaitingForLocalIdle, runState = ConversationRunState.Running)
        assertEquals(ComposerPrimaryAction.Stop, running.composerPrimaryAction())
        assertEquals(ComposerPrimaryAction.Stopping, running.copy(cancelling = true).composerPrimaryAction())
        assertEquals(ComposerPrimaryAction.Busy, running.copy(connected = false).composerPrimaryAction())
        // Stop follows the Host's authoritative running flag; an unknown status never offers Stop.
        assertEquals(ComposerPrimaryAction.Send, idle.copy(runState = ConversationRunState.Unknown).composerPrimaryAction())
    }

    @Test
    fun createdSessionConfigurationSurvivesEmptyHistoryWithoutBorrowingAnotherModel() {
        val model = ConversationModelChoice("provider", "actual", "Actual", listOf("low", "high"))
        val preferences = ComposerSessionPreferences(model = model, effort = "high", modelConfigurationKnown = true)
        assertEquals(model, composerModel(ConversationState(), emptyList(), preferences))
        assertNull(composerModel(ConversationState(), listOf(model), null))
    }

    @Test
    fun staleCapabilitiesShouldResetTheWholeDisplayedAndSubmittedIntent() {
        val previous = ConversationModelChoice("p", "m", "Model", listOf("high"), ModelReasoningCapabilities(
            modes = listOf("enabled"), efforts = listOf("high"), budgetSupported = true,
            executionModes = listOf("pro"), ruleVersion = "1",
        ))
        val requested = ReasoningOptions("enabled", ReasoningEffortId("high"), 1024, "pro")
        assertEquals(requested, resolveComposerReasoning(previous, requested))
        val refreshed = previous.copy(reasoningEfforts = listOf("low"), reasoningCapabilities =
            previous.reasoningCapabilities!!.copy(efforts = listOf("low"), ruleVersion = "2"))
        val effective = resolveComposerReasoning(refreshed, requested)
        assertEquals(ReasoningOptions(), effective)
        assertNull(effective.effort)
        assertNull(effective.budgetTokens)
        assertNull(effective.executionModeId)
        assertEquals(ReasoningOptions(), resolveComposerReasoning(null, requested))
    }

    @Test
    fun effortShortcutShouldHonorCombinationConstraintsWithoutClearingOtherControls() {
        val model = ConversationModelChoice("p", "m", "Model", listOf("low", "high"), ModelReasoningCapabilities(
            efforts = listOf("low", "high"), budgetSupported = true,
            constraints = listOf(ModelReasoningConstraint.EffortBudgetExclusive),
        ))
        assertTrue(model.allowedReasoningEfforts(ReasoningOptions(budgetTokens = 0)).isEmpty())
        assertEquals(listOf("low", "high"), model.allowedReasoningEfforts(ReasoningOptions()))
    }

    @Test
    fun legacyEffortOnlyCatalogShouldContinueToProduceUnifiedIntent() {
        val model = ConversationModelChoice("p", "m", "Model", listOf("low", "high"))
        val requested = ReasoningOptions(effort = ReasoningEffortId("high"))
        assertEquals(requested, resolveComposerReasoning(model, requested))
        assertEquals(listOf("low", "high"), model.allowedReasoningEfforts(requested))
    }
}
