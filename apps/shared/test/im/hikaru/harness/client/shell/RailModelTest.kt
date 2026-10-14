package im.hikaru.harness.client.shell

import im.hikaru.harness.ui.foundation.HarnessIconName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Rail model rules: how destinations group around the divider rule and how the account chip labels. */
class RailModelTest {
    @Test
    fun railSectionsSplitsConsecutiveSectionsIntoSeparateGroups() {
        val sections =
            railSections(
                listOf(
                    rail("home", RailSection.Primary),
                    rail("scheduled", RailSection.Primary),
                    rail("pinned-projects", RailSection.Secondary),
                    rail("pinned-notes", RailSection.Secondary),
                ),
            )

        assertEquals(2, sections.size)
        assertEquals(listOf("home", "scheduled"), sections[0].map { it.id })
        assertEquals(listOf("pinned-projects", "pinned-notes"), sections[1].map { it.id })
    }

    @Test
    fun railSectionsLeavesASingleSectionUndividedAndHandlesEmptyInput() {
        assertEquals(
            1,
            railSections(listOf(rail("home", RailSection.Primary), rail("scheduled", RailSection.Primary))).size,
        )
        assertTrue(railSections(emptyList()).isEmpty())
    }

    @Test
    fun railSectionsDoesNotMergeNonConsecutiveSections() {
        val sections =
            railSections(
                listOf(
                    rail("a", RailSection.Primary),
                    rail("b", RailSection.Secondary),
                    rail("c", RailSection.Primary),
                ),
            )

        assertEquals(listOf(listOf("a"), listOf("b"), listOf("c")), sections.map { group -> group.map { it.id } })
    }

    @Test
    fun railInitialsDerivesAtMostTwoCharactersFromTheLabel() {
        assertEquals("KH", railInitials("kmp-harness"))
        assertEquals("LH", railInitials("local host"))
        assertEquals("LO", railInitials("local"))
        assertEquals("账号", railInitials("账号"))
        assertEquals("", railInitials("   "))
    }

    @Test
    fun railItemDefaultsToThePrimarySectionWithoutAnAccountLabel() {
        val item = AppShellRailItem("home", "主页", HarnessIconName.Home)

        assertEquals(RailSection.Primary, item.section)
        assertEquals(null, item.initials)
        assertTrue(item.enabled)
    }

    @Test
    fun accountChipIdIsAPublicStableRailIdentifier() {
        // The account chip id is part of the shared rail contract and is read by the test sources on
        // every target, so it must stay a public literal and the synthesized chip must use it.
        assertEquals("account", RAIL_ACCOUNT_ID)
        assertEquals(RAIL_ACCOUNT_ID, harnessRailItems(PageId.Home).last().id)
    }

    @Test
    fun fixedRailKeepsTheInScopeCodexSlotsAndNoSecondGroupUntilPinned() {
        val items = harnessRailItems(PageId.Home, accountInitials = "KH")

        // Codex order minus the out-of-scope slots (图像 / 站点 / 地图 / GPT / 代码审查): the first
        // group is 主页 · 定时任务 · 资料库 · 插件 · 探索, then the account chip. No 最近 slot and no
        // 更多 slot; the second group only appears once a destination is pinned.
        assertEquals(
            listOf("home", "scheduled", "library", "plugins", "explore", RAIL_ACCOUNT_ID),
            items.map { it.id },
        )
        assertEquals(
            listOf(
                HarnessIconName.Home,
                HarnessIconName.Clock,
                HarnessIconName.Library,
                HarnessIconName.Plug,
                HarnessIconName.Explore,
                HarnessIconName.User,
            ),
            items.map { it.icon },
        )
        assertEquals(listOf("主页", "定时任务", "资料库", "插件", "探索", "账号"), items.map { it.label })
        assertTrue(items.none { it.section == RailSection.Secondary })
        // 探索 opens the pin menu, so it never draws the selected highlight.
        assertTrue(items.none { it.id == "explore" && it.selected })
        assertEquals(listOf("home"), items.filter { it.selected }.map { it.id })
        assertEquals("KH", items.last().initials)
    }

    @Test
    fun pinningProjectsAddsASecondGroupInExploreMenuOrder() {
        val unpinned = harnessRailItems(PageId.Home)
        assertTrue(unpinned.none { it.id == "pinned-projects" })

        val pinned = harnessRailItems(PageId.Home, pinned = setOf(RailDestination.Projects))
        val pin = pinned.single { it.id == "pinned-projects" }
        assertEquals(RailSection.Secondary, pin.section)
        assertEquals("项目", pin.label)
        assertEquals(RailDestination.Projects, pin.destination)
        // The pin joins the second rail group, before the bottom account chip.
        assertEquals(listOf("home", "scheduled", "library", "plugins", "explore", "pinned-projects", RAIL_ACCOUNT_ID),
            pinned.map { it.id })
    }

    @Test
    fun railDestinationsRoutePagesWithoutTurningExploreOrAccountIntoPages() {
        assertEquals(PageId.Home, railItemPage("home"))
        assertEquals(PageId.ScheduledTasks(), railItemPage("scheduled"))
        assertEquals(PageId.Library, railItemPage("library"))
        assertEquals(PageId.Plugins, railItemPage("plugins"))
        assertEquals(null, railItemPage("explore"))
        assertEquals(null, railItemPage(RAIL_ACCOUNT_ID))
        assertEquals(RailDestination.More, railItemDestination("explore"))
    }

    @Test
    fun selectionFollowsThePageAcrossTheSlots() {
        val cases =
            listOf(
                "library" to PageId.Library,
                "plugins" to PageId.Plugins,
                "home" to PageId.Home,
                "scheduled" to PageId.ScheduledTasks(),
            )
        cases.forEach { (slot, page) ->
            assertEquals(slot, harnessRailItems(page).single { it.selected }.id, "selected slot for " + page)
        }
        // 项目 is a pinned slot, so Projects only selects once the user pinned it from 探索.
        val pinned = setOf(RailDestination.Projects)
        listOf(PageId.Projects(), PageId.Project("kmp-harness")).forEach { page ->
            assertEquals("pinned-projects", harnessRailItems(page, pinned = pinned).single { it.selected }.id)
            assertTrue(harnessRailItems(page).none { it.selected }, "unpinned Projects has no selected slot: " + page)
        }
        assertTrue(harnessRailItems(PageId.Account).none { it.selected })
    }

    private fun rail(
        id: String,
        section: RailSection,
    ): AppShellRailItem = AppShellRailItem(id, id, HarnessIconName.More, section = section)

    @Test
    fun exploreMenuIsIndependentFromTheAccountMenuAndSelectsNoPage() {
        assertEquals(null, railItemPage("explore"))
        assertTrue(harnessRailItems(PageId.Projects()).none { it.id == "explore" && it.selected })

        // The pin menu owns its own state, separate from the account dropdown state.
        val account = AccountMenuState()
        val explore = ExploreMenuState().open()
        assertTrue(explore.isOpen)
        assertFalse(account.isOpen)
        assertFalse(explore.close().isOpen)
        assertTrue(account.open().isOpen)
    }

    @Test
    fun exploreMenuOffersOnlyTheInScopePinnableDestinations() {
        val items = explorePinMenuItems(pinned = emptySet())

        assertEquals(listOf("projects"), items.map { it.option.id })
        assertEquals(listOf("项目"), items.map { it.option.label })
        assertEquals(listOf(PageId.Projects()), items.map { it.option.page })
        assertTrue(items.none { it.pinned }, "Nothing is pinned by default")

        val pinned = explorePinMenuItems(pinned = setOf(RailDestination.Projects))
        assertTrue(pinned.single { it.option.id == "projects" }.pinned)
    }

    @Test
    fun pinnedDestinationKeepsItsRouteAndSelection() {
        assertEquals(PageId.Projects(), railItemPage("pinned-projects"))
        // 资料库 opens its own placeholder page; the pinned 项目 slot is what selects for Projects.
        assertEquals(
            "pinned-projects",
            harnessRailItems(PageId.Projects(), pinned = setOf(RailDestination.Projects)).single { it.selected }.id,
        )
        assertEquals(
            "library",
            harnessRailItems(PageId.Library, pinned = setOf(RailDestination.Projects)).single { it.selected }.id,
        )
    }
}
