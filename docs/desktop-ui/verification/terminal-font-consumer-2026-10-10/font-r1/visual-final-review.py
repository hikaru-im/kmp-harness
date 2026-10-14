from pathlib import Path
from datetime import datetime, timezone
import hashlib
import json
import re
from PIL import Image

ROOT = Path('/tmp/harness-terminal-font-evidence-20261010')
CAPTURES = ROOT / 'PtyTerminalPageInteractionTest/captures'
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
images = []
matrices = sorted(CAPTURES.glob('*.matrix.json'))
assert len(matrices) == len(list(CAPTURES.glob('*.png'))) == 12
for path in matrices:
    stem = path.name.removesuffix('.matrix.json')
    png = CAPTURES / (stem + '.png')
    geometry = CAPTURES / (stem + '.geometry.txt')
    m = json.loads(path.read_text())
    g = geometry.read_text()
    with Image.open(png) as image:
        assert image.size == (m['width'], m['height']), stem
        assert m['full_page'] and 'coordinates=window-local' in g, stem
        assert f"viewport={m['width']}x{m['height']} theme={m['theme']} state={m['state']}" in g, stem
        assert m['revision'] == m['rendered_revision'], stem
        sample = re.search(r'background_sample=\((\d+),(\d+)\) expected=(-?\d+) actual=(-?\d+)', g)
        assert sample, stem
        x, y, expected, actual = map(int, sample.groups())
        assert expected == actual == m['background_expected_argb'] == m['background_actual_argb'], stem
        expected_rgb = (249, 249, 249) if m['theme'] == 'light' else (32, 32, 32)
        encoded_rgb = tuple((expected & 0xffffffff) >> shift & 255 for shift in (16, 8, 0))
        assert encoded_rgb == expected_rgb, stem
        actual_rgb = image.convert('RGB').getpixel((x, y))
        assert actual_rgb == expected_rgb, (stem, actual_rgb)
    bounds = {}
    for name, values in re.findall(r'^([^=\n]+)=Rect.fromLTRB\(([^)]+)\)$', g, re.M):
        left, top, right, bottom = map(float, values.split(','))
        assert 0 <= left < right <= m['width'] and 0 <= top < bottom <= m['height'], (stem, name)
        bounds[name] = dict(left=left, top=top, right=right, bottom=bottom)
    assert {'live-thread', 'pty-terminal-panel', 'pty-terminal-create', 'pty-terminal-actions'} <= bounds.keys(), stem
    assert bool(m['menu_open']) == ('pty-terminal-action-menu' in bounds), stem
    if m['revision'] is None:
        assert m['state'] == 'Unavailable' and m['controlled'] and m['key'] is None, stem
        assert 'rendered=null' in g and 'pty-terminal-grid' not in bounds, stem
    else:
        assert set(m['key']) == {'scope', 'session', 'incarnation', 'terminal'} and all(m['key'].values()), stem
        description = f"{m['columns']} columns {m['rows']} rows; revision {m['revision']}; {m['lifecycle']}"
        assert m['rendered_description'] == description and f'rendered={description}' in g, stem
        if m['state'] == 'Exited':
            assert m['lifecycle'] == 'Exited(code=7)', stem
        else:
            assert m['lifecycle'] == 'Running', stem
        if m['state'] == 'Unknown':
            assert m['controlled'], stem
    images.append(dict(file=str(png.relative_to(ROOT)), sha256=sha(png),
        matrix_file=str(path.relative_to(ROOT)), matrix_sha256=sha(path),
        geometry_file=str(geometry.relative_to(ROOT)), geometry_sha256=sha(geometry),
        metadata=m, bounds=bounds, background_sample=[x,y], actual_background_rgb=actual_rgb,
        original_size_viewed=True))
font_path = ROOT / 'font-geometry-review.json'
font = json.loads(font_path.read_text())
assert font['passed'] and font['full_page_png_count'] == len(font['images']) == 4
for row in font['images']:
    assert sha(ROOT / row['file']) == row['sha256'] and row['original_size_viewed']
review = dict(passed=True, reviewed_at=datetime.now(timezone.utc).isoformat(),
    reviewer='parent', font_full_page_png_count=4, page_full_page_png_count=12,
    font_geometry_review='font-geometry-review.json', font_geometry_review_sha256=sha(font_path),
    all_original_size_viewed=True, page_images=images,
    automated_checks=['Full PNG viewport dimensions and window-local geometry',
        'Declared revision equals rendered revision and rendered description',
        'Independent light F9F9F9 and dark 202020 theme background pixels',
        'Owned complete terminal key, lifecycle retention and menu viewport bounds',
        'All four font capture hashes match independently reviewed font geometry'],
    manual_findings=[
        'All four font images and twelve page images were viewed at original resolution earlier in this continuing parent verification.',
        'Readable output, Chinese, emoji and ANSI emphasis persist across light/dark and wide/compact pages; exited output remains visible.',
        'Controlled Unknown and Unavailable retain explicit unavailable/uncertain actions; Unknown may display its last Running snapshot.',
        'Shared action menus remain inside the window. Existing compact panel overlap is retained.',
        'Exited captures retain final terminal size rather than recomputing dimensions from their shorter error-state grid.',
        'Before font images show READY_0 and after images show DRAIN; these are not same-content original Codex pixel comparisons.'],
    functional_evidence='test-results.json', native_window=False, native_ime=False,
    remote=False, original_pixel_parity=False, other_platform_launch=False,
    overall_goal='active')
(ROOT / 'visual-review.json').write_text(json.dumps(review, indent=2, ensure_ascii=False) + '\n')
print('Independent visual review passed: 4 font captures + 12 page captures')
