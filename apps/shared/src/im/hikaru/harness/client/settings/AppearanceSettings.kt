package im.hikaru.harness.client.settings

import im.hikaru.harness.ui.foundation.HarnessThemeMode
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
public enum class AppearanceMode {
    System, Light, Dark;

    public fun themeMode(): HarnessThemeMode = when (this) {
        System -> HarnessThemeMode.System
        Light -> HarnessThemeMode.Light
        Dark -> HarnessThemeMode.Dark
    }

    public companion object {
        public fun from(mode: HarnessThemeMode): AppearanceMode = when (mode) {
            HarnessThemeMode.System -> System
            HarnessThemeMode.Light -> Light
            HarnessThemeMode.Dark -> Dark
        }
    }
}

@Serializable
public enum class AppearanceFont { System, Sans, Serif, Monospace }

@Serializable
public enum class AppearanceFontStyle { Regular, Medium, Bold, Italic }

@Serializable
public enum class AppearanceDiffStyle { Color, Symbols }

@Serializable
public enum class AppearanceMotion { System, On, Off }

/** Null colors use the measured client palette; user overrides are validated before saving. */
@Serializable
public data class AppearanceVariant(
    val accent: String? = null,
    val background: String? = null,
    val foreground: String? = null,
    val contrast: Int = 50,
    val uiFont: AppearanceFont = AppearanceFont.System,
    val contentFont: AppearanceFont = AppearanceFont.System,
    val codeFont: AppearanceFont = AppearanceFont.Monospace,
    val uiFontStyle: AppearanceFontStyle = AppearanceFontStyle.Regular,
    val contentFontStyle: AppearanceFontStyle = AppearanceFontStyle.Regular,
    val codeFontStyle: AppearanceFontStyle = AppearanceFontStyle.Regular,
) {
    public fun validate() {
        require(contrast in 0..100) { "对比度必须介于 0 与 100 之间" }
        listOf(accent, background, foreground).forEach { color ->
            require(color == null || Regex("#[0-9a-fA-F]{6}").matches(color)) { "颜色须为 #RRGGBB 格式" }
        }
    }

    /** Reset Advanced preserves the visual card's colors and interface font family. */
    public fun resetAdvanced(): AppearanceVariant = copy(
        contrast = 50, contentFont = AppearanceFont.System, codeFont = AppearanceFont.Monospace,
        uiFontStyle = AppearanceFontStyle.Regular, contentFontStyle = AppearanceFontStyle.Regular,
        codeFontStyle = AppearanceFontStyle.Regular,
    )
}

@Serializable
public data class AppearanceSettings(
    val schemaVersion: Int = 1,
    val mode: AppearanceMode = AppearanceMode.System,
    val light: AppearanceVariant = AppearanceVariant(),
    val dark: AppearanceVariant = AppearanceVariant(),
    val uiFontSize: Int = 14,
    val codeFontSize: Int = 12,
    val usePointerCursors: Boolean = false,
    val fontSmoothing: Boolean = true,
    val diffStyle: AppearanceDiffStyle = AppearanceDiffStyle.Color,
    val reducedMotion: AppearanceMotion = AppearanceMotion.System,
) {
    public fun validate() {
        require(schemaVersion == 1) { "不支持此外观配置版本" }
        require(uiFontSize in 11..16) { "界面字号必须介于 11 与 16 之间" }
        require(codeFontSize in 8..24) { "代码字号必须介于 8 与 24 之间" }
        light.validate()
        dark.validate()
    }

    public fun variant(mode: AppearanceMode): AppearanceVariant {
        require(mode != AppearanceMode.System)
        return if (mode == AppearanceMode.Light) light else dark
    }

    public fun withVariant(mode: AppearanceMode, variant: AppearanceVariant): AppearanceSettings {
        require(mode != AppearanceMode.System)
        return if (mode == AppearanceMode.Light) copy(light = variant) else copy(dark = variant)
    }

    public fun resetAdvanced(): AppearanceSettings = copy(
        light = light.resetAdvanced(), dark = dark.resetAdvanced(),
        uiFontSize = 14, codeFontSize = 12, usePointerCursors = false, fontSmoothing = true,
        diffStyle = AppearanceDiffStyle.Color, reducedMotion = AppearanceMotion.System,
    )
}

/** Client share format, deliberately versioned independently of Codex's native share schema. */
@Serializable
public data class AppearanceThemeShare(val mode: AppearanceMode, val theme: AppearanceVariant)

public object AppearanceThemeCodec {
    private const val Prefix = "harness-theme-v1:"
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = false }

    public fun encode(mode: AppearanceMode, theme: AppearanceVariant): String {
        require(mode != AppearanceMode.System)
        theme.validate()
        return Prefix + json.encodeToString(AppearanceThemeShare(mode, theme))
    }

    public fun decode(text: String): AppearanceThemeShare {
        val value = text.trim()
        require(value.startsWith(Prefix)) { "主题文本格式不受支持" }
        val share = json.decodeFromString<AppearanceThemeShare>(value.removePrefix(Prefix))
        require(share.mode != AppearanceMode.System) { "请选择浅色或深色主题" }
        share.theme.validate()
        return share
    }
}
