# 批次 AG 证据：移除线程标题栏无功能的分享按钮（生产改动）

- 日期：2026-10-11
- 写集：apps/shared/src/im/hikaru/harness/client/app/DesktopClientCatalog.kt；apps/shared/test/im/hikaru/harness/client/app/ClientUiCatalogTest.kt。
- 变化：内置 UI slot catalog 不再向 ThreadTitlebarActions 注入 `desktop-client-share`；该项原本渲染「分享」并使用空的 onClick。保留 slot 扩展能力，移除没有本地分享服务支撑的死按钮。

验收：ClientUiCatalogTest 1/1；ScheduledPageScreenshotTest 3/3，证明 jvm-app 重新编译并运行通过。全量 jvm-app 不因这一小批重复运行；AF 全量结果已记录为 647 found / 5 skipped / 641 passed / 1 PTY timeout。

边界：工作区菜单、通知、附件、Agent 和 Model 仍有独立的服务接入缺口，当前只移除了确认的线程标题栏空处理器；下一批继续按实际服务逐项处理。
