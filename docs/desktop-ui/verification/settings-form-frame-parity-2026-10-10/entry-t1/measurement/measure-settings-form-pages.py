#!/usr/bin/env python3
"""Reproducible measurement of the shared Settings page frame (batch T, 2026-10-10).

Reference (real Codex app, RAW pixels, no resampling):
  docs/desktop-ui/reference/captures/2026-10-06-user-pages/settings-general.png
  2560x1440, density 1. The app rail (52px) and the page navigator (289px) sit left of the page, so the
  reference x coordinates compare directly with our 2560px Shell captures.

Our captures (this batch):
  docs/desktop-ui/captures/settings-form-pages-2026-10-10/
  The whole production Shell with a 44dp title strip and no native chrome.

Two independent reads:
  1. Semantics geometry: each page column node against the Shell own main-content-column.
  2. Pixels: the long vertical card borders in the Appearance 2560x1440 capture against the same read on
     the reference Settings > General capture.
"""
import glob
import json
import os

from PIL import Image

CAPTURES = "docs/desktop-ui/captures/settings-form-pages-2026-10-10"
REFERENCE = "docs/desktop-ui/reference/captures/2026-10-06-user-pages/settings-general.png"
X_BAND = (1000, 1900)
Y_BAND = (120, 700)
MIN_RUN = 60
# The page title sits 73px below each capture own content top: 74 in the reference (30px menu bar plus the
# 44px titlebar strip) and 45 in ours (44dp titlebar inset). The ink window below is relative to that top.
REF_TOP = 74
OURS_TOP = 45
TITLE_X = (1070, 1145)
TITLE_Y = (70, 110)

PAGES = [
    ("appearance", "settings-appearance-title"),
    ("models", "settings-models-title"),
    ("keyboard-shortcuts", "settings-shortcuts-title"),
    ("connections", "settings-connections-title"),
    ("git", "git-page"),
    ("worktrees", "worktrees-page"),
]


def lum(px):
    return (px[0] + px[1] + px[2]) // 3


def long_vertical_borders(im, x_band, y_band, thr=252, min_run=MIN_RUN):
    """x runs whose column is darker than thr for at least min_run pixels: the card borders."""
    cols = []
    for x in range(x_band[0], x_band[1]):
        n = 0
        for y in range(y_band[0], y_band[1]):
            if lum(im.getpixel((x, y))) < thr:
                n += 1
        if n >= min_run:
            cols.append(x)
    runs = []
    if cols:
        start = cols[0]
        prev = cols[0]
        for c in cols[1:]:
            if c - prev > 2:
                runs.append([start, prev])
                start = c
            prev = c
        runs.append([start, prev])
    return runs


def ink_band(im, x0, x1, y0, y1, thr=200):
    """Vertical ink band of a glyph cluster, used to compare the page title size across two captures."""
    start = None
    end = None
    for y in range(y0, y1):
        hit = False
        for x in range(x0, x1):
            if lum(im.getpixel((x, y))) < thr:
                hit = True
                break
        if hit and start is None:
            start = y
        if hit:
            end = y
    return [start, end]


def read_tags(path):
    with open(path, "r", encoding="utf-8") as handle:
        data = json.load(handle)
    # layoutBounds is positionInRoot + size, i.e. the unclipped placement. boundsInRoot in the same file is
    # clipped by the ancestor box, so it hides the intentional 8px page shift and must not be used here.
    return {t["tag"]: t["layoutBounds"] for t in data["tags"] if t.get("tag")}


def row(values, widths):
    out = []
    for value, width in zip(values, widths):
        out.append(str(value).rjust(width))
    return " ".join(out)


def main():
    problems = 0
    print("== Semantics geometry (window pixels, density 1) ==")
    widths = [13, 10, 10, 9, 10, 7, 7, 7]
    print(row(["capture", "main-left", "main-right", "col-left", "col-right", "width", "dL", "dR"], widths))
    for stem, tag in PAGES:
        for path in sorted(glob.glob(os.path.join(CAPTURES, stem + "-*.json"))):
            by_tag = read_tags(path)
            name = os.path.basename(path)[:-5]
            main = by_tag.get("main-content-column")
            column = by_tag.get(tag + "-column")
            if main is None or column is None:
                print(row([name, "MISSING main=" + str(main is not None), "column=" + str(column is not None)], widths))
                problems += 1
                continue
            width = column["width"]
            left_delta = column["x"] - main["x"]
            right_delta = (main["x"] + main["width"]) - (column["x"] + column["width"])
            print(row([name, round(main["x"], 1), round(main["x"] + main["width"], 1), round(column["x"], 1),
                       round(column["x"] + column["width"], 1), round(width, 1), round(left_delta, 1),
                       round(right_delta, 1)], widths))
            if abs(width - 728.0) > 0.5 or abs(left_delta + 8.0) > 0.5 or abs(right_delta - 8.0) > 0.5:
                print("    MISMATCH: expected width 728, column-left - main-left = -8, main-right - column-right = 8")
                problems += 1
    print("")
    print("== Pixels: long vertical card borders inside the 728px band ==")
    pairs = [("reference settings-general", REFERENCE),
             ("ours appearance-2560x1440-light", os.path.join(CAPTURES, "appearance-2560x1440-light.png"))]
    for label, path in pairs:
        im = Image.open(path).convert("RGB")
        runs = long_vertical_borders(im, X_BAND, Y_BAND)
        edges = []
        for run in runs:
            edges.append(run[0])
            edges.append(run[1])
        band = [min(edges), max(edges)] if edges else [None, None]
        span = (band[1] - band[0] + 1) if edges else None
        print(label + "  size=" + str(im.size) + "  border-columns=" + str(runs)
              + "  card-band=" + str(band) + "  width=" + str(span))
    print("")
    print("== Page title ink height (pixels, two CJK glyphs, same font stack) ==")
    titles = [("reference settings-general title", REFERENCE, REF_TOP),
              ("ours appearance title", os.path.join(CAPTURES, "appearance-2560x1440-light.png"), OURS_TOP)]
    for label, path, top in titles:
        im = Image.open(path).convert("RGB")
        band = ink_band(im, TITLE_X[0], TITLE_X[1], top + TITLE_Y[0], top + TITLE_Y[1])
        span = (band[1] - band[0] + 1) if band[0] is not None else None
        rel = [band[0] - top, band[1] - top] if band[0] is not None else [None, None]
        print(label + "  content-top=" + str(top) + "  ink-y=" + str(band) + "  ink-y-rel=" + str(rel)
              + "  ink-height=" + str(span))
    print("")
    print("EXPECTED: every page column is 728px wide and sits 8px left of the Shell main column, so the")
    print("card band lines up with the reference. PROBLEMS: " + str(problems))
    return 1 if problems else 0


if __name__ == "__main__":
    raise SystemExit(main())
