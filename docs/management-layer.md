# Management layer contract

## Components

- `Registry`：把稳定名称映射到 `Plugin<C>` 与 `ConfigAdapter<C>`。
- `Entry`：一个声明式安装项，字段与 Cordis Loader 的核心字段对齐为 `id`、`name`、`config`、`disabled`。
- `Loader`：把完整 `Entry` 快照协调成 Runtime 中的 Fiber 集合。
- `LoaderFactory`：让多个管理组件共享 Registry，但各自持有独立的完整快照边界。

KMP 没有跨 JVM、Android、Native 一致的动态模块 import 机制，因此这里使用显式 Registry
代替 Cordis Loader 的 JavaScript 模块解析。Registry 的注册与撤销是控制面操作；应在启动阶段或
调用方自己的单一控制协程中完成。

## Reconciliation

```kotlin
data class AgentConfig(val model: String)

val registry = Registry()
registry.register(
    name = "agent",
    plugin = agentPlugin,
    config = typedConfig<AgentConfig>(),
)

val loader = Loader(runtime, registry)
loader.reconcile(
    listOf(
        Entry(
            id = "primary",
            name = "agent",
            config = AgentConfig("gpt-5"),
        )
    )
)
```

协调规则：

- 相同 Entry、相同 Registry registration 且 Fiber 健康时保留原 Fiber。
- `name`、`config`、`disabled` 或 registration identity 变化时重建 Fiber。
- 删除或禁用 Entry 会卸载对应 Fiber。
- disabled Entry 不要求对应名称已经注册，也不会解码配置。
- Entry ID 必须非空且在一次快照中唯一。
- `Any?` 配置是否“未变化”使用 Kotlin `equals`；配置模型应使用稳定的值相等语义，例如 data class。

所有配置会在 Runtime 发生变化前完成 Registry 解析和类型转换。Loader 自身用 Mutex 串行化
`reconcile` 与 `dispose`。

每个 Loader 都把输入视为自己负责的完整快照，不能让多个配置源调用同一个 Loader。应用可通过
`runtime.provideLoaderFactory(registry)` 提供工厂；`IncludePlugin` 会为每个 Include Fiber 创建隔离的
child Context 和专属 Loader，因此删除一个 include 的 Entry 不会误删根 Loader 或另一个 include
管理的 Fiber。

## Failure and rollback

一次 reconcile 以调用前的完整 Loader 快照为回滚边界：

1. 卸载发生变化或被移除的旧 Fiber。
2. 按目标 Entry 顺序安装新 Fiber。
3. 任一安装失败时，反向卸载本轮已创建的 Fiber。
4. 按旧快照顺序重新安装已卸载的旧 Fiber。

原始安装错误保持主异常；回滚错误进入 suppressed errors。若旧 Fiber 本身无法恢复，Loader
仍保留旧 Entry 和可获得的 Failed Fiber；后续 reconcile 会再次把它视为需要修复，而不会把它
错误地当成健康的未变化 Entry。

## Explicit HMR boundary

本阶段不实现 HMR。Loader 不包含：

- 文件监听；
- class / module cache 替换；
- 动态源码装载；
- 变更依赖图分析。

配置变化会用同一个已注册 Plugin 定义重建 Fiber。进程内显式撤销并重新注册同名 Plugin 后，
下一次 reconcile 也会重建对应 Fiber，但这只是受控 Registry 更新，不是自动代码热替换。
代码升级仍通过应用发布、进程重启或显式重建 Runtime 完成。
