package im.hikaru.harness.agent

import im.hikaru.harness.llm.ContentBlock
import im.hikaru.harness.llm.TextBlock
import im.hikaru.harness.llm.createUserMessage
import im.hikaru.harness.runtime.Runtime
import im.hikaru.harness.runtime.plugin.plugin
import im.hikaru.harness.session.Session
import im.hikaru.harness.session.SessionId
import im.hikaru.harness.session.SessionPlugin
import im.hikaru.harness.session.SessionStore
import im.hikaru.harness.session.sessions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AgentContractTest {
    @Test
    fun registryRequiresFactoryAndSupportsCreateGetListAndDispose() = runTest {
        val runtime = Runtime()
        runtime.install(SessionPlugin())
        runtime.install(AgentPlugin())
        val error =
            assertFailsWith<AgentException> {
                runtime.context.agents.create()
            }
        assertEquals(AgentErrorCode.NO_FACTORY, error.code)

        val factory =
            runtime.context.agents.registerFactory(
                AgentFactory { request ->
                    TestAgent(request.id, request.options, request.session, request.inbox, request.context)
                }
            )
        val first = runtime.context.agents.create(id = AgentId("first"))
        val second = runtime.context.agents.create(id = AgentId("second"))
        assertEquals(listOf(AgentId("first"), AgentId("second")), runtime.context.agents.list().map(Agent::id))
        assertEquals(first.agent, runtime.context.agents.get(AgentId("first")))
        assertFailsWith<AgentException> { runtime.context.agents.create(id = AgentId("first")) }
        first.dispose()
        assertNotNull(runtime.context.agents.get(AgentId("second")))
        second.dispose()
        factory.dispose()
        assertFailsWith<AgentException> { runtime.context.agents.create() }
        runtime.context.dispose()
    }

    @Test
    fun replacingFactoryIsAtomicAndOldHandleCannotRemoveNewFactory() = runTest {
        val runtime = Runtime()
        runtime.install(SessionPlugin())
        runtime.install(AgentPlugin())
        val first = runtime.context.agents.registerFactory(AgentFactory { request ->
            TestAgent(request.id, request.options, request.session, request.inbox, request.context)
        })
        val second = runtime.context.agents.replaceFactory(AgentFactory { request ->
            TestAgent(request.id, request.options, request.session, request.inbox, request.context)
        })
        first.dispose()
        val handle = runtime.context.agents.create(id = AgentId("replacement"))
        assertEquals(AgentId("replacement"), handle.agent.id)
        second.dispose()
        handle.dispose()
        runtime.context.dispose()
    }

    @Test
    fun oldHandleCannotDetachReplacementAndInboxReplayMatchesLiveState() = runTest {
        val runtime = Runtime()
        runtime.install(SessionPlugin())
        runtime.install(AgentPlugin())
        runtime.context.agents.registerFactory(
            AgentFactory { request ->
                TestAgent(request.id, request.options, request.session, request.inbox, request.context)
            }
        )
        val old = runtime.context.agents.create(id = AgentId("same"))
        old.dispose()
        val replacement = runtime.context.agents.create(id = AgentId("same"))
        old.dispose()
        assertEquals(replacement.agent, runtime.context.agents.get(AgentId("same")))

        val message = createUserMessage(listOf(TextBlock("hello")))
        replacement.agent.followup(message)
        replacement.agent.steer(createUserMessage(listOf(TextBlock("steer"))))
        val live = replacement.agent.inbox.snapshot()
        val replayed = Inbox.replay(replacement.agent.session.events(), replacement.agent.id)
        assertEquals(live, replayed)
        replacement.dispose()
        assertNull(runtime.context.agents.get(AgentId("same")))
        runtime.context.dispose()
    }

    @Test
    fun restoringAgentOwnsLoadedSessionUntilHandleDispose() = runTest {
        val runtime = Runtime()
        runtime.install(SessionPlugin())
        runtime.install(AgentPlugin())
        runtime.context.agents.registerFactory(
            AgentFactory { request ->
                TestAgent(request.id, request.options, request.session, request.inbox, request.context)
            }
        )
        val store = runtime.context.sessions
        val prepared = store.prepare(SessionId("restored"))
        val originalHandle = store.enter(prepared)
        originalHandle.dispose()
        val loaded = store.load(prepared.header, prepared.events()).session

        val restored = runtime.context.agents.restore(loaded)
        assertEquals(loaded, runtime.context.sessions.get(SessionId("restored")))
        restored.dispose()
        assertNull(runtime.context.sessions.get(SessionId("restored")))
        runtime.context.dispose()
    }

    @Test
    fun failedFactoryDoesNotPublishOrLeaveSession() = runTest {
        val runtime = Runtime()
        runtime.install(SessionPlugin())
        runtime.install(AgentPlugin())
        var created = 0
        runtime.context.on(AgentEvents.Created) { created += 1 }
        runtime.context.agents.registerFactory(
            AgentFactory { throw IllegalArgumentException("boom") }
        )
        assertFailsWith<IllegalArgumentException> {
            runtime.context.agents.create(id = AgentId("broken"))
        }
        assertEquals(0, created)
        assertTrue(runtime.context.requireSessionStore().list().isEmpty())
        runtime.context.dispose()
    }

    @Test
    fun inboxOperationsPublishTypedEventsAfterDurableCommit() = runTest {
        val runtime = Runtime()
        runtime.install(SessionPlugin())
        runtime.install(AgentPlugin())
        runtime.context.agents.registerFactory(
            AgentFactory { request ->
                TestAgent(request.id, request.options, request.session, request.inbox, request.context)
            }
        )
        val inserted = mutableListOf<String>()
        val claimed = mutableListOf<String>()
        val discarded = mutableListOf<String>()
        runtime.context.on(AgentEvents.InboxInserted) { inserted += it.agent.id.value }
        runtime.context.on(AgentEvents.InboxClaimed) { claimed += it.agent.id.value }
        runtime.context.on(AgentEvents.InboxDiscarded) { discarded += it.agent.id.value }
        val handle = runtime.context.agents.create(id = AgentId("events"))
        handle.agent.followup(createUserMessage(listOf(TextBlock("one"))))
        handle.agent.steer(createUserMessage(listOf(TextBlock("step"))))
        handle.agent.inbox.claim(InboxQueue.NEXT_TURN)
        handle.agent.inbox.clear(InboxQueue.NEXT_STEP)
        assertEquals(listOf("events", "events"), inserted)
        assertEquals(listOf("events"), claimed)
        assertEquals(listOf("events"), discarded)
        handle.dispose()
        runtime.context.dispose()
    }

    private fun im.hikaru.harness.runtime.Context.requireSessionStore(): SessionStore = sessions

    private class TestAgent(
        override val id: AgentId,
        override val options: AgentOptions,
        override val session: Session,
        override val inbox: Inbox,
        override val context: im.hikaru.harness.runtime.Context,
    ) : Agent {
        override val status: AgentStatus = AgentStatus.IDLE
        override suspend fun awaitIdle() = Unit
        override suspend fun cancel(cause: Throwable?, keepInbox: Boolean) {
            if (!keepInbox) {
                inbox.clear(InboxQueue.NEXT_TURN)
                inbox.clear(InboxQueue.NEXT_STEP)
            }
        }
        override suspend fun send(
            message: im.hikaru.harness.llm.Message,
            target: AgentSendTarget,
            wakeup: AgentWakeup,
        ): String = inbox.insert(message, target, wakeup)
    }
}
