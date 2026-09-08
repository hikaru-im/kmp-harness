# Boot

`Boot` 负责创建并组装 Harness Host 的根 Context、Runtime、Registry 和 Loader。

它不负责具体传输协议，也不直接实现 HTTP、WebSocket 或客户端状态投影。
Desktop App 在这里复用启动基础设施，并把 Profile 已组合出的 Catalog 与 Entry 快照交给 API Gateway。

```text
Boot -> Runtime + Registry + Loader -> Plugin installation
```

Boot 不启动 RuoYi Backend Relay，也不创建 LocalConnection、RemoteConnection、Repository 或
ViewModel。未来若出现第二种 Host 应用，可以复用 Boot；当前唯一 Host 装配位于 `apps/jvm-app`。

## 当前实现

`DesktopProfile` 是统一的启动值：`HostDescription + PluginCatalog + Entry snapshot`。文件读取、bundle
patch 组合和监听由 `profile-file` 在进入 Boot 前完成。`HarnessHost.start(profile)` 会：

```text
创建 Runtime / Registry / Loader / ApiGateway
    -> 注册 host.describe Endpoint
    -> 提供 LoaderFactory
    -> 注册内建 HostApiPlugin 和编译期 PluginCatalog
    -> Loader.reconcile(完整 Entry 快照)
```

`HarnessHost.reconcileProfile(entries)` 保留内建 Host API 行，并事务性刷新其余 Profile 行。未知插件或
非法配置会在 Runtime 变更前失败；安装失败由 Loader 回滚。

启动中任意一步失败都会先释放 Loader，再销毁根 Context，并把清理异常附加到原始启动异常。
`HarnessHost.close()` 使用相同顺序，支持重复调用。
