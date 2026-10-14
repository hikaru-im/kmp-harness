# 本地插件包消费契约：有限 schema 与安装状态边界

日期：2026-10-07。状态：分析交付，供后续 backend 实施；本文没有实施服务。唯一仓库写集为本文；没有修改生产代码、构建、运行测试、管理用户插件或改动本机 Codex 配置。保留既有 dirty 文件。

## 1. 收敛结论与实施入口

1. 本机真实包入口为 `<packageRoot>/.codex-plugin/plugin.json`；已观察的技能声明为 `skills: "./skills/"`，实际文件为 `<packageRoot>/skills/<skillDirectory>/SKILL.md`。可把这些文本作为静态编译服务的数据输入，不需要动态 Kotlin 插件加载。
2. 实测 Codex 缓存有 **19 个版本目录、56 个 SKILL.md**；这不是 installed inventory。用户配置有 **10 个插件 enabled=true**，也不等于运行有效，更不等于 Harness 已安装。
3. 首批建议实现 **Harness 自有本地目录导入、持久 inventory、启停、卸载与技能文本消费**。导入成功必须对应真实受管文件和持久记录；不能扫描 Codex cache 后直接映射为 InstalledPlugin，也不能映射 Runtime catalog/Fiber。
4. 根据主线程最新确认，技能内容由静态 typed `AgentInstructionsService/Key` 接入 AgentLoop，进入真实 `GenerateOptions.system`；该服务正在另行实现。**GenerateOptions immutable，不能依靠 Request hook 替换 options。** 本文不重复调查工具或 LLM 执行链。
5. MCP、Apps、hooks、extensions 只确认包里有相应声明；首批没有消费者，明确 Unsupported。技能说明中的工具名称、安装命令或授权要求不会自动产生可用工具。导入技能文本不代表包的全部功能已经移植。
6. 以下 schema 是 Harness 明确制定、可验证的子集；不是 Codex 全规范兼容承诺。未知字段与未闭合持久格式见第 7 节。

## 2. 证据来源与定位方式

优先读取既有 [Plugins 实施契约](./plugins-implementation-contract-2026-10-07.md) 和 [evidence JSON](./plugins-implementation-contract-2026-10-07.evidence.json)，随后复核当前 ASAR 及本地 manifest。仓库源码定位已先使用 CodeGraph，确认 `InstalledPluginRepository` / `UnavailablePluginRepository` 等当前边界；没有继续重复主线程 runtime 消费者调查。

### 2.1 当前 Desktop archive

`/Applications/ChatGPT.app/Contents/Resources/app.asar`：546868793 bytes；SHA256 `22466838293554644d2dcb52aa128b389c713bed734900cd926bed76de47aa71`。Header words `[4,5779180,5779176,5779170]`；payload base `5779188`。以下偏移都是 asset 内 **零基 UTF-8 bytes**，不是 JS 字符索引或行号；archive absolute offset = absoluteStart + byteOffset。没有依赖仓库根目录旧 bundle。

| Archive asset | Size | Payload offset | Absolute start | SHA256 |
| --- | ---: | ---: | ---: | --- |
| `/.vite/build/main-C7cfj__D.js` | 3732595 | 3308929 | 9088117 | `d8d4f45e673efc81a9e3db98fa0fa57908094bf2fd13f73f6b149cf2a3104c2e` |
| `/webview/assets/app-initial-69cd8dbddec5.js` | 11173718 | 45548891 | 51328079 | `8c11b76e960300895e8a3dde8b2613f87c92ed226b58173aa73ceaff9cd2b604` |
| `/webview/assets/app-shared-122c56612a72.js` | 7778898 | 59112582 | 64891770 | `a800cb8a356a0d7c61820cb29ed4478d30f8a73f4c7c0d9d19e035b0ffa91926` |

| Asset / byte offset | 直接可确认的 leaf | 不可据此推断 |
| --- | --- | --- |
| main @108200 附近 | `jo=[.agents,plugins,marketplace.json]`；本地 entry schema 为 `name` 与 `source:{source:local,path}`；`Lo` 只校验 `name/version/bundledContentVariant?` | 这是 bundled materialization 的最小 validator，不是完整包 schema |
| main @129771 | `Xo(root)` 读取 `.codex-plugin/plugin.json` 并用 `Lo.parseAsync` | 不证明任意 marketplace 的全规范 |
| shared @5269563 | `plugin/installed` 查询携 host 和 `cwds`，background、retry=false、staleTime=0 | 不可用磁盘 cache/config 替代该结果 |
| shared @5274689 | `plugin/read` 参数是 `pluginName` 加 `marketplacePath` 或 `remoteMarketplaceName` | 展示名、app ID、远程 ID 不可混作操作 reference |
| initial @1933395 / @1933733 | `pun` / `mun` 实际发送 `plugin/install` / `plugin/uninstall`，成功或异常分别记录；卸载前有 protected 检查 | 未看到 app-server 内部安装数据库事务或完整磁盘删除算法 |
| initial @1916963 | `Dln` 返回 `keyPath=plugins.<pluginId>.enabled,value=<bool>,mergeStrategy=upsert` | enabled 不等于独立 inventory |
| initial @1935906 / @1936228 | hosted 或 `source.type=remote` 走 HTTP enable/disable；其余 `config/batchWrite` 携 filePath、expectedVersion、reloadUserConfig=true，随后刷新 | 不能只按 host 决定写本机 config；不能直接移植私有 HTTP |
| main @141748 | `xne` 也通过 app-server `config/batchWrite` 改 plugin enabled 并 reload | 未授权 Harness 编辑 Codex config |
| main @638113 / @639096 | `zy` 合并真实 installed 状态；`By` 仅选择 installed 且 enabled 的 package roots，并考虑 localVersion/source | 缓存目录存在、UI optimistic update 均不是成功依据 |
| main @658632 | executor 先读 `plugin/installed`，再选择 package roots | 不需要把 executor 逻辑复制到 Harness 首批技能服务 |

既有 evidence 另记录 main 的 Desktop MCP materialization：`codex-app-tools` 会改写 `.mcp.json` 并触发 config reload；其 installed 文件可能包含 Desktop runtime 值，不应视作可直接移植的原始 MCP 配置。本文仅输出服务器 ID 与字段名称，没有输出 env、headers、密钥、token 或 raw credentials。

### 2.2 实测本地包路径

共同绝对根：`/Users/hikaru/.codex/plugins/cache/`。下表每个相对版本目录的入口都是 `/.codex-plugin/plugin.json`；技能数为该版本实际文件计数，非可运行能力计数、非 installed 计数。

| Cache 相对版本目录 | SKILL.md 数 | 其他声明/观察 |
| --- | ---: | --- |
| `created-by-me-remote/dev-6a8e4aa3c8388191abc825e07413b659/1.0.0` | 0 | apps key 与 dev ID 同名 |
| `openai-bundled/browser/26.930.61225` | 0 | 声明 skills，但当前 skills 目录为空；有 hooks |
| `openai-bundled/code-review/26.930.61225` | 0 | mcpServers 文件，server `code-review` |
| `openai-bundled/codex-app-tools/0.1.5` | 0 | mcpServers 文件，server `codex_app` |
| `openai-bundled/unified-computer-use/26.930.61225` | 0 | mcpServers 文件，server `cua_repl`；有 hooks |
| `openai-bundled/visualize/1.0.46` | 1 | `skills/visualize/SKILL.md`；bundledContentVariant=`live-disabled` |
| `openai-curated-remote/app-69ef18c674308191a2f952431f91ea61/1.0.0` | 0 | apps key 同名 |
| `openai-curated-remote/data-analytics/1.0.11` | 20 | skills；`extensions.com.openai.dependencies` |
| `openai-curated-remote/github/0.1.12-5f7cd798dc99` | 0 | apps `github` |
| `openai-curated-remote/openai-templates/0.1.1` | 20 | skills；apps `default_templates` |
| `openai-curated-remote/plugin-management/0.1.0` | 1 | skills；apps `plugin_management` |
| `openai-curated-remote/sites/0.1.75` | 4 | skills；apps `sites`；有 integrity.json，未验证其规范 |
| `openai-curated-remote/work-pets/0.1.6` | 3 | skills；apps `chatgpt_pets` |
| `openai-curated-remote/write-like-me/0.1.1` | 1 | skills；apps `write_like_me` |
| `openai-primary-runtime/documents/26.1005.12141` | 1 | skills |
| `openai-primary-runtime/pdf/26.1005.12141` | 1 | `skills/pdf/SKILL.md` |
| `openai-primary-runtime/presentations/26.1005.12141` | 1 | skills |
| `openai-primary-runtime/spreadsheets/26.1005.12141` | 2 | skills；apps `connected_documents` |
| `openai-primary-runtime/template-creator/26.1005.12141` | 1 | skills |

包中的 version 实测与目录名一致；这只是快照证据，不能用版本字符串推导 semver、installed 或 latest。

可复核样本（完整路径为共同根 + 下列相对路径）：

| 相对文件路径 | SHA256 |
| --- | --- |
| `openai-bundled/visualize/1.0.46/.codex-plugin/plugin.json` | `349c5e0c38ce48437b7c844df8d50145d9cf28677f0028b6840099e413d22375` |
| `openai-curated-remote/data-analytics/1.0.11/.codex-plugin/plugin.json` | `100ee5a9e1387d81276cb1fa40e7e4ebda94f0750cb99465545c540da16d43c4` |
| `openai-primary-runtime/pdf/26.1005.12141/.codex-plugin/plugin.json` | `20f9a6e25c1c28b3856342fce83dcefd0c003eb9b49c5a995898558c1c540581` |
| `openai-bundled/visualize/1.0.46/skills/visualize/SKILL.md` | `37a648f231b3716956e5b534bff1eef36195b5f7261ccd78e913a9ad5c961f0f` |
| `openai-primary-runtime/pdf/26.1005.12141/skills/pdf/SKILL.md` | `a6e20f4ae66364a06d577dd51e0ce66ee2ef955be129cbe195e0a27f403daaf8` |
| `openai-curated-remote/data-analytics/1.0.11/skills/build-report/SKILL.md` | `a464dc6bcda8bf5659ba951a9ccba52020cf9c5d6f5f40e05d0e6a42149b51cf` |

## 3. 已观察 schema 与 Harness 支持子集

### 3.1 插件 manifest

观察到的 JSON shape；括号内类型来自本次实际文件，不代表其它类型必然合法或非法：

| 字段 | 本地观察 | 首批 Harness 行为 |
| --- | --- | --- |
| `name` | string，19 个入口均存在 | 必需非空；作为包声明名，不作为全局唯一 ID |
| `version` | string，含 `0.1.12-5f7cd798dc99`、`26.1005.12141` | 必需非空；不强制 semver，不推导升级顺序 |
| `description` | string | 可选展示文本；不执行 |
| `skills` | 观察到 string `./skills/` | 首批只接受这个目录约定；其它类型/布局 UnsupportedSchema |
| `interface.displayName/shortDescription/longDescription/developerName/category` | string | 可选展示字段；缺省回退 name/description |
| `interface.defaultPrompt` | string[]、null 或缺省 | string[] 仅作为明确用户选择的起始 prompt 建议；不自动注入 system |
| `interface.capabilities/keywords` | string[] | 展示声明，不作实际能力判定 |
| `interface.logo/logoDark/composerIcon/screenshots/brandColor/*URL` | 路径/展示字段存在 | 首批可以不支持；不能自动访问外网或读取未选择文件 |
| `author/homepage/repository/license` | author object（name/email/url 等）、其他元数据 | 可选；展示不得改变来源信任 |
| `mcpServers` | string `./.mcp.json` | 只记 DeclaredUnsupported(Mcp)，不读或启动其配置 |
| `apps` | string `./.app.json` | 只记 DeclaredUnsupported(Apps)，不访问账号或授权数据 |
| `hooks` | inline object；Stop/Interrupt/SubagentStop 的 mcp_tool 条目 | Unsupported，不执行事件命令/工具 |
| `extensions` | data-analytics 有 `com.openai.dependencies[]`，含 name/marketplace/isRequired/categories | Unsupported；不自动安装依赖 |
| `bundledContentVariant` | visualize 为 `live-disabled` | 保留诊断标记；行为 Unknown，不能照搬启停逻辑 |
| 其它字段 | 没有完整规范证据 | 忽略执行含义，记录 unsupported 字段名；不声称全兼容 |

最小包输入示例（**Harness 支持子集示例，非当前已安装插件**）：

```text
selected-package/
  .codex-plugin/plugin.json
  skills/
    explain-change/
      SKILL.md
```

```json
{
  "name": "local-instructions",
  "version": "1",
  "description": "Local instruction package",
  "skills": "./skills/",
  "interface": {
    "displayName": "Local Instructions",
    "defaultPrompt": ["Explain this change"]
  }
}
```

包没有 skills、声明目录不存在、目录为空，必须分别报告 NoSupportedAssets / InvalidPackage / NoSupportedAssets；browser 样本证明有声明不等于有文件。只支持 MCP/Apps 的包不能返回“技能安装成功”。混合包可导入真实技能，但详情必须保留未支持资产，不显示“所有能力已连接”。

### 3.2 SKILL.md 路径与内容

实测 56 个文件均有 YAML front matter 的 `name`、`description`；本次字段名检查没有证明任何额外 key 的消费者。三个复核样本的 front matter 都为第 1–4 行，随后为 Markdown body；name 既有裸 scalar（visualize/build-report）也有双引号 scalar（pdf）。

```markdown
---
name: explain-change
description: "Explain a supplied change in plain language."
---
Use the supplied evidence. List the change and its effects.
```

有限读取协议：

- 输入选择 **packageRoot**；读取 `.codex-plugin/plugin.json`，随后枚举 `skills/` 下的直接子目录，仅读取 `<child>/SKILL.md`。本机实际路径符合该布局，不默认递归扫描任意深度。
- 可另提供明确的 **StandaloneSkillDirectory** 输入，根文件为 `SKILL.md`；这是 Harness 自定义导入方式，不冒充完整 Codex package。其版本/包名由独立导入元数据给出，来源标签为用户本地导入。
- UTF-8 文本；front matter 必须由独立 `---` 行开闭，name/description 为非空 string，正文非空。采用编译依赖的 YAML parser 或明确支持该 scalar 子集的 parser，不能把去引号/按冒号切分宣称 YAML 全兼容。其它 YAML 类型、tag、alias 等首批拒绝；未知 key 不产生执行行为。
- skill identity 采用 Harness plugin ID + 相对 skill 目录；front matter name 是声明名/显示名。同包重复 name 应显式报冲突，跨包同名保留归属，不能全局按名称覆盖。
- 路径必须规范化并约束在选定 root；拒绝 `..`、绝对引用、symlink 逃逸；首批可以直接拒绝输入中的 symlink。路径约束与 name/version 的显示含义分离，不用 manifest 名拼接未校验磁盘路径。
- 明确制定大小/数量上限并返回 typed 错误（建议 manifest 256 KiB、单技能 1 MiB、256 个技能、技能合计 8 MiB）。这些是 Harness 设计值，不是测得的 Codex 限制。
- 导入保存实际 UTF-8 内容、relativePath、SHA256 和规范化 metadata。安装后不依赖原目录继续存在；启停读取受管 snapshot。原目录变化不静默替换已安装版本，需明确重新导入/更新。
- `references/`、`scripts/`、`assets/`、templates 等文件确实随包存在，但首批不自动展开或执行 Markdown 引用。未接资源读取/运行服务时标记不支持；prompt 中的路径不能让服务跨越 root 读文件。

`agents/openai.yaml` 实测存在于 visualize/pdf 样本，含 `interface.display_name/short_description/default_prompt`，pdf 另有 icon/brand 字段。它是额外展示声明；首批可明确 Unsupported，不与 SKILL.md front matter 或 plugin.interface 的优先级混为已知规范。

### 3.3 MCP/Apps 仅格式证据

本次调查只对白名单结构作读证据；生产 importer 不需要读取这些文件。

- `.app.json` 顶层 `apps` 为 object；entry keys 见包表，value 含 `id`、可选 `required`。**plugin 名不等于 app ID**：openai-templates → default_templates，spreadsheets → connected_documents。entry 不证明 tool inventory、已授权或已连接。
- `.mcp.json` 顶层 `mcpServers` 为 object；code-review、codex_app、cua_repl 分别为 server keys。已观察字段名包括 command、args、cwd、enabled、env_vars、env、startup_timeout_sec、tool_timeout_sec、tools、enabled_tools、omit_tools_from、default_tools_approval_mode。仅字段名证据，不交付 raw 值、不启动进程。
- hooks 的 mcp_tool server/tool 引用不自动注册 tool；extensions 的 dependency 引用不自动授予能力。Settings/config 工具、连接 Apps、MCP schema/execute 的 Harness 消费者均未交付，维持 Unsupported。

## 4. ID、source 与 installed 归属

### 4.1 Codex 身份证据

`/Users/hikaru/.codex/config.toml` 的 plugins 表实测有以下 10 个 key，均只有本次确认的 `enabled=true`：

```text
codex-app-tools@openai-bundled
visualize@openai-bundled
browser@openai-bundled
unified-computer-use@openai-bundled
code-review@openai-bundled
documents@openai-primary-runtime
pdf@openai-primary-runtime
spreadsheets@openai-primary-runtime
presentations@openai-primary-runtime
template-creator@openai-primary-runtime
```

这证明这些本地 config identity 使用 `pluginName@marketplaceName`。不能为全部远程来源臆造相同规则；ASAR 另外使用 remotePluginId/shareContext.remotePluginId，详情与操作有单独 marketplace reference。main `Hy` 的去重顺序为 remotePluginId → shareContext.remotePluginId → id。

本次读取的 marketplace manifest：

| 完整路径 | manifest name / entries | SHA256 |
| --- | --- | --- |
| `/Users/hikaru/.codex/.tmp/bundled-marketplaces/openai-bundled/.agents/plugins/marketplace.json` | openai-bundled / 7 | `0eefbf7db86434780f797f194f80147e85a75066f97afa86a464beb89dc28d76` |
| `/Users/hikaru/.cache/codex-runtimes/codex-primary-runtime/plugins/openai-primary-runtime/.agents/plugins/marketplace.json` | openai-primary-runtime / 5 | `1cb081867ce75ef757ab9ef1d9b36a246f5d81632aac9dcf00ad27def09eb18f` |
| `/Users/hikaru/.codex/.tmp/plugins/.agents/plugins/marketplace.json` | openai-curated / 65 | `62ed7a1c906a7e486a84ee3f2f7eba048d075e360e355072e1b0823a128f432e` |

本地 entries 观察到 `name`、`source:{source:local,path:./plugins/<name>}`、`policy.installation=AVAILABLE`、`policy.authentication=ON_INSTALL|ON_USE`、category。第三个 manifest 另存在 url/git-subdir 来源；首批不获取这些远程包。marketplace entry 的 `source.source` 与 RPC summary 的 `source.type` 属于不同层，不能混用。

**特别不能归并**：checkout 的 marketplace name 是 openai-curated，远程 cache 路径标签为 openai-curated-remote；当前证据未证明任意条目的 installed/source 对应关系。路径标签只作调查 provenance，不能自动给 Harness 导入包贴官方授权或远程安装身份。

### 4.2 Harness 自有身份（建议）

- `HarnessPluginId` 在成功导入时生成并持久化；manifest.name/version 为声明字段。重复导入行为明确为新安装或指定 ID 的替换，不能凭同名隐式覆盖。
- `PluginSource.LocalDirectory` 保存选定来源的规范化路径及导入指纹；StandaloneSkillDirectory 为另一个 typed source。若用户指定一个 Codex cache 版本目录作为输入，只能标为“从该本地目录导入”，不能继承 Codex installed/enabled/account 身份。
- installed 真相来自 **Harness inventory + 受管 snapshot**。scope 带 host/repository 服务归属，UI 请求带 generation；跨 host 不复用本机记录。未提供本地 backend 的 host 明确 Unsupported。
- installed/enabled/consumable 分开：Installed 证明记录和资产存在；Enabled 是 Harness 配置；Consumable 还要求资产可验证及 instructions 服务可用。缺失文件或坏记录返回 Failure/BrokenInstall，不合成为成功空列表。

## 5. 安装、卸载、启停的持久语义

### 5.1 已确认的 Codex 边界

安装/卸载经过 app-server `plugin/install` / `plugin/uninstall`；本地 enable leaf 是 config/batchWrite → reloadUserConfig → refresh，remote 分支走账号服务。持久文件格式、删除是否保留旧 cache、版本选择事务、remote 与本地状态 reconcile 的全规则 **Unknown**。本次没有调用这些 RPC，也没有执行 Codex CLI/AppServer 来验证状态。

`/Users/hikaru/.codex/plugins` 直接观察到 cache、.remote-plugin-install-staging、.plugin-appserver；没有在此层观察到 installed_plugins.json。**这不证明该文件全局不存在，更不能自行创造或编辑它。** `.plugin-appserver` 内的 app-server 安装不属于 Harness 资产。

### 5.2 首批可闭环的 Harness 语义（设计，不是 Codex 实测行为）

| 操作 | 必须发生的真实持久/消费者变化 | 成功条件 |
| --- | --- | --- |
| Import/Install local instructions | 校验所选包 → 在 Harness 自有目录 staging 保存规范化 metadata 与 SKILL 内容 → 发布受管 snapshot → 原子更新 inventory revision | 返回可读回的 Harness ID、版本和技能；重启可恢复；不是 UI 先行插卡 |
| listInstalled/readDetail | 从自有 inventory 读取，校验受管文件与 revision，展示 supported/unsupported assets | 无记录才是真实 Empty；损坏或 I/O 失败不是 Empty |
| Enable/Disable | 原子更新自有 enabled/revision；后续 AgentInstructionsService 读取/快照刷新按 revision 生效 | 下一次真实请求含/不含该技能文本；不只改 UI boolean |
| Skill selection | 用户显式选定技能；读取其受管文本并形成 typed Instructions 输入 | 最终 system 包含确定顺序、去重后的 selected+enabled 内容；默认不注入所有包正文 |
| Uninstall | 移除自有 inventory 引用，使未来请求不可选；只清理由该 ID 拥有的受管文件 | 源目录与 Codex 状态完整保留；清理失败显式报告，不删共享目录 |
| Replace imported version | 明确目标 Harness ID/revision，新 snapshot 校验后提交；旧 snapshot 按自有所有权回收 | 失败仍能读旧版本；不从目录名臆测 latest |

实现必须处理 inventory 与 filesystem 的提交/恢复边界：临时 staging 不出现在 installed 列表，更新失败不丢旧记录；若提交后清理失败，可明确返回已移除记录但待清理的受管残留。具体自有文件名/schemaVersion 由 backend 固定并记录，不能使用 Codex 私有文件名假装兼容。

请求期以稳定 revision 获取内容。disable/uninstall 保证**后续请求**不消费；已发给模型的内容无法撤回，进行中的请求取消语义由主线程定义，不能声称已清除当前请求 system。相对资源尚未支持时，纯文本消费只承诺技能指令进入请求，不承诺所有外部脚本/产品专用步骤能够执行。

## 6. 绝不能接入的 Codex 私有状态

本次授权只读调查的文件不是 backend 的长期数据源。后续默认 importer **不能扫描、推导或读写 Codex 私有 installed 状态**。

| 边界 | 后续 Harness 行为 |
| --- | --- |
| Codex `config.toml`，含 plugins/marketplaces/mcp_servers | 不读作 Harness inventory，不写 enabled、不全量解析或复制凭据；本次 enabled 仅为调查证据 |
| `.codex-global-state.json`、state SQLite/WAL/SHM、rollouts、账号/OAuth/credential stores | 不读、不迁移、不修改；不用于推断插件身份 |
| `.remote-plugin-install-staging`、`.plugin-appserver`、sync lock/marker、materialization-key | 不读作安装状态、不干预、不调用内置管理程序 |
| `.tmp` marketplace checkout、bundled-marketplaces、runtime cache、Desktop userData/executor-plugins | 不自动监控或同步；只保留本文来源证据 |
| Codex `plugins/cache` | 不自动枚举成已安装列表；仅在用户明确选择某个具体 packageRoot 时读取 manifest + skills 白名单并导入到 Harness |
| 被选包 `.mcp.json/.app.json`、hooks/credential/env 等 | 首批不读取配置内容、不复制、不执行；manifest 声明只映射 Unsupported |
| Codex packageRoot 原文件 | 只读导入，enable/uninstall/update 全部仅作用 Harness 自有状态，不回写源目录 |
| Codex hosted HTTP 或私有 RPC | 不模拟、不直接移植；若未来公开服务经明确授权接入，另设 adapter/capability，不能用本地推测代替 |

生产读集限定为所选 manifest、所选 skills 下的 SKILL.md（或 standalone 根 SKILL.md）；不要把系统全目录扫描、复制整个 package、加载 scripts/agents 当成必要步骤。导入日志只记录 ID、相对路径、hash、支持状态和 typed 错误，不输出未知 JSON raw 值或 MCP 配置。

## 7. Unknown 与明确 Unsupported

- Codex 全 plugin schema：skills/mcpServers/apps 是否支持 array/object、glob、inline，commands/agents/prompts 的全规则，未知；只交付观察到的字符串路径子集。
- 全 SKILL front matter/YAML 规范、name 限制与元数据优先级，未知；Harness 必须说明 parser 子集及拒绝错误。
- installed 私有存储位置/schema、安装完成与 config/cache 的精确事务关系、卸载 cache 保留策略，未知。
- marketplace cache 路径标签到 remote/account/plugin source 的完整映射，未知。远程 ID 不从目录生成。
- bundledContentVariant、integrity.json 的完整校验和执行语义，未知；无签名验证就不显示“官方已验证”。
- 本机 per-skill enable 具体消费 leaf 未闭合；已见 hosted skills enable/disable 不给 Harness 借用。首批若做技能启停，必须是自有设置。
- 自定义 Settings、MCP、Apps、hooks、依赖授权/执行：首批 Unsupported，不能用 mock Connected、fake tool schema 或成功空回调填补。
- AgentInstructionsService/Key 的具体 API 和存储路径由主线程交付；本文只规定文本、身份、revision 和启停的契约，不臆造该服务已存在。

## 8. 后续实施与验收项

建议顺序：实现有限 parser 与 typed errors → Harness 自有 inventory/受管 snapshot/启停卸载 → 只读 repository 接 UI → 接主线程 AgentInstructionsService/Key → 验收真实请求。各层是编译期服务与 DTO；包只提供数据，不参与动态 UI/Fiber 注册。

- [ ] 导入真实本地 directory，UI installed 条目可追溯到自有持久记录和文件；restart 后一致。成功空列表仅来自成功读取无记录。
- [ ] 支持上述最小 manifest + `skills/<dir>/SKILL.md`，name/description/body 解析正确；语法错、空目录、缺文件、类型不符、超限、路径逃逸返回具体 typed 错误。
- [ ] 同名跨包不覆盖；同包重名明确冲突；来源、Harness ID、manifest name/version、skill relativePath 分开保存。
- [ ] 混合包如 spreadsheets 可导入技能，但 Apps 仍为 Unsupported；纯 Apps/MCP 包不造成功安装的技能。
- [ ] 不自动导入 56 个技能或继承 10 个 Codex enabled；用户选择 local source，installed 归属是 Harness。
- [ ] 在主线程的真实 consumer 验收中，选中且 enabled 的受管技能确实进入最终 GenerateOptions.system，并体现在持久 RequestHeader；不存在依赖 hook 替换 immutable options 的实现。
- [ ] disable、uninstall 后下一次请求不含原技能；错误、被移除选择及 revision 切换有明确语义；重复请求不叠加重复 system 文本。
- [ ] defaultPrompt 只有明确选择后进入真实 prompt 操作；不自动替用户提交，不替代 skill instructions。
- [ ] 导入/提交失败不破坏旧 inventory；卸载只删除 Harness 拥有的 snapshot；原目录与 Codex config/cache/私有状态字节不变。
- [ ] 不执行 MCP/Apps/hooks/scripts，不读取其 credentials；未支持能力明确，不造 Connected 或 tools 可用状态。
- [ ] 非本地 host、未暴露 backend 的连接使用 Unsupported/Unavailable；不读 UI 端本机目录假装远程安装状态。

以上是后续实施验收，**本次没有运行这些测试**。本次仅做只读包/ASAR 结构、字段、hash、偏移核对与本文落盘；主线程最新消费者确认作为后续集成依据。
