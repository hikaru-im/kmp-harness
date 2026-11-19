#!/usr/bin/env node
/**
 * Resolve every Phase 4 UI claim to an exact position in the baseline text.
 *
 * Claims live in <semantic-root>/sources/ui-claims.json. Each anchor must occur in
 * the named baseline file (an explicit `occurrence` is required when it is not
 * unique). The output records UTF-16, UTF-8, line/column and the exact excerpt
 * so every behavioral statement in the phase report can be located.
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
const claims = JSON.parse(
  fs.readFileSync(path.join(semanticRoot, "sources", "ui-claims.json"), "utf8"),
);
const verification = JSON.parse(
  fs.readFileSync(path.join(semanticRoot, "verification.json"), "utf8"),
);

function lineColumnIn(text, offset) {
  let line = 1;
  let lastNewline = -1;
  for (let i = 0; i < offset; i += 1) {
    if (text[i] === "\n") {
      line += 1;
      lastNewline = i;
    }
  }
  return { line, column: offset - lastNewline - 1 };
}

function occurrences(text, needle) {
  const result = [];
  let from = 0;
  while (from <= text.length) {
    const found = text.indexOf(needle, from);
    if (found < 0) break;
    result.push(found);
    from = found + 1;
  }
  return result;
}

const results = [];
const failures = [];
for (const claim of claims) {
  const filePath = path.join(root, "02-baseline", "formatted", claim.file);
  const text = fs.readFileSync(filePath, "utf8");
  const all = occurrences(text, claim.anchor);
  const occurrence = claim.occurrence ?? 0;
  const found = all.length > occurrence;
  const problems = [];
  if (!found) {
    problems.push(`anchor not found (${all.length} occurrences): ${claim.anchor}`);
  } else if (all.length > 1 && claim.occurrence == null) {
    problems.push(`anchor is not unique (${all.length} occurrences) and no occurrence was specified`);
  }
  let evidence = null;
  if (found) {
    const offset = all[occurrence];
    const { line, column } = lineColumnIn(text, offset);
    evidence = {
      utf16Offset: offset,
      byteOffset: Buffer.byteLength(text.slice(0, offset), "utf8"),
      line,
      column,
      excerpt: text.slice(offset, offset + claim.anchor.length),
    };
  }
  if (problems.length > 0) failures.push({ id: claim.id, problems });
  results.push({
    id: claim.id,
    category: claim.category,
    file: claim.file,
    claim: claim.claim,
    confidence: claim.confidence,
    anchor: claim.anchor,
    occurrence: found ? occurrence : null,
    totalOccurrences: all.length,
    evidence,
    status: problems.length === 0 ? "pass" : "fail",
    problems,
  });
}

const byCategory = {};
for (const result of results) {
  byCategory[result.category] ??= { claims: 0, passed: 0 };
  byCategory[result.category].claims += 1;
  if (result.status === "pass") byCategory[result.category].passed += 1;
}

const output = {
  generatedAt: new Date().toISOString(),
  generatedBy: "scripts/codex-full-restore-ui-evidence-20261103.mjs",
  phase,
  scope:
    phase === 12
      ? "Phase 12 remaining settings-page slices only: notifications, import, security, age, agreements, trusted-contact, safety/time-management, keyboard-shortcuts, Codex Micro, appshots, voice, device-storage, pets, configuration/work-settings, ads-controls, archived-chats, data-controls, code-review, cloud-preferences, cloud-environments, personalization, chronicle, usage, debug, computer-use, browser-use/extension, local-environments, external-section, hooks, file-type-handlers, skills route, cloud-computer, passwords, team, parental-controls, rosalind and codex-cloud pages, plus hooks helper modules and the visible-sections plumbing. Claims are static source locations, not runtime observations."
      : phase === 11
      ? "Phase 11 terminal and file-panel slices only: terminal tab/panel, Cloud terminal states, PTY snapshots/reconnect, shell labels, background/thread terminals, xterm addons/CSS, bottom panel, app-shell file tree, file open targets, workspace-agent files, text/Markdown/Pierre editors, sketch and annotation editors, artifact preview, library and remote pickers. File search UI is host-side and xterm internals are vendored. Claims are static source locations, not runtime observations."
      : phase === 10
      ? "Phase 10 responsive and mobile-layout slices only: breakpoint/capability hooks, app-shell frame and sidebar chrome, settings/searchable/projects layouts, dialog sheets, popover positioning, composer attachments, editor toolbar, tab strip, coarse-pointer rows, automations and MCP/plugin rows, plus referenced CSS media/container queries. This Electron bundle has responsive web layout but no native mobile shell. Claims are static source locations, not runtime observations."
      : phase === 9
      ? "Phase 9 plugin and account slices only: plugin directory/cards, plugin detail and settings routes, install/remove/uninstall, marketplaces, MCP servers/auth, team add-plugins, remote connections, account menu/settings/deletion, profile, login/select-workspace and browser logout. Claims are static source locations, not runtime observations."
      : phase === 8
      ? "Phase 8 scheduled-task and automation slices only: `/scheduled` page, list/filter/groups, rows, detail pane and composer, automations layout/create/history, dialog and schedule editor, targeting and notification policy, side-panel tab, actions menu, submit validation, run results/run-now, delete confirmation, search and team row. Claims are static source locations, not runtime observations."
      : phase === 7
      ? "Phase 7 Git and worktree slices only: Review/Changes panel, diff rendering boundary, commit/branch switcher, worktrees settings, worktree creation/restore and Git settings. Claims are static source locations, not runtime observations."
      : phase === 6
      ? "Phase 6 settings shell, settings pages and model-provider (gateways) slice only. Claims are static source locations, not runtime observations."
      : phase === 5
        ? "Phase 5 conversation page, turn list, message rendering and composer slice only. Claims are static source locations, not runtime observations."
        : phase === 4
          ? "Phase 4 home/space sidebar and route landing-page container slice only. Claims are static source locations, not runtime observations."
          : "Phase 3 shell/navigation slice only. Claims are static source locations, not runtime observations.",
  sourceRoot: "02-baseline/formatted/webview",
  summary: {
    claims: results.length,
    passed: results.filter((result) => result.status === "pass").length,
    failed: failures.length,
    byCategory,
  },
  claims: results,
  css: verification.css,
  failures,
};
const outputPath = path.join(semanticRoot, "ui-evidence.json");
fs.writeFileSync(outputPath, JSON.stringify(output, null, 2));
console.log(JSON.stringify({ output: outputPath, ...output.summary }, null, 2));
process.exitCode = failures.length === 0 ? 0 : 1;
