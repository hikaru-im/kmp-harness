package im.hikaru.harness.ui.foundation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Home has no `app.asar` icon chunk (docs/desktop-ui/reference/codex-rail-icons.md), so its geometry is
 * pinned to the same 2.5..17.5 grid extent as the other rail glyphs. This guards the regression that
 * rendered the Home glyph ~14px where the rail target is 16px.
 */
class HomeGlyphGridTest {
    @Test
    fun homeGlyphUsesTheSharedRailGridExtentAndCentre() {
        assertEquals(2.5f, HomeGlyphGrid.LEFT)
        assertEquals(17.5f, HomeGlyphGrid.RIGHT)
        assertEquals(2.5f, HomeGlyphGrid.TOP)
        assertEquals(17.5f, HomeGlyphGrid.BOTTOM)
        assertEquals(15f, HomeGlyphGrid.SPAN)
        // Square silhouette, centred on (10, 10) like the Clock/Library/Images/Plug rail glyphs.
        assertEquals(HomeGlyphGrid.SPAN, HomeGlyphGrid.BOTTOM - HomeGlyphGrid.TOP)
        assertEquals(10f, (HomeGlyphGrid.LEFT + HomeGlyphGrid.RIGHT) / 2f)
        assertEquals(10f, (HomeGlyphGrid.TOP + HomeGlyphGrid.BOTTOM) / 2f)
    }

    @Test
    fun strokedHomeGlyphReachesTheSixteenOfTwentyRailExtent() {
        // The rail renders a 20px box at RailIconScale with RailIconStrokeRatio; a stroked glyph's
        // drawn extent is its grid span plus the stroke. The Codex rail glyphs fill 16 of the 20px box.
        val box = HarnessTokens.Control.Icon.value
        val artwork = box * HarnessTokens.Control.RailIconScale
        val stroke = artwork * HarnessTokens.Control.RailIconStrokeRatio
        val extent = HomeGlyphGrid.SPAN + stroke

        assertEquals(20f, box)
        assertTrue(extent >= 16f, "Home outline extent " + extent + " is under the 16px rail target")
        assertTrue(extent <= 16.6f, "Home outline extent " + extent + " overshoots the 16px rail target")
    }

    @Test
    fun homeDoorAndEaveStayInsideTheGlyphBounds() {
        assertTrue(HomeGlyphGrid.DOOR_LEFT > HomeGlyphGrid.LEFT)
        assertTrue(HomeGlyphGrid.DOOR_RIGHT < HomeGlyphGrid.RIGHT)
        assertTrue(HomeGlyphGrid.DOOR_LEFT < HomeGlyphGrid.DOOR_RIGHT)
        assertTrue(HomeGlyphGrid.EAVE > HomeGlyphGrid.TOP)
        assertTrue(HomeGlyphGrid.EAVE < HomeGlyphGrid.BOTTOM)
        assertTrue(HomeGlyphGrid.DOOR_TOP > HomeGlyphGrid.EAVE, "door must start below the eave")
        assertTrue(HomeGlyphGrid.DOOR_TOP < HomeGlyphGrid.BOTTOM)
    }
}

