package im.hikaru.harness.llm.koog

import im.hikaru.harness.llm.LlmAdapter
import im.hikaru.harness.runtime.effect.Disposable
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Keeps a retired executor alive until every prepared or in-flight call releases its lease. */
internal class KoogLlmGeneration(
    val adapter: LlmAdapter,
    private val resource: Disposable,
    val routes: List<KoogProviderRoute>,
    private val mutex: Mutex = Mutex(),
) {
    private var leases = 0
    private var retired = false
    private var closed = false

    suspend fun acquire(): KoogGenerationLease =
        mutex.withLock {
            check(!closed) { "Koog LLM generation is closed" }
            leases += 1
            KoogGenerationLease(this)
        }

    suspend fun retire() = withContext(NonCancellable) {
        closeIfIdle(
            mutex.withLock {
                retired = true
                if (leases == 0) closeLocked() else null
            }
        )
    }

    private suspend fun release() {
        closeIfIdle(
            mutex.withLock {
                check(leases > 0) { "Koog generation lease underflow" }
                leases -= 1
                if (retired && leases == 0) closeLocked() else null
            }
        )
    }

    private fun closeLocked(): Disposable? {
        if (closed) return null
        closed = true
        return resource
    }

    private suspend fun closeIfIdle(executorToClose: Disposable?) {
        executorToClose?.dispose()
    }

    class KoogGenerationLease internal constructor(
        private val generation: KoogLlmGeneration,
    ) {
        private var released = false
        private val releaseMutex = Mutex()

        val adapter: LlmAdapter
            get() = generation.adapter

        suspend fun release() = withContext(NonCancellable) {
            releaseMutex.withLock {
                if (!released) {
                    released = true
                    generation.release()
                }
            }
        }
    }
}
