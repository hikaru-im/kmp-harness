package im.hikaru.harness.api.gateway.host

import im.hikaru.contracts.harness.identity.HostDescription

/** Runtime 向 API Gateway 暴露的 Host 能力。 */
public fun interface HostApiService {
    public suspend fun describe(): HostDescription
}
