#!/usr/bin/env python3
"""Re-runnable control-geometry check for batch AA (Settings right-hand controls).

Reference: docs/desktop-ui/reference/captures/2026-10-06-user-pages/settings-general.png
  2560x1440 density 1; carries a Display-P3 ICC profile, so it is converted to sRGB before any colour
  read (geometry is unaffected). Reference content top y=74.
Ours: docs/desktop-ui/captures/settings-controls-2026-10-11/page-2560x1440-light.png
  production Shell, density 1, content top y=44.

Corner radius is fitted from the left-edge profile at the 50 percent coverage threshold of each
control, because a raw pixel inset is not the radius: for a corner radius r the first row (its centre
0.5px below the top edge) starts at inset r - sqrt(2*r*0.5 - 0.5*0.5).
"""
import io
import math
import sys
from PIL import Image, ImageCms

REF = 'docs/desktop-ui/reference/captures/2026-10-06-user-pages/settings-general.png'
OURS = 'docs/desktop-ui/captures/settings-controls-2026-10-11/page-2560x1440-light.png'


def load_srgb(path):
    im = Image.open(path)
    profile = im.info.get('icc_profile')
    rgb = im.convert('RGB')
    if not profile:
        return rgb
    src = ImageCms.ImageCmsProfile(io.BytesIO(profile))
    return ImageCms.profileToProfile(rgb, src, ImageCms.createProfile('sRGB'), outputMode='RGB')


def lum(p):
    return (p[0] + p[1] + p[2]) // 3


def hx(p):
    return '#%02x%02x%02x' % p[:3]


def outer(px, region, thr=252):
    x0, x1, y0, y1 = region
    xs, ys = [], []
    for y in range(y0, y1):
        for x in range(x0, x1):
            if lum(px[x, y]) < thr:
                xs.append(x)
                ys.append(y)
    if not xs:
        return None
    return min(xs), min(ys), max(xs), max(ys)


def predicted(radius):
    out = []
    for dy in range(8):
        c = dy + 0.5
        inside = max(0.0, 2.0 * radius * c - c * c)
        out.append(max(0.0, radius - math.sqrt(inside)))
    return out


def fit_radius(insets):
    best, best_err = None, None
    r = 2.0
    while r <= 18.0:
        pred = predicted(r)
        err = 0.0
        for i, m in enumerate(insets):
            if m is None:
                continue
            err += (pred[i] - m) ** 2
        if best_err is None or err < best_err:
            best, best_err = r, err
        r += 0.25
    return best


def control(label, px, region, fill_at, top):
    b = outer(px, region)
    if b is None:
        print('%-26s not found' % label)
        return
    x0, y0, x1, y1 = b
    edge = lum(px[(x0 + x1) // 2, y0])
    thr = int(round(255 - 0.5 * (255 - edge)))
    insets = []
    for dy in range(8):
        xs = [x for x in range(max(0, x0 - 4), x0 + 30) if lum(px[x, y0 + dy]) < thr]
        insets.append((xs[0] - x0) if xs else None)
    radius = fit_radius(insets)
    print('%-26s bbox x %4d..%-4d w %3d  y %4d..%-4d h %2d  rel_top %3d'
          % (label, x0, x1, x1 - x0 + 1, y0, y1, y1 - y0 + 1, y0 - top))
    print('%-26s fill %s  edge %s  thr %d  insets %s  fitted radius %.2f'
          % ('', hx(px[fill_at[0], fill_at[1]]), hx(px[(x0 + x1) // 2, y0]), thr,
             [i if i is not None else '-' for i in insets], radius))


def main():
    root = sys.argv[1] if len(sys.argv) > 1 else '.'
    global OURS
    if len(sys.argv) > 2:
        OURS = sys.argv[2]
    ref = load_srgb(root + '/' + REF).load()
    our = load_srgb(root + '/' + OURS).load()
    print('REFERENCE (content top 74)')
    control('compact button (change)', ref, (1740, 1796, 531, 570), (1750, 548), 74)
    control('select (Default app)', ref, (1645, 1796, 592, 631), (1660, 611), 74)
    control('select (4 CJK)', ref, (1688, 1796, 653, 692), (1700, 672), 74)
    control('switch (on)', ref, (1751, 1796, 286, 317), (1762, 296), 74)
    print()
    print('OURS (content top 44)')
    control('compact button (change)', our, (1720, 1798, 506, 548), (1746, 528), 44)
    control('select (close shortcut)', our, (1664, 1798, 792, 832), (1672, 812), 44)
    control('select (2 CJK)', our, (1716, 1798, 652, 692), (1726, 672), 44)
    control('switch (disabled off)', our, (1750, 1798, 256, 288), (1770, 268), 44)
    return 0


if __name__ == '__main__':
    sys.exit(main())
