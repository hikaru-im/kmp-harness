package im.hikaru.harness.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import im.hikaru.contracts.common.ApiResult
import im.hikaru.contracts.harness.identity.HostDescription
import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.contracts.harness.protocol.ProtocolVersion
import im.hikaru.harness.api.gateway.ApiGateway
import im.hikaru.harness.api.gateway.host.HostDescribeEndpoint
import im.hikaru.harness.api.gateway.session.*
import im.hikaru.harness.client.app.HarnessApp
import im.hikaru.harness.client.connection.Connection
import im.hikaru.harness.client.connection.ConnectionTransport
import im.hikaru.harness.client.connection.SessionSummary as ClientSessionSummary
import im.hikaru.harness.client.projects.*
import im.hikaru.harness.client.settings.*
import im.hikaru.harness.desktop.connection.LocalConnection
import im.hikaru.harness.desktop.projects.DesktopLocalProjectReadResult
import im.hikaru.harness.desktop.projects.DesktopLocalProjectRepository
import im.hikaru.harness.desktop.projects.DesktopLocalProjectStore
import im.hikaru.harness.llm.Message
import im.hikaru.harness.session.SessionId
import im.hikaru.harness.session.api.SessionPrompt
import im.hikaru.harness.session.api.SessionPromptReceipt
import im.hikaru.harness.session.api.SessionSummary
import im.hikaru.harness.ui.foundation.HarnessThemeMode
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.runBlocking
import kotlin.test.*

/** Real app, local transport, directory validation and durable store; only Session endpoints/model catalog are fixtures. */
@OptIn(ExperimentalTestApi::class)
class LocalProjectsInteractionTest {
    @Test
    fun registerExistingDirectorySelectHomeProjectAndPersistConfirmedMembershipBeforePrompt() {
        val fixture = ProjectInteractionFixture()
        try {
            // A real alias proves that registration and session creation use the canonical root.
            val alias = Files.createSymbolicLink(fixture.directory.resolve("root-alias"), fixture.root)
            runSkikoComposeUiTest(size = Size(1440f, 900f)) {
                setContent {
                    HarnessApp(fixture.connection, connectionTransport = ConnectionTransport.Local,
                        localProjectRepository = fixture.repository, modelSettingsRepository = fixture.models,
                        themeMode = HarnessThemeMode.Light)
                }
                waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag("local-project-manage").fetchSemanticsNodes().isNotEmpty() }
                onNodeWithTag("local-project-manage").performClick()
                waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag("local-projects-empty").fetchSemanticsNodes().isNotEmpty() }
                onNodeWithTag("local-project-create").assertIsEnabled().performClick()
                fillProjectRegistration("Interaction project", alias.toString())
                onNodeWithTag("local-project-editor-save").performClick()
                waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag("local-project-editor-save").fetchSemanticsNodes().isEmpty() }

                val registered = fixture.snapshot()
                val project = registered.projects.single()
                assertEquals(fixture.root.toRealPath().toString(), project.roots.single().path)
                assertEquals("Interaction project", project.name)
                assertEquals(alias.toString(), registered.createReceipts.single().request.directory)
                assertTrue(registered.memberships.isEmpty())
                assertTrue(fixture.creates.isEmpty())
                assertTrue(fixture.prompts.isEmpty())
                onNodeWithTag("local-project-row-${project.id.value}").assertExists()

                onNodeWithTag("new-task").performClick()
                // Exercise both picker paths, rather than relying on create auto-selection.
                onNodeWithTag("local-project-picker-open").performClick()
                onNodeWithTag("默认工作目录").performClick()
                onNodeWithTag("local-project-picker-open").assertTextEquals("默认工作目录").performClick()
                // Picker entries are keyed by project id, not by display name.
                onNodeWithTag("local-project-picker-item-${project.id.value}").performClick()
                onNodeWithTag("local-project-picker-open").assertTextEquals(project.name)
                onNodeWithTag("live-composer-editor").performTextReplacement("Run in the selected real directory")
                chooseProjectInteractionModel()
                onNodeWithTag("live-composer-send").assertIsEnabled().performClick()
                waitUntil(timeoutMillis = 10_000) { fixture.prompts.size == 1 }
                waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag("live-thread").fetchSemanticsNodes().isNotEmpty() }

                assertEquals(1, fixture.creates.size)
                assertEquals(fixture.root.toRealPath().toString(), fixture.creates.single().options.cwd)
                assertEquals(PROJECT_PROVIDER, fixture.creates.single().agentOptions.provider)
                assertEquals(PROJECT_MODEL, fixture.creates.single().agentOptions.model)
                assertEquals(fixture.sessions.single().id, fixture.prompts.single().id)
                assertTrue(fixture.membershipWasDurableAtPrompt, "Membership must commit before prompt is delivered")
                // A fresh store instance must recover the full typed summary, not just an in-memory flag.
                val persisted = fixture.snapshot()
                val membership = persisted.memberships.single()
                assertEquals(project.id, membership.projectId)
                assertEquals(fixture.sessions.single().id.value, membership.session.id.value)
                assertEquals(fixture.sessions.single().createdAt, membership.session.createdAt)
                assertEquals(fixture.sessions.single().cwd, membership.session.cwd)
                assertEquals(fixture.sessions.single().agentPreset, membership.session.agentPreset)
                assertEquals(1, persisted.projects.size)
                assertEquals(1, persisted.createReceipts.size)
                assertTrue(persisted.revision > registered.revision)
                assertTrue(Files.readString(fixture.storeFile).contains("\"schemaVersion\":1"))
            }
        } finally { fixture.close() }
    }

    @Test
    fun failedSessionAttachSurvivesSameConnectionReconnectAndRequiresExplicitRetryThenSend() {
        val fixture = ProjectInteractionFixture()
        val repository = FailFirstProjectAttachRepository(fixture.repository)
        val draft = "Keep this draft until explicit association retry and send"
        var connection by mutableStateOf<Connection?>(fixture.connection)
        try {
            runSkikoComposeUiTest(size = Size(1440f, 900f)) {
                setContent {
                    HarnessApp(connection, connectionTransport = ConnectionTransport.Local,
                        localProjectRepository = repository, modelSettingsRepository = fixture.models,
                        themeMode = HarnessThemeMode.Light)
                }
                waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag("local-project-manage").fetchSemanticsNodes().isNotEmpty() }
                onNodeWithTag("local-project-manage").performClick()
                waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag("local-projects-empty").fetchSemanticsNodes().isNotEmpty() }
                onNodeWithTag("local-project-create").performClick()
                fillProjectRegistration("Retry association project", fixture.root.toString())
                onNodeWithTag("local-project-editor-save").performClick()
                waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag("local-project-editor-save").fetchSemanticsNodes().isEmpty() }
                val registered = fixture.snapshot()
                val project = registered.projects.single()
                val beforeAttach = Files.readAllBytes(fixture.storeFile)
                onNodeWithTag("new-task").performClick()
                onNodeWithTag("local-project-picker-open").assertTextEquals(project.name)
                chooseProjectInteractionModel()
                onNodeWithTag("live-composer-editor").performTextReplacement(draft)
                onNodeWithTag("live-composer-send").assertIsEnabled().performClick()
                waitUntil(timeoutMillis = 10_000) {
                    onAllNodes(hasTestTag("local-project-attach-retry") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
                }
                onNodeWithTag("live-thread").assertExists()
                onNodeWithTag("local-project-attach-pending").assertTextContains("关联待确认", substring = true)
                onNodeWithTag("live-composer-send").assertIsNotEnabled()
                onNodeWithTag("live-composer-editor").assertTextContains(draft)
                val session = runBlocking { fixture.connection.session.list().single() }
                val originalTarget = repository.attachTargets.single()
                assertEquals(1, fixture.creates.size)
                assertEquals(fixture.root.toRealPath().toString(), session.cwd)
                assertEquals(session, repository.attachSessions.single())
                assertEquals(project.id, originalTarget.projectId)
                assertEquals(project.roots.single(), originalTarget.root)
                assertEquals(1, repository.validatedTargets.size)
                assertTrue(fixture.prompts.isEmpty())
                assertTrue(fixture.snapshot().memberships.isEmpty())
                assertContentEquals(beforeAttach, Files.readAllBytes(fixture.storeFile))

                // Recompose the same app through a temporary disconnect, retaining its Host.
                runOnIdle { connection = null }
                waitUntil(timeoutMillis = 10_000) {
                    onAllNodes(hasTestTag("local-project-attach-retry") and isNotEnabled()).fetchSemanticsNodes().isNotEmpty()
                }
                onNodeWithTag("local-project-attach-pending").assertExists()
                onNodeWithTag("local-project-attach-retry").assertIsNotEnabled()
                onNodeWithTag("live-composer-send").assertIsNotEnabled()
                onNodeWithTag("live-composer-editor").assertTextContains(draft)
                assertEquals(listOf(session.id.value), fixture.sessions.map { it.id.value })
                assertEquals(1, repository.attachTargets.size)
                assertEquals(1, repository.validatedTargets.size)
                assertTrue(fixture.prompts.isEmpty())

                runOnIdle { connection = fixture.connection }
                waitUntil(timeoutMillis = 10_000) {
                    repository.loadedBindings.any { it.scope.generation != originalTarget.binding.scope.generation } &&
                        onAllNodes(hasTestTag("local-project-attach-retry") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
                }
                val restoredBinding = repository.loadedBindings.last()
                assertSame(fixture.connection, restoredBinding.connection)
                assertEquals(originalTarget.binding.scope.hostId, restoredBinding.scope.hostId)
                assertEquals(fixture.host, restoredBinding.scope.hostId)
                assertEquals(ConnectionTransport.Local, restoredBinding.transport)
                assertTrue(restoredBinding.scope.generation > originalTarget.binding.scope.generation)
                onNodeWithTag("local-project-attach-pending").assertTextContains("关联待确认", substring = true)
                onNodeWithTag("live-composer-send").assertIsNotEnabled()
                onNodeWithTag("live-composer-editor").assertTextContains(draft)
                assertEquals(1, repository.attachTargets.size, "Reconnect must not automatically attach")
                assertEquals(1, repository.validatedTargets.size, "Reconnect must not automatically revalidate")
                assertEquals(1, fixture.creates.size)
                assertEquals(listOf(session.id.value), fixture.sessions.map { it.id.value })
                assertTrue(fixture.prompts.isEmpty())
                assertContentEquals(beforeAttach, Files.readAllBytes(fixture.storeFile))

                onNodeWithTag("local-project-attach-retry").assertIsEnabled().performClick()
                waitUntil(timeoutMillis = 10_000) {
                    onAllNodesWithTag("local-project-attach-pending").fetchSemanticsNodes().isEmpty() &&
                        onAllNodes(hasTestTag("live-composer-send") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
                }
                assertEquals(2, repository.validatedTargets.size)
                assertEquals(2, repository.attachTargets.size)
                val revalidated = repository.validatedTargets.last()
                val retriedTarget = repository.attachTargets.last()
                assertTrue(revalidated.binding.sameOwner(restoredBinding))
                assertTrue(retriedTarget.binding.sameOwner(restoredBinding))
                assertEquals(project.id, revalidated.projectId)
                assertEquals(originalTarget.root, revalidated.root)
                assertEquals(revalidated.projectId, retriedTarget.projectId)
                assertEquals(revalidated.root, retriedTarget.root)
                assertNotNull(retriedTarget.controllerEpoch)
                assertNotEquals(originalTarget.controllerEpoch, retriedTarget.controllerEpoch)
                assertEquals(listOf(session, session), repository.attachSessions.toList())
                val persisted = fixture.snapshot()
                val membership = persisted.memberships.single()
                assertEquals(project.id, membership.projectId)
                assertEquals(session, membership.session)
                assertEquals(registered.projects, persisted.projects)
                assertEquals(registered.createReceipts, persisted.createReceipts)
                assertTrue(persisted.revision > registered.revision)
                onNodeWithTag("local-project-attach-pending").assertDoesNotExist()
                onNodeWithTag("local-project-attach-retry").assertDoesNotExist()
                onNodeWithTag("live-composer-editor").assertTextContains(draft)
                onNodeWithTag("live-composer-send").assertIsEnabled()
                assertTrue(fixture.prompts.isEmpty(), "Association retry must only unlock explicit send")
                assertEquals(1, fixture.creates.size)
                assertEquals(listOf(session.id.value), fixture.sessions.map { it.id.value })

                onNodeWithTag("live-composer-send").performClick()
                waitUntil(timeoutMillis = 10_000) { fixture.prompts.size == 1 }
                waitForIdle()
                assertEquals(session.id.value, fixture.prompts.single().id.value)
                assertEquals(1, fixture.creates.size, "Explicit send must reuse the original session")
                assertEquals(listOf(session.id.value), fixture.sessions.map { it.id.value })
                assertEquals(2, repository.attachTargets.size)
                assertEquals(2, repository.validatedTargets.size)
                assertTrue(fixture.membershipWasDurableAtPrompt)
                assertEquals(listOf(membership), fixture.snapshot().memberships)
            }
        } finally { fixture.close() }
    }

    @Test
    fun missingDirectoryRegistrationShowsFailureAndDoesNotCreateDirectorySessionOrPrompt() {
        val fixture = ProjectInteractionFixture()
        try {
            val missing = fixture.directory.resolve("does-not-exist")
            runSkikoComposeUiTest(size = Size(1440f, 900f)) {
                setContent {
                    HarnessApp(fixture.connection, connectionTransport = ConnectionTransport.Local,
                        localProjectRepository = fixture.repository, modelSettingsRepository = fixture.models)
                }
                waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag("local-project-manage").fetchSemanticsNodes().isNotEmpty() }
                onNodeWithTag("local-project-manage").performClick()
                waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag("local-projects-empty").fetchSemanticsNodes().isNotEmpty() }
                onNodeWithTag("local-project-create").performClick()
                fillProjectRegistration("Invalid project", missing.toString())
                onNodeWithTag("local-project-editor-save").performClick()
                waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag("local-project-editor-error").fetchSemanticsNodes().isNotEmpty() }
                onNodeWithTag("local-project-editor-error").assertTextContains("目录不存在", substring = true)
                onNodeWithTag("local-project-editor-close").performClick()
                onNodeWithTag("local-projects-empty").assertExists()
                assertTrue(fixture.snapshot().projects.isEmpty())
                assertTrue(fixture.snapshot().createReceipts.isEmpty())
                assertTrue(fixture.creates.isEmpty())
                assertTrue(fixture.prompts.isEmpty())
                assertFalse(Files.exists(missing))
                assertFalse(Files.exists(fixture.storeFile), "Rejected registration must not commit a success document")
            }
        } finally { fixture.close() }
    }

    @Test
    fun selectedDirectoryDeletedBeforeSendPreservesDraftAndHasZeroCreateAndPromptCalls() {
        val fixture = ProjectInteractionFixture()
        try {
            runSkikoComposeUiTest(size = Size(1440f, 900f)) {
                setContent {
                    HarnessApp(fixture.connection, connectionTransport = ConnectionTransport.Local,
                        localProjectRepository = fixture.repository, modelSettingsRepository = fixture.models)
                }
                waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag("local-project-manage").fetchSemanticsNodes().isNotEmpty() }
                onNodeWithTag("local-project-manage").performClick()
                waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag("local-projects-empty").fetchSemanticsNodes().isNotEmpty() }
                onNodeWithTag("local-project-create").performClick()
                fillProjectRegistration("Removed root", fixture.root.toString())
                onNodeWithTag("local-project-editor-save").performClick()
                waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag("local-project-editor-save").fetchSemanticsNodes().isEmpty() }
                val before = Files.readAllBytes(fixture.storeFile)
                onNodeWithTag("new-task").performClick()
                onNodeWithTag("local-project-picker-open").assertTextEquals("Removed root")
                chooseProjectInteractionModel()
                onNodeWithTag("live-composer-editor").performTextReplacement("Keep this draft when cwd is invalid")
                Files.delete(fixture.root)
                onNodeWithTag("live-composer-send").assertIsEnabled().performClick()
                waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag("local-project-prepare-error").fetchSemanticsNodes().isNotEmpty() }
                onNodeWithTag("local-project-prepare-error").assertTextContains("目录不存在", substring = true)
                onNodeWithTag("live-home").assertExists()
                onNodeWithTag("live-thread").assertDoesNotExist()
                onNodeWithTag("live-composer-editor").assertTextContains("Keep this draft when cwd is invalid")
                assertTrue(fixture.creates.isEmpty())
                assertTrue(fixture.prompts.isEmpty())
                assertTrue(fixture.sessions.isEmpty())
                assertTrue(fixture.snapshot().memberships.isEmpty())
                assertContentEquals(before, Files.readAllBytes(fixture.storeFile))
                assertFalse(Files.exists(fixture.root))
            }
        } finally { fixture.close() }
    }
}

private fun SemanticsNodeInteractionsProvider.fillProjectRegistration(name: String, directory: String) {
    onNodeWithTag("local-project-name-input").performTextReplacement(name)
    onNodeWithTag("local-project-directory-input").performTextReplacement(directory)
}

private fun SemanticsNodeInteractionsProvider.chooseProjectInteractionModel() {
    onNodeWithTag("live-composer-model").assertIsEnabled().performClick()
    onNodeWithTag("menu:conversation-model:${PROJECT_PROVIDER.length}:$PROJECT_PROVIDER:${PROJECT_MODEL.length}:$PROJECT_MODEL")
        .performClick()
}

private const val PROJECT_PROVIDER = "projects-test-provider"
private const val PROJECT_MODEL = "projects-test-model"

private class ProjectInteractionFixture {
    val directory: Path = Files.createTempDirectory("local-projects-ui-").toRealPath()
    val root: Path = Files.createDirectory(directory.resolve("existing-root"))
    val storeFile: Path = directory.resolve("projects.json")
    val host = HostId("projects-ui-local-host")
    val gateway = ApiGateway()
    val connection = LocalConnection(gateway)
    val repository = DesktopLocalProjectRepository(host, DesktopLocalProjectStore(storeFile), expectedConnection = connection)
    val sessions = CopyOnWriteArrayList<SessionSummary>()
    val creates = CopyOnWriteArrayList<SessionCreateRequest>()
    val prompts = CopyOnWriteArrayList<SessionPromptRequest>()
    private val histories = ConcurrentHashMap<SessionId, List<Message>>()
    @Volatile var membershipWasDurableAtPrompt = false
        private set
    val models = ModelSettingsRepository(ProjectInteractionModels()).also { runBlocking { it.load() } }

    init {
        runBlocking {
            gateway.register(HostDescribeEndpoint) {
                ApiResult(ApiResult.SUCCESS_CODE, data = HostDescription(ProtocolVersion.Current, host, "Projects local fixture"))
            }
            gateway.register(SessionListEndpoint) { ApiResult(ApiResult.SUCCESS_CODE, data = sessions.toList()) }
            gateway.register(SessionCreateEndpoint) { request ->
                creates += request
                val summary = SessionSummary(request.id ?: SessionId("created-project-session"), 123L, request.options.cwd, "fixture-preset")
                sessions += summary
                ApiResult(ApiResult.SUCCESS_CODE, data = summary)
            }
            gateway.register(SessionHistoryEndpoint) { id ->
                ApiResult(ApiResult.SUCCESS_CODE, data = histories[id].orEmpty())
            }
            gateway.register(SessionPromptEndpoint) { request ->
                val summary = sessions.single { it.id == request.id }
                val saved = assertIs<DesktopLocalProjectReadResult.Ready>(
                    DesktopLocalProjectStore(storeFile).read(LocalProjectOwner(host))).snapshot
                membershipWasDurableAtPrompt = saved.memberships.any {
                    it.session.id.value == summary.id.value && it.session.createdAt == summary.createdAt &&
                        it.session.cwd == summary.cwd && it.session.agentPreset == summary.agentPreset
                }
                histories[request.id] = listOf(request.message)
                val prompt: SessionPrompt = object : SessionPrompt {
                    override val receipt = SessionPromptReceipt(request.id, request.message.id.value)
                    override suspend fun awaitIdle() = Unit
                }
                prompts += request
                ApiResult(ApiResult.SUCCESS_CODE, data = prompt)
            }
            gateway.register(SessionCancelEndpoint) { ApiResult(ApiResult.SUCCESS_CODE, data = Unit) }
        }
    }

    fun snapshot(): LocalProjectSnapshot = runBlocking {
        assertIs<DesktopLocalProjectReadResult.Ready>(DesktopLocalProjectStore(storeFile).read(LocalProjectOwner(host))).snapshot
    }

    // Called after runSkikoComposeUiTest returns, so composition and controller jobs are disposed first.
    fun close() { directory.toFile().deleteRecursively() }
}

/** Real directory/store behavior, with one deliberate failure before durable attach. */
private class FailFirstProjectAttachRepository(
    private val delegate: LocalProjectRepository,
) : LocalProjectRepository by delegate {
    val loadedBindings = CopyOnWriteArrayList<LocalProjectBinding>()
    val validatedTargets = CopyOnWriteArrayList<ValidatedLocalProjectTarget>()
    val attachTargets = CopyOnWriteArrayList<ValidatedLocalProjectTarget>()
    val attachSessions = CopyOnWriteArrayList<ClientSessionSummary>()

    override suspend fun list(binding: LocalProjectBinding, isCurrent: () -> Boolean): LocalProjectsResult {
        val result = delegate.list(binding, isCurrent)
        if (result is LocalProjectsResult.Ready) loadedBindings += binding
        return result
    }

    override suspend fun validateRoot(
        binding: LocalProjectBinding, id: LocalProjectId, root: LocalProjectRoot, isCurrent: () -> Boolean,
    ): LocalProjectRootResult {
        val result = delegate.validateRoot(binding, id, root, isCurrent)
        if (result is LocalProjectRootResult.Ready) validatedTargets += result.target
        return result
    }

    override suspend fun attachSession(
        target: ValidatedLocalProjectTarget, session: ClientSessionSummary, isCurrent: () -> Boolean,
    ): LocalProjectWriteResult {
        attachTargets += target
        attachSessions += session
        return if (attachTargets.size == 1) {
            LocalProjectWriteResult.Failed(LocalProjectFailure.Error("Deliberate first project association failure"))
        } else delegate.attachSession(target, session, isCurrent)
    }
}

private class ProjectInteractionModels : ModelSettingsBackend {
    override suspend fun read() = ModelSettingsSnapshot(
        providers = listOf(ConfigurableProvider(
            id = PROJECT_PROVIDER, displayName = "Projects test catalog", active = true,
            configuration = ProviderConfiguration(PROJECT_PROVIDER, modelIds = listOf(PROJECT_MODEL)),
            models = listOf(ProviderModel(PROJECT_MODEL, "Projects test model")),
        )), available = true, unavailableReason = null,
    )
    override suspend fun save(configuration: ProviderConfiguration, expectedRevision: Long): Unit = error("Read-only test catalog")
    override suspend fun remove(providerId: String, expectedRevision: Long): Unit = error("Read-only test catalog")
    override suspend fun setCredential(name: String, value: String): Unit = error("Read-only test catalog")
}
