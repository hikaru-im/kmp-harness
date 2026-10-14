# 共享 Tooltip 与侧栏接入契约

日期：2026-10-08。实施模型：`SHUAIAPI/gpt-6.1-sol`，provider `opencodex`。父线程负责分析、审阅与串行验证。

## 原版来源

本次核对 `/Applications/ChatGPT.app/Contents/Resources/app.asar`：大小 561986433，payload base 5808772。`webview/assets/app-shared-6c00c2afcf84.js` 的绝对 offset 为 68309201，大小 7973084，SHA256 为 `c85c0913e7ae7478e2756a2a34b172ddac37ae993d40443709a67da08ad4f611`。提取证据另存同目录的 JSON 与 TXT。

- C5 wrapper 对 disabled/null content 直接返回原 trigger；悬停延迟为显式参数/context/200ms。
- Nvi 的 delayOpen 显式覆盖为250ms；底层 primitive provider 的700ms是另一默认值。
- 键盘焦点立即打开。点击、失焦、离开、context menu 与 Escape 关闭简单提示。
- triggerPopupOpen 抑制简单提示。默认 top/center；源码声明不能证明像素一致。

## 当前切片

旧 HarnessTooltip 使用 inline Column，显示时会挤动布局；本切片改为不获取焦点的 anchored Popup。保留旧 controlled visible API；新增 hover/focus API 的 Modifier 交给实际 clickable/focusable trigger，不增加可 Tab 的包装节点。

只接真实 RailNavItem，使用实际模型标签与已有动作。保留52dp侧栏、36dp item、44dp pitch、rail-ID/Role.Tab 与 More 的 root anchor。account avatar 和 compact bottom bar 不在本切片。

写集为 foundation 新 HarnessTooltip.kt、HarnessComponents.kt 旧 API 兼容、AppShell.kt 侧栏钩子与生命周期、desktop 新 TooltipInteractionTest.kt。生产不得添加假数据或服务。

## 生命周期与交互

1. 默认 hover200ms，focus立即显示；disabled/null/blank不挂 popup，不改变按钮业务。
2. 点击、Escape、blur、leave取消 pending/open；不得吞 click/Enter/Space/Tab，不得双派发。dismiss后持续 hover/focus不自动重开，pointer move不恢复。
3. content/enabled/page scope变化、A→B→A、unmount、More/account打开均取消；无关重组不取消。
4. 复用 harnessPopoverPosition 的 flip/clamp/RTL；窗口 margin 内限宽，长标签完整换行，显示前后 anchor/page布局稳定。

## 父线程验证

实施释放哈希后冻结源码，逐类串行构建。正常交互使用真实 mouse/key 输入、所属root与测得bounds，10秒deadline；测试fixture与生产数据来源区分。重点验证延迟/焦点、单次正确业务派发、过期定时器取消、实际侧栏和More suppression、布局及edge/RTL。

HARNESS_TOOLTIP_CAPTURE_DIR 保存1440×900/600×800 × 浅深主题的实际popup root截图、hash、尺寸和geometry。新增Tooltip测试及必要壳层回归通过后才记录最终数量。

## 最终定向验收

2026-10-08 实施写集释放后，父线程冻结2,201个输入，在同一源码manifest与隔离build-dir中逐类串行运行：`TooltipInteractionTest` **29/29**、`HarnessAppThreadPanelsShellTest` **8/8**；无失败、错误或跳过。新API命名为`HarnessTooltipTrigger`，保留旧`HarnessTooltip(text, visible)`调用兼容。

首轮测试编译因两个不可用的GetTextLayoutResult helper失败，执行0tests；修正为当前Compose支持的SemanticsActions读取后编译通过，运行28/29。唯一失败为160×200 RTL超长reader的真实滚轮末尾断言：正确Popup root的144×184 reader滚动范围4148px，但冻结动画时钟后只推进6px。最后仅在该用例滚轮阶段开启`mainClock.autoAdvance=true`，保留真实wheel、全文、换行、无ellipsis和10秒断言；生产Tooltip与公共helper未改。最终29/29确认该修正。失败轮次独立保留，不累计。

4张独立Popup root PNG和4份geometry覆盖600×800/1440×900、浅深色与交替LTR/RTL；另有完整壳层80PNG和80geometry。父线程核对文件数、SHA256、bitmap尺寸、独立Popup root和8px窗口边界，并目视检查紧凑浅色/宽窗深色Tooltip及紧凑浅色/拖宽navigator深色终端组合。全文可读，提示不挤动anchor，壳层导航和面板回归通过。

独立XML、summary、完整冻结manifest、交付哈希、命令日志及失败历史位于`../verification/tooltip-rail-2026-10-08/`；截图位于`../captures/tooltip-rail-2026-10-08/`。实现仍使用真实侧栏数据和业务回调，受控fixture只用于测试。这是离屏交互与截图完整性验收；原版同状态像素、原生窗口与远端未验收。整体复刻目标继续进行中。
