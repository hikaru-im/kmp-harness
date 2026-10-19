@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package im.hikaru.harness.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import im.hikaru.harness.client.shell.*
import im.hikaru.harness.ui.foundation.HarnessThemeMode
import kotlin.test.*

/** Header search opens the command menu, and the bell swaps the sidebar body for the activity feed. */
@OptIn(ExperimentalTestApi::class)
class WorkspaceNavigatorActionsInteractionTest {
    private class Fixture(
        private val recents: List<RecentTaskItem> =
            listOf(
                RecentTaskItem("koog-slots", "实施 DSH Koog 同步与 UI Slots"),
                RecentTaskItem("clash-rule", "添加 Clash IP 直连规则"),
            ),
    ) {
        var searchOpen by mutableStateOf(false)
        var query by mutableStateOf("")
        var selectedThreadId: String? = null
        var newTaskCalls = 0

        private val projects =
            listOf(
                WorkspaceProjectNode(
                    projectId = "kmp-harness",
                    name = "KMP Harness",
                    children = listOf(WorkspaceItemNode("koog-slots", "实施 DSH Koog 同步与 UI Slots")),
                ),
            )

        @Composable
        fun Content() {
            AppShell(
                themeMode = HarnessThemeMode.Light,
                state =
                    AppShellState(
                        navigation =
                            NavigationState(
                                currentPage = PageId.Home,
                                expandedProjectIds = setOf("kmp-harness"),
                            ),
                        connection = ConnectionIndicatorState.Local,
                    ),
                navItems = harnessRailItems(PageId.Home, accountInitials = "KH"),
                onNavSelect = {},
                navigator = WorkspaceNavigatorState(workspaceLabel = "Codex", workspaceId = "本地", searchQuery = query),
                projectNodes = projects,
                recentTasks = recents,
                onToggleSidebar = {},
                onNewTask = { newTaskCalls++ },
                onNavigatorSearch = { query = it },
                navigatorSearchOpen = searchOpen,
                onToggleNavigatorSearch = { searchOpen = !searchOpen },
                onToggleProject = {},
                onSelectNode = {},
                onSelectRecent = { selectedThreadId = it.id },
                content = {},
            )
        }
    }

    @Test
    fun searchButtonOpensTheCommandMenuInsteadOfFilteringTheSidebar() = runSkikoComposeUiTest(size = Size(1440f, 900f)) {
        val fixture = Fixture()
        setContent { fixture.Content() }
        onNodeWithTag("workspace-command-menu").assertDoesNotExist()
        onNodeWithTag("workspace-search-toggle").performClick()
        onNodeWithTag("workspace-command-menu").assertIsDisplayed()
        onNodeWithTag("workspace-command-input").assertIsDisplayed()
        // The old inline field is gone; the rows behind the menu stay unfiltered.
        onNodeWithTag("navigator-search").assertDoesNotExist()
        onNodeWithTag("command-chat-koog-slots").assertIsDisplayed()
        onNodeWithTag("command-chat-clash-rule").assertIsDisplayed()
        onNodeWithText("快捷操作").assertIsDisplayed()
    }

    @Test
    fun typingFiltersChatsAndChoosingOneClosesTheMenu() = runSkikoComposeUiTest(size = Size(1440f, 900f)) {
        val fixture = Fixture()
        setContent { fixture.Content() }
        onNodeWithTag("workspace-search-toggle").performClick()
        onNodeWithTag("workspace-command-input").performClick()
        onNodeWithTag("workspace-command-input").performTextInput("Clash")
        onNodeWithTag("command-chat-clash-rule").assertIsDisplayed()
        onNodeWithTag("command-chat-koog-slots").assertDoesNotExist()
        onNodeWithTag("command-chat-clash-rule").performClick()
        runOnIdle {
            assertEquals("clash-rule", fixture.selectedThreadId)
            assertFalse(fixture.searchOpen)
            assertEquals("", fixture.query)
        }
        onNodeWithTag("workspace-command-menu").assertDoesNotExist()
        onNodeWithTag("new-task").assertIsDisplayed()
    }

    @Test
    fun escapeClosesTheMenuAndClearsTheQuery() = runSkikoComposeUiTest(size = Size(1440f, 900f)) {
        val fixture = Fixture()
        setContent { fixture.Content() }
        onNodeWithTag("workspace-search-toggle").performClick()
        onNodeWithTag("workspace-command-input").performClick()
        onNodeWithTag("workspace-command-input").performTextInput("Clash")
        onNodeWithTag("workspace-command-input").performKeyInput {
            keyDown(Key.Escape)
            keyUp(Key.Escape)
        }
        onNodeWithTag("workspace-command-menu").assertDoesNotExist()
        runOnIdle {
            assertFalse(fixture.searchOpen)
            assertEquals("", fixture.query)
        }
        onNodeWithTag("new-task").assertIsDisplayed()
    }

    @Test
    fun arrowKeysMoveTheHighlightAndEnterActivatesIt() = runSkikoComposeUiTest(size = Size(1440f, 900f)) {
        val fixture = Fixture()
        setContent { fixture.Content() }
        onNodeWithTag("workspace-search-toggle").performClick()
        onNodeWithTag("workspace-command-input").performKeyInput {
            keyDown(Key.DirectionDown)
            keyUp(Key.DirectionDown)
        }
        onNodeWithTag("workspace-command-input").performKeyInput {
            keyDown(Key.Enter)
            keyUp(Key.Enter)
        }
        runOnIdle { assertEquals("clash-rule", fixture.selectedThreadId) }
        onNodeWithTag("workspace-command-menu").assertDoesNotExist()
    }

    @Test
    fun clickingOutsideTheCardDismissesTheMenu() = runSkikoComposeUiTest(size = Size(1440f, 900f)) {
        val fixture = Fixture()
        setContent { fixture.Content() }
        onNodeWithTag("workspace-search-toggle").performClick()
        onNodeWithTag("workspace-command-menu").assertIsDisplayed()
        onNodeWithTag("workspace-command-scrim").performMouseInput { click(Offset(100f, 100f)) }
        onNodeWithTag("workspace-command-menu").assertDoesNotExist()
        runOnIdle { assertFalse(fixture.searchOpen) }
    }

    @Test
    fun bellSwapsTheSidebarToTheActivityViewAndBack() = runSkikoComposeUiTest(size = Size(1440f, 900f)) {
        val fixture = Fixture()
        setContent { fixture.Content() }
        onNodeWithTag("new-task").assertIsDisplayed()
        onNodeWithTag("workspace-activity-toggle").performClick()
        onNodeWithTag("workspace-activity-view").assertIsDisplayed()
        // Codex keeps New chat above the feed instead of replacing it with the bell state.
        onNodeWithTag("new-task").assertIsDisplayed()
        onNodeWithContentDescription("关闭活动视图").assertIsDisplayed()
        onNodeWithText("暂无未读会话").assertIsDisplayed()
        onNodeWithTag("activity-clash-rule").assertIsDisplayed()
        onNodeWithTag("workspace-activity-toggle").performClick()
        onNodeWithTag("workspace-activity-view").assertDoesNotExist()
        onNodeWithTag("new-task").assertIsDisplayed()
        onNodeWithContentDescription("查看活动").assertIsDisplayed()
    }

    @Test
    fun activityOverflowMenuTogglesPriorityAndRestoresDefaults() = runSkikoComposeUiTest(size = Size(1440f, 900f)) {
        val fixture = Fixture()
        setContent { fixture.Content() }
        onNodeWithTag("workspace-activity-toggle").performClick()
        onNodeWithTag("workspace-activity-menu").assertIsDisplayed().performClick()
        onNodeWithText("显示").assertIsDisplayed()
        onNodeWithText("优先事项部分").assertIsDisplayed()
        onNodeWithText("置顶").assertIsDisplayed()
        onNodeWithText("定时任务").assertIsDisplayed()
        onNodeWithTag("activity-menu-mark-read").assertIsDisplayed().assertIsNotEnabled()
        onNodeWithTag("activity-menu-archive").assertIsDisplayed().assertIsNotEnabled()
        onNodeWithTag("activity-menu-priority").performClick()
        onNodeWithTag("activity-menu-priority").assertDoesNotExist()
        onNodeWithText("暂无未读会话").assertDoesNotExist()
        // The trigger moves to the last remaining section, so the menu stays reachable.
        onNodeWithTag("workspace-activity-menu").assertIsDisplayed().performClick()
        onNodeWithTag("activity-menu-reset").performClick()
        onNodeWithText("暂无未读会话").assertIsDisplayed()
    }

    @Test
    fun activityOverflowMenuCanHidePinnedChats() = runSkikoComposeUiTest(size = Size(1440f, 900f)) {
        val fixture =
            Fixture(
                recents =
                    listOf(
                        RecentTaskItem("koog-slots", "实施 DSH Koog 同步与 UI Slots"),
                        RecentTaskItem("pinned-chat", "固定的聊天", pinned = true),
                        RecentTaskItem("clash-rule", "添加 Clash IP 直连规则"),
                    ),
            )
        setContent { fixture.Content() }
        onNodeWithTag("workspace-activity-toggle").performClick()
        onNodeWithTag("activity-pinned-chat").assertIsDisplayed()
        onNodeWithTag("workspace-activity-menu").performClick()
        onNodeWithTag("activity-menu-pinned").performClick()
        onNodeWithTag("activity-pinned-chat").assertDoesNotExist()
        onNodeWithTag("new-task").assertIsDisplayed()
    }

    @Test
    fun headerButtonsShowTheirHoverTips() = runSkikoComposeUiTest(size = Size(1440f, 900f)) {
        val fixture = Fixture()
        mainClock.autoAdvance = false
        setContent { fixture.Content() }
        onNodeWithTag("workspace-search-toggle").performMouseInput { moveTo(center) }
        mainClock.advanceTimeBy(250)
        onNodeWithTag("navigator-tooltip-workspace-search-toggle").assertIsDisplayed()
        onNodeWithTag("workspace-activity-toggle").performMouseInput { moveTo(center) }
        mainClock.advanceTimeBy(250)
        onNodeWithTag("navigator-tooltip-workspace-activity-toggle").assertIsDisplayed()
    }
}
