@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package im.hikaru.harness.desktop

import androidx.compose.ui.test.*
import androidx.compose.ui.geometry.Offset
import im.hikaru.contracts.common.ApiResult
import im.hikaru.contracts.harness.identity.HostDescription
import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.contracts.harness.protocol.ProtocolVersion
import im.hikaru.harness.api.gateway.ApiGateway
import im.hikaru.harness.api.gateway.host.HostDescribeEndpoint
import im.hikaru.harness.api.gateway.session.*
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
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import im.hikaru.harness.client.conversation.*
import im.hikaru.harness.client.connection.SessionId as ClientSessionId
import kotlinx.coroutines.CompletableDeferred
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File
import java.security.MessageDigest
import javax.imageio.ImageIO
import kotlinx.serialization.json.*


internal const val PROJECTS_RECENT_PROVIDER = "projects-recent-test-provider"
internal const val PROJECTS_RECENT_MODEL = "projects-recent-test-model"

internal class ProjectsRecentFixture {
    val directory: Path = Files.createTempDirectory("projects-recent-ui-").toRealPath()
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
    @Volatile var listFailure = false
    @Volatile var listGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null
    val models = ModelSettingsRepository(ProjectsRecentModels()).also { runBlocking { it.load() } }

    init {
        runBlocking {
            gateway.register(HostDescribeEndpoint) {
                ApiResult(ApiResult.SUCCESS_CODE, data = HostDescription(ProtocolVersion.Current, host, "Projects local fixture"))
            }
            gateway.register(SessionListEndpoint) {
                listGate?.await()
                if (listFailure) error("Deliberate test list failure")
                ApiResult(ApiResult.SUCCESS_CODE, data = sessions.toList())
            }
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

private class ProjectsRecentModels : ModelSettingsBackend {
    override suspend fun read() = ModelSettingsSnapshot(
        providers = listOf(ConfigurableProvider(
            id = PROJECTS_RECENT_PROVIDER, displayName = "Projects test catalog", active = true,
            configuration = ProviderConfiguration(PROJECTS_RECENT_PROVIDER, modelIds = listOf(PROJECTS_RECENT_MODEL)),
            models = listOf(ProviderModel(PROJECTS_RECENT_MODEL, "Projects test model")),
        )), available = true, unavailableReason = null,
    )
    override suspend fun save(configuration: ProviderConfiguration, expectedRevision: Long): Unit = error("Read-only test catalog")
    override suspend fun remove(providerId: String, expectedRevision: Long): Unit = error("Read-only test catalog")
    override suspend fun setCredential(name: String, value: String): Unit = error("Read-only test catalog")
}

/** Static display cases are test-only. Route tests use ProjectsRecentFixture's real adapter/store. */
internal object ProjectsRecentDisplayData {
    val host = HostId("projects-recent-display-host")
    val scope = ConversationHostScope(host, 1, null)
    val owner = LocalProjectOwner(host)
    val longRoot = "/test-only/" + "long directory with spaces/".repeat(12) + "root"
    val longName = "超长项目名称 " + "Long project name ".repeat(14)
    val sessions = (0 until 24).map { n ->
        ClientSessionSummary(ClientSessionId("session-${n.toString().padStart(2, '0')}-" + "long-id-".repeat(8)),
            1_780_000_000_000L + (n / 2) * 1_000, "$longRoot/$n", if (n % 2 == 0) "test-agent-preset" else null)
    }.reversed()
    val projects = (0 until 24).map { n ->
        LocalProject(LocalProjectId("project-$n"), if (n == 0) longName else "Project $n", owner,
            listOf(LocalProjectRoot("$longRoot/$n")))
    }
    fun binding(connection: Connection, generation: Long = 1) =
        LocalProjectBinding(connection, LocalProjectScope(host, generation), ConnectionTransport.Local)
    fun projects(binding: LocalProjectBinding?, phase: LocalProjectsPhase = LocalProjectsPhase.Ready,
        memberships: Boolean = false): LocalProjectsState = LocalProjectsState(
        binding = binding, phase = phase,
        snapshot = LocalProjectSnapshot(owner, projects = if (phase == LocalProjectsPhase.Empty) emptyList() else projects,
            memberships = if (memberships) sessions.take(2).map { LocalProjectMembership(projects.first().id, it) } else emptyList()),
        selectedProjectId = projects.first().id.takeUnless { phase == LocalProjectsPhase.Empty }, canRefresh = binding != null,
        failure = when (phase) {
            LocalProjectsPhase.Error -> LocalProjectFailure.Error("Test read failed")
            LocalProjectsPhase.Unavailable -> LocalProjectFailure.Unavailable(LocalProjectUnavailableReason.LocalOnly, "Test local-only service")
            LocalProjectsPhase.Unsupported -> LocalProjectFailure.UnsupportedVersion(99)
            LocalProjectsPhase.AccessDenied -> LocalProjectFailure.AccessDenied("Test access denied")
            else -> null
        },
    )
    fun recent(phase: ConversationDataPhase = ConversationDataPhase.Ready, stale: Boolean = false) = ConversationState(
        host = scope, connected = phase != ConversationDataPhase.Disconnected, listPhase = phase,
        sessions = if (phase == ConversationDataPhase.Ready || stale) sessions else emptyList(),
        errors = if (phase == ConversationDataPhase.Failed)
            mapOf(ConversationErrorKind.List to ConversationError(ConversationErrorKind.List, "Test list failure", ConversationRetry.Refresh))
            else emptyMap(),
    )
}

/** Real commit with one lost acknowledgement; replay resolves the original durable request receipt. */
internal class ProjectsRecentUnknownCreate(private val delegate: LocalProjectRepository) : LocalProjectRepository by delegate {
    val entered = CompletableDeferred<Unit>()
    val release = CompletableDeferred<Unit>()
    val requests = CopyOnWriteArrayList<CreateLocalProject>()
    override suspend fun create(binding: LocalProjectBinding, request: CreateLocalProject, isCurrent: () -> Boolean): LocalProjectWriteResult {
        requests += request
        if (requests.size == 1) {
            entered.complete(Unit)
            release.await()
            val result = delegate.create(binding, request, isCurrent)
            return if (result is LocalProjectWriteResult.Committed)
                LocalProjectWriteResult.Failed(LocalProjectFailure.Error("Test acknowledgement lost; result unknown")) else result
        }
        return delegate.create(binding, request, isCurrent)
    }
}

/** UI actions use actual pointer input. Semantic OnClick capture is only for stale-callback injection. */
@OptIn(ExperimentalTestApi::class)
internal fun SemanticsNodeInteraction.projectsRecentPointerClick() = performMouseInput { click() }

/** Wheel input reaches the actual visible item; never substitutes semantic ScrollTo/OnClick. */
@OptIn(ExperimentalTestApi::class)
internal fun SemanticsNodeInteractionsProvider.projectsRecentWheelTo(pageTag: String, itemTag: String, delta: Float = 400f) {
    repeat(100) {
        if (runCatching { onNodeWithTag(itemTag).assertIsDisplayed() }.isSuccess) return
        // Page tags include their padding: this gutter avoids the values' horizontal scroll areas.
        onNodeWithTag(pageTag).performMouseInput { moveTo(Offset(4f, center.y)); scroll(delta) }
    }
    onNodeWithTag(itemTag).assertIsDisplayed()
}

/** Complete unmerged tagged geometry includes popups, state, theme, dimensions, semantics and SHA256. */
@OptIn(ExperimentalTestApi::class)
internal fun SemanticsNodeInteractionsProvider.projectsRecentCapture(
    directory: File, name: String, state: String, theme: HarnessThemeMode, width: Int, height: Int,
    target: String = "projects-recent-viewport", viewport: Boolean = true,
    modelState: String = state,
) {
    directory.mkdirs()
    val bitmap = onNodeWithTag(target).captureToImage()
    val bytes = Image.makeFromBitmap(bitmap.asSkiaBitmap()).use { image ->
        image.encodeToData(EncodedImageFormat.PNG)?.use { it.bytes } ?: error("PNG encoding failed")
    }
    val file = File(directory, "$name.png").also { it.writeBytes(bytes) }
    val decoded = assertNotNull(ImageIO.read(file))
    if (viewport) { assertEquals(width, decoded.width); assertEquals(height, decoded.height) }
    assertTrue(decoded.width > 0 && decoded.height > 0)
    val tagged = onAllNodes(SemanticsMatcher("has any test tag") {
        it.config.contains(SemanticsProperties.TestTag)
    }, useUnmergedTree = true).fetchSemanticsNodes()
    val geometry = buildJsonObject {
        put("case", name); put("state", state); put("modelState", modelState); put("theme", theme.name)
        put("viewportWidth", width); put("viewportHeight", height); put("captureTarget", target)
        put("imageWidth", decoded.width); put("imageHeight", decoded.height)
        put("sha256", MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
        putJsonArray("nodes") { tagged.forEach { node -> add(buildJsonObject {
            put("tag", node.config[SemanticsProperties.TestTag])
            put("id", node.id)
            put("rootId", generateSequence(node) { it.parent }.last().id)
            put("layoutLeft", node.positionInRoot.x); put("layoutTop", node.positionInRoot.y)
            put("layoutWidth", node.size.width); put("layoutHeight", node.size.height)
            val b = node.boundsInRoot
            put("left", b.left); put("top", b.top); put("right", b.right); put("bottom", b.bottom)
            put("width", b.width); put("height", b.height)
            val window = node.boundsInWindow
            put("windowLeft", window.left); put("windowTop", window.top)
            put("windowRight", window.right); put("windowBottom", window.bottom)
            put("disabled", node.config.contains(SemanticsProperties.Disabled))
            node.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)?.let {
                put("verticalScroll", it.value()); put("verticalScrollMax", it.maxValue())
            }
            node.config.getOrNull(SemanticsProperties.HorizontalScrollAxisRange)?.let {
                put("horizontalScroll", it.value()); put("horizontalScrollMax", it.maxValue())
            }
            node.config.getOrNull(SemanticsProperties.Selected)?.let { put("selected", it) }
            node.config.getOrNull(SemanticsProperties.StateDescription)?.let { put("stateDescription", it) }
            node.config.getOrNull(SemanticsProperties.Text)?.let { put("text", it.joinToString(" | ") { value -> value.text }) }
            node.config.getOrNull(SemanticsProperties.ContentDescription)?.let { put("contentDescription", it.joinToString(" | ")) }
        }) } }
    }
    File(directory, "$name.geometry.json").writeText(Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), geometry))
}
