package im.hikaru.harness.desktop

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import im.hikaru.harness.client.git.*
import java.io.File
import kotlinx.serialization.json.*
import kotlin.math.max
import kotlin.math.min
import kotlin.test.assertTrue

/** One durable JSON sidecar for every PNG, including separate popup-root captures. */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.writeGitCaptureManifest(
    png: File, case: String, theme: String, width: Int, height: Int,
    bitmapWidth: Int, bitmapHeight: Int, captureTag: String, scrollTarget: String,
    git: GitState, managed: ManagedWorktreeState, managedServicePresent: Boolean,
) {
    val capture = onNodeWithTag(captureTag).assertIsDisplayed().fetchSemanticsNode()
    val captureBounds = capture.boundsInRoot
    val captureRoot = capture.rootId()
    val allNodes = onAllNodes(SemanticsMatcher("all semantic nodes") { true }, useUnmergedTree = true)
        .fetchSemanticsNodes()
    val mergedById = onAllNodes(SemanticsMatcher("all merged semantic nodes") { true })
        .fetchSemanticsNodes().associateBy { it.id }
    val nodes = allNodes.filter { it.config.contains(SemanticsProperties.TestTag) }
    val repository = (git.repository as? GitRead.Ready<GitRepository>)?.value
    val renderedManaged = if (!managedServicePresent) null else if (managed.repository == repository) managed
        else ManagedWorktreeState(repository = repository)
    val manifest = buildJsonObject {
        put("schema", "harness.git-worktrees.capture.v1")
        put("image", png.name)
        put("case", case)
        put("theme", theme)
        put("width", width)
        put("height", height)
        put("bitmapWidth", bitmapWidth)
        put("bitmapHeight", bitmapHeight)
        put("units", "pixels")
        put("density", this@writeGitCaptureManifest.density.density)
        put("captureTag", captureTag)
        put("captureRootId", captureRoot)
        put("captureViewport", captureBounds.json())
        put("scrollTarget", scrollTarget)
        put("state", buildJsonObject {
            put("binding", git.binding?.toString()?.let(::JsonPrimitive) ?: JsonNull)
            put("selectedCwd", git.selectedCwd?.let(::JsonPrimitive) ?: JsonNull)
            put("repository", git.repository.phase())
            put("repositoryRoot", repository?.root?.let(::JsonPrimitive) ?: JsonNull)
            put("status", git.status.phase())
            put("branches", git.branches.phase())
            put("worktrees", git.worktrees.phase())
            put("diff", git.diff.phase())
            put("commitSnapshot", git.commitSnapshot.phase())
            put("diffSource", git.diffRequest.source.name)
            put("selectedBranchRef", git.selectedBranchRef?.let(::JsonPrimitive) ?: JsonNull)
            put("baseRef", git.diffRequest.baseRef?.let(::JsonPrimitive) ?: JsonNull)
            put("creationBusy", git.creationBusy)
            put("checkoutBusy", git.checkoutBusy)
            put("commitBusy", git.commitBusy)
            put("creation", git.creation.toString())
            put("checkout", git.checkout.toString())
            put("commit", git.commit.toString())
            put("managedServicePresent", managedServicePresent)
            put("managedPhase", renderedManaged?.phase?.name?.let(::JsonPrimitive) ?: JsonNull)
            put("managedStale", renderedManaged?.stale?.let(::JsonPrimitive) ?: JsonNull)
            put("managedMessage", renderedManaged?.message?.let(::JsonPrimitive) ?: JsonNull)
            put("managedEntries", buildJsonArray {
                renderedManaged?.inventory?.entries?.forEach { entry ->
                    add(buildJsonObject { put("root", entry.root); put("ownership", entry.ownership.name) })
                }
            })
        })
        // SettingsFormFrame's page scroll is untagged. Record it as well as tagged nested lists.
        put("scrollContainers", buildJsonArray {
            allNodes.forEach { node ->
                val vertical = node.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)
                val horizontal = node.config.getOrNull(SemanticsProperties.HorizontalScrollAxisRange)
                if (vertical != null || horizontal != null) add(buildJsonObject {
                    put("id", node.id); put("rootId", node.rootId())
                    put("tag", node.config.getOrNull(SemanticsProperties.TestTag)?.let(::JsonPrimitive) ?: JsonNull)
                    put("clippedBoundsInRoot", node.boundsInRoot.json())
                    vertical?.let { put("vertical", buildJsonObject { put("value", it.value()); put("max", it.maxValue()) }) }
                    horizontal?.let { put("horizontal", buildJsonObject { put("value", it.value()); put("max", it.maxValue()) }) }
                })
            }
        })
        put("taggedNodes", buildJsonArray {
            nodes.forEach { node ->
                val clipped = node.boundsInRoot
                val layout = Rect(node.positionInRoot.x, node.positionInRoot.y,
                    node.positionInRoot.x + node.size.width, node.positionInRoot.y + node.size.height)
                val sameRoot = node.rootId() == captureRoot
                val visibleWidth = max(0f, min(clipped.right, captureBounds.right) - max(clipped.left, captureBounds.left))
                val visibleHeight = max(0f, min(clipped.bottom, captureBounds.bottom) - max(clipped.top, captureBounds.top))
                val intersects = sameRoot && visibleWidth > 0f && visibleHeight > 0f
                val fullyContained = intersects && layout.width > 0f && layout.height > 0f &&
                    clipped.left <= layout.left + 1f && clipped.top <= layout.top + 1f &&
                    clipped.right >= layout.right - 1f && clipped.bottom >= layout.bottom - 1f &&
                    layout.left >= captureBounds.left - 1f && layout.top >= captureBounds.top - 1f &&
                    layout.right <= captureBounds.right + 1f && layout.bottom <= captureBounds.bottom + 1f
                // Action/disabled semantics may live on a merged input's descendants. Match the
                // merged configuration used by assertIsEnabled while retaining unmerged geometry.
                val actionConfig = mergedById[node.id]?.config ?: node.config
                val action = actionConfig.contains(SemanticsActions.OnClick) || actionConfig.contains(SemanticsActions.SetText)
                add(buildJsonObject {
                    put("id", node.id)
                    put("tag", node.config[SemanticsProperties.TestTag])
                    put("rootId", node.rootId())
                    put("layoutBoundsInRoot", layout.json())
                    put("clippedBoundsInRoot", clipped.json())
                    put("clippedBoundsInWindow", node.boundsInWindow.json())
                    put("inCaptureRoot", sameRoot)
                    // Intersection is geometric evidence, not a claim about popup occlusion or pixel parity.
                    put("intersectsCapture", intersects)
                    put("fullyContainedInCapture", fullyContained)
                    put("enabled", if (action || actionConfig.contains(SemanticsProperties.Disabled))
                        JsonPrimitive(!actionConfig.contains(SemanticsProperties.Disabled)) else JsonNull)
                    node.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)?.let { range ->
                        put("verticalScroll", buildJsonObject { put("value", range.value()); put("max", range.maxValue()) })
                    }
                    node.config.getOrNull(SemanticsProperties.HorizontalScrollAxisRange)?.let { range ->
                        put("horizontalScroll", buildJsonObject { put("value", range.value()); put("max", range.maxValue()) })
                    }
                })
            }
        })
    }
    assertTrue(captureBounds.width > 0f && captureBounds.height > 0f, "capture must have visible area")
    assertTrue(nodes.any { it.rootId() == captureRoot && it.boundsInRoot.width > 0f && it.boundsInRoot.height > 0f })
    File(png.parentFile, png.nameWithoutExtension + ".json").writeText(manifest.toString() + System.lineSeparator())
}

private fun SemanticsNode.rootId(): Int = generateSequence(this) { it.parent }.last().id
private fun Rect.json(): JsonObject = buildJsonObject {
    put("left", left); put("top", top); put("right", right); put("bottom", bottom)
    put("width", width); put("height", height)
}
private fun GitRead<*>.phase(): String = when (this) {
    GitRead.Unselected -> "Unselected"
    GitRead.Loading -> "Loading"
    is GitRead.Ready -> "Ready"
    is GitRead.Failed -> "Failed: " + failure.kind.name + ": " + failure.message
}
