#!/usr/bin/env node
/**
 * Phase 10 (responsive + mobile) semantic rename specs.
 *
 * Writes one renames/<slice>.json per bounded slice. Every declAnchor is exact
 * baseline text; the semantic tool renames only the identifier binding inside
 * that anchor and later proves inverse byte equality.
 *
 * Usage:
 *   node scripts/codex-full-restore-phase10-renames-20261104.mjs [--out DIR]
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
const outIndex = process.argv.indexOf("--out");
const outDir =
  outIndex >= 0 ? path.resolve(process.argv[outIndex + 1]) : path.join(phaseDir, "renames");

const SHARED = "app-shared-601019a330d7.js";
const INITIAL = "app-initial-18b189e867e7.js";
const PRIMARY = "app-primary-75d53eef2374.js";

const specs = {
  "media-query-hooks-slice": {
    source: SHARED,
    renames: [
      ["Nx", "initReactEnvironmentModule", "Initializes the React namespace, layout-effect alias, server flag and empty size value used by the media hooks."],
      ["ain", "useIsClient", "Returns the client flag through useSyncExternalStore, with a server snapshot that stays false."],
      ["uin", "initIsClientStoreModule", "Creates the external store used by useIsClient."],
      ["din", "useMediaQuery", "Hydration-safe media-query hook: returns matchMedia(e).matches only after the client flag is true and the query is non-null."],
      ["min", "initMediaQueryModule", "Initializes the React bindings and client store for useMediaQuery."],
      ["mye", "useMatchMedia", "Generic matchMedia hook with defaultValue/initializeWithValue options and addListener fallback."],
      ["pye", "useEventListener", "Adds an event listener to window or a supplied target and removes it on cleanup."],
      ["hye", "useUnmountEffect", "Runs the latest callback once when the component unmounts."],
    ],
  },
  "reduced-motion-slice": {
    source: SHARED,
    renames: [
      ["hin", "resolveReducedMotion", "Maps the off/on/system preference plus the system media query to a boolean."],
      ["gin", "systemPrefersReducedMotion", "Reads (prefers-reduced-motion: reduce) when matchMedia exists."],
      ["_in", "getReducedMotionMediaQueryList", "Returns the reduced-motion MediaQueryList or null when matchMedia is unavailable."],
      ["xin", "initReducedMotionStore", "Creates the reduced-motion query store and derived effective-preference store."],
      ["Sin", "useReducedMotion", "Selects the effective reduced-motion value; the argument can force false."],
      ["s2", "initReducedMotionModule", "Initializes the reduced-motion preference modules."],
    ],
  },
  "touch-device-slice": {
    source: SHARED,
    renames: [
      ["iOn", "getUserAgent", "Returns navigator.userAgent or an empty string outside a browser."],
      ["aOn", "isIOSDevice", "Tests the user agent for iPad, iPhone or iPod."],
      ["oOn", "isMacOS", "Tests the user agent for Mac OS X."],
      ["sOn", "isTouchDevice", "Returns true for iOS, Android or (pointer: coarse)."],
    ],
  },
  "mobile-layout-store-slice": {
    source: INITIAL,
    renames: [
      ["LRr", "mobileLayoutEnabledStore", "Read-only store that is constant false; the mobile-layout feature flag never enables in this bundle.", "var LRr;"],
      ["RRr", "initMobileLayoutFlag", "Declares the constant-false mobile-layout flag store."],
      ["zRr", "smallViewportStore", "Writable store fed by matchMedia('(width < 640px), (pointer: coarse)').", "var zRr, BRr;"],
      ["VRr", "initMobileLayoutStore", "Registers the 640px/pointer-coarse media listener and derives the mobile-layout store."],
      ["BRr", "isMobileLayoutStore", "Derives mobile layout as mobileLayoutEnabled && !smallViewport, so it stays false while the flag is false.", "var zRr, BRr;"],
    ],
  },
  "app-shell-frame-slice": {
    source: PRIMARY,
    renames: [
      ["Ejt", "initAppShellFrameModule", "Initializes the shell-frame stores and constants, including the constant-null fixed width store.", "var Ejt, sF, cF, Djt, Ojt, kjt, Ajt, jjt, Mjt;"],
      ["bjt", "AppShellFrame", "Renders the app-shell frame, root CSS variables, data-app-shell-* attributes and left-panel layout."],
    ],
  },
  "app-shell-left-panel-slice": {
    source: PRIMARY,
    renames: [
      ["iF", "initAppShellLayoutStyles", "CSS-module class map for workspace, layout, left panel, responsive left panel and page surfaces."],
      ["ajt", "AppShellLeftPanel", "Collapsible/resizable left panel; adds the responsive left-panel class when collapsible and keeps its minimum width at 240."],
    ],
  },
  "floating-left-panel-slice": {
    source: PRIMARY,
    renames: [
      ["xjt", "toPixelValue", "Formats a number as a px string."],
      ["Sjt", "toClampedPixelValue", "Clamps a value at zero before formatting it as px."],
      ["Cjt", "toPixelString", "Formats a number as px."],
      ["wjt", "FloatingLeftPanel", "Fixed floating left panel with enter/exit motion and width animation."],
      ["Tjt", "FloatingLeftPanelContainer", "Motion container used by the floating left panel."],
    ],
  },
  "sidebar-chrome-slice": {
    source: PRIMARY,
    renames: [
      ["QP", "initSidebarTriggerContext", "Creates the sidebar-trigger context and its default toggle callback."],
      ["ZP", "sidebarTriggerContext", "Context object consumed by sidebar chrome to toggle the sidebar.", "var TAt, ZP;"],
      ["EAt", "SidebarChrome", "Chooses browser-popover chrome when state is 'browser-popover', otherwise app-shell chrome."],
      ["DAt", "AppShellSidebarChrome", "App-shell sidebar chrome; wraps the sidebar trigger and forwards the toggle callback."],
      ["OAt", "toggleSidebar", "Default sidebar toggle that emits the toggleSidebar/sidebar_trigger telemetry event."],
      ["kAt", "BrowserPopoverSidebarChrome", "Browser-popover sidebar chrome variant."],
      ["AAt", "SidebarTrigger", "Sidebar trigger button with show/hide message ids based on state."],
    ],
  },
  "sidebar-navigation-header-slice": {
    source: PRIMARY,
    renames: [
      ["vIn", "initSidebarElectronMessages", "Message catalog for the Electron sidebar, including 'Closes the mobile sidebar drawer'."],
      ["xIn", "NavigationHeader", "Navigation header taking priorityEntrypoint, sidebarMode and sidebarTriggerState."],
      ["CIn", "initNavigationHeaderModule", "Initializes the navigation-header dependencies."],
    ],
  },
  "searchable-page-layout-slice": {
    source: "searchable-page-layout-6693d2a8dff5.js",
    renames: [
      ["ve", "initSearchablePageStyles", "CSS-module class map for the searchable page shell, header row, title, actions, search, navigation, toolbar and filter tags."],
      ["ye", "PageHeader", "Page header with sticky/collapse-on-scroll, horizontal padding, wrapped actions and optional search expansion."],
      ["be", "TitleBarInsetContainer", "Measures title-bar insets and applies the --header-content-start variable."],
      ["xe", "PageHeaderToolbar", "Page-header toolbar renderer."],
      ["M", "initPageHeaderContext", "Creates the page-header context used by nested header slots."],
      ["N", "SearchablePageLayout", "Searchable page layout; uses browser:max-md variants for compact browser layouts."],
      ["Ee", "SearchablePageFadeHeader", "Sticky header with an optional bottom fade."],
      ["De", "SearchablePageToolbarRow", "Toolbar row with wrap/inset variants."],
      ["I", "initSearchablePageModes", "Defines the header presentation modes: default, inset, hero, catalog, compact and profile."],
    ],
  },
  "settings-content-layout-slice": {
    source: "settings-content-layout-ac451a456013.js",
    renames: [
      ["_", "initSettingsContentContext", "Creates the settings-content layout context."],
      ["oe", "useContainTouchOverscroll", "Pointer-coarse touch handler that contains overscroll at the settings scroll container."],
      ["x", "SettingsContent", "Settings page content with scroll reset, sticky controls, density and mobile-header attributes."],
      ["S", "SettingsContentLayoutProvider", "Provides whether the settings page uses the slate layout."],
      ["T", "initSettingsContentModule", "Initializes the settings-content module dependencies."],
    ],
  },
  "dialog-content-slice": {
    source: SHARED,
    renames: [
      ["FSn", "initDialogStyles", "CSS-module class map for dialog overlays, surfaces and responsive sheets."],
      ["c6", "isResponsiveSheetSize", "Recognizes responsiveSheet and edgeToEdgeSheet sizes."],
      ["ISn", "getDialogSizeClass", "Maps dialog size to its width class."],
      ["LSn", "DialogContentImpl", "Dialog content implementation handling close affordances and overlays."],
      ["RSn", "DialogTitle", "Dialog title with variant-specific typography."],
      ["zSn", "DialogClose", "Dialog close wrapper."],
      ["BSn", "DialogOverlay", "Dialog overlay with variant-specific background and dimming."],
      ["VSn", "DialogTrigger", "Dialog trigger wrapper."],
      ["HSn", "DialogContent", "Dialog content; 'sheet' anchors bottom-left, while responsiveSheet becomes a bottom sheet below 40rem."],
    ],
  },
  "popover-positioning-slice": {
    source: "popover-12c80c240995.js",
    renames: [
      ["T", "getClosestSide", "Chooses the closest rectangle side to a point."],
      ["E", "getOffsetPoints", "Offsets rectangle corners by a distance."],
      ["D", "getRectCorners", "Returns the four corners of a rectangle."],
      ["O", "isPointInPolygon", "Point-in-polygon test used to decide whether the pointer left the popover."],
      ["k", "sortPointsByAngle", "Sorts points by angle around a center."],
      ["A", "getConvexHull", "Monotone-chain convex hull used to shape the hover bridge."],
      ["M", "initPolygonGeometryModule", "Initializes the polygon geometry helpers."],
      ["I", "initPopoverContext", "Creates the popover context and its usePopover accessor."],
      ["V", "initPopoverStyles", "CSS-module class map for the popover and shake transition."],
      ["Y", "initPopoverModule", "Initializes the popover components and their Floating UI configuration."],
      ["W", "Popover", "Popover root with trigger and content subcomponents.", "var H, U, W, G, K, q, J;"],
      ["G", "PopoverTrigger", "Popover trigger button with aria-haspopup=dialog.", "var H, U, W, G, K, q, J;"],
      ["q", "PopoverHoverContent", "Hover-open popover content with pointer-leave bridge handling.", "var H, U, W, G, K, q, J;"],
      ["J", "PopoverContent", "Popover content wrapper.", "var H, U, W, G, K, q, J;"],
      ["K", "PopoverContentImpl", "Floating-UI popover content: sideOffset 8, alignOffset -5 for center, collisionPadding 20, hideWhenDetached.", "var H, U, W, G, K, q, J;"],
      ["X", "initPopoverSupportModule", "Initializes the supporting portal/accessibility modules used by the popover components."],
      ["Z", "initPopoverEntrypoint", "Entrypoint initializer that loads the popover components and their support modules."],
    ],
  },
  "responsive-sheet-caller-slice": {
    source: "browser-composer-disclaimer-8c236022152a.js",
    renames: [
      ["ye", "BrowserComposerDisclaimer", "Renders the browser composer disclaimer and opens a responsiveSheet dialog below the 640px breakpoint."],
    ],
  },
  "composer-attachments-slice": {
    source: "composer-content-b926d24ef280.js",
    renames: [
      ["GP", "ComposerAttachments", "Composer attachment row with a horizontal scroll container and per-attachment removal."],
    ],
  },
  "editor-mobile-toolbar-slice": {
    source: "editor-76f8ec768ed8.js",
    renames: [
      ["tt", "preventDefaultEvent", "Prevents the default browser action."],
      ["nt", "preventDefaultAndFocusCurrentTarget", "Prevents default and focuses the current target."],
      ["rt", "isNarrowViewport", "Returns matchMedia('(max-width: 639px)').matches."],
      ["it", "EditorToolbarButton", "Toolbar button that prevents pointer-down focus loss."],
      ["at", "preventDefaultPointerDown", "Prevents the default pointer-down action."],
    ],
  },
  "tab-strip-slice": {
    source: "thread-app-shell-chrome-e7e38c7ea254.js",
    renames: [
      ["yc", "TabStrip", "App-shell tab strip; each tab is an @container/app-shell-tab container."],
    ],
  },
  "side-panel-pointer-coarse-slice": {
    source: "side-panel-b2f1ecc83aff1.js",
    renames: [
      ["ps", "SummaryPanelItem", "Summary panel item; reserves trailing padding for pointer-coarse input."],
      ["Ls", "DesktopConnectionRow", "Connected-computer row; reveals its trailing action for pointer-coarse input."],
    ],
  },
  "projects-index-slice": {
    source: "projects-index-page-78237015641b.js",
    renames: [
      ["Ti", "ProjectsIndexCreateButton", "Projects-index create action; in compact mode its label is browser:max-md:sr-only."],
    ],
  },
  "automation-row-slice": {
    source: "automation-row-37c157e22263.js",
    renames: [
      ["Ae", "AutomationSuggestionRow", "Suggested scheduled-task row with add/set-up actions."],
      ["We", "AutomationRow", "Scheduled-task row that establishes an @container for its content."],
    ],
  },
  "settings-form-slice": {
    source: "settings-form-d03684fa1af2.js",
    renames: [
      ["ln", "GiftCreditsRow", "Dedicated gift-credit row in the usage and billing credits section."],
      ["un", "CreditsBalanceSection", "Credits balance section; stacks at max-md when it has children."],
      ["dn", "AutoTopUpSection", "Auto top-up section; stacks at max-md when enabled."],
    ],
  },
  "settings-analytics-slice": {
    source: "settings-analytics-telemetry-f26bc8be0b57.js",
    renames: [
      ["Be", "AnalyticsCard", "Analytics card; its series legend becomes scrollable only when pointer is fine, and stays expanded for pointer-coarse."],
    ],
  },
  "settings-plugin-controls-slice": {
    source: "settings-plugin-controls-cbca110a69d1.js",
    renames: [
      ["wt", "PluginSettingsRow", "Plugin settings row; wraps with max-sm:flex-wrap."],
    ],
  },
  "plugin-mcp-row-slice": {
    source: "plugins-settings-7a89487c0c4c.js",
    renames: [
      ["wa", "McpServerRow", "MCP server row; its actions become full-width, wrapped and end-aligned at max-sm."],
    ],
  },
  "schedule-dialog-slice": {
    source: "dialog-c06baa494c77.js",
    renames: [
      ["qn", "getScheduleType", "Maps hourly/daily/weekdays/weekly schedule strings to the analytics schedule-type enum."],
      ["$n", "WorkspaceAgentSchedulesDialog", "Workspace-agent schedules dialog; uses size=responsiveSheet."],
    ],
  },
};

fs.mkdirSync(outDir, { recursive: true });
const summary = [];
for (const [slice, spec] of Object.entries(specs)) {
  const entries = spec.renames.map(([oldName, newName, evidence, anchor]) => ({
    old: oldName,
    new: newName,
    declAnchor: anchor ?? `function ${oldName}(`,
    originalPath: `webview/assets/${spec.source}`,
    evidence,
    confidence: "high",
  }));
  fs.writeFileSync(
    path.join(outDir, `${slice}.json`),
    `${JSON.stringify(entries, null, 2)}\n`,
  );
  summary.push({ slice, bindings: entries.length });
}
console.log(
  JSON.stringify(
    {
      slices: summary.length,
      bindings: summary.reduce((sum, s) => sum + s.bindings, 0),
      summary,
    },
    null,
    2,
  ),
);
