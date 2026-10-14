# 批次 AE 证据：定时任务模板卡虚线描边与标题节奏（生产改动）

- 日期：2026-10-11
- 写集（生产 3 个文件）：
  - `modules/ui-foundation/src/im/hikaru/harness/ui/foundation/HarnessComponents.kt`：`HarnessSurface` 新增 `dashed: Boolean = false` 与 `radius: Dp = HarnessTokens.Corners.Medium`；实心分支（`Surface` + `BorderStroke`）完全不变；虚线分支用 `Box.clip(shape).background(fill).drawBehind` 画一条 1px 3-on/2-off 的 `Path` 圆角矩形描边，并补 `CompositionLocalProvider(LocalContentColor provides Text)`。
  - `modules/ui-foundation/src/im/hikaru/harness/ui/foundation/HarnessTokens.kt`：新增实测令牌 `Corners.Card = 22.dp`。
  - `apps/shared/src/im/hikaru/harness/client/scheduling/ScheduledPageLayout.kt`：模板卡改用 `dashed = true, radius = HarnessTokens.Corners.Card`；落地标题块包进 `Column(spacedBy(HarnessTokens.Spacing.One))`（改前是外层 12dp rhythm）。
- 关联：W（模板栅格几何 359/gutter 20/738）、AC（`BorderSubtle` 收口）
- 目录：`docs/desktop-ui/verification/scheduled-card-outline-2026-10-11/entry-ae1/`

## 问题

W 批把落地栅格几何对齐了（卡 359 宽、gutter 20、grid 738），但把卡外观记为「内容驱动」而没做描边本身。参考 `scheduled-tasks.png` 的模板卡其实是**虚线**外框、且角比我们的实心 8px 圆角明显更圆。本批收口这两点，并顺带修掉落地标题块比参考松 8px 的行距。

## 实测（参考图带 Display-P3，先转 sRGB 再读）

| 卡外观 | 参考 `scheduled-tasks.png` 卡1 | 我们改前 | 我们改后（`Corners.Card = 22.dp`） |
| --- | --- | --- | --- |
| 盒 | x1080..1438 y614..723（359×110） | 359×66 | 359×66 |
| 顶行 dark 比例 | 196/367（`####.####..###..###…`） | 267/367 实线 | 259/367（`####.####.####…`） |
| 顶边角内缩 | 18 / 18 | 13 / 13 | 17 / 17 |
| 左边角内缩（上/下） | 19 / 18 | — | 17 / 1 |
| 圆角拟合（切线约束，robust） | 22.94（TL）/ 22.21（TR） | — | 21.94 |

- 参考是虚线描边，我们改前是实心发丝线 → 本批引入 `HarnessSurface(dashed = true)`。
- 圆角用「弧与两条边相切」做约束拟合 `(x-r)² + (y-r)² = r²`，只取落在角弧上的点（`x≥2, y≥2`）、取各点正根的中位数，**对虚线相位不敏感**（相位只删样本、不改 min-x）。参考两个角读到 22.94 / 22.21（中位 22.6），我们 22dp 的路径读到 21.94 —— 相差 <1px，落在该方法 <1px 的分辨率内。22dp 同时等于 asar 记录的 `--composer-radius: 22px`，故取 22。顶边 dark 宽度代理法（相位敏感）读出的 18 vs 17 与本结论一致。

## 落地标题节奏（`Spacing.One`）

| 行 | 参考 ink 高 / 上方间隙 | 我们改前 | 我们改后 |
| --- | --- | --- | --- |
| 时钟 | 48 / — | 48 / — | 48 / — |
| 标题 | 17 / 16 | 17 / 16 | 17 / 16 |
| 副标题 | 13 / **11** | 13 / **19** | 13 / **11** |

外层 12dp rhythm（`spacedBy(12.dp)`）在标题→副标题之间画出 19px ink 间隙；参考是 11px。把三段包进 `spacedBy(Spacing.One = 4.dp)` 后，`HarnessText` 的 lineHeight 余量把 ink 间隙压到 11，与参考一致。

## 验收

- 定时页捕获类 `ScheduledPageScreenshotTest` **3/3**，288 文件写入 `docs/desktop-ui/captures/scheduled-card-outline-2026-10-11/`（`{landing|empty|confirmed|detail|…}-{light|dark}-{1440x900|600x800}-{viewport|history}.{png,geometry.json}`）。
- `ui-foundation` 模块 **53/53**。
- 全量 `jvm-app` **647 found / 5 skipped / 642 started / 641 passed / 1 failed**（exit 1）。唯一失败是 `PtyTerminalFontInteractionTest.savedFontChangeRefitsSameRunningPtyAtFixedBounds`：`ComposeTimeoutException: Condition still not satisfied after 30000 ms` —— 即 Z / AB 两批记录的同一类真实 PTY IO 就绪门在满载下的环境余量抖动，该用例渲染终端字体面板、不渲染任何定时页，与本批改动无关。隔离重跑该类 **3/3** 通过（exit 0，`logs/pty-font-isolated.log`）。
- 重跑 `measurement/measure-scheduled-card.py`：参考角 22.94、我们 21.94；顶边内缩 18 vs 17；标题 ink 间隙 16 / 11 一致。

## 边界与残留

- 参考卡是**内容驱动**更高（110 vs 66）：参考卡带一个前导 emoji 图标 + 两行描述，我们没有图标、描述单行。`HarnessIconName` 里没有 robot / microscope / envelope / money 等字形，故前导图标本批故意不做；这是内容差异不是描边缺陷。
- 参考副标题文案不同（「ChatGPT 可以帮你处理持续性任务…」vs 我们「安排工作区中的重复工作。」）；我们比参考多两行（「还没有定时任务。」「示例模板 · 点击编辑，不会保存或运行」，gaps 17 / 18）。
- 顶行 dark 比例 259/367 vs 参考 196/367：两边的标称 dash 都是 3-on/2-off，差在描边的抗锯齿——Skia 把我们 1px 的 dash 画得偏厚、Chromium 把参考画得偏薄。记录为渲染差异，不按它反推改标称 dash（那会是拿参考的 AA 去过拟合我们的描边）。
- 圆角残余 <1px，在本方法分辨率内；不认为需要继续收。
- `HarnessSurface` 是全应用共享控件，但新增参数默认值保持原实心行为、实心分支逐行未改；只有定时页模板卡新用了虚线分支。给 `HarnessSurface` 加参数并改 `ScheduledPageLayout` 之后，旧的 scheduled 捕获目录（`scheduled-page-2026-10-10/`、`-w1/`、`scheduled-execution/`）相对当前代码已过期，新的 `scheduled-card-outline-2026-10-11/` 取代它们。
- 本批是几何 / 结构对齐，不是像素一致；无原生窗口组合（本机无屏幕录制权限）。各批证据独立，整体目标 active。

