package im.hikaru.harness.session.api

import im.hikaru.harness.agent.*
import im.hikaru.harness.llm.Message
import im.hikaru.harness.llm.ReasoningEffortId
import im.hikaru.harness.llm.TextBlock
import im.hikaru.harness.llm.createUserMessage
import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.Runtime
import im.hikaru.harness.session.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class SessionDescendantsContractTest {
    @Test
    fun onlyVerifiedSubagentChainsInTheSameLiveRegistryAreListed() = runTest {
        val fixture = fixture()
        val foreign = fixture()
        try {
            fixture.create("root")
            val child = fixture.create("child", "root", options = AgentOptions(
                provider = "actual-provider", model = "actual-model", reasoningEffort = ReasoningEffortId("high"),
            ))
            (child.agent as TestAgent).status = AgentStatus.RUNNING
            fixture.create("nested", "child")
            fixture.create("ordinary-fork", "root", origin = null)
            fixture.create("under-ordinary-fork", "ordinary-fork")
            fixture.create("unknown-origin", "root", origin = null)
            fixture.create("missing-parent", "not-live")
            fixture.create("self", "self")
            fixture.create("cycle-a", "cycle-b")
            fixture.create("cycle-b", "cycle-a")
            fixture.create("unrelated")
            foreign.create("root")
            foreign.create("foreign-child", "root")

            val result = assertIs<SessionDescendantsResult.Available>(fixture.api.descendants(id("root")))
            assertEquals(listOf(id("child"), id("nested")), result.children.map { it.child })
            assertEquals(DescendantCoverage.LIVE_REGISTRY, result.coverage)
            assertFalse(result.complete)
            val first = result.children.first()
            assertEquals(listOf(id("root"), id("child")), first.verifiedAncestorPath)
            assertEquals(DescendantStatus.RUNNING, first.status)
            assertEquals("actual-provider", first.provider)
            assertEquals("actual-model", first.model)
            assertEquals("high", first.reasoningEffort)
            assertEquals(child.agent.session.header.createdAt, first.startedAtEpochMillis)
            val nested = result.children.last()
            assertEquals(listOf(id("root"), id("child"), id("nested")), nested.verifiedAncestorPath)
            assertEquals(id("child"), nested.parentSession)
            assertEquals(DescendantStatus.UNKNOWN, nested.status, "IDLE is not Done or Waiting")
            assertNull(nested.model)
            assertNull(nested.objective)

            for (invalid in listOf("root", "ordinary-fork", "under-ordinary-fork", "unknown-origin",
                "missing-parent", "self", "cycle-a", "cycle-b", "unrelated", "foreign-child")) {
                assertFailsWith<IllegalArgumentException>(invalid) {
                    fixture.api.readChildHistory(id("root"), id(invalid))
                }
            }
            assertFailsWith<IllegalStateException> { fixture.api.descendants(id("absent-root")) }
            assertFailsWith<IllegalStateException> {
                fixture.api.readChildHistory(id("absent-root"), id("child"))
            }
            // A cycle involving the requested parent itself cannot authorize a child read.
            assertFailsWith<IllegalArgumentException> {
                fixture.api.readChildHistory(id("cycle-a"), id("cycle-b"))
            }
        } finally {
            foreign.runtime.context.dispose()
            fixture.runtime.context.dispose()
        }
    }

    @Test
    fun genuineEmptyAndRealChildHistoryRemainDistinctFromMissingCapability() = runTest {
        val fixture = fixture()
        try {
            fixture.create("root")
            val empty = assertIs<SessionDescendantsResult.Available>(fixture.api.descendants(id("root")))
            assertTrue(empty.children.isEmpty())
            assertFalse(empty.complete)
            val child = fixture.create("child", "root")
            val message = createUserMessage(listOf(TextBlock("actual child transcript")))
            child.agent.session.append(SessionEventKeys.UserMessage, UserMessageEvent(turn = 1, message = message))
            val history = assertIs<ChildHistoryResult.Available>(fixture.api.readChildHistory(id("root"), id("child")))
            assertEquals(id("root"), history.parent)
            assertEquals(id("child"), history.child)
            assertEquals(listOf(message.copy(timestampMillis = child.agent.session.events().single().time)), history.messages)
            assertEquals(child.agent.session.deriveMessages(), history.messages)
            child.dispose()
            assertTrue(assertIs<SessionDescendantsResult.Available>(fixture.api.descendants(id("root"))).children.isEmpty())
            assertFailsWith<IllegalArgumentException> { fixture.api.readChildHistory(id("root"), id("child")) }
        } finally {
            fixture.runtime.context.dispose()
        }
    }

    @Test
    fun delayedHistoryRejectsReplacementOfParentIntermediateOrChildEvenWithTheSameIds() = runTest {
        for (replaced in listOf("root", "middle", "child")) {
            val fixture = fixture()
            try {
                val handles = mapOf(
                    "root" to fixture.create("root"),
                    "middle" to fixture.create("middle", "root"),
                    "child" to fixture.create("child", "middle"),
                )
                val entered = CompletableDeferred<Unit>()
                val release = CompletableDeferred<Unit>()
                val reader = LiveSessionDescendants(fixture.runtime.context.agents) { session ->
                    val messages = session.deriveMessages()
                    entered.complete(Unit)
                    release.await()
                    messages
                }
                val pending = async {
                    assertFailsWith<IllegalStateException> {
                        reader.readChildHistory(id("root"), id("child"))
                    }
                }
                entered.await()
                handles.getValue(replaced).dispose()
                fixture.create(replaced, when (replaced) { "root" -> null; "middle" -> "root"; else -> "middle" })
                release.complete(Unit)
                pending.await()
            } finally {
                fixture.runtime.context.dispose()
            }
        }
    }

    @Test
    fun delayedHistoryRejectsDisappearingAncestorAndClosedRegisteredSession() = runTest {
        val fixture = fixture()
        try {
            fixture.create("root")
            val middle = fixture.create("middle", "root")
            val child = fixture.create("child", "middle")
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val reader = LiveSessionDescendants(fixture.runtime.context.agents) {
                entered.complete(Unit)
                release.await()
                emptyList()
            }
            val pending = async {
                assertFailsWith<IllegalStateException> { reader.readChildHistory(id("root"), id("child")) }
            }
            entered.await()
            middle.dispose()
            release.complete(Unit)
            pending.await()
            assertTrue(assertIs<SessionDescendantsResult.Available>(fixture.api.descendants(id("root"))).children.isEmpty())
            fixture.create("middle", "root")
            fixture.runtime.context.sessions.attach(child.agent.session).dispose()
            assertFailsWith<IllegalStateException> { fixture.api.readChildHistory(id("root"), id("child")) }
        } finally {
            fixture.runtime.context.dispose()
        }
    }

    @Test
    fun historyFailureAndCancellationAreNotConvertedToUnavailableOrEmpty() = runTest {
        val fixture = fixture()
        try {
            fixture.create("root")
            fixture.create("child", "root")
            val failure = IllegalStateException("history failed")
            val failing = LiveSessionDescendants(fixture.runtime.context.agents) { throw failure }
            assertSame(failure, assertFailsWith<IllegalStateException> { failing.readChildHistory(id("root"), id("child")) })
            val cancelled = CancellationException("read cancelled")
            val cancelling = LiveSessionDescendants(fixture.runtime.context.agents) { throw cancelled }
            assertSame(cancelled, assertFailsWith<CancellationException> { cancelling.readChildHistory(id("root"), id("child")) })
        } finally {
            fixture.runtime.context.dispose()
        }
    }

    private suspend fun fixture(): Fixture {
        val runtime = Runtime()
        runtime.install(SessionPlugin())
        runtime.install(AgentPlugin())
        runtime.context.agents.registerFactory(AgentFactory { request ->
            TestAgent(request.id, request.options, request.session, request.inbox, request.context)
        })
        runtime.install(SessionApiPlugin())
        return Fixture(runtime)
    }

    private class Fixture(val runtime: Runtime) {
        val api: SessionApi get() = runtime.context.sessionApi
        suspend fun create(
            name: String,
            parent: String? = null,
            origin: SessionOrigin? = if (parent == null) null else SessionOrigin.SUBAGENT,
            options: AgentOptions = AgentOptions(),
        ): AgentHandle = runtime.context.agents.create(
            id = AgentId(name), options = options,
            sessionOptions = CreateSessionOptions(parentSession = parent?.let(::SessionId), origin = origin),
        )
    }

    private class TestAgent(
        override val id: AgentId,
        override val options: AgentOptions,
        override val session: Session,
        override val inbox: Inbox,
        override val context: Context,
    ) : Agent {
        override var status = AgentStatus.IDLE
        override suspend fun awaitIdle() = Unit
        override suspend fun cancel(cause: Throwable?, keepInbox: Boolean) = Unit
        override suspend fun send(message: Message, target: AgentSendTarget, wakeup: AgentWakeup): String =
            inbox.insert(message, target, wakeup)
    }

    private fun id(value: String): SessionId = SessionId(value)
}
