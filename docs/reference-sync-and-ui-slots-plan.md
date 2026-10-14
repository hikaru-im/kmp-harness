# DSH、Koog 与 UI Slots 同步规划

状态：Phase 1 与最小 Phase 3/4 已实施，后续阶段待实施  
评估日期：2026-10-05

本文记录一次只读评估后的同步计划。目标是继续参考 DSH 做 KMP Agent，不把 DSH 的
TypeScript/React 运行时直接搬进 Kotlin Multiplatform。

## 一、当前基线

| 项目 | 当前基线 | 远端或目标基线 | 结论 |
| --- | --- | --- | --- |
| DSH 参考仓库 | `0a53fb55`，2026-08-30，`dsh-v0.1.2-alpha.2` | `master`：`5badb150`，2026-10-03，`dsh-v0.2.1-alpha.1` | 先建立干净只读对比目录，不改现有脏工作树 |
| Koog | `1.3.0`，由 `apps/jvm-app/module.yaml` 使用 | `1.3.0` | 四个 Koog 包已一起升级并通过门禁 |
| KMP UI Slot | 已有冷启动 `UiSnapshot` 方案 | 对齐 DSH 的类型、生命周期和 Slot 目录概念 | 保留冷启动模型，吸收协议语义 |

`references/deepseek-harness` 当前有大量未提交文件，包含 UI、Session、snapshot、docs 和
vendor 等目录。本轮不在该工作树上执行 `pull`、`reset` 或覆盖操作。

## 二、值得同步的 DSH 内容

### 2.1 优先同步：契约和架构思想

1. **Slot 类型代数**：`single`、`list`、`keyed`，以及需要默认实现和接管语义时使用的
   `chain`。
2. **Slot 所有权**：Slot 由宿主组件声明和渲染，插件只注册贡献，不直接操作 Shell 根节点。
3. **编译期契约**：DSH 使用 `SlotMap`、`SlotEntryDef` 和类型合并约束组件输入；KMP 使用
   `UiSlotKey<P>` 与 `UiSchema` 达到同样的类型安全。
4. **贡献排序和替换规则**：`single/keyed` 是有意的替换点，`list` 用稳定 `id` 做追加，
   相同优先级必须可诊断。
5. **生命周期边界**：DSH 的 `ctx.slots.inject()` 会跟随父 Slot 声明的生命周期；KMP 第一版
   将其等价语义压缩为“启动期注册、freeze、只读 Snapshot”，以后再引入动态 owner 生命周期。
6. **生成式 Slot Catalog**：把 Slot 的名称、类型、作用域、Owner Props、当前实现和替换风险
   作为可检查产物，避免 UI 插件靠字符串猜测宿主结构。

### 2.2 第二阶段同步：Agent UI 所需的领域模型

- Session Header、Composer Actions、Sidebar Actions、Settings Tabs。
- Tool Call 的 preparing/start/result 生命周期和可恢复的投影数据。
- Agent/Session/Model Catalog 的稳定标识和展示元数据。
- 插件来源、版本、兼容性和禁用状态，供 Desktop Client Catalog 使用。

这些内容要先落到 KMP 的 typed contracts，再接 Compose Slot；不直接复制 DSH 的 React 组件或
浏览器状态管理。

### 2.3 暂不同步

- Web React renderer、`useSyncExternalStore`、React Context 和 per-entry error boundary。
- 动态 JavaScript 插件、HMR、Web Worker、运行时下载和 ClassLoader 式加载。
- DSH Inspector、分析上报和 Web 专用文件预览。
- 没有对应 KMP Session/Agent contract 的 DSH UI 细节。

## 三、Koog 升级评估

Koog `1.1.1` 到 `1.3.0` 之间已有几项直接影响当前实现的修复：

- `KG-898`：OpenAI Responses 的 `response.instructions` 同时接受单字符串和数组，解决当前
  `apps/jvm-app/README.md` 记录的反序列化问题。
- `KG-866`：OpenAI/DeepSeek 流式 `reasoning_content` 进入 Koog reasoning frame，并支持后续
  请求回放。
- `1.2.0` 增加 Skills 支持；如果以后把 DSH 的技能目录接入 Agent，这项可以单独评估。
- `1.3.0` 包含模型目录和部分 Provider metadata 更新，但不应因此改变 Harness 的
  Provider-neutral contract。

### 建议版本

先锁定 `1.3.0`，不要直接追随未发布的 `main`。`apps/jvm-app/module.yaml` 中以下依赖必须
保持同版本并一次性升级：

```text
ai.koog:prompt-executor-model
ai.koog:prompt-executor-openai-client
ai.koog:http-client-core
ai.koog:http-client-ktor
```

### 升级门禁

1. 先只改版本号，编译 `llm-koog` 和 `llm-koog-openai`。
2. 复跑现有请求映射、消息映射、tool schema、tool stream、reasoning、usage、finish reason、
   failure 和 replay 契约。
3. 用现有 scripted HTTP fixture 验证 Chat Completions 和 Responses 的请求 JSON、SSE、tool
   call/result、取消、early-stop、client close。
4. 新增或恢复 `response.instructions` 字符串 fixture，并验证 `reasoning_content` 的回放。
5. 最后再运行可选 live acceptance；live 失败不能用旁路解析掩盖 Koog API 行为。

升级失败时只回退四个依赖版本，保留已经通过的 Harness 适配层改动。

## 四、IntelliJ Extension Point 与 DSH Slot 的映射

| IntelliJ 思路 | DSH 形式 | KMP 形式 |
| --- | --- | --- |
| `ExtensionPointName<T>` | `SlotMap` 中的类型化 Slot | `UiSlotKey<P>` / `UiSchema` |
| Extension 实例 | `ctx.slots.register()` 的 Entry | `UiContribution<P>` |
| `extensionList` 缓存 | Host Renderer 的 Entry Snapshot | `UiSnapshot` |
| `ToolWindowFactory` | Root/Panel 类 Slot | Root、Sidebar、Panel Slot |
| `StatusBarWidgetFactory` | Header/Toolbar list Slot | Session Header、Composer Action Slot |
| `Configurable` | Settings Tab Slot | Settings Tabs Slot |
| 宿主 Extension Point 所有者 | 父 Entry 的 `children` 声明 | 静态 Schema 的 owner/children 约束 |

采用的共同原则是：宿主定义扩展点，扩展实现只提供能力，宿主决定展示位置和输入；扩展实例在
启动装配后可缓存，配置变化通过重启或新 Snapshot 生效。

## 五、KMP UI Slot 目标设计

### 5.1 第一版必须具备的语义

- 编译期已知的 Slot Key 和 Props 类型。
- `single`、`keyed`、`list` 三种基础 Slot；`chain` 延后到 Composer 需要接管/回退时再加入。
- 明确的 `order`、`id` 和重复冲突诊断。
- Client Runtime 与 Host Runtime 分离。
- UI Plugin 在启动阶段向 `UiContributionSink` 注册。
- `freeze()` 后生成不可变 `UiSnapshot`，Compose 只读 Snapshot。
- Plugin 失败时由 Client Loader 回滚，不影响 Host Runtime。

### 5.2 第一批真实 Slot

| Slot | 类型 | 作用域 | 用途 |
| --- | --- | --- | --- |
| `root` | single | root | 默认 Harness Shell |
| `conversation.header` | single | session-maybe | Session 标题、状态和扩展操作 |
| `tool.call.view` | keyed | session | 按 tool 名称或 tool kind 提供展示器 |
| `sidebar.actions` | list | root | 插件入口、Workspace 或 Agent 操作 |
| `settings.tabs` | list | root | Provider、Plugin 和凭据设置页面 |
| `composer.actions` | list | session | 附件、模式、Agent 操作 |

第一条可验收链路只实现 `root`、`conversation.header` 和 `sidebar.actions`，先证明插件装配、
Snapshot 和 Compose 接线，再实现 Tool 与 Composer 的复杂作用域。

### 5.3 与 DSH 的有意差异

KMP 第一版不实现 DSH 的动态 `slots.inject()`、运行时 declaration epoch、热卸载和 React
standard hooks。静态 Schema 仍然要保留 owner、children、scope 和 cardinality 字段，保证以后
能够增加动态模型时不需要推翻 Slot Key。

## 六、实施顺序

### Phase 0：建立可重复的参考快照

- 为 DSH 建立干净临时 worktree 或只读 clone。
- 保存 `0a53fb55` 与 `5badb150` 的差异清单。
- 只提取 Slot、Plugin Catalog、Session/Agent contract、Tool projection 和 Model Catalog。

已完成：对照结果见 [dsh-slot-contract-comparison.md](dsh-slot-contract-comparison.md)。

### Phase 1：Koog 1.3.0 升级

- 只改四个 Koog 版本。
- 通过现有契约和 scripted HTTP 门禁。
- 更新 Koog 约束文档，移除已验证的 `response.instructions` 阻塞说明。

已完成：四个依赖均为 1.3.0；Responses 字符串 `instructions` 和 Chat `reasoning_content` scripted
fixture 已通过，`llm-koog`、`llm-koog-openai`、`jvm-app` JVM 门禁通过。

### Phase 2：DSH 对齐清单

- 把 DSH 的 Slot、Plugin metadata、Session/Tool projection 与 KMP contract 做字段级对照。
- 标记“直接采用、改写后采用、暂不采用”。
- 不把 DSH 参考仓库源码复制到生产模块。

### Phase 3：`modules/ui-slot` 核心

- 实现 `UiSlotKey`、`UiSchema`、`UiContributionSink`、`UiSnapshot` 和 `SlotOutlet`。
- 完成 Single、Keyed、List 的排序、冲突、冻结和不可变性测试。

已完成：新增 `modules/ui-slot`，并通过 6 个 JVM 契约测试。

### Phase 4：Client Runtime 与 Desktop Catalog

- 新增 Client Runtime、Client Plugin Catalog 和 `HarnessApp` 装配。
- 验证 Host/Client 独立启动、失败回滚和独立关闭。
- 接入 Root、Header、Sidebar 三个实际 Slot。

已完成最小纵切：`ClientHarnessHost` 复用 Runtime、Loader、EffectScope 生命周期，Desktop Catalog
安装 root/header/sidebar 三个贡献，`HarnessApp` 通过 Compose Snapshot 接线；apps/shared 集成测试通过。

### Phase 5：Session/Tool/Settings 扩展

- 增加 session-maybe/session 作用域。
- 增加 Tool Call keyed renderer、Settings Tabs 和 Composer Actions。
- 只有出现需要默认实现被插件接管的场景时，才加入 Chain Slot。

## 七、验收命令与交付物

Koog 升级阶段：

```bash
./kotlin check -m llm-koog
./kotlin check -m llm-koog-openai
./kotlin test -m jvm-app
```

UI Slot 阶段：

```bash
./kotlin test --include-module=ui-slot --platform=jvm
./kotlin test --include-module=shared --platform=jvm
./kotlin test --include-module=jvm-app --platform=jvm
```

交付物按顺序为：Koog 升级变更、DSH 字段对照表、UI Slot 核心模块、Desktop Client Catalog、
三条真实 UI Slot 链路。每一阶段都保留可回退的独立提交。
