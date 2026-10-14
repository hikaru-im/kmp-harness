# Managed Worktree 归属与保护数据实施分析

分析日期：2026-10-07；实施验收更新：2026-10-08。状态：M1 的真实 managed 登记、只读 inventory/归属核验及页面接线已实现；pin、archive 与删除服务仍未实现。

阅读规则：第 1–10 节保留实施前的分析事实与范围建议；其中“当前/尚无/未来”描述的是分析时点，实际交付、源码基线、验证和限制以第 11 节为准。

本批唯一写集为本文件；不修改生产、测试、构建配置及其他文档，不运行构建，不访问或修改用户插件/工作树数据。不扩展应用 snapshot 服务。上一批 branch checkout 已由父线程接管；父线程报告隔离四组 72/72（29+39+2+2）通过，本批未复跑。本分析按当前 checkout 源码描述，不沿用旧契约中的“尚无 checkout”结论。

## 1. 结论与首个可实施批次

当前真实链路能发现普通 Git worktrees、创建并验证新 worktree、读取 live sessions、保存本地项目 memberships。它不能证明某个 worktree 属于 Harness 管理，也不能证明不存在关联历史会话或 pin。

建议先实施 **M1：独立 managed 登记库 + 创建回执登记 + inventory 与分维度保护观察**。M1 可真实回答“本 owner 的登记库记录了哪些已验证创建”，并展示当前 Git 元数据及已观察会话；缺少的保护维度保持 `Unknown` / `Unavailable`。即使记录为 Managed，删除、自动清理、force remove 和 prune 仍必须 `Unavailable`。

首批不新增 session/pin/archive 端点，也不要求消费 live/membership 列表：保护卡直接展示这些维度未知或服务不可用，避免把 subset 当作 absence proof。持久会话 catalog、pin、archive 仅列后续依赖，不展开会话架构改造。没有 pin 服务不等于没有 pins；普通 Git worktree list 不等于 managed inventory。

## 2. 原版依据与适用边界

依据同目录已有 [git-worktrees 契约](git-worktrees-implementation-contract-2026-10-07.md) 与 [evidence JSON](git-worktrees-implementation-contract-2026-10-07.evidence.json)。本批读取既有记录，未重新提取或哈希 ASAR。

记录的基线：`/Applications/ChatGPT.app/Contents/Resources/app.asar`，大小 `546868793` bytes，SHA256 `22466838293554644d2dcb52aa128b389c713bed734900cd926bed76de47aa71`；evidence 捕获时间 `2026-10-07T07:36:04.398448+00:00`。偏移采用零起点 UTF-8、结束位置不含边界。

| 锚点 | 原版事实 | 本项目应采纳的边界 |
| --- | --- | --- |
| W01 | `codex-worktrees` 按 host config / worktrees root 查询 | 明确 host 与 managed inventory 来源，不能把任意 `git worktree list` 作为此结果 |
| W03 | `worktree-delete` 主桥 await 后丢弃服务返回值；另有 `worktree-set-owner-thread` | owner 是独立数据；未来删除必须传播 typed receipt，不能照抄丢返回值行为 |
| W05 | attaching/cleanup roots guards，归档/失败/unsupported 分类；自动归档检查 owner、pins、newborn | 管理和保护需真实服务；原版 `force: true` 不是本项目删除授权 |
| W08 | worker 区分 `codex-worktrees`、`list-worktrees`、managed state 与 owner 方法 | 区分登记 inventory、Git inventory、保护观察；其中 snapshot 能力不在本任务范围 |
| W09 | worker 返回 success/worktreeId 或 deletion-failed / unsupported-nested-repository | 不用 toast、列表消失或成功启动命令替代删除结果确认 |
| W10 | 分页 thread/list、pinnedThreads.list、thread/read 合并去重；重复 cursor 报错；排除 archived，pins 可补回列表外条目 | live subset 不足；分页失败/不完整须保留未知，不能变成 empty |
| W11 | 无 pinnedThreads service 明确报 unavailable | 缺服务必须显示不可用，不能解释为 no pins |
| W12 | targeted lock 后重查 pins，再 archiveConversation(cleanupWorktree:false)、delete、invalidate | 未来 destructive plan 要重验保护和结果；本批不实现 archive/delete |
| W14 | 路径、linked conversations/loading/pin 提示、New chat/Delete；新 chat 复用文件/分支而不复制 history | 各按钮分别依赖数据与能力；原版禁用条件不能代替本项目尚缺的保护服务 |

## 3. 当前真实源码链

源码经 CodeGraph 优先定位与读取。下面列出可复查的符号与路径；“缺口”限定为本次审查的 Git、projects、session-api/gateway、persistence 与桌面注入链，不宣称扫描了所有模块。

| 当前位置 / 符号 | 已完成事实 | 不能据此证明的内容 |
| --- | --- | --- |
| `apps/shared/src/im/hikaru/harness/client/git/GitModels.kt`：GitBinding / GitRepository / GitWorktree | binding 有 identity/host/generation/access；repository 有 root/commonDir/gitDir；worktree 有 path/HEAD/branch/bare/lock/prunable | 无 managed owner、pins、primary/shared/session protection 字段 |
| 同文件：GitWorktreeCreator / GitCreateWorktreeReceipt | optional capability；receipt 含 repository/request/startOid/worktree/worktrees | request 没有持久 operation ID；receipt 不是持久登记 |
| `apps/jvm-app/src/im/hikaru/harness/desktop/git/DesktopGitBackend.kt`：createWorktree | 校验 scope/path/ref、复验 repository、原子预留目的目录、真实 `worktree add`，再扫描并核验 path/ref/OID 才返回 Created | 无 managed store 或登记；`local()` 只检 access + hostId，不验证 member identity |
| `apps/shared/src/im/hikaru/harness/client/git/GitController.kt` | create/checkout 共用 mutation admission；核验回执；scope fencing；仅当前 scope 发布 Created 和选择新 cwd | 状态仅在内存；导航 fence 可能不发布回执，不能依赖页面回调登记 |
| `apps/shared/src/im/hikaru/harness/client/projects/LocalProjectModels.kt` | LocalProjectOwner 只有 hostId；generation 属于 scope | 不是 identity + host 的 managed ownership key |
| `apps/jvm-app/src/im/hikaru/harness/desktop/projects/DesktopLocalProjectRepository.kt` | 验证 LocalConnection、host 和连接；attachSession 读取真实 session.list、核对 summary/canonical cwd，再 CAS membership | membership 只证明已登记关系，不代表所有会话、历史或 pins |
| `apps/jvm-app/src/im/hikaru/harness/desktop/projects/DesktopLocalProjectStore.kt` | `local-projects.json`；schema 1；OS lock、exact bytes CAS、owner revision、atomic rename 与 fsync；坏文件不假空 | 其 owner/schema 不适合作为无需迁移的 managed 登记 |
| `modules/session-api/src/im/hikaru/harness/session/api/SessionApi.kt`：DefaultSessionApi.list/history | list 为 `context.agents.list()` 的 live agents；history 要求 live agent；已有 descendants/readChildHistory 等真实能力 | 无 durable catalog、pins、archive/worktree protection 端点；idle 不等于永久 Done |
| `modules/api/gateway/src/im/hikaru/harness/api/gateway/session/SessionApiGateway.kt` | session.list 等 typed endpoint 委托 SessionApi | gateway 没有额外持久 catalog 查询 |
| `apps/jvm-app/src/im/hikaru/harness/desktop/connection/LocalConnection.kt` | 本地通过真实 gateway 调用，不需要网络 | 不能补出不存在的 lifecycle/pin 数据 |
| `modules/session-persistence/src/im/hikaru/harness/session/persistence/RoomSessionPersistence.kt` | Room schema 1；header 有 id/cwd/parent/origin 等；flush transaction 写 header/events | 无 host/identity/pin/archive；DAO 无全量 header catalog；load 可能 repair open turn 并 flush，不是只读目录 API |
| `apps/jvm-app/src/main.kt` 与 `apps/shared/src/im/hikaru/harness/client/app/HarnessApp.kt` | desktop 注入 LocalConnection、DesktopGitBackend、DesktopLocalProjectRepository | 尚无 managed repository 注入 |

启动与路径依据：`modules/bundle/desktop/src/im/hikaru/harness/bundle/desktop/DesktopHarnessHome.kt` 按显式 home → `HARNESS_HOME` → `user.home/.harness`；DesktopBundle 安装 session persistence 到 `harness-sessions.db`，host description 使用固定 `HostId("desktop-local")`。这不是机器 UUID，不能凭相同字符串把外部 host/home 数据认作本机归属。

## 4. 保存位置与归属键

### 4.1 候选取舍

| 候选 | 取舍 |
| --- | --- |
| 当前 Harness home 下独立 `managed-worktrees.json` | **推荐 M1**。单独 schema、owner partitions、OS lock 与 exact bytes CAS；与 local-projects 的既有文件格式解耦 |
| 扩 `local-projects.json` | 不建议首批采用：现有 strict schema、host-only owner 和 project membership 语义需要迁移，扩大范围 |
| 在 sessions Room 新增 managed 表 | 不建议 M1：引入 DB schema/migration 与 session 生命周期耦合；仅为了登记没有必要 |
| repository config / Git commonDir sidecar / 路径前缀 | 外部可修改、跨 identity 可见，不能单独作为归属权威；未来可辅助诊断，不自动授予 Managed |

`managed-worktrees.json` 位于应用 home，避免为登记写用户 repository 的 `.git`。本批没有创建该文件，也没有访问真实 home。文件不存在只证明“该登记库暂无登记”，不证明“Git 没有 worktrees”或“没有保护数据”。坏 schema/JSON、权限失败、lock busy 或扫描错误都不能降级为可信空集。

### 4.2 持久 key 与运行 scope

建议 key：`Owner(identity variant, verified host) + canonical commonDir + canonical worktree root`。

- identity 使用完整 `MemberIdentity(target backend/tenant, userId, userType)`；不要使用 token、displayName。null 显式表示 LocalAnonymous，与登录 owner 分区，禁止自动迁移或互认。
- host 由当前验证过的本地连接/description 与 home namespace 限定；固定 desktop-local 不是跨机器证明。更强 installation ID 属于另一个设计批次，当前不能声称已存在。
- commonDir 标识共享 repository；root 标识具体 worktree。source root、gitDir 作为验证 fingerprint 和创建证据，不能按 source root 把同 commonDir 的 repo 分裂。
- canonicalization 用真实路径/组件，拒绝路径歧义、缺失或不匹配；symlink 解析失败保持 Unknown。不可用字符串 prefix 判断归属或关联。
- generation、connection instance 与页面 epoch 用于运行 fencing，不写成永久 owner。导航回来不能继承旧 scope 的 receipt/路径。

建议 record 保存稳定 record ID、operation ID、owner、canonical root/commonDir/gitDir、source root、创建时间、start OID、经核验的 creation receipt 摘要和登记状态。创建 branch/HEAD 是当时证据，展示当前 branch/HEAD 必须重新读 Git，不能永久使用创建值。

## 5. M1：真实登记、inventory 与保护观察

### 5.1 接线位置与失败语义

在 host/app 生命周期的 `DesktopManagedGitBackend` decorator 中登记，委托真实 GitBackend/creator/branch switcher，保留上一批 checkout capability 与行为；使用捕获并验证的 owner，调用真实 DesktopGitBackend creator，返回给页面前完成 receipt 核验与 CAS 登记。这样 accepted mutation 即使导航也会完成原 owner 的登记，页面仍按原 scope fence，不泄露旧 receipt。不能把登记挂在 GitPages 收到 Created 或 controller 当前页面状态的回调上。

登记单独建模，不把现有 Git Created 改为虚假失败或虚假回滚：

| 事实 | 登记状态 / 行为 |
| --- | --- |
| 真实 Git Created、receipt 与请求/owner/repository 重验一致、CAS 已提交 | Managed / Registered，提供持久登记 evidence |
| Git Created，但登记冲突、权限/I/O 失败 | CreatedRegistrationFailed：保留实际路径与原因；不自动重发 Git、不删除，不标 Managed |
| Git 启动后无法确认结果或取消 | UnknownOutcome / needsRescan；不生成 ownership receipt，不重发创建 |
| 仅有 Prepared intent，或 crash 中断 | Pending/UnknownOwnership；intent 不是归属证明 |
| Git worktree 存在，但未登记或是旧创建 | UnknownOwnership，允许普通只读观察；路径前缀、branch 名、ctime 不触发自动收养 |
| 登记存在但路径/Git fingerprint 不匹配或缺失 | Missing/Mismatch/Unknown，保留记录与诊断；不凭路径存在就恢复 Managed |

preflight 检查登记服务可用，避免在确定无法登记时开始创建。为缩小 crash gap，M1 应有持久 operation intent（Prepared → GitVerified → Registered，另有 UnknownOutcome），在 Git dispatch 前落盘；只确认落盘不等于 Managed。重启后可重读精确请求/目标/ref/OID 与持久 verified evidence；仅 Git list 匹配不足以证明创建者，不能自动升级 Prepared。CAS 成功后 directory fsync 失败须单独报告 durability warning，不能声称未提交或回滚。

store 复用已有实现的原则而不扩其 schema：OS exclusive lease，读 token 绑定 source/owner/revision/**真实完整 bytes**，提交前复验 bytes，保留其他 owner 数据，temp fsync + atomic rename + directory fsync。冲突不覆盖外部编辑；lease 只保护本登记库，不承诺和外部 Git 命令之间的原子事务。

### 5.2 保护状态不能压成 boolean

每维使用 `Known(value, evidence, observedAt, scope)` / `Unknown` / `Unavailable` / `Failed` / `Stale`。区分“确认有保护”“完整读取后确认没有”和“未能读取”。仅业务证据定义的完整读取才可 `Known(empty)`。Managed 与 Deletable 是两个不同判断。

| 保护维度 | M1 可真实提供 | 剩余边界 |
| --- | --- | --- |
| 归属 | 本登记库及 creation evidence + 当前 fingerprint 的核验结果 | 旧/外部 worktrees 未知，不自动导入 |
| path/branch/HEAD/bare/locked/prunable | 真实 Git inventory，带 freshness/scope | prunable 不是授权清理；locked 为阻断证据 |
| primary/main worktree | 有可靠 Git metadata 时可识别；需专门校验 | 不用 list 第一行或 path 名猜测；未提供 reader 时 Unknown |
| tracked/index/untracked/conflicts | 当前 worktree 的真实 status observer | clean 不证明 ignored 文件、nested repo 或 submodule 安全 |
| linked sessions | 首批显示 Unknown，原因是完整持久会话读取不可用 | live list 与 memberships 只是 subset；本切片不新增 consumer，不给“无关联会话”结论 |
| active sessions / sharing / attaching | 仅可呈现真实已观察的运行或关联证据 | 缺 lifecycle、归属、跨进程/共享完整性时 Unknown |
| pins | 服务缺失即 Unavailable | 不显示 no pins，不据此开放 delete |
| archive | 尚无真实持久 lifecycle endpoint | Unavailable；idle/cancel/Runtime close 不等于 archive |
| newborn / pending creation | 持久 intent 可作为本应用未完成操作保护 | 不据目录时间猜测其他创建者 |
| nested repositories / submodules / ignored content | M1 未提供完整专门 reader 时 Unknown | dirty=false 不能合并为可删除 |

过期或页面切换后的读结果不得覆盖当前 owner/scope；导航期间 accepted 登记不取消。刷新失败保留已有观察并标 Stale/Failed，不把失败结果置为 empty 或清除保护。

## 6. 后续依赖（不进入首批写集）

已确认事实：RoomSessionPersistence 仅按 ID load，没有持久 header 全量目录；load 可能 repair/flush，不能当只读 catalog。LocalProjectMembership 仅是已登记成员；普通 Git list、live session list 都不能证明 absence。首批展示 Unknown，不新增虚假的 empty endpoint。

后续若补会话保护，优先既有 persistence owner 的只读 header catalog/lookup，不恢复 Agent、不修写事件、不另开同 DB 的 Room。既有 header 缺 identity/host，仍需 ScopeUnknown；完整性、flush freshness 和 canonical cwd 关联需另立契约与测试。此处不授具体端点/文件写集。

Pin 需真实 owner-scoped 持久 metadata、完整读与实际 pin 写入/重启证据；缺服务、未初始化/迁移或读取失败不能 Known(empty)。Archive 需真实持久 lifecycle 与 receipt，idle/cancel/Runtime close 不是 archive。两者仅记录依赖，不在本批深挖。

用户排除应用 snapshot：不添加 snapshot 服务，也不把它列为后续删除的必做依赖。删除还缺完整 ownership、pins、持久会话、运行/共享、primary、nested/内容保护，以及 plan 复查和结果确认；仍必须 Unavailable，不能从本分析推导执行许可。

## 7. 按钮与状态的数据来源

当前 WorktreesPage 的读取此 worktree/创建/刷新是既有 Git UI，不是 W14 的 managed 面板。以下是未来 managed UI 的接线边界，不能把建议写成已实现。

| 按钮或状态 | 真实来源 | 最小可用行为 / 不可用原因 |
| --- | --- | --- |
| Host / owner selector | 验证过的 local connection + 明确 identity variant | scope 改变 fence reads；foreign/remote 不承认本机 ownership |
| Refresh | 登记库 + Git inventory + 各 protection reader | 分源状态；某源失败不能假装全量 refresh 成功 |
| Managed 分组/数量 | 登记 records 与 Git fingerprint reconcile | 普通 Git 列表另列 UnknownOwnership；不把所有条目算 managed |
| Path / branch / HEAD | 当前真实 Git read | register receipt 只作创建证据；缺失/changed 标明 |
| Ownership / registration warning | 持久 key、receipt/intent、CAS 结果 | Registered、Pending、RegistrationFailed、UnknownOwnership 分开 |
| Linked conversations | 首批 Unknown；未来需完整 durable catalog | live/membership subset 不是 absence proof，不展示“0 个关联会话” |
| Loading / empty / error | 每源读取状态与完整性 | empty 仅在对应完整读取成功后展示；总保护未知仍保留 |
| Pin hint / no pins | 真实 pin reader | 缺服务显示不可用；不能用 live sessions/pinned UI 缺项推 no pins |
| 读取此 worktree / 打开路径 | 当前绑定的已验证 canonical root | 只读导航不改变 owner；不能自动收养未登记工作树 |
| New chat | 真实 session.create + root/owner 验证 + membership 登记 | 当前 managed 面板尚未接线；不能伪造会话或复制 history，需另授写集 |
| Archive | 真实 archive lifecycle capability | 当前 Unavailable |
| Delete / cleanup / force / prune | 完整保护与 mutation plan、复查、结果确认 | 首批及依赖未补齐时均 Unavailable；不提供 fallback shell 删除 |

## 8. 首批限定写集建议（需单独授权）

以下仅建议下一轮 M1，当前许可仍只有本文档。首批范围是登记、只读 inventory/归属 reconcile、保护未知展示；不新增 SessionApi/Room/pin/archive/module 变更。

| 类型 | 精确候选路径 | 职责 |
| --- | --- | --- |
| 新增 | `apps/shared/src/im/hikaru/harness/client/git/ManagedWorktreeModels.kt` | owner/key、registration intent/evidence、分维度保护观察、repository 接口 |
| 新增 | `apps/shared/src/im/hikaru/harness/client/git/ManagedWorktreeController.kt` | 只读 refresh/state/fencing；缺保护源明确 Unknown/Unavailable |
| 新增 | `apps/jvm-app/src/im/hikaru/harness/desktop/git/DesktopManagedWorktreeStore.kt` | 独立 JSON、OS lease、真实 bytes CAS、atomic/fsync |
| 新增 | `apps/jvm-app/src/im/hikaru/harness/desktop/git/DesktopManagedWorktreeRepository.kt` | owner/local host 校验、登记与真实 Git inventory reconcile |
| 新增 | `apps/jvm-app/src/im/hikaru/harness/desktop/git/DesktopManagedGitBackend.kt` | 委托现有能力、创建前 intent/创建后 verified 登记；不改变 checkout receipt |
| 修改 | `apps/jvm-app/src/main.kt` | 当前 home/verified local connection 注入 store/repository/decorator |
| 修改 | `apps/shared/src/im/hikaru/harness/client/app/HarnessApp.kt` | optional managed repository 注入，无服务时明确 unavailable |
| 修改 | `apps/shared/src/im/hikaru/harness/client/git/GitPages.kt` | managed/unknown ownership 与保护未知只读卡片、刷新、登记失败提示 |
| 新增测试 | `apps/jvm-app/test/im/hikaru/harness/desktop/git/DesktopManagedWorktreeStoreTest.kt` | CAS/lease/损坏/owner/restart |
| 新增测试 | `apps/jvm-app/test/im/hikaru/harness/desktop/git/DesktopManagedWorktreeIntegrationTest.kt` | 真实 tmp repo/creator/登记/失败/导航无丢记录；delegation 保留 checkout |
| 新增测试 | `apps/shared/test/im/hikaru/harness/client/git/ManagedWorktreeControllerTest.kt` | scope、refresh 失败/stale、Unknown 不变 empty |
| 新增测试 | `apps/jvm-app/test/im/hikaru/harness/desktop/ManagedWorktreeInteractionTest.kt` | 只读卡片/刷新、保护不可用、无 destructive consumer |

合计建议 12 个文件（9 新增、3 修改）。`GitPages.kt` 与父线程上一批有重叠，须明确重新授予后才改。首批不修改 GitModels/GitController/DesktopGitBackend，登记结果经 managed repository 观察；保留真实 Git Created 语义及现有 mutation admission。若 decorator 无法通过既有 capability seam 保持兼容，应先报告具体阻碍，不默默扩大写集。

存储 intent 是 crash gap 诊断，不授予未验证 worktree 归属；只读 refresh 不自动收养旧记录/工作树，不补做创建，不删除记录对应的文件。后续 session/pin/archive 写集另行分析授权。

## 9. 有意义的验证矩阵

本批未执行以下验证。未来实施由父线程隔离串行运行，真实数据使用临时 home/repo/DB，不改用户 home；finally 清理只是资源收尾，不作为业务完成证据。

| 批次 / 边界 | 必须验证的事实 |
| --- | --- |
| M1 真实 create → register → restart | 真实临时 Git repo/worktree、真实 creator receipt；落盘 key/receipt 与实际 commonDir/root/ref/OID 匹配；关闭 owner 后 fresh store reader 仍 Registered；原 workspace bytes 保留 |
| 登记 CAS / OS lease | 外部真实文件 bytes 编辑导致冲突、不覆盖编辑；其他 owner 不丢；lock contender 拒绝；坏 schema/JSON/权限不假空 |
| Git 成功、登记失败 | Git worktree 实际存在且文件保留；RegistrationFailed；无再次 Git dispatch/自动删除/虚假 Managed |
| crash / pending intent | Prepared 无 ownership receipt；Git dispatch 后未知不重发；重启不凭 Git list/路径匹配自动收养；只真实持久 verified evidence 可参与 reconcile |
| scope / identity / host / aliases | anonymous 与 member、tenant/userType、foreign host 隔离；symlink/canonical commonDir/root 不误归属；generation 不制造新 owner |
| accepted mutation 导航 | 原捕获 owner 得到真实登记；新 scope 不接收旧 receipt/路径；同 repo 当前观察适时真实刷新而不沿用旧 read |
| 普通与旧 worktrees | 普通 inventory 非空、managed registry 为空时仍 UnknownOwnership；existing created worktree 不因 prefix/branch 名变 Managed |
| protection freshness | Known(protected)、Unknown、Unavailable、Failed、Stale 分开；刷新失败不清保护；clean/managed 不开放 delete |
| session/pin/archive 缺服务 | 明确 Unknown/Unavailable，绝不 Known(empty)/no pins/archived；不新增假端点，也不读取 Room load 作 absence proof |
| UI 状态与操作 | managed/unknown ownership、subset/empty/loading/error/unavailable 可区分；read/refresh 有真实 consumer；delete/cleanup/archive 无可调用副作用 |

## 10. 本批交付与限制

唯一新增文件为本文档。源码、既有 dirty、branch checkout 写集均保留；没有启动构建或运行测试，没有删除实现，没有应用 snapshot 服务扩展。

最小先行结论为 M1 的真实登记 inventory、只读归属核验与显式保护未知展示；持久会话/pin/archive/完整 destructive safety 仅记录后续依赖。下一轮建议授第 8 节 12 文件，不授删除能力。

## 11. M1 实际交付与验收（2026-10-08）

实施使用用户指定的 SHUAIAPI/gpt-6.1-sol 代理；父线程完成 main/HarnessApp 接线、审查、页面断言修正及隔离串行验收。实际写集为第 8 节列出的 12 个文件；未修改 GitModels/GitController/DesktopGitBackend 或构建配置。

| 交付 | 实际行为 |
| --- | --- |
| 独立登记库 | 当前 Harness home 的 managed-worktrees.json，schema 1；完整 identity（含 anonymous）、Host、home namespace 分区；OS 文件锁、完整原始字节 CAS、原子替换、文件 fsync 与目录持久性告警 |
| 创建与登记 | 先持久 Prepared，持久性未确认、owner/仓库/CAS 不符时不调用 Git；真实 Created 后核对目标 Git 指纹，依次存 Verified/Registered；登记失败保留真实工作树与 Created 回执，不自动重发/删除 |
| 重启与归属 | 只认持久 Verified/Registered evidence，并重读 canonical commonDir/root/gitDir；普通/旧工作树仍 UnknownOwnership，指纹不符为 FingerprintMismatch，缺失目标为 Missing；当前 HEAD/branch 从真实 Git 读取 |
| 桌面接线 | 仅 verified 当前 LocalConnection 的 owner 使用登记服务；缺 owner validator 时同步禁用创建 capability；decorator 保持已有读取和 checkout 委托 |
| 页面 | Managed/Unknown/Missing、登记告警、Loading/Error/Stale/Unavailable、真实刷新与重试；owner 切换首帧过滤旧 inventory，迟到 reads 不发布；accepted 登记仍写入捕获的 owner |
| 保护状态 | 关联会话/共享/内容保护 Unknown，pin/archive Unavailable；没有 Known(empty) 或可删除推断；未新增删除、自动清理、force/prune/归档 consumer |

存储的 OS lease 只协调遵循锁协议的读写者；完整 bytes CAS 检测已观察的外部编辑，不承诺将任意不遵守锁的进程与 Git dispatch 原子串联。目录 fsync 告警不是“提交失败”：rename 后真实记录仍保留，Prepared 告警阻止 Git 调用，Registered 告警保留 Created 和已提交记录。

验证只在隔离副本与测试自己创建的临时 Git 仓库/home；受控 seams 仅暂停 IO 或注入明确失败。真实创建、当前 HEAD、索引/未暂存/未跟踪内容保留、创建后取消、登记冲突与 owner 导航均有生产服务证据。全套最终数量和独立 XML 见下表；不叠加初轮失败或重跑次数。

| 定向组 | 最终结果 | 独立证据目录（以下位于 /tmp/harness-native-verification-evidence-20261007） |
| --- | --- | --- |
| Shared Git + managed controller | 33/33 | managed-shared |
| Desktop Git、真实进程与 managed store/integration | 52/52 | managed-desktop-git-final/jvm |
| ManagedWorktreeInteractionTest | 3/3 | managed-ui-final/jvm |
| WorktreeCreationInteractionTest 回归 | 2/2 | managed-worktree-ui-regression/jvm |
| GitBranchCheckoutInteractionTest 回归 | 2/2 | managed-checkout-ui-regression/jvm |

最终 **92/92**（33+52+3+2+2），无失败、错误或跳过。所有最终 XML 均已按组保存；Desktop 最后补存报告的52项包含已有Git回归与13项managed存储/组合，不与上一轮52项重复相加。

命令在 /tmp/harness-native-verification-20261007 内执行，build-dir=/tmp/harness-native-verification-build-20261007：

```text
./kotlin test --build-dir=/tmp/harness-native-verification-build-20261007 -p jvm -m shared --include-classes='im.hikaru.harness.client.git.*'
./kotlin test --build-dir=/tmp/harness-native-verification-build-20261007 -p jvm -m jvm-app --include-classes='im.hikaru.harness.desktop.git.*'
./kotlin test --build-dir=/tmp/harness-native-verification-build-20261007 -p jvm -m jvm-app --include-classes=im.hikaru.harness.desktop.ManagedWorktreeInteractionTest
./kotlin test --build-dir=/tmp/harness-native-verification-build-20261007 -p jvm -m jvm-app --include-classes=im.hikaru.harness.desktop.WorktreeCreationInteractionTest
./kotlin test --build-dir=/tmp/harness-native-verification-build-20261007 -p jvm -m jvm-app --include-classes=im.hikaru.harness.desktop.GitBranchCheckoutInteractionTest
```

12 文件基线 hash 保存于 /tmp/harness-managed-verification-20261007/manifest.json；验收时全部与隔离副本逐字节一致。页面初轮 substring 断言修正前的失败保留在 managed-ui-initial-failure，不计为通过；修正后 3/3。创建回归首次因 Amper 遥测缓存写权限被阻止，获准后执行通过；不计为源代码测试失败。报告复制按组完成后再启动下一组，避免 build reports 覆写最终证据。

本批未证明真实进程 crash/recovery、完整持久会话枚举或 pin/archive、跨机器/远端服务、全量 desktop/shared、Android/iOS、原生窗口或像素一致。M1 完成不表示 Worktree 管理所有动作完成。下一个 Git commit 契约已独立分析，见 git-commit-implementation-contract-2026-10-07.md；C1限定写集已交同一代理实施，尚未验收，不影响本节 M1 基线。git diff --check 通过；12文件hash标识本批验收时点，后续C1需另建基线。
