package im.hikaru.harness.llm.koog

import ai.koog.prompt.llm.LLModel
import im.hikaru.harness.llm.AlwaysRetryPolicy
import im.hikaru.harness.llm.LlmModelReasoningInfo
import im.hikaru.harness.llm.ModelModality
import im.hikaru.harness.llm.NormalRetryPolicy
import im.hikaru.harness.llm.RetryPolicy
import im.hikaru.harness.llm.defaultRetryPolicy

/** 一个已安装或由 settings 快照物化的 Harness 模型到 Koog [LLModel] 映射。 */
data class KoogModelRoute(
    val model: LLModel,
    /** Protocol semantics used for this model; omission inherits its provider route. */
    val api: String? = null,
    val name: String = model.id,
    val description: String? = null,
    val inputModalities: List<ModelModality>? = null,
    val defaultMaxTokens: Long? = null,
    val reasoning: LlmModelReasoningInfo? = null,
) {
    init {
        require(model.id.isNotBlank()) {
            "Koog model id must not be blank"
        }
        require(name.isNotBlank()) {
            "Koog model name must not be blank"
        }
        require(api == null || api.isNotBlank()) {
            "Koog model api must not be blank"
        }
        model.contextLength?.let { contextLength ->
            require(contextLength > 0L) {
                "Koog model context length must be positive"
            }
        }
        model.maxOutputTokens?.let { maxOutputTokens ->
            require(maxOutputTokens > 0L) {
                "Koog model max output tokens must be positive"
            }
        }
        defaultMaxTokens?.let { requestDefault ->
            require(requestDefault > 0L) {
                "Koog model default max tokens must be positive"
            }
            model.maxOutputTokens?.let { capability ->
                require(requestDefault <= capability) {
                    "Koog model default max tokens must not exceed its output capability"
                }
            }
        }
    }
}

/** 一个由代码安装的 provider route，以及某一 generation 当前可解析的 Koog 模型目录。 */
data class KoogProviderRoute(
    val id: String,
    val name: String,
    val models: List<KoogModelRoute>,
    /** Default protocol semantics for models that do not name one. */
    val api: String = id,
    val retryPolicy: RetryPolicy = defaultRetryPolicy(),
    /** Koog protocol/capability template for configured models absent from this catalog. */
    val customModelTemplate: LLModel =
        requireNotNull(models.firstOrNull()?.model) {
            "Koog provider must declare at least one installed model"
        },
) {
    init {
        require(id.isNotBlank()) {
            "Koog provider id must not be blank"
        }
        require(name.isNotBlank()) {
            "Koog provider name must not be blank"
        }
        require(api.isNotBlank()) {
            "Koog provider api must not be blank"
        }
        require(models.isNotEmpty()) {
            "Koog provider must declare at least one model"
        }
        require(models.map { route -> route.model.id }.distinct().size == models.size) {
            "Koog provider model ids must not contain duplicates"
        }
    }
}

internal fun KoogProviderRoute.detachedCopy(): KoogProviderRoute =
    copy(
        models = models.map { route ->
            route.copy(
                model = route.model.copy(capabilities = route.model.capabilities?.toList()),
                inputModalities = route.inputModalities?.toList(),
                reasoning = route.reasoning?.copy(
                    efforts = route.reasoning.efforts.toList(),
                ),
            )
        },
        retryPolicy = retryPolicy.detachedCopy(),
        customModelTemplate =
            customModelTemplate.copy(capabilities = customModelTemplate.capabilities?.toList()),
    )

internal fun KoogProviderRoute.apiFor(model: KoogModelRoute): String =
    model.api ?: api

private fun RetryPolicy.detachedCopy(): RetryPolicy =
    when (this) {
        is NormalRetryPolicy ->
            copy(retryableCodes = retryableCodes.toList())

        is AlwaysRetryPolicy -> copy()
    }
