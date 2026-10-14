#!/usr/bin/env python3
"""Reproducible measurement of the Codex scheduled-tasks navigator against our capture.

Reference (the real Codex app, RAW pixels):
  docs/desktop-ui/reference/captures/2026-10-06-user-pages/scheduled-tasks.png
  2560x1440. This capture is 1:1 (density 1): the icon rail measures 52px wide, which equals
  HarnessTokens.Control.RailWidth, and the navigator measures 288px, which is the user-resized
  reference width already recorded in workspace-navigator-measurement-2026-10-06.md.
  Therefore 1 raw px == 1 CSS px. Navigator left border = x 52, right border = x 340,
  app content starts at y 74 (below the 30px macOS menu bar and the 44px titlebar strip).

Our capture (density 1, navigator Box at root x 0..248):
  docs/desktop-ui/captures/scheduled-page-2026-10-10/landing-light-1440x900-viewport.png

Both are reported as offsets relative to their own navigator/content origin so the two
different window scalings are comparable.
"""
import os
import sys
from PIL import Image

REF = "docs/desktop-ui/reference/captures/2026-10-06-user-pages/scheduled-tasks.png"
OURS = "docs/desktop-ui/captures/scheduled-page-2026-10-10/landing-light-1440x900-viewport.png"
REF_NAV_LEFT, REF_CONTENT_TOP, REF_NAV_RIGHT = 52, 74, 340
OURS_NAV_LEFT, OURS_CONTENT_TOP, OURS_NAV_RIGHT = 0, 0, 248


def ink_bands(im, x0, x1, y0, y1, thr=200):
    bands, inb, s = [], False, 0
    for y in range(y0, y1):
        hit = any(sum(im.getpixel((x, y))) // 3 < thr for x in range(x0, x1))
        if hit and not inb:
            s, inb = y, True
        if not hit and inb:
            bands.append((s, y - 1))
            inb = False
    if inb:
        bands.append((s, y1 - 1))
    return bands


def col_runs(im, y0, y1, x0, x1, thr=200, gap=3):
    cols = [x for x in range(x0, x1) if any(sum(im.getpixel((x, y))) // 3 < thr for y in range(y0, y1))]
    out = []
    if cols:
        s = prev = cols[0]
        for c in cols[1:]:
            if c - prev > gap:
                out.append((s, prev))
                s = c
            prev = c
        out.append((s, prev))
    return out


def measure(root, path, nav_left, content_top, nav_right, label):
    im = Image.open(os.path.join(root, path)).convert("RGB")
    print("== " + label + " ==")
    print("image", im.size, "navigator x", nav_left, "..", nav_right, "content top y", content_top)
    print("right border check:", [x for x in range(nav_right - 3, nav_right + 4)
                                  if sum(im.getpixel((x, content_top + 300))) // 3 < 247])
    bands = ink_bands(im, nav_left + 4, nav_right - 4, content_top, content_top + 320)
    names = ["title", "new-task", "section", "task-1 name", "task-1 status", "task-2 name", "task-2 status"]
    for i, (a, b) in enumerate(bands):
        runs = col_runs(im, a, b + 1, nav_left + 4, nav_right - 4)
        name = names[i] if i < len(names) else "band" + str(i)
        print("  %-14s y %4d..%-4d (rel %3d..%-3d)  x rel %s"
              % (name, a, b, a - content_top, b - content_top,
                 [(s - nav_left, e - nav_left) for s, e in runs]))
    return bands


if __name__ == "__main__":
    root = sys.argv[1] if len(sys.argv) > 1 else "."
    measure(root, REF, REF_NAV_LEFT, REF_CONTENT_TOP, REF_NAV_RIGHT, "reference Codex capture")
    print()
    measure(root, OURS, OURS_NAV_LEFT, OURS_CONTENT_TOP, OURS_NAV_RIGHT, "our capture (after Batch P)")
