# 本地插件登记与 Skills 消费

日期：2026-10-07。实施模型：`SHUAIAPI/gpt-6.1-sol`，主线程负责生产接线、审查和串行隔离验证。插件管理与整体复刻仍在推进。

## 本批行为

- 用户选择本地源目录，读取 `.codex-plugin/plugin.json` 与明确支持布局的 `SKILL.md`，保存 metadata 和技能原文快照到 Harness home 下的 `client-plugins.json`。添加默认停用。
- 列表、详情、启用/停用、移除登记都来自同一个真实服务。移除只删除 Harness 登记，源目录保留。UI 不把此流程称作完整包安装/卸载。
- 启用的 Skills 由静态 typed `AgentInstructionsService` 接入真实 AgentLoop；每次请求构造之前读取快照，合并原有 system，并使用相同文本写入请求与 RequestHeader。
- 同名不同来源保持独立 ID；重复来源拒绝。后续源文件修改不会静默改变已经登记的快照。
- Apps/MCP/hooks/scripts/技能附件没有消费者，明确未支持；不执行包代码、命令或授权。公开市场继续不在范围内。

## 接线与并发

- main 创建一个 `DesktopUserPlugins`，同时注入本机 Host Context 和 `DesktopInstalledPluginRepository`。`Context.provide` 自身注册生命周期 effect，Host 关闭时清理。
- HarnessApp 只向 adapter 提供已经验证的本机 Connection、完整 Host/account/generation scope 和明确 Local transport。连接换代/断开撤销旧 access；旧结果不渲染到新范围。
- controller 跨 scope 保留写入单飞；刷新不取消 mutation。失败不自动重发，保存成功后刷新失败仍保留 typed receipt。
- adapter 的 CAS revision 来自最后一次成功返回给页面的列表快照，防止列表读取与 revision 读取之间另一写入者改变文件后，旧 UI 获得新 token。
- 存储使用有界严格 UTF-8、路径/文件检查、文件字节 SHA revision、JVM mutex 与 OS file lock、原子替换；坏文档不覆盖。

## 验证

源码副本 `/tmp/harness-native-verification-20261007`，构建目录 `/tmp/harness-native-verification-build-20261007`；各模块串行运行，单独保存 JUnit XML。测试只使用临时目录和受控模型 adapter，不发网络模型请求或改用户插件目录。

- AgentInstructionsContractTest：7/7，无失败或跳过。覆盖请求/header 同源、无贡献保持原请求、独立贡献、重试/后续请求重新读取且不累积、失败、主动取消及悬挂读取取消。初跑测试泛型推断错误已修正后重新通过。报告：`/tmp/harness-native-verification-evidence-20261007/plugins-agent-instructions/jvm`。
- DesktopUserPluginsTest：17/17；持久存储、manifest/Skills 快照、坏文件与路径拒绝、CAS 和并发行为。报告：`/tmp/harness-native-verification-evidence-20261007/plugins-service/jvm`。
- DesktopInstalledPluginRepositoryTest：9/9；本机 transport/connection/Host/scope 校验、列表快照 revision 与过期访问拒绝。报告：`/tmp/harness-native-verification-evidence-20261007/plugins-adapter/jvm`。
- InstalledPluginsControllerTest 与 PluginManagementControllerTest：20/20（11+9）；读取/筛选/详情、管理单飞、scope 切换、取消、保存 receipt 与刷新失败。报告：`/tmp/harness-native-verification-evidence-20261007/plugins-controller/jvm`。
- PluginRoutingInteractionTest：3/3；实际导航与详情路由。报告：`/tmp/harness-native-verification-evidence-20261007/plugins-routing/jvm`。
- PluginManagementInteractionTest：3/3；空列表、路径校验、默认停用、登记→详情→启用→移除、详情滚动、源文件保留、只读入口与无登录 Relay 禁止管理。报告：`/tmp/harness-native-verification-evidence-20261007/plugins-management-ui/jvm`。
- 本批共59项（7+17+9+20+3+3），均无失败、错误或跳过，jvm-app main/test 源码编译通过。初次管理测试参数编译错误、重复标题唯一匹配和无登录 Relay binding 等测试假设已修正，最终3项重跑通过；不累计失败轮次，不把交互通过计为视觉验收。

## 剩余边界

这是本机技能文本登记，不代表完整 Codex 包生态兼容。包资产安装/升级/卸载、授权配置、MCP/Apps 消费、逐页视觉对照仍未完成。Cron 生产开关仍为 false；Worktree 管理、终端 PTY、默认子 Agent spawn 等其他保留范围继续按计划推进。


## 最新同步：2026-10-07 插件视觉与公共输入框

指定模型新增插件离屏截图和公共输入实现；主线程分析 ASAR 管理布局、审查绘制回归、修正测试语义边界并串行隔离验证。`HarnessTextField` 已改为独立标签/说明与最小 36dp 的 BasicTextField 编辑区域，字号增长时按内容扩展；保留现有调用参数、真实 editor testTag、禁用、错误、单/多行和密码转换，新增默认 `readOnly=false`。登记路径文字、标签和说明不再被 Material floating label 的整体 36dp 约束裁切。

- `HarnessTextFieldInteractionTest`：最终 **3/3**，其中绘制方法覆盖浅/深 × 280/480dp × fontScale 1/1.5 × normal/error/disabled/readOnly 共32种组合；与独立 Text 文字像素参照比较，验证空值不画假文字，以及真实输入/替换、禁用/只读、错误语义、多行与密码布局。测试几何用标签与说明之间的完整编辑区域；BasicTextField 可编辑语义是内部文字高度，截图不能额外裁掉 glyph 边缘。初轮编译和断言修正前的失败不计为通过数。
- desktop 定向回归最终 **24/24**：`PluginsScreenshotTest` 15、`PluginManagementInteractionTest` 3、`LocalProjectsInteractionTest` 4、`WorktreeCreationInteractionTest` 2；无失败、错误或跳过。项目与 Worktree 包含真实临时目录/Git 服务，截图数据仅测试 fixture。
- 最终 **64张 PNG**：浅/深 × 1440×900/600×800，包含列表、详情长文与 Skills/Apps 滚动、空/错误/不可用/加载、只读及登记/错误路径/移除弹窗。保存于 `captures/plugins-visual-2026-10-07/`，`manifest.json` 记录尺寸、SHA256 与测试边界。弹窗截图为含 scrim/背景的完整 page viewport，不是紧贴弹窗的 crop；旧 `dialog-crop-from-page-viewport` 文件未纳入最终证据。

源码快照与当前输入组件/测试/插件截图测试逐字节一致。命令使用相同隔离源码与 build-dir，先 jvm-app 后 ui-foundation 串行运行。JUnit 报告分别保存 `/tmp/harness-native-verification-evidence-20261007/plugins-visual-final/jvm`、`/tmp/harness-native-verification-evidence-20261007/text-field-final/jvm`。人工复核普通/紧凑登记弹窗和错误路径文字，以及列表、详情滚动与状态截图可读性；没有同尺寸真实原版管理页参考，所以不声明像素一致，也不替代全量 desktop/shared、Android/iOS 或实际窗口验收。前批59项插件功能测试仍是前批结果，不与本批失败/重跑叠加。

ASAR管理内容明确 `isSingleColumn: true`，市场截图不能用作管理页双列布局依据；详情 Apps/Skills 使用 row/line-clamp-1，见 `reverse-engineering/plugins-layout-supplement-2026-10-07.md` 与同名证据JSON。后续仍需逐页几何对齐及保留范围的真实服务补齐。Cron 生产开关继续为 false；本批完成不表示整体复刻完成。
