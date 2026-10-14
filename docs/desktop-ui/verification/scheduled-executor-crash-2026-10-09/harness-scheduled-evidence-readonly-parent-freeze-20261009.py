from pathlib import Path
import argparse
import hashlib
import json
import shutil

p = argparse.ArgumentParser()
p.add_argument('--attempt', required=True)
p.add_argument('--delivery', default='/tmp/harness-scheduled-evidence-readonly-delivery-20261009.json')
p.add_argument('--base', default='/tmp/harness-scheduled-executor-crash-parent-candidate-20261009-initial')
p.add_argument('--base-build', default='/tmp/harness-scheduled-executor-crash-parent-build-20261009-initial')
a = p.parse_args()
repo = Path('/Volumes/Data/IdeaProjects/kmp-harness')
base = Path(a.base)
source = Path('/tmp/harness-scheduled-executor-crash-parent-candidate-20261009-' + a.attempt)
build = Path('/tmp/harness-scheduled-executor-crash-parent-build-20261009-' + a.attempt)
d = json.loads(Path(a.delivery).read_text())
assert d.get('model') == 'SHUAIAPI/gpt-6.1-sol' and d.get('provider') == 'opencodex'
assert d.get('write_set_released') is True and d.get('tests_run') is False and d.get('builds_run') is False
records = d.get('files') or d.get('changed_files')
allowed = {
    'modules/session-persistence/src/im/hikaru/harness/session/persistence/RoomSessionPersistence.kt',
    'apps/jvm-app/src/im/hikaru/harness/desktop/scheduling/DesktopScheduledSessionEvidence.kt',
    'modules/session-persistence/test/im/hikaru/harness/session/persistence/SessionPersistenceReadOnlyEventsTest.kt',
    'apps/jvm-app/test/im/hikaru/harness/desktop/scheduling/DesktopScheduledSessionEvidenceReadOnlyTest.kt',
    'apps/jvm-app/test/im/hikaru/harness/desktop/scheduling/DesktopScheduledExecutorCrashProcessTest.kt',
}
reported = {e['path'] for e in records}
assert reported == allowed and len(records) == len(allowed), 'Wrong write set'
old = json.loads(Path(str(base) + '-manifest.json').read_text())
assert len(old['inputs']) == 2211
for e in records:
    assert e['before_sha256'] == old['inputs'].get(e['path']), 'Wrong before SHA ' + e['path']
    assert hashlib.sha256((repo / e['path']).read_bytes()).hexdigest() == e['after_sha256'], e['path']
for name, digest in old['inputs'].items():
    assert hashlib.sha256((base / name).read_bytes()).hexdigest() == digest, 'Base drift ' + name
    current = repo / name
    assert current.is_file(), 'Missing input ' + name
    if hashlib.sha256(current.read_bytes()).hexdigest() != digest:
        assert name in reported, 'Unreported drift ' + name
for root in ('apps', 'modules'):
    for current in (repo / root).rglob('*.kt'):
        if 'build' not in current.parts:
            name = str(current.relative_to(repo))
            assert name in old['inputs'] or name in reported, 'Unreported new source ' + name
assert not source.exists() and not build.exists()
shutil.copytree(base, source, symlinks=True, ignore=shutil.ignore_patterns('build', '.git', 'node_modules', '__pycache__', 'captures'))
for e in records:
    dest = source / e['path']
    dest.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(repo / e['path'], dest)
    assert hashlib.sha256(dest.read_bytes()).hexdigest() == e['after_sha256']
inputs = {str(f.relative_to(source)): hashlib.sha256(f.read_bytes()).hexdigest() for f in sorted(source.rglob('*')) if f.is_file() and (f.suffix in ('.kt', '.yaml') or f.name == 'kotlin')}
assert len(inputs) == 2213
for name, digest in old['inputs'].items():
    if name not in reported:
        assert inputs[name] == digest, 'Copy drift ' + name
Path(str(source) + '-manifest.json').write_text(json.dumps(dict(source=str(source), base=str(base), overlays=sorted(reported), inputs=inputs), indent=2) + '\n')
shutil.copytree(a.base_build, build, symlinks=True, ignore=shutil.ignore_patterns('docs'))
(source / 'docs/desktop-ui').mkdir(parents=True, exist_ok=True)
(build / 'docs').symlink_to(source / 'docs', target_is_directory=True)
print(json.dumps(dict(source=str(source), build=str(build), inputs=len(inputs), overlays=sorted(reported))))
