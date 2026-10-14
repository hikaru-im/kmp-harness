#!/usr/bin/env python3
"""Re-runnable Scheduled template-card outline and heading-rhythm check for batch AE.

Reference: docs/desktop-ui/reference/captures/2026-10-06-user-pages/scheduled-tasks.png
  (density 1, Display-P3 ICC converted to sRGB before any colour read; page area starts at x=341).
Ours: docs/desktop-ui/captures/scheduled-card-outline-2026-10-11/empty-light-1440x900-viewport.png

Batch W aligned the landing grid geometry (card 359 wide, gutters 20, grid 738) and recorded the card
body as content-driven. Batch AE settles three outline/rhythm facts:

  * the reference card outline is a 1px 3-on/2-off dash, not a solid hairline (HarnessSurface dashed path);
  * the reference card corner, fit as an arc tangent to both edges (robust to dash phase), reads a radius
    of ~22.6; our 22dp HarnessTokens.Corners.Card path reads ~21.9. The top-row-dark-extent proxy is
    phase-sensitive and reads the same pair as 18 vs 17, which is why the tangent fit is the primary metric;
  * the reference heading pairs with its line on an 11px ink gap, where the surrounding 12dp rhythm drew 19px.

Usage: measure-scheduled-card.py <root> [ours.png]
"""
import io
import sys
from PIL import Image, ImageCms

REF = 'docs/desktop-ui/reference/captures/2026-10-06-user-pages/scheduled-tasks.png'
OURS = ('docs/desktop-ui/captures/scheduled-card-outline-2026-10-11/'
        'empty-light-1440x900-viewport.png')
DASH_THR = 252
INK_THR = 200


def load_srgb(path):
    im = Image.open(path)
    profile = im.info.get('icc_profile')
    rgb = im.convert('RGB')
    if not profile:
        return rgb
    src = ImageCms.ImageCmsProfile(io.BytesIO(profile))
    return ImageCms.profileToProfile(rgb, src, ImageCms.createProfile('sRGB'), outputMode='RGB')


def run_lengths(px, y, x0, x1, thr=DASH_THR):
    row = [1 if px[x, y] < thr else 0 for x in range(x0, x1)]
    out = []
    cur, n = row[0], 1
    for v in row[1:]:
        if v == cur:
            n += 1
        else:
            out.append(('#' if cur else '.', n))
            cur, n = v, 1
    out.append(('#' if cur else '.', n))
    return sum(row), out


def extent_x(px, y, x0, x1, thr=DASH_THR):
    xs = [x for x in range(x0, x1) if px[x, y] < thr]
    return (xs[0], xs[-1]) if xs else None


def extent_y(px, x, y0, y1, thr=DASH_THR):
    ys = [y for y in range(y0, y1) if px[x, y] < thr]
    return (ys[0], ys[-1]) if ys else None


def ink_bands(px, x0, x1, y0, y1, thr=INK_THR):
    out = []
    for y in range(y0, y1):
        if any(px[x, y] < thr for x in range(x0, x1)):
            if out and y - out[-1][1] <= 4:
                out[-1][1] = y
                out[-1][2] = min(out[-1][2], min(x for x in range(x0, x1) if px[x, y] < thr))
                out[-1][3] = max(out[-1][3], max(x for x in range(x0, x1) if px[x, y] < thr))
            else:
                xs = [x for x in range(x0, x1) if px[x, y] < thr]
                out.append([y, y, xs[0], xs[-1]])
    return out


def report_card(tag, px, x0, x1, y0, y1):
    # Dashed border rows hold about 55 percent dark, so 100 of 359 separates the card's top and
    # bottom edges from the interior; the same rule on columns finds the left and right edges.
    rows = [y for y in range(y0, y1) if sum(1 for x in range(x0, x1) if px[x, y] < DASH_THR) > 100]
    if len(rows) < 2:
        print('%-22s card rows not found %s' % (tag, rows))
        return
    top, bot = rows[0], rows[-1]
    cols = [x for x in range(x0, x1) if sum(1 for y in range(top, bot + 1) if px[x, y] < DASH_THR) > 25]
    if len(cols) < 2:
        print('%-22s card cols not found' % tag)
        return
    left, right = cols[0], cols[-1]
    total, runs = run_lengths(px, top, left - 4, right + 5)
    ex = extent_x(px, top, left - 4, right + 5)
    print('%-22s box x %d..%d  y %d..%d (%dx%d)  top row dark %d/%d  pattern %s'
          % (tag, left, right, top, bot, right - left + 1, bot - top + 1, total, right - left + 9,
             ''.join(c * k for c, k in runs[:16])))
    print('%-22s   top-edge x %d..%d -> corner inset left %d right %d'
          % ('', ex[0], ex[1], ex[0] - left, right - ex[1]))
    lex = extent_y(px, left, top, bot + 1)
    if lex:
        print('%-22s   left-edge y %d..%d -> corner inset top %d bottom %d'
              % ('', lex[0], lex[1], lex[0] - top, bot - lex[1]))


def corner_radius(px, bx, by, span=45, half=70, thr=DASH_THR):
    """Tangent-constrained corner radius: for every row of the top-left arc take the leftmost dark
    pixel, then solve (x-r)^2 + (y-r)^2 = r^2 for r (the arc is tangent to the top and left edges).
    The dash phase only removes samples, so the median of the per-point roots is stable.
    """
    pts = []
    for dy in range(span):
        y = by + dy
        xs = [x for x in range(bx - 3, bx + half) if px[x, y] < thr]
        if not xs:
            continue
        x = min(xs) - bx
        if x <= 1:  # reached the straight left edge, past the corner
            break
        pts.append((x, dy))
    rs = []
    for x, y in pts:
        if x < 2 or y < 2:
            continue
        # r^2 - 2r(x+y) + (x^2+y^2) = 0
        b, c = -2 * (x + y), x * x + y * y
        disc = b * b - 4 * c
        if disc < 0:
            continue
        rs.append((-b + disc ** 0.5) / 2)
    if not rs:
        return None, 0
    rs.sort()
    return rs[len(rs) // 2], len(rs)


def report_heading(tag, px, x0, x1, y0, y1):
    bands = ink_bands(px, x0, x1, y0, y1)
    print(tag)
    prev = None
    for a, b, ax, bx in bands:
        gap = '-' if prev is None else str(a - prev - 1)
        print('   rows %4d..%-4d h%2d x %4d..%-4d w%4d  ink gap above %s'
              % (a, b, b - a + 1, ax, bx, bx - ax + 1, gap))
        prev = b


def main():
    root = sys.argv[1] if len(sys.argv) > 1 else '.'
    global OURS
    if len(sys.argv) > 2:
        OURS = sys.argv[2]
    ref = load_srgb(root + '/' + REF).convert('L').load()
    our = load_srgb(root + '/' + OURS).convert('L').load()
    print('=== card outline ===')
    report_card('reference card 1', ref, 1075, 1445, 606, 740)
    # Our grid: the landing cards sit in the 1440x900 light viewport.
    report_card('ours card 1', our, 470, 840, 340, 460)
    print()
    print('=== corner radius (arc tangent to both edges) ===')
    rr, rn = corner_radius(ref, 1080, 614)
    orr, on = corner_radius(our, 475, 386)
    print('reference top-left  radius=%s (n=%d)' % (('%0.2f' % rr) if rr else 'n/a', rn))
    print('ours      top-left  radius=%s (n=%d)' % (('%0.2f' % orr) if orr else 'n/a', on))
    print()
    print('=== landing heading rhythm ===')
    report_heading('reference', ref, 1100, 1800, 440, 600)
    report_heading('ours', our, 450, 1000, 150, 320)
    return 0


if __name__ == '__main__':
    sys.exit(main())
