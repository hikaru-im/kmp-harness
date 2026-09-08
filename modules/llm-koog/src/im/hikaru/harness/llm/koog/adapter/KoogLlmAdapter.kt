package im.hikaru.harness.llm.koog

import ai.koog.prompt.executor.model.PromptExecutor
import im.hikaru.harness.llm.GenerateOptions
import im.hikaru.harness.llm.LlmAdapter
import im.hikaru.harness.llm.LlmException
import im.hikaru.harness.llm.LlmModelInfo
import im.hikaru.harness.llm.LlmProviderInfo
import im.hikaru.harness.llm.LlmResolvedModelInfo
import im.hikaru.harness.llm.RetryPolicy
import im.hikaru.harness.llm.StreamChunk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/** 使用 Koog PromptExecutor 实现 Harness LlmAdapter 的边界。 */
class KoogLlmAdapter internal constructor(
    private val executor: PromptExecutor,
    routes: List<KoogProviderRoute>,
    private val requestMapper: KoogRequestMapper,
    private val streamMapper: KoogStreamMapper,
    private val metadataMapper: KoogModelMetadataMapper,
    private val failureMapper: KoogFailureMapper,
) : LlmAdapter {

    constructor(
        executor: PromptExecutor,
        routes: List<KoogProviderRoute>,
        optionMapper: KoogOptionMapper = BasicKoogOptionMapper(),
        usageMapper: KoogUsageMapper = DefaultKoogUsageMapper(),
        finishReasonMapper: KoogFinishReasonMapper = StopOnlyKoogFinishReasonMapper(),
        replayRestorer: KoogReplayRestorer = RejectingKoogReplayRestorer,
        failureClassifier: KoogProviderFailureClassifier =
            UnclassifiedKoogProviderFailures,
        messageMapper: KoogMessageMapper? = null,
        toolMapper: KoogToolMapper? = null,
        reasoningMapper: KoogReasoningMapper? = null,
        toolStreamMapper: KoogToolStreamMapper? = null,
        toolCallTerminalPolicy: KoogToolCallTerminalPolicy? = null,
        textTerminalPolicy: KoogTextTerminalPolicy? = null,
        replayWriter: KoogReplayWriter = NoopKoogReplayWriter,
    ) : this(
        executor = executor,
        routes = routes,
        requestMapper =
            KoogRequestMapper(
                messageMapper =
                    ReplayAwareKoogMessageMapper(
                        delegate = messageMapper ?: DefaultKoogContentMessageMapper(),
                        replayRestorer = replayRestorer,
                    ),
                optionMapper = optionMapper,
                toolMapper = toolMapper ?: DefaultKoogToolMapper(),
            ),
        streamMapper =
            KoogStreamMapper(
                finishReasonMapper = finishReasonMapper,
                usageMapper = usageMapper,
                reasoningMapper = reasoningMapper ?: DefaultKoogReasoningMapper(),
                toolStreamMapper = toolStreamMapper ?: DefaultKoogToolStreamMapper(),
                toolCallTerminalPolicy =
                    toolCallTerminalPolicy ?: StrictKoogToolCallTerminalPolicy,
                textTerminalPolicy =
                    textTerminalPolicy ?: StrictKoogTextTerminalPolicy,
                replayWriter = replayWriter,
            ),
        metadataMapper = KoogModelMetadataMapper(),
        failureMapper = DefaultKoogFailureMapper(failureClassifier),
    )

    private val routes =
        routes.map(KoogProviderRoute::detachedCopy)

    private val routesById =
        this.routes.associateBy(KoogProviderRoute::id)

    init {
        require(this.routes.isNotEmpty()) {
            "Koog adapter must declare at least one provider"
        }
        require(routesById.size == this.routes.size) {
            "Koog adapter provider ids must not contain duplicates"
        }
    }

    override fun providerInfo(provider: String): LlmProviderInfo {
        val route = requireRoute(provider)
        return LlmProviderInfo(
            id = route.id,
            name = route.name,
        )
    }

    override fun providerRetryPolicy(provider: String): RetryPolicy =
        requireRoute(provider).retryPolicy

    override suspend fun listModels(provider: String): List<LlmModelInfo> =
        metadataMapper.listModels(
            provider = provider,
            models = requireRoute(provider).models,
        )

    override suspend fun resolveModel(
        provider: String,
        model: String,
    ): LlmResolvedModelInfo {
        val route = requireModel(provider, model)
        return metadataMapper.resolveModel(
            provider = provider,
            route = route,
        )
    }

    override fun stream(options: GenerateOptions): Flow<StreamChunk> =
        flow {
            val providerRoute = requireRoute(options.provider)
            val modelRoute = requireModel(providerRoute, options.model)
            val model = modelRoute.model
            val api = providerRoute.apiFor(modelRoute)
            val request = requestMapper.map(options, model, api)
            val frames =
                executor.executeStreaming(
                    prompt = request.prompt,
                    model = request.model,
                    tools = request.tools,
                )
            emitAll(
                streamMapper.map(
                    frames = frames,
                    context =
                        KoogStreamContext(
                            provider = options.provider,
                            model = options.model,
                            koogProvider = model.provider.id,
                            api = api,
                        ),
                )
            )
        }.mapKoogFailures(
            failureMapper = failureMapper,
            context =
                KoogFailureContext(
                    provider = options.provider,
                    model = options.model,
                    api = routeApiOrProvider(options.provider, options.model),
                ),
        )

    private fun requireRoute(provider: String): KoogProviderRoute =
        routesById[provider]
            ?: throw LlmException(
                message = "Unknown Koog provider route '$provider'",
                code = KoogLlmErrorCode.UNKNOWN_PROVIDER,
            )

    private fun requireModel(
        provider: String,
        model: String,
    ): KoogModelRoute = requireModel(requireRoute(provider), model)

    private fun requireModel(
        route: KoogProviderRoute,
        model: String,
    ): KoogModelRoute =
        route.models.firstOrNull { candidate ->
            candidate.model.id == model
        } ?: throw LlmException(
            message = "Unknown Koog model '${route.id}/$model'",
            code = KoogLlmErrorCode.UNKNOWN_MODEL,
        )

    private fun routeApiOrProvider(
        provider: String,
        model: String,
    ): String {
        val route = routesById[provider] ?: return provider
        val modelRoute = route.models.firstOrNull { candidate -> candidate.model.id == model }
            ?: return provider
        return route.apiFor(modelRoute)
    }
}
