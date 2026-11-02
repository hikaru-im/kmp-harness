package im.hikaru.harness.client.summary

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Expansion memory and the preference key the summary sections persist under. */
class ThreadSummarySectionStateTest {
    @Test
    fun defaultExpansionFollowsTheCallersDefault() {
        val state = ThreadSummarySectionState()

        assertNull(state.persistedExpanded("sources"))
        assertTrue(state.isExpanded("sources"))
        assertFalse(state.isExpanded("sources", defaultCollapsed = true))
    }

    @Test
    fun initialExpansionOverridesTheDefault() {
        val state = ThreadSummarySectionState(mapOf("plan" to false))

        assertFalse(state.isExpanded("plan"))
        assertTrue(state.isExpanded("sources"))
    }

    @Test
    fun togglingWritesThePersistedPreferenceKey() {
        val writes = mutableListOf<Pair<String, Boolean>>()
        val state = ThreadSummarySectionState(persist = { key, expanded -> writes += key to expanded })

        state.toggle("plan")
        assertFalse(state.isExpanded("plan"))

        state.toggle("plan")
        assertTrue(state.isExpanded("plan"))

        assertEquals(
            listOf(
                ThreadSummarySectionExpandedStatePrefix + "plan" to false,
                ThreadSummarySectionExpandedStatePrefix + "plan" to true,
            ),
            writes,
        )
    }

    @Test
    fun expandAndCollapseAreIdempotent() {
        val writes = mutableListOf<Boolean>()
        val state = ThreadSummarySectionState(persist = { _, expanded -> writes += expanded })

        state.expand("sources")
        state.expand("sources")
        state.collapse("sources")
        state.collapse("sources")

        assertEquals(listOf(true, false), writes)
    }
}
