#!/usr/bin/env python3
"""Re-runnable subtle-border check for batch AC (card border, row dividers, control outline).

Reference: docs/desktop-ui/reference/captures/2026-10-06-user-pages/settings-general.png
  2560x1440, density 1, carries a Display-P3 ICC profile, so it is converted to sRGB before any
  colour read. Verified conversion-stable: the card border reads #eeedee raw in P3 and #efeeef under
  perceptual, relative and absolute intents, so the P3->sRGB step does not move neutral greys.
Ours: docs/desktop-ui/captures/settings-border-token-2026-10-11/page-2560x1440-light.png
  production Shell, density 1, content top y=44 (reference content top is y=74, so ours is -30).

Batch AA measured the card border, row dividers and control outline at #f5f5f5 against #efeeef.
The token was BorderSubtle = 0x147F7F7F (gray-127 at 8 percent). Alpha over white composites as
  brightness = 255 - (255 - base) * alpha
so gray-127 (127) gives 255 - 128 * alpha:
  alpha 0x14 (8.0 percent)   -> 245  (#f5f5f5)   <- old token
  alpha 0x22 (13.3 percent)  -> 238  (#eeeeee)   <- new token
  reference capture           -> 238  (#eeedee)
The current app.asar defines --color-border-subtle against --alpha-base (#0d0d0d in light), a ~6-7
percent tint of near-black, which is the same direction and strength as the new value.
"""
import io
import sys
from PIL import Image, ImageCms

REF = 'docs/desktop-ui/reference/captures/2026-10-06-user-pages/settings-general.png'
OURS = ('docs/desktop-ui/captures/settings-border-token-2026-10-11/'
        'page-2560x1440-light.png')


def load_srgb(path):
    im = Image.open(path)
    profile = im.info.get('icc_profile')
    rgb = im.convert('RGB')
    if not profile:
        return rgb
    src = ImageCms.ImageCmsProfile(io.BytesIO(profile))
    return ImageCms.profileToProfile(rgb, src, ImageCms.createProfile('sRGB'), outputMode='RGB')


def hx(p):
    return '#%02x%02x%02x' % p[:3]


# surface -> (reference pixel, our pixel). Our content top is 30px above the reference.
ROWS = [
    ('card top border', (1500, 263), (1500, 233)),
    ('row divider 1', (1500, 433), (1500, 403)),
    ('row divider 2', (1500, 520), (1500, 490)),
    ('card left edge', (1079, 700), (1079, 670)),
    ('control outline', (1720, 598), (1727, 798)),
    ('rail end hairline', (340, 700), (340, 670)),
]


def main():
    root = sys.argv[1] if len(sys.argv) > 1 else '.'
    global OURS
    if len(sys.argv) > 2:
        OURS = sys.argv[2]
    ref = load_srgb(root + '/' + REF).load()
    our = load_srgb(root + '/' + OURS).load()
    print('%-20s %-10s %-10s %s' % ('surface', 'reference', 'ours', 'delta'))
    problems = 0
    for name, rp, op in ROWS:
        r, o = hx(ref[rp[0], rp[1]]), hx(our[op[0], op[1]])
        dr, do = int(r[1:3], 16), int(o[1:3], 16)
        print('%-20s %-10s %-10s %+d' % (name, r, o, do - dr))
        # White-backed surfaces must land within 2 of the reference; the rail edge also composites
        # over the (slightly grey) rail background, so it is reported but not bounded.
        if name != 'rail end hairline' and abs(do - dr) > 2:
            problems += 1
    print()
    print('PROBLEMS: %d' % problems)
    return 1 if problems else 0


if __name__ == '__main__':
    sys.exit(main())

