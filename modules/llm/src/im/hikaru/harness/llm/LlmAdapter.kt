package im.hikaru.harness.llm

import im.hikaru.harness.runtime.effect.Disposable
import kotlinx.coroutines.flow.Flow

/** One adapter-owned model-resolution generation bound to its eventual stream call. */
interface PreparedAdapterCall : Disposable {
    /** Exact model metadata from the same adapter generation as [stream]. */
    val model: LlmResolvedModelInfo

    /** Dispatch through that generation without re-reading dynamic connection facts. */
    fun stream(options: GenerateOptions): Flow<StreamChunk>

    override suspend fun dispose() = Unit
}

/**
 * 提供方协议适配器。
 *
 * 实现只负责一次真实提供方调用；重试由独立 llm-retry Plugin 在 AgentLoop
 * 的持久步骤边界执行。
 */
interface LlmAdapter {

    /** 描述本适配器拥有的一条 provider route。 */
    fun providerInfo(provider: String): LlmProviderInfo =
        LlmProviderInfo(
            id = provider,
            name = provider,
        )

    /** 返回随 provider route 一起捕获的重试策略数据。 */
    fun providerRetryPolicy(provider: String): RetryPolicy =
        defaultRetryPolicy()

    /** 返回用于模型选择器的建议性模型目录。 */
    suspend fun listModels(provider: String): List<LlmModelInfo> =
        emptyList()

    /** 解析精确 provider/model 的容量、默认值和推理能力。 */
    suspend fun resolveModel(
        provider: String,
        model: String,
    ): LlmResolvedModelInfo =
        LlmResolvedModelInfo(
            provider = provider,
            id = model,
            name = model,
        )

    /**
     * Bind model metadata and request dispatch to one adapter generation.
     * Dynamic adapters override this so a reload cannot split one prepared call.
     */
    suspend fun prepareCall(
        provider: String,
        model: String,
    ): PreparedAdapterCall {
        val resolved = resolveModel(provider, model)
        val adapter = this
        return object : PreparedAdapterCall {
            override val model: LlmResolvedModelInfo = resolved

            override fun stream(options: GenerateOptions): Flow<StreamChunk> =
                adapter.stream(options)
        }
    }

    /** 执行一次模型请求并输出 provider-neutral StreamChunk。 */
    fun stream(options: GenerateOptions): Flow<StreamChunk>
}
