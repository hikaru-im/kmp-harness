#!/usr/bin/env node
/**
 * Phase 14 determinism harness.
 *
 * Runs the call-graph extractor twice into two scratch directories and writes
 * `14-callgraph/determinism-runs.json` recording both payload hashes. The
 * extractor's own `verification.json` then reads that file instead of claiming
 * reproducibility for itself.
 *
 * Read-only over the baseline; writes only the comparison file and scratch
 * directories under the system temp dir.
 */
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { execFileSync } from "node:child_process";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, "..");
const script = path.join(here, "codex-full-restore-callgraph-20261106.mjs");
const outDir = path.join(root, "references/codex-26.1027.61615-24141/full-restore/14-callgraph");

const scratch = fs.mkdtempSync(path.join(os.tmpdir(), "cg-determinism-"));
const runs = [];
for (const n of [1, 2]) {
  const dir = path.join(scratch, `run${n}`);
  process.stderr.write(`run ${n} -> ${dir}\n`);
  execFileSync(process.execPath, ["--max-old-space-size=8192", script, "--out", dir], {
    stdio: "inherit",
    cwd: root,
  });
  const v = JSON.parse(fs.readFileSync(path.join(dir, "verification.json"), "utf8"));
  runs.push({
    run: n,
    nodeHash: v.determinism.nodeHash,
    edgeHash: v.determinism.edgeHash,
    payloadHash: v.determinism.payloadHash,
    nodes: v.totals.nodes,
    edges: v.totals.edges,
    reverseCheckSampled: v.reverseCheck.sampled,
    reverseCheckFailures: v.reverseCheck.failures,
  });
}

const identical = new Set(runs.map((r) => r.payloadHash)).size === 1;
fs.mkdirSync(outDir, { recursive: true });
fs.writeFileSync(
  path.join(outDir, "determinism-runs.json"),
  JSON.stringify(
    {
      generatedAt: new Date().toISOString(),
      generatedBy: "scripts/codex-full-restore-callgraph-determinism-20261106.mjs",
      phase: 14,
      method:
        "The extractor is invoked twice into separate scratch directories; each run's node/edge/payload hashes are read from its own verification.json.",
      identical,
      runs,
    },
    null,
    1,
  ) + "\n",
);
fs.rmSync(scratch, { recursive: true, force: true });
process.stderr.write(`identical=${identical}\n`);
process.exit(identical ? 0 : 1);
