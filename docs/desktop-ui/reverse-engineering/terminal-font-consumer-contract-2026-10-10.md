# 终端字体消费者合同 R1（2026-10-10）

实施模型：SHUAIAPI/gpt-6.1-sol，provider opencodex。分析已完成，见 terminal-font-consumer-analysis-2026-10-10.md。

## 唯一源码写集

- `apps/shared/src/im/hikaru/harness/client/panels/PtyTerminalPanel.kt`
- `apps/shared/src/im/hikaru/harness/client/panels/PtyTerminalGrid.kt`
- 新增 `apps/jvm-app/test/im/hikaru/harness/desktop/PtyTerminalFontInteractionTest.kt`

保留全部现有 dirty/untracked 文件；禁止 reset/stash/clean/checkout/commit/push、修改用户配置、干预其他进程。理解代码先 CodeGraph；索引存在归档同名副本，无法唯一定位时读取上述实际生产路径。正在继续同一授权任务，Comet probe 已为 none，不重新进入流程。

## 生产要求

1. 从 LocalHarnessCodeTextStyle 消费代码 size/family/weight/style 和 lineHeight，不能额外乘 UI 字号缩放；共享测量入口可放 Grid.kt 的 internal helper。不改共享 Theme 或设置接口。
2. 空视口与 grid 按同一 metrics、同样 10dp scrollbar gutter 测量。首次真实尺寸未知时不提交 5×2 临时 size。字体或 density 改变但面板 IntSize 不变时，重新计算并调用现有 controller.viewport。
3. 空视口绑定 epoch 改变也重新提交；grid terminal key 改变重新提交。稳定 callback 与 effect keys；不得用整份 snapshot revision 或不稳定 callback 作 key，导致每轮 polling 发 resize。
4. 普通 glyph 保留主题 weight/style；ANSI Bold/Italic 对应覆盖；其它 ANSI 属性、宽字符 columnOffsets、cursor、scrollbar、输入、选区、history 和菜单不改变。字体改变不重建/关闭 PTY，也不重建 reading/input bridge。
5. Unknown、Unavailable、Exited、owner 切换仍经过已有权限/完整身份检查；不加假数据或本地回执。

## 三个新增测试（固定名字）

新 class `im.hikaru.harness.desktop.PtyTerminalFontInteractionTest`，仅以下三个 @Test：

- `emptyViewportUsesSavedFontBeforeFirstRealCreate`：真实 RealPtyPageFixture + controller/页面，未创建时在固定面板内保存代码字号 12→20，独立验证 viewport 行列变化；creates/closes/input 均为0。真实鼠标点击创建后，初始 PTY/kernel size 使用更新后的 viewport；仅创建一次。
- `savedFontChangeRefitsSameRunningPtyAtFixedBounds`：完整 HarnessApp、真实 PTY；1440×900 浅色与 600×800 深色，各自保存代码字号 12→20；根/终端正文 bounds 不变，columns/rows 减小，完整 key 与 Running identity 保持，creates=1/closes=0。通过真实输入触发 PTY 内 stty size 并读取文件独立确认 kernel 与 snapshot 同步，精确输入字节仍正确。每组合保存 before/after 完整页面 PNG 和 geometry JSON，共4图；非仅 popup/grid crop。截图使用新目录 `/tmp/harness-terminal-font-captures-20261010`，不能使用旧批 capture 目录。
- `codeFontFamilyAndStyleRenderWithoutAdditionalUiScale`：从真实 PTY 获得含普通与 ANSI Bold/Italic 的 snapshot；固定尺寸组件使用保存的浅/深代码字体族/样式，验证真实绘制与字体设置有对应变化，普通字形不被 Normal 覆盖，ANSI 强调保持；字号 8/24 边界无裁切/异常；UI 字号 11→16 在固定 viewport 内不额外缩放代码 metrics。预期从独立 Text/字体测量或参考文字像素获得，不调用生产 metrics helper充当预期，不仅断言 settings 值。

可复用现有 internal RealPtyPageFixture；不修改 fixture、旧页面测试、测试名称或原有独立业务/身份/字节/kernel 断言。所有新测试保证 finally 有界清理真实所属进程。无需扩大测试矩阵；以三个具体风险为准。

## 工作者交付

工作者只实施和静态检查，禁止运行 build/test/kotlin/gradle/amper/screenshot/native app/PTY fixture。父线程待工作者 exit0、释放写集后，冻结源码并独立串行动态验收。

交付 `/tmp/harness-terminal-font-delivery-20261010.json` 和 `.md`，JSON 字段：model、provider、baseline_sha256、contract_sha256、files（三路径的 path/before_sha256/after_sha256；新文件 before_sha256=null）、tests_run/builds_run/screenshots_run/native_execution_run 全 false、write_set_released/source_writes_stopped 全 true。交付后停止写入并退出。

父验收：新增3、既有页面7/controller8/grid4/input bridge4/真实连接4，总6类30项；完整 JVM build。旧PTY184项是独立历史证据，不混入本批数字。复核截图与关键 diff，更新计划和 readiness；未做原版同状态截图或 native IME，不宣布像素一致。
