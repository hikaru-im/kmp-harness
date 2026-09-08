package im.hikaru.harness.runtime.event

import im.hikaru.harness.runtime.effect.Disposable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/** Listener registration order options. */
data class EventOptions(
    val prepend: Boolean = false,
)

private typealias EmitListener<T> = (T) -> Unit
private typealias ParallelListener<T> = suspend (T) -> Unit
private typealias SequentialListener<T> = suspend (T) -> Unit
private typealias SerialListener<T, R> = suspend (T) -> R?
private typealias BailListener<T, R> = (T) -> R?
private typealias WaterfallListener<T, R> = (T, next: () -> R) -> R
private typealias SuspendWaterfallListener<T, R> =
    suspend (T, next: suspend () -> R) -> R
private typealias PipelineListener<T> = suspend (T) -> T

/**
 * Type-safe event dispatch with Cordis-compatible mode semantics.
 *
 * Every dispatch snapshots its listeners before invocation. Registration and
 * removal during dispatch therefore affect only later dispatches.
 */
@OptIn(ExperimentalAtomicApi::class)
class EventsService {

    private class Listener<C : Any>(
        val callback: C,
        val once: Boolean,
    ) {
        val claimed = AtomicBoolean(false)
    }

    private val listeners =
        AtomicReference<Map<Any, List<Listener<*>>>>(emptyMap())

    fun <T : Any> on(
        key: EventKey<T>,
        options: EventOptions = EventOptions(),
        listener: EmitListener<T>,
    ): Disposable =
        register(key, options, once = false, listener)

    fun <T : Any> once(
        key: EventKey<T>,
        options: EventOptions = EventOptions(),
        listener: EmitListener<T>,
    ): Disposable =
        register(key, options, once = true, listener)

    fun <T : Any> on(
        key: ParallelEventKey<T>,
        options: EventOptions = EventOptions(),
        listener: ParallelListener<T>,
    ): Disposable =
        register(key, options, once = false, listener)

    fun <T : Any> once(
        key: ParallelEventKey<T>,
        options: EventOptions = EventOptions(),
        listener: ParallelListener<T>,
    ): Disposable =
        register(key, options, once = true, listener)

    fun <T : Any> on(
        key: SequentialEventKey<T>,
        options: EventOptions = EventOptions(),
        listener: SequentialListener<T>,
    ): Disposable =
        register(key, options, once = false, listener)

    fun <T : Any> once(
        key: SequentialEventKey<T>,
        options: EventOptions = EventOptions(),
        listener: SequentialListener<T>,
    ): Disposable =
        register(key, options, once = true, listener)

    fun <T : Any, R : Any> on(
        key: SerialEventKey<T, R>,
        options: EventOptions = EventOptions(),
        listener: SerialListener<T, R>,
    ): Disposable =
        register(key, options, once = false, listener)

    fun <T : Any, R : Any> once(
        key: SerialEventKey<T, R>,
        options: EventOptions = EventOptions(),
        listener: SerialListener<T, R>,
    ): Disposable =
        register(key, options, once = true, listener)

    fun <T : Any, R : Any> on(
        key: BailEventKey<T, R>,
        options: EventOptions = EventOptions(),
        listener: BailListener<T, R>,
    ): Disposable =
        register(key, options, once = false, listener)

    fun <T : Any, R : Any> once(
        key: BailEventKey<T, R>,
        options: EventOptions = EventOptions(),
        listener: BailListener<T, R>,
    ): Disposable =
        register(key, options, once = true, listener)

    fun <T : Any, R : Any> on(
        key: WaterfallEventKey<T, R>,
        options: EventOptions = EventOptions(),
        listener: WaterfallListener<T, R>,
    ): Disposable =
        register(key, options, once = false, listener)

    fun <T : Any, R : Any> once(
        key: WaterfallEventKey<T, R>,
        options: EventOptions = EventOptions(),
        listener: WaterfallListener<T, R>,
    ): Disposable =
        register(key, options, once = true, listener)

    fun <T : Any, R : Any> on(
        key: SuspendWaterfallEventKey<T, R>,
        options: EventOptions = EventOptions(),
        listener: SuspendWaterfallListener<T, R>,
    ): Disposable =
        register(key, options, once = false, listener)

    fun <T : Any, R : Any> once(
        key: SuspendWaterfallEventKey<T, R>,
        options: EventOptions = EventOptions(),
        listener: SuspendWaterfallListener<T, R>,
    ): Disposable =
        register(key, options, once = true, listener)

    fun <T : Any> on(
        key: PipelineEventKey<T>,
        options: EventOptions = EventOptions(),
        listener: PipelineListener<T>,
    ): Disposable =
        register(key, options, once = false, listener)

    fun <T : Any> once(
        key: PipelineEventKey<T>,
        options: EventOptions = EventOptions(),
        listener: PipelineListener<T>,
    ): Disposable =
        register(key, options, once = true, listener)

    /** Synchronously broadcasts in registration order and stops on failure. */
    fun <T : Any> emit(
        key: EventKey<T>,
        event: T,
    ) {
        for (listener in snapshot<EmitListener<T>>(key)) {
            if (claimOnce(key, listener)) {
                listener.callback(event)
            }
        }
    }

    /** Broadcasts to every listener while containing individual observer failures. */
    fun <T : Any> emitContained(
        key: EventKey<T>,
        event: T,
        onFailure: (Throwable) -> Unit = {},
    ) {
        for (listener in snapshot<EmitListener<T>>(key)) {
            if (!claimOnce(key, listener)) {
                continue
            }
            try {
                listener.callback(event)
            } catch (error: Throwable) {
                onFailure(error)
            }
        }
    }

    /** Concurrently invokes every listener and waits for all listeners to settle. */
    suspend fun <T : Any> parallel(
        key: ParallelEventKey<T>,
        event: T,
    ) {
        val snapshot =
            snapshot<ParallelListener<T>>(key)
                .filter { listener -> claimOnce(key, listener) }

        val failures =
            coroutineScope {
                snapshot.map { listener ->
                    async {
                        try {
                            listener.callback(event)
                            null
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Throwable) {
                            error
                        }
                    }
                }.awaitAll()
            }.filterNotNull()

        val primary =
            failures.firstOrNull()
                ?: return

        for (failure in failures.drop(1)) {
            if (failure !== primary) {
                primary.addSuppressed(failure)
            }
        }

        throw primary
    }

    /** Awaits every listener in registration order, stopping on failure or cancellation. */
    suspend fun <T : Any> sequential(
        key: SequentialEventKey<T>,
        event: T,
    ) {
        for (listener in snapshot<SequentialListener<T>>(key)) {
            if (claimOnce(key, listener)) {
                listener.callback(event)
            }
        }
    }

    /** Sequentially awaits listeners and returns the first Cordis bail value. */
    suspend fun <T : Any, R : Any> serial(
        key: SerialEventKey<T, R>,
        event: T,
    ): R? {
        for (listener in snapshot<SerialListener<T, R>>(key)) {
            if (!claimOnce(key, listener)) {
                continue
            }

            val result =
                listener.callback(event)

            if (isBailed(result)) {
                return result
            }
        }

        return null
    }

    /** Synchronous serial dispatch that returns the first Cordis bail value. */
    fun <T : Any, R : Any> bail(
        key: BailEventKey<T, R>,
        event: T,
    ): R? {
        for (listener in snapshot<BailListener<T, R>>(key)) {
            if (!claimOnce(key, listener)) {
                continue
            }

            val result =
                listener.callback(event)

            if (isBailed(result)) {
                return result
            }
        }

        return null
    }

    /**
     * Runs synchronous onion middleware in registration order.
     *
     * A listener is claimed for once semantics only when the chain reaches it;
     * listeners behind a short-circuit remain registered.
     */
    fun <T : Any, R : Any> waterfall(
        key: WaterfallEventKey<T, R>,
        event: T,
        terminal: (T) -> R,
    ): R {
        val snapshot =
            snapshot<WaterfallListener<T, R>>(key)

        var cursor = 0

        fun next(): R {
            while (cursor < snapshot.size) {
                val listener =
                    snapshot[cursor++]

                if (!claimOnce(key, listener)) {
                    continue
                }

                return listener.callback(event, ::next)
            }

            return terminal(event)
        }

        return next()
    }

    /**
     * Runs asynchronous onion middleware in registration order.
     *
     * A listener is claimed for once semantics only when the chain reaches it;
     * listeners behind a short-circuit remain registered.
     */
    suspend fun <T : Any, R : Any> waterfall(
        key: SuspendWaterfallEventKey<T, R>,
        event: T,
        terminal: suspend (T) -> R,
    ): R {
        val snapshot =
            snapshot<SuspendWaterfallListener<T, R>>(key)

        var cursor = 0

        suspend fun next(): R {
            while (cursor < snapshot.size) {
                val listener =
                    snapshot[cursor++]

                if (!claimOnce(key, listener)) {
                    continue
                }

                return listener.callback(event, ::next)
            }

            return terminal(event)
        }

        return next()
    }

    /** Sequentially feeds each asynchronous listener's output into the next. */
    suspend fun <T : Any> pipeline(
        key: PipelineEventKey<T>,
        initial: T,
    ): T {
        var current =
            initial

        for (listener in snapshot<PipelineListener<T>>(key)) {
            if (claimOnce(key, listener)) {
                current = listener.callback(current)
            }
        }

        return current
    }

    private fun isBailed(value: Any?): Boolean =
        value != null && value != false

    private fun <C : Any> register(
        key: Any,
        options: EventOptions,
        once: Boolean,
        callback: C,
    ): Disposable {
        val subscription =
            Listener(
                callback = callback,
                once = once,
            )

        while (true) {
            val current = listeners.load()
            val eventListeners = current[key].orEmpty()
            val updatedListeners =
                if (options.prepend) {
                    listOf(subscription) + eventListeners
                } else {
                    eventListeners + subscription
                }
            val updated = LinkedHashMap(current)
            updated[key] = updatedListeners

            if (listeners.compareAndSet(current, updated)) {
                break
            }
        }

        return Disposable {
            remove(key, subscription)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <C : Any> snapshot(key: Any): List<Listener<C>> =
        listeners.load()[key]
            ?.map { listener -> listener as Listener<C> }
            .orEmpty()

    private fun claimOnce(
        key: Any,
        listener: Listener<*>,
    ): Boolean {
        if (!listener.once) {
            return true
        }

        if (!listener.claimed.compareAndSet(expectedValue = false, newValue = true)) {
            return false
        }

        remove(key, listener)
        return true
    }

    private fun remove(
        key: Any,
        listener: Listener<*>,
    ) {
        while (true) {
            val current = listeners.load()
            val eventListeners = current[key] ?: return
            val index = eventListeners.indexOfFirst { candidate -> candidate === listener }
            if (index < 0) {
                return
            }

            val updatedListeners = eventListeners.toMutableList()
            updatedListeners.removeAt(index)
            val updated = LinkedHashMap(current)
            if (updatedListeners.isEmpty()) {
                updated.remove(key)
            } else {
                updated[key] = updatedListeners
            }

            if (listeners.compareAndSet(current, updated)) {
                return
            }
        }
    }
}
