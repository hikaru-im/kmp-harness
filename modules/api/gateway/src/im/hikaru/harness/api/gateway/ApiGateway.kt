package im.hikaru.harness.api.gateway

import im.hikaru.contracts.common.ApiResult
import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.effect.Disposable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 把稳定的类型化 API Endpoint 分派给 Runtime 一侧的处理器。
 *
 * Gateway 不处理 HTTP、WebSocket、Relay、JSON 或客户端连接状态。注册和注销通过同一个
 * Mutex 串行化；调用只在锁内取得处理器，不会在执行用户代码时占用注册表锁。
 */
public class ApiGateway {

    private val mutex = Mutex()
    private val registrations = linkedMapOf<String, Registration<*, *>>()

    /** 当前已经注册的方法集合，主要用于诊断和测试。 */
    public suspend fun methods(): Set<String> =
        mutex.withLock {
            registrations.keys.toSet()
        }

    /**
     * 注册一个 Endpoint，并返回只拥有本次注册的 Disposable。
     *
     * 同名 Endpoint 不能重复注册。旧 Disposable 不会误删后来重新注册的处理器。
     */
    public suspend fun <Request : Any, Response : Any> register(
        endpoint: ApiEndpoint<Request, Response>,
        handler: suspend (Request) -> ApiResult<Response>,
    ): Disposable {
        val registration =
            Registration(
                endpoint = endpoint,
                handler = handler,
            )

        mutex.withLock {
            check(endpoint.method !in registrations) {
                "API endpoint '${endpoint.method}' is already registered"
            }
            registrations[endpoint.method] = registration
        }

        return Disposable {
            mutex.withLock {
                if (registrations[endpoint.method] === registration) {
                    registrations.remove(endpoint.method)
                }
            }
        }
    }

    /**
     * 注册 Endpoint，并立即把注销动作交给 [owner] 的生命周期管理。
     */
    public suspend fun <Request : Any, Response : Any> register(
        owner: Context,
        endpoint: ApiEndpoint<Request, Response>,
        handler: suspend (Request) -> ApiResult<Response>,
    ): Disposable {
        val disposable = register(endpoint, handler)

        try {
            return owner.effect(disposable)
        } catch (error: Throwable) {
            try {
                disposable.dispose()
            } catch (cleanupError: Throwable) {
                if (cleanupError !== error) {
                    error.addSuppressed(cleanupError)
                }
            }
            throw error
        }
    }

    /**
     * 调用一个已注册 Endpoint。
     *
     * Endpoint 实例同时承担类型令牌职责：只有注册时使用的同一个实例才能调用对应处理器。
     */
    public suspend fun <Request : Any, Response : Any> invoke(
        endpoint: ApiEndpoint<Request, Response>,
        request: Request,
    ): ApiResult<Response> {
        val registration =
            mutex.withLock {
                registrations[endpoint.method]
            }

        if (registration == null || registration.endpoint !== endpoint) {
            return ApiResult(
                code = GatewayResultCodes.NotFound,
                msg = "Harness API endpoint '${endpoint.method}' 未注册",
            )
        }

        return try {
            @Suppress("UNCHECKED_CAST")
            val result =
                (registration as Registration<Request, Response>).handler(request)

            if (result.code == null || (result.isSuccess && result.data == null)) {
                ApiResult(
                    code = GatewayResultCodes.InternalError,
                    msg = "Harness API 返回了无效结果",
                )
            } else {
                result
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            ApiResult(
                code = GatewayResultCodes.InternalError,
                msg = "Harness API 处理失败",
            )
        }
    }

    private class Registration<Request : Any, Response : Any>(
        val endpoint: ApiEndpoint<Request, Response>,
        val handler: suspend (Request) -> ApiResult<Response>,
    )
}
