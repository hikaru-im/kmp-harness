#!/usr/bin/env node
/**
 * Build a bounded member slice from one baseline-formatted JS file.
 *
 * The slice keeps only the requested top-level declarations (and the whole
 * VariableDeclaration that contains them), plus the import specifiers those
 * members actually reference. Member text is copied byte-for-byte from the
 * baseline; no bundle code is executed.
 *
 * Usage:
 *   node scripts/codex-full-restore-slice-20261103.mjs \
 *     --source <formatted.js> --members "A,B,C" \
 *     --output <slice.js> --manifest <slice.json> [--tools DIR]
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
    const key = argv[i];
    if (!key.startsWith("--")) throw new Error(`unexpected argument: ${key}`);
    out[key.slice(2)] = argv[i + 1];
    i += 1;
  }
  return out;
}

const args = parseArgs(process.argv);
const tools = args.tools ?? "/tmp/codex-full-restore-tools-HShmrO";
const parser = require(path.join(tools, "node_modules/@babel/parser"));
const traverse = require(path.join(tools, "node_modules/@babel/traverse")).default;
const sourcePath = path.resolve(args.source);
const outputPath = path.resolve(args.output);
const manifestPath = path.resolve(args.manifest);
const requested = String(args.members ?? "")
  .split(",")
  .map((name) => name.trim())
  .filter(Boolean);
if (requested.length === 0) throw new Error("--members is empty");

const source = fs.readFileSync(sourcePath, "utf8");
const sourceSha256 = crypto.createHash("sha256").update(fs.readFileSync(sourcePath)).digest("hex");
const ast = parser.parse(source, {
  sourceType: "module",
  plugins: ["jsx", "flow"],
  errorRecovery: false,
});

const declarations = new Map();
const statementByStart = new Map();
for (const node of ast.program.body) {
  if (node.type === "FunctionDeclaration" || node.type === "ClassDeclaration") {
    if (node.id) {
      declarations.set(node.id.name, { node, statement: node, kind: node.type });
      statementByStart.set(node.start, node);
    }
    continue;
  }
  if (node.type === "VariableDeclaration") {
    statementByStart.set(node.start, node);
    for (const declarator of node.declarations) {
      if (declarator.id.type === "Identifier") {
        declarations.set(declarator.id.name, {
          node: declarator,
          statement: node,
          kind: "VariableDeclaration",
        });
      }
    }
  }
}

const selected = new Map();
const missing = [];
for (const name of requested) {
  const found = declarations.get(name);
  if (!found) {
    missing.push(name);
    continue;
  }
  selected.set(found.statement.start, found.statement);
}
if (missing.length > 0) throw new Error(`members not found: ${missing.join(", ")}`);

const selectedStatements = [...selected.values()].sort((a, b) => a.start - b.start);
const selectedNames = new Set();
for (const statement of selectedStatements) {
  if (statement.type === "FunctionDeclaration" || statement.type === "ClassDeclaration") {
    if (statement.id) selectedNames.add(statement.id.name);
  } else if (statement.type === "VariableDeclaration") {
    for (const declarator of statement.declarations) {
      if (declarator.id.type === "Identifier") selectedNames.add(declarator.id.name);
    }
  }
}

const importSpecifiers = new Map(); // local -> {source, imported, local, line}
for (const node of ast.program.body) {
  if (node.type !== "ImportDeclaration") continue;
  for (const specifier of node.specifiers) {
    const imported =
      specifier.type === "ImportDefaultSpecifier"
        ? "default"
        : specifier.type === "ImportNamespaceSpecifier"
          ? "*"
          : specifier.imported.type === "StringLiteral"
            ? specifier.imported.value
            : specifier.imported.name;
    importSpecifiers.set(specifier.local.name, {
      source: node.source.value,
      imported,
      local: specifier.local.name,
      line: specifier.loc.start.line,
      column: specifier.loc.start.column,
      specifierType: specifier.type,
    });
  }
}

const usedImports = new Map();
const unresolvedTopLevel = new Map();
const globals = new Set();
const selectedRanges = selectedStatements.map((statement) => [statement.start, statement.end]);
function insideSelected(offset) {
  return selectedRanges.some(([start, end]) => offset >= start && offset < end);
}
traverse(ast, {
  Identifier(identifierPath) {
    if (!identifierPath.isReferencedIdentifier()) return;
    if (!insideSelected(identifierPath.node.start)) return;
    const name = identifierPath.node.name;
    if (selectedNames.has(name)) return;
    const binding = identifierPath.scope.getBinding(name);
    if (!binding) {
      globals.add(name);
      return;
    }
    if (!binding.scope.path.isProgram()) return;
    const importInfo = importSpecifiers.get(name);
    if (importInfo) {
      usedImports.set(name, importInfo);
      return;
    }
    const parent = binding.path.parent;
    const line = binding.path.node.loc?.start.line ?? parent?.loc?.start.line ?? null;
    const type = binding.path.node.type;
    if (!unresolvedTopLevel.has(name)) unresolvedTopLevel.set(name, { name, type, line });
  },
});

const importsBySource = new Map();
for (const info of [...usedImports.values()].sort((a, b) => a.line - b.line || a.column - b.column)) {
  if (!importsBySource.has(info.source)) importsBySource.set(info.source, []);
  importsBySource.get(info.source).push(info);
}

function renderImport(info) {
  if (info.specifierType === "ImportDefaultSpecifier") return `default as ${info.local}`;
  if (info.specifierType === "ImportNamespaceSpecifier") return `* as ${info.local}`;
  return info.imported === info.local ? info.local : `${info.imported} as ${info.local}`;
}

const headerLines = [
  "/*",
  ` * Phase ${args.phase ?? "3"} member slice generated by scripts/codex-full-restore-slice-20261103.mjs`,
  ` * Source: ${path.basename(sourcePath)}`,
  ` * Source SHA-256: ${sourceSha256}`,
  ` * Members: ${requested.join(", ")}`,
  " * This is a reading slice, not a standalone runnable module. Free identifiers",
  " * and unresolved top-level references are recorded in the slice manifest.",
  " */",
];
const importLines = [];
for (const [importSource, infos] of importsBySource) {
  importLines.push(
    `import { ${infos.map(renderImport).join(", ")} } from ${JSON.stringify(importSource)};`,
  );
}

const chunks = [];
const memberRanges = [];
let cursor = headerLines.join("\n") + "\n" + (importLines.length ? importLines.join("\n") + "\n\n" : "\n");
for (const statement of selectedStatements) {
  const text = source.slice(statement.start, statement.end);
  const sliceStart = Buffer.byteLength(cursor, "utf8");
  const sliceStartUtf16 = cursor.length;
  chunks.push(text);
  memberRanges.push({
    members: statement.type === "VariableDeclaration"
      ? statement.declarations.map((d) => (d.id.type === "Identifier" ? d.id.name : null)).filter(Boolean)
      : [statement.id?.name].filter(Boolean),
    type: statement.type,
    sourceStart: statement.start,
    sourceEnd: statement.end,
    sourceStartLine: statement.loc.start.line,
    sourceEndLine: statement.loc.end.line,
    sliceStartByte: sliceStart,
    sliceStartUtf16,
    bytes: Buffer.byteLength(text, "utf8"),
    utf16Length: text.length,
  });
  cursor += text + "\n\n";
}
const footer = [
  "/* Unresolved top-level references (declared outside this slice):",
  [...unresolvedTopLevel.values()]
    .map((item) => ` * - ${item.name} (${item.type}, source line ${item.line ?? "?"})`)
    .join("\n") || " * (none)",
  " */",
  "",
].join("\n");
const output = headerLines.join("\n") + "\n" + (importLines.length ? importLines.join("\n") + "\n\n" : "\n") + chunks.join("\n\n") + "\n\n" + footer;
fs.mkdirSync(path.dirname(outputPath), { recursive: true });
fs.writeFileSync(outputPath, output);

const manifest = {
  generatedAt: new Date().toISOString(),
  generatedBy: "scripts/codex-full-restore-slice-20261103.mjs",
  source: {
    path: sourcePath,
    sha256: sourceSha256,
    bytes: Buffer.byteLength(source, "utf8"),
    utf16Length: source.length,
  },
  output: {
    path: outputPath,
    sha256: crypto.createHash("sha256").update(output).digest("hex"),
    bytes: Buffer.byteLength(output, "utf8"),
    utf16Length: output.length,
  },
  requested,
  members: memberRanges,
  imports: [...importsBySource].map(([importSource, infos]) => ({
    source: importSource,
    specifiers: infos,
  })),
  unresolvedTopLevel: [...unresolvedTopLevel.values()].sort((a, b) => (a.line ?? 0) - (b.line ?? 0)),
  globals: [...globals].sort(),
};
fs.mkdirSync(path.dirname(manifestPath), { recursive: true });
fs.writeFileSync(manifestPath, JSON.stringify(manifest, null, 2));
console.log(JSON.stringify({
  output: outputPath,
  bytes: manifest.output.bytes,
  members: memberRanges.length,
  imports: manifest.imports.reduce((n, item) => n + item.specifiers.length, 0),
  unresolvedTopLevel: manifest.unresolvedTopLevel.length,
  globals: manifest.globals.length,
}, null, 2));
