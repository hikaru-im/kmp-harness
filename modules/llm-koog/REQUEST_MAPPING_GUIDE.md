# Koog 请求映射实现指南

这一阶段区分两种职责：`llm-koog` 通用层负责不会因 Provider 改变的字段，具体 Provider
mapper 负责 stop、reasoning effort 等具有不同参数类型和约束的字段。不要把 Provider 专属字段
塞进通用 `LLMParams.additionalProperties` 来绕过类型检查。

## 当前已经真实支持的通用子集

| Harness 输入 | Koog 输出 | 约束 |
|---|---|---|
| `system` | 第一条 `Message.System` | 使用 `RequestMetaInfo.Empty`，不伪造时间 |
| user/assistant `TextBlock` | 保序的 `MessagePart.Text` | 保留 Harness message id |
| `sessionId` | `Prompt.id` | 无 session 时依次回退到末条 message id、`provider/model` |
| `temperature` | `LLMParams.temperature` | 通用 Koog 范围是 `0.0..2.0` |
| `maxTokens` | `LLMParams.maxTokens` | Harness `Long` 必须可无损转成 Koog `Int` |
| 已解析模型 | `KoogRequest.model` | 必须使用 Adapter route 给出的同一 `LLModel` 实例 |

`purpose` 是 Harness 的调用用途，不是 Provider 请求参数，继续留在 Runtime 一侧。工具 schema 与
tool-call/tool-result 按 [TOOL_MAPPING_GUIDE.md](TOOL_MAPPING_GUIDE.md) 单独实现。

## 为什么 stop 和 reasoning effort 不能写在通用 mapper

Koog 1.1.1 的通用 `LLMParams` 没有这两个字段。具体客户端使用不同子类，例如：

- OpenAI Chat 使用 `OpenAIChatParams.stop` 和 `reasoningEffort`；stop 最多四项；
- Anthropic 使用 `AnthropicParams.stopSequences`，reasoning 使用 `thinking` 配置而不是相同枚举；
- OpenAI-compatible 服务是否接受 OpenAI 的全部字段，仍要以该服务协议为准。

因此 `BasicKoogOptionMapper` 会对 stop/reasoning effort 返回
`KOOG_UNSUPPORTED_OPTION`。这表示通用层没有承诺支持，不是丢字段。

当前 JVM/Desktop OpenAI route 中，`o3-mini` 的模型 metadata 与 mapper 都只声明
`low`/`medium`/`high`；Koog 枚举中存在 `NONE`/`MINIMAL` 不代表该精确模型 route 已支持这些值。

## 第一步：选择一个具体 Provider 和 Koog client

先只选择一个 Provider，不要同时实现多个分支。把对应 Koog client 依赖显式加入使用它的模块，
然后阅读该版本的参数子类构造器和 client 请求转换代码。确认以下事实后再写 mapper：

1. 使用 Chat Completions 还是 Responses API；
2. stop 的数量、空值和模型限制；
3. Harness `ReasoningEffortId` 到 Provider 枚举的完整映射；
4. 模型不支持某个 effort 时由谁拒绝；
5. `maxTokens` 在该 API 中表示输出 token 还是别的限制。

无法证明的值必须抛 `LlmException`，不能回退到默认 effort 或删除 stop。

## 第二步：先写 Provider mapper 的纯测试

为新的 `KoogOptionMapper` 至少覆盖：

- `null` 字段保持 `null`，不主动覆盖 Provider 默认值；
- temperature/maxTokens 原值传递，Long 到 Int 溢出使用稳定错误码；
- stop 保序且不改写字符串；
- 每个已声明的 reasoning effort 精确映射；
- 未知 effort、Provider 不支持的 option 和非法组合显式失败；
- 传入错误的 `options.provider` 或 `LLModel` 时显式失败；
- 返回的是目标 Provider 的参数子类，而不是仅含自由字段的通用 `LLMParams`。

模块现有测试已经证明 `KoogOptionMapper` 会收到 Adapter 解析后的 `LLModel`，其返回值会原样进入
`Prompt.params`。

## 第三步：实现 Provider mapper

实现结构应保持 Provider 分支可见：

```kotlin
class ExampleProviderOptionMapper : KoogOptionMapper {
    override fun map(
        options: GenerateOptions,
        model: LLModel,
    ): LLMParams {
        // 1. 验证 options.provider、model.provider 和已选择的 client flavor。
        // 2. 对 maxTokens 做 Long -> Int 的有界转换。
        // 3. 精确映射该 Provider 支持的 stop/reasoning effort。
        // 4. 构造该 Provider 的 LLMParams 子类。
        // 5. 对任何未覆盖值抛稳定 LlmException。
        TODO("由具体 Provider 语义决定")
    }
}
```

不要在 mapper 中读取 API key、创建 HTTP client 或执行请求；这些属于 Provider Executor Factory。

## 第四步：安装 mapper

把实现显式传给 `KoogLlmPlugin`：

```kotlin
KoogLlmPlugin(
    executorFactory = executorFactory,
    routes = routes,
    providerSemantics =
        listOf(
            KoogProviderSemantics(
                provider = "example",
                optionMapper = ExampleProviderOptionMapper(),
            )
        ),
)
```

使用 settings/credential 构造器时也有 `providerSemantics` 参数。bundle 中未覆盖的组件仍使用保守默认
实现；默认 option mapper 是 `BasicKoogOptionMapper`，不会自动猜 Provider。

## 第五步：做一次边界集成测试

使用记录型 `PromptExecutor` 验证：

1. Harness route 解析出的模型就是 mapper 收到的模型；
2. `Prompt.params` 是期望的 Provider 参数子类；
3. 请求只执行一次；
4. unsupported option 在调用 executor 之前失败；
5. 错误经过 `LlmRuntime` 后成为唯一 error finish，且稳定 code 未丢失。

这里仍使用测试 executor，不需要真实 API key。真实网络验收留到 Provider Factory 完成后。

## 验收

```bash
./kotlin check -m llm-koog
./kotlin build
```

只有 Provider 参数类、错误契约和 Plugin 注入测试全部通过后，才能把该 Provider 的 stop 或
reasoning effort 标为支持。通用 `BasicKoogOptionMapper` 仍应拒绝这些字段。
