#!/usr/bin/env python3
"""Reproducible measurement of the Codex Settings page against our capture.

Reference (real Codex app, RAW pixels):
  docs/desktop-ui/reference/captures/2026-10-06-user-pages/settings-general.png
  2560x1440, density 1 (1 raw px == 1 CSS px). App content starts at y 74 (30px macOS menu bar plus
  the 44px titlebar strip); the page column is the 728px band at x 1079..1806.

Our capture (this batch):
  docs/desktop-ui/captures/settings-page-2026-10-10/page-2560x1440-light.png
  The whole production Shell rendered at 2560x1440 with no native chrome, a 44dp title strip and a
  durable settings store (so no "session-only" status line). Its content top is y 44.

Both are reported relative to their own content top, so the two captures are comparable.
"""
import os
import sys

from PIL import Image

REF = "docs/desktop-ui/reference/captures/2026-10-06-user-pages/settings-general.png"
OURS = "docs/desktop-ui/captures/settings-page-2026-10-10/page-2560x1440-light.png"
REF_TOP, OURS_TOP = 74, 44
PAGE_X0, PAGE_X1 = 1000, 1900


def lum(px):
    return (px[0] + px[1] + px[2]) // 3


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


def col_runs(im, y0, y1, x0, x1, thr=200, gap=8):
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


def long_runs(im, y, lo=225, hi=250, min_len=300, x0=350, x1=2545):
    xs = [x for x in range(x0, x1) if lo <= lum(im.getpixel((x, y))) <= hi]
    out = []
    if xs:
        start = prev = xs[0]
        for c in xs[1:]:
            if c - prev > 2:
                if prev - start >= min_len:
                    out.append((start, prev))
                start = c
            prev = c
        if prev - start >= min_len:
            out.append((start, prev))
    return out


def report(root, path, top, label, span):
    im = Image.open(os.path.join(root, path)).convert("RGB")
    print("== " + label + " ==")
    print("image", im.size, "content top y", top)
    for a, b in ink_bands(im, PAGE_X0, PAGE_X1, top, top + span):
        print("  ink rel y %3d..%-3d  x %s"
              % (a - top, b - top, col_runs(im, a, b + 1, PAGE_X0, PAGE_X1)))
    print("  card borders and row separators (long horizontal runs):")
    for y in range(top, top + span):
        runs = long_runs(im, y)
        if runs:
            print("    rel y %3d  %s" % (y - top, runs))
    return im


if __name__ == "__main__":
    root = sys.argv[1] if len(sys.argv) > 1 else "."
    report(root, REF, REF_TOP, "reference Codex capture (light, 2560x1440)", 600)
    print()
    report(root, OURS, OURS_TOP, "our capture (production Shell, light, 2560x1440)", 600)
