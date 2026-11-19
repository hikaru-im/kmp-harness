#!/usr/bin/env node
/**
 * Probe the extracted Codex ASAR for pipeline-relevant properties.
 *
 * Read-only: reads original/ text files and parses a bounded sample with
 * @babel/parser. It never executes archive code.
 *
 * Outputs probes/sourcemaps.json, obfuscation.json, module-wrapping.json,
 * react-compiler.json and probe-summary.json.
 */
import fs from "node:fs";
import path from "node:path";
import process from "node:process";
import { createRequire } from "node:module";

const require = createRequire(import.meta.url);

function parseArgs(argv) {
  const out = {};
  for (let i = 2; i < argv.length; i += 1) {
    const arg = argv[i];
    if (!arg.startsWith("--")) throw new Error(`Unexpected argument: ${arg}`);
    const key = arg.slice(2);
    const value = argv[i + 1];
    if (value === undefined || value.startsWith("--")) throw new Error(`Missing value for ${arg}`);
    out[key] = value;
    i += 1;
  }
  return out;
}

const args = parseArgs(process.argv);
const originalDir = path.resolve(args.original ?? "");
const sourceFilesPath = path.resolve(args["source-files"] ?? "");
const outputDir = path.resolve(args.output ?? "");
const toolsDir = path.resolve(args.tools ?? "");
if (!fs.existsSync(originalDir)) throw new Error(`original dir missing: ${originalDir}`);
if (!fs.existsSync(sourceFilesPath)) throw new Error(`source-files missing: ${sourceFilesPath}`);
if (fs.existsSync(outputDir)) throw new Error(`output dir already exists: ${outputDir}`);
fs.mkdirSync(outputDir, { recursive: true });

const babelParser = require(path.join(toolsDir, "node_modules/@babel/parser"));
const sourceFiles = JSON.parse(fs.readFileSync(sourceFilesPath, "utf8")).files;
const textFiles = sourceFiles.filter((f) => /\.(?:js|mjs|cjs|css|html?)$/i.test(f.path));
const jsFiles = textFiles.filter((f) => /\.(?:js|mjs|cjs)$/i.test(f.path));
const cssFiles = textFiles.filter((f) => /\.css$/i.test(f.path));
const htmlFiles = textFiles.filter((f) => /\.html?$/i.test(f.path));

const patterns = {
  packer: /eval\s*\(\s*function\s*\(\s*p\s*,\s*a\s*,\s*c\s*,\s*k\s*,\s*[er]\s*\)/g,
  aaencode: /[｡-ﾟ]{2,}/g,
  urlencode_eval: /eval\s*\(\s*(?:decodeURIComponent|unescape)\s*\(/g,
  urlencode_density: /(?:%[0-9A-Fa-f]{2}){20,}/g,
  string_array_ident: /_0x[0-9a-f]{4,}/gi,
  string_array_decl: /(?:var|let|const)\s+_0x[0-9a-f]{4,}\s*=\s*\[/gi,
  hex_escape: /\\x[0-9a-fA-F]{2}/g,
  unicode_escape: /\\u[0-9a-fA-F]{4}/g,
  base64_blob: /[A-Za-z0-9+/]{200,}={0,2}/g,
  atob: /\batob\s*\(/g,
  opaque_predicate: /while\s*\(\s*!!\s*\[\s*\]\s*\)|if\s*\(\s*!!\s*\[\s*\]\s*\)/g,
};
const modulePatterns = {
  esm_import: /^\s*import\s+(?:[\w*{][^;\n]*?\s+from\s+)?["'][^"']+["']/gm,
  esm_export: /^\s*export\s+(?:default\b|const\b|let\b|var\b|function\b|class\b|\{|\*)/gm,
  dynamic_import: /\bimport\s*\(/g,
  cjs_require: /\brequire\s*\(\s*["'][^"']+["']\s*\)/g,
  cjs_exports: /\b(?:module\.exports|exports\.[A-Za-z_$])/g,
  webpack_require: /__webpack_require__|webpackChunk/g,
  vite_preload: /__vitePreload|__vite__mapDeps/g,
  rolldown: /rolldown-runtime|__commonJS|__esm\b/g,
  umd: /typeof exports\s*===?\s*["']object["']|typeof define\s*===?\s*["']function["']/g,
  system_register: /System\.register\s*\(/g,
};
const compilerPatterns = {
  c_call: /\b[A-Za-z_$][\w$]*\.c\(\s*\d+\s*\)/g,
  useMemoCache: /\buseMemoCache\b/g,
  cache_index: /\bcache\[\d+\]/g,
  compilerCache: /\bcompilerCache\b/g,
  rc_runtime: /react-compiler-runtime|__reactCompilerRuntime/gi,
};

function countMatches(text, re) {
  re.lastIndex = 0;
  let count = 0;
  let first = null;
  let capped = false;
  let m;
  while ((m = re.exec(text)) !== null) {
    count += 1;
    if (first === null) first = { index: m.index, sample: text.slice(m.index, m.index + 120).replace(/\s+/g, " ") };
    if (count >= 100_000) {
      capped = true;
      break;
    }
  }
  return { count, first, capped };
}

function lineCol(text, index) {
  let line = 1;
  let last = 0;
  for (let i = 0; i < index && i < text.length; i += 1) {
    if (text.charCodeAt(i) === 10) {
      line += 1;
      last = i + 1;
    }
  }
  return { line, column: index - last + 1 };
}

function addHit(bucket, file, match) {
  bucket.count += match.count;
  bucket.files += 1;
  if (bucket.examples.length < 8) {
    bucket.examples.push({
      file: file.path,
      bytes: file.size,
      matches: match.count,
      capped: match.capped === true,
      first: match.first ? { ...lineCol(file.text, match.first.index), sample: match.first.sample } : null,
    });
  }
}

const obfuscation = {};
for (const name of Object.keys(patterns)) obfuscation[name] = { count: 0, files: 0, examples: [] };
const wrapping = {};
for (const name of Object.keys(modulePatterns)) wrapping[name] = { count: 0, files: 0, examples: [] };
const compiler = {};
for (const name of Object.keys(compilerPatterns)) compiler[name] = { count: 0, files: 0, examples: [] };

const sourcemaps = {
  mapEntries: [],
  urlHits: [],
  sourceUrlHits: [],
  sourcesContentHits: [],
  cssImports: [],
  htmlScripts: [],
};
const largeFiles = [];
let totalTextBytes = 0;
let scanned = 0;
const started = Date.now();

for (const file of textFiles) {
  const abs = path.join(originalDir, file.path);
  let text;
  try {
    text = fs.readFileSync(abs, "utf8");
  } catch (error) {
    sourcemaps.urlHits.push({ file: file.path, error: String(error) });
    continue;
  }
  file.text = text;
  file.size = Buffer.byteLength(text);
  totalTextBytes += file.size;
  scanned += 1;
  if (file.size >= 1_000_000) largeFiles.push({ path: file.path, bytes: file.size });

  // Sourcemap / sourceURL scan (tail is enough for sourceMappingURL).
  const tail = text.slice(Math.max(0, text.length - 32768));
  const sm = /\/\/[#@]\s*sourceMappingURL=([^\s]+)/.exec(tail) || /\/\*[#@]\s*sourceMappingURL=([^*]+)\*\//.exec(tail);
  if (sm) sourcemaps.urlHits.push({ file: file.path, url: sm[1].slice(0, 300), kind: "sourceMappingURL" });
  const su = /\/\/[#@]\s*sourceURL=([^\s]+)/.exec(tail);
  if (su) sourcemaps.sourceUrlHits.push({ file: file.path, url: su[1].slice(0, 300) });
  if (text.includes("sourcesContent")) sourcemaps.sourcesContentHits.push({ file: file.path });

  if (/\.(?:js|mjs|cjs)$/i.test(file.path)) {
    for (const [name, re] of Object.entries(patterns)) {
      const match = countMatches(text, re);
      if (match.count > 0) addHit(obfuscation[name], file, match);
    }
    for (const [name, re] of Object.entries(modulePatterns)) {
      const match = countMatches(text, re);
      if (match.count > 0) addHit(wrapping[name], file, match);
    }
    for (const [name, re] of Object.entries(compilerPatterns)) {
      const match = countMatches(text, re);
      if (match.count > 0) addHit(compiler[name], file, match);
    }
  } else if (/\.css$/i.test(file.path)) {
    for (const m of text.matchAll(/@import\s+(?:url\()?["']?([^"')\s;]+)/g)) {
      if (sourcemaps.cssImports.length < 200) sourcemaps.cssImports.push({ file: file.path, target: m[1].slice(0, 300) });
    }
  } else if (/\.html?$/i.test(file.path)) {
    for (const m of text.matchAll(/<script\b[^>]*\bsrc=["']([^"']+)["']/gi)) {
      sourcemaps.htmlScripts.push({ file: file.path, src: m[1].slice(0, 300) });
    }
  }
  file.text = undefined; // release
}

// AST confirmation of React Compiler cache calls on the highest-hit small files.
const astConfirm = {
  parser: `@babel/parser ${require(path.join(toolsDir, "node_modules/@babel/parser/package.json")).version}`,
  filesParsed: 0,
  filesFailed: 0,
  cacheCalls: 0,
  useMemoCacheCalls: 0,
  examples: [],
  failures: [],
};
// Re-read only a bounded candidate list; per-file text was released after scanning.
const candidateFiles = new Map();
for (const example of compiler.c_call.examples) candidateFiles.set(example.file, example.matches);
for (const example of compiler.useMemoCache.examples) candidateFiles.set(example.file, (candidateFiles.get(example.file) ?? 0) + example.matches);
const ordered = [...candidateFiles.entries()].sort((a, b) => b[1] - a[1]).slice(0, 40);
for (const [rel, hits] of ordered) {
  const abs = path.join(originalDir, rel);
  const stat = fs.statSync(abs);
  if (stat.size > 6_000_000) continue;
  const text = fs.readFileSync(abs, "utf8");
  let ast;
  try {
    ast = babelParser.parse(text, {
      sourceType: "unambiguous",
      errorRecovery: false,
      plugins: ["jsx", "typescript", "classProperties", "classPrivateProperties", "classPrivateMethods", "dynamicImport", "importMeta", "topLevelAwait"],
    });
  } catch (error) {
    astConfirm.filesFailed += 1;
    if (astConfirm.failures.length < 10) astConfirm.failures.push({ file: rel, error: String(error).slice(0, 300) });
    continue;
  }
  astConfirm.filesParsed += 1;
  const visit = (node) => {
    if (!node || typeof node !== "object") return;
    if (Array.isArray(node)) {
      for (const child of node) visit(child);
      return;
    }
    if (typeof node.type === "string") {
      if (node.type === "CallExpression") {
        const callee = node.callee;
        const args = node.arguments ?? [];
        if (callee?.type === "MemberExpression" && callee.computed === false && callee.property?.name === "c" && args.length === 1 && args[0]?.type === "NumericLiteral") {
          astConfirm.cacheCalls += 1;
          if (astConfirm.examples.length < 10) astConfirm.examples.push({ file: rel, line: node.loc?.start?.line ?? null, column: node.loc?.start?.column ?? null, kind: "member.c(n)" });
        } else if (callee?.type === "Identifier" && callee.name === "useMemoCache") {
          astConfirm.useMemoCacheCalls += 1;
          if (astConfirm.examples.length < 10) astConfirm.examples.push({ file: rel, line: node.loc?.start?.line ?? null, column: node.loc?.start?.column ?? null, kind: "useMemoCache(...)" });
        }
      }
    }
    for (const key of Object.keys(node)) {
      if (key === "loc" || key === "start" || key === "end" || key === "extra") continue;
      visit(node[key]);
    }
  };
  visit(ast.program);
}

const mapEntries = sourceFiles.filter((f) => /\.map$/i.test(f.path));

const obfuscationConclusion = {
  packer_or_aaencode: obfuscation.packer.count + obfuscation.aaencode.count,
  obfuscator_string_array: obfuscation.string_array_decl.count,
  high_hex_density_files: obfuscation.hex_escape.files,
  high_unicode_density_files: obfuscation.unicode_escape.files,
  base64_blob_files: obfuscation.base64_blob.files,
  assessed: true,
  note: "Counts are lexical hints, not proof of obfuscation. Absence of Packer/AAEncode plus overwhelmingly ESM module syntax is evidence for minified-but-not-obfuscated input.",
};

const output = {
  generatedAt: new Date().toISOString(),
  generatedBy: "scripts/codex-full-restore-probe-20261103.mjs",
  inputs: {
    original: originalDir,
    sourceFiles: sourceFilesPath,
    tools: toolsDir,
    scannedTextFiles: scanned,
    jsFiles: jsFiles.length,
    cssFiles: cssFiles.length,
    htmlFiles: htmlFiles.length,
    totalTextBytes,
  },
  durationSeconds: Number(((Date.now() - started) / 1000).toFixed(3)),
  sourcemaps: {
    mapEntries,
    ...sourcemaps,
    conclusion: {
      mapEntryCount: mapEntries.length,
      sourceMappingUrlCount: sourcemaps.urlHits.length,
      sourceUrlCount: sourcemaps.sourceUrlHits.length,
      sourcesContentCount: sourcemaps.sourcesContentHits.length,
      usableSourcemapEvidence: mapEntries.length > 0 || sourcemaps.urlHits.length > 0,
    },
  },
  obfuscation: { patterns: obfuscation, conclusion: obfuscationConclusion },
  moduleWrapping: { patterns: wrapping, largeFiles: largeFiles.sort((a, b) => b.bytes - a.bytes).slice(0, 30) },
  reactCompiler: { patterns: compiler, astConfirm },
  probeSummary: {
    textFiles: scanned,
    jsFiles: jsFiles.length,
    cssFiles: cssFiles.length,
    htmlFiles: htmlFiles.length,
    totalTextBytes,
    sourcemapUsable: mapEntries.length > 0 || sourcemaps.urlHits.length > 0,
    obfuscationEvidence: obfuscationConclusion.packer_or_aaencode + obfuscationConclusion.obfuscator_string_array,
    reactCompilerCacheCalls: astConfirm.cacheCalls,
    reactCompilerUseMemoCacheCalls: astConfirm.useMemoCacheCalls,
  },
};

for (const [name, value] of Object.entries(output)) {
  if (name === "generatedAt" || name === "generatedBy" || name === "inputs" || name === "durationSeconds") continue;
  fs.writeFileSync(path.join(outputDir, `${name}.json`), JSON.stringify(value, null, 2));
}
fs.writeFileSync(path.join(outputDir, "probe-summary.json"), JSON.stringify(output, null, 2));
console.log(JSON.stringify(output.probeSummary, null, 2));
