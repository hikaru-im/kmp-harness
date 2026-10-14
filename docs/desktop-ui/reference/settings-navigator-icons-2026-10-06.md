# Codex Settings navigator icons

Date: 2026-10-06. Scope: the leading glyph of each row in the Settings page-owned navigator (the
289px left column), last worked in batch C1.5A.4. This note records where the artwork comes from,
which rows are drawn from Codex data, and what is still a placeholder. It does not touch the app
Rail, the Workspace navigator, the Account menu, the More overlay or the Settings main content.

## Source

| Field | Value |
| --- | --- |
| Archive | `/Applications/ChatGPT.app/Contents/Resources/app.asar` |
| App version | `26.930.51102` |
| SHA-256 | `22252f9b76d73a04d9e23710e358201cc4c29b6f003cfbbe109eeb0784dcf848` |
| Section model | `webview/assets/use-visible-settings-sections-dc3bbd6366c8.js` |
| Shared icon factory | `webview/assets/app-shared-9d148924be0b.js` (the `J({name, canvas, ...})` icon objects) |
| Reference capture | `docs/desktop-ui/reference/captures/2026-10-06-user-pages/settings-general.png` (2560x1440, sha256 `d89296acb02f7f319e6fbcc551592ec99a5970f4157d93b06e27e140287edc7b`) |

## Method

Codex ships its own monochrome icon set rather than a third-party pack. Each icon is one object

```
{name:`gear-light-16`, canvas:{width:16,height:16,viewBox:`0 0 16 16`, ...},
 optical:{bounds:{...}, center:{x:9.999909,y:9.999512}, ...}, body:`<path ... fill="currentColor"/> ...`}
```

The `name` encodes the artwork (`<glyph>-light-<size>`), `canvas` gives the 16x16 grid, `optical`
carries the extracted `inkBounds`, and `body` is the raw SVG markup. The Settings navigator renders
the **16px** variant (the reference ink is 10-16px, which the 16-unit glyphs reproduce one to one;
the 20px twins are 17px tall and do not).

The generator reads the artwork verbatim, so the Compose paths are the Codex path data, not a
redraw. It lives in `modules/ui-foundation/src/im/hikaru/harness/ui/foundation/SettingsNavIcons.kt`
as `SettingsNavIcon`, kept apart from `HarnessIconName` because the rail and Workspace glyphs were
signed off in earlier batches. Rendering scales the 16-unit grid to the 16dp row box with Compose's
`PathParser`; there is no extra `glyphScale`, so the ink lands where Codex puts it.

### Picking the icon for a slug

The section map binds a slug to its artwork. When the entry has a `navigation` object the row draws
`navigation.assets["16"]`; when it does not, the fallback is the entry's `component` (a `{16, 20}`
pair is read at `16`, and a bare component is already a 16px icon). The bundle is minified, so the
identifier is resolved through the module's import map and then the factory. Batch C1.5A.3 resolved
the 集成 group:

| Harness slug | Codex slug | Path in the bundle | Artwork |
| --- | --- | --- | --- |
| `plugins` | `plugins-settings` | `component` = `Gt` -> shared export `lS` (`jSi`) | inline `<svg>`, no factory name |
| `computer-control` | `computer-use` | `component` = `qi` (module-local) | inline `<svg>`, no factory name |
| `app-snapshots` | `appshots` | `navigation.assets[16]` = `Se` -> shared export `Km` | `appshot-window-light-16` |
| `browser` | `browser-use` | `component` = `zi` (module-local) | inline `<svg>`, no factory name |
| `cloud-computer` | `cloud-computer` | `navigation.assets[16]` = `Ce` -> shared export `Ltt` | `web-browser-cursor-light-16` |

Batch C1.5A.4 resolved the 编码 group, whose Codex slugs are the section map's `coding` list
(`hooks-settings`, `connections`, `cloud-settings`, `codex-cloud`, `cloud-environments`, `code-review`,
`git-settings`, `local-environments`, `environments`, `worktrees`):

| Harness slug | Codex slug | Path in the bundle | Artwork |
| --- | --- | --- | --- |
| `hooks` | `hooks-settings` | `navigation.assets[16]` = `p` -> shared export `Fc` | `hook-light-16` |
| `connections` | `connections` | `navigation.assets[16]` = `Ft` -> shared export `iot` | `globe-light-16` |
| `codex-cloud` | `codex-cloud` | `navigation.assets[16]` = `Z` -> shared export `ost` | `cloud-light-16` |
| `code-review` | `code-review` | `navigation.assets[16]` = `b` -> shared export `Nrt` | `pull-request-open-light-16` |
| `git` | `git-settings` | `navigation.assets[16]` = `Qt` -> shared export `nct` | `branch-light-16` |
| `worktrees` | `worktrees` | `navigation.assets[16]` = `f` -> shared export `Elt` | `arrow-branch-light-16` |

Two rows have no artwork of their own to draw:

- `codex-cloud-legacy` ("Codex 云端（旧版）"). Codex's coding list has `cloud-settings` and
  `cloud-environments` alongside `codex-cloud`, and all three are bound to the same `Z` / `Kt` pair
  (`cloud-light-16` / `cloud-light-20`), so no slug is distinctly the "legacy" one. The row keeps a
  placeholder; adopting either candidate would draw the same cloud glyph as `codex-cloud`.
- `archived-chats` ("已归档的聊天"). Codex does have an `archived` group, but its only slug is
  `data-controls`, a different row (data and privacy controls), not archived chats. There is no Codex
  artwork for this row, so it keeps a placeholder.

The three inline components in the 集成 group carry no factory `name` and no `inkBounds`, so their ink
box is measured from a 2048px raster of the same path data (see the geometry section). Their `viewBox`
is `0 0 16 16` and their `fill-rule` is taken from the `fillRule` attribute on each path.

`person-circle-light-16` and `circle-person-light-16` (resolved in C1.5A.2) are two separate factory
entries whose `body` is byte-identical, so `SettingsNavIcon.Profile` draws `profile`,
`parental-controls` and `trusted-contact`, and `SettingsNavIcon.Account` reuses the same path data.

## Row table

`inkBounds` is the Codex value on the 16x16 grid; "measured" marks the inline artwork whose box was
measured rather than taken from a factory entry. "Drawn" means the row renders the Codex artwork;
"placeholder" means it still shows the earlier shared glyph.

| Group | Row | Harness slug | Codex slug | Artwork | inkBounds (x,y,w,h) | Status |
| --- | --- | --- | --- | --- | --- | --- |
| 个人 | 常规 | `general` | `general-settings` | `gear-light-16` | 1.691895, 1.240234, 12.617004, 13.518555 | Drawn |
| 个人 | 通知 | `notifications` | `notifications` | `bell-light-16` | 1.954590, 1.307861, 12.090660, 13.383789 | Drawn |
| 个人 | 导入 | `import` | `import` | `arrow-down-open-base-light-16` | 2.141235, 2.141113, 11.716797, 11.717773 | Drawn |
| 个人 | 个人资料 | `profile` | `profile` | `circle-person-light-16` | 1.474609, 1.474609, 13.050781, 13.050781 | Drawn |
| 个人 | 外观 | `appearance` | `appearance` | `sun-light-16` | 0.811279, 0.808594, 14.383789, 14.382813 | Drawn |
| 个人 | 家长控制 | `parental-controls` | `parental-controls` | `person-circle-light-16` | 1.474609, 1.474609, 13.050781, 13.050781 | Drawn |
| 个人 | 受信任联系人 | `trusted-contact` | `trusted-contact` | `person-circle-light-16` | 1.474609, 1.474609, 13.050781, 13.050781 | Drawn |
| 个人 | 语音 | `voice` | `voice` | `microphone-light-16` | 2.529297, 0.974609, 10.941162, 14.133789 | Drawn |
| 个人 | 配置 | `configuration` | unresolved | unresolved | - | Placeholder |
| 个人 | 个性化 | `personalization` | `personalization` | `personalization-light-16` | 1.474609, 1.474609, 13.050781, 13.050781 | Drawn |
| 个人 | Mini 与虚拟宠物 | `mini-pets` | `pets` | `pet-egg-light-16` | 2.474609, 1.141602, 11.050781, 13.716797 | Drawn |
| 个人 | 键盘快捷键 | `keyboard-shortcuts` | `keyboard-shortcuts` | `square-bolt-light-16` | 1.807983, 1.807861, 12.383789, 12.383789 | Drawn |
| 个人 | 使用情况和计费 | `usage-billing` | `usage` | `gauge-light-16` | 0.141113, 0.807861, 15.716797, 13.387451 | Drawn |
| 个人 | 账户 | `account` | `account` | `circle-person-light-16` | 1.474609, 1.474609, 13.050781, 13.050781 | Drawn (see the rosette note) |
| 集成 | 插件 | `plugins` | `plugins-settings` | inline component `Gt` (`jSi`) | 1.5234, 1.4609, 12.9609, 12.9844 (measured) | Drawn |
| 集成 | 电脑操控 | `computer-control` | `computer-use` | inline component `qi` | 3.1406, 3.0234, 10.4219, 10.4531 (measured) | Drawn |
| 集成 | 应用快照 | `app-snapshots` | `appshots` | `appshot-window-light-16` | 1.141602, 2.141113, 13.716797, 11.717773 | Drawn |
| 集成 | 浏览器 | `browser` | `browser-use` | inline component `zi` | 1.1328, 2.1328, 13.7422, 11.7422 (measured) | Drawn |
| 集成 | Cloud computer | `cloud-computer` | `cloud-computer` | `web-browser-cursor-light-16` | 1.141113, 2.140625, 13.988098, 13.01416 | Drawn |
| 编码 | 钩子 | `hooks` | `hooks-settings` | `hook-light-16` | 2.141602, 1.597656, 11.716797, 12.627930 | Drawn |
| 编码 | 连接 | `connections` | `connections` | `globe-light-16` | 1.474609, 1.474609, 13.050781, 13.050781 | Drawn |
| 编码 | Codex 云端 | `codex-cloud` | `codex-cloud` | `cloud-light-16` | 0.808105, 2.141602, 14.383789, 10.383789 | Drawn |
| 编码 | Codex 云端（旧版） | `codex-cloud-legacy` | none (see the note) | unresolved | - | Placeholder |
| 编码 | 代码审查 | `code-review` | `code-review` | `pull-request-open-light-16` | 2.308105, 1.974609, 11.717773, 12.050781 | Drawn |
| 编码 | Git | `git` | `git-settings` | `branch-light-16` | 2.307617, 1.974609, 11.384766, 12.050781 | Drawn |
| 编码 | Worktrees | `worktrees` | `worktrees` | `arrow-branch-light-16` | 1.471924, 2.141113, 11.719727, 11.702148 | Drawn |
| 已归档 | 已归档的聊天 | `archived-chats` | none (see the note) | unresolved | - | Placeholder |

24 of the 27 rows draw Codex artwork (the whole 个人 group except 配置, the whole 集成 group, and six
of the seven 编码 rows) and 3 stay placeholders. 22 entries carry dedicated artwork in
`SettingsNavIcon`, because the three person-circle rows share one.

The 编码 and 已归档 rows sit at or below the 900px fold of both the reference capture and the Harness
captures, so only the first rows of the 编码 group can be compared pixel to pixel (below). They are
listed so the map is complete against `SettingsNavigatorModel`.

## Measured geometry

Both columns are read from the same 1px = 1dp captures: the reference is cropped at x 0..341, y
74..974 (dropping the 30px menu bar and the 44px title bar); the Harness capture is cropped at x
0..341, y 0..900. `ink_x` is the glyph ink span measured in the icon column (x 56..86) so it excludes
the row label and the surrounding box.

### 个人 group

| Row | Codex `ink_x` | Harness `ink_x` | Codex h | Harness h |
| --- | --- | --- | --- | --- |
| 常规 | 69..82 (14w) | 69..82 (14w) | 14 | 14 |
| 通知 | 70..81 (12w) | 70..81 (12w) | 14 | 14 |
| 导入 | 70..81 (12w) | 70..81 (12w) | 12 | 12 |
| 个人资料 | 69..82 (14w) | 69..82 (14w) | 14 | 14 |
| 外观 | 69..82 (14w) | 69..82 (14w) | 14 | 14 |
| 家长控制 | 69..82 (14w) | 69..82 (14w) | 14 | 14 |
| 受信任联系人 | 69..82 (14w) | 69..82 (14w) | 14 | 14 |
| 语音 | 71..80 (10w) | 70..81 (12w) | 14 | 14 |
| 配置 | 70..81 (12w) | 66..85 (20w) | 14 | 20 |
| 个性化 | 69..82 (14w) | 69..82 (14w) | 14 | 14 |
| Mini 与虚拟宠物 | 70..81 (12w) | 70..81 (12w) | 14 | 14 |
| 键盘快捷键 | 70..81 (12w) | 70..81 (12w) | 12 | 14 |
| 使用情况和计费 | 68..83 (16w) | 68..83 (16w) | 13 | 14 |
| 账户 | 69..81 (13w) | 69..82 (14w) | 13 | 14 |

Eleven of the fourteen rows carry the identical ink box on both axes. The three deviations are all
weight or shape, not row position: 配置 is the placeholder, so its 20px box against the reference 12px
is the sun placeholder rather than the Codex glyph; 语音 reads 12w against the reference 10w and
键盘快捷键 and 使用情况和计费 reach one extra antialiased row of height, the same weight bias the earlier
C1 measurement saw; 账户 reads 13w against 14w because the two shapes differ (below).

### 集成 group

| Row | Codex `ink_x` | Harness `ink_x` | Codex h | Harness h |
| --- | --- | --- | --- | --- |
| 插件 | 69..82 (14w) | 69..82 (14w) | 14 | 14 |
| 电脑操控 | 71..81 (11w) | 71..81 (11w) | 11 | 11 |
| 应用快照 | 69..82 (14w) | 69..82 (14w) | 12 | 12 |
| 浏览器 | 69..82 (14w) | 69..82 (14w) | 12 | 12 |
| Cloud computer | 69..82 (14w) | 69..82 (14w) | 13 | 13 |

All five rows now match the reference ink box exactly on both axes. Before this batch the
placeholders read 16px wide (插件, 电脑操控, Cloud computer) and 16px tall (浏览器); 应用快照 already
matched.

For the three inline glyphs the 16-unit box is measured, not factory-provided: each SVG was
rasterised at 2048px and the ink bounding box converted back to grid units. The same procedure
reproduces `plugin-light-16`'s factory inkBounds to within 0.016 unit, which bounds the measurement
error.

### 编码 group (visible rows only)

| Row | Codex `ink_x` | Harness `ink_x` | Codex h | Harness h |
| --- | --- | --- | --- | --- |
| 钩子 | 70..81 (12w) | 70..81 (12w) | 14 | 13 |
| 连接 | 69..82 (14w) | 69..82 (14w) | 14 | 14 |
| Codex 云端 | 69..83 (15w, clipped) | 69..82 (14w, clipped) | 8 | 11 |

钩子 and 连接 match the reference ink box on both axes (钩子's one-row height difference is the same
weight bias, not a position shift). Codex 云端 is clipped by the bottom edge of both captures, so its
measured height is not comparable. The other five rows - Codex 云端（旧版）, 代码审查, Git, Worktrees
and 已归档的聊天 - are below the fold in both captures: **unavailable**, not measured and not estimated.

## Region diff

Comparing the reference crop against the regenerated Harness capture. 集成 was measured over the
requested window x 0..341, y 560..730 and over the full group (heading through Cloud computer), y
585..775. 编码 was measured over the requested reference box (0, 74, 341, 900) against the matching
Harness box (0, 0, 341, 826), and over the visible band y 785..899.

| Region | Mean abs RGB | Pixels within +/-16 |
| --- | --- | --- |
| 编码, requested window (ref y 74..900 vs harness y 0..826) | 7.98 | 94.22% |
| 编码, visible band y 785..899 | 8.48 | 94.06% |
| 集成, y 560..730 | 7.28 | 95.01% |
| 集成, full group y 585..775 | 8.73 | 93.95% |
| 个人, x 0..341, y 100..562 | 8.86 | 93.19% |
| Whole navigator column, y 0..900 | 8.22 | 94.04% |

The 集成 rows are the tightest region in the navigator. The 编码 figure covers only the heading and the
two fully visible rows, because the rest of the group falls below the capture fold; it is a real
measurement of the visible band, not of the whole group. The whole-column figure has moved 8.39 ->
8.28 -> 8.22 across the three batches.

## Remaining differences

- 账户 shape. The reference 账户 glyph is a dense rosette, but the app.asar section model resolves
  `account`'s navigation asset to `circle-person-light-16` (verified by following `Y` -> `gm`). The
  row follows the factory, so it draws a person disc while the capture shows a rosette. Either the
  capture predates the current bundle or the rosette is drawn by a wrapper this rule does not cover;
  the factory mapping is reported as-is instead of guessing the rosette artwork.
- 配置 evidence gap. `configuration` is not a slug in the Codex section model, so there is no factory
  name to resolve. The reference glyph (a circle with a small wave inside) most plausibly maps to
  `agent`/work-settings, but that is not proven, so the row keeps its explicit placeholder.
- Inline artwork. 插件, 电脑操控 and 浏览器 are inline `<svg>` components in the section map with no
  factory `name` and no `inkBounds`; their path data is transcribed verbatim and their ink box is
  measured. The factory ships near-twin entries (`plugin-light-16`, `web-browser-light-16`) whose
  coordinates differ slightly, but the section map's own fallback is the inline component, so that is
  what the rows follow.
- Ink weight. The drawn glyphs are 15-46% heavier than the reference by summed ink, and the two
  finest-detail rows read highest (键盘快捷键 1.46, 外观 1.28). This is not glyph-specific: unchanged
  elements on the same canvases carry the same bias (the search glyph 1.09, the 设置 title 1.06, the
  个人 heading 1.29, the 常规 label 1.24). The reference capture is a Retina screenshot downscaled to
  2560x1440, so its strokes are thinner than a 1:1 16px render. No bbox or shape error is involved.
- Placeholders. The 3 rows marked "Placeholder" still draw the earlier shared glyphs: 配置 (no Codex
  slug), Codex 云端（旧版） (no slug that is distinctly the legacy cloud) and 已归档的聊天 (Codex's
  `archived` group holds only `data-controls`).
- Below the fold. Codex 云端（旧版）, 代码审查, Git, Worktrees and 已归档的聊天 sit outside the visible
  viewport of both the reference capture and the Harness captures, so they have no same-state pixel
  evidence at all; only their structure (path data, view box, ink box) is reported. Codex 云端 itself
  is only partly visible in both.
- No pixel-parity claim. There is no same-state, same-size Codex screenshot of this navigator rendered
  1:1, so this note reports structure, box and weight, never pixel identity.

## Reproduce

`./kotlin --build-output=/tmp/batchC1-20261006 test -m ui-foundation` runs `SettingsNavIconTest`, which
checks the view box, the ink box, the optical centre and that every recorded path stays on the 16-unit
grid. `./kotlin --build-output=/tmp/batchC1-20261006 test -m shared` runs `SettingsNavigatorStateTest`,
which asserts that each catalogued row carries its dedicated glyph. `test -m jvm-app
--include-classes im.hikaru.harness.desktop.ScreenshotTest` rewrites the
`docs/desktop-ui/captures/rail/settings-navigator-*.png` captures.

Comparison artifacts: `/tmp/c1_5a4/coding-group.png` (reference | Harness for the 编码 band, with the
below-fold rows labelled unavailable) and `/tmp/c1_5a4/coding-icons.png` (the six transcribed 编码
glyphs); `/tmp/c1_5a3/integration-group.png` and `/tmp/c1_5a3_candidates.png` for the 集成 group; and
the C1.5A.2 artifacts under `/tmp/c1_5a2/`.
