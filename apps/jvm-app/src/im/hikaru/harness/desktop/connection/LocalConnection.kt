package im.hikaru.harness.desktop.connection

import im.hikaru.contracts.common.ApiResult
import im.hikaru.contracts.harness.identity.HostDescription
import im.hikaru.harness.api.gateway.ApiGateway
import im.hikaru.harness.api.gateway.host.HostDescribeEndpoint
import im.hikaru.harness.client.connection.Connection
import im.hikaru.harness.client.connection.HostApi

/**
 * Desktop 客户端到同进程 Harness Host 的本地连接。
 *
 * 本实现直接调用类型化 Gateway，不创建 RPC 信封，也不执行 JSON 编解码。
 */
public class LocalConnection(
    gateway: ApiGateway,
) : Connection {

    override val host: HostApi =
        object : HostApi {
            override suspend fun describe(): ApiResult<HostDescription> =
                gateway.invoke(
                    endpoint = HostDescribeEndpoint,
                    request = Unit,
                )
        }
}
