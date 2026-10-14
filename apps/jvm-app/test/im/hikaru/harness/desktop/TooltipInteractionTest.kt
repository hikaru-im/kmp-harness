@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package im.hikaru.harness.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import im.hikaru.harness.client.shell.*
import im.hikaru.harness.ui.foundation.*
import java.io.File
import java.security.MessageDigest
import javax.imageio.ImageIO
import kotlinx.serialization.json.*
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import kotlin.test.*

/** Physical input on the real trigger and independent popup roots; parent runs this serially. */
@OptIn(ExperimentalTestApi::class)
class TooltipInteractionTest {
    private class Fixture {
        var text: String? by mutableStateOf("Tooltip A")
        var enabled by mutableStateOf(true)
        var present by mutableStateOf(true)
        var scope by mutableStateOf("A")
        var suppressed by mutableStateOf(false)
        val calls = mutableListOf<String>()

        @Composable fun Content() {
            CompositionLocalProvider(LocalDensity provides Density(1f)) { HarnessTheme {
                Column(Modifier.fillMaxSize().padding(32.dp).testTag("tooltip-page"),
                    verticalArrangement = Arrangement.spacedBy(32.dp)) {
                    if (present) HarnessTooltipTrigger(text, enabled = enabled, scope = scope, popupSuppressed = suppressed) { input ->
                        Box(input.size(36.dp).background(HarnessTokens.Colors.SurfaceSelected)
                            .clickable { calls += "trigger-a" }.testTag("trigger-a"))
                    } else Spacer(Modifier.size(36.dp))
                    Box(Modifier.size(36.dp).background(HarnessTokens.Colors.SurfaceSelected)
                        .clickable { calls += "trigger-b" }.testTag("trigger-b"))
                }
            } }
        }
    }

    @Test fun hoverWaitsFor200MillisAndLeaveCloses() = runSkikoComposeUiTest(size = Size(600f, 800f)) {
        val f = Fixture(); mainClock.autoAdvance = false
        setContent { f.Content() }
        hover("trigger-a")
        advance(100)
        onNodeWithTag("harness-tooltip").assertDoesNotExist()
        advance(150)
        onNodeWithTag("harness-tooltip").assertIsDisplayed()
        onNodeWithText("Tooltip A").assertIsDisplayed()
        hover("trigger-b")
        onNodeWithTag("harness-tooltip").assertDoesNotExist()
        assertTrue(f.calls.isEmpty())
    }

    @Test fun earlyLeaveCancelsPendingHover() = runSkikoComposeUiTest {
        val f = Fixture(); mainClock.autoAdvance = false; setContent { f.Content() }
        hover("trigger-a"); advance(100); hover("trigger-b"); advance(400)
        onNodeWithTag("harness-tooltip").assertDoesNotExist(); assertTrue(f.calls.isEmpty())
    }

    @Test fun unmountCancelsPendingAndOpenTooltip() {
        for (open in listOf(false, true)) runSkikoComposeUiTest {
            val f = Fixture(); mainClock.autoAdvance = false; setContent { f.Content() }
            hover("trigger-a"); advance(if (open) 250 else 100)
            if (open) onNodeWithTag("harness-tooltip").assertIsDisplayed()
            runOnIdle { f.present = false }; advance(400)
            onNodeWithTag("harness-tooltip").assertDoesNotExist()
            onNodeWithTag("trigger-a").assertDoesNotExist(); assertTrue(f.calls.isEmpty())
        }
    }

    @Test fun tabFocusOpensImmediatelyAndPopupAddsNoTabStop() = runSkikoComposeUiTest {
        val f = Fixture(); mainClock.autoAdvance = false; setContent { f.Content() }
        key("trigger-a", Key.Tab)
        onNodeWithTag("trigger-a").assertIsFocused()
        onNodeWithTag("harness-tooltip").assertIsDisplayed()
        key("trigger-a", Key.Tab)
        onNodeWithTag("trigger-b").assertIsFocused()
        onNodeWithTag("harness-tooltip").assertDoesNotExist()
        shiftTab("trigger-b")
        onNodeWithTag("trigger-a").assertIsFocused()
        onNodeWithTag("harness-tooltip").assertIsDisplayed(); assertTrue(f.calls.isEmpty())
    }

    @Test fun escapeDoesNotReopenUnderHeldFocusOrHover() = runSkikoComposeUiTest {
        val f = Fixture(); mainClock.autoAdvance = false; setContent { f.Content() }
        key("trigger-a", Key.Tab); hover("trigger-a"); advance(250)
        key("trigger-a", Key.Escape); advance(400)
        onNodeWithTag("trigger-a").assertIsFocused()
        onNodeWithTag("harness-tooltip").assertDoesNotExist()
        hover("trigger-a"); advance(400)
        onNodeWithTag("harness-tooltip").assertDoesNotExist()
        hover("trigger-b"); hover("trigger-a"); advance(250)
        onNodeWithTag("harness-tooltip").assertIsDisplayed(); assertTrue(f.calls.isEmpty())
    }

    @Test fun clickDismissesAndDispatchesBusinessIdentityExactlyOnce() = runSkikoComposeUiTest {
        val f = Fixture(); mainClock.autoAdvance = false; setContent { f.Content() }
        hover("trigger-a"); advance(250)
        onNodeWithTag("harness-tooltip").assertIsDisplayed()
        pointer("trigger-a"); advance(400)
        assertEquals(listOf("trigger-a"), f.calls)
        onNodeWithTag("harness-tooltip").assertDoesNotExist()
        hover("trigger-a"); advance(400)
        onNodeWithTag("harness-tooltip").assertDoesNotExist()
        hover("trigger-b"); hover("trigger-a"); advance(250)
        onNodeWithTag("harness-tooltip").assertIsDisplayed()
    }

    @Test fun enterAndSpaceRetainTheFocusedTriggerBusinessAction() = runSkikoComposeUiTest {
        val f = Fixture(); mainClock.autoAdvance = false; setContent { f.Content() }
        key("trigger-a", Key.Tab)
        onNodeWithTag("harness-tooltip").assertIsDisplayed()
        key("trigger-a", Key.Enter); advance(400)
        onNodeWithTag("trigger-a").assertIsFocused()
        onNodeWithTag("harness-tooltip").assertDoesNotExist()
        assertEquals(listOf("trigger-a"), f.calls)
        key("trigger-a", Key.Tab)
        shiftTab("trigger-b"); onNodeWithTag("harness-tooltip").assertIsDisplayed()
        key("trigger-a", Key.Spacebar); advance(400)
        assertEquals(listOf("trigger-a", "trigger-a"), f.calls)
        onNodeWithTag("harness-tooltip").assertDoesNotExist()
    }

    @Test fun contextPressDismissesWithoutDispatchingPrimaryAction() = runSkikoComposeUiTest {
        val f = Fixture(); mainClock.autoAdvance = false; setContent { f.Content() }
        hover("trigger-a"); advance(250)
        val node = onNodeWithTag("trigger-a").fetchSemanticsNode()
        val inputRoot = generateSequence(node) { it.parent }.last()
        val started = System.nanoTime()
        root("trigger-a").performMouseInput {
            moveTo(node.boundsInRoot.center - inputRoot.boundsInRoot.topLeft)
            press(MouseButton.Secondary); release(MouseButton.Secondary)
        }
        assertTrue(System.nanoTime() - started < 10_000_000_000L, "Context press deadline")
        advance(400); onNodeWithTag("harness-tooltip").assertDoesNotExist(); assertTrue(f.calls.isEmpty())
    }

    @Test fun tooltipDisabledNullAndBlankStillAllowTriggerBusinessInput() {
        for (mode in listOf("disabled", "null", "blank")) runSkikoComposeUiTest {
            val f = Fixture().apply { when (mode) {
                "disabled" -> enabled = false
                "null" -> text = null
                "blank" -> text = "  "
            } }
            mainClock.autoAdvance = false; setContent { f.Content() }
            hover("trigger-a"); advance(250)
            onNodeWithTag("harness-tooltip").assertDoesNotExist()
            key("trigger-a", Key.Tab); onNodeWithTag("trigger-a").assertIsFocused()
            onNodeWithTag("harness-tooltip").assertDoesNotExist()
            key("trigger-a", Key.Enter); pointer("trigger-a")
            assertEquals(listOf("trigger-a", "trigger-a"), f.calls, mode)
        }
    }

    private enum class Change { Text, Disabled, Empty, Scope, Suppression }
    @Test fun pendingTextChangeCancelsOldIntent() = changedIntent(Change.Text, false)
    @Test fun openTextChangeCancelsOldContent() = changedIntent(Change.Text, true)
    @Test fun pendingDisabledCancelsOldIntent() = changedIntent(Change.Disabled, false)
    @Test fun openDisabledCancelsOldContent() = changedIntent(Change.Disabled, true)
    @Test fun pendingNullCancelsOldIntent() = changedIntent(Change.Empty, false)
    @Test fun openBlankCancelsOldContent() = changedIntent(Change.Empty, true)
    @Test fun pendingScopeAToBToACancelsOldIntent() = changedIntent(Change.Scope, false)
    @Test fun openScopeAToBToACancelsOldContent() = changedIntent(Change.Scope, true)
    @Test fun pendingOverlaySuppressionCancelsOldIntent() = changedIntent(Change.Suppression, false)
    @Test fun openOverlaySuppressionCancelsOldContent() = changedIntent(Change.Suppression, true)

    private fun changedIntent(change: Change, open: Boolean) = runSkikoComposeUiTest {
        val f = Fixture(); mainClock.autoAdvance = false; setContent { f.Content() }
        hover("trigger-a"); advance(if (open) 250 else 100)
        if (open) onNodeWithTag("harness-tooltip").assertIsDisplayed()
        runOnIdle { when (change) {
            Change.Text -> f.text = "Tooltip B"
            Change.Disabled -> f.enabled = false
            Change.Empty -> f.text = if (open) " " else null
            Change.Scope -> f.scope = "B"
            Change.Suppression -> f.suppressed = true
        } }
        advance(400); onNodeWithTag("harness-tooltip").assertDoesNotExist()
        runOnIdle { f.text = "Tooltip A"; f.enabled = true; f.scope = "A"; f.suppressed = false }
        advance(400); onNodeWithTag("harness-tooltip").assertDoesNotExist()
        hover("trigger-a"); advance(400)
        onNodeWithTag("harness-tooltip").assertDoesNotExist()
        hover("trigger-b"); hover("trigger-a"); advance(250)
        onNodeWithTag("harness-tooltip").assertIsDisplayed()
        onNodeWithText("Tooltip A").assertIsDisplayed(); onNodeWithText("Tooltip B").assertDoesNotExist()
        assertTrue(f.calls.isEmpty())
    }

    @Test fun focusedContentChangeRequiresBlurAndRefocus() = runSkikoComposeUiTest {
        val f = Fixture(); mainClock.autoAdvance = false; setContent { f.Content() }
        key("trigger-a", Key.Tab); onNodeWithTag("harness-tooltip").assertIsDisplayed()
        runOnIdle { f.text = "Tooltip B" }; advance(400)
        onNodeWithTag("trigger-a").assertIsFocused(); onNodeWithTag("harness-tooltip").assertDoesNotExist()
        key("trigger-a", Key.Tab)
        shiftTab("trigger-b"); onNodeWithText("Tooltip B").assertIsDisplayed(); assertTrue(f.calls.isEmpty())
    }

    @Test fun actualShellNavigationAndMoreKeepRailGeometryAndIdentity() = runSkikoComposeUiTest(size = Size(1440f, 900f)) {
        var state by mutableStateOf(AppShellState())
        val calls = mutableListOf<String>(); val pinCalls = mutableListOf<RailDestination>(); var moreCalls = 0; var accountCalls = 0
        mainClock.autoAdvance = false
        setContent { CompositionLocalProvider(LocalDensity provides Density(1f)) {
            AppShell(
                state = state,
                navItems = harnessRailItems(state.navigation.currentPage, pinned = state.pinnedRailDestinations),
                onNavSelect = { item ->
                    calls += item.id
                    state = state.copy(navigation = state.navigation.navigate(assertNotNull(railItemPage(item.id))))
                },
                onOpenExploreMenu = { moreCalls++; state = state.copy(exploreMenu = state.exploreMenu.open()) },
                onDismissExploreMenu = { state = state.copy(exploreMenu = state.exploreMenu.close()) },
                onTogglePinRailDestination = { destination ->
                    pinCalls += destination
                    state = state.copy(pinnedRailDestinations =
                        if (destination in state.pinnedRailDestinations) state.pinnedRailDestinations - destination
                        else state.pinnedRailDestinations + destination)
                },
                onOpenAccountMenu = { accountCalls++; state = state.copy(accountMenu = state.accountMenu.open()) },
                onDismissAccountMenu = { state = state.copy(accountMenu = state.accountMenu.close()) },
                navigator = state.workspace, projectNodes = emptyList(), recentTasks = emptyList(),
                onToggleSidebar = {}, onNewTask = {}, onNavigatorSearch = {},
                onToggleProject = {}, onSelectNode = {}, onSelectRecent = {},
                themeMode = HarnessThemeMode.Light,
                content = { Box(Modifier.fillMaxSize().testTag("shell-page")) },
            )
        } }
        settleGitLayout()
        val before = listOf("app-rail", "rail-home", "rail-scheduled", "shell-page").associateWith {
            onNodeWithTag(it).fetchSemanticsNode().boundsInWindow
        }
        assertEquals(52f, before.getValue("app-rail").width)
        assertEquals(36f, before.getValue("rail-home").width)
        assertEquals(36f, before.getValue("rail-home").height)
        assertEquals(44f, before.getValue("rail-scheduled").top - before.getValue("rail-home").top)
        hover("rail-plugins"); advance(250)
        onNodeWithTag("rail-tooltip-plugins").assertIsDisplayed(); onNodeWithText("插件").assertIsDisplayed()
        before.forEach { (tag, bounds) -> assertEquals(bounds, onNodeWithTag(tag).fetchSemanticsNode().boundsInWindow, tag) }
        pointer("rail-plugins")
        waitUntil(timeoutMillis = 10_000) { state.navigation.currentPage == PageId.Plugins }
        assertEquals(listOf("plugins"), calls); advance(400)
        onNodeWithTag("rail-tooltip-plugins").assertDoesNotExist()
        hover("rail-explore"); advance(250); onNodeWithTag("rail-tooltip-explore").assertIsDisplayed()
        pointer("rail-explore")
        onNodeWithTag("explore-menu").assertIsDisplayed()
        onNodeWithTag("rail-tooltip-explore").assertDoesNotExist(); assertEquals(1, moreCalls)
        hover("rail-home"); advance(250); onNodeWithTag("rail-tooltip-home").assertDoesNotExist()
        // Pinning 项目 from the menu adds the second rail group without navigating.
        pointer("explore-pin-projects")
        assertEquals(listOf(RailDestination.Projects), pinCalls.toList())
        advance(400)
        assertTrue(RailDestination.Projects in state.pinnedRailDestinations)
        assertEquals(listOf("plugins"), calls)
        pointer("rail-account")
        assertEquals(1, accountCalls); assertTrue(state.accountMenu.isOpen)
        onNodeWithTag("account-menu").assertIsDisplayed()
        hover("rail-plugins"); advance(250); onNodeWithTag("rail-tooltip-plugins").assertDoesNotExist()
        assertEquals(52f, onNodeWithTag("app-rail").fetchSemanticsNode().boundsInWindow.width)
    }

    @Test fun railKeyboardUsesActualItemAndDisabledItemStaysDisabled() = runSkikoComposeUiTest(size = Size(1440f, 900f)) {
        val calls = mutableListOf<String>()
        mainClock.autoAdvance = false
        setContent { CompositionLocalProvider(LocalDensity provides Density(1f)) { HarnessTheme {
            AppRail(harnessRailItems(PageId.Home).mapIndexed { index, item -> if (index == 1) item.copy(enabled = false) else item },
                onNavSelect = { calls += it.id })
        } } }
        key("rail-home", Key.Tab)
        onNodeWithTag("rail-home").assertIsFocused(); onNodeWithTag("rail-tooltip-home").assertIsDisplayed()
        key("rail-home", Key.Enter); assertEquals(listOf("home"), calls)
        onNodeWithTag("rail-tooltip-home").assertDoesNotExist()
        key("rail-home", Key.Tab)
        onNodeWithTag("rail-library").assertIsFocused(); onNodeWithTag("rail-tooltip-library").assertIsDisplayed()
        key("rail-library", Key.Spacebar); assertEquals(listOf("home", "library"), calls)
        hover("rail-scheduled"); advance(250); onNodeWithTag("rail-tooltip-scheduled").assertDoesNotExist()
        onNodeWithTag("rail-scheduled").assertIsNotEnabled(); pointer("rail-scheduled")
        assertEquals(listOf("home", "library"), calls)
    }

    @Test fun legacyVisibleSignatureUsesPopupWithoutLayoutShift() = runSkikoComposeUiTest {
        var visible by mutableStateOf(false); mainClock.autoAdvance = false
        setContent { HarnessTheme { Column {
            if (visible) {
                HarnessTooltip("legacy", true) { Box(Modifier.size(36.dp).testTag("legacy-anchor")) }
            } else {
                HarnessTooltip("legacy", false) { Box(Modifier.size(36.dp).testTag("legacy-anchor")) }
            }
            Box(Modifier.size(36.dp).testTag("legacy-next"))
        } } }
        settleGitLayout()
        val before = onNodeWithTag("legacy-next").fetchSemanticsNode().boundsInWindow
        runOnIdle { visible = true }; settleGitLayout()
        onNodeWithTag("harness-tooltip").assertIsDisplayed()
        assertEquals(before, onNodeWithTag("legacy-next").fetchSemanticsNode().boundsInWindow)
        assertNotEquals(root("legacy-anchor").fetchSemanticsNode().id, root("harness-tooltip").fetchSemanticsNode().id)
        runOnIdle { visible = false }; settleGitLayout(); onNodeWithTag("harness-tooltip").assertDoesNotExist()
    }

    @Test fun popup600Light() = popupCapture(600, 800, HarnessThemeMode.Light, LayoutDirection.Ltr)
    @Test fun popup600Dark() = popupCapture(600, 800, HarnessThemeMode.Dark, LayoutDirection.Rtl)
    @Test fun popup1440Light() = popupCapture(1440, 900, HarnessThemeMode.Light, LayoutDirection.Rtl)
    @Test fun popup1440Dark() = popupCapture(1440, 900, HarnessThemeMode.Dark, LayoutDirection.Ltr)

    private fun popupCapture(width: Int, height: Int, theme: HarnessThemeMode, direction: LayoutDirection) =
        runSkikoComposeUiTest(size = Size(width.toFloat(), height.toFloat())) {
            mainClock.autoAdvance = false
            val text = "Full tooltip label 工作区 " + "long path / complete wrapping / ".repeat(12) + "END"
            setContent { CompositionLocalProvider(LocalDensity provides Density(1f), LocalLayoutDirection provides direction) {
                HarnessTheme(theme) { Box(Modifier.fillMaxSize().testTag("capture-page")) {
                    HarnessTooltipTrigger(text, modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                        align = HarnessPopoverAlign.Start) { input ->
                        Box(input.size(36.dp).background(HarnessTokens.Colors.SurfaceSelected).clickable {}.testTag("capture-anchor"))
                    }
                } }
            } }
            hover("capture-anchor"); advance(250)
            val results = mutableListOf<TextLayoutResult>()
            onNodeWithTag("harness-tooltip-text", true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
            assertTrue(results.any { it.layoutInput.text.text == text && it.lineCount > 1 &&
                (0 until it.lineCount).none { line -> it.isLineEllipsized(line) } }, "Full label must wrap without ellipsis")
            val popup = onNodeWithTag("harness-tooltip", true).fetchSemanticsNode().boundsInWindow
            val anchor = onNodeWithTag("capture-anchor").fetchSemanticsNode().boundsInWindow
            assertTrue(popup.top >= anchor.bottom + 6f, "Top placement must flip below the top-edge trigger: $popup, $anchor")
            if (direction == LayoutDirection.Ltr) assertEquals(anchor.left, popup.left)
            else assertEquals(anchor.right, popup.right)
            capture("tooltip-${width}x$height-${theme.name.lowercase()}", width, height, theme, direction)
        }

    @Test fun rtlLogicalEndFlipsBesideLeftEdgeTrigger() = runSkikoComposeUiTest(size = Size(600f, 800f)) {
        mainClock.autoAdvance = false
        setContent { CompositionLocalProvider(LocalDensity provides Density(1f), LocalLayoutDirection provides LayoutDirection.Rtl) {
            HarnessTheme { Box(Modifier.fillMaxSize()) {
                HarnessTooltipTrigger("RTL end", side = HarnessPopoverSide.End,
                    modifier = Modifier.align(Alignment.CenterEnd).padding(8.dp)) { input ->
                    Box(input.size(36.dp).clickable {}.testTag("capture-anchor"))
                }
            } }
        } }
        key("capture-anchor", Key.Tab)
        val anchor = onNodeWithTag("capture-anchor").fetchSemanticsNode().boundsInWindow
        val popup = onNodeWithTag("harness-tooltip", true).fetchSemanticsNode().boundsInWindow
        assertEquals(8f, anchor.left)
        assertEquals(anchor.right + 6f, popup.left, "RTL logical end prefers left then flips to available right")
        assertTrue(popup.right <= 592f && popup.top >= 8f && popup.bottom <= 792f)
    }

    @Test fun tinyRtlEndTooltipClampsAndRealWheelReadsFullText() = runSkikoComposeUiTest(size = Size(160f, 200f)) {
        mainClock.autoAdvance = false
        val text = "Complete long content 工作区 ".repeat(100) + "END"
        setContent { CompositionLocalProvider(LocalDensity provides Density(1f), LocalLayoutDirection provides LayoutDirection.Rtl) {
            HarnessTheme { Box(Modifier.fillMaxSize()) {
                HarnessTooltipTrigger(text, side = HarnessPopoverSide.End,
                    modifier = Modifier.align(Alignment.CenterEnd).padding(8.dp)) { input ->
                    Box(input.size(36.dp).background(HarnessTokens.Colors.SurfaceSelected).clickable {}.testTag("capture-anchor"))
                }
            } }
        } }
        // Keyboard focus keeps the noninteractive popup mounted while the mouse enters its reader.
        key("capture-anchor", Key.Tab); onNodeWithTag("capture-anchor").assertIsFocused()
        val bounds = onNodeWithTag("harness-tooltip", true).fetchSemanticsNode().boundsInWindow
        assertTrue(bounds.left >= 8f && bounds.top >= 8f && bounds.right <= 152f && bounds.bottom <= 192f, "$bounds")
        assertTrue(bounds.width <= 144f && bounds.height <= 184f)
        // Desktop wheel scrolling animates. Let the shared helper's waitForIdle settle the
        // real wheel before checking the end; the paused hover clock advances only one frame.
        mainClock.autoAdvance = true
        println("TOOLTIP_READER_CLOCK autoAdvance=${mainClock.autoAdvance}")
        val started = System.nanoTime()
        pluginsPageScrollToEnd("harness-tooltip-text")
        assertTrue(System.nanoTime() - started < 10_000_000_000L)
        onNodeWithTag("harness-tooltip-text", true).assertTextEquals(text)
        val results = mutableListOf<TextLayoutResult>()
        onNodeWithTag("harness-tooltip-text", true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        assertTrue(results.any { it.lineCount > 1 && (0 until it.lineCount).none { line -> it.isLineEllipsized(line) } })
    }

    private fun ComposeUiTest.advance(millis: Long) { mainClock.advanceTimeBy(millis); waitForIdle() }

    private fun ComposeUiTest.root(tag: String): SemanticsNodeInteraction {
        val node = onNodeWithTag(tag, true).fetchSemanticsNode()
        val root = generateSequence(node) { it.parent }.last()
        return onNode(SemanticsMatcher("Tooltip input root ${root.id}") { it.id == root.id }, true)
    }

    private fun ComposeUiTest.hover(tag: String) {
        val started = System.nanoTime(); settleGitLayout()
        val node = onNodeWithTag(tag).assertIsDisplayed().fetchSemanticsNode()
        val inputRoot = generateSequence(node) { it.parent }.last()
        assertTrue(node.boundsInRoot.width > 0 && node.boundsInRoot.height > 0)
        root(tag).performMouseInput { moveTo(node.boundsInRoot.center - inputRoot.boundsInRoot.topLeft) }
        settleGitLayout()
        assertTrue(System.nanoTime() - started < 10_000_000_000L, "Hover deadline: $tag")
    }

    private fun ComposeUiTest.pointer(tag: String) {
        val started = System.nanoTime(); clickGitNode(onNodeWithTag(tag))
        assertTrue(System.nanoTime() - started < 10_000_000_000L, "Pointer deadline: $tag")
    }

    private fun ComposeUiTest.key(tag: String, key: Key) {
        val started = System.nanoTime(); settleGitLayout()
        root(tag).performKeyInput { keyDown(key); keyUp(key) }; settleGitLayout()
        assertTrue(System.nanoTime() - started < 10_000_000_000L, "Keyboard deadline: $tag")
    }

    private fun ComposeUiTest.shiftTab(tag: String) {
        val started = System.nanoTime(); settleGitLayout()
        root(tag).performKeyInput { keyDown(Key.ShiftLeft); keyDown(Key.Tab); keyUp(Key.Tab); keyUp(Key.ShiftLeft) }
        settleGitLayout()
        assertTrue(System.nanoTime() - started < 10_000_000_000L, "Shift Tab deadline: $tag")
    }

    private fun ComposeUiTest.capture(name: String, width: Int, height: Int, theme: HarnessThemeMode, direction: LayoutDirection) {
        settleGitLayout()
        val popup = onNodeWithTag("harness-tooltip", true).assertIsDisplayed().fetchSemanticsNode()
        val popupRoot: SemanticsNode = generateSequence(popup) { it.parent }.last()
        val anchor = onNodeWithTag("capture-anchor").fetchSemanticsNode()
        val anchorRoot = generateSequence(anchor) { it.parent }.last()
        assertNotEquals(anchorRoot.id, popupRoot.id, "Capture actual independent popup root")
        val bounds = popup.boundsInWindow
        assertTrue(bounds.left >= 8f && bounds.top >= 8f && bounds.right <= width - 8f && bounds.bottom <= height - 8f, "$bounds")
        assertTrue(bounds.width <= 320f)
        val bitmap = root("harness-tooltip").captureToImage()
        val bytes = Image.makeFromBitmap(bitmap.asSkiaBitmap()).use { image ->
            image.encodeToData(EncodedImageFormat.PNG)?.use { it.bytes } ?: error("PNG encoding failed")
        }
        val directory = File(System.getenv("HARNESS_TOOLTIP_CAPTURE_DIR")?.takeIf { it.isNotBlank() }
            ?: "/tmp/harness-tooltip-captures-20261008")
        assertTrue(directory.isDirectory || directory.mkdirs())
        val png = File(directory, "$name.png").also { it.writeBytes(bytes) }
        val decoded = assertNotNull(ImageIO.read(png))
        assertEquals(popupRoot.size.width, decoded.width); assertEquals(popupRoot.size.height, decoded.height)
        val record = buildJsonObject {
            put("schema", "harness.tooltip.popup.v1"); put("image", png.name)
            put("windowWidth", width); put("windowHeight", height); put("theme", theme.name)
            put("layoutDirection", direction.name); put("density", 1f)
            put("captureRootId", popupRoot.id); put("anchorRootId", anchorRoot.id)
            put("captureWidth", decoded.width); put("captureHeight", decoded.height)
            put("rootBoundsInWindow", popupRoot.boundsInWindow.toString())
            put("popupBoundsInWindow", bounds.toString()); put("anchorBoundsInWindow", anchor.boundsInWindow.toString())
            put("windowBounds", "0,0,$width,$height")
            put("sha256", MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
        }
        File(directory, "$name.geometry.json").writeText(Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), record))
    }
}
