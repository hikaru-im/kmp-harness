@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)

package im.hikaru.harness.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import im.hikaru.harness.client.plugins.*
import im.hikaru.harness.ui.foundation.HarnessTheme
import im.hikaru.harness.ui.foundation.HarnessThemeMode
import kotlin.test.*

/**
 * The plugin detail page must show user-facing copy instead of the parser's internal declaration
 * tokens. Unrecognised descriptors stay visible verbatim so nothing is silently dropped.
 */
class PluginDetailUnsupportedLabelTest {
    private val canonical = listOf(
        "apps (unsupported)" to "Apps 不可用",
        "MCP (unsupported)" to "MCP 不可用",
        "hooks (unsupported)" to "Hooks 不可用",
        "scripts (unsupported)" to "Scripts 不可用",
        "skill attachments (unsupported)" to "技能附件不可用",
        "other capabilities (unsupported)" to "其他能力不可用",
    )

    @Test fun canonicalTokensAreLocalizedAndUnknownDescriptorsPassThrough() =
        runSkikoComposeUiTest(size = Size(1440f, 2400f)) {
            val unknown = "Test adapter resources; integration is unavailable."
            val plugin = PluginsPageDisplayData.plugins().first()
            // Apps stays Available so the "apps (unsupported)" descriptor is not suppressed.
            val detail = InstalledPluginDetail(plugin,
                skills = PluginSkillsState.Available(emptyList()),
                apps = PluginAppsState.Available(emptyList()),
                unsupportedAssets = canonical.map { it.first } + unknown)
            val state = PluginsPageDisplayData.state()
                .copy(selectedId = plugin.id, detail = PluginLoadState.Success(detail))
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    HarnessTheme(themeMode = HarnessThemeMode.Dark) {
                        Box(Modifier.fillMaxSize()) { InstalledPluginDetailPage(state, {}) }
                    }
                }
            }
            waitForIdle()
            onNodeWithTag("plugin-detail-unsupported", useUnmergedTree = true).assertIsDisplayed()
            canonical.forEachIndexed { index, (raw, localized) ->
                onNodeWithTag("plugin-detail-unsupported-" + index, useUnmergedTree = true)
                    .assertTextEquals(localized)
                assertTrue(onAllNodesWithText(raw).fetchSemanticsNodes().isEmpty(),
                    "Raw declaration token must not render: $raw")
            }
            onNodeWithTag("plugin-detail-unsupported-" + canonical.size, useUnmergedTree = true)
                .assertTextEquals(unknown)
            assertTrue(onAllNodesWithTag("plugin-detail-unsupported-" + (canonical.size + 1),
                useUnmergedTree = true).fetchSemanticsNodes().isEmpty(), "No extra unsupported rows expected")
        }
}
