from pathlib import Path
from datetime import datetime, timezone
import argparse, hashlib, importlib.util, json, shutil
import xml.etree.ElementTree as ET

REPO = Path('/Volumes/Data/IdeaProjects/kmp-harness')
PREFIX = '/tmp/harness-worktree-chat-archive-a1-'
BASE = Path(PREFIX + 'baseline-20261010')
DOCS = ['docs/desktop-ui/replication-plan-2026-10-07.md',
        'docs/desktop-ui/git-worktrees-page-readiness-2026-10-08.md']
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()

def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + '\n')

def snapshot_docs():
    for name in DOCS:
        target = BASE / 'documentation-before' / name
        assert not target.exists()
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(REPO / name, target)
    print('Saved both current documentation baselines')

def finalize(attempt):
    root = Path(PREFIX + 'evidence-20261010-' + attempt)
    archive = REPO / 'docs/desktop-ui/verification/worktree-chat-archive-session-gate-2026-10-10' / attempt
    assert not archive.exists()
    manifest = json.loads((root / 'source-manifest.json').read_text())
    baseline = json.loads((root / 'baseline.json').read_text())
    delivery = json.loads((root / 'delivery.json').read_text())
    tests = json.loads((root / 'test-results.json').read_text())
    build = json.loads((root / 'jvm-app-build.json').read_text())
    review = json.loads((root / 'parent-review.json').read_text())
    assert review['accepted'] and review['scope'] == 'session-only'
    assert attempt == 'gate-r3'
    assert json.loads((root / 'initial-worker-exit.json').read_text())['exit'] == 0
    repair = json.loads((root / 'repair-baseline.json').read_text())
    assert delivery['repair_baseline_sha256'] == sha(root / 'repair-baseline.json')
    assert delivery['repair_contract_sha256'] == sha(root / 'repair-contract.md')
    assert repair['unchanged_required'] == 2260 and len(repair['inputs']) == 2261
    changed_by_repair = [name for name, digest in manifest['inputs'].items() if repair['inputs'].get(name) != digest]
    assert set(changed_by_repair) == {'modules/session/src/im/hikaru/harness/session/SessionAdmission.kt','modules/session/test/im/hikaru/harness/session/SessionAdmissionContractTest.kt'}
    test_repair = json.loads((root / 'test-repair-baseline.json').read_text())
    assert delivery['test_repair_baseline_sha256'] == sha(root / 'test-repair-baseline.json')
    assert delivery['test_repair_contract_sha256'] == sha(root / 'test-repair-contract.md')
    assert len(test_repair['inputs']) == 2261 and test_repair['unchanged_required'] == 2260
    assert [name for name,digest in manifest['inputs'].items() if test_repair['inputs'].get(name) != digest] == ['modules/session/test/im/hikaru/harness/session/SessionAdmissionContractTest.kt']
    failed_root = REPO / 'docs/desktop-ui/verification/worktree-chat-archive-session-gate-2026-10-10/gate-r1-failed'
    failure = json.loads((failed_root / 'failure.json').read_text())
    assert failure['status'] == 'compile-failed' and failure['tests_executed'] == 0
    assert not failure['jvm_app_build_run'] and not failure['subsequent_suites_run']
    for row in json.loads((failed_root / 'archive-sha256.json').read_text())['files']:
        p = failed_root / row['path']; assert sha(p) == row['sha256'] and p.stat().st_size == row['bytes']
    failed_r2 = REPO / 'docs/desktop-ui/verification/worktree-chat-archive-session-gate-2026-10-10/gate-r2-failed'
    failure_r2 = json.loads((failed_r2 / 'failure.json').read_text())
    assert failure_r2['status'] == 'test-errors' and failure_r2['tests_executed'] == 8 and failure_r2['errors'] == 2 and failure_r2['passed'] == 6
    assert not failure_r2['subsequent_suites_run'] and not failure_r2['jvm_app_build_run']
    for row in json.loads((failed_r2 / 'archive-sha256.json').read_text())['files']:
        p = failed_r2 / row['path']; assert sha(p) == row['sha256'] and p.stat().st_size == row['bytes']
    assert tests['status'] == 'passed' and tests['tests'] == 35
    assert len(tests['suites']) == 4 and build['exit'] == 0
    totals = {k: sum(s['totals'][k] for s in tests['suites'])
              for k in ('tests', 'failures', 'errors', 'skipped')}
    assert totals == dict(tests=35, failures=0, errors=0, skipped=0)
    for suite in tests['suites']:
        assert suite['exit'] == 0
        name = suite['class_name']
        recorded, count = [], 0
        for xml in (root / name.rsplit('.', 1)[-1]).glob('TEST-*.xml'):
            tree = ET.parse(xml).getroot()
            for case in tree.iter('testcase'):
                assert case.get('classname') == name
                assert not any(child.tag in ('failure', 'error', 'skipped') for child in case)
                recorded.append(case.get('name').removesuffix('()'))
                count += 1
        assert count == suite['expected'] == suite['totals']['tests']
        assert set(recorded) == set(baseline['fixed_methods'][name])
        assert len(recorded) == len(set(recorded))
    assert manifest['model'] == delivery['model'] == 'SHUAIAPI/gpt-6.1-sol'
    assert manifest['provider'] == delivery['provider'] == 'opencodex'
    assert json.loads((root / 'worker-exit.json').read_text())['exit'] == 0
    assert delivery['write_set_released'] and delivery['source_writes_stopped']
    spec = importlib.util.spec_from_file_location('gate_parent', root / 'parent-verify.py')
    parent = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(parent)
    parent.parent.check(REPO, manifest['inputs'])
    parent.parent.check(Path(manifest['source']), manifest['inputs'])
    changed = [name for name, h in manifest['inputs'].items()
               if baseline['inputs'].get(name) != h]
    assert set(changed) == {row['path'] for row in delivery['files']}
    assert len(manifest['inputs']) == 2261 and len(changed) == 4
    for name in DOCS:
        assert sha(REPO / name) == sha(BASE / 'documentation-before' / name), name
    acceptance = dict(
        status='passed', accepted_at=datetime.now(timezone.utc).isoformat(),
        batch='worktree-chat-archive-session-gate-' + attempt,
        requested_model=manifest['model'], requested_provider=manifest['provider'],
        worker_exit=0, worker_write_set_released=True, parent_frozen_verification=True,
        source_inputs=2261, changed_existing_inputs=2, added_inputs=2, unchanged_inputs=2257,
        repair_modified_inputs=1, repair_unchanged_inputs=2260, test_repair_modified_inputs=1, test_repair_unchanged_inputs=2260, prior_failed_attempts=['gate-r1-failed','gate-r2-failed'], prior_tests_executed=[0,8],
        modified_files=delivery['files'], test_suites=4, totals=totals, jvm_app_build_exit=0,
        source_manifest='source-manifest.json', source_snapshot=manifest['source'],
        build_directory=manifest['build'], review='parent-review.json',
        session_admission=True, agent_quiescence=False, room_quiescence=False,
        process_pty_quiescence=False, archived_metadata=False, archive_ui=False,
        explicit_quiescent_restore=False, native_window=False, remote=False,
        original_pixel_parity=False, screenshots_run=False, overall_goal='active')
    write(root / 'acceptance.json', acceptance)
    summary = (
        '# Worktrees 聊天归档 A1：Session 准入定向验收通过\n\n'
        '宿主请求路由 SHUAIAPI/gpt-6.1-sol / opencodex。工作者 exit0 释放四文件写集后，'
        '父线程冻结2,261输入；修改2个既有文件、新增2个文件，其余2,257输入字节保持。'
        '独立串行完成4类35/35，失败/错误/跳过均0；完整JVM app build exit0。'
        '固定8个新增方法和27个既有方法均核对实际XML。\n\n'
        'gate-r1 在编译阶段失败，实际执行0项；失败源码与日志独立保留于 gate-r1-failed。'
        '同一指定模型先修正一处接收者引用；gate-r2执行8项，6通过、2跨Default等待发生虚拟时间超时，旧三类和build未运行。其后只修测试文件两处等待及import，各窄修其余2,260输入保持；退出后重新冻结gate-r3验收，方法名规范化仅去末尾()且保留原始XML。\n\n'
        '本批只交付属于精确Store/Session实例的活动准入与独占reservation，覆盖实际Session写入、'
        'catalog生命周期及同步回调，验证取消释放、过期token与同ID replacement隔离。'
        '保留owner mutex及同步通知；默认open-turn repair与pending retry重启行为通过旧回归。\n\n'
        'Session层通过不能证明Agent、Room、后台进程或PTY静止。Agent执行入口、显式静止恢复、'
        '独立归档元数据、正式bootstrap及Worktrees/Recent页面接线仍待后续；未启用归档或删除按钮。'
        '本批无截图或原生窗口执行，不声称原版像素一致。整体目标active；各批测试不累加。\n')
    (root / 'acceptance.md').write_text(summary)
    shutil.copytree(root, archive)
    shutil.copytree(BASE / 'documentation-before', archive / 'documentation-before')
    for row in delivery['files']:
        name = row['path']
        target = archive / 'source-after' / name
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(Path(manifest['source']) / name, target)
        assert sha(target) == row['after_sha256']
        if row['before_sha256'] is not None:
            target = archive / 'source-before' / name
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(BASE / 'before' / name, target)
            assert sha(target) == row['before_sha256']
    shutil.copy2(Path(__file__), archive / 'finalize.py')
    plan = REPO / DOCS[0]
    content = plan.read_text()
    old = '| 2（页面及路由已验收；Host观察host-r5 38/38；聊天固定pin-r2 56/56） |'
    new = '| 2（页面及路由已验收；Host观察host-r5 38/38；聊天固定pin-r2 56/56；Session准入' + attempt + ' 35/35） |'
    assert content.count(old) == 1
    content = content.replace(old, new)
    old_paragraph = next(line for line in content.splitlines() if line.startswith('当前 Git/Worktrees 关联聊天固定已完成 pin-r2 定向验收：'))
    content = content.replace(old_paragraph,
        '当前 Git/Worktrees 聊天固定 pin-r2 已验收；归档/恢复只读设计已收齐并收敛为分步实施。'
        'Session准入 A1（' + attempt + '）已由指定模型实施、父线程独立验收4类35/35及完整JVM build。'
        '这只证明Session写入和Store生命周期的准入门槛，未证明Agent/Room/process/PTY静止，未启用归档按钮。'
        '下一步接Agent/Inbox/driver实际入口和显式静止恢复，再推进独立归档持久化及Worktrees/Recent接线。完整移除保护继续单列。')
    detail = (
        '\n## 2026-10-10 Worktrees 聊天归档 A1：Session准入 ' + attempt + ' 最终验收\n\n'
        '归档只读设计已退出，父线程将广泛A提案收敛为四文件合同。指定 ' 
        '**SHUAIAPI/gpt-6.1-sol / opencodex** 实施，退出释放写集后，父线程冻结2,261输入；'
        'SessionStore/Session两文件修改，新增SessionAdmission及固定8方法测试；其余2,257输入字节保持。\n\n'
        '每个Store持有精确实例的协调器，普通目标活动/catalog许可覆盖真实操作与回调，独占reservation在忙碌时立即typed拒绝。'
        'reservation关闭目标写入及Store生命周期准入，保留其他既有Session append；取消释放、旧token/handle、'
        '同ID replacement及owner dispose都有定向证明。owner mutex和同步observer语义保留，不引入继承上下文授权。\n\n'
        '父线程独立串行完成Admission8、Session14、Agent6、Persistence7，共4类 **35/35**，失败/错误/跳过均0；'
        '完整JVM app build **exit0**。旧open-turn repair与pending retry重启续跑均保留。'
        'gate-r1编译失败（0项执行）的源码和日志独立保留；同一模型仅修复一处接收者，'
        '窄修基线中其余2,260输入保持。gate-r2编译通过但8项中2项跨Default等待虚拟时间超时，6项通过；旧三类及build未运行，失败独立保留。随后同一模型只修测试文件两处等待及import，各窄修保持其余2,260输入。gate-r3重新冻结并独立串行验收；XML方法名仅规范化末尾()，原名保留，不复用旧XML。'
        '源码before/after、review、冻结manifest、固定方法XML、日志、delivery及逐文件SHA位于 '
        '`verification/worktree-chat-archive-session-gate-2026-10-10/' + attempt + '/`。\n\n'
        '本批只证明Session层准入。Agent/Inbox/driver、显式静止恢复、Room/process/PTY静止、持久归档/正式bootstrap、'
        'Worktrees与Recent/当前聊天同步仍待后续；归档按钮未启用。无截图、原生窗口或像素验收。'
        '不加入独立归档管理页、删除或清理；历史批次不累加，整体目标继续active。\n')
    plan.write_text(content + detail)
    readiness = REPO / DOCS[1]
    content = readiness.read_text()
    old = '下一批先审查归档/恢复生命周期设计，涵盖真实入口共享gate、静止证明、恢复自动retry和持久提交边界；尚未派实施。完整归档/恢复与移除保护仍未完成，不开放删除或清理。'
    assert content.count(old) == 1
    content = content.replace(old,
        '归档/恢复只读设计已收齐，首批Session准入已实施并通过下述定向验收。完整归档/恢复与移除保护仍未完成，不开放删除或清理。')
    readiness.write_text(content + detail)
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
    parent.parent.check(REPO, manifest['inputs'])
    print('Archived and verified', len(files), 'files;', archive)

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('action', choices=('snapshot-docs', 'finalize'))
    parser.add_argument('--attempt', default='gate-r1')
    args = parser.parse_args()
    if args.action == 'snapshot-docs':
        snapshot_docs()
    else:
        finalize(args.attempt)
