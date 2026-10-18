package im.hikaru.harness.client.shell

import im.hikaru.harness.ui.foundation.HarnessIconName

/** App-level destinations rendered by the fixed rail. They are independent from workspace pages. */
public enum class RailDestination {
    Home,
    Scheduled,
    Library,
    Plugins,
    More,

    /** The workspace Projects destination. It has no fixed slot; the 探索 menu pins it. */
    Projects,
}

/**
 * The fixed App Rail, following the Codex rail contract.
 *
 * Codex's rail is built-in destinations
 * (`首页 · 图像 · 定时任务 · 资料库 · 插件 · 探索`), then a rule, then the destinations the user has
 * *pinned* from the 探索 menu (`项目 · 站点 · 地图 · GPT · 代码审查` in the reference). This client
 * keeps the same shape with a narrower scope:
 *
 * - First group: `主页 · 定时任务 · 资料库 · 插件`, then `探索`.
 * - `资料库` opens this client's Project workspace, and is labelled 「项目」 to match that surface.
 * - `探索` only opens a pin menu; it never replaces the page. It carries the selected highlight while
 *   the current page is a destination inside its menu that has not been pinned.
 * - Second group: the pinned destinations, initially empty. Pinning `项目` adds the `项目` slot there.
 *
 * `图像 · 站点 · 地图 · GPT · 代码审查` are outside the confirmed scope and are not offered anywhere.
 *
 * The [AppShellRailItem] list is shared by the app, the screenshot fixtures and the rail tests so the
 * slot contract is asserted in one place.
 */
public fun harnessRailItems(
    selected: PageId,
    accountInitials: String? = null,
    pinned: Set<RailDestination> = emptySet(),
): List<AppShellRailItem> =
    primaryRailItems(selected, pinned) +
        pinnedSecondSection(pinned, selected) +
        AppShellRailItem(
            id = RAIL_ACCOUNT_ID,
            label = "账号",
            icon = HarnessIconName.User,
            selected = false,
            initials = accountInitials,
        )

private fun primaryRailItems(selected: PageId, pinned: Set<RailDestination>): List<AppShellRailItem> =
    listOf(
        AppShellRailItem(
            id = "home",
            label = "主页",
            icon = HarnessIconName.Home,
            selected = selected is PageId.Home,
            section = RailSection.Primary,
            destination = RailDestination.Home,
        ),
        AppShellRailItem(
            id = "scheduled",
            label = "定时任务",
            icon = HarnessIconName.Clock,
            selected = selected is PageId.ScheduledTasks,
            section = RailSection.Primary,
            destination = RailDestination.Scheduled,
        ),
        AppShellRailItem(
            id = "library",
            label = "资料库",
            icon = HarnessIconName.Library,
            selected = selected is PageId.Library,
            section = RailSection.Primary,
            destination = RailDestination.Library,
        ),
        AppShellRailItem(
            id = "plugins",
            label = "插件",
            icon = HarnessIconName.Plug,
            selected = selected is PageId.Plugins || selected is PageId.PluginDetail,
            section = RailSection.Primary,
            destination = RailDestination.Plugins,
        ),
        // 探索 opens a pin menu. While the current page belongs to one of its destinations and that
        // destination is not pinned, 探索 itself is the selected slot (Codex marks it
        // `data-selected` + `aria-current="page"`); a pinned destination selects its own slot.
        AppShellRailItem(
            id = "explore",
            label = "探索",
            icon = HarnessIconName.Explore,
            selected = exploreHoldsCurrentPage(selected, pinned),
            section = RailSection.Primary,
            destination = RailDestination.More,
        ),
    )

/**
 * True while the current page is a destination the 探索 menu offers and that destination has not
 * been pinned. Codex then paints 探索 as the selected rail item; once the destination is pinned it
 * selects its own slot and 探索 goes back to its resting state.
 */
internal fun exploreHoldsCurrentPage(selected: PageId, pinned: Set<RailDestination>): Boolean =
    explorePinOptions().any { it.destination !in pinned && it.selects(selected) }

/** The pinned destinations render in [explorePinOptions] order, so the rail never reorders them. */
private fun pinnedSecondSection(
    pinned: Set<RailDestination>,
    selected: PageId,
): List<AppShellRailItem> =
    explorePinOptions().filter { it.destination in pinned }.map { option ->
        AppShellRailItem(
            id = "pinned-${option.id}",
            label = option.label,
            icon = option.icon,
            selected = option.selects(selected),
            section = RailSection.Secondary,
            destination = option.destination,
        )
    }

/**
 * Page a rail destination opens. 探索 opens the pin menu, which this function reports as null.
 */
public fun railDestinationPage(destination: RailDestination): PageId? =
    when (destination) {
        RailDestination.Home -> PageId.Home
        RailDestination.Scheduled -> PageId.ScheduledTasks()
        RailDestination.Library -> PageId.Library
        RailDestination.Plugins -> PageId.Plugins
        RailDestination.More -> null
        RailDestination.Projects -> PageId.Projects()
    }

public fun railItemDestination(id: String): RailDestination? =
    when (id) {
        "home" -> RailDestination.Home
        "scheduled" -> RailDestination.Scheduled
        "library" -> RailDestination.Library
        "plugins" -> RailDestination.Plugins
        "explore" -> RailDestination.More
        else -> null
    }

/** Compatibility helper for callers that still start from a rail item id. */
public fun railItemPage(id: String): PageId? =
    railItemDestination(id)?.let(::railDestinationPage) ?: explorePinOptions().firstOrNull { "pinned-${it.id}" == id }?.page

/**
 * Open/closed state of the 探索 pin menu. It never replaces the current page, and it is independent
 * from [AccountMenuState]: the 探索 button owns this menu, the account chip owns the account menu.
 */
public data class ExploreMenuState(
    val isOpen: Boolean = false,
) {
    public fun open(): ExploreMenuState = copy(isOpen = true)

    public fun close(): ExploreMenuState = copy(isOpen = false)
}

/** A destination the 探索 menu can pin to the rail's second group. */
public data class PinOption(
    val id: String,
    val label: String,
    val icon: HarnessIconName,
    val destination: RailDestination,
    val page: PageId,
) {
    internal fun selects(selected: PageId): Boolean = when (id) {
        "projects" -> selected is PageId.Projects || selected is PageId.Project
        else -> false
    }
}

/**
 * Destinations the 探索 menu offers to pin. The reference lists `项目 · 站点 · 地图 · GPT · 代码审查`;
 * this client keeps only `项目`, because the other four are outside the confirmed scope and pinned
 * entries inherit the list order here.
 */
public fun explorePinOptions(): List<PinOption> =
    listOf(
        PinOption("projects", "项目", HarnessIconName.Projects, RailDestination.Projects, PageId.Projects()),
    )

/** Menu rows in declared order, each carrying whether the destination is currently pinned. */
public fun explorePinMenuItems(pinned: Set<RailDestination>): List<PinMenuItem> =
    explorePinOptions().map { PinMenuItem(it, it.destination in pinned) }

public data class PinMenuItem(
    val option: PinOption,
    val pinned: Boolean,
)
