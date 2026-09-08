package im.hikaru.harness.llm

import im.hikaru.harness.runtime.event.WaterfallEventKey
import kotlinx.coroutines.flow.Flow

/** 每次模型流式调用外围的 Cordis-compatible waterfall。 */
object LlmStreamEvent :
    WaterfallEventKey<GenerateOptions, Flow<StreamChunk>>(
        name = "llm/stream",
    )
