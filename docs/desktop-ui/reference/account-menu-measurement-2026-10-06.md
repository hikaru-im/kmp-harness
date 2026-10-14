# Account / Profile menu measurement (batch 2)

Date: 2026-10-06. Scope: the rail avatar's Codex Profile Dropdown (identity header, Host, 设置,
个人资料, separator, 退出登录, and the signed-out 使用 ChatGPT 登录 row). This batch does not touch
the Workspace Navigator, the app Rail geometry, the BottomBar, the page-to-navigator mapping, or the
titlebar.

## Sources

| Field | Value |
| --- | --- |
| Codex reference | `app.asar` menu primitive (`wu` / `cC`) and its tokens, extracted to `/tmp/codex-asar/`; no same-state capture of the account menu itself exists |
| Harness captures | `docs/desktop-ui/captures/rail/account-menu-light-1440x900.png`, `account-menu-dark-1440x900.png`, `account-menu-signed-out-light-1440x900.png` and their `-crop.png` bottom-left crops |
| Producer | `ScreenshotTest.renderAccountMenuStates` over `AccountMenuShell` (the overlay actually open over `PageId.Home`) |
| Scale | 1 screenshot px = 1 dp (calibrated in `rail-measurement-2026-10-05.md`) |

## Structure now rendered

```text
identity   avatar(24) + account name + "本地 · 已连接"        48
Host       host glyph + Host                                 36
设置       gear glyph + 设置                                  36
个人资料   person glyph + 个人资料                            36
----------  separator hairline  ----------                   4 + 1 + 4
退出登录   sign-out glyph + 退出登录                          36
```

Signed out, the tail after 设置 is a single `使用 ChatGPT 登录` row (enabled only when `canLogin`),
with no separator. The rows come from `accountMenuItems(state)` in `AppShellState.kt`, so the
signed-in/signed-out structure is a pure, assertable list.

## Measured geometry (Codex token vs Harness render)

| Metric | Codex token (app.asar) | Harness render | Delta |
| --- | ---: | ---: | --- |
| Panel width | `min-w-[180px]`, `--container-xs` 320 max | 264 (`MenuWidth`) | inside 180-320 |
| Panel radius | 16 (`rounded-2xl`) | 16 | 0 |
| Panel padding | 4 (`--app-menu-gutter` = `--spacing`) | 4 | 0 |
| Item height | 36 (`--app-menu-item-height`) | 36 | 0 |
| Item radius | 12 (`rounded-xl`) | 12 | 0 |
| Item padding | x8 / y5 (`--padding-row-x` / `--padding-row-y`) | x8 / y5 | 0 |
| Item icon box | 16 (`--icon-xs`) | 16 | 0 |
| Icon-label gap | 6 (`--spacing-menu-item-content`) | 6 | 0 |
| Separator block | 4 (`--app-menu-separator-gutter`) | 4 + 1 px rule + 4 | 0 |
| Dropdown shadow | `0 0 0 .5px border, 0px 8px 16px -4px #0000001f` (`--shadow-dropdown-overlay` = `--shadow-xl-spread`) | blur 16 / offset-y 8 / spread -4 / `#0000001f` (`Modifier.dropShadow`) | 0 |
| Identity header height | not separately tokenised (shared menu primitive) | 48 | allowed +/-4 dp |

## Measured render (dark capture, bottom-left crop)

| Metric | Value |
| --- | ---: |
| Panel bbox | x 60..323 (w 264), y 683..891 (h 209) |
| Left inset | 60 = rail 52 + Spacing.Two 8 |
| Bottom inset | 8 (`Spacing.Two`) |
| Header avatar | 24 x 24, centred at y 710.5 |
| Item centres | Host 752.5, 设置 788.5, 个人资料 824.5, 退出登录 869.5 |
| Item pitch | 36 |
| Separator hairline | y 847, i.e. Host centre + 2 x 36 + the separator block |
| Panel height identity | 4 + 48 + 36 x 4 + 9 + 4 = 209 |

The light panel is `#FFFFFF` on the `#FFFFFF` canvas, so it reads only by its 0.5 dp `BorderSubtle`
hairline and its dropdown shadow. The geometry above was measured on the dark palette, where
`SurfaceElevated` `#282828` contrasts with the `#171717` canvas.

The shadow is measured on the light capture, where it reads against the white canvas. Below the
panel's bottom edge (y 891) the canvas falls to `229` at y 892 and relaxes back to `238` by y 899;
right of the panel the edge column (x 324) reads `239` and returns to the canvas over ~10 px. The
stronger fall below the panel is the `8 px` y-offset in `--shadow-xl`; the `.5 px` ring in
`--shadow-xl-spread` is the existing hairline border rather than a second ring.

## Not asserted

- No Codex capture of this exact menu in this exact state exists, so this is a component-level
  comparison of the shared menu primitive's tokens against the rendered geometry, not a whole-window
  pixel diff.
- The profile dropdown, the audio-attachment menu and the thread-summary menu all use the same
  `wu` / `cC` primitive; the identity header is per-instance content with no CSS height, so its height
  is the implementation choice of 48 dp (allowed error +/-4 dp) rather than a measured token.
- Opening the menu never changes `currentPage`: it flips `AccountMenuState.isOpen`, and the overlay
  anchors beside the rail avatar at bottom-start. That invariant is asserted by `AccountMenuStateTest`
  and shown by the captures (Home is behind an open menu), not by a pixel diff.
