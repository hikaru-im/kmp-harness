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
- `LiveConversationSurface.kt`：composer 98px 结构、22 圆角、`#363636`、0.5dp 内侧微光（近似 1px inset 阴影）、多行输入上限、Enter/Shift+Enter、占位文字按首页/会话切换、footer 控件右侧对齐。
- `AppShell.kt`：仅 `PageId.Thread` 走全尺寸会话布局，composer 悬浮在底部，实测高度通过 `onGloballyPositioned` 写入 `LocalConversationBottomInset`；其它页面仍走原布局，标题栏阴影未改动。
- 时间戳：`Message.timestampMillis` 来自会话事件 `event.time`，经 `SessionProjection`、client `Message`、`LocalConnection` 传到 UI；没有时间就不画时间，未伪造。显示格式为“星期X + 24 小时制”。

## 4. 改动文件

```
apps/shared/module.yaml
apps/shared/src/im/hikaru/harness/client/app/ConversationAppearance.kt   (新增)
apps/shared/src/im/hikaru/harness/client/app/ConversationMarkdown.kt     (新增)
apps/shared/src/im/hikaru/harness/client/app/ConversationComponents.kt
apps/shared/src/im/hikaru/harness/client/app/LiveConversationSurface.kt
apps/shared/src/im/hikaru/harness/client/shell/AppShell.kt
apps/shared/src/im/hikaru/harness/client/connection/Connection.kt
apps/jvm-app/src/im/hikaru/harness/desktop/connection/LocalConnection.kt
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
| `final-markdown-light-1090x760.png` | 浅色 + 窄窗口：标题、列表、代码块、引用、表格 |

错误卡片描边与 activity 折叠行改完后重新构建（`Build successful`）并重启夹具（home `…-variants`，选中 `fixture-error-20261017`），重新截取了 `final-error-dark-1467x870.png`（替换掉改动前无描边的版本）与两张 activity 状态图；截图前等待动画稳定，测量后已把展开的折叠行收回、鼠标移开。

把工具失败改回 activity 折叠行后再次构建（`Build successful`，17:48 刷新共享 jar）并重启同一夹具，重新截取 `final-error-dark-1467x870.png`；展开态在本机与参考对照后确认：折叠行展开时 chevron 下向、正文为工具输出。

加入「用时」头、工具结果折叠与动作行合并后再次构建（`Build successful`，18:39 刷新共享 jar）并重启夹具，错误会话与长会话都重新截图核对：活动行只剩一条「已读取 summary.txt」，展开体是折叠进来的工具输出，动作行只出现在末次回复下。
| `reference-layout.json` / `reference-icons.json` / `reference-fades.json` / `reference-markdown.json` / `reference-markdown-css.txt` | 参考 DOM 与 SVG/CSS 证据 |

像素取样（截图转 sRGB 后读取原始像素）：

| 位置 | 实测 |
| --- | --- |
| 画布 | `#181818` |
| composer 主体 | `#363636` |
| composer 边缘描边 | `#404040`（近似微光，非真实 1px blur） |
| 用户气泡 | `#163f75`~`#173d74`（目标 `#173e76`，受显示色彩配置与抗锯齿影响） |
| 单行用户气泡高度 | 42px（10 + 22.75 + 10 ≈ 42.75） |
| composer 总高 | 98px（实测 752→850 边界） |
| 错误卡片描边 | 左 `x=534`、右 `x=1269`、上 `y=326` 均为 `#3b3b3b`；按 `#181818` 叠加白 15.7% 计算为 `#3c3c3c`，与参考 `rgba(255,255,255,.156)` 一致。取样自 17:04 构建（当时工具失败还渲染成卡片）；改成 activity 折叠行后，卡片只在会话级错误时出现 |
| activity chevron 悬停 | 放大到 8× 目视确认是参考那种填充 chevron（不是细描边箭头）；旧版本用 `HarnessIconName.ChevronRight` 时明显偏细 |
| activity chevron 展开 | 同一位置确认旋转为下向 chevron，位置较收起态下移约 2px（绕 14dp 方框中心旋转） |

## 7. 已知限制（未声称完整复刻）

- composer 内侧微光用 0.5dp 描边近似 1px inset 阴影，没有实现真正的模糊。
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
- 附件、权限调整、听写/语音、朗读、评价、分支均为禁用控件并给出原因，没有新增真实功能。
- 部分图标（Up/Stop/Chevron/Code/Expand）为手绘路径，未逐像素对齐原 SVG；activity 折叠行的 chevron 已改为参考原 path。
- 时间显示固定“星期六14:30”样式，未复刻参考的完整相对日期/local 规则。
- 工具 activity 文案按工具名启发式分类，运行中的 reasoning 动态状态未单独区分。
- 最终构建的自动化滚动注入未再送达窗口（列表状态日志确认未收到滚轮事件），上滚/回到底部交互以 15:24 构建截图为准；该手势不涉及后续改动，建议验收时手动复核一次。
