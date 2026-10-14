# 本机 Projects 最小真实实现契约

> 2026-10-08 当前状态：本文正文保留实施前的分析。DesktopLocalProjectStore/Repository、LocalProjectsController、生产 LocalProjectsPage/Detail/Picker/Editor 已存在；已接入真实目录验证、项目登记与目录绑定、Home cwd 传递和 session membership。下文“没有服务”“建议新增”不是当前缺口。下一批复用这些能力，重点见 `projects-recent-page-contract-2026-10-08.md`。持久 membership 仍不代表重启后完整会话枚举和恢复，不能据此启用历史会话或归档动作。

日期：2026-10-07。本次仅调查并交付本文；未修改产品、未运行构建或测试。先经 CodeGraph 定位与读取真实源码，再核对当前 ASAR。

## 1. 结论与实施边界

当前 Harness **没有可供 UI 调用的项目列表、创建、目录绑定服务**。`ProjectsState` 是内存视觉状态；`SessionSummary.cwd` 是真实会话工作目录，但没有 project ID，也不能从 cwd 推导项目所有权。

最小可实施闭环是：Desktop 注入本机项目持久 store 和目录验证 adapter → Projects 列出真实登记记录 → 创建时登记一个已存在目录 → 选择项目及其目录 → Home 首次创建会话传入真实 `CreateSessionOptions(cwd)` → 保存真实返回的 SessionId 与项目的关联。首批只支持一个 root；DTO 保留 root 列表，避免以后多目录改数据格式。这里“创建项目”创建的是 Harness 登记记录，不承诺 mkdir、Git 初始化或 ASAR 原生项目创建。

项目列表可以通过新增 Desktop store 实现重启恢复。现有 Session API 只列 live Agent，不能据此承诺历史项目会话全部恢复；持久线程枚举和恢复必须单列能力及后续门槛。目录绑定不等于文件权限授权，也不等于沙箱限制。

排除：Relay/远端项目、云项目、ChatGPT hosted 项目、enrollment、团队权限、云环境、Git 初始化、worktree 管理、项目文件上传/同步。本轮不建议添加没有消费者的归档、删除、共享或权限开关。

## 2. 当前 ASAR 参考证据

- Archive：`/Applications/ChatGPT.app/Contents/Resources/app.asar`。
- 当前大小：`546868793` bytes。
- 本次重新核实 SHA-256：`22466838293554644d2dcb52aa128b389c713bed734900cd926bed76de47aa71`。
- 仓库旧 `docs/desktop-ui/reverse-engineering/inventory-2026-10-07/manifest.json` 的 archive 大小 `546868335`、hash `22252f9b76d73a04d9e23710e358201cc4c29b6f003cfbbe109eeb0784dcf848` 已过期，不作为当前消费者证据。旧 `projects-index-page-3d127290b4aa.js` 也不作为依据。
- 必要 bundles 从当前 archive 只读解析；临时抽取目录为 `/tmp/harness-projects-asar-20261007`。以下 archive offset 是文件起始的绝对字节偏移，fragment offset 是该 bundle 内的 UTF-8 字节偏移，均为 0-based。

| 当前 `/webview/assets/` bundle | bytes | archive offset | SHA-256 |
| --- | ---: | ---: | --- |
| `projects-index-page-eaa3b96f1827.js` | 58635 | 387889993 | `d8bef21467628b65ee4013cc4cee0e649846694ccf126d9e05f6730a5cb0427a` |
| `create-project-dialog-ae7f8c6b2975.js` | 21024 | 134387082 | `784cd9d976fc748a34b22703fef783488c29a4bae47d753143f26b980702fd39` |
| `composer-project-picker-content-8db3aa776219.js` | 5140 | 128502663 | `711b166fd429849d402f34b7111c8d5a9d95d3d3261ac05001296faecc864ce4` |

### 页面与动作证据

| 组成/动作 | 当前消费者或字段 | 精确证据/实施约束 |
| --- | --- | --- |
| 项目列表与项目内 local chats | `R(r.LOCAL_PROJECTS)`、`projectId`、`threadKeys` | projects-index offset 2576。项目记录和会话记录是两种来源，不可拿 session cwd 去重后假称项目 catalog |
| 本机/远端分组 | `projectKind`、`showLocalProjects`、`showRemoteProjects` | projects-index offset 7527；remote 有独立 host/path，首批不展示 |
| 项目搜索 | `sourceSearchText` | projects-index offset 9065：local 使用 `Xe(e).join(...)`，其他分支使用 `e.path`；Harness 应只对成功加载记录的名称/真实 root 做本地筛选 |
| 目录根信息 | `Ri(e)` → local `{rootPaths:Xe(e)}` | projects-index offset 57730；根路径与 projectId 分开持有 |
| 创建入口与本机默认目标 | `selectedHostId: local` | create-project-dialog offset 5495；远端创建是另一个条件分支 |
| 保存项目 | `ot().createLocal(...)` | create-project-dialog offset 8045；参数 `appearance, initializeDefaultWorkspaceGitRepository, name, setActive, sources`，成功后消费返回 `projectId`、激活并调用 `onProjectCreated` |
| 创建失败/取消 | `projectSetup.createLocalProject.saveError`、返回 null | create-project-dialog offset 8522；异常显示保存错误，null 不制造项目，finally 退出 pending |
| Home 项目选择器 | `onCreateProject/onProjectChange/onSelectWorkspaceProject` | composer-project-picker offset 679；本地 workspace 与受 `cloudProjectsEnabled/workCloudOnly` 控制的 hosted 数据是不同分支 |
| 远端项目标识 | `projectKind===remote`、`hostDisplayName` | composer-project-picker offset 4463；本机闭环不消费这一分支 |

共享数据键的核对来自同次抽取的 `app-shared-122c56612a72.js`：`ACTIVE_WORKSPACE_ROOTS` offset 1849336、`LOCAL_PROJECTS` 1849495、`SELECTED_PROJECT` 1849801、`PROJECT_WRITABLE_ROOTS` 1849837、`PROJECT_ORDER` 1850791。`projectOrigin` offset 2047222 的 schema 表示 local 可以带可选 `chatgpt` 来源；首批 Harness 本机登记无需照搬该 hosted 来源。数据键只能证明客户端状态命名，不能证明磁盘格式或可调用 IPC。

**未闭合消费者**：本次确认 `ot().createLocal` 的 UI 调用与成功/失败路径，未闭合到底层 endpoint/IPC，也未证明 `sources` 的完整类型及目录选择器原生实现。`scripts/analyze-codex-webview.py` 的 inventory/动作片段只能辅助定位，不能补足这条调用链。Harness 不得发明同名 endpoint、复用 ASAR 私有存储或声称已经接入 Codex 项目服务。无需为最小 Harness 本机登记继续扩大 ASAR 扫描。

## 3. Harness 准确源码与真实数据源

以下路径均相对仓库根 `/Volumes/Data/IdeaProjects/kmp-harness`。

| 文件/符号 | 当前事实 | 复用方式/缺口 |
| --- | --- | --- |
| `apps/shared/src/im/hikaru/harness/client/shell/ProjectsState.kt`：`ProjectsState.createLocalProject/findProject` | 生成 `local-project-N`、`新项目 N`，只存在内存 | 不能作为 production adapter；真实 store 返回记录后才投影到壳 |
| `apps/shared/src/im/hikaru/harness/client/shell/AppShellState.kt`：`WorkspaceProjectNode/WorkspaceItemNode` | 节点只有显示字段和 children，无 root/owner | 保留为 UI projection；不用 children.size 伪装完整历史计数 |
| `apps/shared/src/im/hikaru/harness/client/app/HarnessApp.kt`：`projectsState`、Projects 路由、Home `onSubmit` | `remember { ProjectsState(emptyList()) }`；创建按钮禁用并说明缺服务；Home `conversation.submit(agentOptions=...)` 未传 cwd | 注入项目 repository，Home 捕获已选 root 并传 options；当前空内存不是成功读取空 catalog |
| 同文件：`transport/conversationHost/connectionGeneration/verifiedConversationConnection` | 已有 Local/Relay 与验证后的会话绑定 | 必须复用，不能以 account 存在推断“本机不可用” |
| `apps/shared/src/im/hikaru/harness/client/conversation/ConversationController.kt`：`ConversationOperations/RealConversationOperations/submit` | `submit(options:CreateSessionOptions, agentOptions:AgentOptions)`；先 create，再 prompt；create 返回后保留真实选择 | 复用发送/fencing；项目关联失败不可使已有 SessionId 丢失，也不可重发 create |
| `apps/shared/src/im/hikaru/harness/client/connection/Connection.kt`：`CreateSessionOptions/SessionSummary/SessionApi` | create options 和 summary 均有 nullable cwd；summary 无 projectId | 现有接口已能传工作目录；project membership 需独立持久数据 |
| `apps/jvm-app/src/im/hikaru/harness/desktop/connection/LocalConnection.kt`：`session.create/list` | create 把 client cwd 映射到 core `CreateSessionOptions`；list 调用 typed `SessionListEndpoint` | 现有真实消费者，不需为 cwd 新建 mock 通道 |
| `modules/api/gateway/src/im/hikaru/harness/api/gateway/session/SessionApiGateway.kt`：`SessionCreateRequest/SessionCreateEndpoint/SessionListEndpoint` | typed methods `session.create/session.list`，请求包含 options 与 agentOptions | create 链已有；不存在已确认的 `project.*` endpoint |
| `modules/session-api/src/im/hikaru/harness/session/api/SessionApi.kt`：`DefaultSessionApi.create/list` | create 调用 `context.agents.create(...)`；list 是 `context.agents.list().map(...)` | **live-only**；不等于 SQLite 全部历史会话 |
| `modules/agent/src/im/hikaru/harness/agent/AgentRegistry.kt`：`create` | cwd 取 `sessionOptions.cwd ?: options.cwd` | 项目 root 明确放入 session options；不修改已创建会话的 cwd |
| `modules/session/src/im/hikaru/harness/session/SessionHeader.kt`：`SessionHeader/CreateSessionOptions.toHeader` | cwd 持久头字段；验证绝对 POSIX/drive/UNC 形式 | 只验证路径形式，没有证明目录存在、可读或授权 |
| `modules/session-persistence/src/im/hikaru/harness/session/persistence/RoomSessionPersistence.kt`：`SessionRow/SessionPersistenceDao/SessionPersistence` | `harness_sessions` 存 cwd；DAO `findSession/findEvents/countEvents/upsertSession/upsertEvents/deleteEventsFrom`；service `flush/load/inbox` | 支持按已知 ID flush/load；无 catalog list、项目表、项目成员关联；不能由 UI 绕过 service 直接扫描 DB |
| `modules/session/src/im/hikaru/harness/session/SessionStore.kt`：`flush` | 发布 `SessionEvents.Flush`；persistence `flush(session)` 写事务快照 | 不能把 SessionApi.create 成功当作“已证明落盘”；应验证实际 flush/重启行为 |
| `modules/bundle/desktop/src/im/hikaru/harness/bundle/desktop/DesktopBundle.kt`：`desktopPluginCatalog/desktopHostDescription` | catalog 安装 session、agent、session-api、session-persistence 等；DB 为 home 下 `harness-sessions.db`；默认 HostId `desktop-local` | 没有已安装 project service；adapter 构造使用实际 localHostId，不硬编码其他 Host 可访问 |
| `modules/bundle/desktop/src/im/hikaru/harness/bundle/desktop/DesktopHarnessHome.kt`：`resolveDesktopHarnessHome` | explicit → `HARNESS_HOME` → `~/.harness` | 新项目 store 沿用同一真实 home resolver |
| `apps/jvm-app/src/main.kt`：`HarnessApp(gitInitialCwd=...)` | 启动目录来自 `user.dir`，目前用于 Git | 可以作为目录输入候选，但不是已登记项目、不是验证通过的 root，不能自动显示到 Relay |
| `apps/jvm-app/src/im/hikaru/harness/desktop/scheduling/DesktopScheduledStore.kt`：`DesktopScheduledStore` | 已有版本文档、scope owner、锁和 byte CAS 模式 | 参考其 IO/版本/并发处理；不把项目塞进调度文件或复用 Scheduled DTO |

当前已有：verified Local Host、真实 session create/summary.cwd、按 ID 的 Room 持久化、真实会话发送。当前缺：typed 项目 catalog/store、目录验证与选择 adapter、project-session membership、持久会话枚举与可打开历史的恢复链。

## 4. 最小 production adapter 契约（建议新增，非现有 API）

首批用 Desktop 进程内注入，不扩 gateway/Relay。shared 不引用 JVM Path/Room，不用 `Map<String,Any>` 或反射。建议 package：`im.hikaru.harness.client.projects`。

```kotlin
@JvmInline value class LocalProjectId(val value: String)
data class LocalProjectOwner(val hostId: HostId) // 磁盘 owner，不存 generation/Connection
data class LocalProjectScope(val hostId: HostId, val generation: Long)
data class LocalProjectBinding(val connection: Connection, val scope: LocalProjectScope)
data class LocalProjectRoot(val path: String) // adapter 验证后的规范绝对路径
data class LocalProject(
    val id: LocalProjectId, val name: String, val owner: LocalProjectOwner,
    val roots: List<LocalProjectRoot>,
)
data class CreateLocalProject(val requestId: String, val name: String, val directory: String)

interface LocalProjectRepository {
    suspend fun list(binding: LocalProjectBinding): LocalProjectsResult
    suspend fun create(binding: LocalProjectBinding, request: CreateLocalProject): LocalProjectWriteResult
    suspend fun bindDirectory(binding: LocalProjectBinding, id: LocalProjectId, directory: String): LocalProjectWriteResult
    suspend fun attachSession(binding: LocalProjectBinding, id: LocalProjectId, session: SessionSummary): LocalProjectAttachResult
}
```

`attachSession` 使用既有 client `SessionSummary`，服务复验当前 owner、项目 root 与真实 session cwd；不接受 UI 编造 SessionId。`LocalProjectBinding.connection` 按引用身份比较，scope generation 是运行时 fence。Local member identity 维持现有 null 语义，不把 relay 登录账号拿来分割本机目录或重新绑定 generation。

结果类型必须精简且显式：list 的 `Ready(records)`（包含成功空记录）、`Unavailable(reason)`、`Error(reason)`、`UnsupportedVersion(version)`、`AccessDenied(reason)`；write/attach 有真实确认结果或 `Conflict/Rejected/Unavailable/Error`，不返回默认空成功。目录 picker 单独 `Selected(path)/Cancelled/Unavailable/Error`，取消不改变任何项目。Controller phase 用 `Unbound/Loading/Ready/Empty/Error/Unavailable/Unsupported/AccessDenied`；不要把本机权限错误标成“请登录 ChatGPT”。

### Desktop store/路径实现门槛

1. 建议新 `DesktopLocalProjectRepository(localHostId, store, directoryAccess)`；每个读写再次匹配 localHostId 与受信 Local binding。shared 的默认是 `UnavailableLocalProjectRepository(ProjectServiceNotExposed)`；Relay 无论注入了什么都不启用。
2. 建议新增 `DesktopLocalProjectStore`，文件 `${resolvedHarnessHome}/local-projects.json`（**新格式，不是当前已有文件，也不是 Codex 格式**）。保存 version、owner、持久 projectId/name/roots、membership、mutation requestId；首次成功读取不存在文件可以 `Ready(emptyList())`，不可把权限、损坏、取消或不支持版本当作空。
3. 用 typed kotlinx.serialization 文档、原子写入、并发锁/CAS、版本检查。create requestId 在 ambiguous 写失败后保留，重试核对相同持久 mutation，不能生成第二项目。projectId 由服务创建并落盘后确认；不使用 `local-project-N` 或 UI 先插入假记录。
4. `DesktopProjectDirectoryAccess` 验证用户输入/选择的目录：非空、绝对路径、真实存在且为目录、可访问；规范化并处理真实路径，返回确认的 root。`SessionHeader` 的字符串绝对路径校验不足以替代它。首批可用 HarnessDialog + 路径文本输入，只有验证成功才保存；原生 picker 可后续注入，未接入时不显示可点击的“浏览”。
5. 首批 bindDirectory **替换单 root**；旧会话 cwd 不变，其归属保持记录但显示历史 cwd。重新绑定目录不能改写 SessionHeader 或重启动 Agent。
6. 项目列表/详情显示持久记录和真实目录状态。目录删除/不可访问需显示错误并阻止该 root 新会话，不静默改用 `user.dir`。Git 仓库不是必需条件，Git 路径仍走既有 Git adapter。

## 5. Home 与会话的真实闭环

1. 在 Projects 选择已确认项目后进入 `PageId.Project(id)`；项目页的“开始新对话”设置 `selectedProjectId/selectedRoot` 并回 Home。Home picker 保留“无项目”路径，仅列当前 owner 成功加载的记录。
2. 发送前捕获 binding、projectId、root、draft；验证 adapter 仍属当前 verified Local Connection，选中 root 仍可用。调用现有 `ConversationController.submit(options = CreateSessionOptions(cwd = root.path), agentOptions = selectedModelOptions)`。
3. options 只作用于新会话；发送已有会话时不能通过切换 picker 偷改 cwd。创建后的绑定以返回 `SessionSummary.cwd/id` 为准。目录不一致必须显式错误，不能仅根据 UI selection 关联。
4. 在 create 成功后的确认点保存 membership。建议 controller 增加携带捕获项目目标的 created-result 通知，由项目 controller/store 消费；不要用“监听当前 selectedSessionId”猜测新建关联，也不要随当前 picker 改动重新归属。
5. membership 写失败与 prompt 失败分开处理：保留已创建 SessionId；可重试关联持久化，不再 create，不自动重发 prompt。断线或 owner 切换之后的回包不得更新新 owner 的列表/selection。
6. controller 每次 bind(null)/换 connection 实例/Host/generation 取消 jobs、清 selection/root、增加 request fence；Compose 当帧检查当前 binding，旧 owner 列表不能等 LaunchedEffect 才隐藏。搜索仅在 Ready/Empty 的已确认记录上做本地 name/root 筛选；筛选无匹配不同于缺服务。

### 项目内会话的显示与持久化限制

首批 membership 保存真实 id/cwd/createdAt，live summaries 按 owner 与 membership 关联，标题只能使用真实已知信息，未知运行状态保持 Unknown。不能仅因 `session.list()` 空就显示“此项目零历史会话”；已知 membership 但未 live 时显示“会话未载入，当前服务不能恢复”，禁止可点击后发明消息。

若要求**重启后枚举并打开全部项目历史线程**，必须追加 `SessionPersistence` 的 typed header list 和 session resume/history 服务：DAO 当前只有按 ID 查询；`SessionPersistence.load(id)` 重建 Session 并不等于 `DefaultSessionApi.requireAgent(id)` 的 Agent 已存在。必须完成持久枚举、owner 验证、实际恢复 Agent/只读历史消费链及测试，才开放历史打开按钮。项目 store 不承担伪造 Agent 或读取未验证 DB 的责任。本机项目“登记→创建会话 cwd”首批闭环不依赖完成这一服务。

## 6. 页面与公共组件复用

| 页面/状态 | 复用来源 | 要调整的真实消费点 |
| --- | --- | --- |
| Projects 列表/搜索/行 | `apps/shared/src/im/hikaru/harness/client/page/ShellPages.kt` 的 `ProjectsPage`、foundation `HarnessSearchField/HarnessRow/HarnessButton/HarnessText/HarnessSurface` | 替换内存 catalog 输入，区分 Loading/Unavailable/Error/真实 Empty；行显示根目录，数量仅在数据完整时显示 |
| 项目详情/新会话 | 同文件 `ProjectPage`、既有 `PageId.Project` 与 workspace navigator projection | 增加真实 root 与启动绑定；项目上下文当前只开 overlay，不能称已具备项目文件 service |
| 创建/目录绑定 | foundation `HarnessDialog/HarnessTextField/HarnessButton`、主题 `HarnessTokens` | 名称+已有目录，busy/error/取消，确认成功后更新；无目录 adapter 则具体 unavailable |
| loading/error/empty | `modules/ui-foundation/src/im/hikaru/harness/ui/foundation/HarnessFeedback.kt` 的 `HarnessStateContent/HarnessEmptyState` | Unavailable/Unsupported/AccessDenied 用明确文案分支；retry 仅当真实 adapter 可调用 |
| Home 项目 picker | 现有 `HomeComposer` 与公共 button/row/dialog | 受控 project/root selection；不列 cloud/remote、不提供 enrollment |

`ShellPages.kt` 的 `PageScaffold/EmptyHint/ProjectSurfaceRow` 当前是 **private**，新 projects 文件不能直接导入；优先复用公开 foundation primitives，若继续改原页则可在原文件内部复用。宽窄页面使用同一主题、可滚动内容和收缩行，长绝对路径可截断但详情可读取完整值；不照搬未经测量的 ASAR 固定宽度。

## 7. 下一批准确写集与依赖（建议，未实施）

| 顺序 | 文件/新增位置 | 责任与门槛 |
| --- | --- | --- |
| 1 | 新 `apps/shared/src/im/hikaru/harness/client/projects/LocalProjectModels.kt`、`LocalProjectRepository.kt`、`LocalProjectsController.kt` | typed DTO/result、默认 unavailable、binding/request fence；无 JVM IO |
| 2 | 新 `apps/jvm-app/src/im/hikaru/harness/desktop/projects/DesktopLocalProjectStore.kt`、`DesktopLocalProjectRepository.kt`、`DesktopProjectDirectoryAccess.kt` | 真实持久登记/关联、localHostId 复验、已有目录验证；参考 scheduling store 的版本/并发模式 |
| 3 | 新 shared `projects/LocalProjectsPage.kt`，调整 `client/page/ShellPages.kt`、`client/shell/ProjectsState.kt` 或替换其 production 消费 | foundation 列表/详情/创建表单，真实状态与路径，壳仅做 projection |
| 4 | `apps/shared/src/im/hikaru/harness/client/conversation/ConversationController.kt` | created-result 确认通知；关联错误不能抹去已确认会话；保留原发送与 fence |
| 5 | `apps/shared/src/im/hikaru/harness/client/app/HarnessApp.kt`、`apps/jvm-app/src/main.kt` | 注入 repository、真实 Local gate、Home cwd/项目选择/导航；本机 account 不作为远端 scope |
| 6 | 新 shared `client/projects/` tests、desktop `desktop/projects/` tests 和 Home integration test | store 重启/失败、路径验证、scope 竞态、真实 create cwd 与关联 |
| 后续门槛 | `modules/session-persistence/.../RoomSessionPersistence.kt`、`modules/session-api/.../SessionApi.kt`、`modules/api/gateway/.../session/SessionApiGateway.kt`、`LocalConnection.kt` | 仅在要完整历史枚举/打开时追加 list/resume/read 服务；不混入首批本机登记工程 |

依赖使用既有 HostId/Connection/SessionId/SessionSummary、kotlinx.coroutines、kotlinx.serialization、ui-foundation、Desktop HarnessHome；首批 store 可采用 JVM Files，不需要新后端、云 SDK 或 runtime 动态插件。实现前检查各模块直接依赖，不能因为 scheduling 编译可用就假定新 source set 已能引用其内部类型。

## 8. 验收清单

- [ ] 默认缺 adapter、Relay、未 verified、断线分别明确不可用；没有假项目、假空成功或无消费者按钮。
- [ ] 一个真实已有目录登记成功后生成持久 ID；再次读取/重启仍有同一记录；取消、无权限、损坏文件、unsupported version 不被当作空列表。
- [ ] 保存中/错误状态可见；重试写请求去重；并发写冲突明确，旧记录不丢失。
- [ ] 项目名称/root 搜索仅依赖成功数据；目录未绑定与验证失败均阻止该目标的新会话。
- [ ] Home 新建会话的真实 client→LocalConnection→gateway→SessionHeader.cwd 等于确认目录；已有会话不因选择新项目而改 cwd。
- [ ] create 成功、关联失败或 prompt 失败时保留真实 ID；重试不创建重复会话、不自动重复发送。
- [ ] 换 Host、connection 实例、generation、断线的迟到读写不得污染新 UI；Relay 看不到本机启动目录或 catalog。
- [ ] 项目列表/关联记录与 live-only 会话完整度分别表达；未知状态不标 Done，未恢复历史不开放假“打开”。
- [ ] 如宣称重启会话可打开，提供持久枚举与 Agent 恢复/只读 history 的真实服务链证据；仅 flush/load 单项不够。
- [ ] 宽窄浅深、loading/error/empty/unavailable/长路径布局验收由父线程统一编译测试；本文不声称已通过任何新项目测试或像素对齐。

本次交付范围仅本文；上述源码写集是后续建议，尚未执行。
