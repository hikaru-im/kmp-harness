package im.hikaru.harness.llm.koog.openai.semantics

import im.hikaru.harness.llm.koog.KoogProviderSemantics
import im.hikaru.harness.llm.koog.openai.chat.OpenAiChatFinishReasonMapper
import im.hikaru.harness.llm.koog.openai.chat.OpenAiChatOptionMapper
import im.hikaru.harness.llm.koog.openai.chat.OpenAiChatToolCallTerminalPolicy
import im.hikaru.harness.llm.koog.openai.chat.OpenAiChatUsageMapper
import im.hikaru.harness.llm.koog.openai.failure.OpenAiFailureClassifier
import im.hikaru.harness.llm.koog.openai.responses.OpenAiResponsesFinishReasonMapper
import im.hikaru.harness.llm.koog.openai.responses.OpenAiResponsesMessageMapper
import im.hikaru.harness.llm.koog.openai.responses.OpenAiResponsesOptionMapper
import im.hikaru.harness.llm.koog.openai.responses.OpenAiResponsesReasoningMapper
import im.hikaru.harness.llm.koog.openai.responses.OpenAiResponsesReplayRestorer
import im.hikaru.harness.llm.koog.openai.responses.OpenAiResponsesReplayWriter
import im.hikaru.harness.llm.koog.openai.responses.OpenAiResponsesTextTerminalPolicy
import im.hikaru.harness.llm.koog.openai.responses.OpenAiResponsesToolStreamMapper

/** Provider-specific semantics bundles installed by [OpenAiKoogPlugin]. */
public object OpenAiKoogSemantics {
    public fun bundles(): List<KoogProviderSemantics> =
        listOf(
            KoogProviderSemantics(
                provider = OpenAiChatOptionMapper.OPENAI_CHAT_COMPLETIONS_API_ID,
                optionMapper = OpenAiChatOptionMapper(),
                usageMapper = OpenAiChatUsageMapper(),
                finishReasonMapper = OpenAiChatFinishReasonMapper(),
                toolCallTerminalPolicy = OpenAiChatToolCallTerminalPolicy(),
                failureClassifier = OpenAiFailureClassifier(),
            ),
            KoogProviderSemantics(
                provider = OpenAiResponsesOptionMapper.OPENAI_RESPONSES_API_ID,
                optionMapper = OpenAiResponsesOptionMapper(),
                usageMapper = OpenAiChatUsageMapper(),
                finishReasonMapper = OpenAiResponsesFinishReasonMapper(),
                replayRestorer = OpenAiResponsesReplayRestorer(),
                replayWriter = OpenAiResponsesReplayWriter(),
                messageMapper = OpenAiResponsesMessageMapper(),
                reasoningMapper = OpenAiResponsesReasoningMapper(),
                toolStreamMapper = OpenAiResponsesToolStreamMapper(),
                textTerminalPolicy = OpenAiResponsesTextTerminalPolicy(),
                failureClassifier = OpenAiFailureClassifier(),
            ),
        )
}
