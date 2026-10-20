@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package im.hikaru.harness.desktop

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.harness.client.app.HarnessApp
import im.hikaru.harness.client.connection.ConnectionTransport
import im.hikaru.harness.client.conversation.*
import im.hikaru.harness.client.page.*
import im.hikaru.harness.client.projects.*
import im.hikaru.harness.ui.foundation.HarnessTheme
import im.hikaru.harness.ui.foundation.HarnessThemeMode
import java.io.File
import kotlin.test.*

/** Full routing test needs the parent-reviewed HarnessApp wiring patch before execution. */
@OptIn(ExperimentalTestApi::class)
class ProjectsRecentInteractionTest {
    @Test fun pointerRegisterDetailStartPickerCwdMembershipRecentAndThread() =
        pointerRegisterDetailStartPickerCwdMembershipRecentAndThread(1440, 900, HarnessThemeMode.Light)

    @Test fun pointerRegisterDetailStartPickerCwdMembershipRecentAndThread1440x900Dark() =
        pointerRegisterDetailStartPickerCwdMembershipRecentAndThread(1440, 900, HarnessThemeMode.Dark)

    @Test fun pointerRegisterDetailStartPickerCwdMembershipRecentAndThread600x800Light() =
        pointerRegisterDetailStartPickerCwdMembershipRecentAndThread(600, 800, HarnessThemeMode.Light)

    @Test fun pointerRegisterDetailStartPickerCwdMembershipRecentAndThread600x800Dark() =
        pointerRegisterDetailStartPickerCwdMembershipRecentAndThread(600, 800, HarnessThemeMode.Dark)

    private fun pointerRegisterDetailStartPickerCwdMembershipRecentAndThread(
        width: Int, height: Int, theme: HarnessThemeMode,
    ) {
        val f = ProjectsRecentFixture()
        try {
            runSkikoComposeUiTest(size = Size(width.toFloat(), height.toFloat())) {
                val captureDirectory = File(System.getenv("HARNESS_PROJECTS_MAIN_PATH_CAPTURE_DIR")
                    ?.takeIf { it.isNotBlank() } ?: "/tmp/harness-projects-main-path-captures-20261008")
                val viewportTag = "projects-recent-main-path-viewport"
                val caseName = "${width}x$height-${theme.name.lowercase()}"
                val modelItemTag = "menu:conversation-model:${PROJECTS_RECENT_PROVIDER.length}:$PROJECTS_RECENT_PROVIDER:${PROJECTS_RECENT_MODEL.length}:$PROJECTS_RECENT_MODEL"

                fun dismissNavigatorIfOpen() {
                    if (onAllNodesWithTag("navigator-overlay", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()) {
                        // A compact window floats the navigator; the titlebar toggle dismisses it.
                        assertEquals(600, width, "Unexpected floating navigator")
                        toggleSidebarByShortcut()
                        waitUntil(timeoutMillis = 10_000) {
                            onAllNodesWithTag("navigator-overlay", useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
                        }
                    }
                }

                fun pointer(tag: String) {
                    onNodeWithTag(tag).assertIsDisplayed().assertIsEnabled().projectsRecentPointerClick()
                }

                fun capture(checkpoint: String, modelState: String) {
                    dismissNavigatorIfOpen()
                    waitForIdle()
                    onNodeWithTag("navigator-overlay", useUnmergedTree = true).assertDoesNotExist()
                    onNodeWithTag("local-project-editor-save").assertDoesNotExist()
                    onNodeWithTag("local-project-picker-menu").assertDoesNotExist()
                    onNodeWithTag(modelItemTag).assertDoesNotExist()
                    projectsRecentCapture(
                        directory = captureDirectory, name = "$caseName-$checkpoint", state = checkpoint,
                        theme = theme, width = width, height = height,
                        target = viewportTag, viewport = true, modelState = modelState,
                    )
                }

                // Matrix-only offscreen wrapper: capture the complete app at density 1.
                setContent {
                    CompositionLocalProvider(LocalDensity provides Density(1f)) {
                        Box(Modifier.fillMaxSize().testTag(viewportTag)) {
                            HarnessApp(f.connection, connectionTransport = ConnectionTransport.Local,
                                localProjectRepository = f.repository, modelSettingsRepository = f.models, themeMode = theme,
                                shortcutsRepository = remember { loadedMacShortcuts() })
                        }
                    }
                }
                // The home rail owns project selection; "manage" is its 新建项目 row.
                waitUntil(timeoutMillis = 10_000) {
                    onAllNodes(hasTestTag("local-project-picker-open") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
                }
                dismissNavigatorIfOpen()
                pointer("local-project-picker-open")
                pointer("local-project-manage")
                waitUntil(timeoutMillis = 10_000) { projectsRecentHasTag("local-projects-empty") }
                pointer("local-project-create")
                onNodeWithTag("local-project-name-input").assertIsDisplayed().assertIsEnabled()
                    .performTextReplacement("Pointer route project")
                onNodeWithTag("local-project-directory-input").assertIsDisplayed().assertIsEnabled()
                    .performTextReplacement(f.root.toString())
                pointer("local-project-editor-save")
                waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag("local-project-editor-save").fetchSemanticsNodes().isEmpty() }
                val project = f.snapshot().projects.single()
                pointer("local-project-row-${project.id.value}")
                waitUntil(timeoutMillis = 10_000) { projectsRecentHasTag("local-project-detail") }
                onNodeWithTag("local-project-memberships-empty").assertExists()
                onNodeWithTag("local-project-root").assertTextEquals(f.root.toRealPath().toString())
                capture("project-detail", "project=${project.id.value}; memberships=${f.snapshot().memberships.size}; creates=${f.creates.size}; prompts=${f.prompts.size}")
                pointer("local-project-start")
                waitUntil(timeoutMillis = 10_000) { projectsRecentHasTag("local-project-picker-open") }
                dismissNavigatorIfOpen()
                pointer("local-project-picker-open")
                pointer("local-project-picker-item-none")
                onNodeWithTag("local-project-picker-menu").assertDoesNotExist()
                pointer("local-project-picker-open")
                pointer("local-project-picker-item-${project.id.value}")
                onNodeWithTag("local-project-picker-open").assertTextEquals(project.name)
                pointer("live-composer-model")
                pointer(modelItemTag)
                onNodeWithTag(modelItemTag).assertDoesNotExist()
                capture("home-selected", "project=${project.id.value}; provider=$PROJECTS_RECENT_PROVIDER; model=$PROJECTS_RECENT_MODEL; creates=${f.creates.size}; prompts=${f.prompts.size}")
                onNodeWithTag("live-composer-editor").assertIsDisplayed().assertIsEnabled()
                    .performTextReplacement("Run in the registered directory")
                pointer("live-composer-send")
                waitUntil(timeoutMillis = 10_000) { f.prompts.size == 1 }
                waitUntil(timeoutMillis = 10_000) { projectsRecentHasTag("live-thread") }
                assertEquals(f.root.toRealPath().toString(), f.creates.single().options.cwd)
                assertTrue(f.membershipWasDurableAtPrompt)
                val membership = f.snapshot().memberships.single()
                assertEquals(project.id, membership.projectId)
                assertEquals(f.sessions.single().id.value, membership.session.id.value)
                capture("thread-after-send", "project=${project.id.value}; session=${membership.session.id.value}; creates=${f.creates.size}; prompts=${f.prompts.size}; membershipWasDurableAtPrompt=${f.membershipWasDurableAtPrompt}")
                pointer("rail-library")
                waitUntil(timeoutMillis = 10_000) { projectsRecentHasTag("library-page") }
                dismissNavigatorIfOpen()
                onNodeWithTag("library-unavailable").assertIsDisplayed()
                capture("library-placeholder", "sessions=${f.sessions.size}; memberships=${f.snapshot().memberships.size}")
                // 主页 is the new-chat page, not "back to the last thread"; the created session
                // stays in the navigator's recent list instead of staying open.
                pointer("rail-home")
                waitUntil(timeoutMillis = 10_000) { projectsRecentHasTag("live-home") }
                assertEquals(1, f.creates.size)
                assertEquals(1, f.prompts.size)
            }
        } finally { f.close() }
    }

    @Test fun distinctProjectPhasesDoNotSearchFailedSnapshots() {
        for (phase in LocalProjectsPhase.entries) runSkikoComposeUiTest(size = Size(600f, 800f)) {
            var query by mutableStateOf("")
            setContent { HarnessTheme(HarnessThemeMode.Light) {
                LocalProjectsPage(ProjectsRecentDisplayData.projects(null, phase), query, { query = it }, {}, {}, {})
            } }
            onNodeWithTag("local-project-status-${phase.name.lowercase()}").assertExists()
            if (phase in listOf(LocalProjectsPhase.Ready, LocalProjectsPhase.Empty)) {
                onNodeWithTag("local-project-search").assertIsEnabled().performTextReplacement("no-match")
                onNodeWithTag(if (phase == LocalProjectsPhase.Empty) "local-projects-empty" else "local-projects-no-results").assertExists()
            } else {
                onNodeWithTag("local-project-search").assertIsNotEnabled()
                onNodeWithTag("local-project-row-project-0").assertDoesNotExist()
            }
        }
    }


    @Test fun headerBandKeepsActionsBesideTheTitleUntilTheNarrowStackThreshold() {
        val state = ProjectsRecentDisplayData.projects(null)
        var wideActionsLeft = 0f
        for (width in listOf(1440, 600)) runSkikoComposeUiTest(size = Size(width.toFloat(), 800f)) {
            setContent { CompositionLocalProvider(LocalDensity provides Density(1f)) {
                HarnessTheme(HarnessThemeMode.Light) {
                    Box(Modifier.fillMaxSize().padding(start = 336.dp)) {
                        LocalProjectsPage(state, "", {}, {}, {}, {})
                    }
                }
            } }
            waitForIdle()
            val header = onNodeWithTag("local-project-header").fetchSemanticsNode().boundsInRoot
            val create = onNodeWithTag("local-project-create").fetchSemanticsNode().boundsInRoot
            val refresh = onNodeWithTag("local-project-refresh").fetchSemanticsNode().boundsInRoot
            assertEquals(create.height, refresh.height, 1f)
            // Both actions share one row in every width class: the page never interleaves them.
            assertEquals(create.top, refresh.top, 1f)
            assertTrue(create.left >= header.left - 1f && refresh.right <= header.right + 1f,
                "Actions stay inside the header band: header=$header create=$create refresh=$refresh")
            if (width == 1440) {
                // Wide band: the actions sit beside the title, right of the text column.
                wideActionsLeft = create.left
                assertTrue(create.left > header.width / 2f,
                    "Wide band keeps the actions beside the title: header=$header create=$create")
            } else {
                // Narrow band: the actions share the title's left edge on their own stacked row.
                assertEquals(header.left, create.left, 1f)
                assertTrue(create.left < wideActionsLeft,
                    "Narrow band drops the actions to their own left-aligned row: header=$header create=$create")
            }
        }
    }

    @Test fun listRowBandsStaySingleLineForLongNamesAndPaths() {
        runSkikoComposeUiTest(size = Size(1440f, 900f)) {
            setContent { CompositionLocalProvider(LocalDensity provides Density(1f)) {
                HarnessTheme(HarnessThemeMode.Dark) {
                    Box(Modifier.fillMaxSize().padding(start = 336.dp)) {
                        LocalProjectsPage(ProjectsRecentDisplayData.projects(null), "", {}, {}, {}, {})
                    }
                }
            } }
            waitForIdle()
            val first = onNodeWithTag("local-project-row-project-0").fetchSemanticsNode().boundsInRoot
            val second = onNodeWithTag("local-project-row-project-1").fetchSemanticsNode().boundsInRoot
            // The fixture's longest name and a path far wider than the row must both ellipsize into the
            // single-line band instead of growing the row. The longest title still rounds one line of
            // glyphs 4dp taller than the short titles, so the band is bounded rather than equal.
            assertTrue(first.height < 48f && second.height < 48f,
                "Long value rows keep the single-line band: first=$first second=$second")
            assertTrue(kotlin.math.abs(first.height - second.height) <= 4f,
                "The longest title adds no second line: first=$first second=$second")
        }
    }

    @Test fun projectsLongNameRootSearchAndPointerWheelReachLastOfTwentyFour() {
        runSkikoComposeUiTest(size = Size(600f, 800f)) {
            var query by mutableStateOf("")
            val opened = mutableListOf<LocalProjectId>()
            val state = ProjectsRecentDisplayData.projects(null)
            setContent { HarnessTheme(HarnessThemeMode.Light) {
                LocalProjectsPage(state, query, { query = it }, {}, {}, { opened += it })
            } }
            onNodeWithTag("local-project-row-project-0").assertTextContains(ProjectsRecentDisplayData.longName)
            projectsRecentWheelTo("local-projects-page", "local-project-row-project-23")
            onNodeWithTag("local-project-row-project-23").projectsRecentPointerClick()
            assertEquals(listOf(LocalProjectId("project-23")), opened)
            projectsRecentWheelTo("local-projects-page", "local-project-search", delta = -600f)
            onNodeWithTag("local-project-search").performTextReplacement("超长项目名称")
            onNodeWithTag("local-project-row-project-0").assertExists()
            onNodeWithTag("local-project-row-project-23").assertDoesNotExist()
            onNodeWithTag("local-project-search").performTextReplacement("${ProjectsRecentDisplayData.longRoot}/23")
            onNodeWithTag("local-project-row-project-23").assertExists()
            onNodeWithTag("local-project-row-project-0").assertDoesNotExist()
        }
    }

    @Test fun narrowDetailActionsWrapAndPointerStartUsesRealSelectedId() {
        val f = ProjectsRecentFixture()
        try {
            runSkikoComposeUiTest(size = Size(600f, 800f)) {
                val base = ProjectsRecentDisplayData.projects(ProjectsRecentDisplayData.binding(f.connection))
                val project = base.projects.first()
                val state = base.copy(snapshot = base.snapshot!!.copy(projects = listOf(project)))
                val starts = mutableListOf<LocalProjectId>()
                var pageDensity: Density? = null
                setContent { CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    HarnessTheme(HarnessThemeMode.Light) {
                        pageDensity = LocalDensity.current
                        Box(Modifier.fillMaxSize().padding(start = 336.dp)) {
                            LocalProjectDetailPage(state, project.id, emptyList(), {}, { starts += it }, {}, {})
                        }
                    }
                } }
                projectsRecentWheelTo("local-project-detail", "local-project-bind")
                waitForIdle()
                mainClock.advanceTimeByFrame()
                waitForIdle()
                onNodeWithTag("local-project-name").assertTextEquals(project.name)
                onNodeWithTag("local-project-root").assertTextEquals(project.roots.single().path)
                val density = assertNotNull(pageDensity)
                assertEquals(1f, density.density)
                val startNode = onNodeWithTag("local-project-start").assertIsDisplayed().assertIsEnabled().fetchSemanticsNode()
                val bindNode = onNodeWithTag("local-project-bind").assertIsDisplayed().assertIsEnabled().fetchSemanticsNode()
                val start = startNode.boundsInRoot
                val bind = bindNode.boundsInRoot
                assertTrue(bind.top >= start.bottom,
                    "Compact content wraps action buttons: density=${density.density}, fontScale=${density.fontScale}, start=$start, bind=$bind")
                assertTrue(start.left >= 336f && start.right <= 600f && bind.right <= 600f)
                assertEquals(startNode.size.height.toFloat(), start.height, 1f)
                assertEquals(bindNode.size.height.toFloat(), bind.height, 1f)
                onNodeWithTag("local-project-start").projectsRecentPointerClick()
                assertEquals(listOf(project.id), starts)
            }
        } finally { f.close() }
    }


    @Test fun pickerLongNameScrollSelectionDismissAndOldCallbackAreFenced() {
        val f = ProjectsRecentFixture()
        try {
            runSkikoComposeUiTest(size = Size(600f, 800f)) {
                var state by mutableStateOf(ProjectsRecentDisplayData.projects(ProjectsRecentDisplayData.binding(f.connection)))
                var currentBinding by mutableStateOf(state.binding)
                val selected = mutableListOf<LocalProjectId?>()
                setContent { HarnessTheme(HarnessThemeMode.Light) { LocalProjectPicker(state, {
                    selected += it; state = state.copy(selectedProjectId = it)
                }, {}, true, currentBinding = currentBinding) } }
                val anchor = onNodeWithTag("local-project-picker-open").fetchSemanticsNode().boundsInRoot
                assertTrue(anchor.width <= 280f)
                onNodeWithTag("local-project-picker-open").projectsRecentPointerClick()
                onNodeWithTag("local-project-picker-item-project-0").assertIsSelected()
                val staleClick = onNodeWithTag("local-project-picker-item-project-0").fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
                repeat(8) { onNodeWithTag("local-project-picker-menu").performMouseInput { moveTo(center); scroll(500f) } }
                onNodeWithTag("local-project-picker-item-project-23").assertIsDisplayed().projectsRecentPointerClick()
                onNodeWithTag("local-project-picker-menu").assertDoesNotExist()
                assertEquals(LocalProjectId("project-23"), selected.single())
                val retainedState = state
                onNodeWithTag("local-project-picker-open").projectsRecentPointerClick()
                // Change only the caller binding: the controller still owns its old binding/snapshot.
                runOnIdle { currentBinding = ProjectsRecentDisplayData.binding(f.connection, 2) }
                onNodeWithTag("local-project-picker-menu").assertDoesNotExist()
                onNodeWithTag("local-project-picker-open").assertTextEquals("默认工作目录").assertIsNotEnabled()
                onNodeWithTag("local-project-picker-root").assertDoesNotExist()
                onNodeWithTag("local-project-picker-item-project-0").assertDoesNotExist()
                onNodeWithTag("local-project-picker-item-project-23").assertDoesNotExist()
                onNodeWithText(ProjectsRecentDisplayData.longName).assertDoesNotExist()
                onNodeWithText(retainedState.selectedProject!!.roots.single().path).assertDoesNotExist()
                runOnIdle { staleClick() }
                assertEquals(1, selected.size)
                assertEquals(retainedState, state)
                runOnIdle { currentBinding = currentBinding!!.copy(scope = LocalProjectScope(HostId("another-project-host"), 2)) }
                onNodeWithTag("local-project-picker-open").assertTextEquals("默认工作目录").assertIsNotEnabled()
                onNodeWithTag("local-project-picker-root").assertDoesNotExist()
                runOnIdle { staleClick() }
                assertEquals(1, selected.size)
                assertEquals(retainedState, state)
            }
        } finally { f.close() }
    }

    @Test fun pickerFailedReadsAndRemovedOptionsRejectCapturedSelection() {
        val f = ProjectsRecentFixture()
        try {
            runSkikoComposeUiTest(size = Size(600f, 800f)) {
                val ready = ProjectsRecentDisplayData.projects(ProjectsRecentDisplayData.binding(f.connection))
                var state by mutableStateOf(ready)
                val selected = mutableListOf<LocalProjectId?>()
                setContent { HarnessTheme(HarnessThemeMode.Light) {
                    LocalProjectPicker(state, { selected += it }, {}, true, currentBinding = ready.binding)
                } }
                onNodeWithTag("local-project-picker-open").projectsRecentPointerClick()
                val staleClick = onNodeWithTag("local-project-picker-item-project-0").fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
                for (phase in LocalProjectsPhase.entries.filter { it !in listOf(LocalProjectsPhase.Ready, LocalProjectsPhase.Empty) }) {
                    runOnIdle { state = ProjectsRecentDisplayData.projects(ready.binding, phase) }
                    assertEquals(ready.snapshot, state.snapshot, "The failed read keeps the controller snapshot")
                    onNodeWithTag("local-project-status-${phase.name.lowercase()}").assertExists()
                    onNodeWithTag("local-project-picker-open").assertTextEquals("默认工作目录").assertIsNotEnabled()
                    onNodeWithTag("local-project-picker-root").assertDoesNotExist()
                    onNodeWithTag("local-project-picker-item-project-0").assertDoesNotExist()
                    onNodeWithTag("local-project-picker-menu").assertDoesNotExist()
                    runOnIdle { staleClick() }
                    assertTrue(selected.isEmpty())
                }
                runOnIdle { state = ready.copy(snapshot = ready.snapshot!!.copy(projects = ready.snapshot!!.projects.drop(1))) }
                onNodeWithTag("local-project-picker-open").assertIsEnabled()
                runOnIdle { staleClick() }
                assertTrue(selected.isEmpty(), "A removed project cannot be selected by an old callback")
            }
        } finally { f.close() }
    }

    @Test fun detailFailedReadsHideRetainedSnapshotAndRejectStartBindAndOpen() {
        val f = ProjectsRecentFixture()
        try {
            runSkikoComposeUiTest(size = Size(600f, 800f)) {
                val ready = ProjectsRecentDisplayData.projects(ProjectsRecentDisplayData.binding(f.connection), memberships = true)
                val project = ready.projects.first()
                var state by mutableStateOf(ready)
                var starts = 0
                var binds = 0
                var opens = 0
                setContent { HarnessTheme(HarnessThemeMode.Light) {
                    LocalProjectDetailPage(state, project.id, ProjectsRecentDisplayData.sessions,
                        { binds++ }, { starts++ }, { opens++ }, {})
                } }
                val staleStart = onNodeWithTag("local-project-start").fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
                val staleBind = onNodeWithTag("local-project-bind").fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
                val sessionTag = "local-project-session-${ready.snapshot!!.memberships.first().session.id.value}"
                val staleOpen = onNodeWithTag(sessionTag).fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
                for (phase in LocalProjectsPhase.entries.filter { it !in listOf(LocalProjectsPhase.Ready, LocalProjectsPhase.Empty) }) {
                    runOnIdle { state = ProjectsRecentDisplayData.projects(ready.binding, phase, memberships = true) }
                    assertEquals(ready.snapshot, state.snapshot)
                    onNodeWithTag("local-project-status-${phase.name.lowercase()}").assertExists()
                    onNodeWithTag("local-project-detail-unavailable").assertExists()
                    onNodeWithTag("local-project-name").assertDoesNotExist()
                    onNodeWithTag("local-project-root").assertDoesNotExist()
                    onNodeWithTag("local-project-start").assertDoesNotExist()
                    onNodeWithTag("local-project-bind").assertDoesNotExist()
                    onNodeWithTag(sessionTag).assertDoesNotExist()
                    runOnIdle { staleStart(); staleBind(); staleOpen() }
                    assertEquals(0, starts)
                    assertEquals(0, binds)
                    assertEquals(0, opens)
                }
            }
        } finally { f.close() }
    }

    @Test fun editorBusyClosePreservesPendingThenExactRetryConfirmsOneDurableProject() {
        val f = ProjectsRecentFixture()
        val repository = ProjectsRecentUnknownCreate(f.repository)
        try {
            runSkikoComposeUiTest(size = Size(600f, 800f)) {
                var controller: LocalProjectsController? = null
                var open by mutableStateOf(true)
                setContent {
                    val scope = rememberCoroutineScope()
                    val c = remember { LocalProjectsController(scope, repository).also { controller = it } }
                    DisposableEffect(c) { onDispose { c.close() } }
                    LaunchedEffect(c) { c.bind(LocalProjectBinding(f.connection, LocalProjectScope(f.host, 1), ConnectionTransport.Local)) }
                    val state by c.state.collectAsState()
                    HarnessTheme(HarnessThemeMode.Light) {
                        if (open) LocalProjectEditorDialog(state, null, { open = false }, { c.create(it) }, { id, path -> c.bindDirectory(id, path) }, { c.retryCreate() })
                    }
                }
                waitUntil(timeoutMillis = 10_000) { controller?.state?.value?.phase == LocalProjectsPhase.Empty }
                onNodeWithTag("local-project-name-input").performTextReplacement("Unknown result")
                onNodeWithTag("local-project-directory-input").performTextReplacement(f.root.toString())
                val staleSave = onNodeWithTag("local-project-editor-save").fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
                val staleClose = onNodeWithTag("local-project-editor-close").fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
                onNodeWithTag("local-project-editor-save").projectsRecentPointerClick()
                waitUntil(timeoutMillis = 10_000) { repository.entered.isCompleted }
                onNodeWithTag("local-project-editor-save").assertIsNotEnabled().projectsRecentPointerClick()
                val pending = assertNotNull(controller!!.state.value.pendingCreate)
                onNodeWithTag("local-project-editor-close").projectsRecentPointerClick()
                assertEquals(pending, controller!!.state.value.pendingCreate)
                repository.release.complete(Unit)
                waitUntil(timeoutMillis = 10_000) { controller!!.state.value.mutation == null }
                assertEquals(1, f.snapshot().projects.size)
                runOnIdle { open = true }
                onNodeWithTag("local-project-editor-save").assertTextEquals("重试确认")
                onNodeWithTag("local-project-name-input").assertIsNotEnabled()
                onNodeWithTag("local-project-editor-save").projectsRecentPointerClick()
                waitUntil(timeoutMillis = 10_000) { controller!!.state.value.pendingCreate == null }
                assertEquals(listOf(pending, pending), repository.requests.toList())
                assertEquals(1, f.snapshot().projects.size)
                assertEquals(pending.requestId, f.snapshot().createReceipts.single().request.requestId)
                runOnIdle { controller!!.bind(LocalProjectBinding(f.connection, LocalProjectScope(f.host, 2), ConnectionTransport.Local)) }
                waitUntil(timeoutMillis = 10_000) { controller!!.state.value.phase == LocalProjectsPhase.Ready }
                runOnIdle { staleSave(); staleClose() }
                assertEquals(2, repository.requests.size)
                assertTrue(open, "Old editor dismissal must not close the new binding's dialog")
            }
        } finally { repository.release.complete(Unit); f.close() }
    }

    @Test fun detailFullRootWrappedActionsAndUnavailableMembershipNeverRestore() {
        val f = ProjectsRecentFixture()
        try {
            runSkikoComposeUiTest(size = Size(600f, 800f)) {
                val state = ProjectsRecentDisplayData.projects(ProjectsRecentDisplayData.binding(f.connection), memberships = true)
                var opens = 0
                var clipboardText: (() -> String?)? = null
                setContent {
                    val clipboard = LocalClipboardManager.current
                    SideEffect { clipboardText = { clipboard.getText()?.text } }
                    HarnessTheme(HarnessThemeMode.Dark) { LocalProjectDetailPage(state, state.projects.first().id,
                        emptyList(), {}, {}, { opens++ }, {}) }
                }
                onNodeWithTag("local-project-name").assertTextEquals(state.projects.first().name)
                projectsRecentWheelTo("local-project-detail", "local-project-name")
                val nameScroll = onNodeWithTag("local-project-name").fetchSemanticsNode()
                    .config[androidx.compose.ui.semantics.SemanticsProperties.HorizontalScrollAxisRange].value()
                onNodeWithTag("local-project-name").performMouseInput { moveTo(center); scroll(800f, ScrollWheel.Horizontal) }
                assertTrue(onNodeWithTag("local-project-name").fetchSemanticsNode()
                    .config[androidx.compose.ui.semantics.SemanticsProperties.HorizontalScrollAxisRange].value() > nameScroll)
                onNodeWithTag("local-project-root").assertTextEquals(state.projects.first().roots.single().path)
                projectsRecentWheelTo("local-project-detail", "local-project-root-copy")
                onNodeWithTag("local-project-root-copy").projectsRecentPointerClick()
                runOnIdle { assertEquals(state.projects.first().roots.single().path, clipboardText!!()) }
                onNodeWithTag("local-project-memberships-not-live").assertExists()
                projectsRecentWheelTo("local-project-detail", "local-project-start")
                val start = onNodeWithTag("local-project-start").fetchSemanticsNode().boundsInRoot
                val bind = onNodeWithTag("local-project-bind").fetchSemanticsNode().boundsInRoot
                assertTrue(start.left >= 0f && start.right <= 600f && bind.left >= 0f && bind.right <= 600f)
                val nonLiveTag = "local-project-session-${state.snapshot!!.memberships.first().session.id.value}"
                projectsRecentWheelTo("local-project-detail", nonLiveTag)
                onNodeWithTag(nonLiveTag).projectsRecentPointerClick()
                assertEquals(0, opens)
            }
        } finally { f.close() }
    }
}

@OptIn(ExperimentalTestApi::class)
private fun SemanticsNodeInteractionsProvider.projectsRecentHasTag(tag: String) = onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
