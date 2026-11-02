package im.hikaru.harness.client.summary

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import im.hikaru.harness.ui.foundation.HarnessIcon
import im.hikaru.harness.ui.foundation.HarnessIconName
import im.hikaru.harness.ui.foundation.HarnessText
import im.hikaru.harness.ui.foundation.HarnessTokens
import kotlinx.coroutines.delay

/**
 * How a [ThreadSummarySection] presents its content, mirroring Codex
 * `ThreadSummaryPanelSectionMode` (`accordion | dropdown | headerless`):
 *
 * - [Accordion] draws a header that toggles the content.
 * - [Dropdown] draws a header but always renders the content; the header is not a toggle.
 * - [Headerless] draws no header and always renders the content.
 */
public enum class ThreadSummarySectionMode { Accordion, Dropdown, Headerless }

/** Codex `THREAD_SUMMARY_PANEL_SECTION_AUTO_COLLAPSE_DELAY_MS`. */
public const val ThreadSummarySectionAutoCollapseDelayMillis: Long = 30_000L

/** Codex `THREAD_SUMMARY_PANEL_SECTION_EXPANDED_STATE_PREFIX`, for callers that persist expansion. */
public const val ThreadSummarySectionExpandedStatePrefix: String = "thread-summary-panel-section-expanded-"

internal enum class ThreadSummaryAutoCollapse { Pending, Collapsed, Canceled }

/**
 * Expansion and auto-collapse memory for one summary panel.
 *
 * The panel creates one of these with `remember` so expansion survives closing and reopening the
 * island. [persist] receives `(preferenceKey, expanded)` where `preferenceKey` is
 * [ThreadSummarySectionExpandedStatePrefix] plus the section key, so a durable settings store can be
 * wired without changing the primitive. Auto-collapse state is never persisted.
 */
public class ThreadSummarySectionState(
    initialExpanded: Map<String, Boolean> = emptyMap(),
    private val persist: ((preferenceKey: String, expanded: Boolean) -> Unit)? = null,
) {
    private val expandedValues = mutableStateMapOf<String, Boolean>()
    private val autoCollapseValues = mutableStateMapOf<String, ThreadSummaryAutoCollapse>()

    init {
        expandedValues.putAll(initialExpanded)
    }

    /** The explicit expansion choice for [sectionKey], or null when the caller's default applies. */
    public fun persistedExpanded(sectionKey: String): Boolean? = expandedValues[sectionKey]

    /** Applies a durable document once it loads. Restoring never re-persists the same values. */
    public fun restore(expanded: Map<String, Boolean>) {
        expandedValues.clear()
        expandedValues.putAll(expanded)
    }

    /** Snapshot for a durable store; auto-collapse state is intentionally excluded. */
    public fun snapshot(): Map<String, Boolean> = expandedValues.toMap()

    public fun isExpanded(sectionKey: String, defaultCollapsed: Boolean = false): Boolean =
        persistedExpanded(sectionKey) ?: !defaultCollapsed

    public fun setExpanded(sectionKey: String, expanded: Boolean) {
        if (expandedValues[sectionKey] == expanded) return
        expandedValues[sectionKey] = expanded
        persist?.invoke(ThreadSummarySectionExpandedStatePrefix + sectionKey, expanded)
    }

    public fun toggle(sectionKey: String, defaultCollapsed: Boolean = false) {
        setExpanded(sectionKey, !isExpanded(sectionKey, defaultCollapsed))
    }

    public fun expand(sectionKey: String) = setExpanded(sectionKey, true)

    public fun collapse(sectionKey: String) = setExpanded(sectionKey, false)

    internal fun autoCollapseState(sectionKey: String): ThreadSummaryAutoCollapse =
        autoCollapseValues[sectionKey] ?: ThreadSummaryAutoCollapse.Pending

    internal fun markAutoCollapsed(sectionKey: String) {
        autoCollapseValues[sectionKey] = ThreadSummaryAutoCollapse.Collapsed
    }

    internal fun cancelAutoCollapse(sectionKey: String) {
        autoCollapseValues[sectionKey] = ThreadSummaryAutoCollapse.Canceled
    }

    internal fun resetAutoCollapse(sectionKey: String) {
        autoCollapseValues.remove(sectionKey)
    }
}

/**
 * One summary-panel section. Codex's `ThreadSummaryPanelSection`: a chevron header with an optional
 * trailing count, content that is hidden while collapsed, and an optional 30-second auto-collapse.
 *
 * Clicking the header cancels a pending auto-collapse, so a section the user re-opens stays open until
 * its content changes again. When [autoCollapse] turns false after a collapse, the pending state is
 * restored, matching Codex's tri-state (`pending | collapsed | canceled`).
 */
@Composable
public fun ThreadSummarySection(
    sectionKey: String,
    title: String,
    state: ThreadSummarySectionState,
    modifier: Modifier = Modifier,
    count: Int? = null,
    mode: ThreadSummarySectionMode = ThreadSummarySectionMode.Accordion,
    defaultCollapsed: Boolean = false,
    autoCollapse: Boolean = false,
    autoCollapseDelayMillis: Long = ThreadSummarySectionAutoCollapseDelayMillis,
    isLast: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val autoState = state.autoCollapseState(sectionKey)

    LaunchedEffect(sectionKey, autoCollapse) {
        if (!autoCollapse) {
            if (state.autoCollapseState(sectionKey) == ThreadSummaryAutoCollapse.Collapsed) {
                state.resetAutoCollapse(sectionKey)
            }
            return@LaunchedEffect
        }
        if (state.autoCollapseState(sectionKey) != ThreadSummaryAutoCollapse.Pending) return@LaunchedEffect
        delay(autoCollapseDelayMillis)
        // A click while the delay is running cancels the collapse; the effect is not restarted by
        // that state change, so re-check before applying it.
        if (state.autoCollapseState(sectionKey) == ThreadSummaryAutoCollapse.Pending) {
            state.markAutoCollapsed(sectionKey)
        }
    }

    val expanded = if (autoCollapse && autoState == ThreadSummaryAutoCollapse.Collapsed) {
        false
    } else {
        state.persistedExpanded(sectionKey) ?: !defaultCollapsed
    }

    Column(modifier.fillMaxWidth()) {
        if (mode != ThreadSummarySectionMode.Headerless) {
            val toggle = mode == ThreadSummarySectionMode.Accordion
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (toggle) {
                            Modifier.clickable(role = Role.Button) {
                                if (autoCollapse) state.cancelAutoCollapse(sectionKey)
                                // Toggle against the effective state: an auto-collapsed section has
                                // no explicit preference yet, so the header must open it rather than
                                // writing the default back as a collapse.
                                state.setExpanded(sectionKey, !expanded)
                            }
                        } else {
                            Modifier
                        },
                    )
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .semantics(mergeDescendants = true) { testTag = "thread-summary-section-$sectionKey" },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Two),
            ) {
                HarnessText(
                    title,
                    color = HarnessTokens.Colors.TextMuted,
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(Modifier.weight(1f))
                if (count != null) {
                    HarnessText(
                        count.toString(),
                        modifier = Modifier.semantics {
                            testTag = "thread-summary-section-$sectionKey-count"
                        },
                        color = HarnessTokens.Colors.TextMuted,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                if (toggle) {
                    val rotation by animateFloatAsState(
                        targetValue = if (expanded) 0f else -90f,
                        label = "thread-summary-section-chevron",
                    )
                    HarnessIcon(
                        HarnessIconName.ChevronDown,
                        modifier = Modifier.rotate(rotation),
                        tint = HarnessTokens.Colors.TextMuted,
                        size = 14.dp,
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = mode == ThreadSummarySectionMode.Headerless ||
                mode == ThreadSummarySectionMode.Dropdown ||
                expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(Modifier.fillMaxWidth(), content = content)
        }
        if (!isLast) {
            HorizontalDivider(color = HarnessTokens.Colors.BorderSubtle)
        }
    }
}
