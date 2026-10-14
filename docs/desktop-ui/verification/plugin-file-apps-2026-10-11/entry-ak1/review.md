# 批次 AK 证据：本地插件文件型 Apps 名称只读接入（生产改动）

- 日期：2026-10-11
- 写集（生产 2 个文件、测试 2 个文件）：
  - `modules/bundle/desktop/src/im/hikaru/harness/bundle/desktop/DesktopUserPlugins.kt`：manifest `apps` 支持**字符串路径**形态（实测 `./.app.json`），读取其顶层 `apps` object 的**键**作为 App ID；新增 `readAppsFile`、`MAX_APPS_FILE`，并让已被消费的 apps 文件不再被打成 unsupported 描述符。
  - `apps/jvm-app/src/im/hikaru/harness/desktop/plugins/DesktopInstalledPluginRepository.kt`：更新 Apps 不可用文案与注释（`.app.json` 现在会读）。
  - `modules/bundle/desktop/test/.../DesktopUserPluginsTest.kt`：+2 个测试。`apps/jvm-app/test/.../DesktopInstalledPluginRepositoryTest.kt`：+1 个测试。
- 关联：AI（能力清单名称只读）、AF（inline Apps）、契约 `reverse-engineering/plugins-package-consumption-contract-2026-10-07.md` §3.3 / §1 表。
- 目录：`docs/desktop-ui/verification/plugin-file-apps-2026-10-11/entry-ak1/`

## 问题

AF 让详情页消费 manifest **inline** 声明的 Apps 名称，AI 追加了 MCP/hooks/scripts 名称；但 Apps 的**文件型**声明仍旧一律进 `apps (unsupported)`。逆向契约明确记录了一个可安全枚举的形态：manifest 的 `apps` 可以是**字符串路径**（实测 `./.app.json`），该文件顶层有一个 `apps` object，其 **key 就是 App ID**（`openai-templates → default_templates`、`spreadsheets → connected_documents`），value 里有 `id`/`required` 等配置。此前我们没有消费它。

## 改动

`importLocal` 的 `apps` 分支新增字符串路径处理：

1. `apps: "<path>"` → 用既有 `relativeDirectory` 校验（拒绝绝对路径、`..`、控制字符、`\\:*?<>|`、>16 段）后在 source 内解析；`readAppsFile` 只读取**顶层 `apps` object 的键**。
2. 成功 → 键并入 `applications`（沿用既有排序/上限/`checkText`），并记录为“已消费”，`ASSET_PATHS` 循环不再为这同一个文件追加 `apps (unsupported)`；失败（缺失/非普通文件/非 JSON/无 `apps` object/超 `MAX_APPS`）→ 回落 `apps (unsupported)`，不猜、不部分读取。
3. 目录型 `apps/`、未识别的路径形态、以及其它声明继续 `unsupported`。

安全边界：只保留**名称**（`MAX_APPS=64`、单名 `MAX_NAME=256`、文件 `MAX_APPS_FILE=64 KiB`）；端点/密钥/配置正文不进入清单；路径逃逸被 `relativeDirectory` 拒绝；不连接、不鉴权、不执行任何 App。

## 验收

- `desktop` 模块 `DesktopUserPluginsTest` **24/24**（本批前 22；新增 `appFileDeclaredByPathYieldsSortedNamesOnlyAndNoUnsupportedDescriptor` 与 `unreadableOrEscapingAppsDeclarationsStayDescriptorsAndAreNeverRead`），`logs/desktop-module-desktopuserplugins.log`。
- `jvm-app` 定向：`DesktopInstalledPluginRepositoryTest` + `PluginsPageInteractionTest` + `PluginManagementInteractionTest` + `PluginsPageScreenshotTest` + `PluginLocalUpdateInteractionTest` **38/38**（含新增 `fileFormAppsDeclaredByPathReachTheDetailPageAsNamesOnly`：详情页 `PluginAppsState.Available` 名称 = 文件键、`unsupportedAssets` 为空、清单不含 `APPS_FILE_SECRET`）。
- 全量 `jvm-app` **648 found / 5 skipped / 643 started / 643 passed / 0 failed**（104 容器成功、0 失败，exit 0，`logs/jvm-app-full.log`）。

## 边界与残留

- 只接入**契约有实测证据**的那一种文件形态（manifest 字符串路径 → 顶层 `apps` object 的键）。`apps/` **目录**型没有观测到约定，仍记 unsupported，不按目录名猜测 App ID。
- 只读名称，不证明 tool inventory、已授权或已连接；不读取 value 里的 `id`/`required` 之外的任何配置，也不落地到执行路径。
- 未新增 UI 元素（只有 fallback 文案变化），因此本批无截图、不主张像素；MCP 连接/授权、hooks 配置写入与脚本执行生命周期仍未接。
- `captures/scheduled-execution/`（未跟踪）被全量运行刷新后按先例还原到批次前字节（组合 sha `a842ee6a…`）；本批未改动定时页，刷新内容与 AJ 批次一致。
- 各批证据独立，整体目标 active。
