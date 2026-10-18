@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package im.hikaru.harness.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import im.hikaru.harness.client.git.*
import im.hikaru.harness.ui.foundation.HarnessColors
import im.hikaru.harness.ui.foundation.HarnessDarkColors
import im.hikaru.harness.ui.foundation.HarnessLightColors
import im.hikaru.harness.ui.foundation.HarnessTheme
import im.hikaru.harness.ui.foundation.HarnessThemeMode
import im.hikaru.harness.ui.foundation.HarnessTokens
import java.io.File
import javax.imageio.ImageIO
import kotlinx.coroutines.CompletableDeferred
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import kotlin.test.*

/**
 * Production pages + real controllers, staged through test adapter gates/calls only. No reflection,
 * state injection, real Git invocation, native title bar or shell. Density-1 viewport PNGs cover
 * light/dark x 1440x900/600x800; assertions check geometry and action availability, not pixel parity.
 * Tests intentionally require explicit parent execution; capture location can be overridden by env.
 */
@OptIn(ExperimentalTestApi::class)
class GitWorktreesScreenshotTest {
    @Test fun captureGitReadStates() = capture(listOf(Case.GitUnselected, Case.GitLoading, Case.GitDataLoading,
        Case.GitEmpty, Case.GitError, Case.GitUnavailable, Case.GitNotRepository, Case.GitRemote, Case.GitReadError))

    @Test fun captureGitLongPathsRefsAndBoundedDiff() = capture(listOf(Case.GitLongReady, Case.GitBranchMenu,
        Case.GitDiffBaseMenu, Case.GitLargeDiff))

    @Test fun captureGitWordLevelEmphasis() = capture(listOf(Case.GitWordLevelDiff))

    @Test fun captureGitHideImports() = capture(listOf(Case.GitHideImports))

    @Test fun captureGitLoadFullFiles() = capture(listOf(Case.GitLoadFullFiles))

    @Test fun captureOrdinaryWorktreeReadStates() = capture(listOf(Case.WorktreesUnselected, Case.WorktreesLoading,
        Case.WorktreesEmpty, Case.WorktreesError, Case.WorktreesRemote, Case.WorktreesLongReady, Case.NoManagedService))

    @Test fun captureManagedReadStatesAndOwnershipVariants() = capture(listOf(Case.ManagedLoading, Case.ManagedEmpty,
        Case.ManagedError, Case.ManagedUnavailable, Case.ManagedVariants))

    @Test fun captureManagedStaleLoadingErrorAndUnavailable() = capture(listOf(Case.ManagedStaleLoading,
        Case.ManagedStaleError, Case.ManagedStaleUnavailable))

    @Test fun captureCreationBusyFailedUnknownAndCreatedReceipt() = capture(listOf(Case.CreateBusy, Case.CreatePathExists,
        Case.CreateNeedsRescan, Case.CreateSuccess))

    private fun capture(cases: List<Case>) {
        val directory = captureDirectory()
        for (case in cases) for ((width, height) in listOf(1440 to 900, 600 to 800)) {
            for ((themeSlug, theme) in listOf("light" to HarnessThemeMode.Light, "dark" to HarnessThemeMode.Dark)) {
                val f = GitWorktreesPageFixture(select = false)
                val gate = CompletableDeferred<Unit>()
                try {
                    configure(case, f, gate)
                    runSkikoComposeUiTest(size = Size(width.toFloat(), height.toFloat())) {
                        setContent {
                            HarnessTheme(themeMode = theme) {
                                Box(Modifier.fillMaxSize().background(HarnessTokens.Colors.Canvas).testTag(viewportTag)) {
                                    if (case.git) SettingsGitPage(f.git)
                                    else WorktreesPage(f.git, managedController = if (case == Case.NoManagedService) null else f.managed)
                                }
                            }
                        }
                        if (!case.git && case !in listOf(Case.WorktreesUnselected, Case.WorktreesLoading,
                                Case.WorktreesRemote, Case.NoManagedService)) {
                            val expected = when (case) {
                                Case.ManagedLoading -> ManagedWorktreePhase.Loading
                                Case.ManagedError -> ManagedWorktreePhase.Error
                                Case.ManagedUnavailable -> ManagedWorktreePhase.Unavailable
                                else -> ManagedWorktreePhase.Ready
                            }
                            waitUntil { f.managed.state.value.phase == expected }
                        }
                        val prefix = "${case.slug}-$themeSlug-${width}x$height"
                        fun save(suffix: String, scrollTarget: String = "current offsets; see scrollContainers") {
                            settleGitLayout()
                            val file = File(directory, "$prefix-$suffix.png")
                            writeBitmap(file, onNodeWithTag(viewportTag).captureToImage(), width, height)
                            writeGitCaptureManifest(file, case.slug, themeSlug, width, height, width, height,
                                viewportTag, scrollTarget, f.git.state.value, f.managed.state.value,
                                managedServicePresent = !case.git && case != Case.NoManagedService)
                        }
                        fun focus(tag: String, suffix: String = "focused-viewport", index: Int = 0) {
                            val listTag = when (tag) {
                                "git-worktree-path", "git-worktree-branch", "git-worktree-select" -> "git-worktrees-list"
                                "managed-ownership", "managed-worktree-path", "managed-branch", "managed-head",
                                "managed-registration", "managed-registration-warning",
                                "managed-sessions-availability", "managed-pins-availability",
                                "managed-archive-availability", "managed-other-protection-availability" -> "managed-inventory-list"
                                "git-branch" -> "git-branches-list"
                                "git-change" -> "git-changes-list"
                                else -> null
                            }
                            val target = scrollGitNodeIntoView(onAllNodesWithTag(tag)[index], listTag)
                            val node = target.fetchSemanticsNode()
                            val bounds = node.boundsInRoot
                            val viewport = onNodeWithTag(viewportTag).fetchSemanticsNode().boundsInRoot
                            assertTrue(bounds.width > 0f && bounds.height > 0f, "focused tag must have clipped visible area: $tag")
                            assertTrue(bounds.height >= node.size.height - 1f &&
                                bounds.width >= minOf(node.size.width.toFloat(), 24f) - 1f,
                                "focused target must show its full height and readable width, including list clipping: $tag $bounds")
                            assertTrue(bounds.left >= viewport.left - 1f && bounds.right <= viewport.right + 1f &&
                                bounds.top >= viewport.top - 1f && bounds.bottom <= viewport.bottom + 1f,
                                "focused clipped bounds must fit the captured viewport: $tag $bounds")
                            save(suffix, "$tag[$index]")
                        }
                        // Desktop menus are separate popup roots; capture the actual menu node too.
                        fun saveMenu(suffix: String, tag: String) {
                            settleGitLayout()
                            val node = onNodeWithTag(tag).assertIsDisplayed()
                            val bounds = node.fetchSemanticsNode().boundsInRoot
                            val bitmap = node.captureToImage()
                            assertTrue(bitmap.width in 1..width)
                            // Popup menus are bounded, not full-height. The diff "变更选项" menu gained a
                            // ninth entry (渲染预览), so allow up to 9 rows while still catching runaway growth.
                            assertTrue(bitmap.height in 1..380, "popup menu must stay bounded: $bitmap")
                            assertTrue(kotlin.math.abs(bitmap.width - bounds.width) <= 1f)
                            assertTrue(kotlin.math.abs(bitmap.height - bounds.height) <= 1f)
                            val file = File(directory, "$prefix-$suffix.png")
                            writeBitmap(file, bitmap, bitmap.width, bitmap.height)
                            writeGitCaptureManifest(file, case.slug, themeSlug, width, height, bitmap.width, bitmap.height,
                                tag, "popup menu; see scrollContainers", f.git.state.value, f.managed.state.value,
                                managedServicePresent = !case.git && case != Case.NoManagedService)
                        }
                        save("page-viewport")

                        when (case) {
                            Case.GitUnselected -> {
                                onNodeWithTag("git-refresh").assertIsNotEnabled()
                                onNodeWithTag("git-checkout-submit").assertIsNotEnabled()
                                onNodeWithTag("git-diff-source").performScrollTo().assertIsNotEnabled()
                            }
                            Case.GitLoading, Case.GitDataLoading -> {
                                onNodeWithTag("git-refresh").assertIsNotEnabled()
                                revealRepositoryInput()
                                onNodeWithTag("git-repository-select").assertIsNotEnabled()
                                onNodeWithTag("git-checkout-branch").assertIsNotEnabled()
                                onNodeWithTag("git-diff-source").assertIsNotEnabled()
                                onNodeWithTag("git-clean").assertDoesNotExist()
                                onNodeWithTag("git-diff-empty").assertDoesNotExist()
                            }
                            Case.GitEmpty -> {
                                onNodeWithTag("git-clean").performScrollTo().assertIsDisplayed()
                                onNodeWithTag("git-checkout-branch").assertIsNotEnabled()
                                onNodeWithTag("git-diff-empty").performScrollTo().assertIsDisplayed()
                            }
                            Case.GitError, Case.GitUnavailable, Case.GitNotRepository -> {
                                onNodeWithTag("git-refresh").assertIsEnabled()
                                onNodeWithTag("git-clean").assertDoesNotExist()
                                listOf("已暂存 0", "未暂存 0", "未跟踪 0").forEach { onNodeWithText(it).assertDoesNotExist() }
                                onNodeWithTag("git-diff-source").assertIsNotEnabled()
                            }
                            Case.GitRemote, Case.WorktreesRemote -> {
                                onNodeWithTag("git-unavailable").assertExists()
                                onNodeWithTag("git-refresh").assertIsNotEnabled()
                                revealRepositoryInput()
                                onNodeWithTag("git-repository-select").assertIsNotEnabled()
                            }
                            Case.GitReadError -> {
                                onNodeWithTag("git-clean").assertDoesNotExist()
                                onNodeWithText("没有分支引用。").assertDoesNotExist()
                                onNodeWithTag("git-diff-empty").assertDoesNotExist()
                                onNodeWithTag("git-checkout-branch").performScrollTo().assertIsNotEnabled()
                                save("status-refs-errors")
                                onNodeWithText("Diff 超出后端读取限额。").performScrollTo().assertIsDisplayed()
                            }
                            Case.GitLongReady -> {
                                onNodeWithTag("git-repository-root").assertTextEquals(GitWorktreesPageFixture.longRoot)
                                onNodeWithTag("git-current-branch").performScrollTo().assertTextEquals(GitWorktreesPageFixture.longRef.removePrefix("refs/heads/"))
                                save("status-paths")
                            }
                            Case.GitBranchMenu -> {
                                clickGitNode(scrollGitNodeIntoView(onNodeWithTag("git-checkout-branch")))
                                val menu = onNodeWithTag("git-checkout-branch-menu").fetchSemanticsNode().boundsInRoot
                                assertTrue(menu.width <= width.toFloat())
                                assertTrue(menu.height <= 321f, "branch menu must be bounded: $menu")
                                scrollGitNodeIntoView(onNodeWithTag("menu:2")).assertTextEquals(GitWorktreesPageFixture.longRef)
                                save("menu-long-ref")
                                saveMenu("menu-popup-long-ref", "git-checkout-branch-menu")
                                clickGitNode(onNodeWithTag("menu:2"))
                                onNodeWithTag("git-checkout-branch-value").assertTextEquals(GitWorktreesPageFixture.longRef)
                                save("selected-long-ref")
                            }
                            Case.GitDiffBaseMenu -> {
                                clickGitNode(scrollGitNodeIntoView(onNodeWithTag("git-diff-source")))
                                clickGitNode(onNodeWithTag("menu:2"))
                                waitUntil { f.git.state.value.diff !is GitRead.Loading }
                                clickGitNode(scrollGitNodeIntoView(onNodeWithTag("git-diff-base")))
                                scrollGitNodeIntoView(onNodeWithTag("menu:2")).assertTextEquals(GitWorktreesPageFixture.longRef)
                                save("base-menu-long-ref")
                                saveMenu("base-menu-popup-long-ref", "git-diff-base-menu")
                                clickGitNode(onNodeWithTag("menu:2"))
                                waitUntil { f.ready() }
                                onNodeWithTag("git-diff-base-value").assertTextEquals(GitWorktreesPageFixture.longRef)
                                save("base-selected")
                            }
                            Case.GitLargeDiff -> {
                                val viewport = onNodeWithTag("git-diff-viewport").performScrollTo().assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                                assertTrue(viewport.height in 287f..289f, "bounded diff height: $viewport")
                                assertTrue(viewport.width <= width - 32f)
                                assertTrue(viewport.top >= 0 && viewport.bottom <= height.toFloat())
                                onNodeWithTag("git-diff-summary").assertTextEquals("1 个文件变更 · +500 −0")
                                // 500 changed lines exceed the Harness open-by-default threshold, so the file starts collapsed.
                                onAllNodesWithTag("git-diff-file")[0].assertTextContains("展开", substring = true)
                                onAllNodesWithTag("git-diff-file-patch").assertCountEquals(0)
                                save("diff-collapsed")
                                clickGitNode(scrollGitNodeIntoView(onAllNodesWithTag("git-diff-file")[0]))
                                waitUntil { onAllNodesWithTag("git-diff-file-patch").fetchSemanticsNodes().isNotEmpty() }
                                onAllNodesWithTag("git-diff-file")[0].assertTextContains("收起", substring = true)
                                val vertical = onNodeWithTag("git-diff-vertical")
                                val horizontal = onAllNodesWithTag("git-diff-file-patch")[0]
                                vertical.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 300f) }
                                horizontal.performSemanticsAction(SemanticsActions.ScrollBy) { it(500f, 0f) }
                                waitUntil {
                                    vertical.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value() > 0f &&
                                        horizontal.fetchSemanticsNode().config[SemanticsProperties.HorizontalScrollAxisRange].value() > 0f
                                }
                                assertEquals(viewport, onNodeWithTag("git-diff-viewport").fetchSemanticsNode().boundsInRoot)
                                save("diff-after-independent-scroll")
                                // Word wrap replaces the horizontal scroll with one row per line, so the
                                // unselectable number cell stays beside the line it belongs to.
                                clickGitNode(scrollGitNodeIntoView(onNodeWithTag("git-diff-options")))
                                waitUntil { onAllNodesWithTag("menu:wrap").fetchSemanticsNodes().isNotEmpty() }
                                clickGitNode(onNodeWithTag("menu:wrap"))
                                waitUntil { onAllNodesWithTag("git-diff-wrap").fetchSemanticsNodes().isNotEmpty() }
                                // 500 changed lines plus their `@@` header, one row each.
                                assertEquals(501, onAllNodesWithTag("git-diff-file-patch-line").fetchSemanticsNodes().size)
                                assertEquals(501, onAllNodesWithTag("git-diff-gutter").fetchSemanticsNodes().size)
                                // The wrapped body wraps instead of scrolling, and the added menu trigger
                                // still fits the narrow 600px viewport next to the other controls.
                                assertNull(onAllNodesWithTag("git-diff-wrap")[0].fetchSemanticsNode()
                                    .config.getOrNull(SemanticsProperties.HorizontalScrollAxisRange))
                                val optionsBounds = onNodeWithTag("git-diff-options").fetchSemanticsNode().boundsInRoot
                                assertTrue(optionsBounds.left >= 0f && optionsBounds.right <= width.toFloat(),
                                    "diff options control must fit the viewport: $optionsBounds")
                                save("diff-wrapped")
                                clickGitNode(scrollGitNodeIntoView(onNodeWithTag("git-diff-options")))
                                waitUntil { onAllNodesWithTag("menu:wrap").fetchSemanticsNodes().isNotEmpty() }
                                clickGitNode(onNodeWithTag("menu:wrap"))
                                waitUntil { onAllNodesWithTag("git-diff-file-patch").fetchSemanticsNodes().isNotEmpty() }
                                clickGitNode(scrollGitNodeIntoView(onNodeWithTag("git-diff-layout-split")))
                                waitUntil { onAllNodesWithTag("git-diff-split").fetchSemanticsNodes().isNotEmpty() }
                                save("diff-split")
                            }
                            Case.GitWordLevelDiff -> {
                                // The body fits inside the bounded 288dp diff viewport, so the page scroll
                                // (not the body's own scroll) is what has to move it into view.
                                onNodeWithTag("git-diff-viewport").performScrollTo().assertIsDisplayed()
                                val palette = paletteOf(theme)
                                val body = onNodeWithTag("git-diff-file-patch").fetchSemanticsNode()
                                assertTrue(body.boundsInRoot.width > 0f && body.boundsInRoot.height > 0f,
                                    "word-diff body must have a visible area: ${body.boundsInRoot}")
                                // `wordDiffsEnabled.2` starts true, so the changed word is tinted with no
                                // interaction: only the inserted word of the paired edit stands out.
                                assertEquals(listOf("Codex "), gitWordEmphasisRanges(body, palette))
                                fun openWordDiffMenu() {
                                    clickGitNode(scrollGitNodeIntoView(onNodeWithTag("git-diff-options")))
                                    waitUntil { onAllNodesWithTag("menu:word-diffs").fetchSemanticsNodes().isNotEmpty() }
                                }
                                fun emphasisRanges(): List<String> =
                                    gitWordEmphasisRanges(onNodeWithTag("git-diff-file-patch").fetchSemanticsNode(), palette)
                                // Both body captures follow the same menu interaction, so the pair differs only
                                // in the emphasis band and not in the anchor's focus colour.
                                openWordDiffMenu()
                                onNodeWithTag("menu:word-diffs").assertIsSelected()
                                saveMenu("options-menu-word-diffs-on", "git-diff-options-menu")
                                clickGitNode(onNodeWithTag("menu:word-diffs"))
                                waitUntil { emphasisRanges().isEmpty() }
                                // The emphasis never changes the selectable text, only the tint over the changed
                                // word: the whole patch line is still there once the toggle is off.
                                onNodeWithTag("git-diff-file-patch")
                                    .assertTextContains("+    println(\"Hello \" + name + \" from the Codex harness\")", substring = true)
                                parkGitPointer()
                                save("word-emphasis-off")
                                openWordDiffMenu()
                                onNodeWithTag("menu:word-diffs").assertIsNotSelected()
                                saveMenu("options-menu-word-diffs-off", "git-diff-options-menu")
                                clickGitNode(onNodeWithTag("menu:word-diffs"))
                                waitUntil { emphasisRanges().isNotEmpty() }
                                parkGitPointer()
                                save("word-emphasis-on")
                            }
                            Case.GitHideImports -> {
                                onNodeWithTag("git-diff-viewport").performScrollTo().assertIsDisplayed()
                                fun bodyText(): String = onNodeWithTag("git-diff-file-patch")
                                    .fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)
                                    .orEmpty().joinToString("\n") { it.text }
                                fun openImportMenu() {
                                    clickGitNode(scrollGitNodeIntoView(onNodeWithTag("git-diff-options")))
                                    waitUntil { onAllNodesWithTag("menu:hide-imports").fetchSemanticsNodes().isNotEmpty() }
                                }
                                // `hideDiffImports` starts false, so the import change is on screen first.
                                assertTrue(bodyText().contains("+import new.Replacement"),
                                    "imports must be visible before the toggle: ${bodyText()}")
                                parkGitPointer()
                                save("imports-shown")
                                openImportMenu()
                                onNodeWithTag("menu:hide-imports").assertIsNotSelected()
                                saveMenu("options-menu-hide-imports-off", "git-diff-options-menu")
                                clickGitNode(onNodeWithTag("menu:hide-imports"))
                                waitUntil { !bodyText().contains("import old.Deprecated") }
                                // The toggle removes only the import-only change; the shared context and
                                // the real edit stay, so the two captures differ only in those two rows.
                                assertTrue(bodyText().contains("+val extra = 2"),
                                    "ordinary edits must survive the import filter: ${bodyText()}")
                                parkGitPointer()
                                save("imports-hidden")
                                openImportMenu()
                                onNodeWithTag("menu:hide-imports").assertIsSelected()
                                saveMenu("options-menu-hide-imports-on", "git-diff-options-menu")
                                clickGitNode(onNodeWithTag("menu:hide-imports"))
                                waitUntil { bodyText().contains("+import new.Replacement") }
                            }
                            Case.GitLoadFullFiles -> {
                                onNodeWithTag("git-diff-viewport").performScrollTo().assertIsDisplayed()
                                fun patchText(): String = onNodeWithTag("git-diff-file-patch")
                                    .fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)
                                    .orEmpty().joinToString("\n") { it.text }
                                fun openFullFileMenu() {
                                    clickGitNode(scrollGitNodeIntoView(onNodeWithTag("git-diff-options")))
                                    waitUntil { onAllNodesWithTag("menu:load-full-files").fetchSemanticsNodes().isNotEmpty() }
                                }
                                // Default is partial: the untouched tail of the file is not in the patch.
                                assertFalse(patchText().contains(FULL_FILE_TAIL_LINE))
                                parkGitPointer()
                                save("diff-partial")
                                openFullFileMenu()
                                onNodeWithTag("menu:load-full-files").assertIsNotSelected()
                                saveMenu("options-menu-load-full-files-off", "git-diff-options-menu")
                                clickGitNode(onNodeWithTag("menu:load-full-files"))
                                waitUntil { patchText().contains(FULL_FILE_TAIL_LINE) }
                                // The toggle re-read the patch: the same change now carries the whole file.
                                assertTrue(patchText().contains("+    println(\"hello\")"),
                                    "the change must survive the full-context re-read: ${patchText()}")
                                parkGitPointer()
                                save("diff-full-file")
                                openFullFileMenu()
                                onNodeWithTag("menu:load-full-files").assertIsSelected()
                                saveMenu("options-menu-load-full-files-on", "git-diff-options-menu")
                                clickGitNode(onNodeWithTag("menu:load-full-files"))
                                waitUntil { !patchText().contains(FULL_FILE_TAIL_LINE) }
                            }
                            Case.WorktreesUnselected -> {
                                onNodeWithTag("managed-unbound").performScrollTo().assertIsDisplayed()
                                onNodeWithTag("managed-refresh").assertIsNotEnabled()
                                onNodeWithTag("git-worktree-create-submit").assertIsNotEnabled()
                                onNodeWithTag("git-worktrees-empty").assertDoesNotExist()
                            }
                            Case.WorktreesLoading -> {
                                onNodeWithTag("git-refresh").assertIsNotEnabled()
                                onNodeWithTag("git-worktree-create-path").assertIsNotEnabled()
                                onNodeWithTag("managed-unbound").performScrollTo().assertIsDisplayed()
                                onNodeWithTag("managed-refresh").assertIsNotEnabled()
                                onNodeWithTag("git-worktrees-empty").assertDoesNotExist()
                            }
                            Case.WorktreesEmpty -> {
                                onNodeWithTag("managed-empty").performScrollTo().assertIsDisplayed()
                                save("managed-empty")
                                onNodeWithTag("git-worktrees-empty").performScrollTo().assertIsDisplayed()
                                onNodeWithTag("git-worktree-select").assertDoesNotExist()
                            }
                            Case.WorktreesError -> {
                                onNodeWithText("工作树清单读取失败。").performScrollTo().assertIsDisplayed()
                                onNodeWithTag("git-worktrees-empty").assertDoesNotExist()
                                onNodeWithTag("git-refresh").assertIsEnabled()
                            }
                            Case.WorktreesLongReady -> {
                                scrollGitNodeIntoView(onAllNodesWithTag("git-worktree-select")[1], "git-worktrees-list").assertIsEnabled()
                                onAllNodesWithTag("git-worktree-path")[1].assertTextEquals(GitWorktreesPageFixture.longRoot)
                                focus("git-worktree-path", "ordinary-long-path", 1)
                                focus("git-worktree-select", "ordinary-long-path-action", 1)
                                scrollGitNodeIntoView(onAllNodesWithTag("git-worktree-select")[2], "git-worktrees-list").assertIsNotEnabled()
                                save("prunable-unavailable")
                            }
                            Case.NoManagedService -> {
                                onNodeWithTag("managed-unavailable").performScrollTo().assertIsDisplayed()
                                onNodeWithTag("managed-refresh").assertDoesNotExist()
                            }
                            Case.ManagedLoading -> {
                                onNodeWithTag("managed-loading").performScrollTo().assertIsDisplayed()
                                onNodeWithTag("managed-refresh").assertIsNotEnabled()
                                onNodeWithTag("managed-count").assertDoesNotExist()
                            }
                            Case.ManagedEmpty -> onNodeWithTag("managed-empty").performScrollTo().assertIsDisplayed()
                            Case.ManagedError -> {
                                onNodeWithTag("managed-error").performScrollTo().assertIsDisplayed()
                                onNodeWithTag("managed-empty").assertDoesNotExist()
                                onNodeWithTag("managed-refresh").assertIsEnabled()
                            }
                            Case.ManagedUnavailable -> {
                                onNodeWithTag("managed-unavailable").performScrollTo().assertIsDisplayed()
                                onNodeWithTag("managed-empty").assertDoesNotExist()
                                onNodeWithTag("managed-refresh").assertIsEnabled()
                            }
                            Case.ManagedVariants -> {
                                for ((index, label) in listOf("Managed 归属已核验", "归属未知", "登记与当前 Git 指纹不匹配", "当前 Git 列表未找到此登记路径").withIndex()) {
                                    scrollGitNodeIntoView(onNodeWithText(label), "managed-inventory-list").assertIsDisplayed()
                                    onAllNodesWithTag("managed-sessions-availability")[index].assertTextEquals("保护状态未知")
                                    onAllNodesWithTag("managed-pins-availability")[index].assertTextEquals("保护服务不可用")
                                    save("ownership-$index", "managed-ownership[$index]")
                                    focus("managed-sessions-availability", "ownership-$index-sessions", index)
                                    focus("managed-pins-availability", "ownership-$index-pins", index)
                                }
                                onNodeWithTag("managed-destructive-unavailable").performScrollTo().assertIsDisplayed()
                            }
                            Case.ManagedStaleLoading, Case.ManagedStaleError, Case.ManagedStaleUnavailable -> {
                                runOnIdle {
                                    f.managedReads.gate = gate
                                    f.managedReads.failure = when (case) {
                                        Case.ManagedStaleError -> ManagedWorktreeRead.Failed("刷新失败；当前归属未核验。")
                                        Case.ManagedStaleUnavailable -> ManagedWorktreeRead.Failed("Managed 服务不可用；当前归属未核验。", unavailable = true)
                                        else -> null
                                    }
                                }
                                clickGitNode(scrollGitNodeIntoView(onNodeWithTag("managed-refresh")))
                                if (case != Case.ManagedStaleLoading) {
                                    runOnIdle { gate.complete(Unit) }
                                    waitUntil { f.managed.state.value.phase != ManagedWorktreePhase.Loading }
                                } else onNodeWithTag("managed-refresh").assertIsNotEnabled()
                                onNodeWithTag("managed-stale").performScrollTo().assertIsDisplayed()
                                onNodeWithTag("managed-count").assertTextContains("当前核验数量未知", substring = true)
                                onNodeWithText("Managed 归属已核验").assertDoesNotExist()
                                for ((index, label) in listOf("上次归属为 Managed · 当前未核验", "上次归属未知 · 当前未核验",
                                    "上次登记与 Git 指纹不匹配 · 当前未核验", "上次 Git 列表未找到此登记路径 · 当前未核验").withIndex()) {
                                    scrollGitNodeIntoView(onNodeWithText(label), "managed-inventory-list").assertIsDisplayed()
                                    save("stale-ownership-$index", "managed-ownership[$index]")
                                }
                            }
                            Case.CreateBusy, Case.CreatePathExists, Case.CreateNeedsRescan, Case.CreateSuccess -> {
                                onNodeWithTag("git-worktree-create-path").performScrollTo().performTextReplacement(GitWorktreesPageFixture.longRoot)
                                onNodeWithTag("git-worktree-create-branch").performScrollTo().performTextReplacement("codex/screenshot-receipt")
                                clickGitNode(scrollGitNodeIntoView(onNodeWithTag("git-worktree-create-submit")))
                                val target = when (case) {
                                    Case.CreateBusy -> "git-worktree-create-busy"
                                    Case.CreatePathExists -> "git-worktree-create-error"
                                    Case.CreateNeedsRescan -> "git-worktree-create-needs-rescan"
                                    else -> "git-worktree-create-success"
                                }
                                waitUntil { onAllNodesWithTag(target).fetchSemanticsNodes().isNotEmpty() }
                                onNodeWithTag(target).performScrollTo().assertIsDisplayed()
                                if (case == Case.CreateBusy || case == Case.CreateNeedsRescan) {
                                    onNodeWithTag("git-worktree-create-submit").assertIsNotEnabled()
                                    onNodeWithTag("git-worktree-create-path").assertIsNotEnabled()
                                }
                                if (case != Case.CreateSuccess) {
                                    onNodeWithTag("git-worktree-create-path").assertTextEquals(GitWorktreesPageFixture.longRoot)
                                    onNodeWithTag("git-worktree-create-success").assertDoesNotExist()
                                    onNodeWithTag("git-worktree-created-boundary").assertDoesNotExist()
                                } else {
                                    onNodeWithTag("git-worktree-created-branch").assertTextEquals("refs/heads/codex/screenshot-receipt")
                                    onNodeWithTag("git-worktree-created-boundary").performScrollTo().assertIsDisplayed()
                                    onNodeWithText("Managed 归属已核验").assertDoesNotExist()
                                }
                                assertEquals(1, f.backend.creations.size)
                            }
                        }
                        // Each state gets a genuinely scrolled lower-section capture, not another top screenshot.
                        val focusedTag = when (case) {
                            Case.GitEmpty -> "git-diff-empty"
                            Case.GitReadError -> "git-error"
                            Case.GitLargeDiff, Case.GitWordLevelDiff, Case.GitHideImports,
                            Case.GitLoadFullFiles -> "git-diff-viewport"
                            Case.GitUnselected, Case.GitLoading, Case.GitDataLoading, Case.GitError,
                            Case.GitUnavailable, Case.GitNotRepository, Case.GitRemote, Case.GitLongReady,
                            Case.GitBranchMenu, Case.GitDiffBaseMenu -> "git-diff-source"
                            Case.WorktreesUnselected, Case.WorktreesLoading -> "managed-unbound"
                            Case.WorktreesEmpty, Case.ManagedEmpty -> "managed-empty"
                            Case.WorktreesError -> "git-error"
                            Case.WorktreesRemote -> "managed-unbound"
                            Case.NoManagedService, Case.ManagedUnavailable -> "managed-unavailable"
                            Case.WorktreesLongReady -> "git-worktree-select"
                            Case.ManagedLoading -> "managed-loading"
                            Case.ManagedError -> "managed-error"
                            Case.ManagedVariants -> "managed-destructive-unavailable"
                            Case.ManagedStaleLoading, Case.ManagedStaleError, Case.ManagedStaleUnavailable -> "managed-stale"
                            Case.CreateBusy -> "git-worktree-create-busy"
                            Case.CreatePathExists -> "git-worktree-create-error"
                            Case.CreateNeedsRescan -> "git-worktree-create-needs-rescan"
                            Case.CreateSuccess -> "git-worktree-created-boundary"
                        }
                        // Error tags repeat per failed read; focus the last error (Diff or worktrees).
                        val focusedIndex = if (focusedTag == "git-error")
                            onAllNodesWithTag(focusedTag).fetchSemanticsNodes().lastIndex else 0
                        parkGitPointer()
                        focus(focusedTag, index = focusedIndex)
                    }
                } finally { gate.complete(Unit); f.close() }
            }
        }
    }

    private fun configure(case: Case, f: GitWorktreesPageFixture, gate: CompletableDeferred<Unit>) {
        when (case) {
            Case.GitLoading, Case.WorktreesLoading -> f.backend.discoveryGate = gate
            Case.GitDataLoading -> f.backend.readGate = gate
            Case.GitEmpty, Case.WorktreesEmpty -> {
                f.backend.emptyRefs = true; f.backend.emptyWorktrees = true; f.managedReads.ownerships = emptyList()
            }
            Case.GitError -> f.backend.discoveryFailure = GitFailure(GitFailureKind.CommandFailed, "读取失败；请刷新重试。")
            Case.GitUnavailable -> f.backend.discoveryFailure = GitFailure(GitFailureKind.Unavailable, "本机 Git 读取服务不可用。")
            Case.GitNotRepository -> f.backend.discoveryFailure = GitFailure(GitFailureKind.NotRepository, "此目录不是 Git 仓库。")
            Case.GitReadError -> {
                f.backend.statusFailure = GitFailure(GitFailureKind.Timeout, "状态读取超时；数量未知。")
                f.backend.branchesFailure = GitFailure(GitFailureKind.CommandFailed, "分支读取失败；引用未知。")
                f.backend.diffFailure = GitFailure(GitFailureKind.OutputTooLarge, "Diff 超出后端读取限额。")
            }
            Case.GitLongReady -> {
                f.backend.currentRef = GitWorktreesPageFixture.longRef
                f.backend.currentOid = GitWorktreesPageFixture.featureOid
                f.backend.changes = listOf(GitChange(GitWorktreesPageFixture.longRoot + "/renamed file.kt",
                    originalPath = GitWorktreesPageFixture.longRoot + "/original file.kt", indexStatus = 'R', worktreeStatus = 'M'),
                    GitChange("未跟踪.txt", indexStatus = '?', worktreeStatus = '?'))
            }
            Case.GitBranchMenu -> f.backend.extraRefs = (1..30).map { GitBranch("refs/heads/extra-$it", GitWorktreesPageFixture.oid, false, false) }
            Case.GitLargeDiff -> f.backend.patch = GitWorktreesPageFixture.largePatch
            Case.GitWordLevelDiff -> f.backend.patch = GitWorktreesPageFixture.wordLevelPatch
            Case.GitHideImports -> f.backend.patch = GitWorktreesPageFixture.importPatch
            Case.GitLoadFullFiles -> {
                f.backend.patch = GitWorktreesPageFixture.partialPatch
                f.backend.fullPatch = GitWorktreesPageFixture.fullFilePatch
            }
            Case.WorktreesError -> f.backend.worktreesFailure = GitFailure(GitFailureKind.CommandFailed, "工作树清单读取失败。")
            Case.WorktreesLongReady -> f.backend.extraWorktrees = listOf(
                GitWorktree(GitWorktreesPageFixture.longRoot, GitWorktreesPageFixture.oid, GitWorktreesPageFixture.longRef),
                GitWorktree("/test-only/prunable", null, null, prunable = true, pruneReason = "目标目录缺失。"))
            Case.ManagedLoading -> f.managedReads.gate = gate
            Case.ManagedEmpty -> f.managedReads.ownerships = emptyList()
            Case.ManagedError -> f.managedReads.failure = ManagedWorktreeRead.Failed("Managed 登记读取失败；保护仍未知。")
            Case.ManagedUnavailable -> f.managedReads.failure = ManagedWorktreeRead.Failed("Managed 登记服务不可用。", unavailable = true)
            Case.ManagedVariants -> f.managedReads.ownerships = ManagedOwnership.entries.toList()
            Case.ManagedStaleLoading, Case.ManagedStaleError, Case.ManagedStaleUnavailable -> f.managedReads.ownerships = ManagedOwnership.entries.toList()
            Case.CreateBusy -> f.backend.creationGate = gate
            Case.CreatePathExists -> f.backend.creationFailure = GitCreateWorktreeFailure(GitFailure(GitFailureKind.InvalidRequest, "目标路径已存在；草稿保留。"))
            Case.CreateNeedsRescan -> f.backend.creationFailure = GitCreateWorktreeFailure(GitFailure(GitFailureKind.CommandFailed, "创建结果未知；必须重扫实际清单。"), needsRescan = true)
            else -> Unit
        }
        if (case == Case.GitRemote || case == Case.WorktreesRemote) {
            f.git.bind(GitWorktreesPageFixture.binding.copy(access = GitAccess.RemoteUnavailable, generation = 2))
        }
        if (case != Case.GitUnselected && case != Case.WorktreesUnselected) {
            f.git.selectRepository(if (case == Case.GitLongReady) GitWorktreesPageFixture.longRoot else GitWorktreesPageFixture.root)
        }
    }

    private fun captureDirectory(): File {
        val configured = System.getenv("HARNESS_GIT_WORKTREES_CAPTURE_DIR")?.takeIf { it.isNotBlank() }
        val directory = if (configured != null) File(configured) else {
            val root = generateSequence(File(System.getProperty("user.dir") ?: ".").absoluteFile) { it.parentFile }
                .firstOrNull { File(it, "docs/desktop-ui").isDirectory } ?: error("Cannot locate docs/desktop-ui")
            File(root, "docs/desktop-ui/captures/git-worktrees")
        }
        check(directory.isDirectory || directory.mkdirs()) { "Cannot create capture directory: $directory" }
        return directory
    }

    private fun writeBitmap(file: File, bitmap: ImageBitmap, width: Int, height: Int) {
        assertEquals(width, bitmap.width, file.name)
        assertEquals(height, bitmap.height, file.name)
        Image.makeFromBitmap(bitmap.asSkiaBitmap()).use { image ->
            requireNotNull(image.encodeToData(EncodedImageFormat.PNG)).use { file.writeBytes(it.bytes) }
        }
        val decoded = requireNotNull(ImageIO.read(file)) { "Unreadable PNG: $file" }
        assertEquals(width, decoded.width, file.name)
        assertEquals(height, decoded.height, file.name)
        assertTrue(file.length() > 0, file.name)
        println("GIT WORKTREES SCREENSHOT: ${file.absolutePath} ($width x $height)")
    }

    /** The palette [HarnessTheme] actually provides for an explicit capture mode. */
    private fun paletteOf(theme: HarnessThemeMode): HarnessColors =
        if (theme == HarnessThemeMode.Dark) HarnessDarkColors else HarnessLightColors

    /**
     * The substrings the word-level diff tinted with its emphasis colour, read back from the rendered
     * text's span styles. Only the emphasis tint is counted; the weaker line background uses a
     * different colour, so an untouched line contributes nothing. An empty result means the emphasis
     * is off.
     */
    private fun gitWordEmphasisRanges(node: SemanticsNode, palette: HarnessColors): List<String> {
        val emphasis = setOf(
            palette.DiffAdded.copy(alpha = wordDiffTestAlpha),
            palette.DiffRemoved.copy(alpha = wordDiffTestAlpha),
        )
        return node.config.getOrNull(SemanticsProperties.Text).orEmpty().flatMap { annotated ->
            annotated.spanStyles.filter { it.item.background in emphasis }
                .map { annotated.text.substring(it.start, it.end) }
        }
    }

    /**
     * Parks the pointer in the page corner so a capture does not carry a hover highlight the reader
     * would otherwise have to discount when comparing two states.
     */
    private fun ComposeUiTest.parkGitPointer() {
        val viewport = onNodeWithTag(viewportTag).fetchSemanticsNode().boundsInRoot
        // The viewport fills the window, so its own origin is the window origin: the corner below the
        // centred settings column carries no hover target.
        onNodeWithTag(viewportTag).performMouseInput { moveTo(Offset(viewport.width - 2f, viewport.height - 2f)) }
        settleGitLayout()
    }

    private enum class Case(val slug: String, val git: Boolean = false) {
        GitUnselected("git-unselected", true), GitLoading("git-repository-loading", true), GitDataLoading("git-data-loading", true),
        GitEmpty("git-empty", true), GitError("git-error", true), GitUnavailable("git-unavailable", true),
        GitNotRepository("git-not-repository", true), GitRemote("git-remote-unavailable", true), GitReadError("git-read-error", true),
        GitLongReady("git-long-paths-refs", true), GitBranchMenu("git-long-branch-menu", true),
        GitDiffBaseMenu("git-diff-base-menu", true), GitLargeDiff("git-bounded-large-diff", true),
        GitWordLevelDiff("git-word-level-diff", true),
        GitHideImports("git-hide-imports", true),
        GitLoadFullFiles("git-load-full-files", true),
        WorktreesUnselected("worktrees-unselected"), WorktreesLoading("worktrees-loading"), WorktreesEmpty("worktrees-empty"),
        WorktreesError("worktrees-error"), WorktreesRemote("worktrees-remote-unavailable"), WorktreesLongReady("worktrees-long-ready"),
        NoManagedService("managed-service-absent"), ManagedLoading("managed-loading"), ManagedEmpty("managed-empty"),
        ManagedError("managed-error"), ManagedUnavailable("managed-unavailable"), ManagedVariants("managed-ownership-variants"),
        ManagedStaleLoading("managed-stale-loading"), ManagedStaleError("managed-stale-error"), ManagedStaleUnavailable("managed-stale-unavailable"),
        CreateBusy("creation-busy"), CreatePathExists("creation-path-exists"), CreateNeedsRescan("creation-needs-rescan"), CreateSuccess("creation-created"),
    }

    private companion object {
        const val viewportTag = "git-worktrees-page-viewport"

        /**
         * Mirrors the shared module's package-internal `WORD_DIFF_EMPHASIS_ALPHA` (not visible here):
         * the emphasis tint is the diff green/red at this alpha, layered over the line background.
         */
        const val wordDiffTestAlpha = 0.35f

        /** A line only the full-context patch carries, so its presence proves the diff was re-read. */
        const val FULL_FILE_TAIL_LINE = "// still unchanged"
    }
}
