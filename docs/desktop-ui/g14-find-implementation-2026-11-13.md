# G14 页内查找实现记录（2026-11-13）

本文件记录 G14 的第一项能力“页内查找”。快捷聊天、系统通知与全局热键仍是独立后续切片，不在本次实现中伪造入口。

## 已交付

- 会话页新增浮动查找栏：打开、关闭、文本输入、命中计数、上一个、下一个。
- 查找只读取当前 `ConversationViewport` 已渲染的消息文本（`TextContent`），按消息与内容顺序做大小写不敏感的字面匹配；空查询不报告命中，结果上限避免超长会话阻塞界面。
- 选择命中后滚动到所属会话 turn；`Enter` 下一个、`Shift+Enter` 上一个、`Escape` 关闭。
- 新增 `Ctrl/Cmd+F` 的真实快捷键命令 `FindInConversation`，仅在会话页消费；快捷键注册表与旧快捷键配置兼容。
- diff 页原有查找栏已纳入本次验收：它对 parsed diff body / hunk header 做同样的字面查找，并在命中时展开文件与定位行。

## 验证

- `ConversationFindTest`：2/2
- `ShortcutsRepositoryTest`：7/7
- `ShortcutsInputControllerTest`：5/5
- `LiveConversationInteractionTest`：31/31（含打开、计数、下一个、Escape）
- `GitDiffReadingInteractionTest`：8/8
- `git diff --check`：通过

## 边界

本切片不把查找结果扩展成全局搜索，也不搜索未挂载页面、隐藏文件或后端数据；只处理当前页面真实渲染路径。系统通知、快捷聊天独立窗口与全局热键冲突/平台能力检测留待 G14 后续切片。
