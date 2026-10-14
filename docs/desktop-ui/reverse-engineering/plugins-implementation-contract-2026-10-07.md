# Plugins 实施契约：当前 Desktop ASAR

日期：2026-10-07。本次仅调查并写本文与同名 evidence JSON；未修改生产代码，未构建、测试、启动 GUI 或执行插件管理操作。

## 1. 可实施结论

Harness 当前 Connection 未暴露用户插件目录或管理服务。下一批限定为静态 typed DTO、管理状态、foundation 页面和 unavailable repository。默认状态必须为 Unavailable(PluginServiceNotExposed)，具体原因：“当前连接未暴露用户插件目录与管理服务”。禁止用空成功列表、示例插件、假安装成功或空回调填补服务缺口。

ASAR 用户插件与 Harness Runtime 插件不是同义概念。Runtime 确实支持 install(Plugin, config, parent) 和 uninstall(Fiber)，但这是内部 Context/Fiber 生命周期；编译 catalog、Profile reload 不能充当外部用户插件安装/卸载服务。

## 2. 当前 archive 与证据

| 字段 | 当前实测值 |
| --- | --- |
| Path | /Applications/ChatGPT.app/Contents/Resources/app.asar |
| Size | 546868793 bytes |
| SHA256 | 22466838293554644d2dcb52aa128b389c713bed734900cd926bed76de47aa71 |
| mtimeNs | 1791302427362313974 |
| Header words | [4, 5779180, 5779176, 5779170] |
| Payload base | 5779188 |

旧 inventory-2026-10-07/manifest.json 的 size=546868335、SHA256=22252f9b76d73a04d9e23710e358201cc4c29b6f003cfbbe109eeb0784dcf848，与当前 archive 不同；只用于候选名称参考。本次没有使用仓库根目录旧 bundle 内容。

同目录 plugins-implementation-contract-2026-10-07.evidence.json 保存 30 个必要 assets 的当前 path/size/SHA/payloadOffset/absoluteStart、288 个关键片段、32 个已检查 leaf 片段，以及 Harness 源码指纹。偏移是零基 UTF-8 bytes：absoluteOffset = payloadBase + payloadOffset + byteOffset；片段包含上下文和分类，完整证据不依赖临时目录存续。

既有 scripts/analyze-codex-webview.py 分析结果位于 /tmp/harness-plugins-contract-20261007/analysis/。该脚本词法命中不证明调用链；evidence 分开记录真实消费者、registry 与仅上下文。未调用服务端，实时账号权限及部署状态未验证。

## 3. 原版页面与布局

| 表面 | 已确认组成/入口 | 主要 bundle 前缀 |
| --- | --- | --- |
| 插件目录 | /plugins；搜索、openai/workspace/personal 分区、卡片、滚动分页、loading/error/empty；受账号、flag、能力过滤 | plugins-state、plugins-query、plugins-cards-grid、use-search-plugins |
| 详情 | /plugins/{pluginId}；图标、名称、developer、description、动作、Skills、Apps、MCP/tools、Settings、连接依赖 | plugin-detail-page、plugin-detail-view、plugin-detail-actions |
| 管理入口 | /settings/plugins-settings；initialHostId、initialTab=plugins；返回导航保留 query/分区/滚动/history | plugins-skills-tabs、plugin-detail-actions |
| 管理 tabs | Plugins / Apps / MCPs / Skills，按能力/计数过滤；Manage extensions 是 accessible title，未证明 sidebar 可见文字就是它 | plugins-skills-tabs |
| 已安装列表 | 按 hostId/workspace roots 查询，合并安装状态、去重、hidden IDs、本地搜索；与目录入口区分 | installed-plugins、plugins-settings、app-shared |
| Marketplace 管理 | Plugins 管理内嵌表面，含 loading、部分来源错误、添加/升级/移除/重试；不是已证明的第五个 tab | plugins-settings、app-initial |
| 安装/依赖 dialog | required apps、授权状态、安装过程、错误、连接流程 | plugin-installation-content、plugin-required-apps、plugins-connections-modal |

源码布局证据：settings 根为 flex min-h-0 flex-1 flex-col；header 为 flex flex-wrap items-center justify-between gap-3；卡片为 flex flex-col overflow-hidden rounded-lg border border-default；安装 dialog 为 h-fit max-w-[calc(100vw-2rem)] overflow-hidden。Compose 复用现有 foundation 主题/组件，header 可换行、长文本可布局、列表有受约束的独立滚动区。没有 GUI/截图，不宣称固定像素尺寸或视觉一致；会话面板 600px 算法不自动套用到插件设置页。

## 4. Scope 与真实消费者

Scope 必须包括 hostId、accountId（可缺）、productContext、workspace roots、serviceOrigin。插件来源也参与分派，不能只按本机/远程 host 判断。目录映射为 openai → global → GLOBAL、workspace → workspace → WORKSPACE、personal → personal → USER：中间值用于 host 搜索，最后值用于 hosted HTTP。

| 动作/数据 | 当前 ASAR 消费者 | Harness gate/限制 |
| --- | --- | --- |
| 本机目录/Marketplace | app-initial sendRequest plugin/list，cwds 可选、marketplaceKinds=[local]；另有 [vertical] 查询，返回 featuredPluginIds/marketplaces/marketplaceLoadErrors | 当前未暴露；未来保留部分来源错误 |
| 已安装 | app-shared sendRequest plugin/installed，cwds、background priority/timeout | 真实成功后才能 EmptyInstalled |
| 详情 | app-shared sendRequest plugin/read，pluginName + marketplacePath 或 remoteMarketplaceName | 来源/reference 完整；不能仅凭展示名定位 |
| 搜索/更多 | use-search-plugins sendRequest plugin/search，searchTerm/scope/cwds/cursor/limit/timeout；hosted GET /ps/plugins/search | capability 与真实 cursor |
| Install | plugin-detail-page onInstall → 安装 flow；本机 plugin/install；hosted POST /ps/plugins/{plugin_id}/install | 有真实安装消费者才开放 |
| hosted 安装参数 | plugin-installation-content：includeAppsNeedingAuth=true、body install_attempt_id、product/account headers | 首批排除；不能模拟授权完成 |
| Uninstall | app-initial plugin/uninstall 或 POST /ps/plugins/{plugin_id}/uninstall；protected/bundled 限制 | 不得映射为内部 Fiber 卸载 |
| Enable/Disable | hosted 或 source.type=remote 走 POST /ps/plugins/{plugin_id}/enable 或 disable；其他来源走本机 config/batchWrite | 不得只按 host 分派 |
| 本机启停 leaf | app-initial Dln：edits keyPath=plugins.<id>.enabled，mergeStrategy=upsert；filePath/expectedVersion/reloadUserConfig=true | 需要真实用户 config adapter/version |
| Try now / Try in chat | 安装 flow 携 defaultPrompt，进入真实 prompt/navigation | 不是 Install 同义动作；未接 prompt 消费者则不可用 |
| Open plugin | destination 存在才提供内部导航 | typed destination 必须真实存在 |
| Manage | remotePluginId 等条件成立才构造 settings route 或外部目的地 | 首批仅已有本地 typed navigation |
| Settings | plugin-settings-url、自定义设置入口/工具 | Harness 无消费者；首批 unavailable |
| Share | installed-plugin-actions GET /ps/plugins/{plugin_id}/shares 元数据 + dialog；imported Marketplace 限制 | 写入/版本更新 leaf 未闭合，首批不可交互 |
| Share RPC 注册 | app-shared 出现 plugin/share/list/save/updateTargets/delete | registry/scheduler 字符串不是 Share 按钮消费者证明 |
| Add/Upgrade Marketplace | app-initial marketplace/add、marketplace/upgrade | 当前无服务，不开放 mutation |
| Remove Marketplace | marketplace/remove；UI 确认并可能先逐个卸载关联插件，记录 completedCount/partial failure | 必须完整消费部分失败；当前不可用 |
| Refresh/Retry | query refetch/invalidation/fetchNextPage | 有真实 repository 才可点击；禁止空回调 |
| required Apps Connect | OAuth/API-key/noauth 流程；GET /ps/plugins/canonical-app/{app_id} 读元数据 | Harness 无授权/回调服务；排除实现 |
| Skill 读取 | host plugin/skill/read | 外部 skill 来源与读取服务另批 |
| Skill 启停 | hosted POST /ps/plugins/{plugin_id}/skills/{skill_name}/enable 或 disable | 未证明同名本机 RPC；首批排除 |
| Back/tab/filter/clear/close | 页面本地 reducer/navigation | typed 本地事件可实施 |

云端读路径还包括 GET /ps/plugins/list、workspace/created、workspace/shared、/ps/plugin-categories/{category_slug}/plugins、/ps/plugins/{plugin_id}、/ps/plugins/installed。installed limit=1000、pageToken，productContext 影响 includeExtensions；POST /ps/plugins/batch 以 50 条批次 hydrate。这些是 ASAR 分支证据，不作为移植私有云服务的建议。

## 5. 搜索与状态

搜索 bundle 明确 Ee=50、De=200：默认 limit=50，debounce=200ms，输入先 trim。RPC gate 涉及 authMethod 与 app-server 版本 0.147.0-alpha.6；旧/不支持路径对真实 installed/list 来源做本地匹配。Harness 应采用真实 capability，不照搬 magic 版本判断。只有已真实加载的集合才允许本地过滤。

Host 搜索使用 nextCursor；hosted 使用 pagination.next_page_token。目录/category/personal 常见 limit=50；个人目录 USER 与 workspaceCreated 的 cursor 独立。初次错误、加载下一页错误、部分来源失败要分开，保留已成功数据。hydrate 合并 installed/enabled/app metadata/hidden IDs，展示 ID 与 pluginName/marketplacePath/remotePluginId 不可互换。

必须分开的状态：

- 页面：NotLoaded、Loading、Ready、Error、Unavailable、UnsupportedHost、AuthRequired。
- 成功空结果：EmptyInstalled、EmptyDirectory、NoMatches；只有真实成功/真实过滤后才能出现。
- 分页：LoadingNext、NextPageError、PartialMarketplaceError。
- 插件：Installed/NotInstalled/Unknown、Enabled/Disabled/Unknown、Installing/Uninstalling、AdminDisabled/AppUnavailable。
- 依赖：Unknown、NeedsConnection、Connected、AuthRequired/AuthError；没有授权服务不显示 Connected。

原版文案证据包括 Plugins are not available for this host、Choose another host to browse and manage plugins、Unable to load installed plugins、No installed plugins、No matching plugins、No plugins found、Disabled by admin、Unavailable、Needs connection、Connected。

Host/account/roots 变化隔离缓存、取消请求、清除旧选择。详情/安装有 account changed guard；plugin-oauth-install-reconciliation-by-state-v1 是外部授权流程标识，不能模拟回调成功。

## 6. Harness 现状

CodeGraph 已复核当前磁盘源码，evidence 保存指纹；行号可能随主线程改动移动。

| 文件/符号 | 事实 |
| --- | --- |
| apps/shared/.../connection/Connection.kt:16 | 只有 host/session；SessionApi 同文件提供 list/create/history/prompt/cancel |
| apps/shared/.../connection/HostApi.kt:7 | 只有 describe |
| modules/api/gateway/.../host/HostApiGateway.kt:10 | 注册 host.describe |
| modules/api/gateway/.../session/SessionApiGateway.kt:22 | session.* endpoints，没有用户插件管理 |
| modules/runtime/.../Runtime.kt:187,336 | 内部 Plugin install/Fiber uninstall |
| modules/loader/.../PluginCatalog.kt:8,43 | 编译 PluginDefinition/catalog |
| modules/bundle/desktop/.../DesktopBundle.kt:39 | 编译 DesktopPluginCatalog |
| modules/profile/.../ProfileModels.kt:54 | 编译 ProfileBundleCatalog |

缺口：用户插件身份/来源、installed inventory、目录/搜索/详情、Marketplace、用户 config/version 写入、包获取/校验/卸载、授权/权限、产品账号 scope 与操作事件。Connections targeted 测试通过不代表插件服务存在，本任务没有重复该测试。

## 7. 下一批 typed 状态与页面边界

采用编译期不可变 DTO，不使用 Map<String,Any>、反射或运行期 UI 注册。

- PluginScope(hostId, accountId?, productContext, workspaceRoots, serviceOrigin)；HostId/AccountId/PluginId/MarketplaceId/Cursor/RequestGeneration 使用专用类型。
- PluginDirectoryTab：OpenAi/Workspace/Personal；PluginOrigin：HostLocal/HostRemoteMarketplace/Hosted。
- PluginReference：HostPlugin(pluginName, marketplacePath 或 remoteMarketplaceName) 或 HostedPlugin(remotePluginId)，操作 reference 与展示 ID 分开。
- PluginSummary(scope, id, reference, name, developer?, description?, logo?, origin, installedState, enabledState, policy, dependencies)；安装、启用、授权与 admin policy 独立，未知明确 Unknown。
- PluginDetail 包含同 scope 的 summary 和真实外部 skills/apps/MCP/tools/settings；未加载字段用 NotLoaded/Unavailable，不造空成功。
- PluginPage<T>(scope, generation, items, nextCursor?, sourceErrors)；每个来源独立 cursor。
- PluginCapabilities 按 readInstalled/readDirectory/search/readDetail/install/uninstall/setEnabled/marketplace/auth/settings/share 分别 Available 或 Unavailable(reason)，不使用一个总 canManage。

建议只读 PluginRepository：capabilities(scope)、listInstalled(request)、listDirectory(request)、search(request)、read(request)。PluginResult 为 Success(value)/Unavailable(reason)/AuthRequired/Failure(typedError)，协程取消继续传播。当前 UnavailablePluginRepository 每项返回 PluginServiceNotExposed；不返回 Success(emptyList())。

状态为 PluginLoadState<T>：NotLoaded/Loading/Ready/Empty(kind)/Error/Unavailable/AuthRequired/UnsupportedHost。根状态保存 scope/generation、页面 Directory/Installed/Detail/Marketplaces、tab/query、分页、selection。可以实施 SelectPage/SelectDirectoryTab/ChangeQuery/ClearQuery/SelectPlugin/Back/CloseDetail/ScopeChanged 等纯 reducer 事件。

Load/Refresh/LoadNext/Install 等远程事件仅在对应真实消费者存在时提供。响应须匹配 scope + generation + query/selection；旧响应直接丢弃。没有服务直接 unavailable，不做假 loading。只有真实外部 DTO 才能展示插件卡片；不存在详情输入时保留不可用状态。

未来 mutation 另设 typed adapter：InstallPlugin/UninstallPlugin/SetPluginEnabled/Add/Upgrade/RemoveMarketplace；发送前验证 source、scope、per-operation capability、config expectedVersion，真实成功后 refetch。若未来服务提供事件，要求 scope/operationId/revision；当前没有可订阅的用户插件事件，不能宣称实时推送。

## 8. 写集与验收

建议下一批限定目录 apps/shared/src/im/hikaru/harness/client/plugins/：PluginModels.kt、PluginRepository.kt、UnavailablePluginRepository.kt、PluginState.kt、PluginPages.kt；测试目录 apps/shared/test/im/hikaru/harness/client/plugins/：PluginStateTest.kt。主线程拥有 AppShell/settings route 接线。本次未修改这些生产文件。

首批 UI：foundation 管理页、目录/已安装/详情的 typed 本地导航、具体 unavailable 文案；Apps/MCPs/Skills 未支持时也明确 unavailable，不显示假零数量。查询 adapter 必须等待真实用户插件 endpoint/capability/reference 模型；只读先于 mutation。编译 catalog 如需展示，应独立标注 Runtime 诊断目录。

明确未闭合消费者：Share 写入和版本更新；本机 Skill 启停；自定义 Settings 工具读写到 Harness 的映射。禁止据 registry 或 runtime 生命周期补成可交互动作。

首批排除 ChatGPT hosted/团队/workspace 云编辑、云授权、分享、云项目环境、cloud computer、快照、电脑/浏览器操控、语音、宠物、自动审查、计费、图片库。Connections/Home/Thread/Shortcuts 不扩范围。

验收：

- [ ] archive path/size/SHA、asset hash、UTF-8 byte offsets 可重读验证。
- [ ] 默认 PluginServiceNotExposed；无假空成功、示例插件或安装/授权成功状态。
- [ ] 真实消费者与 Share registry/未闭合分支明确分开。
- [ ] scope/generation 贯穿 DTO；host/account/roots/query 切换拒收旧结果。
- [ ] source.type=remote 不误写本机 config；protected/admin policy 限制明确。
- [ ] 导航为 typed 本地事件；无消费者的 Install/Uninstall/Retry/Connect/Share 不可交互。
- [ ] loading/error/empty/auth/unavailable 独立，分页失败保留成功数据。
- [ ] Runtime 内部 catalog/Fiber 不冒充用户插件目录或卸载能力。
- [ ] foundation 页面窄宽可布局、长文本可读、滚动受约束；不宣称未经截图验证的像素一致。
- [ ] 后续转换测试覆盖 unavailable、scope 切换、旧响应拒收、真实空结果、分页失败和 mutation gate；由主线程串行执行。

本次校验仅限 JSON 解析、hash/片段偏移及两个目标文件检查；不运行构建或测试。两个文件交付后释放，不继续写入。
