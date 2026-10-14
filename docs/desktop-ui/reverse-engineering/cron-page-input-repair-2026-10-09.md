# Cron页面真实字符输入：R4窄修分析

父线程R3已进入完整HarnessApp的定时页面，但6项都在`typeAscii`最终EditableText断言失败：预期`local scheduled review`，实际空字符串。生产和测试均编译通过，0 PNG，19类未运行。证据：`../verification/scheduled-cron-page-enable-2026-10-09/cron-page-r3-failed/`。尚未记录鼠标点击后的Focused，不能据此宣称已验证鼠标焦点。

## 固定依赖证据

实际运行依赖为Compose UI/test/foundation Desktop 1.11.1。官方Maven sources已固定SHA，7份原始源码存于`cron-page-input-evidence-2026-10-09/`，见manifest。

- `InputDispatcher.skiko.kt:338`仅构造KeyDown、KeyUp；codePoint虽提供ASCII映射，但nativeEvent缺省null。
- `TextFieldKeyInput.desktop.kt:35`的isTypedEvent要求`awtEventOrNull.id == KEY_TYPED`且keyChar可打印；`TextFieldKeyInput.kt:77`据此构造CommitTextCommand。因此现有pressKey序列不能通过Desktop文字输入分支。
- `KeyEvent.skiko.kt:68`可构造带nativeEvent、codePoint和类型的事件。`KeyEvent.desktop.kt:38`把AWT KEY_TYPED映射为Unknown，字符来自keyChar。
- `SkikoComposeUiTest.scene`可访问ComposeScene。真实字符可沿scene的键盘事件分发进入焦点字段；保持按下、字符、抬起顺序，使用真实AWT KEY_TYPED载荷。不得调用语义SetText、performTextReplacement、草稿写入、直接回调或反射代替。

## 实施边界

继续同一工作者与SHUAIAPI/gpt-6.1-sol / opencodex。仅允许修改`ScheduledCronPageIntegrationTest.kt`及既有tmp交付JSON/MD。另外三份源码SHA保持R3；原始baseline before SHA不替换，另在repair_history记录本轮before SHA。

真实鼠标按已测owning root和可见bounds点击后立即assertIsFocused。字符输入逐字经过Compose键盘事件管线，在UI线程分发；逐字settle并验证EditableText前缀与焦点，让后续失败区分焦点、事件和重组。保留真实mouse/key/wheel、10秒期限、所有场景/业务/几何断言、两类10项Test、12张Paused/Accepted/Completed宽窄浅深矩阵。修复后只静态检查、报告SHA、释放写集，不build/test；父线程冻结cron-page-r4并串行20类134项验收。
