package im.hikaru.harness.llm.koog

import ai.koog.prompt.streaming.StreamFrame
import im.hikaru.harness.llm.AbortedFinishReason
import im.hikaru.harness.llm.ErrorFinishReason
import im.hikaru.harness.llm.FinishChunk
import im.hikaru.harness.llm.FinishReason
import im.hikaru.harness.llm.MaxTokensFinishReason
import im.hikaru.harness.llm.StreamChunk
import im.hikaru.harness.llm.StopFinishReason
import im.hikaru.harness.llm.ToolCallsFinishReason
import im.hikaru.harness.llm.UsageChunk

/**
 * Provider 声明其 delta-only tool-call 流是否应在当前 End 前补齐 block-end。
 *
 * 公共 terminal 层仍拥有实际状态修改；Provider policy 只能做语义决策，不能制造或改写 block。
 */
fun interface KoogToolCallTerminalPolicy {
    fun shouldCompleteOpenToolCalls(
        frame: StreamFrame.End,
        context: KoogStreamContext,
    ): Boolean
}

/** Provider 声明 End 是否权威结束仅有 delta、没有 TextComplete 的文本块。 */
fun interface KoogTextTerminalPolicy {
    fun shouldCompleteOpenTextBlocks(
        frame: StreamFrame.End,
        context: KoogStreamContext,
    ): Boolean
}

/** provider-neutral terminal 不猜测缺失的文本 complete frame。 */
object StrictKoogTextTerminalPolicy : KoogTextTerminalPolicy {
    override fun shouldCompleteOpenTextBlocks(
        @Suppress("UNUSED_PARAMETER")
        frame: StreamFrame.End,
        @Suppress("UNUSED_PARAMETER")
        context: KoogStreamContext,
    ): Boolean = false
}

/** provider-neutral terminal 不猜测任何缺失的 complete frame。 */
object StrictKoogToolCallTerminalPolicy : KoogToolCallTerminalPolicy {
    override fun shouldCompleteOpenToolCalls(
        @Suppress("UNUSED_PARAMETER")
        frame: StreamFrame.End,
        @Suppress("UNUSED_PARAMETER")
        context: KoogStreamContext,
    ): Boolean = false
}

/**
 * 编排 Koog End frame 的 provider-neutral terminal 输出。
 *
 * Provider policy 只能选择是否补齐 open tool calls；实际状态修改仍由这里完成。随后统一校验成功
 * 状态、映射 usage/finish，并且只在所有步骤成功后返回不可变 terminal chunk 快照。
 */
internal class KoogStreamTerminalMapper(
    private val finishReasonMapper: KoogFinishReasonMapper =
        StopOnlyKoogFinishReasonMapper(),
    private val usageMapper: KoogUsageMapper = DefaultKoogUsageMapper(),
    private val toolCallTerminalPolicy: KoogToolCallTerminalPolicy =
        StrictKoogToolCallTerminalPolicy,
    private val textTerminalPolicy: KoogTextTerminalPolicy =
        StrictKoogTextTerminalPolicy,
    private val replayWriter: KoogReplayWriter = NoopKoogReplayWriter,
) {
    fun map(
        frame: StreamFrame.End,
        state: KoogStreamState,
        context: KoogStreamContext,
    ): List<StreamChunk> {
        val completedBlocks =
            buildList {
                if (textTerminalPolicy.shouldCompleteOpenTextBlocks(frame, context)) {
                    addAll(state.completeOpenTextBlocks())
                }
                if (toolCallTerminalPolicy.shouldCompleteOpenToolCalls(frame, context)) {
                    addAll(state.completeOpenToolCalls())
                }
            }.sortedBy { chunk ->
                (chunk as im.hikaru.harness.llm.BlockEndChunk).index
            }
        state.finish()
        val usage = usageMapper.map(frame.metaInfo, context)
        val finishReason = finishReasonMapper.map(frame.finishReason, context)
        val replayState =
            if (finishReason.isSuccessfulTerminal()) {
                replayWriter.write(
                    state = state,
                    frame = frame,
                    context = context,
                    finishReason = finishReason,
                )
            } else {
                null
            }

        return buildList {
            addAll(completedBlocks)
            if (usage != null) {
                add(UsageChunk(usage))
            }
            add(FinishChunk(finishReason, replayState))
        }
    }
}

private fun FinishReason.isSuccessfulTerminal(): Boolean =
    when (this) {
        StopFinishReason, ToolCallsFinishReason, MaxTokensFinishReason -> true
        is AbortedFinishReason, is ErrorFinishReason -> false
    }
