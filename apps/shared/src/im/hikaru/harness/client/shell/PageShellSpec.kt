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
    /**
     * True when the page draws its own content column, so the shell must not cap the surface it hands
     * over at [im.hikaru.harness.ui.foundation.HarnessTokens.Control.ContentMaxWidth]. Measured on the
     * live 新标签页 page: its toolbar spans the pane and the column below it is the page's own 768
     * (`max-w-3xl`), wider than the 672 the thread pages share — a shell cap would clip both.
     */
    val ownContentColumn: Boolean = false,
    /**
     * True when the shell may dock this page's composer at the bottom of its surface. 新标签页 brings
     * no composer at all, so a pane holding that tab must draw no dock: the split would otherwise
     * leave the dock's gutter behind as an empty band under the pane. The app still decides whether a
     * layout has a docked composer at all, so this flag only ever removes the dock.
     */
    val docksComposer: Boolean = true,
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
 * Navigator the shell mounts for [page]. Home, Thread and the 新标签页 page keep the Workspace
 * navigator; Settings, Plugins and Hosts own a dedicated navigator; the Library and Project pages and
 * Account mount none, so the Workspace project tree never leaks into those pages.
 */
public fun pageShellSpec(page: PageId): PageShellSpec =
    when (page) {
        // The reference puts the workspace navigator on Home/Thread only; the Library and Project
        // pages carry their own in-page layout and mount no navigator (measured width 0).
        PageId.Home,
        is PageId.Thread,
        -> PageShellSpec(NavigatorKind.Workspace, resizable = true)

        // 新标签页 keeps the Workspace navigator (the live title strip draws its 隐藏侧边栏 toggle while
        // the tab is on this page), and it brings its own 768 column — see [PageShellSpec].
        PageId.NewTab -> PageShellSpec(
            NavigatorKind.Workspace,
            resizable = true,
            ownContentColumn = true,
            docksComposer = false,
        )

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
