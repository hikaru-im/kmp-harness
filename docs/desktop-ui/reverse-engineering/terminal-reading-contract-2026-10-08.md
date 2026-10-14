# 终端输出阅读窄实施契约

日期：2026-10-08。工作者：用户指定 SHUAIAPI/gpt-6.1-sol，provider=opencodex。父线程负责分析、审阅、冻结和串行验收。

## 写集

生产只允许 `apps/shared/src/im/hikaru/harness/client/panels/ThreadPanels.kt` 中 BackgroundOutputPanel、ThreadPanels 的展示状态接线及必要的局部 helper/import。测试只允许新增 `apps/jvm-app/test/im/hikaru/harness/desktop/TerminalReadingInteractionTest.kt`。保持现有 BackgroundOutputPanel 调用兼容。

不改 controller/repository/reducer/传输、进程选择和输入/EOF/Cancel 行为，不改共享滚轮 helper，不创建工作者，不运行构建或测试，不改 Comet/config/git 状态，不覆盖其他 dirty 文件。

## 阅读状态

1. 使用静态 typed Compose 状态。生产阅读身份至少包含 state.scope、state.ownerGeneration、完整 BackgroundTerminalKey；terminalGeneration/revision 不是身份。remember 的位置必须让同 key 的 Loading/Error 及其他 tab 的选择不销毁阅读状态。
2. 同 owner/精确 key 的 Ready → Loading → Ready/Error → Loading → Ready 保留纵向与横向位置。Loading/Error/Unavailable 仍显示原有明确状态，不把旧输出伪装成当前 Ready，也不从 retained buffer 构造成功结果。状态保留的是阅读位置及必要的局部比较数据。
3. ownerGeneration、host/session、selected processId 或 producerIncarnation 改变时重置；即使缓冲完全相同，也不能复用旧位置或异步跟随。关闭 BackgroundOutput tab 释放该展示状态，重开是初次显示；仅切换到 Subagents tab 保留。原有输入草稿规则不变。
4. 首次非空 Ready 显示到输出底部，横向从 0 开始。已在底部时，新增/变更输出后等待真实布局再滚到新底部；已向上阅读时保持原纵向像素位置。Harness 允许至多 1 个实际像素的舍入容差；原版是 viewportY>=baseY。不得加入大的“近底部”阈值。
5. 决策应依据变更前的已测位置与最大范围，不能由追加后的新 maxValue 把原来的底部误认为向上阅读。也不能被卸载/重挂或 maxValue 的瞬时值误导。revision 增加但 buffer 相同不能把用户拉到底部。
6. 跟随等待期间的真实用户向上滚动必须撤销跟随；owner/key/关闭变化必须取消待执行效果。不要只靠固定 sleep、无限循环或读取旧 closure。
7. 前缀追加且用户向上阅读：纵向保持（允许 1px 舍入），横向保持。非前缀替换/源截断：正文只显示新的完整 buffer，向上阅读者保留旧像素 offset 并 clamp 到新合法范围；底部阅读者跟随新尾部。这是明确的 Harness 策略，不是 Xterm 锚点复刻。
8. 同身份 resize：向上阅读者保留两个轴并按新范围 clamp；底部阅读者随新范围保持底部。没有 PTY resize RPC。保留长行横向滚动、SelectionContainer、完整原始文本、truncated 提示以及 No output yet 仅用于 Ready 的空 buffer。
9. 不引入输出轮询/订阅/进程写入，不生成样例数据，不添加虚构生命周期。测试 fixture 仅在测试文件中。

## 必要验收

新 suite 必须使用生产 ThreadPanels，重要刷新路径通过真实 ThreadPanelsController 与测试 repository 的延迟响应，确认不是测试旁路自建展示组件。正常选择/点击/滚轮使用真实 performMouseInput/performKeyInput 和测得 root/node bounds；不要用 performClick/performScrollTo/scrollTo 直接改变状态来替代用户动作。可读取公开滚动语义或保存测量诊断来断言两轴位置；复用已有实际 wheel 单位和 helper。

覆盖：

- 首次长输出到尾部；用户向上滚动后追加保留，滚回底部后追加跟随。
- 同 key 的 Loading、Error/retry 重挂后两轴保留；buffer 相同而 revision 增加不抢阅读位置。
- 真实长行横向滚动到末尾标记，输出全文不省略。
- 非前缀替换/截断的 clamp、新文本与旧文本清理；空输出仍是 Ready，Unavailable/Error 不当作空成功。
- 相同 buffer 下的 host/session/ownerGeneration/process/incarnation 变化；切换 tab 保留、关闭重开重置。
- 延迟跟随期间向上滚动、owner 切换与关闭，证明过期 effect 不影响新视图。
- 同身份面板 resize 的 clamp/跟随和紧凑窗口操作可达性。

截图包含 320×600 与 640×800、浅深色、尾部/向上阅读/横向末尾三类状态；使用 HARNESS_TERMINAL_READING_CAPTURE_DIR，每张保存尺寸、SHA、实测 viewport/text/scroll 几何及全文末尾证据。使用 10s deadline，不吞异常、skip 或静默退出。截图是测试局部真实 UI，不能证明原版像素、原生窗口或远端一致。

完成有限写集 diff --check，将 before/after SHA、实际模型/provider、变更路径、新测试数和类名、tests_run=false、builds_run=false、write_set_released=true 写到 `/tmp/harness-terminal-reading-delivery-20261008.json` 与 `.md`。释放后停止改写。父线程再冻结输入，逐类串行运行新 suite、既有 ThreadPanelsInteractionTest 和 HarnessAppThreadPanelsShellTest；出现具体风险才扩展验证。
