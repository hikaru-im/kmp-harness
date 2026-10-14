# 本地终端紧凑视觉合同

实施模型 SHUAIAPI/gpt-6.1-sol / opencodex。先读同目录 terminal-local-visual-analysis-2026-10-10.md，再实施。已完成R5全部功能，不重复改造服务。

## 有限写集

1. apps/shared/src/im/hikaru/harness/client/panels/PtyTerminalPanel.kt
2. apps/shared/src/im/hikaru/harness/client/panels/PtyTerminalGrid.kt
3. apps/jvm-app/test/im/hikaru/harness/desktop/PtyTerminalPageInteractionTest.kt

写集外所有已有dirty/untracked均保护。禁止reset/stash/clean/checkout/commit、改用户配置、干预已有进程。不得运行build、test、screenshot或native app；只实施与静态检查。交付后立即停止源码写入并退出，父线程冻结后独立动态验收。

## 实施

- 一行紧凑操作区替换六按钮FlowRow及独立selector行。selector仍显示真实完整key匹配的最新lifecycle和编号；空间不足时不得使图标离开窗口。已有真实terminal才显示selector；新建显式icon保留pty-terminal-create，使用共享HarnessIconButton、tooltip/语义标签。更多入口pty-terminal-actions，用共享typed HarnessMenu，标签明确。
- 菜单保留刷新/关闭/复制选区/粘贴/回到底部；各项原testTag、禁用条件、controller调用、copy/paste实现语义不变。关闭标destructive。selectionScope至少覆盖bindingEpoch、selectionEpoch、selectedKey；作用域变化关闭popup并废弃待调度action。菜单打开或关闭都不启动、不关闭PTY。键盘导航/Escape及action关闭后调度复用共用menu。动作不可因resize/轮询重复执行。
- 持续显示真实Unknown、Unavailable、load/error及非成功mutation回执；成功回执不占常驻正文。移走Blink工程说明；不添加假清理、重启、Source directories、Cloud状态行。真实Exited保留紧凑状态和screen/history。
- PtyTerminalGrid滚动条10dp宽，track采用当前主题Surface，thumb Border，hover/active BorderStrong。保留指针定位/拖动、scrollback与selection算法、绘制native列、cursor及size测量。固定13sp/18sp字体与empty viewport合同保持，本批不接Appearance消费者。正文start16/top8/bottom12保持。

## 受保护功能与定向验证

旧六个页面测试名称、所有业务/字节/身份/计数/生命周期断言、真实fixture、独立literal期望和kernel resize检查保留。仅因操作移入菜单改变点击/禁用断言路径；新增可复用open/actions helpers，不能用直接controller调用代替用户鼠标/键盘动作。historySelectionCopyAndExitedScreenRemainVisible全部selection/copy/writes/exited/history断言保留，菜单打开需关闭后再检查input焦点。capture继续严格校验真实selector Running与Exited(7)、主题背景、完整key/revision及控件几何，允许捕获控件集合变更为实际可见selector/create/actions。

新增一项 terminalActionMenuUsesRealPointerKeyboardAndScope：真实鼠标打开菜单；无shell时disabled项与Escape关闭且creates=0；键盘选择刷新后popup已移除且不会新建/关闭；真实创建后保留操作可达。已有ownerSwitch用例可补popup被销毁检查，不削弱原owner与其他面板断言。通过现有menu公共能力实现scope失效，不为了测试加入生产假服务。

原10张完整页面PNG保持：1440x900与600x800、浅深真实Running/Exited共8，controlled Unknown/Unavailable各1。新增1440浅色与600深色menu图及geometry，检查anchor/menu视口包含和disabled项，不用popup根capture掩盖完整页面。契约分清真实PTY状态与controlled异常。

父线程将串行运行Page7、controller8/grid4/bridge4、Shell8、ConnectionIntegration4，共35项及完整JVM build。不再运行全部184，除非本批发现具体服务风险。

## 交付

写 /tmp/harness-terminal-local-visual-delivery-20261010.json 和 .md。JSON含model/provider、baseline_sha256、contract_sha256、files[三路径before/after SHA]、tests_run/builds_run/screenshots_run/native_execution_run=false、write_set_released/source_writes_stopped=true、changed_summary、limitations。before输入与基线位置由派发prompt给出。说明哪些是ASAR证据、哪些是Harness适配，不称原版像素/原生窗口/IME/Remote验收已完成。
