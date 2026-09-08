package im.hikaru.harness.client.connection

import im.hikaru.contracts.common.ApiResult
import im.hikaru.contracts.harness.identity.HostDescription

/** 客户端可调用的 Host API。 */
public interface HostApi {
    public suspend fun describe(): ApiResult<HostDescription>
}
