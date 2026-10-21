package im.hikaru.harness.llm.koog

import im.hikaru.harness.runtime.effect.Disposable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class KoogLlmGenerationTest {
    @Test
    fun cancelledLeaseReleaseMustWaitForTheLockAndDisposeExactlyOnce() = runTest {
        val mutex = Mutex()
        var closed = 0
        val route = testKoogRoute()
        val generation = KoogLlmGeneration(KoogLlmAdapter(RecordingPromptExecutor(), listOf(route)),
            Disposable { closed++ }, listOf(route), mutex)
        val lease = generation.acquire()
        generation.retire()
        mutex.lock()
        val release = launch { lease.release() }
        runCurrent()
        release.cancel()
        assertEquals(0, closed)
        mutex.unlock()
        release.join()
        assertEquals(1, closed)
        lease.release()
        generation.retire()
        assertEquals(1, closed)
    }
}
