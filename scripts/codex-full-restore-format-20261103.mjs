#!/usr/bin/env node
/**
 * Format every extracted JS/CSS/HTML file with the pinned Prettier.
 *
 * Reads original/ (never writes it) and writes a parallel formatted/ tree.
 * Reports per-file status, byte counts and timing to format-report.json.
 * The script is restartable only into a fresh output directory by design.
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
const outputDir = path.resolve(args.formatted ?? "");
const sourceFilesPath = path.resolve(args["source-files"] ?? "");
const toolsDir = path.resolve(args.tools ?? "");
const reportPath = path.resolve(args.report ?? path.join(path.dirname(outputDir), "format-report.json"));
if (!fs.existsSync(originalDir)) throw new Error(`original missing: ${originalDir}`);
if (!fs.existsSync(sourceFilesPath)) throw new Error(`source-files missing: ${sourceFilesPath}`);
if (fs.existsSync(outputDir)) throw new Error(`formatted dir already exists: ${outputDir}`);
fs.mkdirSync(outputDir, { recursive: true });

const prettier = require(path.join(toolsDir, "node_modules/prettier"));
const prettierVersion = require(path.join(toolsDir, "node_modules/prettier/package.json")).version;
const entries = JSON.parse(fs.readFileSync(sourceFilesPath, "utf8")).files;
const targets = entries.filter((f) => /\.(?:js|mjs|cjs|css|html?)$/i.test(f.path));

const FORMAT_OPTIONS = {
  printWidth: 80,
  tabWidth: 2,
  useTabs: false,
  semi: true,
  singleQuote: false,
  quoteProps: "as-needed",
  trailingComma: "all",
  bracketSpacing: true,
  arrowParens: "always",
  endOfLine: "lf",
};

function parserFor(file) {
  if (/\.(?:js|mjs|cjs)$/i.test(file)) return "babel";
  if (/\.css$/i.test(file)) return "css";
  if (/\.html?$/i.test(file)) return "html";
  throw new Error(`No parser for ${file}`);
}

const started = Date.now();
const results = [];
const failures = [];
let done = 0;
let inputBytes = 0;
let outputBytes = 0;
let lastLog = Date.now();

function writeReport(complete) {
  const report = {
    generatedAt: new Date().toISOString(),
    generatedBy: "scripts/codex-full-restore-format-20261103.mjs",
    complete,
    prettier: prettierVersion,
    options: FORMAT_OPTIONS,
    inputs: { original: originalDir, sourceFiles: sourceFilesPath, tools: toolsDir },
    output: { root: outputDir, files: done, inputBytes, outputBytes },
    durationSeconds: Number(((Date.now() - started) / 1000).toFixed(3)),
    failures,
    files: results,
  };
  fs.writeFileSync(reportPath, JSON.stringify(report, null, 2));
}

for (const entry of targets) {
  const rel = entry.path;
  const abs = path.join(originalDir, rel);
  const dest = path.join(outputDir, rel);
  const fileStart = Date.now();
  try {
    const text = fs.readFileSync(abs, "utf8");
    const formatted = await prettier.format(text, { parser: parserFor(rel), ...FORMAT_OPTIONS });
    fs.mkdirSync(path.dirname(dest), { recursive: true });
    fs.writeFileSync(dest, formatted, "utf8");
    const inBytes = Buffer.byteLength(text);
    const outBytes = Buffer.byteLength(formatted);
    inputBytes += inBytes;
    outputBytes += outBytes;
    results.push({
      path: rel,
      parser: parserFor(rel),
      inputBytes: inBytes,
      outputBytes: outBytes,
      durationMs: Date.now() - fileStart,
      status: "formatted",
    });
  } catch (error) {
    failures.push({ path: rel, error: String(error).slice(0, 1000) });
    results.push({ path: rel, parser: parserFor(rel), status: "failed" });
  }
  done += 1;
  if (done % 250 === 0 || Date.now() - lastLog > 15000) {
    lastLog = Date.now();
    const elapsed = (Date.now() - started) / 1000;
    console.log(JSON.stringify({
      done,
      total: targets.length,
      failures: failures.length,
      inputMB: Number((inputBytes / 1e6).toFixed(1)),
      outputMB: Number((outputBytes / 1e6).toFixed(1)),
      elapsedSeconds: Number(elapsed.toFixed(1)),
      filesPerSecond: Number((done / elapsed).toFixed(2)),
    }));
    writeReport(false);
  }
}

writeReport(true);
const summary = {
  total: targets.length,
  formatted: results.filter((r) => r.status === "formatted").length,
  failed: failures.length,
  inputBytes,
  outputBytes,
  durationSeconds: Number(((Date.now() - started) / 1000).toFixed(3)),
  output: outputDir,
  report: reportPath,
};
console.log(JSON.stringify(summary, null, 2));
process.exitCode = failures.length === 0 ? 0 : 1;
