# UI Slot 与现有插件框架结合方案

状态：Proposed

本文规划 KMP Harness 的客户端 UI Slot。目标不是移植 DSH 的动态浏览器插件系统，也不是实现
运行时热重载，而是参考 IntelliJ Platform 的 Extension Point 模型，在现有 KMP Plugin、Loader
和 Compose 客户端之上建立一套冷启动、编译期、类型安全的 UI 扩展机制。

## 一、结论

第一版不需要改造 `modules/runtime` 的核心语义。`Plugin`、`Context`、`Fiber`、`EffectScope`、
`Registry`、`Loader`、`PluginCatalog` 和 Profile 组合模型都可以继续使用。

需要新增的主要是：

1. 一个纯启动期 UI 扩展模块 `modules/ui-slot`。
2. 一个与 Host Runtime 分离的 Client Runtime。
3. 一个只包含已编译 UI Plugin 的 Desktop Client Catalog。
4. `apps/jvm-app` 中同时启动 Host Runtime 和 Client Runtime 的装配代码。

改造量结论：

| 范围 | 改造量 |
| --- | --- |
| Runtime、Context、Fiber、EffectScope | 无 |
| Registry、Loader、PluginCatalog | 无 |
| Host Profile、Session、Agent、LLM、Tools | 无 |
| UI Slot 核心 | 新增模块 |
| Client Runtime 与 HarnessApp | 新增客户端装配层 |
| Desktop UI Catalog | 新增编译期 Catalog 和 UI 插件 |
| `jvm-app` 入口 | 小型接线改造 |
| 独立 Client Profile 文件 | 可选，小型文件层扩展 |

第一版明确不做：

- 客户端 UI 热重载。
- 任意 JavaScript、ClassLoader、Dex 或动态模块加载。
- 运行时 Slot 声明、等待注入和撤销。
- 模型临时生成 UI。
- React 风格的 per-entry error boundary。

UI 配置变化通过冷启动生效。用户修改 Client Profile 后重启应用，Client Runtime、UI Plugin 和
Slot Snapshot 全部重新构建。

## 二、为什么采用冷启动模型

DSH 的 Slot 需要处理动态插件和浏览器页面重载，因此引入了 declaration epoch、`slots.inject`、
注册撤销和运行时重新挂载。这些语义的必要性来自运行时代码变化。

KMP 当前已经明确使用编译期 `PluginCatalog`，不支持动态下载和动态代码加载。客户端 UI Plugin
可以全部编译进应用。因此可以采用更接近 IntelliJ Extension Point 的模型：

```text
静态 UiSchema
    -> 启动 Client Runtime
    -> 安装 UI Plugin
    -> 收集 UI contributions
    -> freeze 为只读 UiSnapshot
    -> Compose 挂载
```

IntelliJ 的 `ExtensionPointName<T>` 提供类型化扩展点，`extensionList` 在扩展实例创建后缓存。
UI 功能通过 `ToolWindowFactory`、`StatusBarWidgetFactory`、`Configurable` 等专用 Extension Point
接入。KMP UI Slot 复用同样的启动期扩展点思想，但不复制 IntelliJ 的完整插件隔离和动态 Plugin API。

参考：

- [IntelliJ Platform Extension Points](https://plugins.jetbrains.com/docs/intellij/plugin-extension-points.html)
- [ExtensionPointName.kt](https://github.com/JetBrains/intellij-community/blob/master/platform/extensions/src/com/intellij/openapi/extensions/ExtensionPointName.kt)

## 三、现有框架可复用能力

| 现有能力 | UI Slot 方案中的角色 | 第一版改造 |
| --- | --- | --- |
| `Plugin<C>` | 编译期 UI Plugin 定义 | 无 |
| `Plugin.apply(context, config, scope)` | 启动时向客户端贡献 UI | 无 |
| `Context.provide/get/require` | 提供 Connection、UI Contribution Sink 和客户端服务 | 无 |
| `EffectScope` | UI Plugin 其他资源的生命周期 | 无 |
| `ServiceKey` | `UiContributionKey`、`ConnectionKey` | 无 |
| `Registry` | 按名称解析已编译 Client Plugin | 无 |
| `Loader` | 冷启动时协调 Client Plugin Entries | 无 |
| `PluginCatalog` | Desktop Client UI Catalog | 无 |
| `Entry`、`EntryPatch` | Client UI 插件选择和禁用 | 无 |
| `ProfileBundle`、`ProfileBundleCatalog` | 客户端编译期 bundle 组合 | 无 |
| `composeProfile` | 组合 Client Profile layers | 无 |
| `HarnessHost` | Host 运行时装配 | 仅作为 Client Host 的实现参考 |

核心判断是：UI Slot 不需要进入 Runtime 内核。它是 `modules/runtime` 之上的一层客户端贡献模型，
与 LLM、Session、Agent 一样都是 Runtime 的使用者。

## 四、总体架构

```text
Host Runtime
    Session / Agent / Tools / LLM
                |
                | Connection
                v
Client Runtime
    ConnectionKey
    UiContributionKey
    Registry + Loader
        |
        +-- client UI shell plugins
        +-- client feature UI plugins
        |
        v
    freeze -> UiSnapshot
                |
                v
        HarnessApp
            SlotOutlet(root, props)
```

Host 和 Client 使用不同的 `Runtime`、`Registry` 和 `Loader`。这样可以保证：

- UI Plugin 不需要看到 Session、LLM 和 Agent 的 Host 依赖。
- Host Plugin 的失败不会直接破坏客户端 Compose 树。
- 客户端 Profile 可以独立选择 UI 插件。
- Client Runtime 关闭时只释放客户端资源。

Desktop 第一版可以让两个 Runtime 运行在同一个进程中。移动端未来连接远程 Host，仍使用同一个
Client Runtime 和同一套 Slot 模型。

## 五、核心数据模型

### 1. 类型化 Slot Key

不把 Slot 退化成无类型的 `String`。每个 Slot 使用一个静态 Key 对象，泛型参数固定它的 Props
类型。

```kotlin
public enum class UiSlotKind {
    Single,
    Keyed,
    List,
}

public sealed interface UiSlotKey<out P : Any> {
    public val name: String
    public val kind: UiSlotKind
}

public class SingleUiSlotKey<P : Any>(
    override val name: String,
) : UiSlotKey<P> {
    override val kind: UiSlotKind = UiSlotKind.Single
}

public class KeyedUiSlotKey<K : Any, P : Any>(
    override val name: String,
) : UiSlotKey<P> {
    override val kind: UiSlotKind = UiSlotKind.Keyed
}

public class ListUiSlotKey<P : Any>(
    override val name: String,
) : UiSlotKey<P> {
    override val kind: UiSlotKind = UiSlotKind.List
}
```

Key 对象身份是运行时身份，`name` 用于诊断、Schema 冲突检测和未来的配置文件表达。两个不同 Key
对象不能注册同一个 `name`。

第一版只支持：

- `SingleUiSlotKey`：标题区、状态区、根视图、整个对话区。
- `KeyedUiSlotKey`：按 Tool 名称、页面 ID、Widget ID 分派。
- `ListUiSlotKey`：工具栏动作、侧栏按钮、设置项。

`chain` 延后。当前没有必须由多个插件通过 selector 竞争接管的区域。

### 2. 静态 UiSchema

所有 Slot 在 Client Runtime 启动前静态声明，不依赖某个 UI Plugin 是否已经安装。

```kotlin
public object ClientUiSlots {
    public val Root: SingleUiSlotKey<RootProps> =
        SingleUiSlotKey("root")

    public val SessionHeader: SingleUiSlotKey<SessionHeaderProps> =
        SingleUiSlotKey("conversation.header")

    public val ToolCallView: KeyedUiSlotKey<String, ToolCallProps> =
        KeyedUiSlotKey("tool.call.view")

    public val SidebarActions: ListUiSlotKey<SidebarActionProps> =
        ListUiSlotKey("sidebar.actions")
}

public val ClientUiSchema: List<UiSlotKey<*>> =
    listOf(
        ClientUiSlots.Root,
        ClientUiSlots.SessionHeader,
        ClientUiSlots.ToolCallView,
        ClientUiSlots.SidebarActions,
    )
```

静态 Schema 解决三个问题：

- Plugin 安装顺序不再决定 Slot 能否注册。
- 不需要 `slots.inject()` 和 declaration epoch。
- Slot Key 的拼写和 Props 类型在编译期检查。

没有 UI Plugin 渲染某个 Slot 时，该 Slot 的贡献只是不可见，不作为启动错误。

### 3. 启动期 Contribution Sink

UI Plugin 只在 Client Runtime 启动阶段获得可写 Sink。

```kotlin
public interface UiContributionSink {
    public fun <P : Any> contribute(
        key: SingleUiSlotKey<P>,
        order: Int = 0,
        content: @Composable (P) -> Unit,
    )

    public fun <K : Any, P : Any> contribute(
        key: KeyedUiSlotKey<K, P>,
        cell: K,
        order: Int = 0,
        content: @Composable (K, P) -> Unit,
    )

    public fun <P : Any> contribute(
        key: ListUiSlotKey<P>,
        id: String,
        order: Int = 0,
        content: @Composable (P) -> Unit,
    )
}
```

`UiContributionKey : ServiceKey<UiContributionSink>` 由 Client Runtime 在安装任何 UI Plugin 前
提供。

Sink 只允许在启动阶段写入。完成 `Loader.reconcile()` 后调用 `freeze()`，后续任何
`contribute()` 都抛出异常。Sink 只由 Client Runtime 启动事务串行调用，不提供并发写入协议。

### 4. 只读 UiSnapshot

启动完成后，Sink 转成不可变 Snapshot。

```kotlin
public class UiSnapshot internal constructor(
    private val entries: Map<UiSlotKeyId, List<UiContribution<*>>>,
) {
    public fun <P : Any> single(
        key: SingleUiSlotKey<P>,
    ): UiContribution<P>?

    public fun <K : Any, P : Any> keyed(
        key: KeyedUiSlotKey<K, P>,
        cell: K,
    ): UiContribution<K, P>?

    public fun <P : Any> list(
        key: ListUiSlotKey<P>,
    ): List<UiContribution<P>>
}
```

Snapshot 不提供 runtime mutation API，不产生注册事件，也不需要 `StateFlow`。Compose 只读取
Snapshot。UI 内容需要动态数据时，由 Plugin 自己订阅 `StateFlow` 或使用 Compose state。

## 六、与现有 Plugin 框架的结合

### 1. Plugin 仍然使用现有定义

UI Plugin 继续使用 `Plugin<C>`、`SimplePlugin`、`PluginDefinition` 和 `ConfigAdapter`。

```kotlin
public val SessionHeaderPlugin: SimplePlugin =
    simplePlugin {
        inject(ConnectionKey)
        inject(UiContributionKey)

        apply { context, _ ->
            val connection =
                context.require(ConnectionKey)
            val contributions =
                context.require(UiContributionKey)

            contributions.contribute(
                key = ClientUiSlots.SessionHeader,
                order = 100,
            ) { props ->
                SessionHeaderContent(
                    props = props,
                    connection = connection,
                )
            }
        }
    }
```

UI Plugin 不直接操作 Compose 根，也不导入 Shell 内部组件。它只向已经声明的 Extension Point
贡献内容。

### 2. Client Runtime 启动顺序

`ClientHarnessHost.start()` 采用以下事务：

```text
create Runtime
    -> create Registry
    -> create Loader
    -> provide ConnectionKey
    -> create UiContributionBuilder(ClientUiSchema)
    -> provide UiContributionKey
    -> register ClientPluginCatalog
    -> Loader.reconcile(clientEntries)
    -> require every enabled Client Entry to have an Active Fiber
    -> builder.freeze()
    -> return ClientHarnessHost(snapshot)
```

任意步骤失败：

```text
dispose Loader
    -> dispose root Context
    -> discard UiContributionBuilder
    -> surface ColdStartUiFailure
```

Pending Fiber 表示 Client Plugin 的必需依赖还没有满足。第一版把它视为冷启动失败，不冻结
`UiSnapshot`，也不启动部分可用的 Compose 应用。

这个流程和现有 `HarnessHost.start()` 保持同一事务风格，但返回的是 `UiSnapshot` 而不是 Host API
Gateway。

### 3. 为什么不需要 Slot 也变成 Service

把每个 Slot 包装成 `ServiceKey` 会让服务命名空间膨胀，也会让 Slot 的存在状态伪装成业务能力。
本方案使用一个 `UiContributionKey` 服务提供启动期 Sink，Slot 本身由静态 Schema 表达。

### 4. Disposable 和 EffectScope 的边界

Slot contribution 不需要运行时撤销，因此 `contribute()` 不返回 `Disposable`。

`EffectScope` 仍用于 UI Plugin 的其他资源，例如：

- 订阅 Connection 或 Settings。
- 启动 Plugin 私有的后台流。
- 持有需要随 Client Runtime 停止而释放的资源。

Client Runtime 启动失败时，Loader 和根 Context 负责按现有事务边界释放这些资源。

## 七、渲染语义

### 1. Single Slot

同一个 Key 可以有多个 contribution，但只有一个可见。

排序规则：

1. `order` 升序。
2. 相同 `order` 按 Client Plugin Catalog 顺序。
3. 如果两个 Contribution 在同一 Cell 使用相同 `order`，启动失败。

内置 Shell 使用 `order = 0`。显式覆盖使用负数。

### 2. Keyed Slot

`cell` 相同的 Contribution 进入同一个 Cell。每个 Cell 的 winner 使用和 Single Slot 相同的
规则。

```text
ToolCallView["bash"]
    -> BashToolCardContribution

ToolCallView["edit"]
    -> EditToolCardContribution
```

未注册的 Cell 使用 Owner 提供的 fallback。

### 3. List Slot

List Slot 保留全部 Contribution，按 `order` 升序排列。相同 `order` 保持 Catalog 顺序。

List Slot 的 `id` 必须唯一。重复 `id` 启动失败。

### 4. Fallback

`SlotOutlet` 必须接受 fallback：

```kotlin
SlotOutlet(
    key = ClientUiSlots.SessionHeader,
    props = headerProps,
) {
    DefaultSessionHeader(headerProps)
}
```

没有 Contribution，或 Owner 选择忽略某个 Slot 时，可以渲染 fallback。

## 八、Compose 渲染层

### 1. CompositionLocal

Client Runtime 启动后，将 `UiSnapshot` 放入 CompositionLocal：

```kotlin
CompositionLocalProvider(
    LocalUiSnapshot provides clientHost.snapshot,
) {
    HarnessApp(connection)
}
```

Slot 读取不持有 Runtime 对象，也不通过全局单例访问 Service。

### 2. SlotOutlet

```kotlin
@Composable
public fun <P : Any> SlotOutlet(
    key: SingleUiSlotKey<P>,
    props: P,
    fallback: @Composable () -> Unit = {},
) {
    val contribution =
        LocalUiSnapshot.current.single(key)

    if (contribution == null) {
        fallback()
    } else {
        contribution.content(props)
    }
}
```

Keyed 和 List 版本分别按 Cell 和有序列表渲染。Compose 侧只依赖 `UiSnapshot`，不依赖
`Loader`、`Runtime` 或 Plugin 类型。

### 3. Owner Props

Owner 决定 Slot 能拿到的业务输入。第一版只支持显式 Owner Props，不提供全局 Standard Props。

例如：

```kotlin
public data class SessionHeaderProps(
    val title: String,
    val workspaceName: String?,
    val modifier: Modifier,
)
```

需要跨 Slot 共享的数据由根 Shell 或业务 Plugin 通过 Props 传递。未来如果出现稳定的 Session、
Theme、Settings 标准面，再增加 Standard Props。

### 4. Composition 失败

启动阶段 Plugin `apply()` 失败可以由 Client Runtime 捕获并回滚。

任意 Contribution 的 `@Composable` body 在 composition 阶段抛异常时，不承诺类似 React Error
Boundary 的局部恢复。第一版按 fail-fast 处理，由应用级错误页承接。

需要局部容错的 Plugin 应把自己的数据转换和可选分支封装在 content 内部，但不能假设框架能
安全恢复任意 composition 异常。

## 九、模块与改动范围

### 1. 新增 `modules/ui-slot`

建议的 KMP 模块依赖：

```text
modules/runtime
$compose.runtime
```

不依赖 Material、Foundation、Desktop、Android Activity 或具体业务模块。

主要内容：

```text
UiSlotKey.kt
UiContributionSink.kt
UiContributionBuilder.kt
UiSnapshot.kt
SlotOutlet.kt
UiContributionKey.kt
```

模块本身是 KMP library：

```yaml
product:
  type: kmp/lib
  platforms: [jvm, android, iosSimulatorArm64, iosArm64]
```

### 2. 新增 `modules/bundle/desktop-client`

职责：

- 声明 Desktop Client Plugin Catalog。
- 提供 Desktop Client Profile Bundle。
- 只引用 Desktop 客户端 UI Plugin。
- 不引用 Host Plugin。

该模块同样是 KMP library，但第一版的 Catalog 只在 Desktop App 中启用。

建议名称：

```text
@hikaru-ai/harness-desktop-client
```

### 3. 扩展 `apps/shared`

新增：

```text
ClientHarnessHost.kt
ClientPluginCatalog.kt
ConnectionKey.kt
HarnessApp.kt
```

`apps/shared` 只提供跨平台客户端装配能力。具体平台使用哪个 Client Catalog，由 `jvm-app`、
`android-app` 和 `ios-app` 注入。

### 4. 修改 `apps/jvm-app`

当前入口直接启动 Host Runtime，并在 Compose 中渲染 `HostScreen`。

目标入口：

```text
start Desktop Host
create LocalConnection
start Desktop Client Runtime(connection, clientCatalog, clientEntries)
render HarnessApp
close Client Runtime and Host on exit
```

现有 `HostScreen` 应迁移为 Root Slot 的默认 UI Plugin，而不是留在 `main.kt` 中。

### 5. 暂不修改的核心模块

| 模块 | 第一版动作 |
| --- | --- |
| `modules/runtime` | 不修改 |
| `modules/loader` | 不修改 |
| `modules/profile` | 不修改 |
| `modules/boot/HarnessHost` | 不修改 |
| `modules/session`、Agent、LLM、Tools | 不修改 |

`ClientHarnessHost` 与 `HarnessHost` 会有少量相似的启动和关闭代码。第一版优先保持两条生命周期
边界清楚，不先抽公共基类。等第二个客户端 Runtime 验证稳定后，再考虑提取通用 Boot helper。

## 十、Client Profile 的冷启动策略

第一版先使用代码级 Catalog 和 Entries：

```kotlin
val DesktopClientPluginCatalog =
    PluginCatalog(
        listOf(
            uiShellPluginDefinition,
            sessionHeaderPluginDefinition,
            sidebarActionsPluginDefinition,
        )
    )

val DesktopClientEntries =
    listOf(
        Entry("ui-shell", "ui-shell"),
        Entry("session-header", "session-header"),
        Entry("sidebar-actions", "sidebar-actions"),
    )
```

这样不需要修改 Profile 文件格式，也能尽快验证 Client Runtime 和 Slot 契约。

后续如果要让用户通过 Harness Home 配置客户端 UI，应使用独立的 Client Profile：

```text
~/.harness/client-profile.yaml
~/.harness/client-profile-patch.yaml
```

不能把 Host Entry 和 Client Entry 混在同一个 Profile 后同时传给两个 Catalog。Host Catalog
不认识 Client UI Plugin，Client Catalog 也不应该看到 Session、Agent 等 Host Plugin。

Client Profile 可以复用：

- `ProfileBundle`
- `ProfileBundleCatalog`
- `EntryPatch`
- `composeProfile`
- Profile Codec

建议使用 `ProfilePatchReload.Startup`，不启动文件 watcher。修改后重启应用生效。

如果现有 `FileProfileLoader` 不能方便地指向独立路径，应在 `modules/profile-file` 中新增
Client Profile path 或参数化路径，而不是修改 Plugin、Loader 或 Runtime 语义。这属于小型文件层
改造，不是核心框架重构。

## 十一、实施阶段

### Phase 1：Ui Slot 核心

交付：

- `modules/ui-slot`
- `SingleUiSlotKey`、`KeyedUiSlotKey`、`ListUiSlotKey`
- `ClientUiSchema`
- `UiContributionSink`
- `UiContributionBuilder`
- `UiSnapshot`
- `SlotOutlet`

验收：

- 相同 name、不同 Key 对象会在 Schema 校验时失败。
- freeze 后继续 contribute 会失败。
- Single 和 Keyed 的同 order 冲突会失败。
- List 按 order 稳定排序。
- Snapshot 在构造后不可修改。

### Phase 2：Client Runtime

交付：

- `ClientHarnessHost`
- `ConnectionKey`
- `UiContributionKey`
- `ClientPluginCatalog`
- 客户端启动、回滚和关闭测试

验收：

- Host Runtime 和 Client Runtime 可以同时启动。
- UI Plugin 可以通过现有 Loader 安装。
- 任一 UI Plugin 启动失败时，Client Runtime complete rollback。
- Client Runtime 关闭不会关闭 Host Runtime。

### Phase 3：Desktop Compose 接入

交付：

- `modules/bundle/desktop-client`
- `HarnessApp`
- Root Slot 默认 Plugin
- 一个 Header 或 Status 插件
- `jvm-app` 双 Runtime 入口

验收：

- Desktop 启动后 Host Description 仍可读取。
- Root Slot 渲染默认应用。
- Header 或 Status 插件显示在目标 Slot。
- 禁用该插件并重启后 Slot 消失。
- 恢复插件并重启后 Slot 恢复。

### Phase 4：独立 Client Profile，可选

交付：

- 独立 Client Profile 文件。
- `ProfilePatchReload.Startup`。
- Host 与 Client Entries 完全隔离。

验收：

- 修改 Client Profile 后，当前进程不重载。
- 重启后使用新 Entries。
- Client Profile 不影响 Host Profile。

### Phase 5：Android 和 iOS

交付：

- 平台 Client Catalog。
- 平台 HarnessApp 入口。
- Remote Connection 注入。

验收：

- 共享 `ui-slot` 和 `HarnessApp` 在 JVM、Android、iOS 编译。
- 平台可以独立选择自己的 Client UI Plugin。
- Host 连接失败时显示客户端启动或连接错误，不启动部分 UI Plugin。

## 十二、测试与验证

### Unit Tests

`modules/ui-slot`：

- Key identity 和 name 唯一性。
- Single winner。
- Keyed cell 隔离。
- List 排序和重复 id 拒绝。
- order 冲突。
- freeze 后拒绝写入。
- Snapshot 不可变性。

### Integration Tests

`apps/shared` 或 `jvm-app`：

- Client Runtime 启动两个贡献插件。
- 禁用其中一个后 Snapshot 中不存在该 Contribution。
- 插件 apply 失败时 Loader 完成回滚。
- Host Runtime 的 Session、Agent Loop 和 Gateway 不受影响。
- Client Runtime 关闭后 Compose 不再持有 Snapshot。

### Build Checks

建议的最小检查：

```bash
./kotlin test --include-module=ui-slot --platform=jvm
./kotlin test --include-module=shared --platform=jvm
./kotlin test --include-module=jvm-app --platform=jvm
./kotlin build --platform=jvm
```

Android 和 iOS 接入后再增加对应目标的 compile/build 检查。

### Live Checks

当前 WSL 环境中的 Compose 首帧可能受 Skiko 图形上下文限制。无法在本机完成的 Compose
first-frame 和桌面窗口检查必须记录为未验证，不能以构建通过替代运行验收。

## 十三、风险与处理

| 风险 | 处理 |
| --- | --- |
| Client 和 Host Runtime 启动代码重复 | 第一版接受少量重复，第二个平台稳定后再抽公共 Boot helper |
| 同一 Profile 混用 Host 与 Client Entry | 强制使用两个 Catalog 和两个 Entry 列表 |
| UI Plugin 在 freeze 后继续注册 | Builder 进入 sealed 状态并直接失败 |
| 插件之间抢占 Single Slot | 显式 order，相同 order 启动失败 |
| Compose composition 崩溃无法局部恢复 | 第一版 fail-fast，不承诺 per-entry boundary |
| Client UI 代码泄漏 Host 依赖 | Client Runtime 只提供 Connection 和 Client 层服务 |
| 未来再次引入热重载 | 需要重新设计 declaration epoch 和 subscription，不能把它当作小增量 |

## 十四、最终建议

采用“现有 Plugin Framework + 静态 UiSchema + 启动期 Contribution Builder + 只读 Snapshot”
方案。

不需要改造 Runtime、Loader、Registry 或 PluginCatalog。第一版只新增 UI Slot 模块、Client
Runtime 和 Desktop Client Catalog，并在 `jvm-app` 完成双 Runtime 装配。

冷启动模型使 UI 扩展表和 Plugin 生命周期保持简单。等真实 Compose 客户端稳定后，再根据需求
决定是否增加独立 Client Profile、设备特定 Catalog 或有限的运行时刷新能力。
