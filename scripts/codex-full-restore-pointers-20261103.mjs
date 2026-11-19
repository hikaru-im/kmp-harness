#!/usr/bin/env node
/**
 * Write <semantic-root>/original/pointers.json: hash pointers from the semantic
 * slice back to the Phase 2 baseline, without copying the large source files.
 */
import fs from "node:fs";
import path from "node:path";
import crypto from "node:crypto";
import process from "node:process";

function parseArgs(argv) {
  const out = {};
  for (let i = 2; i < argv.length; i += 1) {
    if (!argv[i].startsWith("--")) throw new Error(`unexpected argument: ${argv[i]}`);
    out[argv[i].slice(2)] = argv[i + 1];
    i += 1;
  }
  return out;
}

const args = parseArgs(process.argv);
const root = path.resolve(args.root ?? ".");
const semanticRoot = path.resolve(args["semantic-root"] ?? path.join(root, "03-semantic"));
const phaseDirName = path.basename(semanticRoot);
const phase = phaseDirName.startsWith("12")
  ? 12
  : phaseDirName.startsWith("11")
  ? 11
  : phaseDirName.startsWith("10")
  ? 10
  : phaseDirName.startsWith("09")
  ? 9
  : phaseDirName.startsWith("08")
  ? 8
  : phaseDirName.startsWith("07")
  ? 7
  : phaseDirName.startsWith("06")
    ? 6
    : phaseDirName.startsWith("05")
      ? 5
      : phaseDirName.startsWith("04")
        ? 4
        : 3;
const reportsDir = path.join(semanticRoot, "reports");

function sha256(filePath) {
  return crypto.createHash("sha256").update(fs.readFileSync(filePath)).digest("hex");
}

function describe(filePath) {
  const buffer = fs.readFileSync(filePath);
  return {
    path: path.relative(root, filePath),
    sha256: crypto.createHash("sha256").update(buffer).digest("hex"),
    bytes: buffer.length,
  };
}

const reportNames = fs
  .readdirSync(reportsDir)
  .filter((name) => name.endsWith(".semantic.json"))
  .sort();
const reports = reportNames.map((name) => {
  const reportPath = path.join(reportsDir, name);
  const report = JSON.parse(fs.readFileSync(reportPath, "utf8"));
  return { name, reportPath, report };
});

const baselineFiles = new Map();
const semanticFiles = new Map();
for (const { report } of reports) {
  baselineFiles.set(path.resolve(report.baselineFile), describe(path.resolve(report.baselineFile)));
  semanticFiles.set(path.resolve(report.semanticFile), describe(path.resolve(report.semanticFile)));
}

const slices = [];
for (const { report } of reports) {
  if (!report.sliceManifest) continue;
  const manifest = JSON.parse(fs.readFileSync(report.sliceManifest, "utf8"));
  slices.push({
    manifest: path.relative(root, report.sliceManifest),
    source: describe(path.resolve(manifest.source.path)),
    formattedSlice: describe(path.resolve(report.formattedFile)),
    semanticSlice: describe(path.resolve(report.semanticFile)),
    members: manifest.members.map((member) => ({
      members: member.members,
      sourceStart: member.sourceStart,
      sourceEnd: member.sourceEnd,
      sourceStartLine: member.sourceStartLine,
      sliceStartUtf16: member.sliceStartUtf16,
      utf16Length: member.utf16Length,
    })),
    imports: manifest.imports.reduce((n, group) => n + group.specifiers.length, 0),
    unresolvedTopLevel: manifest.unresolvedTopLevel.length,
  });
}

const appSharedPath = path.join(
  root,
  "02-baseline/formatted/webview/assets/app-shared-601019a330d7.js",
);

const output = {
  generatedAt: new Date().toISOString(),
  generatedBy: "scripts/codex-full-restore-pointers-20261103.mjs",
  phase,
  asar: {
    path: "/Applications/ChatGPT.app/Contents/Resources/app.asar",
    sha256: "335bb4f5bf2dd682d3fec3455b43935ff3b902043b4682fae74d1520aeec89e9",
    bundleId: "com.openai.codex",
    version: "26.1027.61615",
    build: "24141",
  },
  baseline: {
    formattedRoot: "02-baseline/formatted/webview",
    note: `The Phase ${phase} formatted copies and slices are derived from this formatted tree, which Phase 2 proved equivalent to the extracted ASAR originals.`,
  },
  baselineFiles: [...baselineFiles.values()].sort((a, b) => a.path.localeCompare(b.path)),
  semanticFiles: [...semanticFiles.values()].sort((a, b) => a.path.localeCompare(b.path)),
  slices,
  externalReferences: [
    {
      path: "02-baseline/formatted/webview/assets/app-shared-601019a330d7.js",
      ...describe(appSharedPath),
      note: "Not copied into semantic/. Shared helpers and constants imported by the selected slices are documented here instead of copying the full chunk.",
    },
  ],
  reports: reports.map(({ name, reportPath }) => ({
    name,
    path: path.relative(root, reportPath),
    ...describe(reportPath),
  })),
};
const outputPath = path.join(semanticRoot, "original", "pointers.json");
fs.mkdirSync(path.dirname(outputPath), { recursive: true });
fs.writeFileSync(outputPath, JSON.stringify(output, null, 2));
console.log(JSON.stringify({
  output: outputPath,
  baselineFiles: output.baselineFiles.length,
  semanticFiles: output.semanticFiles.length,
  slices: slices.length,
  reports: output.reports.length,
}, null, 2));
