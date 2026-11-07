#!/usr/bin/env node
/**
 * Phase 11 (terminal + file panel) semantic rename specs.
 *
 * Writes one renames/<slice>.json per bounded slice. Every declAnchor is exact
 * baseline text; the semantic tool renames only the identifier binding inside
 * that anchor and later proves inverse byte equality.
 *
 * Usage:
 *   node scripts/codex-full-restore-phase11-renames-20261104.mjs [--out DIR]
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
const outIndex = process.argv.indexOf("--out");
const outDir =
  outIndex >= 0 ? path.resolve(process.argv[outIndex + 1]) : path.join(phaseDir, "renames");

const INITIAL = "app-initial-18b189e867e7.js";
const PRIMARY = "app-primary-75d53eef2374.js";
const SHELL_CHROME = "thread-app-shell-chrome-e7e38c7ea254.js";

const specs = {
  "terminal-tab-slice": {
    source: "terminal-tab-d3a5a5a51dbb.js",
    renames: [
      ["z", "useTerminalSessionSnapshot", "Subscribes a component to one terminal session snapshot through useSyncExternalStore."],
      ["Oe", "getNullSessionServerSnapshot", "Server snapshot for the terminal session store, so SSR renders no session."],
      ["ke", "useConversationTerminalSnapshot", "Subscribes to the conversation-level terminal snapshot, or a shared no-op when no conversation id is available."],
      ["Ae", "getNullConversationServerSnapshot", "Server snapshot for the conversation terminal store."],
      ["je", "noopUnsubscribe", "Shared no-op returned when there is no conversation to subscribe to."],
      ["H", "initTerminalTabStoresModule", "Initializes the terminal session/conversation stores and their React bindings."],
      ["Me", "CloudTerminalStatus", "Renders the Cloud terminal status banner for reconnecting, stopped, disconnected and failed states."],
      ["Pe", "initCloudTerminalStatusModule", "Initializes the message formatter and status view for the Cloud terminal banner."],
      ["Fe", "CloudTerminalTab", "Cloud-host terminal tab; resolves environment state, reconnect/restart actions and the terminal panel."],
      ["Ie", "useTerminalSessionState", "Derives error, input error, exit code, attachment and output flags from the session snapshot."],
      ["W", "CloudTerminalOpeningStatus", "Status block shown while the Cloud terminal is opening or waiting for an environment."],
      ["ze", "initCloudTerminalTabModule", "Initializes the Cloud terminal tab and its environment bindings."],
      ["Be", "TerminalWorkspaceWarning", "Warning shown when a terminal cwd is outside the chat's workspaces, with dismiss and new-terminal actions."],
      ["He", "initTerminalWorkspaceWarningModule", "Initializes the workspace warning component and its message ids."],
      ["Ue", "buildChangeDirectoryCommand", "Builds a shell-specific cd command line (PowerShell, cmd or POSIX) for the selected workspace root."],
      ["We", "initChangeDirectoryCommandModule", "Loads the shell-label helpers used by buildChangeDirectoryCommand."],
      ["Ge", "WorkspaceRootShortcutHint", "Hint that Alt/Option plus a number switches the terminal cwd between workspace roots."],
      ["Ke", "preventDefaultHandler", "Prevents the browser default for the workspace shortcut key."],
      ["Je", "initWorkspaceRootShortcutModule", "Initializes the workspace root shortcut hint and its platform bindings."],
      ["Ye", "TerminalTabSlots", "Chooses the Cloud or local terminal tab and renders it into the tab slot layout."],
      ["Xe", "LocalTerminalTab", "Local terminal tab; wires cwd, workspace warning, shell cwd lookup and the terminal panel."],
      ["$", "initTerminalTabModule", "Initializes the terminal tab module and its lazy child chunks."],
    ],
  },
  "terminal-panel-slice": {
    source: "terminal-panel-7bc13dac3ecc.js",
    renames: [
      ["he", "TerminalErrorState", "Terminal crash state with the 'The terminal encountered an error' copy and a Reload action."],
      ["z", "initTerminalErrorStateModule", "Initializes the terminal error component and its message bindings."],
      ["_e", "handleTerminalCustomKeyEvent", "Custom xterm key handler: new tab, copy/paste, Insert shortcuts and macOS readline key mapping."],
      ["B", "preventDefaultAndStopPropagation", "Stops the browser default and propagation for a handled terminal key."],
      ["ve", "isCopyShortcut", "True for Cmd+C, Ctrl+Shift+C or Ctrl+Insert when the terminal has a selection."],
      ["ye", "isPasteShortcut", "True for Cmd+V, Ctrl+Shift+V or Shift+Insert paste shortcuts."],
      ["be", "getReadlineControlCharacter", "Maps macOS Option+Arrow/Backspace/Delete to the readline control characters used by the shell."],
      ["V", "isCtrlShiftShortcut", "Matches Ctrl+Shift+<key> without Alt or Meta."],
      ["H", "isCtrlShortcut", "Matches Ctrl+<key> without Shift, Alt or Meta."],
      ["xe", "isInsertShortcut", "Matches the Insert key with the given Ctrl/Shift modifier state."],
      ["U", "isMacShortcut", "Matches a bare Cmd(Command)+<key> chord without other modifiers."],
      ["J", "initReadlineControlCharacters", "Initializes the control-character constants used for macOS readline key mapping."],
      ["Se", "fitAndResizeTerminal", "Fits the xterm addon to its container and reports the new cols/rows to the PTY host."],
      ["Ce", "TerminalPanelLazy", "Lazy boundary around the terminal panel with a loading fallback and retryable error fallback."],
      ["we", "TerminalPanelLoadFallback", "Loading/error fallback shown before the terminal panel chunk is ready."],
      ["Te", "TerminalPanel", "Main xterm panel: theme/font wiring, keyboard handling, clipboard, resize, scroll-to-bottom and PTY writes."],
      ["Z", "initTerminalPanelModule", "Initializes the terminal panel with xterm core, fit/weblinks/clipboard addons and theme stores."],
    ],
  },
  "xterm-addons-slice": {
    source: "xterm-window-zoom-6ea5709641f4.js",
    renames: [
      ["s", "reportTerminalLinkActivation", "Reports a clicked terminal link with its href, host id and terminal_link_click initiator."],
      ["l", "registerCanvasDprMonitor", "Registers the xterm screen DPR monitor and patches mouse-service coordinates on the core."],
      ["u", "scaleMouseEventCoordinates", "Scales mouse coordinates from CSS pixels into the terminal canvas coordinate space."],
    ],
  },
  "xterm-output-panel-slice": {
    source: "xterm-output-panel-b1a344e3de5f.js",
    renames: [
      ["k", "XtermOutputPanel", "Read-only xterm output panel used by background terminal thumbnails and tabs."],
    ],
  },
  "background-terminal-slice": {
    source: "background-terminal-28355224b010.js",
    renames: [
      ["o", "BackgroundTerminalThumbnail", "Thumbnail that renders the buffered output and title of a background terminal."],
      ["u", "initBackgroundTerminalModule", "Initializes the background terminal thumbnail and its output reader."],
    ],
  },
  "background-terminal-tab-slice": {
    source: "local-conversation-background-terminal-tab-15c64f0a7d5b.js",
    renames: [
      ["g", "BackgroundTerminalTab", "Tab for a local conversation background terminal; reads its session snapshot and renders XtermOutputPanel."],
      ["_", "useBackgroundTerminalSessionSnapshot", "Subscribes to a background terminal session snapshot for the tab."],
      ["v", "getNullBackgroundSessionSnapshot", "Server snapshot for the background terminal store."],
      ["C", "initBackgroundTerminalTabModule", "Initializes the background terminal tab and lazily loads the xterm output panel."],
    ],
  },
  "thread-terminal-tool-slice": {
    source: "thread-terminal-390395ede9ad.js",
    renames: [
      ["r", "readThreadTerminalTool", "Tool handler that reads the thread's app terminal snapshot and returns cwd, shell, truncation note and buffer."],
      ["i", "textToolResult", "Builds a single inputText tool result with the success flag."],
      ["a", "READ_THREAD_TERMINAL_TOOL_NAME", "Name constant for the read_thread_terminal tool.", "var a, o, s;"],
    ],
  },
  "shell-label-slice": {
    source: "shell-label-1f18309df00d.js",
    renames: [
      ["r", "getShellDisplayLabel", "Resolves a shell command/path to its display label (PowerShell, bash, cmd, zsh)."],
      ["i", "normalizeShellCommand", "Trims quoting and path prefixes from a shell command before label lookup."],
      ["a", "getPathBasename", "Returns the final path segment for Windows or POSIX shell paths."],
      ["o", "initShellLabelModule", "Initializes the shell label lookup tables."],
    ],
  },
  "terminal-warning-slice": {
    source: "terminal-workspace-warning-state-2125af67019f.js",
    renames: [
      ["s", "shouldShowTerminalWorkspaceWarning", "True when the terminal cwd is outside the current workspaces and the same target was not dismissed."],
      ["c", "isSameWorkspaceBinding", "Compares two workspace bindings by host id and normalized workspace root."],
    ],
  },
  "terminal-route-slice": {
    source: INITIAL,
    renames: [
      ["C1a", "openTerminalTab", "Opens or focuses a terminal tab route, activating the owning pane and its tab."],
      ["X1", "getTerminalTabId", "Builds a stable file/terminal tab id from host, path and session identity."],
      ["w1a", "openTerminalInEnvironment", "Opens a terminal tab inside the selected Cloud environment when one is available."],
      ["P1a", "initTerminalRouteKindModule", "Initializes the terminal route kind and its cwd/hostId/sessionId params."],
      ["F1a", "prefetchTerminalTab", "Prefetches the terminal tab chunk before the route opens."],
      ["L1a", "defineTerminalTabRoute", "Durable terminal tab route; may drop into left, right or bottom panes and transfers conversation state."],
      ["H1a", "initTerminalTabRouteModule", "Initializes the terminal tab route, its descriptor and lazy import."],
      ["K1a", "initTerminalHostServiceModule", "Initializes terminal reconnect backoff, the host service class and session snapshots."],
      ["q1a", "truncateTerminalBuffer", "Caps a terminal buffer at 16k code units, keeping the newest output."],
      ["Y1a", "computeRelativeTerminalCwd", "Returns the cwd relative to the workspace root, or null when it equals the root."],
      ["X1a", "stripWorkspaceRootPrefix", "Strips a workspace-root prefix plus separator from a terminal cwd."],
      ["Q1", "disposeIfSupported", "Calls Symbol.dispose on a resource when it implements the disposable protocol."],
      ["o0a", "initTerminalSessionModule", "Initializes the terminal host service, session snapshots and workspace binding APIs."],
    ],
  },
  "background-terminal-route-slice": {
    source: INITIAL,
    renames: [
      ["ZGo", "getBackgroundTerminalRouteId", "Builds the background-terminal route id from conversation and terminal ids."],
      ["eKo", "initBackgroundTerminalRouteKindModule", "Initializes the background-terminal route kind."],
      ["tKo", "defineBackgroundTerminalRoute", "Durable background-terminal route with readOutput state and restore handling."],
      ["oKo", "initBackgroundTerminalRouteModule", "Initializes the background-terminal route and its lazy tab import."],
    ],
  },
  "terminal-session-transfer-slice": {
    source: INITIAL,
    renames: [
      ["s0a", "transferTerminalSessionsToConversation", "Moves terminal session state to the target conversation during a tab transfer."],
      ["c0a", "stagePendingWorktreeSessionTransfer", "Stores source workspace state for a pending worktree transfer."],
      ["l0a", "commitPendingWorktreeSessionTransfer", "Applies and clears the staged worktree session transfer for the target conversation."],
      ["d0a", "transferTabsToNewConversation", "Rebuilds tabs for a new conversation and transfers terminal/browser targets."],
      ["v0a", "createTransferredTabTarget", "Creates a new terminal session id or reuses the copied tab path for a transferred tab."],
      ["y0a", "insertTransferredTab", "Inserts a copied tab after the requested tab id in the destination tab set."],
      ["b0a", "resolveTransferredTabPath", "Resolves the path a copied tab should keep in the destination conversation."],
      ["x0a", "openTransferredTab", "Opens the transferred browser or terminal tab through the destination route."],
    ],
  },
  "thread-bottom-panel-slice": {
    source: SHELL_CHROME,
    renames: [
      ["Wc", "ThreadRightPanelChrome", "Right side-panel chrome: active tab, review/git visibility and tab restoration."],
      ["Kc", "ThreadBottomPanelChrome", "Bottom panel chrome with the 'Hide bottom panel' action and tab strip drop destination."],
    ],
  },
  "app-shell-bottom-panel-slice": {
    source: PRIMARY,
    renames: [
      ["BCt", "PanelTabsChrome", "Tab chrome shared by shell panels; renders active tab, sticky regions and drag targets."],
      ["jNt", "BottomPanelContainer", "Resizable bottom panel container using the persisted height and clamped main-content height."],
      ["FNt", "BottomPanelFocusArea", "Focus scope marked with data-app-shell-focus-area=bottom-panel."],
      ["vF", "PanelDialogFrame", "Embedded panel dialog frame with title, failure state and responsive size."],
    ],
  },
  "files-panel-slice": {
    source: INITIAL,
    renames: [
      ["QHa", "FileTreeSlotHost", "Projects file-tree rows into a shadow root through named slots and loading placeholders."],
      ["oUa", "FileTreeHoverProvider", "Tracks hover path and target description for the file tree."],
      ["pUa", "FileTree", "Generic file tree with cwd, git status, icons, rename and click/double-click handling."],
      ["RUa", "SearchInFilesButton", "Files toolbar button that dispatches the host find-in-files request with the workspace root."],
      ["_Ga", "FileBrowserPanel", "Workspace file browser panel with header actions, path actions and file selection."],
      ["yGa", "FileBrowserProvider", "Provides file browser search/focus state, loading flags and resizable tree measurement."],
    ],
  },
  "files-tree-slice": {
    source: INITIAL,
    renames: [
      ["AVa", "FileTreeRowDecoration", "Renders a virtualized row's spacing, git lane, actions and custom decoration."],
      ["jVa", "FileTreeRowRenderer", "Renders one file-tree item with rename input, drag state and context hover."],
      ["NVa", "FileTreeVirtualList", "Virtualized file tree list with overscan, sticky folders, search and drag-and-drop."],
      ["WVa", "FileTreeElementRegistry", "Custom-element registry that manages shadow-DOM slots for file-tree rows."],
    ],
  },
  "files-open-targets-slice": {
    source: INITIAL,
    renames: [
      ["iHa", "WorkspaceFileBrowser", "Side-panel file browser tab content with composition, git status and context menu."],
      ["sHa", "buildPrimaryOpenTargetItems", "Builds the primary 'Open in <target>' menu item for a file."],
      ["cHa", "buildFileContextMenuItems", "Builds copy path, open with, add to chat and save-as context menu items."],
      ["kHa", "openWorkspaceFile", "Dispatches file opening to the host, including durable/cloud file branches."],
      ["Q$", "createOpenedFileModel", "Creates the opened-file model with scope, app path and artifact navigation target."],
      ["lGa", "prepareWorkspaceFile", "Loads file contents and metadata before an open-in target is invoked."],
      ["uGa", "openFileInTarget", "Opens a workspace file in the chosen app or system target."],
      ["dGa", "getSystemOpenInAction", "Returns the platform-specific 'Show in Finder/Explorer' action."],
    ],
  },
  "workspace-agent-files-slice": {
    source: "files-78b140b02df1.js",
    renames: [
      ["Ge", "initFileTreeRowStyles", "Initializes the hashed row/content class names for the workspace-agent file tree."],
      ["Ke", "WorkspaceAgentResourceRow", "Resource row with title, icon, selection and trailing actions."],
      ["Ye", "initWorkspaceAgentFilesUiModule", "Initializes the workspace-agent files UI module."],
      ["Xe", "WorkspaceAgentFilesRoot", "Root view for the workspace-agent files panel with back navigation."],
      ["Ze", "WorkspaceAgentFileBrowser", "Main workspace-agent file browser with rename, delete, upload and refresh flows."],
      ["Qe", "hasNonEmptyName", "True when a file tree node has a non-empty name."],
      ["$e", "getNodeChildren", "Returns the children array of a file tree node."],
      ["et", "FileNameActionDialog", "Dialog for naming, renaming or confirming deletion of a file or folder."],
      ["tt", "FilePreviewDialog", "File preview dialog with download, open-preview and edit-file actions."],
      ["rt", "initWorkspaceAgentFilesModule", "Initializes the workspace-agent files module and its message catalog."],
    ],
  },
  "file-actions-slice": {
    source: "file-actions-20b2bae1bfa9.js",
    renames: [
      ["E", "ArtifactEndResourceActionsGate", "Renders artifact download/open actions only when the host supports them."],
      ["je", "ArtifactEndResourceActions", "End-of-response artifact actions: show in folder, open with, download and editor menu items."],
      ["Me", "incrementCounter", "Returns the next integer for the artifact download mutation key."],
      ["Ne", "getNullQueryData", "Query function returning null for unsupported native-open queries."],
      ["Pe", "getShowInFilesystemLabel", "Returns the platform label for revealing a file in Finder or Explorer."],
      ["k", "initArtifactEndResourceActionsModule", "Initializes the artifact end-resource actions and their host bindings."],
    ],
  },
  "file-viewer-menu-slice": {
    source: "file-viewer-menu-a3e8ac4313a0.js",
    renames: [
      ["H", "getVisibleFileOpenTargets", "Filters the file's open targets to handlers that are currently visible."],
      ["U", "openFileWithHandler", "Applies the selected file-handler preference and opens the file with it."],
      ["G", "FileViewerOpenWithSection", "Menu section listing 'Open with' targets for the current file."],
      ["K", "useFileViewerMenuPortalEffect", "Layout effect that installs and clears the file viewer menu portal container."],
      ["q", "getFileViewerMenu", "Returns the lazily loaded FileViewerMenu component."],
      ["J", "FileViewerMenu", "File viewer menu rendering the open-with section for a path."],
      ["W", "initFileViewerMenuModule", "Initializes the file viewer menu and its lazy boundary."],
    ],
  },
  "file-viewer-menu-state-slice": {
    source: "file-viewer-menu-state-9dc851107db1.js",
    renames: [
      ["o", "initFileViewerMenuStateModule", "Creates the external store that holds the file viewer menu portal element."],
    ],
  },
  "text-file-editor-slice": {
    source: "text-file-editor-tab-content.electron-3dfaf00197f1.js",
    renames: [
      ["Ni", "TextFileEditorTabContent", "Text file editor tab with find bar, large-file read-only state and save/selection errors."],
      ["Vi", "MarkdownFileEditor", "Markdown file editor with source/preview toggle and Copy Markdown action."],
      ["Ji", "DiscardFileChangesDialog", "Confirmation dialog for discarding unsaved text file changes."],
      ["Qi", "TextFileEditorErrorState", "Open/save error state for a text file tab ('Could not open file' / 'Could not save file')."],
      ["da", "TextFileEditorController", "Owns the editor model, read/save lifecycle, dirty state and large-file handling."],
    ],
  },
  "pierre-editor-slice": {
    source: "pierre-file-editor-24b92997461d.js",
    renames: [
      ["nr", "PierreEditorSurface", "Pierre editor host that wires items, edit state and worker options into the editor."],
      ["gr", "FileEditorToolbar", "Editor toolbar with undo/redo, save status and Vim mode toggle."],
      ["Ar", "DiffChangeNavigator", "Previous/next change navigation for a file diff, with compact position display."],
      ["Fr", "getDiffRenderState", "Returns a too-large state when a diff exceeds the highlight size threshold."],
      ["bi", "EditorFindReplacePanel", "Find/replace panel for the Pierre editor."],
      ["ki", "PierreEditorColumn", "Main Pierre editor column with comments, comparison base and first-change reveal."],
    ],
  },
  "sketch-editor-slice": {
    source: "editor-76f8ec768ed8.js",
    renames: [
      ["je", "initSketchEditorMessagesModule", "Initializes the sketch editor message catalog."],
      ["Me", "SketchEditorModalContent", "Sketch editor modal content with tools, presets and dismissal boundary."],
      ["He", "SketchToolbar", "Sketch toolbar with tool selection, leading/trailing controls and mobile tool set."],
      ["Ue", "SketchToolbarButton", "Sketch toolbar button with selected/ghost styles and unified-viewer sizing."],
      ["We", "SketchCanvasView", "Canvas view rendering the sketch document, selection and drawing preview."],
      ["Ye", "SketchEditorDialog", "Dialog shell around the sketch editor with overlay and outside-click handling."],
      ["et", "SketchEditorCanvas", "Editable sketch canvas with text editing, eraser, undo/redo and saving state."],
    ],
  },
  "annotation-comment-editor-slice": {
    source: "annotation-editor-c5dd18b88966.js",
    renames: [
      ["b", "ComposerAnnotationEditor", "Composer annotation dialog with 'Edit annotation' title, comment input and delete/save actions."],
      ["w", "initComposerAnnotationEditorModule", "Initializes the composer annotation editor and its annotation card dependency."],
    ],
  },
  "text-selection-annotation-slice": {
    source: "annotation-editor-981552d71aa9.js",
    renames: [
      ["d", "SelectedTextAnnotationEditor", "Floating note editor for selected response text with optional comment, shake validation and submit modes."],
      ["h", "initSelectedTextAnnotationEditorModule", "Initializes the selected-text annotation editor and its shake helper."],
    ],
  },
  "response-text-annotation-slice": {
    source: "response-text-annotation-editor-ebd4d2d4ecef.js",
    renames: [
      ["v", "ResponseTextAnnotationEditor", "Lazy response annotation editor with suspense fallback and prefetch hook."],
      ["y", "ResponseTextAnnotationEditorFallback", "Loading, failure and close fallback for the response annotation editor."],
      ["w", "initResponseTextAnnotationEditorModule", "Initializes the lazy response annotation editor and its error boundary."],
    ],
  },
  "artifact-preview-slice": {
    source: "artifact-tab-content.electron-757f58d12b55.js",
    renames: [
      ["jo", "ArtifactPreviewRenderer", "Chooses the artifact preview surface or workbook editor for the artifact document."],
      ["Mo", "ArtifactPreviewSurface", "Artifact preview surface with blocked/loading fallbacks and XLSX save-problem warning."],
      ["No", "WorkbookPreviewEditor", "Workbook preview editor with CRDT updates and session key handling."],
      ["qo", "ArtifactTabHeader", "Artifact tab header with refresh and annotation actions for the selected artifact."],
      ["Jo", "ArtifactPreviewTab", "Main artifact tab content composing preview, annotations and experience-specific branches."],
    ],
  },
  "artifact-preview-status-slice": {
    source: "artifact-preview-status-22025ddcf23f.js",
    renames: [
      ["u", "ArtifactPreviewStatus", "Loading skeleton or 'Couldn’t load this preview' error state for an artifact preview."],
    ],
  },
  "library-file-picker-slice": {
    source: "library-file-picker-dialog-content-c6205f26b78f.js",
    renames: [
      ["nn", "applyLibraryPickerFilters", "Stores the selected library filters and secondary account filters in the picker cache."],
      ["ln", "LibraryPickerDialogShell", "Dialog shell for the Add from library picker with close and focus handling."],
      ["un", "LibraryPickerContent", "Stateful library picker body with scope, selection and container rendering."],
      ["W", "LibraryFilePickerDialog", "Main Library file picker dialog with search, filters, grid/list modes and selection."],
      ["Nn", "LibraryPickerLoadingRows", "Loading row/skeleton content for the library picker grid or list."],
      ["Fn", "LibraryPickerFileRow", "File row for the library picker with thumbnail, metadata and selection."],
      ["In", "LibraryPickerFolderIcon", "Folder icon tile with an account badge for the library picker."],
      ["Ln", "LibraryPickerItemPreview", "Preview tile or thumbnail for a library item."],
      ["Rn", "LibraryPickerFolderMetadata", "Folder metadata line with path and account labels."],
      ["zn", "LibraryPickerFileMetadata", "File date/account metadata line for the library picker."],
    ],
  },
  "remote-cwd-picker-slice": {
    source: "remote-cwd-picker-2f71a83ff7a7.js",
    renames: [
      ["F", "browseRemoteDirectory", "Browses a directory on a host workspace-files service, with abort support."],
      ["pe", "useRemoteDirectoryQuery", "React Query hook for browsing one remote directory."],
      ["L", "filterFileTypeFilters", "Normalizes host file-type filters, dropping wildcard accept entries."],
      ["B", "browseExecutorDirectory", "Browses a directory through an executor's hosted-thread files service."],
      ["ge", "fetchRemoteDirectory", "Chooses executor or host browsing for a remote directory."],
      ["G", "RemoteCwdPicker", "Remote working-directory picker with parent navigation, loading and empty states."],
      ["Se", "RemoteDirectoryEntryRow", "Selectable remote file/folder row with active/selected styling."],
      ["we", "RemoteFolderEntryRow", "Folder row for remote directory suggestions."],
      ["be", "getParentDirectoryPath", "Computes the parent directory path for the 'Go to parent folder' action."],
      ["xe", "splitDirectoryPathAndPrefix", "Splits a remote path into its searchable prefix and last segment."],
    ],
  },
  "use-file-picker-slice": {
    source: "use-file-picker-ed94b8e7bfdc.js",
    renames: [
      ["l", "useFilePicker", "Hook returning the host file-picker function with accept filtering, multi-select and title defaults."],
      ["d", "initUseFilePickerModule", "Initializes the file picker hook and its message bindings."],
    ],
  },
};

fs.mkdirSync(outDir, { recursive: true });
const summary = [];
for (const [slice, spec] of Object.entries(specs)) {
  const entries = spec.renames.map(([oldName, newName, evidence, anchor]) => ({
    old: oldName,
    new: newName,
    declAnchor: anchor ?? `function ${oldName}(`,
    originalPath: `webview/assets/${spec.source}`,
    evidence,
    confidence: "high",
  }));
  fs.writeFileSync(
    path.join(outDir, `${slice}.json`),
    `${JSON.stringify(entries, null, 2)}\n`,
  );
  summary.push({ slice, bindings: entries.length });
}
console.log(
  JSON.stringify(
    {
      slices: summary.length,
      bindings: summary.reduce((sum, s) => sum + s.bindings, 0),
      detail: summary,
    },
    null,
    2,
  ),
);
