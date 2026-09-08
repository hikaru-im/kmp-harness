package im.hikaru.harness.llm.koog

/** 流式响应 mapper 使用的稳定路由身份，不携带消息、工具参数或凭据。 */
data class KoogStreamContext(
    val provider: String,
    val model: String,
    val koogProvider: String,
    /** Installed protocol semantics selected for this provider/model route. */
    val api: String = provider,
) {
    init {
        require(provider.isNotBlank()) {
            "Koog stream provider must not be blank"
        }
        require(model.isNotBlank()) {
            "Koog stream model must not be blank"
        }
        require(koogProvider.isNotBlank()) {
            "Koog stream client provider must not be blank"
        }
        require(api.isNotBlank()) {
            "Koog stream api must not be blank"
        }
    }
}
