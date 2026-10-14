# Thread 摘要面板「停止所有后台终端」：本批实施边界（2026-10-11）

## 1. 本批依据（ASAR 只读）

从本机安装包抽取的 webview bundle（`/tmp/asar-new/webview/assets`）只读分析，未改动任何原包。原始片段见 `verification/thread-summary-stop-all-2026-10-11/entry-av1/logs/asar-stop-all-evidence.txt`。

| 编号 | 观察到的内容 |
| --- | --- |
| C01 | 「后台进程」小节的行渲染器 `$b` 在 terminal 分支里，每个 terminal 行都带一个 `Q.ItemActionButton`：`label` 取自 `codex.localConversation.backgroundTerminals.stop`（=「停止所有后台终端」），`visibility: 'interaction'`，`disabled: d != null`，`type: 'button'`；`d === e.terminal.id` 时该按钮换成 spinner（`U`），否则是图标（`Nc`）。 |
| C02 | 点击处理器 `h`：`if (i != null && d == null) { f(e.id); cleanBackgroundTerminals(conversationId).catch(onStopError).finally(() => f(null)) }`。即：一次点击会停止该会话的**所有**后台终端（不是只停被点击的那一个）；`d`（正在进行的 id）非空时不重入；失败走 `onStopError`（`codex.localConversation.backgroundTerminals.cleanError` =「无法停止后台终端」toast）。 |
| C03 | 该动作挂在**每一个** terminal 行上（同一 `$b` 渲染所有行），行本身仍可点击打开该终端输出（`ItemTrigger: onClick → onOpenTerminal`）。 |
| C04 | 摘要面板小节集合（含 `background-tasks`）与 zh-CN 文案：`backgroundTerminals.stop` =「停止所有后台终端」、`backgroundTerminals.cleanError` =「无法停止后台终端」、`backgroundTerminals.defaultLabel` =「后台终端」。 |

## 2. 已观察到与未观察到的语义

- **已观察**：动作是「停全部」而非「停这一个」；挂到每个 terminal 行；一次点击期间不重入（disabled + 行内 spinner）；失败有独立 toast 文案；成功/失败都不改变行的 `command`/`label`。
- **未观察到**：`cleanBackgroundTerminals` 的后端实现（是否批量 RPC、单次失败是否影响其余进程、部分失败的返回形态）；toast 的精确位置/时长；`visibility: 'interaction'`（hover 才显示）的精确 CSS 过渡；原版同状态像素。
- **本仓已具备的等价物**：`ThreadPanelsRepository.operateTerminal(owner, key, Cancel)` 是按单个进程的显式取消，`TerminalOperationOutcome` 区分 `Applied`/`Rejected`/`Unknown`。本批把它按「对每个 running 进程各发一次 Cancel」组合成停全部，不新增后端协议。

## 3. Harness 实施（本批生产改动）

| 层 | 改动 |
| --- | --- |
| `summary/ThreadSummaryActivity`（shared，加 1 常量） | 新增 `ThreadSummaryStopAllLabel` =「停止所有后台终端」（取自 C04 的 i18n 键）。行模型与投影不变。 |
| `summary/ThreadSummaryActivityController`（shared） | 状态新增 `stoppingProcesses` 与 `stopNotice`；新增 `stopAllProcesses()`：只取消**上一次成功读取里 lifecycle 为 `Running`** 的进程（清单位于控制器内，不重新扫描进程表），对每个 key 各发一次 `operateTerminal(owner, key, Cancel)`；`Applied`/`Unknown` 的 key 记入 `stoppedKeys`，同一 owner incarnation 内不重发，`Rejected` 可重试；非 `Applied` 的消息按顺序去重后进 `stopNotice`；结束后重新 `refresh()` 以读取真实清单。未绑定/已关闭/无 running/已在途一律 no-op 返回 null。绑定与关闭会清空清单与 `stoppedKeys`，并取消在途的停全部任务。 |
| `summary/ThreadSummaryPanel`（shared） | 新增可选参数 `processesStopping`/`processesNotice`/`onStopProcesses`；`onStopProcesses != null` 时在**每个** process 行右侧渲染 `thread-summary-process-stop-<id>` 图标按钮（`contentDescription` =「停止所有后台终端」，`processesStopping` 时 disabled），与行点击（打开终端）分离；`processesNotice` 非空时在进程小节下方渲染 `thread-summary-processes-notice`。深色/浅色都走既有 `HarnessTokens`。 |
| `HarnessApp`（shared） | 把 `summaryActivity.stoppingProcesses` / `stopNotice` 与 `{ summaryActivityController.stopAllProcesses() }` 接进摘要 island。 |
| `HarnessAppThreadPanelsShellTest`（jvm-app） | 追加断言：running / exited 两个 process 行都有 `thread-summary-process-stop-<id>`。 |

### 3.1 与原版的差异（明确不主张）

- 原版动作是 hover 才可见（`visibility: 'interaction'`）；本仓始终可见（无 hover 语义层，避免不可发现/不可测的控件）。
- 原版失败是 toast；本仓没有全局 toast 基础设施，改为在进程小节内渲染服务原文（`thread-summary-processes-notice`），文案来自服务返回，不新造。
- 原版在该按钮上显示 spinner；本仓用 `HarnessButtonState(enabled = false)` 表达在途状态，未加动画。
- 原版走一个 `cleanBackgroundTerminals(conversationId)` 批量调用；本仓按进程各发一次既有 `Cancel`，因此「部分成功」时可能出现部分消息，本批按去重后的原文展示，不合并语义。
- 未实现：`showInlineActivity` 去重、`autoCollapse`「{count} 完成」、subagent 行 `diffStats`、摘要其余小节；无原生窗口/像素证据。

## 4. 证据

- 验收：`docs/desktop-ui/verification/thread-summary-stop-all-2026-10-11/entry-av1/`
- 抽取片段与本地化字符串：同上 `logs/asar-stop-all-evidence.txt`。
