package im.hikaru.harness.client.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/**
 * The shell's main content region: the area right of the navigator and left of the side panels,
 * inside the page-surface card. The App Rail and the navigator are deliberately outside it.
 *
 * Measured on the live split view: the left panel is a single `0,44 340x1272` column — rail and
 * navigator together — spanning the full window height while two panes sit beside it, and the panes
 * are divided by a 16px `cursor-col-resize` separator (window x 1566 in a 2560px window, starting at
 * the tab strip's top edge). So a split duplicates the page surface, never the navigation chrome.
 */
public interface HarnessMainRegion {
    /**
     * Renders the region. [modifier] arrives with the parent's layout data (the shell's weight), and
     * [surface] composes the page surface once per pane — once while unsplit, twice while split.
     */
    @Composable
    public fun Panes(modifier: Modifier, surface: @Composable (Modifier) -> Unit)
}

/**
 * The window's main region, or null for the plain single-surface shell. A window that owns the
 * title-bar tabs provides its split region; anything else (mobile, previews) leaves it null.
 */
public val LocalHarnessMainRegion: ProvidableCompositionLocal<HarnessMainRegion?> =
    staticCompositionLocalOf { null }
