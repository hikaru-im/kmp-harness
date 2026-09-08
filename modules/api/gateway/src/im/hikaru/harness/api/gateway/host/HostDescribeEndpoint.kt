package im.hikaru.harness.api.gateway.host

import im.hikaru.contracts.harness.identity.HostDescription
import im.hikaru.harness.api.gateway.ApiEndpoint

/** 获取当前 Harness Host 描述的类型化 Endpoint。 */
public object HostDescribeEndpoint : ApiEndpoint<Unit, HostDescription>("host.describe")
