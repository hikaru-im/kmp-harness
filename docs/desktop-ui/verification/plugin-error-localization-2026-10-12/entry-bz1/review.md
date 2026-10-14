# Batch BZ：插件页错误态与能力标题本地化（2026-10-12）

## §1 本批目标

关闭 Batch BY 新发现的缺口：插件页在隔离空 home 下的「库存不可用」错误态仍渲染英文整句
`Local plugin inventory or source is invalid, unsupported, or unavailable.` 且重试按钮落到共享默认
`Retry`；同一详情页的能力标题 `MCP Servers` / `Hooks` / `Scripts` 也是英文。本批把这些用户可见文案
对齐中文，并给出 ASAR 依据（见
`../../reverse-engineering/plugin-inventory-localization-contract-2026-10-12.md`）。

## §2 生产改动

| 文件 | 行 | 改动 |
| --- | --- | --- |
| `modules/bundle/desktop/src/im/hikaru/harness/bundle/desktop/DesktopUserPlugins.kt` | 72 | `DesktopUserPluginsException` 默认 `sanitizedMessage`：英文整句 → 「本机插件库存或来源无效、不受支持或不可用。」（仍是唯一脱敏异常，不保留原始消息/内容/原因；显式中文消息不变） |
| `apps/shared/src/im/hikaru/harness/client/plugins/InstalledPluginsPage.kt` | 385 | `PluginReadFeedback` 的 `PluginLoadState.Error` 分支显式传 `retryLabel = "重试"`，不再落到共享默认 `Retry` |
| `apps/shared/src/im/hikaru/harness/client/plugins/InstalledPluginsPage.kt` | 361-363 | `capabilityLabel`：`MCP Servers` / `Hooks` / `Scripts` → `MCP 服务器` / `钩子` / `脚本` |

文字流向：`DesktopUserPlugins.invalid()` → `DesktopUserPluginsException(默认中文)` →
`DesktopInstalledPluginRepository.guarded` 的 `catch (error: DesktopUserPluginsException)`
→ `PluginRepositoryError(error.message)` → `InstalledPluginsController` → `PluginLoadState.Error`
→ `PluginReadFeedback` → `HarnessErrorState(message, retryLabel = "重试")`。

## §3 测试改动

| 文件 | 改动 |
| --- | --- |
| `modules/bundle/desktop/test/.../DesktopUserPluginsTest.kt` | 新增 `defaultSanitizedMessageIsLocalizedAndKeepsNoDetails`（断言默认消息为中文且 `cause` 为空） |
| `apps/jvm-app/test/.../PluginsPageInteractionTest.kt` | `failedReadIsErrorAndExplicitRefreshRecovers` 新增：`error-state` 子树内存在「重试」，全页无 `Retry` |
| `apps/jvm-app/test/.../PluginsPageScreenshotTest.kt` | `Case.Failure` 新增：存在「重试」且无 `Retry` |
| `apps/jvm-app/test/.../PluginManagementInteractionTest.kt` | 断言文案 `MCP Servers` → `MCP 服务器` |

## §4 验收（各批独立，不累加为总体覆盖率）

| 命令 | 结果 |
| --- | --- |
| `-p jvm -m desktop --include-classes=…DesktopUserPluginsTest` | 28 found / 28 passed / 0 skipped / 0 failed（`logs/targeted-bundle-desktop.txt`） |
| `-p jvm -m jvm-app --include-classes='im.hikaru.harness.desktop.*Plugin*Test'` | 61 found / 61 passed / 0 skipped / 0 failed（`logs/targeted-jvm-plugin-set.txt`） |
| `-p jvm -m jvm-app --include-classes=…PluginsPageInteractionTest` | 9 found / 9 passed / 0 failed（`logs/targeted-plugins-page-interaction.txt`） |
| `-p jvm -m shared --include-classes='im.hikaru.harness.client.plugins.*Test'` | 20 found / 20 passed / 0 failed（`logs/targeted-shared-plugins.txt`） |
| `-p jvm -m shared`（全量） | 561 found / 561 passed / 0 skipped / 0 failed（`logs/full-shared.txt`） |
| `-p jvm -m jvm-app`（全量） | 699 found / 5 skipped / 694 started / 693 passed / **1 failed**（`logs/full-jvm-app.txt`） |

全量 `jvm-app` 的唯一失败是 `PtyTerminalFontInteractionTest.savedFontChangeRefitsSameRunningPtyAtFixedBounds`
（`ComposeTimeoutException: Condition still not satisfied after 30000 ms`，等待「保存字号后重排同一 PTY」）。
这是已知的 PTY 字体超时：该用例在隔离重跑中通过，且同类 `PtyTerminalPageInteractionTest`
（BX 批次曾出现同族超时）本次也通过。隔离重跑 `PtyTerminal*Test`：10 found / 10 passed / 0 failed
（`logs/pty-flake-rerun.txt`）。本批未改动终端/外观代码，判定为环境相关的 PTY 抖动。

## §5 截图产物核对与还原

全量运行重写了 `docs/desktop-ui/captures/`。与批次内快照逐文件 sha256 对比：仅 84 个文件字节变化，
分布在 `plugins-page-2026-10-08`（64）与 `scheduled-execution`（20）——前者是运行间非确定 id 抖动
（本地化内容已在快照内），后者与本批无关。已把这 84 个文件还原为快照字节，还原后 captures 与快照
**逐文件 sha256 完全一致（差异 0）**。插件页失败态截图 JSON 现已含「重试」且不含 `"Retry"`
（`logs/plugins-capture-localization-check.txt`）。

## §6 边界（明确不主张）

- 共享默认值未改：`HarnessErrorState` / `HarnessRow` / `HarnessContentState.Error` 的 `retryLabel`
  默认仍是 `Retry`；本批只在插件页调用点显式传中文。`SettingsConnectionsPage` 错误态无重试按钮，不涉及。
- `DesktopUserPluginsConflict` 的英文消息未本地化：它不会到达界面（仓储层 `catch` 映射为中文），
  本批不改。
- 未做原版同状态像素比对：本仓插件页与原版插件页不是同一族界面，本批只证明文案语言与本仓一致性。
- 视觉证据仅为「截图 JSON 文案」级别；未新拍原生窗口截图（BY 已覆盖原生壳层）。

## §7 复现

```sh
./kotlin test --build-dir=/tmp/harness-bz-build-20261012 -p jvm -m desktop  --include-classes=im.hikaru.harness.bundle.desktop.DesktopUserPluginsTest
./kotlin test --build-dir=/tmp/harness-bz-build-20261012 -p jvm -m jvm-app --include-classes='im.hikaru.harness.desktop.*Plugin*Test'
./kotlin test --build-dir=/tmp/harness-bz-build-20261012 -p jvm -m shared  --include-classes='im.hikaru.harness.client.plugins.*Test'
./kotlin test --build-dir=/tmp/harness-bz-build-20261012 -p jvm -m shared
./kotlin test --build-dir=/tmp/harness-bz-build-20261012 -p jvm -m jvm-app
```
