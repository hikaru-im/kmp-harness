# Page-level navigator matrix and audit (batch 0 + batch B)

Date: 2026-10-06. Batch B adds the app.asar revision check, the user screenshots as the highest evidence
tier, a per-page A/B/C classification, the scheduled-tasks planning gap, and the Settings/Plugins
implementation gaps. It changes no page model and no visual token: `PageShellSpec`, `PageId`,
`NavigationState`, `AppShellState`, the Rail, the AccountMenu, the More overflow, the BottomBar, the
titlebar and the Main Surface are byte-for-byte what batch 0/1 left. The batch 0 record is kept below
and is marked superseded where batch B corrects it.

Highest constraint (user): Codex pages do **not** share one left navigator. Different pages have
different navigators and some pages have none, so the Rail and the Navigator are not one-to-one. The
App Rail is a fixed app-level column; the Navigator is owned by the current page.

## Codex evidence baseline (app.asar revisions)

Two revisions of the installed bundle have been read. The older one is kept because every earlier
document cites its chunk file names; the current one is what batch B analyzed. Extraction is
read-only, into `/tmp`, and never writes inside the repository.

| Field | Batch 0 baseline (older) | Current (batch B) |
| --- | --- | --- |
| Archive | `/Applications/ChatGPT.app/Contents/Resources/app.asar` | same |
| App version | `26.930.31730` | `26.930.51102` (CFBundleVersion `13100`) |
| SHA-256 | `64023959c7c3a4c19022c44f3e8d4b3e4010cefb6e2ea1edce0b535fd417776e` | `22252f9b76d73a04d9e23710e358201cc4c29b6f003cfbbe109eeb0784dcf848` |
| Size | 522 MB | 546868335 bytes (about 546 MB) |

Chunk file names carry content hashes and changed between the revisions, so the batch 0 bundle names
are **not present** in the current bundle. Current equivalents used by batch B:

| Batch 0 chunk | Current chunk |
| --- | --- |
| `settings-page-bbd5e3fa3b8c.js` | `settings-page-b07b6e77dc14.js` |
| `sidebar-b3619131cde6.js` (main app sidebar) | `sidebar-debb6bb9726d.js` (scheduled) + `sidebar-d2a4148ed3c4.js` (code review) |
| `thread-app-shell-chrome-db6a0c50aea0.js` | `thread-app-shell-chrome-6e13d1129e78.js` |
| `projects-index-page-52d8e1d790a3.js` | `projects-index-page-3d127290b4aa.js` |
| `local-conversation-page-c5fb95945be8.js` | `local-conversation-page-71ad501896fc.js` |
| (not read before) | `library-page-74fd612a16e9.js` (exports `LibraryPage`) |
| (not read before) | `plugin-detail-page-a5b128ddee86.js` (exports `PluginDetailPage`, `ManagePluginDetailPage`) |
| (not read before) | `plugins-settings-146885f88b18.js` (exports `PluginsSettings`) |

Batch B additionally read `app-initial-f9b16fbf8fc7.js` and `app-shared-9d148924be0b.js` (unchanged
between the two revisions). Those two bundles hold the route table and the page-to-sidebar resolver
cited below.

## Evidence tiers

- **user capture**: a Codex window screenshot supplied by the user. Highest tier. Saved in-repo under
  `docs/desktop-ui/reference/captures/2026-10-06-user-pages/` with `manifest.json` and SHA-256.
- **app.asar**: a route literal, component name, control id or resolver branch read from the installed
  bundle. Structural evidence; it shows what the product can render, not which state the user sees.
- **structural inference / Harness decision**: this project's own choice, used only where no Codex
  evidence exists. It must not be described as a Codex match.

## User-capture evidence (2026-10-06)

Three light-theme captures, raw 2560x1440, no resampling. Repo copies and the original temporary paths
are both listed; the SHA-256 values are copied from the in-repo `manifest.json`.

| Page | Repo copy | Original path | SHA-256 |
| --- | --- | --- | --- |
| Settings / General | `captures/2026-10-06-user-pages/settings-general.png` | `/var/folders/5f/ssbgv_654cg21y7ztd9kljc40000gn/T/TemporaryItems/NSIRD_screencaptureui_3sIQXF/截屏2026-10-06 下午5.12.08.png` | `d89296acb02f7f319e6fbcc551592ec99a5970f4157d93b06e27e140287edc7b` |
| Plugins marketplace | `captures/2026-10-06-user-pages/plugins-marketplace.png` | `/var/folders/5f/ssbgv_654cg21y7ztd9kljc40000gn/T/TemporaryItems/NSIRD_screencaptureui_b5L3H0/截屏2026-10-06 下午5.12.40.png` | `192df4ff8dd9ed400fe219aaaa8720ca649607e567ede8d10303bd80c722c5f2` |
| Scheduled tasks | `captures/2026-10-06-user-pages/scheduled-tasks.png` | `/var/folders/5f/ssbgv_654cg21y7ztd9kljc40000gn/T/TemporaryItems/NSIRD_screencaptureui_IJ2xuN/截屏2026-10-06 下午5.12.58.png` | `33b6fe02c9ed53fe5bbb42b82e16cbb185248b1af2e8077efe5c9ce143eb11ae` |

Common geometry read from the three captures: Rail `x = 0..51`; page Navigator `x = 52..340` (about
289px including the boundary); main content from `x = 341`; Navigator surface about `#fcfcfc`.

Observed Navigator structure, read per image (not from any earlier description):

- **Settings** (`settings-general.png`): title `设置` at the left of the header row, with no icon on the
  right; a visible search **input** below the title (rounded fill, magnifier, placeholder `搜索`); group
  headings `个人` / `集成` / `编码` / `已归档`; rows carry leading icons; `常规` is selected (rounded fill);
  `个人资料` and `账户` carry a trailing external-link arrow. No back row and no collapse control are
  visible. Main content is the centered `常规` form with a long right-hand scroll.
- **Plugins** (`plugins-marketplace.png`): title `自定义` with a magnifier **icon** on the right of the
  title row (no search input in this state); entries `插件` / `技能`; group heading `已安装`; installed rows
  with leading plugin icons (`Writing Style`, `WebCodex`, `Context7`, `Data`, `GitHub`). Main content is the
  marketplace: search, refresh, settings, `添加`, a `公开 / 个人` toggle and two-column category cards.
- **Scheduled tasks** (`scheduled-tasks.png`): title `定时任务` with a magnifier icon on the right; a
  `+ 新建任务` row; group heading `即将执行` with a filter icon on its right; a task row `跟踪 Koog PR #1703`
  with the status `监控中`. Main content is the `安排任务` empty state with template cards.

Caveat: the captures prove only the state they show (navigator expanded, light theme). They prove the
back row, the collapse control and collapsible group headings are **not visible** in this state; they do
not prove those affordances are absent from the product. Nothing here is implemented or asserted.

## Overlay surfaces (never a page navigator)

These are overlays. They are not the page's navigator and opening or closing one must not change
`NavigationState.currentPage`.

| Surface | Codex shape | Owner |
| --- | --- | --- |
| Avatar / account menu | profile dropdown from the rail avatar | `AccountMenuState`, an overlay |
| Rail More overflow | `sidebarElectron.showMore` list of overflow destinations | `RailOverflowState`, an overlay |
| Thread right panel | `RightPanelOutlet` / `thread-right-panel-state` | `OverlayId.ProjectContext` (Thread surface panel) |
| Thread bottom panel | `BottomPanelOutlet` | Thread surface panel |

## Page matrix with evidence classification (batch B)

| PageId | Harness NavigatorKind | Harness state object | Codex route / control | Class | Evidence |
| --- | --- | --- | --- | --- | --- |
| `Home` | Workspace | `WorkspaceNavigatorState` | `/` | **A** | `vO.home.sidebar = conversations`; `sidebarElectron.homeNavigation`; rail icon set; prior user capture |
| `RecentTasks` | Workspace | `WorkspaceNavigatorState` | no standalone route | **C** | `sidebar.recents.*` is a Home-sidebar section, not a page; Harness-only today |
| `Projects` | Workspace | `WorkspaceNavigatorState` | `/projects` | **C** | route + `projects-index-page-3d127290b4aa.js` (`ProjectsIndexPage`) exist; the left-column kind for `/projects` is unresolved |
| `Project` | Workspace | `WorkspaceNavigatorState` | `/projects?projectId=...` | **C** | `ProjectsIndexPage` has expandable `Project > chat/thread` children; the detail left column is unconfirmed |
| `Thread` | Workspace | `WorkspaceNavigatorState` | `/thread/:conversationId`, `/local/:id` | **A** | `local-conversation-page-71ad501896fc.js`, `thread-app-shell-chrome-6e13d1129e78.js` (right/bottom outlets) |
| `Settings` | Settings | `SettingsNavigatorState` | `/settings/*` | **A** | user capture `settings-general.png`; `settings.nav.*` + `data-list-navigation-item` in `settings-page-b07b6e77dc14.js` |
| `Plugins` | Plugins | `PluginsNavigatorState` | `/skills`, `/plugins` | **A** | user capture `plugins-marketplace.png` (`自定义`, `插件`/`技能`, `已安装`); app.asar `/skills` resolves to the `customize` sidebar |
| `PluginDetail` | Plugins | `PluginsNavigatorState` | `/skills/:pluginId` | **A** | app.asar resolver returns `customize` for any `/skills/...` path |
| `Images` | Images | `ImagesNavigatorState` | `/images` (rail item opens `/maps`) | **A** | `$O.images = builtin:images`; `sidebarElectron.imagesRouteNavLink`; resolver keeps the space/library sidebar area |
| `Hosts` | Hosts | `HostsNavigatorState` | no route, no `builtin:hosts` | **C** | hosts appear only inside Settings (`settings.hostDropdown.*`); the Harness Hosts page is a project decision |
| `Account` | None | (none) | `/profile`, `/u/:username`; menu is an overlay | **B** | the account surface is the avatar profile menu overlay; no page-level navigator is shown |

Classification key: **A** a page-owned left navigator is directly shown (user capture and/or app.asar);
**B** no page-level left navigator is directly shown; **C** insufficient evidence, so the Harness mapping
stays a project decision pending a capture.

Totals: A = Home, Thread, Settings, Plugins, PluginDetail, Images; B = Account; C = RecentTasks, Projects,
Project, Hosts.

Note on `PluginDetail`: the embedded plugin-app surface at `/plugins/:pluginId/app/:toolName` is a
separate route that the same resolver maps to **no** sidebar. That is a sub-surface of the plugin page,
not the page's navigator, so it does not change the `Plugins`/`PluginDetail` classification.

## Corrections to the batch 0 record

- Batch 0 listed Settings / Plugins / Images / Hosts as "structural inference". Batch B upgrades
  **Settings** and **Plugins** to A with the user captures, upgrades **Images** to A with the bundle
  resolver, and demotes **Hosts** to C (no route, no destination, hosts are a Settings section).
- Batch 0's account row asserted a "full-width main surface" (NavigatorKind.None). Keep None, but the
  evidence is that the account surface is a **menu overlay**, not that a Codex Account page renders
  full width. The Account page itself is unproven and stays a Harness decision.
- Batch 0 asserted the Settings section list is "backed by `settings.nav.heading.*`". That is true of
  the bundle, but the **rendered** Settings navigator (title without a right icon, a visible search
  input, grouped rows, leading icons) is only known from the user capture; batch 0 never had one.
- The `sidebarElectron.libraryRouteNavLink` (Library) and `sidebarElectron.imagesRouteNavLink` (Images)
  destinations are distinct rail items, so the Harness `NavigatorKind.Images` covers the Images/Library
  family rather than a dedicated gallery navigator of its own.

## Scheduled tasks: an independent reference type (local implementation)

Codex has a distinct scheduled-tasks page: route `/scheduled` (with `/inbox` redirected to it), a
`scheduled` sidebar kind in the resolver, the sidebar chunk `sidebar-debb6bb9726d.js`
(`scheduled.sidebar.*` controls: `searchTasks`, `filterBy`, `loading`, `runs.*`, `upcoming`, ...), and
the user capture titled `定时任务`. It remains independent from `RecentTasks` (which is a section of the
Home sidebar, not this page).

The local implementation now provides `PageId.ScheduledTasks`, `NavigatorKind.Scheduled`, a dedicated
scheduled navigator state, and a deterministic in-memory repository. It supports search, ownership and
team filters, task expansion, older-run pagination, retry, local task creation, team navigation, and
opening a run as a Thread carrying `automationId`, `runId`, and `navigationSource = scheduled_page`.
The repository is intentionally local; the `/automations` backend contract remains a later integration.

## Settings navigator: implementation gap (structure/token only)

The user capture shows: title `设置` with no right-side icon; a **visible search input** below the title;
group headings `个人` / `集成` / `编码` / `已归档`; rows with leading icons; a selected row with a rounded
fill; trailing external-link arrows on `个人资料` and `账户`. No back row and no collapse control are
visible in this state.

The current Harness `SettingsNavigator` differs on every one of those points: it renders a `设置分区`
subtitle, a search row tagged `settings-navigator-search`, a back row tagged `settings-navigator-back`, a
header collapse toggle, a single generic `分区` label and a flat six-row list with no icons. These are
recorded as gaps for a later batch; batch B changes none of them. The `settings.nav.collapseSidebar` /
`settings.nav.expandSidebar` strings exist in the bundle but a collapse control is not visible in the
capture, so it must not be forced on. Group headings are not proven collapsible and must not be given
that behaviour by inference.

Batch A measurements for the Harness Settings navigator (light 1440x900) are unchanged and are recorded
in `settings-navigator-measurement-2026-10-06.md`. There is still no same-state Codex Settings capture in
the Harness viewport, so no pixel-parity claim is made; the geometry there is structure/token alignment.

## Plugins navigator: implementation gap

The user capture shows: title `自定义` with a magnifier icon on the right; entries `插件` / `技能`; a group
heading `已安装`; installed plugin rows with leading icons. The current Harness `PluginsNavigator`
renders a `插件 / 已安装插件 / 全部插件 / 仅启用插件` placeholder instead. The marketplace toolbar and the
two-column category content in the main surface belong to a later plugins-page batch, not to the
navigator. Recorded as gaps; batch B changes none of them.

## What batch B changed

- This document: the app.asar revision table, the user-capture table, the A/B/C classification, the
  corrections, the scheduled-tasks gap and the Settings/Plugins gaps.
- New pure-state test `PageNavigatorMatrixAuditTest.kt` (audit anchor, exhaustive matrix, Workspace
  page pin, None affordance, overlay isolation).
- No change to `PageShellSpec`, `PageId`, `NavigationState`, `AppShellState`, or any Compose visual code.

---

## Batch 0 record (kept; superseded where batch B corrects it)

Scope: the page-to-navigator mapping and its state isolation only. This batch does not change any visual
token, the Rail geometry/colours/icons, the Workspace Navigator visuals, the AccountMenu, the More
overflow, the BottomBar visuals or the native titlebar.

### Categories kept separate

| Surface | Owner | Is it a left navigator? |
| --- | --- | --- |
| App Rail | shell (always mounted) | No, app-level fixed column |
| Workspace Navigator | the page, via `PageShellSpec` | Yes, resizable |
| Settings / Plugins / Images / Hosts navigator | the page, via `PageShellSpec` | Yes, fixed width |
| Profile / account menu | `AccountMenuState`, an overlay | No |
| Rail More overflow | `RailOverflowState`, an overlay | No |
| Thread context panel (right) | `OverlayId.ProjectContext`, a Thread surface panel | No |

The last four are explicitly **not** Workspace Navigators, so none of them may be recorded as "the page's
navigator" or mounted as a `NavigatorKind.Workspace`.

### PageId inventory and matrix (batch 0)

`PageId` is a sealed interface with eleven shapes. `pageShellSpec(page)` in
`apps/shared/src/im/hikaru/harness/client/shell/PageShellSpec.kt` maps each one:

| PageId | Content entry (`HarnessApp` `content` branch) | NavigatorKind | Navigator state object | Collapsible | Resizable | Desktop | Narrow (< 640dp) | Evidence |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `Home` | `HomePage` | Workspace | `WorkspaceNavigatorState` | yes | yes | pushed column / floating drawer | bottom bar + drawer | app.asar sidebar sections; user capture |
| `RecentTasks` | `RecentTasksPage` | Workspace | `WorkspaceNavigatorState` | yes | yes | same | same | batch B: C (not a standalone Codex page) |
| `Projects` | `ProjectsPage` | Workspace | `WorkspaceNavigatorState` | yes | yes | same | same | batch B: C (left column unresolved) |
| `Project` | `ProjectPage` | Workspace | `WorkspaceNavigatorState` | yes | yes | same | same | batch B: C (detail column unconfirmed) |
| `Thread` | `ThreadPage` / `SessionScreen` | Workspace | `WorkspaceNavigatorState` | yes | yes | same + right context panel | same | app.asar `local-conversation-page`, `thread-app-shell-chrome` |
| `Settings` | `SettingsScreen` | Settings | `SettingsNavigatorState` | yes | no | pushed column / floating drawer | bottom bar + drawer | user capture 2026-10-06; app.asar `settings.nav.*` |
| `Plugins` | `PluginInventoryScreen` | Plugins | `PluginsNavigatorState` | yes | no | same | same | user capture 2026-10-06; app.asar `/skills` to `customize` |
| `PluginDetail` | `PluginInventoryScreen` | Plugins | `PluginsNavigatorState` | yes | no | same | same | app.asar `/skills/:pluginId` to `customize` |
| `Images` | `ImagesPage` (placeholder) | Images | `ImagesNavigatorState` | yes | no | same | same | app.asar `builtin:images` + resolver |
| `Hosts` | `HostScreen` | Hosts | `HostsNavigatorState` | yes | no | same | same | batch B: C (no Codex route / destination) |
| `Account` | `AccountScreen` | **None** | (none) | no | no | full-width main surface | full-width + bottom bar, no navigator entry | account menu is an overlay (`account-menu-measurement-2026-10-06.md`) |

`NavigatorKind.None` is the only way a page owns no navigator. Every other page mounts exactly one.

### Affordance contract for a NavigatorKind.None page

A page that mounts no navigator must draw none of the navigator affordances. The shell derives all of
them from `PageShellSpec.mountsNavigator` (`navigator != NavigatorKind.None`):

| Affordance | Drawn when | None page |
| --- | --- | --- |
| Titlebar sidebar toggle (hamburger) | `mountsNavigator` | hidden (fixed in batch 0) |
| Bottom-bar navigator entry (`bottom-nav-drawer`) | `mountsNavigator` | hidden (fixed in batch 0) |
| Drawer column / floating drawer | `NavigatorLayout != Hidden` (Hidden when no navigator) | none |
| Floating scrim | `NavigatorLayout == Floating` | none |
| Resize hit region | `PageShellSpec.resizable` (Workspace only) | none |

### Audit result (batch 0)

#### Code already satisfied

- `pageShellSpec` is exhaustive over the `PageId` sealed interface with no `else` branch, so a new page
  cannot silently fall through to a default navigator.
- The mounted navigator is a pure function of `state.navigation.currentPage`.
- `NavigatorKind.None` yields `activeNavigator == null`, `NavigatorLayout.Hidden`, and no resize handle.
- `spec.resizable` is true only for Workspace.
- Page-owned navigators are separate value objects that never read `WorkspaceNavigatorState`.
- The More overflow and the account/profile menu never replace `currentPage`; the Thread context panel
  is a `NavigationState` overlay that also does not replace `currentPage`.
- The drawer toggle belongs to the current page's navigator (`HarnessApp.onToggleSidebar` switches on
  `pageShellSpec(page).navigator`).

#### Fixed in batch 0 (was a defect)

`MainSurface` to `Titlebar` drew the sidebar toggle unconditionally, and `AppBottomBar` inserted the
`工作区` drawer entry unconditionally, so a `NavigatorKind.None` page (`PageId.Account`) still showed a
hamburger at desktop width and a phantom navigator entry at compact width. Fixed with
`PageShellSpec.mountsNavigator`, `showNavigatorToggle` and `showNavigatorEntry`. Captures:
`docs/desktop-ui/captures/rail/account-no-navigator-{light,dark}-{1440x900,600x800}.png`.

#### Still to compare against a Codex capture

- `Projects` / `Project`: app.asar shows a project index with expandable `Project > chat/thread`
  children, but not what the left column holds on that route (batch B: still C).
- `Hosts`: reached from the account menu in this client; no app.asar navigator chunk (batch B: still C).
- Per-page collapse / resize affordances for the page-specific navigators: no token found, so this
  client keeps them fixed-width and non-resizable by inference.

### Evidence levels used

- **app.asar**: a bundle name, route literal or control id extracted from the installed bundle.
- **user capture**: a Codex window screenshot supplied by the user.
- **structural inference**: Harness only; listed as pending a capture.

Batch B extends this list with the revision table, the in-repo capture manifest and the A/B/C
classification above.
