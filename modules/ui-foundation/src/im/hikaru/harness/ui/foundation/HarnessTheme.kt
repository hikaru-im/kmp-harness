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
import androidx.compose.runtime.ReadOnlyComposable
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
private fun HarnessThemeMode.effectiveDarkTheme(): Boolean = resolveDarkTheme(this, isSystemInDarkTheme())

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
        bodyLarge = TextStyle(fontFamily = HarnessTokens.Fonts.Ui, fontSize = 14.sp, lineHeight = 20.sp),
        bodyMedium = TextStyle(fontFamily = HarnessTokens.Fonts.Ui, fontSize = 13.sp, lineHeight = 18.sp),
        bodySmall = TextStyle(fontFamily = HarnessTokens.Fonts.Ui, fontSize = 12.sp, lineHeight = 16.sp),
        labelLarge = TextStyle(fontFamily = HarnessTokens.Fonts.Ui, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
        labelMedium = TextStyle(fontFamily = HarnessTokens.Fonts.Ui, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
        titleMedium = TextStyle(fontFamily = HarnessTokens.Fonts.Ui, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
        titleLarge = TextStyle(fontFamily = HarnessTokens.Fonts.Ui, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
        headlineSmall = TextStyle(fontFamily = HarnessTokens.Fonts.Ui, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
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
 */
@Composable
public fun HarnessTheme(
    themeMode: HarnessThemeMode = HarnessThemeMode.System,
    customization: HarnessThemeCustomization = LocalHarnessThemeCustomization.current,
    content: @Composable () -> Unit,
) {
    val darkTheme = themeMode.effectiveDarkTheme()
    val variant = if (darkTheme) customization.dark else customization.light
    val colors = variant.colors(if (darkTheme) HarnessDarkColors else HarnessLightColors)
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
                color = colors.Canvas,
                contentColor = colors.Text,
                content = content,
            )
        }
    }
}
