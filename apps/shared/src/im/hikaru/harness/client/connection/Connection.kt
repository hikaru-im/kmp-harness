package im.hikaru.harness.client.connection

import im.hikaru.harness.session.api.SessionApi

/**
 * 客户端访问 Harness Host 的类型化连接。
 *
 * Desktop 使用进程内 LocalConnection，Mobile 后续使用 RemoteConnection；上层客户端状态不需要
 * 判断 Agent 实际位于本地还是远程。
 */
public interface Connection {
    public val host: HostApi
    public val session: SessionApi
}
