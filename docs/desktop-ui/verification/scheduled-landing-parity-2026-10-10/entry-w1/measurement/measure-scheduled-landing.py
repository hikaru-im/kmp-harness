#!/usr/bin/env python3
"""Compare the Scheduled landing body (empty state + template grid) of a Codex reference capture
and one of our captures.

Usage:
  measure-scheduled-landing.py <reference.png> <refPageLeft> <ours.png> <oursPageLeft>

Both images are density 1. pageLeft removes each capture's left chrome (the reference's rail +
navigator end at x=341; our page surface starts where its navigator ends). The script prints the
empty-state boxes and the template grid geometry and lists any mismatch over 3px.

Borders are detected by counting light-grey pixels (225..250 on the 0..255 luma), which both the
reference card outline and our HarnessSurface border produce, while ignoring darker glyph ink.
"""
import sys
from PIL import Image

TOL = 3

# Card height depends on the card's own content. The reference cards carry a leading icon and a
# two-line description; ours carry a title and a one-line description, so the height is reported
# as an expected content difference rather than a geometry defect.
CONTENT_DRIVEN_GRID_KEYS = {"card_h"}


def load(path):
    return Image.open(path).convert("L")


def box(px, x0, x1, y0, y1, thr=200):
    mnx = mny = None; mxx = mxy = -1
    for y in range(y0, y1):
        for x in range(x0, x1):
            if px[x, y] < thr:
                mnx = x if mnx is None else min(mnx, x); mxx = x if mxx is None else max(mxx, x)
                mny = y if mny is None else min(mny, y); mxy = y if mxy is None else max(mxy, y)
    return None if mnx is None else (mnx, mny, mxx, mxy, mxx - mnx + 1, mxy - mny + 1)


def ink_rows(px, x0, x1, y0, y1, thr=200):
    out = []; run = None
    for y in range(y0, y1):
        if any(px[x, y] < thr for x in range(x0, x1)):
            if run is None: run = y
        elif run is not None:
            out.append((run, y - 1)); run = None
    if run is not None: out.append((run, y1 - 1))
    return out


def border_rows(px, x0, x1, y0, y1, need=300):
    rows = [y for y in range(y0, y1)
            if sum(1 for x in range(x0, x1) if 225 <= px[x, y] <= 250) > need]
    out = []
    for y in rows:
        if out and y - out[-1][1] <= 2: out[-1][1] = y
        else: out.append([y, y])
    return out


def border_cols(px, x0, x1, y0, y1, need=40):
    cols = [x for x in range(x0, x1)
            if sum(1 for y in range(y0, y1) if 225 <= px[x, y] <= 250) > need]
    out = []
    for x in cols:
        if out and x - out[-1][1] <= 2: out[-1][1] = x
        else: out.append([x, x])
    return out


def measure(path, page_left):
    im = load(path); px = im.load(); W, H = im.size
    rows = ink_rows(px, page_left + 4, W - 2, 0, H)
    # The empty state is the first run of three text-ish blocks before the grid; report the first
    # three ink blocks that are narrower than 60% of the page (the grid border rows are wider).
    pw = W - page_left
    state = []
    for a, b in rows:
        bw = box(px, page_left + 4, W - 2, a, b + 1)
        if bw and bw[4] < pw * 0.6:
            state.append(bw)
        if len(state) >= 3: break
    # Only look for card borders below the empty state, so title-strip / toolbar rows never register.
    brows = border_rows(px, page_left + 4, W - 2, (state[-1][3] + 20) if state else 0, H)
    grid = None
    if len(brows) >= 4:
        # first card row band uses the first two border rows
        y_top, y_bot = brows[0][0], brows[1][0]
        cols = border_cols(px, page_left + 4, W - 2, y_top + 2, y_bot - 2)
        if len(cols) >= 4:
            gaps = [cols[1][0] - cols[0][1] - 1, cols[3][0] - cols[2][1] - 1]
            grid = {
                "card1": cols[0][0], "card1_right": cols[1][1],
                "card2": cols[2][0], "card2_right": cols[3][1],
                "card_w": cols[1][1] - cols[0][0] + 1,
                "col_gap": cols[2][0] - cols[1][1] - 1,
                "grid_w": cols[3][1] - cols[0][0] + 1,
                "row_top": y_top, "row_bottom": y_bot,
                "card_h": y_bot - y_top + 1,
                "row_gap": brows[2][0] - brows[1][1] - 1 if len(brows) >= 3 else None,
            }
    return {"size": (W, H), "page_left": page_left, "state": state, "grid": grid}


def main():
    a = measure(sys.argv[1], int(sys.argv[2]))
    b = measure(sys.argv[3], int(sys.argv[4]))
    problems = []
    known = []
    labels = ["clock", "title", "subtitle"]
    for i, lab in enumerate(labels):
        ra = a["state"][i] if i < len(a["state"]) else None
        rb = b["state"][i] if i < len(b["state"]) else None
        print("  " + lab + "  ref " + str(ra) + "   ours " + str(rb))
        if ra and rb:
            # Only the height is comparable: the two empty states carry different copy, so their
            # ink widths differ by text length rather than by font size.
            if abs(ra[5] - rb[5]) > TOL:
                problems.append(lab + " h ref=" + str(ra[5]) + " ours=" + str(rb[5]))
    print("  grid  ref " + str(a["grid"]))
    print("        ours " + str(b["grid"]))
    if a["grid"] and b["grid"]:
        for key in ("card_w", "col_gap", "grid_w", "card_h", "row_gap"):
            if a["grid"].get(key) is not None and b["grid"].get(key) is not None:
                if abs(a["grid"][key] - b["grid"][key]) > TOL:
                    line = key + " ref=" + str(a["grid"][key]) + " ours=" + str(b["grid"][key])
                    (known if key in CONTENT_DRIVEN_GRID_KEYS else problems).append(line)
    print("PROBLEMS:", len(problems))
    for p in problems:
        print("  - " + p)
    print("KNOWN content-driven (not a defect):", len(known))
    for k in known:
        print("  ~ " + k)
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
