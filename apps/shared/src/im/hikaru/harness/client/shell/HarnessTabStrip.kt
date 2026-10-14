package im.hikaru.harness.client.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/** Static fixture tabs; real session binding belongs to a later change. */
public data class HarnessWorkspaceTab(val id: String, val title: String)

public sealed interface HarnessTabLayout {
    public data object Single : HarnessTabLayout
    public data class Split(val left: String, val right: String) : HarnessTabLayout
}

/** Window-owned tabs and the single or side-by-side main surface. */
public class HarnessTabStripState(initialTabs: List<HarnessWorkspaceTab>, initialActive: String) {
    init {
        require(initialTabs.map { it.id }.distinct().size == initialTabs.size)
        require(initialTabs.isEmpty() || initialTabs.any { it.id == initialActive })
    }

    public var tabs: List<HarnessWorkspaceTab> by mutableStateOf(initialTabs)
        private set
    public var activeId: String by mutableStateOf(initialActive)
        private set
    public var layout: HarnessTabLayout by mutableStateOf(HarnessTabLayout.Single)
        private set
    /**
     * Share of the workspace width the left pane keeps while split. Codex's panes are user-sized
     * rather than equal halves, so the seam is draggable and this survives a side swap.
     */
    public var splitFraction: Float by mutableStateOf(DEFAULT_SPLIT_FRACTION)
        private set
    public val split: HarnessTabLayout.Split? get() = layout as? HarnessTabLayout.Split
    /**
     * Whether the tab strips are hidden (Codex 隐藏标签页). Hidden is a *view* mode, not a tab-pane
     * mode: the tabs stay open and keep their layout, a pane simply draws its page title where its tab
     * strip would be, and the trailing control that hid them turns into 新建标签页. Measured on the
     * live shell: the toggle swaps the strip for the pane's title bar in place, and the mode is shared
     * by both panes of a split.
     */
    public var tabsHidden: Boolean by mutableStateOf(false)
        private set

    public fun toggleTabsHidden() { tabsHidden = !tabsHidden }

    private var history: List<String> by mutableStateOf(listOf(initialActive).filter { id -> initialTabs.any { it.id == id } })
    private var historyIndex: Int by mutableStateOf(history.lastIndex)
    private var nextTabNumber = 1
    public val canGoBack: Boolean get() = historyIndex > 0
    public val canGoForward: Boolean get() = historyIndex < history.lastIndex

    public fun select(id: String) {
        if (tabs.none { it.id == id }) return
        val previousActive = activeId
        activate(id)
        // Focusing either visible tab retains the split; another tab replaces the focused side.
        split?.let { current ->
            if (id != current.left && id != current.right) {
                layout = if (current.left == previousActive) current.copy(left = id) else current.copy(right = id)
            }
        }
    }

    private fun activate(id: String) {
        activeId = id
        if (history.getOrNull(historyIndex) != id) {
            history = history.take(historyIndex + 1) + id
            historyIndex = history.lastIndex
        }
    }

    public fun back() {
        if (!canGoBack) return
        activeId = history[--historyIndex]
        layout = HarnessTabLayout.Single
    }

    public fun forward() {
        if (!canGoForward) return
        activeId = history[++historyIndex]
        layout = HarnessTabLayout.Single
    }

    public fun close(id: String) {
        val index = tabs.indexOfFirst { it.id == id }
        if (index < 0) return
        val current = split
        tabs = tabs.filterNot { it.id == id }
        if (current != null && (current.left == id || current.right == id)) {
            activeId = if (current.left == id) current.right else current.left
            layout = HarnessTabLayout.Single
        } else if (activeId == id) {
            activeId = tabs.getOrNull(index.coerceAtMost(tabs.lastIndex))?.id.orEmpty()
        }
        val prefixCount = history.take(historyIndex + 1).count { it != id }
        history = history.filterNot { it == id }
        historyIndex = prefixCount - 1
        if (history.getOrNull(historyIndex) != activeId && activeId.isNotEmpty()) activate(activeId)
    }

    public fun add(tab: HarnessWorkspaceTab) {
        if (tabs.any { it.id == tab.id }) return
        tabs = tabs + tab
        activate(tab.id)
        layout = HarnessTabLayout.Single
        // Opening a tab brings the strips back: while they are hidden the trailing control is 新建标签页,
        // which is exactly the way out of the hidden mode.
        tabsHidden = false
    }

    public fun addNewTab() {
        var id: String
        do { id = "tab-${nextTabNumber++}" } while (tabs.any { it.id == id })
        add(HarnessWorkspaceTab(id, "新标签页"))
    }

    /** Move a visible tab across the split to swap sides, or replace the target side with another tab. */
    public fun moveToHalf(id: String, right: Boolean) {
        if (tabs.none { it.id == id }) return
        val current = split
        val other = if (current == null) {
            activeId.takeIf { it != id } ?: tabs.firstOrNull { it.id != id }?.id
        } else {
            (if (right) current.left else current.right).takeIf { it != id }
                ?: if (right) current.right else current.left
        }
        if (other == null || other == id) return
        layout = HarnessTabLayout.Split(if (right) other else id, if (right) id else other)
        activate(id)
    }

    public fun toggleSplit() {
        if (split != null) unsplit() else moveToHalf(activeId, right = false)
    }

    /** Clamped to a fifth of the workspace per side so neither pane can be dragged shut. */
    public fun resizeSplit(fraction: Float) {
        if (fraction.isNaN()) return
        splitFraction = fraction.coerceIn(MIN_SPLIT_FRACTION, MAX_SPLIT_FRACTION)
    }

    /**
     * Applies a seam drag of [deltaPixels] over a [workspacePixels] wide workspace. The seam passes a
     * raw pixel delta so the live fraction is read here, where a gesture cannot act on a stale one.
     */
    public fun moveSplit(deltaPixels: Float, workspacePixels: Float) {
        if (workspacePixels <= 0f) return
        resizeSplit(splitFraction + deltaPixels / workspacePixels)
    }

    public fun unsplit() { layout = HarnessTabLayout.Single }

    private companion object {
        const val DEFAULT_SPLIT_FRACTION = 0.5f
        const val MIN_SPLIT_FRACTION = 0.2f
        const val MAX_SPLIT_FRACTION = 0.8f
    }
}

public val HarnessFixtureTabs: List<HarnessWorkspaceTab> = listOf(
    HarnessWorkspaceTab("welcome", "新标签页"),
    HarnessWorkspaceTab("thread-a", "解释 Amper 编译删除失败"),
    HarnessWorkspaceTab("thread-b", "查看 Codex 复刻项目状态"),
)

@Composable
public fun rememberHarnessTabStripState(): HarnessTabStripState = remember {
    HarnessTabStripState(HarnessFixtureTabs, HarnessFixtureTabs.first().id)
}
