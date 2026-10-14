# 终端输出阅读：当前源码与 ASAR 分析

日期：2026-10-08。状态：分析完成，尚未实施或验收。本次读取当前安装包，不复用 2026-10-07 的资源偏移。

## 原版证据

`terminal-reading-analysis-2026-10-08.evidence.json` 保存安装包、九个资源的 SHA256/范围，以及直接支持结论的原始短片段。归档来源为 `/Applications/ChatGPT.app/Contents/Resources/app.asar`，561,986,433 字节，payload 起点 5,808,772。

1. `local-conversation-background-terminal-tab-ac79f8e2324b.js` 优先读取 commandExecution 的 aggregatedOutput，回退到真实 terminal session snapshot buffer；后者通过 useSyncExternalStore 订阅。非空输出交给 XtermOutputPanel，空串显示 No output yet。这不能证明 Harness 已具备订阅能力。
2. `xterm-output-panel-3299039ae01e.js` 使用只读 Xterm、FitAddon、WebLinksAddon，disableStdin=true、lineHeight=1.2。首个已有缓冲写入后安排滚到底部。
3. 更新前先执行 `n=re(e)`：缓冲为旧内容前缀时只写增量，否则 reset 后重写；只有更新前的 n 为真才安排 scrollToBottom。
4. import/export 对应确认 re 是 display helper 的 x；其实现为 `viewportY >= baseY`。**原版检查已在底部，并没有数十像素的“接近底部”容差。** 先前简述“接近底部”在此纠正。
5. ResizeObserver 调用 fit；主题、字体、host 变化涉及不同 effect。未运行原版交互，本次不推断其非前缀重置后的精确阅读锚点，也不将 Xterm 行重排效果当作 Compose 的能力。

## Harness 当前行为

先经 CodeGraph 读取 ThreadPanels、模型、state、controller、repository；对应 SHA 与片段记录在 evidence JSON。

| 当前行为 | 源码依据 | 本批结论 |
| --- | --- | --- |
| 长行可横向阅读 | BackgroundOutputPanel 的 HarnessText.horizontalScroll | 已有能力，不能把截图右缘截断说成“没有横向滚动” |
| 刷新后位置丢失 | 两个 rememberScrollState 都位于 Ready 分支；BeginTerminalLoad 切到 Loading | 同进程读取期间正文卸载，局部展示状态被释放 |
| 无输出跟随 | 普通 verticalScroll，没有依内容变更滚到底部的逻辑 | 需要依据用户阅读位置做窄修复 |
| 真实数据已接入 | 有真实 inventory、精确 process key、revision fence、输入/EOF/Cancel | 沿现有回调和服务保留，不加入运行时示例进程 |
| 当前是有限读取 | refreshTerminal 的 repository.readTerminalSelection | 输出订阅和轮询需要独立生产契约，本批不冒充实时推送 |
| owner 有独立 incarnation | controller bind 更新 epoch，state.ownerGeneration 随之更新 | 展示 key 必须包含 ownerGeneration，不能只含可见 host/session |
| process key 含 incarnation | BackgroundTerminalKey(scope, processId, producerIncarnation) | 同 PID 的新进程不能继承旧阅读位置 |

## 推荐切片

先修复真实输出的阅读状态：同 owner/进程刷新保留两个轴的位置，初次显示及用户已在底部时跟随新输出，用户向上阅读时保持位置。所有异步跟随必须随 owner/进程/视图关闭失效。继续使用原有真实数据和操作回调。

窄契约见 `terminal-reading-contract-2026-10-08.md`。本批是 Compose plain-text 阅读适配；1px 底部容差、非前缀缓冲的 clamp 策略都明确属于 Harness 选择，不宣称原版 Xterm 行为一致。PTY、ANSI、resize RPC、实时订阅及子 Agent 历史复用单独排期。

消息区域暂时仍是父线程冻结副本 33/33 通过，worker 的旧交付报告未释放当前测试文件；终端分析不改变该验收状态。
