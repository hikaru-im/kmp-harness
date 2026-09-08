# Harness Host 与 API 架构

本文是 Harness Host、客户端 API、RuoYi Relay 和配置所有权的权威设计。DSH 提供 API 领域和
连接语义参考，但本项目按 KMP、本地 Agent 与远程 Mobile 客户端的实际边界实现。

## 核心决策

1. Desktop Harness Host 是 Agent 执行状态和 Harness 配置的所有者。
2. RuoYi Backend 提供认证、租户、Host 注册、远程 Relay 和自身后端服务配置，不是 Harness
   配置中心。
3. LocalConnection 与 RemoteConnection 共享类型化 API 和结果语义，不要求共享物理传输实现。
4. 本地调用直接进入 API Gateway，不进行 JSON 或 RPC 信封转换。
5. 远程调用经 RuoYi Relay 到达同一个 API Gateway；Relay 不实现 Agent 业务。
6. CLI、HMR、动态源码加载和 Mobile 直连 Desktop 不在当前架构中。

## 状态所有权

| 边界 | 拥有的状态与能力 | 明确不拥有 |
|---|---|---|
| RuoYi Backend | 登录、租户、权限、Host 在线状态、连接 generation、Relay 路由与请求相关性、后端自身配置 | Runtime、Session、Harness Profile、LLM/MCP 配置、Host 凭据 |
| Desktop Harness Host | Runtime、Loader、Agent Session、Workspace、Agent/Expert、MCP、Knowledge、Skill、LLM、Settings、Credentials | 用户登录态、租户身份、跨 Host 路由 |
| `apps/shared` | Connection contract、Repository、ViewModel、会话投影和连接状态 | Runtime Service、Plugin、Host 配置存储 |
| Mobile Client | 远程 UI 和客户端状态 | 本地 Runtime、Desktop 文件系统、Host 凭据存储 |

RuoYi 的 system/infra 配置能力只服务 RuoYi 后端自身，不能作为 Desktop Harness Profile、LLM
Provider、MCP Server 或 Agent 组合的事实来源。

## 调用链路

本地链路不经过网络：

```text
Desktop UI
    -> Repository / ViewModel
    -> LocalConnection
    -> ApiGateway
    -> Runtime Service
    -> Agent Plugin
```

远程链路由双方主动连接 RuoYi：

```text
Mobile UI
    -> RemoteConnection
    -> RuoYi Backend Relay
    -> Desktop HostRelayConnection
    -> ApiGateway
    -> Runtime Service
    -> Agent Plugin
```

RuoYi Relay 负责认证、授权、目标 Host 路由、requestId 相关性和断线失败。它不解释 endpoint
业务，不读取 Runtime Service，也不把 Relay payload 转换成 Spring 业务 VO。

## API 分层

`apps/shared` 暴露客户端使用的类型化 API。具体 Connection 实现决定如何到达 Host：

```text
Connection
├── HostApi
├── SessionApi
├── WorkspaceApi
├── AgentApi / ExpertApi
├── McpApi
├── KnowledgeApi
├── SkillApi
├── LlmApi
├── SettingsApi
├── CredentialsApi
├── SubagentApi
└── GoalApi
```

类型化请求和响应模型属于 `contracts/harness-protocol`。Gateway endpoint 和 Runtime Service 适配
属于对应 Harness 模块。Connection、Repository 和 ViewModel 留在 `apps`，不创建
`modules/client-runtime` 或 `modules/client-repository`。

当前已实现：

```text
Connection.host.describe()
    -> LocalConnection
    -> HostDescribeEndpoint
    -> HostApiService
    -> HostDescription
```

## DSH API 对照

DSH 当前公开 52 个 client-request unary method，另有两个事件流、一个服务端交互响应入口和一个
Session 日志下载入口。我们保留其领域划分，但不逐字复制 Fetch、SSE 或 Node 文件系统载体。

| 领域 | DSH method | 本项目决策 |
|---|---|---|
| Host | `describe`、`pickDirectory`、`listDirectory`、`createDirectory`、`openPath` | `describe` 已实现；原生文件操作默认仅 Desktop 本地 |
| Session | `list`、`search`、`create`、`history`、`models`、`selectModel`、`rename`、`fork`、`prompt`、`attachment`、`updateQueue`、`cancel` | Agent 执行主 API |
| Events | `events.mux`、`events.host` | 映射为 Connection 管理的 Session/Host Flow；远程通过 Relay event 传输 |
| Interaction | `respond` | 用于审批和用户问题响应，保留请求相关性 |
| Workspace | `list`、`create`、`rename`、`delete`、`insertBefore`、`insertSessionBefore`、`archiveSession` | Host 所有，按 Desktop Workspace 能力实现 |
| Skill | `skill.list` | Host Skill catalog；调用仍可通过 `session.prompt` 的命令入口 |
| Agent Preset | `list`、`select`、`read`、`copy`、`openDocument`、`remove` | 映射到本项目 Agent/Expert 组合；由 Host 管理 |
| LLM | `providers`、`models`、`discoverModels` | Host Provider 与模型目录；不进入 RuoYi 配置中心 |
| Settings | `describe`、`openDocument`、`update`、`replace`、`mutate` | Host Profile/Settings API；不进入 RuoYi 配置中心 |
| Credentials | `describe`、`set`、`unset` | Host 安全存储；值只允许写入，响应永不返回明文 |
| Subagent | `list`、`history`、`prompt`、`interrupt` | 高级 Agent 能力 |
| Goal | `create`、`edit`、`pause`、`resume`、`complete`、`clear` | 高级 Agent 能力 |
| Download | `sessionLog` | Host 诊断与导出能力 |

DSH 没有公开独立的 `mcp.*`、`knowledge.*` 或 `expert.*` 执行 API。本项目需要这些领域，但具体
endpoint 必须由各自领域模型决定；不能为了表面对齐 DSH 而把它们塞进 Settings 或 Session 的
无类型 payload。

## 实施顺序

第一阶段形成最小 Agent 会话闭环：

```text
session.list
session.create
session.history
session.prompt
session.cancel
session events
host events
```

`session.prompt` 只确认请求被接受；模型增量、工具调用和运行状态通过事件流交付，因此事件模型
必须与第一批 Session API 同期实现。

第二阶段补齐对话可用性和安全交互：

```text
session.models / session.selectModel
session.rename / session.search / session.fork
session.updateQueue / session.attachment
approval response / question response
skill.list
workspace.*
```

第三阶段实现 Host 配置与高级 Agent：

```text
settings.* / credentials.* / llm.*
Agent / Expert / MCP / Knowledge / Skill 管理
subagent.* / goal.*
session log export
```

## 敏感 API

以下能力默认只允许 LocalConnection，不能因为已经存在 Relay 就自动向远程开放：

- Credential 写入和删除；
- 包含 secret 的 Settings 写入；
- 携带临时 API Key 的模型发现；
- Agent/Expert 组合文件编辑；
- Desktop 文件选择、目录创建和路径打开。

Settings 响应必须对 secret 做结构化脱敏；Credentials 只能返回 configured/source/writable 等状态，
不能返回值。Host 凭据应写入平台安全存储。

RuoYi Relay 不持久化也不把凭据当作配置管理，但它终止远程传输时并不天然具备端到端保密性。
在引入 Client 到 Host 的端到端加密和明确授权前，远程 Credentials 与 secret 写入保持关闭。

## Capability 与授权

`HostDescription.capabilities` 表示 Host 实现了什么协议能力，不表示当前用户获得了什么权限。
远程调用必须同时满足：

```text
协议版本兼容
    + Host capability
    + RuoYi 身份与 Host ACL
    + Endpoint 自身的 local-only / remote policy
```

本地调用仍经过 Gateway 的 endpoint 注册、Service 可用性和结果校验，但不承担网络身份校验。
