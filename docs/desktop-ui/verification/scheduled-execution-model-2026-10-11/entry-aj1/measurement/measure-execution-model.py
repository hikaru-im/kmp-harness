#!/usr/bin/env python3
"""Re-runnable check for batch AJ: the local execution-model disclosure and the
blocked/Unknown-run explanation actually render, and did not exist before this batch.

Inputs (offscreen captures, density 1):
  prev landing  : captures/scheduled-card-outline-2026-10-11/landing-light-1440x900-viewport.png
  curr landing  : captures/scheduled-execution-model-2026-10-11/landing-light-1440x900-viewport.png
  curr unknown  : captures/scheduled-execution-model-2026-10-11/unknown-light-1440x900-viewport.png
Bounds come from the matching *.geometry.json sidecars, not hard-coded guesses.
"""
import json
from pathlib import Path
from PIL import Image

ROOT = Path("docs/desktop-ui/captures")
PREV = ROOT / "scheduled-card-outline-2026-10-11"
CURR = ROOT / "scheduled-execution-model-2026-10-11"

def sidecar_bounds(png: Path, tag: str):
    d = json.loads(png.with_suffix(".geometry.json").read_text())
    for entry in d.get("visibleKeyTags", []):
        if entry["tag"] == tag:
            return entry["bounds"]
    return None

def ink_bbox(img, box, thresh=200):
    x0, y0, x1, y1 = box
    l = t = None
    r = b = -1
    count = 0
    for y in range(max(0, y0), min(img.height, y1 + 1)):
        for x in range(max(0, x0), min(img.width, x1 + 1)):
            if min(img.getpixel((x, y))) < thresh:
                count += 1
                l = x if l is None else min(l, x)
                t = y if t is None else min(t, y)
                r = max(r, x)
                b = max(b, y)
    return (count, (l, t, r, b) if count else None)

problems = []

prev_landing = Image.open(PREV / "landing-light-1440x900-viewport.png").convert("RGB")
curr_landing = Image.open(CURR / "landing-light-1440x900-viewport.png").convert("RGB")
curr_unknown = Image.open(CURR / "unknown-light-1440x900-viewport.png").convert("RGB")

# 1. The notice band must be blank before this batch and inked after.
band = (268, 4, 1420, 28)
prev_ink = ink_bbox(prev_landing, band)
curr_ink = ink_bbox(curr_landing, band)
print("notice band y=4..28 x=268..1420")
print("  prev (batch AE) ink pixels:", prev_ink[0], "bbox", prev_ink[1])
print("  curr (batch AJ) ink pixels:", curr_ink[0], "bbox", curr_ink[1])
if prev_ink[0] != 0:
    problems.append("prior capture already had ink in the notice band")
if curr_ink[0] == 0:
    problems.append("current capture has no ink where the notice must render")

# 2. The notice's own geometry box must be non-empty and near the top of the page.
nb = sidecar_bounds(CURR / "landing-light-1440x900-viewport.png", "scheduled-execution-model")
print("  notice sidecar bounds:", nb)
if not nb or nb["height"] <= 0 or nb["width"] <= 0:
    problems.append("notice node has empty bounds")
if nb and nb["top"] > 40:
    problems.append("notice is not near the top of the page")

# 3. The blocked/Unknown explanation must render in the Unknown detail state.
bb = sidecar_bounds(CURR / "unknown-light-1440x900-viewport.png", "scheduled-blocked-runs")
rb = sidecar_bounds(CURR / "unknown-light-1440x900-viewport.png", "scheduled-run-blocked")
print("unknown state: run-blocked bounds", rb, "blocked-runs bounds", bb)
if not bb:
    problems.append("blocked-runs explanation node is missing in the Unknown state")
else:
    box = (int(bb["left"]), int(bb["top"]), int(bb["right"]), int(bb["bottom"]))
    ink = ink_bbox(curr_unknown, box)
    print("  blocked-runs ink pixels:", ink[0], "bbox", ink[1])
    if ink[0] == 0:
        problems.append("blocked-runs explanation has no visible ink")
    if ink[1] and (ink[1][3] - ink[1][1]) < 8:
        problems.append("blocked-runs explanation is too short to be the disclosure text")

print("PROBLEMS:", len(problems))
for p in problems:
    print("  -", p)
