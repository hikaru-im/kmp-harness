package im.hikaru.harness.llm.koog

import im.hikaru.harness.llm.LlmModelContext
import im.hikaru.harness.llm.LlmModelInfo
import im.hikaru.harness.llm.LlmResolvedModelInfo

/** 把静态 Koog model route 转成 Harness 模型目录和权威元数据。 */
internal class KoogModelMetadataMapper {

    fun listModels(
        provider: String,
        models: List<KoogModelRoute>,
    ): List<LlmModelInfo> =
        models.map { route ->
            LlmModelInfo(
                provider = provider,
                id = route.model.id,
                name = route.name,
                description = route.description,
                inputModalities = route.inputModalities?.toList(),
            )
        }

    fun resolveModel(
        provider: String,
        route: KoogModelRoute,
    ): LlmResolvedModelInfo =
        LlmResolvedModelInfo(
            provider = provider,
            id = route.model.id,
            name = route.name,
            description = route.description,
            inputModalities = route.inputModalities?.toList(),
            context = route.model.contextLength?.let(::LlmModelContext),
            defaultMaxTokens = route.defaultMaxTokens,
            reasoning = route.reasoning,
        )
}
