package im.hikaru.harness.client.shell

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import im.hikaru.harness.client.app.ConversationAppearance
import im.hikaru.harness.client.conversation.ConversationCommand
import im.hikaru.harness.client.page.LocalWorkspaceNavigatorVisible
import im.hikaru.harness.client.app.LocalConversationBottomInset
import im.hikaru.harness.client.app.LocalConversationViewportHeight
import im.hikaru.harness.client.panels.ThreadPanelLayout
import im.hikaru.harness.client.panels.ThreadPanelWidthMode
import im.hikaru.harness.client.scheduled.label
import im.hikaru.harness.ui.foundation.*
import kotlinx.coroutines.delay
import kotlin.ranges.coerceAtLeast

/**
 * Shared application layout: a fixed icon rail, an optional workspace navigator, the main surface,
 * and an optional right context panel. Platform window chrome and system actions stay outside this
 * composable.
 */
@Composable
public fun AppShell(
    state: AppShellState,
    navItems: List<AppShellRailItem>,
    onNavSelect: (AppShellRailItem) -> Unit,
    onOpenAccountMenu: () -> Unit = {},
    onDismissAccountMenu: () -> Unit = {},
    onAccountMenuAction: (AccountMenuAction) -> Unit = {},
    onOpenExploreMenu: () -> Unit = {},
    onDismissExploreMenu: () -> Unit = {},
    onOpenExploreDestination: (PageId) -> Unit = {},
    onTogglePinRailDestination: (RailDestination) -> Unit = {},
    navigator: WorkspaceNavigatorState,
    scheduledTasks: im.hikaru.harness.client.scheduled.ScheduledTasksState = state.scheduledTasksNavigator,
    projectNodes: List<WorkspaceProjectNode>,
    recentTasks: List<RecentTaskItem>,
    onToggleSidebar: () -> Unit,
    onNewTask: () -> Unit,
    onNavigatorSearch: (String) -> Unit,
    /** Settings-navigator filter, kept separate from the Workspace [onNavigatorSearch]. */
    onSettingsSearch: (String) -> Unit = {},
    onSettingsSelect: (SettingsNavItem) -> Unit = {},
    /** `settings.nav.back`: leaves Settings for the main app. */
    onSettingsBack: () -> Unit = {},
    // The header search action opens the workspace command menu.
    navigatorSearchOpen: Boolean = false,
    onToggleNavigatorSearch: () -> Unit = {},
    onToggleProject: (String) -> Unit,
    onSelectNode: (WorkspaceItemNode) -> Unit,
    onSelectRecent: (RecentTaskItem) -> Unit,
    onPluginEnabledFilterChange: ((Boolean) -> Unit)? = null,
    onScheduledSearch: (String) -> Unit = {},
    onScheduledToggleTask: (String) -> Unit = {},
    onScheduledShowOlder: (String) -> Unit = {},
    onScheduledRetry: () -> Unit = {},
    onScheduledNewTask: () -> Unit = {},
    onScheduledOpenRun: (im.hikaru.harness.client.scheduled.ScheduledTask, im.hikaru.harness.client.scheduled.ScheduledRun) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    themeMode: HarnessThemeMode = HarnessThemeMode.System,
    // Writes the user's navigator resize back to the shared layout state. Null keeps
    // the navigator fixed at its layout width and mounts no resize hit region.
    onNavigatorResize: ((Dp) -> Unit)? = null,
    onOpenProjectContext: ((String) -> Unit)? = null,
    /** Workspace section “+” action: opens the local-project editor. */
    onNewProject: (() -> Unit)? = null,
    /** Workspace section “…” action: opens the Projects page. */
    onOpenProjects: (() -> Unit)? = null,
    content: @Composable () -> Unit,
    composer: (@Composable () -> Unit)? = null,
    contextPanel: (@Composable () -> Unit)? = null,
    contextPanelWidthMode: ThreadPanelWidthMode? = null,
    onDismissContextPanel: (() -> Unit)? = null,
    /**
     * The thread summary island. It is a separate surface from the tabbed [contextPanel]; a page that
     * has no summary passes null and draws nothing. Width is a fixed 300px column (the observed
     * summary panel width) so the main content is the only flexible region.
     */
    summaryPanel: (@Composable () -> Unit)? = null,
    summaryPanelWidth: Dp = 300.dp,
    onDismissSummaryPanel: (() -> Unit)? = null,
    scheduledNavigatorContent: (@Composable () -> Unit)? = null,
    /** Page-owned Plugins navigator body. Null falls back to the shell's filter-only placeholder. */
    pluginsNavigatorContent: (@Composable () -> Unit)? = null,
) {
    HarnessTheme(themeMode = themeMode) {
        var shellOrigin by remember { mutableStateOf(Offset.Zero) }
        var accountBounds by remember { mutableStateOf(Rect.Zero) }
        BoxWithConstraints(
            modifier = modifier.fillMaxSize().background(HarnessTokens.Colors.ShellBackground)
                .onGloballyPositioned { shellOrigin = it.boundsInWindow().topLeft },
        ) {
            // Window-rect of the 探索 item, so the popup can anchor beside it.
            var exploreAnchor by remember { mutableStateOf(ExploreMenuAnchor(0, 0)) }
            // Both hover popovers share one state machine (trigger hover tracks the item, content
            // hover keeps it, a short settle closes it) — see HoverPopoverState.
            // Selection changes the rail item's value during navigation; hover lifetime follows its stable id.
            val previewPopover = rememberHoverPopoverState<String>()
            val explorePopover = rememberHoverPopoverState<String>()
            // Mirror the shared state into the existing menu/anchoring plumbing.
            LaunchedEffect(explorePopover.isOpen) {
                if (explorePopover.isOpen) onOpenExploreMenu() else onDismissExploreMenu()
            }
            // The 探索 menu also closes from outside the hover pair (choosing a destination). Its
            // content then leaves the composition without a pointer-leave, which would strand
            // `contentHovered` and keep the rail trigger painted as open; clear the hover state
            // whenever the menu is no longer open.
            LaunchedEffect(state.exploreMenu.isOpen) {
                if (!state.exploreMenu.isOpen) explorePopover.close()
            }
            // The page owns the navigator: only the kind named by the current page mounts, and a page
            // with NavigatorKind.None has no drawer at all, so it can never show the Workspace tree.
            val spec = pageShellSpec(state.navigation.currentPage)
            val activeNavigator: NavigatorState? =
                when (spec.navigator) {
                    NavigatorKind.None -> null
                    NavigatorKind.Workspace -> navigator
                    NavigatorKind.Settings -> state.settingsNavigator
                    NavigatorKind.Plugins -> state.pluginsNavigator
                    NavigatorKind.Scheduled -> state.scheduledTasksNavigator
                }
            val layout =
                if (activeNavigator == null) {
                    NavigatorLayout.Hidden
                } else {
                    navigatorLayout(activeNavigator.mode, maxWidth)
                }
            val navigatorPreferred = pageNavigatorWidth(spec.navigator, state.navigatorPreferredWidth)
            val drawerWidth = navigatorWidth(layout, maxWidth, navigatorPreferred)
            val navigatorTag = navigatorTestTag(spec.navigator)
            // A page with NavigatorKind.None owns no navigator, so it draws no navigator affordance:
            // no titlebar toggle, no compact bottom-bar entry, no drawer/scrim and no resize handle.
            val navigatorMounted = spec.mountsNavigator
            // A rail destination's navigator is previewed on hover only while the current page shows no
            // navigator column: either the page mounts none, or the one it mounts is collapsed — the
            // hidden column is exactly what the hover brings back, as measured (`data-slate-sidebar-peeking`
            // on the collapsed panel). While a navigator is on screen the preview stays out of its way.
            val navigatorForDestination: (RailDestination) -> NavigatorKind = { destination ->
                railDestinationPage(destination)?.let { pageShellSpec(it).navigator } ?: NavigatorKind.None
            }
            val navigatorOnScreen = navigatorMounted && layout != NavigatorLayout.Hidden
            val previewKind =
                navItems.firstOrNull { it.id == previewPopover.activeKey }
                    ?.destination
                    ?.let(navigatorForDestination)
                    ?.takeIf { !navigatorOnScreen && it != NavigatorKind.None }
            // What the peek is showing. It takes the hovered destination's navigator as soon as one
            // exists and holds the last one through a slot that owns none (库 / 项目 / 探索), so moving
            // between rail slots never blinks the panel away and back: the popover's own settle closes it
            // once the pointer has left the rail, and resting on a slot with no navigator closes it after
            // that same settle — the close delay Radix HoverCard, the popover this shell mirrors, applies.
            var shownPeekKind by remember { mutableStateOf<NavigatorKind?>(null) }
            LaunchedEffect(previewKind, previewPopover.activeKey) {
                when {
                    previewKind != null -> shownPeekKind = previewKind
                    previewPopover.activeKey == null -> shownPeekKind = null
                    else -> {
                        delay(previewPopover.settleMillis)
                        shownPeekKind = null
                    }
                }
            }
            // BoxWithConstraintsScope.maxWidth cannot resolve inside the pointer-input receiver below.
            val navigatorAvailableWidth = maxWidth
            // The shell's own height: what the peek card sizes itself from (its `let` block has no
            // BoxWithConstraintsScope receiver).
            val shellHeight = maxHeight
            // The rail is a fixed side column, so this is the leading offset of the content area.
            val railWidth = HarnessTokens.Control.RailWidth
            val mainAreaWidth = (maxWidth - railWidth - if (layout == NavigatorLayout.Pushed) drawerWidth else 0.dp).coerceAtLeast(0.dp)
            val panelOverlay = contextPanelWidthMode != null && mainAreaWidth < 672.dp
            // The summary island keeps its 300px column while there is room; a narrow window floats it
            // over the content with a scrim, the same contract the context panel uses.
            val summaryOverlay = summaryPanel != null && mainAreaWidth < 672.dp
            val panelWidth = if (contextPanelWidthMode == null) HarnessTokens.Control.ProjectContext else
                ThreadPanelLayout.resolveWidth(
                    mainWidth = mainAreaWidth.value, height = maxHeight.value,
                    availableWidth = if (panelOverlay) mainAreaWidth.value else (mainAreaWidth - 353.dp).coerceAtLeast(0.dp).value,
                    mode = if (panelOverlay) ThreadPanelWidthMode.Full else contextPanelWidthMode,
                ).dp
            // One navigator body, shared by the pushed column and the floating drawer. The width is
            // applied here, once, so the pane and its resize edge stay one column.
            // The drag starts from the width that is actually laid out, not the stored preference:
            // when the layout clamps it (a pushed 520dp preference renders 258dp at a 630dp window),
            // a preference-based start would leave a dead zone before the edge moved.
            val latestDrawerWidth by rememberUpdatedState(drawerWidth)
            val latestResize by rememberUpdatedState(onNavigatorResize)
            val latestToggleSidebar by rememberUpdatedState(onToggleSidebar)
            // Closing the command menu resets its query: Codex starts the next search empty.
            val closeNavigatorSearch: () -> Unit = {
                onNavigatorSearch("")
                onToggleNavigatorSearch()
            }
            // One navigator body, shared by the pushed column, the floating drawer and the hover
            // preview. `kind` selects which page's navigator to draw, so the preview can render the
            // hovered destination's navigator without navigating to it.
            val navigatorContent: @Composable (NavigatorKind, Dp, Boolean) -> Unit = { kind, width, handleResize ->
                Box(
                    modifier =
                        Modifier
                            .width(width)
                            .fillMaxHeight()
                            .semantics { testTag = navigatorTestTag(kind) ?: "navigator-none" },
                ) {
                    when (kind) {
                        NavigatorKind.Workspace ->
                            WorkspaceNavigator(
                                state = state,
                                navigator = navigator,
                                projectNodes = projectNodes,
                                recentTasks = recentTasks,
                                onNewTask = onNewTask,
                                searchOpen = navigatorSearchOpen,
                                onToggleSearch = onToggleNavigatorSearch,
                                onToggleProject = onToggleProject,
                                onSelectNode = onSelectNode,
                                onSelectRecent = onSelectRecent,
                                onOpenProjectContext = onOpenProjectContext,
                                onNewProject = onNewProject,
                                onOpenProjects = onOpenProjects,
                            )

                        NavigatorKind.Settings ->
                            SettingsNavigator(
                                state = state.settingsNavigator,
                                onSearch = onSettingsSearch,
                                onSelect = onSettingsSelect,
                            )
                        NavigatorKind.Plugins -> {
                            if (pluginsNavigatorContent != null) {
                                CompositionLocalProvider(LocalWorkspaceNavigatorVisible provides (layout == NavigatorLayout.Pushed)) {
                                    pluginsNavigatorContent()
                                }
                            } else {
                                PluginsNavigator(state.pluginsNavigator, onPluginEnabledFilterChange)
                            }
                        }
                        NavigatorKind.Scheduled -> {
                            if (scheduledNavigatorContent != null) {
                                scheduledNavigatorContent()
                            } else {
                                ScheduledTasksNavigator(
                                    state = scheduledTasks,
                                    onSearch = onScheduledSearch,
                                    onToggleTask = onScheduledToggleTask,
                                    onShowOlder = onScheduledShowOlder,
                                    onRetry = onScheduledRetry,
                                    onNewTask = onScheduledNewTask,
                                    onOpenRun = onScheduledOpenRun,
                                )
                            }
                        }
                        NavigatorKind.None -> Unit
                    }
                    // Shared resize feedback at the right edge. The drag reads the latest width
                    // through rememberUpdatedState: keying the pointer session on the value it
                    // mutates would cancel the gesture on every recomposition.
                    if (handleResize && spec.resizable && latestResize != null && layout != NavigatorLayout.Hidden) {
                        val density = LocalDensity.current
                        val resizeDrag = remember(layout, navigatorAvailableWidth) { NavigatorResizeDragState() }
                        HarnessResizeHandle(
                            onResizeStart = { resizeDrag.start(latestDrawerWidth) },
                            onResize = { amount ->
                                val result = resizeDrag.drag(
                                    delta = with(density) { amount.toDp() },
                                    layout = layout,
                                    availableWidth = navigatorAvailableWidth,
                                )
                                latestResize?.invoke(result.width)
                                if (result.collapse) latestToggleSidebar()
                            },
                            modifier = Modifier.align(Alignment.CenterEnd)
                                .semantics { testTag = "navigator-resize-handle" },
                            description = "调整导航区宽度",
                            lineAlignment = Alignment.CenterEnd,
                        )
                    }

                    // A non-resizable pushed navigator still needs a static boundary. The resize
                    // component owns the line whenever it is mounted, avoiding a double border.
                    if (layout == NavigatorLayout.Pushed && !(handleResize && spec.resizable && latestResize != null)) {
                        Box(
                            modifier =
                                Modifier
                                    .align(Alignment.CenterEnd)
                                    .fillMaxHeight()
                                    .width(1.dp)
                                    .background(HarnessTokens.Colors.BorderSubtle),
                        )
                    }
                }
            }
            Column(modifier = Modifier.fillMaxSize()) {
                // The window owns the title strip (Jewel's TitleBar above this shell), so the shell
                // starts at the strip's bottom edge and the card fills from there.
                Box(modifier = Modifier.weight(1f)) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        AppRail(
                            navItems = navItems,
                            onNavSelect = onNavSelect,
                            onOpenAccountMenu = onOpenAccountMenu,
                            onAccountBounds = { accountBounds = it },
                            accountMenu = state.accountMenu,
                            onDismissAccountMenu = onDismissAccountMenu,
                            onAccountMenuAction = onAccountMenuAction,
                            exploreMenu = state.exploreMenu,
                            onOpenExploreMenu = onOpenExploreMenu,
                            onDismissExploreMenu = onDismissExploreMenu,
                            onExploreAnchor = { exploreAnchor = it },
                            onExploreHoverChanged = { hov -> explorePopover.onTriggerHover("explore", hov) },
                            onNavHoverChanged = { item, hov -> previewPopover.onTriggerHover(item.id, hov) },
                            // A preview replaces this item's tip: while the current page shows no
                            // navigator column, a hovered destination that has one floats it instead.
                            popoverActiveForItem = { item ->
                                // 探索 paints as open for as long as its menu is up, whether the menu
                                // came from the hover state or a click.
                                previewPopover.activeKey == item.id ||
                                    (item.id == "explore" && (explorePopover.isOpen || state.exploreMenu.isOpen))
                            },
                            tipSuppressedForItem = { item ->
                                !navigatorOnScreen &&
                                    item.destination?.let(navigatorForDestination)?.let { it != NavigatorKind.None } == true
                            },
                            tooltipScope = state.navigation.currentPage to state.navigation.selectedWorkspaceId,
                            tooltipSuppressed = state.navigation.overlay != OverlayId.None || state.panel.isOpen,
                        )
                        // The page surface card: the navigator, the main content and any side panel sit
                        // on one rounded surface, inset from the window's right and bottom edges.
                        Box(
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .padding(
                                        end = HarnessTokens.Control.PageSurfaceInset,
                                        bottom = HarnessTokens.Control.PageSurfaceInset,
                                    )
                                    // The reference card is :
                                    // a half-pixel hairline plus a soft drop shadow that spills over the rail
                                    // and the title strip. The shadow is drawn before the clip so it escapes.
                                    .dropShadow(
                                        shape = RoundedCornerShape(HarnessTokens.Control.PageSurfaceRadius),
                                        // `--shadow-card`, shared with the peek card.
                                        shadow = HarnessTokens.Control.CardShadow,
                                    )
                                    .clip(RoundedCornerShape(HarnessTokens.Control.PageSurfaceRadius))
                                    // The shadow may paint inside the shape, so the card lays the shell
                                    // tone under its regions; otherwise a translucent region (the
                                    // navigator's 65% tint) would sample the shadow instead of the shell.
                                    .background(HarnessTokens.Colors.ShellBackground)
                                    .border(
                                        0.5.dp,
                                        HarnessTokens.Colors.SurfaceRing,
                                        RoundedCornerShape(HarnessTokens.Control.PageSurfaceRadius),
                                    ),
                        ) {
                            Row(modifier = Modifier.fillMaxSize()) {
                                if (layout == NavigatorLayout.Pushed) {
                                    Box(Modifier.fillMaxHeight().background(HarnessTokens.Colors.NavigationBackground)) {
                                        navigatorContent(spec.navigator, drawerWidth, true)
                                    }
                                }
                                // The main region — right of the navigator, inside the page-surface
                                // card — is what the title-bar tabs split. The rail and the navigator
                                // are outside it, so neither is ever duplicated by a split.
                                val mainRegion = LocalHarnessMainRegion.current
                                val pageSurface: @Composable (Modifier) -> Unit = { surfaceModifier ->
                                    // While a split is up, this surface is one pane's, and a pane's column
                                    // is its *own* page's — the shell's chrome follows the conversation,
                                    // but a page never borrows the conversation's column cap, nor its
                                    // composer dock.
                                    val paneTab = LocalHarnessPaneTab.current
                                    val paneSpec = paneTab?.page?.let(::pageShellSpec) ?: spec
                                    MainSurface(
                                        modifier = surfaceModifier.semantics { testTag = "main-surface" },
                                        // The dock hangs off the shell's single composer, which drafts and
                                        // sends on the **conversation**; a pane that is a tab never gets it.
                                        docksComposer = paneSpec.docksComposer && paneTab == null,
                                        conversation = (paneTab?.page ?: state.navigation.currentPage) is PageId.Thread,
                                        contentMaxWidth =
                                            when {
                                                // A page that brings its own column (新标签页) keeps the whole
                                                // surface: its toolbar is pane-wide and its column is wider than
                                                // the shared cap.
                                                paneSpec.ownContentColumn -> null
                                                paneSpec.navigator == NavigatorKind.Settings ->
                                                    HarnessTokens.Control.SettingsContentMaxWidth
                                                else -> HarnessTokens.Control.ContentMaxWidth
                                            },
                                        content = {
                                            CompositionLocalProvider(LocalWorkspaceNavigatorVisible provides
                                                (layout == NavigatorLayout.Pushed && paneSpec.navigator == spec.navigator)) {
                                                content()
                                            }
                                        },
                                        composer = composer,
                                    )
                                }
                                if (mainRegion == null) {
                                    pageSurface(Modifier.weight(1f))
                                } else {
                                    mainRegion.Panes(Modifier.weight(1f), pageSurface)
                                }
                                if (contextPanel != null && !panelOverlay) {
                                    Box(
                                        modifier =
                                            Modifier
                                                .width(1.dp)
                                                .fillMaxHeight()
                                                .background(HarnessTokens.Colors.BorderSubtle),
                                    )
                                    ContextPanelSurface(contextPanel, panelWidth, contextPanelWidthMode != null)
                                }
                                if (summaryPanel != null && !summaryOverlay) {
                                    Box(
                                        modifier =
                                            Modifier
                                                .width(1.dp)
                                                .fillMaxHeight()
                                                .background(HarnessTokens.Colors.BorderSubtle),
                                    )
                                    ContextPanelSurface(summaryPanel, summaryPanelWidth, true)
                                }
                            }
                        }
                    }
                    if (layout == NavigatorLayout.Floating) {
                        // The drawer floats above the main surface instead of pushing it, so a narrow
                        // window keeps the content at its own width. It starts after the rail (or at
                        // the window edge under a bottom bar), so the navigation chrome stays
                        // clickable; a scrim over the rest of the content area dismisses it, and the
                        // drawer is drawn after the scrim so its rows keep receiving clicks.
                        Row(modifier = Modifier.fillMaxSize()) {
                            Spacer(Modifier.width(railWidth))
                            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxSize()
                                            .background(NavigatorScrim)
                                            .clickable(onClick = onToggleSidebar)
                                            .semantics {
                                                role = Role.Button
                                                testTag = "navigator-scrim"
                                            },
                                )
                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxHeight()
                                            .shadow(8.dp)
                                            .semantics { testTag = "navigator-overlay" },
                                ) {
                                    Box(Modifier.fillMaxHeight().background(HarnessTokens.Colors.NavigationBackground)) {
                                        navigatorContent(spec.navigator, drawerWidth, true)
                                    }
                                }
                            }
                        }
                    }
                    if (contextPanel != null && panelOverlay) {
                        // Keep the production titlebar actions and pushed navigator above/beside
                        // the floating panel so users can open or close another Thread tab.
                        val mainLeading = railWidth + if (layout == NavigatorLayout.Pushed) drawerWidth else 0.dp
                        // The shell's own coordinate space already starts below the window strip.
                        val titleHeight = 0.dp
                        Box(Modifier.fillMaxSize().padding(start = mainLeading, top = titleHeight + 1.dp)) {
                            if (onDismissContextPanel != null) Box(
                                Modifier.fillMaxSize().background(NavigatorScrim)
                                    .clickable(onClick = onDismissContextPanel)
                                    .semantics { testTag = "thread-panel-scrim" },
                            )
                            Box(Modifier.align(Alignment.CenterEnd).width(panelWidth).fillMaxHeight()
                                .shadow(8.dp).semantics { testTag = "thread-panel-overlay" }) {
                                ContextPanelSurface(contextPanel, panelWidth, true)
                            }
                        }
                    }
                    if (summaryPanel != null && summaryOverlay) {
                        val mainLeading = railWidth + if (layout == NavigatorLayout.Pushed) drawerWidth else 0.dp
                        // The shell's own coordinate space already starts below the window strip.
                        val titleHeight = 0.dp
                        Box(Modifier.fillMaxSize().padding(start = mainLeading, top = titleHeight + 1.dp)) {
                            if (onDismissSummaryPanel != null) Box(
                                Modifier.fillMaxSize().background(NavigatorScrim)
                                    .clickable(onClick = onDismissSummaryPanel)
                                    .semantics { testTag = "thread-summary-scrim" },
                            )
                            Box(Modifier.align(Alignment.CenterEnd).width(summaryPanelWidth).fillMaxHeight()
                                .shadow(8.dp).semantics { testTag = "thread-summary-overlay" }) {
                                ContextPanelSurface(summaryPanel, summaryPanelWidth, true)
                            }
                        }
                    }
                    shownPeekKind?.let { kind ->
                        // A hidden navigator peeks. Measured on the live shell with the sidebar collapsed
                        // and a rail item hovered: the left panel flips to `data-slate-sidebar-peeking`
                        // (`overflow-visible`, `z-40`) and its `[data-slate-sidebar-content]` becomes a
                        // floating card — x 56..340, y 48..1312 of a 2560x1320 window, i.e. the 288px
                        // column less the 4px start margin, 4px off the rail, the title strip and the
                        // bottom; `border-radius: calc(--radius-xl-base - --spacing)` = 8px; an opaque
                        // elevated surface; and `inset 0 0 0 var(--hairline) var(--app-shell-sidebar-popover-border),
                        // var(--shadow-card)` — the same card shadow the page surface uses. The hovered rail
                        // item reports `aria-expanded=true`, the peek shows *that destination's* navigator,
                        // and nothing navigates.
                        //
                        // It is a plain overlay of the shell, not a `Popup`. A Popup is a scene layer the
                        // window keeps inside its own bounds: this card plus the room its shadow needs was
                        // taller than the space below its own origin, so the layer was aligned to the
                        // window's bottom edge and the whole panel was shifted *up* 16px — the peek started
                        // above the page card's top edge. Drawn in the shell it sits where it is placed, and
                        // its height comes from the shell's own measured height instead of the window's
                        // (the title strip is `Titlebar` + its hairline, so window-minus-constants is off
                        // by that hairline).
                        // The peek restores the width the pushed column would have had, so it does not
                        // depend on the collapsed mode's own (zero) width.
                        val peekWidth = navigatorWidth(NavigatorLayout.Pushed, navigatorAvailableWidth, navigatorPreferred)
                        val peekSource = remember { MutableInteractionSource() }
                        val peekHovered by peekSource.collectIsHoveredAsState()
                        LaunchedEffect(peekHovered) { previewPopover.onContentHover(peekHovered) }
                        val peekShape = RoundedCornerShape(HarnessTokens.Control.NavigatorPeekRadius)
                        val peekCardWidth = peekWidth - HarnessTokens.Control.NavigatorPeekInset
                        // Measured v155 card: y=48 in window coordinates (4px below the title strip) and
                        // 826px tall in an 882px window, i.e. the shell height less 4 above and 4 below.
                        val peekCardHeight = (shellHeight - HarnessTokens.Spacing.Two).coerceAtLeast(0.dp)
                        Box(
                            modifier =
                                Modifier
                                    .align(Alignment.TopStart)
                                    .offset(
                                        x = HarnessTokens.Control.RailWidth + HarnessTokens.Control.NavigatorPeekInset,
                                        y = HarnessTokens.Control.NavigatorPeekInset,
                                    )
                                    .size(width = peekCardWidth, height = peekCardHeight)
                                    // The shadow goes in before the clip so it escapes the card; its
                                    // token is the page surface's own (`--shadow-card`).
                                    .dropShadow(peekShape, HarnessTokens.Control.CardShadow)
                                    .clip(peekShape)
                                    // Opaque: the reference paints the peeking card with the popover
                                    // background, not the 65% tint the embedded column uses.
                                    .background(HarnessTokens.Colors.NavigatorPeekSurface)
                                    // 1px 8% ink inset ring (`--hairline` at this resolution).
                                    .border(1.dp, HarnessTokens.Colors.SurfaceRingStrong, peekShape)
                                    .hoverable(peekSource)
                                    .semantics { testTag = "navigator-peek" },
                        ) {
                            navigatorContent(kind, peekCardWidth, false)
                        }
                    }
                }
            }
            // The 探索 pin menu is its own overlay: it anchors beside the 探索 button and stays
            // independent from the account avatar menu. Drawn in-composition so captures record it.
            if (state.exploreMenu.isOpen) {
                ExploreMenuOverlay(
                    anchor = exploreAnchor,
                    selected = state.navigation.currentPage,
                    pinned = state.pinnedRailDestinations,
                    onDismiss = onDismissExploreMenu,
                    onOpenDestination = onOpenExploreDestination,
                    onTogglePin = onTogglePinRailDestination,
                    onHoverChanged = { hov -> explorePopover.onContentHover(hov) },
                )
            }
            // avatar without being clipped by the rail column. It is drawn inside this composition
            // (not a Popup layer) so off-screen captures record it.
            if (state.accountMenu.isOpen) {
                AccountMenuOverlay(
                    state = state.accountMenu,
                    anchorBounds = accountBounds.translate(-shellOrigin),
                    avatarInitials = navItems.firstOrNull { it.id == RAIL_ACCOUNT_ID }?.initials,
                    onDismiss = onDismissAccountMenu,
                    onAction = onAccountMenuAction,
                )
            }
            // Codex's search affordance is a command menu centred over the shell, not a field in the
            // sidebar. Drawn last so it sits above the rail, navigator and page content.
            if (navigatorSearchOpen) {
                WorkspaceCommandMenu(
                    query = navigator.searchQuery,
                    onQueryChange = onNavigatorSearch,
                    recentTasks = recentTasks,
                    projectNodes = projectNodes,
                    onNewTask = {
                        closeNavigatorSearch()
                        onNewTask()
                    },
                    onNewProject = {
                        closeNavigatorSearch()
                        onNewProject?.invoke()
                    },
                    onSelectRecent = { task ->
                        closeNavigatorSearch()
                        onSelectRecent(task)
                    },
                    onDismiss = closeNavigatorSearch,
                )
            }
        }
    }
}

@Composable
public fun AppRail(
    navItems: List<AppShellRailItem>,
    onNavSelect: (AppShellRailItem) -> Unit,
    onOpenAccountMenu: () -> Unit = {},
    accountMenu: AccountMenuState = AccountMenuState(),
    onDismissAccountMenu: () -> Unit = {},
    onAccountMenuAction: (AccountMenuAction) -> Unit = {},
    exploreMenu: ExploreMenuState = ExploreMenuState(),
    onOpenExploreMenu: () -> Unit = {},
    onDismissExploreMenu: () -> Unit = {},
    onExploreAnchor: (ExploreMenuAnchor) -> Unit = {},
    /** 探索 is hover-opened; the rail reports its hover so the owner can keep or drop the menu. */
    onExploreHoverChanged: (Boolean) -> Unit = {},
    /** Every destination reports its hover so the owner can preview a page navigator. */
    onNavHoverChanged: (AppShellRailItem, Boolean) -> Unit = { _, _ -> },
    /** True while this item's hover shows the navigator preview, which replaces its tip. */
    tipSuppressedForItem: (AppShellRailItem) -> Boolean = { false },
    /** True while this item keeps a rail popover (探索 menu or navigator preview) open. */
    popoverActiveForItem: (AppShellRailItem) -> Boolean = { false },
    tooltipScope: Any? = Unit,
    tooltipSuppressed: Boolean = false,
    onAccountBounds: (Rect) -> Unit = {},
) {
    val accountItem =
        navItems.firstOrNull { it.id == RAIL_ACCOUNT_ID }
            ?: AppShellRailItem(RAIL_ACCOUNT_ID, "账号", HarnessIconName.User)
    val destinationItems = navItems.filterNot { it.id == RAIL_ACCOUNT_ID }
    val density = LocalDensity.current
    Column(
        modifier =
            Modifier
                .width(HarnessTokens.Control.RailWidth)
                .fillMaxHeight()
                .semantics { testTag = "app-rail" }
                // The rail is the window background, not a distinct surface: the reference measures the
                // same 246 as the app background. It draws no end edge, only a soft ~13px fade towards
                // the page surface card (the card's 4% ring spread leftwards, 246 -> 236).
                .background(HarnessTokens.Colors.ShellBackground)
                // 8dp inset leaves the 36dp item box and puts the icon centre on the rail centre.
                .padding(
                    start = HarnessTokens.Spacing.Two,
                    top = HarnessTokens.Spacing.Two,
                    end = HarnessTokens.Spacing.Two,
                    bottom = HarnessTokens.Control.RailAccountBottomInset,
                ),
        horizontalAlignment = Alignment.CenterHorizontally,
        // Codex pitch is 44px: a 36px item plus an 8px gap.
        verticalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Two),
    ) {
        railSections(destinationItems).forEachIndexed { index, section ->
            // A single rule between consecutive groups; the normal 8dp gap already surrounds it.
            if (index > 0) RailGroupDivider()
            section.forEach { item ->
                // 探索 opens the pin menu; every other slot navigates. The trigger also reports its
                // top edge so the menu can anchor beside it.
                val exploreTrigger = item.id == "explore"
                key(item.id) {
                    RailNavItem(
                        item = item,
                        tooltipScope = tooltipScope,
                        tooltipSuppressed =
                            tooltipSuppressed || accountMenu.isOpen || exploreMenu.isOpen ||
                                tipSuppressedForItem(item),
                        onClick = {
                            // 探索 is hover-opened; a click just makes sure it stays open.
                            if (exploreTrigger) onOpenExploreMenu() else onNavSelect(item)
                        },
                        modifier =
                            if (exploreTrigger) {
                                Modifier.onGloballyPositioned { coordinates ->
                                    val bounds = coordinates.boundsInWindow()
                                    onExploreAnchor(
                                        ExploreMenuAnchor(
                                            // The reference panel left edge is the 探索 item right edge + 5 (49 for a box ending at 44).
                                            left = bounds.right.roundToInt() + with(density) { HarnessTokens.Control.ExploreMenuOffsetX.roundToPx() },
                                            top = bounds.top.roundToInt() + with(density) { HarnessTokens.Control.ExploreMenuOffsetY.roundToPx() },
                                        ),
                                    )
                                }
                            } else {
                                Modifier
                            },
                        // 探索 opens its pin menu on hover and draws no tip; other slots keep the tip.
                        hoverOpensMenu = exploreTrigger,
                        onHoverChanged = { hov ->
                            if (exploreTrigger) onExploreHoverChanged(hov)
                            onNavHoverChanged(item, hov)
                        },
                        popoverActive = popoverActiveForItem(item),
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        // The account menu itself is an overlay owned by AppShell, so it is not drawn inside the rail
        // column; the rail avatar only opens or dismisses it.
        RailAccountAvatar(
            item = accountItem,
            expanded = accountMenu.isOpen,
            modifier = Modifier.onGloballyPositioned { onAccountBounds(it.boundsInWindow()) },
            onClick = {
                if (accountMenu.isOpen) {
                    onDismissAccountMenu()
                } else {
                    onOpenAccountMenu()
                }
            },
        )
    }
}

/**
 * Codex Profile Dropdown: a compact, single-column menu anchored beside the rail account chip. The
 * structure and the geometry come from the Codex menu primitive in app.asar; the measurement note is
 * docs/desktop-ui/reference/account-menu-measurement-2026-10-06.md.
 *
 * The menu is an overlay beside the rail: opening it never changes the current page, it only flips
 * [AccountMenuState.isOpen].
 */
@Composable
private fun AccountMenu(
    state: AccountMenuState,
    onAction: (AccountMenuAction) -> Unit,
    avatarInitials: String?,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(HarnessTokens.Control.MenuRadius)
    Column(
        modifier =
            modifier
                // Codex draws the dropdown overlay with `--shadow-dropdown-overlay`
                // (`0 0 0 .5px border, 0px 8px 16px -4px #0000001f`); the .5px ring is the
                // hairline border below, so only the xl blur is drawn here.
                .dropShadow(
                    shape = shape,
                    shadow =
                        Shadow(
                            radius = HarnessTokens.Control.MenuShadowBlur,
                            spread = HarnessTokens.Control.MenuShadowSpread,
                            offset = DpOffset(x = 0.dp, y = HarnessTokens.Control.MenuShadowOffsetY),
                            color = HarnessTokens.Control.MenuShadowColor,
                        ),
                )
                .width(HarnessTokens.Control.MenuWidth)
                .clip(shape)
                .background(HarnessTokens.Colors.SurfaceElevated)
                .semantics { testTag = "account-menu" }
                .padding(HarnessTokens.Control.MenuPadding)
                .verticalScroll(rememberScrollState()),
    ) {
        AccountMenuIdentity(state, avatarInitials)
        // The row list is a pure model so the signed-in/out structure is unit-testable; see
        // accountMenuItems in AppShellState.kt.
        accountMenuItems(state).forEach { item ->
            AccountMenuRow(
                icon = item.icon,
                title = item.label,
                testTag = item.testTag,
                enabled = item.enabled,
                onClick = { onAction(item.action) },
            )
            if (item.separatorAfter) AccountMenuSeparator()
        }
    }
}

/**
 * The account menu as an in-composition overlay: a full-surface dismiss layer plus the panel anchored
 * to the rail avatar. It keeps the current page, and it draws inside the shell composition so the
 * off-screen capture records it (a Popup layer is not composited by ImageComposeScene).
 */
@Composable
private fun BoxScope.AccountMenuOverlay(
    state: AccountMenuState,
    anchorBounds: Rect,
    avatarInitials: String?,
    onDismiss: () -> Unit,
    onAction: (AccountMenuAction) -> Unit,
) {
    HarnessAnchoredOverlay(anchorBounds, onDismiss, "account-menu") {
        AccountMenu(state = state, onAction = onAction, avatarInitials = avatarInitials)
    }
}

/** Identity header: account avatar, name, and the compressed connection line. */
@Composable
private fun AccountMenuIdentity(state: AccountMenuState, avatarInitials: String?) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(HarnessTokens.Control.MenuHeaderHeight)
                .padding(horizontal = HarnessTokens.Control.MenuItemPaddingX),
        horizontalArrangement = Arrangement.spacedBy(HarnessTokens.Control.MenuIconGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HarnessAvatar(avatarInitials, size = HarnessTokens.Control.MenuAvatar)
        Column(modifier = Modifier.weight(1f)) {
            HarnessText(state.identityLabel, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            HarnessText(
                listOfNotNull(state.modeLabel.takeIf { state.isLoggedIn }, state.connectionLabel)
                    .filter { it.isNotBlank() && it != state.identityLabel }
                    .distinct().joinToString(" · "),
                color = HarnessTokens.Colors.TextMuted,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
            )
        }
    }
}

/** Separator: a hairline with the Codex menu separator block margin. */
@Composable
private fun AccountMenuSeparator() {
    HorizontalDivider(
        color = HarnessTokens.Colors.BorderSubtle,
        modifier = Modifier.padding(vertical = HarnessTokens.Control.MenuSeparatorGutter),
    )
}

/** One compact single-line dropdown item: 16dp glyph, label, no subtitle row. */
@Composable
private fun AccountMenuRow(
    icon: HarnessIconName,
    title: String,
    testTag: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(HarnessTokens.Control.MenuItemHeight)
                .clip(RoundedCornerShape(HarnessTokens.Control.MenuItemRadius))
                .clickable(enabled = enabled, onClick = onClick)
                .semantics {
                    role = Role.Button
                    this.testTag = testTag
                }
                .padding(
                    horizontal = HarnessTokens.Control.MenuItemPaddingX,
                    vertical = HarnessTokens.Control.MenuItemPaddingY,
                ),
        horizontalArrangement = Arrangement.spacedBy(HarnessTokens.Control.MenuIconGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(HarnessTokens.Control.MenuLeadingWidth), contentAlignment = Alignment.Center) {
            HarnessIcon(
                icon,
                tint = if (enabled) HarnessTokens.Colors.TextMuted else HarnessTokens.Colors.TextDisabled,
                size = HarnessTokens.Control.MenuIcon,
            )
        }
        HarnessText(
            title,
            modifier = Modifier.weight(1f),
            color = if (enabled) HarnessTokens.Colors.Text else HarnessTokens.Colors.TextDisabled,
            maxLines = 1,
        )
    }
}

/**
 * The 探索 pin menu as an in-composition overlay: a full-surface dismiss layer plus the panel
 * anchored beside the 探索 button. It is a separate menu from the account chip dropdown; both draw
 * inside the shell composition so the off-screen capture records them.
 */
/** Window-coordinate placement of the 探索 popup, in PIXELS from the window origin (as Popup speaks). */
public data class ExploreMenuAnchor(val left: Int, val top: Int)

@Composable
private fun ExploreMenuOverlay(
    anchor: ExploreMenuAnchor,
    pinned: Set<RailDestination>,
    selected: PageId,
    onDismiss: () -> Unit,
    onOpenDestination: (PageId) -> Unit,
    onTogglePin: (RailDestination) -> Unit,
    onHoverChanged: (Boolean) -> Unit,
) {
    // A real Popup: Compose places it in window coordinates off the 探索 item, so no shell/root
    // offset arithmetic is needed and it can escape the shell's bounds.
    Popup(
        popupPositionProvider =
            remember(anchor) {
                object : PopupPositionProvider {
                    override fun calculatePosition(
                        anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize,
                    ): IntOffset = IntOffset(anchor.left, anchor.top)
                }
            },
        // Non-focusable: a focusable popup steals focus from the rail item, which drops its hover and
        // flickers the menu. Maintaining the hover is the owner's job, not the popup's.
        properties = PopupProperties(focusable = false),
        onDismissRequest = onDismiss,
    ) {
        ExploreMenu(
            pinned = pinned,
            selected = selected,
            onOpenDestination = onOpenDestination,
            onTogglePin = onTogglePin,
            onHoverChanged = onHoverChanged,
        )
    }
}

/**
 * The 探索 menu. Codex renders it as a destination list; each row is two controls: the destination
 * button (leading glyph + label) navigates, and the trailing pill button pins or unpins it to the
 * rail's second group. Geometry and shadow match the account dropdown, the same Codex menu primitive.
 */
@Composable
private fun ExploreMenu(
    pinned: Set<RailDestination>,
    selected: PageId,
    onOpenDestination: (PageId) -> Unit,
    onTogglePin: (RailDestination) -> Unit,
    onHoverChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(HarnessTokens.Control.ExploreMenuRadius)
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    LaunchedEffect(hovered) { onHoverChanged(hovered) }
    Column(
        modifier =
            modifier
                // Same Codex `--shadow-dropdown-overlay` as the account dropdown.
                .dropShadow(
                    shape = shape,
                    shadow =
                        Shadow(
                            radius = HarnessTokens.Control.MenuShadowBlur,
                            spread = HarnessTokens.Control.MenuShadowSpread,
                            offset = DpOffset(x = 0.dp, y = HarnessTokens.Control.MenuShadowOffsetY),
                            color = HarnessTokens.Control.MenuShadowColor,
                        ),
                )
                .width(HarnessTokens.Control.ExploreMenuWidth)
                .clip(shape)
                .background(HarnessTokens.Colors.SurfaceElevated)
                .padding(HarnessTokens.Control.MenuPadding)
                .hoverable(source)
                .semantics { testTag = "explore-menu" },
    ) {
        explorePinMenuItems(pinned).forEach { entry ->
            ExploreMenuRow(
                entry = entry,
                // Codex marks the row of the page that is open with `aria-current="page"` and a
                // half-strength ghost wash, so the menu shows where the user already is.
                current = entry.option.selects(selected),
                onOpenDestination = { onOpenDestination(entry.option.page) },
                onTogglePin = { onTogglePin(entry.option.destination) },
            )
        }
    }
}

/**
 * One 探索 row: the destination button plus the trailing pin control, matching the Codex
 * `data-explore-row` group (destination button + a 20px transparent pill pin button).
 */
@Composable
private fun ExploreMenuRow(
    entry: PinMenuItem,
    /** The destination of this row is the page currently open. */
    current: Boolean = false,
    onOpenDestination: () -> Unit,
    onTogglePin: () -> Unit,
) {
    // Codex draws the 探索 row as `rounded-xl` (12px), matching the menu panel's 20px corner family.
    val shape = RoundedCornerShape(HarnessTokens.Corners.Large)
    // One interactive surface for the whole row: the ripple and the hover fill span the trailing pin
    // too, so the row reads as a single control instead of a ripple cut off at the pin.
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(HarnessTokens.Control.MenuItemHeight)
                .clip(shape)
                .background(
                    when {
                        hovered -> HarnessTokens.Colors.SurfaceSelected
                        current -> HarnessTokens.Colors.CurrentRowBackground
                        else -> Color.Transparent
                    },
                )
                .then(if (hovered) Modifier.border(1.dp, HarnessTokens.Colors.Border, shape) else Modifier)
                .clickable(
                    interactionSource = source,
                    indication = LocalIndication.current,
                    onClick = onOpenDestination,
                )
                .semantics {
                    role = Role.Button
                    selected = current
                    testTag = "explore-destination-${entry.option.id}"
                },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = HarnessTokens.Control.MenuItemPaddingX),
            horizontalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Two),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HarnessIcon(entry.option.icon, tint = HarnessTokens.Colors.Text)
            HarnessText(entry.option.label, maxLines = 1)
        }
        // Trailing pin control: nested on top of the row surface so its tap toggles the pin instead
        // of navigating, while the row's ripple still runs underneath it.
        val pinSource = remember { MutableInteractionSource() }
        val pinHovered by pinSource.collectIsHoveredAsState()
        Box(
            modifier =
                Modifier
                    .padding(end = HarnessTokens.Spacing.One)
                    .size(HarnessTokens.Control.Icon)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(if (pinHovered) HarnessTokens.Colors.SurfaceSelected else Color.Transparent)
                    .hoverable(pinSource)
                    .clickable(onClick = onTogglePin)
                    .semantics {
                        role = Role.Button
                        contentDescription = if (entry.pinned) "从侧边栏取消置顶" else "固定到侧边栏"
                        testTag = "explore-pin-${entry.option.id}"
                    },
            contentAlignment = Alignment.Center,
        ) {
            HarnessIcon(
                name = if (entry.pinned) HarnessIconName.PushpinFilled else HarnessIconName.Pushpin,
                // Codex draws the pinned (filled) glyph in the tertiary grey and the unpinned
                // (outline) glyph in the stronger secondary grey.
                tint = if (entry.pinned) HarnessTokens.Colors.TextDisabled else HarnessTokens.Colors.TextSecondary,
                size = HarnessTokens.Control.NavigatorIcon,
            )
        }
    }
}

@Composable
private fun RailGroupDivider() {
    Box(
        modifier =
            Modifier
                .width(HarnessTokens.Control.RailDividerWidth)
                .height(1.dp)
                // The bundle names the group rule `border-default`, and the capture reads as the
                // stronger of the two rail rules (delta 16 over the rail, vs 9 for the end hairline).
                .background(HarnessTokens.Colors.Border)
                .semantics { testTag = "rail-divider" },
    )
}

@Composable
private fun RailNavItem(
    item: AppShellRailItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tooltipScope: Any? = Unit,
    tooltipSuppressed: Boolean = false,
    /**
     * 探索 opens its pin menu on hover and never draws a tip; every other slot keeps the hover tip.
     */
    hoverOpensMenu: Boolean = false,
    onHoverChanged: ((Boolean) -> Unit)? = null,
    /** True while this item keeps a rail popover open (探索 menu or navigator preview). */
    popoverActive: Boolean = false,
) {
    val selected = item.selected
    val hoverSource = remember { MutableInteractionSource() }
    val hovered by hoverSource.collectIsHoveredAsState()
    val content: @Composable (Modifier) -> Unit = { extra ->
        Box(
            modifier =
                modifier
                    .then(extra)
                    .fillMaxWidth()
                    .height(HarnessTokens.Control.RailItem)
                    .clip(RoundedCornerShape(HarnessTokens.Control.RailItemRadius))
                    // Codex paints a 234 hover fill on the whole item and keeps it while the pointer is
                    // on the popover the item opened; the ripple stays (the click is a real Compose one).
                    .background(if (selected || hovered || popoverActive) HarnessTokens.Colors.SurfaceSelected else Color.Transparent)
                    .clickable(enabled = item.enabled, onClick = onClick)
                    .semantics {
                        role = Role.Tab
                        testTag = "rail-" + item.id
                    },
            contentAlignment = Alignment.Center,
        ) {
            HarnessIcon(
                // Codex draws the solid glyph for the selected destination and the outline for the rest.
                name = if (selected) item.icon.filledVariant() else item.icon,
                tint =
                    when {
                        // Codex promotes the glyph to full ink while the item is selected, hovered,
                        // or keeping its own popover open (the 探索 menu, a navigator preview).
                        selected || hovered || popoverActive -> HarnessTokens.Colors.Text
                        // At rest every unselected rail glyph keeps the disabled-weight grey.
                        else -> HarnessTokens.Colors.TextDisabled
                    },
                glyphScale = HarnessTokens.Control.RailIconScale,
                strokeRatio = HarnessTokens.Control.RailIconStrokeRatio,
            )
        }
    }
    if (onHoverChanged != null) LaunchedEffect(hovered) { onHoverChanged(hovered) }
    if (hoverOpensMenu) {
        content(Modifier.hoverable(hoverSource, enabled = item.enabled))
        return
    }
    HarnessTooltipTrigger(
        text = item.label, enabled = item.enabled, scope = tooltipScope,
        popupSuppressed = tooltipSuppressed, popupTestTag = "rail-tooltip-${item.id}",
        // The rail is a left column, so its tips open to the right of the item, not above it, and
        // the rail tip is the inverted dark pill the Codex rail draws.
        side = HarnessPopoverSide.End,
        railPill = true,
    ) { triggerModifier ->
        content(triggerModifier.then(Modifier.hoverable(hoverSource, enabled = item.enabled)))
    }
}

@Composable
private fun RailAccountAvatar(
    item: AppShellRailItem,
    onClick: () -> Unit,
    expanded: Boolean = false,
    modifier: Modifier = Modifier,
) {
    // Codex anchors the account chip at the rail bottom and fills it with the account colour and
    // initials; without a colour source yet we use the measured reference fill and fall back to the
    // generic user glyph when the caller has no label to derive initials from.
    val initials = item.initials?.takeIf { it.isNotBlank() }
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    Box(
        modifier = modifier.size(HarnessTokens.Control.RailItem)
            .clip(RoundedCornerShape(HarnessTokens.Control.RailItemRadius))
            .background(if (expanded || hovered) HarnessTokens.Colors.SurfaceSelected else Color.Transparent)
            .hoverable(interactionSource)
            .clickable(enabled = item.enabled, interactionSource = interactionSource, indication = null, onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = item.label
                stateDescription = if (expanded) "已展开" else "已收起"
                testTag = "rail-" + item.id
            },
        contentAlignment = Alignment.Center,
    ) {
        HarnessAvatar(
            initials,
            modifier = if (item.selected) Modifier.border(1.dp, HarnessTokens.Colors.BorderStrong, CircleShape) else Modifier,
        )
    }
}
/** Dimming behind the floating navigator; the Codex window dims its surface the same way. */
private val NavigatorScrim: Color = Color.Black.copy(alpha = 0.32f)

/**
 * Text column of a navigator row: the row inset plus an icon box and its gap, so a project's children
 * align under the project name and a recent title or a section label aligns under a project glyph.
 */
private val NavigatorTextIndent: Dp =
    HarnessTokens.Control.NavigatorPaddingX + HarnessTokens.Control.NavigatorIcon + HarnessTokens.Spacing.Two

@Composable
private fun WorkspaceNavigator(
    state: AppShellState,
    navigator: WorkspaceNavigatorState,
    projectNodes: List<WorkspaceProjectNode>,
    recentTasks: List<RecentTaskItem>,
    onNewTask: () -> Unit,
    searchOpen: Boolean,
    onToggleSearch: () -> Unit,
    onToggleProject: (String) -> Unit,
    onSelectNode: (WorkspaceItemNode) -> Unit,
    onSelectRecent: (RecentTaskItem) -> Unit,
    onOpenProjectContext: ((String) -> Unit)?,
    onNewProject: (() -> Unit)?,
    onOpenProjects: (() -> Unit)?,
) {
    // The bell swaps the sidebar body for Codex's in-place activity inbox.
    var activityOpen by remember { mutableStateOf(false) }
    // Search lives in the command menu overlay, so the sidebar behind it stays unfiltered.
    val query = if (searchOpen || activityOpen) "" else navigator.searchQuery.trim()
    // Sections collapse from their header; a search always opens them so filtered rows stay visible.
    var projectsCollapsed by remember { mutableStateOf(false) }
    var recentsCollapsed by remember { mutableStateOf(false) }
    val projectsOpen = query.isNotEmpty() || !projectsCollapsed
    val recentsOpen = query.isNotEmpty() || !recentsCollapsed
    // Keep New chat/search fixed above one scrolling project/conversation list.
    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        SidebarHeader(
            navigator = navigator,
            activityOpen = activityOpen,
            onToggleActivity = { activityOpen = !activityOpen },
            onToggleSearch = onToggleSearch,
        )
        if (activityOpen) {
            // Codex keeps the New chat row in the activity view: header 52 + the 38px row block, so
            // the feed starts at the same y as the project sections.
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            start = HarnessTokens.Control.NavigatorPaddingX,
                            end = HarnessTokens.Control.NavigatorPaddingX,
                            bottom = HarnessTokens.Spacing.Two,
                        ),
            ) {
                SidebarNewChatRow(onClick = onNewTask)
            }
            NavigatorActivityView(
                recentTasks = recentTasks,
                projectNodes = projectNodes,
                selectedThreadId = state.navigation.selectedThreadId,
                onSelectRecent = onSelectRecent,
            )
        } else {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            start = HarnessTokens.Control.NavigatorPaddingX,
                            end = HarnessTokens.Control.NavigatorPaddingX,
                            // Codex leaves 8px between the New chat block and the first section.
                            bottom = HarnessTokens.Spacing.Two,
                        ),
                // Codex lays the sidebar out with `gap-px`: 30px rows sit 1px apart.
                verticalArrangement = Arrangement.spacedBy(HarnessTokens.Control.NavigatorRowGap),
            ) {
                SidebarNewChatRow(onClick = onNewTask)
            }
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        // Section rows stop 11px short of the pane edge: Codex reserves that strip for
                        // its scrollbar, while the header and New chat row keep the full width.
                        .padding(start = HarnessTokens.Control.NavigatorPaddingX, end = HarnessTokens.Control.NavigatorPaddingX + 11.dp)
                        .verticalScroll(rememberScrollState()),
            ) {
                val visibleProjects =
                    projectNodes.filter { project ->
                        query.isEmpty() ||
                            project.name.contains(query, ignoreCase = true) ||
                            project.children.any { child -> child.title.contains(query, ignoreCase = true) }
                    }
                val visibleRecentTasks =
                    recentTasks.filter { task ->
                        query.isEmpty() || task.title.contains(query, ignoreCase = true)
                    }
                // One 项目 section: pinned projects stay on top inside it, so the Harness-only 已固定
                // level never renders.
                val projects = visibleProjects.sortedByDescending { it.pinned }
                // The Codex list has 4px of top and 8px of bottom breathing room; consecutive section
                // blocks are 16px apart.
                Spacer(Modifier.height(HarnessTokens.Spacing.One))
                if (projects.isNotEmpty()) {
                    NavigatorSectionHeader(
                        label = "项目",
                        expanded = projectsOpen,
                        onToggle = { projectsCollapsed = !projectsCollapsed },
                    ) {
                        NavigatorHoverAction(
                            icon = HarnessIconName.More,
                            contentDescription = "项目选项",
                            onClick = { onOpenProjects?.invoke() },
                            size = HarnessTokens.Control.WorkspaceNavigatorSectionAction,
                            iconSize = HarnessTokens.Control.NavigatorIcon,
                        )
                        NavigatorHoverAction(
                            icon = HarnessIconName.Plus,
                            contentDescription = "新建项目",
                            onClick = { onNewProject?.invoke() },
                            size = HarnessTokens.Control.WorkspaceNavigatorSectionAction,
                            iconSize = HarnessTokens.Control.NavigatorIcon,
                        )
                    }
                    if (projectsOpen) {
                        Spacer(Modifier.height(HarnessTokens.Spacing.One))
                        Column(verticalArrangement = Arrangement.spacedBy(HarnessTokens.Control.NavigatorRowGap)) {
                            projects.forEach { node ->
                                // When the query matches only a child, keep just the matching children so
                                // the filtered tree does not show non-matching rows (Codex search).
                                val projectMatches = query.isEmpty() || node.name.contains(query, ignoreCase = true)
                                ProjectRow(
                                    node =
                                        if (projectMatches) {
                                            node
                                        } else {
                                            node.copy(children = node.children.filter { it.title.contains(query, ignoreCase = true) })
                                        },
                                    expanded = query.isNotEmpty() || node.projectId in state.navigation.expandedProjectIds,
                                    selectedThreadId = state.navigation.selectedThreadId,
                                    onToggle = onToggleProject,
                                    onSelectNode = onSelectNode,
                                    onOpenContext = onOpenProjectContext,
                                    onNewTask = onNewTask,
                                )
                            }
                        }
                    }
                }
                if (projects.isNotEmpty() && visibleRecentTasks.isNotEmpty()) {
                    Spacer(Modifier.height(HarnessTokens.Spacing.Four))
                }
                if (visibleRecentTasks.isNotEmpty()) {
                    NavigatorSectionHeader(
                        label = "最近",
                        expanded = recentsOpen,
                        onToggle = { recentsCollapsed = !recentsCollapsed },
                    ) {
                        // The live menu's rows were not measured; keep the measured affordance without
                        // inventing actions.
                        NavigatorHoverAction(
                            icon = HarnessIconName.More,
                            contentDescription = "最近会话选项",
                            onClick = {},
                            size = HarnessTokens.Control.WorkspaceNavigatorSectionAction,
                            iconSize = HarnessTokens.Control.NavigatorIcon,
                        )
                        NavigatorHoverAction(
                            icon = HarnessIconName.NewChat,
                            contentDescription = "新建会话",
                            onClick = onNewTask,
                            size = HarnessTokens.Control.WorkspaceNavigatorSectionAction,
                            iconSize = HarnessTokens.Control.NavigatorIcon,
                        )
                    }
                    if (recentsOpen) {
                        Spacer(Modifier.height(HarnessTokens.Spacing.One))
                        Column(verticalArrangement = Arrangement.spacedBy(HarnessTokens.Control.NavigatorRowGap)) {
                            visibleRecentTasks.forEach { task ->
                                RecentTaskRow(
                                    task = task,
                                    selected = state.navigation.selectedThreadId == task.id,
                                    onClick = { onSelectRecent(task) },
                                )
                            }
                        }
                    }
                }
                if (query.isNotEmpty() && projects.isEmpty() && visibleRecentTasks.isEmpty()) {
                    SidebarHint("没有找到匹配的项目或会话")
                }
                Spacer(Modifier.height(HarnessTokens.Spacing.Two))
            }
        }
    }
}

/**
 * Dedicated page navigators. Each owns its own state and renders its own rows, so a Settings or
 * Plugins page never shows the Workspace project tree, workspace search or recent-task state. They
 * are deliberately minimal placeholders until each page's navigator gets its own design pass.
 */
@Composable
private fun SettingsNavigator(
    state: SettingsNavigatorState,
    onSearch: (String) -> Unit,
    onSelect: (SettingsNavItem) -> Unit,
) {
    // The Codex Settings page owns a `sidebar-navigation`, but its 2026-10-06 capture shows only a
    // title, a search field and a grouped row list: no subtitle, no "back to app" row and no collapse
    // control. Those entries stay in the native/title area, so this navigator draws none of them.
    Column(modifier = Modifier.fillMaxSize()) {
        SettingsNavigatorTitle()
        SettingsNavigatorSearch(value = state.query, onValueChange = onSearch)
        SettingsNavigatorGroups(state = state, onSelect = onSelect)
    }
}

/** `settings.nav.title`: the Settings navigator title strip. The capture draws no action in this row. */
@Composable
private fun SettingsNavigatorTitle() {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(HarnessTokens.Control.SettingsHeader)
                .padding(horizontal = HarnessTokens.Spacing.Four),
        contentAlignment = Alignment.CenterStart,
    ) {
        HarnessText(
            "设置",
            color = HarnessTokens.Colors.Text,
            style = TextStyle(fontSize = HarnessTokens.Text.Lg, fontWeight = FontWeight.SemiBold),
            maxLines = 1,
        )
    }
}

/** `settings.search.*`: the visible search field the Settings capture shows under the title. */
@Composable
private fun SettingsNavigatorSearch(value: String, onValueChange: (String) -> Unit) {
    HarnessSearchField(
        value = value,
        onValueChange = onValueChange,
        placeholder = "搜索设置",
        accessibilityLabel = "搜索设置",
        modifier = Modifier.fillMaxWidth().padding(horizontal = HarnessTokens.Control.NavigatorPaddingX),
        testTag = "settings-navigator-search",
    )
}

/**
 * The grouped Settings rows. Groups and rows come from [SettingsNavigatorModel], so the order, the
 * headings and the filter are data, not layout code. A trimmed query drops non-matching rows and any
 * heading left over an empty run.
 */
@Composable
private fun ColumnScope.SettingsNavigatorGroups(state: SettingsNavigatorState, onSelect: (SettingsNavItem) -> Unit) {
    val groups = settingsNavigatorGroups(state)
    Column(
        modifier =
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = HarnessTokens.Control.NavigatorPaddingX),
    ) {
        Spacer(Modifier.height(HarnessTokens.Spacing.Two))
        if (groups.isEmpty()) {
            SidebarHint("没有匹配的设置")
        } else {
            groups.forEachIndexed { index, group ->
                if (index > 0) {
                    Spacer(Modifier.height(HarnessTokens.Spacing.Three))
                }
                SettingsGroupHeading(group.heading)
                Spacer(Modifier.height(HarnessTokens.Control.NavigatorRowGap))
                Column(verticalArrangement = Arrangement.spacedBy(HarnessTokens.Control.NavigatorRowGap)) {
                    group.items.forEach { item ->
                        SettingsNavigatorRow(item = item, selected = item.slug == state.selectedSlug, onSelect = { onSelect(item) })
                    }
                }
            }
        }
    }
}

/** One Codex section heading: a muted label in its own 36px box. */
@Composable
private fun SettingsGroupHeading(text: String) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(HarnessTokens.Control.SettingsGroupHeading)
                .padding(start = HarnessTokens.Spacing.Two),
        contentAlignment = Alignment.CenterStart,
    ) {
        HarnessText(
            text,
            color = HarnessTokens.Colors.TextMuted,
            style = TextStyle(fontSize = HarnessTokens.Text.Sm),
            maxLines = 1,
        )
    }
}

/** One Settings row: 30px box, 16px leading glyph, optional trailing external-link arrow. */
@Composable
private fun SettingsNavigatorRow(
    item: SettingsNavItem,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(HarnessTokens.Control.NavRow)
                .clip(RoundedCornerShape(HarnessTokens.Control.SettingsRowRadius))
                .background(if (selected) HarnessTokens.Colors.SurfaceSelected else Color.Transparent)
                .clickable(onClick = onSelect)
                .semantics {
                    role = Role.Tab
                    this.selected = selected
                    testTag = "settings-row-" + item.slug
                }
                .padding(horizontal = HarnessTokens.Spacing.Two),
        horizontalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Two),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsNavGlyphIcon(
            item.icon,
            tint = HarnessTokens.Colors.Text,
            size = HarnessTokens.Control.NavigatorIcon,
        )
        HarnessText(item.label, modifier = Modifier.weight(1f), color = HarnessTokens.Colors.Text, maxLines = 1)
        if (item.external) {
            HarnessIcon(HarnessIconName.ExternalLink, tint = HarnessTokens.Colors.TextMuted, size = HarnessTokens.Control.NavigatorIcon)
        }
    }
}


@Composable
private fun PluginsNavigator(state: PluginsNavigatorState, onEnabledFilterChange: ((Boolean) -> Unit)? = null) {
    NavigatorPane(title = "插件", subtitle = "已安装插件") {
        HarnessRow(
            title = "全部插件",
            state = HarnessRowState(selected = !state.enabledOnly, enabled = onEnabledFilterChange != null),
            onClick = onEnabledFilterChange?.let { change -> { change(false) } },
            testTag = "plugins-filter-all",
        )
        HarnessRow(
            title = "仅启用的插件",
            state = HarnessRowState(selected = state.enabledOnly, enabled = onEnabledFilterChange != null),
            onClick = onEnabledFilterChange?.let { change -> { change(true) } },
            testTag = "plugins-filter-enabled",
        )
        if (state.query.isNotBlank()) {
            SidebarHint("过滤：" + state.query)
        }
    }
}


@Composable
private fun ScheduledTasksNavigator(
    state: im.hikaru.harness.client.scheduled.ScheduledTasksState,
    onSearch: (String) -> Unit,
    onToggleTask: (String) -> Unit,
    onShowOlder: (String) -> Unit,
    onRetry: () -> Unit,
    onNewTask: () -> Unit,
    onOpenRun: (im.hikaru.harness.client.scheduled.ScheduledTask, im.hikaru.harness.client.scheduled.ScheduledRun) -> Unit,
) {
    var searchOpen by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.One),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(HarnessTokens.Control.NavigatorHeader).padding(horizontal = HarnessTokens.Control.NavigatorPaddingX),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HarnessText("定时任务", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, maxLines = 1)
            HarnessIconButton(contentDescription = "搜索定时任务", onClick = { searchOpen = !searchOpen }, modifier = Modifier.semantics { testTag = "scheduled-toggle-search" }) {
                HarnessIcon(HarnessIconName.Search, tint = HarnessTokens.Colors.TextMuted)
            }
        }
        if (searchOpen || state.query.isNotEmpty()) ScheduledSearchRow(state.query, onSearch)
        HarnessButton(
            text = "新建任务",
            onClick = onNewTask,
            modifier = Modifier.fillMaxWidth().padding(horizontal = HarnessTokens.Control.NavigatorPaddingX),
            variant = HarnessButtonVariant.Ghost,
            size = HarnessControlSize.Compact,
            testTag = "scheduled-new-task",
        )
        HorizontalDivider(color = HarnessTokens.Colors.BorderSubtle)
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = HarnessTokens.Control.NavigatorPaddingX),
            verticalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.One),
        ) {
            SectionLabel("即将执行")
            when (state.loadState) {
                im.hikaru.harness.client.scheduled.ScheduledLoadState.Loading -> SidebarHint("正在加载定时任务…")
                im.hikaru.harness.client.scheduled.ScheduledLoadState.Error -> {
                    HarnessRow(
                        title = state.loadError ?: "定时任务加载失败",
                        state = HarnessRowState(control = HarnessControlState.Error),
                        onRetry = onRetry,
                        retryLabel = "重试",
                        testTag = "scheduled-retry",
                    )
                }
                im.hikaru.harness.client.scheduled.ScheduledLoadState.Ready -> {
                    if (state.visibleTasks.isEmpty()) {
                        SidebarHint(if (state.query.isBlank()) "暂无定时任务" else "没有匹配的定时任务")
                    }
                    state.visibleTasks.forEach { task ->
                        val expanded = task.id in state.expandedTaskIds
                        HarnessRow(
                            title = task.title,
                            description = task.status.label,
                            state = HarnessRowState(selected = task.id == state.selectedTaskId, expanded = expanded),
                            onClick = { onToggleTask(task.id) },
                            onExpandedChange = { onToggleTask(task.id) },
                            leading = { HarnessIcon(HarnessIconName.Clock, tint = HarnessTokens.Colors.TextMuted) },
                            testTag = "scheduled-task-" + task.id,
                        ) {
                            state.visibleRuns(task).forEach { run ->
                                HarnessRow(
                                    title = run.summary ?: run.status.name,
                                    description = if (run.chatRef.isNullOrBlank()) "此运行没有关联会话" else run.id,
                                    state = HarnessRowState(depth = 1),
                                    onClick = if (run.chatRef.isNullOrBlank()) null else ({ onOpenRun(task, run) }),
                                    leading = { HarnessIcon(HarnessIconName.ChevronRight, tint = HarnessTokens.Colors.TextMuted) },
                                    testTag = "scheduled-run-" + run.id,
                                )
                            }
                            if (state.hasOlderRuns(task)) {
                                HarnessButton(
                                    text = "显示更早运行",
                                    onClick = { onShowOlder(task.id) },
                                    variant = HarnessButtonVariant.Ghost,
                                    size = HarnessControlSize.Compact,
                                    testTag = "scheduled-older-" + task.id,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduledSearchRow(value: String, onValueChange: (String) -> Unit) {
    HarnessSearchField(
        value = value,
        onValueChange = onValueChange,
        placeholder = "搜索任务",
        accessibilityLabel = "搜索定时任务",
        modifier = Modifier.fillMaxWidth().padding(horizontal = HarnessTokens.Control.NavigatorPaddingX),
        height = HarnessTokens.Control.NavRow,
        cornerRadius = HarnessTokens.Control.NavigatorRowRadius,
        testTag = "scheduled-search",
    )
}

/** Shared frame for the dedicated navigators: a page-owned header, a rule and a scrollable body. */
@Composable
private fun NavigatorPane(
    title: String,
    subtitle: String,
    headerAction: (@Composable () -> Unit)? = null,
    headerExtra: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = HarnessTokens.Spacing.Two, vertical = HarnessTokens.Spacing.Two),
        verticalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.One),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = HarnessTokens.Spacing.Two, top = HarnessTokens.Spacing.Two),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                HarnessText(title, color = HarnessTokens.Colors.Text, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                HarnessText(subtitle, color = HarnessTokens.Colors.TextMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
            headerAction?.invoke()
        }
        headerExtra?.invoke()
        HorizontalDivider(color = HarnessTokens.Colors.BorderSubtle, modifier = Modifier.padding(vertical = HarnessTokens.Spacing.One))
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.One),
        ) {
            content()
        }
    }
}

@Composable
private fun NavigatorRow(
    label: String,
    selected: Boolean,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(HarnessTokens.Control.Row)
                .clip(RoundedCornerShape(HarnessTokens.Corners.Medium))
                .background(if (selected) HarnessTokens.Colors.SurfaceSelected else Color.Transparent)
                .semantics { role = Role.Tab }
                .padding(horizontal = HarnessTokens.Spacing.Two),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HarnessText(
            label,
            color = if (selected) HarnessTokens.Colors.Text else HarnessTokens.Colors.TextMuted,
            maxLines = 1,
        )
    }
}

@Composable
private fun SidebarNewChatRow(
    onClick: () -> Unit,
    label: String = "新聊天",
    icon: HarnessIconName = HarnessIconName.NewChat,
    selected: Boolean = false,
    testTag: String = "new-task",
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(HarnessTokens.Control.NavRow)
                .clip(RoundedCornerShape(HarnessTokens.Control.WorkspaceNavigatorRowRadius))
                .background(if (selected || hovered) HarnessTokens.Colors.NavigatorRowFill else Color.Transparent)
                .hoverable(interactionSource)
                .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
                .semantics {
                    role = Role.Button
                    this.testTag = testTag
                }
                .padding(horizontal = HarnessTokens.Spacing.Two),
        horizontalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Two),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HarnessIcon(icon, tint = HarnessTokens.Colors.NavigatorRowText, size = HarnessTokens.Control.NavigatorIcon)
        HarnessText(
            label,
            color = HarnessTokens.Colors.NavigatorRowText,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
        )
    }
}

@Composable
private fun SidebarHeader(
    navigator: WorkspaceNavigatorState,
    activityOpen: Boolean,
    onToggleActivity: () -> Unit,
    onToggleSearch: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(HarnessTokens.Control.WorkspaceNavigatorHeader)
                // Codex's workspace button sits 8px inside the header's own 8px inset, so the name
                // aligns with the New chat and project icons at x=16.
                .padding(start = HarnessTokens.Spacing.Four, end = HarnessTokens.Spacing.Three)
                .semantics { testTag = "workspace-navigator-header" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Harness deliberately has no workspace switcher: Codex's 18sp/85% name stays, but its
        // dropdown chevron and menu are omitted, so the name is a plain label.
        HarnessText(
            navigator.workspaceLabel,
            modifier =
                Modifier
                    .weight(1f)
                    .semantics { testTag = "workspace-label" },
            color = HarnessTokens.Colors.NavigatorRowText,
            style =
                MaterialTheme.typography.titleLarge.copy(
                    fontSize = HarnessTokens.Text.Lg,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
            maxLines = 1,
        )
        // Codex moved search out of the row list into the header; both actions are 28x28 with a 4px gap.
        Row(
            horizontalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.One),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The bell toggles the in-place activity inbox; Codex keeps it blue while it is open.
            NavigatorHoverAction(
                icon = HarnessIconName.Bell,
                contentDescription = if (activityOpen) "关闭活动视图" else "查看活动",
                onClick = onToggleActivity,
                size = HarnessTokens.Control.WorkspaceNavigatorHeaderAction,
                iconSize = HarnessTokens.Control.NavigatorIcon,
                selected = activityOpen,
                testTag = "workspace-activity-toggle",
            )
            // Opening the command menu does not select the magnifier in Codex; it only closes on Esc
            // or a result.
            NavigatorHoverAction(
                icon = HarnessIconName.Search,
                contentDescription = "搜索会话",
                onClick = onToggleSearch,
                size = HarnessTokens.Control.WorkspaceNavigatorHeaderAction,
                iconSize = HarnessTokens.Control.NavigatorIcon,
                testTag = "workspace-search-toggle",
            )
        }
    }
}

/**
 * Section header with the Codex 30px row, collapsible label + chevron, and hover-only actions.
 * [actions] is only composed while the header is hovered.
 */
@Composable
private fun NavigatorSectionHeader(
    label: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(HarnessTokens.Control.NavRow)
                .hoverable(interactionSource)
                // Codex's label button starts 4px left of the text column and is 25px tall inside
                // the 30px row, so its 4px text inset lands the label on the shared column.
                .padding(start = HarnessTokens.Spacing.One, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier =
                Modifier
                    .weight(1f)
                    .height(25.dp)
                    .clip(RoundedCornerShape(HarnessTokens.Control.WorkspaceNavigatorActionRadius))
                    .clickable(interactionSource = interactionSource, indication = null, onClick = onToggle)
                    .padding(horizontal = HarnessTokens.Spacing.One)
                    .semantics {
                        role = Role.Button
                        stateDescription = if (expanded) "已展开" else "已收起"
                        testTag = "section-" + label
                    },
            horizontalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.One),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HarnessText(
                label,
                color = HarnessTokens.Colors.ChromeIcon,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
            HarnessIcon(
                if (expanded) HarnessIconName.ChevronDown else HarnessIconName.ChevronRight,
                tint = HarnessTokens.Colors.ChromeIcon,
                size = HarnessTokens.Control.NavigatorChevron,
            )
        }
        if (hovered) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.One),
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
    }
}

/**
 * Small hover action used by the workspace navigator (header, section headers and rows). The
 * background is the measured 5.5% wash, not the page-wide `SurfaceSelected`.
 */
@Composable
private fun NavigatorHoverAction(
    icon: HarnessIconName,
    contentDescription: String,
    onClick: () -> Unit,
    size: Dp,
    iconSize: Dp,
    selected: Boolean = false,
    testTag: String = contentDescription,
    // Every navigator icon button carries the same hint Codex shows on hover; pass null to opt out.
    tooltip: String? = contentDescription,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    // Selected header actions (the activity bell) use the measured accent wash and accent ink; a
    // plain hover keeps the neutral 5.5% row wash.
    val fill =
        when {
            selected -> HarnessTokens.Colors.Accent.copy(alpha = 0.13f)
            hovered -> HarnessTokens.Colors.NavigatorRowFill
            else -> Color.Transparent
        }
    HarnessTooltipTrigger(text = tooltip, popupTestTag = "navigator-tooltip-" + testTag) { tip ->
        Box(
            modifier =
                Modifier
                    .size(size)
                    .clip(RoundedCornerShape(HarnessTokens.Control.WorkspaceNavigatorActionRadius))
                    .then(tip)
                    .background(fill)
                    .hoverable(interactionSource)
                    .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
                    .semantics {
                        role = Role.Button
                        this.contentDescription = contentDescription
                        this.testTag = testTag
                    },
            contentAlignment = Alignment.Center,
        ) {
            HarnessIcon(
                icon,
                tint = if (selected) HarnessTokens.Colors.Accent else HarnessTokens.Colors.ChromeIcon,
                size = iconSize,
            )
        }
    }
}

/**
 * Codex's in-sidebar activity inbox, shown when the header bell is active. It replaces the project
 * list and recents rather than opening a popover while keeping the New chat row above; 优先 lists
 * unread sessions and the rest of the list stays a chronological feed.
 */
@Composable
private fun ColumnScope.NavigatorActivityView(
    recentTasks: List<RecentTaskItem>,
    projectNodes: List<WorkspaceProjectNode>,
    selectedThreadId: String?,
    onSelectRecent: (RecentTaskItem) -> Unit,
) {
    val navigationActions = LocalConversationNavigationActions.current
    val menuState = remember { NavigatorActivityMenuState() }
    val visibleTasks = if (menuState.showPinned) recentTasks else recentTasks.filter { !it.pinned }
    val priority = visibleTasks.filter { it.unread }
    val showRecents = visibleTasks.isNotEmpty()
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = HarnessTokens.Control.NavigatorPaddingX)
                .verticalScroll(rememberScrollState())
                .semantics { testTag = "workspace-activity-view" },
    ) {
        Spacer(Modifier.height(HarnessTokens.Spacing.One))
        if (menuState.showPriority) {
            NavigatorActivitySectionHeader("优先级") {
                NavigatorActivityMenuTrigger(menuState, navigationActions, recentTasks)
            }
            if (priority.isEmpty()) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            // Codex's placeholder is 37px tall: 8px padding around a 21px line.
                            .padding(horizontal = HarnessTokens.Spacing.Two, vertical = HarnessTokens.Spacing.Two),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    HarnessText(
                        "暂无未读会话",
                        color = HarnessTokens.Colors.ChromeIcon,
                        style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 21.sp),
                        maxLines = 1,
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(HarnessTokens.Control.NavigatorRowGap)) {
                    priority.take(5).forEach { task ->
                        NavigatorActivityRow(
                            task = task,
                            projectName = activityProjectName(task.id, projectNodes),
                            selected = selectedThreadId == task.id,
                            onClick = { onSelectRecent(task) },
                        )
                    }
                }
            }
        }
        if (showRecents) {
            // Codex moves the overflow trigger onto the first section that remains visible; it only
            // adds the 16px group gap when 优先级 is still above.
            if (menuState.showPriority) Spacer(Modifier.height(HarnessTokens.Spacing.Four))
            NavigatorActivitySectionHeader("最近") {
                if (!menuState.showPriority) NavigatorActivityMenuTrigger(menuState, navigationActions, recentTasks)
            }
            Column(verticalArrangement = Arrangement.spacedBy(HarnessTokens.Control.NavigatorRowGap)) {
                visibleTasks.forEach { task ->
                    NavigatorActivityRow(
                        task = task,
                        projectName = activityProjectName(task.id, projectNodes),
                        selected = selectedThreadId == task.id,
                        onClick = { onSelectRecent(task) },
                    )
                }
            }
        }
        Spacer(Modifier.height(HarnessTokens.Spacing.Two))
    }
}

/** Activity-view display switches and menu visibility, owned by the sidebar and reset by 恢复默认设置. */
private class NavigatorActivityMenuState {
    var open by mutableStateOf(false)
    var showPriority by mutableStateOf(true)
    var showPinned by mutableStateOf(true)
    // Codex's 定时任务 switch stays in the menu; Harness has no scheduled-activity rows yet, so the
    // preference is retained without a section to gate.
    var showScheduled by mutableStateOf(true)

    fun restoreDefaults() {
        showPriority = true
        showPinned = true
        showScheduled = true
    }
}

/** The activity overflow button and its measured 172px menu card. */
@Composable
private fun NavigatorActivityMenuTrigger(
    state: NavigatorActivityMenuState,
    navigationActions: ConversationNavigationActions,
    recentTasks: List<RecentTaskItem>,
) {
    HarnessPopover(
        expanded = state.open,
        onDismissRequest = { state.open = false },
        onExpandedChange = { state.open = it },
        side = HarnessPopoverSide.Bottom,
        align = HarnessPopoverAlign.Start,
        // Codex leaves 2px between the 24px trigger and the menu card.
        sideOffset = 2.dp,
        popupWidth = 172.dp,
        popupShape = 20.dp,
        popupBorderWidth = 0.dp,
        popupShadow = HarnessTokens.Spacing.Two,
        popupScrollable = false,
        anchor = { toggle ->
            NavigatorHoverAction(
                icon = HarnessIconName.More,
                contentDescription = "活动视图选项",
                onClick = toggle,
                size = HarnessTokens.Control.WorkspaceNavigatorSectionAction,
                iconSize = HarnessTokens.Control.NavigatorIcon,
                testTag = "workspace-activity-menu",
            )
        },
    ) {
        NavigatorActivityMenu(state, navigationActions, recentTasks)
    }
}

/**
 * Codex's activity-view settings: a 显示 group of section switches, a separator, and the two bulk
 * chat actions.
 */
@Composable
private fun NavigatorActivityMenu(
    state: NavigatorActivityMenuState,
    navigationActions: ConversationNavigationActions,
    recentTasks: List<RecentTaskItem>,
) {
    val visibleTasks = if (state.showPinned) recentTasks else recentTasks.filter { !it.pinned }
    val unreadIds = visibleTasks.filter { it.unread }.map { it.id }
    val archiveIds = visibleTasks.map { it.id }
    Column(modifier = Modifier.padding(HarnessTokens.Spacing.One)) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(27.dp)
                    .padding(horizontal = HarnessTokens.Spacing.Two),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HarnessText(
                "显示",
                modifier = Modifier.weight(1f),
                color = HarnessTokens.Colors.ChromeIcon,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
            )
            NavigatorHoverAction(
                icon = HarnessIconName.Refresh,
                contentDescription = "恢复默认设置",
                onClick = {
                    state.restoreDefaults()
                    state.open = false
                },
                size = 16.dp,
                iconSize = 16.dp,
                testTag = "activity-menu-reset",
            )
        }
        // Codex dismisses the settings menu after a switch, so the feed immediately shows the result.
        NavigatorActivityMenuToggle("优先事项部分", state.showPriority, {
            state.showPriority = it
            state.open = false
        }, "activity-menu-priority")
        NavigatorActivityMenuToggle("置顶", state.showPinned, {
            state.showPinned = it
            state.open = false
        }, "activity-menu-pinned")
        NavigatorActivityMenuToggle("定时任务", state.showScheduled, {
            state.showScheduled = it
            state.open = false
        }, "activity-menu-scheduled")
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = HarnessTokens.Spacing.Two, vertical = HarnessTokens.Spacing.One)
                    .height(1.dp)
                    .background(HarnessTokens.Colors.BorderSubtle),
        )
        NavigatorActivityMenuAction(
            label = "全部标为已读",
            enabled = unreadIds.isNotEmpty() && navigationActions.markAllRead != null,
            onClick = {
                state.open = false
                navigationActions.markAllRead?.invoke(unreadIds)
            },
            testTag = "activity-menu-mark-read",
        )
        NavigatorActivityMenuAction(
            label = "归档聊天",
            enabled = archiveIds.isNotEmpty() && navigationActions.archiveAll != null,
            onClick = {
                state.open = false
                navigationActions.archiveAll?.invoke(archiveIds)
            },
            testTag = "activity-menu-archive",
        )
    }
}

@Composable
private fun NavigatorActivityMenuToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(29.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(if (hovered) HarnessTokens.Colors.NavigatorRowFill else Color.Transparent)
                .hoverable(interactionSource)
                .clickable(interactionSource = interactionSource, indication = null) { onCheckedChange(!checked) }
                .semantics {
                    role = Role.Checkbox
                    stateDescription = if (checked) "已显示" else "已隐藏"
                    this.testTag = testTag
                }
                .padding(horizontal = HarnessTokens.Spacing.Two),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HarnessText(
            label,
            modifier = Modifier.weight(1f),
            color = HarnessTokens.Colors.Text,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
        )
        if (checked) HarnessIcon(HarnessIconName.Check, tint = HarnessTokens.Colors.Text, size = HarnessTokens.Control.NavigatorIcon)
    }
}

@Composable
private fun NavigatorActivityMenuAction(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    testTag: String,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(29.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(if (hovered && enabled) HarnessTokens.Colors.NavigatorRowFill else Color.Transparent)
                .hoverable(interactionSource, enabled = enabled)
                .clickable(interactionSource = interactionSource, indication = null, enabled = enabled, onClick = onClick)
                .semantics {
                    role = Role.Button
                    this.testTag = testTag
                }
                .padding(horizontal = HarnessTokens.Spacing.Two),
        contentAlignment = Alignment.CenterStart,
    ) {
        HarnessText(
            label,
            color = if (enabled) HarnessTokens.Colors.Text else HarnessTokens.Colors.TextDisabled,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
        )
    }
}

@Composable
private fun NavigatorActivitySectionHeader(
    label: String,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(HarnessTokens.Control.NavRow)
                // Codex's activity header is 8px in from the feed and leaves 6px for its action.
                .padding(start = HarnessTokens.Spacing.Two, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HarnessText(
            label,
            modifier = Modifier.weight(1f),
            color = HarnessTokens.Colors.ChromeIcon,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            maxLines = 1,
        )
        // Codex keeps the activity feed's overflow action visible, unlike the collapsible sections.
        Row(
            horizontalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.One),
            verticalAlignment = Alignment.CenterVertically,
            content = actions,
        )
    }
}

@Composable
private fun NavigatorActivityRow(
    task: RecentTaskItem,
    projectName: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(HarnessTokens.Control.WorkspaceNavigatorRowRadius))
                .background(if (selected || hovered) HarnessTokens.Colors.NavigatorRowFill else Color.Transparent)
                .hoverable(interactionSource)
                .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
                .semantics {
                    role = Role.Button
                    testTag = "activity-" + task.id
                }
                // Codex measures 6/5/8/8 padding inside the 12.5px activity card.
                .padding(start = HarnessTokens.Spacing.Two, end = 5.dp, top = 6.dp, bottom = HarnessTokens.Spacing.Two),
        // Codex keeps 2px between the title line and the snippet line.
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Two),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HarnessText(
                task.title,
                modifier = Modifier.weight(1f),
                color = HarnessTokens.Colors.NavigatorRowText,
                style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 20.sp),
                maxLines = 1,
            )
            if (task.unread) {
                Box(
                    modifier =
                        Modifier
                            .size(6.dp)
                            .background(HarnessTokens.Colors.Accent, CircleShape),
                )
            }
            // The project name shares the title line and stays right-aligned, as in Codex.
            if (projectName != null) {
                HarnessText(
                    projectName,
                    color = HarnessTokens.Colors.ChromeIcon,
                    style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 20.sp),
                    maxLines = 1,
                )
            }
        }
        // The second line is the event snippet; Harness stores that as the optional status.
        if (task.status != null) {
            HarnessText(
                task.status,
                style = MaterialTheme.typography.bodySmall,
                color = HarnessTokens.Colors.ChromeIcon,
                maxLines = 2,
            )
        }
    }
}

private fun activityProjectName(taskId: String, projectNodes: List<WorkspaceProjectNode>): String? =
    projectNodes.firstOrNull { project -> project.children.any { it.id == taskId } }?.name

/** Measured Codex command-menu geometry: 520px wide, 145px below the title bar, 20px corners. */
private val CommandMenuWidth: Dp = 520.dp
private val CommandMenuTopInset: Dp = 145.dp
private val CommandMenuCorner: Dp = 20.dp
private val CommandMenuMaxHeight: Dp = 487.dp
private val CommandMenuShadow: Shadow =
    Shadow(
        radius = 32.dp,
        spread = (-8).dp,
        offset = DpOffset(x = 0.dp, y = 16.dp),
        color = Color(0x30000000),
    )

private data class CommandMenuAction(
    val label: String,
    val shortcut: String,
    val icon: HarnessIconName,
    val onClick: () -> Unit,
)

/**
 * Codex's command menu (`⌘K` / header search). A centred, undimmed card with a plain query field,
 * filtered chats and the three shortcut actions. The card owns the keyboard: Esc dismisses,
 * Up/Down move the highlight and Enter activates it.
 */
@Composable
private fun WorkspaceCommandMenu(
    query: String,
    onQueryChange: (String) -> Unit,
    recentTasks: List<RecentTaskItem>,
    projectNodes: List<WorkspaceProjectNode>,
    onNewTask: () -> Unit,
    onNewProject: () -> Unit,
    onSelectRecent: (RecentTaskItem) -> Unit,
    onDismiss: () -> Unit,
) {
    val trimmed = query.trim()
    val chats =
        recentTasks.filter { task ->
            val projectName = activityProjectName(task.id, projectNodes)
            trimmed.isEmpty() ||
                task.title.contains(trimmed, ignoreCase = true) ||
                projectName?.contains(trimmed, ignoreCase = true) == true
        }
    val actions =
        listOf(
            CommandMenuAction("新聊天", "⌘N", HarnessIconName.NewChat, onNewTask),
            CommandMenuAction("打开文件夹", "⌘O", HarnessIconName.Folder, onNewProject),
            // Harness has no file search surface yet; the row keeps the measured action list.
            CommandMenuAction("搜索文件", "⌘P", HarnessIconName.Search, {}),
        ).filter { trimmed.isEmpty() || it.label.contains(trimmed, ignoreCase = true) }
    val itemCount = chats.size + actions.size
    var selectedIndex by remember(trimmed) { mutableStateOf(0) }
    LaunchedEffect(itemCount) {
        if (selectedIndex >= itemCount) selectedIndex = 0
    }
    val activate: (Int) -> Unit = { index ->
        when {
            index < chats.size -> onSelectRecent(chats[index])
            index - chats.size in actions.indices -> actions[index - chats.size].onClick()
        }
    }
    val inputFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { inputFocus.requestFocus() }
    }
    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        // Codex paints `--color-dialog-overlay` (#0002, black 13.3%) behind the card, so the rail,
        // navigator and page visibly dim while the menu is open. Clicking the backdrop dismisses.
        val scrimSource = remember { MutableInteractionSource() }
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(HarnessTokens.Colors.DialogScrim)
                    .clickable(interactionSource = scrimSource, indication = null, onClick = onDismiss)
                    .semantics { testTag = "workspace-command-scrim" },
        )
        val cardSource = remember { MutableInteractionSource() }
        Column(
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = CommandMenuTopInset)
                    .width(CommandMenuWidth)
                    .dropShadow(RoundedCornerShape(CommandMenuCorner), CommandMenuShadow)
                    .clip(RoundedCornerShape(CommandMenuCorner))
                    .background(HarnessTokens.Colors.SurfaceElevated)
                    .clickable(interactionSource = cardSource, indication = null, onClick = {})
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) {
                            false
                        } else {
                            when (event.key) {
                                Key.Escape -> {
                                    onDismiss()
                                    true
                                }

                                Key.DirectionDown -> {
                                    if (itemCount > 0) selectedIndex = (selectedIndex + 1) % itemCount
                                    true
                                }

                                Key.DirectionUp -> {
                                    if (itemCount > 0) selectedIndex = (selectedIndex - 1 + itemCount) % itemCount
                                    true
                                }

                                Key.Enter -> {
                                    if (itemCount > 0) activate(selectedIndex)
                                    true
                                }

                                else -> false
                            }
                        }
                    }
                    .semantics { testTag = "workspace-command-menu" },
        ) {
            // Codex's card has 5px padding; the field itself then insets its text by 10px.
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            start = HarnessTokens.Spacing.One + 1.dp,
                            end = HarnessTokens.Spacing.One + 1.dp,
                            top = HarnessTokens.Spacing.One + 1.dp,
                            bottom = HarnessTokens.Spacing.One,
                        )
                        .height(33.dp)
                        .padding(horizontal = 10.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (query.isEmpty()) {
                    HarnessText(
                        "搜索聊天",
                        color = HarnessTokens.Colors.ChromeIcon,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = HarnessTokens.Colors.Text),
                    cursorBrush = SolidColor(HarnessTokens.Colors.Accent),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .focusRequester(inputFocus)
                            .semantics { testTag = "workspace-command-input" },
                )
            }
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = CommandMenuMaxHeight - 43.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 5.dp, end = 5.dp, bottom = 8.dp),
            ) {
                if (chats.isNotEmpty()) {
                    CommandMenuSectionLabel("聊天")
                    Spacer(Modifier.height(HarnessTokens.Spacing.One))
                    Column {
                        chats.forEachIndexed { index, task ->
                            CommandMenuRow(
                                title = task.title,
                                trailing = activityProjectName(task.id, projectNodes),
                                shortcut = "⌃" + (index + 1),
                                selected = selectedIndex == index,
                                testTag = "command-chat-" + task.id,
                                onClick = { onSelectRecent(task) },
                            )
                        }
                    }
                }
                if (actions.isNotEmpty()) {
                    CommandMenuSectionLabel("快捷操作")
                    Spacer(Modifier.height(HarnessTokens.Spacing.One))
                    Column {
                        actions.forEachIndexed { actionIndex, action ->
                            val index = chats.size + actionIndex
                            CommandMenuRow(
                                title = action.label,
                                icon = action.icon,
                                trailing = null,
                                shortcut = action.shortcut,
                                selected = selectedIndex == index,
                                testTag = "command-action-" + actionIndex,
                                onClick = action.onClick,
                            )
                        }
                    }
                }
                if (chats.isEmpty() && actions.isEmpty()) {
                    SidebarHint("没有找到匹配的聊天或操作")
                }
            }
        }
    }
}

@Composable
private fun CommandMenuSectionLabel(label: String) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(27.dp)
                .padding(start = HarnessTokens.Spacing.Two, end = HarnessTokens.Spacing.Two, top = HarnessTokens.Spacing.Two),
        contentAlignment = Alignment.TopStart,
    ) {
        HarnessText(
            label,
            color = HarnessTokens.Colors.ChromeIcon,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            maxLines = 1,
        )
    }
}

@Composable
private fun CommandMenuRow(
    title: String,
    icon: HarnessIconName? = null,
    trailing: String?,
    shortcut: String,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(31.dp)
                .clip(RoundedCornerShape(HarnessTokens.Control.WorkspaceNavigatorRowRadius))
                .background(if (selected || hovered) HarnessTokens.Colors.NavigatorRowFill else Color.Transparent)
                .hoverable(interactionSource)
                .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
                .semantics {
                    role = Role.Button
                    this.testTag = testTag
                }
                .padding(horizontal = HarnessTokens.Spacing.Two),
        horizontalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Two),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            HarnessIcon(icon, tint = HarnessTokens.Colors.ChromeIcon, size = HarnessTokens.Control.NavigatorIcon)
        }
        HarnessText(
            title,
            modifier = Modifier.weight(1f),
            color = HarnessTokens.Colors.NavigatorRowText,
            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 20.sp),
            maxLines = 1,
        )
        if (trailing != null) {
            HarnessText(
                trailing,
                color = HarnessTokens.Colors.ChromeIcon,
                style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 20.sp),
                maxLines = 1,
            )
        }
        HarnessText(
            shortcut,
            color = HarnessTokens.Colors.ChromeIcon,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
        )
    }
}

/** Page-owned navigator section label (Settings/Plugins/Scheduled keep the older 12sp style). */
@Composable
private fun SectionLabel(text: String) {
    HarnessText(
        text,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    start = HarnessTokens.Control.NavigatorPaddingX,
                    top = HarnessTokens.Spacing.Five,
                    bottom = HarnessTokens.Control.NavigatorSectionRun,
                ),
        color = HarnessTokens.Colors.TextMuted,
        style = MaterialTheme.typography.labelMedium,
        maxLines = 1,
    )
}

@Composable
private fun SidebarHint(text: String) {
    HarnessText(
        text,
        modifier = Modifier.padding(start = HarnessTokens.Spacing.Two),
        color = HarnessTokens.Colors.TextDisabled,
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun RecentTaskRow(
    task: RecentTaskItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    ConversationContextMenu(task.id) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(HarnessTokens.Control.NavRow)
                    .clip(RoundedCornerShape(HarnessTokens.Control.WorkspaceNavigatorRowRadius))
                    .background(if (selected || hovered) HarnessTokens.Colors.NavigatorRowFill else Color.Transparent)
                    .hoverable(interactionSource)
                    .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
                    .semantics {
                        role = Role.Tab
                        this.selected = selected
                        testTag = "recent-" + task.id
                    }
                    .padding(start = HarnessTokens.Control.NavigatorPaddingX, end = HarnessTokens.Control.NavigatorPaddingX),
            horizontalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Two),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (task.pinned) HarnessIcon(HarnessIconName.Pushpin, tint = HarnessTokens.Colors.ChromeIcon, size = 14.dp)
            HarnessText(
                task.title,
                modifier = Modifier.weight(1f),
                color = HarnessTokens.Colors.NavigatorRowText,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
            )
            if (task.unread) {
                Box(modifier = Modifier.size(6.dp).background(HarnessTokens.Colors.Accent, CircleShape))
            }
            NavigatorConversationActions(id = task.id, visible = hovered)
        }
    }
}

/**
 * Hover-only pin/archive pair shared by recent tasks and project children. The live v155 row draws
 * two 20x20 boxes with 12px glyphs and an 8px gap; actions stay absent when the session cannot be
 * managed, instead of rendering disabled controls.
 */
@Composable
private fun RowScope.NavigatorConversationActions(
    id: String,
    visible: Boolean,
) {
    if (!visible) return
    val actions = LocalConversationNavigationActions.current
    val session = actions.sessions[id] ?: return
    if (!session.managementAvailable || actions.busy) return
    Row(
        horizontalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Two),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavigatorHoverAction(
            icon = if (session.pinned) HarnessIconName.PushpinFilled else HarnessIconName.Pushpin,
            contentDescription = if (session.pinned) "取消固定" else "固定",
            onClick = { actions.dispatch(id, ConversationCommand.TogglePin) },
            size = HarnessTokens.Control.WorkspaceNavigatorRowAction,
            iconSize = 12.dp,
        )
        if (session.archiveAvailable) {
            NavigatorHoverAction(
                icon = HarnessIconName.Archive,
                contentDescription = if (session.archived) "取消归档" else "归档",
                onClick = { actions.dispatch(id, ConversationCommand.ToggleArchive) },
                size = HarnessTokens.Control.WorkspaceNavigatorRowAction,
                iconSize = 12.dp,
            )
        }
    }
}

@Composable
private fun ProjectRow(
    node: WorkspaceProjectNode,
    expanded: Boolean,
    selectedThreadId: String?,
    onToggle: (String) -> Unit,
    onSelectNode: (WorkspaceItemNode) -> Unit,
    onOpenContext: ((String) -> Unit)?,
    onNewTask: () -> Unit,
) {
    var showAll by remember(node.projectId) { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val selected = node.children.any { it.id == selectedThreadId }
    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = HarnessTokens.Spacing.Two),
        // The project row and its children are separate 30px rows with the Codex `gap-px` between them.
        verticalArrangement = Arrangement.spacedBy(HarnessTokens.Control.NavigatorRowGap),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(HarnessTokens.Control.NavRow)
                    .clip(RoundedCornerShape(HarnessTokens.Control.WorkspaceNavigatorRowRadius))
                    .background(if (selected || hovered) HarnessTokens.Colors.NavigatorRowFill else Color.Transparent)
                    .hoverable(interactionSource)
                    .clickable(interactionSource = interactionSource, indication = null) { onToggle(node.projectId) }
                    .semantics {
                        role = Role.Button
                        stateDescription = if (expanded) "已展开" else "已收起"
                        testTag = "project-" + node.projectId
                    }
                    .padding(start = HarnessTokens.Control.NavigatorPaddingX, end = HarnessTokens.Control.NavigatorPaddingX),
            horizontalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Two),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Codex uses the folder glyph as the project marker; expansion stays visible through the
            // content rather than a leading chevron.
            HarnessIcon(HarnessIconName.Folder, tint = HarnessTokens.Colors.NavigatorRowText, size = HarnessTokens.Control.NavigatorIcon)
            HarnessText(
                node.name,
                modifier = Modifier.weight(1f),
                color = HarnessTokens.Colors.NavigatorRowText,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
            )
            if (hovered) {
                // Codex keeps the row actions hidden until the row is hovered: "…" + new chat.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.One),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    onOpenContext?.let { open ->
                        NavigatorHoverAction(
                            icon = HarnessIconName.More,
                            contentDescription = "项目选项",
                            onClick = { open(node.projectId) },
                            size = HarnessTokens.Control.WorkspaceNavigatorRowAction,
                            iconSize = 12.dp,
                        )
                    }
                    NavigatorHoverAction(
                        icon = HarnessIconName.NewChat,
                        contentDescription = "在项目中新建会话",
                        onClick = onNewTask,
                        size = HarnessTokens.Control.WorkspaceNavigatorRowAction,
                        iconSize = 12.dp,
                    )
                }
            }
        }
        if (expanded) {
            val preview = node.children.take(5).toMutableList()
            node.children.firstOrNull { it.id == selectedThreadId && it !in preview }?.let { preview.add(it) }
            val visibleChildren = if (showAll) node.children else preview
            visibleChildren.forEach { child ->
                ProjectChildRow(child = child, selected = child.id == selectedThreadId, onClick = { onSelectNode(child) })
            }
            if (node.children.size > preview.size) {
                ShowAllRow(
                    label = if (showAll) "收起" else "展开显示",
                    testTag = "project-show-all-${node.projectId}",
                    onClick = { showAll = !showAll },
                )
            }
        }
    }
}

/**
 * The Codex "展开显示" row: 32px tall, 14sp regular at 49.4% ink, with the text aligned to the
 * project children's 40px text column.
 */
@Composable
private fun ShowAllRow(
    label: String,
    testTag: String,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(32.dp)
                .padding(start = NavigatorTextIndent - HarnessTokens.Spacing.Two),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier =
                Modifier
                    .clip(RoundedCornerShape(HarnessTokens.Control.WorkspaceNavigatorActionRadius))
                    .hoverable(interactionSource)
                    .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
                    .semantics {
                        role = Role.Button
                        this.testTag = testTag
                    }
                    .padding(horizontal = HarnessTokens.Spacing.Two, vertical = HarnessTokens.Spacing.One),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HarnessText(
                label,
                color = HarnessTokens.Colors.ChromeIcon,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ProjectChildRow(
    child: WorkspaceItemNode,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    // Children align under the project name (no leading glyph), the Codex tree indent.
    ConversationContextMenu(child.id) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(HarnessTokens.Control.NavRow)
                    .clip(RoundedCornerShape(HarnessTokens.Control.WorkspaceNavigatorRowRadius))
                    .background(if (selected || hovered) HarnessTokens.Colors.NavigatorRowFill else Color.Transparent)
                    .hoverable(interactionSource)
                    .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
                    .semantics {
                        role = Role.Tab
                        this.selected = selected
                        testTag = "node-" + child.id
                    }
                    .padding(start = NavigatorTextIndent, end = HarnessTokens.Control.NavigatorPaddingX),
            horizontalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Two),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HarnessText(
                child.title,
                modifier = Modifier.weight(1f),
                color = HarnessTokens.Colors.NavigatorRowText,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
            )
            NavigatorConversationActions(id = child.id, visible = hovered)
        }
    }
}

@Composable
private fun MainSurface(
    modifier: Modifier = Modifier,
    /** The cap the page's content column is laid out at, or null when the page brings its own. */
    contentMaxWidth: Dp? = HarnessTokens.Control.ContentMaxWidth,
    /** Whether this pane's page docks a composer; a page with none drops the dock, gutter and all. */
    docksComposer: Boolean = true,
    conversation: Boolean = false,
    content: @Composable () -> Unit,
    composer: (@Composable () -> Unit)?,
) {
    BoxWithConstraints(modifier.fillMaxHeight()) {
        CompositionLocalProvider(LocalConversationViewportHeight provides maxHeight) {
            if (conversation) {
                // The conversation docks its composer over the timeline instead of stacking below it: the
                // timeline keeps the full height and the composer floats on the measured canvas.
                var composerHeight by remember { mutableStateOf(98.dp) }
                val density = LocalDensity.current
                val inset = if (docksComposer && composer != null) composerHeight + 16.dp else 0.dp
                Box(Modifier.fillMaxSize().background(ConversationAppearance.canvas)) {
                    CompositionLocalProvider(LocalConversationBottomInset provides inset) { content() }
                    if (docksComposer && composer != null) {
                        Box(
                            Modifier.align(Alignment.BottomCenter).widthIn(max = 768.dp).fillMaxWidth()
                                .padding(horizontal = 16.dp).padding(bottom = 16.dp),
                        ) {
                            Box(Modifier.matchParentSize().background(ConversationAppearance.canvas))
                            Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(32.dp).offset(y = (-32).dp)
                                .background(Brush.verticalGradient(listOf(Color.Transparent, ConversationAppearance.canvas))))
                            Box(Modifier.fillMaxWidth().onGloballyPositioned {
                                composerHeight = with(density) { it.size.height.toDp() }
                            }.semantics { testTag = "composer-column" }) { composer() }
                        }
                    }
                }
                return@CompositionLocalProvider
            }
            Column(Modifier.fillMaxSize().background(HarnessTokens.Colors.WorkspaceBackground)) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    Box(
                        Modifier.then(
                            if (contentMaxWidth == null) Modifier.fillMaxSize()
                            else Modifier.widthIn(max = contentMaxWidth).fillMaxWidth().fillMaxHeight(),
                        ).semantics { testTag = "main-content-column" },
                    ) { content() }
                }
                if (docksComposer) composer?.let { ComposerDock(it) }
            }
        }
    }
}
@Composable
private fun ComposerDock(
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(
            horizontal = HarnessTokens.Composer.DockInset,
            vertical = HarnessTokens.Composer.DockInset,
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier =
                Modifier
                    .widthIn(max = HarnessTokens.Composer.MaxWidth)
                    .fillMaxWidth()
                    .semantics { testTag = "composer-column" },
        ) {
            content()
        }
    }
}

@Composable
private fun ContextPanelSurface(
    panel: @Composable () -> Unit,
    width: Dp,
    threadPanel: Boolean,
) {
    Box(Modifier.fillMaxHeight()) {
        Column(
            modifier = Modifier.width(width).fillMaxHeight().background(HarnessTokens.Colors.Surface)
                .padding(if (threadPanel) 0.dp else HarnessTokens.Spacing.Three),
            verticalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Three),
        ) {
            panel()
        }
    }
}
