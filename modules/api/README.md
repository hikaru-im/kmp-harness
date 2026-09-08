# Harness API

`modules/api` 保存传输无关的 Harness API 基础设施。当前只有 `gateway`，它把稳定 API 调用分派
到 Runtime Service。

这里不实现 HTTP/WebSocket、RuoYi Relay、客户端 Connection 或 UI。未来 endpoint descriptor、
capability 映射等同时被本地和远程调用复用的 API 机制可以放在这里；具体 Agent 能力仍由
agent、mcp、knowledge、expert、skill 模块提供。

远程转发不会产生 `modules/host/transport`：Mobile 与 Desktop Host 都主动连接 RuoYi 后端，
后端 Relay 把请求送到 Desktop 后再调用 Gateway。
