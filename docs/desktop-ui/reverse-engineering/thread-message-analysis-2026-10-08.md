# Thread 消息与工具展示分析 — 2026-10-08

## 交付范围

本次为只读分析，生产源码与测试均未修改。父线程最后指令将范围收窄为本分析交付；Menu、Scheduled、Tooltip 不在本交付范围。实施未完成，也未启动。仅新增本文与相邻 evidence JSON。

实际模型为 `gpt-6.1-sol`；交接配置为 `SHUAIAPI/gpt-6.1-sol`、provider `opencodex`。provider 来自父线程交接，未独立检查运行时路由。`tests_run=false`、`builds_run=false`、`write_set_released=true`。

## 原版资源与证据边界

当前归档：`/Applications/ChatGPT.app/Contents/Resources/app.asar`，大小 `561986433` bytes。四个 little-endian uint32 header 值为 `[4, 5808764, 5808760, 5808755]`；payload base 为 `5808772`。本交付重新核对了这些已提取资源的长度、SHA256 与归档对应字节范围。未更新应用，也未重新提取或遍历资源。

所有 offset 为零起始 byte offset；excerpt 的 UTF-8 范围为 `[start, end)`。relative offset 从 payload base 起算，absolute offset 从归档头起算。以下是本报告实际引用的 bundle，完整精确片段见文末及 evidence JSON。

| Bundle | Relative offset | Absolute offset | Size bytes | SHA256 |
| --- | ---: | ---: | ---: | --- |
| user-message-a43cebb8c1d9.js | 518675671 | 524484443 | 28190 | `6bfa47663931f44cef65aedc164ffcd1994d8f252419ac1e953c280ca9f66643` |
| sites-end-resource-7e6c33984bd6.js | 448217193 | 454025965 | 407677 | `54f47c7e3f9935314d20110f71ad5a37c4fff17710235c7df9460e00b237a317` |
| local-conversation-turn-310d2b91b0d8.js | 282263808 | 288072580 | 93614 | `b3d86bc43fa4dd0a96c1ca428301fe18f37c52896a995bbd3525ccfcfd9a3828` |
| agent-activity-item-e4e1a1c7e3f6.js | 39212091 | 45020863 | 22762 | `813df4abf7873a1076d25db54bbf073a753f43aa61d45943c82347201ea85cc2` |
| app-shared-f2d570e85ceb.css | 70473513 | 76282285 | 1247457 | `48caa3b5cef73fb568b697fe9aa0c6724916333b035b6c36d7958713ed759a38` |

JS 可证明组件结构、传入的状态与 class 字符串；CSS 可证明声明存在。本次未启动原版页面、未取得 computed style、未采集窗口或 popup 截图，没有测出渲染后的 padding、字体、宽高与像素。CSS 变量引用不等于已知计算值，不能据此声称像素一致。

## 当前 Compose 与 typed Message

当前源码的精确引用范围如下；每段 verbatim source、byte offset 与源文件 SHA 已存入 evidence JSON。其他 transport 结论沿用已完成的 exact-file CodeGraph 分析，未扩展到新文件。

| 文件 | 行 | 证据 |
| --- | ---: | --- |
| `apps/shared/src/im/hikaru/harness/client/connection/Connection.kt` | 60–90 | typed-message-content |
| `apps/shared/src/im/hikaru/harness/client/connection/Connection.kt` | 100–129 | typed-message-source-and-fields |
| `apps/shared/src/im/hikaru/harness/client/app/ConversationComponents.kt` | 57–59 | viewport-stable-message-key |
| `apps/shared/src/im/hikaru/harness/client/app/ConversationComponents.kt` | 69–93 | role-only-current-classification |
| `apps/shared/src/im/hikaru/harness/client/app/ConversationComponents.kt` | 105–123 | reasoning-disclosure |
| `apps/shared/src/im/hikaru/harness/client/app/ConversationComponents.kt` | 125–147 | tool-call-disclosure |
| `apps/shared/src/im/hikaru/harness/client/app/ConversationComponents.kt` | 149–166 | recursive-tool-result |

`ConversationViewport` 使用 `LazyColumn`，每个 Message 一个 item，以 `message.id` 为稳定 key。`MessageCard` 目前仅按 `role == USER` 分类；所有消息是全宽 `HarnessSurface`，用户显示 User 图标与 You，助手显示 Host 图标与 Harness。

Reasoning 默认折叠，展开显示完整 text；ToolCall 默认折叠，展开显示完整 arguments；ToolResult 始终显示并递归渲染其 content。`isError` 使用错误颜色与 Tool failed 文案；当前非错误结果有 Check/Success 装饰，最小切片建议改为中性结果装饰，避免把 `isError=false` 推断为已完成执行或业务成功。错误/retry 与既有滚动、展开输入路径必须保留。

| 类型 | 已有字段/语义 | 可以用于展示 |
| --- | --- | --- |
| Message | id、role、content、source | 稳定 key、来源分类、逐消息内容 |
| TextContent | text | 完整正文 |
| ReasoningContent | text | 独立可展开 reasoning |
| ToolCallContent | id、name、arguments | 调用名与完整原始参数 |
| ToolResultContent | toolCallId、递归 content、isError | 原始结果与结果错误标记 |
| MessageSource | User、Plugin(plugin)、Model(provider/model/replayState)、Tool(callId) | 区分人类、工具、模型和插件来源 |

LocalConnection 保留 role/source、call/result ID、递归内容及 isError；RemoteConnection history 解码 `List<Message>`。工具结果可能是 `role=USER + ToolMessageSource`。当前 role-only 分支会把这样的消息渲染为 You；下一切片应按 source 分类，只把明确的 `UserMessageSource` 展示为用户气泡，工具与插件使用中性来源样式。不能将所有 USER 消息直接认作人类输入。

当前 Message 无 typed turn ID、消息 timestamp、每调用 timing、running/completed 生命周期、exit code、file diff、artifact 描述或 token 计数。`SessionSummary.createdAt` 不是消息时间，`ModelMessageSource.replayState` 是不透明 JsonElement，不能将其当作稳定的 UI 状态契约。原始文本可能包含这些词或值，但不构成可供 UI 可靠推断的字段。

## 与原版的可证差异

- 用户 bundle 的 bubble class 使用 `max-w-(--user-chat-width)`、`break-words` 与 thread margin padding，另有 `items-end` 布局；现有 Compose 用户卡片全宽。上一阶段提取确认 alignment 默认值为 end，本交付的短片段主要证明该 alignment 输入及末端布局分支，未渲染测量实际宽度。
- 助手 bundle 的普通外层为 `group flex min-w-0 flex-col`；现有 Compose 给助手加了统一全宽 surface、图标和可见标题。该 JS class 证据仅证明外层结构，不证明助手所有子项都无卡片装饰。
- 上一阶段已确认原版 You said / ChatGPT said 标题为 sr-only；不应把它们直接抄成普通可见标题。本交付未给这一点新增短片段，需引用时沿用既有提取分析，不能作为本次新增渲染证据。
- 原版 turn 折叠接收 `hasFinalAssistantStarted`、`isTurnCancelled`、`persistedCollapsed` 等状态。当前 Message 无该 turn 生命周期，不能复制原版自动合并/完成折叠行为。
- 原版 activity item 读取 callId、completed/success 与 operation status，显示 running/failed/completed。当前模型仅能证明结果的 `isError`，无法生成同等生命周期。已完成分析指出丰富 exec 展示另有 output/timing；本次短片段直接支持的是 callId 与状态关系，不声称已证明 timing 的具体字段路径。

## 建议首实施切片

仅建议修改 `apps/shared/src/im/hikaru/harness/client/app/ConversationComponents.kt` 的展示层；本次未实施。

1. 保留 `message.id` 稳定 key 和一个 Message 一个 item，按明确 source 区分人类气泡、助手正文、工具/插件来源。真正用户消息在末端展示，设置宽度上限并允许全文换行；source 不匹配的 USER 消息不可误标 You。
2. 助手正文减少统一全宽卡片装饰；保持正文完整、内容次序及错误/retry 行为。
3. Reasoning 与 ToolCall 改为紧凑、中性 disclosure，保留默认折叠、完整原始文本/arguments、点击与键盘可访问的展开语义。
4. ToolResult 保留递归完整内容与 `isError` 错误呈现。非错误结果使用中性标记，不新造 success/completed。

不跨 Message 配对 call/result，不新增 markdown/file/turn 协议，不改 transport/model，不推断 running、timing、success 或 completion。即使 call/result ID 可对应，本切片也不据此重新分组消息或隐去原始内容。

## 建议验收与实际执行

建议父线程批准实施后验证：

- `role=USER + ToolMessageSource` 不显示人类 You 气泡；UserMessageSource 的长中文/长路径换行，正文完整。
- Reasoning/ToolCall 默认折叠，真实指针与键盘展开/折叠后原始内容完整；相同 id 更新内容不误重置业务状态。
- 嵌套 ToolResult 顺序完整，错误仅由 isError 表达，无 running/完成/耗时假状态。
- history reload、会话切换、滚动与请求失败/retry 的现有断言不减弱。
- 在 600×800、1024×768、1440×900 的浅/深主题及 resize 场景检查用户宽度、助手正文、disclosure 和长内容。截图和 geometry 应采集真实目标内容，单独报告结构与像素结果。

建议复跑 `im.hikaru.harness.desktop.LiveConversationInteractionTest` 与 `im.hikaru.harness.desktop.HarnessAppThreadPanelsShellTest`。当前文件分别有 11 / 14 个 `@Test`；既有分析记录 Shell 类含 8 个窗口/主题 geometry 场景，该数量不代表本次运行通过。实际新增用例为 **0**，运行测试为 **0**，运行构建为 **0**，本次未采集截图。

源码审计以 continuation pre-document-write 快照为 before，未重建任务初始 hash；逐文件 before/after 和是否匹配见 evidence JSON 与 `/tmp/harness-thread-message-analysis-20261008.json`。只读文件若有并行变动，仅作为观察记录，不能归为本交付修改。两份允许文档的 before 为不存在；after SHA 见最终 `/tmp` 报告，以避免文档自引用 hash。

## 精确 bundle 片段

### user-bubble-width-and-padding

`webview/assets/user-message-a43cebb8c1d9.js`，UTF-8 bytes `[18606, 18921)`；证据类型：`javascript_structure_or_behavior`。

```javascript
dt){let e=g(`bg-user-message text-user-message min-w-0 max-w-(--user-chat-width) overflow-hidden break-words px-(--thread-content-margin) [&_.contain-inline-size]:[contain:initial]`,Q.bubble,k===`start`&&Q.leadingBubble,M&&Q.compactMessageBubble,Ye&&Q.otherUserMessage,o!=null&&Q.accentedBubble,M?`py-2`:`py-2.5`,Ve
```

### user-alignment-input

`webview/assets/user-message-a43cebb8c1d9.js`，UTF-8 bytes `[14644, 14849)`；证据类型：`javascript_structure_or_behavior`。

```javascript
(e){let n=(0,Lt.c)(170),{message:r,alignment:i,accentColor:o,senderAccountUserId:s,sentAtMs:u,collapsedLineCount:f,alwaysShowActions:p,compactActions:m,hideActions:h,messageStatus:ee,messageStatusIcon:ne,m
```

### user-end-alignment-class

`webview/assets/user-message-a43cebb8c1d9.js`，UTF-8 bytes `[21561, 21661)`；证据类型：`javascript_structure_or_behavior`。

```javascript
flex w-full flex-col items-end gap-1`:`grid max-w-full grid-cols-[auto_minmax(0,max-content)] items-
```

### assistant-outer-wrapper

`webview/assets/sites-end-resource-7e6c33984bd6.js`，UTF-8 bytes `[110432, 110542)`；证据类型：`javascript_structure_or_behavior`。

```javascript
group flex min-w-0 flex-col`,t[194]!==ue||t[195]!==n.sentAtMs||t[196]!==Ge||t[197]!==ie?(At=!ie&&!Ge&&n.sentAt
```

### turn-collapse-state-inputs

`webview/assets/local-conversation-turn-310d2b91b0d8.js`，UTF-8 bytes `[79330, 79610)`；证据类型：`javascript_structure_or_behavior`。

```javascript
hasFinalAssistantStarted:ei||!W&&ir,isTurnCancelled:!G&&rt,persistedCollapsed:te,onSetCollapsed:ne,forceExpanded:U||!Gt&&ut!==`off`&&(V||j!=null&&M!=null&&j>=M-1),disableCollapse:s!=null||U||We,preventAutoCollapse:Gt&&W||lt||ar,hasInlineSubagentActivity:ir,inlineSubagentActivityC
```

### turn-cancellation-state

`webview/assets/local-conversation-turn-310d2b91b0d8.js`，UTF-8 bytes `[61179, 61299)`；证据类型：`javascript_structure_or_behavior`。

```javascript
rogress`:nt===`inProgress`,rt=_.status===`cancelled`,it;t[5]===g?it=t[6]:(it=()=>g==null?void 0:Hi(g.params.input,g.item
```

### tool-call-and-result-state

`webview/assets/agent-activity-item-e4e1a1c7e3f6.js`，UTF-8 bytes `[6476, 6811)`；证据类型：`javascript_structure_or_behavior`。

```javascript
s??null):null,c=i==null?o?.operationId??r.callId:i.id,l=nt(lt,c),u=i??l,d=u==null?o==null?r?.completed??!1:yt(o.status):yt(u.status),f=u==null?r?.success:u.status===`error`?!1:u.status===`success`||u.status===`warning`,p=`running`;d&&(p=f===!1?`failed`:`completed`);let m;t[0]===r?m=t[1]:(m=r==null?null:L(r),t[0]=r,t[1]=m);let h=m,g;t
```

### css-user-width-declaration

`webview/assets/app-shared-f2d570e85ceb.css`，UTF-8 bytes `[347583, 347614)`；证据类型：`unrendered_css_declaration`。

```css
max-width:var(--user-chat-width
```

### css-thread-padding-declaration

`webview/assets/app-shared-f2d570e85ceb.css`，UTF-8 bytes `[514178, 514221)`；证据类型：`unrendered_css_declaration`。

```css
padding-inline:var(--thread-content-margin)
```
