# Runtime

Runtime 是 Harness 的 KMP 执行内核，负责 Context、Fiber、Service、Effect、事件和串行生命周期
变更。它参考 Cordis core，但保留显式 `Runtime` 协调器和静态类型 ServiceKey。

核心关系：

```text
Runtime
└── Root Context
    ├── Service resolution / isolate / intercept
    ├── EventsService
    └── Fiber Context
        └── EffectScope
```

Runtime 不知道 Agent UI、Repository、HTTP、WebSocket、Relay、RuoYi 或配置文件格式。所有外部
资源必须通过 Context effect 或 Fiber 生命周期释放；所有安装、卸载、Service 变化和 dispose
通过 Runtime mutation lane 串行化。

详细行为见 [docs/runtime-lifecycle.md](../../docs/runtime-lifecycle.md)、
[docs/advanced-context.md](../../docs/advanced-context.md) 和
[docs/cordis-alignment.md](../../docs/cordis-alignment.md)。
