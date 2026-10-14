# DSH Slot Contract 对照

评估日期：2026-10-05

本对照来自独立临时 clone `/tmp/kmp-harness-dsh-remote.37bvfN`，工作树固定在
`5badb15009ae1756c3afe0ae0cef1faafc290ccc`，基线提交为
`0a53fb55bea101816fa226bb964ae2bed71c343b`。`references/deepseek-harness` 当前工作树没有执行
pull、fetch、reset、checkout 或写入操作。

| DSH 契约 | KMP Harness 采用方式 | 当前范围 |
| --- | --- | --- |
| `SlotMap` 声明 cardinality、scope、owner props 和 children | `UiSchema`、`UiSlotKey<P>`、`UiSlotKind`，业务 Props 在 `apps/shared` 静态声明 | 采用；当前实现 root、conversation header、sidebar actions |
| 宿主 entry 声明并渲染 children，插件只 register contribution | `SlotOutlet` / `ListSlotOutlet` 由 `HarnessApp` 渲染，插件只通过 `UiContributionSink` 注册 | 采用 |
| single / list / keyed / chain 类型代数 | `SingleUiSlotKey`、`ListUiSlotKey`、`KeyedUiSlotKey` | chain 延后 |
| single 与 keyed cell 的 priority winner | `order` 升序；同一 cell 同 order 失败 | 采用并有契约测试 |
| list 的稳定 id 与注册顺序 | `id` 唯一；`order` 相同时按贡献序号稳定排序 | 采用并有契约测试 |
| `ctx.slots.inject()` 跟随声明生命周期，支持动态撤销和重挂载 | Client Runtime 启动期安装、`freeze()`、只读 Snapshot | 有意改写；不引入动态 declaration epoch、HMR 或热卸载 |
| React renderer / uSES / per-entry boundary | Compose `LocalUiSnapshot` 和 `SlotOutlet` | 只吸收 Snapshot 读取语义，不复制 Web renderer |
| Client inspect/generated catalog | `DesktopClientPluginCatalog` 与静态 `ClientUiSchema` | 采用编译期 Catalog；未引入生成器 |

本轮落地的结论是“宿主拥有 Slot、插件提供贡献、渲染读取冻结 Snapshot”。DSH 的动态插件加载、
JavaScript、React、运行时 Slot 声明和 Chain 选择器不进入 KMP 生产模块。
