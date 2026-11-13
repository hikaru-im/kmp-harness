# G14 系统通知实现记录（2026-11-13）

本文件记录 G14 第二项能力“定时任务系统通知”。快捷聊天与独立全局热键仍是后续切片。

## 已交付

- 新增共享通知协议 `SystemNotificationService`：发送结果明确区分 `Delivered`、`Denied`、`Unsupported`、`Failed`，平台不支持或系统拒绝时不会伪造“已通知”。
- 新增 `ScheduledNotificationTracker`：
  - 应用启动时把已有 terminal run 作为历史基线，不重播旧通知；
  - 只在运行从非 terminal 变为 terminal 时产生一次通知；
  - 执行器替换时重新建立历史基线；
  - 已产生的 run id 去重，权限拒绝不会导致每次状态重放都重复轰炸。
- 定时任务成功（`Succeeded`）和失败/未完成终态（`Failed`、`Blocked`、`Incomplete`、`Interrupted`、`Aborted`、`Rejected`、`Unknown`）发送系统通知；未调用提示的 `Withdrawn` 不发送。
- JVM 桌面适配使用 AWT `SystemTray`，无头环境、系统拒绝权限、平台不支持和显示失败分别返回真实结果；关闭应用时移除 tray 资源。
- 主程序在本机定时执行器启动后挂载观察器，观察器生命周期与执行器 scope 一致。

## 验证

- `ScheduledNotificationsTest`：2/2，覆盖历史基线、成功/失败文案、终态去重和执行器替换重新建基线。
- `ShortcutsInteractionTest`：7/7，同时验证新增通知代码不破坏桌面模块编译。
- `git diff --check`：通过。

## 边界

本切片不把任务 prompt 或终端输出写入系统通知，只显示任务名称与成功/未完成状态；系统通知权限由平台适配器决定，不在应用 UI 中伪造授权成功。通知点击跳转、通知中心历史管理和跨平台原生 API 适配留待后续独立变更。
