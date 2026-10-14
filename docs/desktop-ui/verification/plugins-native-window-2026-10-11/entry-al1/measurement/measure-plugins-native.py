#!/usr/bin/env python3
"""Re-runnable geometry check for batch AL: the plugin inventory page renders in a
real macOS window (title strip + traffic lights + rail/navigator columns), not only
in the off-screen Compose test.

Inputs (native captures, density 1):
  captures/native/plugins-native-dark-1440x900.png
  captures/native/plugins-native-light-1440x900.png
  captures/native/plugins-native-system-1440x900.png

Everything is measured from pixels: nothing is hard-coded except the band bounds
used to isolate traffic lights, the title divider row, and the rail/navigator columns,
which are the same window chrome documented in docs/desktop-ui/native-window-acceptance.md.
"""
from pathlib import Path
from PIL import Image

NATIVE = Path("docs/desktop-ui/captures/native")
NAMES = {
    "dark": NATIVE / "plugins-native-dark-1440x900.png",
    "light": NATIVE / "plugins-native-light-1440x900.png",
    "system": NATIVE / "plugins-native-system-1440x900.png",
}


def colored_cluster(img, box):
    """Count strongly-saturated pixels (macOS traffic lights are red/yellow/green)."""
    x0, y0, x1, y1 = box
    count = 0
    l = t = None
    r = b = -1
    for y in range(y0, min(img.height, y1)):
        for x in range(x0, min(img.width, x1)):
            px = img.getpixel((x, y))
            if max(px) - min(px) > 60:
                count += 1
                l = x if l is None else min(l, x)
                t = y if t is None else min(t, y)
                r = max(r, x)
                b = max(b, y)
    return count, (l, t, r, b) if count else None


def column_transitions(img, y, x_start, x_end, tol=6):
    """Return x positions where a full row segment shifts colour beyond tol."""
    out = []
    prev = img.getpixel((x_start, y))
    for x in range(x_start + 1, x_end):
        cur = img.getpixel((x, y))
        if max(abs(a - b) for a, b in zip(prev, cur)) > tol:
            out.append(x)
        prev = cur
    return out


def row_transitions(img, x, y_start, y_end, tol=6):
    out = []
    prev = img.getpixel((x, y_start))
    for y in range(y_start + 1, y_end):
        cur = img.getpixel((x, y))
        if max(abs(a - b) for a, b in zip(prev, cur)) > tol:
            out.append(y)
        prev = cur
    return out


def ink_bbox(img, box, delta=40):
    """Theme-neutral ink: the modal surface colour is the background, and any pixel
    further than `delta` (max channel gap) from it is content. Works for light and
    dark palettes without assuming which way text sits."""
    x0, y0, x1, y1 = box
    x1 = min(img.width - 1, x1)
    y1 = min(img.height - 1, y1)
    counts = {}
    for y in range(max(0, y0), y1 + 1):
        for x in range(max(0, x0), x1 + 1):
            px = img.getpixel((x, y))
            counts[px] = counts.get(px, 0) + 1
    bg = max(counts, key=counts.get)
    l = t = None
    r = b = -1
    count = 0
    for y in range(max(0, y0), y1 + 1):
        for x in range(max(0, x0), x1 + 1):
            px = img.getpixel((x, y))
            if max(abs(a - c) for a, c in zip(px, bg)) > delta:
                count += 1
                l = x if l is None else min(l, x)
                t = y if t is None else min(t, y)
                r = max(r, x)
                b = max(b, y)
    return bg, count, (l, t, r, b) if count else None


problems = []
imgs = {}
for theme, path in NAMES.items():
    if not path.exists():
        problems.append(f"missing capture {path}")
        continue
    img = Image.open(path).convert("RGB")
    imgs[theme] = img
    pw, ph = img.size
    print(f"[{theme}] {path.name}  size={pw}x{ph}")
    if (pw, ph) != (1440, 900):
        problems.append(f"{theme}: unexpected size {pw}x{ph}")

    lights, lbox = colored_cluster(img, (0, 0, 90, 26))
    print(f"  traffic lights: colored_px={lights} bbox={lbox}")
    if lights == 0:
        problems.append(f"{theme}: no traffic-light colour cluster (title bar not captured?)")

    # Title strip divider: a horizontal rule near y=44 separates title strip from content.
    rt = row_transitions(img, 700, 0, 60)
    print(f"  row transitions x=700 y=0..60 -> {rt[:6]}")

    # Rail / navigator / content column boundaries along a content-row scan.
    ct = column_transitions(img, 400, 0, 420)
    print(f"  column transitions y=400 x=0..420 -> {ct[:10]}")

    # Content pane ink must be non-trivial (the plugin page renders real text).
    bg, ink, ibox = ink_bbox(img, (352, 60, 1439, 899))
    print(f"  content bg={bg} ink px={ink} bbox={ibox}")
    if ink < 2000:
        problems.append(f"{theme}: content pane almost empty (ink={ink})")

    # Navigator column (x 52..340) must carry the plugin list rows.
    nbg, nink, nbox = ink_bbox(img, (52, 90, 340, 899))
    print(f"  navigator bg={nbg} ink px={nink} bbox={nbox}")
    if nink < 800:
        problems.append(f"{theme}: navigator column almost empty (ink={nink})")

print()
if imgs.get("light") is not None and imgs.get("system") is not None:
    same = imgs["light"].tobytes() == imgs["system"].tobytes()
    print(f"light == system (host appearance is Light): {same}")

print()
if problems:
    print("PROBLEMS:")
    for p in problems:
        print(" -", p)
    raise SystemExit(1)
print("PROBLEMS 0")
