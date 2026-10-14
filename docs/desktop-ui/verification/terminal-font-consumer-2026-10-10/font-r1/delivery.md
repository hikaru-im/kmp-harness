# 终端字体消费者 worker 交付（2026-10-10）

实施模型：SHUAIAPI/gpt-6.1-sol；provider：opencodex。

## 实施

- PtyTerminalGrid.kt 新增共享 internal 字体 metrics；消费 LocalHarnessCodeTextStyle 的 size/family/weight/style/lineHeight，使用实际测量高度与声明行高的较大值，不额外乘 UI 字号缩放。普通 glyph 继承主题 weight/style，ANSI Bold/Italic 仅覆盖对应属性。
- PtyTerminalPanel.kt 的空视口与 grid 共用 metrics 与 10dp scrollbar gutter。保存真实 IntSize；尺寸未知时不提交临时最小 size；字体、density、空视口 bindingEpoch 或 grid 完整 terminal key 改变时通过稳定 callback 重新提交 viewport。未改 reading/input bridge 的 remember keys。
- 新增 PtyTerminalFontInteractionTest.kt，仅包含合同指定的三个测试：emptyViewportUsesSavedFontBeforeFirstRealCreate、savedFontChangeRefitsSameRunningPtyAtFixedBounds、codeFontFamilyAndStyleRenderWithoutAdditionalUiScale。测试使用真实 PTY fixture、独立字体测量/参考绘制与 kernel size/输入字节断言，包含 finally 有界清理。
- 第二个测试编写了两组固定尺寸/浅深主题的完整页面 before/after PNG 与 geometry JSON 输出逻辑，目标目录 /tmp/harness-terminal-font-captures-20261010。本 worker 未执行该逻辑，未生成截图。

## 静态证据

- baseline.json SHA256：799946a51b42a27a739a5ebc2c5f6c69654ae056eff9b8fa58a4370c2ef473b8。
- 合同 SHA256：120f3ff984633a187f1af4bd761517de83d4e513c79a8654de6659c8e5ca493c。
- 三文件的 before/after SHA256 记录在同名 JSON 中，新测试 before_sha256 为 null。交付前再次核对源码哈希一致。
- 对 2,253 个 baseline inputs 进行只读哈希核对，仅合同允许的两个既有生产文件变化，无缺失；新增测试属于授权写集。全部既有 dirty/untracked 已保留。
- 已复核生产 diff、三个固定测试名、测试预期未调用生产 metrics helper、新截图目录、源码词法/字符串转义、尾随空白与最终 LF；git diff --check 通过。
- 未编译或进行类型检查。静态检查不能证明动态行为或像素一致。

## 验证与释放

- tests_run / builds_run / screenshots_run / native_execution_run 均为 false。未运行 build/test/kotlin/gradle/amper/screenshot/native app/PTY fixture。
- write_set_released / source_writes_stopped 均为 true。交付完成后 worker 停止源码写入并退出。
- 父线程待 worker 退出后冻结源码并独立串行验收：新增3、既有页面7/controller8/grid4/input bridge4/真实连接4，共6类30项；完整 JVM build；截图与关键 diff 复核。动态结果均待父线程验证，不混入旧PTY184项历史证据，也不宣称原版像素一致或 native IME 验收。
