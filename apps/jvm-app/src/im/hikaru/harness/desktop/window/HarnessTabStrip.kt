package im.hikaru.harness.desktop.window

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import im.hikaru.harness.client.shell.HarnessWorkspaceTab
import im.hikaru.harness.client.shell.HarnessTabLayout
import im.hikaru.harness.client.shell.HarnessTabStripState
import im.hikaru.harness.ui.foundation.*

/** Pointer and main-region bounds use the same window coordinate system, in pixels. */
public class HarnessTabDragState {
    public var tabId: String? by mutableStateOf(null)
        private set
    public var pointer: Offset by mutableStateOf(Offset.Zero)
        private set
    /**
     * Window rect of the shell's main region, published by the split region that owns the drops. It is
     * observed, because the title bar lays its pane segments out on these columns: a tab strip must
     * start exactly where the pane's content starts — at the rail edge when the navigator is hidden or
     * floating, after the navigator when it is pushed.
     */
    public var regionBounds: Rect by mutableStateOf(Rect.Zero)
    public val active: Boolean get() = tabId != null
    public val targetRight: Boolean? get() = if (active && regionBounds.contains(pointer)) {
        pointer.x >= regionBounds.center.x
    } else null

    public fun start(id: String, position: Offset) { tabId = id; pointer = position }
    public fun move(position: Offset) { pointer = position }
    public fun finish(state: HarnessTabStripState) {
        val id = tabId
        val right = targetRight
        if (id != null && right != null) state.moveToHalf(id, right)
        cancel()
    }
    public fun cancel() { tabId = null }
}

/**
 * The window's title bar. Measured on the live 44px strip: it is not one band but a row of segments
 * cut by 1px hairlines —
 *
 * ```
 * [ leading chrome | rule ][ pane 1 ][ rule ][ pane 2 ][ … ][ trailing cluster ]
 *    rail+navigator            tab strip    tab strip        布局 / 标签页 controls
 * ```
 *
 * The leading chrome is as wide as the App Rail plus the navigator column and carries the history
 * cluster (返回 / 前进), the navigator toggle and 新聊天, inset to the measured 88px from the window's
 * left edge so they clear the native traffic lights. Each pane segment is one of the shell's main-region
 * columns, so a tab strip starts exactly where its pane's content starts: at the rail edge while the
 * navigator is hidden or floating, after the navigator while it is pushed. The trailing cluster is
 * pinned to the right edge and holds the layout toggle plus the tabs toggle.
 *
 * [contentInset] is the one place the two coordinate systems meet: Jewel insets the title bar's content
 * by the macOS window controls, so the strip's own origin is not the window's. Every column the shell
 * publishes is in window coordinates (measured 340 for rail + navigator) and is shifted by
 * [contentInset] here, which is what makes a pane's segment start at the same x as the pane below it.
 *
 * The cluster keeps Codex's `min-w-max` width: while the navigator is hidden the column is only the rail
 * wide, so the cluster paints past it — 88..224px of a 52px column — and the hairline that closes the
 * column falls inside the first pane's segment (the measured 230px), with that pane's own content and
 * tabs starting 8px past it (the measured 238px header).
 *
 * [onToggleNavigator] is the page's navigator (sidebar) toggle, or null when the page on screen has no
 * navigator (`PageShellSpec.mountsNavigator`). [paneTitle] is the page's own title bar, drawn in place
 * of the tab strips while they are hidden (隐藏标签页).
 *
 * [backShortcut], [navigatorShortcut] and [newChatShortcut] are the chords the shell actually binds,
 * shown in the hover tips the way the live bar does. A null chord (no key adapter on this platform, or
 * a cleared binding) drops it.
 */
@Composable
public fun HarnessTabStrip(
    state: HarnessTabStripState,
    drag: HarnessTabDragState,
    modifier: Modifier = Modifier,
    backShortcut: String? = null,
    onToggleNavigator: (() -> Unit)? = null,
    navigatorShortcut: String? = null,
    onNewChat: (() -> Unit)? = null,
    newChatShortcut: String? = null,
    paneTitle: (@Composable () -> Unit)? = null,
    // The native traffic lights own the first ~82px of a macOS title bar; every other platform starts
    // its controls at the strip's own 8px inset. The value is in window coordinates.
    leadInset: Dp = HarnessTokens.Control.TitlebarLeadInset,
    // Where this strip's own origin sits in window coordinates: Jewel already insets the title bar's
    // content by the macOS window controls (measured ~88px), so every window-coordinate column the
    // shell hands us has to lose that offset before it is used as a strip-local one. 0 on the platforms
    // that have no such controls, which makes the strip and the shell below it share one origin.
    contentInset: Dp = 0.dp,
    clientRegion: Modifier.(String) -> Modifier = { this },
) {
    val density = LocalDensity.current
    // The columns come from the region the shell laid out. It is one frame behind on the very first
    // pass, so the leading chrome falls back to the rail and the pane segment fills.
    val region = drag.regionBounds
    val buttons = 2 + (if (onToggleNavigator != null) 1 else 0) + (if (onNewChat != null) 1 else 0)
    // Codex's start slot is `width: rail` with `min-w-max`: the panes keep the shell's own columns —
    // measured, the live split cuts the title bar at the seam (1452 of a 52..1452 viewport) — while the
    // cluster it holds paints past the rail rather than squeezing, which is why the measured slot is
    // 52px wide holding 136px of controls at the 88px inset.
    val cluster = leadInset + HarnessTokens.Control.TabStripControl * buttons +
        HarnessTokens.Control.TitlebarGap * buttons + HarnessTokens.Control.TitlebarGap
    // In strip coordinates: the cluster's own leading inset, and the leading column (rail + navigator)
    // the shell published, both shifted by the strip's window origin.
    val clusterInset = (leadInset - contentInset).coerceAtLeast(0.dp)
    val lead = (with(density) { region.left.toDp() }.coerceAtLeast(HarnessTokens.Control.RailWidth) - contentInset)
        .coerceAtLeast(0.dp)
    // Where the leading column ends: the rail plus the navigator column while the navigator is pushed
    // (measured 340px), the cluster's own trailing edge while it is hidden (measured 230px). The hairline
    // sits there and the pane's content clears it, which is the measured 238px header start.
    val leadingRule = (cluster - contentInset - lead).coerceAtLeast(0.dp)
    val panes = with(density) { region.width.toDp() }
    val seam = HarnessTokens.Control.SplitSeamWidth
    CompositionLocalProvider(LocalTabClientRegion provides clientRegion) {
        // The band is the app background, as measured on the live bar (`rgb(246,246,246)`, the same
        // F6F6F6 the shell paints around the page card): the strip is not the white page surface, so it
        // paints the background itself instead of letting whatever is composed under it show through.
        Row(
            modifier.fillMaxWidth().height(HarnessTokens.Control.Titlebar)
                .background(HarnessTokens.Colors.AppBackground)
                .testTag("harness-tab-strip"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LeadingChrome(lead, clusterInset, state, drag, backShortcut, onToggleNavigator, navigatorShortcut, onNewChat, newChatShortcut)
            val split = state.split
            if (split == null || panes <= seam) {
                // One pane: its segment takes everything from the rail to the trailing cluster, the way
                // the live main titlebar is the flex-1 segment.
                Box(Modifier.weight(1f)) {
                    PaneSegment(state, drag, Modifier.fillMaxSize(), paneTitle, leadingRule, main = true)
                }
            } else {
                // The left pane keeps the region's own share, so its strip is cut where the panes divide
                // below it; the right pane absorbs the leftover width.
                PaneSegment(
                    state, drag,
                    Modifier.width(((panes - seam) * state.splitFraction).coerceAtLeast(0.dp)),
                    paneTitle, leadingRule, main = true,
                )
                // The seam's own band carries the boundary rule, so the title bar cuts exactly where the
                // panes are divided below it.
                Box(Modifier.width(seam).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    TitlebarRule(HarnessTokens.Control.TitlebarRule, "harness-tab-pane-rule")
                }
                // Only the pane that starts at the rail sits under the leading column, so the second
                // pane's content starts at its own 8px inset, as measured.
                Box(Modifier.weight(1f)) {
                    PaneSegment(state, drag, Modifier.fillMaxSize(), paneTitle, null, main = false)
                }
            }
            TrailingCluster(state)
        }
    }
}

/**
 * The fixed chrome at the head of the strip: the history cluster, the navigator toggle and 新聊天,
 * laid out with 6px gaps and the measured 88px lead inset — [inset] once the strip's own window origin
 * has been taken off it, so the first control lands 88px from the window's left edge.
 */
@Composable
private fun LeadingChrome(
    width: Dp,
    inset: Dp,
    state: HarnessTabStripState,
    drag: HarnessTabDragState,
    backShortcut: String?,
    onToggleNavigator: (() -> Unit)?,
    navigatorShortcut: String?,
    onNewChat: (() -> Unit)?,
    newChatShortcut: String?,
) {
    Box(Modifier.width(width).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
        Row(
            // `min-w-max`: the cluster is measured unbounded, so a narrow leading column insets the
            // pane's own content rather than squeezing four 28px controls into less than they need.
            Modifier.wrapContentWidth(Alignment.Start, unbounded = true)
                .padding(start = inset, end = HarnessTokens.Control.TitlebarGap),
            horizontalArrangement = Arrangement.spacedBy(HarnessTokens.Control.TitlebarGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StripIconButton(HarnessIconName.ArrowLeft, "返回", "harness-tab-back", state::back,
                enabled = state.canGoBack, shortcut = backShortcut)
            StripIconButton(HarnessIconName.ArrowRight, "前进", "harness-tab-forward", state::forward,
                enabled = state.canGoForward)
            // The page mounts no navigator on some pages; the shell then draws none of its
            // affordances, this control included (`PageShellSpec.mountsNavigator`).
            if (onToggleNavigator != null) {
                StripIconButton(
                    HarnessIconName.SidebarToggle,
                    "显示/隐藏侧边栏",
                    "harness-tab-navigator",
                    { drag.cancel(); onToggleNavigator.invoke() },
                    shortcut = navigatorShortcut,
                )
            }
            // Codex's 新聊天 sits at the end of the same cluster, on the tab-row radius (12.5px).
            if (onNewChat != null) {
                StripIconButton(
                    HarnessIconName.NewChat,
                    "新聊天",
                    "harness-tab-new-chat",
                    onNewChat,
                    radius = HarnessTokens.Control.TabRowControlRadius,
                    shortcut = newChatShortcut,
                )
            }
        }
    }
}

/**
 * One pane's title-bar segment. Inset 8px from its start and 6px from its end, as measured; a pane
 * with the tabs hidden draws the page's own title bar instead of a strip.
 *
 * [leadingRule] is where the leading column's hairline falls inside this segment, or null for a pane
 * that does not start at the rail. The strip's content clears it, so the rule never crosses a tab.
 */
@Composable
private fun PaneSegment(
    state: HarnessTabStripState,
    drag: HarnessTabDragState,
    modifier: Modifier,
    paneTitle: (@Composable () -> Unit)?,
    leadingRule: Dp?,
    main: Boolean,
) {
    val insetStart = (leadingRule ?: 0.dp) + HarnessTokens.Control.TabStripInsetStart
    Box(
        modifier.fillMaxHeight().clipToBounds().semantics { testTag = if (main) "harness-tab-pane-main" else "harness-tab-pane-secondary" },
        contentAlignment = Alignment.CenterStart,
    ) {
        if (leadingRule != null) {
            Box(Modifier.align(Alignment.CenterStart).offset(x = leadingRule)) {
                TitlebarRule(HarnessTokens.Control.TitlebarRule, "harness-tab-lead-rule")
            }
        }
        if (state.tabsHidden) {
            // 隐藏标签页: the strip is replaced in place by the pane's page title, which the page
            // publishes. It keeps the toggle in the same slot, so the mode is one click away.
            Box(Modifier.fillMaxSize().padding(start = insetStart), contentAlignment = Alignment.CenterStart) {
                paneTitle?.invoke()
            }
        } else {
            TabRow(state, drag, insetStart, main)
        }
    }
}

/**
 * A pane's tab row: the tabs it holds, the plus, and — between two adjacent tabs — the measured 1px
 * 12px separator, which the live strip hides as soon as either neighbour is hovered or focused.
 */
@Composable
private fun TabRow(state: HarnessTabStripState, drag: HarnessTabDragState, insetStart: Dp, main: Boolean) {
    // A split gives each pane its own tab: the left pane's strip shows the left tab as selected, the
    // right pane's strip the right one.
    val split = state.split
    val selectedId = if (split == null) state.activeId else if (main) split.left else split.right
    var hoveredIndex by remember { mutableStateOf(-1) }
    Row(
        Modifier.fillMaxSize()
            .padding(start = insetStart, end = HarnessTokens.Control.TabStripInsetEnd)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(HarnessTokens.Control.TabChipGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        state.tabs.forEachIndexed { index, tab ->
            key(tab.id) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HarnessTabChip(
                        tab, tab.id == selectedId, state, drag,
                        onHoverChanged = { hovered -> hoveredIndex = if (hovered) index else if (hoveredIndex == index) -1 else hoveredIndex },
                    )
                    // The separator sits at the trailing edge of every tab but the last, and both of the
                    // tabs it divides dismiss it while either is hovered.
                    if (index < state.tabs.lastIndex) {
                        Box(
                            Modifier.width(1.dp).height(HarnessTokens.Control.TabSeparator)
                                .alpha(if (hoveredIndex == index || hoveredIndex == index + 1) 0f else 1f)
                                .background(HarnessTokens.Colors.ChromeRule)
                                .testTag("harness-tab-separator-${tab.id}"),
                        )
                    }
                }
            }
        }
        // The plus lives in the tab row, where Codex draws a 12.5px corner.
        StripIconButton(HarnessIconName.Plus, "新标签页", if (main) "harness-tab-add" else "harness-tab-add-secondary", state::addNewTab,
            radius = HarnessTokens.Control.TabRowControlRadius)
        Spacer(Modifier.width(HarnessTokens.Control.TabStripControl))
    }
}

/**
 * The trailing cluster, pinned to the right edge: the layout toggle (退出完整视图 / 进入完整视图) and the
 * tabs toggle. While the tabs are shown the second control hides them (隐藏标签页, drawn engaged — it is
 * the mode the live shell shows with its strip up); while they are hidden it becomes 新建标签页, which
 * brings the strips back.
 */
@Composable
private fun TrailingCluster(state: HarnessTabStripState) {
    Row(
        Modifier.fillMaxHeight().padding(end = HarnessTokens.Spacing.Two),
        horizontalArrangement = Arrangement.spacedBy(HarnessTokens.Control.TitlebarGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TitlebarRule(HarnessTokens.Control.TitlebarTrailingRule, "harness-tab-trailing-rule")
        StripIconButton(
            HarnessIconName.FullView,
            if (state.split == null) "退出完整视图" else "进入完整视图",
            "harness-tab-split",
            state::toggleSplit,
            enabled = state.tabs.size >= 2,
            radius = HarnessTokens.Control.TabRowControlRadius,
            // Measured: the layout control carries the 10% ink fill while one pane has the full width
            // and drops it as soon as the panes are split, so the fill reports the layout, not the click.
            engaged = state.split == null,
        )
        if (state.tabsHidden) {
            StripIconButton(
                HarnessIconName.Plus, "新建标签页", "harness-tab-new",
                state::addNewTab, radius = HarnessTokens.Control.TabRowControlRadius,
            )
        } else {
            StripIconButton(
                HarnessIconName.SplitView, "隐藏标签页", "harness-tab-hide",
                state::toggleTabsHidden, radius = HarnessTokens.Control.TabRowControlRadius, engaged = true,
            )
        }
    }
}

/** A 1px hairline in the title bar's ink, sized to the segment boundary it cuts. */
@Composable
private fun TitlebarRule(height: Dp, tag: String) {
    Box(
        Modifier.width(1.dp).height(height).background(HarnessTokens.Colors.ChromeRule).testTag(tag),
    )
}

private val LocalTabClientRegion = staticCompositionLocalOf<Modifier.(String) -> Modifier> { { this } }

/**
 * One tab slot. Measured on the live tab bar: a 238x32 slot holding an inset painter — a `#FFFFFF`
 * pill with a 1px `rgba(26,28,31,.08)` ring at corner 10 when the tab is selected, nothing at all
 * when it is not (an unselected tab is bare ink at 49% alpha). The close target is a 20x20 box at the
 * trailing edge, revealed by hover.
 */
@Composable
private fun HarnessTabChip(
    tab: HarnessWorkspaceTab,
    selected: Boolean,
    state: HarnessTabStripState,
    drag: HarnessTabDragState,
    onHoverChanged: (Boolean) -> Unit = {},
) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    // The row needs the hover to dismiss the separator next to this chip (measured on the live strip).
    LaunchedEffect(hovered) { onHoverChanged(hovered) }
    val clientRegion = LocalTabClientRegion.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    val shape = RoundedCornerShape(HarnessTokens.Control.TabChipRadius)
    Box(
        Modifier.width(HarnessTokens.Control.TabChip).height(HarnessTokens.Control.TabChipHeight)
            .onGloballyPositioned { origin = it.positionInWindow() }
            .hoverable(source)
            .clickable(role = Role.Tab) { state.select(tab.id) }
            .pointerInput(tab.id, state, drag) {
                detectDragGestures(
                    onDragStart = { drag.start(tab.id, origin + it) },
                    onDragCancel = drag::cancel,
                    onDragEnd = { drag.finish(state) },
                    onDrag = { change, _ -> change.consume(); drag.move(origin + change.position) },
                )
            }
            .clientRegion("tab-${tab.id}")
            .semantics { testTag = "harness-tab-${tab.id}"; this.selected = selected },
    ) {
        // The pill painter is a separate layer under the content, so the hover fill and the selected
        // pill are drawn in one place and the text never shifts. The measured pill is exactly
        // `#FFFFFF` in the light capture, which is the light theme's elevated surface; dark mode
        // elevates to its own surface rather than carrying the light value across.
        Box(
            Modifier.matchParentSize().clip(shape)
                .background(if (selected) HarnessTokens.Colors.SurfaceElevated else Color.Transparent)
                .then(if (selected) Modifier.border(1.dp, HarnessTokens.Colors.SurfaceRingStrong, shape) else Modifier),
        )
        if (!selected && hovered) {
            Box(Modifier.matchParentSize().clip(shape).background(HarnessTokens.Colors.ChromeFill))
        }
        Row(
            Modifier.matchParentSize().padding(start = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HarnessText(
                tab.title,
                modifier = Modifier.weight(1f),
                color = if (selected) HarnessTokens.Colors.Text else HarnessTokens.Colors.ChromeIcon,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Codex reveals the close target on the selected tab and on hover; it is absent at rest.
            if (selected || hovered) {
                TabCloseButton(tab, state)
            } else {
                Spacer(Modifier.width(HarnessTokens.Control.TabClose))
            }
        }
    }
}

@Composable
private fun TabCloseButton(tab: HarnessWorkspaceTab, state: HarnessTabStripState) {
    // 20x20 at corner 10. Codex draws a 14px cross in that box; the shared Close artwork covers the
    // same share of its 16dp box, so the rendered cross lands at the measured size.
    StripIconButton(
        name = HarnessIconName.Close,
        description = "关闭 ${tab.title} 标签页",
        tag = "harness-tab-close-${tab.id}",
        onClick = { state.close(tab.id) },
        size = HarnessTokens.Control.TabClose,
        radius = HarnessTokens.Control.TabChipRadius,
        iconSize = 16.dp,
        showTip = false,
    )
}

/**
 * Title-bar control. Measured off the live tab bar: a 28x28 box with a 16px glyph, ink at
 * `rgba(26,28,31,.494)`, a 10% ink fill on hover or while toggled on (with the ink lifted to full
 * strength), and `opacity: .4` — never a greyed colour — when it is unavailable. The hover tip is the
 * inverted dark pill the rail already uses, dropped just under the 44px strip.
 */
@Composable
private fun StripIconButton(
    name: HarnessIconName,
    description: String,
    tag: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    size: Dp = HarnessTokens.Control.TabStripControl,
    radius: Dp = HarnessTokens.Control.TitlebarControlRadius,
    iconSize: Dp = 16.dp,
    shortcut: String? = null,
    // A toggled-on control keeps the 10% ink fill and full-strength ink without being hovered, which
    // is how the live 退出完整视图 control renders while the split view is up.
    engaged: Boolean = false,
    // No measured tip exists for the tab close target, so it is left tip-free rather than given
    // wording Codex does not show.
    showTip: Boolean = true,
) {
    val clientRegion = LocalTabClientRegion.current
    HarnessTooltipTrigger(
        text = if (shortcut.isNullOrBlank()) description else "$description $shortcut",
        enabled = enabled && showTip,
        side = HarnessPopoverSide.Bottom,
        popupTestTag = "harness-tab-tip-$tag",
        railPill = true,
        delayMillis = 350,
    ) { tooltipModifier ->
        val source = remember { MutableInteractionSource() }
        val hovered by source.collectIsHoveredAsState()
        Box(
            // The tip modifier goes in before the clickable: it has no focus target of its own and
            // rides the one the clickable installs.
            Modifier.size(size).then(tooltipModifier)
                // A disabled control keeps its ink and drops to opacity .4, exactly as measured.
                .alpha(if (enabled) 1f else 0.4f)
                .clip(RoundedCornerShape(radius))
                .background(
                    when {
                        !enabled -> Color.Transparent
                        engaged || hovered -> HarnessTokens.Colors.ChromeFill
                        else -> Color.Transparent
                    },
                )
                .hoverable(source, enabled = enabled)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .clientRegion(tag).semantics { testTag = tag; contentDescription = description },
            contentAlignment = Alignment.Center,
        ) {
            HarnessIcon(
                name,
                tint = if (engaged) HarnessTokens.Colors.Text else HarnessTokens.Colors.ChromeIcon,
                size = iconSize,
            )
        }
    }
}
