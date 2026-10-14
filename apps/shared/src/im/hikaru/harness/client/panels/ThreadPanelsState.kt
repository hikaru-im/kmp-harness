package im.hikaru.harness.client.panels

/** Controlled snapshot. The owner reduces local actions and supplies scoped external responses. */
public data class ThreadPanelsState(
    val scope: ThreadPanelScope,
    val openTabs: List<ThreadPanelTab> = ThreadPanelTab.entries.toList(),
    val selectedTab: ThreadPanelTab? = openTabs.firstOrNull(),
    val terminal: PanelLoad<BackgroundTerminalOutput> = PanelLoad.Unavailable(scope, PanelUnavailableReason.TerminalServiceMissing),
    val subagents: PanelLoad<SubagentOverview> = PanelLoad.Unavailable(scope, PanelUnavailableReason.ChildrenServiceMissing),
    val selectedChild: ChildAgentKey? = null,
    val detail: PanelLoad<ChildAgentDetail> = PanelLoad.Unavailable(scope, PanelUnavailableReason.ChildHistoryUnavailable),
    val terminalGeneration: Long = 0,
    val overviewGeneration: Long = 0,
    val detailGeneration: Long = 0,
    val showAllActive: Boolean = false,
    val showAllDone: Boolean = false,
    val overviewRefreshing: Boolean = false,
    val terminalControls: TerminalControls? = null,
    val terminalInventory: List<BackgroundTerminalSummary> = emptyList(),
    val selectedTerminalKey: BackgroundTerminalKey? = (terminal as? PanelLoad.Ready)?.value?.key,
    /** Changes for every controller owner incarnation, even when the visible scope is equal. */
    val ownerGeneration: Long = 0,
) {
    init {
        require(openTabs.distinct().size == openTabs.size)
        require(if (openTabs.isEmpty()) selectedTab == null else selectedTab in openTabs)
        require(listOf(terminal.scope, subagents.scope, detail.scope).all { it == scope })
        require(terminal !is PanelLoad.Ready || terminal.value.key.scope == scope)
        require(terminalControls == null || terminalControls.key.scope == scope)
        require(terminalInventory.all { it.key.scope == scope })
        require(terminalInventory.map { it.key.processId }.distinct().size == terminalInventory.size)
        require(selectedTerminalKey == null || selectedTerminalKey.scope == scope)
        require(subagents !is PanelLoad.Ready || subagents.value.scope == scope)
        require(selectedChild == null || (selectedChild.scope == scope && ThreadPanelTab.Subagents in openTabs &&
            subagents is PanelLoad.Ready && subagents.value.children.any { it.key == selectedChild }))
        require(detail !is PanelLoad.Ready || (detail.value.key == selectedChild && detail.value.key.scope == scope))
        require(detail !is PanelLoad.Loading || selectedChild != null)
        require(terminalGeneration >= 0 && overviewGeneration >= 0 && detailGeneration >= 0)
    }

    public val selectedSummary: ChildAgentSummary?
        get() = (subagents as? PanelLoad.Ready)?.value?.children?.firstOrNull { it.key == selectedChild }

    /** Both the current list authorization and the detail authorization must permit interaction. */
    public val interactiveDetail: ChildAgentDetail?
        get() = (detail as? PanelLoad.Ready)?.value?.takeIf {
            it.key == selectedChild && it.canInteract && selectedSummary?.canInteract == true
        }

    public fun visibleChildren(section: SubagentSection): List<ChildAgentSummary> {
        val rows = (subagents as? PanelLoad.Ready)?.value?.children(section) ?: return emptyList()
        return when (section) {
            SubagentSection.Active -> if (showAllActive) rows else rows.take(4)
            SubagentSection.Done -> if (showAllDone) rows else rows.take(10)
        }
    }
}

public sealed interface ThreadPanelAction {
    public val scope: ThreadPanelScope
    public data class SelectTab(override val scope: ThreadPanelScope, val tab: ThreadPanelTab) : ThreadPanelAction
    public data class OpenTab(override val scope: ThreadPanelScope, val tab: ThreadPanelTab) : ThreadPanelAction
    public data class CloseTab(override val scope: ThreadPanelScope, val tab: ThreadPanelTab) : ThreadPanelAction
    public data class ShowMore(override val scope: ThreadPanelScope, val section: SubagentSection) : ThreadPanelAction
    public data class SelectChild(val key: ChildAgentKey) : ThreadPanelAction {
        override val scope: ThreadPanelScope get() = key.scope
    }
    public data class SelectTerminal(val key: BackgroundTerminalKey) : ThreadPanelAction {
        override val scope: ThreadPanelScope get() = key.scope
    }
    public data class Back(override val scope: ThreadPanelScope) : ThreadPanelAction
    public data class BeginTerminalLoad(override val scope: ThreadPanelScope) : ThreadPanelAction
    public data class BeginOverviewLoad(override val scope: ThreadPanelScope) : ThreadPanelAction
    /** Refresh keeps an already verified selection while the new snapshot is in flight. */
    public data class BeginOverviewRefresh(override val scope: ThreadPanelScope) : ThreadPanelAction
    public data class BeginDetailLoad(override val scope: ThreadPanelScope) : ThreadPanelAction
    public data class ReceiveTerminal(
        override val scope: ThreadPanelScope,
        val generation: Long,
        val load: PanelLoad<BackgroundTerminalOutput>,
    ) : ThreadPanelAction
    public data class ReceiveTerminalSelection(
        val snapshot: BackgroundTerminalSelection, val generation: Long,
    ) : ThreadPanelAction {
        override val scope: ThreadPanelScope get() = snapshot.scope
    }
    public data class ReceiveOverview(
        override val scope: ThreadPanelScope,
        val generation: Long,
        val load: PanelLoad<SubagentOverview>,
    ) : ThreadPanelAction
    public data class ReceiveDetail(
        val key: ChildAgentKey,
        val generation: Long,
        val load: PanelLoad<ChildAgentDetail>,
    ) : ThreadPanelAction {
        override val scope: ThreadPanelScope get() = key.scope
    }
}

/** No IO here. Request generations must be captured AFTER reducing BeginLoad/SelectChild. */
public fun ThreadPanelsState.reduce(action: ThreadPanelAction): ThreadPanelsState {
    if (action.scope != scope) return this
    return when (action) {
        is ThreadPanelAction.SelectTab -> if (action.tab in openTabs) copy(selectedTab = action.tab) else this
        is ThreadPanelAction.OpenTab -> copy(openTabs = (openTabs + action.tab).distinct(), selectedTab = action.tab)
        is ThreadPanelAction.CloseTab -> {
            val index = openTabs.indexOf(action.tab)
            if (index < 0) return this
            val remaining = openTabs.filterNot { it == action.tab }
            val next = if (selectedTab == action.tab) remaining.getOrNull(index) ?: remaining.lastOrNull() else selectedTab
            val closed = copy(openTabs = remaining, selectedTab = next,
                selectedChild = if (action.tab == ThreadPanelTab.Subagents) null else selectedChild,
                detail = if (action.tab == ThreadPanelTab.Subagents) unavailableHistory() else detail)
            when (action.tab) {
                ThreadPanelTab.BackgroundOutput -> closed.copy(terminalGeneration = terminalGeneration + 1,
                    terminal = PanelLoad.Unavailable(scope, PanelUnavailableReason.TerminalServiceMissing), terminalControls = null)
                ThreadPanelTab.Subagents -> closed.copy(overviewGeneration = overviewGeneration + 1,
                    detailGeneration = detailGeneration + 1, showAllActive = false, showAllDone = false,
                    overviewRefreshing = false,
                    subagents = PanelLoad.Unavailable(scope, PanelUnavailableReason.ChildrenServiceMissing))
            }
        }
        is ThreadPanelAction.ShowMore -> if (subagents is PanelLoad.Ready && ThreadPanelTab.Subagents in openTabs) {
            when (action.section) {
                SubagentSection.Active -> copy(showAllActive = true)
                SubagentSection.Done -> copy(showAllDone = true)
            }
        } else this
        is ThreadPanelAction.SelectChild -> {
            if (ThreadPanelTab.Subagents !in openTabs) return this
            val row = (subagents as? PanelLoad.Ready)?.value?.children?.firstOrNull { it.key == action.key } ?: return this
            copy(selectedTab = ThreadPanelTab.Subagents, selectedChild = row.key,
                detailGeneration = detailGeneration + 1,
                detail = if (row.historyAvailability == ChildHistoryAvailability.Available) PanelLoad.Loading(scope) else unavailableHistory())
        }
        is ThreadPanelAction.SelectTerminal -> {
            if (ThreadPanelTab.BackgroundOutput !in openTabs || action.key == selectedTerminalKey ||
                terminalInventory.none { it.key == action.key }) return this
            copy(selectedTab = ThreadPanelTab.BackgroundOutput, selectedTerminalKey = action.key,
                terminalGeneration = terminalGeneration + 1, terminal = PanelLoad.Loading(scope), terminalControls = null)
        }
        is ThreadPanelAction.Back -> clearChild()
        is ThreadPanelAction.BeginTerminalLoad -> if (ThreadPanelTab.BackgroundOutput in openTabs) {
            copy(terminal = PanelLoad.Loading(scope), terminalGeneration = terminalGeneration + 1, terminalControls = null)
        } else this
        is ThreadPanelAction.BeginOverviewLoad -> if (ThreadPanelTab.Subagents in openTabs) {
            clearChild().copy(subagents = PanelLoad.Loading(scope), overviewGeneration = overviewGeneration + 1,
                overviewRefreshing = false)
        } else this
        is ThreadPanelAction.BeginOverviewRefresh -> if (ThreadPanelTab.Subagents in openTabs) {
            if (subagents is PanelLoad.Ready) copy(overviewGeneration = overviewGeneration + 1, overviewRefreshing = true)
            else reduce(ThreadPanelAction.BeginOverviewLoad(scope))
        } else this
        is ThreadPanelAction.BeginDetailLoad -> if (selectedSummary?.historyAvailability == ChildHistoryAvailability.Available) {
            copy(detailGeneration = detailGeneration + 1, detail = PanelLoad.Loading(scope))
        } else this
        is ThreadPanelAction.ReceiveTerminal -> {
            if (ThreadPanelTab.BackgroundOutput !in openTabs || action.generation != terminalGeneration || action.load.scope != scope) return this
            val incoming = (action.load as? PanelLoad.Ready)?.value
            if (incoming != null && incoming.key.scope != scope) return this
            if (incoming != null && selectedTerminalKey != null && incoming.key != selectedTerminalKey) return this
            val previous = (terminal as? PanelLoad.Ready)?.value
            if (incoming != null && previous != null && incoming.key == previous.key && incoming.revision <= previous.revision) return this
            copy(terminal = action.load, selectedTerminalKey = selectedTerminalKey ?: incoming?.key)
        }
        is ThreadPanelAction.ReceiveTerminalSelection -> {
            if (ThreadPanelTab.BackgroundOutput !in openTabs || action.generation != terminalGeneration ||
                (selectedTerminalKey != null && action.snapshot.selectedKey != selectedTerminalKey)) return this
            copy(terminalInventory = action.snapshot.inventory.toList(), selectedTerminalKey = action.snapshot.selectedKey)
                .reduce(ThreadPanelAction.ReceiveTerminal(scope, action.generation, action.snapshot.output))
        }
        is ThreadPanelAction.ReceiveOverview -> {
            if (ThreadPanelTab.Subagents !in openTabs || action.generation != overviewGeneration || action.load.scope != scope) return this
            val incoming = (action.load as? PanelLoad.Ready)?.value
            if (incoming != null && incoming.scope != scope) return this
            val selected = incoming?.children?.firstOrNull { it.key == selectedChild }
            when {
                selected == null -> clearChild().copy(subagents = action.load, overviewRefreshing = false)
                selected.historyAvailability == ChildHistoryAvailability.Unavailable -> copy(
                    subagents = action.load, overviewRefreshing = false,
                    detail = unavailableHistory(), detailGeneration = detailGeneration + 1)
                else -> copy(subagents = action.load, overviewRefreshing = false)
            }
        }
        is ThreadPanelAction.ReceiveDetail -> {
            if (ThreadPanelTab.Subagents !in openTabs || action.key != selectedChild ||
                action.generation != detailGeneration || action.load.scope != scope) return this
            if (action.load is PanelLoad.Ready && action.load.value.key != selectedChild) return this
            copy(detail = action.load)
        }
    }
}

private fun ThreadPanelsState.unavailableHistory(): PanelLoad.Unavailable =
    PanelLoad.Unavailable(scope, PanelUnavailableReason.ChildHistoryUnavailable)

private fun ThreadPanelsState.clearChild(): ThreadPanelsState =
    copy(selectedChild = null, detail = unavailableHistory(), detailGeneration = detailGeneration + 1)
