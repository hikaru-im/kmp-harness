# Infrastructure plugins

Phase 7 提供 logger、timer 与 include 三组独立模块，不把平台 I/O、协程调度或配置格式塞进
runtime 核心。它们复用 Context effect、Service 和 Loader 生命周期，并继续明确排除 HMR、文件监听
与动态代码加载。

## Logger

`logger` 是 `io.github.oshai:kotlin-logging:8.0.4` 与 Harness Context 之间的薄桥接层，只包含：

- `LoggerService`：按名称返回 kotlin-logging 的 `KLogger`。
- `LoggerKey`：把服务纳入 Context 的类型化依赖解析。
- `LoggerPlugin`：在 Fiber 生命周期内提供服务绑定。

```kotlin
runtime.install(LoggerPlugin)

val log = runtime.context.require(LoggerKey).logger("agent.worker")
log.info { "started" }
```

Harness 不定义自己的 Logger、level、record、sink、格式、console exporter 或平台输出，也不修改
`KotlinLoggingConfiguration.loggerFactory` 这类进程级全局状态。LoggerPlugin 停止时只撤销
`LoggerService` 绑定；实际 logger 和 backend 不归某个 Runtime 所有。

`logger` 库的 JVM/Android 目标包含 SLF4J API，但不包含任何 provider；没有 provider 时 SLF4J 使用
NOP。平台应用在最终装配层选择实现：

- `jvm-app` 引入 runtime-only 的 Logback，并用 `resources/logback.xml` 输出到控制台。
- Spring Boot `server` 继续使用 starter 自带的 Logback，不重复引入 provider。
- `android-app` 在 `Application.attachBaseContext()` 中、首个 logger 创建前选择 kotlin-logging
  内置的 Android Native factory，日志直接进入 Logcat，不引入 Android SLF4J provider。
- Darwin 目标继续使用 kotlin-logging 提供的 OSLog 实现。

原 `logger-console` 模块已删除；格式与输出仍由各最终应用负责，而不是由 Harness logger 模块负责。

## Timer

`TimerPlugin` 提供一个 `TimerService`。每个 timer 同时属于 Service scope 与发起调用的 Context：任一
生命周期结束都会取消 coroutine Job。

```kotlin
context.timeout(1.seconds) { refreshOnce() }
context.interval(5.seconds) { heartbeat() }

val search = context.debounce<String>(300.milliseconds) { query ->
    searchRemote(query)
}
search.submit("cordis")
```

语义：

- interval 使用 fixed-delay，回调不会重叠；失败策略显式选择 `STOP` 或 `CONTINUE`。
- debounce 保留最后一次输入。
- throttle 默认立即执行首个输入，并可选择在窗口末尾执行最新输入。
- 非取消异常交给 `TimerFailureHandler`，取消异常保持协程取消语义。
- TimerService 已销毁后拒绝创建 handle 或提交 trigger。

## Include

Include 分为四层：

```text
ConfigResource -> EntryCodec -> ConfigSource -> IncludeService -> child Loader
```

- `ConfigResource` 只负责读取文本与 revision；`MutableConfigResource` 提供乐观锁写回。
- `EntryCodec` 只负责格式；`JsonEntryCodec` 保留 plugin config 为 `JsonElement`。
- `ConfigSource` 组合 I/O 与格式，向 IncludeService 提供完整 `ConfigSnapshot`。
- `EntryTransform`/`PatchEntry` 在 reconcile 前执行纯快照变换。
- `IncludeService` 用 Mutex 串行化整个 load/decode/transform/reconcile 或 save 事务。

应用先提供共享 Registry 的 LoaderFactory：

```kotlin
runtime.provideLoaderFactory(registry)
registry.register("include", IncludePlugin, typedConfig<IncludeConfig>())
```

每个 Include Fiber 会 isolate `IncludeKey`，创建 child Context 和专属 Loader。包含的 Plugin 可以通过
自己的 Context 获取所在 IncludeService，而根 Context 和 sibling include 看不到该绑定。

`refresh()` 在读取或解码失败时不触碰运行态；Loader 安装失败时执行完整快照回滚。`save()` 先协调
运行态，再用上次 revision 写回；写回失败会把 child Loader 恢复到调用前快照。`InMemoryConfigResource`
与 JSON codec 是 common 实现；文件、Android asset、Apple bundle 或远程配置应作为平台适配器提供，
本模块不隐含文件系统或 watcher。
