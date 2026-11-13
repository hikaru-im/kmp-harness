#!/usr/bin/env node
/**
 * Phase 10 (responsive + mobile) UI claim index.
 *
 * Produces 10-semantic/sources/ui-claims.json. Curated claims anchor on exact
 * baseline text for breakpoints, capability detection, the responsive shell,
 * mobile page structure, overlays and the referenced CSS. Message-id bindings
 * are extracted from the bounded slices and verified against the baseline.
 *
 * Usage:
 *   node scripts/codex-full-restore-phase10-claims-20261104.mjs [--out <file>]
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, "..");
const phaseDir = path.join(
  root,
  "references/codex-26.1027.61615-24141/full-restore/10-semantic",
);
const baseline = path.join(
  root,
  "references/codex-26.1027.61615-24141/full-restore/02-baseline/formatted",
);
const outIndex = process.argv.indexOf("--out");
const outPath =
  outIndex >= 0
    ? path.resolve(process.argv[outIndex + 1])
    : path.join(phaseDir, "sources/ui-claims.json");

const claims = [];
let seq = 0;
function add(category, file, anchor, claim, occurrence) {
  seq += 1;
  const entry = {
    id: `p10-${String(seq).padStart(3, "0")}-${category}`,
    category,
    file,
    anchor,
    claim,
    confidence: "high",
  };
  if (occurrence != null) entry.occurrence = occurrence;
  claims.push(entry);
}

const AI = "webview/assets/app-initial-18b189e867e7.js";
const AS = "webview/assets/app-shared-601019a330d7.js";
const AP = "webview/assets/app-primary-75d53eef2374.js";
const AR = "webview/assets/app-runtime-b179428fcc1e.js";
const EN = "webview/assets/entry-1f3cf7b4835d.js";
const ED = "webview/assets/editor-76f8ec768ed8.js";
const BCD = "webview/assets/browser-composer-disclaimer-8c236022152a.js";
const SCL = "webview/assets/settings-content-layout-ac451a456013.js";
const SPL = "webview/assets/searchable-page-layout-6693d2a8dff5.js";
const PIP = "webview/assets/projects-index-page-78237015641b.js";
const SFE = "webview/assets/settings-form-d03684fa1af2.js";
const SPC = "webview/assets/settings-plugin-controls-cbca110a69d1.js";
const PS = "webview/assets/plugins-settings-7a89487c0c4c.js";
const CC = "webview/assets/composer-content-b926d24ef280.js";
const TASC = "webview/assets/thread-app-shell-chrome-e7e38c7ea254.js";
const SP = "webview/assets/side-panel-b2f1ecc3aff1.js";
const SAT = "webview/assets/settings-analytics-telemetry-f26bc8be0b57.js";
const AROW = "webview/assets/automation-row-37c157e22263.js";
const DC = "webview/assets/dialog-c06baa494c77.js";
const POP = "webview/assets/popover-12c80c240995.js";

// ---------------------------------------------------------------- breakpoints
add("breakpoint", AI, "window.matchMedia(`(width < 640px), (pointer: coarse)`)", "The small-viewport store subscribes to `(width < 640px), (pointer: coarse)`, so a coarse pointer counts as a small viewport at any width.");
add("breakpoint", AI, "(LRr = K(Q, (e, { get: t }) => !1))", "The mobile-layout feature flag store is a constant `false`; the mobile layout branch is never enabled in this build.");
add("breakpoint", AI, "(BRr = K(Q, (e, { get: t }) => t(LRr, e) && !t(zRr)))", "The derived mobile-layout store is `mobileLayoutEnabled && !smallViewport`, and therefore always `false` while the flag is constant false.");
add("breakpoint", AS, "window.matchMedia(`(prefers-reduced-motion: reduce)`).matches", "The reduced-motion helper reads `(prefers-reduced-motion: reduce)` from `matchMedia`.");
add("breakpoint", AS, "(vin = `(prefers-reduced-motion: reduce)`)", "The reduced-motion preference store subscribes to the same `(prefers-reduced-motion: reduce)` media query.");
add("breakpoint", AS, "function din(e) {", "`din` is the hydration-safe media-query hook; it consults the client flag and the media list before returning `matches`.");
add("breakpoint", ED, "return window.matchMedia(`(max-width: 639px)`).matches;", "The image-annotation editor treats viewports at or below 639px as narrow.");
add("breakpoint", ED, "let e = window.setTimeout(() => ne(!1), 600);", "The annotation editor auto-hides its toolbar after a 600 ms timer.");
add("breakpoint", BCD, "r = g(`(width < 640px)`),", "The composer disclaimer selects its compact presentation with `(width < 640px)`.");

// ---------------------------------------------------------------- capability
add("capability", AS, "/Android/.test(iOn()) ||", "`isTouchDevice` treats Android user agents as touch devices.");
add("capability", AS, "window.matchMedia?.(`(pointer: coarse)`).matches === !0;", "`isTouchDevice` also returns true when `(pointer: coarse)` matches, so touch capability is not derived from width alone.");
add("capability", EN, "(document.documentElement.dataset.codexWindowType = `electron`)", "The Electron entry chunk marks the document as `data-codex-window-type=\\\"electron\\\"`.");
add("capability", AR, "(document.documentElement.dataset.codexWindowType = `electron`)", "The app runtime repeats the `data-codex-window-type=\\\"electron\\\"` assignment on `documentElement`.");

// ---------------------------------------------------------------- shell
add("shell-responsive", AP, "\"data-app-shell-feature-navigation\": B,", "The app-shell frame exposes `data-app-shell-feature-navigation` for CSS that depends on the feature-navigation layout.");
add("shell-responsive", AP, "\"data-app-shell-responsive-sidebar\": !1,", "The Electron app-shell frame hardcodes `data-app-shell-responsive-sidebar` to `false`, so the responsive sidebar rules cannot activate in this build.");
add("shell-responsive", AP, "\"data-app-shell-sidebar-open\": fe,", "The app-shell frame publishes the live sidebar-open state as `data-app-shell-sidebar-open`.");
add("shell-responsive", AP, "\"data-app-shell-frame\": !0,", "The app-shell frame marks its root with `data-app-shell-frame`.");
add("shell-responsive", AP, "\"data-app-shell-compact-page-gutter\": wt,", "The frame publishes `data-app-shell-compact-page-gutter` for compact page gutters.");
add("shell-responsive", AP, "\"data-app-shell-page-surface\": H,", "The frame publishes `data-app-shell-page-surface` on the page surface.");
add("shell-responsive", AP, "\"--app-shell-navigation-rail-width\": mt,", "The frame exposes `--app-shell-navigation-rail-width` as a root CSS variable.");
add("shell-responsive", AP, "c === void 0 ? `app-shell` : c) === `browser-popover`", "Sidebar chrome has two variants, `app-shell` and `browser-popover`; the browser-popover variant is selected only when the state says so.");
add("shell-responsive", AP, "description: `Closes the mobile sidebar drawer`,", "The Electron sidebar message catalog documents the trigger as 'Closes the mobile sidebar drawer'.");
add("shell-responsive", AP, "(QAt = `_ResponsiveLeftPanel_azz1c_2`)", "The layout style map defines the `_ResponsiveLeftPanel_azz1c_2` class token.");
add("shell-responsive", AP, "ResponsiveLeftPanel: QAt,", "The layout style map exports the token as `ResponsiveLeftPanel`.");
add("shell-responsive", AP, "ve = l ? window.innerWidth : 1440,", "The app-shell frame uses the measured `window.innerWidth` and falls back to 1440 when no width is available.");
add("shell-responsive", AP, "`@container/app-shell-detail-panel flex h-full min-h-0 flex-col`", "The detail panel establishes an `app-shell-detail-panel` inline-size container.");
add("shell-responsive", AP, "[@container_app-shell-detail-panel_(max-width:899px)]:grid-cols-[auto_minmax(0,1fr)_auto]", "Below 899px of detail-panel width the toolbar switches to an `auto / 1fr / auto` grid.");
add("shell-responsive", AP, "browser:min-[768px]:[[data-app-shell-sidebar-open=false]_&]:ps-[calc(var(--app-shell-navigation-rail-width)+var(--spacing)*2)]", "A browser-only `min-width:768px` variant adds rail padding when the sidebar is closed; the `browser:` variant requires the browser window type.");

// ---------------------------------------------------------------- mobile pages
add("mobile-pages", SCL, "let M = j && !1,", "`SettingsContent` computes its mobile-header flag as `j && !1`, so the settings mobile header is hardcoded off in this build.");
add("mobile-pages", SCL, "\"data-settings-mobile-header\": Ee,", "The settings scroll container still emits `data-settings-mobile-header` (always false here).");
add("mobile-pages", SCL, "de = ce && !O && !1;", "Touch-overscroll containment is computed as `ce && !O && !1`, so the settings page never installs it in this build.");
add("mobile-pages", SCL, "\"data-contain-touch-overscroll\": we,", "SettingsContent marks its scroll container with `data-contain-touch-overscroll` when containment is active.");
add("mobile-pages", SCL, "n.dataset.containTouchOverscroll = String(", "`useContainTouchOverscroll` increments a `data-contain-touch-overscroll` counter on `documentElement`.", 0);
add("mobile-pages", SCL, "t.closest(`[data-app-shell-main-surface]`)", "The touch-overscroll hook scopes itself to the closest `[data-app-shell-main-surface]`.");
add("mobile-pages", SPL, "X = R && !G && !U && `browser:max-md:text-lg browser:max-md:font-medium`,", "The searchable page title becomes `text-lg font-medium` at `max-md` in browser windows.");
add("mobile-pages", SPL, "R && `browser:max-md:min-h-0`,", "A `browser:max-md:min-h-0` variant removes the minimum header height in compact browser layouts.");
add("mobile-pages", SPL, "R && !U && `browser:max-md:hidden`,", "The page-header title is hidden at `max-md` in the relevant browser layout mode.");
add("mobile-pages", SPL, "`mx-auto w-full text-base leading-6 text-secondary browser:max-md:hidden`", "The secondary searchable-page label is hidden at `max-md` in browser windows.");
add("mobile-pages", SPL, "className: k ? `@container/searchable-header` : `contents`,", "The searchable page header row becomes a named `searchable-header` container when the header can collapse.");
add("mobile-pages", PIP, "className: s ? `browser:max-md:sr-only` : void 0,", "The projects-index create action renders its label visually hidden (`sr-only`) at `max-md` in browser windows.");
add("mobile-pages", SFE, "max-md:flex-col max-md:items-stretch", "The credits balance section stacks vertically at `max-md` when it has child content.", 0);
add("mobile-pages", SFE, "max-md:flex-col max-md:items-stretch", "The auto top-up section stacks vertically at `max-md` when it is enabled.", 1);
add("mobile-pages", SPC, "max-sm:flex-wrap", "The plugin settings row wraps at `max-sm`.");
add("mobile-pages", PS, "max-sm:w-full max-sm:flex-wrap max-sm:justify-end", "The MCP server row's actions become full-width, wrapped and end-aligned at `max-sm`.");
add("mobile-pages", PS, "max-sm:flex-col max-sm:items-stretch", "The MCP server row stacks its controls at `max-sm`.");
add("mobile-pages", CC, "`hide-scrollbar w-full overflow-x-auto py-1`", "The composer attachment row is a horizontally scrollable strip with a hidden scrollbar.");
add("mobile-pages", TASC, "`@container/app-shell-tab relative flex w-full shrink-0 items-center`", "Each app-shell tab is an `app-shell-tab` inline-size container, so tab content can react to the tab's own width.");
add("mobile-pages", SP, "pointer-coarse:pe-16", "The summary panel item reserves trailing padding for coarse pointers.");
add("mobile-pages", SP, "`pointer-coarse:pointer-events-auto pointer-coarse:opacity-100`", "The connected-computer row reveals its trailing action for coarse pointers.");
add("mobile-pages", SAT, "pointer-coarse:h-auto pointer-coarse:min-h-25 pointer-coarse:overflow-visible", "The analytics legend is non-scrolling and expanded for coarse pointers.");
add("mobile-pages", AROW, "className: `@container`,", "The automation row establishes a container for its own width-based layout.");

// ---------------------------------------------------------------- overlays
add("overlay", AS, "function c6(e) {", "`isResponsiveSheetSize` recognizes the responsive-sheet dialog sizes.");
add("overlay", AS, "e === `responsiveSheet` ||", "`responsiveSheet` is one of the responsive-sheet dialog sizes.");
add("overlay", AS, "(wSn = `_responsiveSheet_1u8c7_2`)", "The dialog style map binds the responsive sheet to `_responsiveSheet_1u8c7_2`.");
add("overlay", AS, "T === `responsiveSheet` &&", "Dialog content applies responsive-sheet behavior when its size is `responsiveSheet`.");
add("overlay", DC, "size: `responsiveSheet`,", "The workspace-agent schedules dialog uses `size: 'responsiveSheet'`.");
add("overlay", BCD, "size: `responsiveSheet`", "The composer disclaimer also opens a `responsiveSheet` dialog below its breakpoint.");
add("overlay", POP, "sideOffset: s = 8,", "Popover content defaults to an 8px side offset.");
add("overlay", POP, "alignOffset: l ?? (c === `center` ? 0 : -5),", "Popover alignment uses 0 for center alignment and -5 for the other alignments unless an explicit offset is given.");
add("overlay", POP, "hideWhenDetached: !0,", "Popover content hides when its anchor is detached.");
add("overlay", POP, "collisionPadding: 20,", "Popover content keeps 20px collision padding from the viewport edge.");

// ---------------------------------------------------------------- CSS
add("css", "webview/assets/fd9b7fd80db1.css", "@container app-shell-detail-panel (width<=899px) {", "The detail-panel container query breakpoint is 899px.");
add("css", "webview/assets/fd9b7fd80db1.css", "._responsiveSheet_1u8c7_2 {", "The responsive sheet is bottom-anchored, full width, capped at 28rem and rounded at the top corners.", 0);
add("css", "webview/assets/fd9b7fd80db1.css", "._responsiveSheet_1u8c7_2 {", "At `width>=40rem` the responsive sheet moves to a vertically centered position with all corners rounded.", 1);
add("css", "webview/assets/fd9b7fd80db1.css", "padding-bottom: env(safe-area-inset-bottom, 0px);", "The responsive sheet adds `safe-area-inset-bottom` padding.", 0);
add("css", "webview/assets/fd9b7fd80db1.css", "._edgeToEdgeSheet_1u8c7_2 {", "The edge-to-edge sheet is a separate dialog surface from the responsive sheet.");
add("css", "webview/assets/fd9b7fd80db1.css", "container: left-panel/inline-size;", "`@container/left-panel` maps to an inline-size container named `left-panel`.");
add("css", "webview/assets/fd9b7fd80db1.css", "container: app-shell-tab/inline-size;", "`@container/app-shell-tab` maps to an inline-size container named `app-shell-tab`.");
add("css", "webview/assets/fd9b7fd80db1.css", "container: app-shell-main-content/inline-size;", "`@container app-shell-main-content` maps to an inline-size container named `app-shell-main-content`.");
add("css", "webview/assets/fd9b7fd80db1.css", "container: searchable-header/inline-size;", "`@container/searchable-header` maps to an inline-size container named `searchable-header`.");
add("css", "webview/assets/fd9b7fd80db1.css", ".browser\\:max-md\\:hidden:where(", "`browser:max-md:hidden` compiles to a selector gated on `[data-codex-window-type=\\\"browser\\\"]`.");
add("css", "webview/assets/fd9b7fd80db1.css", ".pointer-coarse\\:pointer-events-auto {", "`pointer-coarse:pointer-events-auto` is generated under `@media (pointer: coarse)`.");
add("css", "webview/assets/fd9b7fd80db1.css", ".pointer-coarse\\:pe-16 {", "`pointer-coarse:pe-16` exists as a coarse-pointer-only padding utility.");
add("css", "webview/assets/fd9b7fd80db1.css", ".pointer-coarse\\:size-10 {", "`pointer-coarse:size-10` enlarges touch targets to 2.5rem under a coarse pointer.");
add("css", "webview/assets/fd9b7fd80db1.css", "@media (any-pointer: coarse) {", "A separate `any-pointer: coarse` block covers devices with any coarse pointer.", 0);
add("css", "webview/assets/59ad52d5aa88.css", "._Layout_azz1c_2[data-app-shell-responsive-sidebar=\"true\"] {", "When the responsive sidebar is enabled, the layout collapses `--app-shell-left-panel-width` to 0px below 768px.");
add("css", "webview/assets/59ad52d5aa88.css", "._ResponsiveLeftPanel_azz1c_2 {", "The responsive left panel is `display: none` under the same below-768px rule.");
add("css", "webview/assets/59ad52d5aa88.css", "--app-shell-left-panel-width: 0px;", "The below-768px responsive layout zeroes the left-panel width variable.");
add("css", "webview/assets/59ad52d5aa88.css", "._TabStripFlushStart_mr1y2_2 {", "At `width>=768px` the flush-start tab strip resets its inline-start padding to 0.");
add("css", "webview/assets/b74e19ff7ff4.css", "@container composer-footer (width<=440px) {", "The composer footer has a `composer-footer` container query at 440px.");
add("css", "webview/assets/b74e19ff7ff4.css", "@media (hover: none), (pointer: coarse) {", "Composer CSS groups `(hover: none)` and `(pointer: coarse)` into one touch-capability block.");
add("css", "webview/assets/126a18e4438e.css", "._shell_1vs63_2 {", "The searchable page shell has its own `_shell_1vs63_2` layout class.");
add("css", "webview/assets/c7abafaae9d4.css", "._reveal_1xm50_1 {", "The sidebar reveal element animates `grid-template-columns` from `0fr` to `1fr`.", 0);

// ---------------------------------------------------------------- message ids
const sliceSpecs = [
  ["sidebar-navigation-header", "copy", "the navigation header", 3],
  ["schedule-dialog", "copy", "the workspace-agent schedules dialog", 4],
  ["settings-plugin-controls", "copy", "the plugin settings row", 2],
  ["settings-form", "copy", "the credits settings section", 3],
  ["settings-analytics", "copy", "the analytics card", 2],
  ["searchable-page-layout", "copy", "the searchable page layout", 2],
  ["side-panel-pointer-coarse", "copy", "the side-panel pointer-coarse row", 2],
  ["dialog-content", "copy", "the dialog content", 3],
  ["plugin-mcp-row", "copy", "the MCP server row", 2],
  ["projects-index", "copy", "the projects-index create action", 1],
  ["mobile-layout-store", "copy", "the mobile layout store", 1],
  ["media-query-hooks", "copy", "the media-query hooks", 1],
];

const priority = [
  "sidebar", "mobile", "drawer", "close", "open", "schedule", "cancel", "save",
  "delete", "remove", "time", "day", "week", "hour", "title", "description",
  "label", "button", "action", "settings", "empty", "error", "warning",
];
function score(id) {
  const lower = id.toLowerCase();
  let total = 0;
  for (let i = 0; i < priority.length; i += 1) {
    if (lower.includes(priority[i])) total += priority.length - i;
  }
  return total;
}
function humanTail(id) {
  const tail = id.split(".").slice(-2).join(" ");
  return tail
    .replace(/([a-z0-9])([A-Z])/g, "$1 $2")
    .replace(/[_-]+/g, " ")
    .toLowerCase();
}
function sliceSource(slice) {
  const manifest = JSON.parse(
    fs.readFileSync(path.join(phaseDir, "reports", `${slice}-slice.json`), "utf8"),
  );
  return path.basename(manifest.source.path);
}
const claimedAnchors = new Set(claims.map((claim) => `${claim.file}\0${claim.anchor}`));
for (const [slice, category, subject, cap] of sliceSpecs) {
  const text = fs.readFileSync(
    path.join(phaseDir, "formatted/webview/assets", `${slice}-slice.js`),
    "utf8",
  );
  const ids = [...new Set([...text.matchAll(/id: `([^`]+)`/g)].map((m) => m[1]))];
  const selected = ids
    .map((id, index) => ({ id, index, value: score(id) }))
    .sort((a, b) => b.value - a.value || a.index - b.index)
    .slice(0, cap)
    .sort((a, b) => a.index - b.index);
  for (const { id } of selected) {
    const file = `webview/assets/${sliceSource(slice)}`;
    const anchor = `id: \`${id}\``;
    if (claimedAnchors.has(`${file}\0${anchor}`)) continue;
    claimedAnchors.add(`${file}\0${anchor}`);
    const occurrence = fs
      .readFileSync(path.join(baseline, file), "utf8")
      .split(anchor).length - 1;
    add(
      category,
      file,
      anchor,
      `The ${subject} binds \`${id}\` for its ${humanTail(id)} copy.`,
      occurrence > 1 ? 0 : undefined,
    );
  }
}

// Verify every anchor against the baseline before writing.
const failures = [];
for (const claim of claims) {
  const text = fs.readFileSync(path.join(baseline, claim.file), "utf8");
  const count = text.split(claim.anchor).length - 1;
  if (count === 0) failures.push(`${claim.id}: anchor not found: ${claim.anchor}`);
  else if (count > 1 && claim.occurrence == null) {
    failures.push(`${claim.id}: anchor is not unique (${count}): ${claim.anchor}`);
  } else if (claim.occurrence != null && claim.occurrence >= count) {
    failures.push(`${claim.id}: occurrence ${claim.occurrence} >= ${count}: ${claim.anchor}`);
  }
}
if (failures.length > 0) {
  console.error(failures.join("\n"));
  process.exit(1);
}

fs.mkdirSync(path.dirname(outPath), { recursive: true });
fs.writeFileSync(outPath, `${JSON.stringify(claims, null, 2)}\n`);
const byCategory = {};
for (const claim of claims) byCategory[claim.category] = (byCategory[claim.category] ?? 0) + 1;
console.log(JSON.stringify({ outPath, claims: claims.length, byCategory }, null, 2));
