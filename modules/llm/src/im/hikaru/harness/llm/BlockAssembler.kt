package im.hikaru.harness.llm

/**
 * 把适配器原始 StreamChunk 增量组装成内容块和 assistant 消息。
 *
 * AgentLoop 会同时记录原始 chunk，并使用这一份实现构造最终消息。
 */
class BlockAssembler {

    private data class PartialBlock(
        val blockType: String,
        var text: String = "",
        var toolCallId: CallId? = null,
        var toolCallName: String? = null,
        var toolCallArguments: String = "",
        var completed: ContentBlock? = null,
    )

    private val partials =
        mutableMapOf<Int, PartialBlock>()

    private val order =
        mutableListOf<Int>()

    var usage: TokenUsage? = null
        private set

    var finish: FinishReason = StopFinishReason
        private set

    var replayState: kotlinx.serialization.json.JsonElement? =
        null
        private set

    fun push(chunk: StreamChunk) {
        when (chunk) {
            is BlockStartChunk -> {
                if (chunk.index !in partials) {
                    order += chunk.index
                    partials[chunk.index] =
                        PartialBlock(blockType = chunk.blockType)
                }
            }

            is TextDeltaChunk -> {
                val partial = ensure(chunk.index, "text")
                if (partial.completed == null) {
                    partial.text += chunk.text
                }
            }

            is ReasoningDeltaChunk -> {
                val partial = ensure(chunk.index, "reasoning")
                if (partial.completed == null) {
                    partial.text += chunk.text
                }
            }

            is ToolCallDeltaChunk -> {
                val partial = ensure(chunk.index, "tool-call")
                if (partial.completed == null) {
                    partial.toolCallId = chunk.id
                    if (chunk.name != null) {
                        partial.toolCallName = chunk.name
                    }
                    partial.toolCallArguments += chunk.argumentsDelta
                }
            }

            is BlockEndChunk -> {
                val partial = ensure(chunk.index, chunk.block.type)
                if (partial.completed == null) {
                    partial.completed = chunk.block
                }
            }

            is UsageChunk ->
                usage = chunk.usage

            is FinishChunk -> {
                finish = chunk.reason
                replayState = chunk.replayState
            }
        }
    }

    fun blocks(): List<ContentBlock> {
        val result =
            order.map { index ->
                assemble(
                    partial = checkNotNull(partials[index]) {
                        "BlockAssembler invariant violated: no partial for index $index"
                    },
                    index = index,
                )
            }

        return if (finish is MaxTokensFinishReason) {
            result.filterNot { block -> block is ToolCallBlock }
        } else {
            result
        }
    }

    fun message(
        provider: String,
        model: String,
    ): Message =
        createAssistantMessage(
            content = blocks(),
            provider = provider,
            model = model,
            replayState = replayState,
        )

    private fun ensure(
        index: Int,
        blockType: String,
    ): PartialBlock =
        partials.getOrPut(index) {
            order += index
            PartialBlock(blockType = blockType)
        }

    private fun assemble(
        partial: PartialBlock,
        index: Int,
    ): ContentBlock {
        partial.completed?.let { block ->
            return block
        }

        return when (partial.blockType) {
            "text" -> TextBlock(partial.text)
            "reasoning" -> ReasoningBlock(partial.text)
            "tool-call" ->
                ToolCallBlock(
                    id = partial.toolCallId ?: CallId("call-$index"),
                    name = partial.toolCallName.orEmpty(),
                    arguments = partial.toolCallArguments,
                )

            else ->
                error(
                    "Cannot assemble incomplete block of type '${partial.blockType}'"
                )
        }
    }
}
