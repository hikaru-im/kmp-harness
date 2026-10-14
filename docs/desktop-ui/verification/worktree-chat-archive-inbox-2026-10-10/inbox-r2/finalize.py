from pathlib import Path
from datetime import datetime, timezone
import argparse, hashlib, importlib.util, json, shutil
import xml.etree.ElementTree as ET

REPO = Path('/Volumes/Data/IdeaProjects/kmp-harness')
PREFIX = '/tmp/harness-worktree-chat-archive-inbox-'
BASE = Path(PREFIX + 'baseline-20261010')
DOCS = ['docs/desktop-ui/replication-plan-2026-10-07.md',
        'docs/desktop-ui/git-worktrees-page-readiness-2026-10-08.md']
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()

def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + '\n')

def finalize(attempt):
    root = Path(PREFIX + 'evidence-20261010-' + attempt)
    archive = REPO / 'docs/desktop-ui/verification/worktree-chat-archive-inbox-2026-10-10' / attempt
    assert not archive.exists()
    manifest = json.loads((root / 'source-manifest.json').read_text())
    baseline = json.loads((root / 'baseline.json').read_text())
    delivery = json.loads((root / 'delivery.json').read_text())
    tests = json.loads((root / 'test-results.json').read_text())
    build = json.loads((root / 'jvm-app-build.json').read_text())
    review = json.loads((root / 'parent-review.json').read_text())
    assert review['accepted'] and review['scope'] == 'inbox-only'
    assert json.loads((root / 'worker-exit.json').read_text())['exit'] == 0
    assert delivery['baseline_sha256'] == sha(root / 'baseline.json')
    assert delivery['contract_sha256'] == sha(root / 'contract.md')
    assert delivery['write_set_released'] and delivery['source_writes_stopped']
    assert all(delivery[k] is False for k in ('tests_run', 'builds_run', 'screenshots_run', 'native_execution_run'))
    assert manifest['model'] == delivery['model'] == 'SHUAIAPI/gpt-6.1-sol'
    assert manifest['provider'] == delivery['provider'] == 'opencodex'
    assert tests['status'] == 'passed' and tests['tests'] == 54
    assert len(tests['suites']) == 7 and build['exit'] == 0
    totals = {k: sum(s['totals'][k] for s in tests['suites'])
              for k in ('tests', 'failures', 'errors', 'skipped')}
    assert totals == dict(tests=54, failures=0, errors=0, skipped=0)
    for suite in tests['suites']:
        assert suite['exit'] == 0
        name = suite['class_name']
        recorded = []
        for xml in (root / name.rsplit('.', 1)[-1]).glob('TEST-*.xml'):
            tree = ET.parse(xml).getroot()
            for case in tree.iter('testcase'):
                assert case.get('classname') == name
                assert not any(c.tag in ('failure', 'error', 'skipped') for c in case)
                recorded.append(case.get('name').removesuffix('()'))
        assert len(recorded) == suite['expected'] == suite['totals']['tests']
        assert set(recorded) == set(baseline['fixed_methods'][name])
        assert len(recorded) == len(set(recorded))
    spec = importlib.util.spec_from_file_location('inbox_parent', root / 'parent-verify.py')
    parent = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(parent)
    parent.checker.check(REPO, manifest['inputs'])
    parent.checker.check(Path(manifest['source']), manifest['inputs'])
    changed = [n for n, h in manifest['inputs'].items() if baseline['inputs'].get(n) != h]
    assert set(changed) == set(parent.ALLOWED) == {r['path'] for r in delivery['files']}
    assert len(baseline['inputs']) == 2261 and len(manifest['inputs']) == 2262
    assert len(changed) == 3
    for name in DOCS:
        assert sha(REPO / name) == sha(BASE / 'documentation-before' / name), name
    acceptance = dict(
        status='passed', accepted_at=datetime.now(timezone.utc).isoformat(),
        batch='worktree-chat-archive-inbox-' + attempt,
        requested_model=manifest['model'], requested_provider=manifest['provider'],
        worker_exit=0, worker_write_set_released=True, parent_frozen_verification=True,
        source_inputs=2262, changed_existing_inputs=2, added_inputs=1, unchanged_inputs=2259,
        modified_files=delivery['files'], test_suites=7, totals=totals, jvm_app_build_exit=0,
        source_manifest='source-manifest.json', source_snapshot=manifest['source'],
        build_directory=manifest['build'], review='parent-review.json',
        session_admission=True, inbox_full_operation_admission=True,
        caller_cancellation_consistency=True, owner_disposal_consistency=False,
        registry_driver_admission=False, agent_quiescence=False, room_quiescence=False,
        process_pty_quiescence=False, archived_metadata=False, archive_ui=False,
        explicit_quiescent_restore=False, native_window=False, remote=False,
        original_pixel_parity=False, screenshots_run=False, overall_goal='active')
    write(root / 'acceptance.json', acceptance)
    detail = (
        '\n## 2026-10-10 Worktrees 聊天归档 A2a：Inbox ' + attempt + ' 最终验收\n\n'
        '先完成A2只读分析，再收敛为Session/Inbox两文件修改和固定8方法新测试。指定 '
        '**SHUAIAPI/gpt-6.1-sol / opencodex** 实施并exit0释放写集；父线程冻结2,262输入，'
        '修改2个既有文件、新增1个文件，其余2,259输入字节保持。\n\n'
        'Session仅新增精确实例operation许可入口。Inbox insert/claim/replace/clear在mutex、复制及空队列判断前准入，'
        'Closing四入口拒绝无变更。最小NonCancellable段覆盖append、projection、version/消息ID及同步通知，'
        'nextItemId在回调前推进；验证调用者取消、回调失败、提交前失败和旧Inbox/同ID replacement隔离。'
        '正常存活owner下live/replay一致；owner disposal竞争仍由后续有限清理合同处理。\n\n'
        '父线程独立串行完成Inbox8、SessionAdmission8、Session14、Agent6、AgentLoop10、LlmRetry1、Persistence7，'
        '共7类 **54/54**，失败/错误/跳过均0；完整JVM app build **exit0**。'
        '旧open-turn repair、pending retry重启续跑及keepInbox取消语义保留；实际XML固定方法已逐项核对。'
        '源码before/after、diff、review、冻结manifest、XML、日志、交付和逐文件SHA位于 '
        '`verification/worktree-chat-archive-inbox-2026-10-10/' + attempt + '/`。\n\n'
        '本批只证明Inbox全段准入与正常owner下的取消一致性；Registry/driver、有限owner清理、显式静止恢复、'
        'Room/process/PTY静止、持久归档/正式bootstrap、Worktrees与Recent/当前聊天同步仍待后续。'
        '归档按钮未启用；无截图、原生窗口或像素验收。不加入独立归档页、删除或清理。历史测试不累加，整体目标active。\n')
    (root / 'acceptance.md').write_text('# Inbox 定向验收通过\n' + detail)
    shutil.copytree(root, archive)
    shutil.copy2(Path(__file__), archive / 'finalize.py')
    for row in delivery['files']:
        name = row['path']
        assert sha(archive / 'source-after' / name) == row['after_sha256']
        if row['before_sha256'] is not None:
            assert sha(archive / 'source-before' / name) == row['before_sha256']
    plan = REPO / DOCS[0]
    content = plan.read_text()
    old = '；Session准入gate-r3 35/35） |'
    assert content.count(old) == 1
    content = content.replace(old, '；Session准入gate-r3 35/35；Inbox ' + attempt + ' 54/54） |')
    old_paragraph = next(l for l in content.splitlines() if l.startswith('当前 Git/Worktrees 聊天固定 pin-r2 已验收；'))
    content = content.replace(old_paragraph,
        '当前 Git/Worktrees 聊天固定 pin-r2、Session准入A1（gate-r3）及Inbox准入A2a（' + attempt + '）已验收。'
        'Inbox批次父线程独立串行54/54及完整JVM build通过；各批独立保留，不累加。'
        '归档/恢复继续推进Registry/driver和有限owner清理，再做显式静止恢复、资源静止、持久归档与Worktrees/Recent接线。'
        '尚未证明完整静止或启用归档按钮；完整移除保护继续单列。')
    plan.write_text(content + detail)
    readiness = REPO / DOCS[1]
    readiness.write_text(readiness.read_text() + detail)
    for name in DOCS:
        target = archive / 'documentation-after' / name
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(REPO / name, target)
    files = sorted(p for p in archive.rglob('*') if p.is_file())
    write(archive / 'archive-sha256.json', dict(files=[
        dict(path=str(p.relative_to(archive)), bytes=p.stat().st_size, sha256=sha(p)) for p in files]))
    for row in json.loads((archive / 'archive-sha256.json').read_text())['files']:
        path = archive / row['path']
        assert path.stat().st_size == row['bytes'] and sha(path) == row['sha256']
    parent.checker.check(REPO, manifest['inputs'])
    print('Archived and verified', len(files), 'files;', archive, flush=True)

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--attempt', default='inbox-r1')
    finalize(parser.parse_args().attempt)
