# 会话页与输入区复刻验证 · 2026-10-17

范围：Compose Desktop 客户端的会话时间线（用户气泡、assistant 正文、思考/工具活动、错误提示、Markdown）与底部输入区（悬浮 composer、附件/权限、模型选择、听写、停止/发送），对照实时运行的 Codex Desktop（进程 ChatGPT.app，CDP 9222 / 窗口 `318`，1090×760，dpr=1，深色）实测。

参考侧操作只读：未注入修改脚本，未读取 cookie/token/localStorage，未打开浏览器页面。测量前等待动画稳定；参考窗口尺寸、视图模式、选中标签、`scrollTop` 已恢复。

## 1. 参考实测（实时 Codex）

### 1.1 时间线容器

| 项 | 实测值 |
| --- | --- |
| 滚动容器 rect | `[341,44,745,712]` |
| scroll 内边距 | `padding-top: 32px`，容器 `column-reverse` |
| 滚动条槽 | `scrollbar-gutter: stable both-edges`，左右各 11px；正文可用宽 723 |
| transcript 内边距 | `0 16px 32px`，内容列 691 |
| 正文宽度上限 | `--thread-content-max-width: 48rem`（768px，含内边距的外层上限）；宽窗口正文实际 736 |
| 块间距 | turn 之间 12px；同一消息内 activity/content 之间 16px |
| 顶部渐隐 | 40px，opacity stops 1 / .82 / .48 / .12 / 0（0 / 10 / 30 / 60 / 100%） |

### 1.2 消息与活动

| 项 | 实测值 |
| --- | --- |
| 用户气泡 | `max-width:70%`；深色背景 `#173e76`、文字 `#f6fafe`；浅色 `#e8f3fe` / `#0c274a`；圆角 22；padding `10px 16px`；正文 14/22.75 |
| 用户 meta | 气泡下 4px；行高 26；复制按钮 26×26；时间 12px tertiary；右对齐 |
| assistant 正文 | 14/22.75 |
| assistant 操作行 | 距正文 top 6px；按钮 26×26；间隔 2；整体向左偏 4px |
| activity 行 | 高 21；图标 16；文字 14px、60% 白；默认折叠 |

activity 折叠行的实测结构（只读 DOM，`/tmp/actprobe5.mjs` / `actprobe6.mjs` / `actprobe7.mjs`）：

- 行本身 `div.group/activity-header relative inline-flex min-w-0 max-w-full self-start items-center gap-1`：**内容宽度**（实测 180 / 110px，不是整列宽）、`self-start` 左对齐、高 21、`align-items: center`，行内 `gap: 4px`。
- 行内三件：命中区 `button.absolute inset-0`（尺寸等于行宽）、文本组 `span.gap-1.5`（图标 16 与标签之间 6px）、chevron 包装 `span.pointer-events-none.relative.flex` 14×14。
- chevron 为 `svg.icon-2xs`（渲染 14×14，属性 `width/height=20`、`viewBox="0 0 20 20"`、`fill="none"`），类含 `text-text/60 opacity-0 … transition-transform duration-relaxed`；展开时追加 `rotate-90 opacity-100`（组件源码 `function FJe` 实测 `r = n && \`rotate-90 opacity-100\``）。即**默认不可见**，悬停或聚焦才淡入，展开时旋转 90° 并保持可见。
- chevron 不是描边箭头：`<path fill="currentColor">` 的**填充**路径，`d="M7.52925 3.7793C7.75652 3.55203 8.10803 3.52383 8.36616 3.69434L8.47065 3.7793L14.2207 9.5293C14.4804 9.789 14.4804 10.211 14.2207 10.4707L8.47065 16.2207C8.21095 16.4804 7.78895 16.4804 7.52925 16.2207C7.26955 15.961 7.26955 15.539 7.52925 15.2793L12.8085 10L7.52925 4.7207L7.44429 4.61621C7.27378 4.35808 7.30198 4.00657 7.52925 3.7793Z"`，在 20 的 viewBox 内约 7×13，视觉上比 `HarnessIconName.ChevronRight` 那种细描边 chevron 粗得多。
- 标签为单个 `span.block.max-w-full.min-w-0.truncate`，14px / 21px，`text-text/60`，悬停时 `group-hover/activity-header:text-default` 变为全白。实测文本形如「加载了工具已搜索网页」，是应用自己拼好的一个摘要串。
- 字体族与正文一致（实测标签与正文同为 `-apple-system, system-ui, …`）。
- 展开体由 `UJe` 渲染：`flex min-w-0 flex-col`，外面套一层 `Lw{padding:"offset"}`；**展开态的间距未实测**（参考中 7 个折叠行全部处于收起状态，按只读约束未点击展开）。
| 错误 aside | 背景 `#181818`；圆角 25；padding `20 / 16 / 12 / 16`；文字 13/21.125；图标 18px 垂直居中；描边 0.5px `rgba(255,255,255,.156)`；阴影 `0 0 2px rgb(0 0 0/5%), 0 4px 6px rgb(0 0 0/2%)` |

错误 aside 的内部结构（只读 DOM 实测，`/tmp/errprobe3.mjs`）：`aside` 直接子节点为装饰层 `absolute inset-0 -z-10 bg-transparent`、图标容器 `flex items-center self-center`（18×18，svg 属性 16×16）、文本列 `flex min-w-0 flex-1 flex-col gap-1.5`；文本列本例只有一个子节点，`gap-1.5`（6px）未产生实际间距，卡片高 53 = 16 + 21.125 + 16。文本 `span.wrap-anywhere` 13px / 21.125px（`text-sm` + `leading-relaxed`），颜色 `rgb(255,255,255)`。参考的错误卡片内**没有**重试按钮。

描边取值来源：`--color-border-strong` = `var(--app-color-border-heavy, …)`；`--app-color-border-heavy` 深色声明为 `color-mix(in oklab, var(--white) 16%, transparent)`，在运行中的应用里计算值为 `rgba(255, 255, 255, 0.156)`（实测），与卡片实际 ring 的 `rgba(255,255,255,0.157)` 一致。浅色声明为 `color-mix(in oklab, var(--app-color-text-foreground) 12%, transparent)`（`#1a1c1f` 12%），**未做浅色像素实测**。

#### 工具失败的真实渲染（2026-10-17 实测，`/tmp/failprobe5.mjs` / `failprobe6.mjs`）

在真实 Codex 里新建一个会话，让模型执行必然失败的命令 `cat /tmp/kmp-probe-missing-20261017.txt`（该文件不存在），取真实失败回合。turn 的子节点依次为：

| 块 | rect | 内容 |
| --- | --- | --- |
| user | `462,76,736,96` | 用户气泡 |
| spacer | `462,172,736,16` | 16px |
| activity | `462,188,736,65` | 头部「用时 8秒」+ 下方一条摘要行 |
| spacer | `462,252,736,16` | 16px |
| assistant | `462,268,736,114` | 普通回复，错误文本在其中的代码块里 |

- activity 头：`button[aria-expanded="true"]`，`462,189,72,21`，文本「用时 8秒」，14px / 21px，颜色 `rgba(255,255,255,0.498)`；chevron 14×14 同色，类 `icon-2xs transition-transform duration-basic rotate-90`。
- activity 第二行：另一条 `group/activity-header`，`462,231,286,21`，图标 16 + 文本「已读取 kmp-probe-missing-20261017.txt」，均为 `text-text/60`。
- **失败没有单独的错误样式**：activity 块里没有警告图标、没有红色、没有 aside 卡片，成功与失败的活动行结构完全一致。
- 错误正文出现在 **assistant 自己的回复**里（模型把它写成代码块）：`div … rounded-(--radius-3xl-base) border border-subtle`，`462,268,736,82`，背景 `rgb(24,24,24)`、圆角 20、1px 边框 `rgba(255,255,255,0.043)`，正文 14/22.75，头部「纯文本」（`min-h-12`）。

结论：工具失败不是参考里的独立 UI 状态；1.2 那张错误 aside 的原型是**会话级错误**（如模型容量不足），不是工具失败。实现据此把 `ToolResultContent` 从错误卡片改回 activity 折叠行。

#### 回合活动头「用时 N秒」（实测）

- 位置：回合的活动块顶部。新会话的 turn 结构是 `div.flex.flex-col`（`462,76,736,306`），依次为 user 块、16px 间隔、activity 块、16px 间隔、assistant 块。
- 文案来源：zh-CN 目录 `localConversation.workedFor` = `用时 {time}`（asar 偏移 556727110；英文默认串 `Worked for {time}`，描述为 “Divider shown between agent activity and the final assistant response in a completed turn”）。未到阈值时另有 `localConversation.workingFor`（zh-CN `已处理 {time}`）。
- 结构：`div.text-size-chat.text-secondary`（`462,188,736,23`，计算色 `rgba(255,255,255,.498)`）内是 `button[aria-expanded="true"]`（`462,189,72,21`）：文本 `span` 54px + 4px + chevron 14×14。
- 展开体 `div.pt-1` 内第一条活动行在 `462,231,286,21`（`text-text/60`）；头底 211 → 行顶 231，间距 20。
- 同一应用里较早的会话（「解释 Amper 编译删除失败」）用的是另一套 turn 渲染器：`div.group/agent-activity.flex.flex-col` 里文字与活动行同层、没有「用时」头。所以这个头并非所有回合都有。

### 1.3 输入区

外层 `_ComposerLayoutRoot` 实测：背景 `rgb(54,54,54)` = `#363636`，圆角 22，无外边框，内阴影 `rgba(255,255,255,.2) 0 0 1px 0`；`_ComposerLayoutBody` 透明。此前差异清单把透明的 Body 当成整个 composer，结论有误，本记录按 Root 实测更正。

| 项 | 实测值 |
| --- | --- |
| 总高 | 98px = 顶部附件空位 14 + 输入 44 + gap 4 + footer 28 + 底部 8 |
| 输入区水平内边距 | 12 |
| footer 水平内边距 | 8 |
| 加号按钮 | 28×28 |
| 权限 chip | 73×28（参考显示“自定义”） |
| 控件间距 | 5 |
| 模型 | 按内容宽，位于 footer 右侧 |
| 听写 / 主操作 | 28 / 28；主按钮深色 `#2c67c5`、浅色 `#3a83f7` |
| 占位文字 | 会话内“与 Codex 协作”；首页“随心输入” |
| composer 上方渐隐 | 32px |
| 回到底部按钮 | 32×32，背景 `#181818`，位于 composer 顶部上方 24px |

#### 合并后的 composer 回归（2026-10-17 19:26 实测）

合并 `origin/main` 带入了新的 `LiveConversationComposer.kt` / `ConversationComposerToolbar.kt`（权限 chip、模型与推理强度弹窗、附件位、发送/停止），但它同时用一套未经本页实测的样式（`SurfaceElevated`、1dp 外描边、16/16/12 内边距、32px 控件）替换了已对齐的视觉。按“结构可以采纳、样式以 Codex 为准”的原则，保留上游控件与交互，把外观改回下表的实测值（tokens 在 `HarnessTokens.Composer`）：

| 项 | 实现值（实测来源） |
| --- | --- |
| 背景 / 圆角 | `#363636`（深）/ 白（浅）；22 |
| 边缘 | 无外描边；0.5dp 白色 20% 内侧微光（近似 `0 0 1px inset`） |
| 内边距 | 上 14、下 8；输入区水平 12、footer 水平 8 |
| 输入行 | 44 高；14 / 20 白色；占位 0.498 白，会话内「与 Codex 协作」、首页「随心输入」 |
| footer | 行高 28；控件 28×28、间距 5；文字 13 / 18 |
| 权限 chip | 6px 内边距、16 图标 + 4 gap；完全访问整体用 `rgb(255,133,73)`，chip 取实测短标题「完全访问」（弹窗项仍是「完全访问权限」） |
| 主操作 | `#2C67C5`（深）/ `#3A83F7`（浅），白色 16px 图标 |
| 弹窗 | 320 宽、圆角 20、4 内缩、0.5px 白色 8.2% 环、`0 8px 16px -4px` 阴影；行 312×42.6（8/5 内边距、圆角 15、13/18 标签 + 12/14 描述）；四项权限 + 右侧「了解更多」下划线链接 |
| chip 悬停/展开 | 填充白色 8%（实测于展开态） |
| 上方渐隐 / 底部内缩 | 32px / 16px |

补充实测：当前实时窗口里的 `_ComposerLayoutRoot` 为 736×98（x=461.5，y=764），footer 首行 y=62；`+` 在 x=8、权限 chip 在 x=41（gap 5）、模型按内容右对齐、听写与主操作各 28，右内边距 8。输入包装层 `0 12px`，`ProseMirror` 14/20。权限弹窗行内标签在 DOM 里同时存在白色与橙色的两层 span，按像素取样，完全访问的可见文字是橙色（`rgb(255,133,73)`）。

#### footer 逐项复刻与两个弹窗（2026-10-17 20:24–20:33 实测）

此轮把参考 footer 的每个插槽、权限弹窗与模型弹窗都按 DOM + 像素重测了一遍，并补齐了此前未采纳的第四项与「了解更多」链接。参考窗口仍为 1630×882，CDP target `66ABDF2C…`；测量结束已用 Escape 关闭弹窗，没有改动真实应用设置。

footer（参考 `_ComposerLayoutFooter`，根 736×98，x=461.5 / y=764）：

| 项 | 实测值 |
| --- | --- |
| footer 行 | 高 28，位于根底部上方 8；左右内边距 8 |
| 加号槽 | 28×28，位于 x=469.5（根左 +8）；白色 16px 加号 |
| 权限 chip | 28 高，内边距 `0 6`，16 图标 + 4 gap + 13/18 文字；展开/悬停填充白色 8% |
| 左簇间距 | 加号与权限 chip 之间 5 |
| 模型 chip | 内边距 `0 8`，内容 gap 5；模型名 13/18 白色，强度 13/18 `#3A83F7`，右侧 16px chevron（tertiary） |
| 右簇 | 模型 chip 右缘与听写槽左缘重合（1125.5）；听写 28×28；再空 8；主操作 28×28；右内边距 8 |
| 主操作 | 深色 `#2C67C5` / 浅色 `#3A83F7`，白色 16px 图标；空输入为波形，有文字为向上箭头，运行中为停止方块 |

图标路径按参考 SVG 原样转录：加号/麦克风/向上箭头/chevron/重置/对勾/手/盾牌/齿轮（齿轮即第四项的 `20 20` “自定义”图标，依赖 evenodd 挖孔）。权限 chip 的短标题此前已实测为「自定义」（chip 73×28）；「请求批准 / 帮我批准」的 chip 图标仍未在参考里切换到对应状态实测，当前用手形与对勾盾牌推断。

权限弹窗（参考 DOM）：

| 项 | 实测值 |
| --- | --- |
| 容器 | 320×204.25，位于 chip 左缘（504, 620）；圆角 20，内缩 4；表面 `surface-elevated-secondary/90`（像素上 `rgba(43,43,43)`）；0.5px 白 8.2% 环 + `0 8px 16px -4px` 阴影 |
| 头部 | 312×26，内边距 `5 8`，13/16，`rgba(255,255,255,.498)`；右侧「了解更多」下划线链接 52×16 |
| 行 | 312×42.5625，x=508，首行 y=650；圆角 15、内边距 `5 8`、内容 gap 6；图标 20 viewBox 以 18 绘制、75% 不透明度；标签 13/18.5714 白色，描述 12/14 `rgba(255,255,255,.498)`；选中项右侧 16px 对勾 |
| 第四行 | 「自定义 (config.toml)」/「使用 config.toml 中定义的权限」，齿轮图标 |
| 完全访问 | 标签与描述同为 `rgb(255,133,73)`；描述文案「可不受限制地访问互联网和你电脑上的任何文件」 |

实现把权限选项扩为四项（`ComposerPermission.Custom`），文案与参考逐字一致；和其余三项一样，它只是 UI 偏好（`ComposerPermission` 从不写入 AgentOptions）。「了解更多」没有可跳转的文档，按附件/听写的同样做法渲染为禁用链接 + 「文档暂不可用」提示。此前弹窗底部有一行本实现自己的「仅保存界面选择，执行权限仍由 Host 管理。」说明，按用户要求已删除；现在弹窗与参考一样以第四行为结尾，实测高 203.5（参考 204.25）。

模型弹窗（参考实测 254 宽）：

| 项 | 实测值 |
| --- | --- |
| 容器 | 254 宽、内缩 4、圆角 20、同一表面色与阴影；左缘与模型 chip 左缘对齐 |
| 头部 | 48 高：左缩进 32，chip 182×48 圆角 8，右上角 32×32 重置；强度 14/20 `#3A83F7` 在模型名上方（`max` 时改用 `--color-chart-purple`，深色 `#C8A4FB`），模型名 12/16 白 65% + 12px chevron |
| 滑杆行 | 40 高（`--spacing` 4 上下），容器 242×32 内边距 `2 6`，root 230×28；轨道 230×24、圆角 12、未填充 `color(srgb 1 1 1 / 0.1)`、填充 `#3A83F7`；每档一个 4px 圆点，距轨道两端 13px，已到达 30% 白、未到达 25% 白；旋钮 Ø28 纯白；圆点 = 档位、左右方向键切换 |
| 触发器 | 强度面板展开时，footer 的模型 chip 文字换成居中的「选择强度」（13px，0.498 白），宽度保持关闭时的模型名宽度，并保持 `data-[state=open]` 的 8% 白色填充 |
| 菜单表面 | 90% 不透明：在 `#181818` 页面上呈现 `rgb(43,43,43)`，在 `#363636` composer 上呈现 `rgb(46,46,46)`（像素反推，无额外底板） |
| 列表视图（同弹窗第二屏） | 内容内边距 `0 4 4`；分组标题「选择模型」235×26.5625（内边距 `5 8`，文字 x=弹窗+12，13px `rgba(255,255,255,.498)`）；模型行为 `role=menuitemradio` 235×28.5625，x=弹窗+4，圆角 15、内边距 `5 8`，标签 13/18.5714 白色，选中行右侧 17×17 对勾；行与行之间没有间隙，行距等于行高 |
| 列表右侧留白 | 行右缘距弹窗右缘 15（弹窗 254 = 4 内缩 + 235 行 + 11px 滚动条槽 + 4 内缩）；参考里模型多于可视高度时该槽出现，实测列表只有 8 项也保留了这一槽 |
| 参考专属项 | 列表首行是 Codex 专属的「默认 / 推荐模型集」两行入口（42.5625 高）；harness 没有对应概念，未实现 |

本构建的渲染复核（离线夹具 `…-variants`，1467×870，dark）：composer 外沿 `x=534/1269`、`y=752/849`，即 736×98；footer 高 28、底距 8；加号字形 550–561，权限图标 583–594 / 文字到 652；模型文字 1000–1167、chevron 1179–1184、麦克风 1206–1217、主操作圆盘 1234–1261，得到 chip 右缘 = 听写左缘 1197.5、+8 = 圆盘左缘 1233.5、右内边距 8，与参考结构一致。权限弹窗四项、橙色完全访问、选中对勾、齿轮与「了解更多」均已在截图中核对；模型弹窗实测约 254 宽，重置位于右上角，只读会话下滑杆禁用并显示原因。

21:11–21:18 又用带模型配置的离线夹具（`/tmp/kmp-harness-fixture-models-home-20261017`，三个 fixture 模型）复核首页 composer 与模型列表：首页不再出现「请选择本机目录中的模型。」提示或「配置模型」行（发送按钮仍静默禁用）；点开「选择模型」后，弹窗外沿 `x=1053/1306`（含 0.5 环即 254）、表面 `y=465.5/588`（约 122 高 = 4 内缩 + 26 标题 + 3×28.5 行 + 4），三行文字顶边 `508/537/566`（行距 29 ≈ 28.56），文字左缘 1066 = 弹窗 +12，均与参考一致。权限弹窗在固定行高后重测：弹窗外沿 `x=631.5/950.5`（319 + 环 = 320）、`y=384.5/588`（203.5，参考 204.25），四行标签顶边 `421/464/507/550`（行距 43 ≈ 42.5625），行高此前被 Material3 `minimumInteractiveComponentSize` 撑到 48dp，给行固定高度（权限 42.5、模型 28.5）后不再膨胀。选中模型后再从强度弹窗头部进入列表，选中行右侧出现 17×17 对勾（见 `final-home-model-list-selected-dark-1467x870.png`）。

#### 思考等级（推理强度）复刻（2026-10-17 21:20–21:45 实测）

参考的「思考等级」就是模型弹窗的强度视图（`data-model-picker-view` 的第二屏）。此前本实现只画了滑杆的外形，档位名称、刻度和展开态都没有对齐；这轮按 DOM 与打包产物重做。证据：

- 实时窗口（CDP target `66ABDF2C…`，仍为只读测量，未改动设置）打开强度视图后得到 DOM：popup 254×96；`_Container_xwb5v_1` 242×32（padding `2 6`），root 230×28；track 230×24、圆角 12、背景 `color(srgb 1 1 1 / 0.1)`；range `rgb(58,131,247)`、圆角 `12 0 0 12`；thumb 28×28 纯白；tick 4×4，位置 `calc(0% + 13px)` / `calc(50% + 0px)` / `calc(100% - 13px)`，已到达 `rgba(255,255,255,.3)`、未到达 `rgba(255,255,255,.25)`。头部 chip 182×48（强度 14/20 + 模型 12/16 + 12 chevron），重置 32×32；`aria-valuemin=0 / max=2 / now=1`。
- 档位名称来自 asar 的 zh-CN 文案键 `composer.mode.local.reasoning.*.label`，并用 DOM 里触发器内的 sr-only 列表逐项核对：无 / 极低 / 轻度 / 中 / 高 / 极高 / Max / Ultra / 持续。旧实现把 medium/high/xhigh/max/ultra 写成「中等 / 深度 / 超高 / 最高 / 极高」，与参考不符，已按此表改正（`max`、`ultra` 在 zh-CN 包里没有译文，保持英文）。
- 强度视图展开时 footer 触发器换成「选择强度」（`composer.modelPicker.selectEffort.label`）：实测 chip 由 870–1125 变为 878–1128，宽度基本不变、文字 0.498 白居中、底为打开态 8% 白。
- `data-maximum`（`max`）时强度读数改用 `--color-chart-purple`（深色 `#C8A4FB`）。

本构建渲染复核（同一临时夹具，为三个模型补了 `reasoningEfforts`）：强度弹窗外沿 `x=892/1145`、`y=492/588`，即 254×96；蓝色填充 `904..1134`（弹窗 +12 起、230 宽），圆点中心 917/1018/1119（距轨道两端 13），旋钮 28px 实测 1106..1133；选 `high` 时头部是蓝色「高」，选 `max` 时是 `rgb(199,164,251)` 的「Max」；footer chip 同步显示「Fixture Provider / Fixture Alpha 高」，展开时显示「选择强度」。

harness 差异：模型设置里没有「默认档位」元数据，参考则知道每个模型的默认 effort。为避免伪造档位，滑杆的点数严格等于声明的 effort 数（参考同样是 3 档 3 点）；effort 未设置时旋钮停在第一档、头部显示「默认」，重置按钮清回未设置。

#### 强度弹窗头部与无档位模型（2026-10-17 22:45–22:55 实测）

再次只读测量实时窗口（同一 CDP target；测完把参考的模型选择恢复为 Deepseek V4.1 Flash (ClinePass) / 高，弹窗关闭，窗口尺寸未动）：

- 头部不是整行 chip：`_SliderTopRowMotion` 246×48 内是左 32×32 的 `_FastModeToggle`（闪电、顶对齐）、居中的内容宽 `_ViewToggle`（本例 118×48、padding 4、radius 8、背景只在悬停时出现）、右 32×32 的 `_ResetToDefa`。`_ViewToggle` 内两行：effort 14/20 `rgb(58,131,247)`，模型名 12/16 `rgba(255,255,255,.65)`，chevron 12×12，行间 4px。此前 harness 把 chip 拉成整行宽、chevron 甩到最右，本轮改为内容宽并在两侧 32px 槽之间居中。
- 无推理档位的模型：实时 `powerSelections` 只包含有档位的 3 个模型；打包产物 `app.asar` 里强度视图过滤后 `p==null` 直接渲染空的 `SimpleView`，外层用 `C.length>=2` 决定是否进入强度视图（否则直接 `advanced`）。即 Codex 对没有（或只有 1 个）档位的模型不显示任何提示文字，弹窗直接进入模型列表。harness 已按此处理：删除「此模型未提供可调整的推理档位。」，档位 <2 时点开即列表；选中该类模型后直接关闭弹窗（不再切强度视图）。
- 模型列表视图（`advanced`，254×320）：`选择模型` 标题、`默认 / 推荐模型集` 分组标签、纯模型名行、右侧 16px 对勾。

本构建渲染复核（同一离线夹具）：选中 Fixture Gamma（无档位）后弹窗直接关闭、footer 只显示模型名，再次点开进入模型列表；选中 Fixture Alpha 后强度视图 254×96，chip 内容宽 182（夹具模型名带 provider 前缀，结构与参考的 118 相同）、左 32 槽 + 居中 + 右 32 重置，模型名右侧 4px 处是 12px chevron。构建 22:34 `Build successful`。

23:03 再次复核（同一夹具、新构建 23:02 `Build successful`）：Gamma 未选中时先开强度视图（`请先选择模型。`），经头部 chip 进列表选中 Gamma 后弹窗直接关闭；再次点开直接是列表，当前项 17px 勾选。只读会话（fixture-short）点开弹窗同样是列表视图（视图只看档位数，不看 `canChoose`），行可悬停/点击但选择不变，随后的 `LiveConversationInteractionTest` 两处断言按此更新。

仍未闭合的差异：Ask/Automatic/Custom 的 chip 图标、发送/停止的启用态（夹具线程始终 `canCancel`，只能看到停止态）没有参考原型；「自定义」被选中时的 chip 只实测到短标题与 73×28 宽度，图标沿用齿轮。模型列表里参考独有的「默认 / 推荐模型集」两行入口（42.56 高）未实现，因为 harness 没有对应概念；列表行在参考里是 `role=menuitemradio`，本实现复用同一个 TextButton 行并只上报 `selected`。

## 2. Markdown 依据

从 asar（`/Applications/ChatGPT.app/Contents/Resources/app.asar`，CSS 偏移约 66435101）提取 Markdown CSS，存为 `reference-markdown-css.txt`。采用的换算（16px 基准，1rem=16dp）：

- `--markdown-font-size:14`，`--markdown-space:3.5`；
- h1 21/28、h2 17.5/24.5、h3 15.75/24.5、h4 14/21、h5/h6 14/22.75，半粗；
- 列表项之间 0；列表缩进 22.75 + 内容间距 5.25；
- 引用块上下各 7、起始缩进 21、行高 21、竖线 3.5、圆角 1.75；
- 表格字号 `max(code 字号, 12.25)`，表头 top 7 / 行高 14，表体 top 8.75，列尾 21，末行 bottom 21；
- 代码正文 12/20，圆角 20，头部 48，背景 `#2b2b2b`（#181818 叠白 8%），边框白 4.3%，块上下 margin 17.5；
- 分隔线上下 margin 24.5。

实现使用 `org.jetbrains:markdown:0.7.18` 的 GFM AST（纯 Compose 渲染，无 WebView），链接仅用户点击后交给系统打开，且只允许 http/https/mailto。

## 3. 实现要点

- `ConversationAppearance.kt`：会话独立 palette（深色画布 `#181818`、浅色白），不改 shell 全局颜色；`ringStrong` 为实测的 `--color-border-strong`。
- `ConversationComponents.kt` 的错误卡片按实测改为：0.5dp `ringStrong` 描边 + `Modifier.shadow` 近似 CSS 的两层阴影，文字由 `bodyMedium` 改为 `LocalHarnessContentTextStyle` 13sp / 21.125sp。此前只有背景色，卡片在 `#181818` 画布上没有边界。
- `ConversationComponents.kt` 的 `ToolResultContent` 不再映射到错误卡片：`isError` 与成功走同一条 `ActivityDisclosure`。错误 aside 只保留给会话级错误，即 `ConversationViewport` 的 `error` 参数。
- 回合活动头：`turnWorkedFor` 按回合取「人类提问时间 → 回合最后一条消息时间」，只在回合第一条带活动的消息上方画一行「用时 {time}」（14/21，`tertiary` = 0.498 白），时间缺失时不画。
- 工具结果按 `callId` 折进对应的工具调用行：结果消息本身不再单独占一行，输出显示在调用行的展开体里（标签仍沿用 `message:<结果消息 id>:content:<i>:content:<j>`，语义标签不变）。
- 活动行标签按参考写成动作摘要：`已读取 <文件名>` / `编辑了 <文件名>` / `已搜索网页` / `运行了命令`，文件名取 `arguments` 里 `path`/`file_path`/`filePath` 的 basename；失败不改变标签外观。
- 动作行（复制/朗读/评价/分支）只在回合最后一条带文本的消息下渲染一次。
- `ConversationComponents.kt` 的 activity 折叠行按实测改为：行改为 `Alignment.Start` 内容宽度（命中区不再横跨整列）、图标 16 与标签间距 6、标签与 chevron 间距 4、chevron 由 12dp 改为 14dp 且默认透明、悬停淡入并把标签提到全白、展开时旋转 90°。字体由 UI 字体改为 `LocalHarnessContentTextStyle`（与正文同族）。
- `ConversationAppearance.kt` 新增 `ConversationGlyph.ActivityChevron`：把上面那条参考 path 原样放进 `conversationArtwork`（20 的 viewBox，按 14dp 缩放），用 `ConversationIcon` 填充绘制。此前用的是 `HarnessIconName.ChevronRight`（细描边 chevron），形状与参考不符。
- `ConversationComponents.kt`：时间线 `LazyColumn`；外层左右各 11dp 模拟 `stable both-edges`；条目 `widthIn(max=768).padding(horizontal=16)`；`contentPadding top=32`，`bottom = 底部占位 + 20`；turn 间距 12；末尾 0 高锚点；底部占位变化与内容变化都只在“原本贴底”时重新对齐末尾，用户主动上滚后不会被拉回。
- `ConversationMarkdown.kt`：标题、段落、强调、删除线、行内代码、链接/引用链接/自动链接、嵌套列表/引用/任务框、分隔线、对齐表格、围栏/缩进代码；图片只显示 alt，不加载远程资源；HTML/数学等保持原文。
- `LiveConversationComposer.kt`：合并后承载输入区。保留上游的多行输入上限与 Enter/Shift+Enter 提交，按实测改为 98px 结构（14 + 44 + 4 + 28 + 8）、22 圆角、`#363636`、0.5dp 内侧微光、输入区 12 / footer 8 的分别内缩、14/20 正文，占位文字按首页/会话切换。
- `ConversationComposerToolbar.kt`：保留上游的权限 chip、模型与推理强度弹窗、附件位和发送/停止，按实测改为 28px 控件、5px 间距、13/18 标签、0.498 白 ink；权限 chip 用「完全访问 / 自定义」短标题，完全访问橙色前景；主操作使用实测蓝色与波形/箭头/停止三种 16px 图标。权限弹窗按实测补齐第四项「自定义 (config.toml)」与头部「了解更多」，行 312×42.6、图标 18@75%、文案逐字对齐参考；模型弹窗为 254 宽、48 头部（32 内缩 + 182 chip + 32 重置）+ 32 滑杆行；列表视图为 26 标题 + 28.5 行距 + 右侧 15 留白，选中行 17px 对勾。权限行与模型行都给固定高度，避免 Material3 把行撑到 48dp；权限弹窗底部的 Host 说明已删除。强度视图按参考改正档位名称（无 / 极低 / 轻度 / 中 / 高 / 极高 / Max / Ultra / 持续）、每档一个 4px 圆点、28px 旋钮与 30%/25% 圆点色，`max` 时读数用紫色；强度面板展开时触发器显示居中的「选择强度」。
- `HarnessTokens.Composer` / `HarnessComposerControls.kt` / `HarnessOverlays.kt`：把上面这些实测值收进共享 tokens，并给弹窗增加可选形状/颜色/描边/阴影参数（默认值保持其它页面不变）。
- `AppShell.kt`：仅 `PageId.Thread` 走全尺寸会话布局，composer 悬浮在底部，实测高度通过 `onGloballyPositioned` 写入 `LocalConversationBottomInset`；其它页面仍走原布局，标题栏阴影未改动。
- 时间戳：`Message.timestampMillis` 来自会话事件 `event.time`，经 `SessionProjection`、client `Message`、`LocalConnection` 传到 UI；没有时间就不画时间，未伪造。显示格式为“星期X + 24 小时制”。

## 4. 改动文件

```
apps/shared/module.yaml
apps/shared/src/im/hikaru/harness/client/app/ConversationAppearance.kt   (新增；含 footer/权限/模型图标的参考 SVG path)
apps/shared/src/im/hikaru/harness/client/app/ConversationMarkdown.kt     (新增)
apps/shared/src/im/hikaru/harness/client/app/ConversationComponents.kt
apps/shared/src/im/hikaru/harness/client/app/LiveConversationSurface.kt
apps/shared/src/im/hikaru/harness/client/app/LiveConversationComposer.kt      (合并后按实测回归)
apps/shared/src/im/hikaru/harness/client/app/ConversationComposerToolbar.kt  (合并后按实测回归)
apps/shared/src/im/hikaru/harness/client/app/ComposerPresentation.kt         (四项权限、参考文案、chip 短标题)
apps/shared/src/im/hikaru/harness/client/shell/AppShell.kt
modules/ui-foundation/src/im/hikaru/harness/ui/foundation/HarnessTokens.kt
modules/ui-foundation/src/im/hikaru/harness/ui/foundation/HarnessComposerControls.kt
modules/ui-foundation/src/im/hikaru/harness/ui/foundation/HarnessOverlays.kt
apps/shared/src/im/hikaru/harness/client/connection/Connection.kt
apps/jvm-app/src/im/hikaru/harness/desktop/connection/LocalConnection.kt
apps/jvm-app/test/im/hikaru/harness/desktop/LiveConversationInteractionTest.kt          (chip 短标题断言)
apps/jvm-app/test/im/hikaru/harness/desktop/LiveHarnessConversationInteractionTest.kt   (chip 短标题断言)
modules/llm/src/im/hikaru/harness/llm/Message.kt
modules/session/src/im/hikaru/harness/session/SessionProjection.kt
```

新增依赖：`org.jetbrains.kotlinx:kotlinx-datetime:0.7.1`、`org.jetbrains:markdown:0.7.18`。

## 5. 执行记录

构建（构建目录 `/tmp/kmp-harness-conversation-20261017-build`）：

```bash
./kotlin build -m jvm-app --build-dir=/tmp/kmp-harness-conversation-20261017-build
```

结果：`Build successful`（Amper 会编译 `compileJvmTest` 源集，本任务**没有运行任何测试套件**，验收由用户完成）。

运行：

```bash
./kotlin run -m jvm-app --build-dir=/tmp/kmp-harness-conversation-20261017-build -- --window-size 1467x870 --home /tmp/kmp-harness-conversation-home-20261017 --theme=dark
```

离线历史夹具：使用仓库外的临时适配器，在真实启动流程中调用正式 `DesktopWorktreeChatRestoreService` 的 quiescent 恢复入口，只读 SQLite 历史；不发送模型请求、不执行工具，也不修改仓库启动文件或真实数据库。夹具脚本来源见 `fixtures/README.md`。

## 6. 运行截图与像素取证

截图均在 `docs/desktop-ui/captures/conversation-page-2026-10-17/`：

| 文件 | 内容 |
| --- | --- |
| `reference-dark-1090x760.png` | 参考会话恢复后截图 |
| `final-home-dark-1467x870.png` | 最终版首页与 composer |
| `final-short-dark-1467x870.png` | 短会话：用户气泡、时间、复制、Markdown 列表 |
| `final-long-bottom-dark-1467x870.png` | 长历史底部 |
| `final-long-scrolled-dark-1467x870.png` | 上滚后出现“回到底部”按钮（15:24 构建） |
| `final-long-returned-dark-1467x870.png` | 点击按钮回到末条消息（15:24 构建） |
| `final-error-dark-1467x870.png` | 错误会话：「用时 13秒」头、活动行「已读取 summary.txt」（结果已折进该行）、仅末次回复带动作行（18:39 构建） |
| `final-activity-hover-dark-1467x870.png` | activity 行悬停：chevron 淡入、标签提到全白（17:04 构建） |
| `final-activity-expanded-dark-1467x870.png` | activity 行展开：chevron 旋转 90° 并保持可见（17:04 构建） |
| `final-reasoning-expanded-dark-1467x870.png` | 思考展开 |
| `final-multiline-dark-1467x870.png` | 多行输入，composer 增高后末条消息仍可见 |
| `final-composer-thread-dark-1467x870.png` | footer 逐项复刻后的会话 composer：736×98、加号/盾牌 chip/模型 chip/麦克风/蓝色停止（20:33 构建） |
| `final-composer-permissions-dark-1467x870.png` | 权限弹窗：320 宽、四项权限、橙色完全访问、齿轮自定义、「了解更多」下划线链接（20:33 构建） |
| `final-composer-model-dark-1467x870.png` | 模型弹窗：254 宽、48 头部（182 chip + 右上重置）、32 滑杆行；只读会话下滑杆禁用（20:33 构建） |
| `final-home-composer-dark-1467x870.png` | 首页 composer：`随心输入`、波形主按钮（20:33 构建） |
| `final-home-composer-typed-dark-1467x870.png` | 首页输入 `hi` 后：主按钮切换为向上箭头（无模型时 35% 禁用色）（20:33 构建） |
| `final-home-composer-models-dark-1467x870.png` | 首页 composer（夹具配置了 3 个模型）：chip 为「选择模型」，不再显示模型缺失提示或「配置模型」行（21:15 构建） |
| `final-home-model-list-dark-1467x870.png` | 模型列表：254 弹窗、26 标题、三行 28.5 行距、文字 x=弹窗+12、右侧 15 留白（21:15 构建） |
| `final-home-model-list-selected-dark-1467x870.png` | 选中 Fixture Beta 后再进列表：选中行右侧 17×17 对勾，首行悬停填充（21:15 构建） |
| `final-home-permissions-dark-1467x870.png` | 首页权限弹窗（已删除 Host 说明）：320×203.5、四行 42.5 行距（21:15 构建） |
| `final-home-effort-default-dark-1467x870.png` | 强度视图（未设置 effort）：254×96、230 轨道、3 个 4px 圆点、28px 旋钮；触发器显示「选择强度」（21:42 构建） |
| `final-home-effort-high-dark-1467x870.png` | 强度视图选 `high`：蓝色「高」、蓝色填充到右端（21:42 构建） |
| `final-home-effort-max-dark-1467x870.png` | 强度视图选 `max`：读数变紫（`rgb(199,164,251)`）「Max」（21:42 构建） |
| `effort-popup-content-chip-dark-1467x870.png` | 头部 chip 改为内容宽并居中、chevron 紧贴模型名（22:34 构建） |
| `no-effort-model-closed-dark-1467x870.png` | 选中无档位模型 Fixture Gamma：弹窗直接关闭、footer 无强度、无提示文字（22:34 构建） |
| `no-effort-model-list-dark-1467x870.png` | 无档位模型再次点开：直接进入模型列表（22:34 构建） |
| `no-effort-model-readonly-dark-1467x870.png` | 只读会话点开模型弹窗：同样是列表视图、选择不可改 |
| `reference-composer-live-1630x882.png` | 实时 Codex 窗口：同一 composer 的参考外观（含听写/语音按钮） |
| `final-markdown-light-1090x760.png` | 浅色 + 窄窗口：标题、列表、代码块、引用、表格 |

错误卡片描边与 activity 折叠行改完后重新构建（`Build successful`）并重启夹具（home `…-variants`，选中 `fixture-error-20261017`），重新截取了 `final-error-dark-1467x870.png`（替换掉改动前无描边的版本）与两张 activity 状态图；截图前等待动画稳定，测量后已把展开的折叠行收回、鼠标移开。

把工具失败改回 activity 折叠行后再次构建（`Build successful`，17:48 刷新共享 jar）并重启同一夹具，重新截取 `final-error-dark-1467x870.png`；展开态在本机与参考对照后确认：折叠行展开时 chevron 下向、正文为工具输出。

加入「用时」头、工具结果折叠与动作行合并后再次构建（`Build successful`，18:39 刷新共享 jar）并重启夹具，错误会话与长会话都重新截图核对：活动行只剩一条「已读取 summary.txt」，展开体是折叠进来的工具输出，动作行只出现在末次回复下。

合并 `origin/main` 后按实测回归 composer（`Build successful`，19:26 刷新共享 jar）并重启同一夹具：会话 composer 的纯色区实测 734×96（加 0.5dp 描边约 736×98，与实时 `_ComposerLayoutRoot` 一致），权限切到「完全访问」后 chip 与图标整体变橙；权限弹窗重新截图核对 320 宽、4 内缩、行高 42.6；首页 composer 同步取图。测量用的实时窗口只在读取 DOM 时短暂展开过权限弹窗，随后用 Escape 关闭，没有改动应用设置。

20:24–20:33 补齐 footer 图标、权限第四项与「了解更多」、模型弹窗版式后再次构建（构建目录 `/tmp/kmp-harness-merge-20261017-build`，`Build successful`）并重启夹具。注意：本轮 `./kotlin build -m jvm-app` 只刷新了 `jvm-app-jvm.jar`，`tasks/_shared_jarJvm/shared-jvm.jar` 仍是 20:13 的旧包（即使删掉 jar 与 `incremental.state/_shared_jarJvm-*` 也不会重建）；改用 `./kotlin package -m jvm-app --build-dir=…` 后才生成 20:33 的新共享 jar，夹具类路径才会包含 `ComposerPermission.Custom`。重启后逐项截图核对：footer 736×98 与各槽位像素位置、权限弹窗四项与链接、模型弹窗 254 宽与右上重置、首页空/输入两态的主按钮图标。实时窗口的权限弹窗在这次 DOM 读取后同样用 Escape 关闭。

21:38–21:44 按实测补做思考等级：修正 `effortLabel` 的全部档位名称、滑杆几何（230 轨道、4px 圆点、28px 旋钮、30%/25% 圆点色、`max` 紫色）、强度面板展开时的「选择强度」触发器，`./kotlin package -m jvm-app --build-dir=/tmp/kmp-harness-merge-20261017-build` 报 `Build successful`（21:42 刷新共享 jar）；给临时夹具的三个模型补上 `reasoningEfforts` 后重启，逐档截图并像素复核弹窗 254×96、轨道 904–1134、圆点 917/1018/1119、旋钮 1106–1133、`Max` 颜色 `rgb(199,164,251)`。

21:11–21:16 为模型列表对齐再做一次构建：在权限行与模型行上给固定高度（`HarnessTokens.Composer.PermissionItemHeight = 42.5dp`、`ModelItemHeight = 28.5dp`），否则 Material3 的最小交互尺寸会把行撑到 48dp；`./kotlin package -m jvm-app --build-dir=/tmp/kmp-harness-merge-20261017-build` 报 `Build successful` 并刷新共享 jar（21:15），随后用带 3 个模型配置的临时夹具（`/tmp/kmp-harness-fixture-models-home-20261017`，仓库外）重启，逐个截图测量首页、模型列表（选中前后）与权限弹窗，测量完关闭弹窗并结束夹具进程。
| `reference-layout.json` / `reference-icons.json` / `reference-fades.json` / `reference-markdown.json` / `reference-markdown-css.txt` | 参考 DOM 与 SVG/CSS 证据 |

像素取样（截图转 sRGB 后读取原始像素）：

| 位置 | 实测 |
| --- | --- |
| 画布 | `#181818` |
| composer 主体 | `#363636` |
| composer 边缘 | `#363636` 上一条白色 20% 的 1px 内侧微光（参考 `rgba(255,255,255,.2) 0 0 1px 0 inset`；实现用 0.5dp 描边近似，像素落在 `#404040` 一带） |
| 用户气泡 | `#163f75`~`#173d74`（目标 `#173e76`，受显示色彩配置与抗锯齿影响） |
| 单行用户气泡高度 | 42px（10 + 22.75 + 10 ≈ 42.75） |
| composer 总高 | 98px（实测 752→850 边界） |
| 错误卡片描边 | 左 `x=534`、右 `x=1269`、上 `y=326` 均为 `#3b3b3b`；按 `#181818` 叠加白 15.7% 计算为 `#3c3c3c`，与参考 `rgba(255,255,255,.156)` 一致。取样自 17:04 构建（当时工具失败还渲染成卡片）；改成 activity 折叠行后，卡片只在会话级错误时出现 |
| activity chevron 悬停 | 放大到 8× 目视确认是参考那种填充 chevron（不是细描边箭头）；旧版本用 `HarnessIconName.ChevronRight` 时明显偏细 |
| activity chevron 展开 | 同一位置确认旋转为下向 chevron，位置较收起态下移约 2px（绕 14dp 方框中心旋转） |

## 7. 已知限制（未声称完整复刻）

- composer 内侧微光用 0.5dp 描边近似 1px inset 阴影，没有实现真正的模糊。
- 主操作按钮只实测到参考的蓝色（空输入时那里显示的是语音按钮，不是发送）：发送/停止的禁用态与生成中态样式没有参考原型，实现沿用实测蓝色，禁用态用 35% 透明度。
- 参考的听写/语音按钮已按实测画入（16px 麦克风、28px 槽位），但仍是禁用占位并提示「听写暂不可用」；模型 chip 里的 provider 图标没有对应资源，未实现。
- 权限弹窗第四项「自定义 (config.toml)」与「了解更多」已补齐外观；前者只是 UI 选项（harness 不读取 config.toml），后者没有文档地址，渲染为禁用链接并提示「文档暂不可用」。
- 权限 chip 的图标只实测到「完全访问」的橙色警告图标；「请求批准 / 帮我批准 / 自定义」的 chip 图标按弹窗内同款图标推断（手形 / 对勾盾牌 / 齿轮）。「自定义」的 chip 短标题有实测依据（参考 chip 73×28 时显示「自定义」），但没有在参考里切换权限复测（那会改真实应用的设置）。
- 模型列表按参考保留右侧 15（4 内缩 + 11px 滚动槽）留白，但不绘制滚动条；行高为固定的 28.5，未上报 `menuitemradio` 语义，也没有键盘上下切换。参考独有的「默认 / 推荐模型集」入口未实现。
- 强度滑杆只渲染模型声明的档位（与参考一致），但没有「默认档位」元数据：effort 未设置时旋钮停在第一档并显示「默认」，参考会显示该模型的默认档位；`max` 的紫色取自 `--color-chart-purple` 深色值，浅色未实测。
- 强度弹窗头部左侧的 32px 快速模式（闪电）开关没有实现：harness 没有 service tier 概念，该槽位留空以保持 chip 居中与整体几何；模型列表行仍带 `provider / model` 前缀，也没有参考的「默认 / 推荐模型集」分组标题。
- 本机（Local）模式未选模型时，首页 composer 不显示任何提示（按用户要求删除了「请选择本机目录中的模型。」），发送保持静默禁用；模型 chip 是唯一的可发现入口。
- 错误卡片的两层 CSS 阴影用 `Modifier.shadow(6dp)` 近似，模糊半径与偏移未逐像素对齐；在 `#181818` 画布上这两层（黑 5% / 2%）本身几乎不可见，可见边界来自 0.5dp 描边。
- 错误卡片保留了一个参考里没有的“重试”文字按钮（仅在调用方传入 `onRetry` 时出现，用于真实的提交/取消/历史重试）；参考卡片没有该控件。
- 错误卡片浅色描边取自样式表声明的 12% 前景色，未做浅色像素实测。
- 「用时」头在参考里是整组活动的折叠开关（`button[aria-expanded]`，收起后只留这一行）；本实现没有把整回合的活动聚合到同一个容器里，因此这一行只是纯文本、不可点击，活动行也始终可见。
- 分钟级时长（本实现写成 `N分M秒`）是按 zh-CN 文案推的，参考里没有实测到超过 60 秒的回合；参考的 `{time}` 具体格式函数未定位。
- 参考里较早的会话用另一套 turn 渲染器（`group/agent-activity`，无「用时」头、文字与活动行同层），本实现统一使用带「用时」头的这套。
- 回合里的中间叙述文字（模型在调用工具前说的话）在本实现里仍与活动行分属不同消息，因此「已读取」行会出现在这段文字之后；参考的新渲染器把活动块整体放在回复之前，两者的先后未在同一份数据上对照过。
- 工具调用行与结果行是按 `callId` 折叠的；`callId` 重复或结果找不到对应调用时仍会单独成行（标签「工具输出」）。
- activity 折叠行的展开体间距仍是 8dp：参考用 `Lw{padding:"offset"}`，具体值未实测（参考中折叠行全部收起，按只读约束没有点击展开）。
- activity 折叠行的一行只显示一个标签；参考的标签串（如「加载了工具已搜索网页」）是应用把连续活动拼成一个摘要字符串，本实现按工具名逐条生成，未做同样的合并。
- 折叠行的旋转/淡入用 `animateFloatAsState` 做近似过渡，未对齐参考 `duration-relaxed` 的具体时长与缓动。
- assistant 操作行（复制/朗读/评价/分支）在参考中未能在默认状态下测量到（未悬停时不出现），实现沿用先前记录的 26×26 / gap 2 / top 6 / offset -4。
- 代码块头部不吸顶；展开按钮为禁用占位；没有语法高亮。
- Markdown 不支持数学、脚注、原始 HTML 渲染、远程图片加载与文内锚点；表格列宽为 Compose 估算后均分剩余宽度，未复刻 CSS 的 `data-col-size` 自动布局。
- 附件、听写/语音、朗读、评价、分支均为禁用控件并给出原因；权限四选项可选，但只是 UI 偏好（不写入 AgentOptions、不改变执行权限），弹窗底部对此有说明。没有新增真实功能。
- 部分图标（Up/Stop/Chevron/Code/Expand）为手绘路径，未逐像素对齐原 SVG；activity 折叠行的 chevron、footer 的加号/麦克风/发送箭头/对勾/重置/手/盾牌/齿轮，以及模型弹窗的 chevron 已按参考 SVG path 原样转录。
- 时间显示固定“星期六14:30”样式，未复刻参考的完整相对日期/local 规则。
- 工具 activity 文案按工具名启发式分类，运行中的 reasoning 动态状态未单独区分。
- 最终构建的自动化滚动注入未再送达窗口（列表状态日志确认未收到滚轮事件），上滚/回到底部交互以 15:24 构建截图为准；该手势不涉及后续改动，建议验收时手动复核一次。
