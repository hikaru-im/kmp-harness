from pathlib import Path
import argparse
import hashlib
import json
import shutil

p = argparse.ArgumentParser()
p.add_argument('--attempt', required=True)
p.add_argument('--run-label', required=True)
a = p.parse_args()
repo = Path('/Volumes/Data/IdeaProjects/kmp-harness')
source = Path('/tmp/harness-scheduled-executor-crash-parent-candidate-20261009-' + a.attempt)
manifest_path = Path(str(source) + '-manifest.json')
manifest = json.loads(manifest_path.read_text())
delivery_path = Path('/tmp/harness-scheduled-executor-crash-delivery-20261009.json')
delivery = json.loads(delivery_path.read_text())
assert delivery['model'] == 'SHUAIAPI/gpt-6.1-sol' and delivery['provider'] == 'opencodex'
assert delivery['write_set_released'] is True
for record in delivery.get('files') or delivery.get('changed_files'):
    assert manifest['inputs'][record['path']] == record['after_sha256'], record['path']
for name, digest in manifest['inputs'].items():
    assert hashlib.sha256((source / name).read_bytes()).hexdigest() == digest, 'Frozen drift ' + name
    assert hashlib.sha256((repo / name).read_bytes()).hexdigest() == digest, 'Workspace drift ' + name
expected = {'DesktopScheduledExecutorCrashProcessTest': 5, 'DesktopScheduledExecutorIntegrationTest': 7, 'DesktopScheduledLeaderLeaseProcessTest': 2, 'DesktopScheduleEngineTest': 3, 'ScheduledExecutorOwnerTest': 13, 'DesktopScheduledStoreTest': 15, 'DesktopScheduledSessionEvidenceTest': 3}
results = Path('/tmp/harness-pages-verified-20261009/scheduled-executor-crash') / a.run_label
summaries = sorted(results.glob('*/summary.json'))
assert {path.parent.name for path in summaries} == set(expected), 'Missing or unexpected suite'
totals = dict(tests=0, failures=0, errors=0, skipped=0)
classes = []
for path in summaries:
    summary = json.loads(path.read_text())
    assert summary['exit'] == 0 and summary['totals'] == dict(tests=expected[path.parent.name], failures=0, errors=0, skipped=0), path
    assert summary['class'].rsplit('.',1)[-1] == path.parent.name, path
    assert (path.parent / 'source-manifest.json').read_bytes() == manifest_path.read_bytes(), path
    classes.append(dict(name=summary['class'], totals=summary['totals']))
    for key in totals:
        totals[key] += summary['totals'][key]
assert totals['tests'] == sum(expected.values())
out = Path(__file__).parent / (a.run_label + '-final')
assert not out.exists(), 'Archive already exists'
shutil.copytree(results, out)
for suffix in ('json', 'md'):
    shutil.copy2(delivery_path.with_suffix('.' + suffix), out / delivery_path.with_suffix('.' + suffix).name)
for script in Path(__file__).parent.glob('harness-scheduled-executor-crash-parent-*.py'):
    shutil.copy2(script, out / script.name)
shutil.copy2(manifest_path, out / 'source-manifest.json')
(out / 'final-summary.json').write_text(json.dumps(dict(status='passed', totals=totals, classes=classes, frozenInputs=len(manifest['inputs']), boundary='Five real temporary child JVM crash windows with controlled provider; no production Cron enablement, heartbeat, original pixel parity or real remote provider validation'), indent=2) + '\n')
print(json.dumps(dict(totals=totals, frozenInputs=len(manifest['inputs']), evidence=str(out))))
