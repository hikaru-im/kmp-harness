@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package im.hikaru.harness.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.harness.client.connection.SessionId
import im.hikaru.harness.client.scheduling.*
import im.hikaru.harness.ui.foundation.HarnessTheme
import im.hikaru.harness.ui.foundation.HarnessThemeMode
import im.hikaru.harness.ui.foundation.HarnessTokens
import java.io.File
import javax.imageio.ImageIO
import kotlinx.coroutines.*
import org.jetbrains.skia.EncodedImageFormat
import kotlin.test.*

/** Test-only stores feed the real repository/page. PNGs are structural evidence, not pixel parity. */
@OptIn(ExperimentalTestApi::class)
class ScheduledManagementScreenshotTest {
    @Test fun captureLoading() = captureMatrix(Scenario.Loading)
    @Test fun capturePausedDetail() = captureMatrix(Scenario.PausedDetail)
    @Test fun captureConfirmedHistory() = captureMatrix(Scenario.ConfirmedHistory)
    @Test fun captureUnknownHistory() = captureMatrix(Scenario.UnknownHistory)
    @Test fun captureUnavailable() = captureMatrix(Scenario.Unavailable)

    @Test
    fun taggedControlsRemainInsideViewportAfterScrollingInBothSizesAndThemes() {
        sizes.forEach { size ->
            themes.forEach { (_, theme) ->
                Scenario.entries.forEach { scenario ->
                    val fixture = fixture(scenario)
                    try {
                        runSkikoComposeUiTest(size = Size(size.width.toFloat(), size.height.toFloat())) {
                            setContent { Page(fixture.repository, theme) }
                            val viewport = onNodeWithTag(rootTag).getUnclippedBoundsInRoot()
                            fun assertTagInsideViewport(tag: String) {
                                val target = onNodeWithTag(tag)
                                // Status and empty regions have no scroll ancestor; scrollable content is brought into view.
                                runCatching { target.performScrollTo() }
                                val node = target.assertIsDisplayed()
                                val bounds = node.getUnclippedBoundsInRoot()
                                val label = scenario.toString() + " " + theme + " " + size + ": " + tag
                                assertTrue(bounds.right > bounds.left && bounds.bottom > bounds.top, label)
                                assertTrue(bounds.left >= viewport.left - 1.dp, label + " clips left")
                                assertTrue(bounds.right <= viewport.right + 1.dp, label + " clips right")
                                assertTrue(bounds.top >= viewport.top - 1.dp, label + " clips top")
                                assertTrue(bounds.bottom <= viewport.bottom + 1.dp, label + " clips bottom")
                            }
                            scenario.tags.forEach { assertTagInsideViewport(it) }
                            if (scenario.ledgerTags.isNotEmpty()) {
                                // History rows only exist after entering the ledger from the landing.
                                onNodeWithTag("scheduled-show-history").performScrollTo().performClick()
                                scenario.ledgerTags.forEach { assertTagInsideViewport(it) }
                            }
                            when (scenario) {
                                Scenario.PausedDetail -> {
                                    onNodeWithTag("scheduled-pause").assertIsNotEnabled()
                                    onNodeWithTag("scheduled-run-now").assertIsNotEnabled()
                                }
                                Scenario.ConfirmedHistory ->
                                    onNodeWithTag("scheduled-page-open-run:confirmed").assertIsNotEnabled()
                                Scenario.UnknownHistory -> {
                                    onNodeWithTag("scheduled-page-run:unknown").assertExists()
                                    assertEquals(ScheduledRunPhase.Unknown,
                                        fixture.repository.state.value.effective!!.runs.single().phase)
                                }
                                Scenario.Loading, Scenario.Unavailable ->
                                    onNodeWithTag("scheduled-page-history").assertDoesNotExist()
                            }
                        }
                    } finally {
                        fixture.close()
                    }
                }
            }
        }
    }

    private fun captureMatrix(scenario: Scenario) {
        val directory = captureDir()
        sizes.forEach { size ->
            themes.forEach { (themeSlug, theme) ->
                val fixture = fixture(scenario)
                try {
                    val file = File(directory, scenario.slug + "-" + themeSlug + "-" + size.width + "x" + size.height + ".png")
                    render(file, size) { Page(fixture.repository, theme) }
                    val decoded = requireNotNull(ImageIO.read(file)) { "Unreadable PNG: " + file.absolutePath }
                    assertEquals(size.width, decoded.width, file.name)
                    assertEquals(size.height, decoded.height, file.name)
                    assertTrue(file.length() > 0, "Empty PNG: " + file.absolutePath)
                } finally {
                    fixture.close()
                }
            }
        }
    }

    @Composable
    private fun Page(repository: ScheduledRepository, theme: HarnessThemeMode) {
        HarnessTheme(themeMode = theme) {
            Box(Modifier.fillMaxSize().background(HarnessTokens.Colors.Canvas).semantics { testTag = rootTag }) {
                // No navigation consumer/executor is supplied; those controls stay truthfully disabled.
                ScheduledManagementPage(repository)
            }
        }
    }

    private fun render(file: File, size: CaptureSize, content: @Composable () -> Unit) {
        val scene = ImageComposeScene(width = size.width, height = size.height, density = Density(1f), content = content)
        try {
            scene.render()
            val image = scene.render()
            file.writeBytes(requireNotNull(image.encodeToData(EncodedImageFormat.PNG)) { "PNG encoding failed" }.bytes)
        } finally {
            scene.close()
        }
    }

    private fun fixture(scenario: Scenario): Fixture {
        val scope = ScheduledScope(HostId("screenshot-scheduled-host"), null, 1)
        val target = ScheduledTarget.Cron(ScheduledDestination.Projectless)
        val model = ScheduledModelRef("screenshot-provider", "screenshot-model")
        val context = ScheduledValidationContext(listOf(target), listOf(model))
        val task = ScheduledTask(
            id = "paused", revision = 1, name = "暂停任务 · 测试夹具",
            prompt = "检查工作区并汇总结果。", target = target,
            schedule = ScheduledRule.Hourly(1, 0), model = model, status = ScheduledTaskStatus.Paused,
        )
        val session = SessionId("screenshot-confirmed-session")
        val confirmed = ScheduledRun(
            id = "confirmed", task = task, requestId = "screenshot-confirmed-request", nominalAt = null,
            claimedAt = 1_000, candidateSessionId = session, inputMessageId = "screenshot-confirmed-input",
            phase = ScheduledRunPhase.Succeeded, sessionRef = session, deliveryIntent = true,
            terminal = ScheduledTerminalEvidence(
                turn = 1, userMessageSeq = 1, turnEndSeq = 2, endedAt = 2_000,
                outcome = ScheduledRunPhase.Succeeded, reason = "已确认 TurnEnd · 测试证据",
            ),
        )
        val unknownSession = SessionId("screenshot-unknown-session")
        val unknown = ScheduledRun(
            id = "unknown", task = task, requestId = "screenshot-unknown-request", nominalAt = null,
            claimedAt = 3_000, candidateSessionId = unknownSession, inputMessageId = "screenshot-unknown-input",
            phase = ScheduledRunPhase.Unknown, sessionRef = unknownSession, deliveryIntent = true,
            detail = "缺少持久化 TurnEnd；结果未知，不能重复投递。",
        )
        // Separate history views keep each evidence row readable in the compact screenshot.
        val runs = when (scenario) {
            Scenario.ConfirmedHistory -> listOf(confirmed)
            Scenario.UnknownHistory -> listOf(unknown)
            else -> emptyList()
        }
        val snapshot = ScheduledSnapshot(scope.owner, 1, listOf(task), runs = runs)
        assertNull(ScheduledValidator.snapshotError(snapshot), "Invalid screenshot ledger")
        val worker = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val store = if (scenario == Scenario.Unavailable) UnavailableScheduledStore() else object : ScheduledStore {
            override suspend fun read(owner: ScheduledOwner): ScheduledReadResult {
                check(owner == snapshot.owner) { "Screenshot store owner mismatch" }
                if (scenario == Scenario.Loading) awaitCancellation()
                return ScheduledReadResult.Ready(snapshot)
            }
            override suspend fun compareAndSet(
                owner: ScheduledOwner, expectedRevision: Long, snapshot: ScheduledSnapshot, isCurrentScope: () -> Boolean,
            ): ScheduledWriteResult = error("Passive screenshot fixture must not write")
        }
        val repository = ScheduledRepository(store, ScheduleEngine(calendar))
        repository.bind(scope, context)
        if (scenario == Scenario.Loading) {
            worker.launch(start = CoroutineStart.UNDISPATCHED) { repository.load() }
            assertEquals(ScheduledLoadPhase.Loading, repository.state.value.phase)
            assertNull(repository.state.value.effective)
        } else {
            runBlocking { repository.load() }
            if (scenario == Scenario.Unavailable) {
                assertEquals(ScheduledLoadPhase.Error, repository.state.value.phase)
                assertNull(repository.state.value.effective)
                assertNotNull(repository.state.value.loadError)
            } else {
                assertEquals(ScheduledLoadPhase.Ready, repository.state.value.phase)
                assertEquals(snapshot, repository.state.value.effective)
                if (scenario == Scenario.PausedDetail) repository.selectTask(task.id)
            }
        }
        return Fixture(repository, worker)
    }

    private fun captureDir(): File {
        val root = generateSequence(File(System.getProperty("user.dir") ?: ".").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "docs/desktop-ui").isDirectory }
            ?: error("Cannot locate repository docs/desktop-ui from user.dir")
        return File(root, "docs/desktop-ui/captures/scheduled-execution").apply {
            check(isDirectory || mkdirs()) { "Cannot create capture directory: " + absolutePath }
        }
    }

    private data class Fixture(val repository: ScheduledRepository, val worker: CoroutineScope) {
        fun close() { worker.cancel() }
    }

    private data class CaptureSize(val width: Int, val height: Int)

    // tags are rendered before entering the ledger; ledgerTags require the landing history button.
    private enum class Scenario(val slug: String, val tags: List<String>, val ledgerTags: List<String> = emptyList()) {
        Loading("loading", listOf("scheduled-loading", "scheduled-loading-content")),
        PausedDetail("paused-detail", listOf("scheduled-detail-name", "scheduled-detail-prompt",
            "scheduled-pause", "scheduled-run-now", "scheduled-delete", "scheduled-page-history")),
        ConfirmedHistory("confirmed-history", listOf("scheduled-management-title", "scheduled-show-history"),
            listOf("scheduled-page-history", "scheduled-page-run:confirmed", "scheduled-page-open-run:confirmed")),
        UnknownHistory("unknown-history", listOf("scheduled-management-title", "scheduled-show-history"),
            listOf("scheduled-page-history", "scheduled-page-run:unknown", "scheduled-page-open-run:unknown")),
        Unavailable("unavailable", listOf("scheduled-load-error", "scheduled-page-retry", "scheduled-error-content")),
    }

    private companion object {
        const val rootTag = "scheduled-capture-root"
        val sizes = listOf(CaptureSize(1440, 900), CaptureSize(600, 800))
        val themes = listOf("light" to HarnessThemeMode.Light, "dark" to HarnessThemeMode.Dark)
        val calendar = object : ScheduleCalendar {
            override fun isValidZone(zoneId: String): Boolean = zoneId == "UTC"
            override fun dateAt(epochMillis: Long, zoneId: String): ScheduleDate = error("Hourly fixture")
            override fun plusDays(date: ScheduleDate, days: Int): ScheduleDate = error("Hourly fixture")
            override fun resolve(date: ScheduleDate, hour: Int, minute: Int, zoneId: String): Long = error("Hourly fixture")
        }
    }
}
