#!/usr/bin/env python3
"""Reproducible measurement of the Codex plugins navigator against our capture.

Reference (real Codex app, RAW pixels):
  docs/desktop-ui/reference/captures/2026-10-06-user-pages/plugins-marketplace.png
  2560x1440, density 1 (1 raw px == 1 CSS px). The icon rail measures 52px, which equals
  HarnessTokens.Control.RailWidth, and the page-owned navigator sits at x 52..340 (289px), the same
  band the scheduled-tasks and settings captures use. App content starts at y 74 (30px macOS menu
  bar + 44px titlebar strip).

Our capture (this batch): docs/desktop-ui/captures/plugins-navigator-2026-10-10/navigator-2560x1440-light.png
  The whole production Shell rendered at 2560x1440 with no native chrome, so the navigator band is
  read from the sidecar geometry JSON instead of assumed.

Every position is reported relative to the navigator origin so the two captures are comparable.
"""
import json
import os
import sys

from PIL import Image

REF = "docs/desktop-ui/reference/captures/2026-10-06-user-pages/plugins-marketplace.png"
OURS = "docs/desktop-ui/captures/plugins-navigator-2026-10-10/navigator-2560x1440-light.png"
REF_NAV = (52, 74, 340, 1440)  # left, top, right, bottom


def lum(px):
    return (px[0] + px[1] + px[2]) // 3


def nav_bounds_from_json(png_path):
    tags = json.load(open(os.path.splitext(png_path)[0] + ".json", encoding="utf-8"))["tags"]
    node = next(t for t in tags if t["tag"] == "plugins-navigator")
    b = node["boundsInRoot"]
    return (round(b["left"]), round(b["top"]), round(b["right"]), round(b["bottom"]))


def ink_bands(im, x0, x1, y0, y1, thr=200):
    bands, start = [], None
    for y in range(y0, y1):
        hit = any(lum(im.getpixel((x, y))) < thr for x in range(x0, x1))
        if hit and start is None:
            start = y
        elif not hit and start is not None:
            bands.append((start, y - 1))
            start = None
    if start is not None:
        bands.append((start, y1 - 1))
    return bands


def col_runs(im, y0, y1, x0, x1, thr=200, gap=3):
    cols = [x for x in range(x0, x1) if any(lum(im.getpixel((x, y))) < thr for y in range(y0, y1))]
    out = []
    if cols:
        start = prev = cols[0]
        for c in cols[1:]:
            if c - prev > gap:
                out.append((start, prev))
                start = c
            prev = c
        out.append((start, prev))
    return out


def highlight_span(im, x0, x1, y0, y1, lo=225, hi=247, min_px=120):
    rows, start = [], None
    for y in range(y0, y1):
        count = sum(1 for x in range(x0, x1) if lo <= lum(im.getpixel((x, y))) <= hi)
        hit = count >= min_px
        if hit and start is None:
            start = y
        elif not hit and start is not None:
            rows.append((start, y - 1))
            start = None
    if start is not None:
        rows.append((start, y1 - 1))
    if not rows:
        return None
    top, bottom = rows[0]
    mid = (top + bottom) // 2
    span = [x for x in range(x0, x1) if lo <= lum(im.getpixel((x, mid))) <= hi]
    return (top, bottom, min(span), max(span)) if span else None


def measure(root, path, nav, label):
    im = Image.open(os.path.join(root, path)).convert("RGB")
    left, top, right, bottom = nav
    print("== " + label + " ==")
    print("image", im.size, "navigator x", left, "..", right, "y", top, "..", bottom,
          "(width %d)" % (right - left))
    print("right-edge scan at y+300 (x %d..%d):" % (right - 4, right + 4),
          [lum(im.getpixel((x, top + 300))) for x in range(right - 4, right + 5)])
    bands = ink_bands(im, left + 4, right - 4, top, top + 260)
    for i, (a, b) in enumerate(bands):
        runs = col_runs(im, a, b + 1, left + 4, right - 4)
        print("  band%-2d y %4d..%-4d (rel %3d..%-3d)  x rel %s"
              % (i, a, b, a - top, b - top, [(s - left, e - left) for s, e in runs]))
    hl = highlight_span(im, left + 4, right - 4, top, top + 260)
    if hl:
        print("  selected highlight y %d..%d (rel %d..%d)  x %d..%d (rel %d..%d) width %d"
              % (hl[0], hl[1], hl[0] - top, hl[1] - top, hl[2], hl[3],
                 hl[2] - left, hl[3] - left, hl[3] - hl[2] + 1))
    return bands


if __name__ == "__main__":
    root = sys.argv[1] if len(sys.argv) > 1 else "."
    measure(root, REF, REF_NAV, "reference Codex capture (light, 2560x1440)")
    print()
    ours = os.path.join(root, OURS)
    measure(root, OURS, nav_bounds_from_json(ours), "our capture (production Shell, light, 2560x1440)")
