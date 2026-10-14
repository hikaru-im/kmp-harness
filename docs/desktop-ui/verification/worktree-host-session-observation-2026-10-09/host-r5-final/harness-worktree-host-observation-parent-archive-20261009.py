from pathlib import Path
import argparse
import hashlib
import json
import re
import shutil
from PIL import Image

parser = argparse.ArgumentParser()
parser.add_argument('--attempt', required=True)
parser.add_argument('--run-label', required=True)
parser.add_argument('--observer-count', required=True, type=int)
parser.add_argument('--ui-count', required=True, type=int)
parser.add_argument('--captures', required=True, type=Path)
args = parser.parse_args()
repo = Path('/Volumes/Data/IdeaProjects/kmp-harness')
source = Path('/tmp/harness-worktree-host-observation-parent-candidate-20261009-' + args.attempt)
manifest_path = Path(str(source) + '-manifest.json')
manifest = json.loads(manifest_path.read_text())
delivery_path = Path('/tmp/harness-worktree-host-observation-delivery-20261009.json')
delivery = json.loads(delivery_path.read_text())
assert delivery['model'] == 'SHUAIAPI/gpt-6.1-sol' and delivery['provider'] == 'opencodex'
assert delivery['write_set_released'] is True
records = delivery.get('files') or delivery.get('changed_files')
for record in records:
    assert manifest['inputs'][record['path']] == record['after_sha256'], record['path']
for name, digest in manifest['inputs'].items():
    assert hashlib.sha256((source / name).read_bytes()).hexdigest() == digest, 'Frozen drift ' + name
    assert hashlib.sha256((repo / name).read_bytes()).hexdigest() == digest, 'Workspace drift ' + name
expected = {
    'DesktopWorktreeSessionObserverTest': args.observer_count,
    'WorktreeSessionObservationInteractionTest': args.ui_count,
    'DesktopManagedWorktreeIntegrationTest': 8,
    'ManagedWorktreeControllerTest': 4,
    'ManagedWorktreeInteractionTest': 3,
    'WorktreeCreationInteractionTest': 2,
    'GitWorktreesScreenshotTest': 6,
}
results = Path('/tmp/harness-pages-verified-20261009/worktree-host-observation') / args.run_label
summaries = sorted(results.glob('*/summary.json'))
assert {p.parent.name for p in summaries} == set(expected), 'Missing or unexpected suite'
totals = dict(tests=0, failures=0, errors=0, skipped=0)
classes = []
for path in summaries:
    summary = json.loads(path.read_text())
    required = dict(tests=expected[path.parent.name], failures=0, errors=0, skipped=0)
    assert summary['exit'] == 0 and summary['totals'] == required, path
    assert summary['class'].rsplit('.', 1)[-1] == path.parent.name, path
    assert (path.parent / 'source-manifest.json').read_bytes() == manifest_path.read_bytes(), path
    classes.append(dict(name=summary['class'], totals=summary['totals']))
    for key in totals:
        totals[key] += summary['totals'][key]
assert totals['tests'] == sum(expected.values())
pngs = sorted(args.captures.glob('*.png'))
assert len(pngs) == 8, ('Observation end and lineage screenshot count', len(pngs))
rows = []
matrix = set()
for png in pngs:
    match = re.search(r'(\d+)x(\d+)', png.name)
    assert match, png
    dimensions = tuple(map(int, match.groups()))
    assert dimensions in {(320, 600), (640, 800)}, png
    themes = [theme for theme in ('light', 'dark') if theme in png.stem.lower()]
    assert len(themes) == 1, png
    stage = 'lineage' if png.stem.endswith('-lineage') else 'end'
    expected_name = f'worktree-session-{dimensions[0]}x{dimensions[1]}-{themes[0]}'
    if stage == 'lineage':
        expected_name += '-lineage'
    assert png.stem == expected_name, png
    slot = (dimensions, themes[0], stage)
    assert slot not in matrix, ('Duplicate screenshot slot', png)
    matrix.add(slot)
    with Image.open(png) as image:
        assert image.size == dimensions, (png, image.size)
    geometries = [g for g in (png.with_suffix('.json'), png.with_suffix('.geometry.json'),
                             png.with_suffix('.geometry.txt')) if g.is_file()]
    assert len(geometries) == 1, (png, geometries)
    geometry = geometries[0]
    proof = json.loads(geometry.read_text())
    image_digest = hashlib.sha256(png.read_bytes()).hexdigest()
    assert proof['schema'] == 'harness.worktree-session-observation.geometry.v1', geometry
    assert (proof['width'], proof['height']) == dimensions, geometry
    assert proof['theme'].lower() == themes[0] and proof['stage'] == stage, geometry
    assert proof['png'] == png.name and proof['sha256'] == image_digest, geometry
    assert proof['status'] == 'Observed', geometry
    assert proof['completedAtMillis'] >= proof['startedAtMillis'], geometry
    assert proof['listMax'] > 0 and 0 <= proof['listValue'] <= proof['listMax'] + 1, geometry
    if stage == 'end':
        assert proof['verticalEnd'] is True, geometry
        assert abs(proof['listMax'] - proof['listValue']) <= 1, geometry
        text = proof['proof']['lastProtectionProof']
        assert text['fullTextVisible'] is True and text['ellipsis'] is False, geometry
    else:
        marker = proof['proof']['markerProof']
        boundary = proof['proof']['boundaryProof']
        assert marker['fullMarkerVisible'] is True and marker['ellipsis'] is False, geometry
        assert marker['marker'] == 'HISTORY-END-MARKER' and marker['fullText'].endswith(marker['marker']), geometry
        assert marker['subtractedScrollPixels'] > 0, geometry
        assert boundary['fullTextVisible'] is True and boundary['ellipsis'] is False, geometry
    rows.append(dict(image=png.name, dimensions=dimensions, theme=themes[0], stage=stage, geometry=geometry.name,
                     sha256=hashlib.sha256(png.read_bytes()).hexdigest(),
                     geometry_sha256=hashlib.sha256(geometry.read_bytes()).hexdigest()))
assert matrix == {(dimensions, theme, stage) for dimensions in ((320, 600), (640, 800))
                  for theme in ('light', 'dark') for stage in ('end', 'lineage')}
out = repo / 'docs/desktop-ui/verification/worktree-host-session-observation-2026-10-09' / (args.run_label + '-final')
captures = repo / 'docs/desktop-ui/captures/worktree-host-session-observation-2026-10-09' / args.run_label
assert not out.exists() and not captures.exists(), 'Archive already exists'
shutil.copytree(results, out)
shutil.copytree(args.captures, captures)
for row in rows:
    for field, digest in (('image', 'sha256'), ('geometry', 'geometry_sha256')):
        assert hashlib.sha256((captures / row[field]).read_bytes()).hexdigest() == row[digest]
for suffix in ('json', 'md'):
    origin = delivery_path.with_suffix('.' + suffix)
    shutil.copy2(origin, out / origin.name)
for script in Path(__file__).parent.glob('harness-worktree-host-observation-parent-*.py'):
    shutil.copy2(script, out / script.name)
shutil.copy2(manifest_path, out / 'source-manifest.json')
(out / 'final-summary.json').write_text(json.dumps(dict(
    status='passed', totals=totals, classes=classes, frozenInputs=len(manifest['inputs']), captures=rows,
    boundary='Real temporary local Runtime/Room/Git; two independent header reads; offscreen production UI; no absence proof, deletion capability, original pixel parity or remote validation'
), indent=2) + '\n')
print(json.dumps(dict(totals=totals, frozenInputs=len(manifest['inputs']), captures=len(rows), evidence=str(out))))
