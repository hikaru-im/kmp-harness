@file:Suppress(
    "INVISIBLE_MEMBER",
    "INVISIBLE_REFERENCE",
)

package im.hikaru.harness.runtime

import im.hikaru.harness.runtime.effect.EffectScope
import im.hikaru.harness.runtime.plugin.SimplePlugin
import im.hikaru.harness.runtime.service.ServiceKey
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 证明 Runtime 状态锁不再跨可挂起回调持有。
 *
 * 旧实现在锁内执行 cleanup / apply，只要它们等待另一个协程提交状态，
 * 就会和锁形成等待环而卡死。以下两个用例在旧实现下会超时。
 */
class RuntimeLaneBoundaryTest {

    private interface LaneService

    private class LaneServiceImpl : LaneService

    private val laneKey =
        ServiceKey<LaneService>("lane-boundary")

    @Test
    fun effectCleanupCanAwaitMutationFromAnotherCoroutine() = runTest {
        val runtime =
            Runtime()

        val target =
            runtime.context.child()

        val other =
            runtime.context.child()

        val cleanupEntered =
            CompletableDeferred<Unit>()

        val gate =
            CompletableDeferred<Unit>()

        target.effect {
            cleanupEntered.complete(Unit)
            gate.await()
        }

        val mutator =
            launch {
                cleanupEntered.await()

                other.provide(
                    laneKey,
                    LaneServiceImpl(),
                )

                gate.complete(Unit)
            }

        withTimeout(5_000) {
            target.dispose()
        }

        mutator.join()

        assertTrue(target.isDisposed)
        assertTrue(other.has(laneKey))
    }

    @Test
    fun applyCanAwaitMutationFromAnotherCoroutine() = runTest {
        val runtime =
            Runtime()

        val entered =
            CompletableDeferred<Unit>()

        val gate =
            CompletableDeferred<Unit>()

        val plugin =
            object : SimplePlugin {
                override suspend fun apply(
                    context: Context,
                    scope: EffectScope,
                ) {
                    entered.complete(Unit)
                    gate.await()
                }
            }

        val install =
            async {
                runtime.install(plugin)
            }

        entered.await()

        withTimeout(5_000) {
            runtime.provide(
                laneKey,
                LaneServiceImpl(),
            )
        }

        gate.complete(Unit)

        install.await()

        assertTrue(runtime.context.has(laneKey))
        assertEquals(1, runtime.fibers.size)
    }
}
