# Rail measurement from the Codex window screenshot

Date: 2026-10-05. Scope: the narrow app rail only. This note exists so the rail tokens are not read
off the old `README` self-assessment (Rail = 64dp), which was this project's own value and not a
Codex measurement.

## Source

| Field | Value |
| --- | --- |
| Image | user-provided live Codex window screenshot, `截屏2026-10-05 上午9.18.30.png` |
| Size | 2560x1440 px |
| State | light, workspace navigator expanded, Home selected |

This is a screenshot measurement. It is not a re-extraction from `app.asar` and it is not a
two-machine golden diff.

## Scale: is 1 screenshot px equal to 1 dp?

The image is a scaled display capture, so pixel counts cannot be converted to dp before the scale is
fixed. Three features of the window content were matched against Codex CSS tokens measured earlier
from `app.asar` (`app-asar-geometry-evidence.md`):

| Feature | Measured in screenshot | Codex token | Implied scale |
| --- | ---: | ---: | ---: |
| Titlebar height (window top to content top, column x=1800) | 44 px | `--height-titlebar` 44 px | 1.00 |
| Rail selected highlight height | 34-36 px | `--height-token-row` 36 px | ~1.00 |
| Rail icon glyph height inside its box | 16-17 px | `--icon-leading-size` 20 px | ~0.85 |

The glyph row is lower than 1.0 because the glyph is inset inside its 20 px box, which is the
expected direction. Two independent content features land on 1.0, so **1 screenshot px = 1 dp for the
window content, and the rail numbers below read directly as dp**.

Limits of this proof:

- The macOS menu bar in the same capture measures 30 px, while a 1x menu bar is 24 px. The menu bar
  is therefore not a usable ruler here and was not used; the scale is calibrated from the window
  content instead.
- Only one screenshot and one state are available. The dp values below are single-sample
  measurements rounded to the nearest whole dp, not averages.

## Measured rail geometry (dp)

| Metric | Value | How it was read |
| --- | ---: | --- |
| Rail width | 52 | left edge 0 to the 1 px border at x=51; navigator background starts at x=52 |
| Rail horizontal inset | 8 | selected highlight spans x 8..43 |
| Selected highlight box | 36 x 34 (radius ~8) | highlight extent, matches `--height-token-row` |
| Icon centre x | 25.5 | centre of the 8..43 highlight |
| Row pitch | 44 | icon row bands centre-to-centre, uniform for every item |
| Icon box | 20 | `--icon-leading-size`; glyphs render 16-17 px inside it |
| Rail surface | #F5F5F5 (245) | flat area at x=8, y=700 |
| Navigator surface | #FCFCFC (252) | x=200, y=700 |
| Canvas | #FFFFFF (255) | x=1500, y=700 |
| Selected highlight fill | #EAEAEA (234) | inside the highlight, away from the glyph |
| Selected glyph | #202224 (32,34,36) | darkest pixel of the selected Home glyph |
| Unselected glyph | #8C8D8E (140,141,142) | darkest pixel of the Clock glyph |
| Group divider | y=346, x 14..37 (24 wide) | single dark row between icon groups |
| Bottom avatar | 24 circle, centre (25.5, ~1323.5) | bounding box of the saturated avatar circle |

## Structure observed

Top-to-bottom: six destination icons, a divider, five more destination icons, a long empty gap, then
the account avatar anchored at the bottom. The rail spans the full window height, including the
titlebar strip: the macOS traffic lights sit at y 45-58 inside the rail's own x range (x 8-48), so the
window's left column is one continuous rail surface with the traffic lights drawn over its top. The
navigator is a separate region to its right and is user-sized (288 px in this capture).
The split is 52 + 288 = 340, so the earlier "left navigation is ONE column of 275" reading does not
describe this window; the rail is a distinct fixed column and the navigator is separate.

Not asserted here: the meaning of each Codex icon, and which group a destination belongs to. Those
belong to the navigation plan, not to rail geometry.

## Source confirmation from app.asar (resolves the `app-asar-geometry-evidence.md` conflict)

`app-asar-geometry-evidence.md` concluded "left navigation is ONE column, not rail + navigator" from
`--spacing-token-sidebar` and the absence of a rail class in the extracted CSS chunks. That conclusion
was wrong for this archive: the rail exists under a different name that the earlier extraction did not
include. Both notes were taken from the same archive, so the conflict is an extraction gap, not a
version difference.

| Field | Value |
| --- | --- |
| Archive | `/Applications/ChatGPT.app/Contents/Resources/app.asar` |
| App version at re-check | `26.930.31730` (`CFBundleShortVersionString`), build `12947` |
| SHA-256 at re-check | `64023959c7c3a4c19022c44f3e8d4b3e4010cefb6e2ea1edce0b535fd417776e` |

Identical to the value recorded in `app-asar-geometry-evidence.md`, so the two notes describe one
archive and the screenshot is a capture of this build.

The rail container in the bundle (minified React, read with `rg -a`):

```text
group/sidebar-rail relative flex min-h-0 shrink-0 flex-col items-center gap-2 px-2 pb-2 select-none
  browser:w-13 browser:gap-0 browser:px-0 browser:pt-0
  browser:[--height-token-nav-row:36px] browser:[--height-token-row:36px]
  browser:[--padding-row-cell-x:8px] browser:[--padding-row-x:8px]
  browser:max-md:border-e browser:max-md:border-subtle browser:max-md:dark:border-default
pt-[calc((var(--sidebar-navigation-header-height, calc(var(--spacing)*10)) - var(--spacing)*9)/2)]
```

and the rail's end-edge hairline:

```text
pointer-events-none absolute inset-y-0 end-0 z-10 border-e-hairline border-default
```

The scroll region and the group rule:

```text
vertical-scroll-fade-mask hide-scrollbar -m-0.5 min-h-0 flex-1 scroll-py-0.5 items-center
  overflow-y-auto p-0.5 browser:m-0 browser:mt-2 browser:w-full browser:items-stretch
  browser:px-row-x browser:pt-0
w-6 border-t border-default browser:self-center   // browser:my-2
```

`browser:w-13` is 13 x 0.25rem = 3.25rem = 52px and `px-2` is 2 x 0.25rem = 8px, which reproduces the
52px rail with a 36px item and an 8px inset that the screenshot measures below. `gap-2` is the 8px gap
that makes the 44px pitch, `pb-2` is the 8px bottom inset, and the group rule is `w-6` = 24px.

## Independent pixel re-measurement (Pillow, 2560x1440 capture)

Re-measured from the raw PNG rather than from a scaled view, so these supersede the rounded values in
the tables above where they differ.

| Metric | Measured | Notes |
| --- | ---: | --- |
| Rail background | x 0-50, `#F5F5F5` (245) | soft gradient 246 -> 240 toward the end edge |
| Rail end border | x 51, 1px, `#EBEBEB` (235) | rail content 51px + 1px border = 52px total |
| Navigator surface | x 52+, `#FCFCFC` (252) | |
| Canvas | `#FFFFFF` (255) | |
| Selected highlight | x 8-43, y 82-117, 36x36 | fill `#EAEAEA` (234) |
| Selected glyph | `#202224` (32,34,36) | darkest pixel of the filled Home |
| Unselected glyph | `#8C8D8E` (140,141,142) | darkest pixel of the Clock |
| Row pitch | 44px | uniform for all 11 destinations |
| Group rule | y 346, x 14-37, 24x1 | colour (229,229,229) |
| Account avatar | x 14-37, y 1312-1335, 24x24 | fill (230,158,62); window bottom y 1349 |
| Glyph extent | 16x16 | every destination glyph: x 18-33, i.e. 0.8 of the 20px icon box |
| Glyph stroke | ~1.25-1.4px | Clock edge runs at y 143 measure 1-2px |

Two deltas between the capture and the bundle tokens are capture artifacts, not token errors, and the
implementation keeps the bundle value:

- Rail surface: capture 245 vs `--gray-fixed-75` `#f3f3f3` (243). The capture is a scaled-display
  screenshot whose window surface is translucent, so absolute greys shift by 1-3 units. The rail keeps
  the bundle value; the observed delta is 2/255.
- Selected fill: capture 234 vs `--gray-fixed-100` `#ededed` (237). Same cause and same direction.

The end border is the one place where the capture and the token disagree in strength (observed delta 9
over the rail, `border-subtle` composites to about 11 over the rail surface). The bundle names it
`border-e-hairline border-default`; the composited `border-subtle` lands at 234 against the capture's
235 at that pixel, so the implementation uses `BorderSubtle`.

## What this rail does and does not fix

Aligned to the capture: rail width, surface, 8px inset, 44px pitch, 36px item with a 10px corner
(`--radius-token-row: 10px`), the selected highlight plus solid selected glyph, the 24px group rule
between consecutive groups, the end-edge hairline, 16px glyphs at a ~1.3px stroke inside the 20px icon
box, and the 24px account chip anchored at the bottom with its initials.

Still open, and only verifiable in a real native window:

- The rail's top offset. The capture's first item starts 8px below the titlebar strip, but the capture
  draws the titlebar inside the window (traffic lights over the rail) while the Harness window uses the
  OS titlebar above the Compose surface. The measured 8px top inset matches, but the absolute position
  needs the native capture.
- Window surface translucency/vibrancy, which the app does not reproduce.
- Icon artwork. The vector system matches the capture on extent, stroke weight, fill-on-select and
  rhythm; the individual glyph shapes are this project's own drawings. Measured against the capture,
  the house and folder silhouettes differ in roof rounding and body aspect ratio.

## Captures produced by this pass

| File | State |
| --- | --- |
| `captures/rail/rail-navigator-expanded-1440x900.png` | Rail plus expanded navigator; rail stays mounted |
| `captures/rail/rail-navigator-collapsed-1440x900.png` | Rail with the navigator collapsed; rail unchanged |
| `captures/rail/rail-component-sheet-1440x900.png` | The rail alone, once per selected destination |

Rendered by `ScreenshotTest.renderRailNavigatorStates` and `ScreenshotTest.renderRailComponentSheet` in
`apps/jvm-app/test/im/hikaru/harness/desktop/ScreenshotTest.kt` (Compose `ImageComposeScene`, density 1).

## Rail entries (final decision, 2026-10-06)
> **Superseded by「Rail re-specification (2026-10-13)」at the end of this file.** The slot set,
> the Images slot and the More overflow described below were replaced in that pass.

The user fixed the rail at the Codex slot set: Home / Recent / Library / Images / Plugins / More, plus
the bottom account chip. The capture's rail carries eleven destinations in two groups (six, rule,
five) plus the account chip; this client renders the six the user chose rather than every page it
implements.

| Group | Slots | Page |
| --- | --- | --- |
| Primary (above the rule) | Home, Recent | `PageId.Home`, `PageId.RecentTasks` |
| Secondary (below the rule) | Library, Images, Plugins, More | `PageId.Projects`/`Project` (Library), `PageId.Images`, `PageId.Plugins`/`PluginDetail`, `PageId.Account` (More) |
| Bottom chip | Account | `PageId.Account` |

Slot mapping and rationale:

- Library carries the Project workspace route (`PageId.Projects` / `PageId.Project`). Codex's Library
  is a file product this client does not implement, and Projects is the matching workspace surface, so
  the slot keeps the Codex glyph but opens the Project pages.
- Images opens a new `PageId.Images` placeholder page. The glyph is reproduced from
  `sidebar-images-icon` (see `codex-rail-icons.md`); the gallery behind it is not built yet.
- More is a live entry that opens the account panel, so the rail has no dead button.
- Host and Settings moved off the rail into the account panel. They keep the existing
  `PageId.Hosts` / `PageId.Settings` routes and state.

This supersedes the intermediate pass that rendered Projects/Plugins/Hosts/Settings in the Secondary
group; that pass added Host/Settings to the rail, and this decision reverses it. That pass also left
Library and Images absent because their pages did not exist; both now have slots, with Images backed
by a placeholder page.

Divider token: the capture's rule reads as delta 16 over the rail surface (229 on 245), while the rail's
end hairline reads as delta 9 (235). The bundle names both `border-default`, but a hairline is a
sub-pixel rule that blends in the capture. The implementation therefore uses `HarnessTokens.Colors.Border`
(alpha-10, delta 19 over the rail) for the group rule and `BorderSubtle` (delta 9) for the end hairline,
which reproduces both measured strengths.

## Rail re-verification (2026-10-06)

Scope: the fixed App Rail only. This pass re-measured the rendered rail against the Codex tokens and
capture instead of re-reading the earlier self-assessment. The account/profile dropdown, the Workspace
Navigator, `pageShellSpec`, the BottomBar and the native titlebar were not touched.

### Method

Pixel measurements come from `ScreenshotTest` renders at density 1 (`ImageComposeScene`), read back
with a PNG decoder: `rail-component-sheet-{light,dark}-1440x900.png` for six rails side by side, and
`rail-navigator-expanded-1440x900.png` for the rail inside the shell. 1 render px = 1 dp.

### Geometry: measured vs Codex (0 deltas)

| Metric | Codex | Harness render | Delta | Source |
| --- | ---: | ---: | --- | --- |
| Rail width | 52 (51 content + 1 hairline) | 52 (x 0..51) | 0 | container `browser:w-13` (13 x 4px) + end hairline |
| Horizontal inset | 8 | 8 (highlight x 8..43) | 0 | container `px-2` |
| Item box | 36 | 36 (items at y 8..43, 52..87) | 0 | `--height-token-nav-row` 36px |
| Row pitch | 44 | 44 | 0 | 36 + `gap-2` 8 |
| Icon box / glyph extent | 20 / 16 | 20 / 16 (glyph x 18..33, y 62..77) | 0 | `--icon-leading-size` 20px |
| Divider rule | 24 x 1 | 24 x 1 (x 14..37 at y 96) | 0 | rail group rule `w-6 border-t` |
| Account chip | 24 | 24 (x 14..37, y 868..891) | 0 | bottom identity chip |
| Chip bottom inset | 8 | 8 (rail resumes y 892..899) | 0 | container `pb-2` |

### Colour: measured vs Codex tokens (0 deltas after capture correction)

| Element | Codex token | Harness render | Delta | Note |
| --- | --- | ---: | --- | --- |
| Rail surface | `--gray-fixed-75` `#f3f3f3` | 243 | 0 | capture reads 245 (translucent window) |
| End hairline | `--alpha-05` `#7f7f7f14` | 234 | 0 | capture reads 235 |
| Selected fill | `--gray-fixed-100` `#ededed` | 237 | 0 | capture reads 234 |
| Divider rule | `--color-border` = `--alpha-10` `#7f7f7f29` | 224 | 0 | capture reads 229 (1px rule on a translucent capture) |
| Selected glyph | `--gray-fixed-1000` `#0d0d0d` | 13 | 0 | capture darkest 32: a ~1.3px stroke never reaches full ink |
| Unselected glyph | `--gray-fixed-400` `#8f8f8f` | 143 | 0 | capture darkest 141 |
| Account chip | account colour | `#e69e3e` | 0 | capture (230,158,62) |

Every rail colour resolves to a Codex token exactly: `HarnessTokens.Colors.Border` = `#7f7f7f29` =
`--alpha-10`, `BorderSubtle` = `#7f7f7f14` = `--alpha-05`, `SurfaceSecondary` = `--gray-fixed-75`,
`SurfaceSelected` = `--gray-fixed-100`, `Text` = `--gray-fixed-1000`, `TextDisabled` = `--gray-fixed-400`.
This supersedes the earlier strength-based rationale for the divider: the bundle's `border-default`
resolves to `--color-border` = `--alpha-10`, so the 224 render is the token value and the capture's 229
is the translucent scaled display, not token drift. No rail colour changes in this pass.

### Slots and selection

Order and grouping are unchanged and asserted in
`RailModelTest.fixedRailKeepsTheSixCodexSlotsAndTheAccountChip`: Primary Home / Recent, the 24px rule,
Secondary Library / Images / Plugins / More, then the account chip. Selection mapping
(Library -> Projects/Project, Images -> Images, Plugins -> Plugins/PluginDetail) is unchanged; More and
the account chip never draw a selected highlight.

### More: resolved to an overflow menu

Before this pass `RailDestination.More` mapped to `null` in `railDestinationPage` and `HarnessApp`'s
`onNavSelect` produced no action, so the More button was dead. Codex's rail More is an overflow
*destination* menu, not a page: `extracted-app-initial-f9b16fbf8fc7.js` `n6o` renders `wu.Item` /
`wu.Separator` (the same menu primitive as the profile dropdown) over `availableDestinations` plus an
optional customize-sidebar action, and the trigger maps `sidebar-more` -> `OZr`.

This pass adds a minimal, independent overflow:

- `RailOverflowState` (open/close) and `railOverflowItems()` in `RailDestinations.kt`. It is separate
  from `AccountMenuState`: the More button owns the overflow, the bottom avatar owns the account menu,
  and opening one closes the other.
- The two entries are the destinations this client keeps off the rail, Host and Settings, routed
  through their existing `PageId.Hosts` / `PageId.Settings()` pages. Codex's explore list and
  customize-sidebar footer are not implemented (recorded, not faked).
- The menu reuses the account dropdown's geometry and `--shadow-dropdown-overlay`, and anchors beside
  the More button (the trigger reports its top edge through `onGloballyPositioned`).

Captures: `rail-more-open-{light,dark}-1440x900.png` and their `-crop.png` top-left crops.

### Home glyph geometry fix (2026-10-06)

Scope: the Home glyph only, outline (`Home`) and solid (`HomeFilled`). No other glyph, no rail token,
and no Account/Profile, More, Navigator, BottomBar or titlebar code changed.

`Home` has no `app.asar` icon chunk (see `codex-rail-icons.md`), so its bounds are authored directly
rather than copied from the bundle. Before this pass the Home artwork sat on a narrower grid: on the
density-1 component sheet it covered 14x14 px inside the 20px icon box, where every other rail glyph
covers 16x16 px. The fix pins both variants to the shared `HomeGlyphGrid` extent 2.5..17.5, the same
grid the Clock/Library/Images/Plugins glyphs use, and draws the outline and the solid fill from one
silhouette so selection does not change the shape.

Grid to render: `CodexGrid.at(c) = (c + 1.5) * artwork / 23` with `RailIconScale` 1.15 over the 20px
box, so `artwork = 23px` and one grid unit is 1px. `at(2.5) = 4px`, `at(17.5) = 19px`, a 15px grid
span that is exactly 16/20 of the box, the Codex glyph extent. The stroke is
`RailIconStrokeRatio 0.055 * 23px = 1.265px`, so the stroked outline spans `15 + 1.265 = 16.265px`,
inside the 16.0-16.6px rail target band (asserted in `HomeGlyphGridTest`).

Before/after bounding boxes from `rail-component-sheet-{light,dark}-1440x900.png` (density 1, 20px icon
box; Home is the first tile, box x 18..37, y 58..77 in the sheet):

| Glyph | Before | After | Target |
| --- | --- | --- | ---: |
| Home solid `HomeFilled` | 14x14 at x 19..32, y 19..32 | 16x16 at x 18..33, y 18..33 | 16 |
| Home outline `Home` | 16x14 at x 18..33, y 19..32 | 16x16 at x 18..33, y 18..33 | 16 |

The after-boxes are read at threshold 220 for the solid and 170 for the outline. At the other threshold
the solid reads 16x15 and the outline 16x17 (one extra antialiased row), because the 1.265px stroke edge
is sub-pixel; the headline 16x16 is the drawn extent, not the AA fringe. The glyph centre is preserved
at render (25.5, 25.5), the same centre as the 8..43 selected highlight.

Only Home changed on the sheet: Recent, Library, Images, Plugins, More and the 24x24 account chip keep
their exact bounding boxes, and no non-Home pixel moved. This is a screenshot-level comparison against
the rail capture and component sheet; there is no bundled Home artwork to diff against.

Regenerated captures: `captures/rail/rail-component-sheet-{light,dark}-1440x900.png` and
`rail-navigator-{expanded,collapsed}-{light,dark}-1440x900.png`.

### Not asserted

- The rail top offset still needs a native window capture (the OS titlebar sits above the Compose
  surface), the same caveat as the 2026-10-05 pass.
- Codex's rail carries eleven destinations in two groups; this client pins six by decision, so the slot
  count and the More contents are this client's own, not a Codex capture match.
- The BottomBar (compact width) keeps its own reduced slot set (`bottomBarItems`, `maxDestinations = 3`):
  Home / Recent / Projects plus the account chip. This pass does not change it, so no rail slot decision
  leaks into the compact layout.


## Rail re-specification (2026-10-13)

Supersedes the 2026-10-06 slot decision. The rail was rebuilt to the live Codex contract with the
out-of-scope destinations dropped; the compact bottom bar was removed. Source: live Codex rail DOM
(22.10.13) plus the same geometry pass as above.

### Slots

| Group | Slots | Page |
| --- | --- | --- |
| First (above the rule) | 主页 · 定时任务 · 资料库 · 插件 · 探索 | `PageId.Home`, `PageId.ScheduledTasks`, `PageId.Library`, `PageId.Plugins`; 探索 opens the pin menu |
| Second (below the rule) | the destinations pinned from 探索 | pinned only; `项目` opens `PageId.Projects` / `Project` |
| Bottom chip | 账号 | account menu |

- The rail is mounted at **every** width. There is no compact bottom bar and no `ShellPresentation` /
  `bottomBarItems`; below 640dp (`NavigatorFloatBelow`) the navigator floats instead of pushing.
- `最近` and `更多` no longer have rail slots. Host and Settings stay account-menu rows.
- Out of scope and not offered anywhere: 图像 · 站点 · 地图 · GPT · 代码审查.

### 探索 behaviour (measured from the live rail)

- Glyph: **three flat dots** (`svg[viewBox="0 0 20 20"]` on the 探索 button) — three filled circles of
  radius ~1.5 at y=10, centred on x = 4.17 / 10 / 15.83.
- 探索 opens its pin menu **on hover** and draws **no tip**. Every other slot shows a hover tip, and
  that tip opens to the **right** of the item (`HarnessPopoverSide.End`), not above it.
- The menu is a `role="dialog"` panel beside the rail, ~228 wide with its top at the 探索 button. Its
  rows are `div[role="group"][data-explore-row]`: a destination button (leading 16/20px glyph + label)
  plus a trailing **20×20 transparent pill pin button** (`aria-label` `从侧边栏取消置顶` while pinned).
  Clicking the row navigates; only the pin button toggles the pin.
- This client makes the whole row one interactive surface so the ripple and the hover fill span the
  pin; the pin is nested on top and consumes its own taps. Row hover is a light fill plus a 1px
  outline.

### Icon states

Every destination carries an outline (unselected) and a solid (selected) variant; `filledVariant()`
maps them.

| Slot | Outline | Solid |
| --- | --- | --- |
| 主页 | `Home` | `HomeFilled` |
| 定时任务 | `Clock` | `ClockFilled` |
| 资料库 | `Library` | `LibraryFilled` |
| 插件 | `Plug` | `PlugFilled` |
| 项目 (pinned) | `Projects` | `ProjectsFilled` |
| 探索 | `Explore` (three dots) | — (never selected; it is a menu) |
| 图钉 | `Pushpin` | `PushpinFilled` |

`Projects` and `Pushpin` are transcribed from the live 探索 menu row's real 16×16 paths. Codex animates
outline↔solid inside one Lottie file, so a static solid path cannot be captured; `LibraryFilled`,
`ProjectsFilled` and `PushpinFilled` are this project's own solid treatments, like the earlier
`ClockFilled` / `FolderFilled` / `PlugFilled`.

### Geometry

Unchanged from the 2026-10-06 pass: rail width 52 (51 + 1px hairline), 8px inset, 36px item, 10px
corner, 44px pitch, 24px group rule, 24px account chip, 20px icon box, 16px glyph extent. The
explore popup sits beside the rail with its top at the 探索 button.

### Not asserted

- The stacked outline/solid artwork is this project's treatment except for the two paths transcribed
  from the live menu; the reference animates inside Lottie and no static solid path exists to diff.
- The native-window rail top offset and the reference's popup offset (left edge over the rail by a few
  px) are still only reproducible in a native capture.
