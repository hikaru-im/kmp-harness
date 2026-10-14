package im.hikaru.harness.client.shell

/**
 * Which left navigator the shell mounts for one page.
 *
 * Codex changes the left column per page and some pages have none, so the App Shell must not assume
 * a Workspace tree everywhere. The fixed App Rail is separate: it is always rendered, while the
 * navigator is owned by the page.
 */
public enum class NavigatorKind {
    /** No left navigator; the main surface takes the full width (Account). */
    None,
    /** Project tree, search and recent tasks (Home, Recent, Projects, Project, Thread). */
    Workspace,
    /** Settings section list (Settings). */
    Settings,
    /** Installed plugin list (Plugins, PluginDetail). */
    Plugins,
    /** Scheduled task and run-history navigator. */
    Scheduled,
}

/**
 * Page-level shell ownership for the left navigator. [resizable] stays false for every navigator
 * except the Workspace one, which keeps the user drag-resize edge.
 */
public data class PageShellSpec(
    val navigator: NavigatorKind,
    val resizable: Boolean = false,
) {
    public companion object {
        /** A page that owns no left navigator and therefore renders no drawer. */
        public val NoNavigator: PageShellSpec = PageShellSpec(NavigatorKind.None)
    }
}

/**
 * True when this spec mounts a left navigator at all. A [NavigatorKind.None] page owns no navigator,
 * so the shell must draw none of its affordances: the titlebar sidebar toggle, the compact bottom-bar
 * navigator entry, the drawer/scrim and the resize handle all follow this flag. Keeping it a pure
 * property lets the page-to-affordance contract be asserted without Compose UI tooling.
 */
public val PageShellSpec.mountsNavigator: Boolean
    get() = navigator != NavigatorKind.None

/**
 * Navigator the shell mounts for [page]. Home and Thread keep the Workspace navigator; Settings,
 * Plugins and Hosts own a dedicated navigator; the Library and Project pages and Account mount none,
 * so the Workspace project tree never leaks into those pages.
 */
public fun pageShellSpec(page: PageId): PageShellSpec =
    when (page) {
        // The reference puts the workspace navigator on Home/Thread only; the Library and Project
        // pages carry their own in-page layout and mount no navigator (measured width 0).
        PageId.Home,
        is PageId.Thread,
        -> PageShellSpec(NavigatorKind.Workspace, resizable = true)

        PageId.Library,
        is PageId.Projects,
        is PageId.Project,
        -> PageShellSpec.NoNavigator

        is PageId.ScheduledTasks -> PageShellSpec(NavigatorKind.Scheduled)

        is PageId.Settings -> PageShellSpec(NavigatorKind.Settings)
        PageId.Plugins,
        is PageId.PluginDetail,
        -> PageShellSpec(NavigatorKind.Plugins)

        PageId.Account -> PageShellSpec.NoNavigator
    }

/**
 * Semantic tag of the mounted navigator, or null when the page has none. Kept as a pure function so
 * the page-to-navigator matrix is testable without Compose UI tooling.
 */
public fun navigatorTestTag(kind: NavigatorKind): String? =
    when (kind) {
        NavigatorKind.None -> null
        NavigatorKind.Workspace -> "workspace-navigator"
        NavigatorKind.Settings -> "settings-navigator"
        NavigatorKind.Plugins -> "plugins-navigator"
        NavigatorKind.Scheduled -> "scheduled-navigator"
    }
