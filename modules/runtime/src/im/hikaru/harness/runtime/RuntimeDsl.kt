package im.hikaru.harness.runtime

import im.hikaru.harness.runtime.event.BailEventKey
import im.hikaru.harness.runtime.event.EventKey
import im.hikaru.harness.runtime.event.ParallelEventKey
import im.hikaru.harness.runtime.event.PipelineEventKey
import im.hikaru.harness.runtime.event.SequentialEventKey
import im.hikaru.harness.runtime.event.SerialEventKey
import im.hikaru.harness.runtime.event.SuspendWaterfallEventKey
import im.hikaru.harness.runtime.event.WaterfallEventKey
import im.hikaru.harness.runtime.intercept.InterceptKey
import im.hikaru.harness.runtime.service.ServiceKey

/** 常用类型键的轻量 Kotlin DSL 工厂。 */
fun <T : Any> serviceKey(
    name: String,
): ServiceKey<T> =
    ServiceKey(name)

fun <T : Any> eventKey(
    name: String,
): EventKey<T> =
    EventKey(name)

fun <T : Any> parallelEventKey(
    name: String,
): ParallelEventKey<T> =
    ParallelEventKey(name)

fun <T : Any> sequentialEventKey(
    name: String,
): SequentialEventKey<T> =
    SequentialEventKey(name)

fun <T : Any, R : Any> serialEventKey(
    name: String,
): SerialEventKey<T, R> =
    SerialEventKey(name)

fun <T : Any, R : Any> bailEventKey(
    name: String,
): BailEventKey<T, R> =
    BailEventKey(name)

fun <T : Any, R : Any> waterfallEventKey(
    name: String,
): WaterfallEventKey<T, R> =
    WaterfallEventKey(name)

fun <T : Any, R : Any> suspendWaterfallEventKey(
    name: String,
): SuspendWaterfallEventKey<T, R> =
    SuspendWaterfallEventKey(name)

fun <T : Any> pipelineEventKey(
    name: String,
): PipelineEventKey<T> =
    PipelineEventKey(name)

fun <T : Any> interceptKey(
    name: String,
    merge: (parent: T, child: T) -> T = { _, child -> child },
): InterceptKey<T> =
    InterceptKey(
        name = name,
        merge = merge,
    )
