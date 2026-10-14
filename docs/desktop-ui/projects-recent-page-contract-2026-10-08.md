# Projects / Recent 页面实施契约

日期：2026-10-08。状态：主线程已核对当前生产路由和已有服务，待 SHUAIAPI/gpt-6.1-sol 实施及主线程验收。仅覆盖确认范围内的本机 Projects、Project 详情、Home 项目 picker 和当前 Host Recent。

## 现有能力与具体缺口

| 区域 | 当前事实 | 本批目标 |
| --- | --- | --- |
| Projects | 生产路由使用 LocalProjectsPage；controller/store/repository 已提供真实登记、目录验证、CAS 和 owner/binding fence | 清晰区分 Unbound/Loading/Empty/Ready/Error/Unavailable/Unsupported/AccessDenied；成功读取后搜索 name/root；长名称、路径及大列表滚动 |
| Project | 已有真实 root、membership、开始会话和目录绑定 | 紧凑窗口按钮换行；root 完整可读；区分没有 membership 与有 membership 但没有 live session；后者不可伪装为可恢复历史 |
| Home picker | 选择真实项目，prepare root 后传 CreateSessionOptions(cwd)，关联失败不重复 create | 长名称受限宽度菜单、菜单滚动、点击关闭、真实 selection；binding 变化清理弹层和旧选项；保留创建与关联 fence |
| Recent | HarnessApp 把 SessionSummary 映射为 id/cwd basename，目前 RecentTasksPage 不接 listPhase、刷新或错误，且无页面滚动/搜索 | 接当前绑定的真实列表状态、显式刷新、搜索、大列表滚动和点击 Thread；不能把加载失败显示为成功空列表 |

SessionSummary 仅有 id、createdAt、cwd、agentPreset；没有 title、updatedAt、unread、运行状态、pin、archive。Recent 可按真实 createdAt 降序并以 id 打破平局，产品文案明确按创建时间；不伪造最近更新时间、未读或运行状态。搜索用现有 id/cwd。共享壳层 RecentTaskItem 的既有预览不能成为生产数据源。

生产 Recent 投影应和 liveProjectSessions 一样检查当前 conversationHost 与 connected；检查 bind effect 尚未执行的帧，防止导航或账号/Host 切换短暂显示旧数据。刷新错误可保留同 scope 上次成功列表并明确过期状态；断开连接的列表不提供正常打开动作。

## 组件与布局

- 复用现有主题、Button、Search、Row、Card、Menu、Dialog；仅必要的页面私有控件可新增。
- 页面统一标题、说明、操作、状态和内容区。普通与紧凑窗口均能滚动，按钮和弹层不越出可见区域。
- 路径和 ID 可复制且完整可访问，折行或独立横向滚动；不以单纯截断文字掩盖信息。
- Editor 保留 typed requestId/pending/retry 确认语义，busy 禁用重复写入；关闭不抹去未知结果请求。
- 产品文案使用“登记已有目录”“未载入”“读取失败”等真实能力描述，避免实现层说明。

## 写集与边界

实现允许修改 LocalProjectsPage.kt、新增 Recent 页面文件和页面私有组件；HarnessApp.kt 仅最小路由参数及安全投影接线，并在 Git 批次释放其写集后进行。专用 desktop interaction/screenshot/support tests 可以新增。

不重写 LocalProjectsController/store/repository、ConversationController 或 session API；发现服务问题先报告主线程。不添加删除、置顶、归档、历史恢复、云项目、团队、权限管理或新 endpoint。不修改共享基础组件、Git/Worktrees/Thread/plugins/Cron 的生产逻辑。保留 dirty/untracked 工作，不提交、不清理。

## 验收

1. 真实 mouse pointer 完整 HarnessApp 路由：Projects 新建→详情→开始会话→Home picker→实际 cwd/session membership→Recent→Thread，复用现有真实 adapter 或明确限定的测试服务。
2. Ready/Empty/Loading/Failed/Unavailable 和搜索无结果可区分；Host/binding 切换不会泄露旧数据或消费旧弹层 callback。
3. 长 name/root/id、20+条数据、紧凑窗口按钮、列表和菜单滚动，pointer 点击命中真实动作。
4. 浅深主题，1440×900 与600×800截图及 state/theme/size/tag bounds geometry；截图 fixtures 仅在 test。
5. 代理不运行构建测试。完成后报告写集并明确释放；主线程串行执行新增测试及必要既有 LocalProjects 回归，记录 XML 数量和截图 hash。

截图证明本批布局和交互，不证明原版 pixel parity、原生窗口、远端链路或重启后完整会话恢复。
