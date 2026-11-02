package im.hikaru.harness.client.shell

import kotlinx.serialization.Serializable

/**
 * Durable shell layout: the parts of the window the user arranges and expects to keep across
 * restarts. Page-owned state (search queries, selections, scroll offsets) stays out of this
 * document.
 *
 * Widths are stored in dp so the document stays readable and platform-independent; [sanitized]
 * clamps them to the navigator's real range before use.
 */
@Serializable
public data class ShellLayoutSettings(
    public val navigatorMode: SidebarMode = SidebarMode.Expanded,
    public val navigatorWidth: Float = NavigatorPreferredWidth.value,
    public val pinnedRailDestinations: Set<RailDestination> = emptySet(),
    /** Raw section key (without the `thread-summary-panel-section-expanded-` prefix) to expansion. */
    public val summaryExpandedSections: Map<String, Boolean> = emptyMap(),
) {
    /** A corrupt, stale or out-of-range document must never produce an unusable width. */
    public fun sanitized(): ShellLayoutSettings {
        val width = if (navigatorWidth.isFinite()) {
            navigatorWidth.coerceIn(NavigatorMinWidth.value, NavigatorMaxWidth.value)
        } else {
            NavigatorPreferredWidth.value
        }
        // Temporarily expanded is derived from the window width, not a user choice; a restart
        // re-derives it from Expanded instead of freezing the narrow-window state.
        val mode = if (navigatorMode == SidebarMode.TemporarilyExpanded) SidebarMode.Expanded else navigatorMode
        return if (width == navigatorWidth && mode == navigatorMode) this
        else copy(navigatorWidth = width, navigatorMode = mode)
    }
}
