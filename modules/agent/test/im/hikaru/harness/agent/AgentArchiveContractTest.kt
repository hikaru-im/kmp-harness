package im.hikaru.harness.agent

import im.hikaru.harness.llm.Message
import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.Runtime
import im.hikaru.harness.session.SessionAdmissionException
import im.hikaru.harness.session.SessionAdmissionRejection
import im.hikaru.harness.session.SessionClock
import im.hikaru.harness.session.SessionEventKeys
import im.hikaru.harness.session.SessionId
import im.hikaru.harness.session.SessionPlugin
import im.hikaru.harness.session.SessionReservationBusyReason
import im.hikaru.harness.session.SessionStore
import im.hikaru.harness.session.TurnStartEvent
import im.hikaru.harness.session.sessions
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.*

class AgentArchiveContractTest {
    @Test
    fun archiveStopsAgentAndDetachesSessionAndReleasesTheGate() = runTest { withFixture { fixture ->
        val handle = fixture.registry.create(AgentId("chat"))
        val session = handle.agent.session
        val begin = assertIs<AgentArchiveBegin.Granted>(fixture.registry.beginArchive(AgentId("chat")))
        try {
            // While the archive owns the Session, no new durable event can start.
            val error = assertFailsWith<SessionAdmissionException> {
                session.append(SessionEventKeys.TurnStart, TurnStartEvent(1L))
            }
            assertEquals(SessionAdmissionRejection.CLOSING, error.reason)
            assertNull(begin.preparation.commit())
        } finally {
            begin.preparation.dispose()
        }
        assertTrue(fixture.registry.list().isEmpty())
        assertNull(fixture.store.get(SessionId("chat")))
        assertFalse(session.isLive())
        // The gate is released, so the same id can be created again as a new identity.
        val again = fixture.registry.create(AgentId("chat"))
        assertEquals("chat", again.agent.id.value)
        again.dispose()
        assertNull(fixture.store.get(SessionId("chat")))
    } }

    @Test
    fun archiveIsBusyInsteadOfQueueingWhileActivityIsInFlight() = runTest { withFixture { fixture ->
        val handle = fixture.registry.create(AgentId("busy"))
        val permit = handle.agent.session.acquireOperationPermit()
        try {
            assertEquals(
                AgentArchiveBegin.Busy(SessionReservationBusyReason.TARGET_ACTIVITY),
                fixture.registry.beginArchive(AgentId("busy")),
            )
        } finally {
            permit.dispose()
        }
        assertSame(handle.agent.session, fixture.store.get(SessionId("busy")))
    } }

    @Test
    fun disposingAnArchivePreparationWithoutCommitKeepsTheAgent() = runTest { withFixture { fixture ->
        val handle = fixture.registry.create(AgentId("keep"))
        val begin = assertIs<AgentArchiveBegin.Granted>(fixture.registry.beginArchive(AgentId("keep")))
        begin.preparation.dispose()
        assertEquals(listOf("keep"), fixture.registry.list().map { it.id.value })
        assertSame(handle.agent.session, fixture.store.get(SessionId("keep")))
        // After release the closed gate no longer blocks new events on the untouched Agent.
        handle.agent.session.append(SessionEventKeys.TurnStart, TurnStartEvent(1L))
    } }

    @Test
    fun archiveOfUnknownAgentIsNotRegistered() = runTest { withFixture { fixture ->
        assertEquals(AgentArchiveBegin.NotRegistered, fixture.registry.beginArchive(AgentId("missing")))
    } }

    private suspend fun withFixture(block: suspend (Fixture) -> Unit) {
        val runtime = Runtime()
        try {
            runtime.install(SessionPlugin(clock = SessionClock { 100L }))
            runtime.install(AgentPlugin())
            val registry = runtime.context.agents
            registry.registerFactory(AgentFactory { request -> ArchiveAgent(request) })
            block(Fixture(runtime.context, registry))
        } finally {
            withContext(NonCancellable) { runtime.context.dispose() }
        }
    }

    private class Fixture(val context: Context, val registry: AgentRegistry) {
        val store: SessionStore get() = context.sessions
    }

    private class ArchiveAgent(request: AgentFactoryRequest) : Agent {
        override val id = request.id
        override val options = request.options
        override val session = request.session
        override val inbox = request.inbox
        override val context = request.context
        override val status = AgentStatus.IDLE
        override suspend fun awaitIdle() = Unit
        override suspend fun cancel(cause: Throwable?, keepInbox: Boolean) = Unit
        override suspend fun send(message: Message, target: AgentSendTarget, wakeup: AgentWakeup): String = ""
    }
}
