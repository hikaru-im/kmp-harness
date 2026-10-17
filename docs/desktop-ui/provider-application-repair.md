# Provider 配置应用与恢复

## 原有链路

`SettingsModelsPage → ModelSettingsRepository → DesktopModelSettingsBackend → DesktopProviderSettings → SettingsScope.replace → settings.watch → KoogLlmReloadCoordinator → executor → LlmRuntime 路由`

读取时，DesktopProviderSettings 分别取得持久配置、凭据描述、已注册路由和模型目录，再映射到 shared UI。保存配置和注册路由是两个结果；保存成功不保证实例创建成功。

原有问题：界面允许缺少引用，读取状态却默认查询 OPENAI_API_KEY；executor 要求显式引用。加载异常只留在 coordinator 内，UI 看不到失败原因。凭据窗口可改名称但不更新 Provider 关联。首次启动加载失败还会销毁监听器，阻止后续配置修复。

## 设计边界

保留设置存储、版本冲突检查、Repository 写后读取、原子路由替换、回滚以及旧 generation 的租约释放机制。不重写模型协议或连接框架，不自动绑定已有密钥，不增加远程模型发现。

增加 provider-neutral 的 LlmSettingsApplication 接口，按设置 namespace 注册到 LlmRuntime；Koog 提供受控错误类别、应用中状态及显式重试。应用以 namespace 为原子单位，可能包含多个 Provider，因此整体更新失败时不能声称其中某个新配置已生效。运行时注册随插件生命周期释放。

Desktop bundle 负责将运行状态和凭据检查转成中文诊断；app 仅依赖 bundle/shared 类型，不导入 provider 实现。刷新只读状态，重试应用才创建新 generation。保存凭据后等待应用完成，再读回状态。

## 删除与替换

- 删除读取凭据状态时对 OPENAI_API_KEY 的隐式回退。
- 删除凭据引用可留空的保存规则和提示；旧配置仍可读取修复。
- 删除凭据弹窗的独立改名功能：引用在 Provider 编辑页修改，弹窗只修改绑定的密钥。
- 删除仅保存 Throwable 的 reloadFailure，改为不含异常文本或密钥的应用状态。
- 替换仅用 active 判断配置生效的展示，区分未生效、正在应用、已生效和沿用旧配置。
- 替换首次客户端加载失败就退出的行为，保留监听和重试能力。取消仍传播。

## 恢复与验证

缺少引用的旧配置显示未绑定凭据，且不能伪装凭据可用。显式保存引用后可配置密钥；修复后重新应用并读取模型。自定义服务地址提示检查模型 ID，留空仍沿用内置目录。

回归覆盖：旧配置启动修复、凭据缺失、保存凭据恢复、自定义模型目录、更新失败保留旧路由、版本冲突、敏感值不进入状态、引用校验与设置页交互。测试使用隔离存储和合成凭据，不修改个人配置，不调用远端模型。

2026-10-17 验证：JVM 定向测试共 45 项通过（DesktopModelSettingsBackendTest 8、ModelSettingsInteractionTest 10、ModelSettingsRepositoryTest 8、KoogLlmDynamicSettingsTest 4、KoogLlmPluginTest 15）。`git diff --check` 通过。未验证 Android/iOS 或真实服务端认证。
