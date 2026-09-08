package im.hikaru.harness.llm

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 一个适配器已经注册并能够处理的 provider route。 */
@Serializable
data class LlmProviderInfo(
    val id: String,
    val name: String,
)

/** 模型能够接收的输入模态。 */
@Serializable
enum class ModelModality {
    @SerialName("text")
    TEXT,

    @SerialName("image")
    IMAGE,
}

/** provider route 对模型选择器公开的建议性条目。 */
@Serializable
data class LlmModelInfo(
    val provider: String,
    val id: String,
    val name: String,
    val description: String? = null,
    val inputModalities: List<ModelModality>? = null,
)

/** 精确 provider/model 路由的上下文容量。 */
@Serializable
data class LlmModelContext(
    val contextWindow: Long,
) {
    init {
        require(contextWindow > 0L) {
            "Model context window must be positive"
        }
    }
}

/** 适配器为一个精确模型公开的推理强度。 */
@Serializable
data class LlmReasoningEffortInfo(
    val id: ReasoningEffortId,
    val name: String,
    val description: String? = null,
)

/** 一个精确模型的可选推理强度及适配器默认值。 */
@Serializable
data class LlmModelReasoningInfo(
    val efforts: List<LlmReasoningEffortInfo>,
    val defaultEffort: ReasoningEffortId? = null,
)

/** provider/model 精确解析后的完整元数据。 */
@Serializable
data class LlmResolvedModelInfo(
    val provider: String,
    val id: String,
    val name: String,
    val description: String? = null,
    val inputModalities: List<ModelModality>? = null,
    val context: LlmModelContext? = null,
    val defaultMaxTokens: Long? = null,
    val reasoning: LlmModelReasoningInfo? = null,
)
