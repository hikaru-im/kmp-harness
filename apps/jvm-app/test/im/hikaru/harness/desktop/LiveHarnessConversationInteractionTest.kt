package im.hikaru.harness.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import im.hikaru.contracts.common.ApiResult
import im.hikaru.contracts.harness.identity.HostDescription
import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.contracts.harness.protocol.ProtocolVersion
import im.hikaru.harness.client.app.HarnessApp
import im.hikaru.harness.client.connection.*
import im.hikaru.harness.client.settings.*
import im.hikaru.harness.ui.foundation.HarnessThemeMode
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.test.*

/** Production app owner and routes; only Connection and model backend are test doubles. */
@OptIn(ExperimentalTestApi::class)
class LiveHarnessConversationInteractionTest {
    @Test
    fun subagentPanelReadsVerifiedHistoryRefreshesAndClearsWhenParentChanges() =
        runSkikoComposeUiTest(size = Size(1440f, 900f)) {
            val parent = SessionId("panel-parent")
            val other = SessionId("other-parent")
            val child = SessionId("verified-child")
            val api = LiveTestSessionApi(listOf(liveSummary(parent), liveSummary(other)))
            api.descendantResults[parent] = SessionDescendantsResult.Available(parent, listOf(
                SessionDescendant(child, parent, listOf(parent, child), DescendantStatus.UNKNOWN)))
            api.descendantResults[other] = SessionDescendantsResult.Available(other, emptyList())
            api.childResults[parent to child] = ChildHistoryResult.Available(parent, child, listOf(parent, child),
                listOf(liveAssistant("Verified child history")))
            val connection = LiveTestConnection(api)
            val models = liveTestModels()
            setContent { HarnessApp(connection, modelSettingsRepository = models) }
            onNodeWithTag("recent-${parent.value}").performClick()
            onNodeWithTag("tab:Subagents").assertDoesNotExist()
            onNodeWithText("子 Agent").performClick()
            onNodeWithText(child.value).performClick()
            onNodeWithText("Verified child history").assertExists()
            onNodeWithText("只读对话记录").assertExists()
            onNodeWithText("刷新历史").performClick()
            runOnIdle {
                assertEquals(listOf(parent to child, parent to child), api.childReads.toList())
                assertFalse(child in api.historyReads, "Child panel must use lineage checked history")
            }
            onNodeWithContentDescription("返回子智能体").performClick()
            onNodeWithText("刷新子智能体").performClick()
            runOnIdle { assertEquals(listOf(parent, parent), api.descendantReads.toList()) }
            onNodeWithTag("recent-${other.value}").performClick()
            onNodeWithTag("tab:Subagents").assertDoesNotExist()
            onNodeWithText("Verified child history").assertDoesNotExist()
            onNodeWithText("子 Agent").performClick()
            onNodeWithText("没有可读取的 live 子智能体").assertExists()
            onNodeWithText(child.value).assertDoesNotExist()
            runOnIdle { assertTrue(api.creates.isEmpty()); assertTrue(api.prompts.isEmpty()); assertTrue(api.cancels.isEmpty()) }
        }

    @Test
    fun homeCreatesWithCatalogModelThenPromptsTheReturnedIdAndEntersThread() =
        runSkikoComposeUiTest(size = Size(1440f, 900f)) {
            val api = LiveTestSessionApi()
            val idle = CompletableDeferred<Unit>()
            api.idleGate = idle
            val connection = LiveTestConnection(api)
            val models = liveTestModels()
            try {
                setContent { HarnessApp(connection, modelSettingsRepository = models, themeMode = HarnessThemeMode.Light) }
                onNodeWithTag("live-home").assertExists()
                onNodeWithTag("live-home-host").assertTextEquals(connection.description.displayName)
                onNodeWithTag("live-composer-editor").performTextReplacement("First production route message")
                onNodeWithTag("live-composer-send").assertIsNotEnabled()
                chooseLiveTestModel()
                onNodeWithTag("live-composer-send").assertIsEnabled().performClick()
                waitUntil { api.prompts.size == 1 && api.idleStarted.isCompleted }
                onNodeWithTag("live-thread").assertExists()
                onNodeWithTag("live-home").assertDoesNotExist()
                onNodeWithText("First production route message").assertExists()
                onNodeWithText("Reply from the test Session API").assertExists()
                onNodeWithTag("live-composer-stop").assertIsEnabled()
                onNodeWithTag("live-composer-send").assertIsNotEnabled()
                runOnIdle {
                    assertEquals(listOf("create", "prompt"), api.commands.toList())
                    assertNull(api.creates.single().requestedId)
                    assertEquals(AgentOptions(provider = LIVE_PROVIDER, model = LIVE_MODEL), api.creates.single().agentOptions)
                    // prompt has no AgentOptions argument; its session was configured by create.
                    assertEquals(api.createdId, api.prompts.single().id)
                    assertEquals(api.creates.single().agentOptions, api.createdOptions[api.prompts.single().id])
                    assertEquals(listOf(TextContent("First production route message")), api.prompts.single().message.content)
                    assertTrue(api.historyReads.isNotEmpty())
                    assertTrue(api.historyReads.all { it == api.createdId })
                    idle.complete(Unit)
                }
                onNodeWithTag("live-composer-editor").performTextReplacement("Next draft")
                onNodeWithTag("live-composer-send").assertIsEnabled()
                runOnIdle { assertEquals(1, api.creates.size); assertEquals(1, api.prompts.size) }
            } finally { idle.complete(Unit) }
        }

    @Test
    fun recentContainsOnlyListedSessionsAndSelectionReadsThatSessionsHistory() =
        runSkikoComposeUiTest(size = Size(1440f, 900f)) {
            val first = SessionId("listed-first")
            val second = SessionId("listed-second")
            val unlisted = SessionId("history-only-not-listed")
            val api = LiveTestSessionApi(listOf(liveSummary(first), liveSummary(second)))
            api.histories[first] = listOf(liveAssistant("First listed history"))
            api.histories[second] = listOf(liveAssistant("Second listed history"))
            api.histories[unlisted] = listOf(liveAssistant("Unlisted history must not appear"))
            val connection = LiveTestConnection(api)
            val models = liveTestModels()
            setContent { HarnessApp(connection, modelSettingsRepository = models) }
            recentRows().assertCountEquals(2)
            onNodeWithTag("recent-${unlisted.value}").assertDoesNotExist()
            onNodeWithTag("recent-${first.value}").performClick()
            onNodeWithText("First listed history").assertExists()
            onNodeWithTag("recent-${second.value}").performClick()
            onNodeWithText("Second listed history").assertExists()
            onNodeWithText("First listed history").assertDoesNotExist()
            onNodeWithText("Unlisted history must not appear").assertDoesNotExist()
            runOnIdle {
                assertEquals(listOf(first, second), api.historyReads.toList())
                assertTrue(api.creates.isEmpty())
                assertTrue(api.prompts.isEmpty())
            }
        }

    @Test
    fun switchingThreadsRejectsAnOldHistoryReplyEvenIfTheBackendIgnoresCancellation() =
        runSkikoComposeUiTest(size = Size(1440f, 900f)) {
            val oldId = SessionId("slow-old-thread")
            val newId = SessionId("current-thread")
            val api = LiveTestSessionApi(listOf(liveSummary(oldId), liveSummary(newId)))
            val delayed = DelayedLiveHistory(listOf(liveAssistant("Late old thread private message")))
            api.delayedHistories[oldId] = delayed
            api.histories[newId] = listOf(liveAssistant("Current thread message"))
            val connection = LiveTestConnection(api)
            val models = liveTestModels()
            try {
                setContent { HarnessApp(connection, modelSettingsRepository = models) }
                onNodeWithTag("recent-${oldId.value}").performClick()
                waitUntil { delayed.started.isCompleted }
                onNodeWithTag("live-thread-loading").assertExists()
                onNodeWithTag("recent-${newId.value}").performClick()
                onNodeWithText("Current thread message").assertExists()
                runOnIdle { delayed.release.complete(Unit) }
                waitUntil { delayed.returned.isCompleted }
                waitForIdle()
                onNodeWithText("Current thread message").assertExists()
                onNodeWithText("Late old thread private message").assertDoesNotExist()
                runOnIdle { assertEquals(listOf(oldId, newId), api.historyReads.toList()) }
            } finally { delayed.release.complete(Unit) }
        }

    @Test
    fun changingHostsClearsThreadAndDraftAndRejectsOldScopeHistoryWithTheSameSessionId() =
        runSkikoComposeUiTest(size = Size(1440f, 900f)) {
            val id = SessionId("same-id-in-different-hosts")
            val oldApi = LiveTestSessionApi(listOf(liveSummary(id)))
            val delayed = DelayedLiveHistory(listOf(liveAssistant("Old host private message")))
            oldApi.delayedHistories[id] = delayed
            val newApi = LiveTestSessionApi(listOf(liveSummary(id)))
            newApi.histories[id] = listOf(liveAssistant("New host private message"))
            val newConnection = LiveTestConnection(newApi, liveHost("different-host"))
            var connection by mutableStateOf<Connection?>(LiveTestConnection(oldApi))
            val models = liveTestModels()
            try {
                setContent { HarnessApp(connection, modelSettingsRepository = models) }
                onNodeWithTag("recent-${id.value}").performClick()
                waitUntil { delayed.started.isCompleted }
                onNodeWithTag("live-composer-editor").performTextReplacement("Old host draft")
                runOnIdle { connection = newConnection }
                onNodeWithTag("live-home").assertExists()
                onNodeWithTag("live-thread").assertDoesNotExist()
                onNodeWithTag("live-home-host").assertTextEquals(newConnection.description.displayName)
                onNodeWithTag("live-composer-editor").assertDraft("")
                runOnIdle { assertTrue(newApi.historyReads.isEmpty(), "New Host must not inherit the old Thread route") }
                onNodeWithTag("recent-${id.value}").performClick()
                onNodeWithText("New host private message").assertExists()
                runOnIdle { delayed.release.complete(Unit) }
                waitUntil { delayed.returned.isCompleted }
                waitForIdle()
                onNodeWithText("New host private message").assertExists()
                onNodeWithText("Old host private message").assertDoesNotExist()
                onNodeWithTag("live-composer-editor").assertDraft("")
                runOnIdle { assertEquals(listOf(id), newApi.historyReads.toList()) }
            } finally { delayed.release.complete(Unit) }
        }

    @Test
    fun localDisconnectAndSameHostNewConnectionKeepThreadAndDraftButClearOldHistory() =
        runSkikoComposeUiTest(size = Size(1440f, 900f)) {
            val id = SessionId("reconnected-real-thread")
            val host = liveHost("stable-local-host")
            val oldApi = LiveTestSessionApi(listOf(liveSummary(id)))
            oldApi.histories[id] = listOf(liveAssistant("History from old connection"))
            val replacementApi = LiveTestSessionApi(listOf(liveSummary(id)))
            val replacementHistory = DelayedLiveHistory(listOf(liveAssistant("History from replacement connection")))
            replacementApi.delayedHistories[id] = replacementHistory
            val replacement = LiveTestConnection(replacementApi, host)
            var connection by mutableStateOf<Connection?>(LiveTestConnection(oldApi, host))
            val models = liveTestModels()
            try {
                setContent { HarnessApp(connection, modelSettingsRepository = models) }
                onNodeWithTag("recent-${id.value}").performClick()
                onNodeWithText("History from old connection").assertExists()
                onNodeWithTag("live-composer-editor").performTextReplacement("Retained reconnect draft")
                onNodeWithTag("live-composer-send").assertIsEnabled()
                runOnIdle { connection = null }
                onNodeWithTag("live-thread").assertExists()
                onNodeWithTag("live-home").assertDoesNotExist()
                onNodeWithTag("live-composer-editor").assertDraft("Retained reconnect draft")
                onNodeWithTag("live-composer-send").assertIsNotEnabled()
                onNodeWithTag("live-composer-submit-unavailable")
                    .assertTextEquals("请连接可用的 Host 后发送消息。")
                onNodeWithText("History from old connection").assertDoesNotExist()
                runOnIdle { connection = replacement }
                waitUntil { replacementHistory.started.isCompleted }
                onNodeWithTag("live-thread").assertExists()
                onNodeWithTag("live-home").assertDoesNotExist()
                onNodeWithTag("live-thread-loading").assertExists()
                onNodeWithTag("live-composer-editor").assertDraft("Retained reconnect draft")
                onNodeWithText("History from old connection").assertDoesNotExist()
                runOnIdle { replacementHistory.release.complete(Unit) }
                onNodeWithText("History from replacement connection").assertExists()
                onNodeWithText("History from old connection").assertDoesNotExist()
                onNodeWithTag("live-composer-send").assertIsEnabled()
                runOnIdle {
                    assertEquals(listOf(id), replacementApi.historyReads.toList())
                    assertTrue(oldApi.prompts.isEmpty())
                    assertTrue(replacementApi.creates.isEmpty())
                    assertTrue(replacementApi.prompts.isEmpty())
                }
            } finally { replacementHistory.release.complete(Unit) }
        }

    @Test
    fun terminalAndSubagentPanelsCloseAnd600dpOverlayPreservesTheMainArea() =
        runSkikoComposeUiTest(size = Size(600f, 800f)) {
            val api = LiveTestSessionApi()
            val connection = LiveTestConnection(api)
            val models = liveTestModels()
            setContent { HarnessApp(connection, modelSettingsRepository = models, themeMode = HarnessThemeMode.Light) }
            // The rail is always mounted; a compact window floats the navigator instead of a drawer.
            chooseLiveTestModel()
            onNodeWithTag("live-composer-editor").performTextReplacement("Open the real Thread route")
            onNodeWithTag("live-composer-send").assertIsEnabled().performClick()
            onNodeWithTag("live-thread").assertExists()
            // Thread routing and the owner-fenced panel binding run in separate effects.
            waitUntil { onAllNodesWithText("终端").fetchSemanticsNodes().isNotEmpty() }
            val before = onNodeWithTag("main-content-column").fetchSemanticsNode().boundsInRoot
            assertTrue(before.width > 0f && before.height > 0f)
            onNodeWithText("终端").performClick()
            onNodeWithTag("thread-panel-overlay").assertExists()
            onNodeWithTag("tab:BackgroundOutput").assertExists()
            onNodeWithText("终端不可用").assertExists()
            val during = onNodeWithTag("main-content-column").fetchSemanticsNode().boundsInRoot
            assertEquals(before, during, "Opening an overlay must preserve the main content area")
            onNodeWithContentDescription("关闭后台终端").performClick()
            onNodeWithTag("thread-panel-overlay").assertDoesNotExist()
            onNodeWithTag("tab:BackgroundOutput").assertDoesNotExist()
            onNodeWithText("子 Agent").performClick()
            onNodeWithTag("thread-panel-overlay").assertExists()
            onNodeWithTag("tab:Subagents").assertExists()
            onNodeWithText("子智能体不可用").assertExists()
            assertEquals(before, onNodeWithTag("main-content-column").fetchSemanticsNode().boundsInRoot)
            onNodeWithContentDescription("关闭子智能体").performClick()
            onNodeWithTag("thread-panel-overlay").assertDoesNotExist()
            onNodeWithTag("tab:Subagents").assertDoesNotExist()
            onNodeWithTag("live-thread").assertExists()
            assertEquals(before, onNodeWithTag("main-content-column").fetchSemanticsNode().boundsInRoot)
        }
}

private const val LIVE_PROVIDER = "live-provider"
private const val LIVE_MODEL = "live-model"

private fun SemanticsNodeInteraction.assertDraft(text: String): SemanticsNodeInteraction =
    assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(text)))

private fun SemanticsNodeInteractionsProvider.chooseLiveTestModel() {
    onNodeWithTag("live-composer-model").assertIsEnabled().performClick()
    onNodeWithTag("menu:conversation-model:${LIVE_PROVIDER.length}:$LIVE_PROVIDER:${LIVE_MODEL.length}:$LIVE_MODEL")
        .performClick()
}

private fun SemanticsNodeInteractionsProvider.recentRows(): SemanticsNodeInteractionCollection =
    onAllNodes(SemanticsMatcher("sessions supplied by the live list") {
        it.config.getOrElse(SemanticsProperties.TestTag) { "" }.startsWith("recent-")
    })

private fun liveHost(id: String = "live-test-host") = HostDescription(
    protocolVersion = ProtocolVersion.Current, hostId = HostId(id), displayName = "Test workstation $id",
)

private fun liveSummary(id: SessionId) = SessionSummary(id, createdAt = 1L)

private fun liveAssistant(text: String) = Message(
    id = "assistant-$text", role = MessageRole.ASSISTANT, content = listOf(TextContent(text)),
    source = ModelMessageSource(LIVE_PROVIDER, LIVE_MODEL),
)

private fun liveTestModels(): ModelSettingsRepository =
    ModelSettingsRepository(LiveCatalogBackend()).also { runBlocking { it.load() } }

private class LiveCatalogBackend : ModelSettingsBackend {
    override suspend fun read() = ModelSettingsSnapshot(
        providers = listOf(ConfigurableProvider(
            id = LIVE_PROVIDER, displayName = "Test catalog provider", active = true,
            configuration = ProviderConfiguration(LIVE_PROVIDER, modelIds = listOf(LIVE_MODEL)),
            models = listOf(ProviderModel(LIVE_MODEL, "Test catalog model")),
        )), available = true, unavailableReason = null,
    )
    override suspend fun save(configuration: ProviderConfiguration, expectedRevision: Long): Unit =
        error("Conversation interactions must not write model settings")
    override suspend fun remove(providerId: String, expectedRevision: Long): Unit =
        error("Conversation interactions must not remove providers")
    override suspend fun setCredential(name: String, value: String): Unit =
        error("Conversation interactions must not write credentials")
}

private class LiveTestConnection(
    override val session: LiveTestSessionApi,
    val description: HostDescription = liveHost(),
) : Connection {
    override val host: HostApi = object : HostApi {
        override suspend fun describe(): ApiResult<HostDescription> =
            ApiResult(ApiResult.SUCCESS_CODE, "", description)
    }
}

private data class LiveCreateCall(val requestedId: SessionId?, val agentOptions: AgentOptions)
private data class LivePromptCall(val id: SessionId, val message: Message)

private class DelayedLiveHistory(val messages: List<Message>) {
    val started = CompletableDeferred<Unit>()
    val release = CompletableDeferred<Unit>()
    val returned = CompletableDeferred<Unit>()

    suspend fun read(): List<Message> = withContext(NonCancellable) {
        started.complete(Unit)
        release.await()
        returned.complete(Unit)
        messages
    }
}

private class LiveTestSessionApi(initialSessions: List<SessionSummary> = emptyList()) : SessionApi {
    val createdId = SessionId("session-id-returned-by-create")
    val sessions = CopyOnWriteArrayList(initialSessions)
    val histories = ConcurrentHashMap<SessionId, List<Message>>()
    val descendantResults = ConcurrentHashMap<SessionId, SessionDescendantsResult>()
    val childResults = ConcurrentHashMap<Pair<SessionId, SessionId>, ChildHistoryResult>()
    val descendantReads = CopyOnWriteArrayList<SessionId>()
    val childReads = CopyOnWriteArrayList<Pair<SessionId, SessionId>>()
    val delayedHistories = ConcurrentHashMap<SessionId, DelayedLiveHistory>()
    val commands = CopyOnWriteArrayList<String>()
    val creates = CopyOnWriteArrayList<LiveCreateCall>()
    val prompts = CopyOnWriteArrayList<LivePromptCall>()
    val historyReads = CopyOnWriteArrayList<SessionId>()
    val createdOptions = ConcurrentHashMap<SessionId, AgentOptions>()
    val cancels = CopyOnWriteArrayList<SessionId>()
    val idleStarted = CompletableDeferred<Unit>()
    var idleGate: CompletableDeferred<Unit>? = null

    override suspend fun list(): List<SessionSummary> = sessions.toList()

    override suspend fun descendants(parent: SessionId): SessionDescendantsResult {
        descendantReads += parent
        return descendantResults[parent] ?: SessionDescendantsResult.Unavailable(parent)
    }

    override suspend fun readChildHistory(parent: SessionId, child: SessionId): ChildHistoryResult {
        childReads += parent to child
        return childResults[parent to child] ?: ChildHistoryResult.Unavailable(parent, child)
    }

    override suspend fun create(id: SessionId?, options: CreateSessionOptions, agentOptions: AgentOptions): SessionSummary {
        commands += "create"
        creates += LiveCreateCall(id, agentOptions)
        val summary = SessionSummary(id ?: createdId, createdAt = 2L, cwd = options.cwd)
        sessions += summary
        createdOptions[summary.id] = agentOptions
        return summary
    }

    override suspend fun history(id: SessionId): List<Message> {
        historyReads += id
        return delayedHistories[id]?.read() ?: histories[id].orEmpty().toList()
    }

    override suspend fun prompt(id: SessionId, message: Message): SessionPrompt {
        check(sessions.any { it.id == id })
        commands += "prompt"
        prompts += LivePromptCall(id, message)
        histories[id] = histories[id].orEmpty() + message + liveAssistant("Reply from the test Session API")
        return object : SessionPrompt {
            override val receipt = SessionPromptReceipt(id, message.id)
            // This fake is bound as Local: returning means local idle, not Relay completion.
            override suspend fun awaitIdle() {
                idleStarted.complete(Unit)
                idleGate?.await()
            }
        }
    }

    override suspend fun cancel(id: SessionId, keepInbox: Boolean) { cancels += id }
}
