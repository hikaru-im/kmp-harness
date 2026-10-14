# 插件管理页面收口契约

日期：2026-10-08。状态：分析完成，待页面实施与主线程验证。指定实施模型：`SHUAIAPI/gpt-6.1-sol`。

## 范围与依据

- 保留本机已登记插件列表、搜索/启用筛选、详情、登记目录、启停与移除登记；公开市场未纳入。
- 原版证据见 `reverse-engineering/plugins-layout-supplement-2026-10-07.md` 及同名 evidence JSON：管理列表单列、顶部操作换行、搜索受限宽度、Skills/Apps 行展示。市场截图不能用于管理页双列依据；当前无同尺寸真实管理页截图。
- 当前生产 `InstalledPluginsPage`、`InstalledPluginDetailPage` 由 `HarnessApp` 绑定 `InstalledPluginsController`；不是壳层 fixture。已有本机 manifest/Skills 文本快照登记和下一次 Agent 请求消费，添加默认停用。
- 完整包安装/升级/卸载、Apps/MCP/hooks/scripts/技能附件和授权配置未实现，页面不能把“移除登记”写成卸载，也不能添加假入口。

## 当前页面差异与本批目标

1. 列表仍采用大块纵向卡片，标题、刷新、添加、搜索和筛选散落。改为统一且可换行的标题/操作区，单列紧凑插件行；搜索保持可见、受限宽度，紧凑窗口可独占一行。
2. 详情返回、刷新与启停/移除目前纵排。按可用宽度换行，保持主要动作可达，名称和能力说明靠近内容。
3. Skills/Apps 使用紧凑行而非大段卡片。单行简介可以截断，但必须有明确的全文读取方式；名称、源路径与完整说明可选择/复制或独立滚动，不能因长值撑宽。
4. 登记/移除弹窗保留真实状态、busy 禁用、失败后的内容和显式刷新。移除说明源目录保留，删除配置不冒充删除源文件。
5. NotLoaded/Loading/Error/Unavailable/AuthRequired/UnsupportedHost/真实Empty/筛选无结果分别呈现，不把未读取或失败写成无插件。保存receipt与后续刷新失败并列保留。
6. 保持现有controller与HarnessApp scope/连接/身份/generation防护；不通过局部缓存展示旧Host列表或保留旧弹窗动作。

## 写集

- `apps/shared/src/im/hikaru/harness/client/plugins/InstalledPluginsPage.kt` 与同目录新增页面私有组件。
- 新增 desktop `PluginsPage*InteractionTest`、`PluginsPage*ScreenshotTest`、`PluginsPage*TestSupport`；必要时最小更新原有插件UI断言。
- 不改controller、repository、服务层、main、连接、共享foundation或其他页面。需要HarnessApp展示参数时输出 `/tmp` patch 由主线程接线。
- 不构建、不测试、不调用构建工具help；主线程按固定源码串行验证。保留已有dirty/untracked，不提交或清理。

## 验收

- 完整HarnessApp路由真实mouse：筛选/搜索→详情→返回；本机临时目录登记默认停用→启用→移除确认取消/确认；无服务和只读连接动作禁用。
- 更换Host/断开隐藏旧数据和弹窗；读取失败与保存成功刷新失败分开；列表20+条及长name/path/description可读并能滚动。
- 普通1440×900、紧凑600×800，浅深主题，列表/详情/空/加载/失败/不可用/只读/弹窗与长值。每张PNG对应state/theme/size/关键tag bounds geometry，菜单/弹窗说明popup或page viewport坐标。
- 主线程串行新增测试与PluginRoutingInteractionTest、PluginManagementInteractionTest、必要controller回归；独立保存XML、PNG/geometry和hash manifest，目视代表图。既有64张截图是此前批次证据，不能作为本批新源码验证。
- 无原版管理页同尺寸参考，声明结构/可读性/交互验证，不声明像素一致或完整插件生命周期完成。
