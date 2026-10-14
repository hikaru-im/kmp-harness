#!/usr/bin/env python3
"""Reproducible measurement of the Codex Settings navigator against our capture.

Reference (real Codex app, RAW pixels):
  docs/desktop-ui/reference/captures/2026-10-06-user-pages/settings-general.png
  2560x1440, density 1 (1 raw px == 1 CSS px). The icon rail measures 52px, which equals
  HarnessTokens.Control.RailWidth, and the page-owned navigator sits at x 52..340 (289px), the same
  band the plugins and scheduled captures use. App content starts at y 74 (30px macOS menu bar and
  the 44px titlebar strip).

Our capture (this batch):
  docs/desktop-ui/captures/settings-navigator-2026-10-10/navigator-2560x1440-light.png
  The whole production Shell rendered at 2560x1440 with no native chrome and a 44dp title strip, so
  the navigator band is read from the sidecar geometry JSON instead of assumed.

Every position is reported relative to the navigator origin so the two captures are comparable.
"""
import json
import os
import sys

from PIL import Image

REF = "docs/desktop-ui/reference/captures/2026-10-06-user-pages/settings-general.png"
OURS = "docs/desktop-ui/captures/settings-navigator-2026-10-10/navigator-2560x1440-light.png"
REF_NAV = (52, 74, 340)  # left, top, right
OURS_SURFACE, OURS_PILL, OURS_SELECTED = 249, 243, 237
REF_SURFACE = 252


def lum(px):
    return (px[0] + px[1] + px[2]) // 3


def nav_left_top_right(png_path, tag):
    tags = json.load(open(os.path.splitext(png_path)[0] + ".json", encoding="utf-8"))["tags"]
    node = next(t for t in tags if t["tag"] == tag)
    b = node["boundsInRoot"]
    return round(b["left"]), round(b["top"]), round(b["right"])


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


def col_runs(im, y0, y1, x0, x1, thr=200, gap=4):
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


def fill_span(im, x0, x1, y0, y1, lo, hi, min_px):
    """First long run of fill-toned pixels: its vertical band and horizontal span."""
    mid = None
    for y in range(y0, y1):
        if sum(1 for x in range(x0, x1) if lo <= lum(im.getpixel((x, y))) <= hi) >= min_px:
            mid = y
            break
    if mid is None:
        return None
    top = mid
    while top > y0 and sum(1 for x in range(x0, x1) if lo <= lum(im.getpixel((x, top - 1))) <= hi) >= min_px:
        top -= 1
    bottom = mid
    while bottom + 1 < y1 and sum(1 for x in range(x0, x1) if lo <= lum(im.getpixel((x, bottom + 1))) <= hi) >= min_px:
        bottom += 1
    span = [x for x in range(x0, x1) if lo <= lum(im.getpixel((x, (top + bottom) // 2))) <= hi]
    return (top, bottom, min(span), max(span)) if span else None


def report(root, path, nav, label, surface, pill, selected):
    im = Image.open(os.path.join(root, path)).convert("RGB")
    left, top, right = nav
    print("== " + label + " ==")
    print("image", im.size, "navigator x", left, "..", right, "top", top,
          "(width %d)" % (right - left))
    print("right-edge scan at y+300:", [lum(im.getpixel((x, top + 300))) for x in range(right - 4, right + 2)])
    print("navigator surface lum:", lum(im.getpixel((left + 100, top + 300))))
    for i, (a, b) in enumerate(ink_bands(im, left + 2, right - 2, top, top + 270)):
        runs = col_runs(im, a, b + 1, left + 2, right - 2)
        print("  band%-2d y rel %3d..%-3d  x rel %s"
              % (i, a - top, b - top, [(s - left, e - left) for s, e in runs]))
    # Both fills are a fixed offset below the navigator surface, so the windows are derived from the
    # measured surface instead of hardcoded per capture (the reference screenshot reads ~3 lighter
    # than our render; see the review's tone note).
    for name, (lo, hi), window in (("search pill", (surface - 16, surface - 5), (top + 40, top + 100)),
                                   ("selected row", (surface - 26, surface - 12), (top + 120, top + 175))):
        found = fill_span(im, left + 2, right - 2, window[0], window[1], lo, hi, 120)
        if found:
            print("  %s: y rel %d..%d  x rel %d..%d  (w %d)"
                  % (name, found[0] - top, found[1] - top, found[2] - left, found[3] - left,
                     found[3] - found[2] + 1))
    return im


if __name__ == "__main__":
    root = sys.argv[1] if len(sys.argv) > 1 else "."
    report(root, REF, REF_NAV, "reference Codex capture (light, 2560x1440)", REF_SURFACE, 243, 237)
    print()
    ours = os.path.join(root, OURS)
    report(root, OURS, nav_left_top_right(ours, "settings-navigator"),
           "our capture (production Shell, light, 2560x1440)",
           OURS_SURFACE, OURS_PILL, OURS_SELECTED)
