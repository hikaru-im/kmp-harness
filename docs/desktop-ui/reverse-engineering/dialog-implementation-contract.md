# Dialog 行为契约（范围确认后 P1）

日期：2026-10-07。适用：保留的设置、项目、定时任务、插件管理、Git/Worktrees 页面。

## 已读取的证据

- settings-unsaved-changes-dialog-84edb456ad34.js：pendingNavigation 非空控制 open；关闭时清空 pendingNavigation；Keep editing 是 secondary，Discard changes 是 danger/submit；提交先保存待执行 callback、清空 pending，再调用 callback。showDialogClose=false、size=compact。
- projects-index-page-3d127290b4aa.js：actions.json 在 6547/6817 附近包含确认弹窗 open、isDeleting、onConfirm、onOpenChange。加载态必须由实际操作提供，按钮点击不等同于完成。
- HarnessComponents.kt 的 HarnessDialog 只有 visible/title/content；目前未定义确认按钮、失败状态或未保存退出行为。
- HarnessActionControls.kt 已有 loading/disabled/error 与 destructive/secondary 按钮，直接复用。

## 实现约定

1. 保留现有容器入口，增加可复用确认弹窗；普通/危险语义、标题/说明、取消与确认文案由调用方提供。
2. 提交状态由 owner 控制：Ready/Submitting/Failed(error)。Submitting 阻止重复提交、取消和外部关闭；Failed 保持弹窗打开并提供可再提交的按钮。不能在组件里伪造保存、删除或网络完成。
3. 未保存退出使用独立纯状态契约：记录目的地；Keep editing 或关闭取消待跳转；Discard 返回并消费一次目的地。业务层负责真实导航与保存。
4. 弹窗内容随窗口约束布局，正文可滚动，按钮不因长正文不可达；危险确认默认焦点在取消；Escape/外点关闭 obey submitting guard；关闭后恢复触发区域焦点。
5. 复用现有文字、颜色、间距、按钮 Token。源码只证明 compact 变体，本轮不声称新宽度/圆角精确来自 Codex；后续补专用截图。

## 验证

- 交互验证：确认/取消、加载时重复操作与关闭阻止、失败重试、危险初始焦点、Escape、关闭焦点返回。
- 状态验证：请求离开→继续编辑→再次请求→放弃后仅消费一次；无待跳转时放弃无效。
- ui-foundation JVM 测试。与其他 Amper 任务串行运行，使用独立 /tmp build 目录。
- 页面接入与参考图对照单独验收；组件测试通过不等于已完成页面。

2026-10-07 实施结果：HarnessConfirmDialog.kt 和 HarnessComponentInteractionTest 已完成；主线程修正编译问题后，JVM 报告 35/35 通过，其中包括 3 项 Dialog 交互与未保存目的地消费测试。页面接入、专用对照截图仍未完成。
