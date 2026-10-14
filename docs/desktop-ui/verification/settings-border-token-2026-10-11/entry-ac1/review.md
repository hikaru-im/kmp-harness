# 批次 AC 证据：设置页细节描边令牌（生产改动）

- 日期：2026-10-11
- 写集：`modules/ui-foundation/src/im/hikaru/harness/ui/foundation/HarnessTokens.kt`（`HarnessLightColors.BorderSubtle` 一行 + 三行注释）
- 关联：AA（设置页右侧控件几何）、AB（PTY 页超时余量，无生产改动）
- 目录：`docs/desktop-ui/verification/settings-border-token-2026-10-11/entry-ac1/`

## 问题

AA 结案后仍留一个跨页面残留：设置卡片外框、行分隔线、控件描边在原版截图里实测 `#efeeef`（238），我们是 `#f5f5f5`（245），差 7。三处都来自同一个令牌 `BorderSubtle = Color(0x147F7F7F)`（gray-127、alpha 8%）。

## 证据

### 参考图（先转 sRGB）

参考截图带 Display-P3 ICC。实测：卡片顶边、行分隔线、卡片左边、控件描边在原始 P3 下都是 `#eeedee`，用 perceptual / relative / absolute 三种意图转 sRGB 后都是 `#efeeef`/`#eeeeef`。中性灰在 P3→sRGB 下不偏移，所以 238–239 是真实目标值，不是转换伪影。

### 当前 app.asar

当前安装版 `/Applications/ChatGPT.app/Contents/Resources/app.asar`（本次实测 SHA-256 `797be85c…c360`、561986433 字节；AA 记录的基线是另一组 SHA/大小，即 asar 已更新）里：

- `--color-border-subtle:var(--lightningcss-light,var(--alpha-05))var(--lightningcss-dark,var(--alpha-06))`
- `--alpha-base:var(--lightningcss-light,#0d0d0d)var(--lightningcss-dark,#fff)`
- `--alpha-06:color-mix(in oklab, var(--alpha-base) 6%, transparent)`

即 `--color-border-subtle` 在浅色下是 `--alpha-base`（`#0d0d0d`，与我们的 `Text` 同值）的 5–6% 淡染，而不是 gray-127 的 8%。按 alpha 合成公式 `brightness = 255 - (255 - base) * alpha`：

- gray-127 @ 8.0% → 245（旧令牌）
- gray-127 @ 13.3% → 238（新令牌 `0x227F7F7F`）
- 参考截图 → 238

### 为什么同一令牌在轨道端读到不同值

同一条 `BorderSubtle` 在卡片上（白底 255）复合成 245，在轨道端边（`#f9f9f9` 底 249）复合成 239——alpha 合成结果取决于底色。原版轨道端边实测 229，比它的卡片更深，说明原版那里用的是更强的令牌（`AppShell.kt` 注释也写 Codex 用 `border-e-hairline border-default`），不是同一支。故本批只改共享令牌强度，不动轨道端边的令牌归属。

## 改动

浅色 `BorderSubtle` 由 `0x147F7F7F` 改为 `0x227F7F7F`（白底合成 238）。深色令牌不动（无深色参考实测）。

## 实测对照（`measurement/measure-borders.py`，输出 `border-measurements.txt`）

| 表面 | 参考 | 本批后 | 差 |
| --- | --- | --- | --- |
| 卡片顶边 | #efeeef | #eeeeee | -1 |
| 行分隔线 1 | #efeeef | #eeeeee | -1 |
| 行分隔线 2 | #efeeef | #eeeeee | -1 |
| 卡片左边 | #efeeef | #eeeeee | -1 |
| 控件描边 | #eeeeef | #eeeeee | 0 |
| 轨道端发丝线 | #e5e5e5 | #e8e8e8 | +3 |

白底三面全部 Δ≤1（改前 Δ7）；轨道端边 239→232（改前 Δ10→Δ3）。

## 圆角 9 vs 10 的结论（本批不改）

AA 留的未决项。直接对照同型控件：我们的行级下拉标称圆角 9 拟合 10.00–10.25，参考同型下拉也拟合 10.00，左缘剖面逐行一致（`[7,5,3,2,2,1]`）。同一拟合在已知标称值上偏高约 1（我们的开关标称 9.5 也拟合 10.00，参考开关标称 10 同样拟合 10.00）。故参考的真实圆角 ≈ 9，`Corners.Control = 9.dp` 维持不变；asar 的 `--radius-lg:10px` 不适用于 28px 行级控件。复跑命令见 AA 的 `measure-controls.py`。

## 验收

- 设置页捕获类 `GeneralSettingsInteractionTest` **6/6**（新截图写入 `docs/desktop-ui/captures/settings-border-token-2026-10-11/`）。
- `ui-foundation` 模块 **53/53**。
- 全量 `jvm-app` **647 found / 5 skipped / 642 passed / 0 failed**（exit 0；104/105 容器成功）。这一次全量同时覆盖 AA、AB、AC 三个批次（AB 此前无全量）。
- 重跑 `measurement/measure-borders.py`：PROBLEMS 0。

## 开关高度 19 vs 20（本批复核后维持 19）

AA 把「开关高 19 vs 20」记为残留，但那次是拿参考的 **ON**（实心 32×20）对我们的 **OFF/禁用**（浅底 + 描边）比，状态不同口径。本批同口径复核：

- 参考页 8 个开启开关全部 32×20。其圆角帽剖面与「轨道高 20、半径 10」自洽：x=1773 处自 y292 到 y311 恰 20 行满色、上下邻行纯白；x=1762 处 16 行，正合半径 10 的帽形。
- 我们的开关布局盒是 32×19（捕获 JSON 的 settings-general-control-*），像素也是 19 行满色。
- 当前 app.asar 的开关令牌是 switch-track-width:32px、switch-track-height:19px、switch-thumb-offset:3px，与我们的 HarnessToggleGeometry（32 / 19 / 3 / 13）逐项一致。

即：我们的实现忠实于当前 app 的声明令牌，参考截图渲染出 20 与当前令牌不符（参考是 2026-10-06，asar 之后已更新，属上游版本差异或渲染外环）。本批维持 19；若要严格贴合参考像素应改 20，但那会与当前上游令牌相悖，且开关是全应用共享控件，故按「忠于上游令牌」保留并记录分歧。

## 边界与残留

- 生产改动只有 1 个常量；该令牌在全仓 29 处使用（菜单分隔线、终端分隔线、标题栏内衬等）。本次只对设置页三面做了参考对照，其余面仍无参考实测；方向与原版语义令牌一致（更深），但未逐面验收。
- 轨道端边未拆令牌，仍用 `BorderSubtle`；参考那里是 229，我们是 232（Δ3）。轨道底色本身也不符（我们 `#f9f9f9`，参考 `#fcfcfc`，疑似窗口 vibrancy），无原生窗口截图权限无法继续。
- 自定义主题路径（`HarnessThemeCustomization`，`lerp(canvas, ink, 0.05f)`）未同步，高对比主题下 `BorderSubtle` 仍偏浅。
- 深色主题 `BorderSubtle = 0xFF2E2E2E` 未实测、未改。
- 紧凑按钮宽度 46 vs 44（字体 advance，AA 残留）本批未动。
