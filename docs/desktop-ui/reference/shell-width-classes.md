# Shell width classes

Date: 2026-10-05. Scope: how the shared Desktop shell switches between the fixed App Rail and the
compact bottom bar. This records the implemented rule and the evidence behind it. It is not a Codex
pixel measurement.

> **Superseded (2026-10-13): the compact bottom bar was removed.** The rail is now mounted at every
> width and the shell no longer has `ShellPresentation`, `CompactShellBreakpoint`, `bottomBarItems` or
> `AppBottomBar`. The sections below record the 2026-10-05 decision; see「Width classes after the rail
> rework (2026-10-13)」at the end for the current rule.

## Breakpoint

| Field | Value | Source |
| --- | --- | --- |
| Rail at or above | 640 dp | decided in this pass |
| Bottom bar below | 640 dp | decided in this pass |
| Reference compact-body token | `--thread-content-compact-max-width: 40rem = 640px` | `app-asar-geometry-evidence.md` |

The collected Codex Desktop evidence has no measured rail-to-bottom-bar breakpoint: Codex Desktop
always shows the rail, and a bottom navigation belongs to the phone clients. The value is therefore
this project's decision, anchored to the reference's own compact-body width (640px) so the shell turns
compact at the width where the reference already switches its body.

Every Desktop reference size stays above the breakpoint, so the three captured sizes keep the rail:
1024x768, 1280x800, 1440x900.

## State matrix

| Window width | Rail | Workspace Navigator | Bottom bar |
| --- | --- | --- | --- |
| >= 640 dp | Fixed column, full height, always mounted | Expanded: pushed beside the rail while the main surface keeps 320 dp, otherwise a drawer above the content. Collapsed: hidden. | absent |
| < 640 dp | absent | Never pushed; a drawer above the content, opened from the bottom bar entry or the title-strip toggle. | replaces the rail |

Rail-item sets:

- Rail: every destination plus the account chip (Primary group above the group rule, Secondary below).
- Bottom bar: the primary destinations, then secondary destinations up to three in total, then the
  account chip (Home / Recent / Projects / Account for the current catalog), plus a leading
  `工作区` entry that opens the navigator drawer.

## Traffic-light clearance

The window title strip is 44 dp and macOS draws the traffic lights over its top-left corner
(`x 8..48` in the live Codex window, see `rail-measurement-2026-10-05.md`). The rail column occupies
that corner at normal widths. When the bottom bar replaces the rail, the title row reserves
`RailWidth` (52 dp) of leading padding so the toggle and the title never slide under the window buttons.

## Verification

`NavigatorLayoutTest` covers the rules above (`ShellPresentationTest` was removed with the bottom bar).
Off-screen captures live in
`docs/desktop-ui/captures/rail/rail-bottombar-600x800.png` (navigator collapsed) and
`rail-bottombar-navigator-600x800.png` (drawer open). They render app content only, so there is no
native window capture of the compact shell yet and no pixel alignment with any reference is claimed.

## Thread panel rule (source update: 2026-10-08)

The earlier 600 dp observation above was made before the Thread panel integration. Current
`AppShell` computes the panel layout from the main area remaining after the rail and any pushed
navigator. When that area is below 672 dp, a Thread panel uses an overlay and a dismissible scrim;
otherwise its width is clamped to leave at least 353 dp for the main surface. The rule applies only
when `contextPanelWidthMode` is supplied, so it does not redefine every project context panel.

A window's outer width alone does not establish whether the panel is beside the content: navigator
visibility and width also affect the result. At 600 dp the compact shell has no rail, the navigator
is never pushed, and a mounted Thread panel uses the overlay. The overlay and scrim begin below
the main title strip, and exclude the rail and any pushed navigator. With the default 44 dp title
strip and its 1 dp divider, the overlay starts at y=45 dp.

### Full HarnessApp verification (2026-10-08)

| Viewport | Navigator | Remaining main width | Panel when open |
| --- | --- | --- | --- |
| 1440x900 | pushed, 275 dp | 1113 dp | beside content |
| 1024x768 | pushed, 275 dp | 697 dp | beside content |
| 1024x768 | pushed, pointer-resized to 400 dp | 572 dp | overlay |
| 600x800 | collapsed, compact shell | 600 dp | overlay |

Each row passed in both light and dark palettes: `HarnessAppThreadPanelsShellTest` 8/8. Real pointer
actions verify panel tabs, close/reopen, exposed scrim, navigator access, and conversation switches.
The 1024 dp resized case measures navigator x=52..452, scrim x=452..1024 and panel x=704..1024;
the title actions remain above the scrim. The 600 dp panel is 320 dp wide and leaves an exposed scrim.

The associated draft-isolation tests passed 6/6, giving 14/14 full-shell tests. Captures comprise
80 PNGs with 80 geometry files under `docs/desktop-ui/verification/thread-shell-2026-10-08/`.
These are off-screen production-shell tests with controlled data; they do not establish original
Codex measurements, native-window behavior, remote transport behavior, or pixel parity.


## Width classes after the rail rework (2026-10-13)

The compact bottom bar is gone. The fixed App Rail is mounted at every width (`rail-measurement-2026-10-05.md`,
「Rail re-specification」), so width now only changes the navigator, not the navigation.

| Window width | Rail | Workspace Navigator |
| --- | --- | --- |
| >= 640 dp | Fixed column, full height, always mounted | Expanded: pushed beside the rail while the main surface keeps 320 dp. Collapsed: hidden. |
| < 640 dp | Fixed column, full height, always mounted | Expanded: a drawer over the content (never pushed), opened from the title-strip toggle. Collapsed: hidden. |

- Breakpoint: `NavigatorFloatBelow = 640.dp` in `NavigatorLayout.kt`, the same 640 dp value the old
  bottom bar used, now read as the width below which an expanded navigator floats instead of pushing.
- There is no bottom-bar entry and no `工作区` bottom-bar item; the navigator's only shell affordance is
  the title-strip toggle, which every navigator page keeps.
- The rail's own slot set is in `rail-measurement-2026-10-05.md`; it does not change with width, and no
  rail slot decision depends on the compact layout anymore.
