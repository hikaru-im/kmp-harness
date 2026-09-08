# llm-koog 与 DSH llm-pi-ai 职责边界审计

本审计以仓库内固定参考
[`references/deepseek-harness/packages/llm/llm-pi-ai`](../../references/deepseek-harness/packages/llm/llm-pi-ai)
为证据，主要核对其 `adapter.ts`、`context.ts`、`stream.ts`、`replay.ts` 和 README。目标是对齐适配层
职责，不照搬 pi-ai、Node、动态 settings 或附件系统的实现形态。

## Adapter 应拥有的职责

| DSH `llm-pi-ai` 行为 | 当前 Koog 边界 | 状态与验收门槛 |
|---|---|---|
| provider/model 精确路由；一次调用捕获不可变 snapshot | 动态 settings 先物化完整 route/catalog/executor generation；prepared/in-flight call 持有 generation lease | 已具备；更新失败回退旧快照，旧 executor 在最后一个 lease 释放后关闭 |
| 模型能力与部署请求默认值分离 | `LLModel.maxOutputTokens` 只表示能力；`KoogModelRoute.defaultMaxTokens` 才进入 `LlmResolvedModelInfo.defaultMaxTokens` | 已有纯测试；不得重新把 capability 自动发送成请求 cap |
| Harness history/options/tools → SDK 请求 | system/text、通用 options、tool schema、assistant tool-call 与 tool-result 历史已实现；公开 mapper 可从 semantics 装配 | provider-neutral 契约与 request integration 已通过；reasoning 私有字段和 Provider options 仍需具体语义 |
| SDK event/frame → block、usage、finish | text、标准 input/output usage、纯文本 reasoning 与 tool-call stream 已接入共享 `KoogStreamState`/terminal seam；delta-only tool 收尾由 route-bundled policy 决策，公共层不绑定 Provider finish 字符串；OpenAI Responses 的 `response.completed`/`ToolCallComplete` 已由独立 route semantics 接入 | provider-neutral 主路径和 OpenAI 两条 scripted route 已验证；空成功响应固定为 `EMPTY_RESPONSE`，invalid usage/identity/index mode 使用稳定错误；OpenAI Responses 的 reasoning 私有字段与 `stop`/`tool_calls`/`max_output_tokens` 已由专属 semantics/replay codec 覆盖，其他 Provider 私有 usage/reasoning 仍未实现 |
| caller cancellation 和 consumer early-stop 终止上游 | failure mapper 保留 `CancellationException`；两条 `KoogCancellationIntegrationContractTest` 已启用通过；OpenAI scripted client fixture 也覆盖两类取消；loopback JDK HTTP server 已验证默认 Ktor transport 的 caller cancellation、consumer early-stop 和正常 SSE 读取会断开上游连接 | scripted fixture 与 loopback server 已证明当前 JVM transport 的取消传播；更长时间的外部 HTTP engine teardown 仍需 live 验收 |
| Provider 错误归一化 | 通用 failure mapper + route-bundled classifier；loopback HTTP 验收证明真实 Koog/Ktor status/body 能到达 OpenAI classifier | Chat 401/429/400/503 与 Responses 429 已映射并验证密钥隔离；未知异常保持 `UNKNOWN`，外部 Provider live 错误格式仍需单独观察 |
| Provider-native replay metadata 的版本化保存与严格恢复 | Core 已有 replay 通道；`KoogReplayRestorer` 已接入并默认 fail-loud；OpenAI Responses 另有版本化 writer/restorer | OpenAI Responses 已补 version/provider/model、stop reason、block 对齐、reasoning `id/summary/encrypted`、round trip、foreign/mismatch/失败 terminal 测试；真实网络 replay 与其他 Provider codec 仍待各自验收 |
| 一次 adapter call 只触发一次可见 Provider attempt | 活动契约证明 Adapter flow 保持 cold，且每次 collection 只调用一次 `executeStreaming` | 真实 factory 还必须禁用 Koog/client 内部 retry；不能让 SDK retry 绕过 durable step 和 retry policy |
| Provider client、HTTP engine 与凭据生命周期 | factory 每个有效 settings/credential generation 创建独立 executor；正常 dispose、替换与 route 注册失败 rollback 均有关闭契约；OpenAI scripted HTTP fixture 已驱动真实 `OpenAILLMClient` 验证 client close 与取消传播 | 动态生命周期已具备；fixture 仍不等于外部网络/HTTP engine live teardown |
| provider idle timeout 与 transport teardown | 交由具体 client/factory；OpenAI factory 将 provider settings 的 `socketTimeoutMillis` 传给 Koog/Ktor | OpenAI loopback 已证明 200 后无 SSE 数据会以 `TIMEOUT` 失败并关闭连接；仍无跨 Provider watchdog，真实外部 engine/live teardown 继续单独验收，普通 request timeout 不能冒充 stream-idle timeout |

## 明确不属于 Adapter 的职责

- 重试决策、backoff、sleep 或第二次 Provider 调用；这些属于 retry Plugin 与持久步骤边界。
- Session 日志、消息持久化、compaction、分支/fork 和 assistant message 提交。
- 工具执行、工具副作用、approval 或 AgentLoop 调度；Adapter 只转换 schema、历史和 tool-call stream。
- fallback model、自动模型切换或负载均衡语义；它们会让记录的 requested model 与真实执行对象不一致。
- 把 Provider 私有字段加入 wire contracts；只有真正跨层消费的稳定数据才进入 `modules/llm`。

## 当前有意保留的差异

- 已对齐 DSH 的 settings 驱动 Provider profiles、dormant adapter、独立 configurable-provider directory 与
  generation snapshot：配置更新创建完整 executor 后再替换 routes，失败恢复旧快照，在途请求继续旧 generation。
- DSH 仍支持当前 Harness 尚未完整具备的 attachment/image、model discovery 和 app attribution headers；
  `llm-koog` 不应私自增加旁路实现，也不能宣称这些能力可用。
- pi-ai 原生事件携带完整 terminal assistant message；Koog 暴露 `StreamFrame` 与 `ResponseMetaInfo`，所以 finish、
  usage 和 replay 必须按选定 Koog client 的真实字段重新证明，不能照抄 pi-ai 字符串。

## 声明 Provider 可用前的额外证据

除 [IMPLEMENTATION_PATH.md](IMPLEMENTATION_PATH.md) 的阶段契约外，还必须具备：

1. 记录型 executor 证明一次 Harness stream collection 只调用一次 `executeStreaming`（基础契约已具备）；
2. 真实 client 配置证明 SDK retry 与 fallback model 均关闭（OpenAI scripted fixture 已证明请求路径和无 fallback
   builder 装配）；
3. consumer early-stop、caller cancellation、provider idle timeout 三类 teardown 验收（OpenAI loopback 已覆盖
   三类默认 Ktor transport 行为；其他 Provider 和外部 live engine 仍待各自验收）；
4. 版本化 replay codec 的成功、篡改、provider/model mismatch 和 content mismatch 测试；
5. 测试凭据 live text/tool 请求与资源关闭；生产 file-backed Responses 已使用测试凭据到达外部 HTTP 200，
   随后由 Koog #2211 阻塞；当前连接没有 Chat Completions 渠道，text/tool 成功路径仍未验收；
6. 日志、异常、settings、replay state 和测试产物的 secret audit；live harness 已加入 chunk/异常/非凭据文件
   扫描与临时目录清理，已随本次外部 Responses 请求执行通过。

其中任何一项未执行，都应明确记录为未验证，不能用 JVM build 或替身测试外推。
