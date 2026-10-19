package im.hikaru.harness.client.shell

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Shared page identity for Desktop, Android and iOS.
 *
 * The old flat [ShellDestination] enum could not express Project > Task > Thread. Pages, overlays and
 * the back stack are modeled separately here; [ShellDestination] stays only as a compatibility adapter.
 *
 * Every page is a Navigation 3 [NavKey]: [NavigationState.pages] is the back stack handed to
 * `NavDisplay`, and the serializable route shape is what a `rememberNavBackStack` or saved state would
 * persist if the app starts restoring navigation.
 */
@Serializable
public sealed interface PageId : NavKey {
    @Serializable
    public data object Home : PageId

    /**
     * The 新标签页 page: what a new tab opens on in the reference. It is a page of its own — not an
     * empty Home — because the live tab strip draws a browser toolbar and a 工具 grid on it, and it
     * mounts the Workspace navigator (the live title strip keeps its 隐藏侧边栏 toggle while the tab is
     * on this page).
     */
    @Serializable
    public data object NewTab : PageId

    /**
     * The rail 资料库 destination. Codex's Library is a file product this client does not implement,
     * so the slot opens an explicit placeholder page instead of a fabricated library.
     */
    @Serializable
    public data object Library : PageId

    @Serializable
    public data class Projects(
        val query: String = "",
    ) : PageId

    @Serializable
    public data class Project(
        val projectId: String,
        val childFilter: ProjectChildFilter = ProjectChildFilter.All,
    ) : PageId

    @Serializable
    public data class Thread(
        val threadId: String,
        val taskId: String? = null,
        val automationId: String? = null,
        val runId: String? = null,
        val navigationSource: String? = null,
    ) : PageId

    @Serializable
    public data class ScheduledTasks(
        val query: String = "",
    ) : PageId


    @Serializable
    public data object Plugins : PageId

    @Serializable
    public data class PluginDetail(
        val pluginId: String,
    ) : PageId

    @Serializable
    public data object Account : PageId

    @Serializable
    public data class Settings(
        val section: SettingsSection = SettingsSection.General,
        val childTab: String? = null,
    ) : PageId
}

@Serializable
public enum class ProjectChildFilter {
    All,
    Tasks,
    Threads,
    Archived,
}

@Serializable
public enum class SettingsSection {
    General,
    Appearance,
    Models,
    Plugins,
    Connections,
    Shortcuts,
    Git,
    Worktrees,
}

/** Entries exposed by the account menu. They navigate to pages without making the menu a page. */
public enum class AccountMenuDestination {
    Settings,
}

public fun AccountMenuDestination.toPageId(): PageId =
    when (this) {
        AccountMenuDestination.Settings -> PageId.Settings()
    }

/** Thread-surface overlays. They never replace [PageId]; they sit on top of the current page. */
public sealed interface OverlayId {
    public data object None : OverlayId

    public data class ProjectContext(
        val projectId: String,
    ) : OverlayId

    public data class ToolDetails(
        val toolCallId: String,
    ) : OverlayId

    public data object ModelPicker : OverlayId

    public data object CommandPalette : OverlayId
}

/**
 * Shared navigation state. Desktop renders it as App Rail + Workspace Navigator + Main Surface;
 * mobile renders the same state through drill-down and bottom navigation.
 */
public data class NavigationState(
    val currentPage: PageId = PageId.Home,
    val backStack: List<PageId> = emptyList(),
    val selectedWorkspaceId: String? = null,
    val expandedProjectIds: Set<String> = emptySet(),
    val selectedTaskId: String? = null,
    val selectedThreadId: String? = null,
    val overlay: OverlayId = OverlayId.None,
) {
    /**
     * The user-owned Navigation 3 back stack: the current page is its last entry. [backStack] keeps
     * carrying the pages *below* it so existing callers and tests keep their shape, while `NavDisplay`
     * receives a list that is never empty.
     */
    public val pages: List<PageId> get() = backStack + currentPage

    public fun navigate(page: PageId): NavigationState =
        if (page == currentPage) this else copy(currentPage = page, backStack = backStack + currentPage)

    public fun back(): NavigationState {
        val previous = backStack.lastOrNull() ?: return this
        return copy(currentPage = previous, backStack = backStack.dropLast(1))
    }

    public fun openOverlay(overlay: OverlayId): NavigationState = copy(overlay = overlay)

    public fun closeOverlay(): NavigationState = copy(overlay = OverlayId.None)

    public fun toggleProject(projectId: String): NavigationState =
        copy(
            expandedProjectIds =
                if (projectId in expandedProjectIds) {
                    expandedProjectIds - projectId
                } else {
                    expandedProjectIds + projectId
                },
        )

    public fun selectThread(threadId: String, taskId: String? = null): NavigationState =
        copy(
            currentPage = PageId.Thread(threadId, taskId),
            selectedThreadId = threadId,
            selectedTaskId = taskId,
            backStack = backStack + currentPage,
        )

    public fun selectScheduledRun(navigation: im.hikaru.harness.client.scheduled.ScheduledRunNavigation): NavigationState =
        copy(
            currentPage =
                PageId.Thread(
                    threadId = navigation.threadId,
                    automationId = navigation.automationId,
                    runId = navigation.runId,
                    navigationSource = navigation.navigationSource,
                ),
            selectedThreadId = navigation.threadId,
            backStack = backStack + currentPage,
        )
}

/**
 * Compatibility adapter for the pre-navigation ShellDestination enum. Existing callers can keep
 * using the flat value while the page model takes over, but it must not carry business pages again.
 */
public fun ShellDestination.toPageId(): PageId =
    when (this) {
        ShellDestination.Conversation -> PageId.Home
        ShellDestination.ScheduledTasks -> PageId.ScheduledTasks()
        ShellDestination.Settings -> PageId.Settings()
        ShellDestination.Account -> PageId.Account
        ShellDestination.PluginInventory -> PageId.Plugins
    }

public fun PageId.toShellDestination(): ShellDestination =
    when (this) {
        PageId.Home,
        PageId.NewTab,
        PageId.Library,
        is PageId.Projects,
        is PageId.Project,
        is PageId.Thread,
        -> ShellDestination.Conversation

        is PageId.ScheduledTasks -> ShellDestination.ScheduledTasks

        PageId.Account -> ShellDestination.Account
        PageId.Plugins,
        is PageId.PluginDetail,
        -> ShellDestination.PluginInventory

        is PageId.Settings -> ShellDestination.Settings
    }
