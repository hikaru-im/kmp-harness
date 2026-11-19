#!/usr/bin/env node
/**
 * Phase 11 (terminal + file panel) bounded slice builder.
 *
 * Each spec names one baseline-formatted chunk and either an explicit member
 * list or `all: true`. `all` resolves every top-level declaration in the chunk
 * (small dedicated modules only). The script shells out to the shared slice
 * tool so member text keeps the same byte-for-byte contract as phases 3–10.
 *
 * Usage:
 *   node scripts/codex-full-restore-phase11-slices-20261104.mjs [--check]
 */
import fs from "node:fs";
import path from "node:path";
import { createRequire } from "node:module";
import { execFileSync } from "node:child_process";
import { fileURLToPath } from "node:url";

const require = createRequire(import.meta.url);
const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, "..");
const restoreRoot = path.join(
  root,
  "references/codex-26.1027.61615-24141/full-restore",
);
const baseline = path.join(restoreRoot, "02-baseline/formatted/webview/assets");
const phaseDir = path.join(restoreRoot, "11-semantic");
const tools = "/tmp/codex-full-restore-tools-HShmrO";
const parser = require(path.join(tools, "node_modules/@babel/parser"));

const specs = [
  {
    name: "terminal-tab",
    source: "terminal-tab-d3a5a5a51dbb.js",
    all: true,
  },
  {
    name: "terminal-panel",
    source: "terminal-panel-7bc13dac3ecc.js",
    all: true,
  },
  {
    name: "xterm-addons",
    source: "xterm-window-zoom-6ea5709641f4.js",
    all: true,
  },
  {
    name: "xterm-output-panel",
    source: "xterm-output-panel-b1a344e3de5f.js",
    all: true,
  },
  {
    name: "background-terminal",
    source: "background-terminal-28355224b010.js",
    all: true,
  },
  {
    name: "background-terminal-tab",
    source: "local-conversation-background-terminal-tab-15c64f0a7d5b.js",
    all: true,
  },
  {
    name: "thread-terminal-tool",
    source: "thread-terminal-390395ede9ad.js",
    all: true,
  },
  {
    name: "shell-label",
    source: "shell-label-1f18309df00d.js",
    all: true,
  },
  {
    name: "terminal-warning",
    source: "terminal-workspace-warning-state-2125af67019f.js",
    all: true,
  },
  {
    name: "terminal-route",
    source: "app-initial-18b189e867e7.js",
    members: [
      "C1a",
      "X1",
      "w1a",
      "P1a",
      "F1a",
      "L1a",
      "H1a",
      "K1a",
      "q1a",
      "Y1a",
      "X1a",
      "Q1",
      "o0a",
    ],
  },
  {
    name: "background-terminal-route",
    source: "app-initial-18b189e867e7.js",
    members: ["ZGo", "eKo", "tKo", "oKo"],
  },
  {
    name: "terminal-session-transfer",
    source: "app-initial-18b189e867e7.js",
    members: [
      "s0a",
      "c0a",
      "l0a",
      "d0a",
      "v0a",
      "y0a",
      "b0a",
      "x0a",
    ],
  },
  {
    name: "thread-bottom-panel",
    source: "thread-app-shell-chrome-e7e38c7ea254.js",
    members: ["Wc", "Kc"],
  },
  {
    name: "app-shell-bottom-panel",
    source: "app-primary-75d53eef2374.js",
    members: ["jNt", "FNt", "vF", "BCt"],
  },
  {
    name: "files-panel",
    source: "app-initial-18b189e867e7.js",
    members: ["_Ga", "yGa", "pUa", "QHa", "oUa", "RUa"],
  },
  {
    name: "files-tree",
    source: "app-initial-18b189e867e7.js",
    members: ["NVa", "WVa", "jVa", "AVa"],
  },
  {
    name: "files-open-targets",
    source: "app-initial-18b189e867e7.js",
    members: ["iHa", "cHa", "sHa", "Q$", "uGa", "lGa", "dGa", "kHa"],
  },
  {
    name: "workspace-agent-files",
    source: "files-78b140b02df1.js",
    all: true,
  },
  {
    name: "file-actions",
    source: "file-actions-20b2bae1bfa9.js",
    all: true,
  },
  {
    name: "file-viewer-menu",
    source: "file-viewer-menu-a3e8ac4313a0.js",
    all: true,
  },
  {
    name: "file-viewer-menu-state",
    source: "file-viewer-menu-state-9dc851107db1.js",
    all: true,
  },
  {
    name: "text-file-editor",
    source: "text-file-editor-tab-content.electron-3dfaf00197f1.js",
    members: ["da", "Ni", "Vi", "Ji", "Qi"],
  },
  {
    name: "pierre-editor",
    source: "pierre-file-editor-24b92997461d.js",
    members: ["nr", "gr", "Ar", "Fr", "bi", "ki"],
  },
  {
    name: "sketch-editor",
    source: "editor-76f8ec768ed8.js",
    members: ["je", "Me", "He", "Ue", "We", "Ye", "et"],
  },
  {
    name: "annotation-comment-editor",
    source: "annotation-editor-c5dd18b88966.js",
    all: true,
  },
  {
    name: "text-selection-annotation",
    source: "annotation-editor-981552d71aa9.js",
    all: true,
  },
  {
    name: "response-text-annotation",
    source: "response-text-annotation-editor-ebd4d2d4ecef.js",
    all: true,
  },
  {
    name: "artifact-preview",
    source: "artifact-tab-content.electron-757f58d12b55.js",
    members: ["qo", "jo", "Mo", "No", "Jo"],
  },
  {
    name: "artifact-preview-status",
    source: "artifact-preview-status-22025ddcf23f.js",
    all: true,
  },
  {
    name: "library-file-picker",
    source: "library-file-picker-dialog-content-c6205f26b78f.js",
    members: ["W", "Fn", "In", "Ln", "Rn", "zn", "Nn", "nn", "ln", "un"],
  },
  {
    name: "remote-cwd-picker",
    source: "remote-cwd-picker-2f71a83ff7a7.js",
    members: ["G", "Se", "we", "B", "L", "F", "be", "xe", "ge", "pe"],
  },
  {
    name: "use-file-picker",
    source: "use-file-picker-ed94b8e7bfdc.js",
    all: true,
  },
];

const checkOnly = process.argv.includes("--check");

function topLevelNames(sourcePath) {
  const source = fs.readFileSync(sourcePath, "utf8");
  const ast = parser.parse(source, {
    sourceType: "module",
    plugins: ["jsx", "flow"],
    errorRecovery: false,
  });
  const names = [];
  for (const node of ast.program.body) {
    if (node.type === "FunctionDeclaration" && node.id) names.push(node.id.name);
    if (node.type === "ClassDeclaration" && node.id) names.push(node.id.name);
    if (node.type === "VariableDeclaration") {
      for (const d of node.declarations) {
        if (d.id.type === "Identifier") names.push(d.id.name);
        if (d.id.type === "ObjectPattern") {
          for (const p of d.id.properties) {
            if (p.value?.type === "Identifier") names.push(p.value.name);
          }
        }
      }
    }
  }
  return [...new Set(names)].filter((name) => !name.startsWith("__"));
}

if (!checkOnly) {
  fs.mkdirSync(path.join(phaseDir, "formatted/webview/assets"), { recursive: true });
  fs.mkdirSync(path.join(phaseDir, "semantic/webview/assets"), { recursive: true });
  fs.mkdirSync(path.join(phaseDir, "renames"), { recursive: true });
  fs.mkdirSync(path.join(phaseDir, "reports"), { recursive: true });
  fs.mkdirSync(path.join(phaseDir, "sources"), { recursive: true });
  fs.mkdirSync(path.join(phaseDir, "original"), { recursive: true });
}

const results = [];
for (const spec of specs) {
  const sourcePath = path.join(baseline, spec.source);
  const members = spec.all ? topLevelNames(sourcePath) : spec.members;
  if (!members || members.length === 0) {
    throw new Error(`spec ${spec.name} resolved no members`);
  }
  const slicePath = path.join(
    phaseDir,
    "formatted/webview/assets",
    `${spec.name}-slice.js`,
  );
  const manifestPath = path.join(phaseDir, "reports", `${spec.name}-slice.json`);
  results.push({ name: spec.name, source: spec.source, members });
  if (checkOnly) continue;
  execFileSync(
    process.execPath,
    [
      path.join(root, "scripts/codex-full-restore-slice-20261103.mjs"),
      "--source",
      sourcePath,
      "--members",
      members.join(","),
      "--output",
      slicePath,
      "--manifest",
      manifestPath,
      "--phase",
      "11",
      "--tools",
      tools,
    ],
    { stdio: "inherit" },
  );
}

console.log(
  JSON.stringify(
    {
      checkOnly,
      slices: results.length,
      members: results.reduce((sum, r) => sum + r.members.length, 0),
      detail: results,
    },
    null,
    2,
  ),
);
