package im.hikaru.harness.ui.foundation

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Theme selection for the shared shell. [System] follows the host appearance and is the default;
 * [Light] and [Dark] are explicit overrides used by acceptance captures and future settings UI.
 */
public enum class HarnessThemeMode {
    System,
    Light,
    Dark,
}

/**
 * The host appearance the composition should read for [HarnessThemeMode.System], or `null` while the
 * platform has no live source for it — in which case [isSystemInDarkTheme] answers as it always did.
 *
 * A platform whose runtime re-reads the host appearance on every composition has no need to provide
 * this. Compose's own desktop runtime does not: on 1.11 `isSystemInDarkTheme()` reads
 * `LocalSystemTheme`, which has no provider there, so the value is taken once and never invalidated —
 * switching 系统 settings would leave the window on whatever it started with. A host (see the desktop
 * app) that samples the appearance itself publishes it here instead, which makes every consumer of
 * [harnessSystemInDarkTheme] — the palette, the title bar, the appearance page's previews — follow the
 * host live.
 */
public val LocalHarnessSystemDark: ProvidableCompositionLocal<Boolean?> = staticCompositionLocalOf { null }

/**
 * Whether the host is currently in dark appearance. Reads [LocalHarnessSystemDark] when it is provided
 * and only falls back to [isSystemInDarkTheme] when it is not.
 */
@Composable
@ReadOnlyComposable
public fun harnessSystemInDarkTheme(): Boolean = LocalHarnessSystemDark.current ?: isSystemInDarkTheme()

/**
 * Pure resolution of the effective palette from the requested [mode] and the host appearance.
 * Kept free of Compose APIs so the system-follow behaviour is covered by unit tests.
 */
public fun resolveDarkTheme(mode: HarnessThemeMode, systemInDarkTheme: Boolean): Boolean =
    when (mode) {
        HarnessThemeMode.System -> systemInDarkTheme
        HarnessThemeMode.Light -> false
        HarnessThemeMode.Dark -> true
    }

/** Effective [HarnessThemeMode] for the current composition; [HarnessThemeMode.System] reads the host. */
@Composable
@ReadOnlyComposable
private fun HarnessThemeMode.effectiveDarkTheme(): Boolean = resolveDarkTheme(this, harnessSystemInDarkTheme())

/**
 * The palette [HarnessTheme] provides under [customization] for [dark], resolved without composing.
 *
 * [HarnessTheme] lays out a `Surface`, so a caller that must not introduce a layout node — Jewel's
 * [org.jetbrains.jewel.window.TitleBar], which the decorated window only recognises as its title bar
 * while it stays a direct child — cannot wrap itself in the theme. It provides the local itself with
 * this instead, and its `HarnessTokens.Colors` then read the same palette everything else does.
 */
public fun harnessColors(dark: Boolean, customization: HarnessThemeCustomization): HarnessColors {
    val variant = if (dark) customization.dark else customization.light
    return variant.colors(if (dark) HarnessDarkColors else HarnessLightColors)
}

private fun HarnessColors.toColorScheme(dark: Boolean) =
    if (dark) {
        darkColorScheme(
            primary = Accent,
            onPrimary = OnAccent,
            secondary = AccentStrong,
            background = Canvas,
            onBackground = Text,
            surface = Surface,
            onSurface = Text,
            surfaceVariant = SurfaceElevated,
            onSurfaceVariant = TextMuted,
            outline = Border,
            outlineVariant = BorderSubtle,
            error = Danger,
            onError = Canvas,
        )
    } else {
        lightColorScheme(
            primary = Accent,
            onPrimary = OnAccent,
            secondary = AccentStrong,
            background = Canvas,
            onBackground = Text,
            surface = SurfaceElevated,
            onSurface = Text,
            surfaceVariant = SurfaceSecondary,
            onSurfaceVariant = TextMuted,
            outline = Border,
            outlineVariant = BorderSubtle,
            error = Danger,
            onError = Canvas,
        )
    }

/** Type scale aligned to Codex light: 12 / 14 / 16 / 18 with 1.4 line height and the UI font. */
private val HarnessTypography =
    Typography(
        bodyLarge = TextStyle(fontFamily = HarnessTokens.Fonts.Ui, fontSize = HarnessTokens.Text.Sm, lineHeight = HarnessTokens.Text.BodyLineHeight),
        bodyMedium = TextStyle(fontFamily = HarnessTokens.Fonts.Ui, fontSize = 13.sp, lineHeight = 18.sp),
        bodySmall = TextStyle(fontFamily = HarnessTokens.Fonts.Ui, fontSize = 12.sp, lineHeight = 16.sp),
        labelLarge = TextStyle(fontFamily = HarnessTokens.Fonts.Ui, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
        labelMedium = TextStyle(fontFamily = HarnessTokens.Fonts.Ui, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
        titleMedium = TextStyle(fontFamily = HarnessTokens.Fonts.Ui, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
        titleLarge = TextStyle(fontFamily = HarnessTokens.Fonts.Ui, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
        headlineSmall = TextStyle(fontFamily = HarnessTokens.Fonts.Ui, fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    )

private val HarnessShapes =
    Shapes(
        small = RoundedCornerShape(HarnessTokens.Corners.Small),
        medium = RoundedCornerShape(HarnessTokens.Corners.Medium),
        large = RoundedCornerShape(HarnessTokens.Corners.Large),
        extraLarge = RoundedCornerShape(HarnessTokens.Corners.Panel),
    )

/**
 * Shared theme. The default [HarnessThemeMode.System] follows the host appearance so light and dark
 * are both first-class; callers can pin [HarnessThemeMode.Light] or [HarnessThemeMode.Dark].
 * Set [drawBackground] to false when the parent, such as a native title bar, owns the surface.
 */
@Composable
public fun HarnessTheme(
    themeMode: HarnessThemeMode = HarnessThemeMode.System,
    customization: HarnessThemeCustomization = LocalHarnessThemeCustomization.current,
    drawBackground: Boolean = true,
    content: @Composable () -> Unit,
) {
    val darkTheme = themeMode.effectiveDarkTheme()
    val variant = if (darkTheme) customization.dark else customization.light
    val colors = harnessColors(darkTheme, customization)
    val baseDensity = LocalHarnessBaseDensity.current ?: LocalDensity.current
    val scale = customization.uiFontSize / 14f
    fun TextStyle.withUiFont(): TextStyle = copy(
        fontFamily = variant.uiFont, fontStyle = variant.uiStyle,
        fontWeight = if (variant.uiWeight == FontWeight.Normal) fontWeight else variant.uiWeight,
    )
    val typography = HarnessTypography.copy(
        bodyLarge = HarnessTypography.bodyLarge.withUiFont(), bodyMedium = HarnessTypography.bodyMedium.withUiFont(),
        bodySmall = HarnessTypography.bodySmall.withUiFont(), labelLarge = HarnessTypography.labelLarge.withUiFont(),
        labelMedium = HarnessTypography.labelMedium.withUiFont(), titleMedium = HarnessTypography.titleMedium.withUiFont(),
        titleLarge = HarnessTypography.titleLarge.withUiFont(), headlineSmall = HarnessTypography.headlineSmall.withUiFont(),
    )
    CompositionLocalProvider(
        LocalHarnessColors provides colors, LocalHarnessThemeCustomization provides customization,
        LocalHarnessBaseDensity provides baseDensity,
        LocalDensity provides Density(baseDensity.density, baseDensity.fontScale * scale),
        LocalHarnessContentTextStyle provides typography.bodyLarge.copy(
            fontFamily = variant.contentFont, fontWeight = variant.contentWeight, fontStyle = variant.contentStyle,
        ),
        LocalHarnessCodeTextStyle provides typography.bodyMedium.copy(
            fontFamily = variant.codeFont, fontWeight = variant.codeWeight, fontStyle = variant.codeStyle,
            fontSize = (customization.codeFontSize / scale).sp, lineHeight = (customization.codeFontSize * 1.5f / scale).sp,
        ),
    ) {
        MaterialTheme(
            colorScheme = colors.toColorScheme(darkTheme),
            typography = typography,
            shapes = HarnessShapes,
        ) {
            Surface(
                color = if (drawBackground) colors.Canvas else Color.Transparent,
                contentColor = colors.Text,
                content = content,
            )
        }
    }
}
