#!/usr/bin/env node
/**
 * Apply evidence-backed binding renames to one formatted file (or member slice)
 * and prove the transformation is byte-reversible.
 *
 * The tool edits only identifier tokens resolved through Babel scopes. It never
 * reformats, generates code, or evaluates bundle code.
 *
 * Usage:
 *   node scripts/codex-full-restore-semantic-20261103.mjs \
 *     --file <formatted.js> [--source <baseline formatted.js>] \
 *     [--slice-manifest <slice.json>] \
 *     --renames <renames.json> --output <semantic.js> --report <report.json> \
 *     [--tools DIR]
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
const filePath = path.resolve(args.file);
const sourcePath = path.resolve(args.source ?? args.file);
const sliceManifestPath = args["slice-manifest"] ? path.resolve(args["slice-manifest"]) : null;
const renamesPath = path.resolve(args.renames);
const outputPath = path.resolve(args.output);
const reportPath = path.resolve(args.report);

const text = fs.readFileSync(filePath, "utf8");
const baselineText = fs.readFileSync(sourcePath, "utf8");
const baselineSha256 = crypto.createHash("sha256").update(fs.readFileSync(sourcePath)).digest("hex");
const sliceManifest = sliceManifestPath ? JSON.parse(fs.readFileSync(sliceManifestPath, "utf8")) : null;
const renameSpecs = JSON.parse(fs.readFileSync(renamesPath, "utf8"));
if (!Array.isArray(renameSpecs)) throw new Error("renames must be an array");

const ast = parser.parse(text, {
  sourceType: "module",
  plugins: ["jsx", "flow"],
  errorRecovery: false,
});

const identifiers = new Map();
const functionAncestors = new Map();
traverse(ast, {
  Identifier(identifierPath) {
    identifiers.set(identifierPath.node.start, identifierPath);
    let current = identifierPath;
    while (current && !current.isFunctionDeclaration()) current = current.parentPath;
    functionAncestors.set(identifierPath.node.start, current?.node?.id?.name ?? "<module>");
  },
});

function findAnchor(spec) {
  const occurrence = spec.declAnchorOccurrence ?? 0;
  let from = 0;
  let found = -1;
  for (let i = 0; i <= occurrence; i += 1) {
    found = text.indexOf(spec.declAnchor, from);
    if (found < 0) break;
    from = found + 1;
  }
  if (found < 0) throw new Error(`declAnchor not found (${occurrence}): ${JSON.stringify(spec.declAnchor)}`);
  return found;
}

function findTokenOffset(anchorText, anchorStart, token) {
  const escaped = token.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  const pattern = new RegExp(`(?<![\\w$])${escaped}(?![\\w$])`, "g");
  let match;
  while ((match = pattern.exec(anchorText)) !== null) {
    const offset = anchorStart + match.index;
    if (identifiers.has(offset)) return offset;
  }
  throw new Error(`token ${token} not found as an identifier in anchor ${JSON.stringify(anchorText)}`);
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

function lineColumnAt(offset) {
  return lineColumnIn(baselineText, offset);
}

function toBaselineOffset(sliceOffset) {
  if (!sliceManifest) return sliceOffset;
  for (const member of sliceManifest.members) {
    const start = member.sliceStartUtf16;
    const end = start + member.utf16Length;
    if (sliceOffset >= start && sliceOffset < end) {
      return member.sourceStart + (sliceOffset - start);
    }
  }
  throw new Error(`offset ${sliceOffset} is outside the slice member ranges; imports cannot be renamed`);
}

function positionFor(sliceOffset, oldText) {
  const baselineOffset = toBaselineOffset(sliceOffset);
  const { line, column } = lineColumnAt(baselineOffset);
  const byteOffset = Buffer.byteLength(baselineText.slice(0, baselineOffset), "utf8");
  return {
    utf16Offset: baselineOffset,
    byteOffset,
    line,
    column,
    sliceUtf16Offset: sliceOffset,
    excerpt: baselineText.slice(baselineOffset, baselineOffset + oldText.length),
  };
}

const edits = [];
const reports = [];
const seenStarts = new Set();
for (const spec of renameSpecs) {
  const anchorStart = findAnchor(spec);
  const declOffset = findTokenOffset(spec.declAnchor, anchorStart, spec.old);
  const identifierPath = identifiers.get(declOffset);
  if (!identifierPath) throw new Error(`no identifier at ${declOffset} for ${spec.old}`);
  let binding = identifierPath.scope.getBinding(spec.old);
  if (!binding) throw new Error(`no binding for ${spec.old} at slice offset ${declOffset}`);
  if (binding.identifier.start !== declOffset) {
    // A function declaration's identifier is visited in the function's own
    // scope, which can be shadowed by a same-named local declared in the body.
    // Resolve the declaration through the parent scope before rejecting it.
    const parentBinding = identifierPath.scope.parent?.getBinding(spec.old);
    if (parentBinding?.identifier.start === declOffset) binding = parentBinding;
  }
  if (binding.identifier.start !== declOffset) {
    throw new Error(
      `anchor resolved to a reference, not the binding declaration: ${spec.old} at ${declOffset}`,
    );
  }
  if (binding.scope.getOwnBinding(spec.new)) {
    throw new Error(
      `semantic name ${spec.new} is already bound in the owning scope of ${spec.old}`,
    );
  }
  const referencePaths = [...binding.referencePaths].filter(
    (reference) => reference.node?.type === "Identifier",
  );
  const violationNodes = [...binding.constantViolations].map((violation) => {
    const node = violation.node;
    if (node?.type === "Identifier") return node;
    if (node?.type === "AssignmentExpression" && node.left.type === "Identifier") return node.left;
    if (node?.type === "UpdateExpression" && node.argument.type === "Identifier") return node.argument;
    throw new Error(
      `unsupported constant violation for ${spec.old}: ${node?.type ?? "<none>"}`,
    );
  });
  const targetByStart = new Map();
  for (const node of [binding.identifier, ...referencePaths.map((reference) => reference.node), ...violationNodes]) {
    targetByStart.set(node.start, node);
  }
  const targets = [...targetByStart.values()];
  const localEdits = [];
  for (const target of targets) {
    if (seenStarts.has(target.start)) throw new Error(`overlapping rename at offset ${target.start}`);
    seenStarts.add(target.start);
    if (text.slice(target.start, target.end) !== spec.old) {
      throw new Error(`token mismatch at ${target.start}: ${text.slice(target.start, target.end)}`);
    }
    localEdits.push({ start: target.start, end: target.end, old: spec.old, new: spec.new });
  }
  edits.push(...localEdits);
  reports.push({
    file: path.basename(filePath),
    baselineFile: path.basename(sourcePath),
    originalPath: spec.originalPath ?? null,
    scope: functionAncestors.get(declOffset) ?? "<module>",
    old: spec.old,
    new: spec.new,
    oldKind: binding.kind,
    declAnchor: spec.declAnchor,
    declaration: positionFor(binding.identifier.start, spec.old),
    references: referencePaths.map((reference) => positionFor(reference.node.start, spec.old)),
    constantViolations: violationNodes.map((node) => positionFor(node.start, spec.old)),
    evidence: spec.evidence,
    confidence: spec.confidence,
    anchors: spec.anchors ?? [spec.declAnchor],
  });
}

edits.sort((a, b) => b.start - a.start);
let semanticText = text;
for (const edit of edits) {
  semanticText = semanticText.slice(0, edit.start) + edit.new + semanticText.slice(edit.end);
}
fs.mkdirSync(path.dirname(outputPath), { recursive: true });
fs.writeFileSync(outputPath, semanticText);

// Build the inverse edit positions from the ascending original order.
const ascending = [...edits].sort((a, b) => a.start - b.start);
let delta = 0;
const inverse = [];
for (const edit of ascending) {
  const newStart = edit.start + delta;
  inverse.push({ start: newStart, end: newStart + edit.new.length, old: edit.new, new: edit.old });
  delta += edit.new.length - edit.old.length;
}
let restored = semanticText;
for (const edit of inverse.sort((a, b) => b.start - a.start)) {
  if (restored.slice(edit.start, edit.end) !== edit.old) {
    throw new Error(`inverse token mismatch at ${edit.start}: ${restored.slice(edit.start, edit.end)}`);
  }
  restored = restored.slice(0, edit.start) + edit.new + restored.slice(edit.end);
}
const inverseByteEqual = restored === text;
if (!inverseByteEqual) throw new Error("inverse rename did not restore the formatted text");

try {
  parser.parse(semanticText, { sourceType: "module", plugins: ["jsx", "flow"] });
} catch (error) {
  throw new Error(`semantic output does not parse: ${error}`);
}

const targetByStart = new Map();
let targetDelta = 0;
for (const edit of ascending) {
  targetByStart.set(edit.start, {
    start: edit.start + targetDelta,
    end: edit.start + targetDelta + edit.new.length,
  });
  targetDelta += edit.new.length - edit.old.length;
}

function semanticPositionAt(sliceOffset, newText) {
  const target = targetByStart.get(sliceOffset);
  if (!target) throw new Error(`no semantic target for slice offset ${sliceOffset}`);
  const { line, column } = lineColumnIn(semanticText, target.start);
  return {
    utf16Offset: target.start,
    byteOffset: Buffer.byteLength(semanticText.slice(0, target.start), "utf8"),
    sliceUtf16Offset: target.start,
    line,
    column,
    excerpt: semanticText.slice(target.start, target.end),
  };
}

for (const entry of reports) {
  entry.targetDeclaration = semanticPositionAt(entry.declaration.sliceUtf16Offset, entry.new);
  entry.targetReferences = entry.references.map((reference) =>
    semanticPositionAt(reference.sliceUtf16Offset, entry.new),
  );
  entry.targetConstantViolations = entry.constantViolations.map((violation) =>
    semanticPositionAt(violation.sliceUtf16Offset, entry.new),
  );
}

const report = {
  generatedAt: new Date().toISOString(),
  generatedBy: "scripts/codex-full-restore-semantic-20261103.mjs",
  formattedFile: filePath,
  baselineFile: sourcePath,
  baselineSha256,
  semanticFile: outputPath,
  semanticSha256: crypto.createHash("sha256").update(semanticText).digest("hex"),
  sliceManifest: sliceManifestPath,
  renameCount: reports.length,
  editCount: edits.length,
  inverseByteEqual,
  semanticParses: true,
  textChanged: semanticText !== text,
  bindings: reports,
};
fs.mkdirSync(path.dirname(reportPath), { recursive: true });
fs.writeFileSync(reportPath, JSON.stringify(report, null, 2));
console.log(JSON.stringify({
  output: outputPath,
  renames: reports.length,
  edits: edits.length,
  inverseByteEqual,
  changed: semanticText !== text,
}, null, 2));
