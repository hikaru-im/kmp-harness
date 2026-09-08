package im.hikaru.harness.llm.koog

import ai.koog.prompt.streaming.StreamFrame
import im.hikaru.harness.llm.BlockStartChunk
import im.hikaru.harness.llm.GenerateOptions
import im.hikaru.harness.llm.TextBlock
import im.hikaru.harness.llm.createUserMessage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KoogCancellationIntegrationContractTest {

    @Test
    fun downstreamEarlyStopShouldCancelProviderWithoutClassifyingFailure() = runTest {
        var providerStopped = false
        var classifierCalls = 0
        val frames =
            flow {
                try {
                    emit(StreamFrame.TextDelta("hello", index = 0))
                    awaitCancellation()
                } finally {
                    providerStopped = true
                }
            }
        val adapter =
            adapter(
                frames = frames,
                onClassify = { classifierCalls++ },
            )

        assertEquals(
            listOf(BlockStartChunk(index = 0, blockType = "text")),
            adapter.stream(options()).take(1).toList(),
        )
        assertTrue(providerStopped)
        assertEquals(0, classifierCalls)
    }

    @Test
    fun callerCancellationShouldStopProviderWithoutProducingAnErrorFinish() = runTest {
        val providerStarted = CompletableDeferred<Unit>()
        var providerStopped = false
        var classifierCalls = 0
        val frames =
            flow<StreamFrame> {
                try {
                    providerStarted.complete(Unit)
                    awaitCancellation()
                } finally {
                    providerStopped = true
                }
            }
        val adapter =
            adapter(
                frames = frames,
                onClassify = { classifierCalls++ },
            )
        val collection =
            launch {
                adapter.stream(options()).collect()
            }

        providerStarted.await()
        collection.cancelAndJoin()

        assertTrue(providerStopped)
        assertEquals(0, classifierCalls)
    }

    private fun adapter(
        frames: Flow<StreamFrame>,
        onClassify: () -> Unit,
    ): KoogLlmAdapter =
        KoogLlmAdapter(
            executor = RecordingPromptExecutor(frames = frames),
            routes = listOf(testKoogRoute()),
            failureClassifier =
                KoogProviderFailureClassifier { _, _ ->
                    onClassify()
                    null
                },
        )

    private fun options(): GenerateOptions =
        GenerateOptions(
            provider = "test",
            model = "test-model",
            messages = listOf(createUserMessage(listOf(TextBlock("hello")))),
        )
}
