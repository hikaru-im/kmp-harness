package im.hikaru.harness.runtime

import im.hikaru.harness.runtime.event.BailEventKey
import im.hikaru.harness.runtime.event.PipelineEventKey
import im.hikaru.harness.runtime.event.SequentialEventKey
import im.hikaru.harness.runtime.event.SuspendWaterfallEventKey
import im.hikaru.harness.runtime.event.WaterfallEventKey
import im.hikaru.harness.runtime.intercept.InterceptKey
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class AdvancedContextTest {

    private data class TraceConfig(
        val labels: List<String>,
    )

    private object TraceIntercept :
        InterceptKey<TraceConfig>(
            name = "trace",
            merge = { parent, child ->
                TraceConfig(
                    labels = parent.labels + child.labels,
                )
            },
        )

    private object ResultKey :
        BailEventKey<String, String>("context.result")

    private object WaterfallKey :
        WaterfallEventKey<Int, Int>("context.waterfall")

    private object SequentialKey :
        SequentialEventKey<String>("context.sequential")

    private object SuspendWaterfallKey :
        SuspendWaterfallEventKey<Int, Int>("context.suspend-waterfall")

    private object PipelineKey :
        PipelineEventKey<String>("context.pipeline")

    @Test
    fun nestedInterceptsShouldMergeFromRootToCurrentContext() = runTest {
        val root =
            Context()

        val outer =
            root.intercept(
                TraceIntercept,
                TraceConfig(listOf("outer")),
            )

        val inherited =
            outer.child()

        val inner =
            inherited.intercept(
                TraceIntercept,
                TraceConfig(listOf("inner")),
            )

        assertNull(root.intercept(TraceIntercept))
        assertEquals(
            TraceConfig(listOf("outer")),
            inherited.intercept(TraceIntercept),
        )
        assertEquals(
            TraceConfig(listOf("outer", "inner")),
            inner.intercept(TraceIntercept),
        )
        assertEquals(
            listOf(
                TraceConfig(listOf("outer")),
                TraceConfig(listOf("inner")),
            ),
            inner.intercepts(TraceIntercept),
        )

        outer.dispose()

        assertFailsWith<IllegalStateException> {
            inner.intercept(TraceIntercept)
        }
    }

    @Test
    fun siblingInterceptsShouldRemainIndependent() {
        val root =
            Context()

        val first =
            root.intercept(
                TraceIntercept,
                TraceConfig(listOf("first")),
            )

        val second =
            root.intercept(
                TraceIntercept,
                TraceConfig(listOf("second")),
            )

        assertEquals(
            TraceConfig(listOf("first")),
            first.intercept(TraceIntercept),
        )
        assertEquals(
            TraceConfig(listOf("second")),
            second.intercept(TraceIntercept),
        )
    }

    @Test
    fun contextShouldExposeBailWaterfallAndPipelineEventDsl() = runTest {
        val context =
            Context()

        context.on(ResultKey) { null }
        context.on(ResultKey) { value -> "$value-result" }

        context.on(WaterfallKey) { value, next -> value + next() }

        context.on(PipelineKey) { value -> "$value-a" }
        context.once(PipelineKey) { value -> "$value-once" }

        assertEquals(
            "input-result",
            context.bail(ResultKey, "input"),
        )
        assertEquals(
            3,
            context.waterfall(WaterfallKey, 1) { 2 },
        )
        assertEquals(
            "input-a-once",
            context.pipeline(PipelineKey, "input"),
        )
        assertEquals(
            "input-a",
            context.pipeline(PipelineKey, "input"),
        )

        context.dispose()
    }

    @Test
    fun contextShouldOwnSequentialAndSuspendWaterfallListeners() = runTest {
        val root =
            Context()

        val child =
            root.child()

        val calls =
            mutableListOf<String>()

        child.on(SequentialKey) { value ->
            calls += "sequential:$value"
        }

        child.on(SuspendWaterfallKey) { value, next ->
            calls += "waterfall:$value"
            value + next()
        }

        root.sequential(SequentialKey, "first")
        assertEquals(
            3,
            root.waterfall(SuspendWaterfallKey, 1) { 2 },
        )
        assertEquals(
            listOf("sequential:first", "waterfall:1"),
            calls,
        )

        child.dispose()

        root.sequential(SequentialKey, "second")
        assertEquals(
            2,
            root.waterfall(SuspendWaterfallKey, 1) { 2 },
        )
        assertEquals(
            listOf("sequential:first", "waterfall:1"),
            calls,
        )

        root.dispose()
    }
}
