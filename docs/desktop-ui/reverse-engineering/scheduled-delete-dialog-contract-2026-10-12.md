# 契约：定时任务删除确认改为模态对话框（2026-10-12）

范围：定时任务详情页「删除」的二次确认。把内联 `HarnessSurface` 确认卡改成与 Codex 一致的模态确认对话框，
并采用 zh-CN 资源里的删除文案。只处理确认交互与**可见文案**；不改动删除语义（只移除任务配置，不取消在途任务、
不清理会话历史）。

## §1 只读依据

来源：`/tmp/asar-new/webview/assets/zh-CN-b05cfd55c534.js`（ASAR 已解包的 zh-CN 资源，只读；未联网）。
`inbox.automations` 命名空间下的删除确认字面：

| Codex key | 文案 |
| --- | --- |
| `inbox.automations.deleteConfirm.title` | 删除 {name}？ |
| `inbox.automations.deleteConfirm.confirm` | 删除定时任务 |
| `inbox.automations.deleteConfirm.descriptionNoPeriod` | 这将永久删除定时任务，并停止日后的运行 |
| `inbox.automations.deleteConfirm.cancel` | 取消 |

同资源里另有 `home.artifactTemplates.deleteConfirm.*`（`删除` / `删除 {title}？` / `此模板将被永久删除。此操作无法撤销。`）
与 `settings.dataControls.archivedChats.deleteConfirm.*`，属于**模板删除**与**归档聊天删除**，本批不采用。

## §2 本仓映射

| 位置 | 原实现 | 新实现 | 依据 |
| --- | --- | --- | --- |
| 呈现 | 详情页内联 `HarnessSurface` 确认卡 | 共享 `HarnessConfirmDialog` 模态对话框 | Codex 删除确认是模态弹窗 |
| 容器 tag | `scheduled-delete-confirmation` | `scheduled-delete-confirmation`（不变） | 保持既有定位 |
| 标题 | 删除此任务配置？ | 删除 {任务名}？ | `inbox.automations.deleteConfirm.title` |
| 说明 | 只删除配置，不取消已运行的任务，也不清理会话历史。 | 这将永久删除定时任务，并停止日后的运行；已开始的任务不会被取消，会话历史也不会被清理。 | `...descriptionNoPeriod` + 本仓删除语义补充 |
| 确认按钮 | 确认删除 | 删除定时任务 | `inbox.automations.deleteConfirm.confirm` |
| 取消按钮 | 取消 | 取消 | `inbox.automations.deleteConfirm.cancel` |
| 意图 | 无 | `HarnessConfirmIntent.Destructive`（初始焦点落在取消、确认按钮为 Destructive） | 与其他破坏性确认一致（`managed-remove-dialog` / `shortcuts-reset-dialog`） |

说明：Codex 的说明句是「这将永久删除定时任务，并停止日后的运行」，并未提在途任务与会话历史。本仓
`ScheduledRepository.delete` 只从 `tasks` 移除该任务、记入 `deletedTaskIds`、清空对应 cursor，**不**取消在途运行、
**不**清理会话历史；`ScheduledLocalExecutionModel` 也明示「本机定时任务只在本应用运行期间执行」。因此保留 Codex
原句并追加一句诚实补充，不宣称实现了并不存在的取消/清理效果。

## §3 交互与不变式

- 对话框 `visible = deleteId == task.id`，由 `deleteDialogFence` 守卫。它与 `pageFence` 的唯一区别是**不含
  `mutation`**：删除提交期间对话框保持打开，从而显示 `Submitting` 与失败原因，而不是在 mutation 置位瞬间被 fence
  关闭。作用域 / 上下文 / 连接 / 阶段 / 选中任务发生变化时仍会关闭对话框。
- `submitState`：`mutation == ScheduledMutation.Delete` → `Submitting`；`mutationError != null` → `Failed`；否则
  `Ready`。失败时共享对话框保持打开并显示 `scheduled-delete-confirmation:error`。
- tag 约定沿用共享对话框：容器 `scheduled-delete-confirmation`，取消 `:cancel`，确认 `:confirm`，错误 `:error`。
- 删除语义与按钮启用条件不变：`scheduled-delete` 仍 `enabled = state.canMutate`，`repository.delete` 不动。
