#!/usr/bin/env python3
"""Render the Codex rail icon artwork from app.asar so glyph shapes can be compared, not guessed.

The rail icons ship as separate Lottie bundles inside the app.asar (for example
`sidebar-projects-icon-*.js`). This script reads the asar header, extracts the icon chunks, parses
the Lottie layers (including ellipse shapes, group transforms and parent layers), and rasterises
each icon on its native 20x20 grid next to the others.

Usage:
    python3 scripts/extract-codex-rail-icons.py [output.png] [--asar PATH] [--icons DIR]

Requires Pillow. Writes the sheet (default /tmp/codex-rail-icons.png). The committed reference sheet
is docs/desktop-ui/reference/captures/codex-rail-icons-20px.png.
"""

import argparse, json, math, os, re, struct, sys
from PIL import Image, ImageDraw

ICONS = [
    "sidebar-projects-icon",
    "sidebar-tasks-icon",
    "sidebar-plugins-icon",
    "sidebar-library-icon",
    "sidebar-images-icon",
    "sidebar-more-icon",
    "sidebar-new-chat-icon",
    "sidebar-search-icon",
]

DEFAULT_ASAR = "/Applications/ChatGPT.app/Contents/Resources/app.asar"


def extract_icons(asar_path, out_dir):
    """Copy every `webview/assets/sidebar-*.js` chunk out of the asar."""
    os.makedirs(out_dir, exist_ok=True)
    written = {}
    with open(asar_path, "rb") as f:
        _, _, _, header_len = struct.unpack("<4I", f.read(16))
        header = json.loads(f.read(header_len).decode("utf-8", "replace"))
        data_start = 16 + header_len

        def walk(node, path):
            for name, value in node.get("files", {}).items():
                full = path + "/" + name
                if "files" in value:
                    walk(value, full)
                elif name.startswith("sidebar-") and name.endswith(".js"):
                    f.seek(data_start + int(value["offset"]))
                    target = os.path.join(out_dir, name)
                    blob = f.read(int(value["size"]))
                    with open(target, "wb") as out:
                        out.write(blob)
                    written[name] = target

        walk(header, "")
    return written


def parse_lottie(path):
    """The chunks are minified JS object literals; quote keys and booleans, then parse."""
    backtick = chr(96)
    src = open(path, encoding="utf-8", errors="replace").read()
    match = re.search(r"a=\[(.*?)\],o=" + backtick, src, re.S)
    body = ("[" + match.group(1) + "]").replace(backtick, '"')
    body = re.sub(r"!1\b", "false", body)
    body = re.sub(r"!0\b", "true", body)
    body = re.sub(r"([:\[,\{])\.(\d)", r"\g<1>0.\2", body)
    body = re.sub(r"([:\[,\{])-\.(\d)", r"\g<1>-0.\2", body)
    body = re.sub(r"([{,])([A-Za-z_][A-Za-z0-9_]*):", r'\1"\2":', body)
    return json.loads(body)


def prop(node, default):
    if not isinstance(node, dict):
        return default
    key = node.get("k")
    if key is None:
        return default
    if node.get("a", 0) == 0:
        return key
    if isinstance(key, list) and key and isinstance(key[0], dict):
        return key[0].get("s", default)
    return default


def val(node, default):
    """Like [prop], but keeps a plain array value (ellipse position and size are static arrays)."""
    if not isinstance(node, dict):
        return default
    key = node.get("k", default)
    if isinstance(key, list) and key and isinstance(key[0], dict):
        return key[0].get("s", default)
    return key


def path_key(node, depth=0):
    """Drill through Lottie keyframes until the bezier path object (the one holding `v`)."""
    if depth > 6 or node is None:
        return None
    if isinstance(node, list):
        return path_key(node[0], depth + 1) if node else None
    if isinstance(node, dict):
        if "v" in node:
            return node
        for field in ("k", "s"):
            if field in node:
                return path_key(node[field], depth + 1)
    return None


def local_matrix(ks):
    p = prop(ks.get("p"), [0, 0])
    s = prop(ks.get("s"), [100, 100])
    r = prop(ks.get("r"), 0)
    a = prop(ks.get("a"), [0, 0])
    p = p if isinstance(p, (list, tuple)) and len(p) >= 2 else [0, 0]
    s = s if isinstance(s, (list, tuple)) and len(s) >= 2 else [100, 100]
    a = a if isinstance(a, (list, tuple)) and len(a) >= 2 else [0, 0]
    r = r if isinstance(r, (int, float)) else 0
    sx, sy = s[0] / 100.0, s[1] / 100.0
    ca, sa = math.cos(math.radians(r)), math.sin(math.radians(r))
    m00, m01, m10, m11 = sx * ca, -sy * sa, sx * sa, sy * ca
    return (m00, m10, m01, m11, p[0] - (m00 * a[0] + m01 * a[1]), p[1] - (m10 * a[0] + m11 * a[1]))


def mat_mul(a, b):
    return (a[0] * b[0] + a[2] * b[1], a[1] * b[0] + a[3] * b[1],
            a[0] * b[2] + a[2] * b[3], a[1] * b[2] + a[3] * b[3],
            a[0] * b[4] + a[2] * b[5] + a[4], a[1] * b[4] + a[3] * b[5] + a[5])


def apply(m, p):
    return (m[0] * p[0] + m[2] * p[1] + m[4], m[1] * p[0] + m[3] * p[1] + m[5])


def layer_matrix(layer, by_index, depth=0):
    matrix = local_matrix(layer.get("ks", {}))
    parent = layer.get("parent")
    if parent is not None and depth < 8 and parent in by_index:
        return mat_mul(layer_matrix(by_index[parent], by_index, depth + 1), matrix)
    return matrix


def bezier(p0, p1, p2, p3, steps=24):
    points = []
    for i in range(steps + 1):
        t = i / steps
        mt = 1 - t
        points.append((mt ** 3 * p0[0] + 3 * mt * mt * t * p1[0] + 3 * mt * t * t * p2[0] + t ** 3 * p3[0],
                       mt ** 3 * p0[1] + 3 * mt * mt * t * p1[1] + 3 * mt * t * t * p2[1] + t ** 3 * p3[1]))
    return points


def path_points(key):
    vertices, out_t, in_t = key["v"], key["o"], key["i"]
    closed = key.get("c", False)
    count = len(vertices)
    points = []
    for index in range(count if closed else count - 1):
        a, b = vertices[index], vertices[(index + 1) % count]
        points += bezier(tuple(a), (a[0] + out_t[index][0], a[1] + out_t[index][1]),
                         (b[0] + in_t[(index + 1) % count][0], b[1] + in_t[(index + 1) % count][1]), tuple(b))
    if closed and points:
        points.append(points[0])
    return points, closed


def render(layers, zoom=16, size=20, color=(32, 34, 36, 255)):
    canvas = Image.new("RGBA", (size * zoom, size * zoom), (0, 0, 0, 0))
    draw = ImageDraw.Draw(canvas)
    by_index = {layer.get("ind"): layer for layer in layers if isinstance(layer, dict)}
    for layer in layers:
        matrix = layer_matrix(layer, by_index)
        for group in layer.get("shapes", []):
            if not isinstance(group, dict):
                continue
            group_matrix = matrix
            stroke = None
            geometries = []
            for item in group.get("it", []):
                if not isinstance(item, dict):
                    continue
                kind = item.get("ty")
                if kind == "tr":
                    group_matrix = mat_mul(matrix, local_matrix(item))
                elif kind == "st":
                    stroke = item
                elif kind == "sh":
                    key = path_key(item.get("ks", {}))
                    if isinstance(key, dict) and "v" in key:
                        geometries.append(("path", key))
                elif kind == "el":
                    centre = val(item.get("p", {}), None)
                    extent = val(item.get("s", {}), None)
                    if isinstance(centre, list) and isinstance(extent, list):
                        geometries.append(("ellipse", (centre, extent)))
            for kind, data in geometries:
                if kind == "path":
                    raw, closed = path_points(data)
                else:
                    cx, cy = data[0]
                    rx, ry = data[1][0] / 2.0, data[1][1] / 2.0
                    raw = [(cx + rx * math.cos(2 * math.pi * i / 72), cy + ry * math.sin(2 * math.pi * i / 72)) for i in range(73)]
                    closed = True
                points = [(x * zoom, y * zoom) for x, y in (apply(group_matrix, p) for p in raw)]
                if stroke is None:
                    continue
                width = prop(stroke.get("w"), 1.0)
                width = width if isinstance(width, (int, float)) else 1.0
                determinant = abs(group_matrix[0] * group_matrix[3] - group_matrix[1] * group_matrix[2])
                scale = math.sqrt(determinant) if determinant > 0 else 1.0
                line_width = max(1, int(round(width * scale * zoom)))
                if closed and len(points) > 2:
                    points = points + [points[0]]
                draw.line(points, fill=color, width=line_width, joint="curve")
                radius = line_width / 2.0
                for point in (points[0], points[-1]):
                    draw.ellipse([point[0] - radius, point[1] - radius, point[0] + radius, point[1] + radius], fill=color)
    return canvas


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("output", nargs="?", default="/tmp/codex-rail-icons.png")
    parser.add_argument("--asar", default=DEFAULT_ASAR)
    parser.add_argument("--icons", default="/tmp/codex-asar-icons", help="directory for extracted icon chunks")
    parser.add_argument("--zoom", type=int, default=16)
    args = parser.parse_args()

    written = extract_icons(args.asar, args.icons)
    missing = [name for name in ICONS if not any(name in key for key in written)]
    if missing:
        print("missing icon chunks: " + ", ".join(missing), file=sys.stderr)
        return 2

    zoom = args.zoom
    tiles = []
    for prefix in ICONS:
        chunk = next(path for name, path in sorted(written.items()) if name.startswith(prefix))
        tiles.append(render(parse_lottie(chunk), zoom=zoom))
    cell = 20 * zoom + 16
    sheet = Image.new("RGBA", (len(tiles) * cell, cell), (255, 255, 255, 255))
    for index, tile in enumerate(tiles):
        sheet.paste(tile, (index * cell + 8, 8), tile)
    sheet.save(args.output)
    print("wrote " + args.output + " " + str(sheet.size))
    print("order: " + ", ".join(ICONS))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
