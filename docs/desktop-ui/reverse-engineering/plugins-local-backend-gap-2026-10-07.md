# 本机插件真实数据接线与最小 backend 缺口

日期：2026-10-07。范围：Harness Desktop 本机插件读取与配置服务。仅源码调查与本文写入；未运行 build/test、启动 GUI/Host、调用插件管理操作或读取用户密钥文件。未重新扫描 ASAR；原版用户插件定义沿用同目录 `plugins-implementation-contract-2026-10-07.md`。

## 1. 可实施结论

1. 本机 Desktop 已持有真实 `HarnessHost.loader`、`registry`、`runtime`，可以读编译定义名称、已协调的 Entry、声明禁用状态、实际 Fiber 状态与注入依赖。它们是 **Harness Runtime 扩展数据**，不是 ASAR 已安装用户插件。
2. 现有稳定 API 没有逐插件 manifest、版本/开发者/安装来源、用户插件安装 inventory、Apps/Skills/MCP 归属或用户插件 enabled 配置。不能把 catalog 名称转换成 `InstalledPlugin`，也不能把 `FiberState.Active` 转换成用户启用状态。
3. `SettingsScope.describe/update/replace/watch` 已具备真实配置读写与进程内 revision 检查；需要已知 namespace 和 schema。当前没有通用 namespace 枚举、插件归属映射或用户插件启停服务。可立即复用已存在的 `llm-koog` 专用配置消费链，不能创建任意 namespace 后宣称有真实消费者。
4. 最小有用批次：新增独立 Runtime 只读 snapshot adapter，明确标题“运行时扩展”；原 `InstalledPluginRepository` 保留默认 `PluginServiceNotExposed`。若目标必须是原版用户插件功能，先实现本机 user-plugin inventory/manifest/config 服务，再注入只读 repository；安装/卸载最后独立实施。

“当前已安装”在本文指**代码可访问的数据来源**；本任务没有启动本机 Host，不宣称实测安装数量、运行状态或账号权限。

## 2. 当前真实消费链与准确源码符号

以下行号来自本次当前磁盘读取；共享工作区后续编辑可能移动行号。路径以仓库根为基准。调查先使用 CodeGraph，再定向核对源码；没有依赖旧根目录 bundle。

| 环节 | 源码符号 / 位置 | 实际含义 |
| --- | --- | --- |
| Desktop bootstrap | `apps/jvm-app/src/main.kt:69–86`，`main` | `startDesktopProfile` → `resources.host` → `profiledHost.host`；直接持有 Host，创建 `LocalConnection(harnessHost.gateway)`，现已用 `DesktopModelSettingsBackend(harnessHost.runtime.context)` 接真实配置 |
| Host 所有权 | `modules/boot/src/im/hikaru/harness/boot/HarnessHost.kt:25–30`，`HarnessHost` | 公开 `profile/runtime/registry/loader/gateway`，生命周期由 `close` 管理 |
| Host 启动 | 同文件 `HarnessHost.start:74–103` | 注册 host/session API、编译 catalog、loader factory，最后协调完整 entries |
| 编译定义 | `modules/loader/src/im/hikaru/harness/loader/PluginCatalog.kt:8–11,43–48`，`PluginDefinition` / `PluginCatalog` | definition 仅 `name` 和 `register`；catalog 公开 `definitions/names`；没有用户包 manifest 字段 |
| 名称解析 | `modules/loader/src/im/hikaru/harness/loader/Registry.kt:16–30,70–81`，`Registry` | 公开 `size/names/contains/register`；`prepare` internal；实际 Registration/config adapter private，不能反射取出 |
| 已应用配置 | `modules/loader/src/im/hikaru/harness/loader/Loader.kt:40–47`，`Loader.entries/entry/fiber` | 真实受该 Loader 管理的 Entry，以及 Entry id 对应 Fiber；disabled 条目也保留 |
| 实例声明 | `modules/loader/src/im/hikaru/harness/loader/Entry.kt:9–14`，`Entry` | `id/name/config:Any?/disabled`；id 是实例身份，同名插件可有多个实例 |
| 实际实例 | `modules/runtime/src/im/hikaru/harness/runtime/Runtime.kt:69–70`，`Runtime.fibers` | Runtime 全部 Fiber 的列表副本，可能包含子 Context/子 Loader 的实例；没有稳定插件名称映射 |
| 生命周期 | `modules/runtime/src/im/hikaru/harness/runtime/plugin/Fiber.kt:31–89,205–235`，`Fiber` | `id/plugin/config/context/state/failure/missingDependencies/canStart`；state 为 `FiberState`，failure 不可直接当公开错误详情透传 |
| 状态枚举 | `modules/runtime/src/im/hikaru/harness/runtime/plugin/FiberState.kt:24–70`，`FiberState` | Pending/Loading/Active/Failed/Unloading/Disposed；不存在用户插件 Installed/Enabled 语义 |
| 依赖声明 | `modules/runtime/src/im/hikaru/harness/runtime/plugin/Plugin.kt:40,69`；`InjectSpec.kt:70–86`；`service/ServiceKey.kt:13–15` | `Plugin.inject` → `InjectSpec.key/required` → key.name；这是消费依赖，不是对外提供能力或 Apps 数量 |
| 当前 profile | `modules/profile-file/src/im/hikaru/harness/profile/file/ProfiledHarnessHost.kt:28–49,59–65` | `host/current/lastReloadFailure/isWatching`；refresh 成功协调后替换 current；current 比启动时 `host.profile.entries` 更适合描述后续 profile |
| profile 元数据 | `modules/profile/src/im/hikaru/harness/profile/ProfileModels.kt:20–24,39–45`；`modules/profile-file/src/im/hikaru/harness/profile/file/FileProfileLoader.kt:22–27` | `ProfileManifest(name,bundles,patchReload)` 是 Host profile manifest；`LoadedFileProfile(entries,warnings,watchedFiles)` 不是逐插件 manifest |
| 服务配置 | `modules/settings/src/im/hikaru/harness/settings/SettingsKey.kt:5–6`；`SettingsService.kt:16–37,147–191` | `Context.get/require(SettingsKey)` 得服务；`scope(namespace)` 只取已注册 namespace，未注册会抛错；无 list API |
| 配置 descriptor | `modules/settings/src/im/hikaru/harness/settings/SettingsModels.kt:29–49`；`SettingsService.kt:59–74` | describe 默认脱敏；返回 value/user/base/revision/applies/secrets；get 返回原始未脱敏值 |
| 持久化 | `modules/settings-file/src/im/hikaru/harness/settings/file/SettingsFileDocumentStore.kt:55,87–147,287–300` | `FileSettingsDocumentStore` 实际文件读写；`SettingsFilePlugin` 提供 `SettingsKey`；文件锁、临时文件替换、可选 watcher |
| 现成业务 adapter | `modules/bundle/desktop/src/im/hikaru/harness/bundle/desktop/DesktopProviderSettings.kt:77–86,117,152`；`apps/jvm-app/src/im/hikaru/harness/desktop/settings/DesktopModelSettingsBackend.kt:10` | `llm-koog` 真实 namespace 的 typed read/save/remove；这是 Provider 管理，不是插件安装/卸载 |

补充：`DesktopBundle.kt:39–63` 的 `DesktopPluginCatalog` 是编译可解析集合；`:66–89` 的 `DesktopProfileBundle` 是默认声明 entries。用户 overlays/disabled/配置失败可能改变实际结果，默认 bundle 清单不能作为已安装成功证据。Host 额外保留 `harness.host-api` 内部条目（`HarnessHost.hostEntries:119–129`），不能把它展示为用户可卸载插件。

客户端 UI Runtime 也不同：`modules/ui-slot/src/im/hikaru/harness/ui/slot/ClientHarnessHost.kt:15–20,48–74` 的 runtime/loader 私有，启动后 `builder.freeze()` 产出 UI snapshot。`apps/shared/.../app/DesktopClientCatalog.kt:17–34` 的 root/header/sidebar/workspace 是编译 UI 贡献，不能作为用户插件列表或动态 Slot 管理源。

## 3. 字段与动作：现在能读写什么

| 需求 | 现在真实可得 / 可调用 | production 限制与结论 |
| --- | --- | --- |
| Runtime 列表 | `Loader.entries`；catalog/registry names 用于独立对照“可解析定义” | 可做本机诊断读取；不将 catalog 全量当安装清单。根 Loader 范围必须标明，不宣称覆盖所有子 Loader |
| Runtime 身份/名称 | `Entry.id/name`，`Loader.fiber(entry.id)?.id` | 使用 Entry id 定位；Fiber id 仅当前 Runtime 生命周期有效；不能按名称去重 |
| Runtime 声明启用 | `!Entry.disabled` | 仅表示配置允许创建实例；Pending/Failed 仍可能声明启用，必须与 state 分列 |
| Runtime 运行/失败 | `Fiber.state`，缺失必需依赖 `missingDependencies` | disabled 无 Fiber 是正常声明状态；非 disabled 无 Fiber 不得伪造 Active。只输出经审核的错误摘要，不 stringify config/Throwable |
| 用户插件 manifest | 无 | `PluginDefinition` 只有名称；`ProfileManifest` 是 Host profile。版本/developer/description/install source 要新增 manifest 数据源，缺失显示 Unknown/Unavailable |
| 用户插件 enabled | 无 | ASAR `plugins.<id>.enabled` 不是 Harness `Entry.disabled` 或 Settings namespace。需新服务提供权威设置、默认值与 expected version |
| 配置只读 | 已知 `SettingsScope.describe()` | 可立即用现有 namespace；归属与 schema 需静态 typed descriptor。不要发送 raw `Entry.config`、`Fiber.config` 或 `SettingsScope.get()` |
| 配置更新 | `SettingsScope.update(patch,expectedRevision)` / `replace(section,expectedRevision)` | 实际校验、store.write 后 revision 更新；有真实 typed consumer 才开放。保留其它字段；脱敏值 `***` 不能回写覆盖秘密 |
| 配置生效 | descriptor `applies:LIVE/RESTART`；已存在 consumer watch | LIVE 枚举不证明所有插件监听。`KoogLlmPluginRuntime.applyDynamic:68–90` 注册 LIVE namespace；`KoogLlmReloadCoordinator.kt:91` 订阅后 reload，是确认的消费链 |
| 依赖/能力 | `Plugin.inject` / `missingDependencies`，已知 `Context.has(key)` | 可显示“必需/可选依赖是否满足”；不能反推导出所有提供的 service/tool/UI/App。对外能力需注册归属 descriptor |
| Apps/Skills/MCP 数量与详情 | 无对应用户插件归属 API | 保留 NotLoaded/Unavailable；不显示假 0 Apps 或空成功 capabilities |
| Runtime 协调 | `Loader.reconcile(desired)`；`HarnessHost.reconcileProfile(entries)` | 已有真实内部 mutation，可能重建实例与依赖；无用户插件权限/持久事务，首批 UI 不暴露 |
| Runtime install/uninstall | `Runtime.install:187` / `uninstall:336` | 操作编译 Plugin/Fiber；不获取/删除用户插件包。不能绑用户 Install/Uninstall 按钮 |
| profile 刷新 | `ProfiledHarnessHost.refresh()` | 读文件并协调真实 profile；不是用户插件 inventory refresh。不得调查时调用；不作为用户 plugin reload 回调 |
| 用户安装/卸载 | 无包管理 service | 要新增包格式、来源、验证、写入/删除、保护条目、失败恢复和 inventory 更新；本批不可交互 |

配置 revision 是 SettingsService **进程内 namespace revision**，注册时从 0 开始，不是跨进程磁盘 CAS。`FileSettingsDocumentStore.write` 的文件锁/替换没有 expected 文件版本检查。`SettingsService.watch` 通知使用原始 `SettingsChange.next/previous`；adapter 只发 invalidation，重新 `describe()` 脱敏读取。文件 watcher 吞掉外部非法文档异常（`:187–197`），没有对 UI 暴露持续 watch error；不能声称完整外部错误可观测。

## 4. Connection 与生产默认的缺口

- `apps/shared/.../connection/Connection.kt:16–19` 仅 `host/session`；`HostApi.kt:7–9` 仅 describe。`LocalConnection.kt:57–70` 只把这些调用分派到 Gateway。PluginMessageSource 只是消息来源标签，不是 plugin registry。
- `modules/api/gateway/.../host/HostApiGateway.kt:10` 注册 `host.describe`；`.../session/SessionApiGateway.kt:22–69` 注册 session.list/create/history/prompt/cancel。`HarnessHost.start` 没有注册用户 plugin 读取或管理 endpoint。Relay RPC 信封/传输能力不等于 plugin endpoint 已实现。
- `apps/shared/.../plugins/PluginRepository.kt:4–18` 的 `InstalledPluginRepository` 是用户插件只读接口；默认 `UnavailablePluginRepository.canRead=false` 并返回 `PluginServiceNotExposed`。已有 `HarnessApp` 可注入该 repository，但 Desktop `main` 当前没有真实用户插件 repository 构造与注入。
- `PluginModels.kt` 的 `PluginScope(hostId,generation,accountId?)` 与 controller 已做范围隔离；模型明确禁止 Runtime startup catalog 成为 `InstalledPlugin` 数据源。保持该语义，不能仅为了页面有列表而放宽。

结论：无需先加 RPC 就能在 Desktop launcher 注入 **本机 Runtime 诊断 adapter**，类似现有 Model Settings adapter；原用户插件页面要成功加载，仍需新增真实用户插件服务。不能借本机账号登录或 Relay 连接自动开放本机数据。

## 5. 最小 production adapter 方案（提案，尚未实现）

### A. 立即可做：真实 Runtime 只读管理面

建议独立 `HarnessRuntimePluginReader` 与 `RuntimePluginSnapshot`，不实现 `InstalledPluginRepository`。输入来自当前已启动的 `HarnessHost`/`ProfiledHarnessHost`，不要再创建一套 Runtime：

```kotlin
// Proposed types only; these symbols do not exist yet.
interface HarnessRuntimePluginReader {
    suspend fun read(scope: PluginScope): RuntimePluginSnapshot
}
data class RuntimePluginSnapshot(
    val scope: PluginScope,
    val revision: Long,
    val entries: List<RuntimePluginEntry>,
)
data class RuntimePluginEntry(
    val entryId: String,
    val definitionName: String,
    val declaredEnabled: Boolean,
    val fiberId: Long?,
    val runtimeState: RuntimePluginState,
    val dependencies: List<RuntimePluginDependency>,
)
```

`RuntimePluginState` 静态覆盖 FiberState，另加 DeclaredDisabled/InstanceUnavailable；没有 Fiber 的依赖字段不可假作已读取空集合，应独立携 Available/Unavailable 状态。配置另设 typed detail，默认 unavailable；已知 namespace 再用 descriptor 脱敏。snapshot revision 必须由实际服务维护，不用“读取次数”伪装 backend revision。

**先补小型一致性读接口再发布 adapter**：`Loader.entries/entry/fiber` getter 没有拿 reconcile mutex，`Runtime.fibers` 虽返回副本但不是与 Loader 原子一致的快照；`ProfiledHarnessHost` live watcher 可并行更新。建议在 loader 模块新增内部持锁 snapshot 导出，只复制 id/name/disabled/fiberState/依赖等静态字段，不导出 Fiber/Context/Any config；锁协调方式核实后实现，不能在 desktop 拼几个 getter 宣称原子。外层 host reader 校验未关闭并绑定本机 hostId。metadata/settings 与 runtime snapshot 不同 revision，分别标明，不假装同一个事务。

Launcher 用 `DesktopRuntimePluginReader(localHostId, host)` 注入独立诊断状态/页面。scope 必须来自 verified online Local connection，adapter 再校验 hostId 等于自身 constructor 值；generation 防旧回包，断开/Host 改变立即清空。Relay/外来 Local Host 返回 Unsupported/Unavailable。本机 accountId 为 null，Relay 身份如未来支持需含完整 tenant/member identity。

读取/刷新有真实 adapter 才开放；编译 metadata 通过静态 `RuntimePluginDescriptor` 补充时必须明确是 compiled metadata，不能伪装现场发现的用户包。若要显示提供能力，增加明确 descriptor/注册归属，不能由 required service 反推。首批仍禁用 Enable/Disable/Install/Uninstall，给出具体缺口。

### B. 真正用户插件页：最小本机 read service

新增 `LocalUserPluginInventory`，权威源是 **Harness 自己管理并校验的本机用户插件 manifest + installed record + enable config**。明确 Host 所有的根路径与包规范；目录内容不是 installed 成功的充分证据。不存在这个源时返回 ServiceNotExposed；根路径/配置损坏返回错误；只有真实成功枚举才允许空列表。不能读取任意 JSON 后当成管理服务，也不能把 ChatGPT/Codex 私有安装目录直接当 Harness 插件。

最小静态 DTO：`LocalUserPluginRecord(id, manifest, sourceReference, enabledState)`；manifest 字段按真实包 schema 校验，未提供的 version/description 保留 Unknown；Apps 仅在服务实际读取时填充。`LocalUserPluginReadService.listInstalled/readDetail` 不承诺市场、推荐、安装或授权。

`DesktopInstalledPluginRepository(localHostId, service)` 适配现有 `listInstalled(scope)` / `readDetail(scope,id)`；明确验证 Local transport 绑定（当前 PluginScope 不携 transport，需受控绑定对象或额外 typed access 参数）、本机 hostId、generation 与实例生命周期。不要仅通过 accountId 是否为空判断 Local。读取结果沿现有 PluginResult/controller 进入 loading/error/empty/unavailable；真实成功后才允许 search/enabledOnly。

先走 launcher 直接注入，最小批次不需要扩 Connection。需要跨进程时再新增 typed service key、Gateway endpoint 与 Connection API，并做权限与序列化契约；不能只是发送 ASAR `plugin/installed` 字符串或透传私有远程服务。

### C. 写入次序与边界

1. **配置**：只接有真实 schema、namespace 归属和业务 watcher 的 SettingsScope；传 expectedRevision，冲突/校验/持久化错误可见；RESTART 显示待重启，不伪报已生效。现成 Provider 页面仍调用 DesktopProviderSettings，不借此生成插件详情数据。
2. **Runtime 启停如将来需要**：新增 Host-owned profile mutation 服务；保护 host-api/settings/session 等依赖，处理 overlays 优先级、expected profile version、持久化/协调/rollback、reload mode 与消费成功证据。`EntryPatch.disabled` 是可用底层表示（ProfileModels:44），`FileProfileLoader` 目前仅 load，无 patch writer；`reconcileProfile` 不持久化且不更新启动 profile 对象。直接改 `loader.entries` 重放会被 profile watcher 覆盖。默认可采用持久配置后冷启动生效，不能擅自动态卸载 UI Runtime。
3. **用户插件 enabled**：在 B 的权威 inventory 服务实现版本化设置写入和实际业务消费者；不映射为 namespace 任意键、`Entry.disabled` 或 Fiber.stop。
4. **安装/卸载**：等待实际包管理服务。移除 Fiber、registry registration 或 compiled catalog 条目都不是卸载用户包；未经消费链闭合保持禁用。

## 6. 下一批建议写集与验收

提案写集（本文没有实施）：loader 模块一致性 snapshot API；JVM `desktop/plugins/DesktopRuntimePluginReader.kt`；shared 独立 Runtime DTO/state/page；父线程 launcher 接注入。用户插件 read service 与 `DesktopInstalledPluginRepository` 单独一批，避免改变现有 `InstalledPlugin` 来源契约。配置和 mutation 等源/消费者闭合后再做。

- [ ] Runtime 与用户插件两个数据类别、页面标题和 repository 明确区分；不显示 runtime catalog 假安装列表。
- [ ] snapshot 与 reconcile/live watcher 并发读取不混代；Entry id 保留多实例；根/子 Loader 范围准确。
- [ ] disabled、Pending、Failed、Active 分别显示，启用声明与运行状态不混淆。
- [ ] 无 manifest/Apps/能力来源时明确 Unknown/Unavailable；不造 version、来源、零数量或空回调。
- [ ] 本机 adapter 校验 constructor localHostId 和受控 Local binding；Relay、离线、其他 Host 无本机数据，scope/generation 变化拒收旧响应。
- [ ] 配置只读脱敏；watch 原始值不外发；revision 冲突、store 错误、RESTART/LIVE 消费限制真实可见。
- [ ] 首批刷新有实际读取动作，启停安装卸载未接消费者时禁用说明。
- [ ] 用户 inventory 缺失服务、读取失败和真实空列表分开；测试 fixtures 仅在测试目录。

本次仅新增本文，保留全部已有脏文件；不修改产品代码、不运行 build/test。交付后释放该文件。
