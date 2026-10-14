from pathlib import Path
import difflib
import hashlib
import importlib.util
import json
import re
import subprocess

repo = Path('/Volumes/Data/IdeaProjects/kmp-harness')
prefix = '/tmp/harness-terminal-renderer-page-'
before_dir = Path(prefix + 'repair-r5-before-20261010')
before = json.loads((before_dir / 'before.json').read_text())
previous = json.loads((before_dir / 'delivery.json').read_text())
delivery = json.loads(Path(prefix + 'delivery-20261010.json').read_text())
repair = json.loads(Path(prefix + 'repair-r5-delivery-20261010.json').read_text())
worker = json.loads(Path(prefix + 'worker-exit-repair-r5-20261010.json').read_text())
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
assert worker['exit'] == 0
assert subprocess.run(['ps', '-p', str(worker['pid']), '-o', 'command='], capture_output=True, text=True).returncode != 0
for data in [delivery, repair]:
    assert data['model'] == 'SHUAIAPI/gpt-6.1-sol' and data['provider'] == 'opencodex'
    assert data['write_set_released'] is True and data['source_writes_stopped'] is True
    for key in ['builds_run', 'tests_run', 'screenshots_run', 'native_execution_run', 'compiler_run', 'process_fixtures_started']:
        assert data[key] is False
    assert data['baseline_sha256'] == before['baseline_sha256']
    assert data['contract_sha256'] == before['contract_sha256']
assert sha(Path(delivery['baseline_path'])) == before['baseline_sha256']
assert sha(Path(delivery['contract_path'])) == before['contract_sha256']
spec = importlib.util.spec_from_file_location('parent_verify', repo / 'docs/desktop-ui/verification/terminal-renderer-page-2026-10-10/parent-verify.py')
parent = importlib.util.module_from_spec(spec)
spec.loader.exec_module(parent)
assert len(before['inputs']) == 2253
parent.check(repo, before['inputs'], before['allowed'])
changed = [name for name, digest in before['inputs'].items() if sha(repo / name) != digest]
assert set(changed) == set(before['allowed'])
old_rows = {row['path']: row for row in previous['files']}
assert len(delivery['files']) == 22
for row in delivery['files']:
    assert sha(repo / row['path']) == row['after_sha256']
    assert row['before_sha256'] == old_rows[row['path']]['before_sha256']
    if row['path'] not in before['allowed']: assert row == old_rows[row['path']]
for key in ['fixed_test_methods', 'fixed_test_method_count', 'repair_r2', 'repair_r3', 'repair_r4']:
    assert delivery[key] == previous[key]
assert delivery['fixed_test_method_count'] == len(delivery['fixed_test_methods']) == 36
for module, name, expected in parent.SUITES:
    path = repo / parent.MODULE_ROOTS.get(module, 'modules/' + module) / 'test' / (parent.TEST_SOURCE_CLASS.get(name, name).replace('.', '/') + '.kt')
    source = path.read_text()
    match = re.search(r'(?m)^class ' + re.escape(name.rsplit('.', 1)[-1]) + r'\b', source)
    assert match
    source = source[match.start():]
    next_class = re.search(r'(?m)^class ', source[1:])
    if next_class: source = source[:next_class.start()+1]
    methods = re.findall(r'@Test\s+(?:public\s+)?fun\s+(\w+)\s*\(', source)
    assert len(methods) == len(set(methods)) == len(re.findall(r'@Test\b', source)) == expected
    assert set(methods) == parent.FIXED_METHODS[name]
assert len(parent.SUITES) == 30 and sum(row[2] for row in parent.SUITES) == 184
diffs = []
for name in before['allowed']:
    old = (before_dir / 'source' / name).read_text()
    new = (repo / name).read_text()
    diffs.append(''.join(difflib.unified_diff(old.splitlines(True), new.splitlines(True), fromfile='r4/' + name, tofile='r5/' + name)))
    if name.endswith('PtyTerminalPageInteractionTest.kt'):
        pattern = r'    @Test fun (?:wideAndCompactLightDarkMappedGridRemainUsable|unknownUnavailableAndViewCloseHaveHonestActions)\(\).*?(?=    @Test fun |\Z)'
        assert re.sub(pattern, '', old, flags=re.S) == re.sub(pattern, '', new, flags=re.S), 'Unexpected other test/helper changes'
        assert new.count('assertEquals(expectedRows.first(),') == 3
        assert 'moveTo(Offset(1f, rowY)); press(); advanceEventTime(16); moveTo(Offset(bounds.width - 1f, rowY)); release()' in new
result = subprocess.run(['git', 'diff', '--check', '--', *before['allowed']], cwd=repo, capture_output=True, text=True)
assert result.returncode == 0, result.stdout + result.stderr
review = dict(status='passed', worker_exited=True, model=delivery['model'], provider=delivery['provider'],
              inputs=2253, unchanged_outside_write_set=2251, changed_files=changed, delivery_sha_verified=22,
              baseline_sha256=before['baseline_sha256'], contract_sha256=before['contract_sha256'],
              fixed_new_test_methods=36, fixed_total_test_methods=184, fixed_test_classes=30, page_test_methods=6,
              four_other_page_methods_and_helpers_byte_identical=True,
              history_copy_method_byte_identical=True, worker_dynamic_validation_not_run=True,
              write_set_released=True, source_writes_stopped=True, dynamic_validation_pending=True)
Path(prefix + 'preflight-review-r5-20261010.json').write_text(json.dumps(review, indent=2) + chr(10))
Path(prefix + 'repair-r5-parent-reviewed-20261010.diff').write_text(''.join(diffs))
print(json.dumps(review))
print(''.join(diffs))
