# Worktrees「环境」小节三项设置验收 AZ

日期：2026-10-11（Asia/Shanghai）。目标模型：deepSeek V4.1 Flash 直接实施，不委托子代理。证据批次名 `entry-az1`。

## 1. 本批目标与依据

AY 补齐了 Worktrees 页首「工作树根目录」，但原版同页的「环境」小节三行（自动删除旧工作树、自动删除限制、创建前获取上游更新）本仓还没有。本批只实现可观察的那一层——三个真实持久化的偏好（含默认值与 `min(1)` 校验）与「关闭自动删除先确认」的交互；破坏性裁剪与创建前 fetch 的实际执行属后端行为，本批不接线、也不主张。完整边界见 `../../../reverse-engineering/worktree-env-settings-contract-2026-10-11.md`。

ASAR 依据（只读抽取，见 `logs/asar-evidence.txt`）：

- N01 `webview/assets/worktrees-settings-page-ad5cbb37d745.js`：上游刷新 `ke(L.upstreamRefreshMode)`（切换 `best-effort`/`never`）、自动删除 `d?.autoCleanupEnabled ?? default`（提交 `Nt(..., L.autoCleanupEnabled, e)`）、保留数量 `d?.keepCount ?? default`（`Enter` 触发保存），三条各有成功/失败 toast。
- N02 同上：关闭自动删除前弹 `autoCleanup.confirm.*` 确认框。
- N03 `webview/assets/app-shared-8bd9d0399038.js`：key/schema/默认值——`worktree-upstream-refresh-mode`（enum `never`|`best-effort`，默认 `never`）、`worktree-auto-cleanup-enabled`（boolean，默认 `true`）、`worktree-keep-count`（`int().min(1)`，默认 `15`）。
- N04 同上：`$pt=`never`` 为 `upstreamRefreshMode` 默认。
- N06 `webview/assets/zh-CN-b05cfd55c534.js`：小节标题「常规」「环境」、三行文案与「自动删除已停用…」替换描述、成功/失败 toast。

## 2. 写入集

生产（3）：

- `apps/shared/src/im/hikaru/harness/client/settings/WorktreeSettings.kt`（扩展：`WorktreeUpstreamRefreshMode`、`WorktreeAutoCleanupDefault`/`WorktreeKeepCountDefault`、`WorktreeSettings` 三个新字段、纯函数 `keepCountValue`/`keepCountError`）
- `apps/shared/src/im/hikaru/harness/client/git/GitPages.kt`（WorktreesPage 参数改 `onSaveWorktreeSettings: (WorktreeSettings)->Unit`；根目录小节改为 `copy(root=…)`；新增 `WorktreeEnvironmentSection`）
- `apps/shared/src/im/hikaru/harness/client/app/HarnessApp.kt`（透传改名 + 整份 settings 保存）

测试（4）：

- `apps/shared/test/im/hikaru/harness/client/settings/WorktreeSettingsTest.kt`（新增 2 项）
- `apps/shared/test@jvm/im/hikaru/harness/client/settings/DesktopWorktreeSettingsStoreTest.kt`（既有 round-trip 追加环境字段断言）
- `apps/jvm-app/test/im/hikaru/harness/desktop/WorktreeRootSettingInteractionTest.kt`（回调改名 + 新增 1 项 `environmentControlsPersistStoredPreferences`）

文档（2，新增）：

- `docs/desktop-ui/reverse-engineering/worktree-env-settings-contract-2026-10-11.md`
- 本文件

## 3. 实现要点

- 模型层对齐原版观察值：默认 `autoCleanupEnabled=true`、`keepCount=15`、`upstreamRefreshMode=Never`（`@SerialName("never"/"best-effort")`）；`keepCountValue` 仅接受 trim 后 ≥1 的整数（原版 `int().min(1)`），`keepCountError` 给出行内文案。新字段都带默认值，AY 旧文档仍可读。
- 页面层新增「环境」小节：自动删除开关（关闭时先 `HarnessConfirmDialog` 确认，确认前不写存储）、保留数量输入（行内错误 + 保存按钮，仅在有改动且合法时可保存）、上游刷新开关（开= `BestEffort`、关= `Never`）；保存/加载错误与「已保存工作树设置」内联提示。自动删除关闭时保留数量描述切换为原版「自动删除已停用…」。
- 保存语义修正：AY 的根目录回调原本用 `WorktreeSettings(root)` **整体替换**（会清空其它字段），本批改为 `settings.copy(root=…)`/整份传入，避免新增字段被覆盖。
- 键位映射（与原版一致，见契约表）：`git-worktree-root` 沿用 AY；本批三项对应 `worktree-auto-cleanup-enabled`/`worktree-keep-count`/`worktree-upstream-refresh-mode`。

## 4. 问题

- 破坏性的自动裁剪与创建前的上游 fetch 执行**未接线**（见 §6），因此三行是真实持久化的偏好而非可观察到效果的行为；文档与契约均如此标注。
- 原版逐条独立 mutation 的并发/失败重试语义、`CmdOrCtrl+S`/`Enter` 快捷键未复刻（本仓用显式保存按钮 + 小节内联提示）。
- `WorktreesPage` 在本批后于 `worktreeSettings == null` 时多渲染一段「工作树设置服务不可用。」（与 AY 根目录小节同款）；截图测试只做几何断言，全量 `jvm-app` 0 失败。
- 本机 `screencapture` 仍返回 `could not create image from display`，无原生窗口/像素证据。

## 5. 验收

所有命令使用 `--build-dir=/tmp/ap-build`。

| 套件 | 类/范围 | 结果 | 日志 |
| --- | --- | --- | --- |
| shared 全模块（生产改动所在模块） | 全部 | **526 found / 0 skipped / 526 started / 526 passed / 0 failed**，exit 0 | `logs/shared.log` |
| shared settings 定向 | `im.hikaru.harness.client.settings.*` | **54 found / 0 skipped / 54 started / 54 passed / 0 failed**，exit 0 | `logs/shared-settings-targeted.log` |
| jvm-app 定向 | `WorktreeRootSettingInteractionTest` | **3 found / 0 skipped / 3 started / 3 passed / 0 failed**，exit 0 | `logs/jvm-app-targeted.log` |
| jvm-app 回归 5 类 | `WorktreePageInteractionTest` + `ManagedWorktreeInteractionTest` + `GitPageInteractionTest` + `GitDiffViewInteractionTest` + `GitDiffRichPreviewInteractionTest` | **22 found / 0 skipped / 22 started / 22 passed / 0 failed**，exit 0 | `logs/jvm-app-regression.log` |
| 全量 jvm-app（本批门槛） | 全部 | **680 found / 5 skipped / 675 started / 675 passed / 0 failed**，exit 0 | `logs/full-jvm-app.txt` |

各套件是独立证据，不累加为总体覆盖率。

### 5.1 新增断言（关键）

- 纯函数（`WorktreeSettingsTest`，+2）：`storedDefaultsMatchTheObservedSettingKeys`（默认 `autoCleanupEnabled=true`、`keepCount=15`、`upstreamRefreshMode=Never`，且 `wire` 为 `never`/`best-effort`）；`keepCountOnlyAcceptsIntegersAtLeastOne`（`1`/` 20 `/`15` 通过，`0`/`-3`/`2.5`/`abc`/空串拒绝；`keepCountError("1")` 为 null、`"0"` 非 null）。
- 存储 round-trip（`DesktopWorktreeSettingsStoreTest`，既有用例追加）：`WorktreeSettings("/tmp/managed", false, 20, BestEffort)` 写盘后由新 store 读回相等，证明环境三字段随 root 一同持久化。
- 页面交互（`WorktreeRootSettingInteractionTest.environmentControlsPersistStoredPreferences`，+1）：初始显示存储值 15 且保存禁用 → 输入 `0` 时保存禁用且出现「请输入不小于 1 的整数。」→ 输入 `20` 保存后 `store.keepCount==20` → 切换上游刷新后 `store.upstreamRefreshMode==BestEffort` → 点击自动删除开关**不立即写**（`store.autoCleanupEnabled` 仍为 true）→ 确认框 `:confirm` 后才变 false，且保留数量描述切换为「自动删除已停用…」。

### 5.2 全量结果

全量 `jvm-app` 为 **680 found / 5 skipped / 675 started / 675 passed / 0 failed**（exit 0），本批未命中历史 worktree/PTY/git-commit 时序抖动，无需隔离重跑。

### 5.3 未跟踪捕获目录

跑前 `docs/desktop-ui/captures/scheduled-execution/` 为 `ac8ebb5463e8ccf2f7306dabee0b044b8e4018c5`；全量运行确实重写了它（跑后为 `4f387960367fa746579d440583d0e5e2d54e802e`），随后按 AE/AU/AV/AW/AX/AY 先例从批次前字节 `/tmp/au-sched-iwXA/orig` 还原，`diff -r` 一致，组合 sha 回到 `ac8ebb5463e8ccf2f7306dabee0b044b8e4018c5`（20 个文件）。其余截图目录（如 `captures/git-worktrees/`）由截图测试按既有机制重写，与 AY 同处理方式，本批未单独备份。

## 6. 未验证边界

- 不主张原版同尺寸像素或原生窗口（本机 Screen Recording 权限不可用，`screencapture` 返回 `could not create image from display`）。
- 未实现：自动删除旧工作树的实际裁剪、创建前「获取上游更新」的实际 git 执行（本批只持久化偏好）；原版逐条 toast、`CmdOrCtrl+S`/`Enter` 快捷键、逐条独立 mutation 的并发/失败重试语义。
- 各批证据独立，整体目标 active。
