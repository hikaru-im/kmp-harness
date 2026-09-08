package im.hikaru.harness.llm.koog

import im.hikaru.harness.llm.LlmErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KoogStreamStateTest {

    @Test
    fun stateShouldAssignOutputOrderAndTrackBlockLifecycle() {
        val state = KoogStreamState()
        val first = state.open(7, KoogStreamBlockType.TEXT)
        val same = state.open(7, KoogStreamBlockType.TEXT)

        assertTrue(first === same)
        assertEquals(0, first.outputIndex)
        assertTrue(first.startIfNeeded())
        assertFalse(first.startIfNeeded())
        first.append("hello")
        first.complete()

        val second = state.open(3, KoogStreamBlockType.REASONING)
        assertEquals(1, second.outputIndex)
        second.startIfNeeded()
        second.append("thinking")
        second.complete()
        assertEquals(
            listOf(7, 3),
            state.blocks().map { block -> block.providerIndex },
        )

        state.finish()
        assertTrue(state.finished)
    }

    @Test
    fun implicitBlocksShouldReuseOnlyTheCurrentOpenTypeAndAllowSequentialBlocks() {
        val state = KoogStreamState()
        val firstText = state.open(null, KoogStreamBlockType.TEXT)
        val sameText = state.open(null, KoogStreamBlockType.TEXT)

        assertTrue(firstText === sameText)
        assertEquals(null, firstText.providerIndex)
        assertEquals(0, firstText.outputIndex)
        firstText.startIfNeeded()
        firstText.complete()

        val reasoning = state.open(null, KoogStreamBlockType.REASONING)
        assertEquals(1, reasoning.outputIndex)
        reasoning.startIfNeeded()
        reasoning.complete()

        val secondText = state.open(null, KoogStreamBlockType.TEXT)
        assertTrue(firstText !== secondText)
        assertEquals(2, secondText.outputIndex)
        secondText.startIfNeeded()
        secondText.complete()

        assertEquals(
            listOf(null, null, null),
            state.blocks().map { block -> block.providerIndex },
        )
        state.finish()
    }

    @Test
    fun openBlockShouldRejectSwitchingBetweenExplicitAndMissingIndexes() {
        val explicitState = KoogStreamState()
        explicitState.open(4, KoogStreamBlockType.TEXT).startIfNeeded()
        assertInvalidState { explicitState.open(null, KoogStreamBlockType.TEXT) }

        val implicitState = KoogStreamState()
        implicitState.open(null, KoogStreamBlockType.REASONING).startIfNeeded()
        assertInvalidState { implicitState.open(4, KoogStreamBlockType.REASONING) }
    }

    @Test
    fun invalidStateShouldUseStableErrorCode() {
        val state = KoogStreamState()
        val block = state.open(0, KoogStreamBlockType.TEXT)

        assertFailsWith<im.hikaru.harness.llm.LlmException> {
            block.append("before start")
        }.also { error ->
            assertEquals(KoogLlmErrorCode.INVALID_STREAM_STATE, error.code)
        }

        block.startIfNeeded()
        block.complete()

        assertFailsWith<im.hikaru.harness.llm.LlmException> {
            state.open(0, KoogStreamBlockType.REASONING)
        }.also { error ->
            assertEquals(KoogLlmErrorCode.INVALID_STREAM_STATE, error.code)
        }

        state.finish()
        assertFailsWith<im.hikaru.harness.llm.LlmException> {
            state.finish()
        }.also { error ->
            assertEquals(KoogLlmErrorCode.INVALID_STREAM_STATE, error.code)
        }
    }

    @Test
    fun invalidIndexesAndOpenBlocksShouldPreventSuccessfulFinish() {
        val state = KoogStreamState()

        assertFailsWith<im.hikaru.harness.llm.LlmException> {
            state.open(-1, KoogStreamBlockType.TEXT)
        }.also { error ->
            assertEquals(KoogLlmErrorCode.INVALID_STREAM_STATE, error.code)
        }

        val block = state.open(2, KoogStreamBlockType.TEXT)
        block.startIfNeeded()
        block.append("unfinished")

        assertFailsWith<im.hikaru.harness.llm.LlmException> {
            state.finish()
        }.also { error ->
            assertEquals(KoogLlmErrorCode.INVALID_STREAM_STATE, error.code)
        }

        block.complete()
        state.finish()
        assertTrue(state.finished)
    }

    @Test
    fun collectionShouldRequireAnExplicitEndFrame() {
        val state = KoogStreamState()

        assertFailsWith<im.hikaru.harness.llm.LlmException> {
            state.requireFinished()
        }.also { error ->
            assertEquals(KoogLlmErrorCode.INVALID_STREAM_STATE, error.code)
        }

        val block = state.open(0, KoogStreamBlockType.TEXT)
        block.startIfNeeded()
        block.complete()
        state.finish()
        state.requireFinished()
    }

    @Test
    fun emptySuccessfulStreamShouldUseRetryableCoreErrorCode() {
        val error =
            assertFailsWith<im.hikaru.harness.llm.LlmException> {
                KoogStreamState().finish()
            }

        assertEquals(LlmErrorCode.EMPTY_RESPONSE, error.code)
    }

    @Test
    fun toolIdentityShouldRemainPerBlockAndEmitNameOnlyOnce() {
        val state = KoogStreamState()
        val block = state.open(4, KoogStreamBlockType.TOOL_CALL)

        block.observeToolCallIdentity(id = "call-1", name = null)
        block.observeToolCallIdentity(id = null, name = "lookup")
        assertEquals("call-1", block.requireToolCallId())
        assertEquals("lookup", block.requireToolCallName())
        assertEquals("lookup", block.takeToolCallNameForDelta())
        assertNull(block.takeToolCallNameForDelta())

        block.observeToolCallIdentity(id = "call-1", name = "lookup")
        assertNull(block.takeToolCallNameForDelta())
    }

    @Test
    fun toolIdentityShouldRejectMissingBlankConflictingOrWrongBlockState() {
        val block =
            KoogStreamState()
                .open(0, KoogStreamBlockType.TOOL_CALL)

        assertInvalidState { block.requireToolCallId() }
        assertInvalidState { block.requireToolCallName() }
        assertInvalidState { block.observeToolCallIdentity(id = "", name = null) }
        assertInvalidState { block.observeToolCallIdentity(id = null, name = " ") }

        val atomicBlock =
            KoogStreamState()
                .open(3, KoogStreamBlockType.TOOL_CALL)
        assertInvalidState {
            atomicBlock.observeToolCallIdentity(id = "call-atomic", name = " ")
        }
        assertInvalidState { atomicBlock.requireToolCallId() }

        block.observeToolCallIdentity(id = "call-1", name = "lookup")
        assertInvalidState { block.observeToolCallIdentity(id = "call-2", name = null) }
        assertInvalidState { block.observeToolCallIdentity(id = null, name = "other") }

        val textBlock =
            KoogStreamState()
                .open(1, KoogStreamBlockType.TEXT)
        assertInvalidState {
            textBlock.observeToolCallIdentity(id = "call-1", name = "lookup")
        }
    }

    @Test
    fun toolIdentityShouldNotLeakAcrossIndependentStreamStates() {
        val first =
            KoogStreamState()
                .open(2, KoogStreamBlockType.TOOL_CALL)
        val second =
            KoogStreamState()
                .open(2, KoogStreamBlockType.TOOL_CALL)
        first.observeToolCallIdentity(id = "call-1", name = "lookup")

        assertEquals("call-1", first.requireToolCallId())
        assertInvalidState { second.requireToolCallId() }
    }

    @Test
    fun toolBlockShouldRequireCompleteIdentityBeforeClosing() {
        val block =
            KoogStreamState()
                .open(0, KoogStreamBlockType.TOOL_CALL)
        block.startIfNeeded()

        assertInvalidState { block.complete() }
        block.observeToolCallIdentity(id = "call-1", name = null)
        assertInvalidState { block.complete() }
        block.observeToolCallIdentity(id = null, name = "lookup")
        block.complete()
        assertTrue(block.completed)
    }

    @Test
    fun reasoningCompletionShouldReplaceDeltaSummaryWithoutDuplicatingIt() {
        val block =
            KoogStreamState()
                .open(0, KoogStreamBlockType.REASONING)

        block.observeReasoning(id = null, summary = listOf("delta-summary"))
        block.observeReasoning(
            id = null,
            summary = listOf("complete-summary"),
            replaceSummary = true,
        )

        assertEquals(
            listOf("complete-summary"),
            block.replaySnapshot().reasoningSummary,
        )
    }

    private fun assertInvalidState(block: () -> Unit) {
        val error =
            assertFailsWith<im.hikaru.harness.llm.LlmException>(block = block)
        assertEquals(KoogLlmErrorCode.INVALID_STREAM_STATE, error.code)
    }
}
