# Codex rail glyph reference

Date: 2026-10-06. Scope: the artwork of the app-rail destination icons. This note exists so the rail
glyphs are redrawn from the Codex bundles instead of eyeballed from a screenshot.

## Source

| Field | Value |
| --- | --- |
| Archive | `/Applications/ChatGPT.app/Contents/Resources/app.asar` |
| App version | `26.930.31730` |
| SHA-256 | `64023959c7c3a4c19022c44f3e8d4b3e4010cefb6e2ea1edce0b535fd417776e` |
| Icon chunks | `sidebar-projects-icon`, `sidebar-tasks-icon`, `sidebar-plugins-icon`, `sidebar-library-icon`, `sidebar-images-icon`, `sidebar-more-icon`, `sidebar-new-chat-icon`, `sidebar-search-icon` |
| Extraction | `scripts/extract-codex-rail-icons.py` (reads the asar header, parses the Lottie layers, rasterises them on the native 20x20 grid) |
| Rendered sheet | `captures/codex-rail-icons-20px.png` (same order as the icon chunks above) |
| Screenshot cross-check | user-provided live Codex capture `截屏2026-10-05 上午9.18.12.png`, rail x 0..51 |

Each chunk carries the artwork name in its `t` template literal, for example
`260625_CGPT_Icon_Plugins_PJ_20x20`, and the icons are Lottie animations on a 20x20 canvas with a
1.33 stroke. The rendered sheet matches the rail glyphs in the live capture, which is what makes the
per-glyph comparison below possible.

## Glyph geometry used by the implementation

| Destination | Codex artwork | Extracted geometry (20x20 grid) |
| --- | --- | --- |
| Projects | `CGPT_Icon_ProjectsFolder` | body rounded rect x 2.5..17.5, y 9.2..16.7 (r 2.2 bottom); separate open top path with the raised tab at y 3.4 and the top edge stepping down to y 5.2 |
| Tasks (Recent) | `CGPT_Icon_Tasks` | face circle centre (10,10) r 7.5; hands from a pivot at (11.05,11.05) to (11.05,6.88) and to (8.95,13.13) |
| Plugins | `CGPT_Icon_Plugins` | cable loop through (14.4,16.2)-(10,17.4)-(2.6,9.5)-(10.3,2.5)-(17.4,10.1)-(12.5,12.2); plug body hexagon (6.9,10.9)(11.05,6.9)(12.6,8.3)(12.25,12.3)(8.2,12.55)(6.88,11.17); pins (10.2,7.45)->(9.1,6.3) and (7.5,10.1)->(6.4,9) |
| More | `CGPT_Icon_More` | three dots on one row |
| Search | `CGPT_Icon_Search` | magnifier: circle plus handle |
| Library | `CGPT_Icon_Library` | three vertical rounded bars |
| Images | `CGPT_Icon_Images` | two overlapping rounded squares |
| New chat | `CGPT_Icon_NewChatPencil` | pencil inside a rounded square |

## Per-destination comparison

| Rail entry | Before | After | Evidence |
| --- | --- | --- | --- |
| Home | house with a narrow peaked roof drawn in the shared 0..1 convention | roof 2.5..17.5 at y 8 over an inset body 3.4..16.6 with a door notch to y 10.3, on the measured silhouette | reference capture silhouette (roof reaches full width in 5 rows, door 4px wide from 68% height); no bundle chunk exists for Home, so the outline variant stays this project's drawing |
| Recent | circle with hands at 12 and 3 | circle r 7.5 with hands at 12 and 7:30 from the Codex pivot | `CGPT_Icon_Tasks`, matches the capture "J" hand shape |
| Projects | folder tab, no interior rule | Codex body + top path, which draws the horizontal rule at y 9.2 | `CGPT_Icon_ProjectsFolder`, matches the capture |
| Plugins | puzzle piece | cable loop with a two-pin plug body | `CGPT_Icon_Plugins`; the enum entries `Plug`/`PlugFilled` replaced `Puzzle`/`PuzzleFilled` |
| Library | absent (Codex-only destination) | three bars: two at x 2.22..6.8 and 6.8..11.39 on the 20 grid sharing the middle edge, the third the same bar tilted -10 degrees about (14.93, 10) | `CGPT_Icon_Library` (`sidebar-library-icon`) |
| Images | absent (Codex-only destination) | back frame x 7.14..17.97 y 2.37..13.21 (r 2.36); front frame a 10.76 square (r 2.3) rotated -8 degrees about (7.368, 12.159); mountain line (2.33,15.63)-(4.06,13.34)-curve-(6.86,12.95)-(12.08,16.89) | `CGPT_Icon_Images` (`sidebar-images-icon`) |

The rail glyph metrics are unchanged from `rail-measurement-2026-10-05.md`: 20px box, ~16px extent,
~1.3px stroke, solid fill when selected.

## Not verified

- `Host` and `Settings` are this project's own drawings. Both are now entries in the account panel
  rather than rail slots, and Codex ships no rail chunk for them, so there is still no Codex glyph to
  compare.
- Library and Images are reproduced from `sidebar-library-icon` and `sidebar-images-icon`. Neither
  bundle carries a separate solid (selected) variant, so the selected state keeps the outline, unlike
  Home / Recent / Plugins.
- The outline (unselected) Home variant has no bundle chunk and no capture: the reference window has
  Home selected, so only the solid house is measured.
- `ClockFilled`, `FolderFilled` and `PlugFilled` are this project's solid treatments; Codex animates
  between outline and solid inside one Lottie file and the capture only shows the solid house.

