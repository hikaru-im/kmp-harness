# 批次 AF 证据：本地插件 inline Apps 消费与清单 schema 迁移（生产改动）

- 日期：2026-10-11
- 写集：
  - modules/bundle/desktop/src/im/hikaru/harness/bundle/desktop/DesktopUserPlugins.kt：DesktopUserPlugin 增加受限的 applications 名称列表；导入 .codex-plugin/plugin.json 中的 apps 数组或对象键；不保存配置值；清单写入 schema 2；schema 1 读取时迁移为空列表；应用包目录与 .app.json 继续列为未支持资源。
  - apps/jvm-app/src/im/hikaru/harness/desktop/plugins/DesktopInstalledPluginRepository.kt：详情页从本地快照消费 Apps 名称；无 Apps 时显示服务确认的空列表；文件形 Apps 仍显示不可用原因。
  - 测试同步更新并新增 schema 1 → schema 2 迁移覆盖。
- 目录：docs/desktop-ui/verification/plugin-apps-inline-2026-10-11/entry-af1/

## 问题

插件详情页已经有 Apps 区域，但本地服务始终返回「Apps 尚未接入」，即使 manifest 已声明 Apps；导入器也把 manifest 的 apps 字段当作未支持能力，导致实际可安全展示的名称被丢弃。用户要求按钮和页面使用真实数据，本批先把低风险、可枚举的 inline Apps 接通。

## 行为

- apps: ["fixture-app"] → 详情 PluginAppsState.Available(["fixture-app"])，页面渲染 Apps 行。
- apps: {"name": ...} → 只保存对象键（例如 name），不保存 value。
- 没有 apps 声明 → Available(emptyList())，页面显示「服务确认此插件没有 Apps」。
- apps/ 目录、.app.json 或无法枚举的 manifest 形态 → 保留 apps (unsupported)，页面说明该资源尚未读取。
- schema 1 清单没有 applications 字段，读取时按空列表迁移；下一次写操作生成 schema 2 并写入 applications: []。
- Apps 名称受 64 项、单名 256 字节、非空无控制字符限制；配置正文、脚本和 MCP 内容不复制、不执行。

## 验收

- DesktopUserPluginsTest：22/22。
- DesktopInstalledPluginRepositoryTest：11/11。
- PluginManagementInteractionTest：3/3。
- PluginsPageInteractionTest：9/9。
- PluginLocalUpdateInteractionTest：10/10。
- PluginsPageScreenshotTest：4/4。
- PluginsScreenshotTest：15/15。
- 全量 jvm-app：647 found / 5 skipped / 642 started / 641 passed / 1 failed，exit 1；唯一失败仍是 PtyTerminalFontInteractionTest.savedFontChangeRefitsSameRunningPtyAtFixedBounds 在 30s 真实 PTY 就绪门超时。
- 本批结束后的同类隔离重跑为 2/3 passed / 1 failed（两次连续重跑均复现该超时），证明当前环境的 PTY 门仍有抖动；该测试不读取插件代码，失败发生在本批生产改动覆盖范围之外。插件 focused suites 均 exit 0；截图测试继续覆盖浅/深色和 1440×900 / 600×800 页面矩阵。

## 边界

本批消费 manifest 中可安全枚举的 inline Apps 名称。MCP server 配置、hooks、scripts、文件型 app 包和授权/连接生命周期仍保留为独立适配器工作；这些能力包含配置正文、外部连接或执行语义，不能由名称列表推断。市场发现、云端项目和 Codex 云端环境仍按既定范围排除。本批是行为验收，没有原版同状态像素一致性声明。
