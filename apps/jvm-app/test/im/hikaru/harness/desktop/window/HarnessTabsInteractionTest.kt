package im.hikaru.harness.desktop.window

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.dp
import im.hikaru.harness.client.page.NewTabPage
import im.hikaru.harness.client.page.NewTabTool
import im.hikaru.harness.client.shell.*
import im.hikaru.harness.ui.foundation.*
import java.io.File
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import kotlin.test.*

/**
 * The window's tab strip. The strip is the **side panel's** control, so its tabs are the panel's tool
 * pages (新标签页 / 变更) and the conversation — 新对话页 or a 会话 — is the other pane, which is not a tab
 * and is drawn from the shell's own page. The state therefore starts *closed*, the way the reference
 * opens on the new chat, and most cases here open the panel first.
 */
@OptIn(ExperimentalTestApi::class)
class HarnessTabsInteractionTest {
    @Test fun sharedResizeFeedbackFollowsThemeAndReturnsToIdle() {
        listOf(HarnessThemeMode.Light, HarnessThemeMode.Dark).forEach { mode ->
            runSkikoComposeUiTest(size = Size(240f, 160f)) {
                var movement = 0f
                setContent {
                    HarnessTheme(themeMode = mode) {
                        Box(Modifier.fillMaxSize().background(HarnessTokens.Colors.Canvas).testTag("resize-root")) {
                            HarnessResizeHandle(onResize = { movement += it }, modifier = Modifier.testTag("resize-handle"))
                        }
                    }
                }
                val line = onNodeWithTag("resize-handle").fetchSemanticsNode().boundsInRoot.center
                fun brightness() = onNodeWithTag("resize-root").captureToImage().lumaAt(line.x.toInt(), line.y.toInt())
                val idle = brightness()
                onNodeWithTag("resize-handle").performMouseInput { moveTo(center) }
                val active = brightness()
                if (mode == HarnessThemeMode.Dark) assertTrue(active > idle) else assertTrue(active < idle)
                runOnIdle { assertEquals(0f, movement, "hover does not resize") }
                onNodeWithTag("resize-root").performMouseInput { moveTo(Offset(200f, 80f)) }
                assertEquals(idle, brightness(), "leaving the handle restores the idle line")
            }
        }
    }

    @Test fun windowsNavigationLivesAboveDockAndIsNotDuplicated() = runSkikoComposeUiTest(size = Size(1200f, 600f)) {
        val state = HarnessTabStripState(HarnessFixtureTabs, HarnessFixtureTabs.first().id)
        val drag = HarnessTabDragState()
        var toggles = 0
        setContent {
            HarnessTheme {
                Column {
                    Box(Modifier.height(HarnessTokens.Control.Titlebar)) {
                        HarnessWindowNavigation(state, drag, null, { toggles++ }, null, null, { this })
                    }
                    HarnessPaneHeader(state, drag, panel = true, title = null)
                }
            }
        }
        onAllNodesWithTag("harness-tab-back").assertCountEquals(1)
        onAllNodesWithTag("harness-tab-forward").assertCountEquals(1)
        onAllNodesWithTag("harness-tab-navigator").assertCountEquals(1)
        val navigation = onNodeWithTag("harness-tab-navigator").fetchSemanticsNode().boundsInRoot
        val dock = onNodeWithTag("harness-pane-header").fetchSemanticsNode().boundsInRoot
        assertTrue(navigation.bottom <= dock.top)
        onNodeWithTag("harness-tab-navigator").performMouseInput { click() }
        runOnIdle { assertEquals(1, toggles) }
    }

    @Test fun homePaneDoesNotReserveAnEmptyHeader() = runSkikoComposeUiTest(size = Size(1200f, 600f)) {
        val state = HarnessTabStripState(HarnessFixtureTabs, HarnessFixtureTabs.first().id)
        val drag = HarnessTabDragState()
        setContent {
            HarnessTheme {
                val region = rememberHarnessTabMainRegion(state, drag, paneHeaders = true)
                region.Panes(Modifier.fillMaxSize()) { modifier -> Box(modifier.testTag("home-body")) }
            }
        }
        onNodeWithTag("harness-pane-header").assertDoesNotExist()
        assertEquals(0f, onNodeWithTag("home-body").fetchSemanticsNode().boundsInRoot.top)
    }

    private fun state() = HarnessTabStripState(HarnessFixtureTabs, HarnessFixtureTabs.first().id)

    @Test fun paneHeadersFollowToolVisibilityAndFullView() = runSkikoComposeUiTest(size = Size(1200f, 600f)) {
        val state = state()
        val drag = HarnessTabDragState()
        setContent {
            HarnessTheme {
                Column {
                    Box(Modifier.height(HarnessTokens.Control.Titlebar)) {
                        HarnessWindowNavigation(state, drag, null, null, null, null, { this })
                    }
                    val region = rememberHarnessTabMainRegion(state, drag, paneHeaders = true)
                    region.Panes(Modifier.weight(1f)) { modifier ->
                        Box(modifier.testTag(if (LocalHarnessPaneTab.current == null) "conversation-body" else "tool-body"))
                    }
                }
            }
        }
        onNodeWithTag("harness-pane-header").assertDoesNotExist()
        onNodeWithTag("harness-tab-show").performClick()
        onAllNodesWithTag("harness-pane-header").assertCountEquals(1)
        onNodeWithTag("harness-conversation-header").assertDoesNotExist()
        onNodeWithTag("harness-tool-header").assertIsDisplayed()
        val body = onNodeWithTag("conversation-body").fetchSemanticsNode().boundsInRoot
        val header = onNodeWithTag("harness-tool-header").fetchSemanticsNode().boundsInRoot
        assertEquals(body.top, header.top)
        val hideBounds = onNodeWithTag("harness-tab-hide").fetchSemanticsNode().boundsInRoot
        onNodeWithTag("harness-tab-hide").performClick()
        onAllNodesWithTag("harness-tab-show").assertCountEquals(1)
        val showBounds = onNodeWithTag("harness-tab-show").fetchSemanticsNode().boundsInRoot
        assertEquals(hideBounds, showBounds, "hiding tabs must keep the toggle at the same position")
        onNodeWithTag("harness-tab-show").performClick()
        assertEquals(hideBounds, onNodeWithTag("harness-tab-hide").fetchSemanticsNode().boundsInRoot)
        onNodeWithTag("harness-tab-split").performClick()
        onNodeWithTag("conversation-body").assertDoesNotExist()
        onNodeWithTag("harness-tool-header").assertIsDisplayed()
        onNodeWithTag("harness-tab-hide").performClick()
        onNodeWithTag("harness-tab-hide").performClick()
        onNodeWithTag("harness-pane-header").assertDoesNotExist()
        onNodeWithTag("harness-tab-show").assertIsDisplayed().performClick()
        onNodeWithTag("harness-tool-header").assertIsDisplayed()
    }

    /**
     * The real geometry: the rail sits left of the main region, so the region the title bar lays its
     * segments on starts at the rail edge, exactly as the shell hands it over. Each pane's surface is
     * tagged with the tab the region handed it — `conversation` for the pane that holds none.
     */
    @Composable private fun Fixture(
        state: HarnessTabStripState,
        drag: HarnessTabDragState,
        // Pinned to Light so a pixel assertion can compare against the measured token values.
        mode: HarnessThemeMode = HarnessThemeMode.Light,
        // The navigator control belongs to the page; most cases here have no navigator mounted.
        onToggleNavigator: (() -> Unit)? = null,
        onNewChat: (() -> Unit)? = null,
        newChatShortcut: String? = null,
        backShortcut: String? = null,
        paneTitle: (@Composable () -> Unit)? = null,
    ) {
        HarnessTheme(themeMode = mode) {
            Column(Modifier.fillMaxSize().background(HarnessTokens.Colors.AppBackground).testTag("tabs-root")) {
                HarnessTabStrip(
                    state, drag,
                    backShortcut = backShortcut,
                    onToggleNavigator = onToggleNavigator,
                    onNewChat = onNewChat,
                    newChatShortcut = newChatShortcut,
                    paneTitle = paneTitle,
                )
                Row(Modifier.weight(1f)) {
                    Box(
                        Modifier.width(HarnessTokens.Control.RailWidth).fillMaxHeight()
                            .testTag("fixture-rail"),
                    )
                    HarnessSplitMainRegion(state, drag, Modifier.weight(1f)) { surface ->
                        val tab = LocalHarnessPaneTab.current
                        Box(surface.testTag("content-" + (tab?.id ?: "conversation"))) {
                            HarnessText("当前标签：" + (tab?.title ?: "对话"))
                        }
                    }
                }
            }
        }
    }

    private fun ComposeUiTest.openPanel(state: HarnessTabStripState) {
        runOnIdle { assertTrue(state.tabsHidden, "the panel starts closed, as the reference opens") }
        onNodeWithTag("harness-tab-new").performMouseInput { click() }
        runOnIdle { assertFalse(state.tabsHidden) }
    }

    @Test fun panelOpensWithATabSelectsKeepsHistoryAndCloses() = runSkikoComposeUiTest(size = Size(1200f, 600f)) {
        val state = state(); val drag = HarnessTabDragState()
        setContent { Fixture(state, drag) }
        // Closed, the strip is the conversation alone and carries the one control that opens the panel.
        onNodeWithTag("harness-tab-new").assertIsDisplayed()
        onNodeWithTag("harness-tab-new-tab").assertDoesNotExist()
        onNodeWithTag("harness-tab-changes").assertDoesNotExist()
        onNodeWithTag("content-conversation").assertIsDisplayed()
        openPanel(state)
        // The panel's tabs are up, and the trailing control became the 布局 / 标签页 pair.
        onNodeWithTag("harness-tab-new-tab").assertIsDisplayed()
        onNodeWithTag("harness-tab-changes").assertIsDisplayed()
        onNodeWithTag("harness-tab-add").assertIsDisplayed()
        onNodeWithTag("harness-tab-new").assertDoesNotExist()
        onNodeWithTag("harness-tab-hide").assertIsDisplayed()
        onNodeWithTag("content-new-tab").assertIsDisplayed()
        onNodeWithTag("harness-tab-changes").performMouseInput { click() }
        onNodeWithTag("content-changes").assertIsDisplayed()
        onNodeWithTag("harness-tab-back").performMouseInput { click() }
        onNodeWithTag("content-new-tab").assertIsDisplayed()
        onNodeWithTag("harness-tab-forward").performMouseInput { click() }
        onNodeWithTag("content-changes").assertIsDisplayed()
        onNodeWithTag("harness-tab-close-changes").performMouseInput { click() }
        onNodeWithTag("harness-tab-changes").assertDoesNotExist()
        onNodeWithTag("harness-tab-add").performMouseInput { click() }
        runOnIdle { assertEquals(2, state.tabs.size); assertEquals(2, state.tabs.map { it.id }.distinct().size) }
        // 隐藏标签页 closes the panel; with tabs still open the control returns as the count badge.
        onNodeWithTag("harness-tab-hide").performMouseInput { click() }
        onNodeWithTag("harness-tab-new-tab").assertDoesNotExist()
        onNodeWithTag("harness-tab-show").assertIsDisplayed()
        onNodeWithTag("harness-tab-new").assertDoesNotExist()
        runOnIdle { assertTrue(state.tabsHidden); assertEquals(2, state.tabs.size) }
    }

    /**
     * The title bar's two modes (隐藏标签页 ⇄ 显示标签页). Closing the panel is not a view over a split —
     * measured, the reference unmounts the panel's `aside` and leaves the conversation alone — so the
     * strip goes with it and the conversation's own title takes the whole bar. 显示标签页 brings both back.
     */
    @Test fun hidingTabsSwapsTheStripForTheConversationTitleAndComesBack() =
        runSkikoComposeUiTest(size = Size(1200f, 500f)) {
            val state = state(); val drag = HarnessTabDragState()
            setContent {
                Fixture(state, drag, paneTitle = { Box(Modifier.testTag("fixture-pane-title")) { HarnessText("聊天") } })
            }
            // Closed: no strip at all, and the conversation titled by its own page.
            onNodeWithTag("harness-tab-new-tab").assertDoesNotExist()
            onNodeWithTag("fixture-pane-title").assertIsDisplayed()
            openPanel(state)
            // Open: the conversation keeps the title, the panel's segment is the one with the strip.
            onNodeWithTag("fixture-pane-title").assertIsDisplayed()
            onNodeWithTag("harness-tab-new-tab").assertIsDisplayed()
            assertTrue(
                onNodeWithTag("harness-tab-new-tab").fetchSemanticsNode().boundsInRoot.left >
                    onNodeWithTag("fixture-pane-title").fetchSemanticsNode().boundsInRoot.left,
                "the strip belongs to the panel's segment, not the conversation's",
            )
            onNodeWithTag("harness-tab-hide").performMouseInput { click() }
            onNodeWithTag("harness-tab-new-tab").assertDoesNotExist()
            onNodeWithTag("fixture-pane-title").assertIsDisplayed()
            onNodeWithTag("harness-tab-show").performMouseInput { click() }
            onNodeWithTag("harness-tab-new-tab").assertIsDisplayed()
            onNodeWithTag("fixture-pane-title").assertIsDisplayed()
        }

    /**
     * The segmentation, measured on the live 44px strip of a 2560px split window: the leading chrome
     * carries the history cluster inset 88px clear of the traffic lights, the hairline that closes the
     * leading column falls at 230px while the navigator is hidden (340px while it is pushed) and the
     * pane's own content starts 8px past it, the panes are cut on the shell's main-region columns, and
     * the layout/tabs controls sit behind a shorter 16px hairline at the right edge.
     */
    @Test fun titleBarIsSegmentedAndTheClusterNeverCollidesWithThePanes() =
        runSkikoComposeUiTest(size = Size(1200f, 500f)) {
            val state = state(); val drag = HarnessTabDragState()
            setContent {
                Fixture(
                    state, drag,
                    onToggleNavigator = {}, onNewChat = {},
                    paneTitle = { Box(Modifier.testTag("fixture-pane-title")) { HarnessText("聊天") } },
                )
            }
            runOnIdle { state.moveToHalf("changes", right = true) }
            val root = onNodeWithTag("tabs-root").fetchSemanticsNode().boundsInRoot
            val scale = root.width / 1200f
            fun bounds(tag: String) = onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
            // The cluster is inset to the measured 88px, clear of the native traffic lights.
            assertEquals(root.left + 88f * scale, bounds("harness-tab-back").left, 1f)
            // The leading column closes on the cluster's own trailing edge: 88 + four 28px controls, 6px
            // apart with a 6px trailing gap = the measured 230px.
            val leadRule = bounds("harness-tab-lead-rule")
            assertEquals(root.left + 230f * scale, leadRule.left, 1f)
            assertEquals(1f * scale, leadRule.width, 1f)
            assertEquals(HarnessTokens.Control.TitlebarRule.value * scale, leadRule.height, 1f)
            // The pane starts at the rail and the rule is an overlay inside it, 8px clear of the content.
            val pane = bounds("harness-tab-pane-main")
            assertEquals(root.left + HarnessTokens.Control.RailWidth.value * scale, pane.left, 1f)
            // The cluster overflows the rail: the pane must clear the last control it paints, 8px past the
            // hairline — the measured 238px pane header. The conversation's segment holds the title; the
            // panel's holds the tabs.
            val lastControl = bounds("harness-tab-new-chat").right
            assertEquals(
                leadRule.right + HarnessTokens.Control.TabStripInsetStart.value * scale,
                bounds("fixture-pane-title").left,
                1f,
            )
            assertTrue(
                bounds("fixture-pane-title").left - lastControl >=
                    HarnessTokens.Control.TabStripInsetStart.value * scale - 0.5f,
                "the pane's content must start past the leading cluster, not under it",
            )
            // The strip is in the side panel's segment, which starts at the seam.
            val secondary = bounds("harness-tab-pane-secondary")
            assertEquals(
                bounds("harness-tab-changes").left,
                secondary.left + HarnessTokens.Control.TabStripInsetStart.value * scale,
                1f,
            )
            // The trailing cluster keeps its own shorter hairline and ends at the strip's own 8px inset.
            val trailingRule = bounds("harness-tab-trailing-rule")
            assertEquals(HarnessTokens.Control.TitlebarTrailingRule.value * scale, trailingRule.height, 1f)
            assertTrue(bounds("harness-tab-split").left > trailingRule.right)
            assertEquals(
                root.right - HarnessTokens.Spacing.Two.value * scale,
                bounds("harness-tab-hide").right,
                1f,
            )
            // The seam cuts the title bar on the very column the panes divide at below it.
            assertEquals(bounds("harness-split-seam").center.x, bounds("harness-tab-pane-rule").center.x, 2f)
            assertEquals(1f * scale, bounds("harness-tab-pane-rule").width, 1f)
            assertEquals(
                HarnessTokens.Control.SplitSeamWidth.value * scale,
                bounds("harness-tab-pane-secondary").left - bounds("harness-tab-pane-main").right,
                1f,
            )
            // 完整视图 hands the whole main region to the panel: no seam, one segment, and the
            // conversation listed as the 聊天 chip the live strip draws there.
            onNodeWithTag("harness-tab-split").performMouseInput { click() }
            onNodeWithTag("harness-tab-pane-rule").assertDoesNotExist()
            onNodeWithTag("fixture-pane-title").assertDoesNotExist()
            onNodeWithTag("harness-tab-conversation").assertIsDisplayed()
            runOnIdle { assertEquals(HarnessTabLayout.Full, state.layout) }
        }

    /**
     * The sidebar control is the page's navigator toggle, not a tab-bar control. Codex draws it only
     * on pages that mount a navigator (`PageShellSpec.mountsNavigator`) and it never hides the tabs.
     */
    @Test fun navigatorControlFollowsThePageToggleAndNeverHidesTabs() {
        var toggles = 0
        runSkikoComposeUiTest(size = Size(900f, 400f)) {
            val state = state(); val drag = HarnessTabDragState()
            setContent { Fixture(state, drag) }
            onNodeWithTag("harness-tab-navigator").assertDoesNotExist()
        }
        runSkikoComposeUiTest(size = Size(900f, 400f)) {
            val state = state(); val drag = HarnessTabDragState()
            setContent { Fixture(state, drag, onToggleNavigator = { toggles++ }) }
            openPanel(state)
            onNodeWithTag("harness-tab-navigator").assertIsDisplayed().performMouseInput { click() }
            runOnIdle { assertEquals(1, toggles) }
            onNodeWithTag("harness-tab-new-tab").assertIsDisplayed()
            onNodeWithTag("harness-tab-changes").assertIsDisplayed()
        }
    }

    /**
     * A dropped tab belongs to the side panel, so the half it is dropped on is the half the **panel**
     * takes — the reference's 移至左侧窗格 / 移至右侧窗格 — and the conversation takes the other one. A drop
     * on the title bar is outside the main region and moves nothing.
     */
    @Test fun physicalDragMovesThePanelToAHalfAndIgnoresTitlebarDrop() = runSkikoComposeUiTest(size = Size(1200f, 600f)) {
        val state = state(); val drag = HarnessTabDragState()
        setContent { Fixture(state, drag) }
        openPanel(state)
        fun drop(id: String, destination: Offset) {
            val origin = onAllNodesWithTag("harness-tab-$id").onFirst().fetchSemanticsNode().boundsInRoot.center
            onNodeWithTag("tabs-root").performMouseInput {
                moveTo(origin); press(); moveTo(origin + Offset(0f, 30f), delayMillis = 40)
            }
            onNodeWithTag("harness-drop-left").assertIsDisplayed()
            onNodeWithTag("tabs-root").performMouseInput {
                moveTo(destination, delayMillis = 40); release()
            }
        }
        drop("changes", Offset(1000f, 300f))
        runOnIdle { assertEquals(HarnessPaneSide.Right, state.sidePanelSide, "the panel took the right half") }
        onNodeWithTag("harness-split-left").assertIsDisplayed()
        onNodeWithTag("harness-split-right").assertIsDisplayed()
        drop("changes", Offset(100f, 300f))
        runOnIdle { assertEquals(HarnessPaneSide.Left, state.sidePanelSide, "and it can take the left one") }
        drop("changes", Offset(900f, 22f))
        runOnIdle {
            assertEquals(HarnessPaneSide.Left, state.sidePanelSide)
            assertFalse(drag.active, "a title-bar drop is released without a target")
        }
        // Closing the last tab leaves the panel with nothing to show, and it goes with it.
        onNodeWithTag("harness-tab-close-changes").performMouseInput { click() }
        onNodeWithTag("harness-tab-changes").assertDoesNotExist()
        onNodeWithTag("harness-tab-close-new-tab").performMouseInput { click() }
        runOnIdle { assertTrue(state.tabs.isEmpty()); assertTrue(state.tabsHidden) }
        onNodeWithTag("harness-split-left").assertDoesNotExist()
        onNodeWithTag("harness-tab-new").assertIsDisplayed()
    }

    @Test fun narrowWindowKeepsControlsVisibleAndCapturesLightDark() {
        for ((width, mode) in listOf(1200 to HarnessThemeMode.Light, 640 to HarnessThemeMode.Dark)) {
            runSkikoComposeUiTest(size = Size(width.toFloat(), 500f)) {
                val state = state(); val drag = HarnessTabDragState()
                setContent {
                    HarnessTheme(themeMode = mode) {
                        Column(Modifier.fillMaxSize().background(HarnessTokens.Colors.AppBackground).testTag("tabs-root")) {
                            HarnessTabStrip(state, drag)
                            Row(Modifier.weight(1f)) {
                                Box(Modifier.width(HarnessTokens.Control.RailWidth).fillMaxHeight())
                                HarnessSplitMainRegion(state, drag, Modifier.weight(1f)) { surface ->
                                    Box(surface.padding(24.dp)) { HarnessText("当前标签") }
                                }
                            }
                        }
                    }
                }
                // The navigator control is absent without a page toggle; the layout controls stay put
                // regardless, and still carry the panel between the split and the full view.
                onNodeWithTag("harness-tab-navigator").assertDoesNotExist()
                runOnIdle { state.toggleTabsHidden() }
                onNodeWithTag("harness-split-left").assertIsDisplayed()
                onNodeWithTag("harness-tab-split").performMouseInput { click() }
                onNodeWithTag("harness-split-left").assertDoesNotExist()
                onNodeWithTag("harness-tab-hide").performMouseInput { click() }
                onNodeWithTag("harness-split-left").assertIsDisplayed()
                // Park the pointer away from the control so the capture shows the engaged fill the
                // toggle keeps on its own, not a hover fill.
                onNodeWithTag("harness-split-seam").performMouseInput { moveTo(center) }
                val bitmap = onNodeWithTag("tabs-root").captureToImage().asSkiaBitmap()
                val image = Image.makeFromBitmap(bitmap)
                val output = File("/Volumes/Data/IdeaProjects/kmp-harness/docs/desktop-ui/captures/tabs-2026-10-14/${mode.name.lowercase()}-$width.png")
                output.parentFile.mkdirs()
                output.writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
                image.close()
                // The title bar alone, so the segmentation (leading cluster, hairlines, pane columns,
                // trailing cluster) can be reviewed at its own 44dp height.
                capture("${mode.name.lowercase()}-$width-strip.png", "harness-tab-strip")
            }
        }
    }

    /**
     * The measured chrome, checked against the pixels rather than the modifier list: the band is the app
     * background with no fill of its own, the selected tab is a white pill ringed by 8% ink, and an
     * unselected tab is bare — no pill, no chip background.
     */
    @Test fun stripBandIsTheAppBackgroundAndOnlyTheSelectedTabDrawsAPill() = runSkikoComposeUiTest(size = Size(1200f, 500f)) {
        val state = HarnessTabStripState(listOf(HarnessWorkspaceTab("a", "A"), HarnessWorkspaceTab("b", "B")), "a")
        val drag = HarnessTabDragState()
        setContent { Fixture(state, drag) }
        runOnIdle { state.toggleTabsHidden() }
        val captured = onNodeWithTag("tabs-root").captureToImage()
        fun chip(id: String) = onNodeWithTag("harness-tab-$id").fetchSemanticsNode().boundsInRoot
        val selected = chip("a")
        val inactive = chip("b")
        // Sampled well past the one-character titles, clear of the trailing close target.
        assertEquals(
            HarnessLightColors.AppBackground.toArgb().toLong() and 0xFFFFFFFFL,
            captured.argbAt((inactive.left + 120f).toInt(), inactive.center.y.toInt()),
            "an unselected tab must not paint a chip background",
        )
        assertEquals(
            HarnessLightColors.SurfaceElevated.toArgb().toLong() and 0xFFFFFFFFL,
            captured.argbAt((selected.left + 120f).toInt(), selected.center.y.toInt()),
            "the selected tab is the measured white pill",
        )
        // The conversation's half of the band is the app background too: the strip is not the white page
        // surface, so nothing composed under it bleeds through.
        val conversation = onNodeWithTag("harness-tab-pane-main").fetchSemanticsNode().boundsInRoot
        assertEquals(
            HarnessLightColors.AppBackground.toArgb().toLong() and 0xFFFFFFFFL,
            captured.argbAt((conversation.left + 4f).toInt(), conversation.center.y.toInt()),
            "the tab band itself paints the app background",
        )
    }

    /**
     * Control behaviour off the live tab bar: the close target is present only on the selected tab and
     * on hover, the hover tip is the dark inverted pill, and it names the chord the shell really binds.
     */
    @Test fun closeTargetHidesAtRestAndHoverTipNamesTheBoundChord() = runSkikoComposeUiTest(size = Size(1200f, 500f)) {
        mainClock.autoAdvance = false
        val state = HarnessTabStripState(listOf(HarnessWorkspaceTab("a", "A"), HarnessWorkspaceTab("b", "B")), "a")
        val drag = HarnessTabDragState()
        setContent {
            HarnessTheme {
                Column(Modifier.fillMaxSize().background(HarnessTokens.Colors.AppBackground)) {
                    HarnessTabStrip(state, drag, backShortcut = "⌘[")
                    Row(Modifier.weight(1f)) {
                        Box(Modifier.width(HarnessTokens.Control.RailWidth).fillMaxHeight())
                        HarnessSplitMainRegion(state, drag, Modifier.weight(1f)) { surface -> Box(surface) }
                    }
                }
            }
        }
        runOnIdle { state.toggleTabsHidden() }
        advance(16)
        onNodeWithTag("harness-tab-close-a").assertIsDisplayed()
        onNodeWithTag("harness-tab-close-b").assertDoesNotExist()
        onNodeWithTag("harness-tab-b").performMouseInput { moveTo(center) }
        advance(16)
        onNodeWithTag("harness-tab-close-b").assertIsDisplayed()
        // 前进 is unavailable at the head of history and renders at opacity .4 like Codex.
        onNodeWithTag("harness-tab-forward").assertIsNotEnabled()
        onNodeWithTag("harness-tab-b").performMouseInput { click() }
        advance(50)
        onNodeWithTag("harness-tab-back").assertIsEnabled().performMouseInput { moveTo(center) }
        advance(100)
        onNodeWithTag("harness-tab-tip-harness-tab-back").assertDoesNotExist()
        advance(400)
        onNodeWithTag("harness-tab-tip-harness-tab-back").assertIsDisplayed()
        onNodeWithTag("harness-tab-tip-harness-tab-back-text", useUnmergedTree = true).assertTextEquals("返回 ⌘[")
    }

    /**
     * The seam is a 16px grab band that also draws the divider: an idle split shows a 1px hairline at
     * the band's centre — 7% ink, the value the live seam's boundary column holds (239) on every row
     * while the pane beside it reads 255 — and pointing at the band adds the resizer's mark on top.
     * Measured off the live split view, where the resizer is a 16px transparent `cursor-col-resize` band
     * and the pane's `left-0 w-px` edge element carries `shadow-[-8px_0_16px_-8px_rgb(0_0_0/0.18)]`
     * (its peak lands under the 1px element, so the divider is one pixel wide, not a band).
     */
    @Test fun splitSeamDrawsThePaneEdgeAtRestAndAddsTheHairlineOnHover() = runSkikoComposeUiTest(size = Size(1200f, 500f)) {
        val state = state(); val drag = HarnessTabDragState()
        setContent { Fixture(state, drag) }
        runOnIdle { state.toggleTabsHidden() }
        val seam = onNodeWithTag("harness-split-seam").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        // The root fills the 1200px test window at the test density, so the scale recovers the 16dp band.
        val scale = onNodeWithTag("tabs-root").fetchSemanticsNode().boundsInRoot.width / 1200f
        assertEquals(16f * scale, seam.width, 1f, "the seam is the measured 16dp grab band")
        val idle = onNodeWithTag("tabs-root").captureToImage()
        val y = seam.center.y.toInt()
        val boundary = idle.argbAt(seam.center.x.toInt(), y)
        // Idle: the edge darkens toward the boundary — the far end of the band's left half is at its
        // darkest, its outer edge is transparent and still the surface the band is composed on.
        assertTrue(
            idle.lumaAt(seam.center.x.toInt(), y) < idle.lumaAt(seam.left.toInt(), y),
            "an idle seam draws the pane edge, darkest at the boundary",
        )
        onNodeWithTag("harness-split-seam").performMouseInput { moveTo(center) }
        assertNotEquals(
            boundary,
            onNodeWithTag("tabs-root").captureToImage().argbAt(seam.center.x.toInt(), y),
            "hovering the band must add the resizer's hairlines",
        )
        runOnIdle { assertEquals(0.5f, state.splitFraction) }
        onNodeWithTag("harness-split-seam").performMouseInput {
            moveTo(center)
            press()
            moveTo(center + Offset(240f, 0f), delayMillis = 40)
            release()
        }
        // 240px of a 1200px workspace, less the drag detector's slop, inside the 0.2..0.8 clamp.
        runOnIdle { assertEquals(0.7f, state.splitFraction, 0.03f) }
        runOnIdle {
            state.resizeSplit(4f)
            assertEquals(0.8f, state.splitFraction)
            state.resizeSplit(-4f)
            assertEquals(0.2f, state.splitFraction)
            state.resizeSplit(Float.NaN)
            assertEquals(0.2f, state.splitFraction)
        }
    }

    /**
     * Records the hover state: an unselected tab reveals its close target and the pointed-at control
     * takes the measured 10% ink fill. The hover tip is a popup in its own composition root, so a
     * node capture cannot include it — [closeTargetHidesAtRestAndHoverTipNamesTheBoundChord] covers it.
     */
    @Test fun capturesRevealedCloseTargetAndHoveredControl() = runSkikoComposeUiTest(size = Size(1200f, 500f)) {
        mainClock.autoAdvance = false
        val state = state(); val drag = HarnessTabDragState()
        setContent { Fixture(state, drag) }
        runOnIdle { state.toggleTabsHidden() }
        onNodeWithTag("harness-tab-changes").performMouseInput { moveTo(center) }
        advance(16)
        onNodeWithTag("harness-tab-close-changes").assertIsDisplayed()
        val bitmap = onNodeWithTag("tabs-root").captureToImage().asSkiaBitmap()
        val image = Image.makeFromBitmap(bitmap)
        val output = File("/Volumes/Data/IdeaProjects/kmp-harness/docs/desktop-ui/captures/tabs-2026-10-14/light-hover.png")
        output.parentFile.mkdirs()
        output.writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        image.close()
    }

    /**
     * The split divides the shell's main region only. Measured on the live split view: the left panel
     * — rail plus navigator — is one full-height column and the page surface is the only thing doubled.
     */
    @Test fun splitDividesTheMainRegionAndNeverTheNavigationChrome() = runSkikoComposeUiTest(size = Size(1400f, 800f)) {
        val state = state(); val drag = HarnessTabDragState()
        setContent {
          Box(Modifier.fillMaxSize().testTag("tabs-root")) {
            CompositionLocalProvider(LocalHarnessMainRegion provides rememberHarnessTabMainRegion(state, drag)) {
                AppShell(
                    state = AppShellState(navigation = NavigationState(currentPage = PageId.Home)),
                    navItems = emptyList(), onNavSelect = {},
                    navigator = WorkspaceNavigatorState(),
                    projectNodes = emptyList(), recentTasks = emptyList(),
                    onToggleSidebar = {}, onNewTask = {}, onNavigatorSearch = {},
                    onToggleProject = {}, onSelectNode = {}, onSelectRecent = {},
                    content = { Box(Modifier.fillMaxSize().testTag("page-content")) },
                )
            }
          }
        }
        onNodeWithTag("app-rail").assertIsDisplayed()
        onNodeWithTag("workspace-navigator").assertIsDisplayed()
        onAllNodesWithTag("main-surface").assertCountEquals(1)
        runOnIdle { state.moveToHalf(state.tabs.last().id, right = true) }
        onNodeWithTag("harness-split-seam").assertIsDisplayed()
        onAllNodesWithTag("main-surface").assertCountEquals(2)
        onAllNodesWithTag("app-rail").assertCountEquals(1)
        onAllNodesWithTag("workspace-navigator").assertCountEquals(1)
        capture("split-shell-light.png", "tabs-root")
    }

    @Test fun stateNeverDuplicatesATabOrReusesAClosedId() {
        val state = state()
        state.moveToHalf("changes", right = false)
        assertEquals(HarnessPaneSide.Left, state.sidePanelSide)
        assertFalse(state.tabsHidden)
        assertEquals(HarnessTabLayout.Split, state.layout)
        state.close("changes")
        assertEquals(HarnessFixtureTabs.first().id, state.activeId)
        state.addNewTab(); val first = state.activeId
        state.addNewTab(); state.close(first); state.addNewTab()
        assertEquals(state.tabs.size, state.tabs.map { it.id }.distinct().size)
        state.tabs.toList().forEach { state.close(it.id) }
        assertEquals("", state.activeId)
        assertTrue(state.tabsHidden, "closing the last tab leaves the panel with nothing to show")
        state.moveToHalf("missing", true)
        assertTrue(state.tabsHidden, "an unknown tab cannot move the panel")
        state.addNewTab()
        assertEquals(1, state.tabs.size)
        assertFalse(state.tabsHidden)
    }

    /**
     * The strip does not select the shell's page — that is the whole point of the two-pane model, and
     * why the conversation is not a tab. The conversation's own page is the one the shell shows, and a
     * side-panel tab never moves it.
     */
    @Test fun theConversationCarriesThePageTheShellShows() {
        val state = state()
        assertEquals(PageId.Home, state.conversationPage, "the conversation starts on 新对话页")
        assertEquals(PageId.Home, state.activePage)
        state.select("changes")
        assertEquals("changes", state.activeId)
        assertEquals(PageId.Settings(SettingsSection.Git), state.activeTab?.page, "变更 is the panel's own page")
        assertEquals(PageId.Home, state.conversationPage, "selecting a panel tab never moves the conversation")
        state.selectConversation(PageId.Thread("thread-a"))
        assertEquals(PageId.Thread("thread-a"), state.activePage)
        state.moveToHalf("changes", right = true)
        assertEquals(PageId.Thread("thread-a"), state.conversationPage, "a drop is a layout change, not a navigation")
    }

    /**
     * Every selection counts, even when it changes nothing else: that bump is the signal that lets the
     * shell follow a click back to a page it had navigated away from (新聊天 while already on 首页).
     * Moving a tab into a half is a layout change, not a selection, so it must not count as one.
     */
    @Test fun everySelectionCountsEvenWhenItChangesNothingElse() {
        val state = state()
        val start = state.selectionEpoch
        state.select("new-tab")
        assertEquals(start + 1, state.selectionEpoch, "re-selecting the active tab counts")
        state.select("changes")
        assertEquals(start + 2, state.selectionEpoch)
        state.moveToHalf("changes", right = true)
        assertEquals(start + 2, state.selectionEpoch, "a drop into a half is not a selection")
        assertEquals(HarnessPaneSide.Right, state.sidePanelSide)
        state.selectConversation(PageId.Home)
        assertEquals(start + 3, state.selectionEpoch, "re-choosing 新对话页 counts too")
    }

    /**
     * The layout control carries the panel between the split and 完整视图, where the conversation's pane is
     * pushed off screen and the conversation is listed as a 聊天 chip instead — and closing the panel
     * always collapses to the conversation alone, whichever layout it was in.
     */
    @Test fun theLayoutControlSwitchesFullViewAndBack() {
        val state = state()
        state.toggleTabsHidden()
        assertEquals(HarnessTabLayout.Split, state.layout)
        state.toggleSplit()
        assertEquals(HarnessTabLayout.Full, state.layout)
        state.toggleSplit()
        assertEquals(HarnessTabLayout.Split, state.layout)
        state.unsplit()
        assertEquals(HarnessTabLayout.Split, state.layout)
        state.toggleSplit()
        state.toggleTabsHidden()
        assertTrue(state.tabsHidden)
        assertEquals(HarnessTabLayout.Split, state.layout, "reopening returns beside the conversation")
    }

    /**
     * A split draws the conversation beside the side panel's tab: the region hands the pane being
     * composed the tab it holds — and `null` to the pane that holds none, which is how a pane says "draw
     * the shell's own page", so the panel's tab is handed to exactly one pane and never twice.
     */
    @Test fun aSplitDrawsTheConversationBesideTheSidePanelsTab() = runSkikoComposeUiTest(size = Size(1400f, 800f)) {
        val state = state(); val drag = HarnessTabDragState()
        setContent {
            Box(Modifier.fillMaxSize().testTag("tabs-root")) {
                CompositionLocalProvider(LocalHarnessMainRegion provides rememberHarnessTabMainRegion(state, drag)) {
                    AppShell(
                        state = AppShellState(navigation = NavigationState(currentPage = PageId.Home)),
                        navItems = emptyList(), onNavSelect = {},
                        navigator = WorkspaceNavigatorState(),
                        projectNodes = emptyList(), recentTasks = emptyList(),
                        onToggleSidebar = {}, onNewTask = {}, onNavigatorSearch = {},
                        onToggleProject = {}, onSelectNode = {}, onSelectRecent = {},
                        content = {
                            val tab = LocalHarnessPaneTab.current
                            Box(
                                Modifier.fillMaxSize()
                                    .testTag("pane-" + (tab?.id ?: "conversation")),
                            )
                        },
                    )
                }
            }
        }
        // Closed, the one surface is the conversation.
        onNodeWithTag("pane-conversation").assertExists()
        onAllNodesWithTag("pane-changes").assertCountEquals(0)
        runOnIdle { state.moveToHalf("changes", right = true) }
        onNodeWithTag("pane-conversation").assertExists()
        onNodeWithTag("pane-changes").assertExists()
        onAllNodesWithTag("pane-new-tab").assertCountEquals(0)
        onAllNodesWithTag("app-rail").assertCountEquals(1)
        capture("split-pane-tabs-light.png", "tabs-root")
        // 完整视图: the panel takes the whole region and the conversation's pane is gone.
        runOnIdle { state.toggleSplit() }
        onAllNodesWithTag("pane-conversation").assertCountEquals(0)
        onNodeWithTag("pane-changes").assertExists()
    }

    /**
     * The strip is drawn in *one* pane's segment — the **side panel's** — because it is the side panel's
     * tab management; the conversation's segment shows the conversation's own title instead. Measured on
     * the live shell: the panel's segment carried the `任务选项卡` tablist while the conversation's carried
     * the `聊天工具栏` with the conversation's name. Ownership follows the side panel, so swapping the
     * panes carries the strip across with it.
     */
    @Test fun aSplitDrawsTheTabStripInTheSidePanelsSegmentOnly() = runSkikoComposeUiTest(size = Size(1200f, 600f)) {
        val state = state(); val drag = HarnessTabDragState()
        setContent { Fixture(state, drag) }
        openPanel(state)
        fun chipLeft() = onNodeWithTag("harness-tab-changes").fetchSemanticsNode().boundsInRoot.left
        fun secondaryLeft() = onNodeWithTag("harness-tab-pane-secondary").fetchSemanticsNode().boundsInRoot.left
        // There is one strip, so a tab id resolves to exactly one node whatever the layout.
        onAllNodesWithTag("harness-tab-changes").assertCountEquals(1)
        onNodeWithTag("harness-tab-pane-secondary").assertDoesNotExist()

        runOnIdle { state.moveToHalf("changes", right = true) }
        assertEquals(HarnessPaneSide.Right, state.sidePanelSide, "the side panel starts on the right")
        onAllNodesWithTag("harness-tab-changes").assertCountEquals(1)
        assertTrue(
            chipLeft() >= secondaryLeft(),
            "the split draws its tab row in the side panel's segment, not in the conversation's",
        )

        runOnIdle { state.swapSides() }
        assertEquals(HarnessPaneSide.Left, state.sidePanelSide, "the side panel can take the other half")
        assertTrue(
            chipLeft() < secondaryLeft(),
            "swapping the panes carries the strip over with the side panel",
        )
    }

    /**
     * The side panel belongs to the conversation, so it exists on a **chat page** only. Measured: 资料库,
     * 定时任务, 插件 and 代码审查 mount no `aside` at all — no `任务选项卡` tablist, no
     * `data-app-shell-workspace-layout-toggle` left in the title bar — and their main area widens to the
     * window edge. The tabs are kept while it is away, so the panel comes back exactly as it was.
     */
    @Test fun theSidePanelExistsOnAChatPageOnly() = runSkikoComposeUiTest(size = Size(1200f, 600f)) {
        val state = state(); val drag = HarnessTabDragState()
        setContent { Fixture(state, drag) }
        openPanel(state)
        onNodeWithTag("harness-tab-new-tab").assertIsDisplayed()
        onNodeWithTag("harness-split-left").assertIsDisplayed()
        onNodeWithTag("content-new-tab").assertIsDisplayed()

        runOnIdle { state.onChatPage = false }
        onNodeWithTag("harness-tab-new-tab").assertDoesNotExist()
        onNodeWithTag("harness-tab-split").assertDoesNotExist()
        onNodeWithTag("harness-tab-hide").assertDoesNotExist()
        onNodeWithTag("harness-split-left").assertDoesNotExist()
        onNodeWithTag("content-new-tab").assertDoesNotExist()
        // The page the conversation is showing takes the whole region.
        onNodeWithTag("content-conversation").assertIsDisplayed()
        runOnIdle {
            assertEquals(2, state.tabs.size, "the panel's tabs are kept while it is off screen")
            assertFalse(state.tabsHidden, "and it is still open, not closed")
        }

        runOnIdle { state.onChatPage = true }
        onNodeWithTag("harness-tab-new-tab").assertIsDisplayed()
        onNodeWithTag("harness-split-left").assertIsDisplayed()
        onNodeWithTag("content-new-tab").assertIsDisplayed()
    }

    /**
     * The shell's single composer dock belongs to the **conversation** — the only pane that can hold a
     * 会话 — so the panel's pane never draws it even when its own page would dock one of its own, and the
     * 新标签页 page's full-width column is never left with the dock's gutter behind it.
     */
    @Test fun aSplitDocksTheComposerInTheConversationOnly() = runSkikoComposeUiTest(size = Size(1400f, 800f)) {
        val state = state(); val drag = HarnessTabDragState()
        setContent {
            Box(Modifier.fillMaxSize().testTag("tabs-root")) {
                CompositionLocalProvider(LocalHarnessMainRegion provides rememberHarnessTabMainRegion(state, drag)) {
                    AppShell(
                        state = AppShellState(navigation = NavigationState(currentPage = PageId.Home)),
                        navItems = emptyList(), onNavSelect = {},
                        navigator = WorkspaceNavigatorState(),
                        projectNodes = emptyList(), recentTasks = emptyList(),
                        onToggleSidebar = {}, onNewTask = {}, onNavigatorSearch = {},
                        onToggleProject = {}, onSelectNode = {}, onSelectRecent = {},
                        content = { Box(Modifier.fillMaxSize().testTag("page-content")) },
                        composer = { Box(Modifier.size(8.dp).testTag("dock-body")) },
                    )
                }
            }
        }
        // 首页 docks a composer of its own, so the conversation keeps one.
        onAllNodesWithTag("composer-column").assertCountEquals(1)
        // 变更 is a 设置 page, whose spec docks one too — but it is the panel's pane, so it does not.
        runOnIdle { state.moveToHalf("changes", right = true) }
        onAllNodesWithTag("composer-column").assertCountEquals(1)
        val dock = onNodeWithTag("composer-column").getUnclippedBoundsInRoot()
        val conversationPane = onNodeWithTag("harness-split-left").getUnclippedBoundsInRoot()
        assertTrue(
            dock.left >= conversationPane.left && dock.right <= conversationPane.right,
            "the dock belongs to the conversation pane, not to the panel's",
        )
        // 完整视图 hands the region to the panel, so no dock is left at all.
        runOnIdle { state.toggleSplit() }
        onAllNodesWithTag("composer-column").assertCountEquals(0)
    }

    /**
     * The 新标签页 page draws the measured reference layout, and says what it cannot do: the toolbar is
     * the 48dp band with the address capsule capped at its measured 770, 返回/前进 are disabled as the
     * reference's are, 变更 is live and opens its destination, and the three cards this client has no
     * surface for are disabled instead of pretending to open one.
     */
    @Test fun newTabPageDrawsTheMeasuredLayoutWithOnlyChangesLive() = runSkikoComposeUiTest(size = Size(1200f, 800f)) {
        var opened: NewTabTool? = null
        setContent {
            HarnessTheme(themeMode = HarnessThemeMode.Light) {
                Box(Modifier.fillMaxSize().testTag("new-tab-root")) {
                    NewTabPage(onOpenTool = { opened = it })
                }
            }
        }
        val toolbar = onNodeWithTag("new-tab-toolbar").getUnclippedBoundsInRoot()
        assertEquals(48.dp, toolbar.bottom - toolbar.top, "the toolbar is the measured 48dp band")
        val address = onNodeWithTag("new-tab-address").getUnclippedBoundsInRoot()
        assertTrue(
            address.right - address.left <= 770.dp,
            "the 搜索或输入网址 capsule is capped at its measured 770",
        )
        onNodeWithTag("new-tab-back").assertIsNotEnabled()
        onNodeWithTag("new-tab-forward").assertIsNotEnabled()
        onNodeWithTag("new-tab-tool-changes").assertIsEnabled()
        listOf("terminal", "files", "sidechat").forEach { tool ->
            onNodeWithTag("new-tab-tool-$tool").assertIsNotEnabled()
        }
        // The 终端 card carries the reference's own options control.
        onNodeWithTag("new-tab-tool-options").assertIsDisplayed()
        onNodeWithTag("new-tab-tool-changes").performClick()
        runOnIdle { assertEquals(NewTabTool.Changes, opened, "变更 opens its destination") }
        capture("new-tab-page-light.png", "new-tab-root")
    }

    /** Records one node as a PNG under the tab captures, so a layout claim can be looked at. */
    private fun ComposeUiTest.capture(file: String, tag: String) {
        val bitmap = onNodeWithTag(tag).captureToImage().asSkiaBitmap()
        val image = Image.makeFromBitmap(bitmap)
        val output = File("/Volumes/Data/IdeaProjects/kmp-harness/docs/desktop-ui/captures/tabs-2026-10-14/$file")
        output.parentFile.mkdirs()
        output.writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        image.close()
    }

    /** `mainClock` is paused in the tip test; advancing and settling is the pattern the tip tests use. */
    private fun ComposeUiTest.advance(millis: Long) { mainClock.advanceTimeBy(millis); waitForIdle() }

    /** ARGB8888 of one captured pixel, so a measured chrome colour can be asserted directly. */
    private fun ImageBitmap.argbAt(x: Int, y: Int): Long =
        asSkiaBitmap().getColor(x, y).toLong() and 0xFFFFFFFFL

    /** The red channel of one captured pixel: enough to order two pixels of the same surface by ink. */
    private fun ImageBitmap.lumaAt(x: Int, y: Int): Long = (argbAt(x, y) shr 16) and 0xFF
}
