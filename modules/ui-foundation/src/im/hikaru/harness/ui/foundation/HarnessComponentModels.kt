package im.hikaru.harness.ui.foundation

/** Loading suppresses repeat actions; Error stays actionable so callers can retry. */
public fun HarnessControlState.canInteract(enabled: Boolean = true): Boolean =
    enabled && this != HarnessControlState.Loading

public enum class HarnessButtonVariant { Primary, Secondary, Ghost, Destructive }
public enum class HarnessControlSize { Regular, Compact }

/** Persistent action state. Press/hover/focus are owned by the Compose interaction source. */
public data class HarnessButtonState(
    val enabled: Boolean = true,
    val selected: Boolean = false,
    val control: HarnessControlState = HarnessControlState.Normal,
) {
    public val interactive: Boolean get() = control.canInteract(enabled)
}

public sealed interface HarnessMenuEntry {
    public data class Item(
        val key: String,
        val text: String,
        val enabled: Boolean = true,
        val state: HarnessControlState = HarnessControlState.Normal,
        val selected: Boolean = false,
        val destructive: Boolean = false,
        val shortcut: String? = null,
        val testTag: String = "menu:$key",
    ) : HarnessMenuEntry {
        public val interactive: Boolean get() = state.canInteract(enabled)
    }

    public data object Separator : HarnessMenuEntry
}

public enum class HarnessNavigation { Next, Previous, First, Last }

internal fun navigateHarnessKeys(keys: List<String>, current: String?, direction: HarnessNavigation): String? {
    if (keys.isEmpty()) return null
    val index = keys.indexOf(current)
    return when (direction) {
        HarnessNavigation.First -> keys.first()
        HarnessNavigation.Last -> keys.last()
        HarnessNavigation.Next -> keys[if (index < 0) 0 else (index + 1) % keys.size]
        HarnessNavigation.Previous -> keys[if (index < 0) keys.lastIndex else (index - 1 + keys.size) % keys.size]
    }
}

/** Separators and unavailable actions never become keyboard selection targets. */
public class HarnessMenuModel(public val entries: List<HarnessMenuEntry>) {
    private val items = entries.filterIsInstance<HarnessMenuEntry.Item>()
    init {
        require(items.all { it.key.isNotBlank() }) { "Menu keys must not be blank" }
        require(items.map { it.key }.distinct().size == items.size) { "Menu keys must be unique" }
    }

    public fun selectable(key: String?): HarnessMenuEntry.Item? = items.firstOrNull { it.key == key && it.interactive }
    public fun navigate(current: String?, direction: HarnessNavigation): String? =
        navigateHarnessKeys(items.filter { it.interactive }.map { it.key }, current, direction)
}

/** The owner applies the requested close before consuming the selection. */
public data class HarnessMenuSelection(
    val pendingKey: String? = null,
    val pendingEntries: List<HarnessMenuEntry>? = null,
) {
    public fun request(model: HarnessMenuModel, key: String): HarnessMenuSelection =
        if (pendingKey == null && model.selectable(key) != null)
            copy(pendingKey = key, pendingEntries = model.entries.toList()) else this

    public fun readyKey(expanded: Boolean): String? = pendingKey.takeUnless { expanded }

    /** Equal ordered snapshots are compatible; removed, reordered or disabled entries invalidate a queue. */
    public fun readyKey(expanded: Boolean, model: HarnessMenuModel): String? =
        readyKey(expanded)?.takeIf { pendingEntries == model.entries && model.selectable(it) != null }
}

public data class HarnessTab(
    val key: String,
    val title: String,
    val enabled: Boolean = true,
    val closable: Boolean = false,
)

/** Immutable selection contract; pages still own routes, panel targets and data mutations. */
public data class HarnessTabsState(val tabs: List<HarnessTab>, val selectedKey: String? = null) {
    init {
        require(tabs.all { it.key.isNotBlank() }) { "Tab keys must not be blank" }
        require(tabs.map { it.key }.distinct().size == tabs.size) { "Tab keys must be unique" }
    }

    /** A removed/disabled selection falls back to the first available tab. */
    public val effectiveSelectedKey: String?
        get() = tabs.firstOrNull { it.key == selectedKey && it.enabled }?.key ?: tabs.firstOrNull { it.enabled }?.key

    public fun select(key: String): HarnessTabsState =
        if (tabs.any { it.key == key && it.enabled }) copy(selectedKey = key) else this

    public fun navigate(direction: HarnessNavigation): HarnessTabsState =
        copy(selectedKey = navigateHarnessKeys(tabs.filter { it.enabled }.map { it.key }, effectiveSelectedKey, direction))

    /** Closing the active tab selects the next available neighbour, then the previous one. */
    public fun close(key: String): HarnessTabsState {
        val index = tabs.indexOfFirst { it.key == key && it.enabled && it.closable }
        if (index < 0) return this
        val remaining = tabs.filterNot { it.key == key }
        val next = if (effectiveSelectedKey == key) {
            tabs.drop(index + 1).firstOrNull { it.enabled }?.key
                ?: tabs.take(index).lastOrNull { it.enabled }?.key
        } else effectiveSelectedKey
        return HarnessTabsState(remaining, next)
    }
}

public data class HarnessRowState(
    val enabled: Boolean = true,
    val selected: Boolean = false,
    val control: HarnessControlState = HarnessControlState.Normal,
    val depth: Int = 0,
    /** null denotes a leaf row; true/false denotes an expandable row. */
    val expanded: Boolean? = null,
) {
    init { require(depth >= 0) { "Row depth must be non-negative" } }
    public val interactive: Boolean get() = control.canInteract(enabled)
    public fun toggleExpanded(): HarnessRowState =
        if (interactive && expanded != null) copy(expanded = !expanded) else this
}

public sealed interface HarnessContentState {
    public data object Ready : HarnessContentState
    public data class Loading(val message: String = "Loading…") : HarnessContentState
    public data class Empty(val title: String, val description: String? = null) : HarnessContentState
    public data class Error(val message: String, val retryLabel: String = "Retry") : HarnessContentState
}
