package im.hikaru.harness.desktop.scheduling

import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.harness.agent.*
import im.hikaru.harness.agent.loop.AgentLoopPlugin
import im.hikaru.harness.api.gateway.ApiGateway
import im.hikaru.harness.api.gateway.session.registerSessionApi
import im.hikaru.harness.client.account.BackendTenant
import im.hikaru.harness.client.account.MemberIdentity
import im.hikaru.harness.client.connection.SessionId as ClientSessionId
import im.hikaru.harness.client.scheduling.*
import im.hikaru.harness.desktop.connection.LocalConnection
import im.hikaru.harness.llm.*
import im.hikaru.harness.runtime.Runtime
import im.hikaru.harness.session.*
import im.hikaru.harness.session.persistence.*
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlin.test.*

/** Actual runtime, SessionApi gateway, inbox and Room; only the provider stream is controlled. */
class LocalScheduledSessionAccessIntegrationTest {
    private val host = HostId("scheduled-local-test")
    private val scope = ScheduledScope(host, null, 1)

    @Test fun candidateCreationFlushesHeaderBeforeReturningWithoutPrompting() = runBlocking {
        temporary { root ->
            val fixture = fixture(root)
            val run = run(root)
            try {
                assertNull(fixture.access.unavailableReason(scope, run.task))
                assertEquals(run.candidateSessionId, fixture.access.create(scope, run))
                val agent = assertNotNull(fixture.runtime.context.agents.get(AgentId(run.candidateSessionId.value)))
                assertEquals(root.toString(), agent.options.cwd)
                assertEquals(root.toString(), agent.session.header.cwd)
                assertEquals("scripted", agent.options.provider)
                assertEquals("test", agent.options.model)
                // A read from the actual durable provider requires the header row to exist already.
                assertTrue(fixture.runtime.context.persistence.inbox(SessionId(run.candidateSessionId.value)).nextTurn.isEmpty())
                assertFalse(fixture.adapter.started.isCompleted)
                assertEquals(ScheduledEvidenceResult.Pending, fixture.access.inspect(scope, run))
            } finally { fixture.close() }
            val reader = reader(root)
            try {
                val restored = reader.context.persistence.load(SessionId(run.candidateSessionId.value))
                assertEquals(root.toString(), restored.header.cwd)
                assertTrue(restored.events().isEmpty())
            } finally { reader.context.dispose() }
        }
    }

    @Test fun realPromptReceiptIsInboxIdentityAndOnlyFlushedExactTurnCompletes() = runBlocking {
        temporary { root ->
            val fixture = fixture(root)
            var run = run(root)
            lateinit var exact: ScheduledTerminalEvidence
            try {
                val created = fixture.access.create(scope, run)
                run = run.copy(sessionRef = created, phase = ScheduledRunPhase.Dispatching, deliveryIntent = true)
                val receipt = fixture.access.prompt(scope, run)
                assertEquals(created, receipt.sessionId)
                assertNotEquals(run.inputMessageId, receipt.messageId)
                run = run.copy(phase = ScheduledRunPhase.Accepted, receiptId = receipt.messageId)
                val options = withTimeout(10_000) { fixture.adapter.started.await() }
                assertEquals("scripted", options.provider)
                assertEquals("test", options.model)
                val agent = assertNotNull(fixture.runtime.context.agents.get(AgentId(created.value)))
                val inserts = agent.session.events().filter { it.type == AgentSessionEventNames.INBOX_SPLICED }
                    .map { Json.decodeFromJsonElement(InboxSpliceEvent.serializer(), it.data) }
                    .filter { it.operation == InboxSpliceOperation.INSERT }.flatMap { it.items }
                val item = inserts.single { it.id == receipt.messageId }
                assertEquals(run.inputMessageId, item.message.id.value)
                assertNotEquals(item.id, item.message.id.value)
                assertEquals(ScheduledEvidenceResult.Pending, fixture.access.inspect(scope, run))
                fixture.adapter.finish.complete(Unit)
                // Synchronization only: scheduler completion is asserted from the persisted typed log below.
                withTimeout(10_000) { agent.awaitIdle() }
                exact = assertIs<ScheduledEvidenceResult.Terminal>(fixture.access.inspect(scope, run)).evidence
                assertEquals(ScheduledRunPhase.Succeeded, exact.outcome)
                val events = agent.session.events()
                assertEquals(SessionEventNames.USER_MESSAGE, events[exact.userMessageSeq.toInt()].type)
                assertEquals(run.inputMessageId, Json.decodeFromJsonElement(UserMessageEvent.serializer(), events[exact.userMessageSeq.toInt()].data).message.id.value)
                assertEquals(SessionEventNames.TURN_END, events[exact.turnEndSeq.toInt()].type)
                assertEquals(ScheduledEvidenceResult.Pending, fixture.access.inspect(scope, run.copy(inputMessageId = receipt.messageId)))
            } finally { fixture.close() }
            val reader = reader(root)
            try {
                val evidence = DesktopScheduledSessionEvidence(reader.context, host)
                assertEquals(exact, assertIs<ScheduledEvidenceResult.Terminal>(evidence.inspect(scope, run)).evidence)
                assertTrue(reader.context.agents.list().isEmpty(), "Reading evidence must not restore a running agent")
            } finally { reader.context.dispose() }
        }
    }

    @Test fun actualProviderFailureProducesFailedEvidenceAndNeverSuccess() = runBlocking {
        temporary { root ->
            val fixture = fixture(root, failed = true)
            try {
                var run = run(root)
                run = run.copy(sessionRef = fixture.access.create(scope, run), deliveryIntent = true)
                fixture.access.prompt(scope, run)
                withTimeout(10_000) { fixture.adapter.started.await() }
                assertEquals(ScheduledEvidenceResult.Pending, fixture.access.inspect(scope, run))
                fixture.adapter.finish.complete(Unit)
                val agent = assertNotNull(fixture.runtime.context.agents.get(AgentId(run.candidateSessionId.value)))
                withTimeout(10_000) { agent.awaitIdle() }
                val terminal = assertIs<ScheduledEvidenceResult.Terminal>(fixture.access.inspect(scope, run)).evidence
                assertEquals(ScheduledRunPhase.Failed, terminal.outcome)
                assertEquals("TEST_FAILURE", terminal.failureCode)
            } finally { fixture.close() }
        }
    }

    @Test fun exactInMemoryEvidenceCannotCompleteWhenActualPersistenceFlushFails() = runBlocking {
        temporary { root ->
            val fixture = fixture(root)
            try {
                var run = run(root)
                run = run.copy(sessionRef = fixture.access.create(scope, run), deliveryIntent = true)
                val session = assertNotNull(fixture.runtime.context.sessions.get(SessionId(run.candidateSessionId.value)))
                session.append(SessionEventKeys.UserMessage, UserMessageEvent(1,
                    createUserMessage(listOf(TextBlock(run.task.prompt))).copy(id = MessageId(run.inputMessageId))))
                session.append(SessionEventKeys.TurnEnd, TurnEndEvent(1, CompletedTurnEndReason))
                assertNotNull(DesktopScheduledSessionEvidence.terminalEvidence(session.events(), run.inputMessageId))
                fixture.runtime.context.persistence.dispose()
                assertIs<ScheduledEvidenceResult.Unavailable>(fixture.access.inspect(scope, run))
                assertFailsWith<IllegalStateException> { fixture.evidence.flushCreated(scope, run.candidateSessionId) }
                assertIs<ScheduledEvidenceResult.Unavailable>(fixture.access.inspect(scope, run.copy(sessionRef = ClientSessionId("missing"))))
            } finally { fixture.close() }
        }
    }

    @Test fun localScopeModelAndCwdAdmissionUseActualServices() = runBlocking {
        temporary { root ->
            val fixture = fixture(root)
            try {
                val run = run(root)
                assertNull(fixture.evidence.unavailableReason(scope, null))
                assertNull(fixture.access.unavailableReason(scope, run.task))
                assertIs<ScheduledEvidenceResult.Unavailable>(fixture.access.inspect(scope,
                    run.copy(sessionRef = ClientSessionId("missing"))))
                val invalidScopes = listOf(scope.copy(hostId = HostId("remote")), scope.copy(memberIdentity =
                    MemberIdentity(BackendTenant("https://example.test", 1), 7)))
                invalidScopes.forEach { foreign ->
                    assertNotNull(fixture.evidence.unavailableReason(foreign, null))
                    assertNotNull(fixture.evidence.unavailableReason(foreign, run.task))
                    assertFailsWith<IllegalStateException> { fixture.evidence.flushCreated(foreign, run.candidateSessionId) }
                    assertIs<ScheduledEvidenceResult.Unavailable>(fixture.evidence.inspect(foreign, run))
                }
                assertNotNull(fixture.access.unavailableReason(scope, run.task.copy(model = null)))
                assertNotNull(fixture.access.unavailableReason(scope, run.task.copy(model = ScheduledModelRef("unregistered", "test"))))
                assertNotNull(fixture.access.unavailableReason(scope, run.task.copy(model = ScheduledModelRef("scripted", "unknown"))))
                val file = Files.writeString(root.resolve("not-a-directory"), "fixture")
                listOf("relative/path", root.resolve("absent").toString(), root.resolve("unused/..").toString(), file.toString()).forEach { cwd ->
                    assertNotNull(fixture.access.unavailableReason(scope, run.task.copy(target = ScheduledTarget.Cron(ScheduledDestination.Cwd(cwd)))))
                }
                assertFalse(fixture.adapter.started.isCompleted)
            } finally { fixture.close() }
            val incomplete = Runtime()
            try {
                assertNotNull(DesktopScheduledSessionEvidence(incomplete.context, host).unavailableReason(scope, null))
            } finally { incomplete.context.dispose() }
        }
    }

    @Test fun directForeignHostAndMemberCreateAndPromptHaveZeroActualSessionSideEffects() = runBlocking {
        temporary { root ->
            val fixture = fixture(root)
            try {
                val candidate = run(root)
                val invalidScopes = listOf(scope.copy(hostId = HostId("remote")), scope.copy(memberIdentity =
                    MemberIdentity(BackendTenant("https://example.test", 1), 7)))
                for (foreign in invalidScopes) {
                    assertFailsWith<IllegalStateException> { fixture.access.create(foreign, candidate) }
                    assertNull(fixture.runtime.context.sessions.get(SessionId(candidate.candidateSessionId.value)))
                    assertTrue(fixture.runtime.context.agents.list().isEmpty())
                    assertFalse(fixture.adapter.started.isCompleted)
                }
                val created = fixture.access.create(scope, candidate)
                val sessionId = SessionId(created.value)
                val session = assertNotNull(fixture.runtime.context.sessions.get(sessionId))
                val eventsBefore = session.events()
                val inboxBefore = fixture.runtime.context.persistence.inbox(sessionId)
                val dispatching = candidate.copy(sessionRef = created, deliveryIntent = true, phase = ScheduledRunPhase.Dispatching)
                for (foreign in invalidScopes) {
                    assertFailsWith<IllegalStateException> { fixture.access.prompt(foreign, dispatching) }
                    assertEquals(eventsBefore, session.events())
                    assertEquals(inboxBefore, fixture.runtime.context.persistence.inbox(sessionId))
                    assertFalse(fixture.adapter.started.isCompleted)
                }
                assertEquals(1, fixture.runtime.context.agents.list().size)
            } finally { fixture.close() }
        }
    }

    private fun run(root: Path): ScheduledRun {
        val task = ScheduledTask("task", 1, "scheduled", "local controlled prompt",
            ScheduledTarget.Cron(ScheduledDestination.Cwd(root.toString())), ScheduledRule.Daily(9, 0, "UTC"),
            ScheduledModelRef("scripted", "test"))
        return ScheduledRun("run", task, "request", null, 1_000, ClientSessionId("scheduled-candidate"), "scheduled-input")
    }

    private suspend fun fixture(root: Path, failed: Boolean = false): Fixture {
        val runtime = Runtime()
        try {
            runtime.install(SessionPlugin())
            runtime.install(LlmPlugin())
            runtime.install(AgentPlugin())
            val adapter = ControlledAdapter(failed)
            runtime.context.llm.registerAdapter(listOf("scripted"), adapter)
            runtime.install(AgentLoopPlugin())
            runtime.install(sessionPersistencePluginForTest(root.resolve("sessions.db").toString()))
            runtime.install(im.hikaru.harness.session.api.SessionApiPlugin())
            val gateway = ApiGateway()
            gateway.registerSessionApi(runtime.context)
            val evidence = DesktopScheduledSessionEvidence(runtime.context, host)
            return Fixture(runtime, adapter, evidence, LocalScheduledSessionAccess(LocalConnection(gateway).session, evidence))
        } catch (error: Throwable) { runtime.context.dispose(); throw error }
    }

    private suspend fun reader(root: Path): Runtime = Runtime().also {
        it.install(SessionPlugin())
        it.install(AgentPlugin())
        it.install(sessionPersistencePluginForTest(root.resolve("sessions.db").toString()))
    }

    private class Fixture(val runtime: Runtime, val adapter: ControlledAdapter,
        val evidence: DesktopScheduledSessionEvidence, val access: LocalScheduledSessionAccess) {
        suspend fun close() { adapter.finish.complete(Unit); runtime.context.dispose() }
    }

    private class ControlledAdapter(private val failed: Boolean) : LlmAdapter {
        val started = CompletableDeferred<GenerateOptions>()
        val finish = CompletableDeferred<Unit>()
        override suspend fun resolveModel(provider: String, model: String): LlmResolvedModelInfo {
            require(provider == "scripted" && model == "test") { "Unknown fixture model" }
            return LlmResolvedModelInfo(provider, model, "Controlled local model")
        }
        override fun stream(options: GenerateOptions): Flow<StreamChunk> = flow {
            emit(BlockStartChunk(0, "text"))
            emit(TextDeltaChunk(0, "controlled local answer"))
            started.complete(options)
            finish.await()
            emit(FinishChunk(if (failed) ErrorFinishReason(LlmFailure("controlled failure", "TEST_FAILURE")) else StopFinishReason))
        }
    }

    private suspend fun temporary(block: suspend (Path) -> Unit) {
        val directory = Files.createTempDirectory("harness-scheduled-session-").toAbsolutePath().normalize()
        try { block(directory) }
        finally { directory.toFile().deleteRecursively() }
    }
}
