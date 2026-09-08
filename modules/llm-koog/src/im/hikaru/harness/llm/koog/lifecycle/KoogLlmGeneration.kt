package im.hikaru.harness.llm.koog

import ai.koog.prompt.executor.model.PromptExecutor
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Creates a Provider client generation from one immutable settings snapshot. */
fun interface KoogGenerationFactory {
    suspend fun create(
        settings: KoogLlmSettings,
        routes: List<KoogProviderRoute>,
    ): PromptExecutor
}

/** Keeps a retired executor alive until every prepared or in-flight call releases its lease. */
internal class KoogLlmGeneration(
    val adapter: KoogLlmAdapter,
    private val executor: PromptExecutor,
    val routes: List<KoogProviderRoute>,
) {
    private val mutex = Mutex()
    private var leases = 0
    private var retired = false
    private var closed = false

    suspend fun acquire(): KoogGenerationLease =
        mutex.withLock {
            check(!closed) { "Koog LLM generation is closed" }
            leases += 1
            KoogGenerationLease(this)
        }

    suspend fun retire() {
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

    private fun closeLocked(): PromptExecutor? {
        if (closed) return null
        closed = true
        return executor
    }

    private fun closeIfIdle(executorToClose: PromptExecutor?) {
        executorToClose?.close()
    }

    class KoogGenerationLease internal constructor(
        private val generation: KoogLlmGeneration,
    ) {
        private var released = false

        val adapter: KoogLlmAdapter
            get() = generation.adapter

        suspend fun release() {
            if (!released) {
                released = true
                generation.release()
            }
        }
    }
}
