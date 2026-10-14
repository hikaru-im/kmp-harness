# Native window acceptance

Last updated: 2026-10-11 (batch AL added the plugin inventory page native captures; the
real window capture method is the one introduced in batch C1.5B).

The Desktop client runs in a real macOS window. Two capture paths exist, and they cover
different things:

| Path | What it renders | Where it runs |
| --- | --- | --- |
| `apps/jvm-app/test/im/hikaru/harness/desktop/ScreenshotTest.kt` | Compose content only, off-screen, `titleBarInset = 0` | JVM test, no display |
| `scripts/capture-desktop-window.sh` | The whole real window: macOS title bar, traffic lights and content | A logged-in macOS GUI session |

The off-screen test cannot show the title bar or the traffic lights because the OS draws them.
Native window acceptance therefore requires running the app and capturing the window.

## Theme behaviour

The JVM entry does not pin a palette. `--theme system` (the default) follows the host appearance
through `isSystemInDarkTheme()`; `light` and `dark` pin the palette. The resolution lives in
`resolveDarkTheme` in `modules/ui-foundation` and is covered by `HarnessThemeModeTest`, so re-pinning
dark fails a unit test instead of only a screenshot.

```bash
# Follow the macOS appearance (default)
./kotlin run -m jvm-app -- --home <temp> --window-size 1440x900 --theme system

# Pin a palette for a deterministic capture
./kotlin run -m jvm-app -- --home <temp> --window-size 1440x900 --theme dark
```

## Prerequisites

- macOS with a logged-in GUI session. The script cannot run headless.
- Screen Recording permission for the process that runs the script (for `screencapture`).

The window is located through CoreGraphics (`CGWindowListCopyWindowInfo`), so System Events / Apple
Events automation is not required. Grant Screen Recording in System Settings > Privacy & Security.
The script fails loudly rather than writing an empty image when the permission is missing.

## Script usage

```bash
scripts/capture-desktop-window.sh \
  --size 1440x900 \
  --destination settings \
  --theme system \
  --out docs/desktop-ui/captures/native \
  --name settings-native-system-1440x900 \
  --build-output /tmp/c1_5b-build
```

The script starts `jvm-app` with an isolated Harness home, polls for an on-screen window titled
`KMP Harness` owned by the launched process tree, then captures that window by ID with
`screencapture -x -o -l <id>`. Capturing by window ID means a window in front of it can never leak
into the image, and no screen region has to be guessed.

Other flags: `--build-output DIR` points the build at a separate directory when the repo `build`
directory is locked; `--timeout SECONDS` (default 900) covers the first compile; `--account-menu-open`
starts with the account menu open; `--keep` leaves the app running for manual inspection.

`--window-size` maps to the full window frame, so the captured PNG matches the requested size
exactly (`1440x900` request produced a `1440x900` PNG). The Compose content area is smaller by the
title strip. Verify dimensions with `file`.

## Manual fallback

If automation is unavailable, run the app, focus its window, and capture interactively:

```bash
./kotlin run -m jvm-app -- --home <temp> --window-size 1440x900 --theme dark
# Then, in another terminal:
screencapture -w -o /tmp/harness-window.png   # select the window when prompted
```

## Evidence (batch C1.5B, 2026-10-07)

Captured on this machine, display 2560x1440, host appearance **Dark**
(`defaults read -g AppleInterfaceStyle` = `Dark`). The window is captured without the menu bar.

| Image | Theme flag | Window id | CG bounds | Result |
| --- | --- | --- | --- | --- |
| `captures/native/settings-native-system-1440x900.png` | `system` | 2475 | `x=48,y=78,w=1440,h=900` | 1440x900 PNG, real traffic lights + title strip over a dark shell |
| `captures/native/settings-native-dark-1440x900.png` | `dark` | 2485 | `x=48,y=78,w=1440,h=900` | Byte-identical to the `system` capture, because the host appearance is already dark |

Both are unmodified `screencapture -x -o -l <id>` captures of the real window.

### Measured geometry (1440x900 native capture)

All values are pixels in the captured PNG (density 1, so 1dp = 1px).

| Element | Measurement |
| --- | --- |
| Rail column | `x 0..50`, rail surface; 1px divider at `x 51`; rail block width 52 |
| Navigator column | `x 52..340` |
| Nav / content boundary | `x 341` (content `x 341..1439`) |
| Title strip | `y 0..43` (44px), reserved for the macOS traffic lights and the app title bar row |
| Title bar row text | `y 16..28`; hamburger `x ~361`, title `x ~392..418`, trailing action `x ~1395..1410` |
| Navigator header 设置 | `x 68..103`, `y 62..78` |
| Navigator search field | `y 96..131` (36px high) |
| First navigator row 常规 | selected pill `y 177..206` (30px high); rows repeat on a 31px pitch |
| Traffic lights | coloured cluster `x 8..59`, `y 8..19`; centres ~ (14,14) (34,14) (54,14) |

### Navigator inset: `titleBarInset` is applied in the real window

The window passes `titleBarInset = NativeWindowChrome.TitlebarHeight`
(`= HarnessTokens.Control.Titlebar`, 44dp). The off-screen `ScreenshotTest` leaves the default
`0.dp`, so a direct comparison shows the rail and navigator columns of the real window sitting
exactly **44px lower** than the off-screen render. Every navigator band matches at `+44`:

| Element | Off-screen `y` | Native `y` | Delta |
| --- | --- | --- | --- |
| Navigator header 设置 | 18..34 | 62..78 | +44 |
| Navigator search field | 52..87 | 96..131 | +44 |
| First row 常规 (selected pill) | 133..162 | 177..206 | +44 |
| Second row 通知 | 172..185 | 216..229 | +44 |

The title strip itself is identical in both paths (`y 0..43`, text `y 16..28`, divider `y 44`),
because `MainSurface` reserves the title bar height in both cases. Only the rail and navigator
columns are inset, which is what keeps their content clear of the traffic lights. This is correct
behaviour, not a misplacement; no window code was changed for C1.5B.

### Overlay

`captures/native/settings-native-overlay.png` is a 4332x900 analysis composite of three 1440x900
panels: [ native capture | off-screen reference shifted down 44px | absolute difference x4 ]. Cyan
guides mark `x 52` and `x 341`; the yellow guide marks the 44px title strip. After the +44 shift the
navigator columns line up. The whole-image mean per-channel absolute difference falls from
9.80/9.86/10.00 (unaligned) to 7.85/7.90/8.01 (aligned); over the navigator column it falls from a
mean absolute sum of 51.1 to 14.7, and to 1.3 once a constant +5/channel offset is removed.

That residual offset is a colour-space effect, not a rendering defect: the native capture embeds a
`Display` ICC profile (the display colour space) while the off-screen PNG is untagged sRGB, so the
same neutral surface reads a few units higher in the native file. The content pane differs by
design (the native run renders the Settings page; the off-screen fixture renders an empty shell),
so the content region is the white area in the overlay's diff panel.

## Historical evidence (2026-10-05 / 2026-10-06, earlier capture method)

Earlier captures used System Events for the window bounds and are kept for the record:
`native-system-1440x900.png`, `native-dark-1440x900.png` (2026-10-05),
`native-shell-system-1440x900.png`, `native-integrated-titlebar-1440x900.png`,
`acceptance-desktop-home-*.png`, `acceptance-desktop-account-*.png` (2026-10-06). They are not
re-verified here.

Off-screen content regression images live in `docs/desktop-ui/captures/` and are generated by
`ScreenshotTest` in both palettes at 2560x1440, 1440x900, 1280x800 and 1024x768.

## Evidence (batch AL, 2026-10-11, plugin inventory page)

The Settings capture above was joined by the plugin inventory page. Host appearance at capture
time was **Light** (`defaults read -g AppleInterfaceStyle` reports the key absent), so `--theme
light` and `--theme system` produced byte-identical images.

| Image | Theme flag | Window id | CG bounds | Result |
| --- | --- | --- | --- | --- |
| `captures/native/plugins-native-dark-1440x900.png` | `dark` | 5658 | `x=48,y=78,w=1440,h=900` | 1440x900 PNG, traffic lights + title strip over a dark plugin page |
| `captures/native/plugins-native-light-1440x900.png` | `light` | 5668 | `x=48,y=78,w=1440,h=900` | 1440x900 PNG, light shell |
| `captures/native/plugins-native-system-1440x900.png` | `system` | 5678 | `x=48,y=78,w=1440,h=900` | byte-identical to the `light` capture (host is Light) |

Re-runnable check: `docs/desktop-ui/verification/plugins-native-window-2026-10-11/entry-al1/measurement/measure-plugins-native.py`
(`PROBLEMS 0`). The shared shell columns land on the same pixels as the Settings capture:
traffic-light cluster `x 8..59, y 8..19`, title divider `y 44`, rail/divider/navigator/boundary
`51, 52, 340, 341`. Both the navigator column and the content pane carry ink in every theme, so
the page rendered rather than an empty shell. No product code changed in this batch.

## Evidence (batch BY, 2026-10-12, scheduled tasks + plugin page)

The scheduled-tasks page now has real-window captures, and the plugin page was re-captured. Screen
Recording had been failing on this machine since 2026-10-11 13:03 (`could not create image from
display`); at the start of this batch `screencapture -x -t png` wrote a 2560x1440 frame again, so
the blocking condition was gone. Host appearance was **Light**, display 2560x1440, window size
`1440x900`; each run used its own isolated Harness home, so the pages show their empty/unavailable
states. The three plugin captures came out byte-identical to the AL captures recorded above, which
shows the capture path reproduces even though Compose node ids and window ids change every run.

| Image | Destination | Theme flag | Result |
| --- | --- | --- | --- |
| `captures/native/plugins-native-{dark,light,system}-1440x900.png` | `plugins` | `dark` / `light` / `system` | 1440x900 PNG; re-run of the AL captures, byte-identical to them; `light` and `system` identical |
| `captures/native/scheduled-native-{dark,light,system}-1440x900.png` | `scheduled` | `dark` / `light` / `system` | 1440x900 PNG; `light` and `system` are byte-identical |

Re-runnable check:
`docs/desktop-ui/verification/native-pages-scheduled-plugins-2026-10-12/entry-by1/measurement/measure-native-pages.py`
(`PROBLEMS 0`; standard library PNG decoder, no Pillow). All six captures show the real window
chrome and land on the same shared shell columns as the C1.5B/AL captures: traffic-light cluster
`x 8..59, y 8..19`, title divider `y 44`, rail/divider/navigator/boundary `51, 52, 340, 341`.
Navigator and content ink are present in every theme, so the pages rendered rather than an empty
shell.

One finding from the captured plugin page: its unavailable state still renders English text
(`Local plugin inventory or source is invalid, unsupported, or unavailable.` and a `Retry` button)
while the rest of the page is Chinese. That localisation gap is recorded in the batch BY review and
in the plan; it was not changed here. No product code changed in this batch.

## Limits

- Only the primary display origin is used for window bounds. A window on a secondary or
  negative-coordinate display needs the manual `screencapture -R` / `-w` fallback.
- The capture includes whatever the window draws; the window is captured by ID, so overlapping
  panels cannot leak in, but the capture still reflects the live system appearance.
- The native title strip is drawn by macOS, so it follows the system appearance rather than the app
  palette: `--theme dark` on a light system shows dark content under a light title strip.
- This document reports in-window boundaries and the off-screen/native alignment. It does not claim
  pixel parity with the Codex Desktop window; native chrome is an implementation capture.
