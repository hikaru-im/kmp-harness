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
import im.hikaru.harness.client.shell.*
import im.hikaru.harness.ui.foundation.*
import java.io.File
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class HarnessTabsInteractionTest {
    private fun state() = HarnessTabStripState(HarnessFixtureTabs, "welcome")

    /**
     * The real geometry: the rail sits left of the main region, so the region the title bar lays its
     * segments on starts at the rail edge, exactly as the shell hands it over.
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
                        Box(surface.testTag("content-${state.activeId}")) { HarnessText("当前标签：${state.activeId}") }
                    }
                }
            }
        }
    }

    @Test fun mouseSelectionHistoryCloseAddAndHideRestore() = runSkikoComposeUiTest(size = Size(1200f, 600f)) {
        val state = state(); val drag = HarnessTabDragState()
        setContent { Fixture(state, drag) }
        onNodeWithTag("harness-tab-back").assertIsNotEnabled()
        onNodeWithTag("harness-tab-thread-a").performMouseInput { click() }
        onNodeWithTag("content-thread-a").assertIsDisplayed()
        onNodeWithTag("harness-tab-back").performMouseInput { click() }
        onNodeWithTag("content-welcome").assertIsDisplayed()
        onNodeWithTag("harness-tab-forward").performMouseInput { click() }
        onNodeWithTag("content-thread-a").assertIsDisplayed()
        onNodeWithTag("harness-tab-close-thread-a").performMouseInput { click() }
        onNodeWithTag("harness-tab-thread-a").assertDoesNotExist()
        onNodeWithTag("harness-tab-add").performMouseInput { click() }
        runOnIdle { assertEquals(3, state.tabs.size); assertEquals(3, state.tabs.map { it.id }.distinct().size) }
        // Opening a tab leaves the strips up and the mode control on 隐藏标签页.
        onNodeWithTag("harness-tab-welcome").assertIsDisplayed()
        onNodeWithTag("harness-tab-hide").assertIsDisplayed()
        onNodeWithTag("harness-tab-new").assertDoesNotExist()
    }

    /**
     * The title bar's two modes (隐藏标签页 ⇄ 新建标签页). Hiding swaps every pane's strip in place for the
     * page's own title bar — the live shell keeps the pane header in the strip's slot — and the control
     * becomes 新建标签页, which brings the strips back.
     */
    @Test fun hidingTabsSwapsTheStripForThePaneTitleAndComesBack() = runSkikoComposeUiTest(size = Size(1200f, 500f)) {
        val state = state(); val drag = HarnessTabDragState()
        setContent {
            Fixture(state, drag, paneTitle = { Box(Modifier.testTag("fixture-pane-title")) { HarnessText("聊天") } })
        }
        onNodeWithTag("harness-tab-welcome").assertIsDisplayed()
        onNodeWithTag("fixture-pane-title").assertDoesNotExist()
        val stripStart = onNodeWithTag("harness-tab-welcome").fetchSemanticsNode().boundsInRoot.left
        onNodeWithTag("harness-tab-hide").performMouseInput { click() }
        onNodeWithTag("harness-tab-welcome").assertDoesNotExist()
        onNodeWithTag("harness-tab-add").assertDoesNotExist()
        // The page's title takes the strip's place in the pane, on the same column it started at.
        onNodeWithTag("fixture-pane-title").assertIsDisplayed()
        assertEquals(
            stripStart,
            onNodeWithTag("fixture-pane-title").fetchSemanticsNode().boundsInRoot.left,
            1f,
        )
        onNodeWithTag("harness-tab-new").assertIsDisplayed().performMouseInput { click() }
        onNodeWithTag("fixture-pane-title").assertDoesNotExist()
        onNodeWithTag("harness-tab-hide").assertIsDisplayed()
        onNodeWithTag("harness-tab-welcome").assertIsDisplayed()
    }

    /**
     * The segmentation, measured on the live 44px strip of a 2560px split window: the leading chrome
     * carries the history cluster inset 88px clear of the traffic lights, the hairline that closes the
     * leading column falls at 230px while the navigator is hidden (340px while it is pushed) and the
     * pane's own content starts 8px past it, the panes are cut on the shell's main-region columns, and
     * the layout/tabs controls sit behind a shorter 16px hairline at the right edge.
     */
    @Test fun titleBarIsSegmentedAndTheClusterNeverCollidesWithThePanes() = runSkikoComposeUiTest(size = Size(1200f, 500f)) {
        val state = state(); val drag = HarnessTabDragState()
        setContent { Fixture(state, drag, onToggleNavigator = {}, onNewChat = {}) }
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
        // The pane starts at the rail and the rule is an overlay inside it, 8px clear of the tabs.
        val pane = bounds("harness-tab-pane-main")
        assertEquals(root.left + HarnessTokens.Control.RailWidth.value * scale, pane.left, 1f)
        // The cluster overflows the rail: the pane must clear the last control it paints, 8px past the
        // hairline — the measured 238px pane header.
        val lastControl = bounds("harness-tab-new-chat").right
        assertEquals(
            leadRule.right + HarnessTokens.Control.TabStripInsetStart.value * scale,
            bounds("harness-tab-welcome").left,
            1f,
        )
        assertTrue(
            bounds("harness-tab-welcome").left - lastControl >= HarnessTokens.Control.TabStripInsetStart.value * scale - 0.5f,
            "the tab strip must start past the leading cluster, not under it",
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
        // Splitting cuts the title bar on the very column the panes divide at below it.
        onNodeWithTag("harness-tab-pane-rule").assertDoesNotExist()
        onNodeWithTag("harness-tab-pane-secondary").assertDoesNotExist()
        onNodeWithTag("harness-tab-split").performMouseInput { click() }
        assertEquals(bounds("harness-split-seam").center.x, bounds("harness-tab-pane-rule").center.x, 2f)
        assertEquals(1f * scale, bounds("harness-tab-pane-rule").width, 1f)
        assertEquals(
            HarnessTokens.Control.SplitSeamWidth.value * scale,
            bounds("harness-tab-pane-secondary").left - bounds("harness-tab-pane-main").right,
            1f,
        )
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
            onNodeWithTag("harness-tab-welcome").assertIsDisplayed()
        }
        runSkikoComposeUiTest(size = Size(900f, 400f)) {
            val state = state(); val drag = HarnessTabDragState()
            setContent { Fixture(state, drag, onToggleNavigator = { toggles++ }) }
            onNodeWithTag("harness-tab-navigator").assertIsDisplayed().performMouseInput { click() }
            runOnIdle { assertEquals(1, toggles) }
            onNodeWithTag("harness-tab-welcome").assertIsDisplayed()
            onNodeWithTag("harness-tab-thread-a").assertIsDisplayed()
        }
    }

    @Test fun physicalDragSplitsSwapsAndIgnoresTitlebarDrop() = runSkikoComposeUiTest(size = Size(1200f, 600f)) {
        val state = state(); val drag = HarnessTabDragState()
        setContent { Fixture(state, drag) }
        // Both panes draw the same tab row while they are split, so a tab id resolves to one node per
        // pane; the drag starts on the first one.
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
        drop("thread-a", Offset(1000f, 300f))
        runOnIdle { assertEquals(HarnessTabLayout.Split("welcome", "thread-a"), state.layout) }
        onNodeWithTag("harness-split-left").assertIsDisplayed()
        onNodeWithTag("harness-split-right").assertIsDisplayed()
        drop("thread-a", Offset(100f, 300f))
        runOnIdle { assertEquals(HarnessTabLayout.Split("thread-a", "welcome"), state.layout) }
        drop("welcome", Offset(900f, 22f))
        runOnIdle { assertEquals(HarnessTabLayout.Split("thread-a", "welcome"), state.layout); assertFalse(drag.active) }
        // Closing a split member returns the survivor to the full width. The right pane's own tab is the
        // one whose close target is clear of the left pane's own scroll: a narrow left pane has to be
        // scrolled to reach a later tab's target, exactly as the live strip's own scroll does.
        onNodeWithTag("harness-tab-close-welcome").performMouseInput { click() }
        onNodeWithTag("harness-split-left").assertDoesNotExist()
        onNodeWithTag("content-thread-a").assertIsDisplayed()
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
                                    Box(surface.padding(24.dp)) { HarnessText("当前标签：${state.activeId}") }
                                }
                            }
                        }
                    }
                }
                // The navigator control is absent without a page toggle; the tab row and the
                // trailing controls stay put regardless.
                onNodeWithTag("harness-tab-navigator").assertDoesNotExist()
                onNodeWithTag("harness-tab-split").assertIsDisplayed().performMouseInput { click() }
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
     * The measured chrome, checked against the pixels rather than the modifier list: the band is the
     * page surface with no fill of its own, the selected tab is a white pill ringed by 8% ink, and an
     * unselected tab is bare — no pill, no chip background.
     */
    @Test fun stripBandIsTransparentAndOnlyTheSelectedTabDrawsAPill() = runSkikoComposeUiTest(size = Size(900f, 400f)) {
        val state = HarnessTabStripState(listOf(HarnessWorkspaceTab("a", "A"), HarnessWorkspaceTab("b", "B")), "a")
        val drag = HarnessTabDragState()
        setContent { Fixture(state, drag) }
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
        assertEquals(
            HarnessLightColors.AppBackground.toArgb().toLong() and 0xFFFFFFFFL,
            captured.argbAt(750, 22),
            "the tab band itself paints nothing",
        )
    }

    /**
     * Control behaviour off the live tab bar: the close target is present only on the selected tab and
     * on hover, the hover tip is the dark inverted pill, and it names the chord the shell really binds.
     */
    @Test fun closeTargetHidesAtRestAndHoverTipNamesTheBoundChord() = runSkikoComposeUiTest(size = Size(900f, 400f)) {
        mainClock.autoAdvance = false
        val state = HarnessTabStripState(listOf(HarnessWorkspaceTab("a", "A"), HarnessWorkspaceTab("b", "B")), "a")
        val drag = HarnessTabDragState()
        setContent {
            HarnessTheme {
                Column(Modifier.fillMaxSize().background(HarnessTokens.Colors.AppBackground)) {
                    HarnessTabStrip(state, drag, backShortcut = "\u2318[")
                    Row(Modifier.weight(1f)) {
                        Box(Modifier.width(HarnessTokens.Control.RailWidth).fillMaxHeight())
                        HarnessSplitMainRegion(state, drag, Modifier.weight(1f)) { surface -> Box(surface) }
                    }
                }
            }
        }
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
        onNodeWithTag("harness-tab-tip-harness-tab-back-text", useUnmergedTree = true).assertTextEquals("\u8fd4\u56de \u2318[")
    }

    /**
     * The seam is a 16px grab band, not a drawn divider: nothing is painted while it is idle, and the
     * 1px line that appears on hover is a vertical gradient fading out at both ends. Measured off the
     * live split view, where the pane separator reports `cursor-col-resize`, `width: 16px` and
     * `opacity: 0` until it is hovered.
     */
    @Test fun splitSeamIsAnInvisibleBandUntilHoverAndResizesThePanes() = runSkikoComposeUiTest(size = Size(1200f, 500f)) {
        val state = state(); val drag = HarnessTabDragState()
        setContent { Fixture(state, drag) }
        onNodeWithTag("harness-tab-split").performMouseInput { click() }
        val seam = onNodeWithTag("harness-split-seam").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        // The root fills the 1200px test window at the test density, so the scale recovers the 16dp band.
        val scale = onNodeWithTag("tabs-root").fetchSemanticsNode().boundsInRoot.width / 1200f
        assertEquals(16f * scale, seam.width, 1f, "the seam is the measured 16dp grab band")
        // Idle: the band paints nothing, so the pane surface runs unbroken behind it.
        assertEquals(
            HarnessLightColors.AppBackground.toArgb().toLong() and 0xFFFFFFFFL,
            onNodeWithTag("tabs-root").captureToImage().argbAt(seam.center.x.toInt(), seam.center.y.toInt()),
            "an idle seam must not paint a divider",
        )
        onNodeWithTag("harness-split-seam").performMouseInput { moveTo(center) }
        assertNotEquals(
            HarnessLightColors.AppBackground.toArgb().toLong() and 0xFFFFFFFFL,
            onNodeWithTag("tabs-root").captureToImage().argbAt(seam.center.x.toInt(), seam.center.y.toInt()),
            "hovering the band must reveal the hairlines",
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
        onNodeWithTag("harness-tab-thread-a").performMouseInput { moveTo(center) }
        advance(16)
        onNodeWithTag("harness-tab-close-thread-a").assertIsDisplayed()
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

    @Test fun stateNeverDuplicatesSplitMemberOrReusesOpenTabId() {
        val state = state()
        state.moveToHalf("welcome", right = true)
        assertEquals(HarnessTabLayout.Split("thread-a", "welcome"), state.layout)
        state.close("thread-a")
        assertEquals("welcome", state.activeId)
        assertEquals(HarnessTabLayout.Single, state.layout)
        state.addNewTab(); val first = state.activeId
        state.addNewTab(); state.close(first); state.addNewTab()
        assertEquals(state.tabs.size, state.tabs.map { it.id }.distinct().size)
        state.tabs.toList().forEach { state.close(it.id) }
        assertEquals("", state.activeId)
        state.moveToHalf("missing", true)
        assertEquals(HarnessTabLayout.Single, state.layout)
        state.addNewTab()
        assertEquals(1, state.tabs.size)
        state.moveToHalf(state.activeId, true)
        assertEquals(HarnessTabLayout.Single, state.layout)
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
}
