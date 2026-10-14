from pathlib import Path
import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import time
import xml.etree.ElementTree as ET
import math
import struct

REPO = Path('/Volumes/Data/IdeaProjects/kmp-harness')
BASELINE = Path('/tmp/harness-scheduled-cron-page-enable-baseline-20261009')
ALLOWED = (
    'apps/jvm-app/src/main.kt',
    'apps/jvm-app/src/im/hikaru/harness/desktop/scheduling/DesktopScheduledExecution.kt',
    'apps/jvm-app/test/im/hikaru/harness/desktop/ScheduledCronPageIntegrationTest.kt',
    'apps/jvm-app/test/im/hikaru/harness/desktop/scheduling/DesktopScheduledExecutionTest.kt',
)
BASE_SUITES = (
    ('jvm-app', 'im.hikaru.harness.desktop.scheduling.DesktopScheduledExecutorCrashProcessTest', 5),
    ('session-persistence', 'im.hikaru.harness.session.persistence.SessionPersistenceReadOnlyEventsTest', 9),
    ('jvm-app', 'im.hikaru.harness.desktop.scheduling.DesktopScheduledSessionEvidenceReadOnlyTest', 10),
    ('jvm-app', 'im.hikaru.harness.desktop.scheduling.DesktopScheduledExecutorIntegrationTest', 7),
    ('jvm-app', 'im.hikaru.harness.desktop.scheduling.DesktopScheduledLeaderLeaseProcessTest', 2),
    ('jvm-app', 'im.hikaru.harness.desktop.scheduling.DesktopScheduleEngineTest', 3),
    ('shared', 'im.hikaru.harness.client.scheduling.ScheduledExecutorOwnerTest', 13),
    ('jvm-app', 'im.hikaru.harness.desktop.scheduling.DesktopScheduledStoreTest', 15),
    ('jvm-app', 'im.hikaru.harness.desktop.scheduling.DesktopScheduledSessionEvidenceTest', 3),
    ('session-persistence', 'im.hikaru.harness.session.persistence.SessionPersistenceContractTest', 7),
    ('session-persistence', 'im.hikaru.harness.session.persistence.SessionPersistenceHeaderCatalogTest', 10),
    ('session-persistence', 'im.hikaru.harness.session.persistence.SessionPersistenceDatabaseFactoryTest', 1),
)


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def write_json(path, value):
    path.write_text(json.dumps(value, indent=2) + chr(10))


def check(root, inputs, changed=()):
    for name, digest in inputs.items():
        assert (root / name).is_file(), 'Missing input: ' + name
        if name not in changed:
            assert sha(root / name) == digest, 'Input drift: ' + name
    for folder in ('apps', 'modules'):
        for path in (root / folder).rglob('*.kt'):
            if 'build' not in path.parts:
                name = str(path.relative_to(root))
                assert name in inputs or name in changed, 'New unreported source: ' + name


def test_suite(source, name, minimum=1, module='jvm-app'):
    text = (source / name).read_text()
    methods = re.findall(r'@Test\s+fun\s+(\w+)\s*\(', text)
    assert len(methods) >= minimum and len(set(methods)) == len(methods), name
    assert len(methods) == len(re.findall(r'@Test\b', text)), 'Uncounted @Test: ' + name
    package = re.search(r'^package\s+(\S+)', text, re.MULTILINE).group(1)
    return (module, package + '.' + Path(name).stem, len(methods))


def paths(attempt):
    assert re.fullmatch(r'[a-z0-9-]+', attempt), 'Invalid attempt label'
    source = Path('/tmp/harness-scheduled-cron-page-enable-source-20261009-' + attempt)
    build = Path('/tmp/harness-scheduled-cron-page-enable-build-20261009-' + attempt)
    evidence = Path('/tmp/harness-pages-verified-20261009/scheduled-cron-page-enable') / attempt
    return source, build, evidence


def delivery(path):
    data = json.loads(path.read_text())
    assert data.get('model') == 'SHUAIAPI/gpt-6.1-sol' and data.get('provider') == 'opencodex'
    assert data.get('tests_run') is False and data.get('builds_run') is False
    assert data.get('write_set_released') is True and data.get('source_writes_stopped') is True
    records = data.get('files') or data.get('changed_files')
    assert len(records) == len(ALLOWED) and {r['path'] for r in records} == set(ALLOWED)
    baseline = json.loads((BASELINE / 'baseline.json').read_text())
    expected = {r['path']: r['before_sha256'] for r in baseline['files']}
    for record in records:
        name = record['path']
        assert record['before_sha256'] == expected[name], 'Before SHA mismatch: ' + name
        assert record['after_sha256'] == sha(REPO / name), 'After SHA mismatch: ' + name
    return data, records, baseline


def rectangle(value):
    numbers = re.findall(r'-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?', value)
    assert len(numbers) == 4, 'Invalid rectangle: ' + value
    bounds = tuple(map(float, numbers))
    assert all(math.isfinite(number) for number in bounds), 'Nonfinite rectangle'
    return bounds


def verify_cron_matrix(folder, summary):
    matrices = [name for name in summary['artifacts'] if name.endswith('/matrix.json')]
    assert len(matrices) == 1, 'Expected one real Cron capture matrix'
    matrix_path = folder / 'artifacts' / matrices[0]
    captures = json.loads(matrix_path.read_text())
    expected = {(width, height, theme, stage)
                for width, height in ((1440, 900), (600, 800))
                for theme in ('Light', 'Dark')
                for stage in ('paused', 'accepted', 'completed')}
    actual = [(row['width'], row['height'], row['theme'], row['stage']) for row in captures]
    assert len(actual) == 12 and set(actual) == expected, 'Incomplete Cron state/size/theme matrix'
    assert len({row['name'] for row in captures}) == 12, 'Duplicate capture name'
    pngs = {name for name in summary['artifacts'] if name.endswith('.png')}
    assert len(pngs) == 12, 'Expected twelve Cron PNG artifacts'
    for row in captures:
        png = matrix_path.parent / (row['name'] + '.png')
        assert Path(row['path']).name == png.name
        relative = str(png.relative_to(folder / 'artifacts'))
        assert relative in pngs, 'Untracked PNG: ' + relative
        assert sha(png) == row['sha256'] == summary['artifacts'][relative]
        content = png.read_bytes()
        assert content[:8] == b'\x89PNG\r\n\x1a\n' and content[12:16] == b'IHDR'
        assert struct.unpack('>II', content[16:24]) == (row['width'], row['height'])
        assert row['taskId'] and row['taskStatus'] == 'Paused'
        assert row['control']['disabled'] is False
        layout = rectangle(row['control']['layout'])
        visible = rectangle(row['controlInViewport'])
        assert layout[2] > layout[0] and layout[3] > layout[1]
        assert visible[2] - visible[0] + 1 >= layout[2] - layout[0]
        assert visible[3] - visible[1] + 1 >= layout[3] - layout[1]
        assert row['prompt']['layout'] and row['scroll']['layout']
        if row['stage'] == 'paused':
            assert row['runId'] is None and row['inputMessageId'] is None and row['runPhase'] is None
        else:
            assert row['runId'] and row['inputMessageId']
            assert row['runPhase'] == ('Accepted' if row['stage'] == 'accepted' else 'Succeeded')
            position, maximum = row['scroll']['scrollValue'], row['scroll']['scrollMax']
            assert math.isfinite(position) and math.isfinite(maximum)
            assert 0 <= position <= maximum + 1 and abs(maximum - position) <= 1
    for width, height, theme in {(w, h, t) for w, h, t, _ in expected}:
        group = [row for row in captures if (row['width'], row['height'], row['theme']) == (width, height, theme)]
        assert len({row['taskId'] for row in group}) == 1, 'Capture task identity changed'
        running = [row for row in group if row['stage'] != 'paused']
        assert len({row['runId'] for row in running}) == 1, 'Capture run identity changed'
        assert len({row['inputMessageId'] for row in running}) == 1, 'Capture input identity changed'
    return dict(captures=12, sizes=[[1440, 900], [600, 800]],
                themes=['Light', 'Dark'], stages=['paused', 'accepted', 'completed'],
                png_sha256_verified=True, control_geometry_verified=True)


def freeze(args):
    data, records, baseline = delivery(args.delivery)
    old = json.loads((BASELINE / 'source-manifest.json').read_text())
    assert len(old['inputs']) == 2213 and sum(s[2] for s in BASE_SUITES) == 85
    base = Path(baseline['base_source'])
    check(base, old['inputs'])
    check(REPO, old['inputs'], ALLOWED)
    source, build, evidence = paths(args.attempt)
    assert not source.exists() and not build.exists() and not evidence.exists()
    shutil.copytree(base, source, symlinks=True, ignore=shutil.ignore_patterns('build', '.git', 'node_modules', '__pycache__', 'captures'))
    for record in records:
        destination = source / record['path']
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(REPO / record['path'], destination)
        assert sha(destination) == record['after_sha256']
    inputs = {str(p.relative_to(source)): sha(p) for p in sorted(source.rglob('*'))
              if p.is_file() and (p.suffix in ('.kt', '.yaml') or p.name == 'kotlin')}
    assert len(inputs) == 2216, 'Unexpected frozen input count'
    check(source, old['inputs'], ALLOWED)
    suites = [test_suite(source, ALLOWED[2], 4), test_suite(source, ALLOWED[3], 3)]
    suites.extend(BASE_SUITES)
    for name in ('ScheduledManagementInteractionTest', 'ScheduledPageInteractionTest',
                 'ScheduledPageRoutingInteractionTest', 'ScheduledRoutingInteractionTest', 'ScheduledPageScreenshotTest'):
        suites.append(test_suite(source, 'apps/jvm-app/test/im/hikaru/harness/desktop/' + name + '.kt'))
    suites.append(test_suite(source, 'apps/shared/test/im/hikaru/harness/client/scheduling/ScheduledRepositoryTest.kt', module='shared'))
    assert len({s[1] for s in suites}) == len(suites)
    manifest = dict(source=str(source), build=str(build), baseline=str(BASELINE), delivery=str(args.delivery),
                    overlays=list(ALLOWED), inputs=inputs, suites=suites)
    write_json(Path(str(source) + '-manifest.json'), manifest)
    shutil.copytree(baseline['base_build'], build, symlinks=True, ignore=shutil.ignore_patterns('docs'))
    (source / 'docs/desktop-ui').mkdir(parents=True, exist_ok=True)
    (build / 'docs').symlink_to(source / 'docs', target_is_directory=True)
    print(json.dumps(dict(source=str(source), build=str(build), inputs=len(inputs), suites=len(suites),
                          expected_tests=sum(s[2] for s in suites))), flush=True)


def verify_suite(source, build, evidence, manifest, module, class_name, expected):
    check(source, manifest['inputs'])
    folder = evidence / class_name.rsplit('.', 1)[-1]
    folder.mkdir(parents=True, exist_ok=False)
    shutil.copy2(Path(str(source) + '-manifest.json'), folder / 'source-manifest.json')
    command = ['./kotlin', 'test', '--build-dir=' + str(build), '-p', 'jvm', '-m', module, '--include-classes=' + class_name]
    environment = os.environ.copy()
    environment['KOTLIN_CLI_JAVA_OPTIONS'] = '-Duser.home=/tmp/harness-plugin-kotlin-user-home-20261009'
    environment['JAVA_TOOL_OPTIONS'] = '-Dskiko.data.path=/tmp/harness-plugin-skiko-data-20261009 -Djava.awt.headless=true'
    environment['HARNESS_SCHEDULED_CRON_CAPTURE_DIR'] = str(source / 'docs/desktop-ui/captures/scheduled-cron-page-enable')
    write_json(folder / 'environment.json', {k: environment[k] for k in
               ('KOTLIN_CLI_JAVA_OPTIONS', 'JAVA_TOOL_OPTIONS', 'HARNESS_SCHEDULED_CRON_CAPTURE_DIR')})
    started = time.time()
    print('START', class_name, flush=True)
    with (folder / 'command.log').open('w') as log:
        result = subprocess.run(command, cwd=source, stdout=log, stderr=subprocess.STDOUT, env=environment)
    totals = dict(tests=0, failures=0, errors=0, skipped=0)
    reports = []
    for path in build.rglob('TEST-*.xml'):
        if path.stat().st_mtime < started:
            continue
        suite = ET.parse(path).getroot()
        if suite.tag != 'testsuite':
            continue
        cases = list(suite.iter('testcase'))
        assert len(cases) == int(suite.get('tests', 0)), 'XML count mismatch: ' + str(path)
        assert all(c.get('classname') == class_name for c in cases), 'Unexpected class: ' + str(path)
        destination = folder / path.name
        assert not destination.exists(), 'Duplicate XML: ' + str(path)
        shutil.copy2(path, destination)
        reports.append(str(path))
        for field in totals:
            totals[field] += int(suite.get(field, 0))
        for case in cases:
            for issue in case:
                if issue.tag in ('failure', 'error'):
                    print('FAIL', case.get('name'), issue.get('message', '')[:3000], flush=True)
    artifacts = {}
    capture_root = source / 'docs/desktop-ui/captures'
    if capture_root.exists():
        for path in capture_root.rglob('*'):
            if path.is_file() and path.stat().st_mtime >= started and path.suffix in ('.png', '.json'):
                relative = str(path.relative_to(source))
                destination = folder / 'artifacts' / relative
                destination.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(path, destination)
                artifacts[relative] = sha(destination)
    summary = dict(class_name=class_name, command=command, exit=result.returncode,
                   elapsed_seconds=round(time.time() - started, 2), expected_tests=expected, totals=totals,
                   reports=reports, artifacts=artifacts)
    write_json(folder / 'summary.json', summary)
    print('RESULT', json.dumps(summary), flush=True)
    check(source, manifest['inputs'])
    if result.returncode != 0 or totals != dict(tests=expected, failures=0, errors=0, skipped=0):
        print('LOG TAIL', chr(10).join((folder / 'command.log').read_text().splitlines()[-45:]), flush=True)
        raise SystemExit(1)
    if class_name == 'im.hikaru.harness.desktop.ScheduledCronPageIntegrationTest':
        summary['cron_matrix'] = verify_cron_matrix(folder, summary)
        write_json(folder / 'summary.json', summary)
    return summary


def run(args):
    source, build, evidence = paths(args.attempt)
    manifest = json.loads(Path(str(source) + '-manifest.json').read_text())
    evidence.mkdir(parents=True, exist_ok=False)
    write_json(evidence / 'suite-plan.json', manifest['suites'])
    summaries = []
    for module, class_name, expected in manifest['suites']:
        summaries.append(verify_suite(source, build, evidence, manifest, module, class_name, expected))
    write_json(evidence / 'all-summary.json', dict(suites=summaries, totals={
        field: sum(s['totals'][field] for s in summaries) for field in ('tests', 'failures', 'errors', 'skipped')}))
    print('SERIAL SUITES PASSED', sum(s['totals']['tests'] for s in summaries), flush=True)


def archive(args):
    data, records, baseline = delivery(args.delivery)
    source, build, evidence = paths(args.attempt)
    manifest = json.loads(Path(str(source) + '-manifest.json').read_text())
    check(source, manifest['inputs'])
    check(REPO, manifest['inputs'])
    summaries = []
    for module, class_name, expected in manifest['suites']:
        folder = evidence / class_name.rsplit('.', 1)[-1]
        summary = json.loads((folder / 'summary.json').read_text())
        assert summary['exit'] == 0 and summary['totals'] == dict(tests=expected, failures=0, errors=0, skipped=0)
        cases = []
        for report in folder.glob('TEST-*.xml'):
            suite = ET.parse(report).getroot()
            assert all(int(suite.get(f, 0)) == 0 for f in ('failures', 'errors', 'skipped'))
            actual = list(suite.iter('testcase'))
            assert int(suite.get('tests', 0)) == len(actual)
            cases.extend(actual)
        assert len(cases) == expected and all(c.get('classname') == class_name for c in cases)
        assert not any(issue.tag in ('failure', 'error', 'skipped') for case in cases for issue in case)
        for name, digest in summary['artifacts'].items():
            assert sha(folder / 'artifacts' / name) == digest
        if class_name == 'im.hikaru.harness.desktop.ScheduledCronPageIntegrationTest':
            assert summary['cron_matrix'] == verify_cron_matrix(folder, summary)
        summaries.append(summary)
    assert len(summaries) == len(manifest['suites'])
    destination = REPO / 'docs/desktop-ui/verification/scheduled-cron-page-enable-2026-10-09' / (args.attempt + '-final')
    assert not destination.exists()
    shutil.copytree(evidence, destination)
    shutil.copy2(args.delivery, destination / 'worker-delivery.json')
    markdown = args.delivery.with_suffix('.md')
    if markdown.exists():
        shutil.copy2(markdown, destination / 'worker-delivery.md')
    shutil.copy2(BASELINE / 'baseline.json', destination / 'baseline.json')
    shutil.copy2(Path(str(source) + '-manifest.json'), destination / 'source-manifest.json')
    totals = {field: sum(s['totals'][field] for s in summaries) for field in ('tests', 'failures', 'errors', 'skipped')}
    write_json(destination / 'acceptance.json', dict(attempt=args.attempt, totals=totals,
               model=data['model'], provider=data['provider'], frozen_inputs=len(manifest['inputs']),
               workspace_inputs_verified=True, source_writes_stopped=True, suites=len(summaries)))
    print(json.dumps(dict(evidence=str(destination), totals=totals)), flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('operation', choices=('freeze', 'run', 'archive'))
    parser.add_argument('--attempt', required=True)
    parser.add_argument('--delivery', type=Path, default=Path('/tmp/harness-scheduled-cron-page-enable-delivery-20261009.json'))
    args = parser.parse_args()
    {'freeze': freeze, 'run': run, 'archive': archive}[args.operation](args)
