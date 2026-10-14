# 插件页错误态本地化：本批实施边界（2026-10-12）

## 1. 本批依据（ASAR 只读）

从本机安装包解包出的 webview bundle（`/tmp/asar-new/webview/assets/`）只读分析，未改动任何原包、未联网。
计数与引导片段抄录见
`../verification/plugin-error-localization-2026-10-12/entry-bz1/logs/asar-zh-cn-evidence.txt`。

| 编号 | asset | 观察到的内容 |
| --- | --- | --- |
| E01 | `zh-CN-b05cfd55c534.js` | 官方简体中文本地化表包含 `重试` 1438 次；引导片段中 `n=\`重试\`` 即为通用重试动作的译文。 |
| E02 | 同上 | 能力类术语译文：`MCP 服务器` 44 次、`钩子` 50 次、`脚本` 36 次；`MCP Servers`、`Scripts` 各 0 次，`Hooks` 仅 7 次（均为代码标识，非界面标题）。 |

## 2. 已观察到与未观察到的语义

- **已观察**：官方 zh-CN 把「重试」动作译作 `重试`；把 MCP/Hooks/Scripts 这类能力标题译作
  `MCP 服务器` / `钩子` / `脚本`。
- **未观察到**：本仓「已安装插件」库存页与原版插件页并非同一族界面（原版是 marketplace/connectors），
  因此本批不改任何页面结构，只对齐**这批用户可见文案的语言**。原版同状态的像素值仍不在本批。

## 3. Harness 实施（本批生产改动）

| 层 | 改动 |
| --- | --- |
| `DesktopUserPlugins`（bundle/desktop） | `DesktopUserPluginsException` 的默认 `sanitizedMessage` 由英文句子改为中文「本机插件库存或来源无效、不受支持或不可用。」。该类仍是唯一的「已脱敏」异常：不保留解析器原始消息、来源内容或可能含密的原因；显式传入的中文消息（如「插件登记已不存在，请刷新列表。」）不受影响。 |
| `InstalledPluginsPage`（shared） | `PluginReadFeedback` 的 `PluginLoadState.Error` 分支调用 `HarnessErrorState(..., retryLabel = "重试")`，不再落到共享默认值；能力标题由 `MCP Servers` / `Hooks` / `Scripts` 改为 `MCP 服务器` / `钩子` / `脚本`。 |

### 3.1 文字流向（本批定位）

`DesktopUserPlugins.invalid()` → `DesktopUserPluginsException(默认中文)` →
`DesktopInstalledPluginRepository.guarded` 的 `catch (error: DesktopUserPluginsException)`
→ `PluginRepositoryError(error.message)` → `InstalledPluginsController` 映射为 `PluginLoadState.Error`
→ `InstalledPluginsPage.PluginReadFeedback` → `HarnessErrorState(message, retryLabel = "重试")`。
插件页正文因此不再出现英文整句，重试按钮也不再落到共享默认 `Retry`。

### 3.2 未实现（明确不主张）

- **共享默认值未改**：`HarnessErrorState` / `HarnessRow` / `HarnessContentState.Error` 的
  `retryLabel` 默认仍是 `Retry`（`modules/ui-foundation`）。本批只在插件页调用点显式传中文，
  避免影响其它调用方；`SettingsConnectionsPage` 的错误态无重试按钮，不涉及本问题。
- **`DesktopUserPluginsConflict` 的消息未本地化**：其英文消息不会到达界面（仓储层在
  `catch (_: DesktopUserPluginsConflict)` 中映射为中文「插件配置已改变，请刷新后重新操作。」），
  本批不改，列为已核实的非用户可见项。
- **未做原版同状态像素比对**：本仓插件页与原版插件页不是同一族界面，本批只证明文案语言与本仓内一致性。
- 其它已知英文残留（`PluginUnavailableReason`、`unsupportedAssets` 描述等）经核对已是中文，不在本批。

## 4. 验收

见 `../verification/plugin-error-localization-2026-10-12/entry-bz1/review.md`。各批证据独立，不累加为总体覆盖率。
