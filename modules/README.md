# Harness Modules

`modules` 保存可复用的 Harness 模块。它既包含内核和管理基础设施，也包含通过 Runtime 生命周期
安装的 Plugin 及 Host 插件组合；不包含最终客户端、Repository、ViewModel 或 RuoYi 后端 feature。

当前模块：

```text
modules/
├── runtime/       Context、Fiber、Service、Effect、Event 与生命周期
├── loader/        Registry、Entry 与声明式 Plugin 协调
├── boot/          Host Runtime 的可复用启动装配
├── bundle/desktop/ Desktop 编译期 Plugin Catalog、默认插件组合与启动入口
├── api/gateway/   传输无关的类型化 Harness API 分派
├── llm/           Provider-neutral 消息、流协议、Adapter seam 与 LlmRuntime
├── llm-koog/      Koog PromptExecutor Adapter 骨架与模型 route 映射
├── session/       仅追加会话事件日志、模型历史投影与生命周期 Store
├── logger/        kotlin-logging 的 Runtime Plugin 桥接
├── timer/         生命周期感知的协程计时器 Plugin
└── include/       配置源到 child Loader 的 Include Plugin
```

规划模块：

```text
agent / agent-loop / mcp / knowledge / expert / skill
```

`session`、`agent` 和 `agent-loop` 的实施边界与阶段顺序见
[Session、Agent 与 AgentLoop 实施规划](../docs/session-agent-loop-plan.md)。
`session` 第一阶段的具体 contract 与任务顺序见
[Session S1 对齐 DSH 实施规划](../docs/session-s1-plan.md)。

这些领域以及后续 `settings`、`credentials`、`workspace` 能力都属于 Harness Host，不接入
RuoYi 配置中心。是否拆成独立物理模块由实际 Service/Plugin 边界决定；领域模型尚未确定前不创建
空模块或无类型通用配置 API。

规划模块真正实现前不创建空依赖。`modules/host/transport` 不在当前架构中：远程 Client 不直接
连接 Desktop Host，而是统一通过 RuoYi Backend Relay。Desktop 侧的 HostRelayConnection 先由
`apps/jvm-app` 持有，出现第二种 Host 应用后再评估是否提取共享模块。

与 DSH 的主要对应关系：

```text
runtime     -> packages/core
loader      -> packages/loader
api/gateway -> packages/api/gateway
logger      -> packages/core logger + packages/logger-console 的非输出部分
timer       -> packages/timer
include     -> packages/include
llm         -> packages/llm/llm
llm-koog    -> packages/llm/llm-pi-ai 的 KMP/Koog 对应层
```

模块依赖必须指向 Runtime 和所需的特定 contract 模块等更低层边界，不能反向依赖 `apps` 或
`backend`。API Gateway 只依赖 `contracts/harness-protocol`，不依赖全部 RuoYi 业务 DTO。
