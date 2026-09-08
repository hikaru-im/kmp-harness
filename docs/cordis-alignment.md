# Cordis alignment status

当前范围以 Cordis lifecycle/loader/Context 和 DSH Profile 组合语义为参考，同时保持 KMP 的静态类型
与显式 Runtime 协调器。不追求逐行移植，也不执行任意 JavaScript 或动态下载代码。

## Completed phases

- Phase 3 Scope Lifecycle：Context effects/dispose、descendant Plugin cleanup、child Context cleanup、disposed protection。
- Phase 4 Robustness：失败与显式 retry、lifecycle rollback、重复卸载与 ownership guards、可重入串行 mutation lane。
- Phase 5 Management Layer：Registry、Entry、Loader、声明式配置协调与失败回滚。
- Phase 6 Advanced Context：typed intercept、Cordis 的 emit/parallel/serial/bail/waterfall 事件语义、Plugin 与 key DSL；另保留 KMP `pipeline` 扩展。
- Phase 7 Infrastructure Plugins：kotlin-logging Context bridge、协程 timer、隔离 child Loader 的 include。
- Phase 8 DSH Profile Boot：编译期 PluginCatalog、bundle/profile/home/overlay/launcher patch 分层、Profile
  初始化、startup/live reload、失败保留最后成功快照。

## Naming

直接沿用 Cordis 词汇与核心职责：

- `Context`
- `Fiber` / `FiberState`
- `EventsService`
- `Loader` / `Entry`

受 Cordis 启发、但按 KMP 静态类型与生命周期模型实现：

- `EffectScope`：承载 `Context.effect()` 注册的资源，但不是 Cordis 的同名公共抽象。
- `InjectSpec` / `Plugin.inject`：表达 Cordis inject 语义，使用 KMP typed key。
- `Registry`：负责 Plugin 注册与查找，不等同于 Cordis 内部 registry 的全部职责。
- `EventKey` 系列：分别编码五种 dispatch 的回调与返回类型。

保留 KMP 设计名：

- `Runtime`：显式协调生命周期与并发事务，Cordis 没有完全等价的独立对象。
- `Plugin`、`Disposable`：跨平台接口本身已经准确。
- `ServiceKey`、`ServiceSlot`、`ServiceBinding`：分别表达类型身份、隔离解析位置与一次具体绑定。
- `RuntimeCore`：纯内部共享实现，不暴露为业务概念。

## Platform boundary

DSH 在 Node 中从 profile `node_modules` 动态 import package。JVM、Android 和 iOS 没有共同的运行时模块
系统，因此本项目使用 `PluginCatalog`：Profile 仍按稳定 name 选择插件，但实现必须已经编译进 Host bundle。
Desktop 的 catalog 位于 `modules/bundle/desktop`，App 不导入 Provider 内部类型。

已实现 patch 文件 live reload；明确排除任意 `!!js`、classpath 扫描、`ServiceLoader`、动态 JAR/npm 下载和
源码 HMR。遇到 group/inject/intercept/isolate 等当前 Runtime 无对应语义的 patch 字段时会直接失败。
