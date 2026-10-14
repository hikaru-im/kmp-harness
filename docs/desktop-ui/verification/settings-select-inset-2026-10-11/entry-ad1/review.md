# 批次 AD 证据：设置页下拉触发器内缩与手绘箭头（生产改动）

- 日期：2026-10-11
- 写集：`modules/ui-foundation/src/im/hikaru/harness/ui/foundation/HarnessTokens.kt`（新增 `Control.SelectPaddingX = 14.dp`；`SelectChevronScale` 1.5f → 1.25f）、`HarnessActionControls.kt`（触发器内容内缩改用 `SelectPaddingX`；更正触发器文档注释）
- 关联：AA（建立触发器）、AC（描边令牌）
- 目录：`docs/desktop-ui/verification/settings-select-inset-2026-10-11/entry-ad1/`

## 问题

AA 建立了 `HarnessSelectTrigger`，但有三处与参考图实测不符，其中两处还与它自己的文档注释互相矛盾：

1. 内容内缩：代码用 `Spacing.Three`（12px），同一条注释却写「inset … about 16px a side」。
2. 手绘箭头：14dp 盒 × `glyphScale` 1.5 画出的 ink 是 12×6，参考是 10×5。
3. 注释写「radius-6」，但同批的实测（AC 已复核）给出 9，`Corners.Control = 9.dp` 才是对的。

## 实测（参考图带 Display-P3，先转 sRGB）

| 参考行级下拉 | 盒 | 前导图标 ink | 标签 ink 左内缩 | 箭头 ink | 箭头右内缩 |
| --- | --- | --- | --- | --- | --- |
| Automatic | x1694..1789（w96） | 无 | 15 | 10×5 | 15 |
| Default app | x1652..1789（w138） | 左内缩 16 | 37（图标之后） | 10×5 | 15 |

我们的启用触发器（close policy 行）改前：标签 ink 左内缩 12、箭头 ink 12×6、箭头右内缩 13。

## 推导

参考给出三种 ink 内缩：前导图标 16、标签 15、箭头（右）15。`HarnessIcon` 与 `HarnessText` 在各自布局盒内还带约 1–2px 侧向 bearing，所以能同时复现这三个 ink 内缩的布局内缩是 14——不是注释写的 16，也不是代码原来的 12。同一 `HarnessIcon` 的 ChevronDown 路径只占盒面 46%，14dp 盒在 scale 1.5 画 12×6、在 1.25 画参考的 10×5。

## 实测对照（`measurement/measure-select-trigger.py`，输出 `select-trigger-measurements.txt`）

| 触发器 ink 指标 | 参考 | 改前（内缩 12） | 内缩 16 | 内缩 14（采用） |
| --- | --- | --- | --- | --- |
| 标签 ink 左内缩 | 15 | 12 | 16 | 14 |
| 箭头 ink 宽×高 | 10×5 | 12×6 | 10×6 | 10×6 |
| 箭头 ink 右内缩 | 15 | 13 | 18 | 16 |

内缩 16 与 12 的三项 |Δ| 合计同为 4；14 把三项压到 1 / 0 / 1，故取 14（并把该值作为实测令牌 `SelectPaddingX` 固定下来）。

## 验收

- 设置页捕获类 `GeneralSettingsInteractionTest` **6/6**（新截图写入 `docs/desktop-ui/captures/settings-select-inset-2026-10-11/`）。
- `ui-foundation` 模块 **53/53**。
- 全量 `jvm-app` **647 found / 5 skipped / 642 passed / 0 failed**（exit 0；104/105 容器成功）。
- 重跑 `measurement/measure-select-trigger.py`：标签内缩 14、箭头 10×6、右内缩 16。

## 边界与残留

- 箭头 ink 仍高 6，参考是 5：我们的手绘 ChevronDown 相对更厚（笔画随 glyphScale 一起放大），不是内缩能修的；要完全一致得换箭头几何。
- 标签左内缩 14 vs 15、箭头右内缩 16 vs 15，各差 1，落在 ink 边缘抗锯齿的 ±1 噪声内。
- 参考图的行级下拉都是启用态（白底）；我们的语言与默认文件打开位置触发器是禁用态，填 `SurfaceSecondary`（灰）。这是状态差异，不是内缩缺陷，本批不动。
- 触发器是全应用共享控件，但内缩是绝对 dp、不随页面变化；本批只对设置页（唯一有完整参考的页）做了这项实测，其它页未逐页重测。
