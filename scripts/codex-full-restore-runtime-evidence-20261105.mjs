#!/usr/bin/env node
/**
 * Phase 13 — assemble runtime-evidence.json from the raw capture files.
 *
 * Every claim in the output carries an explicit status:
 *   observed  — the running app was actually read and produced this
 *   blocked   — the surface exists in the build but could not be reached, with
 *               the observed reason (no sidebar entry) kept separate from the
 *               static gate hint, which is labelled as such
 *   not-run   — never attempted in this phase
 *
 * Nothing here upgrades a static inference into a runtime fact.
 *
 * Usage: node scripts/codex-full-restore-runtime-evidence-20261105.mjs [root]
 */
import { execFileSync } from "node:child_process";
import fs from "node:fs";
import path from "node:path";
import process from "node:process";

const ROOT = process.argv[2] ?? "references/codex-26.1027.61615-24141/full-restore";
const runtime = path.join(ROOT, "13-runtime");
const read = (name) => JSON.parse(fs.readFileSync(path.join(runtime, name), "utf8"));

const sweep = read("sweep-raw.json");
const crosscheck = read("crosscheck.json");
const sheets = read("route-stylesheets.json");
const chunks = read("runtime-chunks.json");
const routes = JSON.parse(fs.readFileSync("/tmp/p12-routes-final.json", "utf8"));

/**
 * Static gate hints, extracted verbatim from the baseline by
 * `codex-full-restore-runtime-gate-hints-20261105.mjs`. They explain *why* a
 * panel is absent; they are not runtime observations and are labelled
 * `kind: "static-gate-hint"` in the output. Written by hand here would be a
 * transcription of a bundle I can read directly, so they are read from disk.
 */
const gateHints = JSON.parse(fs.readFileSync(path.join(runtime, "gate-hints.json"), "utf8"));

const settingsPanels = sweep.filter((s) => s.observed?.activeSlug);
const shellSurfaces = sweep.filter((s) => s.slug.startsWith("shell-"));
const observedRoutes = new Set(settingsPanels.map((s) => s.observed.activeSlug));
const unreached = routes.filter((r) => !observedRoutes.has(r.route)).map((r) => r.route);

const loadedSheets = new Set(sheets.panels[0]?.hrefs ?? []);
const copiedSheets = fs
  .readdirSync(path.join(ROOT, "12-semantic/formatted/webview/assets"))
  .filter((f) => f.endsWith(".css"));
const appliedCopiedSheets = copiedSheets.filter((f) => loadedSheets.has(f));
const unappliedCopiedSheets = copiedSheets.filter((f) => !loadedSheets.has(f));

/**
 * For each stylesheet the static route table says a route preloads but the
 * app never applied, name the module classes and the chunk that owns them, and
 * say whether that chunk was parsed at runtime.
 */
const parsedChunks = new Set(chunks.chunks);
const assetsDir = path.join(ROOT, "02-baseline/formatted/webview/assets");
const baselineFiles = fs.readdirSync(assetsDir);
function owningChunk(cssFile) {
  const text = fs.readFileSync(path.join(assetsDir, cssFile), "utf8");
  const first = /\.(_[A-Za-z0-9]+_[a-z0-9]+_[0-9]+)/.exec(text)?.[1];
  if (!first) return null;
  const hit = baselineFiles.find(
    (f) => f.endsWith(".js") && fs.readFileSync(path.join(assetsDir, f), "utf8").includes(first),
  );
  return hit ? { moduleClass: first, chunk: hit, chunkParsedAtRuntime: parsedChunks.has(hit) } : { moduleClass: first, chunk: null };
}

const appVersion = execFileSync("defaults", ["read", "/Applications/ChatGPT.app/Contents/Info.plist", "CFBundleShortVersionString"])
  .toString()
  .trim();
const appBuild = execFileSync("defaults", ["read", "/Applications/ChatGPT.app/Contents/Info.plist", "CFBundleVersion"]).toString().trim();
const asarHash = execFileSync("shasum", ["-a", "256", "/Applications/ChatGPT.app/Contents/Resources/app.asar"])
  .toString()
  .trim()
  .split(/\s+/)[0];

const consoleTotal = sweep.reduce((a, s) => a + (s.console?.count ?? 0), 0);
const consoleErrors = sweep.reduce((a, s) => a + (s.console?.errors ?? 0), 0);

const evidence = {
  generatedAt: new Date().toISOString(),
  generatedBy: "scripts/codex-full-restore-runtime-evidence-20261105.mjs",
  phase: 13,
  scope:
    "Read-only navigation and evidence capture of one isolated ChatGPT/Codex instance. No form filling, no save/delete/pause/run/send, no settings change, no account switch, no plugin install, no network write.",
  environment: {
    bundle: "/Applications/ChatGPT.app",
    version: appVersion,
    build: appBuild,
    asarSha256: asarHash,
    bundledCliVersion: fs.existsSync("/Applications/ChatGPT.app/Contents/Resources/codex-cli/codex-package.json")
      ? JSON.parse(fs.readFileSync("/Applications/ChatGPT.app/Contents/Resources/codex-cli/codex-package.json", "utf8")).version
      : null,
    note: "The asar hash matches the 02-baseline ASAR hash, so the running binary is the same build the static phases sliced.",
  },
  attach: {
    tier1_runningInstanceCdp: {
      status: "blocked",
      detail:
        "The pre-existing instance (PID 75212) listens on no TCP port and was never touched: `lsof -nP -iTCP -sTCP:LISTEN -p 75212` returned nothing and ports 9222/9229 were closed.",
    },
    tier2_isolatedInstance: {
      status: "observed",
      launchCommand:
        "open -n -a /Applications/ChatGPT.app --args --remote-debugging-port=9333 --user-data-dir=/tmp/codex-runtime-p13/profile",
      transport: "DevTools protocol over Node's built-in WebSocket (no Playwright/Puppeteer installed)",
      cdpVersion: "Chrome/156.0.8078.79",
      windows: [
        { title: "ChatGPT", url: "app://-/index.html", size: "2560x1320", role: "main application window" },
        { title: "ChatGPT", url: "app://-/index.html?initialRoute=%2Fspotlight", size: "760x640", role: "command-menu / spotlight window" },
        { type: "webview", title: "sandbox", role: "inline visualization sandbox for a rendered artifact" },
      ],
      note:
        "Both app windows are `type: page`; the spotlight window is chosen only by its `initialRoute=%2Fspotlight` query. An early pass read the spotlight window by mistake and had to be discarded.",
      sessionState:
        "Although the user-data-dir was new, the instance came up ALREADY AUTHENTICATED (a profile menu with an account display name, a projects list and a populated MCP server list). No login was performed and no credential was entered; the session was inherited from the machine's credential store. Consequence for the report: the missing chatGpt-surface panels cannot be attributed to being logged out.",
    },
    tier3_applescript: { status: "not-run", detail: "not needed once CDP attached" },
  },
  privacy: {
    redaction: "Email addresses, sk-/pk-/rk- keys, JWTs and long hex strings are replaced before anything is written to disk.",
    skipped:
      "The profile/account menu (account name, avatar) was captured once and the screenshots were deleted rather than redacted, because masking an avatar is not reliable; no account, billing, profile, password or team page was opened.",
    neverEntered: "No credential, API key or form field was ever typed into.",
    retainedUserContent:
      "The captured pages are the user's own workspace, so they contain the user's real project names (KMP Harness, 黑苹果), repository/branch names, and the names of locally configured MCP servers (codegraph, dbx, idea, node_repl). These are not credentials and the user confirmed no further handling was needed, but they are personal workspace data and are recorded here rather than described as synthetic.",
  },
  coverage: {
    settings: {
      status: "observed",
      panelsObserved: settingsPanels.length,
      routeKeysReached: [...observedRoutes].sort(),
      routeKeysTotal: routes.length,
      routeKeysUnreached: unreached,
      blockedReason:
        "Every unreached route key has the same observed cause: no entry for it exists in this build's settings sidebar. The rail was re-read with the terminal part of the list in view and reports `scrollHeight === clientHeight === 1272` with `overflow-y: visible`, so nothing is merely scrolled out of sight: the sidebar holds exactly 19 buttons and no more.",
      sidebarGeometry: {
        source: "settings-rail-inventory.json",
        buttons: fs.existsSync(path.join(runtime, "settings-rail-inventory.json"))
          ? JSON.parse(fs.readFileSync(path.join(runtime, "settings-rail-inventory.json"), "utf8")).total
          : null,
        scrollHeight: 1272,
        clientHeight: 1272,
        overflowY: "visible",
        note: "A scrollable rail would have hidden the rest; this one does not scroll, so absence from the button list equals absence from the UI.",
      },
      staticGateHints: Object.fromEntries(unreached.filter((r) => gateHints.hints[r]).map((r) => [r, gateHints.hints[r]])),
      staticGateHintScope:
        "Verbatim switch arms from use-visible-settings-sections-3d650a480b6f.js (see gate-hints.json). They explain why a panel is absent; they are NOT runtime observations, the free variables are unresolved, and they are not proof that a missing key would render if reached.",
      panelInventory: settingsPanels.map((s) => ({
        slug: s.slug,
        runtimePanelSlug: s.observed.activeSlug,
        label: s.observed.activeLabel,
        headings: s.observed.headings.map((h) => h.text),
        textLen: s.observed.textFull?.length ?? 0,
        screenshot: s.screenshot?.file ?? null,
        dom: s.dom?.file ?? null,
        consoleEntries: s.console?.count ?? 0,
        consoleErrors: s.console?.errors ?? 0,
      })),
    },
    shell: {
      status: "observed",
      surfaces: shellSurfaces.map((s) => ({
        slug: s.slug,
        title: s.observed.title,
        headings: s.observed.headings.map((h) => h.text),
        navRails: s.observed.rails?.map((r) => r.ariaLabel) ?? [],
        composer: s.observed.composer
          ? {
              tag: s.observed.composer.tag,
              role: s.observed.composer.role,
              dataAttrs: s.observed.composer.dataAttrs,
              empty: s.observed.composer.empty,
            }
          : null,
        composerButtons: s.observed.composerButtons ?? [],
        screenshot: s.screenshot?.file ?? null,
        dom: s.dom?.file ?? null,
      })),
      sidebarDestinations: ["builtin:home", "builtin:automations", "builtin:customize", "builtin:projects"],
      note:
        "The sidebar's own `data-sidebar-destination` values are `builtin:home`, `builtin:automations`, `builtin:customize` and `builtin:projects`; the `探索` (Explore) entry carries no destination attribute and opens a popover, not a destination.",
    },
    gitChanges: {
      status: "observed",
      detail:
        "Opened by clicking the conversation summary panel's `变更` row. The app opened its own `变更` tab inside the workspace layout (branch line `+20,301 −1,091  feat-SuIYI  origin/main`, a file tree, and a diff of `.idea/vcs.xml`).",
      screenshot: "screenshots/git-changes.png",
      note: "The old and new side of the diff coincide because this checkout's HEAD matches the index for that file; nothing was staged, discarded or committed.",
    },
    exploreMenu: {
      status: "blocked",
      detail:
        "Clicking `探索` opens a radix popover measuring 228x37 at (50,183) whose entire content is the single word `项目`. No `settings.nav.chronicle`-style secondary destination list rendered, so the Explore menu could not be evidenced as a menu of destinations in this build.",
      observedMarker: "the popover DOM contains `_Button_ojzy2_2` / `_ButtonInner_ojzy2_2` and one row labelled 项目",
    },
    composer: {
      status: "observed",
      detail:
        "`div[role=textbox][data-codex-composer=true][data-virtualkeyboard=true]`, 712x44 at (1093,1216), empty. Adjacent controls: `添加文件等内容`, `更改权限` (`完全访问`), a disabled `正在加载模型…` model button, and `发送` — disabled while the composer is empty.",
    },
    scheduledTasks: {
      status: "observed",
      detail: "`安排任务` header plus `新建任务`; no task was created, started or paused.",
    },
    plugins: {
      status: "observed",
      detail:
        "Packaged as `builtin:customize`; renders the marketplace `插件` page with `公开`/`个人` tabs, category headings Communication / Creativity / Data & Analytics / Developer Tools / Education & Research, and `未安装任何插件`. No install was requested.",
    },
    conversations: {
      status: "observed",
      detail: "One existing conversation was opened read-only (`查看 Codex 复刻项目状态`) to observe the conversation page, the summary panel and the composer.",
    },
  },
  runtimeAssets: {
    scriptsParsed: chunks.count,
    capturedVia:
      "`Debugger.enable` + `Debugger.scriptParsed` — `performance.getEntriesByType('resource')` is empty under the `app://` scheme, so timing data cannot be used.",
    stylesheetsApplied: loadedSheets.size,
    phase12SheetsCopied: copiedSheets.length,
    phase12SheetsAppliedAtRuntime: appliedCopiedSheets.length,
    phase12SheetsNotApplied: unappliedCopiedSheets.map((f) => ({
      file: f,
      bytes: fs.statSync(path.join(ROOT, "12-semantic/formatted/webview/assets", f)).size,
      ...owningChunk(f),
    })),
    note:
      "Style sheets only accumulate: after the first panel renders, every later panel adds nothing, so `document.styleSheets` gives the union the settings surface needs, not a per-panel delta. The four unapplied sheets belong to chunks V8 never parsed in this session.",
  },
  console: {
    entries: consoleTotal,
    errors: consoleErrors,
    detail:
      "No `error`-level entry and no uncaught exception on any captured surface. The only noise is Statsig gates/experiments reporting a missing `userID` id_type, plus one react-router notice (`Matched leaf route at location \"/\" does not have an element or Component`) on the home surface.",
  },
  crosscheck: {
    status: "observed",
    file: "crosscheck.json",
    matchRule:
      "A claim matches when every literal run of its zh-CN string appears in order in one perceived string of the page — page text, heading, or a control's accessible name. ICU placeholders ({appName}) and rich-text tags are treated as gaps; `aria-label` is included because an icon-only button's copy is invisible to `innerText` alone.",
    tally: crosscheck.tally,
    note:
      "`not-observed` is not proof of a defect: a page can render state-dependent copy, sit below the fold, or be gated. It only asserts that the string was not among the strings this read-only pass could perceive.",
    findings: {
      fullyMatched: crosscheck.tally.matched ?? 0,
      stateDependent: crosscheck.tally["state-dependent"] ?? 0,
      pageReachedButStringAbsent: crosscheck.tally["not-observed"] ?? 0,
      pageNeverReached: crosscheck.tally["route-not-observed"] ?? 0,
      idWithoutLocaleString: crosscheck.tally.unresolvable ?? 0,
      absentClaimBreakdown: {
        ofThe29: "28 strings appear nowhere on any observed surface; 1 (`settings.appshots.destination.lastChat` = 当前聊天) appears on other panels, so it is shared copy rather than this panel's own.",
        byKind: { control: 11, copy: 12, page: 2, dialog: 2, "settings-page": 2 },
        readAs:
          "The control/dialog/page claims are the informative ones: a dialog title, a destructive-confirmation string or an empty-state label that never rendered says the static slice documented a surface this build does not show by default. Copy claims are the weakest, since most are conditional.",
      },
    },
  },
  discrepancies: [
    {
      id: "d1-settings-surface-not-url-addressable",
      status: "observed",
      staticExpectation: "55 settings routes are separate lazy routes resolved from settings-lazy-routes-8d1f593a29a7.js.",
      runtimeFinding:
        "In this build the settings surface is state, not URL: `history.pushState({}, '', '/settings/notifications')` left the rendered Worktrees panel in place, and entering settings by clicking `设置` leaves `location.pathname === '/'`. The live identity of a panel is its `data-settings-panel-slug`, not a route path.",
    },
    {
      id: "d2-pets-is-not-codex-micro",
      status: "observed",
      staticExpectation: "Phase 12 mapped route key `codex-micro` onto the Mini/pets page.",
      runtimeFinding:
        "The live `Mini 与虚拟宠物` panel reports `data-settings-panel-slug=\"pets\"`, and `codex-micro-settings-1e444c83baa2.js` is never parsed. The two are different panels; the `pets` panel renders `settings.pets.*` copy and a virtual-pet gallery.",
    },
    {
      id: "d3-four-route-stylesheets-never-applied",
      status: "observed",
      staticExpectation: "28 route stylesheets are preloaded by the settings route dependency list.",
      runtimeFinding:
        "24 of the 28 are applied; `93c2fa48ca6d`, `9787caae2bc9`, `9aed74b17655` and `de34b33b485e` are not, and each one's owning chunk was never parsed. The static dep list over-approximates this build's actual load.",
    },
    {
      id: "d4-most-copied-sheets-are-shared-not-per-page",
      status: "observed",
      staticExpectation: "Each settings route contributes its own stylesheet.",
      runtimeFinding:
        "89 stylesheets are applied even before the first panel finishes rendering, and switching among all 19 panels adds none. The settings surface loads one shared bundle of sheets; per-route CSS is not separable at runtime.",
    },
    {
      id: "d5-two-page-windows",
      status: "observed",
      staticExpectation: "One app window.",
      runtimeFinding:
        "Two `type: page` targets share `app://-/index.html`; the command menu is a separate 760x640 window with `?initialRoute=%2Fspotlight`. Any CDP pass that picks `.at(-1)` reads the palette instead of the app.",
    },
    {
      id: "d7-mcp-settings-has-no-panel-and-is-not-gated",
      status: "observed",
      staticExpectation:
        "The visibility switch gives `mcp-settings` an unconditional `{ visible: !0 }`, and the settings section catalog lists it as its own entry, so a reachable MCP settings page was expected.",
      runtimeFinding:
        "No sidebar entry exists for it (`mcp-settings-347422a00db2.js` and `settings-external-section-df17affc8af1.js` were never parsed), yet the MCP server list, the MCP Apps data section and the `清除所有 MCP 应用的站点数据和缓存` control all render *inside* the 插件 page, whose slug is `plugins-settings`. The plugin page imports mcp-settings' own content component with `manageOnly: !0` (`plugins-settings-7a89487c0c4c.js` line 4943), so the feature is present but has no panel of its own in this build. Static reading alone would have predicted a page that does not exist.",
    },
    {
      id: "d8-catalog-and-route-table-disagree",
      status: "observed",
      staticExpectation: "The 55 settings route keys and the section catalog describe the same set of panels.",
      runtimeFinding:
        "The catalog in `app-primary-75d53eef2374.js` has 53 entries; Phase 12's route table has 55. `family` and `environments` are route keys with no catalog entry (`environments` belongs to `settings-external-section`'s `[alpha] 在浏览器中打开` external subtitle), while the live 环境 panel is `local-environments`. Neither static list alone is the UI's panel set: the live set is 19.",
    },
    {
      id: "d9-mcp-content-appears-under-plugins",
      status: "observed",
      staticExpectation: "MCP management is a settings page of its own.",
      runtimeFinding:
        "The 插件 page renders `MCP 4 技能 2`, a searchable `MCP 服务器` list (codegraph, dbx, idea, node_repl) and an `MCP 应用 / 应用数据` section. Merging panels is invisible to a slice-by-chunk reading, because the *chunk* boundary and the *panel* boundary are different things.",
    },
    {
      id: "d6-pages-render-partial-state",
      status: "observed",
      staticExpectation: "Phase 12 claims describe fully populated pages.",
      runtimeFinding:
        "Several pages render a loading/partial state on first paint (`存储` shows `正在更新…` and `2.4 GB`/`165.7 MB` partial totals, `导入` shows `正在检查导入项`, `钩子` shows `1 个钩子`). Copy tied to a settled state is therefore neither confirmed nor refuted by this pass.",
    },
  ],
  artifacts: {
    screenshots: fs.readdirSync(path.join(runtime, "screenshots")).length,
    domSnapshots: fs.readdirSync(path.join(runtime, "dom")).length,
    consoleCaptures: fs.readdirSync(path.join(runtime, "console")).length,
  },
  notRun: [
    "No settings control was toggled, saved or reset; controls are recorded as observed state only.",
    "No credential, API key, account switch or profile/account page.",
    "No Git operation: no stage, discard, commit, push, branch or worktree action (the `变更` tab was opened read-only).",
    "No scheduled task was created, run or paused; no plugin installed; no connection authorized.",
    "No composer input, and no message sent.",
    "Routes absent from the settings sidebar (36 of 55) are `blocked`, not `not-run`: they were looked for and not found.",
  ],
};

fs.writeFileSync(path.join(runtime, "runtime-evidence.json"), JSON.stringify(evidence, null, 2));
console.log(
  JSON.stringify(
    {
      settingsPanels: settingsPanels.length,
      shellSurfaces: shellSurfaces.length,
      unreachedRoutes: unreached.length,
      appliedCopiedSheets: appliedCopiedSheets.length,
      unapplied: unappliedCopiedSheets.length,
      consoleErrors,
      screenshots: evidence.artifacts.screenshots,
      dom: evidence.artifacts.domSnapshots,
      console: evidence.artifacts.consoleCaptures,
      asarHashMatches: asarHash.startsWith("335bb4f5"),
    },
    null,
    2,
  ),
);
