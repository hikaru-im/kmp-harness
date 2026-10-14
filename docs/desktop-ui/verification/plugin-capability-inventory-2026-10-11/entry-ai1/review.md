# 批次 AI 证据：本地插件 MCP / Hooks / Scripts 名称清单（生产改动）

- 日期：2026-10-11
- 写集：
  - modules/bundle/desktop/src/im/hikaru/harness/bundle/desktop/DesktopUserPlugins.kt：从 manifest 的 mcpServers/mcp、hooks、scripts 对象或字符串数组读取名称；只保存名称；清单 schema 由 2 升为 3，schema 1/2 读取时迁移空能力列表。
  - apps/shared/src/im/hikaru/harness/client/plugins/PluginModels.kt：新增 PluginCapabilitySummary。
  - apps/jvm-app/src/im/hikaru/harness/desktop/plugins/DesktopInstalledPluginRepository.kt：详情读取消费能力名称。
  - apps/shared/src/im/hikaru/harness/client/plugins/InstalledPluginsPage.kt：详情页显示 MCP Servers、Hooks、Scripts 名称和数量，并明确配置/执行服务尚未接入。

## 行为

- `mcpServers: {"fixture-mcp": {...}}` → MCP Servers 区域显示 fixture-mcp。
- `hooks: {"before": {...}}`、`scripts: {"check": {...}}` → 对应区域显示名称。
- 配置 value、命令正文、凭据和脚本内容不会写入清单；文件型 `.mcp.json`、hooks 目录和 scripts 目录继续标记为未支持。
- 页面不把名称清单伪装成可连接或可执行服务。

## 验收

- DesktopUserPluginsTest：22/22。
- DesktopInstalledPluginRepositoryTest：11/11，真实本地 fixture 验证三种能力组。
- PluginManagementInteractionTest：3/3，验证详情页显示 MCP Servers 和名称。
- PluginsPageInteractionTest：9/9。
- PluginsPageScreenshotTest：4/4。
- PluginsScreenshotTest：15/15。
- 全量 jvm-app：647 found / 5 skipped / 642 started / 642 passed / 0 failed，exit 0。
- 证据位于本目录，所有 focused 与全量日志均 exit 0；8 个证据文件校验通过。

## 边界

本批完成的是本地 manifest 能力清单。MCP 连接、授权、hook 配置写入、脚本执行、文件型 Apps 和远端插件服务仍需要真实 Host 服务协议；市场发现、云端项目和 Codex 云端环境继续排除。本批是行为和页面验收，没有原版同状态像素一致性声明。
