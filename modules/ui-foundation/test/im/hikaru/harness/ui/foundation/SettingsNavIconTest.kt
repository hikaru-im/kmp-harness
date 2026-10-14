package im.hikaru.harness.ui.foundation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Geometry checks for the catalogued Codex Settings glyphs. The point is that the vector data, not a
 * screenshot, is the source of truth, so every check here works on the recorded path data and ink
 * bounds without a renderer (the plain JVM test runtime has no Skia).
 */
class SettingsNavIconTest {
    /** The Settings rows whose Codex artwork is catalogued in this batch. */
    private val catalogued = listOf(
        "General",
        "Notifications",
        "Import",
        "Profile",
        "Appearance",
        "Voice",
        "KeyboardShortcuts",
        "Appshots",
        "Personalization",
        "Pets",
        "Usage",
        "Account",
        "Plugins",
        "ComputerUse",
        "Browser",
        "CloudComputer",
        "Hooks",
        "Connections",
        "CodexCloud",
        "CodeReview",
        "Git",
        "Worktrees",
    )

    @Test
    fun everyCataloguedGlyphHasArtworkOnTheCodexGrid() {
        assertEquals(
            catalogued,
            SettingsNavIcon.entries.map { it.name },
            "adding a Settings glyph must extend this list and the mapping note together",
        )
        SettingsNavIcon.entries.forEach { icon ->
            val artwork = icon.artwork
            assertEquals(SETTINGS_ICON_VIEW_BOX, artwork.viewBox, icon.name)
            assertTrue(artwork.paths.isNotEmpty(), icon.name + " has no path data")
            assertTrue(artwork.inkWidth > 0f && artwork.inkHeight > 0f, icon.name + " has an empty ink box")
            artwork.paths.forEach { assertTrue(it.data.isNotBlank(), icon.name + " has a blank path") }
        }
    }

    @Test
    fun declaredInkBoundsSitInsideTheViewBox() {
        SettingsNavIcon.entries.forEach { icon ->
            val a = icon.artwork
            assertTrue(a.inkLeft >= 0f, icon.name)
            assertTrue(a.inkTop >= 0f, icon.name)
            assertTrue(a.inkLeft + a.inkWidth <= SETTINGS_ICON_VIEW_BOX, icon.name)
            assertTrue(a.inkTop + a.inkHeight <= SETTINGS_ICON_VIEW_BOX, icon.name)
        }
    }

    @Test
    fun inkBoundsSitOnTheCodexOpticalCentre() {
        // `optical.inkBounds` is the factory's own description of where the ink lands. Every glyph
        // here is centred on the 16-grid to within a tenth of a unit except the six whose artwork is
        // genuinely asymmetric: the `gauge-light-16` dial (Usage) keeps half a unit more space below
        // than above, the `qi` pointer (ComputerUse) carries its mass down and right,
        // `web-browser-cursor-light-16` (CloudComputer) hangs the cursor below the window,
        // `cloud-light-16` (CodexCloud) is bottom-heavy, `pull-request-open-light-16` (CodeReview)
        // leans right and `arrow-branch-light-16` (Worktrees) points left. Each recorded offset is
        // allowed explicitly rather than smoothed away; a transcription slip moves the centre by units.
        val centreTolerance =
            mapOf(
                "Usage" to 0.55f,
                "ComputerUse" to 0.4f,
                "CloudComputer" to 0.7f,
                "CodexCloud" to 0.7f,
                "CodeReview" to 0.2f,
                "Worktrees" to 0.7f,
            )
        SettingsNavIcon.entries.forEach { icon ->
            val a = icon.artwork
            val centre = SETTINGS_ICON_VIEW_BOX / 2f
            val centreX = a.inkLeft + a.inkWidth / 2f
            val centreY = a.inkTop + a.inkHeight / 2f
            val tolerance = centreTolerance[icon.name] ?: 0.1f
            assertTrue(kotlin.math.abs(centreX - centre) <= tolerance, icon.name + " centre x " + centreX)
            assertTrue(kotlin.math.abs(centreY - centre) <= tolerance, icon.name + " centre y " + centreY)
        }
    }

    @Test
    fun pathDataStaysOnTheCodexGrid() {
        val number = Regex("-?\\d+(?:\\.\\d+)?")
        SettingsNavIcon.entries.forEach { icon ->
            icon.artwork.paths.forEach { path ->
                assertTrue(path.data.startsWith("M"), icon.name + " does not start a sub-path")
                assertTrue(path.data.endsWith("Z") || path.data.endsWith("z"), icon.name + " is not closed")
                number.findAll(path.data).forEach { match ->
                    // Only absolute coordinates are used, so every number is a grid position; anything
                    // off the 16-unit grid means the glyph was scaled or pasted from another source.
                    val value = match.value.toFloat()
                    assertTrue(value in -2f..(SETTINGS_ICON_VIEW_BOX + 2f), icon.name + " coordinate " + value)
                }
            }
        }
    }
}
