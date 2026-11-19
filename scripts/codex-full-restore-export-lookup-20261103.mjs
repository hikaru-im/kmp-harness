#!/usr/bin/env node
/**
 * Resolve exported bindings in a concatenated bundle by Babel scope.
 *
 * Concatenated Rolldown chunks can contain several modules with the same local
 * minified name. A flat grep for the local name may return the wrong module.
 * This tool follows the ExportNamedDeclaration specifier to the owning scope
 * binding and prints its declaration position and initializer shape.
 *
 * Usage:
 *   node scripts/codex-full-restore-export-lookup-20261103.mjs \
 *     --file FILE --names Awt,Bwt [--tools DIR]
 */
import fs from "node:fs";
import path from "node:path";
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
const file = path.resolve(args.file);
const names = new Set((args.names ?? "").split(",").filter(Boolean));

if (!file || names.size === 0) {
  throw new Error("usage: --file FILE --names A,B");
}

const text = fs.readFileSync(file, "utf8");
const ast = parser.parse(text, { sourceType: "module", plugins: ["jsx", "flow"] });
const results = [];

function lineOf(offset) {
  return text.slice(0, offset).split("\n").length;
}

function describeBinding(binding) {
  if (!binding) return null;
  const node = binding.path.node;
  const identifier =
    node.type === "Identifier"
      ? node
      : node.id?.type === "Identifier"
        ? node.id
        : node.declarations?.[0]?.id?.type === "Identifier"
          ? node.declarations[0].id
          : null;
  const start = identifier?.start ?? node.start;
  let initKind = null;
  if (node.type === "FunctionDeclaration") initKind = "function";
  else if (node.type === "VariableDeclarator") initKind = node.init?.type ?? "no-init";
  else if (node.type === "ClassDeclaration") initKind = "class";
  else initKind = node.type;
  return {
    localName: binding.identifier.name,
    kind: binding.kind,
    declarationType: node.type,
    initKind,
    utf16Offset: start,
    line: lineOf(start),
    excerpt: text.slice(start, start + 120).replace(/\s+/g, " ").trim(),
  };
}

traverse(ast, {
  ExportNamedDeclaration(exportPath) {
    for (const specifier of exportPath.node.specifiers) {
      const exportedName =
        specifier.exported.type === "Identifier"
          ? specifier.exported.name
          : specifier.exported.value;
      if (!names.has(exportedName)) continue;
      const localName =
        specifier.local.type === "Identifier" ? specifier.local.name : specifier.local.value;
      const binding =
        exportPath.scope.getBinding(localName) ?? exportPath.scope.parent?.getBinding(localName);
      results.push({
        exportedName,
        localName,
        exportLine: lineOf(specifier.start),
        binding: describeBinding(binding),
      });
    }
  },
});

console.log(JSON.stringify({ file, requested: [...names], results }, null, 2));
