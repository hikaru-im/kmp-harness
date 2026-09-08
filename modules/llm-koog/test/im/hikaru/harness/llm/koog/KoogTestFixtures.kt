package im.hikaru.harness.llm.koog

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message as KoogMessage
import ai.koog.prompt.streaming.StreamFrame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** 创建所有 Koog 单元测试共用的确定性模型。 */
internal fun testKoogModel(
    id: String = "test-model",
    provider: String = "test",
): LLModel =
    LLModel(
        provider = LLMProvider(provider, "Test Provider"),
        id = id,
        contextLength = 32_000,
        maxOutputTokens = 4_096,
    )

/** 创建不会携带请求内容或凭据的确定性流映射上下文。 */
internal fun testKoogStreamContext(
    provider: String = "test",
    model: String = "test-model",
    koogProvider: String = provider,
): KoogStreamContext =
    KoogStreamContext(
        provider = provider,
        model = model,
        koogProvider = koogProvider,
    )

/** 创建所有 Koog 单元测试共用的静态 provider route。 */
internal fun testKoogRoute(
    model: LLModel = testKoogModel(),
    provider: String = "test",
): KoogProviderRoute =
    KoogProviderRoute(
        id = provider,
        name = "Test Provider",
        models = listOf(
            KoogModelRoute(
                model = model,
                name = "Test Model",
            )
        ),
    )

/** 记录调用次数和关闭状态的 PromptExecutor 测试替身。 */
internal class RecordingPromptExecutor(
    private val frames: Flow<StreamFrame> = emptyFlow(),
    private val streamingError: Throwable? = null,
) : PromptExecutor() {

    var closed: Boolean = false
        private set

    var streamingCalls: Int = 0
        private set

    var lastStreamingPrompt: Prompt? = null
        private set

    var lastStreamingModel: LLModel? = null
        private set

    var lastStreamingTools: List<ToolDescriptor>? = null
        private set

    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): KoogMessage.Assistant =
        error("测试不应调用非流式 execute")

    override fun executeStreaming(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): Flow<StreamFrame> {
        streamingCalls++
        lastStreamingPrompt = prompt
        lastStreamingModel = model
        lastStreamingTools = tools.toList()
        streamingError?.let { error -> throw error }
        return frames
    }

    override suspend fun moderate(
        prompt: Prompt,
        model: LLModel,
    ): ModerationResult =
        error("测试不应调用 moderate")

    override fun close() {
        closed = true
    }
}
