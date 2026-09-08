package im.hikaru.harness.runtime.event

/** Cordis-compatible event dispatch modes. */
enum class DispatchMode {
    Emit,
    Parallel,
    Sequential,
    Serial,
    Bail,
    Waterfall,
    SuspendWaterfall,
}

/** A synchronous observer event dispatched by emit. */
open class EventKey<T : Any>(
    val name: String,
) {
    override fun toString(): String =
        "EventKey($name)"
}

/** An asynchronous observer event dispatched concurrently by parallel. */
open class ParallelEventKey<T : Any>(
    val name: String,
) {
    override fun toString(): String =
        "ParallelEventKey($name)"
}

/** An asynchronous observer event that awaits every listener in registration order. */
open class SequentialEventKey<T : Any>(
    val name: String,
) {
    override fun toString(): String =
        "SequentialEventKey($name)"
}

/** An asynchronous result event dispatched sequentially by serial. */
open class SerialEventKey<T : Any, R : Any>(
    val name: String,
) {
    override fun toString(): String =
        "SerialEventKey($name)"
}

/** A synchronous result event dispatched sequentially by bail. */
open class BailEventKey<T : Any, R : Any>(
    val name: String,
) {
    override fun toString(): String =
        "BailEventKey($name)"
}

/**
 * A synchronous onion-middleware event.
 *
 * Each listener decides whether to continue by invoking next. Returning without
 * invoking next short-circuits the remaining listener chain and terminal.
 */
open class WaterfallEventKey<T : Any, R : Any>(
    val name: String,
) {
    override fun toString(): String =
        "WaterfallEventKey($name)"
}

/**
 * An asynchronous onion-middleware event.
 *
 * Listener and terminal suspension, failure, and cancellation remain part of
 * the caller's coroutine. Returning without invoking next short-circuits the
 * remaining chain.
 */
open class SuspendWaterfallEventKey<T : Any, R : Any>(
    val name: String,
) {
    override fun toString(): String =
        "SuspendWaterfallEventKey($name)"
}

/** An asynchronous value transformation pipeline retained from the KMP API. */
open class PipelineEventKey<T : Any>(
    val name: String,
) {
    override fun toString(): String =
        "PipelineEventKey($name)"
}
