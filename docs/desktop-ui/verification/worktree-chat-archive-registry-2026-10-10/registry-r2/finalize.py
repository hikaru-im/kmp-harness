from pathlib import Path
from datetime import datetime, timezone
import argparse, hashlib, importlib.util, json, shutil
import xml.etree.ElementTree as ET

REPO = Path('/Volumes/Data/IdeaProjects/kmp-harness')
PREFIX = '/tmp/harness-worktree-chat-archive-registry-'
BASE = Path(PREFIX + 'baseline-20261010')
DOCS = ['docs/desktop-ui/replication-plan-2026-10-07.md',
        'docs/desktop-ui/git-worktrees-page-readiness-2026-10-08.md']
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()

def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + '\n')

def finalize(attempt):
    root = Path(PREFIX + 'evidence-20261010-' + attempt)
    archive = REPO / 'docs/desktop-ui/verification/worktree-chat-archive-registry-2026-10-10' / attempt
    assert not archive.exists()
    manifest = json.loads((root / 'source-manifest.json').read_text())
    baseline = json.loads((root / 'baseline.json').read_text())
    delivery = json.loads((root / 'delivery.json').read_text())
    tests = json.loads((root / 'test-results.json').read_text())
    build = json.loads((root / 'jvm-app-build.json').read_text())
    review = json.loads((root / 'parent-review.json').read_text())
    assert review['accepted'] and review['scope'] == 'registry-only'
    assert json.loads((root / 'worker-exit.json').read_text())['exit'] == 0
    assert delivery['baseline_sha256'] == sha(root / 'baseline.json')
    assert delivery['contract_sha256'] == sha(root / 'contract.md')
    assert delivery['write_set_released'] and delivery['source_writes_stopped']
    assert all(delivery[k] is False for k in ('tests_run', 'builds_run', 'screenshots_run', 'native_execution_run'))
    assert manifest['model'] == delivery['model'] == 'SHUAIAPI/gpt-6.1-sol'
    assert manifest['provider'] == delivery['provider'] == 'opencodex'
    if attempt == 'registry-r2':
        repair = json.loads((root / 'repair-delivery.json').read_text())
        assert repair['model'] == manifest['model'] and repair['provider'] == manifest['provider']
        assert repair['normalization']['raw_delivery_sha256'] == sha(root / 'repair-delivery-raw.json')
        assert repair['repair_baseline_sha256'] == sha(root / 'repair-baseline.json')
        assert repair['original_baseline_sha256'] == sha(root / 'baseline.json')
        assert repair['contract_sha256'] == sha(root / 'contract.md')
        assert repair['write_set_released'] and repair['source_writes_stopped']
        assert len(repair['files']) == 1
        repair_row = repair['files'][0]
        assert repair_row['before_sha256'] == sha(root / 'repair-test-before.kt')
        assert repair_row['after_sha256'] == sha(root / 'source-after' / repair_row['path'])
        assert json.loads((root / 'original-worker-exit.json').read_text())['exit'] == 0
        failed = REPO / 'docs/desktop-ui/verification/worktree-chat-archive-registry-2026-10-10/registry-r1'
        assert json.loads((failed / 'failure.json').read_text())['tests_executed'] == 0
    assert tests['status'] == 'passed' and tests['tests'] == 60
    assert len(tests['suites']) == 8 and build['exit'] == 0
    totals = {k: sum(s['totals'][k] for s in tests['suites'])
              for k in ('tests', 'failures', 'errors', 'skipped')}
    assert totals == dict(tests=60, failures=0, errors=0, skipped=0)
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
    assert len(baseline['inputs']) == 2262 and len(manifest['inputs']) == 2263
    assert len(changed) == 2
    for name in DOCS:
        assert sha(REPO / name) == sha(BASE / 'documentation-before' / name), name
    acceptance = dict(
        status='passed', accepted_at=datetime.now(timezone.utc).isoformat(),
        batch='worktree-chat-archive-registry-' + attempt,
        requested_model=manifest['model'], requested_provider=manifest['provider'],
        worker_exit=0, worker_write_set_released=True, parent_frozen_verification=True,
        source_inputs=2263, changed_existing_inputs=1, added_inputs=1, unchanged_inputs=2261,
        modified_files=delivery['files'], test_suites=8, totals=totals, jvm_app_build_exit=0,
        source_manifest='source-manifest.json', source_snapshot=manifest['source'],
        build_directory=manifest['build'], review='parent-review.json',
        session_admission=True, inbox_full_operation_admission=True, registry_full_operation_admission=True, regular_handle_admission=True,
        caller_cancellation_consistency=True, owner_disposal_consistency=False,
        driver_admission=False, agent_quiescence=False, room_quiescence=False,
        process_pty_quiescence=False, archived_metadata=False, archive_ui=False,
        explicit_quiescent_restore=False, native_window=False, remote=False,
        original_pixel_parity=False, screenshots_run=False, overall_goal='active')
    write(root / 'acceptance.json', acceptance)
    detail = (
        '\n## 2026-10-10 Worktrees 聊天归档 B：Registry ' + attempt + ' 最终验收\n\n'
        '父线程先复核 Registry 生产源码及生命周期只读报告，收敛为一个生产文件和一个新增测试文件。'
        '指定 **SHUAIAPI/gpt-6.1-sol / opencodex** 实施并 exit0 释放写集；父线程冻结 2,263 输入，'
        '修改 1 个既有文件、新增 1 个文件，其余 2,261 输入字节保持。\n\n'
        'registry-r1 因新增测试访问跨模块 internal Context.isDisposing 编译失败，尚未执行行为测试；失败证据独立保留。'
        'registry-r2 由同一指定模型仅修新增测试，使用真实 cleanup gate 与公开 isDisposed 断言，生产实现字节保持；原基线与两次交付均保留。\n\n'
        'create/restore 在 Registry mutex 前获取精确 Store catalog 许可，覆盖 setup/factory/publication/'
        'Restore listener/同步通知/最后取消检查/NonCancellable rollback。创建登记与已发布 entry 使用精确 identity，'
        '未采用 loaded Session 的失败不关闭该 Session；普通 Handle Closing 拒绝后可重试，旧 identity 不影响 replacement。'
        '本批未改变 Registry owner disposal，父 Context 并发关闭的 Runtime lane 等待风险仍由后续合同处理。\n\n'
        '父线程独立串行完成 Registry6、Inbox8、SessionAdmission8、Session14、Agent6、AgentLoop10、LlmRetry1、Persistence7，'
        '共 8 类 **60/60**，失败/错误/跳过均 0；完整 JVM app build **exit0**。'
        'XML 固定方法逐项核对；原 open-turn repair、pending retry 重启与 keepInbox 取消语义回归保留。'
        '源码 before/after、diff、父 review、冻结 manifest、XML、日志、交付和逐文件 SHA 位于 '
        'verification/worktree-chat-archive-registry-2026-10-10/' + attempt + '/。\n\n'
        '下一批 driver 独立活动；有限 owner 清理依赖 Runtime 生命周期等待边界的独立验收。'
        '之后才做显式静止恢复、Room/process/PTY 静止、归档持久化与 Worktrees/Recent 接线。'
        '归档按钮未启用；无截图、原生窗口或像素验收；不增加独立归档页、删除或清理。各批证据独立，整体目标 active。\n')
    (root / 'acceptance.md').write_text('# Registry 定向验收通过\n' + detail)
    shutil.copytree(root, archive)
    shutil.copy2(Path(__file__), archive / 'finalize.py')
    for row in delivery['files']:
        name = row['path']
        assert sha(archive / 'source-after' / name) == row['after_sha256']
        if row['before_sha256'] is not None:
            assert sha(archive / 'source-before' / name) == row['before_sha256']
    plan = REPO / DOCS[0]
    content = plan.read_text()
    old = '；Inbox inbox-r2 54/54） |'
    assert content.count(old) == 1
    content = content.replace(old, '；Inbox inbox-r2 54/54；Registry ' + attempt + ' 60/60） |')
    old_paragraph = next(l for l in content.splitlines() if l.startswith('当前 Git/Worktrees 聊天固定 pin-r2'))
    content = content.replace(old_paragraph,
        '当前 Git/Worktrees 聊天固定 pin-r2、Session准入A1（gate-r3）、Inbox准入A2a（inbox-r2）和Registry准入B（' + attempt + '）已验收。'
        'Registry批次父线程独立串行60/60及完整JVM build通过；各批独立保留，不累加。'
        '归档/恢复继续推进driver独立活动；有限owner清理依赖Runtime生命周期等待边界，再做显式静止恢复、资源静止、'
        '持久归档与Worktrees/Recent接线。尚未证明完整静止或启用归档按钮；完整移除保护继续单列。')
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
    parser.add_argument('--attempt', default='registry-r2')
    finalize(parser.parse_args().attempt)
