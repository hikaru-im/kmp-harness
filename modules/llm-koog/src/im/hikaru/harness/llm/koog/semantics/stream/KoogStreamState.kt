package im.hikaru.harness.llm.koog

import im.hikaru.harness.llm.BlockEndChunk
import im.hikaru.harness.llm.CallId
import im.hikaru.harness.llm.LlmErrorCode
import im.hikaru.harness.llm.LlmException
import im.hikaru.harness.llm.StreamChunk
import im.hikaru.harness.llm.TextBlock
import im.hikaru.harness.llm.ToolCallBlock

/** Stream lifecycle state and stable Harness output ordering. */
class KoogStreamState {
    private val explicitBlocksByProviderIndex =
        linkedMapOf<Int, KoogStreamBlockState>()
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
