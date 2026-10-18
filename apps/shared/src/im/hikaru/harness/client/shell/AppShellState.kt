package im.hikaru.harness.client.shell

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import im.hikaru.harness.ui.foundation.HarnessIconName
import im.hikaru.harness.client.scheduled.ScheduledTasksState

public enum class SidebarMode {
    Expanded,
    Collapsed,
    TemporarilyExpanded,
}

public data class SidebarState(
    val mode: SidebarMode = SidebarMode.Expanded,
    val selectedSessionId: String? = null,
)

public enum class ShellDestination {
    Conversation,
    ScheduledTasks,
    Settings,
    Account,
    PluginInventory,
}

public enum class ConnectionIndicatorState {
    Local,
    Connecting,
    Online,
    Disconnected,
    Incompatible,
    NoHost,
    RequiresLogin,
}

public enum class PanelKind {
    None,
    Settings,
    ToolDetails,
    ModelPicker,
    CommandPalette,
}

public data class PanelState(
    val kind: PanelKind = PanelKind.None,
    val isOpen: Boolean = false,
)

/** State owned by the account avatar/menu surface. It does not replace the current page. */
public data class AccountMenuState(
    val isOpen: Boolean = false,
    val identityLabel: String = "本地模式",
    val modeLabel: String = "本地",
    val connectionLabel: String = "未连接",
    val hostLabel: String = "当前没有选中的 Host",
    val availableHostCount: Int = 0,
    val isLoggedIn: Boolean = false,
    val canLogin: Boolean = false,
) {
    public fun open(): AccountMenuState = copy(isOpen = true)

    public fun close(): AccountMenuState = copy(isOpen = false)
}

public enum class AccountMenuAction {
    Settings,
    Profile,
    Login,
    Logout,
}

/** One row of the Codex profile dropdown, in render order, below the identity header. */
public data class AccountMenuItem(
    val testTag: String,
    val label: String,
    val icon: HarnessIconName,
    val action: AccountMenuAction,
    val enabled: Boolean = true,
    /** Draws the Codex separator rule after this row. */
    val separatorAfter: Boolean = false,
)

/**
 * The dropdown rows for [state] in Codex order: Host, Settings, then either the signed-in
 * Profile + Log out pair or the signed-out Sign in row. A pure function so the structure and the
 * signed-in/signed-out split stay assertable without a Compose UI harness; [AccountMenu] renders
 * exactly this list.
 */
public fun accountMenuItems(state: AccountMenuState): List<AccountMenuItem> {
    val settings =
        AccountMenuItem("account-menu-settings", "设置", HarnessIconName.Settings, AccountMenuAction.Settings)
    return if (state.isLoggedIn) {
        listOf(
            settings,
            AccountMenuItem(
                "account-menu-profile",
                "个人资料",
                HarnessIconName.User,
                AccountMenuAction.Profile,
                separatorAfter = true,
            ),
            AccountMenuItem("account-menu-logout", "退出登录", HarnessIconName.SignOut, AccountMenuAction.Logout),
        )
    } else {
        listOf(
            settings,
            AccountMenuItem(
                "account-menu-sign-in",
                "使用 ChatGPT 登录",
                HarnessIconName.User,
                AccountMenuAction.Login,
                enabled = state.canLogin,
            ),
        )
    }
}

public data class ProjectContextState(
    val visible: Boolean = true,
)

public data class AppShellState(
    val sidebar: SidebarState = SidebarState(),
    /**
     * Navigator width the user picked, clamped to `NavigatorMinWidth..NavigatorMaxWidth` when laid
     * out. Codex lets the workspace navigator be resized; this carries that preference so the shell
     * does not have to treat 275dp as the only width. Defaults to [NavigatorPreferredWidth].
     */
    val navigatorPreferredWidth: Dp = NavigatorPreferredWidth,
    /** Compatibility field for the pre-navigation shell. Derived from [navigation] while it is unused. */
    val destination: ShellDestination = ShellDestination.Conversation,
    val selectedNavigationId: String? = null,
    val navigation: NavigationState = NavigationState(),
    val workspace: WorkspaceNavigatorState = WorkspaceNavigatorState(),
    /** Page-owned navigators. Only the one named by the current page's [PageShellSpec] is mounted. */
    val settingsNavigator: SettingsNavigatorState = SettingsNavigatorState(),
    val pluginsNavigator: PluginsNavigatorState = PluginsNavigatorState(),
    val scheduledTasksNavigator: ScheduledTasksState = ScheduledTasksState(),
    val accountMenu: AccountMenuState = AccountMenuState(),
    /** Open/closed state of the rail 探索 pin menu. */
    val exploreMenu: ExploreMenuState = ExploreMenuState(),
    /**
     * Destinations the user pinned from the 探索 menu. They render in the rail's second group, below
     * the group rule; the empty default is the initial state, so the rail has no second group until
     * the user pins something.
     */
    val pinnedRailDestinations: Set<RailDestination> = emptySet(),
    val thread: ThreadSurfaceState? = null,
    val connection: ConnectionIndicatorState = ConnectionIndicatorState.Connecting,
    val panel: PanelState = PanelState(),
    val projectContext: ProjectContextState = ProjectContextState(),
)

public data class AppShellNavItem(
    val id: String,
    val label: String,
    val destination: ShellDestination,
    val icon: HarnessIconName = HarnessIconName.More,
    val enabled: Boolean = true,
)

public data class AppShellSessionItem(
    val id: String,
    val title: String,
)

public enum class NavigatorDisplayMode {
    Compact,
    Detailed,
    Sections,
}

public enum class WorkspaceItemKind {
    Task,
    Thread,
}

/**
 * Common contract for a page's left navigator: what it draws and whether it is collapsed.
 *
 * [mode] is deliberately *not* page-owned. Measured on the live app, the navigator has one collapse
 * state for the whole session — collapsing it on one page leaves it collapsed on the next page that
 * owns a navigator, and every page's navigator opens and closes together. So the app holds one
 * [SidebarMode] and writes it into each navigator it hands the shell, while the content below the
 * mode (search, filters, selection) stays per-page. A page with `NavigatorKind.None` has no
 * navigator at all, so it has no mode to share and draws no navigator affordance.
 */
public interface NavigatorState {
    public val mode: SidebarMode
}

/** Flips a navigator between expanded and collapsed; a temporary expansion expands like a collapse. */
public fun SidebarMode.toggled(): SidebarMode =
    if (this == SidebarMode.Collapsed) SidebarMode.Expanded else SidebarMode.Collapsed

/** Left workspace navigator: workspace header, search, project tree and recent tasks. */
public data class WorkspaceNavigatorState(
    val workspaceId: String? = null,
    val workspaceLabel: String = "Local Workspace",
    override val mode: SidebarMode = SidebarMode.Expanded,
    val searchQuery: String = "",
    val displayMode: NavigatorDisplayMode = NavigatorDisplayMode.Sections,
    val isLoading: Boolean = false,
) : NavigatorState {
    public fun open(): WorkspaceNavigatorState = copy(mode = SidebarMode.Expanded)

    public fun close(): WorkspaceNavigatorState = copy(mode = SidebarMode.Collapsed)

    public fun toggle(): WorkspaceNavigatorState =
        if (mode == SidebarMode.Collapsed) {
            open()
        } else {
            close()
        }

    public fun withSearchQuery(query: String): WorkspaceNavigatorState = copy(searchQuery = query)
}

/**
 * Settings navigator. It owns the grouped row list and its own filter (see [SettingsNavigatorModel]
 * and [settingsNavigatorGroups]); it is not a Workspace tree with a different header, so its search
 * never reads [WorkspaceNavigatorState.searchQuery]. [selectedSlug] names the selected row.
 */
public data class SettingsNavigatorState(
    override val mode: SidebarMode = SidebarMode.Expanded,
    val selectedSlug: String = SettingsNavigatorModel.DefaultSlug,
    val query: String = "",
) : NavigatorState

/** Plugins navigator: installed plugin filter, independent of the Workspace search. */
public data class PluginsNavigatorState(
    override val mode: SidebarMode = SidebarMode.Expanded,
    val query: String = "",
    val enabledOnly: Boolean = false,
) : NavigatorState


/** A task/thread child of a [WorkspaceProjectNode], addressable by its own id. */
public data class WorkspaceItemNode(
    val id: String,
    val title: String,
    val kind: WorkspaceItemKind = WorkspaceItemKind.Thread,
    val status: String? = null,
)

/** A project node in the workspace tree with expandable task/thread children. */
public data class WorkspaceProjectNode(
    val projectId: String,
    val name: String,
    val pinned: Boolean = false,
    val children: List<WorkspaceItemNode> = emptyList(),
)

/** A recent task projected into the navigator, with the same id used inside its project. */
public data class RecentTaskItem(
    val id: String,
    val title: String,
    val status: String? = null,
    val unread: Boolean = false,
    val pinned: Boolean = false,
)

/**
 * Rail groups. Codex divides the rail with a single 24px rule between consecutive groups, so the
 * section only has to identify which destinations sit above and below that rule.
 */
public enum class RailSection {
    /** Session-level destinations, drawn above the group rule. */
    Primary,
    /** Workspace-level destinations, drawn below the group rule. */
    Secondary,
}

/** App Rail entry: an app-level destination rendered as an icon in the narrow left rail. */
public data class AppShellRailItem(
    val id: String,
    val label: String,
    val icon: HarnessIconName,
    val selected: Boolean = false,
    val enabled: Boolean = true,
    val section: RailSection = RailSection.Primary,
    /** Initials for the bottom account chip; null keeps the generic user glyph. */
    val initials: String? = null,
    /** App-level destination; null marks the separate account-menu entry. */
    val destination: RailDestination? = null,
)

/**
 * Consecutive runs of [items] that share a [AppShellRailItem.section], in declaration order. The
 * rail draws one rule between consecutive runs, so a single-section list stays one group.
 */
public fun railSections(items: List<AppShellRailItem>): List<List<AppShellRailItem>> =
    items.fold(mutableListOf<MutableList<AppShellRailItem>>()) { groups, item ->
        if (groups.isEmpty() || groups.last().last().section != item.section) {
            groups.add(mutableListOf(item))
        } else {
            groups.last().add(item)
        }
        groups
    }

/**
 * Two-character initials for the rail account chip: the first letter of the first two words when the
 * label has words ("kmp-harness" -> "KH"), otherwise the first two letters of the label.
 */
public fun railInitials(label: String): String {
    val words = label.split(' ', '-', '_').filter { it.isNotBlank() }
    val initials =
        if (words.size >= 2) {
            words[0].take(1) + words[1].take(1)
        } else {
            label.filter { it.isLetterOrDigit() }.take(2)
        }
    return initials.uppercase()
}

/**
 * Rail item id of the account chip. Public because the shared test sources read it on every target,
 * including Kotlin/Native where an `internal` top-level constant is not visible to the test
 * compilation.
 */
public const val RAIL_ACCOUNT_ID: String = "account"

/** Header state for the main thread surface. */
public data class ThreadSurfaceState(
    val threadId: String? = null,
    val title: String = "",
    val status: String? = null,
    val isRunning: Boolean = false,
)
