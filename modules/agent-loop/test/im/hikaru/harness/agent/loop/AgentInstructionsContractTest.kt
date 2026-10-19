package im.hikaru.harness.agent.loop

import im.hikaru.harness.agent.Agent
import im.hikaru.harness.agent.AgentEvents
import im.hikaru.harness.agent.AgentOptions
import im.hikaru.harness.agent.AgentPlugin
import im.hikaru.harness.agent.AgentRequestErrorDecision
import im.hikaru.harness.agent.AgentStatus
import im.hikaru.harness.agent.agents
import im.hikaru.harness.agent.instructions.AgentInstructionsPlugin
import im.hikaru.harness.agent.instructions.AgentInstructionsService
import im.hikaru.harness.llm.BlockStartChunk
import im.hikaru.harness.llm.ErrorFinishReason
import im.hikaru.harness.llm.FinishChunk
import im.hikaru.harness.llm.GenerateOptions
import im.hikaru.harness.llm.LlmAdapter
import im.hikaru.harness.llm.LlmFailure
import im.hikaru.harness.llm.LlmPlugin
import im.hikaru.harness.llm.LlmSessionId
import im.hikaru.harness.llm.StopFinishReason
import im.hikaru.harness.llm.StreamChunk
import im.hikaru.harness.llm.TextBlock
import im.hikaru.harness.llm.TextDeltaChunk
import im.hikaru.harness.llm.createUserMessage
import im.hikaru.harness.llm.llm
import im.hikaru.harness.runtime.Runtime
import im.hikaru.harness.session.AbortedTurnEndReason
import im.hikaru.harness.session.CompletedTurnEndReason
import im.hikaru.harness.session.EpochReason
import im.hikaru.harness.session.ErrorTurnEndReason
import im.hikaru.harness.session.RequestHeaderEvent
import im.hikaru.harness.session.SessionEventNames
import im.hikaru.harness.session.SessionPlugin
import im.hikaru.harness.session.TurnEndEvent
import im.hikaru.harness.session.TurnEndReason
import im.hikaru.harness.session.UserTurnAbortCause
import im.hikaru.harness.system.prompt.SystemPromptConfig
import im.hikaru.harness.system.prompt.SystemPromptPlugin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AgentInstructionsContractTest {
    @Test
    fun enabledInstructionsPreserveBaseAndReachBothRequestAndHeader() = runTest {
        val readers = mutableListOf<Agent>()
        val contribution = "  Skill instructions\nKeep this formatting.  "
        withFixture(service = AgentInstructionsService { agent ->
            readers += agent
            contribution
        }) { fixture ->
            var observedSystem: String? = null
            fixture.agent.context.on(AgentEvents.Request) { event, next ->
                observedSystem = event.options.system
                next()
            }
            fixture.request()

            val expected = BASE_SYSTEM + "\n\n" + contribution
            assertSame(fixture.agent, readers.single())
            assertEquals(expected, fixture.requests.single().system)
            assertEquals(expected, observedSystem)
            assertEquals(expected, fixture.headers().single().header.system)
            assertEquals(CompletedTurnEndReason, fixture.reason())
        }
    }

    @Test
    fun absentDisabledAndBlankInstructionsPreserveOriginalRequest() = runTest {
        val services = listOf(
            null,
            AgentInstructionsService { null },
            AgentInstructionsService { "" },
            AgentInstructionsService { " \n\t " },
        )
        for (base in listOf(BASE_SYSTEM, null)) {
            for (service in services) {
                withFixture(base = base, service = service) { fixture ->
                    fixture.request()
                    assertEquals(
                        GenerateOptions(
                            provider = "scripted",
                            model = "test",
                            messages = fixture.agent.session.deriveMessages().take(1),
                            system = base,
                            sessionId = LlmSessionId(fixture.agent.session.id.value),
                        ),
                        fixture.requests.single(),
                    )
                    assertEquals(base, fixture.headers().single().header.system)
                    assertEquals(CompletedTurnEndReason, fixture.reason())
                }
            }
        }
    }

    @Test
    fun instructionsWithoutBaseRemainUnmodified() = runTest {
        val contribution = "  Standalone skill.\n"
        withFixture(base = null, service = AgentInstructionsService { contribution }) { fixture ->
            fixture.request()
            assertEquals(contribution, fixture.requests.single().system)
            assertEquals(contribution, fixture.headers().single().header.system)
        }
    }

    @Test
    fun retryReusesStepAssemblyAndEachNextStepReadsFreshSnapshot() = runTest {
        var snapshot: String? = "Skill v1"
        val readers = mutableListOf<Agent>()
        withFixture(
            service = AgentInstructionsService { agent ->
                readers += agent
                snapshot
            },
            response = { call ->
                if (call == 1) {
                    flowOf(FinishChunk(ErrorFinishReason(LlmFailure(message = "Try again", code = "SERVER"))))
                } else {
                    successStream()
                }
            },
        ) { fixture ->
            val observedSystems = mutableListOf<String?>()
            val attempts = mutableListOf<Int>()
            fixture.agent.context.on(AgentEvents.Request) { event, next ->
                observedSystems += event.options.system
                attempts += event.attempt
                if (observedSystems.size == 1) snapshot = "Changed after request snapshot"
                next()
            }
            fixture.agent.context.on(AgentEvents.RequestError) { event, next ->
                if (event.attempt == 1) {
                    snapshot = "Skill v2"
                    AgentRequestErrorDecision.Retry
                } else {
                    next()
                }
            }
            fixture.request()
            snapshot = "Skill v3"
            fixture.request("next request")
            snapshot = null
            fixture.request("disabled")

            // DSH assembles the prompt once per step: the retry keeps Skill v1,
            // while the next step observes Skill v3 and the disabled snapshot.
            val expected: List<String?> = listOf(
                BASE_SYSTEM + "\n\nSkill v1",
                BASE_SYSTEM + "\n\nSkill v1",
                BASE_SYSTEM + "\n\nSkill v3",
                BASE_SYSTEM,
            )
            assertEquals(expected, fixture.requests.map { it.system })
            assertEquals(expected, observedSystems)
            val headers = fixture.headers()
            assertEquals(expected, headers.map { it.header.system })
            assertEquals(
                listOf(EpochReason.INITIAL, EpochReason.CHANGE, EpochReason.INITIAL, EpochReason.INITIAL),
                headers.map { it.header.reason },
            )
            assertEquals(listOf(1, 2, 1, 1), attempts)
            assertEquals(3, readers.size)
            readers.forEach { assertSame(fixture.agent, it) }
            assertEquals(CompletedTurnEndReason, fixture.reason())
        }
    }

    @Test
    fun sourceFailureEndsTurnHonestlyWithoutStartingLlmStream() = runTest {
        var reads = 0
        withFixture(service = AgentInstructionsService {
            reads += 1
            error("Missing or corrupt skill source")
        }) { fixture ->
            fixture.request()
            assertEquals(1, reads)
            assertEquals("Missing or corrupt skill source", assertIs<ErrorTurnEndReason>(fixture.reason()).failure.message)
            fixture.assertNoRequestOutput()
        }
    }

    @Test
    fun sourceCancellationIsAnAbortedTurnWithoutFabricatedSuccess() = runTest {
        withFixture(service = AgentInstructionsService { throw CancellationException("Skill read cancelled") }) { fixture ->
            fixture.request()
            assertEquals(AbortedTurnEndReason(UserTurnAbortCause), fixture.reason())
            fixture.assertNoRequestOutput()
        }
    }

    @Test
    fun cancellingSuspendedReadPropagatesCancellationAndNeverStartsStream() = runTest {
        val started = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        withFixture(service = AgentInstructionsService {
            started.complete(Unit)
            try {
                awaitCancellation()
            } finally {
                cancelled.complete(Unit)
            }
        }) { fixture ->
            fixture.agent.followup(createUserMessage(listOf(TextBlock("hi"))))
            started.await()
            fixture.agent.cancel()
            fixture.agent.awaitIdle()
            assertTrue(cancelled.isCompleted)
            assertEquals(AbortedTurnEndReason(UserTurnAbortCause), fixture.reason())
            fixture.assertNoRequestOutput()
        }
    }

    private suspend fun withFixture(
        base: String? = BASE_SYSTEM,
        service: AgentInstructionsService? = null,
        response: (Int) -> Flow<StreamChunk> = { successStream() },
        block: suspend (Fixture) -> Unit,
    ) {
        val runtime = Runtime()
        try {
            runtime.install(SessionPlugin())
            runtime.install(LlmPlugin())
            runtime.install(AgentPlugin())
            runtime.install(
                SystemPromptPlugin(
                    SystemPromptConfig(
                        includeHarnessIdentity = false,
                        personaPrefix = base ?: "",
                    ),
                ),
            )
            if (service != null) runtime.install(AgentInstructionsPlugin(service))
            val requests = mutableListOf<GenerateOptions>()
            runtime.context.llm.registerAdapter(
                providers = listOf("scripted"),
                adapter = object : LlmAdapter {
                    override fun stream(options: GenerateOptions): Flow<StreamChunk> {
                        requests += options
                        return response(requests.size)
                    }
                },
            )
            runtime.install(AgentLoopPlugin())
            val handle = runtime.context.agents.create(options = AgentOptions(provider = "scripted", model = "test"))
            block(Fixture(handle.agent, requests))
        } finally {
            runtime.context.dispose()
        }
    }

    private class Fixture(val agent: Agent, val requests: List<GenerateOptions>) {
        suspend fun request(text: String = "hi") {
            agent.followup(createUserMessage(listOf(TextBlock(text))))
            agent.awaitIdle()
        }

        suspend fun headers(): List<RequestHeaderEvent> = agent.session.events()
            .filter { it.type == SessionEventNames.REQUEST_HEADER }
            .map { Json.decodeFromJsonElement(RequestHeaderEvent.serializer(), it.data) }

        suspend fun reason(): TurnEndReason = Json.decodeFromJsonElement(
            TurnEndEvent.serializer(),
            agent.session.events().last { it.type == SessionEventNames.TURN_END }.data,
        ).reason

        suspend fun assertNoRequestOutput() {
            assertTrue(requests.isEmpty())
            val names = agent.session.events().map { it.type }
            assertTrue(names.none {
                it == SessionEventNames.REQUEST_HEADER ||
                    it == SessionEventNames.ASSISTANT_CHUNK || it == SessionEventNames.ASSISTANT_MESSAGE
            })
            assertEquals(1, names.count { it == SessionEventNames.TURN_END })
            assertEquals(AgentStatus.IDLE, agent.status)
        }
    }

    private fun successStream(): Flow<StreamChunk> = flowOf(
        BlockStartChunk(0, "text"),
        TextDeltaChunk(0, "done"),
        FinishChunk(StopFinishReason),
    )

    private companion object {
        const val BASE_SYSTEM = "  Base instructions.\n"
    }
}
