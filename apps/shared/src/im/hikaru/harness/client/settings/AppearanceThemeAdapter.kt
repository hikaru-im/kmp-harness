package im.hikaru.harness.client.settings

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import im.hikaru.harness.ui.foundation.HarnessThemeCustomization
import im.hikaru.harness.ui.foundation.HarnessThemeVariant

public fun AppearanceSettings.themeCustomization(): HarnessThemeCustomization = HarnessThemeCustomization(
    light = light.themeVariant(), dark = dark.themeVariant(),
    uiFontSize = uiFontSize, codeFontSize = codeFontSize, usePointerCursors = usePointerCursors,
    reduceMotion = when (reducedMotion) { AppearanceMotion.System -> null; AppearanceMotion.On -> true; AppearanceMotion.Off -> false },
    diffSymbols = diffStyle == AppearanceDiffStyle.Symbols,
)

private fun String?.themeColor(): Color? = this?.let { Color(0xFF000000L or it.removePrefix("#").toLong(16)) }
private fun AppearanceFont.family(): FontFamily = when (this) {
    AppearanceFont.System, AppearanceFont.Sans -> FontFamily.SansSerif
    AppearanceFont.Serif -> FontFamily.Serif
    AppearanceFont.Monospace -> FontFamily.Monospace
}
private fun AppearanceFontStyle.weight(): FontWeight = when (this) {
    AppearanceFontStyle.Medium -> FontWeight.Medium
    AppearanceFontStyle.Bold -> FontWeight.Bold
    else -> FontWeight.Normal
}
private fun AppearanceFontStyle.style(): FontStyle = if (this == AppearanceFontStyle.Italic) FontStyle.Italic else FontStyle.Normal
private fun AppearanceVariant.themeVariant(): HarnessThemeVariant = HarnessThemeVariant(
    accent = accent.themeColor(), background = background.themeColor(), foreground = foreground.themeColor(), contrast = contrast,
    uiFont = uiFont.family(), contentFont = contentFont.family(), codeFont = codeFont.family(),
    uiWeight = uiFontStyle.weight(), uiStyle = uiFontStyle.style(),
    contentWeight = contentFontStyle.weight(), contentStyle = contentFontStyle.style(),
    codeWeight = codeFontStyle.weight(), codeStyle = codeFontStyle.style(),
)
