package im.hikaru.harness.desktop.window

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import im.hikaru.harness.client.shell.HarnessPaneSide
import im.hikaru.harness.client.shell.PageId
import im.hikaru.harness.client.shell.SettingsSection
import im.hikaru.harness.client.shell.HarnessWorkspaceTab
import im.hikaru.harness.client.shell.LocalHarnessPaneTab
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
 *    rail+navigator        conversation    side panel       布局 / 标签页 controls
 * ```
 *
 * The leading chrome is as wide as the App Rail plus the navigator column and carries the history
 * cluster (返回 / 前进), the navigator toggle and 新聊天, inset to the measured 88px from the window's
 * left edge so they clear the native traffic lights. Each pane segment is one of the shell's main-region
 * columns, so a segment starts exactly where its pane's content starts: at the rail edge while the
 * navigator is hidden or floating, after the navigator while it is pushed. The trailing cluster is
 * pinned to the right edge.
 *
 * **Only the side panel's segment draws a tab strip.** The other one belongs to the *conversation*, which
 * is not a tab, and draws the conversation's own title instead — which is what the live shell does with
 * `聊天工具栏` on one side of the seam and `任务选项卡` on the other. The strip is never in both segments
 * at once, and the conversation is never a tab outside 完整视图.
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
 * navigator (`PageShellSpec.mountsNavigator`). [paneTitle] is the conversation's own title bar, drawn in
 * the segment that does not carry the strip.
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
    // Where this strip really starts in window coordinates. Jewel insets the title bar's content by its
    // native chrome, and that inset is not exactly the 88px the leading cluster is measured against — on
    // a 1467px window it lands at 84. Assuming the constant instead of measuring it put the segment, and
    // the hairline that closes the leading column, 4px left of the shell's own edges below, so the rule
    // stopped just short of the navigator boundary it is meant to continue.
    var measuredOrigin by remember { mutableStateOf<Dp?>(null) }
    val origin = measuredOrigin ?: contentInset
    val buttons = 2 + (if (onToggleNavigator != null) 1 else 0) + (if (onNewChat != null) 1 else 0)
    // Codex's start slot is `width: rail` with `min-w-max`: the panes keep the shell's own columns —
    // measured, the live split cuts the title bar at the seam (1452 of a 52..1452 viewport) — while the
    // cluster it holds paints past the rail rather than squeezing, which is why the measured slot is
    // 52px wide holding 136px of controls at the 88px inset.
    val cluster = leadInset + HarnessTokens.Control.TabStripControl * buttons +
        HarnessTokens.Control.TitlebarGap * buttons + HarnessTokens.Control.TitlebarGap
    // In strip coordinates: the cluster's own leading inset, and the leading column (rail + navigator)
    // the shell published, both shifted by the strip's window origin.
    val clusterInset = (leadInset - origin).coerceAtLeast(0.dp)
    val lead = (with(density) { region.left.toDp() }.coerceAtLeast(HarnessTokens.Control.RailWidth) - origin)
        .coerceAtLeast(0.dp)
    // Where the leading column ends: the rail plus the navigator column while the navigator is pushed
    // (measured 340px), the cluster's own trailing edge while it is hidden (measured 230px). The hairline
    // sits there and the pane's content clears it, which is the measured 238px header start.
    val leadingRule = (cluster - origin - lead).coerceAtLeast(0.dp)
    val panes = with(density) { region.width.toDp() }
    val seam = HarnessTokens.Control.SplitSeamWidth
    CompositionLocalProvider(LocalTabClientRegion provides clientRegion) {
        // The band is the app background, as measured on the live bar (`rgb(246,246,246)`, the same
        // F6F6F6 the shell paints around the page card): the strip is not the white page surface, so it
        // paints the background itself instead of letting whatever is composed under it show through.
        Row(
            modifier.fillMaxWidth().height(HarnessTokens.Control.Titlebar)
                // Read the strip's own origin rather than trusting the constant: the native inset the
                // window controls take is not fixed, and every column below is in window coordinates.
                .onGloballyPositioned { coordinates ->
                    val measured = with(density) { coordinates.positionInWindow().x.toDp() }
                    val current = measuredOrigin
                    if (current == null || kotlin.math.abs((current - measured).value) >= 0.5f) {
                        measuredOrigin = measured
                    }
                }
                .background(HarnessTokens.Colors.AppBackground)
                .testTag("harness-tab-strip"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LeadingChrome(lead, clusterInset, state, drag, backShortcut, onToggleNavigator, navigatorShortcut, onNewChat, newChatShortcut)
            // Off a chat page there is no side panel to divide with: measured, the live shell mounts none
            // on 资料库 / 定时任务 / 插件 / 代码审查, so the bar holds the conversation's segment alone.
            val panel = state.onChatPage
            val split = panel && state.layout == HarnessTabLayout.Split && !state.tabsHidden
            if (!split || panes <= seam) {
                // One pane. Closed, it is the conversation and draws the conversation's title; in
                // 完整视图 it is the side panel and the strip owns the whole bar.
                Box(Modifier.weight(1f)) {
                    PaneSegment(
                        state, drag, Modifier.fillMaxSize(), paneTitle, leadingRule,
                        tag = "harness-tab-pane-main",
                        panel = panel && !state.tabsHidden,
                    )
                }
            } else {
                // The two panes are the conversation and the side panel — never the same page twice. The
                // strip belongs to the side panel's segment, so it crosses with the panel when the halves
                // trade places. Measured on the live shell: with the panel open the left segment carried
                // the 聊天工具栏 with the conversation's name and the right one the 任务选项卡 tablist.
                val stripOnLeft = state.sidePanelSide == HarnessPaneSide.Left
                // The left pane keeps the region's own share, so its segment is cut where the panes
                // divide below it; the right pane absorbs the leftover width.
                PaneSegment(
                    state, drag,
                    Modifier.width(((panes - seam) * state.splitFraction).coerceAtLeast(0.dp)),
                    paneTitle, leadingRule, tag = "harness-tab-pane-main", panel = stripOnLeft,
                )
                // The seam's own band carries the boundary rule, so the title bar cuts exactly where the
                // panes are divided below it.
                Box(Modifier.width(seam).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    TitlebarRule(HarnessTokens.Control.TitlebarRule, "harness-tab-pane-rule")
                }
                // Only the pane that starts at the rail sits under the leading column, so the second
                // pane's content starts at its own 8px inset, as measured.
                Box(Modifier.weight(1f)) {
                    PaneSegment(
                        state, drag, Modifier.fillMaxSize(), paneTitle, null,
                        tag = "harness-tab-pane-secondary", panel = !stripOnLeft,
                    )
                }
            }
            TrailingCluster(state)
        }
    }
}

/** Navigation hosted separately in the Windows title bar; only buttons are client hit regions. */
@Composable
internal fun HarnessWindowNavigation(
    state: HarnessTabStripState,
    drag: HarnessTabDragState,
    backShortcut: String?,
    onToggleNavigator: (() -> Unit)?,
    navigatorShortcut: String?,
    onNewChat: (() -> Unit)?,
    clientRegion: Modifier.(String) -> Modifier,
) {
    val buttons = 2 + (if (onToggleNavigator != null) 1 else 0) + (if (onNewChat != null) 1 else 0)
    val inset = HarnessTokens.Spacing.Two
    val width = inset + (HarnessTokens.Control.TabStripControl + HarnessTokens.Control.TitlebarGap) * buttons
    CompositionLocalProvider(LocalTabClientRegion provides clientRegion) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LeadingChrome(width, inset, state, drag, backShortcut, onToggleNavigator, navigatorShortcut, onNewChat, null)
        }
    }
}

/** Hidden-panel actions stay at the client area's trailing edge, including existing tabs. */
@Composable
internal fun HarnessHiddenWorkspaceActions(state: HarnessTabStripState) {
    if (state.onChatPage && state.tabsHidden) {
        Box(Modifier.height(HarnessTokens.Control.Titlebar)) { TrailingCluster(state) }
    }
}

/** Client-area header owned by a pane, without native insets or sidebar placeholders. */
@Composable
internal fun HarnessPaneHeader(
    state: HarnessTabStripState,
    drag: HarnessTabDragState,
    panel: Boolean,
    title: (@Composable () -> Unit)?,
) {
    if (!panel && title == null) return
    Row(
        Modifier.fillMaxWidth().height(HarnessTokens.Control.Titlebar)
            .background(HarnessTokens.Colors.AppBackground).testTag("harness-pane-header"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PaneSegment(state, drag, Modifier.weight(1f), title, null,
            tag = if (panel) "harness-tool-header" else "harness-conversation-header", panel = panel)
        if (panel) TrailingCluster(state)
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
 * One pane's title-bar segment, inset 8px from its start and 6px from its end as measured.
 *
 * [panel] says whether this is the **side panel's** segment: that is the one that carries the tab strip,
 * because the strip manages the panel's tabs. The conversation's segment — and either segment while the
 * panel is closed — draws the pane's own title bar instead.
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
    tag: String,
    panel: Boolean,
) {
    // When the navigator is pushed the leading column already ends on the shell's own 1px border, and
    // this segment starts one pixel past it (measured 340 below, 341 here), so the rule slides one
    // pixel left to continue that border. With the navigator hidden the column closes on the cluster's
    // trailing edge inside the pane, where there is no shell edge underneath to meet.
    val insetStart = (leadingRule ?: 0.dp) + HarnessTokens.Control.TabStripInsetStart
    val onNavigatorEdge = leadingRule?.value == 0f
    val ruleStart = if (onNavigatorEdge) (-1).dp else leadingRule ?: 0.dp
    Box(
        modifier.fillMaxHeight().semantics { testTag = tag },
        contentAlignment = Alignment.CenterStart,
    ) {
        if (leadingRule != null) {
            Box(Modifier.align(Alignment.CenterStart).offset(x = ruleStart)) {
                TitlebarRule(HarnessTokens.Control.TitlebarRule, "harness-tab-lead-rule")
            }
        }
        Box(Modifier.fillMaxSize().clipToBounds(), contentAlignment = Alignment.CenterStart) {
            if (panel) {
                TabRow(state, drag, insetStart, fullView = state.layout == HarnessTabLayout.Full)
            } else {
                // The conversation's own title bar. `null` is deliberate: the pane is telling the shell it
                // is not a tab, so the shell titles it with the conversation's page, not with a tab's.
                Box(Modifier.fillMaxSize().padding(start = insetStart), contentAlignment = Alignment.CenterStart) {
                    CompositionLocalProvider(LocalHarnessPaneTab provides null) { paneTitle?.invoke() }
                }
            }
        }
    }
}

/**
 * The side panel's tab row: the tabs it holds, the plus, and — between two adjacent tabs — the measured
 * 1px 12px separator.
 *
 * [fullView] prepends the conversation as a `聊天` chip. Measured in 完整视图: the live strip lists
 * `聊天` `[349,6,231,32]` first, unselected, while the panel's own tab carries the selection.
 *
 * **The slot has a floor and a ceiling, and the strip scrolls below the floor.** Measured on the live
 * strip over a viewport sweep, every slot is `clamp(stripWidth / count, 90px, 240px)`:
 *
 * | viewport | slots | slot | what the strip does |
 * | --- | --- | --- | --- |
 * | 1090 (10 tabs) | 900 | **90** | floor — track is 900 wide inside a 259 box, `overflow-x: auto` |
 * | 1090 (10 tabs), 2560 | — | 109.3 | division: 1093 / 10 |
 * | 6000 | 2400 | **240** | ceiling — the strip stops growing at 2400 |
 *
 * So the plus reaches the row's right edge only once the slots have hit the 240px ceiling; with fewer
 * tabs it sits immediately after the last one (the live plus is a `flex: 0 0 auto` sibling with a 6px
 * leading pad and no growth of its own), and with none at all there is no strip to follow, so it stands
 * at the strip's own start. **The division has no lower floor to hide content at:** the live minimum of
 * 90px is above the 64px where `@container app-shell-tab not (width >= 4rem)` would put
 * `visibility: hidden` on a tab's content, so that rule never fires and the slots stop shrinking at 90
 * instead. Past that point the live strip does not divide further — it **scrolls** (`overflow-x: auto`
 * plus `hide-scrollbar`, `scrollWidth 900` in a `clientWidth 259` box), which is what the strip here
 * does too: the slots keep their 90px floor and the row scrolls horizontally.
 */
@Composable
private fun TabRow(state: HarnessTabStripState, drag: HarnessTabDragState, insetStart: Dp, fullView: Boolean) {
    // The row's own width decides the slots, so it has to be read before they are measured.
    BoxWithConstraints(
        Modifier.fillMaxSize().padding(start = insetStart, end = HarnessTokens.Control.TabStripInsetEnd),
    ) {
        val count = state.tabs.size
        // Measured: the row is `[strip][6px][28px plus]`, so the strip divides what is left for it.
        val stripMax = (maxWidth - HarnessTokens.Control.TabStripControl - HarnessTokens.Control.TabStripPlusGap)
            .coerceAtLeast(0.dp)
        // Measured `clamp(share, 90px, 240px)` over the **whole** row: [TabRow]'s live source divides
        // `availableTabWidth` by `tabs.length + (sharesTabWidth && !insideScroller ? 1 : 0)`, so the
        // leading 聊天 tab is one of the slots being divided even though it is drawn outside the
        // scroller. Below the floor nothing divides further — the scroller overflows in both layouts
        // (measured 90 in 分屏 at 1090 *and* 1580, and in 完整视图 at 1090 once past 11 slots).
        val slotCount = count + if (fullView) 1 else 0
        val slot = if (slotCount == 0) 0.dp else (stripMax / slotCount).coerceIn(TabSlotMin, TabSlotMax)
        // Measured: a slot's chip is the slot less the 2px its own wrapper reserves (`pe-0.5`). The
        // 完整视图 聊天 chip is the leading tab and gives back more — 231 of a 240 slot — because the
        // leading wrapper carries its own trailing margin on top of that reserve.
        val chipWidth = (slot - HarnessTokens.Control.TabChipGap).coerceAtLeast(0.dp)
        val leadingChipWidth = (slot - HarnessTokens.Control.LeadingTabChipGap).coerceAtLeast(0.dp)
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            if (count > 0 || fullView) {
                // Measured: the 聊天 chip is the strip's `leadingTab` — a `flex: 0 0 auto` sibling
                // **before** the scroller, not its first slot. Pulling the live `scrollLeft` from 0 to
                // 500 left the chip at x=349 while the tabs travelled from 590 to 190, so it must not
                // live inside the scroll container here either.
                if (fullView) {
                    key("conversation") {
                        // Measured: the chip is a selectable tab beside the panel's own, not a way out
                        // of 完整视图 — with it selected the layout controls still read 退出完整视图 /
                        // 进入分屏视图, and the panel's tab drops to unselected.
                        TabSlot(slot, separatorDimmed = state.fullViewShowsChat) {
                            ConversationChip(state, leadingChipWidth, selected = state.fullViewShowsChat)
                        }
                    }
                }
            }
            if (count > 0) {
                // The strip's *box* is capped by what the row has left (the live `flex: 0 1 auto` box,
                // measured 259px) while its *track* is the slots' full width (measured 900px) and
                // scrolls inside that box. The box must not grow past the row: if the scroller took the
                // track's width it would push the plus off the end, which is exactly what went wrong
                // before — the plus is only ever displaced by the strip being wider than its share.
                val scroll = rememberScrollState()
                // The leading chip takes its slot out of the row before the scroller starts, so the box
                // is what the row has left *after* that slot.
                val scrollerMax = (stripMax - if (fullView) slot else 0.dp).coerceAtLeast(0.dp)
                val trackWidth = minOf(slot * count, scrollerMax)
                // Measured: the live scroller keeps the **selected** tab in view. Its class carries
                // `scroll-px-1` (computed `scroll-padding: 2px auto`), and with the last tab selected it
                // sits at `scrollLeft: 641`, its own maximum (a 900px track in a 259px box). Without
                // this the tab the plus just added lands past the box's right edge, so the click reads
                // as having done nothing.
                val density = LocalDensity.current
                val slotPx = with(density) { slot.toPx() }
                val viewportPx = with(density) { trackWidth.toPx() }
                val padPx = with(density) { TAB_SCROLL_PAD.toPx() }
                val activeIndex = state.tabs.indexOfFirst { it.id == state.activeId }
                LaunchedEffect(activeIndex, slotPx, viewportPx) {
                    if (activeIndex < 0 || viewportPx <= 0f || slotPx <= 0f) return@LaunchedEffect
                    val start = activeIndex * slotPx
                    val end = start + slotPx
                    // Only a selection outside the box moves it: a visible one leaves the scroll alone,
                    // so selecting an already-visible tab never jumps the strip.
                    val target = when {
                        start - padPx < scroll.value -> start - padPx
                        end + padPx > scroll.value + viewportPx -> end + padPx - viewportPx
                        else -> return@LaunchedEffect
                    }
                    scroll.animateScrollTo(target.toInt().coerceIn(0, scroll.maxValue))
                }
                Row(
                    Modifier.width(trackWidth).horizontalScroll(scroll),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        Modifier.width(slot * count).height(HarnessTokens.Control.TabChipHeight),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        state.tabs.forEachIndexed { index, tab ->
                            key(tab.id) {
                                // The panel's tabs drop their selection while the full view is showing the
                                // conversation: measured, only one of them carries `aria-selected`.
                                val selected = tab.id == state.activeId && !state.fullViewShowsChat
                                TabSlot(
                                    slot,
                                    // Measured: a separator between two tabs goes away while **either**
                                    // of them is selected, and stays put on hover.
                                    separatorDimmed = selected || state.tabs.getOrNull(index + 1)?.id == state.activeId,
                                ) {
                                    HarnessTabChip(tab, selected, state, drag, width = chipWidth)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.width(HarnessTokens.Control.TabStripPlusGap))
            }
            // With no tabs there is no strip to follow, so the plus stands at the strip's own start —
            // the same place the panel's closed state draws it.
            // The plus lives in the tab row, where Codex draws a 12.5px corner.
            StripIconButton(HarnessIconName.Plus, "新标签页", "harness-tab-add", state::addNewTab,
                radius = HarnessTokens.Control.TabRowControlRadius)
        }
    }
}

/** The measured slot ceiling: 240px of slot, holding a 238px chip and its 2px reserved trailing pad. */
private val TabSlotMax = 240.dp

/** The measured slot floor: below this the live strip stops dividing and scrolls instead. */
private val TabSlotMin = HarnessTokens.Control.TabSlotFloor

/**
 * One slot of the strip: the chip, plus the 1px separator pinned to the slot's trailing edge. Measured on
 * the live strip the separator is drawn after **every** slot, the last one included, where it is merely
 * hidden — so a slot is always `slot` wide, and its chip is the slot less the 2px the slot's own wrapper
 * reserves (`pe-0.5`). The chip is drawn at the slot's start, so that reserve is the gap the separator
 * sits in; the separator itself lands flush at the slot's trailing edge.
 */
@Composable
private fun TabSlot(slot: Dp, separatorDimmed: Boolean, chip: @Composable () -> Unit) {
    Box(Modifier.width(slot)) {
        chip()
        Box(Modifier.align(Alignment.CenterEnd)) {
            TabSeparator(dimmed = separatorDimmed)
        }
    }
}

/**
 * The measured 1px 12px separator between two adjacent tabs. It is dismissed while **either of the tabs
 * it divides is selected** — measured by moving the selection along a ten-tab strip: selecting slot 3
 * hid separators 2 and 3, selecting slot 5 hid 4 and 5, and selecting the last hid 8 and its own
 * trailing one. Hover does **not** dismiss it (a hovered slot kept its own separator and its
 * neighbour's), which corrects the earlier reading of this rule.
 */
@Composable
private fun TabSeparator(dimmed: Boolean) {
    Box(
        Modifier.width(1.dp).height(HarnessTokens.Control.TabSeparator)
            .alpha(if (dimmed) 0f else 1f)
            .background(HarnessTokens.Colors.ChromeRule)
            .testTag("harness-tab-separator"),
    )
}

/**
 * The conversation, listed as a tab in 完整视图 — the one place the live shell turns it into one, because
 * the panel has taken the whole main region. It is never selected (the panel's own tab carries the
 * selection) and choosing it brings the conversation back beside the panel.
 */
@Composable
private fun ConversationChip(state: HarnessTabStripState, width: Dp, selected: Boolean) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val shape = RoundedCornerShape(HarnessTokens.Control.TabChipRadius)
    Box(
        Modifier.width(width).height(HarnessTokens.Control.TabChipHeight)
            // Same as a panel tab's chip: the clip wraps the clickable so the press indication stops on
            // the pill's rounded edge instead of the chip's square box.
            .clip(shape)
            .hoverable(source)
            // Measured on the live chip: clicking it hands the region to the conversation and leaves the
            // layout where it is, so the two trailing controls keep saying 退出完整视图 / 进入分屏视图.
            .clickable(role = Role.Tab) { state.showConversationInFullView() }
            .semantics { testTag = "harness-tab-conversation"; this.selected = selected },
        contentAlignment = Alignment.CenterStart,
    ) {
        // Same pill as a panel tab's: measured, the selected 聊天 is the elevated surface with the ring,
        // and unselected it is bare text — its label step is the only other difference (`text-default`
        // against the tabs' `text-tertiary`).
        Box(
            Modifier.matchParentSize().clip(shape)
                .background(if (selected) HarnessTokens.Colors.SurfaceElevated else Color.Transparent)
                .then(if (selected) Modifier.border(1.dp, HarnessTokens.Colors.SurfaceRingStrong, shape) else Modifier),
        )
        if (!selected && hovered) {
            Box(Modifier.matchParentSize().clip(shape).background(HarnessTokens.Colors.ChromeFill))
        }
        // Measured on the live chip's content row: `ps-2.5` (10px) at the start, a 16px icon, then the
        // label — which starts 20px after the icon's own start (icon 16 + the row's 8px gap, pulled
        // back 4px by the label), the same icon/gap pair a panel tab's chip uses.
        Row(
            Modifier.matchParentSize().padding(start = 10.dp, end = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HarnessIcon(
                HarnessIconName.ChatBubble,
                tint = if (selected) HarnessTokens.Colors.Text else HarnessTokens.Colors.ChromeIcon,
                size = 16.dp,
            )
            Spacer(Modifier.width(TAB_ICON_GAP))
            HarnessText(
                "聊天",
                // `overflow: clip` in the live rule is the clip half of the fade; the mask is the other.
                modifier = Modifier.weight(1f).clipToBounds().harnessFadeTruncate(),
                color = if (selected) HarnessTokens.Colors.Text else HarnessTokens.Colors.ChromeIcon,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                // The live title is `overflow: clip` under a mask, so it never draws an ellipsis — the
                // fade above is what truncates it.
                overflow = TextOverflow.Clip,
            )
        }
    }
}

/**
 * The trailing cluster, pinned to the right edge. Its two slots are the layout toggle and the tabs
 * control, and the live shell gives each of the four states a different pair — measured:
 *
 * | state | 2490 | 2524 |
 * | --- | --- | --- |
 * | panel closed, no tabs | — | `新建标签页` (方框加号) |
 * | panel closed, tabs open | `进入完整视图` | `显示标签页` (计数徽标) |
 * | panel beside the conversation | `进入完整视图` | `隐藏标签页` |
 * | panel in 完整视图 | `退出完整视图` | `进入分屏视图` |
 *
 * The closed state without tabs is the one the new chat page shows: a single 新建标签页, because a new tab
 * is what opens the panel. With tabs already open the same slot only brings them back, so it says so and
 * carries their count.
 */
@Composable
private fun TrailingCluster(state: HarnessTabStripState) {
    // Off a chat page the live shell leaves none of these controls in the bar — measured, 定时任务 and
    // 代码审查 render no `data-app-shell-workspace-layout-toggle` at all.
    if (!state.onChatPage) return
    Row(
        Modifier.fillMaxHeight().padding(end = HarnessTokens.Spacing.Two),
        horizontalArrangement = Arrangement.spacedBy(HarnessTokens.Control.TitlebarGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TitlebarRule(HarnessTokens.Control.TitlebarTrailingRule, "harness-tab-trailing-rule")
        val split = state.layout == HarnessTabLayout.Split
        when {
            state.tabsHidden && state.tabs.isEmpty() -> StripIconButton(
                HarnessIconName.PanelAdd, "新建标签页", "harness-tab-new",
                state::addNewTab, radius = HarnessTokens.Control.TabRowControlRadius,
            )
            state.tabsHidden -> {
                StripIconButton(
                    HarnessIconName.FullView, "进入完整视图", "harness-tab-split",
                    { state.toggleTabsHidden(); if (state.layout != HarnessTabLayout.Full) state.toggleSplit() },
                    radius = HarnessTokens.Control.TabRowControlRadius,
                )
                StripIconButton(
                    HarnessIconName.SplitView, "显示标签页", "harness-tab-show",
                    state::toggleTabsHidden,
                    radius = HarnessTokens.Control.TabRowControlRadius,
                    content = { TabCountBadge(state.tabs.size) },
                )
            }
            else -> {
                StripIconButton(
                    HarnessIconName.FullView,
                    if (split) "进入完整视图" else "退出完整视图",
                    "harness-tab-split",
                    state::toggleSplit,
                    radius = HarnessTokens.Control.TabRowControlRadius,
                    // Measured: the layout control carries the 10% ink fill while the panel has the whole
                    // region and drops it as soon as the panes are split, so the fill reports the layout.
                    engaged = !split,
                )
                StripIconButton(
                    HarnessIconName.SplitView,
                    if (split) "隐藏标签页" else "进入分屏视图",
                    "harness-tab-hide",
                    if (split) state::toggleTabsHidden else state::unsplit,
                    radius = HarnessTokens.Control.TabRowControlRadius,
                    engaged = split,
                )
            }
        }
    }
}

/**
 * 显示标签页's own artwork: a rounded box with the panel's tab count in it, transcribed from the live
 * control (a 12-unit outline on the 16-unit grid with a single digit centred in it). Not an
 * [HarnessIconName] because it carries a number rather than a fixed path.
 */
@Composable
private fun TabCountBadge(count: Int) {
    // Read the ink in the composition: the draw lambda is not a composable scope.
    val ink = HarnessTokens.Colors.ChromeIcon
    Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(14.dp).drawBehind {
                drawRoundRect(
                    color = ink,
                    cornerRadius = CornerRadius(size.minDimension * 0.28f),
                    style = Stroke(width = size.minDimension * 0.115f),
                )
            },
            contentAlignment = Alignment.Center,
        ) {
            HarnessText(
                count.toString(),
                color = HarnessTokens.Colors.ChromeIcon,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
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
 * One tab slot. Measured on the live tab bar: a 238x32 chip holding an inset painter — a `#FFFFFF`
 * pill with a 1px `rgba(26,28,31,.08)` ring at corner 10 when the tab is selected, nothing at all
 * when it is not (an unselected tab is bare ink at 49% alpha). The close target is a 20x20 box at the
 * trailing edge, revealed by hover.
 *
 * [width] is the slot's own width, which the row divides between the open tabs: the live strip's chip is
 * its slot less the 3px that slot reserves for its separator, and 238 is only the ceiling that division
 * reaches when the tabs are few enough for it to fit.
 */
@Composable
private fun HarnessTabChip(
    tab: HarnessWorkspaceTab,
    selected: Boolean,
    state: HarnessTabStripState,
    drag: HarnessTabDragState,
    onHoverChanged: (Boolean) -> Unit = {},
    width: Dp = HarnessTokens.Control.TabChip,
) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    // The row needs the hover to dismiss the separator next to this chip (measured on the live strip).
    LaunchedEffect(hovered) { onHoverChanged(hovered) }
    val clientRegion = LocalTabClientRegion.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    val shape = RoundedCornerShape(HarnessTokens.Control.TabChipRadius)
    Box(
        Modifier.width(width).height(HarnessTokens.Control.TabChipHeight)
            .onGloballyPositioned { origin = it.positionInWindow() }
            // The clip has to sit **outside** the clickable: the press indication is drawn by the
            // clickable's own node, so without a clip wrapping it the ripple crests over the chip's
            // square 32dp box while the pill under it is rounded. Measured on the live chip, the press
            // ink stops at the same rounded edge as the hover fill.
            .clip(shape)
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
        // Measured: the chip's content is inset 10px at the start and 7px at the end, and holds a
        // 16px kind icon, a 4px gap (an 8px flex gap the label pulls back 4px of), and the title.
        Row(
            Modifier.matchParentSize().padding(start = 10.dp, end = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TabKindIcon(tab, selected)
            Spacer(Modifier.width(TAB_ICON_GAP))
            HarnessText(
                tab.title,
                // Measured: the live title is `overflow: clip` under `text-fade-truncate`'s mask, so a
                // title that does not fit fades out over its last 16px rather than ending in "…" —
                // which is nearly every title once the slots sit on their 90px floor.
                modifier = Modifier.weight(1f).clipToBounds().harnessFadeTruncate(),
                color = if (selected) HarnessTokens.Colors.Text else HarnessTokens.Colors.ChromeIcon,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
            // The close target is absolutely positioned over the chip's trailing edge, so it takes no
            // room at rest — the title runs the chip's full width until hover or selection reveals it,
            // and only then does the title give up its 20px (measured: label 51px at rest, 31px while
            // the close is showing).
            if (selected || hovered) Spacer(Modifier.width(HarnessTokens.Control.TabClose))
        }
        if (selected || hovered) {
            Box(Modifier.matchParentSize().padding(end = 7.dp), contentAlignment = Alignment.CenterEnd) {
                TabCloseButton(tab, state)
            }
        }
    }
}

/** The gap between a chip's kind icon and its title: measured 4px (an 8px flex gap, label pulled back). */
private val TAB_ICON_GAP = 4.dp

/**
 * The strip's own scroll padding, measured as the live scroller's `scroll-px-1` (`scroll-padding: 2px
 * auto`): a selected tab is scrolled fully into view with this much air on the side it came from.
 */
private val TAB_SCROLL_PAD = 2.dp

/**
 * What a tab's kind icon is: measured per kind, the live strip swaps the artwork rather than drawing one
 * glyph for every tab. 变更 is [BrowserTabIcon.Changes], 新标签页 is [BrowserTabIcon.Globe], and the
 * terminal tabs are [BrowserTabIcon.Terminal]; a page this client draws none of those for falls back to
 * the globe rather than to nothing, so a chip never loses its icon.
 */
@Composable
private fun TabKindIcon(tab: HarnessWorkspaceTab, selected: Boolean) {
    val icon = when (tab.page) {
        PageId.NewTab -> BrowserTabIcon.Globe
        is PageId.Settings -> when ((tab.page as PageId.Settings).section) {
            SettingsSection.Git -> BrowserTabIcon.Changes
            else -> BrowserTabIcon.Globe
        }
        else -> BrowserTabIcon.Globe
    }
    BrowserTabIconGlyph(
        icon,
        tint = if (selected) HarnessTokens.Colors.Text else HarnessTokens.Colors.ChromeIcon,
    )
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
 *
 * [content] replaces the glyph for controls whose artwork carries data rather than a fixed path — the
 * one that does is 显示标签页, whose box holds the panel's tab count.
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
    // is how the live 退出完整视图 control renders while the full view is up.
    engaged: Boolean = false,
    // No measured tip exists for the tab close target, so it is left tip-free rather than given
    // wording Codex does not show.
    showTip: Boolean = true,
    content: (@Composable () -> Unit)? = null,
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
            if (content != null) {
                content()
            } else {
                HarnessIcon(
                    name,
                    tint = if (engaged) HarnessTokens.Colors.Text else HarnessTokens.Colors.ChromeIcon,
                    size = iconSize,
                )
            }
        }
    }
}
