# Entry AA1 — Settings right-hand controls aligned to the reference capture

Date: 2026-10-11. Batch AA. Production change: two shared files plus two call sites.

## Problem (measured, not assumed)

`docs/desktop-ui/reference/captures/2026-10-06-user-pages/settings-general.png` (2560x1440, density 1,
Display-P3 ICC) draws every row-level control as a 28px strip:

- the row **dropdowns** (Default app, Automatic, Close shortcut only) are white pills with a 1px hairline
  outline (measured #eeeeef), a label, and a 10x5 drawn chevron; 138px wide in the icon form, 96px in the
  four-glyph form.
- the row **change button** is 44px wide around a 24px two-glyph ink, i.e. about 10px a side.
- the **corner radius** fits 9.5-10.0 on both, and 10.0 on the full-round switch (which validates the fit).

Our render disagreed three ways. The row dropdowns were filled secondary *buttons*: solid `#f3f3f3`, no
outline, and the chevron faked with a literal U+2304 text glyph appended to the label. The compact button
used 16px horizontal padding, drawing 58px around the same two glyphs. The compact corner was 8px.

## Change

- `modules/ui-foundation/.../HarnessTokens.kt`: new `Corners.Control = 9.dp` (fitted from the capture;
  the asar token list gives `--radius-lg` as 8-10), `Control.CompactPaddingX = 10.dp`,
  `Control.SelectChevron = 14.dp` with `SelectChevronScale = 1.5f` (the ChevronDown path spans 46 percent of
  its box, so a 14dp box at 1.5 artwork scale draws the measured 10x5 ink).
- `modules/ui-foundation/.../HarnessActionControls.kt`: `HarnessActionSurface` takes an optional radius
  (default unchanged); `HarnessActionButton` uses the control radius and the 10px padding when
  `size == Compact`, and keeps 8px/16px for Regular. New `HarnessSelectTrigger`: 28px tall, control radius,
  white (`SurfaceElevated`) fill, 1px `BorderSubtle` outline, label plus drawn chevron, and the same
  enabled/disabled/loading/error semantics and test-tag contract as the button it replaces.
- `apps/shared/.../SettingsFormComponents.kt`: `SettingsFormSelect` anchors on the new trigger, so every
  form-page select (appearance fonts/styles, model protocol) gets it.
- `apps/shared/.../SettingsGeneralPage.kt`: the close-policy select and the two unwired dropdown rows
  (language, default file app) use the trigger; the literal chevron glyph is gone from the label.

## Verification

| control | reference | before (batch S capture) | after |
| --- | --- | --- | --- |
| compact button (change) | w 44, h 28, radius 9.50 | w 58, radius 8 | w 46, h 28, radius 9.75 |
| row select | w 138 / 96, h 28, radius 10.00, white fill, 1px outline, drawn chevron | w 125, h 28, fill #f3f3f3, no outline, text chevron | w 124, h 28, white fill, 1px outline, 10x5 drawn chevron |
| switch | 32x20 full-round | 32x19 | 32x19 (unchanged) |

`measurement/measure-controls.py` reproduces the whole table from the two PNGs (it converts the reference
out of Display-P3 first, and fits the radius from the left-edge profile at each control's own 50 percent
coverage threshold). Output archived in `measurement/control-measurements.txt`.

Tests: `ui-foundation` module **53 / 53**; the fourteen jvm-app classes that exercise these shared
controls **127 / 127**; the settings capture class itself **6 / 6**; full `jvm-app` module **647 found,
5 skipped, 641 passed, 1 failed** (104 containers successful, exit 1) in
`logs/harness-aa-jvmapp-full-module.log`. The single failure is
`PtyTerminalPageInteractionTest.focusedTypingPasteKeysAndResizeUseRealController`, timing out at a
`waitUntil(10_000)` guarding a real-PTY file round-trip (line 122) under full-suite load; run alone the
class passes **7 / 7** (`logs/harness-aa-pty-page-isolation.log`), so this is the same environment-marginal
gate batch Z fixed in the sibling PTY test, not a settings-control regression — that test renders no
settings page. Batch AB widens that file's real-IO gates. Build green. Fresh captures for 1440x900 and
2560x1440, light and dark, are in `docs/desktop-ui/captures/settings-controls-2026-10-11/`; the batch S
capture directory was not overwritten.

## Boundaries

- Measured residuals, reported not papered over:
  - our compact button is 46px against the reference's 44px. The ink is 22px against the reference's 24px at
    the same 10px inset, so the difference is the CJK advance of our UI font, not padding.
  - our select outline renders #f5f5f5 against the reference's #eeedee. The same 7-value gap appears on the
    settings card outline and the row dividers (batch S matched their positions, not their colour). Our
    `BorderSubtle` is `--alpha-05` (#7f7f7f14), which composites to #f5f5f5 over white, while the reference
    hairline reads #eeedee (`--alpha-10` over white, #eaeaea, is 4 too dark by the same read). Resolving it
    needs an app.asar CSS check for the token the settings surfaces actually use, so the shared border token
    was left alone rather than guessed, and the new trigger uses it for internal consistency.
  - our switch is 19px tall against the reference's 20px at the same 32px width. The repo token cites
    app-shared CSS `32x19`, so the CSS and the render disagree by 1px and the token was not changed.
- The reference covers only Settings > General, so the compact radius and padding now apply to every compact
  button in the app (plugins, Git, worktrees, dialogs) without a reference image for those pages. The
  regular-size button geometry is untouched.
- No test source was added: the trigger's enabled/disabled/click/menu behaviour is already covered through
  both call sites by `GeneralSettingsInteractionTest` and the form-page interaction tests.
- This is geometry and structure, not pixel parity, and there is no native window composition.
