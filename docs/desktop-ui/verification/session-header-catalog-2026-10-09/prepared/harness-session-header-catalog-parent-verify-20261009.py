from pathlib import Path
import argparse
import hashlib
import json
import os
import shutil
import subprocess
import time
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser()
parser.add_argument('module')
parser.add_argument('class_name')
parser.add_argument('expected', type=int)
parser.add_argument('--attempt', default='initial')

parser.add_argument('--run-label', default='')
args = parser.parse_args()
source = Path('/tmp/harness-session-header-catalog-parent-candidate-20261009-' + args.attempt)
build = Path('/tmp/harness-session-header-catalog-parent-build-20261009-' + args.attempt)
root = Path('/tmp/harness-pages-verified-20261009/session-header-catalog')
evidence = root / (args.run_label or args.attempt) / args.class_name.rsplit('.', 1)[-1]
manifest = json.loads(Path(str(source) + '-manifest.json').read_text())
for name, digest in manifest['inputs'].items():
    assert hashlib.sha256((source / name).read_bytes()).hexdigest() == digest, name
evidence.mkdir(parents=True, exist_ok=False)
shutil.copy2(Path(str(source) + '-manifest.json'), evidence / 'source-manifest.json')
command = ['./kotlin', 'test', '--build-dir=' + str(build), '-p', 'jvm', '-m', args.module, '--include-classes=' + args.class_name]
started = time.time()
print('START', args.class_name, flush=True)
with (evidence / 'command.log').open('w') as log:
    result = subprocess.run(command, cwd=source, stdout=log, stderr=subprocess.STDOUT)
totals = {field: 0 for field in ('tests', 'failures', 'errors', 'skipped')}
reports = []
for report in build.rglob('TEST-*.xml'):
    if report.stat().st_mtime < started:
        continue
    suite = ET.parse(report).getroot()
    if suite.tag != 'testsuite':
        continue
    cases = list(suite.iter('testcase'))
    assert len(cases) == int(suite.get('tests', 0)), 'Test count does not match cases: ' + str(report)
    assert all(case.get('classname') == args.class_name for case in cases), 'Unexpected test class: ' + str(report)
    destination = evidence / report.name
    assert not destination.exists(), 'Duplicate XML name: ' + str(report)
    shutil.copy2(report, destination)
    reports.append(str(report))
    for field in totals:
        totals[field] += int(suite.get(field, 0))
    for case in cases:
        for issue in case:
            if issue.tag in ('failure', 'error'):
                print('FAIL', case.get('name'), issue.get('message', '')[:3500], flush=True)
summary = {'class': args.class_name, 'command': command, 'exit': result.returncode, 'elapsedSeconds': round(time.time() - started, 2), 'totals': totals, 'sourceManifest': str(evidence / 'source-manifest.json'), 'reports': reports}
(evidence / 'summary.json').write_text(json.dumps(summary, indent=2) + '\n')
print('RESULT', json.dumps(summary), flush=True)
for name, digest in manifest['inputs'].items():
    assert hashlib.sha256((source / name).read_bytes()).hexdigest() == digest, 'Post-test drift ' + name
passed = result.returncode == 0 and totals == {'tests': args.expected, 'failures': 0, 'errors': 0, 'skipped': 0}
if not passed:
    print('LOG TAIL', '\n'.join((evidence / 'command.log').read_text().splitlines()[-45:]), flush=True)
    raise SystemExit(1)
