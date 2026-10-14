from pathlib import Path
import hashlib, json, re
from collections import Counter
import xml.etree.ElementTree as ET
from PIL import Image

ROOT = Path('/tmp/harness-terminal-local-visual-evidence-20261010')
REPO = Path('/Volumes/Data/IdeaProjects/kmp-harness')
OLD = REPO / 'docs/desktop-ui/verification/terminal-renderer-page-2026-10-10/page-r5/PtyTerminalPageInteractionTest/captures'
CAP = ROOT / 'PtyTerminalPageInteractionTest/captures'
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()

def write(name, value):
    (ROOT / name).write_text(json.dumps(value, ensure_ascii=False, indent=2) + chr(10))

def main():
    delivery = json.loads((ROOT / 'delivery.json').read_text())
    manifest = json.loads((ROOT / 'source-manifest.json').read_text())
    baseline = json.loads((ROOT / 'baseline.json').read_text())
    assert len(manifest['inputs']) == len(baseline['inputs']) == 2253
    changed = [name for name, value in baseline['inputs'].items() if manifest['inputs'][name] != value]
    assert sorted(changed) == sorted(row['path'] for row in delivery['files'])
    for name, value in manifest['inputs'].items():
        assert sha(REPO / name) == value
        assert sha(Path(manifest['source']) / name) == value
    tests = json.loads((ROOT / 'test-results.json').read_text())
    assert tests['status'] == 'passed' and tests['tests'] == 35 and len(tests['suites']) == 6
    total = Counter()
    for suite in tests['suites']:
        assert suite['exit'] == 0
        folder = ROOT / suite['class_name'].rsplit('.', 1)[-1]
        actual = Counter()
        methods = []
        for p in folder.glob('TEST-*.xml'):
            xml = ET.parse(p).getroot()
            cases = list(xml.iter('testcase'))
            assert len(cases) == int(xml.get('tests', 0))
            assert all(c.get('classname') == suite['class_name'] for c in cases)
            methods.extend(c.get('name') for c in cases)
            for field in ('tests', 'failures', 'errors', 'skipped'):
                actual[field] += int(xml.get(field, 0))
        assert dict(actual) == suite['totals']
        assert actual['tests'] == suite['expected'] == len(set(methods))
        assert actual['failures'] == actual['errors'] == actual['skipped'] == 0
        total.update(actual)
    assert total['tests'] == 35
    assert json.loads((ROOT / 'jvm-app-build.json').read_text())['exit'] == 0

    names = [f'{w}x{h}-{theme}-{state}' for w,h in ((1440,900),(600,800))
        for theme in ('light','dark') for state in ('Running','Exited')]
    names += ['1440x900-light-Unknown','600x800-light-Unavailable',
        '1440x900-light-Running-menu','600x800-dark-Running-menu']
    assert {p.stem for p in CAP.glob('*.png')} == set(names)
    reviewed = []
    for name in sorted(names):
        p = CAP / (name + '.png')
        meta = json.loads((CAP / (name + '.matrix.json')).read_text())
        geometry = (CAP / (name + '.geometry.txt')).read_text()
        assert meta['full_page'] is True
        assert meta['menu_open'] == name.endswith('-menu')
        assert meta['native_ime_verified'] is False and meta['pixel_parity_verified'] is False
        assert meta['expectedHostId'] == 'pty-page-local'
        assert meta['controlled'] == (meta['state'] in ('Unknown', 'Unavailable'))
        rects = {}
        for tag, values in re.findall(r'^([a-z-]+)=Rect[.]fromLTRB[(]([^)]*)[)]', geometry, re.M):
            left,top,right,bottom = map(float, values.split(','))
            assert 0 <= left < right <= meta['width'] and 0 <= top < bottom <= meta['height'], (name, tag, values)
            rects[tag] = [left,top,right,bottom]
        assert all(tag in rects for tag in ('pty-terminal-panel','pty-terminal-create','pty-terminal-actions'))
        assert rects['pty-terminal-create'][1] == rects['pty-terminal-actions'][1]
        if meta['state'] != 'Unavailable':
            assert all(tag in rects for tag in ('pty-terminal-grid','pty-terminal-input','pty-terminal-select'))
            assert set(meta['key']) == {'scope','session','incarnation','terminal'}
            assert all(meta['key'].values()) and meta['key']['session'] == 'page-owner'
            assert meta['rows'] > 0 and meta['columns'] > 0
            if meta['state'] != 'Unknown':
                assert meta['revision'] == meta['rendered_revision']
                assert meta['lifecycle'] == ('Running' if meta['state'] == 'Running' else 'Exited(code=7)')
            else:
                assert meta['ack_controlled'] and 0 <= meta['rendered_revision'] <= meta['revision']
        else:
            assert meta['key'] is None and meta['rows'] is None and 'pty-terminal-grid' not in rects
        if meta['menu_open']:
            assert all(tag in rects for tag in ('pty-terminal-action-menu','pty-terminal-refresh','pty-terminal-close',
                'pty-terminal-copy','pty-terminal-paste','pty-terminal-bottom'))
        with Image.open(p) as im:
            assert im.size == (meta['width'], meta['height'])
            x,y = map(int, re.search(r'background_sample=[(]([0-9]+),([0-9]+)[)]', geometry).groups())
            r,g,b,a = im.convert('RGBA').getpixel((x,y))
            argb = (a << 24) | (r << 16) | (g << 8) | b
            if argb >= 1 << 31:
                argb -= 1 << 32
            assert argb == meta['background_expected_argb'] == meta['background_actual_argb']
        old = OLD / (name + '.matrix.json')
        comparison = None
        if old.exists() and meta['state'] in ('Running','Exited'):
            prior = json.loads(old.read_text())
            comparison = dict(before_rows=prior['rows'], after_rows=meta['rows'],
                before_columns=prior['columns'], after_columns=meta['columns'])
            assert meta['rows'] > prior['rows'], (name, comparison)
        reviewed.append(dict(name=name, sha256=sha(p), dimensions=[meta['width'],meta['height']],
            state=meta['state'], theme=meta['theme'], menu=meta['menu_open'], rects=rects,
            background_argb=argb, visible_grid_comparison=comparison))
    write('capture-metadata-review.json', dict(passed=True, png_count=12, full_scene=True,
        source_inputs_verified=2253, unchanged_inputs=2250, independent_xml_totals=dict(total), files=reviewed))
    print(json.dumps(dict(passed=True, tests=dict(total), captures=12,
        grid_comparisons=[dict(name=r['name'], **r['visible_grid_comparison'])
            for r in reviewed if r['visible_grid_comparison']]), ensure_ascii=False))

if __name__ == '__main__':
    main()
