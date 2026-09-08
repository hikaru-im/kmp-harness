package im.hikaru.harness.api.gateway.host

import im.hikaru.contracts.common.ApiResult
import im.hikaru.harness.api.gateway.ApiGateway
import im.hikaru.harness.api.gateway.GatewayResultCodes
import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.effect.Disposable

/** 把 `host.describe` 分派到当前 Context 可见的 [HostApiService]。 */
public suspend fun ApiGateway.registerHostApi(owner: Context): Disposable =
    register(
        owner = owner,
        endpoint = HostDescribeEndpoint,
    ) {
        val service = owner.get(HostApiKey)
            ?: return@register ApiResult(
                code = GatewayResultCodes.ConfigurationError,
                msg = "Harness Host API Service 未注册",
            )

        ApiResult(
            code = ApiResult.SUCCESS_CODE,
            msg = "",
            data = service.describe(),
        )
    }
