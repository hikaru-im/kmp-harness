package im.hikaru.harness.agent

import im.hikaru.harness.llm.Message
import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.Runtime
import im.hikaru.harness.session.SessionClock
import im.hikaru.harness.session.SessionId
import im.hikaru.harness.session.SessionPlugin
import im.hikaru.harness.session.SessionStore
import im.hikaru.harness.session.sessions
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.*

class AgentQuiescentRestoreContractTest {
    @Test
    fun undeclaredFactoryIsRejectedForQuiescentRestoreButStillRestoresNormally() = runTest { withFixture(declared = false) { f ->
        val handle = f.store.create(SessionId("chat"))
        val rejected = assertIs<AgentQuiescentRestore.Rejected>(f.registry.restoreQuiescent(handle.session))
        assertTrue(rejected.reason.contains("未声明"), rejected.reason)
        assertTrue(f.registry.list().isEmpty())
        // The ordinary restore path is unchanged and still attaches an undeclared factory.
        val restored = f.registry.restore(handle.session)
        assertEquals("chat", restored.agent.id.value)
        restored.dispose()
        handle.dispose()
    } }

    @Test
    fun declaredFactoryIsGrantedWithoutAutonomousWorkOrNewEvents() = runTest { withFixture(declared = true) { f ->
        val handle = f.store.create(SessionId("quiet"))
        val before = handle.session.events()
        val granted = assertIs<AgentQuiescentRestore.Granted>(f.registry.restoreQuiescent(handle.session))
        assertEquals("quiet", granted.handle.agent.id.value)
        assertEquals(1, f.factory.created)
        // Attaching a declared factory issues no model work and appends no Session event.
        granted.handle.agent.awaitIdle()
        assertEquals(before, granted.handle.agent.session.events())
        assertEquals(0, f.factory.sends)
        granted.handle.dispose()
        handle.dispose()
    } }

    private suspend fun withFixture(declared: Boolean, block: suspend (Fixture) -> Unit) {
        val runtime = Runtime()
        try {
            runtime.install(SessionPlugin(clock = SessionClock { 100L }))
            runtime.install(AgentPlugin())
            val registry = runtime.context.agents
            val factory = QuietFactory()
            if (declared) registry.registerFactory(factory)
            else registry.registerFactory(AgentFactory { request -> QuietAgent(request) })
            block(Fixture(runtime.context, registry, factory))
        } finally {
            withContext(NonCancellable) { runtime.context.dispose() }
        }
    }

    private class Fixture(val context: Context, val registry: AgentRegistry, val factory: QuietFactory) {
        val store: SessionStore get() = context.sessions
    }

    /** Declares quiescent-restorable support and never starts work on its own. */
    private class QuietFactory : QuiescentRestorableFactory {
        var created = 0
        var sends = 0

        override suspend fun create(request: AgentFactoryRequest): Agent {
            created += 1
            return QuietAgent(request) { sends += 1 }
        }
    }

    private class QuietAgent(request: AgentFactoryRequest, private val onSend: () -> Unit = {}) : Agent {
        override val id = request.id
        override val options = request.options
        override val session = request.session
        override val inbox = request.inbox
        override val context = request.context
        override val status = AgentStatus.IDLE
        override suspend fun awaitIdle() = Unit
        override suspend fun cancel(cause: Throwable?, keepInbox: Boolean) = Unit
        override suspend fun send(message: Message, target: AgentSendTarget, wakeup: AgentWakeup): String {
            onSend()
            return ""
        }
    }
}
