#!/usr/bin/env node
/**
 * Phase 13 — extract the *literal* visibility expressions from the baseline
 * chunk that decides which settings panels exist, for the route keys this
 * phase could not reach at runtime.
 *
 * The point is to keep the two kinds of evidence apart:
 *
 *   runtime  — "the sidebar has 19 buttons and none of them is `security`"
 *   static   — "the switch case for `security` returns `{visible: !1}`"
 *
 * The second explains the first but is not a substitute for it, so it is
 * quoted verbatim (an expression whose variables are NOT resolved) and
 * labelled `static-gate-hint` in the output. Nothing here is described as
 * observed.
 *
 * Usage: node scripts/codex-full-restore-runtime-gate-hints-20261105.mjs
 */
import fs from "node:fs";
import path from "node:path";

const ROOT = "references/codex-26.1027.61615-24141/full-restore";
const assets = path.join(ROOT, "02-baseline/formatted/webview/assets");
const runtime = path.join(ROOT, "13-runtime");

const visibilityChunk = fs.readdirSync(assets).find((f) => /^use-visible-settings-sections-[0-9a-f]+\.js$/.test(f));
const source = fs.readFileSync(path.join(assets, visibilityChunk), "utf8");

const evidence = JSON.parse(fs.readFileSync(path.join(runtime, "runtime-evidence.json"), "utf8"));
const unreached = evidence.coverage.settings.routeKeysUnreached;

/** The switch in `Ot` — one `case \`key\`:` arm per settings slug. */
const switchStart = source.indexOf("Ot = function (e) {");
const switchEnd = source.indexOf("kt = Qr.map");
if (switchStart < 0 || switchEnd < 0) throw new Error("the visibility switch moved or disappeared");
const switchBody = source.slice(switchStart, switchEnd);

/** The `uo` surface map: route key → the surface that must be active for it. */
const surfaceStart = source.indexOf("uo = {");
const surfaceEnd = source.indexOf("}", surfaceStart);
const surfaceBody = source.slice(surfaceStart, surfaceEnd + 1);
const surface = {};
for (const m of surfaceBody.matchAll(/["']?([A-Za-z0-9-]+)["']?:\s*`([A-Za-z]+)`/g)) surface[m[1]] = m[2];

/** The `co` whitelist: route keys that do not go through the surface map. */
const whitelistStart = source.indexOf("co = [");
const whitelistEnd = source.indexOf("]", whitelistStart);
const whitelist = [...source.slice(whitelistStart, whitelistEnd).matchAll(/`([^`]+)`/g)].map((m) => m[1]);

/**
 * Which surfaces are *proven* active by evidence, not assumed: a surface is
 * active if some panel that rendered at runtime is gated on it. This matters
 * because "the surface is wrong" is a different explanation from "the surface
 * is fine and something else hides it", and only the observation can tell
 * them apart.
 */
const observedSlugs = evidence.coverage.settings.routeKeysReached;
const surfacesProvenActive = new Set(observedSlugs.map((s) => surface[s]).filter(Boolean));

const hints = {};
for (const key of unreached) {
  const at = switchBody.indexOf(`case \`${key}\`:`);
  let arm = null;
  if (at >= 0) {
    // The arm runs from the end of the label to the next `case` label.
    const next = switchBody.indexOf("case `", at + `case \`${key}\`:`.length);
    arm = switchBody
      .slice(at + `case \`${key}\`:`.length, next < 0 ? switchBody.length : next)
      .replace(/\s+/g, " ")
      .trim();
  }
  const hardDisabled = arm != null && /^return \{ visible: !1,/.test(arm);
  const alwaysVisible = arm != null && /^return \{ visible: !0,/.test(arm);
  hints[key] = {
    kind: "static-gate-hint",
    inVisibilitySwitch: arm != null,
    hardDisabled,
    unconditionallyVisibleInSwitch: alwaysVisible,
    caseExpression: arm,
    surfaceKey: surface[key] ?? null,
    inSurfaceWhitelist: whitelist.includes(key),
    surfaceProvenActive: surface[key] ? surfacesProvenActive.has(surface[key]) : null,
    plainWords: arm == null
      ? `no \`case\` arm for ${key} in the visibility switch, so the switch returns undefined`
      : hardDisabled
        ? `hard-disabled: the ${key} arm returns visible: !1 unconditionally`
      : alwaysVisible && whitelist.includes(key)
        ? "the switch makes this section unconditionally visible AND the slug is on the surface whitelist, so by static reading it should always be in the sidebar — yet it is not, which is a real static-vs-runtime discrepancy rather than a gate"
        : surface[key] && !surfacesProvenActive.has(surface[key])
          ? `gated on the \`${surface[key]}\` surface, which no rendered panel proves active here (the panels that did render belong to ${[...surfacesProvenActive].sort().join("/")})`
          : surface[key]
            ? `gated on the \`${surface[key]}\` surface, which IS active here, so the surface flag does not explain its absence and this arm's own condition is the remaining candidate`
            : "no surface gate; the arm's own condition (a flag or server check) is the remaining candidate",
  };
}

const out = {
  generatedAt: new Date().toISOString(),
  generatedBy: "scripts/codex-full-restore-runtime-gate-hints-20261105.mjs",
  source: `02-baseline/formatted/webview/assets/${visibilityChunk}`,
  status: "static",
  scope:
    "Explains why the 36 unreached route keys have no sidebar entry. These are verbatim expressions from the baseline bundle; their free variables are NOT resolved and none of this is a runtime observation.",
  surfaceMap: surface,
  whitelist,
  hints,
};

fs.writeFileSync(path.join(runtime, "gate-hints.json"), JSON.stringify(out, null, 2));
const byKind = {};
for (const h of Object.values(hints)) byKind[h.plainWords.split(":")[0]] = (byKind[h.plainWords.split(":")[0]] ?? 0) + 1;
console.log(JSON.stringify({ keys: unreached.length, hardDisabled: Object.values(hints).filter((h) => h.hardDisabled).length, noCase: Object.values(hints).filter((h) => !h.inVisibilitySwitch).length, withSurface: Object.values(hints).filter((h) => h.surfaceKey).length }, null, 2));
