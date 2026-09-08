# RuoYi Contracts

根 `contracts` 模块是 RuoYi HTTP/WebSocket 的 KMP wire model，统一使用
`im.hikaru.contracts` 包名。RuoYi 后端的实际 JSON 形状是这里的权威依据。

```text
contracts/src/im/hikaru/contracts/
├── common/       ApiResult、分页
├── websocket/    RuoYi type/content 消息外层
├── auth/         管理端认证
├── app/          App 会员与系统 API
├── infra/        文件 API
├── sync/         App 离线同步
└── system/       管理端系统摘要
```

HTTP 响应与后端 `CommonResult<T>` 对齐：

```text
ApiResult<T>
├── code
├── msg
└── data
```

WebSocket 消息与后端 `JsonWebSocketMessage` 对齐：

```text
WebSocketMessage
├── type
└── content   # JSON 字符串，由 type 对应的监听器再次解码
```

Harness 不再在本模块中定义平行的 success/error 信封。独立的 Harness 扩展位于
[harness-protocol](harness-protocol/README.md)，其 RelayResponse 直接复用 `ApiResult`，远程消息
直接复用 `WebSocketMessage` 外层。

后端 Controller VO 可以保留 Spring 校验、Swagger 与内部转换，但 KMP contract 必须与最终 JSON
保持一致。时间格式、必填字段与 nullability 应通过 wire parity 测试锁定，不能依赖临时
`toString()` 约定。

本模块不能依赖 Runtime、Connection、Ktor、Spring 或 Compose。数据库实体、Spring Service、
协程 Job、平台句柄和客户端状态也不能进入 contracts。
