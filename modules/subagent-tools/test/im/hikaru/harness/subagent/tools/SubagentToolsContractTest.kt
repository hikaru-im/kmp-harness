package im.hikaru.harness.subagent.tools

import im.hikaru.harness.agent.*
import im.hikaru.harness.llm.CallId
import im.hikaru.harness.llm.Message
import im.hikaru.harness.llm.TextBlock
import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.Runtime
import im.hikaru.harness.runtime.effect.EffectScope
import im.hikaru.harness.session.*
import im.hikaru.harness.session.api.*
import im.hikaru.harness.tools.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.*

class SubagentToolsContractTest {
    @Test
    fun realDispatchCreatesVerifiedLineageExplicitModelAndRealInboxReceipt() = runTest {
        val fixture = fixture()
        try {
            val parent = fixture.create("parent", cwd = "/actual/parent")
            val result = decode(fixture.execute(parent))
            assertEquals(SpawnAgentOutcome.ACCEPTED, result.outcome)
            assertEquals(SpawnAgentDelivery.ACCEPTED, result.delivery)
            assertFalse(result.completionVerified)
            val child = assertNotNull(fixture.registry.get(AgentId(assertNotNull(result.childSessionId))))
            assertEquals(SessionOrigin.SUBAGENT, child.session.header.origin)
            assertEquals(parent.agent.session.id, child.session.header.parentSession)
            assertEquals("/actual/parent", child.session.header.cwd)
            assertEquals("/actual/parent", child.options.cwd)
            assertEquals("selected-provider", child.options.provider)
            assertEquals("selected-model", child.options.model)
            val inbox = child.inbox.snapshot().nextTurn.single()
            assertEquals(result.receiptId, inbox.id)
            assertEquals(result.inputMessageId, inbox.message.id.value)
            assertNotEquals(result.receiptId, result.inputMessageId)
            assertEquals("actual task", (inbox.message.content.single() as TextBlock).text)
            assertEquals(AgentSendTarget.NEXT_TURN, inbox.target)
            assertEquals(AgentWakeup.IF_IDLE, inbox.wakeup)
            assertEquals(0, (child as ControlledAgent).awaitIdleCalls)

            val descendants = assertIs<SessionDescendantsResult.Available>(fixture.api.descendants(parent.agent.session.id))
            assertEquals(listOf(child.session.id), descendants.children.map { it.child })
            assertEquals(DescendantStatus.UNKNOWN, descendants.children.single().status)
            assertFalse(descendants.complete)
            // The consumer commits the actual queued input into the actual Session log.
            child.session.append(SessionEventKeys.UserMessage, UserMessageEvent(turn = 1, message = inbox.message))
            val history = assertIs<ChildHistoryResult.Available>(
                fixture.api.readChildHistory(parent.agent.session.id, child.session.id),
            )
            assertEquals(listOf(inbox.message), history.messages)
            assertEquals(SUBAGENT_TOOLS_PLUGIN_NAME, SubagentToolsPluginDefinition.name)
            assertEquals(listOf(SPAWN_AGENT_TOOL_NAME), fixture.tools.schemas().map { it.name })
            fixture.scope.dispose()
            assertNull(fixture.registry.get(child.id))
            assertNull(fixture.runtime.context.sessions.get(child.session.id))
            assertSame(parent.agent, fixture.registry.get(parent.agent.id))
            assertTrue(fixture.tools.schemas().isEmpty())
        } finally {
            fixture.close()
        }
    }

    @Test
    fun nestedSpawnIsReadableButOrdinaryBranchIsNotAnAgentDescendant() = runTest {
        val fixture = fixture()
        try {
            val root = fixture.create("root")
            val branch = fixture.create("branch", parent = "root", origin = null)
            val first = decode(fixture.execute(root, "first"))
            val child = assertNotNull(fixture.registry.get(AgentId(assertNotNull(first.childSessionId))))
            val second = decode(fixture.tools.execute(request("nested", child.session.id.value), execution(child)))
            val nested = assertNotNull(second.childSessionId)
            val listed = assertIs<SessionDescendantsResult.Available>(fixture.api.descendants(root.agent.session.id))
            assertEquals(setOf(first.childSessionId, nested), listed.children.map { it.child.value }.toSet())
            assertFalse(listed.children.any { it.child == branch.agent.session.id })
            val branchChild = decode(fixture.execute(branch, "branch-child"))
            assertEquals(SpawnAgentOutcome.ACCEPTED, branchChild.outcome, "An ordinary branch can delegate")
            assertEquals(2, assertIs<SessionDescendantsResult.Available>(
                fixture.api.descendants(root.agent.session.id),
            ).children.size, "The ordinary branch edge must not be reclassified")
        } finally {
            fixture.close()
        }
    }

    @Test
    fun untrustedSingleArgumentScopeForgedParentAndImplicitModelAreRejectedBeforeCreate() = runTest {
        val fixture = fixture()
        try {
            val parent = fixture.create("parent")
            assertFailsWith<IllegalStateException> { fixture.tools.execute(request("no-authority", "parent")) }
            val arguments = listOf(
                """{"prompt":"task","provider":"p","model":"m","parent":"parent"}""",
                """{"prompt":"task","provider":"p","model":"m","cwd":"/forged"}""",
                """{"prompt":"task","model":"m"}""",
                """{"prompt":"task","provider":"p","model":""}""",
                """{"prompt":"task","provider":12,"model":"m"}""",
            )
            for ((index, value) in arguments.withIndex()) {
                val response = fixture.tools.execute(request("invalid-$index", "parent").copy(arguments = value), execution(parent.agent))
                assertTrue(response.isError)
                assertEquals(SpawnAgentOutcome.REJECTED, decode(response).outcome)
            }
            assertEquals(SpawnAgentOutcome.REJECTED, decode(fixture.tools.execute(
                request("wrong-scope", "someone-else"), execution(parent.agent),
            )).outcome)
            assertEquals(listOf(parent.agent), fixture.registry.list())
            assertEquals(1, fixture.created.size)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun foreignRegistryForeignStoreAndWrongContextCannotAuthorizeParent() = runTest {
        val fixture = fixture()
        val foreign = fixture()
        try {
            val parent = fixture.create("same-id")
            val other = foreign.create("same-id")
            assertEquals(SpawnAgentOutcome.REJECTED, decode(fixture.tools.execute(
                request("foreign", "same-id"), execution(other.agent),
            )).outcome)
            assertEquals(SpawnAgentOutcome.REJECTED, decode(fixture.tools.execute(
                request("wrong-session", "same-id"), ToolExecutionContext(parent.agent.context, other.agent.session),
            )).outcome)
            assertEquals(SpawnAgentOutcome.REJECTED, decode(fixture.tools.execute(
                request("wrong-context", "same-id"), ToolExecutionContext(fixture.runtime.context, parent.agent.session),
            )).outcome)
            val overlay = parent.agent.context.isolate(SessionKey)
            overlay.provide(SessionKey, foreign.runtime.context.sessions)
            assertEquals(SpawnAgentOutcome.REJECTED, decode(fixture.tools.execute(
                request("foreign-store", "same-id"), ToolExecutionContext(overlay, parent.agent.session),
            )).outcome)
            assertEquals(1, fixture.created.size)
            overlay.dispose()
        } finally {
            foreign.close()
            fixture.close()
        }
    }

    @Test
    fun invalidSelfCycleMissingAncestorAndSubagentWithoutParentCannotSpawn() = runTest {
        val fixture = fixture()
        try {
            val self = fixture.create("self", "self")
            val a = fixture.create("a", "b")
            fixture.create("b", "a")
            val missing = fixture.create("missing", "absent")
            val orphan = fixture.create("orphan", origin = SessionOrigin.SUBAGENT)
            for (handle in listOf(self, a, missing, orphan)) {
                assertEquals(SpawnAgentOutcome.REJECTED, decode(fixture.execute(handle, handle.agent.id.value)).outcome)
            }
            assertEquals(5, fixture.created.size)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun delayedCreateRejectsParentOrAncestorReplacementAndRollsBackOnlyOwnedChild() = runTest {
        for (replacedId in listOf("parent", "ancestor")) {
            val fixture = fixture()
            try {
                val ancestor = fixture.create("ancestor")
                val parent = fixture.create("parent", "ancestor")
                val entered = CompletableDeferred<Unit>()
                val release = CompletableDeferred<Unit>()
                fixture.beforeCreate = { created ->
                    if (created.session.header.origin == SessionOrigin.SUBAGENT && created.id.value !in setOf("parent", "ancestor")) {
                        entered.complete(Unit)
                        release.await()
                    }
                }
                val pending = async { fixture.execute(parent) }
                entered.await()
                (if (replacedId == "parent") parent else ancestor).dispose()
                val replacement = fixture.create(replacedId, if (replacedId == "parent") "ancestor" else null,
                    origin = if (replacedId == "parent") SessionOrigin.SUBAGENT else null)
                release.complete(Unit)
                val response = decode(pending.await())
                assertEquals(SpawnAgentOutcome.ROLLED_BACK, response.outcome)
                assertEquals(SpawnAgentDelivery.NOT_ATTEMPTED, response.delivery)
                val childId = assertNotNull(response.childSessionId)
                assertNull(fixture.registry.get(AgentId(childId)))
                assertNull(fixture.runtime.context.sessions.get(SessionId(childId)))
                assertSame(replacement.agent, fixture.registry.get(AgentId(replacedId)))
                assertEquals(2, fixture.registry.list().size)
            } finally {
                fixture.close()
            }
        }
    }

    @Test
    fun pluginDisposedDuringCreateRejectsLateChildAndDoesNotPrompt() = runTest {
        val fixture = fixture()
        try {
            val parent = fixture.create("parent")
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            fixture.beforeCreate = { created ->
                if (created.session.header.origin == SessionOrigin.SUBAGENT) {
                    entered.complete(Unit)
                    release.await()
                }
            }
            val pending = async { fixture.execute(parent) }
            entered.await()
            fixture.scope.dispose()
            release.complete(Unit)
            val result = decode(pending.await())
            assertEquals(SpawnAgentOutcome.ROLLED_BACK, result.outcome)
            assertNull(fixture.registry.get(AgentId(assertNotNull(result.childSessionId))))
            assertEquals(0, fixture.created.last().sendCalls)
            assertSame(parent.agent, fixture.registry.list().single())
        } finally {
            fixture.close()
        }
    }

    @Test
    fun promptFailurePreservesChildAndReturnsItsIdWithoutAnAutomaticSecondCreate() = runTest {
        val fixture = fixture()
        try {
            val parent = fixture.create("parent")
            fixture.beforeSend = { _, _ -> error("controlled prompt failure") }
            val response = fixture.execute(parent, "prompt-failed")
            assertTrue(response.isError)
            val result = decode(response)
            assertEquals(SpawnAgentOutcome.CREATED_PROMPT_FAILED, result.outcome)
            assertEquals(SpawnAgentDelivery.UNKNOWN, result.delivery)
            assertContains(assertNotNull(result.error), "controlled prompt failure")
            val child = assertNotNull(fixture.registry.get(AgentId(assertNotNull(result.childSessionId))))
            assertTrue(child.inbox.snapshot().nextTurn.isEmpty())
            assertNull(result.receiptId)
            assertNotNull(result.inputMessageId)
            assertFailsWith<IllegalStateException> { fixture.execute(parent, "prompt-failed") }
            assertEquals(2, fixture.created.size)
            assertEquals(1, (child as ControlledAgent).sendCalls)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun failureAfterInboxCommitReportsUnknownDeliveryAndKeepsActualInput() = runTest {
        val fixture = fixture()
        try {
            val parent = fixture.create("parent")
            fixture.afterSend = { _, _ -> error("failed after acceptance") }
            val result = decode(fixture.execute(parent))
            assertEquals(SpawnAgentOutcome.CREATED_PROMPT_FAILED, result.outcome)
            assertEquals(SpawnAgentDelivery.UNKNOWN, result.delivery)
            val child = assertNotNull(fixture.registry.get(AgentId(assertNotNull(result.childSessionId))))
            assertEquals(result.inputMessageId, child.inbox.snapshot().nextTurn.single().message.id.value)
            assertNull(result.receiptId)
            assertFalse(result.completionVerified)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun cancellationDuringDeliveryKeepsIdAndRepeatedCallCannotCreateAnotherChild() = runTest {
        val fixture = fixture()
        try {
            val parent = fixture.create("parent")
            val entered = CompletableDeferred<Unit>()
            val never = CompletableDeferred<Unit>()
            fixture.afterSend = { _, _ -> entered.complete(Unit); never.await() }
            val pending = async { fixture.execute(parent, "cancelled-delivery") }
            entered.await()
            pending.cancel()
            assertFailsWith<CancellationException> { pending.await() }
            val retry = decode(fixture.execute(parent, "cancelled-delivery"))
            assertEquals(SpawnAgentOutcome.CREATED_DELIVERY_UNKNOWN, retry.outcome)
            assertEquals(SpawnAgentDelivery.UNKNOWN, retry.delivery)
            val child = assertNotNull(fixture.registry.get(AgentId(assertNotNull(retry.childSessionId))))
            assertEquals(retry.inputMessageId, child.inbox.snapshot().nextTurn.single().message.id.value)
            assertEquals(2, fixture.created.size)
            assertEquals(1, (child as ControlledAgent).sendCalls)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun parentChangesDuringFollowupReturnsAcceptedReceiptAndExplicitRollback() = runTest {
        val fixture = fixture()
        try {
            val parent = fixture.create("parent")
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            fixture.afterSend = { _, _ -> entered.complete(Unit); release.await() }
            val pending = async { fixture.execute(parent) }
            entered.await()
            parent.dispose()
            val replacement = fixture.create("parent")
            release.complete(Unit)
            val result = decode(pending.await())
            assertEquals(SpawnAgentOutcome.ROLLED_BACK, result.outcome)
            assertEquals(SpawnAgentDelivery.ACCEPTED, result.delivery, "Do not relabel accepted input as unsent")
            assertNotNull(result.receiptId)
            assertNull(fixture.registry.get(AgentId(assertNotNull(result.childSessionId))))
            assertSame(replacement.agent, fixture.registry.list().single())
        } finally {
            fixture.close()
        }
    }

    @Test
    fun rollbackOfOldOwnedHandleCannotDeleteSameIdChildReplacement() = runTest {
        val fixture = fixture()
        try {
            val parent = fixture.create("parent")
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            fixture.afterSend = { _, _ -> entered.complete(Unit); release.await() }
            val pending = async { fixture.execute(parent) }
            entered.await()
            val original = fixture.created.last()
            fixture.scope.dispose()
            assertNull(fixture.registry.get(original.id))
            val replacement = fixture.create(original.id.value)
            release.complete(Unit)
            val result = decode(pending.await())
            assertEquals(SpawnAgentOutcome.ROLLED_BACK, result.outcome)
            assertEquals(SpawnAgentDelivery.ACCEPTED, result.delivery)
            assertSame(replacement.agent, fixture.registry.get(original.id))
            assertSame(replacement.agent.session, fixture.runtime.context.sessions.get(original.session.id))
            assertSame(parent.agent, fixture.registry.get(parent.agent.id))
        } finally {
            fixture.close()
        }
    }

    @Test
    fun scopeIsOptionalButConcurrentRepeatedCallCannotCreateAnotherChild() = runTest {
        val fixture = fixture()
        try {
            val parent = fixture.create("parent")
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            fixture.beforeCreate = { created ->
                if (created.session.header.origin == SessionOrigin.SUBAGENT) {
                    entered.complete(Unit)
                    release.await()
                }
            }
            val call = request("concurrent", null)
            val pending = async { fixture.tools.execute(call, execution(parent.agent)) }
            entered.await()
            assertFailsWith<IllegalStateException> { fixture.tools.execute(call, execution(parent.agent)) }
            release.complete(Unit)
            assertEquals(SpawnAgentOutcome.ACCEPTED, decode(pending.await()).outcome)
            assertEquals(2, fixture.created.size)
            assertEquals(1, fixture.created.last().sendCalls)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun factoryFailureIsCreateFailureAndRollbackFailureIsExplicit() = runTest {
        val fixture = fixture()
        try {
            val parent = fixture.create("parent")
            fixture.beforeCreate = { error("factory unavailable") }
            val failed = decode(fixture.execute(parent, "factory-failed"))
            assertEquals(SpawnAgentOutcome.CREATE_FAILED, failed.outcome)
            assertNull(failed.childSessionId)
            assertEquals(1, fixture.runtime.context.sessions.list().size)
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            fixture.beforeCreate = { created ->
                if (created.id.value != "parent") { entered.complete(Unit); release.await() }
            }
            fixture.cancelFailure = true
            val pending = async { fixture.execute(parent, "rollback-failed") }
            entered.await()
            parent.dispose() // This parent was constructed before cancelFailure was enabled.
            val replacement = fixture.create("parent")
            release.complete(Unit)
            val result = decode(pending.await())
            assertEquals(SpawnAgentOutcome.ROLLBACK_FAILED, result.outcome)
            assertContains(assertNotNull(result.rollbackError), "controlled cancel failure")
            assertSame(replacement.agent, fixture.registry.get(AgentId("parent")))
            fixture.cancelFailure = false
            (replacement.agent as ControlledAgent).failCancel = false
        } finally {
            fixture.close()
        }
    }

    @Test
    fun hostDisposeReleasesAcceptedChildrenWithoutWaitingForIdle() = runTest {
        val fixture = fixture()
        val parent = fixture.create("parent")
        val result = decode(fixture.execute(parent))
        val child = assertNotNull(fixture.registry.get(AgentId(assertNotNull(result.childSessionId)))) as ControlledAgent
        fixture.close()
        assertTrue(child.context.isDisposed)
        assertFalse(child.session.isLive())
        assertEquals(0, child.awaitIdleCalls)
    }

    private suspend fun fixture(): Fixture {
        val runtime = Runtime()
        runtime.install(SessionPlugin())
        runtime.install(AgentPlugin())
        runtime.install(ToolsPlugin())
        runtime.install(SessionApiPlugin())
        val fixture = Fixture(runtime)
        fixture.registry.registerFactory(AgentFactory { request ->
            fixture.beforeCreate(request)
            ControlledAgent(request, fixture).also { fixture.created += it }
        })
        SubagentToolsPlugin().apply(runtime.context, fixture.scope)
        runtime.context.effect { fixture.scope.dispose() }
        return fixture
    }

    private class Fixture(val runtime: Runtime) {
        val registry get() = runtime.context.agents
        val tools get() = runtime.context.require(ToolsKey)
        val api get() = runtime.context.sessionApi
        val scope = EffectScope()
        val created = mutableListOf<ControlledAgent>()
        var beforeCreate: suspend (AgentFactoryRequest) -> Unit = {}
        var beforeSend: suspend (ControlledAgent, Message) -> Unit = { _, _ -> }
        var afterSend: suspend (ControlledAgent, String) -> Unit = { _, _ -> }
        var cancelFailure = false

        suspend fun create(
            id: String, parent: String? = null,
            origin: SessionOrigin? = if (parent == null) null else SessionOrigin.SUBAGENT,
            cwd: String? = null,
        ): AgentHandle = registry.create(
            id = AgentId(id), options = AgentOptions(cwd = cwd),
            sessionOptions = CreateSessionOptions(cwd = cwd, parentSession = parent?.let(::SessionId), origin = origin),
        )

        suspend fun execute(parent: AgentHandle, call: String = "spawn"): ToolExecutionResult = tools.execute(
            request(call, parent.agent.session.id.value), execution(parent.agent),
        )

        suspend fun close() { runtime.context.dispose() }
    }

    private class ControlledAgent(request: AgentFactoryRequest, private val fixture: Fixture) : Agent {
        override val id = request.id
        override val options = request.options
        override val session = request.session
        override val context: Context = request.context
        override val inbox = request.inbox
        override val status = AgentStatus.IDLE
        var awaitIdleCalls = 0
        var sendCalls = 0
        var failCancel = fixture.cancelFailure
        override suspend fun awaitIdle() { awaitIdleCalls++ }
        override suspend fun cancel(cause: Throwable?, keepInbox: Boolean) {
            if (failCancel) error("controlled cancel failure")
        }
        override suspend fun send(message: Message, target: AgentSendTarget, wakeup: AgentWakeup): String {
            sendCalls++
            fixture.beforeSend(this, message)
            val receipt = inbox.insert(message, target, wakeup)
            fixture.afterSend(this, receipt)
            return receipt
        }
    }

    companion object {
        private fun execution(agent: Agent) = ToolExecutionContext(agent.context, agent.session)
        private fun request(call: String, scope: String?) = ToolCallRequest(
            callId = CallId(call), name = SPAWN_AGENT_TOOL_NAME, scope = scope,
            arguments = """{"prompt":"actual task","provider":"selected-provider","model":"selected-model"}""",
        )
        private fun decode(result: ToolExecutionResult): SpawnAgentResult = Json.decodeFromString(
            SpawnAgentResult.serializer(), (result.content.single() as TextBlock).text,
        )
    }
}
