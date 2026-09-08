package im.hikaru.harness.llm.koog

import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message as KoogMessage
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.streaming.StreamFrame
import im.hikaru.harness.llm.FinishReason
import im.hikaru.harness.llm.GenerateOptions
import im.hikaru.harness.llm.LlmException
import im.hikaru.harness.llm.LlmFailure
import im.hikaru.harness.llm.Message as HarnessMessage
import im.hikaru.harness.llm.TokenUsage
import kotlinx.serialization.json.JsonElement

/** Dispatches every semantics seam from the same strictly matched Provider bundle. */
internal class KoogProviderSemanticsRegistry(
    routes: List<KoogProviderRoute>,
    semantics: List<KoogProviderSemantics>,
) : KoogOptionMapper,
    KoogUsageMapper,
    KoogFinishReasonMapper,
    KoogReplayRestorer,
    KoogReplayWriter,
    KoogProviderFailureClassifier {

    private val semanticsByApi: Map<String, KoogProviderSemantics>
    private val defaultMessageMapper = DefaultKoogContentMessageMapper()
    private val defaultToolMapper = DefaultKoogToolMapper()
    private val defaultReasoningMapper = DefaultKoogReasoningMapper()
    private val defaultToolStreamMapper = DefaultKoogToolStreamMapper()
    private val defaultToolCallTerminalPolicy = StrictKoogToolCallTerminalPolicy

    val messageMapper: KoogMessageMapper =
        KoogMessageMapper { message, context ->
            (requireTargetSemantics(context).messageMapper ?: defaultMessageMapper)
                .map(message, context)
        }

    val toolMapper: KoogToolMapper =
        KoogToolMapper { tools, context ->
            (requireSemantics(context.api).toolMapper ?: defaultToolMapper)
                .map(tools, context)
        }

    val reasoningMapper: KoogReasoningMapper =
        KoogReasoningMapper { frame, state, context ->
            (requireSemantics(context.api).reasoningMapper ?: defaultReasoningMapper)
                .map(frame, state, context)
        }

    val toolStreamMapper: KoogToolStreamMapper =
        KoogToolStreamMapper { frame, state, context ->
            (requireSemantics(context.api).toolStreamMapper ?: defaultToolStreamMapper)
                .map(frame, state, context)
        }

    val toolCallTerminalPolicy: KoogToolCallTerminalPolicy =
        KoogToolCallTerminalPolicy { frame, context ->
            (requireSemantics(context.api).toolCallTerminalPolicy
                ?: defaultToolCallTerminalPolicy)
                .shouldCompleteOpenToolCalls(frame, context)
        }

    val textTerminalPolicy: KoogTextTerminalPolicy =
        KoogTextTerminalPolicy { frame, context ->
            (requireSemantics(context.api).textTerminalPolicy
                ?: StrictKoogTextTerminalPolicy)
                .shouldCompleteOpenTextBlocks(frame, context)
        }

    init {
        val routeIds = routes.map(KoogProviderRoute::id)
        require(routeIds.distinct().size == routeIds.size) {
            "Koog provider route ids must not contain duplicates"
        }

        val apiIds = routes.flatMap { route ->
            route.models.map { model -> route.apiFor(model) }
        }.distinct()

        val semanticIds = semantics.map(KoogProviderSemantics::provider)
        require(semanticIds.distinct().size == semanticIds.size) {
            "Koog provider semantics must not contain duplicate providers"
        }

        val routeIdSet = apiIds.toSet()
        val semanticIdSet = semanticIds.toSet()
        val missingSemantics = apiIds.filterNot(semanticIdSet::contains)
        val unknownSemantics = semanticIds.filterNot(routeIdSet::contains)
        require(missingSemantics.isEmpty() && unknownSemantics.isEmpty()) {
            buildString {
                append("Koog provider semantics must match routes exactly")
                if (missingSemantics.isNotEmpty()) {
                    append("; missing: ${missingSemantics.joinToString()}")
                }
                if (unknownSemantics.isNotEmpty()) {
                    append("; unknown: ${unknownSemantics.joinToString()}")
                }
            }
        }

        val suppliedByProvider = semantics.associateBy(KoogProviderSemantics::provider)
        semanticsByApi =
            apiIds.associateWith { provider ->
                suppliedByProvider.getValue(provider)
            }
    }

    override fun map(
        options: GenerateOptions,
        model: LLModel,
    ): LLMParams =
        requireSemantics(options.provider).optionMapper.map(options, model)

    override fun map(
        options: GenerateOptions,
        model: LLModel,
        context: KoogStreamContext,
    ): LLMParams =
        requireSemantics(context.api).optionMapper.map(options, model, context)

    override fun map(
        metaInfo: ResponseMetaInfo,
        context: KoogStreamContext,
    ): TokenUsage? =
        requireSemantics(context.api).usageMapper.map(metaInfo, context)

    override fun map(
        reason: String?,
        context: KoogStreamContext,
    ): FinishReason =
        requireSemantics(context.api).finishReasonMapper.map(reason, context)

    override fun classify(
        error: Throwable,
        context: KoogFailureContext,
    ): LlmFailure? =
        semanticsByApi[context.api]
            ?.failureClassifier
            ?.classify(error, context)

    override fun restore(
        message: HarnessMessage,
        base: KoogMessage.Assistant,
        context: KoogReplayContext,
    ): KoogMessage.Assistant =
        (
            semanticsByApi[context.sourceProvider]
                ?: semanticsByApi[context.targetApi]
                    ?.takeIf { context.sourceProvider == context.targetProvider }
        )
            ?.replayRestorer
            ?.restore(message, base, context)
            ?: base

    override fun write(
        state: KoogStreamState,
        frame: StreamFrame.End,
        context: KoogStreamContext,
        finishReason: FinishReason,
    ): JsonElement? =
        semanticsByApi[context.api]
            ?.replayWriter
            ?.write(state, frame, context, finishReason)

    private fun requireSemantics(provider: String): KoogProviderSemantics =
        semanticsByApi[provider]
            ?: throw LlmException(
                message = "Unknown Koog provider semantics '$provider'",
                code = KoogLlmErrorCode.UNKNOWN_PROVIDER,
            )

    private fun requireTargetSemantics(
        context: KoogMessageMappingContext,
    ): KoogProviderSemantics =
        context.targetContext?.let { target ->
            requireSemantics(target.api)
        } ?: throw LlmException(
            message = "Koog message mapping requires a resolved target provider",
            code = KoogLlmErrorCode.UNKNOWN_PROVIDER,
        )
}
