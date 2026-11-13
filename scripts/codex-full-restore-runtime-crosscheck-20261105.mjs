#!/usr/bin/env node
/**
 * Phase 13 — cross-check the Phase 12 static message-id claims against the
 * text the running application actually rendered.
 *
 * For every Phase 12 claim that names a message id, resolve the id to its
 * zh-CN string, and ask: does that string appear in the observed text of a
 * settings page we reached at runtime?
 *
 * This is deliberately conservative. A string that does not appear proves
 * nothing on its own (the page may be gated, may not be scrolled into view,
 * or its copy may come from a different bundle), so every miss is reported as
 * `not-observed` and only counts as a finding when the page itself rendered.
 *
 * Usage: node scripts/codex-full-restore-runtime-crosscheck-20261105.mjs
 */
import fs from "node:fs";
import path from "node:path";
import process from "node:process";

const ROOT = process.argv[2] ?? "references/codex-26.1027.61615-24141/full-restore";
const runtime = path.join(ROOT, "13-runtime");
const assets = path.join(ROOT, "02-baseline/formatted/webview/assets");

/**
 * The panel's own `data-settings-panel-slug` is the live route key, so the
 * sweep records it directly and no slug→route table is needed. An earlier
 * draft guessed this mapping and wrongly equated the `pets` panel with the
 * `codex-micro` route key — that is exactly the kind of static inference this
 * phase is supposed to test rather than repeat.
 */

const localeFile = fs.readdirSync(assets).find((f) => /^zh-CN-[0-9a-f]{12}\.js$/.test(f));
if (!localeFile) throw new Error("zh-CN locale chunk not found in the baseline");
const localeText = fs.readFileSync(path.join(assets, localeFile), "utf8");
const messages = new Map();
for (const m of localeText.matchAll(/"(settings\.[A-Za-z0-9_.\-]+)":\s*`([^`]*)`/g)) {
  if (!messages.has(m[1])) messages.set(m[1], m[2]);
}

const evidence = JSON.parse(fs.readFileSync(path.join(ROOT, "12-semantic/ui-evidence.json"), "utf8"));

/**
 * ICU-aware containment. A locale string like
 * `将其他 AI 应用的设置、项目和聊天导入 {appName}` renders with the
 * placeholder substituted, so a plain substring test always misses. Split on
 * the placeholder and require every literal run (length > 1) to appear in
 * order; rich-text tags (<a>, <b>) are treated the same way.
 */
function literalRuns(text) {
  return text
    .split(/\{[^}]*\}|<[^>]+>/)
    .map((s) => s.trim())
    .filter((s) => s.length > 1);
}
function containsMessage(haystack, message) {
  const runs = literalRuns(message);
  if (!runs.length) return haystack.includes(message);
  let from = 0;
  for (const run of runs) {
    const at = haystack.indexOf(run, from);
    if (at === -1) return false;
    from = at + run.length;
  }
  return true;
}

/**
 * Claims that describe a non-default state (loading / error / retry / empty /
 * disabled). Those strings only render under a condition the read-only pass
 * never created, so a miss is expected and must not be counted as a defect.
 */
const STATE_CLAIM = /(loading|error|retry|empty|failed|disabled|pending|spinner|skeleton|offline)/i;
const sweep = JSON.parse(fs.readFileSync(path.join(runtime, "sweep-raw.json"), "utf8"));
const routes = JSON.parse(fs.readFileSync("/tmp/p12-routes-final.json", "utf8"));

const chunkToRoute = new Map();
for (const r of routes) {
  if (!r.chunk) continue;
  if (!chunkToRoute.has(r.chunk)) chunkToRoute.set(r.chunk, []);
  chunkToRoute.get(r.chunk).push(r.route);
}

/**
 * Everything a user can perceive on a page, not just `innerText`.
 *
 * A control's accessible name is `aria-label` / `title` and does NOT appear in
 * `innerText`, so a text-only haystack reports icon-only buttons as missing
 * copy — `settings.hooks.refresh` ("重新加载钩子") is exactly that case: it
 * renders as the refresh button's aria-label and nowhere in the page text. The
 * haystack therefore unions the page text with every control's label/text,
 * every heading, the visible nav labels and the page title.
 */
function haystack(observed) {
  const parts = [observed.textFull ?? "", observed.title ?? ""];
  for (const h of observed.headings ?? []) parts.push(h.text ?? "");
  for (const c of observed.controls ?? []) {
    parts.push(c.label ?? "", c.text ?? "", c.placeholder ?? "", c.value ?? "");
  }
  for (const n of observed.visibleNav ?? []) parts.push(n.label ?? "");
  for (const a of observed.dataAttrsTree ?? []) parts.push(a.value ?? "");
  return parts.filter(Boolean);
}

/** Observed pages, keyed by the Phase 12 route key they correspond to. */
const observedByRoute = new Map();
for (const s of sweep) {
  const route = s.observed?.activeSlug;
  if (!route || s.status !== "observed") continue;
  observedByRoute.set(route, {
    slug: s.slug,
    parts: haystack(s.observed),
    headings: s.observed.headings.map((h) => h.text),
  });
}

const results = [];
for (const claim of evidence.claims) {
  const ids = [...new Set([...String(claim.claim).matchAll(/`([a-zA-Z][A-Za-z0-9_.\-]+)`/g)].map((m) => m[1]))].filter((id) =>
    id.includes(".settings.") || id.startsWith("settings.") || /^[a-z][a-zA-Z]*\.[a-zA-Z]/.test(id),
  );
  if (!ids.length) continue;
  const chunk = path.basename(claim.file);
  const routeKeys = chunkToRoute.get(chunk) ?? [];
  const reachable = routeKeys.map((r) => observedByRoute.get(r)).filter(Boolean);
  const ids_ = ids.map((id) => ({ id, text: messages.get(id) ?? null }));
  const usable = ids_.filter((e) => e.text && e.text.length > 1);
  if (!usable.length) {
    results.push({
      claimId: claim.id,
      chunk,
      routeKeys,
      status: "unresolvable",
      reason: "no cited message id has a zh-CN string in the baseline locale chunk",
      ids: ids_,
    });
    continue;
  }
  if (!reachable.length) {
    results.push({
      claimId: claim.id,
      chunk,
      routeKeys,
      status: "route-not-observed",
      reason: routeKeys.length ? "route key was not reached at runtime" : "chunk is not a settings route chunk",
      ids: usable,
    });
    continue;
  }
  // Match against each perceived string separately, so a placeholder-substituted
  // label cannot be "found" by stitching two unrelated fields together.
  const present = (e) => reachable.some((o) => o.parts.some((p) => containsMessage(p, e.text)));
  const matched = usable.filter(present);
  const stateful = STATE_CLAIM.test(claim.category) || STATE_CLAIM.test(claim.claim);
  results.push({
    claimId: claim.id,
    category: claim.category,
    chunk,
    routeKeys,
    observedSlugs: reachable.map((o) => o.slug),
    status:
      matched.length === usable.length
        ? "matched"
        : matched.length
          ? "partial"
          : stateful
            ? "state-dependent"
            : "not-observed",
    ids: usable.map((e) => ({ ...e, present: present(e) })),
  });
}

const tally = {};
for (const r of results) tally[r.status] = (tally[r.status] ?? 0) + 1;

const out = {
  generatedAt: new Date().toISOString(),
  generatedBy: "scripts/codex-full-restore-runtime-crosscheck-20261105.mjs",
  phase: 13,
  scope:
    "Phase 12 message-id claims checked against text rendered by the running app; only settings pages actually reached in 13-runtime are in scope",
  localeChunk: localeFile,
  localeMessageCount: messages.size,
  observedPages: [...observedByRoute.entries()].map(([route, o]) => ({
    route,
    slug: o.slug,
    perceivedStrings: o.parts.length,
    perceivedChars: o.parts.join("").length,
  })),
  tally,
  results,
  failures: results.filter((r) => r.status === "not-observed").length,
};
fs.writeFileSync(path.join(runtime, "crosscheck.json"), JSON.stringify(out, null, 2));
console.log(JSON.stringify({ tally, claimsChecked: results.length, failures: out.failures }, null, 2));
