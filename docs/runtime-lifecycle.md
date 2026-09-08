# Runtime lifecycle contract

## Fiber states

```text
Pending -> Loading -> Active -> Unloading -> Pending
                    \-> Failed
Pending / Failed / Active -> Disposed
```

- `Fiber.failure` 保存最近一次启动或卸载失败。
- `Fiber.retry()` 和 `Runtime.retry(fiber)` 只接受 `Failed`。
- retry 时 required service 缺失会回到 `Pending` 并返回 `false`。
- `Disposed` 是永久终态；重复 `stop` 和重复 `Runtime.uninstall` 是幂等操作。
- 同一个 Plugin 定义允许用不同配置安装多次，每次都会得到唯一 Fiber；这不是重复安装错误。
- 对不属于当前 Runtime 的 Fiber 执行 retry / uninstall 会被拒绝，不能误操作另一棵 Context Tree。

## Rollback

- `Plugin.apply` 失败时，当前 `EffectScope` 中已登记的副作用按反序全部回滚。
- 回滚失败不会掩盖启动错误；启动错误保持为主异常，清理错误进入 suppressed errors。
- deactivate 清理失败进入稳定的 `Failed`，不会停留在 `Unloading`。
- stop 清理失败仍进入 `Disposed`，同时把清理错误交还调用方。
- `EffectScope` dispose 后永久关闭，不能继续登记迟到的副作用。

## Concurrency

Runtime 使用一条可重入的 serialized mutation lane。以下 suspend mutation 会串行执行：

- install
- retry
- uninstall
- provide / replace 及其 Disposable
- Context dispose
- Fiber start / deactivate / stop

Plugin 生命周期中对 Context 的嵌套调用属于当前 transaction；切换 coroutine dispatcher
不会失去 transaction identity。外部并发调用会等待当前 transaction 完成，因此不会观察到
`Loading` / `Unloading` 中间态并与其交叉修改。

同步读取和结构创建 API（例如 `get`、`has`、`child`、`isolate`、`intercept`）不等待 mutation lane。
它们应在并发 mutation 开始前完成，或在同一个生命周期 transaction 内使用。
事件 dispatch 不进入 Runtime mutation lane：每次 dispatch 使用 Listener 快照，`parallel`
在同一快照内并发执行，多个独立 dispatch 也允许重叠。`on` / `once` / Disposable 属于结构变更，
应在 Plugin lifecycle transaction 或调用方自己的单一控制协程中完成。

一个 `Plugin.apply` callback 本身被视为一个 transaction。callback 不应并行 fan-out
多个 runtime mutation；需要并发的后台任务应先建立自己的资源，再通过受控的 suspend
mutation 提交结果。
