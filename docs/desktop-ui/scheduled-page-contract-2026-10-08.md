# 定时任务页面实施契约

日期：2026-10-08。状态：分析完成，页面批次待实施/验证。实施模型：`SHUAIAPI/gpt-6.1-sol`。

## 1. 当前证据与范围

- 原版浅色未选中页面：`reference/captures/2026-10-06-user-pages/scheduled-tasks.png`，2560×1440，包含菜单栏、窗口条和 Dock。比对时按应用内容区域裁切，不能把整屏尺寸当应用 viewport。
- 原版行为与表单代码：`reverse-engineering/scheduled-implementation-contract-2026-10-07.md` 及同名 evidence JSON。其中“无截图”和第 8 节实施前缺口是历史记录；当前事实以本契约和 `scheduled-cron-readiness-2026-10-07.md` 为准。
- 当前生产路由使用 `scheduling/ScheduledRepository`、`ScheduledManagementNavigator` 和 `ScheduledManagementPage`。`scheduled/ScheduledTasksRepository` 与 `AppShell` 的旧 fixture navigator 不能当作生产任务源。
- 已有配置 CRUD、持久化、暂停/运行能力门控、持久运行记录与真实 SessionId 导航。本批改善页面结构及实际路由交互，不扩展 executor 能力。
- 生产 `verifiedCronExecution=false`，Heartbeat unavailable。保存暂停配置不表示会自动执行；仅在真实 capability 允许时开放启用/立即运行。
- 云任务、团队、语音、宠物、电脑/浏览器操控等用户排除项不进入本批。

## 2. 原版布局与适配

| 区域 | 已证实的结构 | 本批实施 |
| --- | --- | --- |
| 左侧 navigator | 标题与搜索图标同一行；新建任务入口；分隔线；任务区标题；任务行 | 独立固定 header，任务列表单独滚动；使用真实 name/status，不制造 unread/running/nextRun |
| 未选中主区 | 居中时钟图标、安排任务标题、说明，下面两列六张建议卡 | 内容限宽、垂直居中，紧凑时一列并可纵滚；静态建议只能标成草稿模板，不能伪造用户配置或连接服务 |
| 选中任务 | ASAR 有真实 detail/history/actions | 主区详情、操作和历史；不再重复 navigator 完整任务列表；保留现有能力门控 |
| 创建/编辑 | ASAR 有 name/prompt/target/model/frequency/destination 等字段 | 使用现有 typed draft/context，表单受宽度约束，保存与取消清楚可见；缺服务显示可理解的原因 |

原版截图中的六张卡是该用户环境的建议，不能把其邮件、财务、研究等内容灌入生产任务列表。若本批采用静态模板，应仅预填可编辑的名称/prompt/规则，点击进入 draft，不保存、不运行、不承诺邮件等未连接能力。可采用工作区总结等适用模板并明确其为示例。原版详情、编辑、深色和紧凑状态没有对应截图，完成后只能声明结构与行为验证。

## 3. 数据与门控

1. 页面只消费真实 repository。任务选择只改 UI selection，不能改变 Active/Paused。
2. Loading/NotLoaded、Unbound、Disconnected、Error、真实 Empty 和 Ready 分别呈现。未完成读取或失败不能显示“没有任务”；错误保留重读动作。
3. 单独展示配置可用性与执行可用性。原因靠近相关动作，不能用大量内部 executor 说明占据居中首页。Unknown 和 durability warning 保留清晰可见的证据。
4. 新建、模板、编辑、保存、暂停、运行、删除遵循 state.canMutate/canActivate/canRunNow 与 draft/mutation/connected 门控。不能为接近截图放开 disabled 能力。
5. target/model 只从当前 scope 的真实 context 选择。长 cwd/provider/model 完整可读、可选择/复制；anchor 与 popup 受 viewport 宽度限制，独立横滚或补充完整值，不横向撑开页面。
6. Host/identity/generation/repository 变化关闭旧弹层；旧 scope 回调必须拒绝。保留 HarnessApp 绑定首帧投影与 connected/context 校验。
7. 历史只使用已有 durable run ledger。Open 仅接受已确认 SessionId，并由当前 Host 导航 fence 校验；candidate ID 不能代替确认引用。Show older 若只是展开已有记录，说明其语义。
8. 删除仅调用现有 repository 显式确认流程。删除配置不代表取消运行，不清理会话和历史。

## 4. 实施写集与协作

指定模型可编辑：

- `apps/shared/src/im/hikaru/harness/client/scheduling/ScheduledManagementPage.kt`
- `apps/shared/src/im/hikaru/harness/client/scheduling/ScheduledManagementNavigator.kt`
- 同目录新增页面私有布局组件
- desktop 新增 `ScheduledPage*InteractionTest`、`ScheduledPage*ScreenshotTest`、`ScheduledPage*TestSupport`
- 如标签/布局变化使既有 management/routing UI 断言失效，最小更新对应测试，保留真实行为断言。

HarnessApp 如需增加页面展示参数或完整 route 适配，提供独立 `/tmp` patch，由主线程接线；避免与当前 Git/Projects 写集冲突。不改 repository、engine、executor、store、main 开关、connection 或 foundation。实现前先用 CodeGraph 定位；不得构建、测试或调用构建工具的 help，由主线程串行验证。

## 5. 验收与证据

- 真实 mouse 主路径：navigator 搜索/选择/新建，draft 修改/取消/保存，详情编辑，暂停/运行 disabled，删除确认取消，已确认历史导航。至少一项完整 HarnessApp route 测试，不能仅孤立页面。
- scope 变化/断开时不显示旧任务、旧历史或提供正常打开动作；绑定首帧不暴露旧 scope。保存失败保留草稿与旧 effective state，不新增执行调用。
- 普通 1440×900 与紧凑 600×800、浅深主题；未选中/空/加载/失败/断开/详情/表单/保存/Unknown 历史/长字段/20+列表。
- 每张截图同步记录 state、theme、width/height、可见关键 testTag 的 bounds；滚动后截图必须描述滚动位置。fixture 限测试，不进入生产。
- 主线程串行跑新增页面测试及 `ScheduledManagementInteractionTest`、`ScheduledRoutingInteractionTest` 和必要 repository 回归；独立保存每轮最终 JUnit XML。
- 核对 PNG 尺寸、geometry、hash/manifest，目视检查代表图，并与原版浅色未选中内容区域比对。无截图的状态不声明像素一致。

页面批次通过不代表 Cron 生产启用、Heartbeat、跨进程 crash/recovery、DST/回拨/missed wakeup 已完成。
