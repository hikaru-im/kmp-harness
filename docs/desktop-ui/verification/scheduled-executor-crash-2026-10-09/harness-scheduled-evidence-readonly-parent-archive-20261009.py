from pathlib import Path
import argparse
import hashlib
import json
import shutil
import xml.etree.ElementTree as ET

p = argparse.ArgumentParser()
p.add_argument('--attempt', required=True)
p.add_argument('--run-label', required=True)
p.add_argument('--delivery', default='/tmp/harness-scheduled-evidence-readonly-delivery-20261009.json')
a = p.parse_args()
repo = Path('/Volumes/Data/IdeaProjects/kmp-harness')
source = Path('/tmp/harness-scheduled-executor-crash-parent-candidate-20261009-' + a.attempt)
mp = Path(str(source) + '-manifest.json')
manifest = json.loads(mp.read_text())
delivery_path = Path(a.delivery)
delivery = json.loads(delivery_path.read_text())
assert delivery['model'] == 'SHUAIAPI/gpt-6.1-sol' and delivery['provider'] == 'opencodex'
assert delivery['write_set_released'] is True and delivery['tests_run'] is False and delivery['builds_run'] is False
for item in delivery.get('files') or delivery.get('changed_files'):
    assert manifest['inputs'][item['path']] == item['after_sha256']
for name, digest in manifest['inputs'].items():
    assert hashlib.sha256((source / name).read_bytes()).hexdigest() == digest, 'Frozen drift ' + name
    assert hashlib.sha256((repo / name).read_bytes()).hexdigest() == digest, 'Workspace drift ' + name
results = Path('/tmp/harness-pages-verified-20261009/scheduled-executor-crash') / a.run_label
plan = json.loads((results / 'suite-plan.json').read_text())
expected = {row[1]: row[2] for row in plan}
assert len(expected) == 12 and sum(expected.values()) > 66
summaries = sorted(results.glob('*/summary.json'))
assert {p.parent.name for p in summaries} == {n.rsplit('.', 1)[-1] for n in expected}
totals = dict(tests=0, failures=0, errors=0, skipped=0)
classes = []
for path in summaries:
    s = json.loads(path.read_text())
    assert s['class'] in expected and s['exit'] == 0
    assert s['totals'] == dict(tests=expected[s['class']], failures=0, errors=0, skipped=0)
    assert (path.parent / 'source-manifest.json').read_bytes() == mp.read_bytes()
    cases = [case for xml in path.parent.glob('TEST-*.xml') for case in ET.parse(xml).iter('testcase')]
    assert len(cases) == expected[s['class']]
    assert all(c.get('classname') == s['class'] and c.find('failure') is None and c.find('error') is None and c.find('skipped') is None for c in cases)
    classes.append(dict(name=s['class'], totals=s['totals']))
    for key in totals:
        totals[key] += s['totals'][key]
assert totals['tests'] == sum(expected.values())
out = Path(__file__).parent / (a.run_label + '-final')
assert not out.exists()
shutil.copytree(results, out)
for suffix in ('json', 'md'):
    shutil.copy2(delivery_path.with_suffix('.' + suffix), out / delivery_path.with_suffix('.' + suffix).name)
for script in Path(__file__).parent.glob('harness-scheduled-*-parent-*.py'):
    shutil.copy2(script, out / script.name)
shutil.copy2(mp, out / 'source-manifest.json')
(out / 'final-summary.json').write_text(json.dumps(dict(status='passed', totals=totals, classes=classes, frozenInputs=len(manifest['inputs']), boundary='Read-only durable evidence and five real child JVM crash windows; production Cron disabled, original visual parity and remote provider not verified'), indent=2) + '\n')
hashes = {str(f.relative_to(out)): hashlib.sha256(f.read_bytes()).hexdigest() for f in sorted(out.rglob('*')) if f.is_file()}
(out / 'artifact-hashes.json').write_text(json.dumps(hashes, indent=2) + '\n')
print(json.dumps(dict(totals=totals, frozenInputs=len(manifest['inputs']), evidence=str(out))))
