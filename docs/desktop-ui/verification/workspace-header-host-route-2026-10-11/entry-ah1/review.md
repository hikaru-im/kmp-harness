# 批次 AH 证据：工作区选择器接入 Host 页面并清理通知死按钮（生产改动）

- 日期：2026-10-11
- 写集：apps/shared/src/im/hikaru/harness/client/shell/AppShell.kt、apps/shared/src/im/hikaru/harness/client/app/HarnessApp.kt。
- 变化：工作区选择器由空 onClick 改为调用现有 Hosts 路由；删除没有通知服务和页面的 Bell 按钮。AppShell 新增 onOpenHosts 回调，HarnessApp 绑定到 PageId.Hosts。

验收：PageShellSpecTest 7/7；ScreenshotTest 15/15。两组都重新编译 shared/jvm-app 并通过。focused 证据位于本目录。

边界：附件、Agent、Model 等旧版测试 composer 仍有独立能力边界；生产 LiveConversationComposer 已将附件和权限显示为禁用语义，并使用真实模型菜单。
