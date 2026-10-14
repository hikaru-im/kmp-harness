package im.hikaru.harness.client.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/**
 * Open/close state for a popover that is opened by hovering its trigger and kept open while the
 * pointer is over either the trigger or the popover itself.
 *
 * Both the rail 探索 menu and the navigator hover preview use the same contract, which the reference
 * implements with Radix HoverCard: hovering the trigger opens it, moving onto the popover keeps it
 * open, and it closes [settleMillis] after the pointer has left both. Tracking the "active" key while
 * the trigger is hovered is what lets moving from one trigger straight onto the popover — or from one
 * trigger to another — swap the content without an intermediate close.
 */
public class HoverPopoverState<K : Any> internal constructor(
    /** How long the popover stays up after the pointer has left both the trigger and the content. */
    public val settleMillis: Long,
) {
    /** True while the pointer is over the trigger. */
    public var triggerHovered: Boolean by mutableStateOf(false)
        internal set

    /** True while the pointer is over the popover content. */
    public var contentHovered: Boolean by mutableStateOf(false)
        internal set

    /** The key whose popover should be shown, or null when closed. */
    public var activeKey: K? by mutableStateOf(null)
        internal set

    /** The trigger currently under the pointer, used to keep [activeKey] in step with it. */
    public var hoveredKey: K? by mutableStateOf(null)
        internal set

    public val isOpen: Boolean get() = activeKey != null

    /**
     * Force-close, for a dismissal that comes from outside the hover pair (a click-away or a page
     * change) rather than from the pointer leaving the trigger and the content.
     */
    public fun close() {
        triggerHovered = false
        contentHovered = false
        hoveredKey = null
        activeKey = null
    }

    /** Feed the trigger's hover; [key] identifies which trigger. */
    public fun onTriggerHover(key: K, hovered: Boolean) {
        if (hovered) {
            hoveredKey = key
            triggerHovered = true
        } else if (hoveredKey == key) {
            hoveredKey = null
            triggerHovered = false
        }
        // A leave event for a trigger that is no longer the hovered one is stale: each trigger reports
        // its own hover through an asynchronous effect, so leaving one can be delivered *after* entering
        // the next. Clearing here on that stale event dropped `triggerHovered` to false while the pointer
        // was on the next trigger, and the popover closed the moment it had opened — the blink the user
        // saw when moving between rail items.
    }

    public fun onContentHover(hovered: Boolean) {
        contentHovered = hovered
    }
}

/**
 * Remembers a [HoverPopoverState] and runs the open/close effect: while the trigger is hovered the
 * active key tracks the hovered trigger, and once the pointer has left both the trigger and the
 * content the popover closes after a short settle.
 */
@Composable
public fun <K : Any> rememberHoverPopoverState(settleMillis: Long = 200): HoverPopoverState<K> {
    val state = remember { HoverPopoverState<K>(settleMillis) }
    LaunchedEffect(state.triggerHovered, state.contentHovered, state.hoveredKey) {
        when {
            state.triggerHovered -> state.activeKey = state.hoveredKey
            state.contentHovered -> Unit // keep the current key
            else -> {
                delay(settleMillis)
                if (!state.triggerHovered && !state.contentHovered) state.activeKey = null
            }
        }
    }
    return state
}