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
source = Path('/tmp/harness-subagent-history-parent-candidate-20261008-' + a.attempt)
manifest_path = Path(str(source) + '-manifest.json')
manifest = json.loads(manifest_path.read_text())
for name, digest in manifest['inputs'].items():
    assert hashlib.sha256((source / name).read_bytes()).hexdigest() == digest, name
    assert hashlib.sha256((repo / name).read_bytes()).hexdigest() == digest, 'Workspace drift ' + name
results = Path('/tmp/harness-pages-verified-20261008/subagent-history') / a.run_label
summaries = sorted(results.glob('*/summary.json'))
assert len(summaries) == 6, 'Missing regression class summary'
totals = dict(tests=0, failures=0, errors=0, skipped=0)
classes = []
for summary in summaries:
    data = json.loads(summary.read_text())
    assert data['exit'] == 0, summary
    assert (summary.parent / 'source-manifest.json').read_bytes() == manifest_path.read_bytes(), summary
    classes.append(dict(name=data['class'], totals=data['totals']))
    for key in totals:
        totals[key] += data['totals'][key]
assert totals == dict(tests=a.expected, failures=0, errors=0, skipped=0), totals
out = repo / 'docs/desktop-ui/verification/subagent-history-2026-10-08' / (a.run_label + '-final')
captures = repo / 'docs/desktop-ui/captures/subagent-history-2026-10-08' / a.run_label
assert not out.exists() and not captures.exists(), 'Archive already exists'
rows = []
groups = [('history', Path('/tmp/harness-subagent-history-captures-20261008-' + a.attempt), 8),
          ('shell', Path('/tmp/harness-subagent-history-shell-captures-20261008-' + a.attempt), 80)]
for group, origin, count in groups:
    pngs = sorted(origin.glob('*.png'))
    assert len(pngs) == count, (group, len(pngs), count)
    for png in pngs:
        match = re.search(r'(\d+)x(\d+)', png.name)
        assert match, png
        dimensions = tuple(map(int, match.groups()))
        with Image.open(png) as image:
            assert image.size == dimensions, (png, image.size, dimensions)
        geometries = [g for g in [png.with_suffix('.json'), png.with_suffix('.geometry.json'), png.with_suffix('.geometry.txt')] if g.is_file()]
        assert len(geometries) == 1, (png, geometries)
        geometry = geometries[0]
        rows.append(dict(group=group, image=png.name, dimensions=dimensions,
                         sha256=hashlib.sha256(png.read_bytes()).hexdigest(),
                         geometry=geometry.name, geometry_sha256=hashlib.sha256(geometry.read_bytes()).hexdigest()))
shutil.copytree(results, out)
for group, origin, count in groups:
    shutil.copytree(origin, captures / group)
    for row in [r for r in rows if r['group'] == group]:
        for field, hash_field in [('image', 'sha256'), ('geometry', 'geometry_sha256')]:
            assert hashlib.sha256((captures / group / row[field]).read_bytes()).hexdigest() == row[hash_field]
for name in ['harness-subagent-history-delivery-20261008.json', 'harness-subagent-history-delivery-20261008.md',
             'harness-subagent-history-parent-freeze-20261008.py', 'harness-subagent-history-parent-verify-20261008.py',
             'harness-subagent-history-parent-archive-20261008.py']:
    shutil.copy2(Path('/tmp') / name, out / name)
shutil.copy2(manifest_path, out / 'source-manifest.json')
(out / 'final-summary.json').write_text(json.dumps(dict(status='passed', totals=totals, classes=classes,
    frozenInputs=len(manifest['inputs']), captures=rows,
    boundary='Offscreen controlled fixture and production UI; not original pixel parity, native window, remote history or persistent descendants'), indent=2) + '\n')
print(json.dumps(dict(totals=totals, frozenInputs=len(manifest['inputs']), captures=len(rows), evidence=str(out))))
