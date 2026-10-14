# Batch AL — plugin inventory page in a real macOS window

Date: 2026-10-11. Entry: `entry-al1`.

## Why this batch

`docs/desktop-ui/native-window-acceptance.md` only carried native captures for the Settings
page (`c2_1-settings-native-*`, 2026-10-07). The plugin inventory page had off-screen Compose
captures (`captures/plugins-page-2026-10-08/`, `captures/plugins-visual*`) but no capture of the
real window, so the plan kept listing "原生窗口待验收" for plugins. This batch closes that gap for
the plugin page in the three theme flags, on a logged-in GUI session.

This batch adds captures and evidence only. No product code changed.

## Writes

| File | Change |
| --- | --- |
| `docs/desktop-ui/captures/native/plugins-native-dark-1440x900.png` | new, `screencapture -x -o -l 5658` |
| `docs/desktop-ui/captures/native/plugins-native-light-1440x900.png` | new, `screencapture -x -o -l 5668` |
| `docs/desktop-ui/captures/native/plugins-native-system-1440x900.png` | new, `screencapture -x -o -l 5678` |
| `docs/desktop-ui/verification/plugins-native-window-2026-10-11/entry-al1/measurement/measure-plugins-native.py` | new re-runnable geometry check |
| `docs/desktop-ui/verification/plugins-native-window-2026-10-11/entry-al1/measurement/plugins-native-measurements.txt` | new, output of the check (exit 0) |
| `docs/desktop-ui/verification/plugins-native-window-2026-10-11/entry-al1/measurement/captures.sha256` | new, hashes of the three PNGs |
| `docs/desktop-ui/verification/plugins-native-window-2026-10-11/entry-al1/logs/capture.log` | new, the three capture commands and their stdout |
| `docs/desktop-ui/native-window-acceptance.md` | new "Batch AL" evidence section |
| `docs/desktop-ui/replication-plan-2026-10-07.md` | new AL row |

## What was captured

Host appearance at capture time was **Light** (`defaults read -g AppleInterfaceStyle` reports the
key absent), display 2560x1440. Each run launched the app with `--destination plugins` at
`1440x900` and captured the window by CoreGraphics window id.

| Image | Theme flag | Window id | CG bounds | PNG size |
| --- | --- | --- | --- | --- |
| `plugins-native-dark-1440x900.png` | `dark` | 5658 | `x=48,y=78,w=1440,h=900` | 1440x900 RGBA |
| `plugins-native-light-1440x900.png` | `light` | 5668 | `x=48,y=78,w=1440,h=900` | 1440x900 RGBA |
| `plugins-native-system-1440x900.png` | `system` | 5678 | `x=48,y=78,w=1440,h=900` | 1440x900 RGBA |

## Measured geometry (re-run: `measurement/measure-plugins-native.py`, PROBLEMS 0)

All values are pixels in the captured PNG (density 1, so 1dp = 1px).

| Element | dark | light | system | C1.5B settings reference |
| --- | --- | --- | --- | --- |
| PNG size | 1440x900 | 1440x900 | 1440x900 | 1440x900 |
| Traffic-light cluster (colored px, bbox) | 352, `(8,8,59,19)` | 352, `(8,8,59,19)` | 352, `(8,8,59,19)` | `x 8..59, y 8..19` |
| Title strip divider (x=700 scan) | y=44 | y=44 | y=44 | `y 0..43`, divider 44 |
| Rail surface / divider / nav / boundary (y=400 scan) | `51, 52, 340, 341` | `51, 52, 340, 341` | `51, 52, 340, 341` | rail `0..50`, divider 51, nav `52..340`, boundary 341 |
| Content-pane modal surface | `(44,44,44)` | `(255,255,255)` | `(255,255,255)` | dark shell / light shell |
| Content-pane ink (theme-neutral) | 6594 px, bbox `x 571..1439` | 7010 px, bbox `x 571..1439` | 7010 px, bbox `x 571..1439` | — |
| Navigator-column ink (x 52..340) | 3370 px, bbox `(69,104,304,270)` | 3532 px, bbox `(69,104,304,270)` | 3532 px, bbox `(69,104,304,270)` | — |

Findings:

1. The real window chrome (traffic lights, 44px title strip) is present in all three captures, so
   these are genuine window captures, not off-screen renders.
2. The rail/divider/navigator/boundary columns land on exactly the same pixels as the Settings
   acceptance capture (`51, 52, 340, 341`, title divider 44). The shared shell geometry is
   therefore consistent across pages, not Settings-specific.
3. `light` and `system` are byte-identical (sha `c205d15d…`) because the host appearance is
   Light; `dark` differs (sha `97082a6d…`) and its content modal surface is `(44,44,44)`, so the
   dark palette actually applied in the native window.
4. Both the navigator column and the content pane carry ink in every theme, so the plugin page
   rendered (list rows plus page body), not an empty shell.

The `dark` hex values differ from the C1.5B settings capture only in how the pixel run was
sampled; no column index changed.

## Acceptance

- Capture script exit code: 0 for all three runs (`logs/capture.log`).
- `measure-plugins-native.py`: `PROBLEMS 0` (dimension, traffic-light presence, chrome columns,
  content ink, navigator ink all asserted).
- No product code changed, so no test suite re-run is claimed for this batch.

## Boundaries and residual

- These captures prove the plugin page renders in a real window at `1440x900`. They do **not**
  claim pixel parity with the original Codex app: there is no same-state original capture in the
  same coordinate system to diff against, so pixel equality stays unclaimed (same standing
  limitation as the Settings acceptance).
- Only `1440x900` was captured. Other sizes (`1024x768`, `1280x800`, `2560x1440`) remain
  off-screen-only for the plugin page.
- The native captures are PNGs of the window; the app's temp runtime log is deleted by the
  capture script's trap, so `logs/capture.log` records the stdout rather than the app log.
