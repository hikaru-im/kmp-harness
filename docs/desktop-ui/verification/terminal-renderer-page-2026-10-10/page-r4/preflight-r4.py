from pathlib import Path
import difflib
import hashlib
import importlib.util
import json
import re
import subprocess

repo = Path('/Volumes/Data/IdeaProjects/kmp-harness')
prefix = '/tmp/harness-terminal-renderer-page-'
before_dir = Path(prefix + 'repair-r4-before-20261010')
before = json.loads((before_dir / 'before.json').read_text())
delivery_path = Path(prefix + 'delivery-20261010.json')
delivery = json.loads(delivery_path.read_text())
previous = json.loads((before_dir / 'delivery.json').read_text())
repair_path = Path(prefix + 'repair-r4-delivery-20261010.json')
repair = json.loads(repair_path.read_text())
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
assert subprocess.run(['ps', '-p', '8096', '-o', 'command='], capture_output=True, text=True).returncode != 0
assert delivery['model'] == 'SHUAIAPI/gpt-6.1-sol' and delivery['provider'] == 'opencodex'
assert delivery['write_set_released'] is True and delivery['source_writes_stopped'] is True
for key in ['builds_run', 'tests_run', 'screenshots_run', 'native_execution_run', 'compiler_run', 'process_fixtures_started']:
    assert delivery[key] is False
assert sha(Path(delivery['baseline_path'])) == before['baseline_sha256'] == delivery['baseline_sha256']
assert sha(Path(delivery['contract_path'])) == before['contract_sha256'] == delivery['contract_sha256']
spec = importlib.util.spec_from_file_location('parent_verify', repo / 'docs/desktop-ui/verification/terminal-renderer-page-2026-10-10/parent-verify.py')
parent = importlib.util.module_from_spec(spec)
spec.loader.exec_module(parent)
assert len(before['inputs']) == 2253
parent.check(repo, before['inputs'], before['allowed'])
changed = [name for name, digest in before['inputs'].items() if sha(repo / name) != digest]
assert set(changed) == set(before['allowed'])
assert len(delivery['files']) == 22
old_rows = {row['path']: row for row in previous['files']}
for row in delivery['files']:
    assert sha(repo / row['path']) == row['after_sha256']
    assert row['before_sha256'] == old_rows[row['path']]['before_sha256']
    if row['path'] not in before['allowed']:
        assert row == old_rows[row['path']]
assert delivery['fixed_test_method_count'] == 36
assert delivery['fixed_test_methods'] == previous['fixed_test_methods']
assert len(delivery['fixed_test_methods']) == 36
for module, name, expected in parent.SUITES:
    path = repo / parent.MODULE_ROOTS.get(module, 'modules/' + module) / 'test' / (name.replace('.', '/') + '.kt')
    source = path.read_text()
    methods = re.findall(r'@Test\s+(?:public\s+)?fun\s+(\w+)\s*\(', source)
    assert len(methods) == expected and len(set(methods)) == expected
    assert len(re.findall(r'@Test\b', source)) == expected
    assert set(methods) == parent.FIXED_METHODS[name]
assert sum(row[2] for row in parent.SUITES) == 184
diffs = []
for name in before['allowed']:
    old = (before_dir / 'source' / name).read_text()
    new = (repo / name).read_text()
    diffs.append(''.join(difflib.unified_diff(old.splitlines(True), new.splitlines(True), fromfile='r3/' + name, tofile='r4/' + name)))
    if name.endswith('PtyTerminalPageInteractionTest.kt'):
        pattern = r'    @Test fun historySelectionCopyAndExitedScreenRemainVisible\(\).*?(?=    @Test fun |\Z)'
        assert re.sub(pattern, '', old, flags=re.S) == re.sub(pattern, '', new, flags=re.S)
        assert new.count('assertEquals(expectedRows.first(),') == 3
        assert 'moveTo(Offset(1f, rowY)); press(); advanceEventTime(16); moveTo(Offset(bounds.width - 1f, rowY)); release()' in new
    else:
        assert 'currentAddress(down.position), currentAddress(slopTrigger.position)' in new
result = subprocess.run(['git', 'diff', '--check', '--', *before['allowed']], cwd=repo, capture_output=True, text=True)
assert result.returncode == 0, result.stdout + result.stderr
review = dict(status='passed', model=delivery['model'], provider=delivery['provider'], worker_exited=True,
              inputs=2253, unchanged_outside_write_set=2251, changed_files=changed, delivery_sha_verified=22,
              baseline_sha256=before['baseline_sha256'], contract_sha256=before['contract_sha256'],
              fixed_new_test_methods=36, fixed_total_test_methods=184, page_test_methods=6,
              other_page_methods_byte_identical=True, real_pointer_down_anchor_reviewed=True,
              single_move_gesture_preserved=True, independent_literal_clipboard_expectations=True,
              running_and_exited_copy_sentinels_preserved=True, no_native_write_and_close_assertions_preserved=True,
              worker_dynamic_validation_not_run=True, write_set_released=True, source_writes_stopped=True,
              dynamic_validation_pending=True)
Path(prefix + 'preflight-review-r4-20261010.json').write_text(json.dumps(review, indent=2) + '\n')
Path(prefix + 'repair-r4-parent-reviewed-20261010.diff').write_text(''.join(diffs))
print(json.dumps(review))
print(json.dumps(repair))
