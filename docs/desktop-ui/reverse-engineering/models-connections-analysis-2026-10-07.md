# 模型 / Provider 与连接：当前证据及实施边界

日期：2026-10-07。本批先实现模型 / Provider，连接与常规系统动作仍未完成。

## app.asar

当前本机归档直接按 header offset 重新读取。补提取文件在 /tmp/harness-asar-settings-20261007，来源 /Applications/ChatGPT.app/Contents/Resources/app.asar。

| bundle | bytes | SHA256 | 已确认 |
| --- | ---: | --- | --- |
| configuration-panel-9d600f36950d.js | 49460 | 827f191f4d95a064ad7f538993f16c656e1dc84eb84a980c80362bb2f7931c54 | 原配置页有受控写入、配置文件定位、作用域/托管限制、模型能力与推理档位；不是云端 Environment configs |
| remote-connections-page-ad05e377e223.js | 647 | 73b07930e4da7182278463179babba4206eb4b32586954ac00828f12e7f2693e | 独立连接页入口 |
| remote-connections-settings-d2dabff157cf.js | 169928 | 68026dcc3268fdc3aca4c70286f3b28a1a2fc13836c9789e91fcceadb6417a66 | 本机、SSH、发现/手动添加、连接详情、加载/错误/重试；还混合账号设备连接，需要进一步按范围分类 |
| remote-connection-editor-draft-e4ebf3fbf6aa.js | 1980 | ca504f4dbc3cb57f249361288c3812c3a0e28f88fdae5d2701dfa1dd2ad532e2 | SSH 表单含 hostname/alias、port、identity、displayName，提交前 trim/校验 |

settings-configs-52e368d80ee8.js 是 cloud-v2-settings-configs / environmentConfigs，不是本地 Provider 页面，属于本轮排除范围，不能用它冒充模型页的数据接口。

现有 Models / Provider 路由属于 Harness 的基础配置适配；尚未找到可直接一一复刻的原版 Provider CRUD 页面或原图。本页复用已量测 Settings 框架与原配置页的表单/错误/作用域设计，呈现 Harness 实际服务，不宣称新增字段全部来自 Codex 或像素一致。

## Harness 的真实能力

- DesktopBundle 已编译安装 settings-file / credentials-local / llm / llm-koog-openai。
- OpenAiKoogPlugin 动态模式使用 llm-koog namespace，defaults.providers 为空；configurableProviders 提供真实 openai 配置入口。未配置不能画成已可用示例。
- LlmRuntime.listConfigurableProviders、listProviders、listModels 提供配置入口、已注册路由及实际已载入模型目录。已注册路由并不证明远端服务/API key 有效，更不证明新保存配置已完成重载。
- SettingsScope.describe 提供 revision、base/user/resolved；replace 用 expectedRevision 防覆盖，SettingsService 在文件写入成功后提交状态。编辑只能改本页拥有的字段，保留其他 provider、namespace、模型能力、modelOverrides 等用户配置。
- Provider 支持已安装 Chat Completions / Responses 协议、baseUrl、credential 引用、timeouts、模型目录与容量 fallback。新增 provider 采用已编译协议，不做动态 UI/JS 插件加载。
- CredentialsKey.describe/set 是实际凭据服务；DTO 与 settings.json 只保留名称和配置状态，API key 仅作为 UI password draft 与一次写入参数，不进入状态、错误或截图 fixture。
- 当前跨 Host 的 Connection 只有 Host/Session 接口，远程 Relay 禁止凭据写入。模型设置先通过 Desktop 注入的本机 adapter 接入；其他客户端使用明确 unavailable 状态，不伪造远程管理成功。

## 本批实施验收

真实列表/搜索、未配置入口、Provider 新建/编辑/删除配置、模型目录、密码型凭据编辑、保存后重读；磁盘失败、revision 冲突、读失败、取消与重复提交均有明确状态。配置提交成功但刷新失败必须提示已保存/需刷新，不能当作完全未写入后自动重试。

尚待后续：原版连接页面更完整组件/动作分析、SSH adapter、General 系统能力、Composer 模型选择接入、远程 Host 模型管理、远端模型发现/网络探测、原图像素对照。
