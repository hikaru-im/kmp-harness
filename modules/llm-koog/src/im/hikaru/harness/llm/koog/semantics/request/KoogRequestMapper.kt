package im.hikaru.harness.llm.koog

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.Prompt
import ai.koog.prompt.llm.LLModel
import im.hikaru.harness.llm.GenerateOptions

/** 已经转换完成、可以直接交给 Koog PromptExecutor 的请求。 */
internal data class KoogRequest(
    val prompt: Prompt,
    val model: LLModel,
    val tools: List<ToolDescriptor>,
)

/** 把 Harness 请求转换成 Koog Prompt、模型和工具描述。 */
internal class KoogRequestMapper(
    private val messageMapper: KoogMessageMapper = DefaultKoogMessageMapper(),
    private val optionMapper: KoogOptionMapper = BasicKoogOptionMapper(),
    private val toolMapper: KoogToolMapper = DefaultKoogToolMapper(),
) {
    fun map(
        options: GenerateOptions,
        model: LLModel,
        api: String = options.provider,
    ): KoogRequest {
        // system 不属于 Harness Message 列表，需要放在最前面。
        val requestContext =
            KoogStreamContext(
                provider = options.provider,
                model = options.model,
                koogProvider = model.provider.id,
                api = api,
            )
        val mappingContext =
            KoogMessageMappingContext(
                targetContext = requestContext,
            )
        val messages =
            buildList {
                options.system?.let { system ->
                    add(KoogSystemMessageFactory.create(system))
                }

                options.messages.forEach { message ->
                    add(messageMapper.map(message, mappingContext))
                    mappingContext.observe(message)
                }
            }

        val prompt =
            Prompt(
                messages = messages,
                // 同一个 Session 的调用使用稳定的 Prompt id。
                id = options.promptId(),
                params = optionMapper.map(options, model, requestContext),
            )

        return KoogRequest(
            prompt = prompt,
            // 必须使用 Adapter 已经解析好的 LLModel。
            model = model,
                tools =
                    toolMapper.map(
                        tools = options.tools,
                        context = requestContext,
                    ),
        )
    }

    private fun GenerateOptions.promptId(): String =
        sessionId?.value
            ?: messages.lastOrNull()?.id?.value
            ?: "$provider/$model"
}
