#!/usr/bin/env node
/**
 * Static inspection helper for the full-restore Phase 3 slice.
 *
 * Read-only: parses one baseline-formatted JS file and prints JSON about its
 * imports, top-level declarations, exports, or dynamic imports. No code from
 * the bundle is executed.
 *
 * Usage:
 *   node scripts/codex-full-restore-analyze-20261103.mjs imports <file> [--tools DIR]
 *   node scripts/codex-full-restore-analyze-20261103.mjs exports <file> [--tools DIR]
 *   node scripts/codex-full-restore-analyze-20261103.mjs resolve <file> <exportedName> [--tools DIR]
 *   node scripts/codex-full-restore-analyze-20261103.mjs def <file> <localName> [--tools DIR]
 *   node scripts/codex-full-restore-analyze-20261103.mjs dynamic <file> [--tools DIR]
 */
import fs from "node:fs";
import path from "node:path";
import process from "node:process";
import { createRequire } from "node:module";

const require = createRequire(import.meta.url);

function parseArgs(argv) {
  const positional = [];
  let tools = "/tmp/codex-full-restore-tools-HShmrO";
  for (let i = 2; i < argv.length; i += 1) {
    if (argv[i] === "--tools") {
      tools = argv[i + 1];
      i += 1;
    } else {
      positional.push(argv[i]);
    }
  }
  return { positional, tools };
}

const { positional, tools } = parseArgs(process.argv);
const [command, file, name] = positional;
if (!command || !file) {
  console.error("usage: analyze <imports|exports|resolve|def|dynamic> <file> [name] [--tools DIR]");
  process.exit(2);
}
const parser = require(path.join(tools, "node_modules/@babel/parser"));
const source = fs.readFileSync(path.resolve(file), "utf8");
const ast = parser.parse(source, {
  sourceType: "module",
  plugins: ["jsx", "flow"],
  errorRecovery: false,
});

const imports = [];
const tops = new Map();
const exportsMap = new Map();
const dynamicImports = [];

function importedName(specifier) {
  if (specifier.type === "ImportDefaultSpecifier") return "default";
  if (specifier.type === "ImportNamespaceSpecifier") return "*";
  return specifier.imported.type === "StringLiteral"
    ? specifier.imported.value
    : specifier.imported.name;
}

function recordDeclaration(declaration) {
  if (!declaration) return;
  if (declaration.type === "FunctionDeclaration" || declaration.type === "ClassDeclaration") {
    if (declaration.id) tops.set(declaration.id.name, declaration);
    return;
  }
  if (declaration.type === "VariableDeclaration") {
    for (const declarator of declaration.declarations) {
      if (declarator.id.type === "Identifier") tops.set(declarator.id.name, declarator);
    }
  }
}

function walkDynamic(node) {
  if (!node || typeof node !== "object") return;
  if (node.type === "CallExpression" && node.callee?.type === "Import") {
    const arg = node.arguments?.[0];
    dynamicImports.push({
      line: node.loc.start.line,
      column: node.loc.start.column,
      source: arg?.type === "StringLiteral" ? arg.value : null,
    });
  }
  for (const [key, value] of Object.entries(node)) {
    if (key === "loc" || key === "start" || key === "end") continue;
    if (Array.isArray(value)) {
      for (const item of value) walkDynamic(item);
    } else if (value && typeof value === "object" && value.type) {
      walkDynamic(value);
    }
  }
}

for (const node of ast.program.body) {
  if (node.type === "ImportDeclaration") {
    for (const specifier of node.specifiers) {
      imports.push({
        local: specifier.local.name,
        imported: importedName(specifier),
        source: node.source.value,
        line: specifier.loc.start.line,
        column: specifier.loc.start.column,
      });
    }
    continue;
  }
  if (node.type === "ExportNamedDeclaration") {
    if (node.declaration) recordDeclaration(node.declaration);
    for (const specifier of node.specifiers) {
      const local = specifier.local.type === "StringLiteral" ? specifier.local.value : specifier.local.name;
      const exported = specifier.exported.type === "StringLiteral" ? specifier.exported.value : specifier.exported.name;
      exportsMap.set(exported, { local, source: node.source?.value ?? null, line: specifier.loc.start.line });
    }
    continue;
  }
  if (node.type === "ExportDefaultDeclaration") {
    exportsMap.set("default", { local: "<default>", source: null, line: node.loc.start.line });
    continue;
  }
  recordDeclaration(node);
  walkDynamic(node);
}

function range(node) {
  return {
    type: node.type,
    line: node.loc.start.line,
    column: node.loc.start.column,
    endLine: node.loc.end.line,
    endColumn: node.loc.end.column,
    start: node.start,
    end: node.end,
  };
}

if (command === "imports") {
  const bySource = {};
  for (const item of imports) (bySource[item.source] ??= []).push(item);
  console.log(JSON.stringify(bySource, null, 2));
} else if (command === "exports") {
  console.log(JSON.stringify([...exportsMap].map(([exported, info]) => ({ exported, ...info })), null, 2));
} else if (command === "resolve") {
  const info = exportsMap.get(name);
  if (!info) {
    console.log(JSON.stringify({ exported: name, found: false }));
    process.exit(1);
  }
  const declaration = info.local === "<default>" ? null : tops.get(info.local);
  const importBinding = imports.find((item) => item.local === info.local);
  console.log(JSON.stringify({ exported: name, ...info, declaration: declaration ? range(declaration) : null, importBinding: importBinding ?? null }, null, 2));
} else if (command === "def") {
  const declaration = tops.get(name);
  if (!declaration) {
    const importBinding = imports.find((item) => item.local === name);
    console.log(JSON.stringify({ local: name, found: Boolean(importBinding), importBinding: importBinding ?? null }, null, 2));
    process.exit(importBinding ? 0 : 1);
  }
  const snippet = source.slice(declaration.start, Math.min(declaration.end, declaration.start + 4000));
  console.log(JSON.stringify({ local: name, ...range(declaration), snippet }, null, 2));
} else if (command === "dynamic") {
  console.log(JSON.stringify(dynamicImports, null, 2));
} else {
  console.error(`unknown command: ${command}`);
  process.exit(2);
}
