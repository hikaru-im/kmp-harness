# Diff 富文本预览开关：本批实施边界（2026-10-11）

## 1. 本批依据（ASAR 只读）

从本机安装包解包出的 `webview/assets` 只读抽取，原始片段见
`verification/git-rich-preview-2026-10-11/entry-aw1/logs/asar-evidence.txt`：

| 编号 | 观察到的内容 |
| --- | --- |
| E01 | `editor-diff-page-86fa0f0062f3.js`：diff 工具栏有一个 pressed 图标按钮，`aria-label` 取 `codex.diffView.richPreviewToggle`（默认英文 `Toggle rich preview`），tooltip 为同 id 文案，`aria-pressed: g`，`color: g ? 'ghostActive' : 'ghost'`，`onClick: () => c.set(ve, !g)`——即一个持久布尔开关，与布局选择器同排 |
| E02 | 同一工具栏：`initialOpen: O && e.metadata.type !== 'deleted'`——小 diff 默认展开，但**删除文件永不默认展开** |
| E03 | `app-shared-8bd9d0399038.js`：`fOr({filePath, imagePreviewMode, isDeletion, richPreviewEnabled})` 返回 `richPreviewEnabled && !isDeletion && hOr(path) ? 'markdown' : (imagePreviewMode==='always' || (imagePreviewMode==='toggle' && richPreviewEnabled)) ? 'image' : mOr(path) ? 'pdf' : 'diff'` |
| E04 | `hOr`/`mOr`：`hOr(path)` = `_Or.has(D1(path))`，`_Or = Set(['markdown','md','mdown','mdx','mkd'])`；`mOr(path)` = `D1(path)==='pdf'`；`pOr(path)`：`svg → 'toggle'`、`gOr=Set(['avif','bmp','gif','ico','jpeg','jpg','png','tif','tiff','webp']) → 'always'` |
| E05 | `D1(path)`：小写化后取最后一个 `/` 或 `\` 之后的 basename，`i = basename.lastIndexOf('.')`，返回 `i > 0 && i < basename.length-1 ? basename.slice(i+1) : null`——前导点不算扩展名，末尾点也算「无扩展名」 |
| E06 | `code-diff-38f339f0a601.js`：`Pt` 里 `fOr(...)` 得到 `'markdown'` 时渲染 `Markdown{path, fallback: diffView}`——即 markdown 预览带 diff 文本作为回退 |
| E07 | `zh-CN-b05cfd55c534.js`：`codex.diffView.richPreviewToggle` =「切换富文本预览」，`codex.diffView.richPreviewLoading` =「正在加载预览…」，另存在 `codex.diffView.pdfPreviewError` =「无法渲染 PDF 预览」 |

## 2. 已观察到与未观察到的语义

- **已观察**：开关是**页面级持久布尔**；只有「开关打开 ∧ 非删除 ∧ markdown 扩展名」才渲染 markdown 预览；markdown 预览以 diff 文本为 fallback；删除文件不参与默认展开；扩展名判定规则与原文一致（含末尾点）。
- **未观察**：`imagePreviewMode` 的持久来源与取值（代码里只有 `'always'`/`'toggle'` 字面量，UI 上未见设置入口）；原版 markdown 渲染器的实现（`Markdown` 组件本体不在本批片段内，未抓包）；开关的持久 store 初值；图标字形（只看到 `aria-pressed`/`color`/tooltip）。

因此本批只实现**可观察且与本仓能力相称的那一层**：页面级富文本开关 + markdown 预览 + 明确回退，图片/SVG/PDF 保留 diff 文本（见 §3.1）。

## 3. Harness 实施（本批生产改动）

| 层 | 改动 |
| --- | --- |
| `GitRichPreview`（新增，shared） | `GitMarkdownExtensions`、`GitDiffPreviewKind{Diff,Markdown}`、`gitFileExtension`（镜像 `D1`）、`gitDiffPreviewKind`（只保留 markdown 分支）、`gitPreviewPathIsSafe`（非空、无 NUL/反斜杠、非绝对、无盘符、无空/`.`/`..` 段）、`GitPreviewTextLimit = 256 KiB` |
| `GitMarkdown`（新增，shared） | 明确的**小小子集**渲染器：块级 `Heading/Paragraph/Code/Quote/Bullet/Ordered/Rule`（`parseMarkdownBlocks`），行内 `Plain/Bold/Italic/Code/Link`（`parseMarkdownInline`）；未闭合/不匹配的标记**原样保留**，绝不静默丢字 |
| `GitModels` | `GitPreviewFile{Loading,Ready(text),Unavailable(reason)}`；`GitState.previewFiles: Map<String, GitPreviewFile>`；`GitBackend.readPreviewFile(repository, path)` 默认返回 `Unavailable`（无法读取的后端显式回退） |
| `GitController` | `previewJobs` + `refreshPreviewFiles(paths)`：按路径去重、**每个 incarnation 只读一次**（`Ready`/`Unavailable` 都是终态）、与其它查询同一 `epoch` 围栏、LAZY 任务、`invalidate()`/`close()` 取消并清空；`refreshDiff()` 在**补丁文本真的变化**时清空 `previewFiles`（文本相同则保留缓存） |
| `GitPageComponents` | `GitDiffView` 工具栏新增 `GitDiffRichPreviewToggle`（`testTag=git-diff-rich-preview`，selected 语义）；`LaunchedEffect(document, previewPaths)` 在开关打开或 diff 变化时请求 markdown 路径；`GitDiffFileCard` 在 markdown + `Ready` 时渲染 `git-diff-rich-preview-<path>`（标题/代码/空各有 tag），否则渲染 `git-diff-rich-preview-notice-<path>` 并**继续显示 diff 文本** |
| `GitPages` | diff 调用点接线 `state.previewFiles` 与 `controller.refreshPreviewFiles` |
| `DesktopGitBackend`（jvm-app） | 实现 `readPreviewFile`：先 `local(binding)` 校验本机只读绑定，再校验路径安全，再 `Files.isRegularFile(..., NOFOLLOW_LINKS)`（**不跟随符号链接**），有界读取（超过 `GitPreviewTextLimit` 直接回退），`throwOnInvalidSequence` 解码非 UTF-8 直接回退；整个读取**不调用任何 git 命令** |

### 3.1 未实现（明确不主张）

- 图片 / SVG / PDF 预览：本批保留 diff 文本，未接图片或 PDF 渲染管线（原版 `image`/`pdf` 分支与 `codex.diffView.pdfPreviewError` 存在，本批不主张）。
- markdown 渲染器是**子集**而非 CommonMark：不支持表格、任务列表、嵌套强调、引用内块、HTML 等；`snake_case` 之类下划线强调可能误判。
- `imagePreviewMode` 的持久来源与设置入口、开关的持久 store、原版 `Markdown` 组件本体、图标字形。
- 原版同尺寸像素、原生窗口证据；本批只有生产解析器/页面/适配器证据与 Harness 自渲染截图。

## 4. 验收

见 `verification/git-rich-preview-2026-10-11/entry-aw1/review.md`。各批证据独立，不累加为总体覆盖率。
