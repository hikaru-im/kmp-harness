from pathlib import Path
import argparse
import hashlib
import json
import re
import shutil
from PIL import Image

p = argparse.ArgumentParser()
p.add_argument('--attempt', required=True)
p.add_argument('--run-label', required=True)
p.add_argument('--expected', required=True, type=int)
a = p.parse_args()
repo = Path('/Volumes/Data/IdeaProjects/kmp-harness')
source = Path('/tmp/harness-plugin-local-update-parent-candidate-20261008-' + a.attempt)
manifest_path = Path(str(source) + '-manifest.json')
manifest = json.loads(manifest_path.read_text())
for name, digest in manifest['inputs'].items():
    assert hashlib.sha256((source / name).read_bytes()).hexdigest() == digest, name
    assert hashlib.sha256((repo / name).read_bytes()).hexdigest() == digest, 'Workspace drift ' + name
results = Path('/tmp/harness-pages-verified-20261008/plugin-local-update') / a.run_label
summaries = sorted(results.glob('*/summary.json'))
expected_classes = {'DesktopUserPluginsTest', 'DesktopInstalledPluginRepositoryTest',
                    'PluginLocalUpdateInteractionTest', 'PluginManagementInteractionTest',
                    'PluginManagementControllerTest', 'InstalledPluginsControllerTest'}
assert {s.parent.name for s in summaries} == expected_classes, 'Missing regression class'
totals = dict(tests=0, failures=0, errors=0, skipped=0)
classes = []
for summary in summaries:
    data = json.loads(summary.read_text())
    assert data['exit'] == 0 and data['totals']['tests'] > 0, summary
    assert data['totals']['failures'] == data['totals']['errors'] == data['totals']['skipped'] == 0, summary
    assert (summary.parent / 'source-manifest.json').read_bytes() == manifest_path.read_bytes(), summary
    classes.append(dict(name=data['class'], totals=data['totals']))
    for key in totals:
        totals[key] += data['totals'][key]
assert totals == dict(tests=a.expected, failures=0, errors=0, skipped=0), totals
origin = Path('/tmp/harness-plugin-local-update-captures-20261008-' + a.attempt)
pngs = sorted(origin.glob('*.png'))
assert len(pngs) == 8, ('Capture count', len(pngs))
rows = []
for png in pngs:
    match = re.search(r'(\d+)x(\d+)', png.name)
    assert match, png
    dimensions = tuple(map(int, match.groups()))
    assert dimensions in {(320, 600), (640, 800)}, png
    with Image.open(png) as image:
        assert image.size == dimensions, (png, image.size)
    geometries = [g for g in [png.with_suffix('.json'), png.with_suffix('.geometry.json'),
                              png.with_suffix('.geometry.txt')] if g.is_file()]
    assert len(geometries) == 1, (png, geometries)
    geometry = geometries[0]
    rows.append(dict(image=png.name, dimensions=dimensions, geometry=geometry.name,
                     sha256=hashlib.sha256(png.read_bytes()).hexdigest(),
                     geometry_sha256=hashlib.sha256(geometry.read_bytes()).hexdigest()))
out = repo / 'docs/desktop-ui/verification/plugin-local-update-2026-10-08' / (a.run_label + '-final')
captures = repo / 'docs/desktop-ui/captures/plugin-local-update-2026-10-08' / a.run_label
assert not out.exists() and not captures.exists(), 'Archive exists'
shutil.copytree(results, out)
shutil.copytree(origin, captures)
for row in rows:
    for field, digest in [('image', 'sha256'), ('geometry', 'geometry_sha256')]:
        assert hashlib.sha256((captures / row[field]).read_bytes()).hexdigest() == row[digest]
for name in ['harness-plugin-local-update-delivery-20261008.json', 'harness-plugin-local-update-delivery-20261008.md',
             'harness-plugin-local-update-parent-freeze-20261008.py', 'harness-plugin-local-update-parent-verify-20261008.py',
             'harness-plugin-local-update-parent-archive-20261008.py']:
    shutil.copy2(Path('/tmp') / name, out / name)
shutil.copy2(manifest_path, out / 'source-manifest.json')
(out / 'final-summary.json').write_text(json.dumps(dict(status='passed', totals=totals, classes=classes,
    frozenInputs=len(manifest['inputs']), captures=rows,
    boundary='Real temporary local service and controlled model requests; offscreen production UI; Harness local update adaptation, not original pixel parity or remote lifecycle'), indent=2) + chr(10))
print(json.dumps(dict(totals=totals, frozenInputs=len(manifest['inputs']), captures=len(rows), evidence=str(out))))
