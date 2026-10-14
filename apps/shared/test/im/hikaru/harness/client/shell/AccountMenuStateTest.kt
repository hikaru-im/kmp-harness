package im.hikaru.harness.client.shell

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Structure and routing for the rail avatar's Codex Profile Dropdown. The overlay is a composition,
 * so these tests pin the pure pieces: the open/close flag never touches the current page, and
 * [accountMenuItems] fixes the signed-in/signed-out row order and the action each row dispatches.
 */
class AccountMenuStateTest {
    @Test
    fun openingAndClosingTheMenuNeverTouchesTheCurrentPage() {
        val navigation = NavigationState(currentPage = PageId.Thread("thread-1"))
        val closed = AccountMenuState()
        val open = closed.open()

        assertFalse(closed.isOpen)
        assertTrue(open.isOpen)
        // close() is what the outside-click scrim invokes; it flips only the menu flag.
        assertFalse(open.close().isOpen)
        assertEquals(PageId.Thread("thread-1"), navigation.currentPage)
    }

    @Test
    fun signedInDropdownMatchesTheCodexRowOrder() {
        val items = accountMenuItems(AccountMenuState(isLoggedIn = true, canLogin = false))

        assertEquals(
            listOf(
                "account-menu-settings",
                "account-menu-profile",
                "account-menu-logout",
            ),
            items.map { it.testTag },
        )
        assertEquals(
            listOf(
                AccountMenuAction.Settings,
                AccountMenuAction.Profile,
                AccountMenuAction.Logout,
            ),
            items.map { it.action },
        )
        // The separator sits between Profile and Log out, as in the Codex dropdown.
        assertEquals(listOf("account-menu-profile"), items.filter { it.separatorAfter }.map { it.testTag })
    }

    @Test
    fun signedOutDropdownOffersTheSignInRowAndFollowsCanLogin() {
        val enabled = accountMenuItems(AccountMenuState(isLoggedIn = false, canLogin = true))
        assertEquals(
            listOf("account-menu-settings", "account-menu-sign-in"),
            enabled.map { it.testTag },
        )
        assertEquals(AccountMenuAction.Login, enabled.last().action)
        assertTrue(enabled.last().enabled)

        val disabled = accountMenuItems(AccountMenuState(isLoggedIn = false, canLogin = false))
        assertFalse(disabled.last().enabled)
    }

    @Test
    fun everyAccountMenuActionIsReachableFromADropdownItem() {
        val signedIn = accountMenuItems(AccountMenuState(isLoggedIn = true)).map { it.action }.toSet()
        val signedOut = accountMenuItems(AccountMenuState(isLoggedIn = false)).map { it.action }.toSet()
        assertEquals(AccountMenuAction.entries.toSet(), signedIn + signedOut)
    }

    @Test
    fun hostsAndSettingsDestinationsRouteToTheirPages() {
        assertEquals(PageId.Settings(), AccountMenuDestination.Settings.toPageId())
    }
}

