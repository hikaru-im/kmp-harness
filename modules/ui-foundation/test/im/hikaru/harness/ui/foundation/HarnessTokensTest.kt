package im.hikaru.harness.ui.foundation

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals

class HarnessTokensTest {
    @Test
    fun desktopShellTokensKeepTheReferenceGeometry() {
        assertEquals(275f, HarnessTokens.Control.SidebarExpanded.value)
        // Rail geometry measured from the Codex window screenshot; see the rail measurement note.
        assertEquals(52f, HarnessTokens.Control.RailWidth.value)
        assertEquals(36f, HarnessTokens.Control.RailItem.value)
        assertEquals(24f, HarnessTokens.Control.RailAvatar.value)
        // --radius-token-row 10px on the rail container.
        assertEquals(10f, HarnessTokens.Control.RailItemRadius.value)
        // The rail group rule is `w-6`, i.e. 24px.
        assertEquals(24f, HarnessTokens.Control.RailDividerWidth.value)
        assertEquals(4f, HarnessTokens.Spacing.One.value)
        assertEquals(16f, HarnessTokens.Corners.Panel.value)
    }

    @Test
    fun geometryMatchesMeasuredAppAsarTokens() {
        // --height-titlebar calc(spacing * 11) = 44px
        assertEquals(44f, HarnessTokens.Control.Titlebar.value)
        // --height-toolbar 46px
        assertEquals(46f, HarnessTokens.Control.Toolbar.value)
        // --height-token-row 36px
        assertEquals(36f, HarnessTokens.Control.Row.value)
        // --icon-leading-size calc(spacing * 5) = 20px
        assertEquals(20f, HarnessTokens.Control.Icon.value)
        // --thread-content-max-width 42rem = 672px
        assertEquals(672f, HarnessTokens.Control.ContentMaxWidth.value)
        assertEquals(728f, HarnessTokens.Control.SettingsContentMaxWidth.value)
        // --composer-radius calc(spacing * 5.5) = 22px
        assertEquals(22f, HarnessTokens.Control.ComposerRadius.value)
    }

    @Test
    fun workspaceNavigatorRowPitchKeepsTheCodexSidebarGap() {
        // Codex `.sidebar-navigation` sets --height-token-row/-nav-row to 30px and uses `gap-px`
        // (1px) between rows, so the measured Codex recent-row pitch is 31px. Keep 30 + 1.
        assertEquals(30f, HarnessTokens.Control.NavRow.value)
        assertEquals(1f, HarnessTokens.Control.NavigatorRowGap.value)
        assertEquals(
            31f,
            (HarnessTokens.Control.NavRow + HarnessTokens.Control.NavigatorRowGap).value,
            "Workspace navigator row pitch must stay at the Codex 31px",
        )
    }

    @Test
    fun lightAndDarkPalettesExposeTheSameSemanticSlots() {
        // Light is the acceptance target: white canvas, near-black text. Dark must not be forced.
        assertEquals(Color(0xFFFFFFFF), HarnessLightColors.Canvas)
        assertEquals(Color(0xFF0D0D0D), HarnessLightColors.Text)
        assertEquals(Color(0xFF5D5D5D), HarnessLightColors.TextMuted)
        assertEquals(Color(0xFF171717), HarnessDarkColors.Canvas)
    }
}
