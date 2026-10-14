package im.hikaru.harness.ui.foundation

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class HarnessThemeCustomizationTest {
    @Test
    fun anAccentEditPreservesTheMeasuredNeutralPalette() {
        listOf(HarnessLightColors, HarnessDarkColors).forEach { base ->
            val actual = HarnessThemeVariant(accent = Color(0xff123456)).colors(base)
            assertEquals(Color(0xff123456), actual.Accent)
            assertEquals(base.copy(Accent = actual.Accent, AccentStrong = actual.AccentStrong,
                BorderFocused = actual.BorderFocused, OnAccent = actual.OnAccent), actual)
            assertEquals(base, HarnessThemeVariant().colors(base))
        }
    }

    @Test
    fun nestedThemesApplyTheUiScaleOnceAndKeepTheCodeSizeIndependent() = runComposeUiTest {
        var outerScale = 0f
        var innerScale = 0f
        var renderedCodeSize = 0f
        val customization = HarnessThemeCustomization(uiFontSize = 16, codeFontSize = 21)
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(2f, 1.2f)) {
                HarnessTheme(HarnessThemeMode.Light, customization) {
                    val density = LocalDensity.current
                    SideEffect { outerScale = density.fontScale }
                    HarnessTheme(HarnessThemeMode.Light) {
                        val innerDensity = LocalDensity.current
                        val code = LocalHarnessCodeTextStyle.current
                        SideEffect { innerScale = innerDensity.fontScale; renderedCodeSize = code.fontSize.value * innerDensity.fontScale }
                    }
                }
            }
        }
        runOnIdle {
            assertEquals(1.2f * 16f / 14f, outerScale, .0001f)
            assertEquals(outerScale, innerScale, .0001f)
            assertEquals(21f * 1.2f, renderedCodeSize, .0001f)
        }
    }
}
