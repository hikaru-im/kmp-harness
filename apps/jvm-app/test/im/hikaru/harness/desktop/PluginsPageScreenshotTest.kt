@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)

package im.hikaru.harness.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import im.hikaru.harness.client.plugins.*
import im.hikaru.harness.ui.foundation.HarnessTheme
import im.hikaru.harness.ui.foundation.HarnessThemeMode
import im.hikaru.harness.ui.foundation.HarnessTokens
import java.io.File
import kotlin.test.*

/**
 * Page viewports at density 1. Test-only DTOs cover visual states without adding production fixtures.
 * PNG/JSON pairs are structural evidence. No same-size original management reference is available;
 * these tests make no pixel parity claim. Popup captures include their own full viewport and scrim.
 */
class PluginsPageScreenshotTest {
    @Test fun captureInventoryAndCapabilityStates() = capture(listOf(Case.List, Case.Empty, Case.Loading,
        Case.Failure, Case.Unavailable, Case.ReadOnly, Case.SavedRefreshFailure))

    @Test fun captureCompactDetailAndLongReaders() = capture(listOf(Case.Detail, Case.LongValues))

    @Test fun captureRegistrationValidationAndRemovalDialogs() = capture(listOf(Case.Registration,
        Case.InvalidRegistration, Case.Removal))

    @Test fun captureLongListEndAfterMouseWheel() = capture(listOf(Case.LongList))

    private fun capture(cases: List<Case>) {
        val directory = captureDirectory()
        for (case in cases) for ((width, height) in listOf(1440 to 900, 600 to 800)) {
            for (theme in listOf(HarnessThemeMode.Light, HarnessThemeMode.Dark)) {
                runSkikoComposeUiTest(size = Size(width.toFloat(), height.toFloat())) {
                    setContent { Page(case, fixture(case), theme) }
                    waitForIdle()
                    val slug = case.slug + "-" + theme.name.lowercase() + "-" + width + "x" + height
                    fun save(suffix: String = "") = pluginsPageCapture(directory,
                        slug + suffix, case.slug + suffix, theme, width, height, case.dialog)
                    when (case) {
                        Case.List -> {
                            onNodeWithTag("installed-plugin-display-0").assertIsDisplayed()
                            val search = onNodeWithTag("plugins-search").fetchSemanticsNode().boundsInRoot
                            assertTrue(search.width <= 321f, "Search must retain a bounded width: $search")
                        }
                        Case.Empty -> onNodeWithText("尚无已登记插件").assertIsDisplayed()
                        Case.Loading -> {
                            onNodeWithText("正在读取插件信息…").assertIsDisplayed()
                            onNodeWithTag("plugins-add-local").assertIsNotEnabled()
                            onNodeWithTag("plugins-search").assertIsNotEnabled()
                            onNodeWithTag("plugins-refresh").assertIsNotEnabled()
                        }
                        Case.Failure, Case.SavedRefreshFailure -> {
                            onNodeWithText("Test plugin read failed").assertIsDisplayed()
                            onNodeWithText("尚无已登记插件").assertDoesNotExist()
                            if (case == Case.Failure) {
                                onNodeWithText("重试").assertExists()
                                onNodeWithText("Retry").assertDoesNotExist()
                            }
                            onNodeWithTag("plugins-search").assertIsNotEnabled()
                            onNodeWithTag("plugins-add-local").assertIsNotEnabled()
                            if (case == Case.SavedRefreshFailure) {
                                onNodeWithTag("plugin-mutation-receipt").assertTextEquals("登记已保存")
                                onNodeWithTag("plugin-saved-refresh-failure").assertIsDisplayed()
                            }
                        }
                        Case.Unavailable -> {
                            onNodeWithText("插件服务不可用").assertIsDisplayed()
                            onNodeWithTag("plugins-refresh").assertIsNotEnabled()
                            onNodeWithTag("plugins-search").assertIsNotEnabled()
                            onNodeWithTag("plugins-add-local").assertDoesNotExist()
                        }
                        Case.ReadOnly -> {
                            onNodeWithTag("plugins-add-local").assertDoesNotExist()
                            onNodeWithTag("plugin-management-unavailable").assertIsNotEnabled()
                        }
                        Case.Registration, Case.InvalidRegistration, Case.Removal -> {
                            val confirmTag = if (case == Case.Removal) "plugin-remove-confirm" else "plugin-register-confirm"
                            val cancelTag = if (case == Case.Removal) "plugin-remove-cancel" else "plugin-register-cancel"
                            val confirm = onNodeWithTag(confirmTag).assertIsDisplayed()
                            if (case == Case.InvalidRegistration) confirm.assertIsNotEnabled() else confirm.assertIsEnabled()
                            onNodeWithTag(cancelTag).assertIsDisplayed().assertIsEnabled()
                            if (case == Case.Removal) {
                                onNodeWithTag("plugin-remove-name", useUnmergedTree = true)
                                    .assertTextEquals(PluginsPageDisplayData.longName)
                                onNodeWithTag("plugin-remove-source", useUnmergedTree = true)
                                    .assertTextEquals(PluginsPageDisplayData.longPath)
                            } else onNodeWithTag("plugin-local-directory").assertIsDisplayed()
                        }
                        Case.LongList -> {
                            pluginsPageWheelTo("plugin-inventory-list", "installed-plugin-display-23")
                            onNodeWithTag("installed-plugin-display-23").assertIsDisplayed()
                            assertTrue(onNodeWithTag("plugin-inventory-list").fetchSemanticsNode()
                                .config[SemanticsProperties.VerticalScrollAxisRange].value() > 0f)
                        }
                        Case.Detail, Case.LongValues -> onNodeWithTag("plugin-detail-name", useUnmergedTree = true)
                            .assertTextEquals(if (case == Case.LongValues) PluginsPageDisplayData.longName else "Review toolkit")
                    }
                    save()
                    if (case == Case.LongValues) {
                        // Nested readers scroll through full values without growing a lazy item indefinitely.
                        for (tag in listOf("plugin-detail-name-reader", "plugin-detail-description-reader", "plugin-detail-source-reader")) {
                            pluginsPageWheelTo("plugin-detail-list", tag)
                            pluginsPageScrollToEnd(tag)
                            waitForIdle()
                            assertTrue(onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
                                .config[SemanticsProperties.VerticalScrollAxisRange].value() > 0f)
                        }
                        pluginsPageWheelTo("plugin-detail-list", "plugin-detail-source-reader")
                        onNodeWithTag("plugin-detail-source", useUnmergedTree = true)
                            .assertTextEquals(longSource)
                        save("-field-ends")
                        pluginsPageWheelTo("plugin-detail-list", "plugin-skill-0-expand")
                        pluginsPageMouseClick("plugin-skill-0-expand")
                        pluginsPageWheelTo("plugin-detail-list", "plugin-skill-0-description-reader")
                        pluginsPageScrollToEnd("plugin-skill-0-description-reader")
                        pluginsPageWheelTo("plugin-detail-list", "plugin-skill-0-name", -200f)
                        pluginsPageScrollToEnd("plugin-skill-0-name", horizontal = true)
                        waitForIdle()
                        save("-skill-expanded")
                        pluginsPageWheelTo("plugin-detail-list", "plugin-app-0-name")
                        pluginsPageScrollToEnd("plugin-app-0-name", horizontal = true)
                        waitForIdle()
                        save("-app-end")
                    }
                }
            }
        }
    }

    @Composable private fun Page(case: Case, initial: InstalledPluginsState, theme: HarnessThemeMode) {
        var state by remember(case, initial) { mutableStateOf(initial) }
        CompositionLocalProvider(LocalDensity provides Density(1f)) {
            HarnessTheme(themeMode = theme) {
                Box(Modifier.fillMaxSize().background(HarnessTokens.Colors.Canvas)
                    .testTag("plugins-page-capture-viewport")) {
                    val onAction: (InstalledPluginsAction) -> Unit = { action ->
                        state = when (action) {
                            is InstalledPluginsAction.Search -> state.copy(searchQuery = action.query)
                            is InstalledPluginsAction.SetEnabledOnly -> state.copy(enabledOnly = action.enabledOnly)
                            is InstalledPluginsAction.ChangeRegistrationDirectory -> state.copy(registrationDirectory = action.directory)
                            InstalledPluginsAction.CancelManagementDialog -> state.copy(registrationDirectory = null, removeConfirmation = null)
                            else -> state
                        }
                    }
                    if (case.detail) InstalledPluginDetailPage(state, onAction)
                    else InstalledPluginsPage(state, onAction)
                }
            }
        }
    }

    private fun fixture(case: Case): InstalledPluginsState {
        val base = PluginsPageDisplayData.state()
        val detail = base.list.let { (it as PluginLoadState.Success).value.first() }.let { plugin ->
            base.copy(selectedId = plugin.id, detail = PluginLoadState.Success(InstalledPluginDetail(plugin,
                skills = PluginSkillsState.Available(listOf(PluginSkillSummary("Review", "Read source before editing."))),
                apps = PluginAppsState.Unavailable("此本地服务消费 Skills 文本；Apps 尚未接入。"))))
        }
        return when (case) {
            Case.List -> base
            Case.Empty -> base.copy(list = PluginLoadState.Success(emptyList()))
            Case.Loading -> base.copy(list = PluginLoadState.Loading, revision = PluginLoadState.Loading)
            Case.Failure -> base.copy(list = failure, revision = PluginLoadState.NotLoaded)
            Case.Unavailable -> base.copy(hasReadAdapter = false, hasManagementAdapter = false,
                list = PluginLoadState.Unavailable(PluginUnavailableReason.PluginServiceNotExposed), revision = PluginLoadState.NotLoaded)
            Case.ReadOnly -> base.copy(hasManagementAdapter = false, revision = PluginLoadState.NotLoaded)
            Case.SavedRefreshFailure -> base.copy(list = failure, revision = PluginLoadState.NotLoaded,
                mutationReceipt = PluginMutationReceipt("test-revision"), managementNotice = "已添加本地插件登记，默认停用。")
            Case.Detail -> detail
            Case.LongValues -> PluginsPageDisplayData.detailState().copy(
                detail = PluginLoadState.Success(PluginsPageDisplayData.detail(
                    PluginsPageDisplayData.longPlugin.copy(source = longSource))))
            Case.Registration -> base.copy(registrationDirectory = PluginsPageDisplayData.longPath)
            Case.InvalidRegistration -> base.copy(registrationDirectory = "relative/source")
            Case.Removal -> PluginsPageDisplayData.detailState().copy(removeConfirmation = PluginsPageDisplayData.longPlugin)
            Case.LongList -> PluginsPageDisplayData.state(24)
        }
    }

    private fun captureDirectory(): File {
        val root = generateSequence(File(System.getProperty("user.dir") ?: ".").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "docs/desktop-ui").isDirectory } ?: error("Cannot locate docs/desktop-ui")
        return File(root, "docs/desktop-ui/captures/plugins-page-2026-10-08")
    }

    private enum class Case(val slug: String, val detail: Boolean = false, val dialog: Boolean = false) {
        List("list"), Empty("empty"), Loading("loading"), Failure("failure"), Unavailable("unavailable"),
        ReadOnly("readonly"), SavedRefreshFailure("saved-refresh-failure"), Detail("detail", true),
        LongValues("long-values", true), Registration("registration", dialog = true),
        InvalidRegistration("registration-invalid", dialog = true), Removal("removal", true, true),
        LongList("list-end"),
    }

    private companion object {
        // Keep this screenshot-local path long enough to overflow the 160dp reader at either capture width.
        val longSource = "/test-only/" + "long directory with spaces/".repeat(120) + "source-end"
        val failure = PluginLoadState.Error(PluginRepositoryError("Test plugin read failed"))
    }
}
