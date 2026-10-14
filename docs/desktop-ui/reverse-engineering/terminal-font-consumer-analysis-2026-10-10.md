# 终端字体消费者分析（2026-10-10）

## 原版证据

来源是本机 ChatGPT 26.1002.52244 / build 13536 的 app.asar，SHA256 `797be85cd4c5b1be359c55c1d57970671cfba284a5eacf7612ee0ae9add1c360`。本轮已按 manifest offset 重新读取六个 asset，均与 `terminal-local-visual-source-2026-10-10/` 中归档字节相同。

`terminal-panel-40da68951f9e.js.txt` 的关键链路：

- offset 10033 附近：`O=m(n.codeFontSize)`；从主题生成代码字体 family `M=ue(k.fonts)`，以及需要加载的字体 A。
- offset 11681 附近：renderer options 初始传入 `fontSize:ye.current`、`fontFamily:ve.current`。
- offset 14364 附近：以 `[M,O,A]` 为 effect 依赖，等待字体加载 `ae(A)` / `ce(M,O)`，更新 renderer 的 `fontFamily=M` / `fontSize=O`，下一帧执行 `Se(...)` 或 `fit()`。清理取消过期异步结果，且重新确认 renderer identity。

这支持“代码字体与字号是终端的真实输入，变化后重新 fit”。该 asset 没有显式指定 lineHeight/fontWeight/fontStyle/letterSpacing；本批不能把 Harness 的行高策略称为原版 xterm 的像素参数。

## Harness 当前链路与缺口

1. `AppearanceSettings`：默认 UI 字号 14、代码字号 12；代码字号范围 8..24。浅/深模式分别保存 codeFont 和 codeFontStyle。
2. `AppearanceRepository.save`：store.write 成功后才发布 settings；失败保留当前主题。`HarnessApp` 使用保存状态的 themeCustomization。
3. `HarnessTheme`：主题 density 的 fontScale 是 base.fontScale × uiFontSize/14；`LocalHarnessCodeTextStyle` 已用代码字号除以同一个 UI scale，行高为 codeFontSize × 1.5 / scale，因此消费者不能再次乘 UI scale。已有代码字体 family、weight、style 可直接使用。
4. `PtyTerminalGrid` 与 `PtyEmptyViewport` 仍各自固定 Monospace / 13sp / 18sp。正文绘制又把无 ANSI Bold/Italic 的普通 glyph 强行置为 Normal，丢失主题代码样式。
5. 两个 viewport 都只通过 onSizeChanged 重新计算。字体 metrics 变化但容器尺寸不变时不触发真实 resize。
6. `PtyTerminalController.viewport` 已保存初始创建 size，且 resizeSelected 会比较 queued/inflight/attempted/confirmed size，避免重复发送；Unknown/Exited 等不能输入的状态不会 resize。无需修改服务、controller、协议或 PTY。
7. bind 会重置 controller viewport，故空视口的 effect 还须包含 bindingEpoch；grid 的 effect 必须包含真实 terminal key，确保相同行列的终端切换仍提交当前 viewport。不能把字体变化当作新终端 identity。

## 实施决定

复用 LocalHarnessCodeTextStyle；空视口与正文使用同一测量入口，保存实际 IntSize，并从 IntSize、文本测量结果和 density 推导行列。首次尺寸未知时不发布临时最小 size。字体变化重新测量并调用已有 viewport 路径；回调用 rememberUpdatedState，避免不稳定 lambda 触发无穷 effect。

行高沿用共享代码文本样式的 1.5 策略，并取实际文本测量高度与声明行高的较大值；此为 Harness 策略。普通 glyph 保留配置的 weight/style，ANSI Bold/Italic 只覆盖对应属性。列映射、输入、历史、选区、菜单与完整 owner/key 隔离保持现有行为。

验收关注固定面板尺寸时字号变化、未创建阶段的 size、真实 kernel stty size、字体 family/style 的实际绘制，以及 UI 字号不额外缩放代码字号。使用真实 PTY fixture；不声明原版同状态像素一致或原生 IME 验收。
