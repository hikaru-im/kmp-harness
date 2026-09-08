package im.hikaru.harness.runtime

import im.hikaru.harness.runtime.event.EventKey
import im.hikaru.harness.runtime.event.EventOptions
import im.hikaru.harness.runtime.event.EventsService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContextContainedEventTest {

    private data class Notice(
        val value: String,
    )

    private object NoticeKey : EventKey<Notice>("context.contained")

    @Test
    fun containedEmissionShouldPreserveContextListenerRules() = runTest {
        val runtime = Runtime()
        val owner = runtime.context.child()
        val calls = mutableListOf<String>()
        val failures = mutableListOf<String>()

        owner.on(NoticeKey) {
            calls += "failure:${it.value}"
            error("observer failure")
        }
        owner.once(
            key = NoticeKey,
            options = EventOptions(prepend = true),
        ) {
            calls += "once:${it.value}"
            owner.on(NoticeKey) { late ->
                calls += "late:${late.value}"
            }
        }
        owner.on(NoticeKey) {
            calls += "tail:${it.value}"
        }

        runtime.context.emitContained(
            key = NoticeKey,
            event = Notice("first"),
            onFailure = { error -> failures += error.message.orEmpty() },
        )
        runtime.context.emitContained(
            key = NoticeKey,
            event = Notice("second"),
            onFailure = { error -> failures += error.message.orEmpty() },
        )

        assertEquals(
            listOf(
                "once:first",
                "failure:first",
                "tail:first",
                "failure:second",
                "tail:second",
                "late:second",
            ),
            calls,
        )
        assertEquals(
            listOf("observer failure", "observer failure"),
            failures,
        )

        owner.dispose()
        runtime.context.emitContained(NoticeKey, Notice("disposed"))

        assertEquals(6, calls.size)
        runtime.context.dispose()
    }

    @Test
    fun concurrentContainedEmissionShouldClaimOnceListenerExactlyOnce() = runTest {
        val events = EventsService()
        val start = CompletableDeferred<Unit>()
        val calls = Channel<Unit>(Channel.UNLIMITED)
        events.once(NoticeKey) {
            calls.trySend(Unit)
        }

        withContext(Dispatchers.Default) {
            (0 until 100).map {
                async {
                    start.await()
                    events.emitContained(NoticeKey, Notice("concurrent"))
                }
            }.also {
                start.complete(Unit)
            }.awaitAll()
        }

        assertTrue(calls.tryReceive().isSuccess)
        assertTrue(calls.tryReceive().isFailure)
        calls.close()
    }
}
