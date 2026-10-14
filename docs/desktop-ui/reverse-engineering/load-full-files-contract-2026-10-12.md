# Diff「加载完整文件」：本批实施边界（2026-10-12）

## 1. 本批依据（ASAR 只读）

从本机安装包解包出的 webview bundle（`/tmp/asar-new/webview/assets/`）只读分析，未改动原包、未联网。抄录见
`../verification/load-full-files-2026-10-12/entry-cf1/logs/asar-load-full-files.txt`。

| 编号 | asset | 观察到的内容 |
| --- | --- | --- |
| E01 | `zh-CN-b05cfd55c534.js` | `"codex.review.loadFullFiles.enable":"加载完整文件"`，紧邻 `codex.review.loadFailed.{title,description,retry}`。 |
| E02 | `pull-request-code-review-be17c7c34414.js` | diff 选项菜单里该项是 `be.CheckboxItem`，`children: <Z id="codex.review.loadFullFiles.enable" defaultMessage="Load full files" description="Menu item to load full file contents for partial diffs" />`，`checked`/`onCheckedChange` 绑 `loadFullFilesEnabled`/`onToggleLoadFullFiles`。 |
| E02b | `pull-request-code-review-be17c7c34414.js` | 会话组件把状态与回调透传：`{…, hideImports: i.hideImports, onToggleHideImports: i.onToggleHideImports, loadFullFilesEnabled: i.loadFullFilesEnabled, onToggleLoadFullFiles: i.onToggleLoadFullFiles}`——状态由 diff 视图外部持有，布尔回调切换。 |
| E03 | `code-diff-38f339f0a601.js` | 缓存键构造 `Gt({diff, fullContentLoader, hostConfig, ignoreWhitespace, loadFullFilesEnabled, nextFallbackToDisk, snapshotGeneration, workspaceRoot})`——`loadFullFilesEnabled` **编入** diff cache key（同一文件在开关前后是不同的缓存条目）。 |
| E04 | `code-diff-38f339f0a601.js` | 升级门控：`qe = ee && F && (workspaceRoot!=null || fullContentLoader!=null) && be && diff.metadata.isPartial && !isBinary && !isGitlink && !type-new && !type-deleted && !renamePure && …`——即**只对 `metadata.isPartial`（被截断的）文件**才尝试用整文件内容重算；非 partial、二进制、gitlink、整文件重命名/新增/删除都跳过。 |

（本批为「变更选项」菜单 6 项里的第 3 项；前两项为「展开/折叠」与「隐藏空白字符」，后三项为「词级差异」「隐藏导入内容」「复制 git apply 命令」。菜单整体顺序见批 CE 抄录。）

## 2. 已观察到与未观察到的语义

- **已观察**：diff 视图的「变更选项」菜单里有一个「加载完整文件」勾选项，语义是
  `load full file contents for partial diffs`（对被截断的 partial diff，加载完整文件内容）。
- **已观察**：开关只对 `metadata.isPartial` 的文件生效，并且会**改变 diff 的缓存键**——即开关一次会重新取一次该文件的
  diff，而不是在客户端对已有 patch 做局部拼装。
- **已观察**：开关与渲染数据绑在**同一个** diff 视图组件上，`checked`/`onCheckedChange` 由外层持有布尔回调
  （`loadFullFilesEnabled`/`onToggleLoadFullFiles`），和「隐藏导入内容」等兄弟项同构。
- **未观察（本仓前置缺失）**：
  - 原版用 `fullContentLoader` 按对象 id 取 prev/next **整对象**（`maxObjectBytes`，`too-large` 时回退），再在客户端
    自算完整 diff。本仓的 `GitDiffSnapshot`/`GitDiffRequest` **没有 per-file OID**，也没有客户端行级 differ，
    因此无法照抄「取对象 + 客户端重算」这条路径。
  - 原版 `isPartial` 的产生方（host 端截断阈值、`maxObjectBytes`）在本仓不可见，也未实现。
- **未观察**：开关关闭后是否也影响行号栏、是否对全部文件批量生效、是否持久化到设置（CE 已证 `hideDiffImports` 是设置键，
  但 `loadFullFiles` 只观察到组件内布尔状态，未见对应设置键）。

因此本批实现**可观察的那一层**：在「变更选项」菜单中新增「加载完整文件」开关（默认关），开启时让 **Host 用全文件上下文
重算一次该 diff**，从而使被截断文件的 patch 展开为整文件上下文——对用户可观察的结果与 ASAR 一致（同一文件、开关一次、
内容更完整），但**机制不同**（见 §2.1）。

### 2.1 机制差异（如实记录）

| 维度 | ASAR 原版 | 本仓 CF 实现 |
| --- | --- | --- |
| 触发 | 客户端按 per-file OID 取整对象，客户端算 diff | 请求级开关，Host 用 `git diff --unified=<大值>` 重算 |
| 作用面 | 仅 `metadata.isPartial` 的文件升级为 full | 请求内所有文件统一加宽上下文（本仓无 `isPartial` 标记） |
| 缓存 | `loadFullFilesEnabled` 编入 diff cache key | 开关进入 `GitDiffRequest`，控制器据此重读快照 |

选择请求级方案是因为本仓缺少 §2 里点名的两块前置能力（per-file OID + 客户端 differ）；补齐它们属独立批次，不在本批。

## 3. Harness 实施（本批生产改动）

| 层 | 改动 |
| --- | --- |
| `client/git/GitModels.kt`（shared） | `GitDiffRequest` 新增 `loadFullFiles: Boolean = false`（带 ASAR 依据注释）。 |
| `desktop/git/DesktopGitBackend.kt`（jvm-app） | `diff()` 在 `--ignore-all-space` 之后、路径分隔 `--` 之前插入 `if (request.loadFullFiles) args.add("--unified=$FULL_FILE_CONTEXT_LINES")`；文件末尾新增 `private const val FULL_FILE_CONTEXT_LINES = 100_000`。 |
| `client/git/GitPageComponents.kt`（shared） | `GitDiffView` / `GitPatch` 新增 `loadFullFiles` + `onToggleLoadFullFiles`（默认 `false`）；`GitDiffOptionsMenu` 在「展开」之后、「隐藏空白字符」之前插入第 3 项 `HarnessMenuEntry.Item(key = "load-full-files", text = "加载完整文件", selected = loadFullFiles)`；`onSelect` 增 `"load-full-files" -> onToggleLoadFullFiles(!loadFullFiles)`；全链路传参。 |
| `client/git/GitPages.kt`（shared） | `GitPatch(...)` 调用点传 `loadFullFiles = state.diffRequest.loadFullFiles, onToggleLoadFullFiles = { controller.selectDiff(state.diffRequest.copy(loadFullFiles = it)) }`。 |

`--unified=100000` 大于任何现实文件的行数，因此一次重读即可拿到整文件上下文；这不会改变「未更改文件不出现」等既有语义，
只是把 hunk 的上下文补满。

### 3.1 未实现（明确不主张）

- **per-file OID / 客户端 differ**：原版「取整对象 + 客户端重算」路径；本仓用 Host 全上下文重算代替（§2.1）。
- **`isPartial` 门控**：本仓没有 partial 标记，开关对请求内所有文件统一加宽（不区分是否被截断）。
- **`maxObjectBytes` / `too-large` 回退**：原版对超大对象的回退逻辑；本仓由 git 自身处理。
- **持久化**：原版把 `hideDiffImports` 之类写设置；`loadFullFiles` 只观察到组件内布尔状态，本仓同样只存在页面组合状态，重开复位。
- **菜单顺序**：ASAR `jr` 顺序为 `loadFullFiles → richPreview → wordDiffs → hideWhitespace → hideImports → copyGitApplyCommand`；本仓沿用既有菜单（`expand` / `hide-whitespace` / `load-full-files` / `word-diffs` / `hide-imports`），顺序与 ASAR 略有出入。
- **「变更选项」菜单其余项**：`refresh`（`codex.review.refreshGitQueries`）、`richPreview`、复制 `git apply` 命令仍未实现。
- **原版同尺寸像素与原生窗口证据**（未做）。

## 4. 验收

见 `../verification/load-full-files-2026-10-12/entry-cf1/review.md`。各批证据独立，不累加为总体覆盖率。
