# 本地终端视觉分析：2026-10-10

目标是复刻已确认范围内的本地终端，实施模型固定 SHUAIAPI/gpt-6.1-sol / opencodex。分析依据当前本机 app.asar 和已验收的真实 PTY 页面；没有原版同状态窗口截图。

## 当前原版证据

App 26.1002.52244，build 13536；app.asar 561,986,433 bytes，SHA256 `797be85cd4c5b1be359c55c1d57970671cfba284a5eacf7612ee0ae9add1c360`。六个提取 asset、独立 SHA、size 和 absolute offset 见 `terminal-local-visual-source-2026-10-10/manifest.json`。10-07 的旧 archive 证据属于历史版本。

| 来源 | 当前可证事实 | 边界 |
| --- | --- | --- |
| terminal-panel JS | 根为 relative flex h-full w-full flex-col；正文 start16/top8/bottom12，另有 loading overlay 与 initial prompt hint | 本 asset 没有当前 Harness 的六按钮常驻行；不能据此断言所有外层菜单均不存在 |
| terminal-panel CSS | scrollbar 宽高10px；track用terminal background，thumb用border，hover/active用border-strong；前景、背景及selection来自主题 | Harness 对应 semantic token 映射，数值尚无同状态原图证明 |
| terminal-tab JS | durable host 走 Cloud 分支，其余走本地 qe；本地 renderSlots headerRows为空，根 flex h-full min-h-0 flex-col | 本批只采用本地分支；外层tab chrome未测量 |
| local分支提示 | workspace不匹配时显示可Dismiss/Open new terminal的警告；多workspace且尚未交互时显示Source directories及目录切换快捷键 | Harness缺少同语义workspace来源和切换服务，本批不凭空添加 |
| background-terminal-tab | 无输出显示No output yet，有输出用XtermOutputPanel | 背景输出与交互PTY使用不同renderer，不能混为一种状态 |
| thread-panel-toggle-button | ghost/toolbar，pressed secondary，aria-label/pressed，tooltip及shortcut | 未证明实际按钮数值尺寸 |

原版 fontSize 消费 codeFontSize，fontFamily 消费主题；Harness当前 grid 与 empty viewport 固定13sp/18sp。字体消费者需单独与Appearance接线核对，本批保持既有测量和resize合同。原版renderer错误的Reload重建renderer，不是重启shell；Harness没有对应renderer crash服务，本批不添加伪重启。Cloud 的 quiet status、Shell exited、Input may be incomplete、Retry 行不移植。

## 当前 Harness 差异与本批决策

已验收 R5 的真实 inventory、完整身份、PTY输入、kernel resize、history选择复制、退出保留及显式关闭保持。当前六按钮FlowRow、独立selector行、每次成功回执及Blink常驻工程说明占用了正文高度，窄窗口尤其明显。

采用Harness适配的一行紧凑操作区：真实selector、显式新建icon、更多操作菜单。刷新、关闭、复制选区、粘贴、回到底部收进既有typed HarnessMenu；按当前能力禁用并绑定完整selection scope。正常成功回执与Blink实现说明退出常驻正文，失败/Unknown/Unavailable继续可见。真实Exited仍保留简洁状态和最终屏幕。这个操作区是基于现有能力的适配，尚无证据称其与原版外层chrome像素相同。

滚动条使用当前主题Surface/Border/BorderStrong，保持10dp宽、指针拖动、viewport及history算法。正文start16/top8/bottom12保留。两份生产源码和一份受影响页面测试为本批有限写集；controller、Host/Gateway/Local、PTY、输入bridge及其他页面均保持。

动态验收覆盖现有页面6项及一项菜单键盘/关闭后调度检查，shared controller8/grid4/bridge4、完整壳层8、真实Connection集成4，共35项，完整JVM build。原10状态完整页面图维持真实Running/Exited及受控Unknown/Unavailable；另补宽窄菜单图，正常页面可用正文高度与未裁切控件须实际核对。
