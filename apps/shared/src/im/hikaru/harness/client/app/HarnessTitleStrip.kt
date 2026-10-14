package im.hikaru.harness.client.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Content the current page publishes into the window's title strip.
 *
 * The strip is owned by the window (the desktop window draws it as the macOS-fused title bar, above
 * the shell), while the page title and its actions are produced by [HarnessApp]. The app composes the
 * strip once and stores it here; the window renders it. The stored composable reads the app's own
 * state, so the strip recomposes when the page changes.
 */
public class HarnessTitleStripState {
    /**
     * The page's own title bar, drawn inside a pane's title-bar segment while the tab strips are
     * hidden (Codex 隐藏标签页). Codex swaps the strip for the pane's title and chat actions in place,
     * so this is composed once by the page and rendered once per pane that has no tab strip.
     */
    public var content: (@Composable () -> Unit)? by mutableStateOf(null)

    /**
     * The navigator (sidebar) toggle the title bar draws, or null when the page on screen has no
     * navigator to collapse. The page owns this because only [HarnessApp] knows which navigator the
     * current page carries — the same knowledge behind `pageShellSpec(...).navigator`. Codex draws
     * that control in its sidebar header as 隐藏侧边栏 (tip 「显示/隐藏侧边栏 ⇧⌘S」), not as a tab-bar
     * control, so the window renders a page-supplied action rather than its own.
     */
    public var navigatorToggle: (() -> Unit)? by mutableStateOf(null)

    /**
     * The 新聊天 action the leading chrome draws after the navigator toggle, or null when the page has
     * nothing to start a new chat on. Codex draws it at window x=190, at the end of the same cluster.
     */
    public var newChat: (() -> Unit)? by mutableStateOf(null)
}

/** Provided by the desktop window; null when no window owns a strip (previews, other targets). */
public val LocalHarnessTitleStrip: ProvidableCompositionLocal<HarnessTitleStripState?> =
    staticCompositionLocalOf { null }
