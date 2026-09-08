# Desktop App

`jvm-app` 同时装配 Desktop Client 和本地 Harness Host，是本地 Agent 的进程边界。

```text
Compose Desktop UI
    -> LocalConnection
    -> modules/api/gateway
    -> Runtime / Loader
    -> Agent Plugins
```

`LocalConnection` 直接调用同进程 API Gateway，并保持与远程连接相同的类型化 API、contracts 和
`ApiResult` 语义。它不构造 RPC 信封，也不执行 JSON 编解码。它是 Desktop 平台实现，不放入
`apps/shared`。

远程访问 Desktop Agent 时，规划中的 `HostRelayConnection` 由 Desktop 主动连接 RuoYi 后端并
接收转发请求；Desktop 不启动面向公网的 HTTP/WebSocket Server，也不要求开放入站端口。

当前应用通过 `modules/bundle/desktop` 的 `startDesktopProfile()` 启动 DSH-compatible Profile、HarnessHost、
LocalConnection 和首屏 `host.describe`。窗口关闭时先停止 Profile watcher，再释放 Loader 和 Runtime 根
Context。生产源码不注册 Provider，也不导入 Koog/OpenAI 类型；该边界由
`DesktopProductionBoundaryTest` 锁定。

Desktop Harness home 的优先级是显式 `--home`、`HARNESS_HOME`、`~/.harness`。一个平台只使用一个
配置目录，不再增加命名子目录或额外的配置选择项。启动器支持：

```bash
./kotlin run -m jvm-app -- --home /path/to/harness-home \
  --patch /path/to/temporary.patch.yml
```

可重复的 `--patch` 按参数顺序应用。最终组合顺序是 bundle、home patch、启动覆盖和
launcher-derived patch。`patchReload: live` 自动刷新，任何非法更新都保留最后成功快照。

Desktop 启动时自动创建 `package.json`、`cordis.yml`、`cordis.patch.yml` 和 owner-only 的空
`settings.yaml`。`.credentials.yaml` 不会因为启动而出现，只在第一次写入凭据时以 owner-only 权限创建。
这些文件都直接位于同一个 Harness home；插件配置不能覆盖平台选择的 home。

`modules/llm-koog-openai` 是完整的 OpenAI Provider Plugin。它静态安装
`openai-chat-completions`（Chat Completions）和 `openai-responses`（Responses API）两个协议模板，并在
Provider Directory 中只公开 `openai`。活动 Provider route 完全由 `settings.yaml` 创建；空配置时插件休眠，
不会解析凭据或创建 executor。每个模型的 `api` 选择协议，两种协议共享同一 profile 的 API key/base URL，
但各自拥有独立参数、流和 replay 语义。Profile 激活时才从 credentials service 解析凭据引用。
Profile patch 不接受密钥字段；密钥只放在 `.credentials.yaml`。`o3-mini` 只公开并接受
`low`/`medium`/`high` reasoning effort；OpenAI
option/usage/finish/tool-terminal/failure semantics、factory 生命周期和 delta-only tool-call terminal 已有
JVM fixture。`OpenAiClientFixtureTest` 进一步用 scripted HTTP seam 驱动真实 Koog `OpenAILLMClient`，覆盖
请求 JSON、文本/tool SSE、取消/early-stop、executor/client close、密钥边界和带 `/v1` API 根地址的规范化；
外部网络 tool、HTTP engine teardown 及 replay 仍未验收。

选择 Responses API 的模型使用 Koog 原生 `OpenAIResponsesParams`，请求 `v1/responses`，固定 `store:false`，并已由
离线 fixture 验证文本/函数工具 SSE、`response.completed` terminal、取消和 client close。Responses 与 Chat
Completions 的选项和 finish 语义不混用；真实网络 replay 仍未完成。

外部 live 验收使用生产 `startDesktopProfile()` 和隔离的临时 Harness home。测试把 settings 与测试凭据写入
临时 `settings.yaml` / owner-only `.credentials.yaml`，确认凭据来源为 `file`、Directory/route/model 已激活，
关闭 Host 后扫描非凭据产物并删除整个临时目录。默认只运行 Chat 文本；必须显式提供环境变量：

```bash
HARNESS_OPENAI_LIVE_API_KEY='<temporary-key>' \
HARNESS_OPENAI_LIVE_BASE_URL='https://provider.example/v1' \
HARNESS_OPENAI_LIVE_MODEL='<model-from-provider-catalog>' \
./kotlin test -m jvm-app \
  --include-classes=im.hikaru.harness.llm.koog.openai.integration.OpenAiLiveAcceptanceTest
```

工具闭环和 Responses 各自使用独立开关；只有明确启用时才会发送对应请求：

```bash
HARNESS_OPENAI_LIVE_TOOLS=true                 # Chat tool call -> result -> final text
HARNESS_OPENAI_LIVE_RESPONSES=true             # Responses text，上游修复前预计失败
HARNESS_OPENAI_LIVE_RESPONSES_MODEL='<model>'  # 省略时复用 Chat model
```

测试接受带或不带 `/v1` 的 API 根地址，Koog client 创建前会统一移除末尾 `/v1`，再由 Koog 添加具体路径。
endpoint、模型和密钥只进入临时测试目录，不会写入产品 Harness home。未提供 key 时 3 个用例全部由 JUnit
跳过，不会触网或产生费用。生产 file-backed harness 已使用 Responses-only 的兼容端点和 `gpt-5.6-sol`
实际执行：raw Responses 返回 HTTP 200，Harness 也完成 file credential、Directory/route/model 激活并到达
HTTP 200，随后 Koog 1.1.1 因 JetBrains/koog#2211 无法反序列化合法的 string `response.instructions`。
该连接的 Chat Completions 对所有探测模型均返回分组无可用渠道，因此 Chat/tool 未完成外部验收。Responses
等待正式上游修复，不在 Desktop 旁路解析 SSE。

Desktop bundle 显式安装 `settings-file` 和 `credentials-local`，读取 Harness home 下的 `settings.yaml`
与 `.credentials.yaml`。模型声明来自 `settings.yaml`；下面只创建一个对外 `openai` Provider，模型级
`api` 决定走 Chat Completions 还是 Responses：

```yaml
llm-koog:
  providers:
    openai:
      displayName: OpenAI
      api: openai-chat-completions
      baseUrl: https://provider.example/v1
      credential:
        name: OPENAI_API_KEY
      models:
        - id: provider-chat-model-id
          api: openai-chat-completions
          name: Provider Chat Model
          contextWindow: 65536
          maxTokens: 4096
          input: [text]
        - id: provider-responses-model-id
          api: openai-responses
          name: Provider Responses Model
          input: [text]
```

对应 `.credentials.yaml` 仍是 DSH-compatible 的平面引用表：

```yaml
OPENAI_API_KEY: replace-with-your-key
```

`modelOverrides` 可只修改一个安装模型并保留其余目录，但不能与非空 `models` 同时使用。有效文件更新会
原子替换模型目录和客户端 generation；在途请求继续使用旧 generation，非法更新保留最近一次成功配置。
Provider route 可由 YAML 增删，Chat Completions/Responses 协议实现仍由 `OpenAiKoogPlugin` 安装；YAML
只能引用已安装的 `api`，不能凭空新增协议实现。新增协议族应增加独立 Provider Plugin，并由 Desktop
bundle 的编译期 Catalog 暴露。完整字段和继承规则见 `modules/llm-koog/README.md`。

HostRelayConnection、Relay event、Repository、ViewModel 和 Agent UI 尚未实现。
