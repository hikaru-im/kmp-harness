from pathlib import Path
import argparse
import hashlib
import json
import shutil

p = argparse.ArgumentParser()
p.add_argument('--attempt', required=True)
p.add_argument('--delivery', default='/tmp/harness-scheduled-executor-crash-delivery-20261009.json')
p.add_argument('--base', required=True)
p.add_argument('--base-build', required=True)
a = p.parse_args()
repo = Path('/Volumes/Data/IdeaProjects/kmp-harness')
base = Path(a.base)
source = Path('/tmp/harness-scheduled-executor-crash-parent-candidate-20261009-' + a.attempt)
build = Path('/tmp/harness-scheduled-executor-crash-parent-build-20261009-' + a.attempt)
d = json.loads(Path(a.delivery).read_text())
assert d.get('model') == 'SHUAIAPI/gpt-6.1-sol' and d.get('provider') == 'opencodex', 'Wrong implementation model/provider'
assert d.get('write_set_released') is True and d.get('tests_run') is False and d.get('builds_run') is False, 'Missing or invalid release flags'
records = d.get('files') or d.get('changed_files')
allowed = {'apps/jvm-app/test/im/hikaru/harness/desktop/scheduling/DesktopScheduledExecutorCrashProcessHelper.kt', 'apps/jvm-app/test/im/hikaru/harness/desktop/scheduling/DesktopScheduledExecutorCrashProcessTest.kt'}
reported = {e['path'] for e in records}
assert reported == allowed
assert len(records) == len(reported), 'Duplicate delivery paths'
old = json.loads(Path(str(base) + '-manifest.json').read_text())
assert len(old['inputs']) == 2209, 'Unexpected base input count'
for e in records:
    assert 'before_sha256' in e, 'Missing before SHA ' + e['path']
    assert e['before_sha256'] == old['inputs'].get(e['path']), 'Wrong delivery baseline ' + e['path']
for name, digest in old['inputs'].items():
    assert hashlib.sha256((base / name).read_bytes()).hexdigest() == digest, 'Frozen base drift ' + name
    current = repo / name
    assert current.is_file(), 'Missing input ' + name
    if hashlib.sha256(current.read_bytes()).hexdigest() != digest:
        assert name in reported, 'Unreported source drift ' + name
for root in ('apps', 'modules'):
    for current in (repo / root).rglob('*.kt'):
        if 'build' not in current.parts:
            name = str(current.relative_to(repo))
            assert name in old['inputs'] or name in reported, 'Unreported new source ' + name
for e in records:
    assert hashlib.sha256((repo / e['path']).read_bytes()).hexdigest() == e['after_sha256'], e['path']
assert not source.exists() and not build.exists()
shutil.copytree(base, source, symlinks=True, ignore=shutil.ignore_patterns('build', '.git', 'node_modules', '__pycache__', 'captures'))
for e in records:
    dest = source / e['path']
    dest.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(repo / e['path'], dest)
    assert hashlib.sha256(dest.read_bytes()).hexdigest() == e['after_sha256'], 'Copy drift ' + e['path']
inputs = {str(f.relative_to(source)): hashlib.sha256(f.read_bytes()).hexdigest() for f in sorted(source.rglob('*')) if f.is_file() and (f.suffix in ('.kt', '.yaml') or f.name == 'kotlin')}
for name, digest in old['inputs'].items():
    if name not in reported:
        assert inputs.get(name) == digest, 'Frozen copy drift ' + name
assert len(inputs) == 2211, 'Unexpected frozen input count'
Path(str(source) + '-manifest.json').write_text(json.dumps({'source': str(source), 'base': str(base), 'overlays': sorted(reported), 'inputs': inputs}, indent=2) + '\n')
shutil.copytree(a.base_build, build, symlinks=True, ignore=shutil.ignore_patterns('docs'))
(source / 'docs/desktop-ui').mkdir(parents=True, exist_ok=True)
(build / 'docs').symlink_to(source / 'docs', target_is_directory=True)
print(json.dumps({'source': str(source), 'build': str(build), 'inputs': len(inputs), 'overlays': sorted(reported)}))
