package im.hikaru.harness.ui.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density

public data class HarnessThemeVariant(
    val accent: Color? = null,
    val background: Color? = null,
    val foreground: Color? = null,
    val contrast: Int = 50,
    val uiFont: FontFamily = FontFamily.SansSerif,
    val contentFont: FontFamily = FontFamily.SansSerif,
    val codeFont: FontFamily = FontFamily.Monospace,
    val uiWeight: FontWeight = FontWeight.Normal,
    val uiStyle: FontStyle = FontStyle.Normal,
    val contentWeight: FontWeight = FontWeight.Normal,
    val contentStyle: FontStyle = FontStyle.Normal,
    val codeWeight: FontWeight = FontWeight.Normal,
    val codeStyle: FontStyle = FontStyle.Normal,
) {
    public fun colors(base: HarnessColors): HarnessColors {
        // Preserve the default palette when there are no color/contrast edits.
        if (accent == null && background == null && foreground == null && contrast == 50) return base
        val canvas = background ?: base.Canvas
        val ink = foreground ?: base.Text
        val accentColor = accent ?: base.Accent
        val dark = canvas.luminance() < 0.5f
        val onAccent = if (accentColor.luminance() > 0.45f) Color(0xFF0D0D0D) else Color.White
        if (background == null && foreground == null && contrast == 50) return base.copy(
            Accent = accentColor, BorderFocused = accentColor,
            SurfaceSelected = lerp(canvas, accentColor, if (dark) 0.14f else 0.10f),
            AccentStrong = lerp(accentColor, if (dark) Color.White else Color.Black, 0.2f), OnAccent = onAccent,
        )
        return base.copy(
            Canvas = canvas,
            // Keep all page canvases in sync; shell and navigation derive from the workspace.
            AppBackground = if (background == null) base.AppBackground else lerp(canvas, ink, if (dark) 0.065f else 0.08f),
            NavigatorSurface = if (background == null) base.NavigatorSurface else lerp(canvas, ink, if (dark) 0.03f else 0.04f),
            SurfaceCanvas = canvas,
            NavigatorPeekSurface = if (background == null) base.NavigatorPeekSurface else lerp(canvas, ink, if (dark) 0.08f else 0f),
            Surface = if (background == null) base.Surface else lerp(canvas, ink, 0.04f),
            SurfaceSecondary = if (background == null) base.SurfaceSecondary else lerp(canvas, ink, 0.06f),
            SurfaceElevated = if (background == null) base.SurfaceElevated else lerp(canvas, ink, if (dark) 0.08f else 0f),
            SurfaceSelected = lerp(canvas, accentColor, if (dark) 0.14f else 0.10f),
            Text = ink,
            TextMuted = lerp(canvas, ink, 0.4f + contrast * 0.004f),
            TextDisabled = lerp(canvas, ink, 0.35f),
            TextSecondary = ink.copy(alpha = 0.65f),
            SurfaceKeycap = ink.copy(alpha = 0.065f),
            SurfaceRing = ink.copy(alpha = 0.04f),
            SurfaceRingStrong = ink.copy(alpha = 0.08f),
            // Title-bar chrome keeps the measured alpha inks, derived from the customised ink so a
            // recoloured theme does not keep the stock light or dark channels.
            ChromeIcon = ink.copy(alpha = 0.494f),
            ChromeText = ink,
            ChromeFill = ink.copy(alpha = 0.1f),
            ChromeSeam = ink.copy(alpha = 0.25f),
            ChromeSeamEdge = ink.copy(alpha = 0.07f),
            ChromeRule = ink.copy(alpha = 0.078f),
            Border = lerp(canvas, ink, 0.1f + contrast * 0.002f),
            BorderSubtle = lerp(canvas, ink, 0.05f + contrast * 0.001f),
            BorderStrong = lerp(canvas, ink, 0.16f + contrast * 0.003f),
            BorderFocused = accentColor, Accent = accentColor,
            AccentStrong = lerp(accentColor, if (dark) Color.White else Color.Black, 0.2f),
            OnAccent = onAccent,
            CodeBackground = if (background == null) base.CodeBackground else lerp(canvas, ink, 0.03f),
        )
    }
}

public data class HarnessThemeCustomization(
    val light: HarnessThemeVariant = HarnessThemeVariant(),
    val dark: HarnessThemeVariant = HarnessThemeVariant(),
    val uiFontSize: Int = 14,
    val codeFontSize: Int = 12,
    val usePointerCursors: Boolean = false,
    val reduceMotion: Boolean? = null,
    val diffSymbols: Boolean = false,
)

public val LocalHarnessThemeCustomization = staticCompositionLocalOf { HarnessThemeCustomization() }
internal val LocalHarnessBaseDensity = staticCompositionLocalOf<Density?> { null }
public val LocalHarnessContentTextStyle = staticCompositionLocalOf { TextStyle.Default }
public val LocalHarnessCodeTextStyle = staticCompositionLocalOf { TextStyle.Default }

/** Only interactive controls opt in; editable text keeps its native text cursor. */
@Composable
public fun Modifier.harnessPointer(enabled: Boolean = true): Modifier = pointerHoverIcon(
    if (enabled && LocalHarnessThemeCustomization.current.usePointerCursors) PointerIcon.Hand else PointerIcon.Default,
)
