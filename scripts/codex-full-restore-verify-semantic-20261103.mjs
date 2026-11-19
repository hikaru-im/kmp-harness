#!/usr/bin/env node
/**
 * Independently verify a semantic slice phase (03, 04 or 05).
 *
 * For every rename report it:
 *  - re-reads the baseline and semantic files;
 *  - re-checks declaration/reference/constant-violation evidence positions
 *    (UTF-16 offset, UTF-8 byte offset, line, column, excerpt);
 *  - resolves the renamed identifier in the semantic AST and proves it is the
 *    owning scope's own binding (no accidental capture);
 *  - checks that every target reference/violation resolves to that binding.
 *
 * It also re-parses the selected CSS files and locates the selectors and custom
 * properties used by the shell/navigation slice. No bundle code is executed.
 *
 * Usage:
 *   node scripts/codex-full-restore-verify-semantic-20261103.mjs \
 *     --root references/codex-26.1027.61615-24141/full-restore \
 *     --semantic-root 04-semantic \
 *     --output .../04-semantic/verification.json [--tools DIR]
 */
import fs from "node:fs";
import path from "node:path";
import crypto from "node:crypto";
import process from "node:process";
import { createRequire } from "node:module";

const require = createRequire(import.meta.url);

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
const tools = args.tools ?? "/tmp/codex-full-restore-tools-HShmrO";
const parser = require(path.join(tools, "node_modules/@babel/parser"));
const traverse = require(path.join(tools, "node_modules/@babel/traverse")).default;
const postcss = require(path.join(tools, "node_modules/postcss"));
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
const outputPath = path.resolve(args.output ?? path.join(semanticRoot, "verification.json"));

function sha256(buffer) {
  return crypto.createHash("sha256").update(buffer).digest("hex");
}

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

function positionFor(text, offset, length) {
  const { line, column } = lineColumnIn(text, offset);
  return {
    utf16Offset: offset,
    byteOffset: Buffer.byteLength(text.slice(0, offset), "utf8"),
    line,
    column,
    excerpt: text.slice(offset, offset + length),
  };
}

function samePosition(actual, expected, length) {
  return (
    actual.utf16Offset === expected.utf16Offset &&
    actual.byteOffset === expected.byteOffset &&
    actual.line === expected.line &&
    actual.column === expected.column &&
    actual.excerpt === expected.excerpt &&
    actual.excerpt.length === length
  );
}

function mapSliceOffset(manifest, offset) {
  if (!manifest) return offset;
  for (const member of manifest.members) {
    const start = member.sliceStartUtf16;
    const end = start + member.utf16Length;
    if (offset >= start && offset < end) return member.sourceStart + (offset - start);
  }
  throw new Error(`slice offset ${offset} is outside every member range`);
}

function identifierIndex(ast) {
  const index = new Map();
  traverse(ast, {
    Identifier(identifierPath) {
      index.set(identifierPath.node.start, identifierPath);
    },
  });
  return index;
}

const reportsDir = path.join(semanticRoot, "reports");
const reportFiles = fs
  .readdirSync(reportsDir)
  .filter((name) => name.endsWith(".semantic.json"))
  .sort();

const fileResults = [];
const bindingResults = [];
const failures = [];

for (const reportName of reportFiles) {
  const reportPath = path.join(reportsDir, reportName);
  const report = JSON.parse(fs.readFileSync(reportPath, "utf8"));
  const baselineText = fs.readFileSync(report.baselineFile, "utf8");
  const semanticText = fs.readFileSync(report.semanticFile, "utf8");
  const baselineSha = sha256(fs.readFileSync(report.baselineFile));
  const semanticSha = sha256(fs.readFileSync(report.semanticFile));
  const manifest = report.sliceManifest
    ? JSON.parse(fs.readFileSync(report.sliceManifest, "utf8"))
    : null;

  const semanticAst = parser.parse(semanticText, {
    sourceType: "module",
    plugins: ["jsx", "flow"],
  });
  const semanticIdentifiers = identifierIndex(semanticAst);

  const fileFailures = [];
  if (report.inverseByteEqual !== true) fileFailures.push("inverseByteEqual is not true");
  if (report.semanticParses !== true) fileFailures.push("semanticParses is not true");
  if (baselineSha !== report.baselineSha256) fileFailures.push("baseline sha256 changed");
  if (semanticSha !== report.semanticSha256) fileFailures.push("semantic sha256 changed");
  if (report.renameCount !== report.bindings.length) fileFailures.push("renameCount mismatch");

  let evidenceChecks = 0;
  let ownBindingChecks = 0;
  let targetChecks = 0;
  let totalEdits = 0;

  for (const binding of report.bindings) {
    const localFailures = [];
    const expectedBaselineOffset = mapSliceOffset(manifest, binding.declaration.sliceUtf16Offset);
    if (expectedBaselineOffset !== binding.declaration.utf16Offset) {
      localFailures.push("slice declaration offset does not map to recorded baseline offset");
    }
    const recomputedDeclaration = positionFor(
      baselineText,
      binding.declaration.utf16Offset,
      binding.old.length,
    );
    if (!samePosition(recomputedDeclaration, binding.declaration, binding.old.length)) {
      localFailures.push("declaration evidence position mismatch");
    }
    evidenceChecks += 1;

    for (const reference of binding.references) {
      const expected = mapSliceOffset(manifest, reference.sliceUtf16Offset);
      if (expected !== reference.utf16Offset) {
        localFailures.push("slice reference offset does not map to recorded baseline offset");
      }
      const recomputed = positionFor(baselineText, reference.utf16Offset, binding.old.length);
      if (!samePosition(recomputed, reference, binding.old.length)) {
        localFailures.push(`reference evidence mismatch at ${reference.utf16Offset}`);
      }
      evidenceChecks += 1;
    }
    for (const violation of binding.constantViolations) {
      const expected = mapSliceOffset(manifest, violation.sliceUtf16Offset);
      if (expected !== violation.utf16Offset) {
        localFailures.push("slice violation offset does not map to recorded baseline offset");
      }
      const recomputed = positionFor(baselineText, violation.utf16Offset, binding.old.length);
      if (!samePosition(recomputed, violation, binding.old.length)) {
        localFailures.push(`violation evidence mismatch at ${violation.utf16Offset}`);
      }
      evidenceChecks += 1;
    }

    const expectedEdits = 1 + binding.references.length + binding.constantViolations.length;
    totalEdits += expectedEdits;

    const targetIdentifier = semanticIdentifiers.get(binding.targetDeclaration.sliceUtf16Offset);
    if (!targetIdentifier) {
      localFailures.push("no semantic identifier at target declaration offset");
    } else {
      if (targetIdentifier.node.name !== binding.new) {
        localFailures.push("target declaration name mismatch");
      }
      const semanticBinding = targetIdentifier.scope.getBinding(binding.new);
      if (!semanticBinding) {
        localFailures.push("semantic declaration has no binding");
      } else if (semanticBinding.identifier.start !== targetIdentifier.node.start) {
        localFailures.push("target identifier is not the semantic binding declaration");
      } else if (semanticBinding.scope.getOwnBinding(binding.new) !== semanticBinding) {
        localFailures.push("semantic name is not the owning scope's own binding");
      } else {
        ownBindingChecks += 1;
        for (const reference of binding.targetReferences) {
          const pathAtTarget = semanticIdentifiers.get(reference.sliceUtf16Offset);
          if (!pathAtTarget || pathAtTarget.node.name !== binding.new) {
            localFailures.push(`target reference missing at ${reference.sliceUtf16Offset}`);
            continue;
          }
          const isReference = semanticBinding.referencePaths.some(
            (candidate) => candidate.node.start === reference.sliceUtf16Offset,
          );
          if (!isReference) {
            localFailures.push(`target reference is not bound to the renamed binding at ${reference.sliceUtf16Offset}`);
          }
          targetChecks += 1;
        }
        for (const violation of binding.targetConstantViolations) {
          const pathAtTarget = semanticIdentifiers.get(violation.sliceUtf16Offset);
          if (!pathAtTarget || pathAtTarget.node.name !== binding.new) {
            localFailures.push(`target violation missing at ${violation.sliceUtf16Offset}`);
            continue;
          }
          const isViolation = semanticBinding.constantViolations.some(
            (candidate) =>
              candidate.node.start === violation.sliceUtf16Offset ||
              candidate.node.left?.start === violation.sliceUtf16Offset ||
              candidate.node.argument?.start === violation.sliceUtf16Offset,
          );
          if (!isViolation) {
            localFailures.push(`target violation is not bound to the renamed binding at ${violation.sliceUtf16Offset}`);
          }
          targetChecks += 1;
        }
      }
    }

    const record = {
      report: reportName,
      file: path.basename(report.formattedFile),
      baselineFile: path.basename(report.baselineFile),
      old: binding.old,
      new: binding.new,
      scope: binding.scope,
      oldKind: binding.oldKind,
      evidence: binding.evidence,
      confidence: binding.confidence,
      declaration: binding.declaration,
      targetDeclaration: binding.targetDeclaration,
      referenceCount: binding.references.length,
      constantViolationCount: binding.constantViolations.length,
      status: localFailures.length === 0 ? "pass" : "fail",
      failures: localFailures,
    };
    bindingResults.push(record);
    if (localFailures.length > 0) {
      failures.push({ report: reportName, old: binding.old, new: binding.new, failures: localFailures });
    }
  }

  if (totalEdits !== report.editCount) {
    fileFailures.push(`editCount mismatch: expected ${totalEdits}, report ${report.editCount}`);
  }
  if (fileFailures.length > 0) failures.push({ report: reportName, failures: fileFailures });

  fileResults.push({
    report: reportName,
    formattedFile: report.formattedFile,
    baselineFile: report.baselineFile,
    semanticFile: report.semanticFile,
    baselineSha256: baselineSha,
    semanticSha256: semanticSha,
    renameCount: report.bindings.length,
    editCount: report.editCount,
    evidenceChecks,
    ownBindingChecks,
    targetChecks,
    inverseByteEqual: report.inverseByteEqual === true,
    semanticParses: true,
    status: fileFailures.length === 0 ? "pass" : "fail",
    failures: fileFailures,
  });
}

const phase3CssChecks = [
  {
    file: "webview/assets/59ad52d5aa88.css",
    selector: `[data-app-shell-feature-navigation="true"][data-app-shell-responsive-sidebar="true"][data-app-shell-sidebar-open="false"]`,
    claim: "Responsive sidebar layout is keyed off the three app-shell data attributes.",
  },
  {
    file: "webview/assets/59ad52d5aa88.css",
    declaration: "--app-shell-sidebar-top-inset",
    value: "0px",
    claim: "The sidebar top inset custom property is defined in the shell stylesheet.",
  },
  {
    file: "webview/assets/59ad52d5aa88.css",
    selector: "._Navigation_1uxfp_2",
    claim: "Navigation CSS module class referenced by the sidebar/navigation slice.",
  },
  {
    file: "webview/assets/59ad52d5aa88.css",
    selector: "._ConversationSidebar_1uxfp_2",
    claim: "Conversation sidebar CSS module class referenced by the sidebar/navigation slice.",
  },
  {
    file: "webview/assets/197e14f5061d.css",
    selector: "._fallbackDragRegion_57y6d_2",
    claim: "Fallback drag-region class used by AppRootFrame when window chrome is absent.",
  },
  {
    file: "webview/assets/fd9b7fd80db1.css",
    selector: ".pt-\\[var\\(--app-shell-sidebar-top-inset\\,calc\\(var\\(--spacing\\)\\*2\\)\\)\\]",
    claim: "Arbitrary Tailwind padding utility applies the app-shell sidebar top inset.",
  },
  {
    file: "webview/assets/fd9b7fd80db1.css",
    declaration: "--app-shell-sidebar-divider",
    claim: "Sidebar divider custom property is defined in the main utility stylesheet.",
  },
];

const phase4CssChecks = [
  {
    file: "webview/assets/59ad52d5aa88.css",
    declaration: "--padding-row-cell-x",
    value: "8px",
    claim: "Sidebar row cell padding is defined as 8px inside the full-height page surface.",
  },
  {
    file: "webview/assets/59ad52d5aa88.css",
    declaration: "--padding-row-x",
    value: "8px",
    claim: "Sidebar row inline padding falls back to 8px inside the full-height page surface.",
  },
  {
    file: "webview/assets/fd9b7fd80db1.css",
    declaration: "--padding-row-x",
    value: "calc(var(--spacing) * 2)",
    claim: "Default row inline padding is derived from the spacing token.",
  },
  {
    file: "webview/assets/fd9b7fd80db1.css",
    declaration: "--sidebar-navigation-header-gap",
    claim: "Sidebar navigation header gap token is declared in the utility stylesheet.",
  },
  {
    file: "webview/assets/fd9b7fd80db1.css",
    selector: String.raw`.gap-\(--sidebar-navigation-header-gap\)`,
    claim: "Header layout uses the sidebar navigation header gap token.",
  },
  {
    file: "webview/assets/fd9b7fd80db1.css",
    selector: String.raw`.-mb-\(--sidebar-scroll-header-spacing\)`,
    claim: "Sticky sidebar header offsets its bottom margin by the scroll header spacing token.",
  },
  {
    file: "webview/assets/fd9b7fd80db1.css",
    selector: ".px-row-x",
    claim: "Row inline padding utility resolves the shared row padding variable.",
  },
  {
    file: "webview/assets/fd9b7fd80db1.css",
    selector: "[data-project-row]:hover",
    claim: "Project row hover styling is keyed off the `data-project-row` attribute.",
  },
  {
    file: "webview/assets/fd9b7fd80db1.css",
    selector: "[data-projects-rows]",
    claim: "Projects list styling is keyed off the `data-projects-rows` container attribute.",
  },
  {
    file: "webview/assets/fd9b7fd80db1.css",
    selector: "[data-project-row-wrapper]",
    claim: "Project row wrapper hover styling is keyed off `data-project-row-wrapper`.",
  },
  {
    file: "webview/assets/fd9b7fd80db1.css",
    declaration: "--height-token-row",
    claim: "Row height token is declared for sidebar rows.",
  },
  {
    file: "webview/assets/fd9b7fd80db1.css",
    declaration: "--font-weight-normal",
    value: "400",
    claim: "Normal font weight token used by row labels is 400.",
  },
  {
    file: "webview/assets/fd9b7fd80db1.css",
    declaration: "--button-font-weight",
    claim: "Button font weight token used by row actions is declared.",
  },
  {
    file: "webview/assets/fd9b7fd80db1.css",
    declaration: "--color-text-secondary-ghost",
    claim: "Secondary ghost text color used by row content is declared.",
  },
  {
    file: "webview/assets/59ad52d5aa88.css",
    declaration: "--color-text-tertiary",
    claim: "Tertiary text color used by row descriptions is declared.",
  },
];

const phase5CssChecks = [
  {
    file: "webview/assets/59ad52d5aa88.css",
    selector: '[data-composer-placement="home"]',
    claim: "Component stylesheet keys the home-placement composer dropdown styles off the `data-composer-placement` attribute the conversation page writes.",
  },
  {
    file: "webview/assets/fd9b7fd80db1.css",
    selector: '[data-composer-placement="home"]',
    claim: "Utility stylesheet hides/shows composer utilities through the same home placement attribute.",
  },
  {
    file: "webview/assets/fd9b7fd80db1.css",
    selector: ".composer-surface-chrome",
    claim: "Composer surface chrome class is styled for the embedded window types.",
  },
  {
    file: "webview/assets/b74e19ff7ff4.css",
    selector: ".composer-wrap",
    claim: "Composer wrapper receives messaging-root layout and bottom padding.",
  },
  {
    file: "webview/assets/b74e19ff7ff4.css",
    selector: ".composer-upload",
    claim: "Attachment upload chips rendered by the composer are styled under the messaging root.",
  },
  {
    file: "webview/assets/b74e19ff7ff4.css",
    selector: ".thread-pane.drag-active",
    claim: "Attachment drag-over state styles the file drop overlay on the thread pane.",
  },
  {
    file: "webview/assets/fd9b7fd80db1.css",
    selector: ".thread-scroll-container",
    claim: "Thread scroll container class used by the virtualized turn list participates in the first-child min-height utility.",
  },
  {
    file: "webview/assets/b74e19ff7ff4.css",
    selector: ".thread-pane",
    claim: "Thread pane layout class wraps the conversation scroll surface.",
  },
  {
    file: "webview/assets/fd9b7fd80db1.css",
    selector: "[data-thread-find-target",
    claim: "Conversation find-target attribute emitted by the turn list gets a scroll-margin utility.",
  },
  {
    file: "webview/assets/b74e19ff7ff4.css",
    selector: "[data-content-search-turn-key]",
    claim: "Content-search turn key attribute is targeted by turn-level search styles.",
  },
  {
    file: "webview/assets/b74e19ff7ff4.css",
    selector: ".composer-wrap:has(._paused_7oxil_19)",
    claim: "Paused composer state changes the disclaimer rendering.",
  },
  {
    file: "webview/assets/fd9b7fd80db1.css",
    declaration: "--thread-content-compact-max-width",
    value: "40rem",
    claim: "Compact thread content width token caps the conversation column at 40rem.",
  },
  {
    file: "webview/assets/b74e19ff7ff4.css",
    declaration: "--composer-available-height",
    claim: "Composer available-height token feeds the composer viewport sizing calculation.",
  },
];

const phase6CssChecks = [
  {
    file: "webview/assets/fd9b7fd80db1.css",
    selector: ".h-token-nav-row",
    claim: "Settings sidebar rows use the token nav row height utility.",
  },
  {
    file: "webview/assets/fd9b7fd80db1.css",
    selector: ".px-row-x",
    claim: "Settings rows and sidebar entries share the horizontal row padding utility.",
  },
  {
    file: "webview/assets/fd9b7fd80db1.css",
    selector: ".text-form-title",
    claim: "Form and section titles in the settings pages use the shared form-title text utility.",
  },
  {
    file: "webview/assets/fd9b7fd80db1.css",
    selector: ".text-danger",
    claim: "Gateway validation and destructive-action errors use the danger text utility.",
  },
  {
    file: "webview/assets/fd9b7fd80db1.css",
    selector: ".text-success",
    claim: "Gateway connection test success state uses the success text utility.",
  },
  {
    file: "webview/assets/fd9b7fd80db1.css",
    selector: ".text-warning",
    claim: "Gateway credential and discovery warnings use the warning text utility.",
  },
  {
    file: "webview/assets/fd9b7fd80db1.css",
    selector: ".text-tertiary",
    claim: "Settings descriptions and secondary labels use the tertiary text utility.",
  },
  {
    file: "webview/assets/fd9b7fd80db1.css",
    declaration: "--text-form-title",
    claim: "The form-title text utility resolves through the `--text-form-title` design token.",
  },
  {
    file: "webview/assets/126a18e4438e.css",
    declaration: "--spacing-token-button-composer",
    claim: "Settings action buttons reuse the composer button spacing token declared for shared controls.",
  },
  {
    file: "webview/assets/59ad52d5aa88.css",
    selector: "[data-settings-mobile-header]",
    claim: "The base stylesheet keys settings mobile-header layout off the `data-settings-mobile-header` attribute written by the settings content layout.",
  },
  {
    file: "webview/assets/9b4f72ea9acd.css",
    selector: "._profileLoadingBlock_flrea_2",
    claim: "Profile settings loading block class emitted by the profile page has a matching CSS module rule.",
  },
];

const phase7CssChecks = [
  {
    file: "webview/assets/fd9b7fd80db1.css",
    declaration: "--color-codex-diff-added",
    claim: "Review diff additions resolve through the `--color-codex-diff-added` token.",
  },
  {
    file: "webview/assets/fd9b7fd80db1.css",
    declaration: "--color-codex-diff-deleted",
    claim: "Review diff deletions resolve through the `--color-codex-diff-deleted` token.",
  },
  {
    file: "webview/assets/fd9b7fd80db1.css",
    declaration: "--color-token-diff-surface",
    claim: "The diff surface uses the shared `--color-token-diff-surface` token.",
  },
  {
    file: "webview/assets/fd9b7fd80db1.css",
    declaration: "--diffs-line-height",
    claim: "Pierre diff rows size themselves from the `--diffs-line-height` variable Codex sets.",
  },
  {
    file: "webview/assets/fd9b7fd80db1.css",
    selector: ".turn-diff-default-subtitle",
    claim: "Turn diff subtitles use the emitted `turn-diff-default-subtitle` utility class.",
  },
  {
    file: "webview/assets/e5b6a43c85b8.css",
    declaration: "--page-diff-font-size",
    claim: "The `/diff` page styles diff content from the `--page-diff-font-size` variable.",
  },
  {
    file: "webview/assets/e5b6a43c85b8.css",
    selector: "[data-page-block-diff]",
    claim: "The `/diff` page keys diff layout off the `data-page-block-diff` attribute.",
  },
  {
    file: "webview/assets/5a410815ad8f.css",
    selector: "._branchNode_1p23k_1",
    claim: "The branch picker tree uses the CSS-module `_branchNode_1p23k_1` rule.",
  },
];

const phase8CssChecks = [
  {
    file: "webview/assets/fd9b7fd80db1.css",
    selector: ".text-codex-description",
    claim: "Automation descriptions and secondary row text use the emitted `text-codex-description` utility.",
  },
  {
    file: "webview/assets/fd9b7fd80db1.css",
    selector: ".text-tertiary",
    claim: "Empty states and tertiary status text use the `text-tertiary` utility.",
  },
  {
    file: "webview/assets/fd9b7fd80db1.css",
    selector: ".text-secondary",
    claim: "Schedule and detail-panel secondary labels use the `text-secondary` utility.",
  },
  {
    file: "webview/assets/fd9b7fd80db1.css",
    selector: ".min-h-56",
    claim: "The “No scheduled tasks found” search empty state uses the `min-h-56` utility.",
  },
  {
    file: "webview/assets/fd9b7fd80db1.css",
    selector: ".text-danger",
    claim: "Validation and banner errors use the `text-danger` utility.",
  },
  {
    file: "webview/assets/fd9b7fd80db1.css",
    selector: ".text-warning",
    claim: "Schedule warnings use the `text-warning` utility.",
  },
  {
    file: "webview/assets/fd9b7fd80db1.css",
    selector: ".text-info",
    claim: "Info links and status labels use the `text-info` utility.",
  },
  {
    file: "webview/assets/fd9b7fd80db1.css",
    selector: ".bg-text-info",
    claim: "Unread/status dots use the `bg-text-info` utility.",
  },
  {
    file: "webview/assets/fd9b7fd80db1.css",
    selector: ".p-panel",
    claim: "The detail panel body uses the `p-panel` utility.",
  },
  {
    file: "webview/assets/fd9b7fd80db1.css",
    declaration: "--text-form-body",
    claim: "Form-styled automation controls resolve their font size through the `--text-form-body` token.",
  },
  {
    file: "webview/assets/fd9b7fd80db1.css",
    declaration: "--font-ui-weight",
    claim: "Select triggers resolve their font weight through the `--font-ui-weight` token.",
  },
  {
    file: "webview/assets/fd9b7fd80db1.css",
    declaration: "--select-control-font-size",
    claim: "Schedule and timezone select triggers set `--select-control-font-size`.",
  },
];

const phase9CssChecks = [
  {
    file: "webview/assets/fd9b7fd80db1.css",
    selector: ".text-codex-description",
    claim: "Plugin and account secondary descriptions use the emitted `text-codex-description` utility.",
  },
  {
    file: "webview/assets/fd9b7fd80db1.css",
    selector: ".text-secondary",
    claim: "Plugin settings labels and account-menu secondary text use the `text-secondary` utility.",
  },
  {
    file: "webview/assets/fd9b7fd80db1.css",
    selector: ".text-tertiary",
    claim: "Empty and tertiary states in the plugin directory use the `text-tertiary` utility.",
  },
  {
    file: "webview/assets/fd9b7fd80db1.css",
    selector: ".text-danger",
    claim: "Account deletion, plugin load and connection errors use the `text-danger` utility.",
  },
  {
    file: "webview/assets/fd9b7fd80db1.css",
    selector: ".bg-surface",
    claim: "Plugin cards, menus and settings panels use the `bg-surface` utility.",
  },
  {
    file: "webview/assets/fd9b7fd80db1.css",
    selector: ".border-default",
    claim: "Plugin and connection rows use the `border-default` utility for their separators.",
  },
  {
    file: "webview/assets/fd9b7fd80db1.css",
    selector: ".rounded-2xl",
    claim: "Plugin cards and account dialogs use the `rounded-2xl` corner utility.",
  },
  {
    file: "webview/assets/fd9b7fd80db1.css",
    selector: ".truncate",
    claim: "Plugin names, account emails and connection labels truncate with the `truncate` utility.",
  },
  {
    file: "webview/assets/9cc8b2b90a35.css",
    declaration: "--detail-page-section-gap",
    claim: "Plugin detail and profile pages space their sections with the `--detail-page-section-gap` token.",
  },
  {
    file: "webview/assets/9cc8b2b90a35.css",
    declaration: "--detail-page-inline-inset",
    claim: "Plugin detail and profile pages use the `--detail-page-inline-inset` token for inline padding.",
  },
  {
    file: "webview/assets/9cc8b2b90a35.css",
    declaration: "--profile-illustration-surface",
    claim: "The profile illustration surface resolves through the `--profile-illustration-surface` token.",
  },
  {
    file: "webview/assets/9cc8b2b90a35.css",
    declaration: "--profile-illustration-label",
    claim: "Profile illustration labels resolve through the `--profile-illustration-label` token.",
  },
];

const phase12CssChecks = [
  {
    file: "webview/assets/de34b33b485e.css",
    selector: "._field_1wl8q_2",
    claim: "The security page settings form field uses the `_field_1wl8q_2` CSS-module rule (route `security`, dep 11).",
  },
  {
    file: "webview/assets/de34b33b485e.css",
    selector: "._slots_1wl8q_2",
    claim: "Form-field slot layout is a CSS-module rule shipped with the security route.",
  },
  {
    file: "webview/assets/9a011ebae311.css",
    selector: "._interactive_10sh1_1",
    claim: "The storage settings interactive row highlights its focused control (route `storage`, dep 12).",
  },
  {
    file: "webview/assets/f35a0d712228.css",
    selector: '[data-avatar-overlay-native-tint="attention"]',
    claim: "Pets notification tint is driven by the `data-avatar-overlay-native-tint` attribute (route `pets`, dep 13).",
  },
  {
    file: "webview/assets/45bf4071a30f.css",
    selector: "._Root_78xu1_2",
    claim: "The pets/avatars surface root sets its background from the theme text/white tokens (route `pets`, dep 14).",
  },
  {
    file: "webview/assets/9787caae2bc9.css",
    selector: "._exampleUser_e04pk_1",
    claim: "The chronicle example-user mark animates in with its opacity transition (route `chronicle`, dep 34).",
  },
  {
    file: "webview/assets/17e79fe193bf.css",
    selector: "._table_7zul7_2",
    claim: "The computer-use/browser-use table stylesheet defines its viewport/cell layout (route `debug`/`extension`, dep 35).",
  },
  {
    file: "webview/assets/17e79fe193bf.css",
    selector: "._viewport_7zul7_2",
    claim: "The shared table viewport carries the scroll container for the use-permissions tables.",
  },
  {
    file: "webview/assets/e4bfe9e3f201.css",
    selector: "._borderedCardSandbox_10z9p_1",
    claim: "Rosalind/personalization cards use a bordered sandbox card with the Codex corner shape (deps 8/9).",
  },
  {
    file: "webview/assets/e4bfe9e3f201.css",
    declaration: "corner-shape",
    value: "var(--codex-corner-shape)",
    claim: "Bordered card corners resolve through `--codex-corner-shape`.",
  },
  {
    file: "webview/assets/26a9ec0e398b.css",
    selector: "._Button_r3d2l_1",
    claim: "The shared button stylesheet carries the personalization/usage button variants.",
  },
  {
    file: "webview/assets/bc5c05b0b97b.css",
    selector: "._badge_72qp0_2",
    claim: "Status badges (plan/sharing) use the `_badge_72qp0_2` CSS-module rule.",
  },
  {
    file: "webview/assets/90a0a1d06d48.css",
    selector: "._fullscreen_1uxo4_38",
    claim: "Card grids can render a fullscreen variant, used by usage/personalization cards.",
  },
  {
    file: "webview/assets/9c6da1a2e6bf.css",
    selector: "._frame_12dmg_5",
    claim: "The business/attribution frame rule styles inline business labels.",
  },
  {
    file: "webview/assets/4e47cfdb2650.css",
    selector: "._Input_1q827_238",
    claim: "The shared input stylesheet drives settings text inputs.",
  },
  {
    file: "webview/assets/3ecd35c9b4eb.css",
    declaration: "--textarea-base-height",
    claim: "Settings textareas size from the `--textarea-base-height` token.",
  },
  {
    file: "webview/assets/9516d5432844.css",
    declaration: "--memory-summary-shimmer-offset",
    claim: "Memory-summary placeholders animate through `--memory-summary-shimmer-offset`.",
  },
  {
    file: "webview/assets/9d4dcd640ba7.css",
    declaration: "--syntax1",
    claim: "Code blocks inside settings/markdown panels color syntax through `--syntax1`.",
  },
  {
    file: "webview/assets/696fd55735f4.css",
    selector: "._Placeholder_1r3vk_12",
    claim: "Loading placeholders pulse with the `_Placeholder_1r3vk_12` animation.",
  },
  {
    file: "webview/assets/d926df36a3a1.css",
    selector: "._ActionOverlay_1k7i1_91",
    claim: "Row action overlays (reveal/delete) are positioned by the shared overlay rule.",
  },
  {
    file: "webview/assets/ec105dbb0b43.css",
    selector: "._Checkbox_l08eh_2",
    claim: "Settings checkboxes use the shared `_Checkbox_l08eh_2` rule.",
  },
  {
    file: "webview/assets/e18c96a77e71.css",
    selector: "._DropdownIcon_1qm03_489",
    claim: "Select rows render the shared dropdown chevron.",
  },
  {
    file: "webview/assets/e2016a34337c.css",
    selector: "._Calendar_zxknp_1",
    claim: "Quiet-hours/recurring-schedule editors use the shared calendar stylesheet.",
  },
  {
    file: "webview/assets/dd1402a3bc02.css",
    selector: "._Tooltip_453y_1",
    claim: "Tooltips for settings controls use the shared tooltip rule.",
  },
  {
    file: "webview/assets/ec6b8907ce09.css",
    selector: "._tooltip_sk0md_7",
    claim: "A second tooltip variant styles an icon caret for compact controls.",
  },
  {
    file: "webview/assets/280c7fb71b0a.css",
    selector: "._followUp_1k4sb_59",
    claim: "The onboarding/follow-up panel styles its follow-up block (personalization route).",
  },
  {
    file: "webview/assets/90098f1b0d98.css",
    declaration: "--badge-border-color",
    claim: "The personalization badge set resolves its border from `--badge-border-color`.",
  },
  {
    file: "webview/assets/4403cd3ba5b4.css",
    declaration: "--badge-background-color",
    claim: "The usage/personalization badge set resolves its fill from `--badge-background-color`.",
  },
  {
    file: "webview/assets/93c2fa48ca6d.css",
    selector: "._savingsBadge_1fcm6_1",
    claim: "The billing/usage savings badge used by the general settings page styles itself from the tertiary surface tokens (dep 4 of the `general-settings` route).",
  },
  {
    file: "webview/assets/9aed74b17655.css",
    selector: "._fields_5lmnb_2",
    claim: "The general settings form stacks its fields with a 24px gap and tightens to 16px under `[data-discounted]` (dep 5 of the `general-settings` route).",
  },
];

const phase11CssChecks = [
  {
    file: "webview/assets/241c9f83d2ce.css",
    selector: ".xterm .xterm-helper-textarea",
    claim: "The xterm helper textarea is visually hidden and positioned off-screen for IME input.",
  },
  {
    file: "webview/assets/241c9f83d2ce.css",
    selector: ".xterm .composition-view.active",
    claim: "An active IME composition view is displayed as an overlay in the terminal.",
  },
  {
    file: "webview/assets/241c9f83d2ce.css",
    selector: ".xterm .xterm-viewport",
    claim: "The xterm viewport is styled by the vendored terminal stylesheet.",
  },
  {
    file: "webview/assets/d0f289883c52.css",
    selector: "[data-codex-xterm][data-theme]",
    claim: "The app terminal theme defines background, foreground and selection variables.",
  },
  {
    file: "webview/assets/d0f289883c52.css",
    declaration: "transform",
    value: "translateX(var(--terminal-input-preview-offset, 0px))",
    claim: "The terminal cursor is shifted by the input-preview offset variable.",
  },
  {
    file: "webview/assets/d0f289883c52.css",
    declaration: "scrollbar-color",
    value: "var(--color-border) var(--color-codex-terminal-background)",
    claim: "The terminal viewport uses themed scrollbar colors.",
  },
  {
    file: "webview/assets/cecb2c03ae9a.css",
    selector: ".ansi-black-fg",
    claim: "ANSI black maps to the terminal ANSI black variable.",
  },
  {
    file: "webview/assets/cecb2c03ae9a.css",
    selector: ".ansi-red-fg",
    claim: "ANSI red maps to the terminal ANSI red variable.",
  },
  {
    file: "webview/assets/cecb2c03ae9a.css",
    selector: ".ansi-green-fg",
    claim: "ANSI green maps to the terminal ANSI green variable.",
  },
  {
    file: "webview/assets/61321c9dabf9.css",
    selector: "[data-pierre-editor-surface] diffs-container",
    claim: "The Pierre editor surface overrides its selection background variable.",
  },
  {
    file: "webview/assets/61321c9dabf9.css",
    selector: "[data-pierre-editor-annotation]::selection",
    claim: "Pierre annotation selections use the info-soft background.",
  },
];

const phase10CssChecks = [
  {
    file: "webview/assets/fd9b7fd80db1.css",
    atRule: { name: "container", paramsIncludes: "app-shell-detail-panel (width<=899px)" },
    claim: "The detail panel reacts at 899px through an `app-shell-detail-panel` container query.",
  },
  {
    file: "webview/assets/fd9b7fd80db1.css",
    selector: "._responsiveSheet_1u8c7_2",
    claim: "The responsive dialog sheet uses the `_responsiveSheet_1u8c7_2` CSS-module rule.",
  },
  {
    file: "webview/assets/fd9b7fd80db1.css",
    declaration: "padding-bottom",
    value: "env(safe-area-inset-bottom, 0px)",
    claim: "The responsive sheet pads its bottom edge with `safe-area-inset-bottom`.",
  },
  {
    file: "webview/assets/fd9b7fd80db1.css",
    selector: "._edgeToEdgeSheet_1u8c7_2",
    claim: "An edge-to-edge sheet surface exists alongside the responsive sheet.",
  },
  {
    file: "webview/assets/fd9b7fd80db1.css",
    declaration: "container",
    value: "left-panel/inline-size",
    claim: "`@container/left-panel` is emitted as an inline-size container named `left-panel`.",
  },
  {
    file: "webview/assets/fd9b7fd80db1.css",
    declaration: "container",
    value: "app-shell-tab/inline-size",
    claim: "`@container/app-shell-tab` is emitted as an inline-size container named `app-shell-tab`.",
  },
  {
    file: "webview/assets/fd9b7fd80db1.css",
    declaration: "container",
    value: "app-shell-main-content/inline-size",
    claim: "`@container app-shell-main-content` is emitted as an inline-size container.",
  },
  {
    file: "webview/assets/fd9b7fd80db1.css",
    selector: ".browser\\:max-md\\:hidden",
    claim: "`browser:max-md:hidden` is emitted as a browser-window-gated utility.",
  },
  {
    file: "webview/assets/fd9b7fd80db1.css",
    selector: ".pointer-coarse\\:pointer-events-auto",
    claim: "`pointer-coarse:pointer-events-auto` is emitted as a coarse-pointer utility.",
  },
  {
    file: "webview/assets/fd9b7fd80db1.css",
    selector: ".pointer-coarse\\:size-10",
    claim: "`pointer-coarse:size-10` provides a 2.5rem touch target under a coarse pointer.",
  },
  {
    file: "webview/assets/59ad52d5aa88.css",
    selector: "._Layout_azz1c_2[data-app-shell-responsive-sidebar=\"true\"]",
    claim: "The below-768px responsive layout zeroes the left-panel width variable.",
  },
  {
    file: "webview/assets/59ad52d5aa88.css",
    selector: "._ResponsiveLeftPanel_azz1c_2",
    claim: "The responsive left panel is hidden below 768px when the responsive sidebar is enabled.",
  },
  {
    file: "webview/assets/59ad52d5aa88.css",
    selector: "._TabStripFlushStart_mr1y2_2",
    claim: "The flush-start tab strip resets inline-start padding at `width>=768px`.",
  },
  {
    file: "webview/assets/59ad52d5aa88.css",
    atRule: { name: "media", paramsIncludes: "not all and (width>=768px)" },
    claim: "The app-shell layouts define a below-768px media block.",
  },
  {
    file: "webview/assets/b74e19ff7ff4.css",
    atRule: { name: "container", paramsIncludes: "composer-footer (width<=440px)" },
    claim: "The composer footer reacts at 440px through a `composer-footer` container query.",
  },
  {
    file: "webview/assets/b74e19ff7ff4.css",
    atRule: { name: "media", paramsIncludes: "(hover: none), (pointer: coarse)" },
    claim: "Composer CSS groups no-hover and coarse-pointer capability into one media block.",
  },
  {
    file: "webview/assets/126a18e4438e.css",
    selector: "._shell_1vs63_2",
    claim: "The searchable page layout defines its own shell class.",
  },
  {
    file: "webview/assets/126a18e4438e.css",
    atRule: { name: "container", paramsIncludes: "searchable-header not (width>=48rem)" },
    claim: "The searchable page header switches layout below a 48rem header-container width.",
  },
  {
    file: "webview/assets/c7abafaae9d4.css",
    selector: "._reveal_1xm50_1",
    claim: "The sidebar reveal element animates `grid-template-columns` from `0fr`.",
  },
];

const cssChecks =
  phase === 12
    ? phase12CssChecks
    : phase === 11
    ? phase11CssChecks
    : phase === 10
    ? phase10CssChecks
    : phase === 9
    ? phase9CssChecks
    : phase === 8
    ? phase8CssChecks
    : phase === 7
    ? phase7CssChecks
    : phase === 6
    ? phase6CssChecks
    : phase === 5
      ? phase5CssChecks
      : phase === 4
        ? phase4CssChecks
        : phase3CssChecks;

const cssResults = [];
for (const check of cssChecks) {
  const filePath = path.join(semanticRoot, "formatted", check.file);
  const text = fs.readFileSync(filePath, "utf8");
  const ast = postcss.parse(text, { from: filePath });
  let found = null;
  ast.walkRules((rule) => {
    if (found) return;
    if (check.selector && rule.selector.includes(check.selector)) {
      found = { kind: "rule", selector: rule.selector, position: positionFor(text, rule.source.start.offset, rule.selector.length) };
    }
  });
  if (!found && check.declaration) {
    ast.walkDecls(check.declaration, (decl) => {
      if (found) return;
      if (check.value != null && decl.value !== check.value) return;
      found = {
        kind: "declaration",
        prop: decl.prop,
        value: decl.value,
        position: positionFor(text, decl.source.start.offset, decl.toString().length),
      };
    });
  }
  if (!found && check.atRule) {
    ast.walkAtRules(check.atRule.name, (at) => {
      if (found) return;
      if (
        check.atRule.paramsIncludes != null &&
        !at.params.includes(check.atRule.paramsIncludes)
      ) {
        return;
      }
      if (check.atRule.params != null && at.params !== check.atRule.params) return;
      const textAtRule = `@${at.name} ${at.params}`;
      found = {
        kind: "atrule",
        name: at.name,
        params: at.params,
        position: positionFor(text, at.source.start.offset, textAtRule.length),
      };
    });
  }
  cssResults.push({
    file: check.file,
    claim: check.claim,
    expected:
      check.selector ??
      (check.atRule
        ? `@${check.atRule.name} ${check.atRule.params ?? `~${check.atRule.paramsIncludes}`}`
        : `${check.declaration}: ${check.value ?? "<any>"}`),
    found: found != null,
    evidence: found,
    status: found != null ? "pass" : "fail",
  });
  if (!found) failures.push({ css: check.file, expected: check.selector ?? check.declaration });
}

function walkFiles(directory) {
  const out = [];
  for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
    const entryPath = path.join(directory, entry.name);
    if (entry.isDirectory()) out.push(...walkFiles(entryPath));
    else out.push(entryPath);
  }
  return out;
}

const formattedRoot = path.join(semanticRoot, "formatted");
const copyResults = [];
for (const formattedPath of walkFiles(formattedRoot).sort()) {
  const relative = path.relative(formattedRoot, formattedPath);
  if (relative.endsWith("-slice.js")) continue;
  const baselinePath = path.join(root, "02-baseline", "formatted", relative);
  if (!fs.existsSync(baselinePath)) {
    copyResults.push({ file: relative, status: "fail", note: "baseline counterpart missing" });
    failures.push({ copy: relative, note: "baseline counterpart missing" });
    continue;
  }
  const formattedSha = sha256(fs.readFileSync(formattedPath));
  const baselineSha = sha256(fs.readFileSync(baselinePath));
  const equal = formattedSha === baselineSha;
  copyResults.push({
    file: relative,
    baselinePath: path.relative(root, baselinePath),
    formattedSha256: formattedSha,
    baselineSha256: baselineSha,
    status: equal ? "pass" : "fail",
  });
  if (!equal) failures.push({ copy: relative, note: "formatted copy differs from baseline" });
}

const summary = {
  reports: fileResults.length,
  renames: bindingResults.length,
  evidenceChecks: fileResults.reduce((n, item) => n + item.evidenceChecks, 0),
  ownBindingChecks: fileResults.reduce((n, item) => n + item.ownBindingChecks, 0),
  targetChecks: fileResults.reduce((n, item) => n + item.targetChecks, 0),
  inverseByteEqual: fileResults.every((item) => item.inverseByteEqual),
  semanticParses: fileResults.every((item) => item.semanticParses),
  cssChecks: cssResults.length,
  cssPassed: cssResults.filter((item) => item.status === "pass").length,
  formattedCopies: copyResults.length,
  formattedCopiesEqual: copyResults.filter((item) => item.status === "pass").length,
  failures: failures.length,
};

const scope =
  phase === 12
    ? "Phase 12 remaining settings-page slices only: the notifications, import, security, age, agreements, trusted-contact, safety/time-management, keyboard-shortcuts, Codex Micro, appshots, voice, device-storage, pets, configuration/work-settings, ads-controls, archived-chats, data-controls, code-review, cloud-preferences, cloud-environments, personalization, chronicle, usage, debug, computer-use, browser-use/browser-extension, local-environments, external-section, hooks, file-type-handlers, skills-route, cloud-computer, passwords, team, parental-controls, rosalind and codex-cloud settings routes, plus the hooks settings module helpers, the import-settings gate, the age-settings store, settings availability and visible-sections plumbing, and the page-specific stylesheets those routes preload. The five pages already covered by Phase 6 are not re-sliced. This is not whole-application semantic restoration."
    : phase === 11
    ? "Phase 11 terminal and file-panel slices only: terminal tab/panel, Cloud terminal states, PTY session snapshots and reconnect/truncation, shell labels and cwd switching, background/thread terminals, xterm addons and CSS, bottom-panel chrome and persisted height, the app-shell file tree, file browser/open targets, workspace-agent files, artifact end-resource actions, text/Markdown/Pierre editors, sketch and annotation editors, artifact preview, library and remote folder pickers; file search UI itself is host-side and vendored xterm internals are not reversed. This is not whole-application semantic restoration."
    : phase === 10
    ? "Phase 10 responsive and mobile-layout slices only: breakpoint/capability hooks, the app-shell frame and responsive sidebar chrome, settings/searchable/projects page layouts, dialog sheets, popover positioning, composer attachments, editor toolbar, tab strip, side-panel coarse-pointer rows, automations and MCP/plugin rows, plus the referenced CSS media/container queries; this Electron bundle has responsive web layout but no native mobile shell, and this is not whole-application semantic restoration."
    : phase === 9
    ? "Phase 9 plugin and account slices only: plugin directory and cards, plugin detail and settings routes, install/remove/uninstall actions, marketplaces, MCP servers and auth, team add-plugins, remote connections, account menu/settings/deletion, profile pages, login/welcome/select-workspace and browser logout; this is not whole-application semantic restoration."
    : phase === 8
    ? "Phase 8 scheduled-task and automation slices only: `/scheduled` page, list/filter/groups, rows, detail pane and composer, automations layout/create/history, dialog and schedule editor, targeting and notification policy, side-panel tab, actions menu, submit validation, run results/run-now, delete confirmation, search and team row; this is not whole-application semantic restoration."
    : phase === 7
    ? "Phase 7 Git and worktree slices only: Review/Changes side-panel tab, diff area and toolbar, file tree, branch switcher/commit modal, worktrees settings, worktree creation/restore and Git settings; this is not whole-application semantic restoration."
    : phase === 6
    ? "Phase 6 settings shell, settings pages and model-provider (gateways) slices only; this is not whole-application semantic restoration."
    : phase === 5
      ? "Phase 5 conversation page, virtualized turn list, message-item dispatcher, leaf message renderers and composer behavior slices only; this is not whole-application semantic restoration."
      : phase === 4
        ? "Phase 4 home/space sidebar and route landing-page container slice only; this is not whole-application semantic restoration."
        : "Phase 3 shell/navigation slice only; this is not whole-application semantic restoration.";
const unresolvedItems =
  phase === 12
    ? [
        "Phase 12 is a semantic reading of bounded member slices. Alongside the page-specific stylesheets those routes preload, all settings pages share the 1.7 MB Tailwind/utility stylesheet, which is copied byte-for-byte from the baseline rather than re-derived; the utility class names used by a page are recorded as claims, not as generated declarations.",
        "The settings routes are mounted through the lazy route table (`settings-lazy-routes-*.js`). Phase 12 records that each page module is reachable from that table, but it does not execute the route transition, so navigability is inferred from the import graph and the message ids the page binds, not observed at runtime.",
        "Every claim is a lexical/structural fact about the baseline text (message id, data attribute, CSS class, conditional branch). No Electron process was started, no settings form was filled, no toggle was flipped and no server-side settings API was called, so behaviour under a live account is not verified.",
        "Several pages delegate most of their work to shared runtime services (settings store, account API, extension bridge, password manager, OS notification/permission APIs). Phase 12 stops at the call boundary: the page container, its states and copy are readable, the service implementation is not.",
        "Localization bundles are not in the webview baseline: only message ids appear in the chunks, so the rendered strings a user sees in a given locale are outside this phase.",
        "Pages that only exist behind account plans, platform checks or feature flags (age verification, parental controls, ads controls, team, cloud computer, Codex Micro hardware) are covered from their guarded branches; the unguarded production path for those plans was not exercised.",
      ]
    : phase === 11
    ? [
        "The terminal panel vendors xterm.js core and the Fit/WebLinks/Clipboard addons inside large CommonJS module wrappers. Phase 11 copies and renames the app-level wrapper functions but does not reverse xterm's internal renderer, input parser or addon internals.",
        "IME composition and Escape handling live inside the vendored xterm module (`xterm-display-helpers-*.js`, CompositionHelper); the app-level slices only show the hidden helper textarea, composition CSS and the custom key handler that forwards/overrides shortcuts.",
        "Global 'Search in files' is dispatched as the host message `{type:'find-in-files'}`; the search result UI is implemented in the Electron main process and is outside the webview baseline. The bounded slice covers the toolbar trigger, not the host search page.",
        "The file-tree component styles its shadow DOM through inline CSS inside `app-initial-*.js`; those rules are recorded as static claims, not as a standalone stylesheet copy. OS-level file drops onto the Files panel were not found; the tree implements internal move/drag only.",
        "Library and remote-folder pickers cover selection, filtering, parent navigation and loading/error states. The platform file dialogs, remote RPC transport and library account APIs stay at their host call sites.",
        "Artifact/workbook previews cover the tab, status and XLSX warning branches. Spreadsheet engine internals, CRDT transport and native preview processes are not reversed.",
      ]
    : phase === 9
    ? [
        "The plugin surface has three distinct pages: the `/plugins` directory (skills-page/plugins-skills-tabs), the plugin detail page (`/plugins/:pluginId`, including plugin apps) and the plugin settings routes under `/settings/plugins-settings/hosts/:hostId/plugins/:pluginId`. The Phase 9 slices cover their containers, cards, actions, install dialog and settings sections, but not the full file bodies of the 38 source chunks.",
        "Plugin execution is not reversed: tool/app execution, plugin runtime APIs, browser-extension handoff, plugin signatures/verification and the third-party marketplace protocol are recorded only at their UI call sites. This slice restores UI and state flow, not plugin internals.",
        "Marketplace management is covered at the list/remove/upgrade UI boundary (`skills.appsPage.marketplace.*`); the marketplace index format, Git fetch/update transport and plugin archive installation are not reversed.",
        "MCP settings cover server list, stdio/http transport editor, env/args/headers, bearer auth and OAuth client registration (auto/CIMD/DCR). The app-server MCP transport, OAuth browser callback and server process supervision stay outside the slice.",
        "Remote connections cover the settings page tabs (access other devices, control this Mac, SSH, WSL), connection table/dialogs and remote-control device authorization UI. SSH/WSL transport, pairing protocol and device relay are recorded only at their UI/RPC call boundary.",
        "Account surfaces cover the profile dropdown menu, workspace switcher, account settings (name, builder profile, email verification, username, status), account deletion, consumer billing (plan, payment methods, wallet, transactions) and profile pages. No standalone usage page exists in this build; `usage-416fb4aca8bd.js` contains rate-limit banners, not an account usage page.",
        "Login covers `/login`, `/welcome`, `/select-workspace` and the browser `/logout` page plus the Bedrock form and ChatGPT/API-key fallback methods. No credentials were used or observed; OAuth/SSO, workspace recovery and auth-service internals are recorded only as route/state facts.",
        "Shared primitives imported from other chunks (settings rows, switches, selects, dialogs, context menus, tooltips, host pickers) are referenced-not-included; only the member slices named in `reports/` are renamed.",
        "CSS: `fd9b7fd80db1.css` and `9cc8b2b90a35.css` are copied for PostCSS evidence; CSS modules and theme overrides for uncopied plugin/account subcomponents stay outside the slice.",
        "DSH alignment is deliberately not applied here: this phase records Codex implementation facts only.",
      ]
    : phase === 10
    ? [
        "No native mobile shell exists in this bundle. `data-app-shell-responsive-sidebar`, the settings mobile header and touch-overscroll containment are all hardcoded off in the Electron entry path; `codex-mobile-setup-flow-946d5fd9e82b.js` is a QR pairing flow for phones, not an iOS/Android shell.",
        "Runtime viewport behavior was not observed: 640px/768px/899px/40rem/639px and capability queries are recorded from compiled literals, not from resizing an Electron or browser window.",
        "`browser:` variants compile to `[data-codex-window-type=\"browser\"]` selectors and stay inert because Electron sets `data-codex-window-type=\"electron\"`; the browser popover/sidebar chrome variants are recorded as reachable code paths, not as observed Electron behavior.",
        "Responsive primitives that live outside the selected chunks remain referenced-not-included, including the mobile drawer implementation behind sidebar chrome, the full settings navigation shell and page-level route wrappers.",
        "Only 11 CSS files are copied for PostCSS evidence; CSS modules for uncopied page components and theme overrides stay outside the slice.",
        "Touch, IME and on-screen-keyboard behavior is recorded only from pointer/keyboard handlers and CSS; no device, emulator or touch session was run.",
        "Third-party Floating UI positioning is recorded at its call boundary (`sideOffset`, `alignOffset`, `collisionPadding`, `hideWhenDetached`); its internal collision algorithm is not reversed.",
        "No Electron launch, login, navigation, resize or screenshot was performed in this phase.",
      ]
    : phase === 8
    ? [
        "Scheduled tasks have two mounted surfaces in this build: the `/scheduled` route (`ScheduledTasksPage`) and the automations layout reached by redirecting `/automations` (`AutomationsLayoutPage`). The Phase 8 slices cover both surfaces, their rows, dialogs, targeting controls, history rail and run results, but not the full `page-7ef1423c8917.js` or `automations-page-f42fb2994576.js` file bodies.",
        "Create-with-ChatGPT and Create-with-Codex hand off to chat surfaces: only the create-menu entries and their draft/route plumbing are recorded. The `appgen-automations-page-*` and `site-automation-create-panel-*` chunks are referenced but not sliced.",
        "Run-now executes through app-server RPC (`AppServerManager`); the RPC transport, host targeting and server-side error mapping are recorded only at their call sites inside `run-now-*`.",
        "Run-history archive/unarchive depends on shared inbox thread-query and context-menu helpers imported from other chunks; those primitives are not renamed here.",
        "Plugin-template rows surface plugin-provided templates, but the plugin registry and template storage internals are outside the slice; `automation-team-row-*` covers only the rendered team selector.",
        "The create/edit side panel can render site and cloud request variants; the site renderer's request schema and the cloud draft builder are covered only at their dispatch boundary.",
        "CSS: only `fd9b7fd80db1.css` is copied for PostCSS evidence; CSS modules and theme overrides for uncopied automation subcomponents stay outside the slice.",
        "Notification and background behavior is recorded as data flow only (unread ids/counts, mark-all-read, in-progress row state, cloud-continues-while-off copy). No notification was observed, and app-closed scheduling behavior was not executed.",
        "DSH alignment is deliberately not applied here: this phase records Codex implementation facts only.",
      ]
    : phase === 7
    ? [
        "No standalone `/git` page or route exists in this build: the Git surface is the Review/Changes side-panel tab (`ReviewThreadSidePanelTab`), the `/diff` editor page (`EditorDiffPage`), the branch-switcher/commit modal, the worktrees settings page, the worktree creation row, the worktree restore banner and the Git settings page. The Phase 7 slices cover those entry points only.",
        "Diff row rendering is delegated to `@pierre/diffs`: `file-diff-e249aa0e1a7a.js` wraps `FileDiff`/`FileDiffContents` from `file-diff-presentation-84b635a53970.js` and loads `pierre-highlight-provider-1c7940684d2c.js`, `register-pierre-themes-50d08bd72b55.js` and `virtualized-file-diff-line-position-b007776d06bd.js`. Codex-side hunk/line layout, syntax highlighting and virtualization internals inside those chunks are recorded as a library boundary, not reversed.",
        "Review header subcomponents `ze` (ReviewChangesOptionsMenu), the pull-request/commit query builders and several file-tree decoration helpers outside the selected members stay referenced-not-included.",
        "Commit history: the Changes source menu offers a commits source and a selected-commit chip, but no separate commit-history page was found; history browsing is confined to that source menu.",
        "Worktree list: `WorktreesSettingsPage` groups rows by repository, but the row action menu and some icon components live in imported chunks and are not renamed here.",
        "Git settings: branch prefix, force push, draft PR, merge method and commit/PR instructions are renamed at page level; the shared setting-row controls they render are imported from other chunks and stay referenced-not-included.",
        "CSS: only the five stylesheets referenced by these surfaces are copied for PostCSS evidence; CSS modules for uncopied diff/worktree subcomponents stay outside the slice.",
        "DSH alignment is deliberately not applied here: this phase records Codex implementation facts only.",
      ]
    : phase === 6
    ? [
        "settings section catalog: `app-primary` lists every section slug, but only general-settings, appearance, account, profile and gateways route chunks are copied; notifications, import, security, age, agreements, billing, usage, personalization and the remaining sections are referenced through the catalog/route table only.",
        "general-settings: `GeneralSettings` / `GeneralSettingsDesktopContent` / `GeneralSettingsLegacyContent` and `AppearanceSettingsSection` are renamed, but the shared setting-row controls (switch, select, slider) they render are imported from other chunks and stay referenced-not-included.",
        "gateways: the page edits `model_providers`, validates drafts and tests connections; the actual model catalog is fetched by the app-server after restart (`model/list`) and has no in-page model-list UI, so model directory rendering is not part of this slice.",
        "gateways/account: `account/bedrock/discover` only discovers Bedrock profiles and environment credentials; it is not a general model catalog endpoint.",
        "reasoning/effort configuration: no effort control exists on the Gateways page; the composer model picker (`ReasoningEffortLabel`, Phase 5) is the only effort entry point found, so it is recorded as absent from the settings chain rather than invented.",
        "account/profile: `ChatGptAccountSettings`, `ProfileSettings`, `ProfileSettingsLayout` cover the page shells; nested billing/usage/subscription widgets referenced by those pages are not renamed.",
        "CSS: only the eight stylesheets referenced by these pages are copied for PostCSS evidence; CSS modules consumed by uncopied settings sections stay outside the slice.",
        "DSH alignment is deliberately not applied here: this phase records Codex implementation facts only; the user's decision to follow DSH for provider interaction is a later migration concern.",
      ]
    : phase === 5
      ? [
        "composer-content-b926d24ef280.js: the monolithic composer content module (`Uq`, ~5,089 lines) is reached through `CodexComposer` but not copied; input surface internals, the model picker and the full attachment picker remain outside the bounded slices.",
        "conversation-item dispatcher: `ConversationItemContent` routes every item type, but leaf renderers that live in other chunks stay referenced-not-included, including the imported `system-error-content-833b2eec0e47.js` helpers other than `SystemErrorMessage`.",
        "turn footer/action row: `AssistantMessage` computes timestamp and copy/action-row props, but the shared action-button primitives and the feedback dialog it imports are not renamed here.",
        "virtualized list: measured-height callbacks and `useMemoCache` cache-slot wiring are sliced at the call-site level; React internals are not named.",
        "Third-party boundaries: markdown/streamdown rendering and diff rendering libraries are recorded as boundaries only; their internals are not reversed.",
        "Numeric feature flags, composer mode strings and item `type` discriminants remain opaque values; their product meaning is not inferred.",
      ]
    : phase === 4
      ? [
          "app-primary: helper predicates used by the sidebar sections that are still unnamed, including the thread-group/row predicates (kM, aXn) and the project/connection group predicate q6n.",
          "app-primary: the `pGn` LocalThreadRow wrapper remains unnamed because the slice does not distinguish it from `mGn` by behavior; the rich project hover card internals (Of, Hzn) and the context-menu implementation (Ja) also remain outside the bounded slice.",
          "projects-index-page: toolbar organize control `yn`, row cell components and the project actions menu are referenced but not renamed because their declarations are outside the selected page slice.",
          "page: the project sources tab (`zc`) and locked-project view (`_o`) are referenced by ProjectHomeContent but not renamed in this bounded slice.",
          "thread pages: the full conversation body, composer and panel internals are outside this slice; only the page containers and access guard are named.",
          "Numeric feature-flag keys and route-handle constants remain opaque strings; their product meaning is not inferred.",
        ]
      : [
          "app-primary: zq, nFn, wpn/wfr-style workspace-home helpers, and most minified provider/context identifiers outside the selected shell slice.",
          "app-initial: numeric feature-flag keys such as 3085093835 and 544772515 are preserved as opaque strings; their product meaning is not inferred.",
          "app-runtime: route-handle constants nb/lb/oy/fy and the auth-state machine terms (authMethod, requiresAuth) are preserved under their original names where no semantic binding was proven.",
          "Unnamed predicates: the $sr gates (V, H, qe, tt, ct), the l0r skills/spaces fallback conditions, and the Sfr workspace/site-preview conditions remain unnamed.",
          "Plugin tool routes, proxy configuration and meeting page internals are outside this slice; only their sidebar/area entry points are covered.",
        ];
const unverifiedItems =
  phase === 12
    ? [
        "No Electron launch, settings navigation, form submit, toggle, dialog confirm or screenshot was performed in this phase.",
        "Lexical import/export evidence is not runtime mounting evidence: account/enterprise gates, feature flags and desktop-vs-web branches inside these pages were read from compiled source, not exercised.",
        "The bounded slices contain only selected members; the surrounding module code, statement order and every non-requested declaration were not re-rendered or executed.",
        "Server-side settings, account/enterprise APIs and persisted preferences were neither called nor inspected; only the client call sites and message ids embedded in the copied chunks are recorded.",
        "Localization bundles were not inspected; only the default English strings embedded in the copied chunks are recorded.",
        "Twenty-eight route stylesheets are copied for PostCSS evidence (every route-level dep of settings-lazy-routes not already copied by an earlier phase); the 1.7 MB Tailwind utility stylesheet those routes also preload is checked only where a Phase 12 claim cites it, not re-derived.",
      ]
    : phase === 11
    ? [
        "Terminal interaction was not run: no PTY session, shell spawn, resize, copy/paste, IME composition or link click was executed against a live Electron app.",
        "File operations were not run: no file was opened, renamed, deleted, uploaded, previewed, searched or revealed in Finder/Explorer.",
        "Host-side find-in-files results, remote directory browsing and library account contents require Electron main-process services and were not observed at runtime.",
        "Third-party xterm, Pierre and sketch canvas rendering behavior is covered at the bundle/CSS boundary only.",
      ]
    : phase === 10
    ? [
        "No Electron launch, viewport resize, touch session, IME composition, browser-window session or screenshot was performed.",
        "Lexical import/export evidence is not runtime mounting evidence: capability queries, `browser:` variants and constant-false feature flags were read from compiled source, not exercised.",
        "The bounded slices contain only selected members; the original statement order and surrounding module code were not re-rendered or executed.",
        "Localization bundles were not inspected; only the default English strings embedded in the copied chunks are recorded.",
        "No iOS or Android build, emulator, native shell or platform-specific layout was inspected because the bundle contains none.",
      ]
    : phase === 8
    ? [
        "No Electron launch, `/scheduled` navigation, task creation, editing, pause/resume, delete, run-now, history archive, search or screenshot was performed.",
        "Lexical import/export evidence is not runtime mounting evidence: feature gates (`3927407613`, local/cloud automation capabilities), conditional branches and error states were not executed.",
        "The bounded slices contain only selected members; the original statement order and all surrounding module code were not re-rendered or executed.",
        "Localization bundles were not inspected; only the default English strings embedded in the copied chunks are recorded.",
        "App-server automation RPC methods, server-side scheduling and cloud execution were neither inspected nor exercised.",
      ]
    : phase === 7
    ? [
        "No Electron launch, Git page navigation, diff expansion, stage/unstage, commit, branch switch, worktree create/delete/restore or screenshot was performed in this phase.",
        "Lexical import/export evidence is not runtime mounting evidence; feature-gated review modes (git-based review disabled), conflict states and error branches were not executed.",
        "The full thread-side-panel-tab-content, code-diff, git-branch-switcher, worktrees-settings-page, local-conversation-git-actions and codex-composer-adapter files are not copied; only bounded member slices are semantically renamed.",
        "`@pierre/diffs` internals (presentation, highlight provider, themes, virtualized line position) were not inspected or reversed; only the Codex-side wrapper and its CSS variables are recorded.",
        "Localization bundles were not inspected; only the default English message strings embedded in the copied chunks are recorded.",
      ]
    : phase === 6
    ? [
        "No Electron launch, Settings navigation, provider add/edit/connect/test/delete or screenshot was performed in this phase.",
        "Lexical import/export evidence is not runtime mounting evidence; feature-gated settings sections and desktop-vs-web conditional branches were not executed.",
        "The full settings-page, settings-5591ce1b43e9, settings-content-layout, app-primary and general-settings files are not copied; only bounded member slices are semantically renamed.",
        "Localization bundles were not inspected; only the default English message strings embedded in the copied chunks are recorded.",
      ]
    : phase === 5
      ? [
        "No Electron launch, login, conversation rendering, composer typing, attachment upload, queueing or screenshot was performed in this phase.",
        "Lexical import/export evidence is not runtime mounting evidence; switch branches, memo-cache slots and composer mode branches were not executed.",
        "The full local-conversation-page, local-conversation-thread, turn-list, item, user-message, composer and send-message files are not copied; only bounded member slices are semantically renamed.",
        "No markdown, streamdown or diff library internals were inspected or reversed; only their call boundaries are recorded.",
      ]
    : phase === 4
      ? [
          "No Electron launch, login, route navigation, hover, context-menu or screenshot was performed in this phase.",
          "Lexical import/export evidence is not runtime mounting evidence; conditional branches inside the selected slices were not executed.",
          "The full app-primary, projects-index-page, page, local-conversation-page and remote-conversation-page files are not copied; only bounded member slices are semantically renamed.",
        ]
      : [
          "No Electron launch, login, route navigation or screenshot was performed in this phase.",
          "Lexical import/export evidence is not runtime mounting evidence.",
          "The full app-primary and app-initial files are not copied; only bounded member slices are semantically renamed.",
        ];

const verification = {
  generatedAt: new Date().toISOString(),
  generatedBy: "scripts/codex-full-restore-verify-semantic-20261103.mjs",
  phase,
  scope,
  baseline: {
    asarSha256: "335bb4f5bf2dd682d3fec3455b43935ff3b902043b4682fae74d1520aeec89e9",
    source: "02-baseline/formatted/webview",
  },
  summary,
  files: fileResults,
  bindings: bindingResults,
  css: cssResults,
  formattedCopies: copyResults,
  failures,
  unresolved: {
    note: "Names deliberately left unchanged because the slice does not contain enough evidence or the owning declaration is outside the bounded slice.",
    items: unresolvedItems,
  },
  unverified: unverifiedItems,
};

fs.mkdirSync(path.dirname(outputPath), { recursive: true });
fs.writeFileSync(outputPath, JSON.stringify(verification, null, 2));
console.log(JSON.stringify({ output: outputPath, ...summary }, null, 2));
process.exitCode = failures.length === 0 ? 0 : 1;
