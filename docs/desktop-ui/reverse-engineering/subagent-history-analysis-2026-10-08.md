# 子 Agent 历史结构与主对话组件复用分析

日期：2026-10-08。本批只分析与制定契约；终端工作者尚未释放 ThreadPanels.kt，子 Agent 实施尚未派发。

## 原版安装包证据

当前 `/Applications/ChatGPT.app/Contents/Resources/app.asar` 的全文 SHA256 为 `797be85cd4c5b1be359c55c1d57970671cfba284a5eacf7612ee0ae9add1c360`。同名 evidence JSON 保存三个资源的精确偏移、长度、SHA，以及四段关键字节和八段当前生产源码。资源按归档原字节重新核对，不依赖仓库内旧版本 JS。

- `local-conversation-subagents-panel-tab-310e9b710ac5.js` 将 `local-conversation-thread-faa16cd8ed14.js` 的 export `t` 导入为 `k`。选中子 Agent 后直接渲染 `k`，传入真实 hostId/conversationId，`canInteract` 只在明确为 true 时开启；Loading 显示独立 skeleton。这足以证明它复用对话组件；本分析没有追踪该组件内部全部动作，也没有验证原版运行时。
- 同一页面有详情返回、头像、模型和 reasoningEffort 元数据；不必为子 Agent 再造一套消息卡片。
- `verify-descendant-caeedc651b54.js` 排除选择父会话、循环、排除集合成员与已知外 Host；缺少祖先数据时结合后代清单及完整性决定拒绝或请求 hydration。此证据不等于 Harness 已支持原版完整持久历史。

## 当前生产链路

| 层 | 已有能力 | 本次发现的缺口 |
| --- | --- | --- |
| SessionApi.readChildHistory | 返回 parent、child、已验证祖先路径和 List<Message> | 无需新增协议获取结构化正文 |
| ConnectionThreadPanelsRepository.readDetail | 检查 parent/child、路径起止、无环，保持只读 | `transcriptEntry/contentText` 将推理、工具调用和嵌套结果压成文本；丢失 source、call ID、isError、内容类型 |
| ChildAgentDetail | key、扁平 entries、canInteract | 没有完整 typed Message 字段 |
| readOnlySnapshot | 再校验 scope/key，复制 entries 并清除交互权限 | 添加 typed 字段后必须连同嵌套 content 一起快照 |
| ThreadPanelsController/reducer | owner epoch、请求和 detail generation、选中 child、tab、scope 防止晚到响应 | 本次保留现有防护，不改权限投影 |
| ChildDetailPanel | 状态区分、详情标题、返回和只读提示、可选择全文 | Ready 用角色标签加纯文本，未复用主对话气泡与 disclosures |
| 主 ConversationComponents | User 角色和 User source 共同决定气泡；推理/工具 disclosure、递归全文 | MessageCard 目前 private，需要最小内部复用入口 |

生产概览仍是 live registry 的不完整覆盖；只提供 Running/Unknown，不能由 idle 推断 Done/Waiting。生产历史读取不授予 mutation 权限。新增渲染不应改变这两项事实。

## 建议的窄切片

在 ChildAgentDetail 末尾增加默认 null 的 typedMessages 字段，保留旧 entries 调用兼容。生产 adapter 从真实历史保存 typed 消息，旧 entries 仅作为兼容投影；展示层优先使用 typedMessages，null 才进入原有扁平历史兼容分支。显式空 typed 列表表示成功空历史，不能回退到旧正文。保持真实消息顺序、ID、来源、推理、调用 ID、嵌套结果和错误标志。

将现有 MessageCard 提供为模块内部入口，让主对话和子 Agent 调用同一实现。子 Agent 的标题、状态与只读授权仍由原面板负责，空历史仍显示 No messages。不要从扁平角色猜造 UserMessageSource/ModelMessageSource，不建立第二套 disclosure 或样例消息。

实施范围及必要验收见 `subagent-history-contract-2026-10-08.md`。仅在终端写集释放后顺序派发给同一指定模型。本批不引入订阅、持久化后代搜索或子会话 composer；刷新时滚动/展开保留不作为本切片完成结论。

## 验收边界

本次证据为静态归档与当前源码核对，没有运行构建或测试。前一消息切片的 33/33 是父线程冻结副本的临时验证，旧交付报告及注释仍待修正，不能据此宣称本切片通过。原版同状态像素、原生窗口、真实远端和完整持久后代历史仍未验证。
