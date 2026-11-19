#!/usr/bin/env node
/**
 * Build <semantic-root>/binding-map.json from the semantic reports and the
 * independent verification results.
 *
 * Each row keeps the original name, semantic name, owning scope, original and
 * target positions (UTF-16/UTF-8/line/column), evidence, confidence and the
 * verification status. No new renaming happens here.
 */
import fs from "node:fs";
import path from "node:path";
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
const verification = JSON.parse(
  fs.readFileSync(path.join(semanticRoot, "verification.json"), "utf8"),
);
const reportsDir = path.join(semanticRoot, "reports");
const reports = new Map();
for (const name of fs.readdirSync(reportsDir).filter((entry) => entry.endsWith(".semantic.json"))) {
  const report = JSON.parse(fs.readFileSync(path.join(reportsDir, name), "utf8"));
  reports.set(name, report);
}

function relative(filePath) {
  return path.relative(root, path.resolve(filePath));
}

const rows = [];
for (const binding of verification.bindings) {
  const report = reports.get(binding.report);
  if (!report) throw new Error(`missing report ${binding.report}`);
  rows.push({
    file: relative(report.baselineFile),
    formattedFile: relative(report.formattedFile),
    semanticFile: relative(report.semanticFile),
    originalName: binding.old,
    semanticName: binding.new,
    owningScope: binding.scope,
    oldKind: binding.oldKind,
    originalPosition: binding.declaration,
    targetPosition: binding.targetDeclaration,
    referenceCount: binding.referenceCount,
    constantViolationCount: binding.constantViolationCount,
    evidence: binding.evidence,
    confidence: binding.confidence,
    verification: binding.status,
  });
}

rows.sort((a, b) => a.file.localeCompare(b.file) || a.originalPosition.utf16Offset - b.originalPosition.utf16Offset);

const byFile = {};
const byConfidence = {};
for (const row of rows) {
  byFile[row.file] ??= { renames: 0, high: 0, medium: 0 };
  byFile[row.file].renames += 1;
  byFile[row.file][row.confidence] = (byFile[row.file][row.confidence] ?? 0) + 1;
  byConfidence[row.confidence] = (byConfidence[row.confidence] ?? 0) + 1;
}

const output = {
  generatedAt: new Date().toISOString(),
  generatedBy: "scripts/codex-full-restore-binding-map-20261103.mjs",
  phase,
  scope:
    phase === 12
      ? "Phase 12 remaining settings-page slices only: notifications, import, security, age, agreements, trusted-contact, safety/time-management, keyboard-shortcuts, Codex Micro, appshots, voice, device-storage, pets, configuration/work-settings, ads-controls, archived-chats, data-controls, code-review, cloud-preferences, cloud-environments, personalization, chronicle, usage, debug, computer-use, browser-use/extension, local-environments, external-section, hooks, file-type-handlers, skills route, cloud-computer, passwords, team, parental-controls, rosalind and codex-cloud pages, plus the hooks settings model/copy/source-label/route/navigation helpers, the import-settings gate, the age-settings store, settings availability and visible-sections plumbing. Names not listed here were left unchanged and are marked unresolved in verification.json."
      : phase === 11
      ? "Phase 11 terminal and file-panel slices only: terminal tab/panel, Cloud terminal states, PTY snapshots/reconnect, shell labels, background/thread terminals, xterm addons, bottom panel, file tree/browser/open targets, workspace-agent files, artifact actions, text/Markdown/Pierre editors, sketch/annotation editors, artifact preview, library/remote pickers. Names not listed here were left unchanged and are marked unresolved in verification.json."
      : phase === 10
      ? "Phase 10 responsive and mobile-layout slices only: breakpoint/capability hooks, app-shell frame and sidebar chrome, settings/searchable/projects layouts, dialog sheets, popover positioning, composer attachments, editor toolbar, tab strip, coarse-pointer rows, automations and MCP/plugin rows. This Electron bundle has responsive web layout but no native mobile shell. Names not listed here were left unchanged and are marked unresolved in verification.json."
      : phase === 8
      ? "Phase 8 scheduled-task and automation slices only: `/scheduled` page, list/filter/groups, rows, detail pane and composer, automations layout/create/history, dialog and schedule editor, targeting and notification policy, side-panel tab, actions menu, submit validation, run results/run-now, delete confirmation, search and team row. Names not listed here were left unchanged and are marked unresolved in verification.json."
      : phase === 7
      ? "Phase 7 Git and worktree slices only: Review/Changes panel, diff rendering boundary, commit/branch switcher, worktrees settings, worktree creation/restore and Git settings. Names not listed here were left unchanged and are marked unresolved in verification.json."
      : phase === 6
      ? "Phase 6 settings shell, settings pages and model-provider (gateways) slice only. Names not listed here were left unchanged and are marked unresolved in verification.json."
      : phase === 5
        ? "Phase 5 conversation page, turn list, message rendering and composer slice only. Names not listed here were left unchanged and are marked unresolved in verification.json."
        : phase === 4
          ? "Phase 4 home/space sidebar and route landing-page container slice only. Names not listed here were left unchanged and are marked unresolved in verification.json."
          : "Phase 3 shell/navigation slice only. Names not listed here were left unchanged and are marked unresolved in verification.json.",
  totals: {
    renames: rows.length,
    files: Object.keys(byFile).length,
    byConfidence,
    verified: rows.filter((row) => row.verification === "pass").length,
    failed: rows.filter((row) => row.verification !== "pass").length,
  },
  byFile,
  bindings: rows,
};
const outputPath = path.join(semanticRoot, "binding-map.json");
fs.writeFileSync(outputPath, JSON.stringify(output, null, 2));
console.log(JSON.stringify({ output: outputPath, ...output.totals }, null, 2));
