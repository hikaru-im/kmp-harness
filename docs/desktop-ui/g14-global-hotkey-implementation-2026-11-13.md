# G14 全局热键实现记录（2026-11-13）

本文件记录 G14 第四项能力“全局热键”。快捷聊天窗口仍复用真实 `ConversationController`；本切片只增加系统级热键的配置、冲突校验与平台适配边界。

## 已交付

- 共享设置新增 `ShortcutCommand.OpenQuickChat`，默认沿用新标签页侧边聊天参考键：macOS 为 `⌘⌥S`，Windows/Linux 为 `Ctrl+Alt+S`。
- 新增 `GlobalHotkeyRegistrar` 平台 seam、`GlobalHotkeyController` 状态机和 `GlobalHotkeyState`：注册成功、未支持、冲突、失败、未启用均有独立状态，不把普通 Compose 按键事件伪装成系统级热键。
- `GlobalHotkeyController` 从 `ShortcutsRepository` 的可编辑绑定同步快捷聊天热键；注册器触发事件后通过共享 Flow 回到 `HarnessApp`，打开已有独立快捷聊天窗口。
- 快捷键注册前复用 `ShortcutRegistry` 的系统保留组合规则，并检查与其他命令的有效绑定冲突。
- 桌面当前提供明确的 `DesktopGlobalHotkeyRegistrar` seam，但尚未接入 OS-native hook，因此设置页显示“不可用”，不会静默声称已注册。未来接入原生实现时只需替换该 registrar。
- 快捷键设置页新增稳定状态节点 `shortcuts-global-hotkey-status`，保留禁用/不可用状态，不增加伪造的操作按钮。

## 验证

- `GlobalHotkeysTest`：3/3，覆盖未支持、命令冲突前置拒绝、注册触发事件。
- `ShortcutsRepositoryTest`：7/7，覆盖新增命令后的持久化、默认映射与冲突行为。
- `ShortcutsInteractionTest`：7/7，既有设置页与应用分发回归。
- `QuickChatInteractionTest`：3/3，覆盖快捷聊天入口、不可用窗口和全局热键不可用状态展示。
- `git diff --check`：通过。

## 边界

系统级热键需要各桌面平台的原生注册能力；本仓库目前没有可复用的 native hook 依赖，因此桌面实现诚实返回 Unsupported。应用内快捷键仍由现有 `ShortcutsInputController` 处理，不能替代系统级热键；也不记录或持久化任何密钥、令牌或系统事件内容。
