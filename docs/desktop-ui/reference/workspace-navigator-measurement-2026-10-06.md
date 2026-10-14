# Workspace Navigator measurement (batch 1)

Date: 2026-10-06. Scope: the Workspace Navigator only (header, New Chat, 项目 / 最近 sections, project
and recent rows). This batch does not touch the app Rail, the AccountMenu, the BottomBar, or the
page-to-navigator mapping.

## Sources

| Field | Value |
| --- | --- |
| Codex reference | user-provided live capture `截屏2026-10-05 上午9.18.30.png`, 2560x1440, light, Workspace Navigator expanded |
| Harness captures | `docs/desktop-ui/captures/rail/workspace-navigator-{expanded,collapsed,search-open}-{light,dark}-1440x900.png` and their `-crop.png` left-column crops (rail 52 + navigator 275) |
| Producer | `ScreenshotTest.renderWorkspaceNavigatorStates` over the fixed `CodexWorkspaceNavigatorFixture` |
| Scale | 1 screenshot px = 1 dp for the Codex window content (calibrated in `rail-measurement-2026-10-05.md`) |

The Codex Y values below are content-relative: the window content top is y=74 (30px macOS menu bar +
44px titlebar), which the Harness Compose surface starts at y=0.

## Structure now rendered

```text
header      workspace name + dropdown chevron        bell action   search action
new chat    pencil-square glyph + 新聊天
项目        section label
            folder glyph + project name
                child title (aligned under the project name, selected fill)
                child title
            folder glyph + project name
最近        section label
            recent title (no glyph)
            ...
```

Removed in this batch: the two-line "KMP Harness Desktop / 本地" subtitle, the header "+" action, the
permanent large search box, the 已固定 level, and the bottom navigator footer.

## Measured geometry

| Metric | Codex reference | Harness render | Delta |
| --- | ---: | ---: | --- |
| Rail width | 52 (0..51 + 1px border) | 52 | 0 |
| Navigator x span | 52..340 | 52..327 | width 288 vs 275 (see below) |
| Navigator surface | `#FCFCFC` (252) | `#F9F9F9` (249) | token `Surface` `#f9f9f9` |
| Rail surface | `#F5F5F5` (245) | `#F3F3F3` (243) | token `SurfaceSecondary` `#f3f3f3` |
| Canvas | `#FFFFFF` | `#FFFFFF` | 0 |
| Selected row fill | `#EAEAEA` (234) | `#EDEDED` (237) | token `SurfaceSelected` `#ededed` |
| Row box inset (x) | 60..331 | 60..318 | left 8 vs navigator; right edge follows navigator width |
| Row box radius | ~9 | 10 (`NavigatorRowRadius`) | capture-derived |
| Row height | ~30-31.5 | 30 (`--height-token-nav-row`) | see drift |
| Header height | ~50 | 50 (`NavigatorHeader`) | 0 |
| Row glyph | 12-14 | 16 box (`NavigatorIcon`) | capture glyph extent inside the box |

## Measured baselines (row centre Y, content-relative)

| Row | Codex | Harness | Delta |
| --- | ---: | ---: | ---: |
| header name | 25 | 24 | -1 |
| 新聊天 | 67.5 | 67 | -0.5 |
| 项目 label | 109.5 | 107 | -2.5 |
| project row | 142.5 | 141 | -1.5 |
| child 1 | 174 | 171 | -3 |
| child 2 (selected) | 204.5 | 200 | -4.5 |
| second project | 244 | 238 | -6 |
| 最近 label | 290.5 | 283 | -7.5 |
| recent 1 | 324 | 314 | -10 |

X alignment matches: header name x69, section labels x69, row glyph x70, row title x93, recent title
x69. The row text column is `NavigatorPaddingX (8) + NavigatorIcon (16) + Spacing.Two (8) = 32` from
the row box, which is why a child title lines up under a project name and a recent title lines up
under a project glyph.

The Y drift accumulates ~1.3px per row: the Codex capture pitch measures ~31.3px, while the
implementation keeps the app.asar token `--height-token-nav-row` = 30px. Header, section and row
structure match; the residual is a per-row pitch of one to two dp, reported rather than hidden.

## Not asserted

- No same-state, same-data Codex original exists, so this is a structured, component-level comparison
  (geometry, baselines, colours), not a whole-window pixel diff. The fixture titles are this
  project's own; only the layout skeleton is compared.
- Codex absolute greys shift 1-3 units in the translucent capture; the implementation keeps the
  app.asar tokens (`#f9f9f9` sidebar, `#ededed` selected).
- Navigator width: the Harness default is the app.asar preferred width 275px; the reference capture
  was user-resized to 288px, so the row right edge differs (318 vs 331) while the left inset matches.
- The header bell and search actions are visible; the bell has no notification panel behind it yet,
  and the search action opens an inline navigator row rather than a floating panel (no Codex
  search-open capture exists).

## Batch 2: component geometry comparison and the one-row pitch fix (2026-10-06)

Sources: Codex reference is the user capture `截屏2026-10-05 上午9.15.59.png` (340x1320 left-column crop
= rail 52 + navigator 288), the same window as `截屏2026-10-05 上午9.18.12.png` (2560x1440, expanded
light). Harness is `docs/desktop-ui/captures/rail/workspace-navigator-*-{light,dark}-1440x900[-crop].png`
regenerated in this batch. Scale: 1 capture px = 1 dp; Codex content-relative Y = crop Y - 44 (44px
titlebar). Row centres come from the ink profile of the navigator column; the row box from the selected
fill band.

### Measured vs Codex (expanded + light)

| Item | Codex | Harness | Delta | Verdict |
| --- | ---: | ---: | --- | --- |
| Rail / navigator boundary x | 52 | 52 | 0 | match |
| Navigator width | 288 | 275 | +13 | capture user-resized; token default 275 kept |
| Selected fill x span | 60..331 | 60..318 | right edge | right edge follows navigator width |
| Rail surface | 245 | 243 (`SurfaceSecondary`) | +2 | translucent-capture shift |
| Navigator surface | 252 | 249 (`Surface`) | +3 | translucent-capture shift |
| Selected row fill | 237 `#ededed` | 237 `#ededed` | 0 | token match |
| Selected row fill height | 30 | 30 | 0 | `--height-token-nav-row` 30 |
| Row box corner | 10 (`--radius-token-row`) | 10 | 0 | token match |
| Row pitch (recent list) | 30.875 | 30.875 | 0 | **fixed this batch** |
| Header centre (content-relative) | 24 | 24 | 0 | match |
| Header text x / new-chat icon x / section label x | 69 / 70 / 70 | 69 / 70 / 70 | 0 | `--padding-row-x` 8 |
| Project folder x / project text x / child text x / recent text x | 69 / 93 / 96 / 72 | 70 / 93 / 96 / 72 | 0 | match |

### Before / after: the only change

The navigator rows were stacked with no gap, so the pitch was 30.0 and the row centres drifted -1px per
row from Codex (header +0, ..., recent5 -10). After adding a 1px inter-row gap the pitch is 30.875 in
both and every row centre is within +/-1px of Codex, as a constant offset rather than an accumulating one.

| Row (content-relative) | Codex | Harness before | Harness after |
| --- | ---: | ---: | ---: |
| header | 24.0 | 24.0 | 24.0 |
| 新聊天 | 66.5 | 67.5 | 67.5 |
| 项目 label | 108.5 | 107.5 | 108.5 |
| project | 141.5 | 140.5 | 142.5 |
| child 1 | 173.0 | 171.0 | 174.0 |
| child 2 | 203.5 | 200.5 | 204.5 |
| second project | 243.5 | 238.5 | 243.5 |
| 最近 label | 290.0 | 283.5 | 289.5 |
| recent 1 | 323.0 | 317.0 | 324.0 |
| recent 5 | 446.5 | 436.5 | 447.5 |

app.asar evidence for the gap: `.sidebar-navigation{--height-token-nav-row:30px;--height-token-row:var(--height-token-nav-row);--padding-row-cell-x:8px;--padding-row-x:8px;--radius-token-row:10px}`
and the sidebar list containers render `flex flex-col gap-px` (`.gap-px{gap:1px}`). So a Codex row is
30px tall with a 1px inter-row gap, giving the measured 31px pitch.

### Code changes (Workspace Navigator only)

- `HarnessTokens.Control.NavigatorRowGap = 1.dp` (new token; used only by the Workspace Navigator).
- `AppShell` `WorkspaceNavigator`: the body column, the scroll list column and the `ProjectRow` column
  use `Arrangement.spacedBy(NavigatorRowGap)`.
- `HarnessTokens.Control.NavigatorRowRadius` doc now cites `--radius-token-row: 10px`; the value is
  unchanged.
- `HarnessTokensTest.workspaceNavigatorRowPitchKeepsTheCodexSidebarGap` pins 30 + 1 = 31.
- No Rail, AccountMenu, More, BottomBar, MainSurface or page-navigator code changed.

### Resolved from batch 1 (no change needed)

- Row corner ~9 vs 10: the bundle sets `--radius-token-row: 10px`; the ~9 read was capture noise.
- Row glyph 12-14 vs 16: 16 is the icon box; the drawn glyph is smaller inside it. Not a delta.
- Selected fill colour (237) and height (30): match exactly.

### Not asserted / unverified

- Navigator width: the Codex capture is user-resized to 288px; the 275px token default is kept, so the
  row right edge differs (318 vs 331). Not a token error.
- Surfaces: Codex reads 245/252 against tokens 243/249; treated as the translucent-window capture shift,
  the same direction as the rail note.
- Dark, collapsed, search-open and floating/compact states have no same-state Codex capture, so they are
  token/structure aligned only.
- The Codex reference's selected row is a recent item (content-relative ~446); the Harness fixture selects
  查看项目状态 (child + recent). That is fixture data, not geometry.
- The 新聊天 row height: the bundle implies 30px in the sidebar scope, but the measured centre delta is
  about 1px for both 30 and 36, so the height was left unchanged pending a cleaner capture.

