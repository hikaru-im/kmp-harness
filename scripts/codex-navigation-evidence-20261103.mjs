// Persist exact bounded source lookups; analyzed members remain inert text data.
import fs from "node:fs";
import path from "node:path";
import { execFileSync } from "node:child_process";
const [toolDir, root] = process.argv.slice(2);
const inspector = path.resolve("scripts/codex-navigation-inspect-20261103.mjs");
const groups = [
  [
    "webview/assets/app-initial-18b189e867e7.js",
    [
      "l0r",
      "UXr",
      "u0r",
      "f0r",
      "dO",
      "e0r",
      "r0r",
      "X1r",
      "Z1r",
      "qXr",
      "TIt",
      "Lx",
      "ye",
      "hSt",
      "B9e",
      "nfs",
      "n0r",
      "o0r",
      "sde",
      "Rge",
      "uAn",
    ],
  ],
  [
    "webview/assets/app-primary-75d53eef2374.js",
    [
      "RD",
      "km",
      "JP",
      "vAt",
      "GP",
      "oAt",
      "dAt",
      "fAt",
      "mAt",
      "hAt",
      "SAt",
      "My",
      "KP",
      "Mh",
      "qP",
      "scr",
      "iAt",
      "vve",
      "$sr",
    ],
  ],
  [
    "webview/assets/app-shared-601019a330d7.js",
    ["UBt", "xq", "n5e", "K3e", "q3e", "Ug", "Xxt", "hSt", "Jwt"],
  ],
  [
    "webview/assets/app-runtime-b179428fcc1e.js",
    ["aT", "FY", "rY"],
  ],
  ["webview/assets/rolldown-runtime-428824ca0286.js", ["o"]],
];
const rows = [];
for (const [file, names] of groups) {
  const raw = execFileSync(
    process.execPath,
    [inspector, toolDir, root, file, ...names],
    { encoding: "utf8", maxBuffer: 64 * 1024 * 1024 },
  );
  rows.push(JSON.parse(raw));
}
const result = {
  source_asar_sha256: JSON.parse(
    fs.readFileSync(path.join(root, "selection.json"), "utf8"),
  ).source_asar_sha256,
  scope:
    "Exact binding declarations, writes, selected references and module dependencies from original packed members; no execution.",
  files: rows,
};
fs.writeFileSync(
  path.join(root, "source-evidence.json"),
  JSON.stringify(result, null, 2) + "\n",
);
console.log(
  JSON.stringify({
    files: rows.length,
    requests: rows.reduce((n, r) => n + r.requests.length, 0),
    missing: rows.reduce(
      (n, r) => n + r.requests.filter((x) => x.missing).length,
      0,
    ),
  }),
);
