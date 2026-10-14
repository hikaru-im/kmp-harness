package im.hikaru.harness.ui.foundation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The Desktop entry must follow the host appearance by default, so the palette selection is resolved
 * through this pure function. Pinning dark again would fail [systemModeFollowsHostAppearance].
 */
class HarnessThemeModeTest {
    @Test
    fun systemModeFollowsHostAppearance() {
        assertTrue(resolveDarkTheme(HarnessThemeMode.System, systemInDarkTheme = true))
        assertFalse(resolveDarkTheme(HarnessThemeMode.System, systemInDarkTheme = false))
    }

    @Test
    fun explicitOverridesIgnoreHostAppearance() {
        assertFalse(resolveDarkTheme(HarnessThemeMode.Light, systemInDarkTheme = true))
        assertFalse(resolveDarkTheme(HarnessThemeMode.Light, systemInDarkTheme = false))
        assertTrue(resolveDarkTheme(HarnessThemeMode.Dark, systemInDarkTheme = false))
        assertTrue(resolveDarkTheme(HarnessThemeMode.Dark, systemInDarkTheme = true))
    }
}
