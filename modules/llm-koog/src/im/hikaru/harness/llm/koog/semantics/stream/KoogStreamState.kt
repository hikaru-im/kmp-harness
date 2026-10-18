package im.hikaru.harness.llm.koog

import im.hikaru.harness.llm.BlockEndChunk
import im.hikaru.harness.llm.CallId
import im.hikaru.harness.llm.LlmErrorCode
import im.hikaru.harness.llm.LlmException
import im.hikaru.harness.llm.ReasoningBlock
import im.hikaru.harness.llm.StreamChunk
import im.hikaru.harness.llm.TextBlock
import im.hikaru.harness.llm.ToolCallBlock

/** Stream lifecycle state and stable Harness output ordering. */
class KoogStreamState {
    private val explicitBlocksByProviderIndex =
        linkedMapOf<Int, KoogStreamBlockState>()
    private val independentToolBlocks = linkedMapOf<Int, KoogStreamBlockState>()
    private val currentImplicitBlockByType =
        mutableMapOf<KoogStreamBlockType, KoogStreamBlockState>()
    private val blocksInOutputOrder =
        mutableListOf<KoogStreamBlockState>()
    private var nextOutputIndex = 0

    var finished: Boolean = false
        private set

    fun open(
        providerIndex: Int?,
        type: KoogStreamBlockType,
    ): KoogStreamBlockState {
        ensureActive()
        if (providerIndex == null) return openImplicit(type)
        if (providerIndex < 0) {
            invalidState("Provider block index must not be negative: $providerIndex")
        }
        explicitBlocksByProviderIndex[providerIndex]?.let { current ->
            if (current.type != type) {
                invalidState(
                    "Provider block $providerIndex changed type from " +
                        "${current.type.harnessType} to ${type.harnessType}"
                )
            }
            return current
        }

        val implicit = currentImplicitBlockByType[type]
        if (implicit != null && !implicit.completed) {
            invalidState(
                "Provider ${type.harnessType} block changed from a missing index to " +
                    "explicit index $providerIndex before completion"
            )
        }
        return createBlock(providerIndex, type).also { block ->
            explicitBlocksByProviderIndex[providerIndex] = block
        }
    }

    /**
     * Koog 1.3.0 can reuse a Chat choice index for reasoning_content followed by text.
     * The ordinary [open] path remains strict; this narrow transition first closes the
     * reasoning block and then allocates the next Harness output block for that index.
     */
    internal fun openAfterReasoning(
        providerIndex: Int?,
        type: KoogStreamBlockType,
    ): Pair<List<StreamChunk>, KoogStreamBlockState> {
        if (providerIndex == null) {
            return emptyList<StreamChunk>() to open(providerIndex, type)
        }

        val current = explicitBlocksByProviderIndex[providerIndex]
        if (current == null || current.type != KoogStreamBlockType.REASONING) {
            return emptyList<StreamChunk>() to open(providerIndex, type)
        }

        if (current.completed) {
            val next = createBlock(providerIndex, type)
            explicitBlocksByProviderIndex[providerIndex] = next
            return emptyList<StreamChunk>() to next
        }
        if (!current.started) invalidState("Provider reasoning block $providerIndex changed before starting")
        current.complete()
        val end =
            BlockEndChunk(
                index = current.outputIndex,
                block = ReasoningBlock(current.text),
            )
        val next = createBlock(providerIndex, type)
        explicitBlocksByProviderIndex[providerIndex] = next
        return listOf(end) to next
    }

    /** Chat choice indices and tool-call indices are separate protocol namespaces. */
    internal fun openIndependentToolCall(providerIndex: Int?): Pair<List<StreamChunk>, KoogStreamBlockState> {
        ensureActive()
        if (providerIndex != null && providerIndex < 0) invalidState("Provider tool index must not be negative")
        if (providerIndex == null && independentToolBlocks.values.any { !it.completed }) {
            invalidState("Provider tool frame omitted its index while indexed tool calls remain open")
        }
        if (providerIndex != null && currentImplicitBlockByType[KoogStreamBlockType.TOOL_CALL]?.completed == false) {
            invalidState("Provider tool block changed from a missing index to an explicit index before completion")
        }
        val completedReasoning = blocksInOutputOrder.filter {
            it.type == KoogStreamBlockType.REASONING && !it.completed
        }.map { block ->
            block.complete()
            BlockEndChunk(block.outputIndex, ReasoningBlock(block.text))
        }
        val block = if (providerIndex == null) open(null, KoogStreamBlockType.TOOL_CALL)
        else independentToolBlocks.getOrPut(providerIndex) { createBlock(providerIndex, KoogStreamBlockType.TOOL_CALL) }
        return completedReasoning to block
    }

    private fun openImplicit(type: KoogStreamBlockType): KoogStreamBlockState {
        val current = currentImplicitBlockByType[type]
        if (current != null && !current.completed) return current

        val openExplicit =
            explicitBlocksByProviderIndex.values.filter { block ->
                block.type == type && !block.completed
            }
        if (openExplicit.isNotEmpty()) {
            invalidState(
                "Provider ${type.harnessType} frame omitted its index while explicit block(s) " +
                    openExplicit.joinToString { block -> block.sourceLabel } +
                    " remain open"
            )
        }
        return createBlock(providerIndex = null, type = type).also { block ->
            currentImplicitBlockByType[type] = block
        }
    }

    private fun createBlock(
        providerIndex: Int?,
        type: KoogStreamBlockType,
    ): KoogStreamBlockState =
        KoogStreamBlockState(
            providerIndex = providerIndex,
            outputIndex = nextOutputIndex++,
            type = type,
        ).also(blocksInOutputOrder::add)

    fun blocks(): List<KoogStreamBlockState> = blocksInOutputOrder.toList()

    fun replayBlocks(): List<KoogStreamBlockSnapshot> =
        blocksInOutputOrder.map(KoogStreamBlockState::replaySnapshot)

    fun completeOpenToolCalls(): List<StreamChunk> {
        ensureActive()
        return blocksInOutputOrder
            .filter { block -> !block.completed && block.type == KoogStreamBlockType.TOOL_CALL }
            .map { block ->
                val id = CallId(block.requireToolCallId())
                val name = block.requireToolCallName()
                val arguments = block.text
                block.complete()
                BlockEndChunk(
                    index = block.outputIndex,
                    block = ToolCallBlock(id = id, name = name, arguments = arguments),
                )
            }
    }

    fun completeOpenTextBlocks(): List<StreamChunk> {
        ensureActive()
        return blocksInOutputOrder
            .filter { block -> !block.completed && block.type == KoogStreamBlockType.TEXT }
            .map { block ->
                val text = block.text
                block.complete()
                BlockEndChunk(
                    index = block.outputIndex,
                    block = TextBlock(text),
                )
            }
    }

    fun finish() {
        ensureActive()
        if (blocksInOutputOrder.isEmpty()) {
            throw LlmException(
                message = "Koog provider completed without content blocks",
                code = LlmErrorCode.EMPTY_RESPONSE,
            )
        }
        val openBlocks = blocksInOutputOrder.filterNot { block -> block.completed }
        if (openBlocks.isNotEmpty()) {
            invalidState(
                "Stream finished with open provider block(s): " +
                    openBlocks.joinToString { block -> block.sourceLabel }
            )
        }
        finished = true
    }

    fun requireFinished() {
        if (!finished) {
            invalidState("Provider stream completed without an End frame")
        }
    }

    private fun ensureActive() {
        if (finished) {
            invalidState("Stream has already finished")
        }
    }
}
