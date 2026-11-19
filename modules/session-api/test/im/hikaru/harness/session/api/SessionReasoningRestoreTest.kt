package im.hikaru.harness.session.api

import im.hikaru.harness.agent.AgentId
import im.hikaru.harness.agent.AgentOptions
import im.hikaru.harness.agent.AgentPlugin
import im.hikaru.harness.agent.InboxSnapshot
import im.hikaru.harness.agent.agents
import im.hikaru.harness.agent.loop.AgentLoopPlugin
import im.hikaru.harness.llm.*
import im.hikaru.harness.runtime.Runtime
import im.hikaru.harness.session.*
import im.hikaru.harness.session.persistence.SessionPersistence
import im.hikaru.harness.session.persistence.SessionPersistenceKey
import im.hikaru.harness.session.persistence.SessionQuiescentLoad
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionReasoningRestoreTest {
    private val high = ReasoningEffortId("high")
    private val extendedMode = ReasoningModeId("vendor:between-tools")

    @Test
    fun followupUsesSelectedModelAndRecordsItWithoutChangingPreviousMessages() = runTest {
        val requests = mutableListOf<GenerateOptions>()
        val runtime = configuredRuntime(requests)
        try {
            val api = runtime.context.sessionApi
            val session = api.create(agentOptions = AgentOptions(provider = "scripted", model = "first", reasoning = ReasoningOptions()))
            api.prompt(session.id, createUserMessage(listOf(TextBlock("first")))).awaitIdle()
            val next = api.prompt(session.id, createUserMessage(listOf(TextBlock("second"))),
                AgentOptions(provider = "scripted", model = "second", reasoning = ReasoningOptions(effort = high)))
            next.awaitIdle()
            assertNull(next.failure())
            assertEquals(listOf("first", "second"), requests.map { it.model })
            assertEquals(high, requests.last().reasoning?.effort)
            val header = foldRequestHeader(requireNotNull(runtime.context.sessions.get(session.id)).events())
            assertEquals("second", header?.config?.model)
            assertEquals("second", api.modelConfiguration(session.id)?.model)
            assertEquals(high, api.modelConfiguration(session.id)?.reasoning?.effort)
            // A later prompt without an override continues the latest selected model.
            api.prompt(session.id, createUserMessage(listOf(TextBlock("third")))).awaitIdle()
            assertEquals("second", requests.last().model)
        } finally { runtime.context.dispose() }
    }

    @Test
    fun promptShouldRestoreLegacyEffortFromDurableHeader() = runTest {
        assertRestoreAndFork(AgentOptions(provider = "scripted", model = "test", reasoningEffort = high, maxTokens = 4096))
    }

    @Test
    fun promptAndForkShouldPreserveEveryUnifiedReasoningDimension() = runTest {
        assertRestoreAndFork(AgentOptions(provider = "scripted", model = "test", maxTokens = 4096,
            reasoning = ReasoningOptions(extendedMode, high, 0, "pro")))
    }

    @Test
    fun restoredExplicitProviderDefaultShouldNotRefillTheAdaptersLegacyEffort() = runTest {
        assertRestoreAndFork(AgentOptions(provider = "scripted", model = "test", maxTokens = 4096, reasoning = ReasoningOptions()))
    }

    private suspend fun assertRestoreAndFork(options: AgentOptions) {
        val originalRequests = mutableListOf<GenerateOptions>()
        val original = configuredRuntime(originalRequests)
        val durableHeader: SessionHeader
        val durableEvents: List<SessionEventEnvelope>
        try {
            val api = original.context.sessionApi
            val session = api.create(agentOptions = options)
            val prompt = api.prompt(session.id, createUserMessage(listOf(TextBlock("first"))))
            prompt.awaitIdle()
            assertNull(prompt.failure())
            val live = requireNotNull(original.context.sessions.get(session.id))
            durableHeader = live.header
            durableEvents = live.events()
            val raw = durableEvents.last { it.type == SessionEventNames.REQUEST_HEADER }.data
                .jsonObject.getValue("header").jsonObject.getValue("config").jsonObject
            if (options.reasoning == null) assertTrue("reasoning" !in raw)
            if (options.reasoning == ReasoningOptions()) assertEquals(JsonObject(emptyMap()), raw["reasoning"])
        } finally { original.context.dispose() }

        val restoredRequests = mutableListOf<GenerateOptions>()
        val restored = configuredRuntime(restoredRequests)
        try {
            restored.context.provide(SessionPersistenceKey, object : SessionPersistence {
                override suspend fun loadQuiescent(id: SessionId): SessionQuiescentLoad {
                    assertEquals(durableHeader.id, id)
                    return SessionQuiescentLoad.Loaded(id, restored.context.sessions.load(durableHeader, durableEvents))
                }
                override suspend fun listHeaders(): List<SessionHeader> = listOf(durableHeader)
                override suspend fun readEvents(id: SessionId): List<SessionEventEnvelope> = durableEvents
                override suspend fun flush(session: Session) = Unit
                override suspend fun load(id: SessionId): Session = error("Restoration must use the quiescent path")
                override suspend fun inbox(id: SessionId, agentId: AgentId): InboxSnapshot = error("Unexpected inbox load")
                override suspend fun dispose() = Unit
            })
            val second = createUserMessage(listOf(TextBlock("second")))
            val prompt = restored.context.sessionApi.prompt(durableHeader.id, second)
            prompt.awaitIdle()
            assertNull(prompt.failure())
            val agent = requireNotNull(restored.context.agents.get(AgentId(durableHeader.id.value)))
            assertEquals(options.reasoning, agent.options.reasoning)
            assertEquals(options.reasoningEffort, agent.options.reasoningEffort)
            assertEquals(options.maxTokens, agent.options.maxTokens)
            assertEquals(originalRequests.single().callConfig(), restoredRequests.single().callConfig())

            val fork = restored.context.sessionApi.fork(durableHeader.id, second.id.value)
            val forkAgent = requireNotNull(restored.context.agents.get(AgentId(fork.id.value)))
            assertEquals(options.reasoning, forkAgent.options.reasoning)
            assertEquals(options.reasoningEffort, forkAgent.options.reasoningEffort)
            val forkPrompt = restored.context.sessionApi.prompt(fork.id, createUserMessage(listOf(TextBlock("edited second"))))
            forkPrompt.awaitIdle()
            assertNull(forkPrompt.failure())
            assertEquals(originalRequests.single().callConfig(), restoredRequests.last().callConfig())
        } finally { restored.context.dispose() }
    }

    private suspend fun configuredRuntime(requests: MutableList<GenerateOptions>): Runtime = Runtime().also { runtime ->
        runtime.install(SessionPlugin())
        runtime.install(LlmPlugin())
        runtime.install(AgentPlugin())
        runtime.context.llm.registerAdapter(listOf("scripted"), object : LlmAdapter {
            override suspend fun resolveModel(provider: String, model: String) = LlmResolvedModelInfo(
                provider, model, model,
                reasoning = LlmModelReasoningInfo(listOf(LlmReasoningEffortInfo(high, "High")), high),
                reasoningCapabilities = ReasoningCapabilities(
                    modeSupport = CapabilitySupport.SUPPORTED, modes = listOf(extendedMode),
                    effortSupport = CapabilitySupport.SUPPORTED, efforts = listOf(LlmReasoningEffortInfo(high, "High")),
                    budgetSupport = CapabilitySupport.SUPPORTED, budgetRange = ReasoningBudgetRange(0, 8192),
                    executionModeSupport = CapabilitySupport.SUPPORTED, executionModes = listOf("pro"),
                ),
            )
            override fun stream(options: GenerateOptions): Flow<StreamChunk> {
                requests += options
                return flowOf(BlockStartChunk(0, "text"), TextDeltaChunk(0, "ok"), FinishChunk(StopFinishReason))
            }
        })
        runtime.install(AgentLoopPlugin())
        runtime.install(SessionApiPlugin())
    }
}
