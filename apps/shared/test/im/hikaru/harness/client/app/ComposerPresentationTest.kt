package im.hikaru.harness.client.app

import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.harness.client.connection.SessionId
import im.hikaru.harness.client.conversation.*
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
}
