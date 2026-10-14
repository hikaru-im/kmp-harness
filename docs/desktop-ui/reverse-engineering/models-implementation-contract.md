# 模型 / Provider 页面实施契约

先读 models-connections-analysis-2026-10-07.md，以及 apps/shared/src/im/hikaru/harness/client/settings/ModelSettings.kt / ModelSettingsRepository.kt。模型与 repository 已提供；主线程继续写 Desktop adapter / glue / tests，页面并行实现。

## 独立写集

仅新增 apps/shared/src/im/hikaru/harness/client/page/SettingsModelsPage.kt。保留全部既有修改。先 CodeGraph，若无新符号再直接读取。不得修改 model/repository、其他页面、公共基础库、HarnessApp/main、模块清单、测试或截图；不运行 Amper。

页面 API：

    @Composable public fun SettingsModelsPage(
      state: ModelSettingsState,
      onRefresh: () -> Unit,
      onSave: (ProviderConfiguration, Long) -> Unit,
      onRemove: (String, Long) -> Unit,
      onSetCredential: (String, String) -> Unit,
    )

## 页面

- SettingsFormFrame("模型与 Provider", "settings-models-title")，728主列/顶部73；复用 SettingsFormCard/Row/Section、HarnessSearchField、Button、Menu、Dialog。加载、真实空状态、不可用、读/写错误、刷新入口。
- 本地 search draft 筛选 provider ID/name/模型。state.snapshot.providers 是唯一数据源，不追加示例。每个 card 展示 name/id、配置/凭据状态、已注册路由（不能叫远端连接成功）、实际模型数量；提供编辑、查看模型、配置凭据、删除配置（canRemove）；新建依 snapshot.canAddProvider / state.canEdit。
- 查看模型展示 ProviderModel 的真实 id/name/description/context/maxTokens/reasoningEfforts；未注册路由/目录失败使用明确空/错误，不能编造实际可用模型。

## Provider 表单

- 新增/编辑弹层，长表单可滚动；draft 在本地；BasicTextField 真实高度，不用 SettingsFormInput 的逐字段提交来保存整个表单。
- fields：id（编辑禁改）、displayName、api（snapshot.protocols 的 typed Select）、baseUrl（空=默认）、credentialName（名称，空=无）、request/connect/socket timeout、defaultContextWindow/defaultMaxTokens、custom model IDs（逐行一个 ID，空=沿用已安装目录）。用 ProviderConfiguration.validate 与数字解析完成提交校验，错误保留草稿。
- 只有明确填写字段再保存。不读取/编辑 raw JSON。未呈现的原 Host 模型能力与 modelOverrides 由主线程 adapter 保留。
- opening snapshot.revision 是 editorRevision；onSave(candidate, editorRevision)，不能在保存时偷偷升级为新 revision。冲突时提供刷新与重新载入表单，保留旧草稿直到用户重载/取消；新建避免与已配置 ID 重复。
- 记录提交前 state.saveRevision 与 candidate。只有 saveRevision 增长、completedOperation="provider:"+id、!saving、!refreshRequired 且 confirmedProviderConfiguration == candidate 才关闭并显示已保存。confirmedProviderConfiguration 由 owner 在实际 write 返回后发布；空字段继承 Host base 后，重读 configuration 可能含非空默认值，所以页面展示真实重读值，不能按 raw candidate 与 resolved snapshot 直接比较。失败/刷新失败不能假成功关闭。
- 删除用确认弹层，onRemove(id, openingRevision)，成功只按 completedOperation="remove:"+id 与 saveRevision 的确认消费；openai 声明入口可能保留为未配置，不要求整张卡消失。

## 凭据弹层

- 仅配置引用/名称，不展示现有值；密码字段 BasicTextField + PasswordVisualTransformation。name 用 configuration.credentialName 或显示可编辑名称；只在 credentialWritable 和 state.canEdit 时提交。
- API key 留在 dialog local draft，不写日志、semantics 描述、测试 fixture 或 settings 对象；按钮回调 onSetCredential(name, value)。关闭/成功按 saveRevision 增长、completedOperation="credential:"+name 且 !refreshRequired 确认。失败保留供重试，取消清空；不能让全局刷新自动再次写 key。
- saving 时关闭、重复保存和其他修改禁用。remote unavailable 下不能出现可工作的写入动作。

稳定 tags：models-refresh、models-search、models-new-provider、models-provider-{id}、models-edit-{id}、models-view-{id}、models-credential-{id}、models-remove-{id}、provider-editor-id/name/base-url/api/credential/models/request-timeout/connect-timeout/socket-timeout/context/max-tokens、provider-editor-save、provider-editor-reload、provider-editor-error、provider-delete-confirm、provider-delete-refresh、credential-editor-name/value/save/error。

删除确认使用 openingRevision。冲突刷新后 revision 变化时禁用旧窗口的确认按钮，提示取消并重新打开，以检查最新配置；不自动升级旧确认的 revision。snapshot.scopeLabel 明确说明页面配置归属，Desktop 为本机 Host。

## 交付

列出改动文件与实际支持/缺口。不声称原版 Provider 页面像素通过；不扩大到云环境、团队、计费或自动审查。主线程负责运行构建、点击/数据一致性测试与普通/紧凑浅深截图。
