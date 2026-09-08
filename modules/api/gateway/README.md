# API Gateway

`API Gateway` 负责把稳定的 Harness API 映射到 Runtime 中的 Service，并完成参数与结果边界
校验。它是可复用的 Harness 基础设施模块，不是客户端代码。

Gateway 不拥有 HTTP、WebSocket、请求相关性或客户端状态。Desktop 的 `LocalConnection` 可以
在进程内调用 Gateway；远程请求由 RuoYi Backend Relay 转发给 Desktop
`HostRelayConnection` 后调用同一个 Gateway。

```text
LocalConnection ---------------------------> API Gateway -> Runtime Service
RemoteConnection -> Backend Relay -> HostRelayConnection ---^
```

Gateway 只处理 Harness API 描述、输入输出校验、capability 和 Service 分派。公网鉴权、租户隔离、
Host 在线路由、WebSocket 生命周期和重连属于后端 Relay 与连接实现。

Agent Session、Settings、Credentials、LLM/MCP、Agent/Expert、Knowledge 和 Skill 的 Service 与
配置归 Harness Host 所有。RuoYi Relay 只是远程载体，不是这些 Endpoint 的实现或配置来源。

Gateway 依赖独立的 `contracts/harness-protocol`。远程失败由 RuoYi Relay 映射为数字错误码和
`ApiResult`，Gateway 不定义另一套字符串 `ErrorCode` 或通用响应信封。

## 当前实现

`ApiEndpoint<Request, Response>` 使用稳定 method 和实例身份保存类型边界。`ApiGateway` 的注册、
注销和查找通过协程 Mutex 串行化；注册返回只拥有本次注册的 `Disposable`，也可以直接绑定到
Context 生命周期。调用处理器时不会占用注册表锁。

当前第一条 endpoint 为：

```text
HostDescribeEndpoint
    -> Context.get(HostApiKey)
    -> HostApiService.describe()
    -> ApiResult<HostDescription>
```

未知 endpoint 返回 404，Host Service 缺失返回 502，处理异常或非法结果返回 500。Gateway 会保留
协程取消，不会把 `CancellationException` 转成业务失败。

LocalConnection 直接传递 Kotlin 对象，不使用 JSON。未来 HostRelayConnection 在进入 Gateway 前
负责 Relay 解码，因此两条链路共享 endpoint 语义，但不强制共享传输开销。Agent endpoint 会随
agent、mcp、knowledge、expert 和 skill 模块逐步加入。
