# Search 公共组件实施契约

日期：2026-10-07。下一批 P1，先接 Settings 与 Scheduled，再复用于 Projects / Plugins。

## 分析依据

- Settings 参考截图与已测量的 SettingsSearchHeight / SettingsSearchRadius / NavigatorIcon Token；SettingsNavigatorSearch 已使用 BasicTextField、前置搜索图标、占位文字、SurfaceSecondary 和单行布局。
- ScheduledSearchRow 重复相同结构；ScheduledTasksPage 使用 HarnessTextField（Material OutlinedTextField 加固定小高度），当前截图出现文字被挤压，不能继续把 Material 默认内边距直接套入紧凑行。
- 设置过滤由 settingsNavigatorGroups 负责；任务过滤由 ScheduledTasksRepository 负责。组件只报告输入与清除，不拥有列表、路由或过滤结果。

## 输出与行为

- 新建 HarnessSearchField：受控 value/onValueChange、placeholder、明确 accessibility label、enabled、modifier、testTag；用 BasicTextField，避免固定行高裁切 Material label。
- 前置搜索图标，非空时可点击清除；清除只发出空字符串，保持编辑焦点；禁用时输入和清除均不能改值。
- 调用方可传高度/圆角（默认复用 SettingsSearch Token），正文/图标垂直居中。宽度由调用方管理。
- 空、非空、焦点、禁用、浅深色状态；可访问的编辑框名称与清除按钮名称。长输入横向滚动，不让清除按钮被推走。
- 不添加未有依据的 Escape 行为、自动搜索网络、自动跳转或隐藏业务列表。

## 验证与写入边界

- 子 Agent 只写 modules/ui-foundation 新组件及其 test@jvm 交互测试；不动 AppShell / HarnessApp / ShellPages / Token / module.yaml。
- 使用 CodeGraph 检查现有 Token、文字、图标及按钮 API 后直接实现。主线程负责串行构建、页面替换和截图对比。
- 测试验证输入事件、清除事件与焦点、禁用无变更；不要只复制状态 getter 做测试。

此契约只覆盖单行 Search；Select / Textarea / 表单校验继续作为独立任务。

实施结果：ZeroCat/gpt-6.1-sol 完成 HarnessSearchField.kt；主线程审核、将新测试移到 test@jvm 并修正对 EditableText / disabled 输入的验证方式。ui-foundation JVM 37/37 通过。已迁移 SettingsNavigatorSearch、ScheduledSearchRow 与 ScheduledTasksPage；桌面点击/截图 4/4 通过，刷新后 Scheduled 搜索文字未裁切。其他输入组件和全部像素对照仍未完成。
