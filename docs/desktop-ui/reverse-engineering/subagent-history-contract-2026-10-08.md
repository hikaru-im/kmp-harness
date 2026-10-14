# 子 Agent typed 历史复用窄契约

日期：2026-10-08。状态：分析完成，已向同一工作者派发。实施模型固定 `SHUAIAPI/gpt-6.1-sol`，provider=`opencodex`。父线程负责冻结和串行测试。终端R3写集已释放并通过26/26；使用 `/tmp/harness-terminal-reading-parent-candidate-20261008-reviewed-r3` 及其manifest作为唯一基线，终端生产SHA为 `bc4d243d0f7af1b10ec4835b9ccb1c3776a64f84bf3a235821f2231817f1652e`。ThreadPanels.kt本批只修改Child正文与相关imports。

## 有限写集

- `apps/shared/src/im/hikaru/harness/client/panels/ThreadPanelModels.kt`：只增加 ChildAgentDetail typed 历史契约及必要 imports/校验。
- `apps/shared/src/im/hikaru/harness/client/panels/ThreadPanelsRepository.kt`：readDetail 映射和 readOnlySnapshot 的 typed 快照及局部 helper。
- `apps/shared/src/im/hikaru/harness/client/panels/ThreadPanels.kt`：只改 ChildDetailPanel 的正文分支和必要 imports，不碰终端展示。
- `apps/shared/src/im/hikaru/harness/client/app/ConversationComponents.kt`：只为现有 MessageCard 开放最小模块内部入口；主对话视觉/展开规则保持现有实现。
- `apps/shared/test/im/hikaru/harness/client/panels/ConnectionThreadPanelsRepositoryTest.kt`：增加真实 adapter 和快照回归。
- 新增 `apps/jvm-app/test/im/hikaru/harness/desktop/SubagentHistoryInteractionTest.kt`。

保留所有既有 dirty 文件。工作者不运行构建/测试、不改 controller/reducer/connection/transport/Comet/config/git、不创建工作者；父线程管理文档与验证。

## 数据与展示

1. `ChildAgentDetail` 在现有参数末尾增加 `typedMessages: List<Message>? = null`，旧 positional/named 调用保持兼容。非 null 时要求消息 ID 非空、唯一，并与兼容 entries 的顺序 ID 一致；不得由 UI 合成 ID。
2. 生产 readDetail 只接受原已验证的历史结果，保存服务返回的 typed messages。顺序、role、source、模型元数据/replayState、ReasoningContent、ToolCallContent 的 ID/name/arguments、递归 ToolResultContent 的 call ID/content/isError 全部保留。旧 entries 可继续生成用于兼容；不能将扁平字符串重新解析为消息。
3. repository 边界和 readOnlySnapshot 复制消息列表及所有嵌套 content 列表，防止外部可变集合在交付后更改可见历史。保持现有 scope/child/lineage 校验及 canInteract=false；不得让可读历史成为可发送权限。
4. Ready 的 typedMessages 非 null 时使用主对话现有 MessageCard 的同一内部实现；不是复制源码。只有真实 USER+UserMessageSource 使用人类气泡，工具/插件来源保持中性；推理与工具头独立可展开，嵌套正文和错误标志保留。
5. typedMessages=[] 显示 No messages，不回退 entries；typedMessages=null 保留现有纯文本兼容分支，不制造 typed 来源。Loading、Error、Unavailable 不当作空成功，也不展示旧文本冒充 Ready。
6. child 展示身份包含 scope、ownerGeneration、选中 child；换 owner/child 不复用旧展开状态。同 ID 出现在不同 child 不能共享状态。保持只读提示、Back、真实元数据与现有 selectedSummary/interactiveDetail 防护。原版跨刷新阅读状态没有验证，本切片不宣称具备该能力。
7. 不添加新消息协议、附件、markdown、跨消息调用配对、虚构运行耗时/成功状态、composer、轮询或订阅。

## 必要验收

- Adapter 使用包含真人、USER 工具、USER 插件、助手推理和嵌套工具错误结果的真实 ChildHistoryResult，逐字段证明 typed 结构保留；修改返回前持有的外部消息/content/嵌套列表，不能改变已交付快照。
- 生产 ThreadPanels UI 用真实 ThreadPanelsController + ConnectionThreadPanelsRepository + 测试 SessionApi，验证 parent/child 请求参数到 visible 正文的链路，正常选择/展开用真实鼠标或键盘输入。
- 确认只有真人气泡，工具/插件不会冒充 User；推理和工具分别展开；长多行、嵌套结果末尾完整可达、无省略。
- 空 typed、legacy null、Loading/Error/Unavailable、只读无 composer 均有断言；返回/切换 child、相同消息 ID 的跨 owner/child、历史撤销和迟到响应不显示外身份正文。
- 原有 ThreadPanelsControllerTest 中 owner/请求/撤销防护和原有 repository 断言不削弱。新增 suite 的正常交互不能用 performClick/performScrollTo 代替真实输入；复用已验证 wheel/root helper，10s deadline，禁止 skip/吞异常。
- 320×600、640×800 × 浅深色 × 收起/展开保存截图，使用 HARNESS_SUBAGENT_HISTORY_CAPTURE_DIR；每图存实际尺寸、SHA、viewport/text 几何、全文末尾证据。截图只证明 Harness 受控 fixture 的离屏生产 UI。

交付 `/tmp/harness-subagent-history-delivery-20261008.json` 和 `.md`，包含有限 changed files 的 before/after SHA、实际模型/provider、建议测试类与新增数量、tests_run=false、builds_run=false、write_set_released=true，然后停止写集。父线程冻结同一 manifest 后顺序验收新增 repository/UI、既有 controller/面板/主对话/完整壳层；只针对具体剩余风险扩大测试。
