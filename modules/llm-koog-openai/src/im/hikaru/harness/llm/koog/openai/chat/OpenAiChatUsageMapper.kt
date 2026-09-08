package im.hikaru.harness.llm.koog.openai.chat

import ai.koog.prompt.message.ResponseMetaInfo
import im.hikaru.harness.llm.TokenUsage
import im.hikaru.harness.llm.koog.DefaultKoogUsageMapper
import im.hikaru.harness.llm.koog.KoogStreamContext
import im.hikaru.harness.llm.koog.KoogUsageMapper

/**
 * OpenAI Chat Completions 当前只使用 Koog 已归一化的 prompt/completion 计数。
 * cache/reasoning metadata 未被证明为可无损拆分，因此保持为空。
 */
public class OpenAiChatUsageMapper : KoogUsageMapper {
    private val generic = DefaultKoogUsageMapper()

    override fun map(
        metaInfo: ResponseMetaInfo,
        context: KoogStreamContext,
    ): TokenUsage? = generic.map(metaInfo, context)
}
