#!/usr/bin/env python3
"""Re-runnable select-trigger check for batch AD (content inset + drawn chevron).

Reference: docs/desktop-ui/reference/captures/2026-10-06-user-pages/settings-general.png
  (density 1, Display-P3 ICC converted to sRGB before any colour read).
Ours: docs/desktop-ui/captures/settings-select-inset-2026-10-11/page-2560x1440-light.png
  (production Shell, density 1, content top 44; the reference content top is 74).

Batch AA created HarnessSelectTrigger but padded its content by Spacing.Three (12px) while its own doc
comment said the reference inset is 16px a side, and its 14dp chevron at glyphScale 1.5 drew 12x6 where
the reference measures 10x5. This script measures both on the reference and on one of our captures.

Usage: measure-select-trigger.py <root> [ours.png]
"""
import io
import sys
from PIL import Image, ImageCms

REF = 'docs/desktop-ui/reference/captures/2026-10-06-user-pages/settings-general.png'
OURS = ('docs/desktop-ui/captures/settings-select-inset-2026-10-11/'
        'page-2560x1440-light.png')


def load_srgb(path):
    im = Image.open(path)
    profile = im.info.get('icc_profile')
    rgb = im.convert('RGB')
    if not profile:
        return rgb
    src = ImageCms.ImageCmsProfile(io.BytesIO(profile))
    return ImageCms.profileToProfile(rgb, src, ImageCms.createProfile('sRGB'), outputMode='RGB')


def box_border_cols(px, x0, x1, y0, y1, need):
    cols = [x for x in range(x0, x1)
            if sum(1 for y in range(y0, y1) if 225 <= px[x, y] <= 252) >= need]
    out = []
    for x in cols:
        if out and x - out[-1][1] <= 2:
            out[-1][1] = x
        else:
            out.append([x, x])
    return out


def ink_blocks(px, x0, x1, y0, y1, thr):
    cols = [x for x in range(x0, x1) if any(px[x, y] < thr for y in range(y0, y1))]
    out = []
    for x in cols:
        if out and x - out[-1][1] <= 3:
            out[-1][1] = x
        else:
            out.append([x, x])
    return out


def chevron_box(px, x0, x1, y0, y1, thr=248):
    rows = [y for y in range(y0, y1) if any(px[x, y] < thr for x in range(x0, x1))]
    if not rows:
        return None
    cols = [x for x in range(x0, x1) if any(px[x, y] < thr for y in range(min(rows), max(rows) + 1))]
    if not cols:
        return None
    return min(cols), min(rows), max(cols), max(rows)


def report(tag, px, y0, y1):
    # The vertical edge of a 1px pill border only holds ~16 of 28 rows in range once the rounded
    # corners and the top/bottom hairline are excluded, so the column threshold is 10, not 22.
    edges = box_border_cols(px, 1500, 1800, y0, y1, need=10)
    if len(edges) < 2:
        print('%-28s outline not found (%s)' % (tag, edges))
        return None
    bx0, bx1 = edges[0][0], edges[-1][1]
    blocks = ink_blocks(px, bx0 + 2, bx1 - 1, y0 + 2, y1 - 2, 235)
    print('%-28s box x %d..%d  w %d' % (tag, bx0, bx1, bx1 - bx0 + 1))
    for a, b in blocks:
        print('%-28s    ink x %d..%d w %d  left inset %d  right inset %d'
              % ('', a, b, b - a + 1, a - bx0, bx1 - b))
    if blocks:
        cb = chevron_box(px, blocks[-1][0] - 2, min(bx1, blocks[-1][1] + 3), y0 + 2, y1 - 2)
        if cb:
            print('%-28s    chevron ink %dx%d  right inset %d'
                  % ('', cb[2] - cb[0] + 1, cb[3] - cb[1] + 1, bx1 - cb[2]))
    return bx0, bx1, blocks


def main():
    root = sys.argv[1] if len(sys.argv) > 1 else '.'
    global OURS
    if len(sys.argv) > 2:
        OURS = sys.argv[2]
    ref = load_srgb(root + '/' + REF).convert('L').load()
    our = load_srgb(root + '/' + OURS).convert('L').load()
    print('REFERENCE (content top 74)')
    report('ref select Automatic', ref, 659, 687)
    report('ref select Default app', ref, 598, 626)
    print()
    print('OURS (content top 44)')
    # The close-policy row is the only enabled General-page trigger; the language and default-file-app
    # triggers are disabled, and a disabled trigger fills SurfaceSecondary, so only the outline of the
    # enabled one is a white pill with a hairline. The disabled rows are a state difference, not a defect.
    report('ours select close shortcut', our, 798, 826)
    return 0


if __name__ == '__main__':
    sys.exit(main())
