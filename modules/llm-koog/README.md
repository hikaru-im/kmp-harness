# LLM Koog transport

本模块把 Harness `GenerateOptions` / `StreamChunk` 转换为 Koog 1.1.1 的请求与流，不引入 Koog Agent Runtime。
生产厂商的发现、能力和 adapter 创建统一使用 `LlmProviderContribution` 注册表；原 `KoogLlmPlugin` 安装入口已从生产源码移除，仅留在 `test/.../fixtures` 供映射和生命周期回归测试使用。

## 边界

- `KoogLlmAdapter` 是厂商插件内部可复用的传输适配器。option、message、tool、reasoning、usage、finish、replay、failure 各有独立映射接口。
- `KoogProviderRoute.api` 选择协议语义，`vendor` 选择真实厂商语义；连接实例的 `id` 可任意命名。不能从 base URL 或密钥猜厂商。
- `KoogOptionMapper.resolveModel` 与请求编码使用相同规则。统一 `reasoning` 与旧 `reasoningEffort` 都可传入；显式 `ReasoningOptions()` 保留服务默认，不重新填入旧默认档位。
- 默认 mapper 只接受已实现的通用参数，未实现的思考、工具或 replay 字段会显式失败。
- `KoogLlmReloadCoordinator` 必须接收 connection factory；生产入口的回调只调用 `LlmRuntime.createProviderAdapter`。每个连接有独立 generation，完整候选构造成功后才替换；失败保留之前的连接，prepared call 与在途流持有旧 generation 直到释放。

## 配置与目录

`KoogProviderSettings` 保留历史 `llm-koog` 设置格式，新增 vendor、serviceVariant、region、workspace、apiVersion。持久化只保存 credential 引用。

`models` 表示所选模型；OpenAI 旧配置省略它时仍迁移为 bundled catalog。其他厂商不能继承 OpenAI 目录，未选择模型时保持未激活。
`modelOverrides` 仅修改 bundled catalog 已知条目，不能与非空 `models` 同时设置。

发现结果保存在 `discoveredCatalog`，并用 `discoveredCatalogConnection` 绑定连接身份。切换 endpoint、vendor、协议、地区、workspace 或 credential 引用后，旧缓存不参与模型解析。
发现模型消失不自动删除用户选择。远端事实提供名称、输入模态、上下文与输出容量，但并不证明当前适配器能执行所有操作。

`KoogModelProfile.maxOutputTokens` 只表示输出容量。旧 `maxTokens` 保持显式请求默认的兼容语义，不能把远端最大输出额度写进它。
缺失的上下文或输出容量保持未知，不填入虚构的全局常量。

## 源码

```text
adapter/     Harness adapter 和请求/流组合
catalog/     连接、所选模型和绑定后的发现事实
config/      持久化配置与严格 JSON codec
lifecycle/   原子替换、回滚、generation lease
semantics/   request / stream / replay 映射
error/       稳定错误与异常分类
```

协议私有字段应在厂商模块实现。保留 reasoning 文本与工具 call ID 不等于支持任意私有 replay 数据；带签名、summary 或 encrypted reasoning 的协议必须提供自己的 codec。
