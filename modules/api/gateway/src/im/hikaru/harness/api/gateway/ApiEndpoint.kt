package im.hikaru.harness.api.gateway

/**
 * 一个类型化 Harness API 端点。
 *
 * Endpoint 只描述进程内调用的类型和稳定方法名，不负责 JSON、Relay 或请求相关性。
 * 同一个 Endpoint 实例同时作为请求和响应类型的运行时令牌。
 */
public open class ApiEndpoint<Request : Any, Response : Any>(
    public val method: String,
) {

    init {
        require(method.isNotBlank()) {
            "API endpoint method must not be blank"
        }
    }

    override fun toString(): String = "ApiEndpoint($method)"
}
