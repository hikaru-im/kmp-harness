#!/usr/bin/env node
/**
 * Phase 11 (terminal + file panel) UI claim index.
 *
 * Produces 11-semantic/sources/ui-claims.json. Curated claims anchor on exact
 * baseline text for terminal behavior, the bottom panel, file tree/pickers,
 * editors, annotation surfaces and the referenced CSS. Message-id bindings are
 * extracted from the bounded slices and verified against the baseline.
 *
 * Usage:
 *   node scripts/codex-full-restore-phase11-claims-20261104.mjs [--out <file>]
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, "..");
const phaseDir = path.join(
  root,
  "references/codex-26.1027.61615-24141/full-restore/11-semantic",
);
const baseline = path.join(
  root,
  "references/codex-26.1027.61615-24141/full-restore/02-baseline/formatted",
);
const outIndex = process.argv.indexOf("--out");
const outPath =
  outIndex >= 0
    ? path.resolve(process.argv[outIndex + 1])
    : path.join(phaseDir, "sources/ui-claims.json");

const claims = [];
let seq = 0;
function add(category, file, anchor, claim, occurrence) {
  seq += 1;
  const entry = {
    id: `p11-${String(seq).padStart(3, "0")}-${category}`,
    category,
    file,
    anchor,
    claim,
    confidence: "high",
  };
  if (occurrence != null) entry.occurrence = occurrence;
  claims.push(entry);
}

const AI = "webview/assets/app-initial-18b189e867e7.js";
const AP = "webview/assets/app-primary-75d53eef2374.js";
const ASC = "webview/assets/thread-app-shell-chrome-e7e38c7ea254.js";
const TP = "webview/assets/terminal-panel-7bc13dac3ecc.js";
const TT = "webview/assets/terminal-tab-d3a5a5a51dbb.js";
const TTOOL = "webview/assets/thread-terminal-390395ede9ad.js";
const XA = "webview/assets/xterm-window-zoom-6ea5709641f4.js";
const FOPEN = "webview/assets/app-initial-18b189e867e7.js";
const WAF = "webview/assets/files-78b140b02df1.js";
const FA = "webview/assets/file-actions-20b2bae1bfa9.js";
const FVM = "webview/assets/file-viewer-menu-a3e8ac4313a0.js";
const TFE = "webview/assets/text-file-editor-tab-content.electron-3dfaf00197f1.js";
const PE = "webview/assets/pierre-file-editor-24b92997461d.js";
const SE = "webview/assets/editor-76f8ec768ed8.js";
const ACE = "webview/assets/annotation-editor-c5dd18b88966.js";
const STA = "webview/assets/annotation-editor-981552d71aa9.js";
const RTA = "webview/assets/response-text-annotation-editor-ebd4d2d4ecef.js";
const ART = "webview/assets/artifact-tab-content.electron-757f58d12b55.js";
const APS = "webview/assets/artifact-preview-status-22025ddcf23f.js";
const LFP = "webview/assets/library-file-picker-dialog-content-c6205f26b78f.js";
const RCP = "webview/assets/remote-cwd-picker-2f71a83ff7a7.js";
const UFP = "webview/assets/use-file-picker-ed94b8e7bfdc.js";

// ------------------------------------------------------------- TERMINAL
add("terminal", TP, '"data-codex-terminal": !0,', "The terminal panel marks its root with `data-codex-terminal`.");
add("terminal", TP, '"data-codex-xterm": !0,', "The same root carries `data-codex-xterm`, which selects the terminal theme CSS.");
add("terminal", TP, "pasteOnCtrlV: v === `windows`,", "Paste-on-Ctrl+V is enabled only on Windows; other platforms use the host paste chord.");
add("terminal", TP, "_.attachCustomKeyEventHandler(", "The xterm instance installs a custom key handler for copy/paste, Insert and new-terminal shortcuts.");
add("terminal", TP, "_.scrollToBottom()", "After output is written the panel schedules a scroll-to-bottom on the next animation frame.");
add("terminal", TP, "m.setInputPreviewEnabled(c != null);", "The terminal enables its input preview only when a pending input preview exists.");
add("terminal", TP, "(t.fit(), C.resize(n, e.cols, e.rows, r));", "Resize first fits the addon, then reports the resulting cols/rows to the PTY with an optional immediate flag.");
add("terminal", TP, "fontFamily: he.current,", "The xterm options use the code font family read from the app font store.");
add("terminal", TP, "fontSize: ge.current,", "The xterm options use the code font size read from settings.");
add("terminal", TP, "id: `terminal.error.title`", "The terminal crash UI binds `terminal.error.title`.");
add("terminal", TP, "id: `terminal.error.description`", "The terminal crash UI explains recovery with `terminal.error.description`.");
add("terminal", TP, "id: `terminal.error.retry`", "The terminal crash UI offers a Reload action via `terminal.error.retry`.");
add("terminal", TT, "id: `cloudTerminal.reconnecting`", "Cloud terminal tabs expose a reconnecting state.");
add("terminal", TT, "id: `cloudTerminal.connectionFailed`", "Cloud terminal tabs expose a connection-failed state.");
add("terminal", TT, "defaultMessage: `Press ⌥ Opt + Number to change directory`,", "On macOS the workspace-root hint uses Option plus a number.");
add("terminal", TT, "defaultMessage: `Press Alt + Number to change directory`,", "On other platforms the workspace-root hint uses Alt plus a number.");
add("terminal", TT, "`Set-Location -LiteralPath '${e.replaceAll(`'`, `''`)}'\\r`", "The PowerShell cwd switch uses `Set-Location -LiteralPath` with single-quote doubling.");
add("terminal", TT, "/[%!]/.test(e)", "The cmd cwd switch is suppressed when the path contains `%` or `!`, which cmd would expand.");
add("terminal", TT, "cd -- '", "The POSIX cwd switch uses `cd --` with shell-safe single-quote escaping.");
add("terminal", AI, "[1e3, 2e3, 4e3]", "Terminal reconnect attempts back off through 1s, 2s and 4s.", 1);
add("terminal", AI, "(W1a = 3e4)", "The final terminal reconnect delay is 30s.");
add("terminal", AI, "if (e.length <= Q1a) return e;", "Terminal buffers shorter than the 16k cap are returned unchanged.");
add("terminal", AI, "dropDestinations: [`left`, `right`, `bottom`],", "The terminal tab route can be dropped into the left, right or bottom panel.");
add("terminal", AI, "(M1a = `terminal`),", "The terminal route kind string is `terminal`.");
add("terminal", TTOOL, "(a = `read_thread_terminal`),", "The thread terminal tool is named `read_thread_terminal`.");
add("terminal", TTOOL, "description: `Read the current app terminal output for this desktop thread.", "The tool description tells the model it reads the thread's app terminal output.");
add("terminal", TTOOL, "No app terminal session is attached to this thread yet.", "The tool reports a missing terminal session instead of failing.");
add("terminal", XA, "initiator: `terminal_link_click`", "Terminal link activations are reported with the `terminal_link_click` initiator.");
add("terminal", XA, "r?._screenDprMonitor != null && r.register(r._screenDprMonitor);", "The WebLinks/zoom addon registers the xterm screen DPR monitor.");

// --------------------------------------------------------- BOTTOM PANEL
add("bottom-panel", AP, "(ONt = 280), (kNt = `app-shell:bottom-panel-height`));", "The bottom panel defaults to 280px and persists its height under `app-shell:bottom-panel-height`.");
add("bottom-panel", AP, '"data-app-shell-focus-area": `bottom-panel`,', "The bottom panel focus scope is marked `data-app-shell-focus-area=bottom-panel`.");
add("bottom-panel", AP, "o.panelId === `bottom` ? `bottom-panel` : `right-panel`,", "Panel id `bottom` maps to focus area `bottom-panel`; other panels map to `right-panel`.");
add("bottom-panel", ASC, "id: `thread.bottomPanel.hide`", "The bottom panel chrome binds `thread.bottomPanel.hide`.");
add("bottom-panel", ASC, "id: `thread.bottomPanel.openTab`", "The bottom panel chrome binds `thread.bottomPanel.openTab`.");

// -------------------------------------------------------- FILE BROWSER
add("files", AI, "startRenaming(e = this.#c ?? ``, t = {}) {", "The file tree controller exposes `startRenaming(path, options)`.");
add("files", AI, "openSearch(e = ``) {", "The file tree controller exposes `openSearch(initialQuery)`.");
add("files", AI, "getSearchMatchingPaths() {", "The file tree controller exposes `getSearchMatchingPaths()`.", 0);
add("files", AI, "registerRenameInput: D,", "Rows register their rename input so the tree can focus it.");
add("files", AI, "ariaLabel: `Rename ${QBa(t)}`,", "The rename input is labelled `Rename <name>`.");
add("files", AI, "[data-file-tree-virtualized-scroll='true'],", "The file tree scroller is styled through `data-file-tree-virtualized-scroll`.", 0);
add("files", AI, "[data-file-tree-search-input] {", "The inline file-tree search input has its own style block.");
add("files", AI, "[data-item-selected='true']", "Selected file-tree rows are styled through `data-item-selected`.", 0);
add("files", AI, "id: `files.search.allFiles`", "The Files toolbar search button binds `files.search.allFiles`.");
add("files", AI, "type: `find-in-files`,", "The toolbar dispatches a host `find-in-files` message rather than rendering search in the webview.");
add("files", AI, '"data-find-in-files": !0,', "The Files panel root is marked with `data-find-in-files`.");
add("files", AI, "id: `threadSidePanel.workspaceBrowser.loading`", "The browser shows `threadSidePanel.workspaceBrowser.loading` while a directory loads.", 0);
add("files", AI, "id: `localConversation.openTarget.error`", "Failing to open an item raises `localConversation.openTarget.error`.");
add("files", AI, "id: `codex.review.fileTree.contextMenu.copyPath`", "The file tree context menu binds `codex.review.fileTree.contextMenu.copyPath`.");
add("files", AI, "id: `threadSidePanel.workspaceBrowser.openIn`", "The file tree context menu binds `threadSidePanel.workspaceBrowser.openIn`.");
add("files", AI, "id: `workspaceFile.saveAsError`", "A failed Save as raises `workspaceFile.saveAsError`.", 0);
add("files", FVM, "id: `fileViewer.preferenceFailed`", "Saving a file-handler preference failure raises `fileViewer.preferenceFailed`.");
add("files", WAF, "id: `workspaceAgents.resources.writeFailed`", "Workspace-agent file writes surface `workspaceAgents.resources.writeFailed`.");
add("files", WAF, "id: `workspaceAgents.resources.refreshFailed`", "Refreshing workspace-agent files can surface `workspaceAgents.resources.refreshFailed`.");
add("files", FA, "id: `localConversation.endResource.downloadCopy.success`", "Downloading an artifact copy reports success through `downloadCopy.success`.");
add("files", FA, "id: `artifactSession.openIn.showInFolder.failure`", "Failing to reveal an artifact binds `artifactSession.openIn.showInFolder.failure`.");
add("files", LFP, "id: `library-file-picker-search`", "The library picker search field uses test id `library-file-picker-search`.");
add("files", LFP, "id: `libraryPicker.empty.searchTitle`", "The library picker has a dedicated empty-search title.");
add("files", LFP, "id: `libraryPicker.search.placeholder`", "The library picker search field binds `libraryPicker.search.placeholder`.");
add("files", RCP, "id: `remoteCwdPicker.loadError`", "The remote folder picker reports load failures with `remoteCwdPicker.loadError`.");
add("files", RCP, "id: `remoteCwdPicker.emptyFiles`", "The remote folder picker distinguishes an empty file/folder directory.");
add("files", UFP, "id: `composer.filePicker.selectFolder`", "The file picker defaults its title to `composer.filePicker.selectFolder` for directory picks.");
add("files", UFP, "id: `composer.filePicker.selectFiles`", "The file picker defaults its title to `composer.filePicker.selectFiles` for file picks.");

// ------------------------------------------------------------- EDITORS
add("editor", TFE, "id: `textFileEditor.largeFileReadOnly`", "Text files above the size limit open with the `textFileEditor.largeFileReadOnly` notice.");
add("editor", TFE, "id: `markdownFileEditor.copyMarkdown`", "The markdown editor offers Copy Markdown.");
add("editor", TFE, "id: `markdownFileEditor.mode.viewSource`", "The markdown editor can switch to source view.");
add("editor", TFE, "id: `markdownFileEditor.mode.viewPreview`", "The markdown editor can switch to preview view.");
add("editor", TFE, "id: `textFileEditor.discardConfirmation.title`", "Closing a dirty editor opens the discard-confirmation dialog.");
add("editor", TFE, "id: `textFileEditor.openError`", "Opening a text file can raise `textFileEditor.openError`.");
add("editor", TFE, "id: `textFileEditor.saveError`", "Saving a text file can raise `textFileEditor.saveError`.");
add("editor", PE, "This comparison is too large to highlight", "Diffs above the highlight threshold render the too-large comparison notice.");
add("editor", PE, "id: `textFileEditor.undo`", "The pierre editor toolbar binds `textFileEditor.undo`.");
add("editor", PE, "id: `textFileEditor.redo`", "The pierre editor toolbar binds `textFileEditor.redo`.");
add("editor", SE, "id: `chatgpt.sketch.composer_modal.title`", "The sketch editor modal title is `chatgpt.sketch.composer_modal.title`.");
add("editor", SE, "id: `chatgpt.sketch.composer_modal.tool.eraser`", "The sketch editor exposes an eraser tool message.");
add("editor", SE, "id: `chatgpt.sketch.composer_modal.undo.button_label`", "The sketch editor exposes undo in its toolbar.");
add("editor", ART, "This file is too large to preview in the side panel", "Artifacts above the preview size cap show the too-large message instead of rendering.");
add("editor", ART, "id: `artifactTab.cloudFileUnavailable`", "Unavailable Cloud files render `artifactTab.cloudFileUnavailable`.");
add("editor", APS, "id: `artifactTab.previewError`", "Failed artifact previews render `artifactTab.previewError`.");

// --------------------------------------------------------- ANNOTATIONS
add("annotation", ACE, "id: `composer.annotationEditor.title`", "The composer annotation editor title is `composer.annotationEditor.title`.");
add("annotation", ACE, "id: `composer.annotationEditor.comment`", "The composer annotation comment input is labelled `composer.annotationEditor.comment`.");
add("annotation", STA, 'data-selected-text-annotation-editor', "The selected-text annotation editor root carries `data-selected-text-annotation-editor`.");
add("annotation", STA, "id: `selectedTextAnnotationEditor.placeholder`", "The selected-text note uses `selectedTextAnnotationEditor.placeholder`.");
add("annotation", RTA, "id: `responseTextAnnotationMarkers.editor.loadError`", "A failed annotation-editor chunk shows `responseTextAnnotationMarkers.editor.loadError`.");
add("annotation", RTA, "id: `responseTextAnnotationMarkers.editor.loading`", "The annotation editor shows a loading state while its chunk downloads.");
add("annotation", RTA, "id: `responseTextAnnotationMarkers.editor.close`", "The annotation editor fallback offers a Close action.");

// ----------------------------------------------------------------- CSS
add("css", "webview/assets/241c9f83d2ce.css", ".xterm .xterm-helper-textarea {", "The xterm helper textarea is visually hidden and positioned off-screen for IME input.");
add("css", "webview/assets/241c9f83d2ce.css", ".xterm .composition-view.active {", "An active IME composition view is displayed as an overlay in the terminal.");
add("css", "webview/assets/241c9f83d2ce.css", ".xterm .xterm-viewport {", "The xterm viewport is styled by the vendored terminal stylesheet.");
add("css", "webview/assets/d0f289883c52.css", "[data-codex-xterm][data-theme] {", "The app terminal theme defines background, foreground and selection CSS variables under `[data-codex-xterm][data-theme]`.");
add("css", "webview/assets/d0f289883c52.css", "transform: translateX(var(--terminal-input-preview-offset, 0px));", "The terminal cursor is shifted by the input-preview offset variable.");
add("css", "webview/assets/d0f289883c52.css", "scrollbar-color: var(--color-border) var(--color-codex-terminal-background);", "The terminal viewport uses themed scrollbar colors.");
add("css", "webview/assets/cecb2c03ae9a.css", ".ansi-black-fg {", "ANSI black foreground/background map to `--color-codex-terminal-ansi-black`.");
add("css", "webview/assets/cecb2c03ae9a.css", ".ansi-red-fg {", "ANSI red foreground/background map to `--color-codex-terminal-ansi-red`.");
add("css", "webview/assets/cecb2c03ae9a.css", ".ansi-green-fg {", "ANSI green foreground/background map to `--color-codex-terminal-ansi-green`.");
add("css", "webview/assets/61321c9dabf9.css", "[data-pierre-editor-surface] diffs-container {", "The Pierre editor surface overrides its selection background variable.");
add("css", "webview/assets/61321c9dabf9.css", "[data-pierre-editor-annotation]::selection {", "Pierre annotation selections use the info-soft background.");

// -------------------------------------------------------- MESSAGE IDS
const priority = [
  "search", "rename", "open", "close", "empty", "error", "failed", "loading",
  "save", "delete", "download", "upload", "folder", "file", "terminal",
  "shell", "editor", "preview", "retry", "cancel", "title", "description",
];
function score(id) {
  const lower = id.toLowerCase();
  let total = 0;
  for (let i = 0; i < priority.length; i += 1) {
    if (lower.includes(priority[i])) total += priority.length - i;
  }
  return total;
}
function humanTail(id) {
  const tail = id.split(".").slice(-2).join(" ");
  return tail
    .replace(/([a-z0-9])([A-Z])/g, "$1 $2")
    .replace(/[_-]+/g, " ")
    .toLowerCase();
}
function sliceSource(slice) {
  const manifest = JSON.parse(
    fs.readFileSync(path.join(phaseDir, "reports", `${slice}-slice.json`), "utf8"),
  );
  return path.basename(manifest.source.path);
}
const sliceSpecs = [
  ["terminal-tab", "terminal", "Cloud terminal tab", 6],
  ["terminal-panel", "terminal", "terminal panel", 1],
  ["files-panel", "files", "workspace file browser", 4],
  ["files-open-targets", "files", "file context menu", 6],
  ["workspace-agent-files", "files", "workspace-agent files browser", 7],
  ["file-actions", "files", "artifact end-resource actions", 6],
  ["text-file-editor", "editor", "text file editor", 6],
  ["pierre-editor", "editor", "Pierre editor", 4],
  ["sketch-editor", "editor", "sketch editor", 8],
  ["annotation-comment-editor", "annotation", "composer annotation editor", 2],
  ["text-selection-annotation", "annotation", "selected-text annotation editor", 2],
  ["artifact-preview", "editor", "artifact preview", 4],
  ["library-file-picker", "files", "library file picker", 7],
  ["remote-cwd-picker", "files", "remote cwd picker", 7],
  ["use-file-picker", "files", "file picker hook", 2],
];
const claimedAnchors = new Set(claims.map((claim) => `${claim.file}\0${claim.anchor}`));
for (const [slice, category, subject, cap] of sliceSpecs) {
  const text = fs.readFileSync(
    path.join(phaseDir, "formatted/webview/assets", `${slice}-slice.js`),
    "utf8",
  );
  const ids = [...new Set([...text.matchAll(/id: `([^`]+)`/g)].map((m) => m[1]))];
  const selected = ids
    .map((id, index) => ({ id, index, value: score(id) }))
    .sort((a, b) => b.value - a.value || a.index - b.index)
    .slice(0, cap)
    .sort((a, b) => a.index - b.index);
  for (const { id } of selected) {
    const file = `webview/assets/${sliceSource(slice)}`;
    const anchor = `id: \`${id}\``;
    if (claimedAnchors.has(`${file}\0${anchor}`)) continue;
    claimedAnchors.add(`${file}\0${anchor}`);
    const occurrence =
      fs.readFileSync(path.join(baseline, file), "utf8").split(anchor).length - 1;
    add(
      category,
      file,
      anchor,
      `The ${subject} binds \`${id}\` for its ${humanTail(id)} copy.`,
      occurrence > 1 ? 0 : undefined,
    );
  }
}

// Verify every anchor against the baseline before writing.
const failures = [];
for (const claim of claims) {
  const text = fs.readFileSync(path.join(baseline, claim.file), "utf8");
  const occurrence = claim.occurrence ?? 0;
  let from = 0;
  let found = -1;
  for (let i = 0; i <= occurrence; i += 1) {
    found = text.indexOf(claim.anchor, from);
    if (found < 0) break;
    from = found + 1;
  }
  if (found < 0) failures.push(claim.id);
}
if (failures.length > 0) {
  console.error(JSON.stringify({ failures }, null, 2));
  process.exit(1);
}
const byCategory = {};
for (const claim of claims) byCategory[claim.category] = (byCategory[claim.category] ?? 0) + 1;
fs.mkdirSync(path.dirname(outPath), { recursive: true });
fs.writeFileSync(outPath, `${JSON.stringify(claims, null, 2)}\n`);
console.log(JSON.stringify({ claims: claims.length, byCategory, outPath }, null, 2));
