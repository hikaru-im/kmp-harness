# Outcome

在 kmp-harness 中选择性引入 ruoyi-kmp 的账号、租户、客户端基础设施和应用壳，保留 Harness Host 的执行、配置和持久化所有权，完成 Desktop 本地使用以及 Mobile 经 RuoYi Backend 访问 Desktop 的最小 Agent 会话闭环。

用户已确认本稿范围、A1–A20 和三个 Child 拆分，并于 2026-09-11 选择多会话协作；由 Supervisor 统筹，各 Child 按依赖在独立任务中推进。

2026-09-12 父级最终验证判定 A4 失败（集成候选漏并 ruoyi-foundation 分支 tip：8bdc1db、9bef52f），A6 因本机缺少可执行的真实安全存储平台受阻；按 Runtime 的 repair-child 流程追加 Child `ruoyi-foundation-acceptance-repair`（覆盖 A4、A6），重新集成后复验，已进入 integrated 的子任务不重开。

# Scope

## 基线与证据

- 来源任务：`01a08abf-4697-7fa1-9647-17746da51cfd`，已完整读取其可用用户消息与规划、修正、实施启动记录；末尾中断发生在空 brief 的读取阶段。
- 上游本地仓库：`/home/hikaru/IdeaProjects/ruoyi-kmp`，固定源码基线 `b4877d495c3479bf3632f4117a3c62b62c32d209`；本轮已核对 HEAD 和 tracked 工作树。未核对远端最新版，不迁入上游未跟踪工具目录。
- Harness 起点：`2f038820c04449915e4ce19a84b562bb99127cae`，当前分支 `master`；保留已有 `docs/README.md` 和 `docs/ui-slot-integration-plan.md` 的未提交工作。
- 2026-09-10 按 tracked 路径比较，统一 CRLF/LF，并将 Harness contracts 的包路径前缀归一化后：公共 contracts 仅 `module.yaml`、`MemberSyncContracts.kt` 内容不同；上游另有 `InfraContracts.kt` 和 8 个测试文件。相同 backend 路径有 34 个内容差异；数量只用于定位，不能代替行为审查或认定 Harness 独有文件应删除。
- 当前 `apps/shared` 只有 contracts、harness-protocol 和 Compose 依赖。Desktop 仍通过 LocalConnection 显示 Host 信息。后端已按 tenantId/userType/userId/HostId 注册与路由，尚无可用的端到端客户端和订阅交付。

## 交付范围

1. 基座 P0–P2：固定来源与同步清单；合并公共契约及本次接入必要的后端修复；引入 Member 认证、租户、Ktor、Koin、错误模型和独立登录态存储。个人资料所需缓存、同步仅按依赖闭包引入。
2. 应用壳 P3：共享 HarnessApp、导航、账号/租户/个人资料、Host 选择、连接状态；Desktop 本地入口离线可用，远程入口要求认证；提供最小会话操作界面。
3. 远程与平台 P4–P5：HostRelayConnection、RemoteConnection、Host 发现、握手、Session API 和有所有权的事件订阅；完成超时、断线、替换、重连以及平台装配和验收记录。

## Source coverage

本表覆盖引用任务的有效语义。原任务用于恢复需求和授权；仓库文档与上游源码用于实现取证，不视为要求实现其中全部功能。所有行读取状态均为 complete；covered 表示已映射到本稿，不表示实现或验收通过。最终稿与推进方式已获用户确认。

| 单元/来源定位 | 读取 | 保留语义 | Spec 位置 | 验收 | 覆盖与理由 |
| --- | --- | --- | --- | --- | --- |
| U1 首次用户请求 | complete | 给当前项目引入 ruoyi-kmp | integration / 基线与范围 | A1–A20 | covered；具体边界取后续规划与授权 |
| P1 原规划现状/实施 P0 | complete | 固定版本、源码选择性同步、区分布局与内容差异 | integration / 基线与范围 | A1 | covered |
| P2 原规划后端、P1 | complete | 认证/安全/数据库必要修复，保留 Harness WebSocket 扩展 | integration / 后端与身份 | A4 | covered |
| P3 原规划契约包名迁移建议 | complete | 旧的 import 迁移方向 | — | — | superseded；由 U2 替代 |
| U2 用户“上游已经调整了”及修正结论 | complete | im.hikaru.contracts 已统一；签到纯在线；合并 FileInfo；保留 Harness 协议 | integration / 公共契约 | A2、A3 | covered |
| P4 原规划客户端、P2 | complete | 网络、DI、认证、刷新、登出、重启恢复、错误模型 | integration / 客户端基础 | A5、A6、A8 | covered |
| P5 原规划身份模型 | complete | Member /app-api；Admin 留在管理后台；三元身份隔离 | integration / 后端与身份 | A4、A7、A14 | covered；此前建议随“开始实施”继续采用 |
| P6 原规划存储与 Outbox 边界 | complete | AppSession 与 Agent Session 分开；Room 不损坏 Host 数据；不重放 Agent 命令 | integration / 存储与依赖边界、远程行为 | A7、A8、A9、A17 | covered |
| P7 原规划应用层与 P3 | complete | 页面/导航/Repository/ViewModel、Host 选择、本地/远程切换 | integration / 应用行为 | A10、A11、A12 | covered |
| P8 原规划 Desktop 本地模式 | complete | 不强制登录；登录与登出不接管本地 Host 生命周期 | integration / 应用行为 | A10 | covered |
| P9 原规划 P4 远程闭环 | complete | 两端主动连接后端；握手、Session API、事件、超时和恢复 | integration / 远程行为 | A13–A17、A19 | covered |
| P10 原规划敏感 API 与隔离验收 | complete | 不开放远程凭据、secret 设置和本地文件系统操作 | integration / 远程行为 | A14、A15 | covered |
| P11 原规划 P5 | complete | Android/iOS 装配、安全存储、生命周期、部署与后续同步 | integration / 平台与验证 | A6、A18–A20 | covered |
| P12 原规划功能范围 | complete | 首期账号、租户、个人资料；不增加地址/签到/积分/支付页面 | integration / 基线与范围、应用行为 | A1、A11 | covered；已有后端不顺带删除 |
| P13 原规划 UI Slot | complete | 独立推进，现有提案不作为本次前置 | — | — | non-goal；避免改变 apps/shared 依赖边界 |
| P14 原规划管理后台和源码维护 | complete | 保留当前管理后台目录形态；不整仓覆盖或引入不相关 Git 历史 | integration / 基线与范围 | A1、A20 | covered |
| P15 原规划三个交付建议 | complete | 基座 → 应用壳 → 远程与平台 | integration / 交付依赖 | A1–A20 | covered；children 草案已映射，推进方式待确认 |
| U3 用户显式 /comet“开始实施” | complete | 授权实施已修正方案 | — | — | background；不是要求重新做一次只读规划 |
| U4 用户“继续”及当前“查看并继续任务” | complete | 恢复同一个 introduce-ruoyi-kmp change | — | — | background；不创建重复父 change |

## P0 文件级引入与保留清单

路径左侧以 ruoyi-kmp 为根，右侧以 kmp-harness 为根；目录规则递归应用，但只迁入确认范围所需的依赖闭包。

| 来源文件/目录 | 目标与动作 | 必须保留/处理 |
| --- | --- | --- |
| `contracts/src/**` | 映射到 `contracts/src/im/hikaru/contracts/**`，同内容保留 | 不做包名迁移，不并存两套同 FQCN 声明 |
| `contracts/src/infra/InfraContracts.kt` | 合并到目标 infra 契约 | 整合现有 `FileContracts.kt`，保留一个 FileInfo 定义 |
| `contracts/src/app/member/MemberSyncContracts.kt` | 内容合并到同包目标 | 去掉签到同步协议并同步调整后端，不能只删编译引用 |
| `contracts/test/*.kt` | 按 package 放入现有测试布局 | 引入 8 个契约测试，保留 Harness WebSocket/Relay 测试 |
| `contracts/module.yaml` | 按本仓库模板合并依赖 | 保留当前工具链与 harness-protocol 模块边界 |
| `backend/common/**/OAuth2TokenCommonApi.kt`、`backend/features/system/**/OAuth2TokenApiImpl.kt` | 配对审查/合并 OAuth2 契约与实现 | Member 与 Admin 两端兼容，不能孤立替换接口 |
| `backend/framework/**/YudaoSecurityAutoConfiguration.kt`、`WebProperties.kt`、`GlobalExceptionHandler.kt` | 合并本次认证所需行为及对应测试 | 已有 /ws 鉴权和租户上下文 |
| `backend/framework/**/YudaoWebSocketAutoConfiguration.kt`、`JsonWebSocketMessageHandler.kt`、`WebSocketSessionHandlerDecorator.kt` | 按行为逐块合并 | raw content、生命周期 listener、generation、租户上下文回归 |
| `backend/features/sync/**/SyncCommandWhitelist.kt`、`SyncCommandDispatcher.kt` 和测试 | 引入上游白名单及分发规则 | 签到不进入重放队列；保留地址/资料有效处理器 |
| `backend/features/member/**/MemberSignInRecordServiceImpl.kt`、`MemberUserServiceImpl.kt` 和相关测试 | 配套合并签到在线/资料行为 | 处理本仓库独有 MemberSignInSyncCommandHandler 及其测试；保留在线签到 API |
| `backend/features/infra/**/AppFileController.kt`、`FileService.kt`、`FileServiceImpl.kt`、`S3FileClient.kt` | 按契约/头像上传必要性合并 | 与 FileInfo/FilePresignedUrl 序列化一致 |
| `backend/server/module.yaml`、`resources/application*.yaml` | 合并必要依赖与配置键 | 保留 Harness feature、当前服务配置；不导入上游本地凭据 |
| `backend/database/**`、`backend/deploy/**` | 审查新增/差异，按部署依赖选择 | 不自动执行 SQL，不重置数据库；支付数据、额外数据库方言不作为首期要求 |
| `backend/features/pay/**`、codegen/banner、AdminUser 与错误码差异 | 逐项归类；仅依赖闭包所需修复纳入 | 不顺带升级支付、代码生成、后台业务 |
| `apps/shared/src/core/network/**`、`core/error/**` | 迁入客户端基础包并适配 | Ktor Auth、请求 header、错误映射、取消传播 |
| `apps/shared/src/core/session/**` | 迁入登录/租户/刷新状态 | 与后端地址、租户、用户绑定，拒绝陈旧异步结果回写 |
| `apps/shared/src/feature/member/{data,domain,presentation}/auth/**` | 分别在基座/应用壳交付引入 | 先密码登录；外部 SMS/社交登录接入不作为验收前置 |
| `apps/shared/src/feature/system/**`、member profile、`core/database/**`、`core/sync/**` | 仅选择租户/个人资料依赖闭包 | 不默认注册全部会员模块；不把 Harness 命令写进 Outbox |
| `apps/shared/src/di/*.kt` | 按选择的闭包组装 Koin | 不直接复制全量 AppModules；Host Runtime 独立 |
| `apps/shared/src/app/{navigation,theme}/**`、`RuoYiApp.kt` | 复用 UI 基础并建立 HarnessApp | 默认入口为 Harness；不接入会员营销导航 |
| `apps/shared/src@{jvm,android,ios}/{di,core}/**` | 按平台适配网络、登录存储、生命周期 | 替换上游 ruoyi-kmp 存储标识；Desktop 不照搬明文 member-session.json |
| `apps/{jvm-app,android-app,ios-app}/module.yaml` 与入口 | 增量装配 HarnessApp | 保留 Desktop --home/--patch、Host 关闭；Mobile 不装本地 Runtime |
| 无上游对应：`contracts/harness-protocol`、`apps/shared/**/connection`、`apps/jvm-app` 本地适配、`backend/features/harness` | 保留并补齐两端 Relay、Session API 和订阅 | typed API、Gateway 安全策略及身份/订阅隔离 |
| `modules/**`、`bundles/desktop/**`、当前管理后台目录 | 保留；只为已确认 Session 闭环扩展必要适配 | 不覆盖 Host 内核、模型目录、配置与凭据；不转换管理后台为 submodule |

Build 的首项工作是把上述规则落实为实际逐文件 diff；任何新增用户可见功能范围返回本 change 澄清，不把剩余差异默认纳入。

# Non-goals

- 不整仓替换、不改写上游仓库、不导入不相关 Git 历史；不顺带更新管理后台、支付、额外数据库方言或全部会员页面。
- 不把 UI Slot 接入作为前置，不改变 Host/API 权威文档中的所有权。
- 不实现 Mobile 直连 Desktop、后端多实例分布式 Relay、跨用户 Host 共享、端到端加密或远程敏感配置写入。
- 不扩展成完整 DSH API 对齐，不新增 fork、附件、审批、MCP、Knowledge、Goal 等高级业务。
- 首期密码登录可用；SMS、微信/社交平台的注册、密钥配置和外部服务上线不在本次范围。

# Acceptance examples

- A1: 交付记录固定上游 b4877d495c3479bf3632f4117a3c62b62c32d209、逐路径同步/保留/排除结论与许可证来源；实际 diff 不覆盖 Harness 内核、管理后台目录形态或既有无关文档改动。
- A2: 公共契约统一使用 im.hikaru.contracts；产品源码没有旧 im.hikaru.ruoyi.contracts 引用、同 FQCN 重复定义或并存 FileInfo；契约序列化测试通过且 Harness WebSocket/Relay contract 保留。
- A3: 签到只经在线 API 执行，不能进入 sync 白名单或被旧队列重放；地址/个人资料有效同步仍可用，旧签到同步请求稳定拒绝且不产生签到副作用。
- A4: 合并后的后端能以测试配置启动并处理 Member 登录、刷新和租户校验；Admin 既有认证及 Harness raw WebSocket、生命周期通知和租户上下文回归通过。
- A5: 密码登录成功建立 Member 身份；并发令牌过期只进行一次有效刷新；刷新失效进入未登录状态；网络失败与协程取消可区分，取消不被吞为业务成功或认证失败。
- A6: 登录可在安全存储可用的平台重启恢复；存储与 RuoYi 独立，令牌不出现在普通 JSON、Room、日志或提交内容中；Desktop 安全存储不可用时明确提示仅本次会话登录并不落明文，退出后无法恢复。当前环境缺少可执行的真实安全存储平台（无运行中的 Secret Service、无 Windows/macOS 主机、无 Android 设备或模拟器、无 Apple SDK/Xcode）时，该平台重启恢复子句以 Desktop 侧登录态持久化与登出语义测试，加上交付文档逐项如实记录未运行的平台项、原因与复验条件为满足；平台条件具备时仍以真实平台重启恢复为准，未运行的平台项不得记为通过。
- A7: 后端地址、租户或账号切换时，旧请求、刷新和订阅结果不能写入新身份，旧令牌不能发送到新地址/租户，缓存和未提交同步任务不会跨身份读取或执行。
- A8: 断网或服务端登出失败时仍清除本地登录态、取消账号相关工作并关闭远程连接；再次启动不会恢复已登出的会话，本地 Host 和 Agent 数据保持可用。
- A9: 客户端 Koin、业务数据库与 Harness Runtime/Session 数据边界独立；apps/shared 不依赖 Runtime Service，Mobile 不装 Host；已有 harness-sessions.db 可恢复原事件，数据库实例不共享文件且不使用破坏性迁移。
- A10: Desktop 无网络、无账号且未配置模型时能显示本地 Harness 入口；登录/登出不重建或关闭本地 Host，--home/--patch 仍有效，关闭窗口释放 Host、客户端作用域和数据库句柄。
- A11: HarnessApp 提供账号、租户、个人资料、Host 选择与连接状态；未登录远程操作进入登录流程，登录后返回原目标；首期导航不显示地址、签到、积分或支付入口。
- A12: 应用通过同一类型化 Connection 暴露 host.describe 与 session.list/create/history/prompt/cancel；本地 create→prompt→history 与事件日志派生结果一致，模型必须显式选择，缺失模型稳定报错。
- A13: Desktop 与 Mobile 都主动连接已配置的 RuoYi 后端；客户端能发现同身份在线 Host，完成兼容性/能力握手，连接失败与无在线 Host 状态可观察，协议不兼容时不进入会话调用。
- A14: 跨 tenantId、userType 或 userId 的 Host 发现、注册替换、请求、响应与订阅访问均拒绝；payload 不能覆盖登录身份，旧 Host generation 的迟到响应不能完成当前请求。
- A15: 远程 Credentials 写入/删除、secret Settings 写入、临时 API Key 模型发现和 Desktop 文件操作在到达敏感服务前拒绝，敏感值不出现在返回数据、事件或日志中。
- A16: Relay 事件只发给拥有该订阅的客户端连接；取消、切 Host、登出与断线清理订阅；重复/乱序事件按 sequence 处理，缺口可检测并通过 history 恢复，不按用户或租户广播。
- A17: 请求超时、Host 替换、Host/客户端断线均以稳定结果结束 pending 并释放资源；重连重新握手/订阅及恢复历史，未确定结果的 prompt/create 不自动重发或进入 Outbox。
- A18: Android 与 iOS 入口装配同一 HarnessApp 及各自网络/安全存储适配，Desktop、Android、iOS 编译目标全部通过；缺少 Apple SDK/Xcode 或设备时，iOS 编译、iOS 模拟器与 Android 生命周期检查必须在交付文档中逐项标注为未运行（blocked），不得标为通过，并写明复验条件。
- A19: 实际后端、Desktop 和 Android 客户端的登录→发现 Host→创建会话→显式选模型→prompt→流式事件→history→cancel 远程演练在具备真实后端、设备与模型凭据时执行并记录脱敏证据；当前环境不具备时，交付文档必须逐项记录未运行项、原因与复验前提，fixture 或编译结果不得冒充真实 E2E。
- A20: 交付文档包含依赖与数据库兼容说明、启动/部署/回滚和上游后续同步步骤，并逐项区分通过、失败、跳过及阻塞的检查；没有凭据/设备/Apple 环境的运行验收保持未完成。

# Constraints and invariants

- 服从 `docs/harness-host-api.md`、已归档 Session API 与 Session Persistence Room 规格；RuoYi 提供身份和转发，Desktop Host 拥有 Agent、Profile、模型和凭据。
- 继续现有 `introduce-ruoyi-kmp`；工作流状态与验收结果只通过 Comet Runtime 修改。
- 本轮仅完善正式产物。确认拆分前不创建 Child、worktree、独立会话或派发实现。
- 不自动执行数据库升级、部署、merge、push、PR 或提交当前 master 的用户改动。Supervisor 的集成区提交仅在用户确认其推进方式后由 Runtime 管理。
- 不把静态检查通过当作 Desktop 窗口启动、后端连通、移动端实机或模型提供方验证通过。

# Decisions

- 已确认：用户要求引入 ruoyi-kmp，并在修正契约方向后显式调用 Comet 开始实施；随后要求继续同一任务。
- 已确认：上游已采用 im.hikaru.contracts；以当前本地固定 commit 作内容合并，不做包名迁移。
- 沿用已授权规划：Member /app-api，Admin 管理后台独立；Desktop 本地无需登录；首期账号/租户/资料与最小 Session 闭环；UI Slot 和整仓/管理后台替换不在范围。
- 实现约束：原规划要求登录态安全存储，上游 Desktop 明文实现必须适配；无安全存储时采用明确提示的内存会话，不能悄悄降级落明文。此降级行为已随最终稿获得确认。
- 拆分检测：基座可通过契约/认证测试独立验证，应用壳可在本地 Connection/远程 fixture 上验证，远程实现依赖前两者形成实测闭环；存在清晰交付出口和先后依赖，建议 Supervisor Change。
- Supervisor Change：`introduce-ruoyi-kmp`，负责跨层不变量和全量最终验收。
- Child：`ruoyi-foundation`（A1–A9），固定来源、公共契约、后端及客户端账号基础。
- Child：`ruoyi-app-shell`（A10–A12），依赖 ruoyi-foundation，提供 HarnessApp、本地会话和可注入的远程连接界面。
- Child：`ruoyi-remote-platform`（A13–A20），依赖 ruoyi-app-shell，补齐远程、平台与实际验收。
- Child：`ruoyi-foundation-acceptance-repair`（A4、A6），依赖 ruoyi-remote-platform；等价并入 ruoyi-foundation 分支 tip 中缺失的后端启动/登录/刷新/租户测试与客户端账号生命周期测试，并按已确认口径补齐 A6 的安全存储平台记录。
- 2026-09-12 用户确认调整 A18/A19 验收口径：无 Apple SDK/Xcode、无设备、无真实后端与模型凭据时，以「编译目标真实通过 + 交付文档逐项如实记录未运行项、原因与复验条件」为满足，据此收尾并归档；其余验收项与不伪造通过的原则不变。
- 2026-09-12 用户确认把 A18/A19 的同类放宽扩展到 A6：本机无可用真实安全存储平台时，以 Desktop 侧登录态持久化与登出语义测试，加上交付文档逐项如实记录未运行的平台项、原因与复验条件为满足；平台条件具备时仍以真实平台重启恢复为准，未运行的平台项不得记为通过。
- 2026-09-12 父级最终验证失败后按 repair-child 追加 Child：`ruoyi-foundation-acceptance-repair` 覆盖 A4、A6。A4 的修复是把 `ruoyi-foundation` 分支 tip（`8bdc1db`、`9bef52f`）中缺失的后端测试与客户端账号生命周期测试等价并入集成目标；不重开已 integrated 的子任务，也不改动 master 目标分支。
- 已确认推进方式：multi-session。每个可执行 Child 由独立任务处理，当前任务统筹；当前依赖链顺序执行。用户确认原文：“确认，多会话协作（推荐）：每项子任务在独立任务中按依赖推进，当前任务统筹。”

# Open questions

无。用户已确认范围、验收、降级行为、拆分及多会话推进方式。

# Verification expectations

- Shape 检查：必需标题非空、来源覆盖完整、A1–A20 唯一且与 Spec 双向映射、children 依赖无环且完整分配、diff 无空白错误。本轮不声称实现测试已通过。
- 基座：迁入契约序列化测试，认证并发/退出/身份切换/安全存储测试，后端认证和 WebSocket/租户/同步回归；用隔离数据库测试配置启动后端，先记录既有失败再对比。
- 应用壳：shared 与 jvm-app JVM 测试、本地 Session/事件一致性测试、Desktop 首帧与关闭、已有 Host 数据恢复检查。
- 远程：双客户端订阅隔离、三元身份与 generation、超时/断线/重连、敏感 API 拒绝、真实后端+Desktop+Android 演练。
- 平台：按仓库 `./kotlin` 帮助解析并执行目标模块 test/build；Desktop/Android/iOS 编译目标在任意环境都必须真实通过；Android 入口构建、生命周期检查与 iOS 模拟器运行在环境具备时执行。运行环境不可用时必须在交付文档中如实标注为未运行（blocked），任何情况下不得标为通过。
- 修复轮：基座缺失提交等价并入后，`./kotlin test -p jvm -m server` 必须真实通过（H2 与测试替身 Redis），shared/Desktop 账号生命周期测试真实通过，交付文档逐项记录 A6 未运行的真实平台项、原因与复验条件。
- 最终需要新的只读 Verifier 全量核对。真实模型凭据、服务或设备不可用时保留待验收状态；A6、A18/A19 的口径已按用户确认调整为「Desktop 侧语义测试与如实记录未运行项、原因和复验条件即满足」，其余验收项与「未运行检查不得记 passed」的要求不变。
