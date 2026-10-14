# Worktrees「工作树根目录」设置：本批实施边界（2026-10-11）

## 1. 本批依据（ASAR 只读）

本批只读抽取当前安装包的 Worktrees 设置页资产；证据抄录见
`verification/worktree-root-setting-2026-10-11/entry-ay1/logs/asar-evidence.txt`。

| 编号 | asset | 观察到的内容 |
| --- | --- | --- |
| R01 | `webview/assets/worktrees-settings-page-ad5cbb37d745.js` | 页首是「General」小节里的 `Worktree root` 行：当前值取自 `re(kt,r)?.worktreesRoot`；提交走 `Nt(i, r, De.worktreeRoot, e)`（按 host 的设置变更）；成功后 `settings.git.worktreeRoot.save.success` toast，失败走 `.save.error` toast |
| R02 | 同上 | 字段是受控输入：草稿 `u`，仅在 `u != null && u !== saved` 时才允许提交；提交逻辑 `if (!changed || pending) return; const e = draft.trim(); (e === '' || isSafePath(e) && fs.isAbsolute(e)) && mutate(e)` —— 即**空串合法**（回到默认位置），否则必须是**绝对路径**；面板是 `wrap`/`win32` 二选一由 `codexHome` 决定 |
| R03 | 同上 | 提交还绑定 `CmdOrCtrl+S`（`enabled = changed && !pending`），并在输入失焦时触发同一提交；`ariaLabel` 与 `label` 同为 `settings.git.worktreeRoot.*` |
| R04 | `webview/assets/zh-CN-b05cfd55c534.js` | `settings.git.worktreeRoot.label`「工作树根目录」、`.description`「ChatGPT 创建托管工作树的目录。留空则使用默认位置」、`.save.success`「已保存工作树根目录」、`.save.error`「未成功保存工作树根目录」；同页还有「常规」「环境」小节 |

## 2. 已观察到与未观察到的语义

- **已观察**：工作树根目录是**按 host 持久化**的字符串设置；空串 = 默认位置；非空必须是绝对路径；改动仅在「有改动且未在提交」时可保存；保存有成功/失败反馈；同页把设置分成「常规」与「环境」两组。
- **未观察到**：`worktreesRoot` 的存储位置与默认值来源（`re(kt,r)` 是 host 设置查询，未看到其文档）；`isSafePath` 的全部规则；「环境」小节内容；自动删除/保留数量/上游刷新三项的**服务端**语义（它们与 root 同页但属于其它行，本批不实现）。原版**未显示**非法输入的报错文案（失焦时静默不提交），本仓改为显式行内错误属于已记录差异。

因此本批只实现**可观察的那一层**：一个持久化的「工作树根目录」字段（空 = 默认位置；非空须绝对路径），并把该值真正用起来——新工作树的默认父目录取自根目录。

## 3. Harness 实施（本批生产改动）

| 层 | 改动 |
| --- | --- |
| `settings/WorktreeSettings.kt`（新增，shared） | `@Serializable WorktreeSettings(root)`、`configuredRoot`、纯函数 `worktreeRootValue`（trim + 反斜杠归一化）、`worktreeRootError`（空合法；绝对路径合法；含 NUL 或相对路径报错）、`worktreeDefaultPath(root, branch)`、`branchSlug(branch)` |
| `settings/WorktreeSettingsRepository.kt`（新增，shared） | `WorktreeSettingsStore`/`MemoryWorktreeSettingsStore`/`WorktreeSettingsState`/`WorktreeSettingsRepository`，语义镜像 `GeneralSettingsRepository`：`load`/`save`/`retry`、失败写不替换在用值、`canEdit` 只在可写且已加载时为真 |
| `settings/DesktopWorktreeSettingsStore.kt`（新增，shared jvm 源集） | 落盘 `client-worktrees.json`，临时文件 + 原子替换，镜像 `DesktopGeneralSettingsStore` |
| `git/GitPages.kt` | WorktreesPage 顶部新增「工作树根目录」小节（受控输入 + 行内错误 + 保存按钮 + 成功/失败/加载失败提示）；`WorktreeCreationForm` 增 `worktreeRoot` 参数，在根目录已配置且分支是真名时把「新工作树绝对路径」默认填成 `<root>/<slug>`（用户一旦编辑即不再覆盖） |
| `app/HarnessApp.kt` | 新参数 `worktreeSettingsRepository`；`load` 一次并与 `GeneralSettings` 同层；把状态与保存回调透传到 WorktreesPage |
| `jvm-app/src/main.kt` | 构造 `WorktreeSettingsRepository(DesktopWorktreeSettingsStore(home/client-worktrees.json))` 并注入 |

### 3.1 未实现（明确不主张）

- 同页的「自动删除旧工作树」「自动删除限制」「创建前获取上游更新」三行及其服务端行为（其中自动删除是**破坏性**操作，本批不实现、也不接线）。
- 原版的 host 级存储与 `worktreesRoot` 文档、`isSafePath` 的完整规则、CmdOrCtrl+S 快捷键与失焦提交（本仓用显式保存按钮 + 行内校验）。
- 原版同尺寸像素、原生窗口；本批只有生产解析器/仓库/页面证据与 Harness 自渲染截图。

## 4. 验收

见 `verification/worktree-root-setting-2026-10-11/entry-ay1/review.md`。各批证据独立，不累加为总体覆盖率。
