#!/usr/bin/env python3
"""Re-runnable geometry check for the native window captures of batch BY.

Decodes the captured PNGs with the standard library only (zlib + struct), so the check does not
depend on Pillow being installed. It only reads the images and prints measurements; it writes
nothing and exits 0 when no problem was found.

Usage: python3 measure-native-pages.py [capture-dir]
"""

import hashlib
import struct
import sys
import zlib
from pathlib import Path


def load_png(path):
    data = path.read_bytes()
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise SystemExit(f"{path}: not a PNG")
    pos = 8
    width = height = bit_depth = color_type = interlace = None
    idat = bytearray()
    while pos < len(data):
        (length,) = struct.unpack(">I", data[pos:pos + 4])
        kind = data[pos + 4:pos + 8]
        body = data[pos + 8:pos + 8 + length]
        pos += 12 + length
        if kind == b"IHDR":
            width, height, bit_depth, color_type, _comp, _filt, interlace = struct.unpack(">IIBBBBB", body)
        elif kind == b"IDAT":
            idat += body
        elif kind == b"IEND":
            break
    if bit_depth != 8 or interlace != 0 or color_type not in (2, 6):
        raise SystemExit(f"{path}: unsupported PNG (bitDepth={bit_depth} colorType={color_type} interlace={interlace})")
    channels = 4 if color_type == 6 else 3
    raw = zlib.decompress(bytes(idat))
    stride = width * channels
    rows = []
    previous = bytearray(stride)
    offset = 0
    for _y in range(height):
        filter_type = raw[offset]
        offset += 1
        line = bytearray(raw[offset:offset + stride])
        offset += stride
        if filter_type == 1:
            for i in range(channels, stride):
                line[i] = (line[i] + line[i - channels]) & 0xFF
        elif filter_type == 2:
            for i in range(stride):
                line[i] = (line[i] + previous[i]) & 0xFF
        elif filter_type == 3:
            for i in range(stride):
                left = line[i - channels] if i >= channels else 0
                line[i] = (line[i] + ((left + previous[i]) >> 1)) & 0xFF
        elif filter_type == 4:
            for i in range(stride):
                a = line[i - channels] if i >= channels else 0
                b = previous[i]
                c = previous[i - channels] if i >= channels else 0
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[i] = (line[i] + pr) & 0xFF
        rows.append(bytes(line))
        previous = line
    return width, height, channels, rows


def pixel(rows, channels, x, y):
    row = rows[y]
    i = x * channels
    return row[i], row[i + 1], row[i + 2]


def traffic_light_bbox(rows, channels, width, height):
    count = 0
    x0 = y0 = 10 ** 6
    x1 = y1 = -1
    for y in range(min(40, height)):
        for x in range(min(80, width)):
            r, g, b = pixel(rows, channels, x, y)
            if max(r, g, b) - min(r, g, b) > 40 and max(r, g, b) > 120:
                count += 1
                x0, y0 = min(x0, x), min(y0, y)
                x1, y1 = max(x1, x), max(y1, y)
    return count, (x0, y0, x1, y1) if count else None


def title_divider(rows, channels, x):
    """First row (below the top-edge highlight at y=0) that leaves the title-strip colour."""
    strip = pixel(rows, channels, x, 2)
    for y in range(3, 90):
        current = pixel(rows, channels, x, y)
        if max(abs(a - b) for a, b in zip(strip, current)) >= 8:
            return y
    return None


def vertical_edges(rows, channels, y, lo, hi):
    edges = []
    for x in range(lo + 1, hi):
        a = pixel(rows, channels, x - 1, y)
        b = pixel(rows, channels, x, y)
        if max(abs(p - q) for p, q in zip(a, b)) >= 6:
            edges.append(x)
    return edges


def region_ink(rows, channels, background, x0, x1, y0, y1):
    """Pixels differing from `background` by >= 40 in any channel, with their bounding box."""
    count = 0
    bx0 = by0 = 10 ** 6
    bx1 = by1 = -1
    for y in range(y0, y1):
        for x in range(x0, x1):
            if max(abs(a - b) for a, b in zip(pixel(rows, channels, x, y), background)) >= 40:
                count += 1
                bx0, by0 = min(bx0, x), min(by0, y)
                bx1, by1 = max(bx1, x), max(by1, y)
    return count, ((bx0, by0, bx1, by1) if count else None)


def main():
    # .../docs/desktop-ui/verification/<entry-dir>/entry-by1/measurement/this-file → parents[4] = docs/desktop-ui
    root = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parents[4] / "captures" / "native"
    problems = 0
    names = sorted(p for p in root.glob("*.png") if p.name.startswith(("plugins-native-", "scheduled-native-")))
    if not names:
        print(f"PROBLEM: no captures found under {root}")
        return 1
    for path in names:
        width, height, channels, rows = load_png(path)
        count, bbox = traffic_light_bbox(rows, channels, width, height)
        divider = title_divider(rows, channels, 700)
        edges = vertical_edges(rows, channels, 400, 40, 360)
        rail = pixel(rows, channels, 20, 400)
        navigator = pixel(rows, channels, 200, 400)
        content = pixel(rows, channels, 1000, 400)
        nav_ink, nav_bbox = region_ink(rows, channels, navigator, 52, 341, 44, 900)
        content_ink, content_bbox = region_ink(rows, channels, content, 341, 1440, 44, 900)
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        ok = True
        if (width, height) != (1440, 900):
            print(f"PROBLEM {path.name}: size {width}x{height}")
            ok = False
        if count < 200:
            print(f"PROBLEM {path.name}: only {count} traffic-light pixels (not a real window capture?)")
            ok = False
        if divider != 44:
            print(f"PROBLEM {path.name}: title strip divider at y={divider}, expected 44")
            ok = False
        if edges[:4] != [51, 52, 340, 341]:
            print(f"PROBLEM {path.name}: shell edges {edges[:8]} do not start with [51, 52, 340, 341]")
            ok = False
        problems += 0 if ok else 1
        print(f"{path.name}: {width}x{height} sha256={digest[:16]} traffic={count} bbox={bbox} "
              f"titleDivider={divider} shellEdges={edges[:8]} rail={rail} nav={navigator} content={content}")
        print(f"  navigator ink {nav_ink} bbox={nav_bbox}; content ink {content_ink} bbox={content_bbox}")
    print(f"PROBLEMS {problems}")
    return 1 if problems else 0


if __name__ == "__main__":
    raise SystemExit(main())
