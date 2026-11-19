#!/usr/bin/env node
/**
 * Phase 10 (responsive + mobile) bounded slice builder.
 *
 * Each spec names one baseline-formatted chunk and either an explicit member
 * list or `all: true`. `all` resolves every top-level declaration in the chunk
 * (small dedicated modules only). The script shells out to the shared slice
 * tool so member text keeps the same byte-for-byte contract as phases 3–9.
 *
 * Usage:
 *   node scripts/codex-full-restore-phase10-slices-20261104.mjs [--check]
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
const phaseDir = path.join(restoreRoot, "10-semantic");
const tools = "/tmp/codex-full-restore-tools-HShmrO";
const parser = require(path.join(tools, "node_modules/@babel/parser"));

const specs = [
  {
    name: "media-query-hooks",
    source: "app-shared-601019a330d7.js",
    members: ["Nx", "ain", "uin", "din", "min", "mye", "pye", "hye"],
  },
  {
    name: "reduced-motion",
    source: "app-shared-601019a330d7.js",
    members: ["hin", "gin", "_in", "xin", "Sin", "s2"],
  },
  {
    name: "touch-device",
    source: "app-shared-601019a330d7.js",
    members: ["iOn", "aOn", "oOn", "sOn"],
  },
  {
    name: "mobile-layout-store",
    source: "app-initial-18b189e867e7.js",
    members: ["LRr", "RRr", "zRr", "VRr", "BRr"],
  },
  {
    name: "app-shell-frame",
    source: "app-primary-75d53eef2374.js",
    members: ["Ejt", "bjt"],
  },
  {
    name: "app-shell-left-panel",
    source: "app-primary-75d53eef2374.js",
    members: ["iF", "ajt"],
  },
  {
    name: "floating-left-panel",
    source: "app-primary-75d53eef2374.js",
    members: ["xjt", "Sjt", "Cjt", "wjt", "Tjt"],
  },
  {
    name: "sidebar-chrome",
    source: "app-primary-75d53eef2374.js",
    members: ["QP", "TAt", "ZP", "EAt", "DAt", "OAt", "kAt", "AAt"],
  },
  {
    name: "sidebar-navigation-header",
    source: "app-primary-75d53eef2374.js",
    members: ["vIn", "xIn", "CIn"],
  },
  { name: "searchable-page-layout", source: "searchable-page-layout-6693d2a8dff5.js", all: true },
  { name: "settings-content-layout", source: "settings-content-layout-ac451a456013.js", all: true },
  {
    name: "dialog-content",
    source: "app-shared-601019a330d7.js",
    members: ["FSn", "c6", "ISn", "LSn", "RSn", "zSn", "BSn", "VSn", "HSn"],
  },
  { name: "popover-positioning", source: "popover-12c80c240995.js", all: true },
  { name: "responsive-sheet-caller", source: "browser-composer-disclaimer-8c236022152a.js", all: true },
  {
    name: "composer-attachments",
    source: "composer-content-b926d24ef280.js",
    members: ["GP"],
  },
  {
    name: "editor-mobile-toolbar",
    source: "editor-76f8ec768ed8.js",
    members: ["tt", "nt", "rt", "it", "at"],
  },
  {
    name: "tab-strip",
    source: "thread-app-shell-chrome-e7e38c7ea254.js",
    members: ["yc"],
  },
  {
    name: "side-panel-pointer-coarse",
    source: "side-panel-b2f1ecc3aff1.js",
    members: ["ps", "Ls"],
  },
  {
    name: "projects-index",
    source: "projects-index-page-78237015641b.js",
    members: ["Ti"],
  },
  {
    name: "automation-row",
    source: "automation-row-37c157e22263.js",
    members: ["Ae", "We"],
  },
  {
    name: "settings-form",
    source: "settings-form-d03684fa1af2.js",
    members: ["ln", "un", "dn"],
  },
  {
    name: "settings-analytics",
    source: "settings-analytics-telemetry-f26bc8be0b57.js",
    members: ["Be"],
  },
  {
    name: "settings-plugin-controls",
    source: "settings-plugin-controls-cbca110a69d1.js",
    members: ["wt"],
  },
  {
    name: "plugin-mcp-row",
    source: "plugins-settings-7a89487c0c4c.js",
    members: ["wa"],
  },
  {
    name: "schedule-dialog",
    source: "dialog-c06baa494c77.js",
    members: ["Rn", "zn", "Bn", "Vn", "Hn", "Un", "Wn", "Gn", "Kn", "qn", "$n"],
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
      "10",
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
