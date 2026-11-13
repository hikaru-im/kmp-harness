#!/usr/bin/env node
/**
 * Verify the formatted tree against the extracted originals.
 *
 * JS: Babel AST digest equality (locations/raw tokens excluded).
 * CSS: PostCSS canonical structure equality (raws/spaces/source excluded).
 * HTML: Prettier parser acceptance.
 * All files: SHA-256 of original checked against the inventory hash.
 *
 * Optional --idempotence reformats the formatted text and requires byte equality.
 */
import fs from "node:fs";
import path from "node:path";
import crypto from "node:crypto";
import process from "node:process";
import { createRequire } from "node:module";

const require = createRequire(import.meta.url);

function parseArgs(argv) {
  const out = { idempotence: false };
  for (let i = 2; i < argv.length; i += 1) {
    const arg = argv[i];
    if (!arg.startsWith("--")) throw new Error(`Unexpected argument: ${arg}`);
    const key = arg.slice(2);
    const next = argv[i + 1];
    if (next === undefined || next.startsWith("--")) {
      out[key] = true;
      continue;
    }
    out[key] = next;
    i += 1;
  }
  return out;
}

const args = parseArgs(process.argv);
const originalDir = path.resolve(args.original ?? "");
const formattedDir = path.resolve(args.formatted ?? "");
const sourceFilesPath = path.resolve(args["source-files"] ?? "");
const toolsDir = path.resolve(args.tools ?? "");
const reportPath = path.resolve(args.report ?? path.join(path.dirname(formattedDir), "verification.json"));
if (!fs.existsSync(formattedDir)) throw new Error(`formatted missing: ${formattedDir}`);
if (!fs.existsSync(sourceFilesPath)) throw new Error(`source-files missing: ${sourceFilesPath}`);

const babelParser = require(path.join(toolsDir, "node_modules/@babel/parser"));
const postcss = require(path.join(toolsDir, "node_modules/postcss"));
const prettier = require(path.join(toolsDir, "node_modules/prettier"));
const prettierVersion = require(path.join(toolsDir, "node_modules/prettier/package.json")).version;
const babelVersion = require(path.join(toolsDir, "node_modules/@babel/parser/package.json")).version;
const postcssVersion = require(path.join(toolsDir, "node_modules/postcss/package.json")).version;
const entries = JSON.parse(fs.readFileSync(sourceFilesPath, "utf8")).files;
const targets = entries.filter((f) => /\.(?:js|mjs|cjs|css|html?)$/i.test(f.path));

const IGNORED_AST_KEYS = new Set([
  "loc", "start", "end", "extra", "comments", "leadingComments", "trailingComments",
  "innerComments", "tokens", "errors", "range", "raw", "rawValue",
]);

const KEYED_NODE_TYPES = new Set([
  "ObjectProperty", "ObjectMethod", "ClassProperty", "ClassMethod",
  "ClassPrivateProperty", "ClassPrivateMethod", "ClassAccessorProperty",
]);

// Prettier's quoteProps=as-needed turns `{"default": 1}` into `{default: 1}`.
// Both name the same property, so compare the static key by its string value.
function staticKey(key) {
  if (!key) return key;
  if (key.type === "Identifier") return { type: "StaticKey", name: key.name };
  if (key.type === "StringLiteral") return { type: "StaticKey", name: key.value };
  if (key.type === "NumericLiteral") return { type: "StaticKey", name: String(key.value) };
  return key;
}

// `a && (b && c)` and `(a && b) && c` parse into different trees but mean the
// same short-circuit chain. Prettier drops the redundant parentheses.
function logicalChain(node, depth) {
  const operands = [];
  const collect = (current) => {
    if (current?.type === "LogicalExpression" && current.operator === node.operator) {
      collect(current.left);
      collect(current.right);
    } else {
      operands.push(canonical(current, depth + 1));
    }
  };
  collect(node);
  return { type: "LogicalChain", operator: node.operator, operands };
}

// Nested comma expressions are associative; Prettier may flatten them.
function sequenceChain(node, depth) {
  const expressions = [];
  const collect = (current) => {
    if (current?.type === "SequenceExpression") {
      for (const expression of current.expressions) collect(expression);
    } else {
      expressions.push(canonical(current, depth + 1));
    }
  };
  collect(node);
  return { type: "SequenceChain", expressions };
}

function canonical(value, depth = 0) {
  if (depth > 1000) throw new Error("AST too deep");
  if (typeof value === "bigint") return { $bigint: value.toString() };
  if (value === null || typeof value !== "object") return value;
  if (Array.isArray(value)) {
    // Prettier removes stray empty statements (`;;`).
    return value
      .filter((item) => item?.type !== "EmptyStatement")
      .map((item) => canonical(item, depth + 1));
  }
  if (value.type === "LogicalExpression") return logicalChain(value, depth);
  if (value.type === "SequenceExpression") return sequenceChain(value, depth);
  const out = {};
  for (const key of Object.keys(value).sort()) {
    if (IGNORED_AST_KEYS.has(key)) continue;
    const item = value[key];
    if (item === undefined) continue;
    out[key] = canonical(item, depth + 1);
  }
  if (KEYED_NODE_TYPES.has(value.type) && value.computed !== true) {
    out.key = staticKey(out.key);
  }
  // Prettier sorts regular-expression flags; order is not semantic.
  if (value.type === "RegExpLiteral" && typeof out.flags === "string") {
    out.flags = [...out.flags].sort().join("");
  }
  return out;
}

function astDigest(ast) {
  const h = crypto.createHash("sha256");
  h.update(JSON.stringify(canonical(ast.program ?? ast)));
  return h.digest("hex");
}

const BASE_PLUGINS = [
  "jsx", "classProperties", "classPrivateProperties", "classPrivateMethods",
  "dynamicImport", "importMeta", "topLevelAwait",
];
const PARSERS = {
  // Prettier formats .js with its babel parser, which enables Flow. Parsing
  // with the TypeScript plugin first misreads ambiguous expressions such as
  // `typeof X < \`u\``, so mirror Prettier: Flow first, TypeScript as fallback.
  flow: [...BASE_PLUGINS, "flow", "flowComments"],
  typescript: [...BASE_PLUGINS, "typescript"],
};

function parseJsWith(text, plugins) {
  return babelParser.parse(text, { sourceType: "unambiguous", errorRecovery: false, plugins });
}

function parseJsPair(originalText, formattedText) {
  let originalAst;
  let mode;
  try {
    originalAst = parseJsWith(originalText, PARSERS.flow);
    mode = "flow";
  } catch (flowError) {
    try {
      originalAst = parseJsWith(originalText, PARSERS.typescript);
      mode = "typescript";
    } catch {
      throw flowError;
    }
  }
  const formattedAst = parseJsWith(formattedText, PARSERS[mode]);
  return { originalAst, formattedAst, mode };
}

// Prettier is allowed to change only whitespace in CSS. Normalize runs of
// whitespace, spacing around commas/parentheses, and media-feature colons so a
// spacing-only change compares equal; all tokens, values and selectors remain.
function normalizeCssNumbers(text) {
  // Prettier adds a leading zero and drops redundant trailing zeros in numeric
  // literals. url() payloads are replaced by a content hash before this runs.
  return text.replace(
    /(^|[^\w.#-])(-?)(\d*\.\d+|\d+\.\d*|\d+)(?=(?:[a-zA-Z%]+)?(?:[^\w.-]|$))/g,
    (match, prefix, sign, number) => {
      const value = Number(`${sign}${number}`);
      return Number.isFinite(value) ? `${prefix}${value}` : match;
    },
  );
}

// Whitespace that Prettier may insert or remove between plain tokens.
function normalizeCssPlain(text) {
  return normalizeCssNumbers(text)
    .replace(/\s+/g, " ")
    .replace(/\s*,\s*/g, ",")
    .replace(/\(\s+/g, "(")
    .replace(/\s+\)/g, ")")
    .replace(/\)\s+/g, ")")
    .replace(/\s+\(/g, "(")
    // Shorthand separators (`font: 16px / 1.5`) are spacing-insensitive.
    .replace(/\s*\/\s*/g, "/")
    // Prettier spaces math operators in calc(); `*` spacing is optional.
    .replace(/\s*\*\s*/g, "*")
    .trim();
}

// Split a CSS value into string literals (kept verbatim), url() payloads
// (hashed by exact unescaped content) and plain text (whitespace-normalized).
// Joining the segments without extra spaces removes optional spacing between a
// quoted string and its neighbours.
function scanCss(text) {
  const input = String(text);
  let out = "";
  let plain = "";
  const flush = () => {
    if (plain) out += normalizeCssPlain(plain);
    plain = "";
  };
  for (let i = 0; i < input.length; ) {
    const ch = input[i];
    // A backslash-escaped quote belongs to an identifier (Tailwind class
    // selectors contain many of them), not to a string literal.
    if ((ch === '"' || ch === "'") && input[i - 1] !== "\\") {
      flush();
      let j = i + 1;
      while (j < input.length) {
        if (input[j] === "\\" && j + 1 < input.length) { j += 2; continue; }
        if (input[j] === ch) break;
        j += 1;
      }
      out += input.slice(i, Math.min(j + 1, input.length));
      i = j + 1;
      continue;
    }
    if (input.slice(i, i + 4).toLowerCase() === "url(") {
      let j = i + 4;
      while (j < input.length && /\s/.test(input[j])) j += 1;
      const quote = input[j];
      let content = "";
      if (quote === '"' || quote === "'") {
        j += 1;
        while (j < input.length && input[j] !== quote) {
          if (input[j] === "\\" && j + 1 < input.length) { content += input[j + 1]; j += 2; }
          else { content += input[j]; j += 1; }
        }
        j += 1;
      } else {
        while (j < input.length && input[j] !== ")") { content += input[j]; j += 1; }
      }
      while (j < input.length && input[j] !== ")") j += 1;
      j = Math.min(j + 1, input.length);
      flush();
      out += `url(#${crypto.createHash("sha256").update(content).digest("hex")})`;
      i = j;
      continue;
    }
    plain += ch;
    i += 1;
  }
  flush();
  return out;
}

function normalizeCssSelector(text) {
  return scanCss(text)
    .replace(/\s*([>+~])\s*/g, " $1 ")
    // Prettier quotes attribute values that were written unquoted.
    .replace(/\[([^\[\]]*?)=(["'])([^"'\]]*)\2\]/g, "[$1=$3]");
}

function normalizeCssParams(text) {
  return scanCss(text).replace(/\s*:\s*/g, ":");
}

function canonicalCss(node) {
  const out = { type: node.type };
  if (node.type === "rule") out.selector = normalizeCssSelector(node.selector);
  if (node.type === "decl") {
    out.prop = node.prop;
    out.value = scanCss(node.value);
    out.important = Boolean(node.important);
  }
  if (node.type === "atrule") {
    out.name = node.name;
    out.params = normalizeCssParams(node.params ?? "");
  }
  if (node.type === "comment") out.text = node.text.replace(/\s+/g, " ").trim();
  if (node.nodes) out.nodes = node.nodes.map(canonicalCss);
  return out;
}

function firstDifference(a, b, pathStr = "$") {
  if (a === b) return null;
  if (typeof a !== typeof b || a === null || b === null || typeof a !== "object") {
    return { path: pathStr, original: String(a).slice(0, 160), formatted: String(b).slice(0, 160) };
  }
  if (Array.isArray(a) || Array.isArray(b)) {
    if (!Array.isArray(a) || !Array.isArray(b) || a.length !== b.length) {
      return { path: pathStr, original: `array(${Array.isArray(a) ? a.length : a})`, formatted: `array(${Array.isArray(b) ? b.length : b})` };
    }
    for (let i = 0; i < a.length; i += 1) {
      const diff = firstDifference(a[i], b[i], `${pathStr}[${i}]`);
      if (diff) return diff;
    }
    return null;
  }
  const keys = new Set([...Object.keys(a), ...Object.keys(b)]);
  for (const key of [...keys].sort()) {
    const diff = firstDifference(a[key], b[key], `${pathStr}.${key}`);
    if (diff) return diff;
  }
  return null;
}

function sha256(buffer) {
  return crypto.createHash("sha256").update(buffer).digest("hex");
}

const started = Date.now();
const results = [];
const failures = [];
let done = 0;
let lastLog = Date.now();

function writeReport(complete) {
  const report = {
    generatedAt: new Date().toISOString(),
    generatedBy: "scripts/codex-full-restore-verify-20261103.mjs",
    complete,
    tools: { babelParser: babelVersion, postcss: postcssVersion, prettier: prettierVersion },
    canonicalization: {
      jsParse: "Flow plugins first, TypeScript fallback, matching Prettier's babel parser for .js.",
      js: "AST compared after dropping loc/start/end/extra/comments/tokens/errors/range/raw/rawValue and EmptyStatement; bigint values serialized as decimal strings; static property keys compared by string value; same-operator &&/||/?? chains and nested comma expressions flattened; regex flag order ignored.",
      css: "Rules/declarations/at-rules compared structurally; whitespace runs, comma/paren spacing, media-feature colon spacing and selector combinator spacing ignored; attribute-value quotes ignored; numeric leading/trailing zeros normalized; url() payloads compared by sha256 of the unescaped content; string literals compared verbatim.",
      html: "Prettier HTML parser must parse the formatted file; byte equality with the original is not expected.",
    },
    inputs: { original: originalDir, formatted: formattedDir, sourceFiles: sourceFilesPath },
    durationSeconds: Number(((Date.now() - started) / 1000).toFixed(3)),
    totals: {
      files: done,
      js: results.filter((r) => r.kind === "js").length,
      css: results.filter((r) => r.kind === "css").length,
      html: results.filter((r) => r.kind === "html").length,
      jsAstEqual: results.filter((r) => r.kind === "js" && r.astEqual === true).length,
      cssCanonicalEqual: results.filter((r) => r.kind === "css" && r.canonicalEqual === true).length,
      htmlParsed: results.filter((r) => r.kind === "html" && r.parseOk === true).length,
      hashVerified: results.filter((r) => r.hashVerified === true).length,
      failures: failures.length,
    },
    failures,
    files: results,
  };
  fs.writeFileSync(reportPath, JSON.stringify(report, null, 2));
}

for (const entry of targets) {
  const rel = entry.path;
  const originalPath = path.join(originalDir, rel);
  const formattedPath = path.join(formattedDir, rel);
  const row = { path: rel, kind: /\.(?:js|mjs|cjs)$/i.test(rel) ? "js" : /\.css$/i.test(rel) ? "css" : "html" };
  try {
    if (!fs.existsSync(formattedPath)) throw new Error("formatted file missing");
    const originalText = fs.readFileSync(originalPath, "utf8");
    const formattedText = fs.readFileSync(formattedPath, "utf8");
    row.originalBytes = Buffer.byteLength(originalText);
    row.formattedBytes = Buffer.byteLength(formattedText);
    row.originalSha256 = sha256(fs.readFileSync(originalPath));
    row.hashVerified = row.originalSha256 === entry.sha256;
    if (!row.hashVerified) failures.push({ path: rel, reason: "original hash differs from inventory", expected: entry.sha256, actual: row.originalSha256 });

    if (row.kind === "js") {
      const { originalAst, formattedAst, mode } = parseJsPair(originalText, formattedText);
      row.parseMode = mode;
      row.originalAstDigest = astDigest(originalAst);
      row.formattedAstDigest = astDigest(formattedAst);
      row.astEqual = row.originalAstDigest === row.formattedAstDigest;
      if (!row.astEqual) {
        const diff = firstDifference(canonical(originalAst.program), canonical(formattedAst.program));
        row.firstDifference = diff;
        failures.push({ path: rel, reason: "JS AST differs", firstDifference: diff });
      }
      if (args.idempotence) {
        const twice = await prettier.format(formattedText, {
          parser: "babel",
          printWidth: 80, tabWidth: 2, useTabs: false, semi: true, singleQuote: false,
          quoteProps: "as-needed", trailingComma: "all", bracketSpacing: true,
          arrowParens: "always", endOfLine: "lf",
        });
        row.idempotent = twice === formattedText;
        if (!row.idempotent) failures.push({ path: rel, reason: "JS formatting is not idempotent" });
      }
    } else if (row.kind === "css") {
      const originalRoot = postcss.parse(originalText, { from: originalPath });
      const formattedRoot = postcss.parse(formattedText, { from: formattedPath });
      const originalCanonical = canonicalCss(originalRoot);
      const formattedCanonical = canonicalCss(formattedRoot);
      row.originalDeclarations = originalRoot.nodes.reduce((n, node) => n + countDecls(node), 0);
      row.formattedDeclarations = formattedRoot.nodes.reduce((n, node) => n + countDecls(node), 0);
      row.originalRules = originalRoot.nodes.filter((n) => n.type === "rule").length;
      row.formattedRules = formattedRoot.nodes.filter((n) => n.type === "rule").length;
      row.parseOk = true;
      row.canonicalEqual = JSON.stringify(originalCanonical) === JSON.stringify(formattedCanonical);
      if (!row.canonicalEqual) row.firstDifference = firstDifference(originalCanonical, formattedCanonical);
      if (!row.canonicalEqual) failures.push({ path: rel, reason: "CSS canonical structure differs", firstDifference: row.firstDifference });
      if (args.idempotence) {
        const twice = await prettier.format(formattedText, { parser: "css", printWidth: 80, endOfLine: "lf" });
        row.idempotent = twice === formattedText;
        if (!row.idempotent) failures.push({ path: rel, reason: "CSS formatting is not idempotent" });
      }
    } else {
      try {
        prettier.__debug.parse(originalText, { parser: "html" });
        prettier.__debug.parse(formattedText, { parser: "html" });
        row.parseOk = true;
      } catch (error) {
        row.parseOk = false;
        failures.push({ path: rel, reason: "HTML parse failed", error: String(error).slice(0, 500) });
      }
      if (args.idempotence) {
        const twice = await prettier.format(formattedText, { parser: "html", printWidth: 80, endOfLine: "lf" });
        row.idempotent = twice === formattedText;
        if (!row.idempotent) failures.push({ path: rel, reason: "HTML formatting is not idempotent" });
      }
    }
  } catch (error) {
    row.error = String(error).slice(0, 1000);
    failures.push({ path: rel, reason: "verification exception", error: row.error });
  }
  results.push(row);
  done += 1;
  if (done % 250 === 0 || Date.now() - lastLog > 15000) {
    lastLog = Date.now();
    const elapsed = (Date.now() - started) / 1000;
    console.log(JSON.stringify({
      done,
      total: targets.length,
      failures: failures.length,
      elapsedSeconds: Number(elapsed.toFixed(1)),
      filesPerSecond: Number((done / elapsed).toFixed(2)),
    }));
    writeReport(false);
  }
}

writeReport(true);
const summary = {
  total: targets.length,
  js: results.filter((r) => r.kind === "js").length,
  css: results.filter((r) => r.kind === "css").length,
  html: results.filter((r) => r.kind === "html").length,
  jsAstEqual: results.filter((r) => r.kind === "js" && r.astEqual === true).length,
  cssCanonicalEqual: results.filter((r) => r.kind === "css" && r.canonicalEqual === true).length,
  htmlParsed: results.filter((r) => r.kind === "html" && r.parseOk === true).length,
  hashVerified: results.filter((r) => r.hashVerified === true).length,
  idempotent: results.filter((r) => r.idempotent === true).length,
  failures: failures.length,
  durationSeconds: Number(((Date.now() - started) / 1000).toFixed(3)),
  report: reportPath,
};
console.log(JSON.stringify(summary, null, 2));
process.exitCode = failures.length === 0 ? 0 : 1;

function countDecls(node) {
  let n = node.type === "decl" ? 1 : 0;
  if (node.nodes) for (const child of node.nodes) n += countDecls(child);
  return n;
}
