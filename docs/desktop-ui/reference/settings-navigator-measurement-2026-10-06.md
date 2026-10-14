# Settings Navigator measurement (batch A)

Date: 2026-10-06. Scope: the Settings page's own left navigator only. This batch does not touch the App
Rail, the Workspace Navigator, the Account/Profile menu, the More overflow, the BottomBar, the titlebar
or the other page navigators.

## Does Settings have a left navigator?

Yes — from app.asar, not assumed. The extracted bundle `settings-page-bbd5e3fa3b8c.js` (50261 bytes,
from `/Applications/ChatGPT.app/Contents/Resources/app.asar`) renders the Settings page with the same
`sidebar-navigation` container class the Workspace navigator uses:

| Evidence | Value |
| --- | --- |
| Container | `className:`sidebar-navigation flex min-h-0 flex-1 flex-col select-none`` + `px-row-x` |
| Aria label | `settings.nav.ariaLabel` -> "Settings" |
| Title | `settings.nav.title` -> "Settings", description "Title of the Settings sidebar" |
| Back entry | `settings.nav.back` -> "Back to app", description "Button to return to the main app from settings" |
| Collapse / expand | `settings.nav.collapseSidebar` -> "Collapse settings navigation"; `settings.nav.expandSidebar` -> "Expand settings navigation" (tooltip + accessible label) |
| Search | a search input bound to `searchQuery` / `onQueryChange`, plus `settings.search.*` |
| Rows | entries rendered with `data-settings-panel-slug`, an icon, a label, `isActive`, ArrowUp/ArrowDown navigation |
| Grouping | grouped sections (`settings.nav.heading.*`, many `settings.nav.<item>` ids) |

So `PageShellSpec(PageId.Settings) = NavigatorKind.Settings` is structurally correct and unchanged.

There is **no same-state Codex Settings screenshot** in this repository or on the reference machine, so
every geometry value below is Harness-measured and can only claim structure/token alignment.

## Measured Harness geometry (light, 1440x900, density 1)

| Metric | Value | Notes |
| --- | ---: | --- |
| Rail / navigator boundary x | 52 | rail 0..50, 1px hairline at 51 |
| Navigator width | 275 | `SidebarExpanded`; the compact drawer is also 275 |
| Navigator surface | 249 `#f9f9f9` | `Surface` token; dark reads 32 |
| Rail surface | 243 `#f3f3f3` | `SurfaceSecondary`; dark reads 28 |
| Main surface | 255 | `Canvas` |
| Header title ink | y 20..32, centre 26 | title "设置" |
| Header subtitle ink | y 34..49, centre ~41 | subtitle "设置分区" |
| Collapse toggle | header right, 14px glyph | `settings-navigator-collapse`, wired to the current navigator's mode toggle |
| Search row | y 57..92 (36 tall) | `SurfaceSecondary` fill, corner 8; `settings-navigator-search` |
| Back row | centre 127 | "返回应用" + chevron, `settings-navigator-back` |
| Section label | centre 171.5 | "分区" |
| Section row pitch | 40.0 | row 36 (`Control.Row`) + 4 (`Spacing.One`) |
| Selected row fill | 36 tall, x 60..318, corner 8 | `SurfaceSelected` 237 |
| Compact drawer scrim | over 276..599 at ~171 | `NavigatorScrim` black 0.32 over white |

Row order: header (title/subtitle + collapse) -> search -> Back to app -> 分区 label -> six section rows.

## What this batch changed

The Settings navigator previously contradicted the app.asar evidence: it had no search entry, so
`SettingsNavigatorState.query` was unreachable and the "filter" only printed a dead hint, and it had
neither the Back-to-app nor the collapse entry.

- `settingsSectionName` / `settingsNavigatorSections` moved/added to `AppShellState.kt` as pure
  functions; a non-blank query now keeps only the matching sections (blank keeps all).
- `SettingsNavigator` gained the header search row (`settings-navigator-search`), the header collapse
  toggle (wired to the page's own navigator mode) and the "返回应用" Back row.
- `NavigatorPane` gained optional `headerAction` / `headerExtra` slots; Plugins / Images / Hosts pass
  neither, so their output is unchanged.
- `AppShell` gained `onSettingsSearch` and `onSettingsBack` (both default no-ops); `HarnessApp` maps
  them to `settingsNavigator.copy(query = ...)` and `navigation.back()` / Home.
- New `SettingsNavigatorStateTest` (filter + Workspace isolation) and `renderSettingsNavigatorStates`
  fixture (8 captures).

No global navigator token was changed; `NavigatorRowGap` and the Workspace navigator are untouched.

## Evidence classification

- **Code satisfies (app.asar + behaviour test)**: Settings owns a navigator; the navigator is mounted
  only for `PageId.Settings`; the query is Settings-only and never reads `WorkspaceNavigatorState`;
  overlays and the Account menu route do not change `currentPage`.
- **app.asar evidence, no same-state PNG**: the existence and semantics of the Settings title, search,
  Back-to-app and collapse entries, and the `sidebar-navigation` container. Implemented to that
  structure; exact pixels are unverified.
- **Structural inference only**: header height, search-row height and position, Back-row position,
  section row pitch (40), corner radii, and the compact drawer width/scrim.
- **Unable to confirm**: whether Codex uses 30px rows (the `.sidebar-navigation` token) or 36px here —
  the Settings pane uses `Control.Row` 36 via the shared `NavigatorRow`, which Plugins/Images/Hosts
  also use, so it was left unchanged this batch.
- **Known model gaps (not implemented)**: Codex rows carry leading icons and grouped section headings
  (`settings.nav.heading.*`); Harness renders a flat, icon-less six-item list under one "分区" label.

