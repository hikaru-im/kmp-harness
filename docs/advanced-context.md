# Advanced Context contract

## Typed intercept

`InterceptKey<T>` 为 Context 层级上的服务调用配置提供类型边界。默认使用最近一层配置；需要
组合配置时，在 key 上声明 root 到 child 的 merge 规则。

```kotlin
data class TraceConfig(val tags: List<String>)

val Trace = interceptKey<TraceConfig>("trace") { parent, child ->
    TraceConfig(parent.tags + child.tags)
}

val request = runtime.context
    .intercept(Trace, TraceConfig(listOf("request")))
    .intercept(Trace, TraceConfig(listOf("agent")))

val config = request.intercept(Trace)
```

`intercept(key, config)` 创建普通 child Context，因此继承父级 Service resolution、isolation、
owner 和 lifecycle；父级 dispose 时会一并清理。`intercepts(key)` 可读取 root 到当前 Context
的未合并配置链。

## Event modes

事件 API 用不同的 typed key 约束 Listener 和 dispatch，避免把同步与挂起回调、无返回值与
有返回值事件混在同一个 key 上。五种 Cordis 模式以及两个 KMP 异步适配模式如下：

| Mode / Key | 是否等待 | 顺序 | 返回值 | 语义 |
| --- | --- | --- | --- | --- |
| `emit` / `EventKey<T>` | 否 | 注册序 | 无 | 同步观察者广播，遇错停止 |
| `parallel` / `ParallelEventKey<T>` | 是 | 并行 | 无 | 并发调用并等待全部 Listener settle |
| `sequential` / `SequentialEventKey<T>` | 是 | 注册序 | 无 | 依次 await 全部 Listener，失败或取消时停止 |
| `serial` / `SerialEventKey<T, R>` | 是 | 注册序 | 首个 bail 值 | 依次 await，遇到 bail 值提前停止 |
| `bail` / `BailEventKey<T, R>` | 否 | 注册序 | 首个 bail 值 | `serial` 的同步版本 |
| `waterfall` / `WaterfallEventKey<T, R>` | 否（是否继续由 `next()` 决定） | 注册序 | 最外层 Listener 的返回值 | 同步洋葱中间件 |
| `waterfall` / `SuspendWaterfallEventKey<T, R>` | 是（是否继续由 `next()` 决定） | 注册序 | 最外层 Listener 的返回值 | 可挂起洋葱中间件 |

与 Cordis 一致，`null` 和 `false` 不属于 bail 值，其他非 null 返回值会终止 `serial` / `bail`。
`waterfall` Listener 的签名为 `(event, next) -> R`；不调用 `next()` 就会短路其后的 Listener
与 terminal。

`SequentialEventKey` 是 DSH 使用 Cordis `serial` 派发 `Promise<void> | void` 监听器时在 Kotlin 中的
类型安全对应物。JavaScript 的 `undefined` 不构成 bail 值；Kotlin 的 `Unit` 却是非 null 值，直接使用
`SerialEventKey<T, Unit>` 会在第一个 Listener 后停止。`sequential` 因此没有返回值，并在正常路径上等待
全部 Listener。

DSH 的 waterfall 可以把 Promise 作为整个链条的返回值，Kotlin 的同步函数类型不能在链内直接调用挂起
操作。`SuspendWaterfallEventKey` 保留相同的 next/短路语义，同时让 Listener、`next()` 和 terminal 都能
挂起；异常与协程取消原样传播。

Harness 另外保留 `PipelineEventKey<T>` / `pipeline`：它按注册序 await `(T) -> T` Listener，
把前一步输出交给下一步。这是原 KMP API 的异步值转换能力，不属于 Cordis 五种 dispatch mode。

`EventOptions(prepend = true)` 把 Listener 放到当前列表头部；`once` 在实际到达并调用前注销自身。
因此，被 `waterfall` 短路而未到达的 once Listener 仍会保留。
所有模式在开始时复制 Listener 快照，所以当前 dispatch 中的注册与注销只影响后续 dispatch。
Context 的 `on` / `once` 自动把 Disposable 交给当前 Context 生命周期。

`parallel` 会等待全部 Listener：第一个注册顺序中的失败为主异常，其他失败进入 suppressed
errors；协程取消不被包装为普通事件错误。

事件 dispatch 独立于 Runtime mutation lane。Listener 的并发执行不能用来绕过生命周期事务；
需要修改 Runtime 的结果应通过受控的 suspend mutation 提交。Listener 注册和注销应在 Plugin
lifecycle 或调用方自己的单一控制协程中进行。

## Plugin DSL

```kotlin
val agentPlugin = plugin<AgentConfig> {
    inject(LlmKey)
    optional(ToolsKey)

    apply { context, config, scope ->
        val llm = context.require(LlmKey)
        scope.add(llm.open(config.model))
    }
}

val fiber = runtime.plugin(
    plugin = agentPlugin,
    config = AgentConfig("gpt-5"),
)
```

`plugin<C>` 与 `simplePlugin` 在构建时防止重复 inject，并要求且只允许一个 apply block。
`Runtime.plugin(...)` 是 `Runtime.install(...)` 的 Cordis 风格别名；Runtime 仍然保留为 KMP 中
显式的生命周期协调器。

`serviceKey`、`eventKey`、`parallelEventKey`、`sequentialEventKey`、`serialEventKey`、`bailEventKey`、
`waterfallEventKey`、`suspendWaterfallEventKey`、`pipelineEventKey` 和 `interceptKey` 提供无需声明 object
子类的轻量工厂。
