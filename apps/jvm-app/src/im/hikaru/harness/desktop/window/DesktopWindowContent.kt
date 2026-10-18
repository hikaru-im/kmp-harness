package im.hikaru.harness.desktop.window

import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.layout.layout

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import im.hikaru.harness.ui.foundation.HarnessTokens

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import im.hikaru.harness.client.shell.LocalHarnessTabStrip
import im.hikaru.harness.client.shell.rememberHarnessTabStripState
import im.hikaru.harness.client.app.HarnessTitleStripState
import im.hikaru.harness.client.app.LocalHarnessTitleStrip
import im.hikaru.harness.client.shell.LocalHarnessMainRegion
import im.hikaru.harness.client.settings.*
import im.hikaru.harness.ui.foundation.*
import kotlinx.coroutines.delay
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.intui.standalone.theme.darkThemeDefinition
import org.jetbrains.jewel.intui.standalone.theme.default
import org.jetbrains.jewel.intui.standalone.theme.lightThemeDefinition
import org.jetbrains.jewel.intui.window.decoratedWindow
import org.jetbrains.jewel.intui.window.styling.dark
import org.jetbrains.jewel.intui.window.styling.defaults
import org.jetbrains.jewel.intui.window.styling.light
import org.jetbrains.jewel.ui.ComponentStyling
import org.jetbrains.jewel.window.DecoratedWindow
import org.jetbrains.jewel.window.TitleBar
import org.jetbrains.jewel.window.utils.clientRegion
import org.jetbrains.skiko.SystemTheme
import org.jetbrains.skiko.currentSystemTheme
import org.jetbrains.jewel.window.styling.TitleBarColors
import org.jetbrains.jewel.window.styling.TitleBarMetrics
import org.jetbrains.jewel.window.styling.TitleBarStyle

/** Jewel owns window controls and dragging. Windows keeps the workspace dock below the title bar. */
@Composable
public fun DesktopHarnessWindow(
    width: Int,
    height: Int,
    themeMode: HarnessThemeMode = HarnessThemeMode.System,
    customization: HarnessThemeCustomization = LocalHarnessThemeCustomization.current,
    closeConfirmation: CloseConfirmationPolicy = CloseConfirmationPolicy.KeyboardOnly,
    shortcutsRepository: ShortcutsRepository? = null,
    onCloseRequest: () -> Unit,
    closeRequestRejected: Boolean = false,
    content: @Composable () -> Unit,
) {
    val closeController = remember { DesktopWindowCloseController() }
    LaunchedEffect(closeRequestRejected) { if (closeRequestRejected) closeController.retainWindow() }
    val shortcuts = shortcutsRepository ?: remember { ShortcutsRepository(platform = desktopShortcutPlatform()) }
    LaunchedEffect(shortcuts) { if (!shortcuts.state.value.loaded) shortcuts.load() }
    DesktopWindowShortcutBinding(shortcuts, closeController, closeConfirmation, onCloseRequest)
    val windowState = rememberWindowState(size = DpSize(width.dp, height.dp))
    val separateDock = desktopShortcutPlatform() == ShortcutPlatform.Windows
    // The current page publishes its title and actions here; the title bar below renders them.
    val titleStrip = remember { HarnessTitleStripState() }
    val tabState = rememberHarnessTabStripState()
    val tabDrag = remember { HarnessTabDragState() }
    // Each title-bar control's hover tip shows the chord the shell really binds, not a hard-coded one.
    val shortcutsState by shortcuts.state.collectAsState()
    fun boundChord(command: ShortcutCommand): String? = shortcutsState.settings
        .bindings(command, shortcutsState.platform).firstOrNull()?.label(shortcutsState.platform)
    val backShortcut = remember(shortcutsState.settings, shortcutsState.platform) { boundChord(ShortcutCommand.NavigateBack) }
    val navigatorShortcut = remember(shortcutsState.settings, shortcutsState.platform) { boundChord(ShortcutCommand.ToggleSidebar) }
    // Jewel's IntUi decorates the window itself: on macOS it drives JBR's WindowDecorations (custom
    // title bar height, traffic lights, dragging), so the app does not touch the native chrome and
    // does not need JBR's API directly. The app still requires JBR (module.yaml `jvm.jdk`).

    // 系统 follows the host appearance; the appearance is sampled here because Compose's desktop
    // runtime has no live source for it (see [rememberHostAppearanceDark]). Everything inside — the
    // Jewel window styling, the title bar and the shell below it — reads it through HarnessTheme.
    CompositionLocalProvider(LocalHarnessSystemDark provides rememberHostAppearanceDark()) {
        val useDark = when (themeMode) {
            HarnessThemeMode.System -> harnessSystemInDarkTheme()
            HarnessThemeMode.Light -> false
            HarnessThemeMode.Dark -> true
        }
        IntUiTheme(
            theme = if (useDark) JewelTheme.darkThemeDefinition() else JewelTheme.lightThemeDefinition(),
            styling = ComponentStyling.default().decoratedWindow(),
        ) {
            DecoratedWindow(
                onCloseRequest = {
                    closeController.request(closeConfirmation, WindowCloseOrigin.NativeControl, onCloseRequest)
                },
                state = windowState,
                title = "KMP Harness",
                resizable = true,
            ) {
                // The bar's own colours are Harness tokens, so the palette has to be up *around* it: without
                // this provider the style below reads the ambient default — the light palette — and paints a
                // light band under the traffic lights whatever mode the app is in. It is provided as a
                // composition local rather than with HarnessTheme, because the theme lays out a Surface and
                // Jewel recognises this as the window's title bar only while it stays a **direct** child of
                // the decorated window: a layout node in between drops the bar out of the native chrome and
                // the shell is then composed over the traffic lights.
                CompositionLocalProvider(LocalHarnessColors provides harnessColors(useDark, customization)) {
                    TitleBar(style = harnessTitleBarStyle(useDark)) { titleBarState ->
                        // Windows keeps navigation beside the drag region and window controls,
                        // while workspace tabs occupy a separate row below.
                        if (separateDock) {
                            Box(Modifier.align(Alignment.Start).height(HarnessTokens.Control.Titlebar)) {
                                HarnessTheme(themeMode = themeMode, customization = customization, drawBackground = false) {
                                    HarnessWindowNavigation(
                                        state = tabState,
                                        drag = tabDrag,
                                        backShortcut = backShortcut,
                                        onToggleNavigator = titleStrip.navigatorToggle,
                                        navigatorShortcut = navigatorShortcut,
                                        onNewChat = titleStrip.newChat,
                                        clientRegion = { clientRegion(it) },
                                    )
                                }
                            }
                        } else {
                            // Jewel adds native insets after measuring children. Reserve JBR's macOS inset
                            // in the measurement budget too, so the rightmost control stays inside the window.
                            // Measured on the live window: the traffic lights own the first ~88px of a macOS title
                            // bar, which is the same width the strip insets its own cluster by.
                            val nativeInset = if (desktopShortcutPlatform() == ShortcutPlatform.Mac && !titleBarState.isFullscreen)
                                HarnessTokens.Control.TitlebarLeadInset else 0.dp
                            Box(Modifier.align(Alignment.Start).layout { measurable, constraints ->
                                val child = measurable.measure(constraints.offset(horizontal = -nativeInset.roundToPx()))
                                layout(child.width, child.height) { child.placeRelative(0, 0) }
                            }.fillMaxWidth().height(HarnessTokens.Control.Titlebar)) {
                                // The strip's own content reads the theme's typography and material scheme; the
                                // bar *around* it cannot, since only a direct child is recognised by the window.
                                HarnessTheme(themeMode = themeMode, customization = customization, drawBackground = false) {
                                HarnessTabStrip(
                                    state = tabState,
                                    drag = tabDrag,
                                    backShortcut = backShortcut,
                                    onToggleNavigator = titleStrip.navigatorToggle,
                                    navigatorShortcut = navigatorShortcut,
                                    onNewChat = titleStrip.newChat,
                                    // The conversation's segment draws the page's own title bar, which the page
                                    // publishes; the side panel's segment draws the strip instead.
                                    paneTitle = titleStrip.content,
                                    leadInset = if (desktopShortcutPlatform() == ShortcutPlatform.Mac) {
                                        HarnessTokens.Control.TitlebarLeadInset
                                    } else {
                                        HarnessTokens.Spacing.Two
                                    },
                                    // Jewel already insets this content by the macOS window controls, so the strip
                                    // starts ~88px into the window and every window-coordinate column it lays out
                                    // has to lose that origin. Without it the whole title bar sits ~88px right of
                                    // the shell below it: the tab strips start past the navigator's right edge.
                                    contentInset = nativeInset,
                                    clientRegion = { clientRegion(it) },
                                )
                            // The strip is an opaque band above the shell, so the page card's drop shadow cannot
                            // spill into it. Repaint just that spill: a ghost of the card (same shape and corner
                            // radius) pushed below the strip, so only its shadow rises into the strip, and the
                            // band clips the ghost itself away. The strip's own colour is untouched above the band.
                            Box(
                                modifier =
                                    Modifier
                                        .align(Alignment.BottomCenter)
                                        .layout { measurable, constraints ->
                                            // Keep the original shadow in window coordinates, including the
                                            // part beneath the native control inset.
                                            val insetPx = nativeInset.roundToPx()
                                            val child = measurable.measure(constraints.offset(horizontal = insetPx))
                                            layout((child.width - insetPx).coerceAtLeast(0), child.height) {
                                                child.placeRelative(-insetPx, 0)
                                            }
                                        }
                                        .fillMaxWidth()
                                        .height(HarnessTokens.Control.PageSurfaceShadowBand)
                                        .clipToBounds(),
                            ) {
                                Box(
                                    modifier =
                                        Modifier
                                            .padding(
                                                start = HarnessTokens.Control.RailWidth,
                                                end = HarnessTokens.Control.PageSurfaceInset,
                                            )
                                            .fillMaxWidth()
                                            .height(HarnessTokens.Control.PageSurfaceShadowBand + HarnessTokens.Spacing.Four)
                                            .offset(y = HarnessTokens.Control.PageSurfaceShadowBand)
                                            .dropShadow(
                                                shape = RoundedCornerShape(HarnessTokens.Control.PageSurfaceRadius),
                                                shadow =
                                                    Shadow(
                                                        radius = 16.dp,
                                                        spread = 0.dp,
                                                        offset = DpOffset(x = 0.dp, y = 4.dp),
                                                        color = Color.Black.copy(alpha = 0.05f),
                                                    ),
                                            ),
                                )
                            }
                                }
                            }
                        }
                    }
                }
                CompositionLocalProvider(
                    LocalHarnessTitleStrip provides titleStrip,
                    // The tabs divide the shell's main region — right of the navigator, inside the page
                    // card — so the rail, the navigator and the side panels are composed exactly once.
                    LocalHarnessMainRegion provides rememberHarnessTabMainRegion(
                        tabState, tabDrag, paneHeaders = separateDock, titleStrip = titleStrip,
                    ),
                    // The same strip also reports the conversation's page, so the shell and the pane's own
                    // title bar agree on which page the conversation is showing.
                    LocalHarnessTabStrip provides tabState,
                ) {
                    DesktopWindowContent(
                        themeMode = themeMode,
                        customization = customization,
                        shortcutsRepository = shortcuts,
                    ) {
                        content()
                        DesktopWindowCloseDialog(closeController, onCloseRequest)
                    }
                }
            }
        }
    }
}

/** How often the host appearance is sampled — the same one-second interval Compose's own provider uses. */
private const val HostAppearancePollMillis = 1_000L

/**
 * The host's current appearance, sampled once a second so [HarnessThemeMode.System] follows a system
 * switch instead of the value the window happened to open with.
 *
 * Compose's desktop runtime keeps no live source for the system theme on 1.11: `isSystemInDarkTheme()`
 * reads `LocalSystemTheme`, which has no provider there, so the read is never invalidated — a window
 * opened dark stayed dark after macOS went light. skiko exposes the same value as a pull
 * (`currentSystemTheme`, public API, and what Compose 1.12's own `ProvideSystemTheme` polls once a
 * second), so the window samples it on the UI thread and publishes it through [LocalHarnessSystemDark].
 */
@Composable
private fun rememberHostAppearanceDark(): Boolean {
    var dark by remember { mutableStateOf(hostIsDark()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(HostAppearancePollMillis)
            val sampled = hostIsDark()
            if (sampled != dark) dark = sampled
        }
    }
    return dark
}

private fun hostIsDark(): Boolean = currentSystemTheme == SystemTheme.DARK

/**
 * The window title bar in Harness tokens: the same 44dp strip the shell used to draw, its colours
 * from the shared palette so the bar matches the surface under it. The colours come from
 * `HarnessTokens`, which resolves against the ambient palette, so a caller that is not itself inside a
 * [HarnessTheme] must provide `LocalHarnessColors` — see the call site in [DesktopHarnessWindow].
 */
@Composable
private fun harnessTitleBarStyle(useDark: Boolean): TitleBarStyle {
    val colors = HarnessTokens.Colors
    val titleBarColors = if (useDark) {
        TitleBarColors.dark(
            backgroundColor = colors.ShellBackground,
            inactiveBackground = colors.ShellBackground,
            contentColor = colors.Text,
            // No line under the strip: the reference draws none (agent-kmp hides it the same way).
            borderColor = colors.ShellBackground,
        )
    } else {
        TitleBarColors.light(
            backgroundColor = colors.ShellBackground,
            inactiveBackground = colors.ShellBackground,
            contentColor = colors.Text,
            // No line under the strip: the reference draws none (agent-kmp hides it the same way).
            borderColor = colors.ShellBackground,
        )
    }
    val metrics = TitleBarMetrics.defaults(
        height = HarnessTokens.Control.Titlebar,
        titlePaneButtonSize = DpSize(36.dp, 36.dp),
    )
    return if (useDark) {
        TitleBarStyle.dark(colors = titleBarColors, metrics = metrics)
    } else {
        TitleBarStyle.light(colors = titleBarColors, metrics = metrics)
    }
}

/**
 * Desktop window root for the fused title area.
 *
 * The window's title bar is Jewel's [TitleBar] (the strip the current page fills through
 * [LocalHarnessTitleStrip]); the shell below it starts at the page surface card, so no shell control
 * collides with the traffic lights.
 */
@Composable
public fun DesktopWindowContent(
    themeMode: HarnessThemeMode = HarnessThemeMode.System,
    customization: HarnessThemeCustomization = LocalHarnessThemeCustomization.current,
    onPreviewKeyEvent: (KeyEvent) -> Boolean = { false },
    onKeyEvent: (KeyEvent) -> Boolean = { false },
    shortcutsRepository: ShortcutsRepository? = null,
    content: @Composable () -> Unit,
) {
    val focus = remember { FocusRequester() }
    var hasFocus by remember { mutableStateOf(false) }
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(windowFocused, shortcutsRepository) {
        if (!windowFocused) shortcutsRepository?.input?.releaseAll()
    }
    LaunchedEffect(shortcutsRepository) { if (shortcutsRepository != null && !hasFocus) focus.requestFocus() }
    val keyboardModifier = if (shortcutsRepository != null) Modifier
        .focusRequester(focus).onFocusChanged {
            hasFocus = it.hasFocus
            if (!it.hasFocus) shortcutsRepository.input.releaseAll()
        }
        .onPreviewKeyEvent { event ->
            shortcutsRepository.input.preview(event.toShortcutInput()) || onPreviewKeyEvent(event)
        }
        .onKeyEvent { event -> onKeyEvent(event) || shortcutsRepository.input.dispatch(event.toShortcutInput()) }
        .focusable()
    else Modifier.onPreviewKeyEvent(onPreviewKeyEvent).onKeyEvent(onKeyEvent)
    CompositionLocalProvider(
        LocalShortcutsRepository provides shortcutsRepository,
        LocalShortcutsEventRoot provides shortcutsRepository?.input,
    ) {
    HarnessTheme(themeMode = themeMode, customization = customization) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(HarnessTokens.Colors.WorkspaceBackground)
                    .then(keyboardModifier),
        ) {
            // The window owns the title strip (Jewel's TitleBar, above this content), so the shell
            // fills the area below it.
            content()
        }
    }
    }
}

public fun desktopShortcutPlatform(): ShortcutPlatform {
    val os = System.getProperty("os.name", "")
    return when {
        os.startsWith("Mac", ignoreCase = true) -> ShortcutPlatform.Mac
        os.startsWith("Windows", ignoreCase = true) -> ShortcutPlatform.Windows
        os.startsWith("Linux", ignoreCase = true) -> ShortcutPlatform.Linux
        else -> ShortcutPlatform.Unavailable
    }
}
