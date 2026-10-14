@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE") // Exercise the existing internal production select without widening its API.

package im.hikaru.harness.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import im.hikaru.harness.client.git.*
import im.hikaru.harness.client.page.LocalProjectPicker
import im.hikaru.harness.client.projects.*
import im.hikaru.harness.ui.foundation.*
import java.io.File
import java.security.MessageDigest
import javax.imageio.ImageIO
import kotlinx.serialization.json.*
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import kotlin.test.*

/** Real production controls; pointer roots and wheel units come from the existing desktop helpers. */
@OptIn(ExperimentalTestApi::class)
class MenuPickerMigrationInteractionTest {
    @Test fun popupPickers600Light() = pickerMatrix(600, 800, HarnessThemeMode.Light)
    @Test fun popupPickers600Dark() = pickerMatrix(600, 800, HarnessThemeMode.Dark)
    @Test fun popupPickers1440Light() = pickerMatrix(1440, 900, HarnessThemeMode.Light)
    @Test fun popupPickers1440Dark() = pickerMatrix(1440, 900, HarnessThemeMode.Dark)

    private fun pickerMatrix(width: Int, height: Int, theme: HarnessThemeMode) {
        val projects = ProjectsRecentFixture()
        val git = GitWorktreesPageFixture()
        try {
            runSkikoComposeUiTest(size = Size(width.toFloat(), height.toFloat())) {
                val ready = ProjectsRecentDisplayData.projects(ProjectsRecentDisplayData.binding(projects.connection))
                val duplicateName = "Duplicate project with a deliberately long name for complete wrapped menu labels"
                val duplicates = ready.projects.take(2).map { it.copy(name = duplicateName) }
                var projectState by mutableStateOf(ready.copy(snapshot = ready.snapshot!!.copy(projects = duplicates), selectedProjectId = null))
                val selectedIds = mutableListOf<LocalProjectId?>()
                var managementCalls = 0
                val longPath = GitWorktreesPageFixture.longRoot
                val lastPath = longPath + "/last-option"
                val paths = listOf(GitWorktreesPageFixture.root, longPath) +
                    (1..24).map { "/test-only/option-$it" } + lastPath
                var selectedPath by mutableStateOf(GitWorktreesPageFixture.root)
                val pathCalls = mutableListOf<String>()
                var menuMounted = false
                val events = mutableListOf<GitSelectEvent>()
                val observer: (GitSelectEvent) -> Unit = { event ->
                    events += event
                    when (event.kind) {
                        "menu-content-attach" -> menuMounted = true
                        "menu-content-dispose" -> menuMounted = false
                    }
                }
                setContent { CompositionLocalProvider(LocalDensity provides Density(1f), LocalGitSelectEventObserver provides observer) {
                    HarnessTheme(theme) { Box(Modifier.fillMaxSize().testTag("migration-page")) { Column {
                        LocalProjectPicker(projectState, { id ->
                            selectedIds += id; projectState = projectState.copy(selectedProjectId = id)
                        }, { managementCalls++ }, true)
                        GitPageSelect(selectedPath, paths, { it }, true, "migration-git") { path ->
                            assertFalse(menuMounted, "Production Git business callback must observe popup disposal")
                            pathCalls += path; selectedPath = path
                            git.git.selectRepository(path)
                        }
                    } } }
                } }
                pointer("local-project-picker-open")
                capturePopup("local-project-picker-menu", "local-project-picker-open", "projects-${width}x$height-${theme.name.lowercase()}", width, height, theme)
                for (project in duplicates) {
                    val tag = "local-project-picker-item-${project.id.value}"
                    onNodeWithTag(tag).assertTextEquals(duplicateName)
                    val projectLayouts = mutableListOf<TextLayoutResult>()
                    onNode(hasText(duplicateName) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true)
                        .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(projectLayouts) }
                    assertTrue(projectLayouts.any { it.layoutInput.text.text == duplicateName && it.lineCount > 1 &&
                        !it.hasVisualOverflow && it.getLineEnd(it.lineCount - 1) == duplicateName.length &&
                        (0 until it.lineCount).none { line -> it.isLineEllipsized(line) } },
                        "Full project name must wrap without clipping or ellipsis for project ${project.id.value}")
                    pointer(tag)
                    waitUntil(timeoutMillis = 10_000) { projectState.selectedProjectId == project.id }
                    onNodeWithTag("local-project-picker-menu").assertDoesNotExist()
                    pointer("local-project-picker-open")
                    onNodeWithTag(tag).assertIsSelected()
                }
                pointer("默认工作目录")
                waitUntil(timeoutMillis = 10_000) { selectedIds.size == 3 }
                assertEquals(duplicates.map { it.id } + listOf(null), selectedIds)
                pointer("local-project-manage")
                assertEquals(1, managementCalls)

                pointer("migration-git")
                runOnIdle { assertTrue(menuMounted) }
                onNodeWithTag("menu:1").assertTextEquals(longPath)
                val results = mutableListOf<TextLayoutResult>()
                onNode(hasText(longPath), useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
                assertTrue(results.any { it.layoutInput.text.text == longPath && it.lineCount > 1 &&
                    (0 until it.lineCount).none { line -> it.isLineEllipsized(line) } }, "Full option path must wrap without ellipsis")
                capturePopup("migration-git-menu", "migration-git", "git-${width}x$height-${theme.name.lowercase()}", width, height, theme)
                wheelToEnd("migration-git-menu")
                pointer("menu:${paths.lastIndex}")
                waitUntil(timeoutMillis = 10_000) { selectedPath == lastPath && git.ready() }
                assertEquals(listOf(lastPath), pathCalls)
                assertEquals(GitWorktreesPageFixture.binding to lastPath, git.backend.discoveries.last())
                onNodeWithTag("migration-git-menu").assertDoesNotExist()
                onNodeWithTag("migration-git-value").assertTextEquals(lastPath)
                wheelToEnd("migration-git-value", horizontal = true)
                assertTrue(events.any { it.kind == "pointer-initial" })
                assertTrue(events.any { it.kind == "pointer-final" })
                assertTrue(events.any { it.kind == "menu-content-applied" })
                val optionEvent = events.indexOfLast { it.kind == "option" }
                assertTrue(events.subList(0, optionEvent).any { it.kind == "menu-content-dispose" })
            }
        } finally { git.close(); projects.close() }
    }

    @Test fun gitQueuedSelectionRejectsDisabled() = invalidatedGitSelection(GitInvalidation.Disabled)
    @Test fun gitQueuedSelectionRejectsRemovedOption() = invalidatedGitSelection(GitInvalidation.Removed)
    @Test fun gitQueuedSelectionRejectsReorderedOptions() = invalidatedGitSelection(GitInvalidation.Reordered)
    @Test fun gitQueuedSelectionRejectsChangedOwner() = invalidatedGitSelection(GitInvalidation.Owner)
    @Test fun gitQueuedSelectionRejectsDisposedOwner() = invalidatedGitSelection(GitInvalidation.Disposed)

    private enum class GitInvalidation { Disabled, Removed, Reordered, Owner, Disposed }

    private fun invalidatedGitSelection(invalidation: GitInvalidation) {
        val f = GitWorktreesPageFixture()
        f.backend.extraRefs = listOf("refs/heads/a", "refs/heads/b").map { GitBranch(it, GitWorktreesPageFixture.featureOid, false, false) }
        f.git.refresh()
        try { runSkikoComposeUiTest(size = Size(600f, 800f)) {
            var options by mutableStateOf(listOf("refs/heads/a", "refs/heads/b"))
            var enabled by mutableStateOf(true)
            var present by mutableStateOf(true)
            var owner by mutableStateOf(0)
            var mounted = false
            val calls = mutableListOf<String>()
            val before = f.backend.switches.toList()
            val observer: (GitSelectEvent) -> Unit = { event ->
                when (event.kind) {
                    "menu-content-attach" -> mounted = true
                    "menu-content-dispose" -> mounted = false
                    "dismiss-after" -> if (event.expanded == false && mounted) {
                        when (invalidation) {
                            GitInvalidation.Disabled -> enabled = false
                            GitInvalidation.Removed -> options = options.take(1)
                            GitInvalidation.Reordered -> options = options.reversed()
                            GitInvalidation.Owner -> owner++
                            GitInvalidation.Disposed -> present = false
                        }
                    }
                }
            }
            setContent { HarnessTheme { CompositionLocalProvider(LocalGitSelectEventObserver provides observer) {
                if (present) key(owner) { GitPageSelect("refs/heads/a", options, { it }, enabled, "migration-git") { ref ->
                    assertFalse(mounted); calls += ref; f.git.selectBranch(ref)
                } }
            } } }
            pointer("migration-git")
            runOnIdle { assertTrue(mounted) }
            val retained = onNodeWithTag("menu:1").fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
            pointer("menu:1")
            waitUntil(timeoutMillis = 10_000) { !mounted }
            onNodeWithTag("migration-git-menu").assertDoesNotExist()
            runOnIdle { retained() } // Deliberate stale callback injection after real queued input.
            settleGitLayout()
            assertTrue(calls.isEmpty(), "Git queue/old index must be fenced: $invalidation")
            assertNull(f.git.state.value.selectedBranchRef)
            assertEquals(before, f.backend.switches)
        } } finally { f.close() }
    }

    @Test
    fun retainedGitIndexCannotBeTranslatedIntoReorderedOptions() {
        val f = GitWorktreesPageFixture()
        f.backend.extraRefs = listOf("refs/heads/a", "refs/heads/b").map { GitBranch(it, GitWorktreesPageFixture.featureOid, false, false) }
        f.git.refresh()
        try { runSkikoComposeUiTest(size = Size(600f, 800f)) {
            var options by mutableStateOf(listOf("refs/heads/a", "refs/heads/b"))
            var selected by mutableStateOf("not-an-option")
            val calls = mutableListOf<String>()
            setContent { HarnessTheme { GitPageSelect(selected, options, { "Duplicate label" }, true, "migration-git") { ref ->
                selected = ref; calls += ref; f.git.selectBranch(ref)
            } } }
            pointer("migration-git")
            val retained = onNodeWithTag("menu:1").fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
            runOnIdle { options = options.reversed() }
            settleGitLayout()
            onNodeWithTag("menu:1").assertTextEquals("Duplicate label")
            runOnIdle { retained() }
            settleGitLayout()
            assertTrue(calls.isEmpty()); assertNull(f.git.state.value.selectedBranchRef)
            pointer("menu:0")
            waitUntil(timeoutMillis = 10_000) { f.git.state.value.selectedBranchRef == "refs/heads/b" }
            assertEquals(listOf("refs/heads/b"), calls)
        } } finally { f.close() }
    }

    @Test
    fun valueEqualOrderedGitOptionsAcceptTheLatestBusinessCallback() {
        val f = GitWorktreesPageFixture()
        f.backend.extraRefs = listOf("refs/heads/a", "refs/heads/b").map { GitBranch(it, GitWorktreesPageFixture.featureOid, false, false) }
        f.git.refresh()
        try { runSkikoComposeUiTest(size = Size(600f, 800f)) {
            var version by mutableStateOf(0)
            val calls = mutableListOf<Pair<Int, String>>()
            var mounted = false
            val observer: (GitSelectEvent) -> Unit = { event ->
                when (event.kind) {
                    "menu-content-attach" -> mounted = true
                    "menu-content-dispose" -> mounted = false
                    "dismiss-after" -> if (event.expanded == false && mounted) version++
                }
            }
            setContent { HarnessTheme { CompositionLocalProvider(LocalGitSelectEventObserver provides observer) {
                val captured = version
                // New list and String instances compare equal and preserve ordered option meaning.
                val options = listOf("refs/heads/a", "refs/heads/b").map { String(it.toCharArray()) }
                GitPageSelect("refs/heads/a", options, { it }, true, "migration-git") { ref ->
                    assertFalse(mounted)
                    calls += captured to ref; f.git.selectBranch(ref)
                }
            } } }
            pointer("migration-git")
            runOnIdle { version = 1 }
            settleGitLayout()
            pointer("menu:1")
            waitUntil(timeoutMillis = 10_000) { calls.size == 1 }
            assertEquals(listOf(2 to "refs/heads/b"), calls)
            assertEquals("refs/heads/b", f.git.state.value.selectedBranchRef)
        } } finally { f.close() }
    }

    @Test
    fun tinyWindowClampsWideWrappedGitPopupAndStillScrolls() = runSkikoComposeUiTest(size = Size(160f, 200f)) {
        val options = listOf("short", GitWorktreesPageFixture.longRoot) + (1..20).map { "option-$it" }
        var selected by mutableStateOf("short")
        setContent { CompositionLocalProvider(LocalDensity provides Density(1f)) { HarnessTheme {
            Box(Modifier.fillMaxSize()) { GitPageSelect(selected, options, { it }, true, "migration-git") { selected = it } }
        } } }
        pointer("migration-git")
        val bounds = onNodeWithTag("migration-git-menu", true).fetchSemanticsNode().boundsInWindow
        assertTrue(bounds.width <= 144f && bounds.height <= 184f, "Small popup must fit window margins: $bounds")
        assertTrue(bounds.left >= 8f && bounds.top >= 8f && bounds.right <= 152f && bounds.bottom <= 192f)
        wheelToEnd("migration-git-menu")
        pointer("menu:${options.lastIndex}")
        waitUntil(timeoutMillis = 10_000) { selected == options.last() }
    }

    private fun ComposeUiTest.pointer(tag: String) {
        val started = System.nanoTime()
        clickGitNode(onNodeWithTag(tag))
        assertTrue(System.nanoTime() - started < 10_000_000_000L, "Pointer deadline: $tag")
    }

    private fun ComposeUiTest.wheelToEnd(tag: String, horizontal: Boolean = false) {
        val started = System.nanoTime()
        pluginsPageScrollToEnd(tag, horizontal)
        assertTrue(System.nanoTime() - started < 10_000_000_000L, "Wheel deadline: $tag")
    }

    private fun ComposeUiTest.capturePopup(menuTag: String, anchorTag: String, name: String, width: Int, height: Int, theme: HarnessThemeMode) {
        settleGitLayout()
        val menu = onNodeWithTag(menuTag, true).assertIsDisplayed().fetchSemanticsNode()
        val root = generateSequence(menu) { it.parent }.last()
        val anchorRoot = generateSequence(onNodeWithTag(anchorTag).fetchSemanticsNode()) { it.parent }.last()
        assertTrue(root.id != anchorRoot.id, "Screenshot must capture actual independent popup root")
        val bounds = menu.boundsInWindow
        assertTrue(bounds.left >= 8f && bounds.top >= 8f && bounds.right <= width - 8f && bounds.bottom <= height - 8f, "Popup margin geometry: $bounds")
        assertTrue(bounds.width <= if (menuTag.startsWith("local")) 320f else 560f)
        assertTrue(bounds.height <= 320f)
        val target = onNode(SemanticsMatcher("Actual popup root ${root.id}") { it.id == root.id }, true)
        val bitmap = target.captureToImage()
        val bytes = Image.makeFromBitmap(bitmap.asSkiaBitmap()).use { image ->
            image.encodeToData(EncodedImageFormat.PNG)?.use { it.bytes } ?: error("PNG encoding failed")
        }
        val directory = File(System.getenv("HARNESS_MENU_PICKER_CAPTURE_DIR")?.takeIf { it.isNotBlank() }
            ?: "/tmp/harness-menu-picker-captures-20261008")
        assertTrue(directory.isDirectory || directory.mkdirs())
        val png = File(directory, "$name.png").also { it.writeBytes(bytes) }
        val decoded = assertNotNull(ImageIO.read(png))
        assertEquals(root.size.width, decoded.width); assertEquals(root.size.height, decoded.height)
        val geometry = buildJsonObject {
            put("schema", "harness.menu-picker.popup.v1"); put("image", png.name)
            put("windowWidth", width); put("windowHeight", height); put("theme", theme.name); put("density", 1f)
            put("captureRootId", root.id); put("anchorRootId", anchorRoot.id)
            put("captureWidth", decoded.width); put("captureHeight", decoded.height)
            put("rootBoundsInWindow", root.boundsInWindow.toString()); put("menuBoundsInWindow", bounds.toString())
            put("sha256", MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
            putJsonArray("nodes") {
                onAllNodes(SemanticsMatcher("Popup tags") { it.config.contains(SemanticsProperties.TestTag) }, true)
                    .fetchSemanticsNodes().filter { generateSequence(it) { node -> node.parent }.last().id == root.id }.forEach { node ->
                        add(buildJsonObject {
                            put("tag", node.config[SemanticsProperties.TestTag]); put("id", node.id)
                            put("boundsInRoot", node.boundsInRoot.toString()); put("boundsInWindow", node.boundsInWindow.toString())
                            put("layoutWidth", node.size.width); put("layoutHeight", node.size.height)
                        })
                    }
            }
        }
        File(directory, "$name.geometry.json").writeText(Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), geometry))
    }
}
