# Entry W1 — Scheduled landing body aligned to the reference capture

Date: 2026-10-10. Batch W. One production file plus evidence.

## Reference

`docs/desktop-ui/reference/captures/2026-10-06-user-pages/scheduled-tasks.png` (density 1, 2560x1440;
page area starts at x=341, content top y=74). Batch P aligned only the scheduler navigator and
explicitly left "页面主体与模板卡片" for later, so this batch covers the page body.

## Measured before and after

| Element | Reference | Before | After |
| --- | --- | --- | --- |
| clock ink | 48x47 | 40x40 | 48x48 |
| empty-state title ink | 69x17 | 62x15 | 70x17 |
| subtitle ink height | 13 | 13 | 13 |
| card width | 359 | 354 | 359 |
| grid column gutter | 20 | 12 | 20 |
| grid row gutter | 20 | 12 | 20 |
| grid outer width | 738 | 720 | 738 |
| card height | 110 | 75 | 90, or 109 when a description wraps to two lines |

Full output: `measurement/scheduled-landing-measurements.txt` (PROBLEMS: 0).

## Changes — `apps/shared/src/im/hikaru/harness/client/scheduling/ScheduledPageLayout.kt`

1. Template grid: outer width 720 -> 738, column gutter 12 -> 20, row gutter 12 -> 20. The row
   gutter went into its own Column so the clock/title/description block keeps its 12dp rhythm.
   Reference numbers: card 1 x1080..1438 (359), card 2 starts 1459 (gutter 20), grid 1080..1817 (738).
2. Clock: 44 -> 48 with the ring drawn on the box edge (radius `size/2 - 1dp`) so the ink is the box.
   The old 44dp box plus its 3dp inset produced a 40px ink; the reference is 48.
3. Empty-state title: `titleLarge` 16sp -> 18sp/26, which is the extracted `--font-heading-sm`.
4. Card: padding 16 -> 20; title `titleSmall` 14sp -> 16sp/24 (`--font-heading-xs`); description
    `bodySmall` 12sp -> 14sp/18 (`--font-text-sm`).
5. Each template row is now `height(IntrinsicSize.Min)` with `fillMaxHeight()` cards, so a
   description that wraps to two lines no longer leaves one card shorter than its neighbour. The
   reference grid rows are uniform; without this, row 3 measured 90 (left) vs 109 (right).

Type-scale values come from `docs/desktop-ui/reference/app-asar-geometry-evidence.md` (the extracted
`--font-heading-sm 18/26`, `--font-heading-xs 16/24`, `--font-text-sm 14/18`).

## Verification

- `ScheduledPageScreenshotTest` + `ScheduledPageInteractionTest` + `ScheduledManagementInteractionTest`:
  **17 / 17**, 0 failed (`logs/harness-w1-jvmapp-scheduled-page-tests.log`).
- The same three plus `ScheduledManagementScreenshotTest`: **23 / 23**, 0 failed
  (`logs/harness-w1-jvmapp-scheduled-page-and-management.log`).
- `measurement/measure-scheduled-landing.py` on the reference vs our 1440x900 light landing capture:
  **PROBLEMS: 0**. Exactly matching now: clock 48, title 17 tall, subtitle 13 tall, card 359,
   column gutter 20, grid 738, row gutter 20.
   Every card in a row now shares its bottom edge (row 3: left and right both 570..678).
- Captures were written to the new directory `docs/desktop-ui/captures/scheduled-page-2026-10-10-w1/`.
  Batch P's `scheduled-page-2026-10-10/` directory is untouched (mtime unchanged), and the
  hard-coded `docs/desktop-ui/captures/scheduled-execution/` directory was not rewritten either.

## Residual and boundaries

- Card height stays 90 vs the reference 110. That is content-driven: the reference cards carry a
  leading icon and a two-line description; ours carry a title and a one-line description. The
  measurement script reports it as KNOWN rather than as a geometry defect, and it is not fixed by
  inventing content.
- The title-to-description ink gap is 19 vs the reference 12. The header keeps one uniform 12dp
  rhythm for clock/title/description, so this pair stays looser. Matching it would need per-pair
  spacing that one capture does not establish, so it is left as a documented residual.
- Only the light 1440x900 landing was measured. Compact 600x800 and dark are covered by the
  screenshot matrix but have no reference state to compare against.
- This is geometry alignment plus visual review, not pixel parity: the empty-state copy and the
  template list differ from the reference by design, and the reference is a full-screen capture
  while ours is an off-screen page surface.
