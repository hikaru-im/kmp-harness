@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)

package im.hikaru.harness.desktop

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import im.hikaru.harness.agent.AgentId
import im.hikaru.harness.agent.agents
import im.hikaru.harness.client.app.HarnessApp
import im.hikaru.harness.client.connection.*
import im.hikaru.harness.desktop.terminal.RealPtyPageFixture
import im.hikaru.harness.ui.foundation.HarnessThemeMode
import im.hikaru.harness.ui.foundation.HarnessLightColors
import im.hikaru.harness.ui.foundation.HarnessDarkColors
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import java.nio.charset.StandardCharsets.UTF_8
import java.awt.Component
import java.awt.event.KeyEvent as AwtKeyEvent
import java.io.File
import java.nio.file.Files
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import kotlin.test.*

/** Full HarnessApp and real page controls. Offscreen capture does not certify native IME or parity. */
class PtyTerminalPageInteractionTest {
    @Test fun fullHarnessAppOpensReadOnlyAndCreatesOnlyOnRealClick() = runBlocking<Unit> {
        RealPtyPageFixture.use { f ->
            runSkikoComposeUiTest(size = Size(1440f, 900f)) {
                val clipboard = PageClipboard()
                setContent { CompositionLocalProvider(LocalClipboardManager provides clipboard) {
                    HarnessApp(f.connection, terminalLaunchProvider = f.desktopProvider, themeMode = HarnessThemeMode.Light)
                } }
                openTerminal(f)
                onNodeWithTag("pty-terminal-create", true).assertIsEnabled()
                action("pty-terminal-refresh"); waitForIdle(); assertEquals(0, f.creates.get())
                click("terminal-view-background"); click("terminal-view-interactive"); assertEquals(0, f.creates.get())
                click("pty-terminal-create"); awaitTag("pty-terminal-grid")
                waitUntil(timeoutMillis = 30_000) { f.creates.get() == 1 }
                command(f, f.pidPrefix() + "printf 'PAGE_READY\\r\\n'")
                awaitOutput(f, "PAGE_READY"); waitUntil(timeoutMillis = 30_000) { f.recordOwned(); f.ownedProcesses().isNotEmpty() }; assertTrue(f.ownedProcesses().single().isAlive)
                assertEquals(1, f.creates.get())
                onNodeWithContentDescription("关闭后台终端", true).performMouseInput { click() }
                waitUntil(timeoutMillis = 30_000) { !exists("pty-terminal-grid") }
                assertEquals(0, f.closes.get()); assertTrue(f.ownedProcesses().single().isAlive)
                onNodeWithText("终端").performMouseInput { click() }; awaitTag("terminal-view-background")
                assertFalse(exists("pty-terminal-grid"), "Reopened terminal defaults to background output")
                click("terminal-view-interactive"); awaitGrid("PAGE_READY"); assertEquals(1, f.creates.get())
            }
        }
    }

    @Test fun focusedTypingPasteKeysAndResizeUseRealController() = runBlocking<Unit> {
        RealPtyPageFixture.use { f ->
            runSkikoComposeUiTest(size = Size(1440f, 900f)) {
                val clipboard = PageClipboard()
                setContent { CompositionLocalProvider(LocalClipboardManager provides clipboard) {
                    HarnessApp(f.connection, terminalLaunchProvider = f.desktopProvider)
                } }
                openTerminal(f); click("pty-terminal-create"); awaitTag("pty-terminal-grid")
                // Enter raw mode using the real interactive Desktop shell, then independent dd/od capture.
                command(f, f.captureScript(listOf("z", "\u001b[200~粘贴😀\u001b[201~", "\u001b[A", "\u0003"), captureResize = true))
                awaitGrid("READY_0"); waitUntil(timeoutMillis = 30_000) { Files.exists(f.cwd.resolve("bytes-0.bin")) }
                click("pty-terminal-grid"); onNodeWithTag("pty-terminal-input", true).assertIsFocused()
                typeAsciiCharacter('z', Key.Z); runBlocking<Unit> { f.expectBytes(0, "z") }
                awaitGrid("READY_1"); waitUntil(timeoutMillis = 30_000) { Files.exists(f.cwd.resolve("bytes-1.bin")) }; clipboard.setText(AnnotatedString("粘贴😀"))
                click("pty-terminal-grid")
                onNodeWithTag("pty-terminal-input", true).performKeyInput {
                    keyDown(Key.MetaLeft); pressKey(Key.V); keyUp(Key.MetaLeft)
                }
                // Bracketed paste is enabled by captureScript. Exact bytes are independent literals.
                runBlocking<Unit> { f.expectBytes(1, "\u001b[200~粘贴😀\u001b[201~") }
                awaitGrid("READY_2"); waitUntil(timeoutMillis = 30_000) { Files.exists(f.cwd.resolve("bytes-2.bin")) }
                onNodeWithTag("pty-terminal-input", true).performKeyInput { pressKey(Key.DirectionUp) }
                runBlocking<Unit> { f.expectBytes(2, "\u001b[A") }
                awaitGrid("READY_3"); waitUntil(timeoutMillis = 30_000) { Files.exists(f.cwd.resolve("bytes-3.bin")) }
                onNodeWithTag("pty-terminal-input", true).performKeyInput {
                    keyDown(Key.CtrlLeft); pressKey(Key.C); keyUp(Key.CtrlLeft)
                }
                runBlocking<Unit> { f.expectBytes(3, "\u0003") }; awaitOutput(f, "DRAIN")
                assertTrue(f.inputs.contains(TerminalInput.Text("z")))
                assertTrue(f.inputs.contains(TerminalInput.Paste("粘贴😀")))
                assertEquals(1, f.inputs.count { it == TerminalInput.KeyPress(TerminalKeyName.Up) })
                assertEquals(1, f.inputs.count { it == TerminalInput.KeyPress(TerminalKeyName.Character, setOf(TerminalModifier.Control), "c") })
                val gridBefore = onNodeWithTag("pty-terminal-grid", true).fetchSemanticsNode().boundsInRoot
                val key = f.keys.single()
                val snapshotBefore = runBlocking { f.snapshot(key) }
                val resizeBefore = f.resizes.get()
                // Reach the navigator's 520px clamp so the fixed 640px panel must shrink.
                val edge = onNodeWithTag("navigator-resize-handle", true).fetchSemanticsNode().boundsInRoot.center
                onRoot().performMouseInput { moveTo(edge); press(); advanceEventTime(16); moveTo(edge + Offset(300f, 0f)); release() }
                waitUntil(timeoutMillis = 30_000) {
                    f.resizes.get() > resizeBefore &&
                        onNodeWithTag("pty-terminal-grid", true).fetchSemanticsNode().boundsInRoot.width < gridBefore.width &&
                        runBlocking { f.snapshot(key) }.size.columns != snapshotBefore.size.columns
                }
                val gridAfter = onNodeWithTag("pty-terminal-grid", true).fetchSemanticsNode().boundsInRoot
                val snapshotAfter = runBlocking { f.snapshot(key) }
                assertTrue(gridBefore != gridAfter); assertTrue(gridAfter.width < gridBefore.width)
                assertNotEquals(snapshotBefore.size, snapshotAfter.size)
                assertNotEquals(snapshotBefore.size.columns, snapshotAfter.size.columns)
                assertEquals(key, snapshotAfter.metadata.key)
                assertEquals(TerminalLifecycle.Running, snapshotAfter.metadata.lifecycle)
                click("pty-terminal-grid"); onNodeWithTag("pty-terminal-input", true).assertIsFocused()
                typeAsciiCharacter('r', Key.R)
                val expectedKernelSize = "${snapshotAfter.size.rows} ${snapshotAfter.size.columns}"
                waitUntil(timeoutMillis = 30_000) {
                    Files.exists(f.cwd.resolve("size-trigger.hex")) && Files.exists(f.cwd.resolve("kernel-size")) &&
                        Files.readString(f.cwd.resolve("size-trigger.hex")).filterNot { it.isWhitespace() } == "72" &&
                        Files.readString(f.cwd.resolve("kernel-size")).trim() == expectedKernelSize
                }
                awaitOutput(f, "SIZE_DONE")
                assertContentEquals(byteArrayOf(0x72), Files.readAllBytes(f.cwd.resolve("size-trigger.bin")))
                assertEquals("72", Files.readString(f.cwd.resolve("size-trigger.hex")).filterNot { it.isWhitespace() })
                assertEquals(expectedKernelSize, Files.readString(f.cwd.resolve("kernel-size")).trim())
                assertEquals(snapshotAfter.size, runBlocking { f.snapshot(key) }.size)
                assertEquals(1, f.inputs.count { it == TerminalInput.Text("r") })
                assertEquals(1, f.creates.get())
                assertEquals(0L, Files.size(f.cwd.resolve("extra.bin")))
            }
        }
    }

    @Test fun wideAndCompactLightDarkMappedGridRemainUsable() = runBlocking<Unit> {
        for ((width, height) in listOf(1440 to 900, 600 to 800)) for (theme in listOf(HarnessThemeMode.Light, HarnessThemeMode.Dark)) {
            RealPtyPageFixture.use { f ->
                runSkikoComposeUiTest(size = Size(width.toFloat(), height.toFloat())) {
                    setContent { HarnessApp(f.connection, terminalLaunchProvider = f.desktopProvider, themeMode = theme) }
                    openTerminal(f); click("pty-terminal-create"); awaitTag("pty-terminal-grid")
                    command(f, f.pidPrefix() + "printf '\\033[2J\\033[H\\033[31mA中x😀\\033[0m\\r\\nRUNNING\\r\\n'")
                    awaitOutput(f, "RUNNING")
                    val key = f.keys.single()
                    val running = runBlocking { f.snapshot(key) }
                    assertEquals(TerminalLifecycle.Running, running.metadata.lifecycle)
                    onNodeWithTag("pty-terminal-select").assertTextEquals("终端 1 · 运行中")
                    assertTrue(running.screen.any { it.text.trimEnd() == "A中x😀" })
                    assertTrue((running.screen + running.history).flatMap { it.runs }.all { it.columnOffsets != null })
                    capture(f, width, height, theme, "Running", controlled = false)
                    command(f, "printf '\\r\\nFINAL中😀\\r\\n'; exit 7")
                    awaitTag("pty-terminal-exited"); awaitOutput(f, "FINAL中😀")
                    assertEquals(TerminalLifecycle.Exited(7), runBlocking { f.snapshot(key) }.metadata.lifecycle)
                    onNodeWithTag("pty-terminal-select").assertTextEquals("终端 1 · 已退出 (7)")
                    openActions()
                    onNodeWithTag("pty-terminal-paste", true).assertIsNotEnabled()
                    closeActions()
                    capture(f, width, height, theme, "Exited", controlled = false)
                    assertEquals(1, f.creates.get()); assertEquals(0, f.closes.get())
                }
            }
        }
    }

    @Test fun historySelectionCopyAndExitedScreenRemainVisible() = runBlocking<Unit> {
        RealPtyPageFixture.use { f ->
            runSkikoComposeUiTest(size = Size(1440f, 900f)) {
                val clipboard = PageClipboard()
                setContent { CompositionLocalProvider(LocalClipboardManager provides clipboard) {
                    HarnessApp(f.connection, terminalLaunchProvider = f.desktopProvider)
                } }
                openTerminal(f); click("pty-terminal-create"); awaitTag("pty-terminal-grid")
                command(f, f.pidPrefix() + "printf '\\033[2J\\033[H'; i=0; while [ \"${'$'}i\" -lt 80 ]; do printf 'row-%02d 中😀\\r\\n' \"${'$'}i\"; i=${'$'}((i+1)); done; printf 'HISTORY_DONE\\r\\n'")
                awaitOutput(f, "HISTORY_DONE")
                val expectedRows = (0 until 80).map { "row-${it.toString().padStart(2, '0')} 中😀" }
                fun selectFirstHistoryRow() {
                    awaitGrid(expectedRows.first())
                    val grid = onNodeWithTag("pty-terminal-grid", true).fetchSemanticsNode()
                    val visibleLines = grid.config[SemanticsProperties.Text].flatMap { value ->
                        value.text.lines().map { it.trimEnd() }
                    }
                    val row = visibleLines.indexOf(expectedRows.first())
                    assertTrue(row >= 0)
                    val bounds = grid.boundsInRoot
                    val rowY = (row + 0.5f) * bounds.height / visibleLines.size
                    onNodeWithTag("pty-terminal-grid", true).performMouseInput {
                        moveTo(Offset(1f, rowY)); press(); advanceEventTime(16); moveTo(Offset(bounds.width - 1f, rowY)); release()
                    }
                }
                waitUntil(timeoutMillis = 30_000) {
                    val snapshot = runBlocking { f.snapshot(f.keys.single()) }
                    snapshot.metadata.lifecycle == TerminalLifecycle.Running &&
                        snapshot.history.any { it.text.trimEnd() == expectedRows.first() } &&
                        (snapshot.history + snapshot.screen).map { it.text.trimEnd() }.containsAll(expectedRows) &&
                        snapshot.screen.any { it.text.trimEnd() == "HISTORY_DONE" }
                }
                val historyReady = runBlocking { f.snapshot(f.keys.single()) }
                assertTrue(historyReady.history.any { it.text.trimEnd() == expectedRows.first() })
                assertEquals(expectedRows, (historyReady.history + historyReady.screen).map { it.text.trimEnd() }.filter { it in expectedRows })
                // Pointer scrollbar goes to history; drag selection uses the same native cell map.
                val bar = onNodeWithTag("pty-terminal-scrollbar", true)
                bar.performMouseInput { click(Offset(center.x, 1f)) }; waitForIdle()
                openActions()
                onNodeWithTag("pty-terminal-bottom", true).assertIsEnabled()
                closeActions()
                selectFirstHistoryRow()
                openActions()
                onNodeWithTag("pty-terminal-copy", true).assertIsEnabled(); action("pty-terminal-copy")
                val buttonCopy = assertNotNull(clipboard.getText()).text
                assertEquals(expectedRows.first(), buttonCopy.trimEnd())
                assertTrue(buttonCopy.contains("row-")); assertFalse(buttonCopy.contains('\u0000'))
                click("pty-terminal-grid")
                // Select again, then use the platform copy chord and verify it never reaches PTY.
                selectFirstHistoryRow()
                val writes = f.nativeWrites.get()
                waitForIdle(); onNodeWithTag("pty-terminal-input", true).assertIsFocused()
                val runningSentinel = "running-copy-pending"
                clipboard.setText(AnnotatedString(runningSentinel))
                onNodeWithTag("pty-terminal-input", true).performKeyInput { keyDown(Key.MetaLeft); pressKey(Key.C); keyUp(Key.MetaLeft) }
                waitForIdle()
                val runningCopy = assertNotNull(clipboard.getText()).text
                assertNotEquals(runningSentinel, runningCopy)
                assertEquals(expectedRows.first(), runningCopy.trimEnd())
                assertTrue(runningCopy.contains("row-")); assertFalse(runningCopy.contains('\u0000'))
                assertEquals(writes, f.nativeWrites.get())
                action("pty-terminal-bottom"); awaitGrid("HISTORY_DONE")
                command(f, "printf '\\r\\nRETAINED_FINAL\\r\\n'; exit 0"); awaitTag("pty-terminal-exited"); awaitOutput(f, "RETAINED_FINAL")
                val exited = runBlocking { f.snapshot(f.keys.single()) }
                assertTrue(exited.history.isNotEmpty())
                assertEquals(TerminalLifecycle.Exited(0), exited.metadata.lifecycle)
                val exitedWrites = f.nativeWrites.get()
                val exitedCloses = f.closes.get()
                bar.performMouseInput { click(Offset(center.x, 1f)) }; waitForIdle()
                openActions()
                onNodeWithTag("pty-terminal-bottom", true).assertIsEnabled()
                closeActions()
                selectFirstHistoryRow()
                waitForIdle()
                openActions()
                onNodeWithTag("pty-terminal-copy", true).assertIsEnabled()
                closeActions()
                // Real drag restores the editor focus and the same row selection after popup focus.
                selectFirstHistoryRow(); waitForIdle()
                onNodeWithTag("pty-terminal-input", true).assertIsFocused()
                val exitedSentinel = "exited-copy-pending"
                clipboard.setText(AnnotatedString(exitedSentinel))
                onNodeWithTag("pty-terminal-input", true).performKeyInput { keyDown(Key.MetaLeft); pressKey(Key.C); keyUp(Key.MetaLeft) }
                waitForIdle()
                val exitedCopy = assertNotNull(clipboard.getText()).text
                assertNotEquals(exitedSentinel, exitedCopy)
                assertEquals(expectedRows.first(), exitedCopy.trimEnd())
                assertTrue(exitedCopy.contains("row-")); assertFalse(exitedCopy.contains('\u0000'))
                assertEquals(exitedWrites, f.nativeWrites.get()); assertEquals(exitedCloses, f.closes.get())
                assertEquals(0, f.closes.get())
            }
        }
    }

    @Test fun unknownUnavailableAndViewCloseHaveHonestActions() = runBlocking<Unit> {
        RealPtyPageFixture.use { f ->
            runSkikoComposeUiTest(size = Size(1440f, 900f)) {
                setContent { HarnessApp(f.connection, terminalLaunchProvider = f.desktopProvider, themeMode = HarnessThemeMode.Light) }
                openTerminal(f); click("pty-terminal-create"); awaitTag("pty-terminal-grid")
                command(f, f.pidPrefix() + "printf 'BEFORE_UNKNOWN\\r\\n'")
                waitUntil(timeoutMillis = 30_000) { f.recordOwned(); f.ownedProcesses().isNotEmpty() }
                awaitOutput(f, "BEFORE_UNKNOWN")
                val writesBeforeUnknown = f.nativeWrites.get()
                f.afterInput = { key, _ -> im.hikaru.harness.session.api.TerminalMutationResult.UnknownOutcome(key, "Controlled acknowledgement loss") }
                click("pty-terminal-grid"); onNodeWithTag("pty-terminal-input", true).performTextInput("once")
                awaitTag("pty-input-unknown"); openActions(); onNodeWithTag("pty-terminal-paste", true).assertIsNotEnabled()
                onNodeWithTag("pty-terminal-close", true).assertIsEnabled()
                closeActions()
                capture(f, 1440, 900, HarnessThemeMode.Light, "Unknown", controlled = true)
                click("terminal-view-background"); click("terminal-view-interactive"); awaitTag("pty-input-unknown")
                action("pty-terminal-refresh"); awaitTag("pty-input-unknown"); assertEquals(writesBeforeUnknown + 1, f.nativeWrites.get())
                assertEquals(0, f.closes.get()); action("pty-terminal-close"); waitUntil(timeoutMillis = 30_000) { f.closes.get() == 1 }
                openActions()
                onNodeWithTag("pty-terminal-close", true).assertIsNotEnabled()
                closeActions()
            }
        }
        RealPtyPageFixture.use { f ->
            f.unavailable = true
            runSkikoComposeUiTest(size = Size(600f, 800f)) {
                setContent { HarnessApp(f.connection, terminalLaunchProvider = f.desktopProvider, themeMode = HarnessThemeMode.Light) }
                openTerminal(f); awaitTag("pty-discovery-unavailable")
                onNodeWithTag("pty-terminal-create", true).assertIsNotEnabled()
                openActions()
                onNodeWithTag("pty-terminal-refresh", true).assertIsEnabled()
                assertFalse(exists("pty-terminal-grid")); assertEquals(0, f.creates.get())
                closeActions()
                capture(f, 600, 800, HarnessThemeMode.Light, "Unavailable", controlled = true)
            }
        }
    }

    @Test fun ownerSwitchHidesOldTerminalAndPreservesOtherPanels() = runBlocking<Unit> {
        RealPtyPageFixture.use { f ->
            val other = f.runtime.context.agents.create(AgentId("other-page-owner"),
                im.hikaru.harness.agent.AgentOptions(cwd = f.cwd.toString()))
            val otherId = SessionId(other.agent.session.id.value)
            runSkikoComposeUiTest(size = Size(1440f, 900f)) {
                setContent { HarnessApp(f.connection, terminalLaunchProvider = f.desktopProvider) }
                openTerminal(f); click("pty-terminal-create"); awaitTag("pty-terminal-grid")
                command(f, f.pidPrefix() + "printf 'OLD_OWNER\\r\\n'"); awaitOutput(f, "OLD_OWNER")
                onNodeWithText("子 Agent").performMouseInput { click() }; awaitTag("tab:Subagents")
                click("tab:BackgroundOutput"); awaitGrid("OLD_OWNER")
                click("terminal-view-background"); assertFalse(exists("pty-terminal-grid"))
                click("terminal-view-interactive"); awaitGrid("OLD_OWNER")
                openActions()
                click("recent-${otherId.value}"); waitForIdle()
                onNodeWithTag("pty-terminal-action-menu", true).assertDoesNotExist()
                // Focusable popups may consume the outside click; the next real click changes owner.
                if (exists("pty-terminal-grid")) click("recent-${otherId.value}")
                assertFalse(exists("pty-terminal-grid")); assertEquals(0, f.closes.get())
                onNodeWithText("终端").performMouseInput { click() }; awaitTag("terminal-view-background")
                click("terminal-view-interactive"); awaitTag("pty-inventory-empty")
                onNodeWithTag("pty-terminal-action-menu", true).assertDoesNotExist()
                assertFalse(exists("pty-terminal-grid")); assertEquals(1, f.creates.get())
                waitUntil(timeoutMillis = 30_000) { f.recordOwned(); f.ownedProcesses().isNotEmpty() }; assertTrue(f.ownedProcesses().single().isAlive)
                onNodeWithText("子 Agent").performMouseInput { click() }; awaitTag("tab:Subagents")
                onNodeWithTag("live-composer-editor", true).assertExists()
            }
        }
    }

    @Test fun terminalActionMenuUsesRealPointerKeyboardAndScope() = runBlocking<Unit> {
        for ((width, height, theme) in listOf(
                Triple(1440, 900, HarnessThemeMode.Light), Triple(600, 800, HarnessThemeMode.Dark))) {
            RealPtyPageFixture.use { f ->
                runSkikoComposeUiTest(size = Size(width.toFloat(), height.toFloat())) {
                    val clipboard = PageClipboard()
                    setContent { CompositionLocalProvider(LocalClipboardManager provides clipboard) {
                        HarnessApp(f.connection, terminalLaunchProvider = f.desktopProvider, themeMode = theme)
                    } }
                    openTerminal(f); awaitTag("pty-inventory-empty")
                    assertFalse(exists("pty-terminal-select")); assertTrue(f.keys.isEmpty())
                    openActions()
                    onNodeWithTag("pty-terminal-refresh", true).assertIsEnabled().assertIsFocused()
                    for (tag in listOf("pty-terminal-close", "pty-terminal-copy", "pty-terminal-paste", "pty-terminal-bottom")) {
                        onNodeWithTag(tag, true).assertIsNotEnabled()
                    }
                    closeActions()
                    assertEquals(0, f.creates.get()); assertEquals(0, f.closes.get())
                    assertEquals(0, f.nativeWrites.get())

                    openActions()
                    // Real keyboard navigation skips all four disabled items and activates refresh.
                    onNodeWithTag("pty-terminal-refresh", true).performKeyInput { pressKey(Key.DirectionDown) }
                    onNodeWithTag("pty-terminal-refresh", true).assertIsFocused()
                        .performKeyInput { pressKey(Key.MoveHome); pressKey(Key.Enter) }
                    waitUntil(timeoutMillis = 30_000) { !exists("pty-terminal-action-menu") }
                    awaitTag("pty-inventory-empty")
                    assertEquals(0, f.creates.get()); assertEquals(0, f.closes.get())
                    assertTrue(f.keys.isEmpty()); assertEquals(0, f.nativeWrites.get())

                    click("pty-terminal-create"); awaitTag("pty-terminal-grid")
                    command(f, f.captureScript(listOf("\u001b[200~菜单😀\u001b[201~")))
                    awaitGrid("READY_0")
                    waitUntil(timeoutMillis = 30_000) { Files.exists(f.cwd.resolve("bytes-0.bin")) }
                    val key = f.keys.single()
                    assertEquals(f.session, key.owner.session)
                    openActions()
                    onNodeWithTag("pty-terminal-refresh", true).assertIsEnabled()
                    onNodeWithTag("pty-terminal-close", true).assertIsEnabled()
                    onNodeWithTag("pty-terminal-paste", true).assertIsEnabled()
                    onNodeWithTag("pty-terminal-copy", true).assertIsNotEnabled()
                    onNodeWithTag("pty-terminal-bottom", true).assertIsNotEnabled()
                    capture(f, width, height, theme, "Running", controlled = false, menu = true)
                    closeActions()

                    val writesBeforePaste = f.nativeWrites.get()
                    clipboard.setText(AnnotatedString("菜单😀"))
                    action("pty-terminal-paste")
                    runBlocking<Unit> { f.expectBytes(0, "\u001b[200~菜单😀\u001b[201~") }
                    awaitOutput(f, "DRAIN")
                    assertEquals(1, f.inputs.count { it == TerminalInput.Paste("菜单😀") })
                    assertEquals(writesBeforePaste + 1, f.nativeWrites.get())
                    assertEquals(1, f.creates.get()); assertEquals(0, f.closes.get())

                    // The real view switch advances selectionEpoch even when the full key stays the same.
                    val inputsBeforeSwitch = f.inputs.toList()
                    val writesBeforeSwitch = f.nativeWrites.get()
                    openActions(); click("terminal-view-background")
                    onNodeWithTag("pty-terminal-action-menu", true).assertDoesNotExist()
                    if (exists("pty-terminal-grid")) click("terminal-view-background")
                    assertFalse(exists("pty-terminal-grid"))
                    click("terminal-view-interactive"); awaitGrid("DRAIN")
                    onNodeWithTag("pty-terminal-action-menu", true).assertDoesNotExist()
                    assertEquals(key, runBlocking { f.snapshot(key) }.metadata.key)
                    assertEquals(inputsBeforeSwitch, f.inputs.toList())
                    assertEquals(writesBeforeSwitch, f.nativeWrites.get())
                    assertEquals(1, f.creates.get()); assertEquals(0, f.closes.get())
                    assertEquals(0L, Files.size(f.cwd.resolve("extra.bin")))
                    action("pty-terminal-close")
                    waitUntil(timeoutMillis = 30_000) { f.closes.get() == 1 }
                    onNodeWithTag("pty-terminal-action-menu", true).assertDoesNotExist()
                    assertEquals(1, f.creates.get()); assertEquals(1, f.closes.get())
                    assertEquals(writesBeforeSwitch, f.nativeWrites.get())
                }
            }
        }
    }

    private fun ComposeUiTest.exists(tag: String) = onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty()
    private fun ComposeUiTest.awaitTag(tag: String) { waitUntil(timeoutMillis = 30_000) { exists(tag) }; onNodeWithTag(tag, true).assertIsDisplayed() }
    private fun ComposeUiTest.click(tag: String) { awaitTag(tag); onNodeWithTag(tag, true).assertIsEnabled().performMouseInput { click() }; waitForIdle() }
    private fun ComposeUiTest.pointerClick(tag: String) {
        val target = onNodeWithTag(tag, true).assertIsDisplayed().assertIsEnabled()
        val node = target.fetchSemanticsNode()
        val rootNode = generateSequence(node) { it.parent }.last()
        val root = onNode(SemanticsMatcher("Pointer root ${rootNode.id}") { it.id == rootNode.id }, true)
        root.performMouseInput { moveTo(node.boundsInRoot.center - rootNode.boundsInRoot.topLeft) }
        mainClock.advanceTimeByFrame(); waitForIdle()
        val point = target.fetchSemanticsNode().boundsInRoot.center - root.fetchSemanticsNode().boundsInRoot.topLeft
        root.performMouseInput { moveTo(point); advanceEventTime(16); click(point) }
        mainClock.advanceTimeByFrame(); waitForIdle()
    }
    private fun ComposeUiTest.openActions() {
        if (!exists("pty-terminal-action-menu")) pointerClick("pty-terminal-actions")
        awaitTag("pty-terminal-action-menu"); waitForIdle()
    }
    private fun ComposeUiTest.action(tag: String) {
        openActions(); pointerClick(tag)
        waitUntil(timeoutMillis = 30_000) { !exists("pty-terminal-action-menu") }; waitForIdle()
    }
    private fun ComposeUiTest.closeActions() {
        if (exists("pty-terminal-action-menu")) {
            onNodeWithTag("pty-terminal-action-menu", true).performKeyInput { pressKey(Key.Escape) }
            waitUntil(timeoutMillis = 30_000) { !exists("pty-terminal-action-menu") }; waitForIdle()
        }
    }
    private fun ComposeUiTest.openTerminal(f: RealPtyPageFixture) {
        click("recent-${f.session.value}")
        awaitTag("live-thread"); onNodeWithText("终端").performMouseInput { click() }
        awaitTag("terminal-view-background"); assertFalse(exists("pty-terminal-grid")); click("terminal-view-interactive")
        awaitTag("pty-terminal-panel")
    }
    private fun ComposeUiTest.awaitGrid(text: String) {
        waitUntil(timeoutMillis = 30_000) { exists("pty-terminal-grid") &&
            onNodeWithTag("pty-terminal-grid", true).fetchSemanticsNode().config[SemanticsProperties.Text].any { value ->
                value.text.lineSequence().any { it.trimEnd() == text }
            } }
    }
    private fun ComposeUiTest.awaitOutput(f: RealPtyPageFixture, marker: String, lifecycle: TerminalLifecycle? = null) {
        awaitGrid(marker)
        waitUntil(timeoutMillis = 30_000) {
            val snapshot = runBlocking { f.snapshot(f.keys.single()) }
            snapshot.screen.any { it.text.trimEnd() == marker } &&
                (lifecycle == null || snapshot.metadata.lifecycle == lifecycle)
        }
    }
    private fun ComposeUiTest.command(f: RealPtyPageFixture, text: String) {
        // PS1 is independent of script contents; wait for the real interactive shell before input.
        awaitOutput(f, f.shellPrompt, TerminalLifecycle.Running)
        val script = Files.createTempFile(f.cwd, "page-command-", ".sh")
        Files.writeString(script, text + "\n", UTF_8)
        val invocation = ". " + f.quote(script.toAbsolutePath().toString())
        assertTrue(invocation.all { it.code < 128 }, "Readline receives only an ASCII source-file invocation")
        // Both artifacts live under cwd and are removed by the fixture's existing shutdown.
        Files.writeString(script.resolveSibling("${script.fileName}.invocation.txt"), invocation + "\n", UTF_8)
        val inputsBefore = f.inputs.size
        click("pty-terminal-grid"); onNodeWithTag("pty-terminal-input", true).assertIsFocused()
        onNodeWithTag("pty-terminal-input", true).performTextInput(invocation)
        onNodeWithTag("pty-terminal-input", true).performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        waitUntil(timeoutMillis = 30_000) { f.inputs.size >= inputsBefore + 2 }
        assertEquals(listOf(TerminalInput.Text(invocation), TerminalInput.KeyPress(TerminalKeyName.Enter)),
            f.inputs.drop(inputsBefore).take(2))
    }
    @OptIn(androidx.compose.ui.InternalComposeUiApi::class)
    private fun ComposeUiTest.typeAsciiCharacter(character: Char, key: Key) {
        val skiko = this as? SkikoComposeUiTest ?: error("Desktop character event requires Skiko scene")
        onNodeWithTag("pty-terminal-input", true).performKeyInput { keyDown(key) }
        runOnUiThread {
            val native = AwtKeyEvent(object : Component() {}, AwtKeyEvent.KEY_TYPED, System.currentTimeMillis(), 0, AwtKeyEvent.VK_UNDEFINED, character)
            skiko.scene.sendKeyEvent(KeyEvent(key, KeyEventType.Unknown, codePoint = character.code, nativeEvent = native))
        }
        onNodeWithTag("pty-terminal-input", true).performKeyInput { keyUp(key) }; waitForIdle()
    }
    private fun ComposeUiTest.capture(
        f: RealPtyPageFixture, width: Int, height: Int, theme: HarnessThemeMode, state: String, controlled: Boolean,
        menu: Boolean = false,
    ) {
        waitForIdle()
        var renderedRevision: Long? = null
        var renderedDescription: String? = null
        val snapshot = if (state == "Unavailable") {
            assertTrue(f.keys.isEmpty()); assertFalse(exists("pty-terminal-grid")); null
        } else {
            var captured: TerminalSnapshot? = null
            waitUntil(timeoutMillis = 30_000) {
                if (!exists("pty-terminal-grid")) false else {
                    val native = runBlocking { f.snapshot(f.keys.single()) }
                    val description = onNodeWithTag("pty-terminal-grid", true).fetchSemanticsNode()
                        .config[SemanticsProperties.StateDescription]
                    val revision = Regex("revision ([0-9]+);").find(description)?.groupValues?.get(1)?.toLong()
                    // Controlled acknowledgement loss fences a retained screen; record both revisions honestly.
                    val synchronized = if (state == "Unknown") revision != null && revision <= native.revision else
                        description == "${native.size.columns} columns ${native.size.rows} rows; revision ${native.revision}; ${native.metadata.lifecycle}"
                    if (synchronized) { captured = native; renderedRevision = revision; renderedDescription = description }
                    synchronized
                }
            }
            assertNotNull(captured)
        }
        when (state) {
            "Running" -> {
                assertEquals(TerminalLifecycle.Running, assertNotNull(snapshot).metadata.lifecycle)
                onNodeWithTag("pty-terminal-select").assertTextEquals("终端 1 · 运行中")
            }
            "Exited" -> {
                assertIs<TerminalLifecycle.Exited>(assertNotNull(snapshot).metadata.lifecycle)
                assertEquals(TerminalLifecycle.Exited(7), assertNotNull(snapshot).metadata.lifecycle)
                onNodeWithTag("pty-terminal-select").assertTextEquals("终端 1 · 已退出 (7)")
            }
            "Unknown" -> { assertTrue(controlled); awaitTag("pty-input-unknown"); assertNotNull(snapshot) }
            "Unavailable" -> { assertTrue(controlled); assertNull(snapshot) }
            else -> error("Unexpected capture state: $state")
        }
        snapshot?.let { assertEquals(f.keys.single(), it.metadata.key); assertEquals(f.session, it.metadata.key.owner.session) }
        onNodeWithTag("pty-terminal-create", true).assertIsDisplayed()
        onNodeWithTag("pty-terminal-actions", true).assertIsDisplayed()
        if (snapshot != null) onNodeWithTag("pty-terminal-select", true).assertIsDisplayed()
        if (menu) {
            onNodeWithTag("pty-terminal-action-menu", true).assertIsDisplayed()
            onNodeWithTag("pty-terminal-copy", true).assertIsNotEnabled()
            onNodeWithTag("pty-terminal-bottom", true).assertIsNotEnabled()
        } else onNodeWithTag("pty-terminal-action-menu", true).assertDoesNotExist()
        val slug = if (theme == HarnessThemeMode.Dark) "dark" else "light"
        val directory = File("/tmp/harness-terminal-renderer-page-captures-20261010").also { check(it.isDirectory || it.mkdirs()) }
        val prefix = "${width}x$height-$slug-$state" + if (menu) "-menu" else ""
        // A popup has a separate semantics root. Capture the whole scene, including every layer.
        val skiko = this as? SkikoComposeUiTest ?: error("Full page capture requires Skiko scene")
        val bitmap = skiko.captureToImage(); assertEquals(width, bitmap.width); assertEquals(height, bitmap.height)
        val backgroundRect = onNodeWithTag(if (snapshot == null) "pty-terminal-panel" else "pty-terminal-grid", true)
            .fetchSemanticsNode().boundsInWindow
        val sampleX = (if (snapshot == null) backgroundRect.center.x else backgroundRect.right - 2).toInt()
        val sampleY = (backgroundRect.bottom - 8).toInt()
        assertTrue(sampleX in 0 until width && sampleY in 0 until height)
        val expectedBackground = (if (theme == HarnessThemeMode.Dark) HarnessDarkColors else HarnessLightColors).Surface.toArgb()
        val actualBackground = bitmap.toPixelMap()[sampleX, sampleY].toArgb()
        assertEquals(expectedBackground, actualBackground, "The terminal body uses the requested theme Surface")
        Image.makeFromBitmap(bitmap.asSkiaBitmap()).use { image ->
            requireNotNull(image.encodeToData(EncodedImageFormat.PNG)).use { data -> File(directory, "$prefix.png").writeBytes(data.bytes) }
        }
        assertTrue(File(directory, "$prefix.png").length() > 0)
        File(directory, "$prefix.geometry.txt").writeText(buildString {
            appendLine("viewport=${width}x$height theme=$slug state=$state controlled=$controlled menu=$menu coordinates=window-local")
            val viewport = Rect(0f, 0f, width.toFloat(), height.toFloat())
            val menuTags = if (menu) listOf("pty-terminal-action-menu", "pty-terminal-refresh", "pty-terminal-close",
                "pty-terminal-copy", "pty-terminal-paste", "pty-terminal-bottom") else emptyList()
            for (tag in listOf("live-thread", "pty-terminal-panel", "pty-terminal-grid", "pty-terminal-input",
                    "pty-terminal-select", "pty-terminal-create", "pty-terminal-actions") + menuTags) {
                if (exists(tag)) {
                    val rect = onNodeWithTag(tag, true).fetchSemanticsNode().boundsInWindow
                    appendLine("$tag=$rect"); assertTrue(rect.width > 0 && rect.height > 0)
                    assertTrue(rect.left >= viewport.left - 1 && rect.top >= viewport.top - 1 &&
                        rect.right <= viewport.right + 1 && rect.bottom <= viewport.bottom + 1, "$tag is inside the full page viewport")
                }
            }
            appendLine("background_sample=($sampleX,$sampleY) expected=$expectedBackground actual=$actualBackground")
            appendLine("rendered=$renderedDescription")
        })
        File(directory, "$prefix.matrix.json").writeText(buildJsonObject {
            put("width", width); put("height", height); put("theme", slug); put("state", state); put("controlled", controlled)
            put("full_page", true); put("menu_open", menu)
            put("native_ime_verified", false); put("pixel_parity_verified", false)
            put("expectedHostId", f.host.hostId.value)
            put("ack_controlled", state == "Unknown")
            put("revision", snapshot?.let { JsonPrimitive(it.revision) } ?: JsonNull)
            put("rendered_revision", renderedRevision?.let { JsonPrimitive(it) } ?: JsonNull)
            put("rendered_description", renderedDescription?.let { JsonPrimitive(it) } ?: JsonNull)
            put("background_expected_argb", expectedBackground); put("background_actual_argb", actualBackground)
            put("lifecycle", snapshot?.let { JsonPrimitive(it.metadata.lifecycle.toString()) } ?: JsonNull)
            put("rows", snapshot?.let { JsonPrimitive(it.size.rows) } ?: JsonNull)
            put("columns", snapshot?.let { JsonPrimitive(it.size.columns) } ?: JsonNull)
            put("key", snapshot?.let { native -> buildJsonObject {
                put("scope", native.metadata.key.owner.scope.value)
                put("session", native.metadata.key.owner.session.value)
                put("incarnation", native.metadata.key.owner.incarnation.value)
                put("terminal", native.metadata.key.terminal.value)
            } } ?: JsonNull)
        }.toString())
    }
    private class PageClipboard : ClipboardManager {
        private var text: AnnotatedString? = null
        override fun getText(): AnnotatedString? = text
        override fun setText(annotatedString: AnnotatedString) { text = annotatedString }
        override fun hasText(): Boolean = !text?.text.isNullOrEmpty()
    }
}
