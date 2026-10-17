package im.hikaru.harness.client.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * One tab of the **side panel**. Tabs are not the window's pages: the side panel owns them, the way the
 * live shell's `任务选项卡` lists the tool pages opened beside a conversation (新标签页 / 变更 / 终端 / 文件 /
 * 侧边聊天). A conversation is never one of these — it is what the *other* pane shows.
 *
 * [page] is the page the tab shows, or null for a tab that has not been bound to one yet.
 */
public data class HarnessWorkspaceTab(val id: String, val title: String, val page: PageId? = null)

/**
 * The side panel's layout. The two panes are not two tabs: one is the **conversation**, which is not a
 * tab at all, and the other is the **side panel**, which is the thing that holds tabs.
 *
 * Measured on the live shell: with the panel beside the conversation the main titlebar is cut in two,
 * the conversation's segment carrying `聊天工具栏` with the conversation's name and the panel's the
 * `任务选项卡` tablist; with the panel in full view the conversation pane is pushed off-screen
 * (`x = -660`) and the conversation is listed as a `聊天` tab alongside the panel's own.
 */
public sealed interface HarnessTabLayout {
    /** 完整视图: the side panel fills the main region and the conversation is pushed aside. */
    public data object Full : HarnessTabLayout

    /** The conversation and the side panel side by side, with the draggable seam between them. */
    public data object Split : HarnessTabLayout
}

/** Which half the conversation is on; the side panel takes the other one. */
public enum class HarnessPaneSide { Left, Right }

/**
 * The side panel's tabs, its layout, and the page the conversation pane shows.
 *
 * The conversation is deliberately *not* in [tabs]: the live shell draws it in the pane's own title
 * segment as a conversation title (or, in full view, as a `聊天` chip), and it is selected by the
 * navigator rather than by the tab strip. Selecting a side panel tab therefore never moves the
 * conversation.
 */
public class HarnessTabStripState(
    initialTabs: List<HarnessWorkspaceTab>,
    initialActive: String,
    initialConversation: PageId? = PageId.Home,
) {
    init {
        require(initialTabs.map { it.id }.distinct().size == initialTabs.size)
        require(initialTabs.isEmpty() || initialTabs.any { it.id == initialActive })
    }

    /** The side panel's tabs, in strip order. */
    public var tabs: List<HarnessWorkspaceTab> by mutableStateOf(initialTabs)
        private set

    /** The side panel's selected tab. */
    public var activeId: String by mutableStateOf(initialActive)
        private set

    /** How the side panel is laid out against the conversation. */
    public var layout: HarnessTabLayout by mutableStateOf(HarnessTabLayout.Split)
        private set

    /**
     * The page the conversation pane draws. It is what the shell navigates to: the navigator and every
     * in-page gesture move *this*, and the strip deliberately does not.
     */
    public var conversationPage: PageId? by mutableStateOf(initialConversation)
        private set

    /**
     * Share of the workspace width the conversation keeps while the panel is beside it. Codex's panes
     * are user-sized rather than equal halves, so the seam is draggable and this survives a side swap.
     */
    public var splitFraction: Float by mutableStateOf(DEFAULT_SPLIT_FRACTION)
        private set

    /**
     * Whether the side panel is closed. Closing it is not a view mode over a split — measured, the
     * panel's `aside` is unmounted and the conversation is the only pane left — so hiding always
     * collapses to the conversation alone.
     *
     * Closed with **no** tabs the trailing control is 新建标签页, which opens the first one; closed with
     * tabs it is the count badge 显示标签页, which only brings the panel back. That is the difference
     * between the two, and it is why this starts closed: the live shell opens on the new chat with no
     * panel and no tabs.
     */
    public var tabsHidden: Boolean by mutableStateOf(true)
        private set

    /**
     * Which half holds the conversation. The two panes are not interchangeable, and the side panel's
     * pane is the one that owns the tab strip. Measured on the live shell, LTR defaults to the
     * conversation on the left, so the side panel starts on the right — Codex persists the choice under
     * `default-unified-workspace-chat-pane-side`, whose stored value is the *conversation's* side.
     */
    public var contentSide: HarnessPaneSide by mutableStateOf(HarnessPaneSide.Left)
        private set

    /** The pane that holds the side panel, i.e. the pane the tab strip belongs to. */
    public val sidePanelSide: HarnessPaneSide
        get() = if (contentSide == HarnessPaneSide.Left) HarnessPaneSide.Right else HarnessPaneSide.Left

    /** The tab the side panel shows, or null while the id is unknown or unset. */
    public val sidePanelTab: HarnessWorkspaceTab? get() = tabs.firstOrNull { it.id == activeId }

    /**
     * Whether the page the conversation pane is showing is a **chat page** — 新聊天页 or a 会话 — and
     * therefore whether the side panel belongs on screen at all.
     *
     * The shell owns this, the way it owns the title bar's own actions, because only it knows which page
     * it navigated to. Measured on the live shell: 资料库, 定时任务, 插件 and 代码审查 mount **no** side
     * panel — their `aside` reads 0 wide, no `任务选项卡` tablist is rendered, no
     * `data-app-shell-workspace-layout-toggle` is left in the title bar, and the main area takes the whole
     * width right of the navigator (2215 of 2560 on 定时任务). Only the chat page keeps the panel.
     *
     * The tabs themselves are **kept** while it is false — the reference still reports its unified tab
     * strip on those pages — so coming back to a chat page restores the panel exactly as it was.
     */
    public var onChatPage: Boolean by mutableStateOf(true)

    /**
     * Trade places with the side panel — the reference's `UW(e, {mode, contentSide})`, which it triggers
     * by dropping a tab on the other half. The strip follows the side panel, so it crosses with it.
     */
    public fun swapSides() {
        contentSide = sidePanelSide
    }

    /**
     * How many times the conversation has been pointed at a page. The shell follows the strip by
     * observing its state, so a navigation that changes nothing else — choosing 新聊天 while already on
     * 首页 — would otherwise be invisible. Every [selectConversation] bumps this.
     */
    public var selectionEpoch: Int by mutableStateOf(0)
        private set

    /** The tab a pane holds, for the pane that draws it; null while the id is unknown or unset. */
    public fun tabFor(id: String?): HarnessWorkspaceTab? = tabs.firstOrNull { it.id == id }

    /** The page the conversation pane shows, or null while the conversation is unbound. */
    public val activePage: PageId? get() = conversationPage

    /** Point the conversation pane at [page]: what the navigator and every in-page gesture do. */
    public fun selectConversation(page: PageId?) {
        if (page == conversationPage) {
            selectionEpoch++
            return
        }
        conversationPage = page
        selectionEpoch++
    }

    public fun toggleTabsHidden() {
        tabsHidden = !tabsHidden
        // Opening the panel brings it back beside the conversation, which is the layout the live shell
        // returns to whichever way it was closed.
        layout = HarnessTabLayout.Split
    }

    private var history: List<String> by mutableStateOf(listOf(initialActive).filter { id -> initialTabs.any { it.id == id } })
    private var historyIndex: Int by mutableStateOf(history.lastIndex)
    private var nextTabNumber = 1
    public val canGoBack: Boolean get() = historyIndex > 0
    public val canGoForward: Boolean get() = historyIndex < history.lastIndex

    public val activeTab: HarnessWorkspaceTab? get() = sidePanelTab

    /**
     * Focus a side panel tab. [side] is accepted for call compatibility and ignored: there is one side
     * panel, so a selection can never mean "put this tab in the other half".
     */
    public fun select(id: String, side: HarnessPaneSide? = null) {
        if (tabs.none { it.id == id }) return
        activate(id)
    }

    private fun activate(id: String) {
        focus(id)
        if (history.getOrNull(historyIndex) != id) {
            history = history.take(historyIndex + 1) + id
            historyIndex = history.lastIndex
        }
    }

    /** Point the strip at a tab. Every move runs through here so [selectionEpoch] counts them all. */
    private fun focus(id: String) {
        activeId = id
        selectionEpoch++
    }

    public fun back() {
        if (!canGoBack) return
        focus(history[--historyIndex])
    }

    public fun forward() {
        if (!canGoForward) return
        focus(history[++historyIndex])
    }

    public fun close(id: String) {
        val index = tabs.indexOfFirst { it.id == id }
        if (index < 0) return
        tabs = tabs.filterNot { it.id == id }
        if (activeId == id) {
            focus(tabs.getOrNull(index.coerceAtMost(tabs.lastIndex))?.id.orEmpty())
        }
        val prefixCount = history.take(historyIndex + 1).count { it != id }
        history = history.filterNot { it == id }
        historyIndex = prefixCount - 1
        if (history.getOrNull(historyIndex) != activeId && activeId.isNotEmpty()) activate(activeId)
        // Closing the last tab leaves the panel with nothing to show, and the live shell drops the panel
        // with it rather than keeping an empty strip.
        if (tabs.isEmpty()) tabsHidden = true
    }

    public fun add(tab: HarnessWorkspaceTab) {
        // Opening a tool that is already up brings its tab forward rather than adding a second copy of
        // it, which is what the reference does when the same tool is chosen twice.
        if (tabs.any { it.id == tab.id }) {
            activate(tab.id)
            layout = HarnessTabLayout.Split
            tabsHidden = false
            return
        }
        tabs = tabs + tab
        activate(tab.id)
        // Opening a tab brings the panel back beside the conversation.
        layout = HarnessTabLayout.Split
        tabsHidden = false
    }

    public fun addNewTab() {
        var id: String
        do { id = "tab-${nextTabNumber++}" } while (tabs.any { it.id == id })
        add(HarnessWorkspaceTab(id, "新标签页", PageId.NewTab))
    }

    /**
     * Drop a tab on the left or right half of the workspace. The tab belongs to the side panel, so the
     * drop puts the **panel** there and the conversation takes the other half — the reference's
     * `HW(e, t, {contentSide, mode: 'split'})`, whose cue text is 移至左侧窗格 / 移至右侧窗格.
     */
    public fun moveToHalf(id: String, right: Boolean) {
        if (tabs.none { it.id == id }) return
        activate(id)
        // `contentSide` is the conversation's side, so the panel taking the right half means the
        // conversation takes the left one.
        contentSide = if (right) HarnessPaneSide.Left else HarnessPaneSide.Right
        layout = HarnessTabLayout.Split
        tabsHidden = false
    }

    /** 进入完整视图 / 退出完整视图: hand the whole main region to one side or to both. */
    public fun toggleSplit() {
        layout = if (layout == HarnessTabLayout.Split) HarnessTabLayout.Full else HarnessTabLayout.Split
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

    /** Back beside the conversation: what 进入分屏视图 and 退出完整视图 both do. */
    public fun unsplit() { layout = HarnessTabLayout.Split }

    private companion object {
        const val DEFAULT_SPLIT_FRACTION = 0.5f
        const val MIN_SPLIT_FRACTION = 0.2f
        const val MAX_SPLIT_FRACTION = 0.8f
    }
}

/** The 新标签页 tool page: the 工具 list with the address bar, and what 新建标签页 opens. */
public val HarnessNewTabPage: HarnessWorkspaceTab =
    HarnessWorkspaceTab("new-tab", "新标签页", PageId.NewTab)

/** The 变更 tool page: the repository's own changes, which is the Settings Git page. */
public val HarnessChangesPage: HarnessWorkspaceTab =
    HarnessWorkspaceTab("changes", "变更", PageId.Settings(SettingsSection.Git))

/**
 * The side panel's tool pages, as this client can draw them: 新标签页 and 变更. 终端 / 文件 / 侧边聊天 are
 * tool pages the reference also lists and this client has no surface for, so they are not here. Used by
 * the tests to name the tabs they open with; a real window starts with none of them (see
 * [rememberHarnessTabStripState]).
 */
public val HarnessFixtureTabs: List<HarnessWorkspaceTab> = listOf(HarnessNewTabPage, HarnessChangesPage)

/**
 * The tab the pane being composed holds, provided by the region around each pane's surface. The
 * **conversation** pane provides `null`, which is how a pane says "draw the shell's own page, I am not a
 * tab"; only the side panel's pane hands a tab through.
 */
public val LocalHarnessPaneTab: ProvidableCompositionLocal<HarnessWorkspaceTab?> =
    staticCompositionLocalOf { null }

/**
 * The side panel's tab strip, or null where no window owns one (mobile, previews, tests). The strip is
 * the *side panel's* selector, so the app reads the conversation's page from [HarnessTabStripState.conversationPage]
 * and navigates to it; a window that provides nothing leaves navigation to the pages themselves.
 */
public val LocalHarnessTabStrip: ProvidableCompositionLocal<HarnessTabStripState?> =
    staticCompositionLocalOf { null }

/**
 * A window's opening state: **no side panel and no tabs**, which is where the reference lands — a fresh
 * profile has an empty persisted tab list, the conversation is 新对话页, and the trailing cluster holds
 * the one control that opens the first tab (新建标签页). 新建标签页 then brings the panel up beside the
 * conversation, exactly as the reference does.
 */
@Composable
public fun rememberHarnessTabStripState(): HarnessTabStripState = remember {
    HarnessTabStripState(emptyList(), initialActive = "", initialConversation = PageId.Home)
}
