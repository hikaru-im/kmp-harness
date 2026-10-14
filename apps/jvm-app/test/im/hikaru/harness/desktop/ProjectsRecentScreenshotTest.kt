@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package im.hikaru.harness.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import im.hikaru.harness.client.conversation.ConversationDataPhase
import im.hikaru.harness.client.page.*
import im.hikaru.harness.client.projects.*
import im.hikaru.harness.ui.foundation.*
import java.io.File
import kotlin.test.*

/**
 * Test-only display snapshots exercise production page APIs. Four density-1 theme/size variants
 * per case; geometry/PNG metadata is emitted for every viewport and separate popup capture.
 * This is structural evidence after parent execution, not a pixel-parity claim.
 */
@OptIn(ExperimentalTestApi::class)
class ProjectsRecentScreenshotTest {
    private enum class Case {
        ProjectsUnbound, ProjectsLoading, ProjectsEmpty, ProjectsReady, ProjectsError,
        ProjectsUnavailable, ProjectsUnsupported, ProjectsAccessDenied, ProjectsNoResults,
        DetailNoMembership, DetailNonLiveMembership, DetailLiveMembership, DetailFailedSnapshot,
        HomePicker, HomePickerMenu, HomePickerMenuScrolled, HomePickerFailedSnapshot, HomePickerBindingFence,
    }

    @Test fun projectsStatesAndNoResults() = capture(Case.entries.filter { it.name.startsWith("Projects") })
    @Test fun detailMembershipStates() = capture(Case.entries.filter { it.name.startsWith("Detail") })
    @Test fun homePickerAndScrolledPopup() = capture(Case.entries.filter { it.name.startsWith("Home") })

    private fun capture(cases: List<Case>) {
        val output = File(System.getProperty("java.io.tmpdir"), "harness-projects-recent-screenshots-20261008")
        for (case in cases) for ((width, height) in listOf(1440 to 900, 600 to 800)) {
            for (theme in listOf(HarnessThemeMode.Light, HarnessThemeMode.Dark)) {
                val f = ProjectsRecentFixture()
                try {
                    runSkikoComposeUiTest(size = Size(width.toFloat(), height.toFloat())) {
                        val binding = ProjectsRecentDisplayData.binding(f.connection)
                        var query by mutableStateOf(if (case == Case.ProjectsNoResults) "no-match" else "")
                        var selected by mutableStateOf<LocalProjectId?>(ProjectsRecentDisplayData.projects.first().id)
                        var capturedModel = ""
                        setContent {
                            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                                HarnessTheme(themeMode = theme) {
                                    Box(Modifier.fillMaxSize().background(HarnessTokens.Colors.Canvas).testTag("projects-recent-viewport")) {
                                        // Reserve the rail/navigator width at both sizes, including the narrow 264px pane.
                                        Box(Modifier.fillMaxSize().padding(start = 336.dp)) {
                                            when {
                                                case.name.startsWith("Projects") -> {
                                                    val phase = when (case) {
                                                        Case.ProjectsUnbound -> LocalProjectsPhase.Unbound
                                                        Case.ProjectsLoading -> LocalProjectsPhase.Loading
                                                        Case.ProjectsEmpty -> LocalProjectsPhase.Empty
                                                        Case.ProjectsError -> LocalProjectsPhase.Error
                                                        Case.ProjectsUnavailable -> LocalProjectsPhase.Unavailable
                                                        Case.ProjectsUnsupported -> LocalProjectsPhase.Unsupported
                                                        Case.ProjectsAccessDenied -> LocalProjectsPhase.AccessDenied
                                                        else -> LocalProjectsPhase.Ready
                                                    }
                                                    val state = ProjectsRecentDisplayData.projects(binding.takeUnless { phase == LocalProjectsPhase.Unbound }, phase)
                                                    capturedModel = "$state; query=$query"
                                                    LocalProjectsPage(state,
                                                        query, { query = it }, {}, {}, {})
                                                }
                                                case.name.startsWith("Detail") -> {
                                                    val state = ProjectsRecentDisplayData.projects(binding,
                                                        phase = if (case == Case.DetailFailedSnapshot) LocalProjectsPhase.Error else LocalProjectsPhase.Ready,
                                                        memberships = case != Case.DetailNoMembership)
                                                    capturedModel = "$state; liveSessions=${if (case == Case.DetailLiveMembership) ProjectsRecentDisplayData.sessions else emptyList()}"
                                                    LocalProjectDetailPage(state, ProjectsRecentDisplayData.projects.first().id,
                                                        if (case == Case.DetailLiveMembership) ProjectsRecentDisplayData.sessions else emptyList(), {}, {}, {}, {})
                                                }
                                                case.name.startsWith("Home") -> Column(Modifier.fillMaxSize().padding(16.dp),
                                                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                                    HarnessText("Home · 项目选择", style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
                                                    val state = ProjectsRecentDisplayData.projects(binding,
                                                        phase = if (case == Case.HomePickerFailedSnapshot) LocalProjectsPhase.Error else LocalProjectsPhase.Ready)
                                                        .copy(selectedProjectId = selected)
                                                    val currentBinding = if (case == Case.HomePickerBindingFence)
                                                        ProjectsRecentDisplayData.binding(f.connection, 2) else binding
                                                    capturedModel = "$state; currentBinding=$currentBinding"
                                                    LocalProjectPicker(state, { selected = it }, {}, true, currentBinding = currentBinding)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (case in listOf(Case.HomePickerMenu, Case.HomePickerMenuScrolled)) {
                            onNodeWithTag("local-project-picker-open").projectsRecentPointerClick()
                            if (case == Case.HomePickerMenuScrolled) {
                                repeat(8) { onNodeWithTag("local-project-picker-menu").performMouseInput { moveTo(center); scroll(500f) } }
                                onNodeWithTag("local-project-picker-item-project-23").assertIsDisplayed()
                            }
                        }
                        if (case == Case.DetailFailedSnapshot) {
                            onNodeWithTag("local-project-status-error").assertExists()
                            onNodeWithTag("local-project-detail-unavailable").assertExists()
                            onNodeWithTag("local-project-name").assertDoesNotExist()
                            onNodeWithTag("local-project-root").assertDoesNotExist()
                            onNodeWithTag("local-project-start").assertDoesNotExist()
                            onNodeWithTag("local-project-bind").assertDoesNotExist()
                        }
                        if (case in listOf(Case.HomePickerFailedSnapshot, Case.HomePickerBindingFence)) {
                            onNodeWithTag("local-project-status-${if (case == Case.HomePickerFailedSnapshot) "error" else "unbound"}").assertExists()
                            onNodeWithTag("local-project-picker-open").assertTextEquals("默认工作目录").assertIsNotEnabled()
                            onNodeWithTag("local-project-picker-root").assertDoesNotExist()
                            onNodeWithTag("local-project-picker-menu").assertDoesNotExist()
                            onNodeWithTag("local-project-picker-item-project-0").assertDoesNotExist()
                        }
                        val name = "${case.name}-${width}x$height-${theme.name.lowercase()}"
                        assertTrue(capturedModel.isNotEmpty())
                        projectsRecentCapture(output, name, case.name, theme, width, height, modelState = capturedModel)
                        if (case in listOf(Case.HomePickerMenu, Case.HomePickerMenuScrolled)) {
                            val menu = onNodeWithTag("local-project-picker-menu").fetchSemanticsNode().boundsInRoot
                            assertTrue(menu.width <= 320f && menu.height <= 320f)
                            projectsRecentCapture(output, "$name-popup", case.name, theme, width, height,
                                target = "local-project-picker-menu", viewport = false, modelState = capturedModel)
                        }
                    }
                } finally { f.close() }
            }
        }
    }
}
