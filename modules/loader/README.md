# Loader

Loader 是 Runtime 上方的声明式 Plugin 管理层：Registry 把稳定名称映射到 Plugin，Entry 描述
目标安装项，Loader 将完整 Entry 快照协调为 Fiber 集合。

```text
List<Entry> -> Loader.reconcile() -> Runtime.install/uninstall -> Fiber
```

Loader 负责配置转换、差异协调、失败回滚和 dispose；不负责读取 JSON/YAML、文件监听、动态模块
import、HMR 或网络。一个 Loader 只拥有一个完整快照边界，多配置源必须使用各自的 child Loader。

详细契约见 [docs/management-layer.md](../../docs/management-layer.md)。
