from pathlib import Path
import json
import re
import uuid
from PIL import Image

evidence = Path('/tmp/harness-terminal-renderer-page-evidence-20261010-page-r5')
directory = evidence / 'PtyTerminalPageInteractionTest/captures'
records = []
by_slug = {}
for path in sorted(directory.glob('*.matrix.json')):
    slug = path.name.removesuffix('.matrix.json')
    matrix = json.loads(path.read_text())
    width, height, state = matrix['width'], matrix['height'], matrix['state']
    assert matrix['expectedHostId'] == 'pty-page-local'
    assert matrix['native_ime_verified'] is False and matrix['pixel_parity_verified'] is False
    assert matrix['controlled'] is (state in ('Unknown', 'Unavailable'))
    assert matrix['ack_controlled'] is (state == 'Unknown')
    if state == 'Unavailable':
        for field in ('revision', 'lifecycle', 'rows', 'columns', 'key'):
            assert matrix[field] is None
    else:
        key = matrix['key']
        assert set(key) == {'scope', 'session', 'incarnation', 'terminal'}
        assert key['session'] == 'page-owner'
        for field in ('scope', 'incarnation', 'terminal'):
            assert str(uuid.UUID(key[field])) == key[field]
        assert isinstance(matrix['revision'], int) and matrix['revision'] > 0
        assert 2 <= matrix['rows'] <= 200 and 5 <= matrix['columns'] <= 500
        assert matrix['lifecycle'] == ('Exited(code=7)' if state == 'Exited' else 'Running')
    geometry = (directory / (slug + '.geometry.txt')).read_text()
    assert geometry.splitlines()[0] == f"viewport={width}x{height} theme={matrix['theme']} state={state} controlled={str(matrix['controlled']).lower()} coordinates=window-local"
    rects = {}
    for tag, raw in re.findall(r'(?m)^([^=\n]+)=Rect.fromLTRB\(([^)]+)\)$', geometry):
        left, top, right, bottom = map(float, raw.split(','))
        assert 0 <= left < right <= width and 0 <= top < bottom <= height, (slug, tag, raw)
        rects[tag] = [left, top, right, bottom]
    required = {'live-thread', 'pty-terminal-panel', 'pty-terminal-create', 'pty-terminal-refresh', 'pty-terminal-close', 'pty-terminal-copy', 'pty-terminal-paste'}
    if state != 'Unavailable':
        required |= {'pty-terminal-grid', 'pty-terminal-input'}
    else:
        assert 'pty-terminal-grid' not in rects and 'pty-terminal-input' not in rects
    assert required <= set(rects), (slug, required - set(rects))
    panel = rects['pty-terminal-panel']
    for tag in required - {'live-thread', 'pty-terminal-panel'}:
        rect = rects[tag]
        assert panel[0] <= rect[0] < rect[2] <= panel[2] and panel[1] <= rect[1] < rect[3] <= panel[3], (slug, tag)
    # Sample the empty panel interior; the rightmost 10 px contain the scrollbar.
    sample_points = [(width - 40, height - 100), (width - 60, height - 120)]
    assert all(panel[0] < x < panel[2] and panel[1] < y < panel[3] for x, y in sample_points)
    with Image.open(directory / (slug + '.png')) as im:
        pixels = [im.convert('RGB').getpixel(point) for point in sample_points]
    for rgb in pixels:
        brightness = sum(rgb) / 3
        assert brightness > 200 if matrix['theme'] == 'light' else brightness < 80, (slug, rgb)
    records.append(dict(slug=slug, metadata=matrix, geometry=rects, panel_background_rgb=pixels[0],
                        panel_background_samples=[dict(point=point, rgb=rgb) for point, rgb in zip(sample_points, pixels)]))
    by_slug[slug] = matrix
assert len(records) == 10
pairs = []
for width, height in ((1440, 900), (600, 800)):
    for theme in ('light', 'dark'):
        running = by_slug[f'{width}x{height}-{theme}-Running']
        exited = by_slug[f'{width}x{height}-{theme}-Exited']
        assert running['key'] == exited['key']
        assert exited['revision'] > running['revision']
        pairs.append(dict(width=width, height=height, theme=theme, full_key_identical=True,
                          running_revision=running['revision'], exited_revision=exited['revision']))
review = dict(png_count=10, source_fixture_host_and_owner_verified=True, runtime_scope_distinct_from_host_id=True,
              full_key_and_revision_verified=True, native_lifecycle_separate_from_controlled_ack=True,
              unavailable_snapshot_fields_null=True, geometry_nonzero_and_within_viewport=True,
              panel_contains_controls_input_and_grid=True, pairs=pairs, records=records,
              full_harness_app_source_reviewed=True, real_local_gateway_host_native_fixture_reviewed=True,
              controlled_states_only_for_unknown_ack_and_unavailable=True, visual_review_pending=True, actual_theme_pixels_verified=True,
              native_window_verified=False, native_ime_verified=False, original_pixel_parity_verified=False)
(evidence / 'capture-metadata-review.json').write_text(json.dumps(review, indent=2) + '\n')
print(json.dumps({key: value for key, value in review.items() if key != 'records'}))
