# KMP Harness

KMP Harness 是一个 Kotlin Multiplatform Agent Harness。它以 Cordis 的 Context、Fiber、
生命周期和 Loader 语义为基础，同时参考 DeepSeek Harness（DSH）的 Host、API Gateway、
本地连接与远程连接边界。

本仓库同时包含 Compose Multiplatform 客户端和 RuoYi 后端。RuoYi 负责认证、租户、Host 注册、
远程 Relay 和自身后端服务配置；Desktop Harness Host 拥有 Agent 执行与 Harness 配置。两者通过
稳定的 contracts 和 Relay 边界协作。

## 总体架构

```text
                           RuoYi Backend
              认证 / 租户 / Host 注册 / 远程 Relay
                    ↑                     ↑
       RemoteConnection             HostRelayConnection
                    ↑                     ↓
       Android / iOS Client       Desktop Harness Host
                                      ↓
Desktop Compose UI                API Gateway
        ↓                             ↓
LocalConnection                 Runtime / Loader
        └─────────────────────────────↓
                              Agent Plugins
```

本地模式不经过网络：

```text
Desktop UI -> LocalConnection -> API Gateway -> Runtime -> Plugins
```

远程模式不允许 Mobile 直接连接 Desktop，也不要求 Desktop 开放入站端口：

```text
Mobile RemoteConnection
    -> RuoYi Backend Relay
    -> Desktop HostRelayConnection
    -> API Gateway
    -> Runtime / Plugins
```

Desktop Host 主动建立到后端的长连接。后端负责用户、租户和设备鉴权、Host 在线状态、请求路由、
响应相关性与 Agent 事件转发。第一阶段远程 Agent 运行在 Desktop Host；未来如果后端运行云端
Agent，也必须复用同一套 contracts 与 API Gateway 语义。

RuoYi 的配置能力只管理后端服务自身，不保存 Harness Profile、LLM/MCP、Agent/Expert、Knowledge、
Skill 或 Host Credentials。这些状态属于 Harness Host，详细边界见
[Harness Host 与 API 架构](docs/harness-host-api.md)。

## 仓库边界

```text
apps/       最终客户端、客户端状态、Repository、ViewModel、UI 与平台装配
backend/    RuoYi 后端、数据库、管理后台与 Harness Relay
contracts/  RuoYi wire model，以及独立的 Harness protocol 扩展
modules/    可复用 Harness 内核、管理层、API 基础设施、Plugin 与 Host 组合
docs/       生命周期、管理层、Cordis 对齐等详细设计
references/ 只读参考源码，不参与 Git 与产品构建
```

`modules` 不等于“全部业务代码”，也不只包含 Plugin。Runtime、Loader 和 API Gateway 是可复用
基础设施；logger、timer、include 以及未来的 agent、mcp、knowledge、expert、skill 是 Plugin
或 Agent 能力模块。客户端 Connection、Repository、ViewModel 不进入 `modules`。

## 依赖方向

允许的核心依赖方向：

```text
apps/shared -> contracts + contracts/harness-protocol
apps/jvm-app -> apps/shared + modules/bundle/desktop + profile-file
apps/android-app / apps/ios-app -> apps/shared

modules/api/gateway -> contracts/harness-protocol + modules/runtime
modules/boot -> modules/runtime + modules/loader
Harness Plugins -> modules/runtime

backend/features/* -> backend/common + backend/framework + contracts
backend/features/harness -> contracts/harness-protocol + backend/framework
```

禁止的反向依赖：

- `contracts` 不依赖 Runtime、Ktor、Spring、Connection 或 Compose。
- `contracts/harness-protocol` 只在 RuoYi contract 之上增加 Harness 领域消息。
- `apps/shared` 不依赖 Runtime、Loader 或具体 Plugin。
- Runtime 不依赖客户端、RuoYi 后端或平台 UI。
- API Gateway 不拥有 HTTP、WebSocket、Relay 或客户端状态。
- Repository 和 ViewModel 是客户端内部实现，不创建独立 Harness 模块。

## 当前实现状态

已实现：

- Runtime、Context、Fiber、Service、Effect、事件和 intercept。
- Registry、Entry、Loader、失败回滚和配置协调。
- DSH-compatible Profile manifest、bundle/profile/home/overlay/launcher patch 组合与 startup/live reload。
- logger、timer、include 基础设施 Plugin。
- provider-neutral LLM 消息、流式协议、Adapter seam 和 LlmRuntime。
- llm-koog 的 PromptExecutor Adapter、静态模型 route 与生命周期骨架。
- RuoYi `ApiResult`、`WebSocketMessage(type/content)` 基础 wire model。
- 独立的 Client/Host 描述、协议版本、capability、握手和 Relay contracts。
- `apps/shared` 中的纯 KMP 握手协商器。
- 类型化 Connection / HostApi，以及 Desktop LocalConnection。
- API Gateway、`host.describe`、Catalog/Entry DesktopProfile 和 HarnessHost 生命周期。
- RuoYi Harness Host 注册、连接 generation 和 request/response Relay。

规划中，尚未实现：

- `RemoteConnection` 及其连接状态管理。
- Desktop `HostRelayConnection`。
- Relay event subscription、请求超时和多后端实例路由。
- Session、Agent、AgentLoop、MCP、Knowledge、Expert、Skill 模块。

明确不做：

- Agent 交互式 CLI；Desktop 启动器仅解析 `--home`、`--patch`。
- `!!js`、动态 npm/JAR 下载、classpath 扫描和源码 HMR。
- `modules/client-runtime` 或 `modules/client/repository`。
- Client 直接访问 Desktop Host 的 `modules/host/transport`。

## 与 DSH 的对应关系

```text
本仓库 modules/runtime       ≈ DSH packages/core
本仓库 modules/loader        ≈ DSH packages/loader
本仓库 modules/home          ≈ 平台选定配置目录的 JVM 文件系统契约
本仓库 modules/profile-file  ≈ DSH packages/boot/app-boot 的 Profile 文件层
本仓库 modules/bundle/desktop ≈ DSH bundle package + 编译期模块目录
本仓库 modules/api/gateway   ≈ DSH packages/api/gateway
本仓库 modules/llm           ≈ DSH packages/llm/llm
本仓库 modules/llm-koog      ≈ DSH packages/llm/llm-pi-ai 的 KMP/Koog 对应层
本仓库 apps/shared/src/client/connection ≈ DSH packages/client/connection
本仓库 LocalConnection       ≈ DSH InProcessApiClient 的类型化快速路径
RuoYi Backend Relay          = 本项目增加的认证远程接入与转发层
```

我们复用 DSH 的传输无关 Gateway、本地与远程共用协议、Host 拥有 Agent Session 等原则，
但本地调用不照搬 DSH 的 Fetch/JSON 同构载体：本地共享 API 语义并直接调用 Gateway，远程连接
才承担序列化和 Relay。详细设计索引见 [docs/README.md](docs/README.md)，Cordis 对齐状态见
[docs/cordis-alignment.md](docs/cordis-alignment.md)。

## 构建

```bash
./kotlin build
./kotlin test -m runtime
./kotlin test -m contracts -m harness-protocol -m shared
./kotlin test -m harness
```
