#!/usr/bin/env node
/**
 * Phase 12 (remaining settings pages) bounded slice builder.
 *
 * Every spec names one baseline-formatted chunk that a `/settings/:section/*`
 * lazy route actually imports (resolved from settings-lazy-routes), plus the
 * top-level declarations that carry the page container, its sections, form
 * controls, save/retry/error states, empty/loading states and message ids.
 * `all: true` resolves every top-level declaration of a small satellite chunk.
 *
 * Usage:
 *   node scripts/codex-full-restore-phase12-slices-20261105.mjs [--check]
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
const phaseDir = path.join(restoreRoot, "12-semantic");
const tools = "/tmp/codex-full-restore-tools-HShmrO";
const parser = require(path.join(tools, "node_modules/@babel/parser"));

const specs = [
  {
    name: "notifications-settings",
    source: "notifications-settings-f978129b87c5.js",
    members: [
      "K",
      "ct",
      "lt",
      "pt",
      "He",
    ],
  },
  {
    name: "import-settings",
    source: "import-settings-b00895178720.js",
    members: [
      "$n",
      "fn",
      "Un",
      "Jn",
      "Tn",
      "wn",
    ],
  },
  {
    name: "security-settings",
    source: "security-settings-e577a3e884c6.js",
    members: [
      "wo",
      "To",
      "co",
      "Xr",
    ],
  },
  {
    name: "age-settings",
    source: "age-settings-61207ebb5d33.js",
    members: [
      "we",
      "Ee",
      "$",
      "U",
      "Te",
      "q",
    ],
  },
  {
    name: "agreements-settings",
    source: "agreements-settings-a2eb534d7616.js",
    members: [
      "H",
      "$",
      "K",
      "U",
    ],
  },
  {
    name: "trusted-contact-settings",
    source: "trusted-contact-settings-bdcbbb8929a1.js",
    members: [
      "nt",
      "rt",
      "tt",
      "Ke",
      "gt",
      "He",
    ],
  },
  {
    name: "safety-settings",
    source: "safety-settings-c8bfa39ed443.js",
    members: [
      "Re",
      "Pe",
      "ze",
      "Te",
      "Ee",
      "Be",
    ],
  },
  {
    name: "time-management-settings",
    source: "time-management-settings-9936de27f105.js",
    members: [
      "o",
    ],
  },
  {
    name: "keyboard-shortcuts-settings",
    source: "keyboard-shortcuts-settings-bac62a963482.js",
    members: [
      "Wt",
      "sn",
      "Bt",
      "$t",
      "kt",
      "Nt",
    ],
  },
  {
    name: "codex-micro-settings",
    source: "codex-micro-settings-1e444c83baa2.js",
    members: [
      "ui",
      "di",
      "Jr",
      "Ci",
    ],
  },
  {
    name: "appshots-settings",
    source: "appshots-settings-73a1db0fe376.js",
    members: [
      "we",
      "_e",
      "ye",
      "Se",
      "Ce",
      "Te",
    ],
  },
  {
    name: "voice-settings",
    source: "voice-settings-320cf1212f2c.js",
    members: [
      "ur",
      "Tr",
      "dr",
      "$n",
      "xn",
      "br",
    ],
  },
  {
    name: "device-storage-settings",
    source: "device-storage-49df203d8c6c.js",
    members: [
      "ft",
      "it",
      "Ke",
      "et",
      "Ue",
      "Xe",
    ],
  },
  {
    name: "pets-settings",
    source: "pets-settings-route-bfcfc8a51f0e.js",
    members: [
      "gr",
      "kn",
      "ar",
      "Hn",
      "dr",
      "$n",
    ],
  },
  {
    name: "configuration-panel",
    source: "configuration-panel-45ebea890453.js",
    members: [
      "ur",
      "Hn",
      "Bn",
      "vn",
      "Xn",
      "Un",
    ],
  },
  {
    name: "work-settings-panel",
    source: "panel-26b1f785a3f3.js",
    members: [
      "R",
      "z",
      "B",
      "W",
    ],
  },
  {
    name: "ads-controls-settings",
    source: "route-750a592badca.js",
    members: [
      "Ge",
      "Ve",
      "Ee",
      "De",
      "Fe",
      "je",
    ],
  },
  {
    name: "archived-chats-settings",
    source: "data-controls-237ffa3a96c3.js",
    members: [
      "bn",
      "kn",
      "hn",
      "Rn",
      "Fn",
      "Dn",
    ],
  },
  {
    name: "data-controls-settings",
    source: "data-controls-settings-8581e5145e81.js",
    members: [
      "gn",
      "Rt",
      "nn",
      "dn",
      "en",
    ],
  },
  {
    name: "code-review-settings",
    source: "settings-723cc33ccc8a.js",
    members: [
      "Vr",
      "On",
      "ar",
      "ln",
      "Yn",
      "Hr",
    ],
  },
  {
    name: "cloud-preferences-settings",
    source: "cloud-preferences-settings-1ff207242b24.js",
    members: [
      "F",
      "I",
      "B",
      "ae",
    ],
  },
  {
    name: "cloud-environments-settings",
    source: "cloud-environments-settings-page-b4df36cc3786.js",
    members: [
      "Wt",
      "Kt",
      "yt",
      "Rt",
      "Tt",
      "Et",
    ],
  },
  {
    name: "personalization-settings",
    source: "personalization-settings-7ef3fe8695e0.js",
    members: [
      "ka",
      "Na",
      "Qr",
      "ui",
    ],
  },
  {
    name: "chronicle-settings",
    source: "chronicle-settings-page-3f9a395ea72a.js",
    members: [
      "On",
      "Ht",
      "un",
      "sn",
      "mn",
    ],
  },
  {
    name: "usage-settings",
    source: "page-a10a550636a7.js",
    members: [
      "ls",
      "so",
      "Ro",
      "Zi",
      "fi",
    ],
  },
  {
    name: "debug-settings",
    source: "debug-settings-ce35e654cb4e.js",
    members: [
      "P",
      "I",
      "F",
    ],
  },
  {
    name: "computer-use-settings",
    source: "computer-use-settings-981697fabc1b.js",
    members: [
      "Vr",
      "Cr",
      "mr",
      "ti",
      "ni",
    ],
  },
  {
    name: "browser-use-settings",
    source: "browser-use-settings-802dca83451c.js",
    members: [
      "ua",
      "Ur",
      "oi",
      "fa",
    ],
  },
  {
    name: "browser-extension-settings",
    source: "browser-extension-settings-289e421d50e0.js",
    members: [
      "Q",
      "Ve",
      "Z",
      "je",
      "Pe",
      "K",
    ],
  },
  {
    name: "local-environments-settings",
    source: "local-environments-settings-page-a6d95bf07320.js",
    members: [
      "p",
    ],
  },
  {
    name: "external-section-settings",
    source: "settings-external-section-df17affc8af1.js",
    members: [
      "l",
    ],
  },
  {
    name: "hooks-settings",
    source: "hooks-settings-dcc6d1fed9c5.js",
    members: [
      "qt",
      "Dt",
      "ht",
      "ft",
      "At",
      "vt",
    ],
  },
  {
    name: "file-type-handlers-settings",
    source: "file-type-handlers-657061e9bee1.js",
    members: [
      "J",
      "W",
      "le",
      "U",
    ],
  },
  {
    name: "skills-settings-route",
    source: "skills-settings-2afc96506181.js",
    members: [
      "s",
    ],
  },
  {
    name: "cloud-computer-settings",
    source: "settings-8c2f2dacb08c.js",
    members: [
      "jt",
      "Mt",
      "ft",
      "nt",
      "bt",
      "pt",
    ],
  },
  {
    name: "passwords-settings",
    source: "settings-8dd7ca3a0b54.js",
    members: [
      "pt",
      "tt",
      "mt",
      "Ze",
      "ot",
      "lt",
    ],
  },
  {
    name: "team-settings",
    source: "team-settings-page-35cc479e7ba6.js",
    members: [
      "Wr",
      "Kr",
      "sr",
      "xr",
      "pr",
    ],
  },
  {
    name: "parental-controls-settings",
    source: "settings-eb3d9188c741.js",
    members: [
      "Fe",
      "Ve",
      "Oe",
      "ke",
      "Re",
      "ze",
    ],
  },
  {
    name: "rosalind-settings",
    source: "settings-a4d0fe674176.js",
    members: [
      "d",
    ],
  },
  {
    name: "codex-cloud-settings",
    source: "settings-c45e6ea36c88.js",
    members: [
      "Qn",
      "Ln",
      "er",
      "nr",
      "En",
      "$n",
    ],
  },
  {
    name: "hooks-settings-copy",
    source: "hooks-settings-copy-ac22c0a8ed0d.js",
    all: true,
  },
  {
    name: "hooks-settings-model",
    source: "hooks-settings-model-139d6975b199.js",
    all: true,
  },
  {
    name: "hooks-settings-source-label",
    source: "hooks-settings-source-label-4e76521f983f.js",
    all: true,
  },
  {
    name: "hooks-settings-route",
    source: "hooks-settings-route-91e970e20353.js",
    all: true,
  },
  {
    name: "hooks-settings-navigation",
    source: "hooks-settings-navigation-211788f711da.js",
    all: true,
  },
  {
    name: "import-settings-gate",
    source: "import-settings-gate-2dda22aea76f.js",
    all: true,
  },
  {
    name: "age-settings-state",
    source: "age-settings-state-b9e98e16375a.js",
    all: true,
  },
  {
    name: "settings-availability",
    source: "settings-availability-ba7737bda596.js",
    all: true,
  },
  {
    name: "settings-visible-sections",
    source: "use-visible-settings-sections-3d650a480b6f.js",
    members: [
      "za",
      "ea",
      "Qa",
      "ta",
      "no",
      "Va",
      "Za",
      "$i",
      "fo",
      "ro",
    ],
  },
];

const checkOnly = process.argv.includes("--check");

function topLevelNames(sourcePath) {
  const text = fs.readFileSync(sourcePath, "utf8");
  const ast = parser.parse(text, { sourceType: "module", plugins: ["jsx"] });
  const names = [];
  for (const node of ast.program.body) {
    const decl = node.type === "ExportNamedDeclaration" ? node.declaration : node;
    if (!decl) continue;
    if (decl.type === "FunctionDeclaration" && decl.id) names.push(decl.id.name);
    else if (decl.type === "VariableDeclaration") {
      for (const d of decl.declarations) {
        if (d.id.type === "Identifier") names.push(d.id.name);
      }
    }
  }
  return [...new Set(names)].filter((name) => !name.startsWith("__"));
}

if (!checkOnly) {
  for (const sub of ["formatted/webview/assets", "semantic/webview/assets", "renames", "reports", "sources", "original"]) {
    fs.mkdirSync(path.join(phaseDir, sub), { recursive: true });
  }
}

const results = [];
for (const spec of specs) {
  const sourcePath = path.join(baseline, spec.source);
  if (!fs.existsSync(sourcePath)) throw new Error(`missing baseline chunk: ${spec.source}`);
  const members = spec.all ? topLevelNames(sourcePath) : spec.members;
  if (!members || members.length === 0) throw new Error(`spec ${spec.name} resolved no members`);
  const slicePath = path.join(phaseDir, "formatted/webview/assets", `${spec.name}-slice.js`);
  const manifestPath = path.join(phaseDir, "reports", `${spec.name}-slice.json`);
  results.push({ name: spec.name, source: spec.source, members });
  if (checkOnly) continue;
  execFileSync(
    process.execPath,
    [
      path.join(root, "scripts/codex-full-restore-slice-20261103.mjs"),
      "--source", sourcePath,
      "--members", members.join(","),
      "--output", slicePath,
      "--manifest", manifestPath,
      "--phase", "12",
      "--tools", tools,
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
