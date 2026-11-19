# G14 快捷聊天实现记录（2026-11-13）

本文件记录 G14 第三项能力“快捷聊天”。系统通知与页内查找已在同一 G14 中独立交付；全局热键仍是后续切片。

## 已交付

- 新增共享 `QuickChatWindowContent`：使用真实 `ConversationController`、真实 Host session、历史和流式消息，不维护第二份 mock transcript。
- 桌面新增独立 `QuickChatWindow`，由 Compose Desktop `Window` 承载，提供关闭、历史消息、消息输入和发送。
- 桌面主程序创建独立 quick-chat controller，并用本机 `Connection` 与 session event stream 绑定；首次发送可创建真实会话，后续在窗口内继续。
- 新标签页的“侧边聊天”卡片由平台能力显式控制：桌面接入窗口时可用，未接入的平台保持 disabled，不伪造可用入口。
- 快捷聊天支持通过共享 `OpenQuickChat` 绑定接入全局热键控制器；当前桌面 registrar 明确返回 Unsupported，入口仍由新标签页工具卡打开。

## 验证

- `QuickChatInteractionTest`：2/2，覆盖桌面入口路由和不支持平台的明确不可用状态。
- `HarnessTabsInteractionTest`：24/24，验证桌面窗口模块编译与既有窗口回归。
- shared `ScheduledNotificationsTest`：2/2，作为共享模块编译回归。
- `git diff --check`：通过。

## 边界

快捷聊天复用现有会话发送协议；它不复制完整主窗口导航、不伪造模型选择，也不在本切片新增全局快捷键注册。移动/未提供独立窗口宿主的共享平台继续显示不可用状态。
