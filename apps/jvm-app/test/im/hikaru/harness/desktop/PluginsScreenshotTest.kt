@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package im.hikaru.harness.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.harness.client.plugins.InstalledPlugin
import im.hikaru.harness.client.plugins.InstalledPluginDetail
import im.hikaru.harness.client.plugins.InstalledPluginDetailPage
import im.hikaru.harness.client.plugins.InstalledPluginsPage
import im.hikaru.harness.client.plugins.InstalledPluginsState
import im.hikaru.harness.client.plugins.PluginAppsState
import im.hikaru.harness.client.plugins.PluginEnabledState
import im.hikaru.harness.client.plugins.PluginId
import im.hikaru.harness.client.plugins.PluginLoadState
import im.hikaru.harness.client.plugins.PluginRepositoryError
import im.hikaru.harness.client.plugins.PluginScope
import im.hikaru.harness.client.plugins.PluginSkillSummary
import im.hikaru.harness.client.plugins.PluginSkillsState
import im.hikaru.harness.client.plugins.PluginUnavailableReason
import im.hikaru.harness.ui.foundation.HarnessTheme
import im.hikaru.harness.ui.foundation.HarnessThemeMode
import im.hikaru.harness.ui.foundation.HarnessTokens
import java.io.File
import javax.imageio.ImageIO
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Static DTOs render the real plugin pages; no connection, repository, controller or plugin files
 * are used. Source paths below are display strings only. All actions are deliberately inert.
 *
 * Every case uses Light/Dark and a density-1 page viewport of 1440x900 or 600x800. These captures
 * exclude the app shell and native title bar. Normal and scrolled PNGs have the viewport dimensions.
 * Dialog PNGs capture the dialog's complete viewport, including the scrim and background page.
 * They have the same viewport dimensions. Captures are structural evidence, not pixel-parity claims.
 */
@OptIn(ExperimentalTestApi::class)
class PluginsScreenshotTest {
    @Test fun capturePopulatedInventory() = capturePages(Scenario.InventoryPopulated)
    @Test fun captureLongTextDetail() = capturePages(Scenario.DetailLongText)
    @Test fun captureEmptyInventory() = capturePages(Scenario.InventoryEmpty)
    @Test fun captureInventoryError() = capturePages(Scenario.InventoryError)
    @Test fun captureInventoryUnavailable() = capturePages(Scenario.InventoryUnavailable)
    @Test fun captureInventoryLoading() = capturePages(Scenario.InventoryLoading)
    @Test fun captureReadOnlyInventory() = capturePages(Scenario.InventoryReadOnly)
    @Test fun captureDetailEmptyResources() = capturePages(Scenario.DetailEmptyResources)
    @Test fun captureDetailError() = capturePages(Scenario.DetailError)
    @Test fun captureDetailUnavailable() = capturePages(Scenario.DetailUnavailable)
    @Test fun captureDetailLoading() = capturePages(Scenario.DetailLoading)

    @Test fun captureRegistrationDialog() = captureDialogs(DialogScenario.Registration)
    @Test fun captureInvalidRegistrationDialog() = captureDialogs(DialogScenario.RegistrationInvalid)
    @Test fun captureRemovalDialog() = captureDialogs(DialogScenario.Removal)

    @Test
    fun captureLongTextSkillsAndAppsAfterScrolling() {
        val directory = captureDir()
        forEachViewport { size, themeSlug, theme ->
            runSkikoComposeUiTest(size = size.composeSize) {
                setContent { Page(Scenario.DetailLongText, detailState(), theme) }
                val list = onNodeWithTag("plugin-detail-list")
                list.performScrollToNode(hasText(longSkillName))
                onNodeWithText(longSkillName).assertIsDisplayed()
                writeBitmap(
                    File(directory, "detail-long-text-skills-page-viewport-$themeSlug-${size.suffix}.png"),
                    onNodeWithTag(viewportTag).captureToImage(), size,
                )
                list.performScrollToNode(hasText(longAppName))
                onNodeWithText(longAppName).assertIsDisplayed()
                writeBitmap(
                    File(directory, "detail-long-text-apps-page-viewport-$themeSlug-${size.suffix}.png"),
                    onNodeWithTag(viewportTag).captureToImage(), size,
                )
            }
        }
    }

    private fun capturePages(scenario: Scenario) {
        val directory = captureDir()
        forEachViewport { size, themeSlug, theme ->
            val file = File(directory, "${scenario.slug}-page-viewport-$themeSlug-${size.suffix}.png")
            val scene = ImageComposeScene(
                width = size.width, height = size.height, density = Density(1f),
                content = { Page(scenario, fixture(scenario), theme) },
            )
            try {
                scene.render()
                scene.render().use { image ->
                    image.encodeToData(EncodedImageFormat.PNG).use { data ->
                        file.writeBytes(requireNotNull(data) { "PNG encoding failed: ${file.name}" }.bytes)
                    }
                }
                verifyPng(file, size.width, size.height)
            } finally {
                scene.close()
            }
        }
    }

    private fun captureDialogs(scenario: DialogScenario) {
        val directory = captureDir()
        forEachViewport { size, themeSlug, theme ->
            val state = when (scenario) {
                DialogScenario.Registration -> inventoryState().copy(registrationDirectory = sourceDirectory)
                DialogScenario.RegistrationInvalid -> inventoryState().copy(registrationDirectory = "relative/plugin")
                DialogScenario.Removal -> detailState().copy(removeConfirmation = longPlugin)
            }
            val page = if (scenario == DialogScenario.Removal) Scenario.DetailLongText else Scenario.InventoryPopulated
            runSkikoComposeUiTest(size = size.composeSize) {
                setContent { Page(page, state, theme) }
                val confirmTag = if (scenario == DialogScenario.Removal) "plugin-remove-confirm" else "plugin-register-confirm"
                val cancelTag = if (scenario == DialogScenario.Removal) "plugin-remove-cancel" else "plugin-register-cancel"
                val confirm = onNodeWithTag(confirmTag).assertIsDisplayed()
                if (scenario == DialogScenario.RegistrationInvalid) confirm.assertIsNotEnabled()
                else confirm.assertIsEnabled()
                onNodeWithTag(cancelTag).assertIsDisplayed().assertIsEnabled()
                if (scenario == DialogScenario.Removal) {
                    onNodeWithText("源目录和源文件都会保留", substring = true).assertIsDisplayed()
                } else {
                    onNodeWithTag("plugin-local-directory").assertIsDisplayed()
                }
                // The dialog root covers the full viewport, including its scrim and background.
                writeBitmap(
                    File(directory, "${scenario.slug}-dialog-page-viewport-$themeSlug-${size.suffix}.png"),
                    onNode(isDialog()).captureToImage(), viewport = size,
                )
            }
        }
    }

    @Composable
    private fun Page(scenario: Scenario, state: InstalledPluginsState, theme: HarnessThemeMode) {
        HarnessTheme(themeMode = theme) {
            Box(Modifier.fillMaxSize().background(HarnessTokens.Colors.Canvas).semantics { testTag = viewportTag }) {
                if (scenario.isDetail) InstalledPluginDetailPage(state, onAction = {})
                else InstalledPluginsPage(state, onAction = {})
            }
        }
    }

    private fun fixture(scenario: Scenario): InstalledPluginsState = when (scenario) {
        Scenario.InventoryPopulated -> inventoryState()
        Scenario.InventoryEmpty -> inventoryState().copy(list = PluginLoadState.Success(emptyList()), selectedId = null)
        Scenario.InventoryError -> inventoryState().copy(list = readError, revision = PluginLoadState.NotLoaded, selectedId = null)
        Scenario.InventoryUnavailable -> unavailableState()
        Scenario.InventoryLoading -> inventoryState().copy(
            list = PluginLoadState.Loading, revision = PluginLoadState.Loading, selectedId = null,
        )
        Scenario.InventoryReadOnly -> inventoryState().copy(hasManagementAdapter = false, revision = PluginLoadState.NotLoaded)
        Scenario.DetailLongText -> detailState()
        Scenario.DetailEmptyResources -> detailState().copy(detail = PluginLoadState.Success(
            InstalledPluginDetail(
                plugin = inventory.last(), apps = PluginAppsState.Available(emptyList()),
                skills = PluginSkillsState.Available(emptyList()),
            ),
        ), selectedId = inventory.last().id)
        Scenario.DetailError -> detailState().copy(detail = readError)
        Scenario.DetailUnavailable -> unavailableState().copy(
            selectedId = longPlugin.id, detail = PluginLoadState.Unavailable(PluginUnavailableReason.PluginServiceNotExposed),
        )
        Scenario.DetailLoading -> detailState().copy(detail = PluginLoadState.Loading)
    }

    private fun inventoryState() = InstalledPluginsState(
        scope = PluginScope(HostId("visual-fixture-local-host"), generation = 1),
        list = PluginLoadState.Success(inventory), selectedId = longPlugin.id,
        hasReadAdapter = true, hasManagementAdapter = true, revision = PluginLoadState.Success("fixture-revision"),
    )

    private fun detailState() = inventoryState().copy(detail = PluginLoadState.Success(
        InstalledPluginDetail(
            plugin = longPlugin,
            skills = PluginSkillsState.Available(listOf(
                PluginSkillSummary(longSkillName,
                    "审查跨平台页面布局、语义标签和浅深主题。只读取静态测试输入，逐项记录标题、说明和目录路径的换行表现，" +
                        "并检查较窄 viewport 中的按钮和内容是否仍可通过滚动访问。"),
                PluginSkillSummary("Capture fixture evidence", "保存确定性截图，并明确区分页面 viewport、滚动内容和包含弹窗的完整 viewport。"),
            )),
            // These are synthetic adapter-reported resource states, not real integrations.
            apps = PluginAppsState.Available(listOf(longAppName, "Fixture issue tracker")),
            unsupportedAssets = listOf("Commands 尚未支持。", "Hooks 尚未支持；当前仅显示静态资源说明。"),
        ),
    ))

    private fun unavailableState() = InstalledPluginsState(
        scope = PluginScope(HostId("visual-fixture-read-only-host"), generation = 1),
        list = PluginLoadState.Unavailable(PluginUnavailableReason.PluginServiceNotExposed),
    )

    private fun writeBitmap(file: File, bitmap: ImageBitmap, viewport: CaptureSize?) {
        Image.makeFromBitmap(bitmap.asSkiaBitmap()).use { image ->
            image.encodeToData(EncodedImageFormat.PNG).use { data ->
                file.writeBytes(requireNotNull(data) { "PNG encoding failed: ${file.name}" }.bytes)
            }
        }
        verifyPng(file, viewport?.width ?: bitmap.width, viewport?.height ?: bitmap.height)
    }

    private fun verifyPng(file: File, width: Int, height: Int) {
        assertTrue(file.isFile && file.length() > 0, "Empty screenshot: ${file.absolutePath}")
        val decoded = requireNotNull(ImageIO.read(file)) { "Unreadable PNG: ${file.absolutePath}" }
        assertEquals(width, decoded.width, file.name)
        assertEquals(height, decoded.height, file.name)
        println("PLUGINS SCREENSHOT: ${file.absolutePath} (${decoded.width}x${decoded.height})")
    }

    private fun captureDir(): File {
        val root = generateSequence(File(System.getProperty("user.dir") ?: ".").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "docs/desktop-ui").isDirectory }
            ?: error("Cannot locate repository docs/desktop-ui from user.dir")
        return File(root, "docs/desktop-ui/captures/plugins-visual").apply {
            check(isDirectory || mkdirs()) { "Cannot create capture directory: $absolutePath" }
        }
    }

    private fun forEachViewport(capture: (CaptureSize, String, HarnessThemeMode) -> Unit) {
        sizes.forEach { size -> themes.forEach { (slug, theme) -> capture(size, slug, theme) } }
    }

    private data class CaptureSize(val width: Int, val height: Int) {
        val suffix: String get() = "${width}x$height"
        val composeSize: Size get() = Size(width.toFloat(), height.toFloat())
    }

    private enum class Scenario(val slug: String, val isDetail: Boolean = false) {
        InventoryPopulated("inventory-populated"),
        InventoryEmpty("inventory-empty"),
        InventoryError("inventory-error"),
        InventoryUnavailable("inventory-unavailable"),
        InventoryLoading("inventory-loading"),
        InventoryReadOnly("inventory-read-only"),
        DetailLongText("detail-long-text", true),
        DetailEmptyResources("detail-empty-resources", true),
        DetailError("detail-error", true),
        DetailUnavailable("detail-unavailable", true),
        DetailLoading("detail-loading", true),
    }

    private enum class DialogScenario(val slug: String) {
        Registration("registration"), RegistrationInvalid("registration-invalid-path"), Removal("removal"),
    }

    private companion object {
        const val viewportTag = "plugins-visual-page-viewport"
        const val sourceDirectory = "/visual-fixtures/plugins/cross-platform-review-with-a-long-directory-name"
        const val longSkillName = "Cross-platform layout review — 跨平台页面与长文本可读性审查"
        const val longAppName = "Fixture documentation workspace — 跨平台产品设计与插件接入说明文档"
        val sizes = listOf(CaptureSize(1440, 900), CaptureSize(600, 800))
        val themes = listOf("light" to HarnessThemeMode.Light, "dark" to HarnessThemeMode.Dark)
        val readError = PluginLoadState.Error(PluginRepositoryError(
            "无法读取当前主机的插件信息。此处展示静态错误 fixture；请刷新后重试，已登记源目录不会因此变更。",
        ))
        val longPlugin = InstalledPlugin(
            id = PluginId("layout-review"),
            name = "Cross-platform review toolkit — 跨平台插件布局与可访问性审查工具",
            description = "为桌面、移动端和共享页面提供布局审查说明，检查浅色与深色主题、长标题、详细描述和源目录换行。" +
                "这个静态插件 fixture 包含足够长的中英文内容，用于观察 600px 页面 viewport 下的文本可读性和滚动布局。",
            version = "2026.10.0-preview-layout-review", source = sourceDirectory, enabled = PluginEnabledState.Enabled,
        )
        val inventory = listOf(
            longPlugin,
            InstalledPlugin(PluginId("notes"), "Workspace notes",
                description = "记录项目审查结论和待办事项。已登记，等待启用。",
                source = "/visual-fixtures/plugins/workspace-notes", version = "1.4.0", enabled = PluginEnabledState.Disabled),
            InstalledPlugin(PluginId("unknown-status"), "Adapter metadata fixture",
                description = "服务未提供版本、来源或启用状态时，页面应保留明确的缺省说明。",
                enabled = PluginEnabledState.Unknown),
        )
    }
}
